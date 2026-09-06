package de.secoolio.bankranking;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Ein winziger Webserver, der genau eine Datei ausliefert: das Resourcepack.
 *
 * <p>Der Client laedt ein Serverpack ueber HTTP, nicht ueber die Spielverbindung. Damit im LAN
 * niemand auf das Internet angewiesen ist und der Hash niemals veraltet, liefert das Plugin
 * die Datei selbst aus - mit Bordmitteln der Java-Laufzeit, ohne Fremdbibliothek.
 *
 * <p>Der Dateiname enthaelt den Hash. Aendert sich das Pack, aendert sich damit die Adresse,
 * und kein zwischengespeichertes Exemplar kann sich halten. Das ist der uebliche Stolperstein:
 * Hash geaendert, Adresse gleich geblieben, halbe Spielerschaft haengt am alten Pack.
 *
 * <p>Ausgeliefert wird ausschliesslich aus dem Arbeitsspeicher. Es gibt keinen Pfad, den eine
 * Anfrage beeinflussen koennte, und damit auch keine Moeglichkeit, ueber praeparierte Adressen
 * an andere Dateien des Servers zu kommen.
 */
final class ResourcePackServer {

    private static final int BACKLOG = 8;

    private final HttpServer server;
    private final ExecutorService pool;
    private final String path;
    private final int port;

    private ResourcePackServer(HttpServer server, ExecutorService pool, String path, int port) {
        this.server = server;
        this.pool = pool;
        this.path = path;
        this.port = port;
    }

    /**
     * Startet den Webserver.
     *
     * @param bindAddress an welche Adresse gebunden wird; leer bedeutet alle
     * @param port        gewuenschter Port; 0 waehlt einen freien (nur fuer Tests sinnvoll)
     * @param pack        das auszuliefernde Pack
     * @throws IOException wenn der Port belegt ist oder das Binden scheitert
     */
    static ResourcePackServer start(String bindAddress, int port, ResourcePackFile pack)
            throws IOException {
        InetSocketAddress adresse = bindAddress == null || bindAddress.isBlank()
                ? new InetSocketAddress(port)
                : new InetSocketAddress(bindAddress, port);

        HttpServer server = HttpServer.create(adresse, BACKLOG);
        String pfad = "/bankranking-" + pack.sha1() + ".zip";
        byte[] daten = pack.bytes();

        server.createContext(pfad, austausch -> liefere(austausch, daten));
        // Ein eigener, kleiner Pool: der Serverpuls darf niemals auf einen Download warten.
        ExecutorService pool = Executors.newFixedThreadPool(2, auftrag -> {
            Thread t = new Thread(auftrag, "BankRanking-Pack");
            t.setDaemon(true);
            return t;
        });
        server.setExecutor(pool);
        server.start();
        return new ResourcePackServer(server, pool, pfad, server.getAddress().getPort());
    }

    private static void liefere(HttpExchange austausch, byte[] daten) throws IOException {
        try (austausch) {
            String methode = austausch.getRequestMethod();
            if (!"GET".equals(methode) && !"HEAD".equals(methode)) {
                austausch.sendResponseHeaders(405, -1);
                return;
            }
            austausch.getResponseHeaders().set("Content-Type", "application/zip");
            if ("HEAD".equals(methode)) {
                austausch.getResponseHeaders().set("Content-Length", String.valueOf(daten.length));
                austausch.sendResponseHeaders(200, -1);
                return;
            }
            austausch.sendResponseHeaders(200, daten.length);
            try (OutputStream aus = austausch.getResponseBody()) {
                aus.write(daten);
            }
        }
    }

    /** Der tatsaechlich belegte Port - bei Port 0 der vom Betriebssystem gewaehlte. */
    int port() {
        return this.port;
    }

    /** Der Pfad, unter dem das Pack liegt. */
    String path() {
        return this.path;
    }

    /** Die vollstaendige Adresse fuer die Clients. */
    String url(String host) {
        return "http://" + host + ":" + this.port + this.path;
    }

    /** Haelt den Server an. Der eigene Pool wird mit beendet, sonst bliebe er beim
     * Neuladen des Plugins zurueck. */
    void stop() {
        this.server.stop(0);
        this.pool.shutdownNow();
    }
}
