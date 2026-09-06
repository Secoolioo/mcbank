package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Prueft das Zusammenbauen und Ausliefern des Resourcepacks. */
class ResourcePackTest {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    private static ResourcePackFile build(Path ordner) throws IOException {
        return ResourcePackFile.build(ordner, new TestSupport.RecordingLogger());
    }

    private static HttpResponse<byte[]> hole(String url) throws Exception {
        return CLIENT.send(HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5)).build(), HttpResponse.BodyHandlers.ofByteArray());
    }

    @Test
    @DisplayName("Ohne eigene Dateien wird das Pack aus dem Jar unveraendert ausgeliefert")
    void plainPack(@TempDir Path ordner) throws IOException {
        ResourcePackFile pack = build(ordner);
        assertTrue(pack.size() > 1000, "das Pack ist verdaechtig klein");
        assertEquals(40, pack.sha1().length(), "der SHA-1 muss vierzig Hexziffern haben");
        assertTrue(pack.sha1().matches("[0-9a-f]{40}"));
        assertTrue(pack.replaced().isEmpty());
    }

    @Test
    @DisplayName("Derselbe Ordnerinhalt ergibt immer denselben Hash")
    void stableHash(@TempDir Path ordner) throws IOException {
        // Ohne diese Eigenschaft laedt jeder Client das Pack bei jedem Serverstart neu.
        assertEquals(build(ordner).sha1(), build(ordner).sha1());

        Path klaenge = Files.createDirectories(ordner.resolve("pack-eigene/sounds"));
        Files.write(klaenge.resolve("plakat.ogg"), new byte[]{1, 2, 3, 4, 5});
        ResourcePackFile ersteFassung = build(ordner);
        ResourcePackFile zweiteFassung = build(ordner);
        assertEquals(ersteFassung.sha1(), zweiteFassung.sha1(),
                "zweimal packen muss byteweise dasselbe ergeben");
        assertArrayEquals(ersteFassung.bytes(), zweiteFassung.bytes());
    }

    @Test
    @DisplayName("Eine eigene Datei ersetzt die mitgelieferte und aendert den Hash")
    void ownSoundReplaces(@TempDir Path ordner) throws IOException {
        String vorher = build(ordner).sha1();

        Path klaenge = Files.createDirectories(ordner.resolve("pack-eigene/sounds"));
        Files.write(klaenge.resolve("plakat.ogg"), new byte[]{9, 9, 9});
        ResourcePackFile nachher = build(ordner);

        assertNotEquals(vorher, nachher.sha1(), "geaenderter Inhalt braucht einen neuen Hash");
        assertEquals(List.of("assets/bankranking/sounds/kopfgeld/plakat.ogg"), nachher.replaced());
    }

    @Test
    @DisplayName("Eine Datei mit falscher Endung wird gemeldet statt still uebergangen")
    void wrongExtensionWarns(@TempDir Path ordner) throws IOException {
        Path klaenge = Files.createDirectories(ordner.resolve("pack-eigene/sounds"));
        Files.write(klaenge.resolve("plakat.mp3"), new byte[]{1});
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        ResourcePackFile pack = ResourcePackFile.build(ordner, log);

        assertTrue(pack.replaced().isEmpty());
        assertEquals(1, log.warnings().size());
        assertTrue(log.warnings().get(0).contains("plakat.mp3"), log.warnings().get(0));
    }

    @Test
    @DisplayName("Der Webserver liefert genau die Bytes des Packs aus")
    void serverDelivers(@TempDir Path ordner) throws Exception {
        ResourcePackFile pack = build(ordner);
        ResourcePackServer server = ResourcePackServer.start("127.0.0.1", 0, pack);
        try {
            String url = server.url("127.0.0.1");
            assertTrue(url.contains(pack.sha1()), "die Adresse muss den Hash tragen, sonst "
                    + "kann ein Zwischenspeicher eine alte Fassung festhalten");

            HttpResponse<byte[]> antwort = hole(url);
            assertEquals(200, antwort.statusCode());
            assertArrayEquals(pack.bytes(), antwort.body());
            assertEquals("application/zip",
                    antwort.headers().firstValue("Content-Type").orElse(""));
        } finally {
            server.stop();
        }
    }

    @Test
    @DisplayName("Unbekannte Pfade und fremde Methoden werden abgewiesen")
    void serverRejects(@TempDir Path ordner) throws Exception {
        ResourcePackFile pack = build(ordner);
        ResourcePackServer server = ResourcePackServer.start("127.0.0.1", 0, pack);
        try {
            String basis = "http://127.0.0.1:" + server.port();
            assertEquals(404, hole(basis + "/etwas-anderes.zip").statusCode());

            HttpResponse<byte[]> post = CLIENT.send(
                    HttpRequest.newBuilder(URI.create(basis + server.path()))
                            .POST(HttpRequest.BodyPublishers.ofString("x"))
                            .timeout(Duration.ofSeconds(5)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(405, post.statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    @DisplayName("Nach dem Anhalten nimmt der Port keine Verbindung mehr an")
    void serverStops(@TempDir Path ordner) throws Exception {
        ResourcePackFile pack = build(ordner);
        ResourcePackServer server = ResourcePackServer.start("127.0.0.1", 0, pack);
        String url = server.url("127.0.0.1");
        assertEquals(200, hole(url).statusCode());

        server.stop();
        assertThrows(ConnectException.class, () -> hole(url));
    }

    @Test
    @DisplayName("Ein belegter Port wird gemeldet und nicht still uebergangen")
    void portInUse(@TempDir Path ordner) throws Exception {
        ResourcePackFile pack = build(ordner);
        ResourcePackServer erster = ResourcePackServer.start("127.0.0.1", 0, pack);
        try {
            assertThrows(IOException.class,
                    () -> ResourcePackServer.start("127.0.0.1", erster.port(), pack));
        } finally {
            erster.stop();
        }
    }
}
