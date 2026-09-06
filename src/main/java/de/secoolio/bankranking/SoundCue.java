package de.secoolio.bankranking;

import java.util.List;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;

/**
 * Ein Western-Klang in zwei Fassungen: der eigene aus dem Resourcepack und ein Vanilla-Ersatz.
 *
 * <p>Beide stehen hier nebeneinander, damit keine Aufrufstelle wissen muss, ob ein Spieler das
 * Pack geladen hat. Sie nennt den Klang, {@link Effects} entscheidet.
 *
 * <p>Alles laeuft ueber {@link SoundCategory#MASTER}. Der erste Entwurf legte die musikalischen
 * Klaenge auf RECORDS, damit sie sich einzeln herunterdrehen lassen - das war ein Fehler:
 * RECORDS ist der Jukebox-Regler, und wer den auf null stehen hat, hoerte von der Ankuendigung
 * nichts. Eine Ankuendigung, die sich lautlos wegdrehen laesst, ist keine. Wer es leiser mag,
 * stellt {@code kopfgeld.lautstaerke} in der config.yml herunter - das gilt dann fuer alle
 * und ist nachvollziehbar.
 */
enum SoundCue {

    /** Blechhorn zur Plakat-Einblendung. */
    PLAKAT(SoundNames.PLAKAT, SoundCategory.MASTER, 1.0f, 1.0f,
            new Note(Sound.ITEM_GOAT_HORN_SOUND_0, 0.7f, 0.8f, 0L),
            new Note(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.5f, 0.55f, 0L)),

    /** Der Nagel, mit dem das Plakat angeschlagen wird. */
    NAGEL(SoundNames.NAGEL, SoundCategory.MASTER, 0.9f, 1.0f,
            new Note(Sound.BLOCK_ANVIL_LAND, 0.35f, 1.9f, 0L)),

    /** Klapperschlange - hoert nur der Gejagte. */
    GEJAGT(SoundNames.GEJAGT, SoundCategory.MASTER, 1.0f, 1.0f,
            new Note(Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.6f, 0.9f, 0L)),

    /** Trockener Schuss in der Naehe. */
    SCHUSS(SoundNames.SCHUSS, SoundCategory.MASTER, 1.0f, 1.0f,
            new Note(Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.7f, 0L),
            new Note(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.5f, 0.6f, 0L)),

    /** Derselbe Schuss aus der Ferne, nur die Rueckwuerfe. */
    SCHUSS_FERN(SoundNames.SCHUSS_FERN, SoundCategory.MASTER, 0.8f, 1.0f,
            new Note(Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST_FAR, 0.7f, 0.8f, 0L)),

    /**
     * Mundharmonika zur Auszahlung.
     *
     * <p>Vanilla kann keine Melodie in einem Klang, deshalb drei Notenblock-Toene. Die
     * Tonhoehen sind gerechnet, nicht geraten: ein Notenblock steht bei Tonhoehe 1,0 auf
     * Fis4, und D5, B4 und F4 - der absteigende B-Dur-Dreiklang, der klassische Western-
     * Abgang - liegen acht, vier und minus einen Halbton davon entfernt.
     */
    MUNDHARMONIKA(SoundNames.MUNDHARMONIKA, SoundCategory.MASTER, 1.0f, 1.0f,
            new Note(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.8f, 1.5874f, 0L),
            new Note(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.8f, 1.2599f, 5L),
            new Note(Sound.BLOCK_NOTE_BLOCK_FLUTE, 0.8f, 0.9439f, 10L)),

    /** Muenzklimpern an der Beutekiste. */
    MUENZEN(SoundNames.MUENZEN, SoundCategory.MASTER, 1.0f, 1.0f,
            new Note(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6f, 1.5f, 0L),
            new Note(Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.45f, 1.8f, 3L)),

    /** Revolverhahn beim Auswaehlen im Fenster. */
    HAHN(SoundNames.HAHN, SoundCategory.MASTER, 1.0f, 1.0f,
            new Note(Sound.ITEM_CROSSBOW_LOADING_END, 0.6f, 1.4f, 0L)),

    /** Sporen beim Blaettern. */
    SPOREN(SoundNames.SPOREN, SoundCategory.MASTER, 1.0f, 1.0f,
            new Note(Sound.BLOCK_CHAIN_HIT, 0.5f, 1.7f, 0L)),

    /** Die Beutekiste loest sich auf. */
    KISTE_WEG(SoundNames.KISTE_WEG, SoundCategory.MASTER, 1.0f, 1.0f,
            new Note(Sound.BLOCK_BARREL_CLOSE, 0.6f, 0.8f, 0L)),

    /** Blechhorn, wenn ein Kopfgeld kassiert wurde - das hoert der ganze Server. */
    FANFARE(SoundNames.FANFARE, SoundCategory.MASTER, 0.85f, 1.0f,
            new Note(Sound.ITEM_GOAT_HORN_SOUND_1, 0.7f, 0.9f, 0L),
            new Note(Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.45f, 0.6f, 0L));

    /** Ein Ton des Vanilla-Ersatzes, mit eigener Verzoegerung in Ticks. */
    record Note(Sound sound, float volume, float pitch, long delayTicks) {
    }

    private final String event;
    private final SoundCategory category;
    private final float volume;
    private final float pitch;
    private final List<Note> fallback;

    SoundCue(String event, SoundCategory category, float volume, float pitch, Note... fallback) {
        this.event = event;
        this.category = category;
        this.volume = volume;
        this.pitch = pitch;
        this.fallback = List.of(fallback);
    }

    /** Die vollstaendige Ereignis-Kennung fuer die Zeichenketten-Fassung von playSound. */
    String key() {
        return "bankranking:" + this.event;
    }

    SoundCategory category() {
        return this.category;
    }

    float volume() {
        return this.volume;
    }

    float pitch() {
        return this.pitch;
    }

    List<Note> fallback() {
        return this.fallback;
    }
}
