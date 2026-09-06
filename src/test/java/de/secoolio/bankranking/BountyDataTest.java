package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Prueft die Ablage der Kopfgelder.
 *
 * <p>Hier liegen echte Gegenstaende von Spielern. Der Massstab ist deshalb strenger als bei den
 * Punkten: nichts darf verschwinden, auch nicht bei einer beschaedigten Datei oder einem
 * fehlgeschlagenen Speichern.
 */
class BountyDataTest {

    private static final UUID ZIEL = UUID.fromString("069a79f4-44e9-4726-a5be-fca90e7aaf44");
    private static final UUID ALEX = UUID.fromString("ec561538-f3fd-461d-aff5-086b22154bce");
    private static final UUID BOB = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private static BountyData data(Path dir) {
        return new BountyData(new File(dir.toFile(), "kopfgelder.yml"),
                new TestSupport.RecordingLogger());
    }

    private static BountyData geladen(Path dir) {
        BountyData d = data(dir);
        d.load();
        return d;
    }

    private static Bounty.Stake einsatz(UUID von, String name, Map<Material, Integer> items) {
        return new Bounty.Stake(von, name, 1_757_000_000_000L, items);
    }

    private static String datei(Path dir) throws IOException {
        return Files.readString(dir.resolve("kopfgelder.yml"));
    }

    @Test
    @DisplayName("Eine fehlende Datei ergibt einen leeren, aber benutzbaren Stand")
    void missingFile(@TempDir Path dir) {
        BountyData d = geladen(dir);
        assertFalse(d.isLocked());
        assertTrue(d.active().isEmpty());
        assertNull(d.pot(ZIEL));
    }

    @Test
    @DisplayName("Ein Einsatz uebersteht Speichern und Laden")
    void stakeSurvives(@TempDir Path dir) {
        BountyData d = geladen(dir);
        assertTrue(d.stake(ZIEL, "Steve",
                einsatz(ALEX, "Alex", Map.of(Material.DIAMOND, 32))));

        Bounty geladenerTopf = geladen(dir).pot(ZIEL);
        assertNotNull(geladenerTopf);
        assertEquals("Steve", geladenerTopf.name());
        assertEquals(Map.of(Material.DIAMOND, 32), geladenerTopf.total());
        assertEquals(1, geladenerTopf.stakes().size());
        assertEquals("Alex", geladenerTopf.stakes().get(0).name());
    }

    @Test
    @DisplayName("Mehrere Einsaetze auf dieselbe Person sammeln sich in einem Topf")
    void stakesAccumulate(@TempDir Path dir) {
        BountyData d = geladen(dir);
        d.stake(ZIEL, "Steve", einsatz(ALEX, "Alex", Map.of(Material.DIAMOND, 32)));
        d.stake(ZIEL, "Steve", einsatz(BOB, "Bob",
                Map.of(Material.EMERALD_BLOCK, 4, Material.DIAMOND, 8)));

        Bounty topf = geladen(dir).pot(ZIEL);
        assertEquals(2, topf.stakes().size());
        assertEquals(Map.of(Material.DIAMOND, 40, Material.EMERALD_BLOCK, 4), topf.total());
        assertEquals(Set(ALEX, BOB), topf.placers());
    }

    private static java.util.Set<UUID> Set(UUID... ids) {
        return new java.util.LinkedHashSet<>(java.util.List.of(ids));
    }

    @Test
    @DisplayName("Eine beschaedigte Datei sperrt jedes Schreiben und wird nicht angefasst")
    void corruptFileLocks(@TempDir Path dir) throws IOException {
        Path datei = dir.resolve("kopfgelder.yml");
        String kaputt = "kopfgelder:\n  das ist: kein: gueltiges: yaml\n";
        Files.writeString(datei, kaputt);

        BountyData d = geladen(dir);
        assertTrue(d.isLocked());
        assertFalse(d.stake(ZIEL, "Steve", einsatz(ALEX, "Alex", Map.of(Material.DIAMOND, 1))));
        assertFalse(d.save());
        assertEquals(kaputt, Files.readString(datei),
                "die Datei mit fremden Gegenstaenden darf nicht angetastet werden");
    }

    @Test
    @DisplayName("Ein unlesbarer Eintrag ueberlebt das Speichern wortgetreu")
    void unreadableEntryPreserved(@TempDir Path dir) throws IOException {
        Files.writeString(dir.resolve("kopfgelder.yml"), """
                kopfgelder:
                  %s:
                    name: Steve
                    seit: 1
                    einsaetze:
                      - von: keine-uuid
                        items:
                          diamond: 5
                """.formatted(ZIEL));

        BountyData d = geladen(dir);
        assertTrue(d.isPreserved(ZIEL), "der Eintrag muss als unlesbar erkannt werden");
        assertNull(d.pot(ZIEL));

        // Ein Einsatz auf ein bewahrtes Ziel wird abgelehnt - sonst koennten sich der
        // bewahrte Block und ein neuer Topf gegenseitig ueberschreiben.
        assertFalse(d.stake(ZIEL, "Steve", einsatz(ALEX, "Alex", Map.of(Material.DIAMOND, 1))));

        d.stake(BOB, "Bob", einsatz(ALEX, "Alex", Map.of(Material.EMERALD, 2)));
        assertTrue(datei(dir).contains("keine-uuid"), "der unlesbare Block muss erhalten bleiben");
    }

    @Test
    @DisplayName("Ein unbekanntes Material laesst den ganzen Topf bewahrt statt halb gelesen")
    void unknownMaterialPreservesWholePot(@TempDir Path dir) {
        // Ein halb gelesener Topf wuerde teilweise ausgezahlt und der Rest beim naechsten
        // Speichern geloescht - das waere Verlust.
        writeQuietly(dir, """
                kopfgelder:
                  %s:
                    name: Steve
                    seit: 1
                    einsaetze:
                      - von: %s
                        name: Alex
                        zeit: 1
                        items:
                          diamond: 5
                          dirt: 64
                """.formatted(ZIEL, ALEX));
        BountyData d = geladen(dir);
        assertTrue(d.isPreserved(ZIEL));
        assertNull(d.pot(ZIEL));
    }

    @Test
    @DisplayName("Ein gescheitertes Speichern setzt Topf und Beute vollstaendig zurueck")
    void rollbackOnFailedSave(@TempDir Path dir) throws IOException {
        BountyData d = geladen(dir);
        d.stake(ZIEL, "Steve", einsatz(ALEX, "Alex", Map.of(Material.DIAMOND, 10)));

        // Ein Verzeichnis an der Stelle der temporaeren Datei laesst jedes Speichern scheitern.
        Files.createDirectory(dir.resolve("kopfgelder.yml.tmp"));
        assertFalse(d.payout(ZIEL, BOB, "Bob", 2_000L));

        assertEquals(Map.of(Material.DIAMOND, 10), d.pot(ZIEL).total(),
                "der Topf muss unveraendert stehen bleiben");
        assertNull(d.claim(BOB), "es darf keine halbe Beute entstehen");

        Files.delete(dir.resolve("kopfgelder.yml.tmp"));
        assertTrue(d.payout(ZIEL, BOB, "Bob", 2_000L));
    }

    @Test
    @DisplayName("Eine Auszahlung verschiebt den Topf in einem Zug in die Beute des Killers")
    void payoutMovesToClaim(@TempDir Path dir) {
        BountyData d = geladen(dir);
        d.stake(ZIEL, "Steve", einsatz(ALEX, "Alex", Map.of(Material.DIAMOND, 10)));
        assertTrue(d.payout(ZIEL, BOB, "Bob", 5_000L));

        BountyData neu = geladen(dir);
        assertTrue(neu.pot(ZIEL).isEmpty(), "der Topf ist leer");
        assertNotNull(neu.pot(ZIEL).lastPayout(), "die Sperrfrist muss die Leerung ueberleben");
        assertEquals(BOB, neu.pot(ZIEL).lastPayout().killer());

        BountyData.Claim beute = neu.claim(BOB);
        assertNotNull(beute);
        assertEquals(Map.of(Material.DIAMOND, 10), beute.items());
        assertEquals(BountyData.Claim.Reason.AUSZAHLUNG, beute.reason());
    }

    @Test
    @DisplayName("Eine Aufhebung gibt jedem Einzahler genau seinen eigenen Einsatz zurueck")
    void cancelRefundsEachStake(@TempDir Path dir) {
        BountyData d = geladen(dir);
        d.stake(ZIEL, "Steve", einsatz(ALEX, "Alex", Map.of(Material.DIAMOND, 32)));
        d.stake(ZIEL, "Steve", einsatz(BOB, "Bob", Map.of(Material.EMERALD, 7)));
        assertTrue(d.cancel(ZIEL, 9_000L));

        BountyData neu = geladen(dir);
        assertEquals(Map.of(Material.DIAMOND, 32), neu.claim(ALEX).items());
        assertEquals(Map.of(Material.EMERALD, 7), neu.claim(BOB).items());
        assertTrue(neu.pot(ZIEL) == null || neu.pot(ZIEL).isEmpty());
        assertNull(neu.pot(ZIEL), "ein leerer Topf ohne Sperrfrist wird nicht mitgeschleppt");
    }

    @Test
    @DisplayName("Eine Zustellung raeumt die Beute, ein Rest bleibt liegen")
    void deliveryClearsOrKeepsRest(@TempDir Path dir) {
        BountyData d = geladen(dir);
        d.addClaim(BOB, "Bob", BountyData.Claim.Reason.AUSZAHLUNG, 1L,
                Map.of(Material.DIAMOND, 10));

        assertTrue(d.beginDelivery(BOB));
        assertTrue(d.finishDelivery(BOB, Map.of(Material.DIAMOND, 4)));
        assertEquals(Map.of(Material.DIAMOND, 4), geladen(dir).claim(BOB).items());

        assertTrue(d.finishDelivery(BOB, Map.of()));
        assertNull(geladen(dir).claim(BOB));
    }

    @Test
    @DisplayName("Eine unterbrochene Zustellung wird gemeldet, nicht blind wiederholt")
    void interruptedDeliveryWarns(@TempDir Path dir) {
        BountyData d = geladen(dir);
        d.addClaim(BOB, "Bob", BountyData.Claim.Reason.AUSZAHLUNG, 1L, Map.of(Material.DIAMOND, 3));
        d.beginDelivery(BOB);

        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        BountyData neu = new BountyData(new File(dir.toFile(), "kopfgelder.yml"), log);
        neu.load();

        assertNotNull(neu.claim(BOB), "die Gegenstaende bleiben in der Beute");
        assertFalse(neu.claim(BOB).delivering(), "der Merker wird zurueckgesetzt");
        assertTrue(log.warnings().stream().anyMatch(w -> w.contains("Zustellung")),
                log.warnings().toString());
    }

    @Test
    @DisplayName("Der Ort der Beutekiste uebersteht einen Neustart")
    void claimLocationSurvives(@TempDir Path dir) {
        BountyData d = geladen(dir);
        d.addClaim(BOB, "Bob", BountyData.Claim.Reason.AUSZAHLUNG, 1L, Map.of(Material.DIAMOND, 1));
        assertTrue(d.setClaimLocation(BOB, "world", 121.5, 71.0, -44.5));

        BountyData.Claim beute = geladen(dir).claim(BOB);
        assertTrue(beute.hasLocation());
        assertEquals("world", beute.world());
        assertEquals(121.5, beute.x());
        assertEquals(-44.5, beute.z());
    }

    @Test
    @DisplayName("Es bleiben keine temporaeren Dateien liegen")
    void noTempLeftBehind(@TempDir Path dir) throws IOException {
        BountyData d = geladen(dir);
        d.stake(ZIEL, "Steve", einsatz(ALEX, "Alex", Map.of(Material.DIAMOND, 1)));
        try (var eintraege = Files.list(dir)) {
            assertTrue(eintraege.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")));
        }
    }

    private static void writeQuietly(Path dir, String inhalt) {
        try {
            Files.writeString(dir.resolve("kopfgelder.yml"), inhalt);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
