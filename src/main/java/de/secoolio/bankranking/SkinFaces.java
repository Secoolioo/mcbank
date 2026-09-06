package de.secoolio.bankranking;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.imageio.ImageIO;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Besorgt Spielergesichter und haelt sie vor.
 *
 * <p>Bei einem anwesenden Spieler liefert der Server die Adresse des Skins bereits mit dem
 * Profil - Mojang muss also nie gefragt werden. Nur fuer einen Abwesenden wird das Profil
 * einmal nachgeschlagen. Geladen wird immer neben dem Serverpuls: eine Ankuendigung darf nie
 * an einem Netzproblem haengen bleiben.
 *
 * <p>Zwischengespeichert wird das fertige Gesicht, nicht das Bild. Ein Gesicht ist
 * unveraenderlich und darf an alle Spieler gleichzeitig geschickt werden.
 */
final class SkinFaces {

    /** Groesser als das kann kein Skin sein; alles darueber wird verworfen. */
    private static final int MAX_BYTES = 256 * 1024;
    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    private final ExecutorService pool = Executors.newFixedThreadPool(2, auftrag -> {
        Thread t = new Thread(auftrag, "BankRanking-Skin");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            // Bewusst keine Umleitungen: die Adresse stammt aus einer Profil-Eigenschaft, also
            // aus Fremddaten. Ohne diese Sperre liesse sich der Server als Bote missbrauchen.
            .followRedirects(HttpClient.Redirect.NEVER)
            .executor(this.pool)
            .build();

    private final Map<UUID, SkinFace> cache = new ConcurrentHashMap<>();
    private final Map<UUID, String> quelle = new ConcurrentHashMap<>();

    /** Das bereits vorliegende Gesicht, ohne zu laden. */
    Optional<SkinFace> cached(UUID id) {
        return Optional.ofNullable(this.cache.get(id));
    }

    /** Das Gesicht eines anwesenden Spielers. */
    CompletableFuture<SkinFace> of(Player player) {
        return load(player.getUniqueId(), skinUrl(player.getPlayerProfile()));
    }

    /**
     * Das Gesicht eines Spielers, der auch abgemeldet sein darf.
     *
     * <p>Ein Kopfgeld laesst sich auf jeden aussetzen, auch auf jemanden, der gerade nicht da
     * ist - dann muss das Plakat trotzdem sein Gesicht zeigen. Fuer einen Abwesenden wird das
     * Profil einmal bei Mojang nachgeschlagen; das kostet einen Netzzugriff und laeuft deshalb
     * nebenher.
     */
    CompletableFuture<SkinFace> of(UUID id, String name) {
        SkinFace bekannt = this.cache.get(id);
        if (bekannt != null) {
            return CompletableFuture.completedFuture(bekannt);
        }
        Player anwesend = Bukkit.getPlayer(id);
        if (anwesend != null) {
            return of(anwesend);
        }
        // supplyAsync uebergibt die Aufgabe SOFORT an den Pool. Ist der beendet, wirft es
        // synchron eine RejectedExecutionException - ein angehaengtes exceptionally faenge
        // sie nicht, weil es dann gar nicht mehr erreicht wird. Deshalb der Rahmen hier.
        try {
            return CompletableFuture
                    .supplyAsync(() -> {
                        try {
                            return Bukkit.createProfile(id, name).update().join();
                        } catch (Exception e) {
                            return null;
                        }
                    }, this.pool)
                    .thenCompose(profil -> load(id, skinUrl(profil)))
                    .exceptionally(fehler -> SkinFace.defaultFor(id));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(SkinFace.defaultFor(id));
        }
    }

    /** Der eigentliche Ladeweg: Adresse pruefen, Bild holen, Gesicht auslesen. */
    private CompletableFuture<SkinFace> load(UUID id, URL skin) {
        if (skin == null) {
            SkinFace ersatz = SkinFace.defaultFor(id);
            this.cache.put(id, ersatz);
            return CompletableFuture.completedFuture(ersatz);
        }
        String adresse = skin.toString();
        SkinFace bekannt = this.cache.get(id);
        if (bekannt != null && adresse.equals(this.quelle.get(id))) {
            return CompletableFuture.completedFuture(bekannt);
        }
        if (!isAllowedSkinUrl(adresse)) {
            return CompletableFuture.completedFuture(SkinFace.defaultFor(id));
        }
        // Auch hier: der HttpClient benutzt denselben Pool, und sendAsync wirft bei einem
        // beendeten Pool ebenfalls synchron.
        try {
            return this.client.sendAsync(
                            HttpRequest.newBuilder(URI.create(adresse)).timeout(TIMEOUT).build(),
                            HttpResponse.BodyHandlers.ofByteArray())
                    .thenApply(antwort -> {
                        SkinFace gesicht = read(antwort);
                        this.cache.put(id, gesicht);
                        this.quelle.put(id, adresse);
                        return gesicht;
                    })
                    .exceptionally(fehler -> SkinFace.defaultFor(id));
        } catch (RuntimeException e) {
            return CompletableFuture.completedFuture(SkinFace.defaultFor(id));
        }
    }

    private static SkinFace read(HttpResponse<byte[]> antwort) {
        if (antwort.statusCode() != 200 || antwort.body().length > MAX_BYTES) {
            return SkinFace.UNKNOWN;
        }
        try {
            BufferedImage bild = ImageIO.read(new ByteArrayInputStream(antwort.body()));
            return SkinFace.fromSkin(bild);
        } catch (Exception e) {
            return SkinFace.UNKNOWN;
        }
    }

    private static URL skinUrl(PlayerProfile profil) {
        try {
            return profil != null && profil.hasTextures() ? profil.getTextures().getSkin() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Nur die Texturauslieferung von Mojang ist erlaubt.
     *
     * <p>Die Adresse kommt aus einer Profil-Eigenschaft und damit aus Fremddaten. Ohne diese
     * Pruefung liesse sich der Server dazu bringen, beliebige Adressen abzurufen.
     */
    static boolean isAllowedSkinUrl(String adresse) {
        try {
            URI uri = URI.create(adresse);
            return "https".equals(uri.getScheme())
                    && "textures.minecraft.net".equalsIgnoreCase(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }

    /** Vergisst einen Spieler, etwa beim Verlassen des Servers. */
    void forget(UUID id) {
        this.cache.remove(id);
        this.quelle.remove(id);
    }

    void shutdown() {
        this.pool.shutdownNow();
        this.cache.clear();
        this.quelle.clear();
    }
}
