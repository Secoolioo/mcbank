package de.secoolio.bankranking;

import java.util.List;

/**
 * Die Ereignisnamen der eigenen Klaenge, ohne jede Bukkit-Abhaengigkeit.
 *
 * <p>Sie stehen getrennt von {@link SoundCue}, weil dort Vanilla-Klaenge als Ersatz
 * danebenstehen und die Aufzaehlung damit nur mit laufendem Server geladen werden kann. Ein
 * Test, der prueft, ob jeder Klang auch in der {@code sounds.json} des Packs angemeldet ist,
 * waere so nicht moeglich - und genau der fehlte, als alle eigenen Klaenge stumm blieben.
 *
 * <p>Ein einziger unangemeldeter Eintrag laesst den Client die gesamte {@code sounds.json}
 * verwerfen. Dann ist nicht ein Klang stumm, sondern alle.
 */
final class SoundNames {

    static final String PLAKAT = "kopfgeld.plakat";
    static final String NAGEL = "kopfgeld.nagel";
    static final String GEJAGT = "kopfgeld.gejagt";
    static final String SCHUSS = "kopfgeld.schuss";
    static final String SCHUSS_FERN = "kopfgeld.schuss_fern";
    static final String MUNDHARMONIKA = "kopfgeld.mundharmonika";
    static final String MUENZEN = "kopfgeld.muenzen";
    static final String HAHN = "kopfgeld.hahn";
    static final String SPOREN = "kopfgeld.sporen";
    static final String KISTE_WEG = "kopfgeld.kiste_weg";
    static final String FANFARE = "kopfgeld.fanfare";

    /** Alle Ereignisnamen, in der Reihenfolge der Aufzaehlung. */
    static final List<String> ALLE = List.of(PLAKAT, NAGEL, GEJAGT, SCHUSS, SCHUSS_FERN,
            MUNDHARMONIKA, MUENZEN, HAHN, SPOREN, KISTE_WEG, FANFARE);

    private SoundNames() {
    }
}
