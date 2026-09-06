package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlayerStatsTest {

    private static final long NOW = 1_700_000_000_000L;

    private static PlayerStats.Deposit deposit(double points, int items, Material top, long offset) {
        return new PlayerStats.Deposit(NOW + offset, points, items, top);
    }

    @Test
    @DisplayName("Einzahlungen werden gezählt, die größte gemerkt")
    void recordsDeposits() {
        PlayerStats stats = PlayerStats.EMPTY
                .record(deposit(10.0, 64, Material.COBBLESTONE, 0), Map.of(Material.COBBLESTONE, 64))
                .record(deposit(80.0, 5, Material.DIAMOND, 1000), Map.of(Material.DIAMOND, 5))
                .record(deposit(20.0, 32, Material.IRON_INGOT, 2000), Map.of(Material.IRON_INGOT, 32));

        assertEquals(3, stats.deposits());
        assertEquals(101L, stats.items());
        assertEquals(80.0, stats.biggest().points(), 1e-9);
        assertEquals(Material.DIAMOND, stats.biggest().top());
        assertEquals(110.0 / 3, stats.averagePoints(), 1e-9);
        assertEquals(Material.COBBLESTONE, stats.favourite().orElseThrow().getKey());
    }

    @Test
    @DisplayName("Nur die letzten fünf Einzahlungen bleiben, die neueste zuerst")
    void keepsTheLastFive() {
        PlayerStats stats = PlayerStats.EMPTY;
        for (int i = 1; i <= 7; i++) {
            stats = stats.record(deposit(i, i, Material.DIRT, i * 1000L), Map.of(Material.DIRT, i));
        }
        assertEquals(PlayerStats.RECENT_LIMIT, stats.recent().size());
        assertEquals(7.0, stats.recent().get(0).points(), 1e-9);
        assertEquals(3.0, stats.recent().get(4).points(), 1e-9);
        assertEquals(7, stats.deposits());
    }

    @Test
    @DisplayName("Ein neuer Stand lässt den alten unverändert")
    void recordIsImmutable() {
        PlayerStats first = PlayerStats.EMPTY.record(deposit(10.0, 1, Material.DIRT, 0), Map.of(Material.DIRT, 1));
        PlayerStats second = first.record(deposit(20.0, 1, Material.STONE, 1000), Map.of(Material.STONE, 1));
        assertEquals(1, first.deposits());
        assertEquals(2, second.deposits());
        assertTrue(PlayerStats.EMPTY.isEmpty());
    }
}
