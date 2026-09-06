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

import org.bukkit.entity.Player;

/**
 * Besorgt Spielergesichter und haelt sie vor.
 *
 * <p>Die Adresse des Skins liefert der Server bereits mit dem Spielerprofil - Mojang muss also
 * nie gefragt werden. Geladen wird nur noch das Bild selbst, und zwar neben dem Serverpuls: eine
 * Ankuendigung darf nie an einem Netzproblem haengen bleiben.
 *
 * <p>Zwischengespeichert wird das fertige Gesicht, nicht das Bild. Ein Gesicht ist unveraenderlich
 * und darf an alle Spieler gleichzeitig geschickt werden.
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

    /**
     * Das Gesicht eines Spielers.
     *
     * <p>Liegt es vor und hat sich der Skin nicht geaendert, kommt es sofort. Sonst wird es
     * neben dem Serverpuls geholt; scheitert das, kommt das Ersatzgesicht.
     */
    CompletableFuture<SkinFace> of(Player player) {
        UUID id = player.getUniqueId();
        URL skin = skinUrl(player);
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

    private static URL skinUrl(Player player) {
        try {
            var profil = player.getPlayerProfile();
            return profil.hasTextures() ? profil.getTextures().getSkin() : null;
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
