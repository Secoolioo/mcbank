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
    private RankingBoard ranking;

    @Override
    public void onEnable() {
        this.npcKey = new NamespacedKey(this, "npc_id");

        saveDefaultConfig();
        loadSettings();

        this.playerData = new PlayerData(new File(getDataFolder(), "players.yml"), getLogger());
        this.playerData.load();

        this.npcs = new NpcManager(this);
        this.npcs.load();

        this.ranking = new RankingBoard(this);

        getServer().getPluginManager().registerEvents(new BankListener(this), this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
                event -> BankCommands.register(this, event.registrar()));

        getServer().getScheduler().runTask(this, () -> {
            this.npcs.ensureAll();
            for (Player player : getServer().getOnlinePlayers()) {
                this.ranking.enable(player);
            }
        });
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
        if (this.playerData != null) {
            for (Player player : new ArrayList<>(getServer().getOnlinePlayers())) {
                if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof BankGui gui) {
                    gui.refund(player, false);
                    player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
                }
            }
            this.playerData.saveIfDirty();
        }
        if (this.ranking != null) {
            this.ranking.shutdown();
        }
    }

    /** Liest config.yml und players.yml neu und wendet die Einstellungen auf NPCs und Sidebar an. */
    public void reload() {
        reloadConfig();
        loadSettings();
        this.playerData.reload();
        this.npcs.applySettings();
        this.ranking.reapply();
        getLogger().info("Neu geladen: " + this.settings.summaryLine());
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

    public PlayerData playerData() {
        return this.playerData;
    }

    public NpcManager npcs() {
        return this.npcs;
    }

    public RankingBoard ranking() {
        return this.ranking;
    }
}
