package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prueft die Kandidatenliste und das Blaettern. */
class BountyTargetsTest {

    private static final UUID ICH = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CARLA = UUID.randomUUID();

    private static List<BountyTargets.Target> liste(Map<UUID, String> online,
                                                    Map<UUID, String> konten) {
        return BountyTargets.candidates(online, konten, List.of(), ICH);
    }

    @Test
    @DisplayName("Der Betrachter steht nicht in seiner eigenen Auswahl")
    void viewerRemoved() {
        List<BountyTargets.Target> ziele = liste(Map.of(ICH, "Ich", ALEX, "Alex"), Map.of());
        assertEquals(1, ziele.size());
        assertEquals("Alex", ziele.get(0).name());
    }

    @Test
    @DisplayName("Online-Spieler stehen vorn, danach wird alphabetisch sortiert")
    void onlineFirst() {
        List<BountyTargets.Target> ziele = liste(
                Map.of(CARLA, "Carla"),
                Map.of(ALEX, "Alex", BOB, "Bob"));
        assertEquals(List.of("Carla", "Alex", "Bob"), ziele.stream().map(BountyTargets.Target::name).toList());
        assertTrue(ziele.get(0).online());
        assertFalse(ziele.get(1).online());
    }

    @Test
    @DisplayName("Wer online ist und ein Konto hat, erscheint nur einmal")
    void deduplicated() {
        List<BountyTargets.Target> ziele = liste(Map.of(ALEX, "Alex"), Map.of(ALEX, "Alex"));
        assertEquals(1, ziele.size());
        assertTrue(ziele.get(0).online(), "der Online-Eintrag gewinnt");
    }

    @Test
    @DisplayName("Wer ein Kopfgeld traegt, steht auch ohne Konto zur Auswahl")
    void huntedIncluded() {
        Bounty topf = new Bounty(BOB, "Bob", 1L,
                List.of(new Bounty.Stake(ALEX, "Alex", 1L, Map.of(Material.DIAMOND, 1))), null);
        List<BountyTargets.Target> ziele =
                BountyTargets.candidates(Map.of(), Map.of(), List.of(topf), ICH);
        assertEquals(1, ziele.size());
        assertEquals("Bob", ziele.get(0).name());
    }

    @Test
    @DisplayName("Die Seitenzahl stimmt auch bei genau vollen und leeren Listen")
    void pageCount() {
        assertEquals(1, BountyTargets.pageCount(0, 28), "eine leere Liste hat trotzdem eine Seite");
        assertEquals(1, BountyTargets.pageCount(1, 28));
        assertEquals(1, BountyTargets.pageCount(28, 28));
        assertEquals(2, BountyTargets.pageCount(29, 28));
        assertEquals(3, BountyTargets.pageCount(56 + 1, 28));
    }

    @Test
    @DisplayName("Eine zu hohe Seitenzahl landet auf der letzten vorhandenen Seite")
    void clampPage() {
        // Zwischen zwei Aufrufen kann die Liste geschrumpft sein.
        assertEquals(1, BountyTargets.clampPage(5, 2));
        assertEquals(0, BountyTargets.clampPage(-3, 2));
        assertEquals(0, BountyTargets.clampPage(0, 1));
    }

    @Test
    @DisplayName("Der Seitenausschnitt bleibt innerhalb der Liste")
    void pageSlice() {
        List<BountyTargets.Target> alle = liste(Map.of(),
                Map.of(ALEX, "Alex", BOB, "Bob", CARLA, "Carla"));
        assertEquals(2, BountyTargets.page(alle, 0, 2).size());
        assertEquals(1, BountyTargets.page(alle, 1, 2).size());
        assertTrue(BountyTargets.page(alle, 9, 2).isEmpty(), "hinter dem Ende gibt es nichts");
    }
}
