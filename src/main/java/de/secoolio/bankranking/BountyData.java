package de.secoolio.bankranking;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Die Kopfgelder und die noch nicht abgeholte Beute, in {@code kopfgelder.yml}.
 *
 * <p>Bewusst eine eigene Datei neben {@code players.yml}, und zwar aus einem Grund, der schwerer
 * wiegt als Ordnungsliebe: bei einer unlesbaren {@code players.yml} legt {@link PlayerData} die
 * Datei zur Seite und startet mit leeren Konten. Fuer Punkte ist das vertretbar. Hier lagern
 * echte Gegenstaende von Spielern - dasselbe Verhalten waere Diebstahl. Wird diese Datei
 * unlesbar, wird sie <strong>nicht</strong> angefasst und jeder Schreibzugriff gesperrt, bis ein
 * Mensch sie repariert hat.
 *
 * <p>Jede Aenderung ist eine Transaktion: erst den alten Stand merken, dann aendern, dann
 * speichern - schlaegt das Speichern fehl, wird der alte Stand vollstaendig zurueckgesetzt.
 * Damit gilt fuer den Aufrufer die einfache Regel, dass er Gegenstaende erst dann aus dem
 * Fenster oder aus dem Topf nehmen darf, wenn hier {@code true} zurueckkam.
 */
public final class BountyData {

    /**
     * Gegenstaende, die jemandem gehoeren, aber noch nicht in seinem Inventar sind.
     *
     * <p>Das ist das Eigentumsregister. Was in der Welt steht - eine schwebende Kiste - ist nur
     * Anzeige; verschwindet sie, ist nichts verloren.
     *
     * @param delivering ob gerade zugestellt wird. Bleibt der Wert nach einem Absturz stehen,
     *                   koennte die Zustellung schon geklappt haben - dann wird beim naechsten
     *                   Start gewarnt statt blind ein zweites Mal ausgezahlt.
     */
    public record Claim(UUID owner, String name, Reason reason, long time,
                        Map<Material, Integer> items, boolean delivering,
                        String world, double x, double y, double z) {

        /** Warum diese Gegenstaende hier liegen. */
        public enum Reason { AUSZAHLUNG, RUECKERSTATTUNG, RUECKGABE }

        public Claim {
            items = Map.copyOf(items);
        }

        public boolean hasLocation() {
            return this.world != null;
        }

        Claim withItems(Map<Material, Integer> neu) {
            return new Claim(this.owner, this.name, this.reason, this.time, neu, this.delivering,
                    this.world, this.x, this.y, this.z);
        }

        Claim withDelivering(boolean laeuft) {
            return new Claim(this.owner, this.name, this.reason, this.time, this.items, laeuft,
                    this.world, this.x, this.y, this.z);
        }

        Claim withLocation(String world, double x, double y, double z) {
            return new Claim(this.owner, this.name, this.reason, this.time, this.items,
                    this.delivering, world, x, y, z);
        }
    }

    private final File file;
    private final Logger log;

    private final Map<UUID, Bounty> pots = new LinkedHashMap<>();
    private final Map<UUID, Claim> claims = new LinkedHashMap<>();
    /** Bloecke, die nicht gelesen werden konnten - sie werden wortgetreu bewahrt. */
    private final Map<String, Object> unreadablePots = new LinkedHashMap<>();
    private final Map<String, Object> unreadableClaims = new LinkedHashMap<>();

    private boolean dirty;
    private boolean loadFailed;

    public BountyData(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    // ------------------------------------------------------------------ Laden

    public void load() {
        this.pots.clear();
        this.claims.clear();
        this.unreadablePots.clear();
        this.unreadableClaims.clear();
        this.dirty = false;
        this.loadFailed = false;

        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(this.file);
        } catch (IOException | InvalidConfigurationException ex) {
            lock("kopfgelder.yml ist nicht lesbar (" + ex.getMessage() + ")");
            return;
        }
        readInto(yaml);
    }

    /**
     * Laedt im laufenden Betrieb neu.
     *
     * @return false, wenn die Datei nicht gelesen werden konnte
     */
    public boolean reload() {
        if (this.dirty) {
            save();
        }
        if (this.dirty) {
            this.log.warning("kopfgelder.yml hat ungespeicherte Aenderungen und wird nicht neu geladen");
            return false;
        }
        load();
        return !this.loadFailed;
    }

    private void lock(String grund) {
        this.loadFailed = true;
        this.log.severe(grund + ". Die Datei wird NICHT angefasst und es wird nichts gespeichert - "
                + "sie enthaelt echte Gegenstaende von Spielern. Bitte reparieren, danach "
                + "/bankranking reload");
    }

    private void readInto(YamlConfiguration yaml) {
        ConfigurationSection kopfgelder = yaml.getConfigurationSection("kopfgelder");
        if (kopfgelder != null) {
            for (String key : kopfgelder.getKeys(false)) {
                UUID ziel = parseUuid(key);
                Bounty pot = ziel == null ? null : readPot(ziel, kopfgelder.getConfigurationSection(key));
                if (pot == null) {
                    // Ein halb gelesener Topf waere schlimmer als gar keiner: er wuerde
                    // teilweise ausgezahlt und der Rest beim naechsten Speichern geloescht.
                    this.unreadablePots.put(key, YamlSections.rawValue(kopfgelder, key));
                    this.log.warning("kopfgelder.yml: Eintrag '" + key + "' ist unlesbar und wird "
                            + "unveraendert bewahrt. Auf diesen Spieler laesst sich vorerst kein "
                            + "Kopfgeld setzen.");
                } else {
                    this.pots.put(ziel, pot);
                }
            }
        }

        ConfigurationSection abholung = yaml.getConfigurationSection("abholung");
        if (abholung != null) {
            for (String key : abholung.getKeys(false)) {
                UUID besitzer = parseUuid(key);
                Claim claim = besitzer == null
                        ? null : readClaim(besitzer, abholung.getConfigurationSection(key));
                if (claim == null) {
                    this.unreadableClaims.put(key, YamlSections.rawValue(abholung, key));
                    this.log.warning("kopfgelder.yml: Beute-Eintrag '" + key
                            + "' ist unlesbar und wird unveraendert bewahrt");
                } else {
                    if (claim.delivering()) {
                        this.log.warning("kopfgelder.yml: die Zustellung an " + claim.name()
                                + " war beim letzten Herunterfahren nicht abgeschlossen. Die "
                                + "Gegenstaende bleiben in der Beute - moeglicherweise hat der "
                                + "Spieler sie bereits erhalten, bitte pruefen.");
                    }
                    this.claims.put(besitzer, claim.withDelivering(false));
                }
            }
        }
    }

    private static UUID parseUuid(String key) {
        try {
            return UUID.fromString(key);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** @return der Topf, oder {@code null} wenn irgendetwas daran unlesbar ist */
    private Bounty readPot(UUID ziel, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String name = section.getString("name");
        if (name == null || name.isBlank()) {
            return null;
        }
        long seit = section.getLong("seit", 0L);

        Bounty.Payout letzte = null;
        ConfigurationSection auszahlung = section.getConfigurationSection("letzte-auszahlung");
        if (auszahlung != null) {
            UUID killer = parseUuid(String.valueOf(auszahlung.getString("killer")));
            if (killer == null) {
                return null;
            }
            letzte = new Bounty.Payout(auszahlung.getLong("zeit", 0L), killer,
                    auszahlung.getString("name", "?"));
        }

        List<Bounty.Stake> einsaetze = new ArrayList<>();
        for (Map<?, ?> roh : section.getMapList("einsaetze")) {
            UUID von = parseUuid(String.valueOf(roh.get("von")));
            Map<Material, Integer> items = readItems(roh.get("items"));
            if (von == null || items == null || items.isEmpty()) {
                return null;
            }
            long zeit = roh.get("zeit") instanceof Number n ? n.longValue() : 0L;
            String wer = roh.get("name") instanceof String s ? s : "?";
            einsaetze.add(new Bounty.Stake(von, wer, zeit, items));
        }
        if (einsaetze.isEmpty() && letzte == null) {
            // Weder Einsatz noch Sperrfrist: der Eintrag traegt keine Information mehr.
            return null;
        }
        return new Bounty(ziel, name, seit, einsaetze, letzte);
    }

    private Claim readClaim(UUID besitzer, ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        Map<Material, Integer> items = readItems(section.get("items"));
        if (items == null || items.isEmpty()) {
            return null;
        }
        Claim.Reason grund;
        try {
            grund = Claim.Reason.valueOf(
                    String.valueOf(section.getString("grund", "auszahlung")).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
        String welt = null;
        double x = 0;
        double y = 0;
        double z = 0;
        ConfigurationSection ort = section.getConfigurationSection("ort");
        if (ort != null) {
            welt = ort.getString("welt");
            x = ort.getDouble("x");
            y = ort.getDouble("y");
            z = ort.getDouble("z");
        }
        return new Claim(besitzer, section.getString("name", "?"), grund,
                section.getLong("zeit", 0L), items,
                section.getBoolean("zustellung-laeuft", false), welt, x, y, z);
    }

    /** @return die Gegenstaende, oder {@code null} wenn etwas nicht stimmt */
    private Map<Material, Integer> readItems(Object roh) {
        Map<?, ?> quelle;
        if (roh instanceof ConfigurationSection section) {
            quelle = section.getValues(false);
        } else if (roh instanceof Map<?, ?> map) {
            quelle = map;
        } else {
            return null;
        }
        Map<Material, Integer> items = new EnumMap<>(Material.class);
        for (Map.Entry<?, ?> e : quelle.entrySet()) {
            Material material = Material.matchMaterial(String.valueOf(e.getKey()));
            if (material == null || material.isLegacy() || !BountyItems.ALLOWED.contains(material)) {
                return null;
            }
            if (!(e.getValue() instanceof Number anzahl) || anzahl.intValue() <= 0) {
                return null;
            }
            items.put(material, anzahl.intValue());
        }
        return items;
    }

    // --------------------------------------------------------------- Speichern

    public boolean isLocked() {
        return this.loadFailed;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void saveIfDirty() {
        if (this.dirty) {
            save();
        }
    }

    public boolean save() {
        if (this.loadFailed) {
            this.log.severe("kopfgelder.yml wird nicht gespeichert, weil die beschaedigte Datei "
                    + "noch vorhanden ist");
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().setHeader(List.of(
                "Von BankRanking verwaltet. Hier stehen echte Gegenstaende von Spielern.",
                "Nicht von Hand loeschen - lieber /kopfgeld aufheben <Spieler> benutzen."));

        ConfigurationSection kopfgelder = yaml.createSection("kopfgelder");
        // Erst die bewahrten Bloecke, dann die echten: haette ein Ziel beides, gewinnt der
        // echte Topf. Ein Einsatz auf ein bewahrtes Ziel wird ohnehin abgelehnt, die beiden
        // koennen sich also nicht in die Quere kommen.
        this.unreadablePots.forEach((key, wert) -> YamlSections.restore(kopfgelder, key, wert));
        for (Bounty pot : this.pots.values()) {
            if (pot.isEmpty() && pot.lastPayout() == null) {
                continue;
            }
            ConfigurationSection ziel = kopfgelder.createSection(pot.target().toString());
            ziel.set("name", pot.name());
            ziel.set("seit", pot.since());
            if (pot.lastPayout() != null) {
                ConfigurationSection letzte = ziel.createSection("letzte-auszahlung");
                letzte.set("zeit", pot.lastPayout().time());
                letzte.set("killer", pot.lastPayout().killer().toString());
                letzte.set("name", pot.lastPayout().name());
            }
            if (!pot.isEmpty()) {
                List<Map<String, Object>> liste = new ArrayList<>();
                for (Bounty.Stake stake : pot.stakes()) {
                    Map<String, Object> eintrag = new LinkedHashMap<>();
                    eintrag.put("von", stake.from().toString());
                    eintrag.put("name", stake.name());
                    eintrag.put("zeit", stake.time());
                    eintrag.put("items", itemMap(stake.items()));
                    liste.add(eintrag);
                }
                ziel.set("einsaetze", liste);
            }
        }

        ConfigurationSection abholung = yaml.createSection("abholung");
        this.unreadableClaims.forEach((key, wert) -> YamlSections.restore(abholung, key, wert));
        for (Claim claim : this.claims.values()) {
            if (claim.items().isEmpty()) {
                continue;
            }
            ConfigurationSection eintrag = abholung.createSection(claim.owner().toString());
            eintrag.set("name", claim.name());
            eintrag.set("grund", claim.reason().name().toLowerCase(java.util.Locale.ROOT));
            eintrag.set("zeit", claim.time());
            eintrag.set("zustellung-laeuft", claim.delivering());
            if (claim.hasLocation()) {
                ConfigurationSection ort = eintrag.createSection("ort");
                ort.set("welt", claim.world());
                ort.set("x", claim.x());
                ort.set("y", claim.y());
                ort.set("z", claim.z());
            }
            eintrag.set("items", itemMap(claim.items()));
        }

        Path target = this.file.toPath();
        Path temp = target.resolveSibling(this.file.getName() + ".tmp");
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            this.dirty = false;
            return true;
        } catch (IOException ex) {
            this.log.severe("kopfgelder.yml konnte nicht gespeichert werden: " + ex.getMessage());
            return false;
        }
    }

    private static Map<String, Object> itemMap(Map<Material, Integer> items) {
        Map<String, Object> map = new LinkedHashMap<>();
        items.forEach((material, anzahl) -> map.put(BountyItems.key(material), anzahl));
        return map;
    }

    // ------------------------------------------------------------------ Lesen

    /** Der Topf auf diesen Spieler, oder {@code null}. */
    public Bounty pot(UUID target) {
        return this.pots.get(target);
    }

    /** Alle Toepfe, auch die leeren mit laufender Sperrfrist. */
    public Collection<Bounty> pots() {
        return Collections.unmodifiableCollection(this.pots.values());
    }

    /** Alle Toepfe mit Einsaetzen. */
    public List<Bounty> active() {
        List<Bounty> offen = new ArrayList<>();
        for (Bounty pot : this.pots.values()) {
            if (!pot.isEmpty()) {
                offen.add(pot);
            }
        }
        return offen;
    }

    /** Ist der Eintrag dieses Spielers unlesbar und damit gesperrt? */
    public boolean isPreserved(UUID target) {
        return this.unreadablePots.containsKey(target.toString());
    }

    /** Die noch nicht abgeholte Beute dieses Spielers, oder {@code null}. */
    public Claim claim(UUID owner) {
        return this.claims.get(owner);
    }

    public Collection<Claim> claims() {
        return Collections.unmodifiableCollection(this.claims.values());
    }

    // --------------------------------------------------------------- Schreiben

    /**
     * Legt einen Einsatz in den Topf.
     *
     * <p>Der Aufrufer darf die Gegenstaende erst dann aus dem Fenster nehmen, wenn hier
     * {@code true} zurueckkam. Scheitert das Speichern, ist der Stand vollstaendig
     * zurueckgesetzt und die Gegenstaende liegen unveraendert im Fenster.
     */
    public boolean stake(UUID target, String targetName, Bounty.Stake stake) {
        if (this.loadFailed || isPreserved(target) || stake.items().isEmpty()) {
            return false;
        }
        Bounty alt = this.pots.get(target);
        boolean warDirty = this.dirty;

        Bounty neu = (alt == null ? Bounty.empty(target, targetName, stake.time()) : alt)
                .withName(targetName)
                .withStake(stake);
        this.pots.put(target, neu);
        this.dirty = true;
        if (save()) {
            return true;
        }
        if (alt == null) {
            this.pots.remove(target);
        } else {
            this.pots.put(target, alt);
        }
        this.dirty = warDirty;
        return false;
    }

    /**
     * Zahlt den ganzen Topf an den Killer aus - als eine einzige Schreiboperation.
     *
     * <p>Danach gehoeren die Gegenstaende dem Killer und stehen als Beute in der Datei. Erst
     * jetzt darf versucht werden, sie ihm ins Inventar zu legen: ein Absturz an dieser Stelle
     * kostet nichts, weil sie beim naechsten Start noch in seiner Beute liegen.
     */
    public boolean payout(UUID target, UUID killer, String killerName, long now) {
        if (this.loadFailed) {
            return false;
        }
        Bounty alt = this.pots.get(target);
        if (alt == null || alt.isEmpty()) {
            return false;
        }
        Claim alteBeute = this.claims.get(killer);
        boolean warDirty = this.dirty;

        this.pots.put(target, alt.paidOut(new Bounty.Payout(now, killer, killerName)));
        this.claims.put(killer, addToClaim(alteBeute, killer, killerName,
                Claim.Reason.AUSZAHLUNG, now, alt.total()));
        this.dirty = true;
        if (save()) {
            return true;
        }
        this.pots.put(target, alt);
        restoreClaim(killer, alteBeute);
        this.dirty = warDirty;
        return false;
    }

    /**
     * Hebt ein Kopfgeld auf und gibt jedem Einzahler seinen eigenen Einsatz in die Beute.
     *
     * <p>In die Beute und nicht ins Inventar: die Einzahler sind in aller Regel nicht online,
     * und genau dafuer ist die Beute da.
     */
    public boolean cancel(UUID target, long now) {
        if (this.loadFailed) {
            return false;
        }
        Bounty alt = this.pots.get(target);
        if (alt == null || alt.isEmpty()) {
            return false;
        }
        Map<UUID, Claim> alteBeute = new LinkedHashMap<>();
        boolean warDirty = this.dirty;

        for (Bounty.Stake stake : alt.stakes()) {
            alteBeute.putIfAbsent(stake.from(), this.claims.get(stake.from()));
            this.claims.put(stake.from(), addToClaim(this.claims.get(stake.from()), stake.from(),
                    stake.name(), Claim.Reason.RUECKERSTATTUNG, now, stake.items()));
        }
        this.pots.put(target, alt.cleared());
        this.dirty = true;
        if (save()) {
            return true;
        }
        this.pots.put(target, alt);
        alteBeute.forEach(this::restoreClaim);
        this.dirty = warDirty;
        return false;
    }

    /** Legt Gegenstaende in die Beute eines Spielers, etwa weil sein Inventar voll war. */
    public boolean addClaim(UUID owner, String name, Claim.Reason reason, long now,
                            Map<Material, Integer> items) {
        if (this.loadFailed || items.isEmpty()) {
            return false;
        }
        Claim alt = this.claims.get(owner);
        boolean warDirty = this.dirty;
        this.claims.put(owner, addToClaim(alt, owner, name, reason, now, items));
        this.dirty = true;
        if (save()) {
            return true;
        }
        restoreClaim(owner, alt);
        this.dirty = warDirty;
        return false;
    }

    /**
     * Merkt, dass gerade zugestellt wird.
     *
     * <p>Zwischen dem Ablegen im Inventar und dem Loeschen der Beute liegt ein Augenblick, in
     * dem ein Absturz beides bestehen liesse. Der Merker macht das sichtbar, statt es zu
     * verschweigen - beim naechsten Start gibt es eine Warnung, und niemand verliert etwas.
     */
    public boolean beginDelivery(UUID owner) {
        Claim alt = this.claims.get(owner);
        if (alt == null) {
            return false;
        }
        boolean warDirty = this.dirty;
        this.claims.put(owner, alt.withDelivering(true));
        this.dirty = true;
        if (save()) {
            return true;
        }
        this.claims.put(owner, alt);
        this.dirty = warDirty;
        return false;
    }

    /** Schliesst eine Zustellung ab: der Rest bleibt liegen, der Rest von nichts verschwindet. */
    public boolean finishDelivery(UUID owner, Map<Material, Integer> rest) {
        Claim alt = this.claims.get(owner);
        if (alt == null) {
            return false;
        }
        boolean warDirty = this.dirty;
        if (rest.isEmpty()) {
            this.claims.remove(owner);
        } else {
            this.claims.put(owner, alt.withItems(rest).withDelivering(false));
        }
        this.dirty = true;
        if (save()) {
            return true;
        }
        this.claims.put(owner, alt);
        this.dirty = warDirty;
        return false;
    }

    /** Merkt sich, wo die Beutekiste steht, damit sie einen Neustart uebersteht. */
    public boolean setClaimLocation(UUID owner, String world, double x, double y, double z) {
        Claim alt = this.claims.get(owner);
        if (alt == null) {
            return false;
        }
        boolean warDirty = this.dirty;
        this.claims.put(owner, alt.withLocation(world, x, y, z));
        this.dirty = true;
        if (save()) {
            return true;
        }
        this.claims.put(owner, alt);
        this.dirty = warDirty;
        return false;
    }

    private static Claim addToClaim(Claim alt, UUID owner, String name, Claim.Reason reason,
                                    long now, Map<Material, Integer> items) {
        if (alt == null) {
            return new Claim(owner, name, reason, now, items, false, null, 0, 0, 0);
        }
        return alt.withItems(BountyItems.merge(alt.items(), items));
    }

    private void restoreClaim(UUID owner, Claim alt) {
        if (alt == null) {
            this.claims.remove(owner);
        } else {
            this.claims.put(owner, alt);
        }
    }
}
