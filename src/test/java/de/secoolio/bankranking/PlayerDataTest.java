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

import org.bukkit.Material;

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
        PlayerData.AddResult result = data.add(STEVE, "Steve", 44.0, Map.of());
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
            data.add(STEVE, "Steve", 0.1, Map.of());
        }
        assertEquals(10.0, data.get(STEVE), 1e-9);
    }

    @Test
    @DisplayName("Der Name wird bei jeder Einzahlung aktualisiert")
    void updatesName(@TempDir Path dir) {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 5.0, Map.of());
        data.add(STEVE, "SteveNeu", 5.0, Map.of());
        assertEquals("SteveNeu", data.top(1).get(0).getValue().name());
        assertEquals(10.0, data.get(STEVE), 1e-9);
    }

    @Test
    @DisplayName("Rangliste sortiert absteigend, bei Gleichstand nach Name")
    void ranking(@TempDir Path dir) {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 100.0, Map.of());
        data.add(ALEX, "Alex", 300.0, Map.of());
        data.add(HERO, "Hero", 100.0, Map.of());

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
        data.add(STEVE, "Steve", 1.0, Map.of());
        data.add(STEVE, "Steve", 1.0, Map.of());
        try (var stream = Files.list(dir)) {
            assertTrue(stream.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    @DisplayName("Neu laden ersetzt den Speicherstand durch die Datei")
    void reloadReadsFile(@TempDir Path dir) throws IOException {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 10.0, Map.of());

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
        data.add(STEVE, "Steve", 10.0, Map.of());

        Files.writeString(dir.resolve("players.yml"), "spieler: [\n", StandardCharsets.UTF_8);
        assertFalse(data.reload());
        assertEquals(10.0, data.get(STEVE), 1e-9);
        assertTrue(Files.exists(dir.resolve("players.yml")));
    }

    @Test
    @DisplayName("Ein wieder gültiges Konto verdrängt seinen alten unlesbaren Block")
    void repairedAccountWinsOverPreservedBlock(@TempDir Path dir) throws IOException {
        // Punktzahl von Hand verdorben: der Eintrag ist unlesbar, aber die Spieler-ID ist gültig.
        Files.writeString(fileIn(dir).toPath(), """
                spieler:
                  %s:
                    name: Steve
                    punkte: -3.0
                """.formatted(STEVE));
        PlayerData data = data(dir);
        data.load();
        assertEquals(0, data.size(), "der verdorbene Eintrag zählt nicht als Konto");

        assertTrue(data.add(STEVE, "Steve", 40.0, Map.of()).saved());

        PlayerData reloaded = data(dir);
        reloaded.load();
        assertEquals(40.0, reloaded.get(STEVE), 1e-9, "die Einzahlung steht wirklich in der Datei");
    }

    @Test
    @DisplayName("Ein unlesbarer Eintrag ohne Unterabschnitt verliert seinen Inhalt nicht")
    void preservesScalarEntries(@TempDir Path dir) throws IOException {
        Files.writeString(fileIn(dir).toPath(), """
                spieler:
                  %s: 12.5
                  %s:
                    name: Alex
                    punkte: 20.0
                """.formatted(STEVE, ALEX));
        PlayerData data = data(dir);
        data.load();
        assertEquals(1, data.size());

        data.add(ALEX, "Alex", 5.0, Map.of());
        assertTrue(readFile(dir).contains("12.5"), "der ursprüngliche Wert steht weiterhin in der Datei");
    }

    @Test
    @DisplayName("Die Kennzahlen überstehen Speichern und Laden")
    void statisticsRoundTrip(@TempDir Path dir) {
        PlayerData data = data(dir);
        data.load();
        PlayerStats.Deposit first = new PlayerStats.Deposit(1_700_000_000_000L, 40.0, 64, Material.IRON_INGOT);
        PlayerStats.Deposit second = new PlayerStats.Deposit(1_700_000_100_000L, 90.0, 12, Material.DIAMOND);
        data.add(STEVE, "Steve", 40.0, Map.of(), first, Map.of(Material.IRON_INGOT, 64));
        data.add(STEVE, "Steve", 90.0, Map.of(), second, Map.of(Material.DIAMOND, 12));

        PlayerData reloaded = data(dir);
        reloaded.load();
        PlayerStats stats = reloaded.stats(STEVE);
        assertEquals(2, stats.deposits());
        assertEquals(76L, stats.items());
        assertEquals(90.0, stats.biggest().points(), 1e-9);
        assertEquals(Material.DIAMOND, stats.biggest().top());
        assertEquals(Material.IRON_INGOT, stats.favourite().orElseThrow().getKey());
        assertEquals(2, stats.recent().size());
        assertEquals(90.0, stats.recent().get(0).points(), 1e-9);
    }

    @Test
    @DisplayName("Ein unbrauchbares Statistik-Feld kostet nicht den ganzen Spieler")
    void brokenStatisticFieldKeepsThePlayer(@TempDir Path dir) throws IOException {
        Files.writeString(fileIn(dir).toPath(), """
                spieler:
                  %s:
                    name: Steve
                    punkte: 100.0
                    statistik:
                      einzahlungen: "keine Zahl"
                      items: 50
                      materialien:
                        gibtsnicht: 10
                        iron_ingot: 20
                """.formatted(STEVE));
        PlayerData data = data(dir);
        data.load();
        assertEquals(100.0, data.get(STEVE), 1e-9);
        PlayerStats stats = data.stats(STEVE);
        assertEquals(0, stats.deposits(), "das unbrauchbare Feld fällt auf seinen Standard zurück");
        assertEquals(50L, stats.items(), "die gültigen Felder bleiben erhalten");
        assertEquals(Material.IRON_INGOT, stats.favourite().orElseThrow().getKey());
    }

    @Test
    @DisplayName("Nach einem gescheiterten Speichern bleibt auch der bewahrte Block erhalten")
    void rollbackKeepsPreservedBlock(@TempDir Path dir) throws IOException {
        Files.writeString(fileIn(dir).toPath(), """
                spieler:
                  %s:
                    name: Steve
                    punkte: -3.0
                """.formatted(STEVE));
        PlayerData data = data(dir);
        data.load();

        // Ein Verzeichnis an der Stelle der temporären Datei lässt jedes Speichern scheitern.
        Files.createDirectory(dir.resolve("players.yml.tmp"));
        assertFalse(data.add(STEVE, "Steve", 40.0, Map.of()).saved());

        // Nach dem Aufräumen muss der bewahrte Block noch da sein.
        Files.delete(dir.resolve("players.yml.tmp"));
        assertTrue(data.add(ALEX, "Alex", 5.0, Map.of()).saved());
        assertTrue(readFile(dir).contains("-3.0"), "der ursprüngliche Block überlebt den Fehlschlag");
    }

    private static File fileIn(Path dir) {
        return new File(dir.toFile(), "players.yml");
    }

    private static String readFile(Path dir) throws IOException {
        return Files.readString(fileIn(dir).toPath());
    }

    @Test
    @DisplayName("Eine Einzahlung bucht Punkte und Marktsättigung gemeinsam")
    void bookingIsOneTransaction(@TempDir Path dir) {
        PlayerData data = data(dir);
        data.load();
        PlayerData.AddResult result = data.add(STEVE, "Steve", 100.0, Map.of(Material.IRON_INGOT, 600.0));
        assertTrue(result.saved());
        // Der Zeitverfall laeuft fortlaufend, deshalb eine Toleranz statt eines exakten Werts.
        assertEquals(600.0, data.saturationView(STEVE, 24.0).get(Material.IRON_INGOT), 0.01);

        PlayerData reloaded = data(dir);
        reloaded.load();
        assertEquals(100.0, reloaded.get(STEVE), 1e-9);
        assertEquals(600.0, reloaded.saturationView(STEVE, 24.0).get(Material.IRON_INGOT), 0.01);
    }

    @Test
    @DisplayName("Ein ungültiger Betrag wird abgelehnt, ohne etwas zu verändern")
    void rejectsInvalidAmount(@TempDir Path dir) {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 50.0, Map.of());
        assertFalse(data.add(STEVE, "Steve", Double.NaN, Map.of(Material.DIRT, 5.0)).saved());
        assertFalse(data.add(STEVE, "Steve", -1.0, Map.of()).saved());
        assertEquals(50.0, data.get(STEVE), 1e-9);
        assertTrue(data.saturationView(STEVE, 24.0).isEmpty());
    }

    @Test
    @DisplayName("Wer nur nachschaut, hinterlässt keinen Sättigungseintrag")
    void readingDoesNotCreateAnEntry(@TempDir Path dir) throws IOException {
        PlayerData data = data(dir);
        data.load();
        assertTrue(data.saturationView(STEVE, 24.0).isEmpty());
        data.add(STEVE, "Steve", 10.0, Map.of());
        assertFalse(readFile(dir).contains("saettigung"), "ohne Zähler wird kein Block geschrieben");
    }

    @Test
    @DisplayName("Kleinstzähler landen gar nicht erst in der Datei")
    void tinyCountersAreNotWritten(@TempDir Path dir) throws IOException {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 10.0, Map.of(Material.DIRT, 0.4));
        assertFalse(readFile(dir).contains("saettigung"));
    }

    @Test
    @DisplayName("Eine Datei ohne Spieler-Sektion wird zur Seite gelegt statt überschrieben")
    void fileWithoutSectionIsQuarantined(@TempDir Path dir) throws IOException {
        Files.writeString(fileIn(dir).toPath(), "irgendwas: 1\n");
        PlayerData data = data(dir);
        data.load();
        assertEquals(0, data.size());
        File[] quarantined = dir.toFile().listFiles((folder, name) -> name.contains(".corrupt-"));
        assertTrue(quarantined != null && quarantined.length > 0, "die beschädigte Datei liegt daneben");
    }

    @Test
    @DisplayName("Unlesbare Einträge überleben das nächste Speichern")
    void unreadableEntriesSurviveSaving(@TempDir Path dir) throws IOException {
        Files.writeString(fileIn(dir).toPath(), """
                spieler:
                  %s:
                    name: Steve
                    punkte: 100.0
                  keine-uuid:
                    name: Alex
                    punkte: 50.0
                """.formatted(STEVE));
        PlayerData data = data(dir);
        data.load();
        assertEquals(1, data.size());

        data.add(STEVE, "Steve", 5.0, Map.of());
        String content = readFile(dir);
        assertTrue(content.contains("keine-uuid"), "der unlesbare Eintrag steht weiterhin in der Datei");
        assertTrue(content.contains("Alex"), "mitsamt seinem Inhalt");
    }

    @Test
    @DisplayName("Neu laden ersetzt auch die Marktsättigung")
    void reloadReplacesSaturation(@TempDir Path dir) throws IOException {
        PlayerData data = data(dir);
        data.load();
        data.add(STEVE, "Steve", 10.0, Map.of(Material.IRON_INGOT, 900.0));
        assertFalse(data.saturationView(STEVE, 24.0).isEmpty());

        // Der Betreiber entfernt den Sättigungsblock von Hand.
        Files.writeString(fileIn(dir).toPath(), """
                spieler:
                  %s:
                    name: Steve
                    punkte: 10.0
                """.formatted(STEVE));
        assertTrue(data.reload());
        assertTrue(data.saturationView(STEVE, 24.0).isEmpty());
    }
}
