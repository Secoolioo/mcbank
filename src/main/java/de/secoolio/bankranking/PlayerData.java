package de.secoolio.bankranking;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Das Punktekonto aller Spieler in plugins/BankRanking/players.yml.
 *
 * <p>Nur vom Hauptthread benutzen. Jede Einzahlung wird sofort gespeichert; geschrieben wird
 * ueber eine temporaere Datei mit anschliessendem atomaren Verschieben, damit ein Absturz
 * mitten im Schreiben die vorhandene Datei nie beschaedigt.
 */
public final class PlayerData {

    public record Entry(String name, double points, PlayerStats stats) {

        Entry(String name, double points) {
            this(name, points, PlayerStats.EMPTY);
        }
    }

    /** Ergebnis einer Einzahlung: ob gespeichert werden konnte und der neue Kontostand. */
    public record AddResult(boolean saved, double total) {
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final File file;
    private final Logger log;
    private final Map<UUID, Entry> entries = new TreeMap<>();
    private final Map<UUID, Saturation> saturations = new HashMap<>();
    /** Eintraege, die das Plugin nicht lesen konnte - sie werden unveraendert zurueckgeschrieben. */
    private final Map<String, Object> unreadable = new LinkedHashMap<>();
    private boolean dirty;
    private boolean loadFailed;

    public PlayerData(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /**
     * Laedt beim Start. Eine unlesbare Datei wird zur Seite gelegt und der Punktestand startet leer;
     * schlaegt schon das Zurseitelegen fehl, verweigert das Plugin spaeter jedes Speichern, damit die
     * noch rettbare Datei nicht ueberschrieben wird.
     */
    public void load() {
        this.entries.clear();
        this.saturations.clear();
        this.unreadable.clear();
        this.dirty = false;
        this.loadFailed = false;
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(this.file);
        } catch (IOException | InvalidConfigurationException ex) {
            quarantine(ex.getMessage());
            return;
        }
        if (yaml.getConfigurationSection("spieler") == null) {
            // Die Datei entsteht nur mit mindestens einem Konto. Fehlt der Abschnitt, ist sie
            // beschaedigt (etwa ein abgebrochener Schreibvorgang) und darf nicht ueberschrieben werden.
            quarantine("keine lesbare 'spieler'-Sektion");
            return;
        }
        readInto(yaml, this.entries, this.saturations, this.unreadable);
    }

    /**
     * Laedt im laufenden Betrieb neu (z.B. /bankranking reload). Anders als beim Start wird nichts
     * zur Seite gelegt und der Speicherstand bleibt erhalten, wenn die Datei unlesbar ist.
     *
     * @return true, wenn neu geladen wurde
     */
    public boolean reload() {
        if (this.dirty) {
            save();
        }
        if (this.dirty) {
            this.log.warning("players.yml wurde NICHT neu geladen - es gibt noch ungespeicherte Punkte im Speicher");
            return false;
        }
        if (!this.file.exists()) {
            this.entries.clear();
            this.saturations.clear();
            this.unreadable.clear();
            // Die beschaedigte Datei ist nachweislich weg, also darf wieder gespeichert werden.
            this.loadFailed = false;
            return true;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(this.file);
        } catch (IOException | InvalidConfigurationException ex) {
            this.loadFailed = true;
            this.log.severe("players.yml ist beschädigt (" + ex.getMessage()
                    + ") - die Punkte im Speicher bleiben unverändert und es wird nichts gespeichert."
                    + " Datei reparieren oder löschen, danach /bankranking reload");
            return false;
        }
        if (yaml.getConfigurationSection("spieler") == null) {
            this.loadFailed = true;
            this.log.severe("players.yml hat keine lesbare 'spieler'-Sektion - die Punkte im Speicher bleiben"
                    + " unverändert und es wird nichts gespeichert."
                    + " Datei reparieren oder löschen, danach /bankranking reload");
            return false;
        }
        Map<UUID, Entry> freshEntries = new TreeMap<>();
        Map<UUID, Saturation> freshSaturations = new HashMap<>();
        Map<String, Object> freshUnreadable = new LinkedHashMap<>();
        readInto(yaml, freshEntries, freshSaturations, freshUnreadable);
        // Alle drei Stände gemeinsam tauschen, damit Punkte und Sättigung nie auseinanderlaufen.
        this.entries.clear();
        this.entries.putAll(freshEntries);
        this.saturations.clear();
        this.saturations.putAll(freshSaturations);
        this.unreadable.clear();
        this.unreadable.putAll(freshUnreadable);
        this.loadFailed = false;
        return true;
    }

    private void readInto(YamlConfiguration yaml, Map<UUID, Entry> target,
                          Map<UUID, Saturation> saturationTarget,
                          Map<String, Object> unreadableTarget) {
        ConfigurationSection section = yaml.getConfigurationSection("spieler");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                keepUnreadable(section, key, unreadableTarget, "keine gültige Spieler-ID");
                continue;
            }
            double points = section.getDouble(key + ".punkte", Double.NaN);
            if (!Double.isFinite(points) || points < 0.0) {
                keepUnreadable(section, key, unreadableTarget, "keine gültige Punktzahl");
                continue;
            }
            String name = section.getString(key + ".name", "Unbekannt");
            PlayerStats stats = PlayerStats.read(section.getConfigurationSection(key + ".statistik"),
                    System.currentTimeMillis());
            target.put(id, new Entry(name, points, stats));
            readSaturation(section, key, id, saturationTarget);
        }
    }

    /** Merkt sich einen unlesbaren Eintrag wortgetreu, statt ihn beim Speichern zu verlieren. */
    private void keepUnreadable(ConfigurationSection section, String key,
                                Map<String, Object> target, String reason) {
        target.put(key, YamlSections.rawValue(section, key));
        this.log.warning("players.yml: Eintrag '" + key + "' hat " + reason
                + " - er wird beim Speichern unverändert übernommen, bitte von Hand prüfen");
    }

    /** Liest die Marktsaettigung eines Spielers, wenn sie in der Datei steht. */
    private void readSaturation(ConfigurationSection section, String key, UUID id,
                                Map<UUID, Saturation> target) {
        ConfigurationSection block = section.getConfigurationSection(key + ".saettigung");
        if (block == null) {
            return;
        }
        long now = System.currentTimeMillis();
        // Ein von Hand verstellter Zeitstempel darf den Zeitverfall nicht ins Absurde treiben.
        long stand = Math.min(Math.max(0L, block.getLong("stand", now)), now);
        Saturation saturation = new Saturation(stand);
        ConfigurationSection werte = block.getConfigurationSection("werte");
        if (werte != null) {
            for (String materialKey : werte.getKeys(false)) {
                Material material = Material.matchMaterial(materialKey);
                if (material == null || material.isLegacy()) {
                    continue;
                }
                double value = werte.getDouble(materialKey, 0.0);
                if (Double.isFinite(value) && value > 0.0) {
                    saturation.put(material, value);
                }
            }
        }
        if (!saturation.isEmpty()) {
            target.put(id, saturation);
        }
    }

    /**
     * Die Marktsaettigung eines Spielers als unveraenderliche Sicht, mit eingerechnetem Zeitverfall.
     *
     * <p>Legt bewusst nichts an: wer nur nachschaut, hinterlaesst keinen Eintrag.
     *
     * @param halfLifeHours Halbwertszeit aus der Konfiguration
     */
    public Map<Material, Double> saturationView(UUID id, double halfLifeHours) {
        Saturation saturation = this.saturations.get(id);
        if (saturation == null) {
            return Map.of();
        }
        saturation.decay(System.currentTimeMillis(), halfLifeHours);
        return saturation.snapshot();
    }

    private void quarantine(String reason) {
        Path source = this.file.toPath();
        Path target = source.resolveSibling(this.file.getName() + ".corrupt-" + LocalDateTime.now().format(STAMP));
        try {
            Files.move(source, target);
            this.log.severe("players.yml ist beschädigt (" + reason + ") und wurde nach "
                    + target.getFileName() + " verschoben - die Punkte starten leer");
        } catch (IOException ex) {
            this.loadFailed = true;
            this.log.severe("players.yml ist beschädigt (" + reason + ") und konnte NICHT zur Seite"
                    + " gelegt werden (" + ex.getMessage() + ") - es wird nichts gespeichert, bitte die Datei"
                    + " von Hand prüfen");
        }
    }

    /**
     * Bucht eine Einzahlung: Punkte und Marktsaettigung zusammen, sofort gespeichert.
     *
     * <p>Eine Transaktion - schlaegt das Speichern fehl, wird alles zurueckgesetzt, damit der
     * Preis eines Rohstoffs nie faellt, ohne dass jemand dafuer Punkte bekommen hat.
     *
     * @param saturationDeltas Zuwaechse je Rohstoffgruppe aus {@link Scorer.Deposit}
     */
    public AddResult add(UUID id, String name, double delta, Map<Material, Double> saturationDeltas) {
        return add(id, name, delta, saturationDeltas, null, Map.of());
    }

    /**
     * Wie oben, zusaetzlich mit den Kennzahlen fuer die Kontoseite.
     *
     * @param deposit          die Einzahlung fuer die Statistik, oder {@code null}
     * @param itemsPerMaterial Stueckzahl je Material dieser Einzahlung
     */
    public AddResult add(UUID id, String name, double delta, Map<Material, Double> saturationDeltas,
                         PlayerStats.Deposit deposit, Map<Material, Integer> itemsPerMaterial) {
        if (!Double.isFinite(delta) || delta < 0.0) {
            this.log.severe("Einzahlung von " + name + " ergab keinen gültigen Betrag (" + delta
                    + ") - nichts gebucht");
            return new AddResult(false, get(id));
        }
        Entry previous = this.entries.get(id);
        Saturation previousSaturation = this.saturations.get(id);
        Saturation savedSaturation = previousSaturation == null ? null : previousSaturation.copy();
        boolean wasDirty = this.dirty;

        // Der Spieler hat wieder ein gueltiges Konto - der alte, unlesbare Block wird nicht mehr gebraucht.
        Object droppedBlock = this.unreadable.remove(id.toString());
        double total = Scorer.round1((previous == null ? 0.0 : previous.points()) + delta);
        PlayerStats stats = previous == null ? PlayerStats.EMPTY : previous.stats();
        if (deposit != null) {
            stats = stats.record(deposit, itemsPerMaterial);
        }
        this.entries.put(id, new Entry(name, total, stats));
        if (!saturationDeltas.isEmpty()) {
            this.saturations.computeIfAbsent(id, key -> new Saturation(System.currentTimeMillis()))
                    .commit(saturationDeltas);
        }
        this.dirty = true;
        if (save()) {
            return new AddResult(true, total);
        }

        if (previous == null) {
            this.entries.remove(id);
        } else {
            this.entries.put(id, previous);
        }
        if (savedSaturation == null) {
            this.saturations.remove(id);
        } else {
            this.saturations.put(id, savedSaturation);
        }
        if (droppedBlock != null) {
            this.unreadable.put(id.toString(), droppedBlock);
        }
        this.dirty = wasDirty;
        return new AddResult(false, previous == null ? 0.0 : previous.points());
    }

    public double get(UUID id) {
        Entry entry = this.entries.get(id);
        return entry == null ? 0.0 : entry.points();
    }

    /** Die Kennzahlen eines Spielers; nie {@code null}. */
    public PlayerStats stats(UUID id) {
        Entry entry = this.entries.get(id);
        return entry == null ? PlayerStats.EMPTY : entry.stats();
    }

    /**
     * Platz in der Rangliste, 1-basiert; 0 wenn der Spieler noch keine Punkte hat.
     *
     * <p>Zaehlt, wie viele Konten besser stehen - ohne die ganze Liste zu kopieren und zu sortieren,
     * weil das Bank-Fenster diesen Wert bei jedem Klick neu anzeigt.
     */
    public int rank(UUID id) {
        Entry own = this.entries.get(id);
        if (own == null) {
            return 0;
        }
        int place = 1;
        for (Map.Entry<UUID, Entry> entry : this.entries.entrySet()) {
            if (entry.getKey().equals(id)) {
                continue;
            }
            Entry other = entry.getValue();
            if (other.points() > own.points()
                    || (other.points() == own.points()
                        && String.CASE_INSENSITIVE_ORDER.compare(other.name(), own.name()) < 0)) {
                place++;
            }
        }
        return place;
    }

    public List<Map.Entry<UUID, Entry>> top(int count) {
        List<Map.Entry<UUID, Entry>> all = sorted();
        return all.subList(0, Math.min(count, all.size()));
    }

    /** Einmalige, sortierte Abschrift aller Konten - fuer Aufrufer, die mehrfach darauf zugreifen. */
    public List<Map.Entry<UUID, Entry>> snapshot() {
        return sorted();
    }

    private List<Map.Entry<UUID, Entry>> sorted() {
        List<Map.Entry<UUID, Entry>> all = new ArrayList<>(this.entries.entrySet());
        all.sort(Comparator
                .comparingDouble((Map.Entry<UUID, Entry> e) -> e.getValue().points()).reversed()
                .thenComparing(e -> e.getValue().name(), String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    public int size() {
        return this.entries.size();
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void saveIfDirty() {
        if (this.dirty) {
            save();
        }
    }

    /** Schreibt players.yml atomar. Gibt false zurueck, wenn nichts geschrieben werden konnte. */
    public boolean save() {
        if (this.loadFailed) {
            this.log.severe("players.yml wird nicht gespeichert, weil die beschädigte Datei noch vorhanden ist");
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        ConfigurationSection players = yaml.createSection("spieler");
        // Zuerst die bewahrten Bloecke, danach die echten Konten: haette ein Spieler beides
        // (etwa weil seine Punktzahl frueher unlesbar war und er inzwischen wieder eingezahlt hat),
        // gewinnt sein echtes Konto.
        this.unreadable.forEach((key, values) -> YamlSections.restore(players, key, values));
        for (Map.Entry<UUID, Entry> entry : this.entries.entrySet()) {
            String base = "spieler." + entry.getKey();
            yaml.set(base + ".name", entry.getValue().name());
            yaml.set(base + ".punkte", entry.getValue().points());
            PlayerStats stats = entry.getValue().stats();
            if (!stats.isEmpty()) {
                stats.write(yaml.createSection(base + ".statistik"));
            }
            Saturation saturation = this.saturations.get(entry.getKey());
            if (saturation != null) {
                // Kleinstwerte gar nicht erst schreiben - sie wuerden beim Laden ohnehin verworfen.
                saturation.forgetSmall();
            }
            if (saturation != null && !saturation.isEmpty()) {
                yaml.set(base + ".saettigung.stand", saturation.lastDecay());
                for (Map.Entry<Material, Double> amount : saturation.amounts().entrySet()) {
                    yaml.set(base + ".saettigung.werte." + amount.getKey().name().toLowerCase(Locale.ROOT),
                            Scorer.round1(amount.getValue()));
                }
            }
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
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            this.dirty = false;
            return true;
        } catch (IOException ex) {
            this.log.severe("players.yml konnte nicht gespeichert werden: " + ex.getMessage()
                    + " - die Punkte bleiben im Speicher, nächster Versuch bei der nächsten Einzahlung");
            return false;
        }
    }
}
