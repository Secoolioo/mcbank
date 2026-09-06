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

    /** Bis hierhin gilt ein Schuss als nah; darueber hinaus hoert man nur das Echo. */
    private static final double SCHUSS_NAH = 40.0;
    /** So weit traegt das Echo. */
    private static final double SCHUSS_FERN = 120.0;
    /** So weit hoert man die Beutekiste. */
    private static final double KISTE_HOERWEITE = 24.0;

    private final BankRankingPlugin plugin;
    /** Die geplanten Schritte laufender Choreografien, damit sie abbrechbar sind. */
    private final java.util.Set<org.bukkit.scheduler.BukkitTask> running = new java.util.HashSet<>();

    public Effects(BankRankingPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ Klang

    /**
     * Spielt einen Western-Klang fuer genau einen Spieler.
     *
     * <p>Hat er das Resourcepack, kommt der eigene Klang; sonst der Vanilla-Ersatz, notfalls
     * als Folge mehrerer Toene. Die Aufrufstellen wissen davon nichts.
     */
    public void cue(Player player, Location at, SoundCue cue) {
        if (!this.plugin.settings().bountyEnabled()) {
            return;
        }
        float laut = (float) this.plugin.settings().bountyVolume();
        if (laut <= 0.0f) {
            return;
        }
        if (this.plugin.hasPack(player)) {
            player.playSound(at, cue.key(), cue.category(), cue.volume() * laut, cue.pitch());
            return;
        }
        for (SoundCue.Note note : cue.fallback()) {
            if (note.delayTicks() == 0L) {
                player.playSound(at, note.sound(), cue.category(), note.volume() * laut, note.pitch());
            } else {
                later(note.delayTicks(), () -> {
                    if (player.isOnline()) {
                        player.playSound(at, note.sound(), cue.category(),
                                note.volume() * laut, note.pitch());
                    }
                });
            }
        }
    }

    /** Derselbe Klang am Ohr des Spielers. */
    public void cue(Player player, SoundCue cue) {
        cue(player, player.getLocation(), cue);
    }

    /**
     * Spielt einen Klang fuer alle in einem Umkreis.
     *
     * <p>Die Empfaengerliste wird hier berechnet und nicht dem Client ueberlassen: die
     * Reichweite im Resourcepack gilt nur fuer Spieler mit Pack, und dann hoerten zwei
     * Spieler nebeneinander unterschiedlich weit.
     */
    public void cueNearby(Location at, double radius, SoundCue cue) {
        if (at.getWorld() == null) {
            return;
        }
        for (Player player : at.getWorld().getNearbyPlayers(at, radius)) {
            cue(player, at, cue);
        }
    }

    /**
     * Plant einen Schritt und merkt sich seinen Griff.
     *
     * <p>Beim Herunterfahren ist der Zeitplaner gesperrt. Anders als beim Fortschrittsbalken
     * gibt es hier keinen Sofortweg - eine Fanfare beim Abschalten des Plugins nuetzt
     * niemandem, sie entfaellt.
     */
    private void later(long delayTicks, Runnable step) {
        if (!this.plugin.isEnabled()) {
            return;
        }
        this.running.add(this.plugin.getServer().getScheduler()
                .runTaskLater(this.plugin, step, delayTicks));
    }

    // --------------------------------------------------------------- Kopfgeld

    /**
     * Der Gejagte erfaehrt sein Schicksal - und zwar anders als alle anderen.
     *
     * <p>Ohne das lieste er es aus derselben Chatzeile wie der Rest. Er soll sich gejagt
     * fuehlen, nicht darueber informiert werden.
     */
    public void hunted(Player hunted) {
        cue(hunted, SoundCue.GEJAGT);
        if (this.plugin.settings().effectParticles()) {
            Location at = hunted.getLocation().add(0.0, 1.1, 0.0);
            hunted.getWorld().spawnParticle(Particle.FLASH, at, 1);
            hunted.getWorld().spawnParticle(Particle.SMOKE, at, 30, 0.8, 0.5, 0.8, 0.01);
        }
    }

    /**
     * Der Gejagte ist gefallen.
     *
     * <p>Der Schuss und sein Echo erreichen niemals denselben Spieler: wer nah dran ist, hoert
     * den trockenen Knall, wer weiter weg steht, nur die Rueckwuerfe - und die zwei Zehntel
     * spaeter, weil sich das als Entfernung liest.
     */
    public void bountyClaimed(Player killer, Player victim, Location deathAt) {
        for (Player player : deathAt.getWorld().getNearbyPlayers(deathAt, SCHUSS_FERN)) {
            double abstand = player.getLocation().distance(deathAt);
            if (abstand <= SCHUSS_NAH) {
                cue(player, deathAt, SoundCue.SCHUSS);
            } else {
                later(4L, () -> {
                    if (player.isOnline()) {
                        cue(player, player.getLocation(), SoundCue.SCHUSS_FERN);
                    }
                });
            }
        }
        if (this.plugin.settings().effectParticles()) {
            deathAt.getWorld().spawnParticle(Particle.FLASH, deathAt, 1);
            deathAt.getWorld().spawnParticle(Particle.SMOKE, deathAt, 40, 0.6, 0.6, 0.6, 0.02);
        }
        // Die Mundharmonika kommt spaeter: sie ueberschneidet sich im Frequenzbereich mit dem
        // Muenzklimpern der Auszahlung, und acht Ticks Abstand trennen die beiden hoerbar.
        later(12L, () -> {
            if (killer.isOnline()) {
                cue(killer, SoundCue.MUNDHARMONIKA);
            }
        });
        // Und ein Horn fuer den ganzen Server: ein kassiertes Kopfgeld ist ein Ereignis,
        // das auch die angeht, die nicht dabei waren.
        later(20L, () -> {
            for (Player zuhoerer : this.plugin.getServer().getOnlinePlayers()) {
                cue(zuhoerer, SoundCue.FANFARE);
            }
        });
    }

    /** Die Beutekiste erscheint. */
    public void chestSpawned(Location at) {
        cueNearby(at, KISTE_HOERWEITE, SoundCue.MUENZEN);
        if (this.plugin.settings().effectParticles() && at.getWorld() != null) {
            at.getWorld().spawnParticle(Particle.END_ROD, at, 25, 0.3, 0.4, 0.3, 0.02);
        }
    }

    /** Die Beutekiste verschwindet. */
    public void chestGone(Location at) {
        cueNearby(at, KISTE_HOERWEITE, SoundCue.KISTE_WEG);
        if (this.plugin.settings().effectParticles() && at.getWorld() != null) {
            at.getWorld().spawnParticle(Particle.SMOKE, at, 20, 0.3, 0.3, 0.3, 0.01);
        }
    }

    /** Ein Klick im Kopfgeld-Fenster. */
    public void bountyClick(Player player) {
        cue(player, SoundCue.HAHN);
    }

    /** Blaettern im Kopfgeld-Fenster. */
    public void bountyPage(Player player) {
        cue(player, SoundCue.SPOREN);
    }

    /** Beim Herunterfahren und beim Neuladen: keine Choreografie laeuft weiter. */
    public void cancelAll() {
        for (org.bukkit.scheduler.BukkitTask task : this.running) {
            task.cancel();
        }
        this.running.clear();
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
