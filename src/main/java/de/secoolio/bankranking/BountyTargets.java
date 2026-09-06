package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Wer sich als Ziel eines Kopfgelds anbietet.
 *
 * <p>Reine Rechnung: die Kandidatenliste und das Blaettern lassen sich damit ohne Server pruefen.
 *
 * <p>Bewusst <em>nicht</em> ueber {@code Bukkit.getOfflinePlayers()}: das liest den kompletten
 * Spielerdaten-Ordner durch und liefert Eintraege, deren Name {@code null} sein kann. Die drei
 * Quellen hier - wer online ist, wer ein Bankkonto hat, auf wem schon ein Kopfgeld liegt -
 * decken jeden ab, der auf diesem Server ueberhaupt eine Rolle spielt.
 */
final class BountyTargets {

    /** So viele Ziele passen auf eine Seite - dieselben 28 Plaetze wie im Abgabe-Fenster. */
    static final int PER_PAGE = 28;

    /**
     * @param online ob der Spieler gerade da ist; Online-Ziele stehen vorn
     */
    record Target(UUID id, String name, boolean online) {
    }

    private BountyTargets() {
    }

    /**
     * Baut die Kandidatenliste.
     *
     * @param onlineNames wer gerade online ist
     * @param accounts    wer ein Bankkonto hat (auch offline)
     * @param pots        wer bereits ausgeschrieben ist
     * @param viewer      der Betrachter; er wird weggelassen, nicht nur beim Klick abgelehnt
     */
    static List<Target> candidates(Map<UUID, String> onlineNames, Map<UUID, String> accounts,
                                   Collection<Bounty> pots, UUID viewer) {
        Map<UUID, Target> gefunden = new LinkedHashMap<>();
        onlineNames.forEach((id, name) -> gefunden.put(id, new Target(id, name, true)));
        accounts.forEach((id, name) -> gefunden.putIfAbsent(id, new Target(id, name, false)));
        for (Bounty pot : pots) {
            gefunden.putIfAbsent(pot.target(), new Target(pot.target(), pot.name(), false));
        }
        gefunden.remove(viewer);

        List<Target> liste = new ArrayList<>(gefunden.values());
        liste.sort(Comparator.comparing((Target t) -> !t.online())
                .thenComparing(Target::name, String.CASE_INSENSITIVE_ORDER));
        return liste;
    }

    /** Wie viele Seiten die Liste braucht; mindestens eine, auch wenn sie leer ist. */
    static int pageCount(int total, int perPage) {
        return Math.max(1, (total + perPage - 1) / perPage);
    }

    /**
     * Klemmt eine Seitenzahl auf den gueltigen Bereich.
     *
     * <p>Zwischen zwei Aufrufen kann die Liste geschrumpft sein - dann landet der Spieler auf
     * der letzten vorhandenen Seite statt auf einer leeren.
     */
    static int clampPage(int page, int pageCount) {
        return Math.max(0, Math.min(page, pageCount - 1));
    }

    /** Der Ausschnitt einer Seite. */
    static List<Target> page(List<Target> alle, int page, int perPage) {
        int von = Math.min(alle.size(), page * perPage);
        int bis = Math.min(alle.size(), von + perPage);
        return alle.subList(von, bis);
    }
}
