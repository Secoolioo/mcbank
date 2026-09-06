package de.secoolio.bankranking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Der Fortschrittsbalken am oberen Bildrand, solange ein Bank-Fenster offen ist.
 *
 * <p>Je Spieler gibt es hoechstens einen Balken. Beim Wechsel zwischen zwei Bank-Fenstern bleibt er
 * stehen: geprueft wird erst einen Tick nach dem Schliessen, ob wirklich kein Fenster mehr offen ist.
 */
public final class RankProgressBar {

    private final BankRankingPlugin plugin;
    private final Map<UUID, BossBar> bars = new HashMap<>();

    public RankProgressBar(BankRankingPlugin plugin) {
        this.plugin = plugin;
    }

    /** Zeigt den Balken an oder legt ihn an. Mehrfaches Aufrufen schadet nicht. */
    public void show(Player player) {
        if (!this.plugin.settings().bossBarEnabled()) {
            return;
        }
        BossBar bar = this.bars.computeIfAbsent(player.getUniqueId(), key -> BossBar.bossBar(
                net.kyori.adventure.text.Component.empty(), 0.0f,
                BossBar.Color.WHITE, BossBar.Overlay.NOTCHED_10));
        update(player, 0.0);
        player.showBossBar(bar);
    }

    /**
     * Schreibt Text, Farbe und Fuellstand neu.
     *
     * @param preview Punkte, die eine offene Einzahlung zusaetzlich braechte; 0 wenn keine
     */
    public void update(Player player, double preview) {
        BossBar bar = this.bars.get(player.getUniqueId());
        if (bar == null) {
            return;
        }
        double balance = this.plugin.playerData().get(player.getUniqueId());
        RankProgress progress = RankProgress.of(balance + Math.max(0.0, preview));
        bar.color(progress.rank().barColor());
        bar.progress((float) Math.min(1.0, Math.max(0.0, progress.fraction())));
        if (progress.isHighest()) {
            bar.name(Messages.mm(Messages.BOSSBAR_HOECHSTER.replace("<rang>", progress.rank().colored())));
            return;
        }
        String text = Messages.BOSSBAR_TEXT
                .replace("<rang>", progress.rank().colored())
                .replace("<naechster>", progress.next().colored());
        bar.name(Messages.mm(text,
                Placeholder.unparsed("punkte", Scorer.format(progress.have())),
                Placeholder.unparsed("ziel", Scorer.format(progress.need())),
                Placeholder.unparsed("prozent", String.valueOf(progress.percent()))));
    }

    /**
     * Blendet den Balken aus, sobald feststeht, dass kein Bank-Fenster mehr offen ist.
     * Beim Wechsel zwischen zwei Fenstern bleibt er dadurch stehen.
     */
    public void hideLater(Player player) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (!player.isOnline() || !BankWindows.isOurs(player)) {
                hide(player);
            }
        });
    }

    public void hide(Player player) {
        BossBar bar = this.bars.remove(player.getUniqueId());
        if (bar != null) {
            player.hideBossBar(bar);
        }
    }

    /** Beim Herunterfahren und beim Abschalten in der Konfiguration: alle Balken weg. */
    public void hideAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            BossBar bar = this.bars.get(player.getUniqueId());
            if (bar != null) {
                player.hideBossBar(bar);
            }
        }
        this.bars.clear();
    }
}
