package de.secoolio.bankranking;

import java.io.File;
import java.util.ArrayList;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Einstiegspunkt: haelt die Bausteine zusammen und regelt Start, Reload und Herunterfahren. */
public final class BankRankingPlugin extends JavaPlugin {

    /** Wie oft der Inhalt der Rangliste neu berechnet wird (600 Ticks = 30 Sekunden). */
    private static final long REFRESH_TICKS = 600L;
    /** Wie oft geprueft wird, ob ein anderes Plugin die Rangliste verdraengt hat (1 Sekunde). */
    private static final long GUARD_TICKS = 20L;

    private NamespacedKey npcKey;
    private Settings settings;
    private Scorer scorer;
    private PlayerData playerData;
    private NpcManager npcs;
    private BankWindows windows;
    private RankProgressBar progressBar;
    private Effects effects;
    private RankingBoard ranking;
    private ResourcePacks packs;
    private BountyData bountyData;
    private BountyService bounties;
    private LootBoxes lootBoxes;

    @Override
    public void onEnable() {
        this.npcKey = new NamespacedKey(this, "npc_id");

        saveDefaultConfig();
        loadSettings();

        this.playerData = new PlayerData(new File(getDataFolder(), "players.yml"), getLogger());
        this.playerData.load();

        this.npcs = new NpcManager(this);
        this.windows = new BankWindows(this);
        this.progressBar = new RankProgressBar(this);
        this.effects = new Effects(this);
        this.npcs.load();

        this.ranking = new RankingBoard(this);

        this.bountyData = new BountyData(new File(getDataFolder(), "kopfgelder.yml"), getLogger());
        this.bountyData.load();
        this.bounties = new BountyService(this, this.bountyData);
        this.lootBoxes = new LootBoxes(this);

        // Das Resourcepack ist Beiwerk: scheitert es, laeuft die Bank unveraendert weiter und
        // das Kopfgeld zeigt die Sparfassung. Es gibt aber immer ein Objekt - ein fehlendes
        // war frueher die eine Stelle, an der ein Startproblem in dauerhaftes Schweigen fuer
        // jeden Spieler umschlug, waehrend die Ursache nur einmal beim Start im Log stand.
        this.packs = ResourcePacks.start(this);

        getServer().getPluginManager().registerEvents(new BankListener(this), this);
        getServer().getPluginManager().registerEvents(this.lootBoxes, this);
        getServer().getPluginManager().registerEvents(this.packs, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> {
                    BankCommands.register(this, event.registrar());
                    BountyCommands.register(this, event.registrar());
                });

        getServer().getScheduler().runTask(this, () -> {
            this.npcs.ensureAll();
            for (Player player : getServer().getOnlinePlayers()) {
                this.ranking.enable(player);
                this.bounties.refresh(player);
            }
            this.lootBoxes.restoreAll();
        });
        if (this.packs.delivery() != PackStatus.Delivery.ABGESCHALTET) {
            // Der Selbsttest holt das Pack ueber die eigene Adresse ab. Er laeuft neben dem
            // Serverpuls, weil er auf das Netz wartet, und beantwortet vor dem ersten Spieler
            // die Frage, ob die Adresse ueberhaupt erreichbar ist.
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                ResourcePacks.SelfTest ergebnis = this.packs.selfTest();
                if (ergebnis.ok()) {
                    getLogger().info("Selbsttest des Resourcepacks bestanden: "
                            + ergebnis.text());
                } else {
                    // Der Selbsttest geht nur an den Server selbst. Er beweist also nie, dass
                    // ein Mitspieler durchkommt - aber wenn schon er scheitert, kommt sicher
                    // niemand durch, und das gehoert deutlich ins Log.
                    getLogger().warning("Selbsttest des Resourcepacks fehlgeschlagen: "
                            + ergebnis.text());
                }
                getServer().getScheduler().runTask(this, () -> {
                    for (Player player : getServer().getOnlinePlayers()) {
                        this.packs.send(player);
                    }
                });
            });
        }
        getServer().getScheduler().runTaskTimer(this, () -> this.ranking.refreshAll(), REFRESH_TICKS, REFRESH_TICKS);
        getServer().getScheduler().runTaskTimer(this, () -> this.ranking.guard(), GUARD_TICKS, GUARD_TICKS);

        getLogger().info("Konfiguration geladen: " + this.settings.summaryLine());
        getLogger().info(this.playerData.size() + " Spieler-Konten und " + this.npcs.count() + " Bank-NPCs geladen");
        getLogger().info(this.bountyData.active().size() + " laufende Kopfgelder und "
                + this.bountyData.claims().size() + " offene Beutefaecher geladen");
        getLogger().info("BankRanking v" + getPluginMeta().getVersion() + " aktiviert");
    }

    @Override
    public void onDisable() {
        // Beim Herunterfahren erreicht das Schliessen-Ereignis das Plugin nicht mehr von allein,
        // und ein Scheduler ist hier nicht mehr benutzbar: also synchron zurueckgeben und schliessen.
        // Jedes Fenster schliessen, nicht nur das Abgabe-Fenster: ein offen gebliebenes Anzeigefenster
        // waere nach dem Abschalten des Ereignis-Empfaengers eine ganz normale Kiste voller Deko.
        for (Player player : new ArrayList<>(getServer().getOnlinePlayers())) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof BankWindow window) {
                window.onClosed(player, false);
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        }
        if (this.playerData != null) {
            this.playerData.saveIfDirty();
        }
        if (this.progressBar != null) {
            // Direkt und ohne Scheduler: der ist beim Herunterfahren gesperrt.
            this.progressBar.hideAll();
        }
        if (this.ranking != null) {
            this.ranking.shutdown();
        }
        if (this.effects != null) {
            this.effects.cancelAll();
        }
        if (this.lootBoxes != null) {
            // Die Anzeige-Entitaeten sind nicht persistent, wuerden ein Neuladen des Plugins
            // aber als verwaiste Kisten ueberleben.
            this.lootBoxes.shutdown();
        }
        if (this.bounties != null) {
            // Roter TAB-Name und Balken duerfen das Plugin nicht ueberleben.
            this.bounties.shutdown();
        }
        if (this.bountyData != null) {
            this.bountyData.saveIfDirty();
        }
        if (this.packs != null) {
            this.packs.stop();
        }

    }

    /**
     * Liest config.yml und players.yml neu und wendet die Einstellungen auf NPCs und Sidebar an.
     *
     * @return false, wenn players.yml nicht neu geladen werden konnte
     */
    public boolean reload() {
        reloadConfig();
        loadSettings();
        boolean dataOk = this.playerData.reload();
        boolean bountyOk = this.bountyData.reload();
        this.npcs.applySettings();
        this.ranking.reapply();
        if (!this.settings.bossBarEnabled()) {
            this.progressBar.hideAll();
        }
        // Erst alles zuruecksetzen, dann aus den frischen Daten neu setzen: sonst bliebe ein
        // Name rot, dessen Topf von Hand aus der Datei entfernt wurde.
        this.effects.cancelAll();
        // Nur die Anzeige zuruecksetzen, nicht den Dienst beenden - sonst waere der
        // Thread-Pool fuer die Gesichter nach einem Neuladen tot.
        this.bounties.resetDisplay();
        this.lootBoxes.shutdown();
        for (Player player : getServer().getOnlinePlayers()) {
            this.bounties.refresh(player);
        }
        this.lootBoxes.restoreAll();
        getLogger().info("Neu geladen: " + this.settings.summaryLine());
        return dataOk && bountyOk;
    }

    private void loadSettings() {
        this.settings = Settings.load(getConfig(), getLogger());
        this.scorer = new Scorer(this.settings,
                new CategoryClassifier(this.settings.categoryOverrides(), Material::isEdible));
    }

    public void send(CommandSender receiver, String message, TagResolver... resolvers) {
        receiver.sendMessage(Messages.mm(Messages.PREFIX + message, resolvers));
    }

    public NamespacedKey npcKey() {
        return this.npcKey;
    }

    public Settings settings() {
        return this.settings;
    }

    public Scorer scorer() {
        return this.scorer;
    }

    /** Die Marktsaettigung eines Spielers als unveraenderliche Sicht, mit Zeitverfall. */
    public java.util.Map<org.bukkit.Material, Double> saturationView(org.bukkit.entity.Player player) {
        return this.playerData.saturationView(player.getUniqueId(), this.settings.saturationHalfLife());
    }

    public PlayerData playerData() {
        return this.playerData;
    }

    public BankWindows windows() {
        return this.windows;
    }

    public RankProgressBar progressBar() {
        return this.progressBar;
    }

    public Effects effects() {
        return this.effects;
    }

    public NpcManager npcs() {
        return this.npcs;
    }

    /** Die Auslieferung des Resourcepacks, oder {@code null} wenn sie nicht zustande kam. */
    public ResourcePacks packs() {
        return this.packs;
    }

    /** Hat dieser Spieler das Resourcepack geladen? Ohne Pack gilt die Sparfassung. */
    public boolean hasPack(Player player) {
        return this.packs.has(player);
    }

    public BountyService bounties() {
        return this.bounties;
    }

    public LootBoxes lootBoxes() {
        return this.lootBoxes;
    }

    public RankingBoard ranking() {
        return this.ranking;
    }
}
