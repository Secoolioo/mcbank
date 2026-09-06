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

        // Das Resourcepack ist Beiwerk: scheitert es, laeuft die Bank unveraendert weiter und
        // das Kopfgeld zeigt spaeter die Sparfassung.
        this.packs = ResourcePacks.start(this);

        getServer().getPluginManager().registerEvents(new BankListener(this), this);
        if (this.packs != null) {
            getServer().getPluginManager().registerEvents(this.packs, this);
        }
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> BankCommands.register(this, event.registrar()));

        getServer().getScheduler().runTask(this, () -> {
            this.npcs.ensureAll();
            for (Player player : getServer().getOnlinePlayers()) {
                this.ranking.enable(player);
            }
        });
        if (this.packs != null) {
            // Der Selbsttest holt das Pack ueber die eigene Adresse ab. Er laeuft neben dem
            // Serverpuls, weil er auf das Netz wartet, und beantwortet vor dem ersten Spieler
            // die Frage, ob die Adresse ueberhaupt erreichbar ist.
            getServer().getScheduler().runTaskAsynchronously(this, () -> {
                if (this.packs.selfTest()) {
                    getLogger().info("Selbsttest des Resourcepacks bestanden");
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
        this.npcs.applySettings();
        this.ranking.reapply();
        if (!this.settings.bossBarEnabled()) {
            this.progressBar.hideAll();
        }
        getLogger().info("Neu geladen: " + this.settings.summaryLine());
        return dataOk;
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
        return this.packs != null && this.packs.has(player);
    }

    public RankingBoard ranking() {
        return this.ranking;
    }
}
