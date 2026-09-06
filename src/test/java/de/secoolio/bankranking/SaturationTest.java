package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SaturationTest {

    private static final long START = 1_700_000_000_000L;
    private static final long HOUR = 3_600_000L;

    @Test
    @DisplayName("Der Zähler halbiert sich nach jeder Halbwertszeit")
    void decaysByHalfLife() {
        Saturation saturation = new Saturation(START);
        saturation.commit(Map.of(Material.IRON_INGOT, 4000.0));
        saturation.decay(START + 24 * HOUR, 24.0);
        assertEquals(2000.0, saturation.amount(Material.IRON_INGOT), 0.5);
        saturation.decay(START + 48 * HOUR, 24.0);
        assertEquals(1000.0, saturation.amount(Material.IRON_INGOT), 0.5);
    }

    @Test
    @DisplayName("Eine rückwärts laufende Uhr verändert nichts")
    void clockGoingBackwardsIsIgnored() {
        Saturation saturation = new Saturation(START);
        saturation.commit(Map.of(Material.IRON_INGOT, 100.0));
        saturation.decay(START - 5 * HOUR, 24.0);
        assertEquals(100.0, saturation.amount(Material.IRON_INGOT), 1e-9);
        assertEquals(START, saturation.lastDecay());
    }

    @Test
    @DisplayName("Ein negativer Zeitstempel kann den Zähler nicht ins Unendliche treiben")
    void negativeTimestampIsClamped() {
        Saturation saturation = new Saturation(Long.MIN_VALUE);
        assertEquals(0L, saturation.lastDecay());
        saturation.commit(Map.of(Material.IRON_INGOT, 100.0));
        saturation.decay(START, 24.0);
        assertTrue(saturation.isEmpty(), "nach sehr langer Zeit ist der Zähler abgebaut");
    }

    @Test
    @DisplayName("Nicht-endliche Werte werden weder aufgenommen noch behalten")
    void rejectsNonFinite() {
        Saturation saturation = new Saturation(START);
        saturation.commit(Map.of(Material.IRON_INGOT, Double.NaN));
        saturation.commit(Map.of(Material.GOLD_INGOT, Double.POSITIVE_INFINITY));
        assertTrue(saturation.isEmpty());
        saturation.put(Material.DIAMOND, Double.NaN);
        assertTrue(saturation.isEmpty());
    }

    @Test
    @DisplayName("Die Kopie übernimmt auch Werte unter der Vergessensschwelle")
    void copyKeepsSmallValues() {
        Saturation saturation = new Saturation(START);
        saturation.commit(Map.of(Material.IRON_INGOT, 0.8));
        Saturation copy = saturation.copy();
        assertEquals(0.8, copy.amount(Material.IRON_INGOT), 1e-9);
        assertEquals(START, copy.lastDecay());
        copy.forgetSmall();
        assertTrue(copy.isEmpty());
    }

    @Test
    @DisplayName("Die Sicht ist unveränderlich und commit summiert auf")
    void snapshotAndCommit() {
        Saturation saturation = new Saturation(START);
        saturation.commit(Map.of(Material.IRON_INGOT, 100.0));
        saturation.commit(Map.of(Material.IRON_INGOT, 50.0));
        assertEquals(150.0, saturation.amount(Material.IRON_INGOT), 1e-9);
        Map<Material, Double> view = saturation.snapshot();
        assertEquals(150.0, view.get(Material.IRON_INGOT), 1e-9);
        assertFalse(view.getClass().getName().contains("HashMap"), "unveränderliche Kopie erwartet");
    }
}
