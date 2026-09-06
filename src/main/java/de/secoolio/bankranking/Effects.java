package de.secoolio.bankranking;

import java.time.Duration;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

/**
 * Das Beiwerk einer Einzahlung: Klang, Partikel, Bildschirmtext und die Meldung an alle beim
 * Rangaufstieg. Jeder Teil laesst sich in der Konfiguration abschalten.
 */
public final class Effects {

    /** Ab so vielen Punkten kommt eine zweite Partikelschicht dazu. */
    private static final double SPARKLE_FROM = 100.0;
    /** Ab so vielen Punkten gibt es Feuerwerk. */
    private static final double FIREWORK_FROM = 1000.0;

    private final BankRankingPlugin plugin;

    public Effects(BankRankingPlugin plugin) {
        this.plugin = plugin;
    }

    /** Nach einer erfolgreich gebuchten Einzahlung. */
    public void deposit(Player player, double points, double balance) {
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.7f, 1.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                SoundCategory.MASTER, 0.9f, 1.2f);

        if (this.plugin.settings().effectTitle()) {
            Rank rank = Rank.of(balance);
            player.showTitle(Title.title(
                    Messages.mm(Messages.TITEL_EINZAHLUNG,
                            Placeholder.unparsed("punkte", Scorer.format(points))),
                    Messages.mm(Messages.UNTERTITEL_EINZAHLUNG.replace("<rang>", rank.colored()),
                            Placeholder.unparsed("gesamt", Scorer.format(balance))),
                    Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1800),
                            Duration.ofMillis(400))));
        }

        if (!this.plugin.settings().effectParticles()) {
            return;
        }
        Location at = player.getLocation().add(0.0, 1.1, 0.0);
        player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, at, 20, 0.5, 0.6, 0.5);
        if (points >= SPARKLE_FROM) {
            player.getWorld().spawnParticle(Particle.ENCHANT, at, 30, 0.5, 0.5, 0.5);
        }
        if (points >= FIREWORK_FROM) {
            player.getWorld().spawnParticle(Particle.FIREWORK, at, 25, 0.4, 0.5, 0.4);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE,
                    SoundCategory.MASTER, 0.6f, 1.2f);
        }
    }

    /**
     * Beim Aufstieg in einen hoeheren Rang.
     *
     * @param points die Punkte dieser Einzahlung; sie wandern in die Aktionsleiste, weil der
     *               Bildschirmtext dem Aufstieg gehoert
     */
    public void rankUp(Player player, Rank rank, double points) {
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                SoundCategory.MASTER, 0.8f, 1.0f);
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, SoundCategory.MASTER, 0.5f, 1.2f);

        if (this.plugin.settings().effectTitle()) {
            player.showTitle(Title.title(
                    Messages.mm(Messages.TITEL_AUFSTIEG),
                    Messages.mm(Messages.UNTERTITEL_AUFSTIEG.replace("<rang>", rank.colored())),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2500),
                            Duration.ofMillis(600))));
            player.sendActionBar(Messages.mm(Messages.AKTIONSLEISTE_PUNKTE,
                    Placeholder.unparsed("punkte", Scorer.format(points))));
        }

        if (this.plugin.settings().effectParticles()) {
            Location at = player.getLocation().add(0.0, 1.0, 0.0);
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at, 40, 0.6, 0.8, 0.6);
            player.getWorld().spawnParticle(Particle.FLASH, at, 1);
        }

        if (this.plugin.settings().rankUpBroadcast()) {
            this.plugin.getServer().broadcast(Messages.mm(
                    Messages.RANG_BROADCAST.replace("<rang>", rank.colored()),
                    Placeholder.unparsed("name", player.getName())));
        }
    }

    /** Wenn eine Abgabe abgelehnt wurde. */
    public void deny(Player player) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.7f, 1.0f);
    }
}
