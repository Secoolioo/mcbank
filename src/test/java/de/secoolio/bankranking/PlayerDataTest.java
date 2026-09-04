package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerDataTest {

    private static final UUID STEVE = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e7aaf44");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final UUID HERO = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private PlayerData data(Path dir) {
        return new PlayerData(new File(dir.toFile(), "players.yml"), new TestSupport.RecordingLogger());
    }

    @Test
    @DisplayName("Eine fehlende Datei ergibt ein leeres Konto")
    void missingFile(@TempDir Path dir) {
        PlayerData data = data(dir);
        data.load();
        assertEquals(0, data.size());
        assertEquals(0.0, data.get(STEVE), 1e-9);
        assertEquals(0, data.rank(STEVE));
        assertTrue(data.top(10).isEmpty());
    }

    @Test
    @DisplayName("Einzahlungen werden sofort gespeichert und wieder geladen")
    void roundTrip(@TempDir Path dir) {
        PlayerData data = data(dir);
        data.load();
        PlayerData.AddResult result = data.add(STEVE, "Steve", 44.0);
        assertTrue(result.saved());
        assertEquals(44.0, result.total(), 1e-9);
        assertTrue(new File(dir.toFile(), "players.yml").exists());
        assertFalse(data.isDirty());

        PlayerData reloaded = data(dir);
        reloaded.load();
        assertEquals(44.0, reloaded.get(STEVE), 1e-9);
        assertEquals(1, reloaded.size());
        assertEquals("Steve", reloaded.top(1).get(0).getValue().name());
    }

    @Test
    @DisplayName("Wiederholte Einzahlungen summieren sich ohne Rundungsdrift")
    void accumulatesExactly(@TempDir Path dir) {
        PlayerData data = data(dir);
        data.load();
        for (int i = 0; i < 100; i++) {
            data.add(STEVE, "Steve", 0.1);
        }
        assertEquals(10.0, data.get(STEVE), 1e-9);
    }

    @Test
    @DisplayName("Der Name wird bei jeder Einzahlung aktualisiert")
    void updatesName(@TempDir Path dir) {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 5.0);
        data.add(STEVE, "SteveNeu", 5.0);
        assertEquals("SteveNeu", data.top(1).get(0).getValue().name());
        assertEquals(10.0, data.get(STEVE), 1e-9);
    }

    @Test
    @DisplayName("Rangliste sortiert absteigend, bei Gleichstand nach Name")
    void ranking(@TempDir Path dir) {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 100.0);
        data.add(ALEX, "Alex", 300.0);
        data.add(HERO, "Hero", 100.0);

        List<Map.Entry<UUID, PlayerData.Entry>> top = data.top(3);
        assertEquals("Alex", top.get(0).getValue().name());
        assertEquals("Hero", top.get(1).getValue().name());
        assertEquals("Steve", top.get(2).getValue().name());
        assertEquals(1, data.rank(ALEX));
        assertEquals(2, data.rank(HERO));
        assertEquals(3, data.rank(STEVE));
        assertEquals(0, data.rank(UUID.randomUUID()));
        assertEquals(2, data.top(2).size());
        assertEquals(3, data.top(10).size());
    }

    @Test
    @DisplayName("Ungueltige Eintraege werden uebersprungen, gute geladen")
    void skipsBrokenEntries(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("players.yml");
        Files.writeString(file, """
                spieler:
                  069a79f4-44e9-4726-a5be-fca90e7aaf44:
                    name: Steve
                    punkte: 12.5
                  keine-uuid:
                    name: Kaputt
                    punkte: 5.0
                  ec561538-f3fd-461d-aff5-086b22154bce:
                    name: Alex
                    punkte: -3.0
                """, StandardCharsets.UTF_8);
        PlayerData data = data(dir);
        data.load();
        assertEquals(1, data.size());
        assertEquals(12.5, data.get(STEVE), 1e-9);
    }

    @Test
    @DisplayName("Eine kaputte Datei wird zur Seite gelegt statt ueberschrieben")
    void quarantinesBrokenFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("players.yml");
        Files.writeString(file, "spieler: [\n", StandardCharsets.UTF_8);
        PlayerData data = data(dir);
        data.load();
        assertEquals(0, data.size());

        try (var stream = Files.list(dir)) {
            assertTrue(stream.anyMatch(p -> p.getFileName().toString().startsWith("players.yml.corrupt-")));
        }
        assertFalse(Files.exists(file));
    }

    @Test
    @DisplayName("Nach dem Speichern bleibt keine temporaere Datei zurueck")
    void noLeftoverTempFile(@TempDir Path dir) throws IOException {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 1.0);
        data.add(STEVE, "Steve", 1.0);
        try (var stream = Files.list(dir)) {
            assertTrue(stream.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    @DisplayName("Neu laden ersetzt den Speicherstand durch die Datei")
    void reloadReadsFile(@TempDir Path dir) throws IOException {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 10.0);

        Files.writeString(dir.resolve("players.yml"), """
                spieler:
                  069a79f4-44e9-4726-a5be-fca90e7aaf44:
                    name: Steve
                    punkte: 99.0
                """, StandardCharsets.UTF_8);
        assertTrue(data.reload());
        assertEquals(99.0, data.get(STEVE), 1e-9);
    }

    @Test
    @DisplayName("Neu laden laesst eine kaputte Datei in Ruhe und behaelt die Punkte im Speicher")
    void reloadKeepsMemoryOnBrokenFile(@TempDir Path dir) throws IOException {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 10.0);

        Files.writeString(dir.resolve("players.yml"), "spieler: [\n", StandardCharsets.UTF_8);
        assertFalse(data.reload());
        assertEquals(10.0, data.get(STEVE), 1e-9);
        assertTrue(Files.exists(dir.resolve("players.yml")));
    }
}
