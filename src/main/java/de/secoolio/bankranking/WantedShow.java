package de.secoolio.bankranking;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/**
 * Die Ankuendigung eines neuen Kopfgelds: Plakat, Klang und Chatzeile.
 *
 * <p>Zwei Fassungen derselben Sache. Wer das Resourcepack hat, sieht ein bildschirmfuellendes
 * Plakat mit dem echten Gesicht des Gejagten und hoert den eigenen Klang. Wer es nicht hat,
 * bekommt Titel und Untertitel in der gewoehnlichen Schrift und einen Vanilla-Klang. Die
 * Chatzeile geht an alle und ist gleich - sie traegt das Gesicht als Vanilla-Objekt und bleibt
 * stehen, wenn das Plakat laengst verschwunden ist.
 *
 * <p>Die Zeitachse folgt dem mitgelieferten Klang: er ist still bis 0,25 s, schlaegt dann ein
 * und klingt bis 3,8 s aus. Das Plakat wird deshalb ueber 250 ms eingeblendet - es steht genau
 * auf dem Einschlag -, haelt 2,6 s und verschwindet mit dem Nachhall.
 */
final class WantedShow {

    /** Einblenden, Stehen, Ausblenden - abgestimmt auf den Klang. */
    private static final Title.Times TIMES = Title.Times.times(
            Duration.ofMillis(250), Duration.ofMillis(2600), Duration.ofMillis(950));

    private final BankRankingPlugin plugin;
    private final PackWantedPoster poster;

    private long lastShown;

    private WantedShow(BankRankingPlugin plugin, PackWantedPoster poster) {
        this.plugin = plugin;
        this.poster = poster;
    }

    /**
     * Laedt die Schriftmasse aus dem Jar.
     *
     * <p>Scheitert das, gibt es kein Plakat, aber weiterhin Titel und Chatzeile: das Kopfgeld
     * darf nicht an einer fehlenden Grafik haengen.
     */
    static WantedShow create(BankRankingPlugin plugin) {
        try (InputStream in = PackFont.class.getResourceAsStream(PackFont.RESOURCE)) {
            if (in == null) {
                plugin.getLogger().warning("Die Schriftmasse des Plakats fehlen im Jar - "
                        + "es gibt nur die Sparfassung");
                return new WantedShow(plugin, null);
            }
            return new WantedShow(plugin, new PackWantedPoster(PackFont.load(in)));
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Die Schriftmasse des Plakats sind unbrauchbar ("
                    + e.getMessage() + ") - es gibt nur die Sparfassung");
            return new WantedShow(plugin, null);
        }
    }

    /**
     * Zeigt allen Online-Spielern das Plakat.
     *
     * @param face       das Gesicht des Gejagten
     * @param target     seine Kennung; er muss nicht anwesend sein
     * @param targetName sein Name
     * @param placer     wer ausgesetzt hat
     * @param pot        der Topf, aus dem die Belohnung abgelesen wird
     */
    void announce(SkinFace face, java.util.UUID target, String targetName, String placer,
                  Bounty pot) {
        String amount = this.plugin.bounties().reward(pot);
        Settings settings = this.plugin.settings();
        long jetzt = System.currentTimeMillis();
        boolean zeigen = settings.bountyPoster()
                && jetzt - this.lastShown >= settings.bountyPosterGap();
        if (zeigen) {
            this.lastShown = jetzt;
        }

        java.util.List<Component> chat = chatLines(target, targetName, amount, placer);
        Component titelSpar = Messages.mm(Messages.KOPFGELD_TITEL_TEXT);
        Component unterSpar = Messages.mm(Messages.KOPFGELD_UNTERTITEL_TEXT,
                Placeholder.unparsed("name", targetName),
                Placeholder.unparsed("wert", amount));
        Component plakat = this.poster == null || face == null
                ? null : this.poster.render(face, targetName, BountyItems.loot(pot.total()));

        for (Player zuschauer : this.plugin.getServer().getOnlinePlayers()) {
            chat.forEach(zuschauer::sendMessage);
            if (zeigen) {
                boolean mitPack = this.plugin.hasPack(zuschauer);
                if (mitPack && plakat != null) {
                    // Das Plakat traegt bereits Name und Belohnung; der Untertitel bliebe sonst
                    // mitten im Gesicht stehen, weil Vanilla ihn fest auf halber Hoehe zeichnet.
                    zuschauer.showTitle(Title.title(plakat, Component.empty(), TIMES));
                } else {
                    zuschauer.showTitle(Title.title(titelSpar, unterSpar, TIMES));
                }
            }
            // Der Klang haengt ausdruecklich NICHT an der Plakat-Sperre. Im ersten Entwurf tat
            // er das, und dadurch war jedes zweite Kopfgeld innerhalb der Sperrfrist voellig
            // stumm - genau das Bild von "der Sound fehlt". Kommt das Plakat nicht, gibt es
            // wenigstens den Nagel: kurz, leise, aber unueberhoerbar.
            this.plugin.effects().cue(zuschauer, zeigen ? SoundCue.PLAKAT : SoundCue.NAGEL);
        }
        if (zeigen) {
            // Der Nagel faellt in die Einblendung: das Plakat bekommt damit einen Anschlag,
            // den die anschwellende Fanfare allein nicht hat.
            this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
                for (Player zuschauer : this.plugin.getServer().getOnlinePlayers()) {
                    this.plugin.effects().cue(zuschauer, SoundCue.NAGEL);
                }
            }, 6L);
        }
    }

    /**
     * Die Chatzeile mit dem echten Gesicht.
     *
     * <p>Der Tag {@code <head:...>} ist Vanilla und braucht kein Resourcepack - jeder sieht
     * hier also das richtige Gesicht, auch wer das Pack abgelehnt hat.
     */
    private java.util.List<Component> chatLines(java.util.UUID target, String targetName,
                                                String amount, String placer) {
        // Das Gesicht kommt als Vanilla-Objekt: dafuer braucht es kein Resourcepack, jeder
        // sieht es - auch wer das Pack abgelehnt hat.
        String kopf = "<head:'" + target + "'>";
        java.util.List<Component> zeilen = new java.util.ArrayList<>();
        for (String vorlage : Messages.KOPFGELD_PLAKAT_CHAT) {
            zeilen.add(Messages.mm(vorlage.replace("<kopf>", kopf),
                    Placeholder.unparsed("name", targetName),
                    Placeholder.unparsed("wert", amount),
                    Placeholder.unparsed("von", placer)));
        }
        return zeilen;
    }


    /** Nur fuer die Verwaltung: gibt es ueberhaupt ein Plakat? */
    boolean hasPoster() {
        return this.poster != null;
    }
}
