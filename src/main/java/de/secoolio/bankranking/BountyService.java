package de.secoolio.bankranking;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Fuehrt zusammen, was ein Kopfgeld ausmacht: aussetzen, kassieren, anzeigen.
 *
 * <p>Die Fenster und die Ereignis-Empfaenger rufen ausschliesslich hier hinein. Damit gibt es
 * genau einen Ort, an dem die Reihenfolge stimmen muss - erst buchen, dann Gegenstaende
 * bewegen -, statt derselben Sorgfalt an fuenf Stellen.
 */
public final class BountyService {

    /** Was beim Versuch herauskommt, ein Kopfgeld auszusetzen. */
    public enum PlaceResult {
        OK, ABGESCHALTET, DATEI_GESPERRT, ZIEL_BESCHAEDIGT, AUF_SICH_SELBST, ZU_KLEIN,
        SPERRFRIST, SPEICHERFEHLER
    }

    private final BankRankingPlugin plugin;
    private final BountyData data;
    private final SkinFaces faces;
    private final WantedShow show;

    /** Der Balken des Gejagten, je Spieler einer. */
    private final Map<UUID, BossBar> bars = new HashMap<>();
    /** Der TAB-Name vor unserem Eingriff, damit fremde Plugins ihren zurueckbekommen. */
    private final Map<UUID, Component> previousListName = new HashMap<>();

    public BountyService(BankRankingPlugin plugin, BountyData data) {
        this.plugin = plugin;
        this.data = data;
        this.faces = new SkinFaces();
        this.show = WantedShow.create(plugin);
    }

    public BountyData data() {
        return this.data;
    }

    SkinFaces faces() {
        return this.faces;
    }

    // ------------------------------------------------------------------ Werte

    /** Der Bankwert einer Menge Gegenstaende - nur zur Anzeige. */
    public double value(Map<Material, Integer> items) {
        double summe = 0.0;
        for (Map.Entry<Material, Integer> e : items.entrySet()) {
            summe += baseValue(e.getKey()) * e.getValue();
        }
        return Scorer.round1(summe);
    }

    public double value(Bounty pot) {
        return pot == null ? 0.0 : pot.value(this::baseValue);
    }

    private double baseValue(Material material) {
        return MaterialValues.baseValue(material, this.plugin.settings().fallbackValue(),
                this.plugin.settings().materialBase());
    }

    /** Der Wert als Text, so wie ihn die Bank ueberall schreibt. */
    public String format(double wert) {
        return Scorer.format(wert);
    }

    /**
     * Die Belohnung als lesbarer Text - die tatsaechlichen Gegenstaende.
     *
     * <p>Frueher stand hier eine Punktzahl. Die war irrefuehrend: sie sah aus wie ein
     * Bankguthaben, entsprach aber nicht dem, was die Bank fuer dieselben Gegenstaende
     * gutschreiben wuerde - dort greifen noch Wohlstands-Bremse und Marktsaettigung. Und sie
     * beantwortete die einzige Frage nicht, die ein Spieler hat: was bekomme ich?
     */
    public String reward(Bounty pot) {
        return pot == null ? "nichts" : BountyItems.describe(pot.total());
    }

    public String reward(Map<Material, Integer> items) {
        return BountyItems.describe(items);
    }

    /**
     * Der Mindesteinsatz, ausgedrueckt in Diamanten.
     *
     * <p>Auch hier keine Punktzahl: "zwei Diamanten" ist eine Anweisung, "40" ist ein Raetsel.
     */
    public String minStakeText() {
        double proDiamant = Math.max(0.01, baseValue(Material.DIAMOND));
        int diamanten = (int) Math.ceil(this.plugin.settings().bountyMinStake() / proDiamant);
        return diamanten <= 1 ? "1 Diamant" : diamanten + " Diamanten";
    }

    // -------------------------------------------------------------- Aussetzen

    /** Darf auf diesen Spieler gerade ein Kopfgeld gesetzt werden? */
    public PlaceResult canPlace(Player placer, UUID target) {
        if (!this.plugin.settings().bountyEnabled()) {
            return PlaceResult.ABGESCHALTET;
        }
        if (this.data.isLocked()) {
            return PlaceResult.DATEI_GESPERRT;
        }
        if (placer.getUniqueId().equals(target)) {
            return PlaceResult.AUF_SICH_SELBST;
        }
        if (this.data.isPreserved(target)) {
            return PlaceResult.ZIEL_BESCHAEDIGT;
        }
        if (postCooldownLeft(target) > 0L) {
            return PlaceResult.SPERRFRIST;
        }
        return PlaceResult.OK;
    }

    /** Wie lange auf dieses Ziel noch kein neues Kopfgeld gesetzt werden darf. */
    public long postCooldownLeft(UUID target) {
        return BountyRules.postCooldownLeft(this.data.pot(target), System.currentTimeMillis(),
                this.plugin.settings().bountyPostCooldown());
    }

    /**
     * Setzt ein Kopfgeld aus.
     *
     * <p>Der Aufrufer darf die Gegenstaende erst dann aus dem Fenster nehmen, wenn hier
     * {@link PlaceResult#OK} zurueckkam.
     */
    public PlaceResult place(Player placer, UUID target, String targetName,
                             Map<Material, Integer> items) {
        PlaceResult erlaubt = canPlace(placer, target);
        if (erlaubt != PlaceResult.OK) {
            return erlaubt;
        }
        double wert = value(items);
        if (items.isEmpty() || wert < this.plugin.settings().bountyMinStake()) {
            return PlaceResult.ZU_KLEIN;
        }
        long jetzt = System.currentTimeMillis();
        Bounty.Stake einsatz = new Bounty.Stake(placer.getUniqueId(), placer.getName(), jetzt, items);
        if (!this.data.stake(target, targetName, einsatz)) {
            return PlaceResult.SPEICHERFEHLER;
        }

        Bounty topf = this.data.pot(target);
        boolean ersterEinsatz = topf.stakes().size() == 1;
        String gesamt = reward(topf);

        this.plugin.send(placer, ersterEinsatz ? Messages.KOPFGELD_AUSGESETZT : Messages.KOPFGELD_ERHOEHT,
                Placeholder.unparsed("wert", ersterEinsatz ? reward(items) : gesamt),
                Placeholder.unparsed("name", targetName));

        Player gejagter = this.plugin.getServer().getPlayer(target);
        if (gejagter != null) {
            this.plugin.send(gejagter, Messages.KOPFGELD_AUF_DICH,
                    Placeholder.unparsed("wert", gesamt));
            this.plugin.effects().hunted(gejagter);
        }
        if (this.plugin.settings().bountyBroadcast()) {
            this.plugin.getServer().broadcast(Messages.mm(Messages.PREFIX + Messages.KOPFGELD_BROADCAST,
                    Placeholder.unparsed("von", placer.getName()),
                    Placeholder.unparsed("wert", gesamt),
                    Placeholder.unparsed("name", targetName)));
        }

        refreshAll(target);
        announce(target, targetName, placer.getName(), topf);
        return PlaceResult.OK;
    }

    /**
     * Zeigt das Plakat, sobald das Gesicht vorliegt - hoechstens aber zwei Sekunden lang.
     *
     * <p>Auch dann, wenn der Gejagte gerade nicht da ist. Ein Kopfgeld auf einen Abwesenden ist
     * ausdruecklich erlaubt, und dass dann niemand ein Plakat sah, war ein Fehler: die
     * Ankuendigung richtet sich an alle anderen, nicht an ihn.
     */
    private void announce(UUID target, String targetName, String placer, Bounty topf) {
        this.faces.of(target, targetName)
                .completeOnTimeout(SkinFace.defaultFor(target), 2, java.util.concurrent.TimeUnit.SECONDS)
                .thenAccept(gesicht -> this.plugin.getServer().getScheduler().runTask(this.plugin,
                        () -> this.show.announce(gesicht, target, targetName, placer, topf)));
    }

    // -------------------------------------------------------------- Kassieren

    /**
     * Der Gejagte ist gefallen.
     *
     * <p>Zuerst wird der Topf in die Beute des Killers gebucht und gespeichert. Erst danach wird
     * versucht, ihm die Gegenstaende ins Inventar zu legen - ein Absturz dazwischen kostet
     * nichts, weil sie dann beim naechsten Start noch in seiner Beute liegen.
     */
    public void onDeath(Player victim, Player killer) {
        if (!this.plugin.settings().bountyEnabled() || this.data.isLocked()) {
            return;
        }
        Bounty topf = this.data.pot(victim.getUniqueId());
        if (topf == null || topf.isEmpty() || killer == null) {
            return;
        }
        long jetzt = System.currentTimeMillis();
        String betrag = reward(topf);

        switch (BountyRules.check(topf, killer.getUniqueId(), jetzt,
                this.plugin.settings().bountyClaimCooldown())) {
            case SELBST_EINGEZAHLT -> {
                this.plugin.send(killer, Messages.KOPFGELD_SELBST_EINGEZAHLT,
                        Placeholder.unparsed("name", victim.getName()));
                return;
            }
            case KILLER_GESPERRT -> {
                this.plugin.send(killer, Messages.KOPFGELD_KILLER_GESPERRT,
                        Placeholder.unparsed("name", victim.getName()),
                        Placeholder.unparsed("rest", Zeit.kurz(
                                this.plugin.settings().bountyClaimCooldown())));
                return;
            }
            case KEIN_TOPF -> {
                return;
            }
            case AUSZAHLEN -> {
                // weiter unten
            }
        }

        if (!this.data.payout(victim.getUniqueId(), killer.getUniqueId(), killer.getName(), jetzt)) {
            this.plugin.getLogger().severe("Kopfgeld auf " + victim.getName()
                    + " konnte nicht ausgezahlt werden - der Topf bleibt stehen");
            return;
        }

        this.plugin.send(killer, Messages.KOPFGELD_KASSIERT,
                Placeholder.unparsed("wert", betrag),
                Placeholder.unparsed("name", victim.getName()));
        this.plugin.send(victim, Messages.KOPFGELD_OPFER,
                Placeholder.unparsed("name", killer.getName()));
        if (this.plugin.settings().bountyBroadcast()) {
            this.plugin.getServer().broadcast(Messages.mm(
                    Messages.PREFIX + Messages.KOPFGELD_KASSIERT_BROADCAST,
                    Placeholder.unparsed("killer", killer.getName()),
                    Placeholder.unparsed("name", victim.getName()),
                    Placeholder.unparsed("wert", betrag)));
        }
        this.plugin.effects().bountyClaimed(killer, victim, victim.getLocation());
        refreshAll(victim.getUniqueId());
        deliver(killer);
    }

    // --------------------------------------------------------------- Zustellen

    /**
     * Legt die Beute eines Spielers in sein Inventar.
     *
     * <p>Was nicht mehr hineinpasst, bleibt in der Beute liegen und laesst sich jederzeit mit
     * {@code /kopfgeld beute} abholen. Es geht dabei nichts verloren, auch wenn der Spieler
     * mitten im Vorgang die Verbindung verliert.
     */
    public void deliver(Player owner) {
        BountyData.Claim beute = this.data.claim(owner.getUniqueId());
        if (beute == null || beute.items().isEmpty()) {
            return;
        }
        if (!this.data.beginDelivery(owner.getUniqueId())) {
            return;
        }

        List<ItemStack> stapel = BountyItems.toStacks(beute.items());
        Map<Integer, ItemStack> rest = owner.getInventory()
                .addItem(stapel.toArray(new ItemStack[0]));
        owner.saveData();

        Map<Material, Integer> uebrig = new LinkedHashMap<>();
        for (ItemStack stack : rest.values()) {
            uebrig.merge(stack.getType(), stack.getAmount(), Integer::sum);
        }
        int abgeholt = BountyItems.size(beute.items()) - BountyItems.size(uebrig);

        if (!this.data.finishDelivery(owner.getUniqueId(), uebrig)) {
            this.plugin.getLogger().severe("Die Beute von " + owner.getName()
                    + " konnte nicht fortgeschrieben werden - bitte kopfgelder.yml pruefen");
            return;
        }
        if (abgeholt > 0) {
            this.plugin.send(owner, Messages.BEUTE_ABGEHOLT,
                    Placeholder.unparsed("anzahl", String.valueOf(abgeholt)));
            this.plugin.effects().cue(owner, SoundCue.MUENZEN);
        }
        if (uebrig.isEmpty()) {
            // Leer: die Kiste loest sich auf.
            this.plugin.lootBoxes().dissolve(owner.getUniqueId(),
                    beute.hasLocation() ? new org.bukkit.Location(
                            this.plugin.getServer().getWorld(beute.world()),
                            beute.x(), beute.y(), beute.z()) : owner.getLocation());
        } else {
            this.plugin.send(owner, Messages.BEUTE_INVENTAR_VOLL);
            this.plugin.lootBoxes().spawn(owner);
        }
    }

    // ---------------------------------------------------------------- Anzeige

    /** Setzt TAB-Name und Balken eines Spielers auf den aktuellen Stand. */
    public void refresh(Player player) {
        Bounty topf = this.data.pot(player.getUniqueId());
        boolean gejagt = topf != null && !topf.isEmpty();
        String betrag = gejagt ? reward(topf) : "";

        if (this.plugin.settings().bountyTabRed()) {
            if (gejagt) {
                this.previousListName.putIfAbsent(player.getUniqueId(), player.playerListName());
                player.playerListName(Messages.mm(Messages.KOPFGELD_TAB,
                        Placeholder.unparsed("name", player.getName()),
                        Placeholder.unparsed("wert", betrag)));
            } else {
                // null setzt auf den Standardnamen zurueck; hatte ein anderes Plugin einen
                // eigenen gesetzt, bekommt es seinen wieder.
                player.playerListName(this.previousListName.remove(player.getUniqueId()));
            }
        }

        BossBar balken = this.bars.get(player.getUniqueId());
        if (gejagt && this.plugin.settings().bountyBossBar()) {
            Component text = Messages.mm(Messages.KOPFGELD_BOSSBAR,
                    Placeholder.unparsed("wert", betrag));
            if (balken == null) {
                balken = BossBar.bossBar(text, 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
                this.bars.put(player.getUniqueId(), balken);
                player.showBossBar(balken);
            } else {
                balken.name(text);
            }
        } else if (balken != null) {
            player.hideBossBar(balken);
            this.bars.remove(player.getUniqueId());
        }
    }

    /** Frischt die Anzeige eines Spielers auf, sofern er online ist. */
    public void refreshAll(UUID target) {
        Player player = this.plugin.getServer().getPlayer(target);
        if (player != null) {
            refresh(player);
        }
    }

    /** Beim Verlassen: nichts stehen lassen, was den Spieler ueberdauert. */
    public void forget(Player player) {
        BossBar balken = this.bars.remove(player.getUniqueId());
        if (balken != null) {
            player.hideBossBar(balken);
        }
        this.previousListName.remove(player.getUniqueId());
        this.faces.forget(player.getUniqueId());
    }

    /** Beim Herunterfahren: jeden TAB-Namen und Balken zuruecksetzen. */
    public void shutdown() {
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            BossBar balken = this.bars.get(player.getUniqueId());
            if (balken != null) {
                player.hideBossBar(balken);
            }
            if (this.previousListName.containsKey(player.getUniqueId())) {
                player.playerListName(this.previousListName.get(player.getUniqueId()));
            }
        }
        this.bars.clear();
        this.previousListName.clear();
        this.faces.shutdown();
    }

    /** Kleine Hilfe fuer Restzeiten in Meldungen. */
    static final class Zeit {
        private Zeit() {
        }

        /** Eine Dauer in Millisekunden als "4:12" oder "38s". */
        static String kurz(long millis) {
            long sekunden = Math.max(0L, millis / 1000L);
            if (sekunden < 60L) {
                return sekunden + "s";
            }
            return (sekunden / 60L) + ":" + String.format("%02d", sekunden % 60L);
        }
    }
}
