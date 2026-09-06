package de.secoolio.bankranking;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Schickt das Resourcepack an die Spieler und merkt sich, wer es wirklich hat.
 *
 * <p>Niemand wird gekickt. Wer ablehnt oder dessen Download scheitert, bekommt die Sparfassung
 * aus gewoehnlichen Textzeichen - deshalb muss das Plugin je Spieler wissen, woran es ist.
 *
 * <p>Nur der Zustand {@code SUCCESSFULLY_LOADED} zaehlt. {@code ACCEPTED} und {@code DOWNLOADED}
 * sind Zwischenstaende: wer darauf schon umschaltet, zeigt das Plakat, bevor die Schrift geladen
 * ist, und der Spieler sieht Kaestchen.
 */
public final class ResourcePacks implements Listener {

    /**
     * Die feste Kennung dieses Packs.
     *
     * <p>Aus dem Namen abgeleitet und damit ueber alle Serverstarts gleich. Ein neues Pack mit
     * derselben Kennung ersetzt beim Client das alte, statt sich daneben zu stellen - und
     * eingehende Zustandsmeldungen lassen sich sauber von denen anderer Plugins trennen.
     */
    public static final UUID PACK_ID =
            UUID.nameUUIDFromBytes("bankranking-kopfgeld".getBytes(StandardCharsets.UTF_8));

    private final BankRankingPlugin plugin;
    private final ResourcePackFile file;
    private final ResourcePackServer server;
    private final String url;
    private final Set<UUID> loaded = Collections.synchronizedSet(new HashSet<>());

    private ResourcePacks(BankRankingPlugin plugin, ResourcePackFile file,
                          ResourcePackServer server, String url) {
        this.plugin = plugin;
        this.file = file;
        this.server = server;
        this.url = url;
    }

    /**
     * Baut das Pack und startet bei Bedarf den Webserver.
     *
     * @return die fertige Auslieferung, oder {@code null} wenn sie nicht zustande kam. Ein
     *         Fehlschlag ist nie ein Grund, das Plugin nicht zu starten - die Bank muss
     *         weiterlaufen, auch wenn das Plakat schlicht aussieht.
     */
    static ResourcePacks start(BankRankingPlugin plugin) {
        Settings settings = plugin.settings();
        if (!settings.packEnabled()) {
            return null;
        }
        try {
            ResourcePackFile datei = ResourcePackFile.build(plugin.getDataFolder().toPath(),
                    plugin.getLogger());
            Path ablage = plugin.getDataFolder().toPath().resolve("pack").resolve("kopfgeld.zip");
            datei.writeTo(ablage);

            String adresse = settings.packAddress();
            if (adresse.startsWith("http://") || adresse.startsWith("https://")) {
                // Fremde Ablage: der eingebaute Webserver wird gar nicht erst gestartet.
                plugin.getLogger().info("Resourcepack kommt von " + adresse
                        + " (SHA-1 " + datei.sha1() + ", " + kilobyte(datei) + " KB)");
                return new ResourcePacks(plugin, datei, null, adresse);
            }

            ResourcePackServer server = ResourcePackServer.start("", settings.packPort(), datei);
            String host = adresse.isEmpty() ? localAddress() : adresse;
            String url = server.url(host);
            plugin.getLogger().info("Resourcepack wird ausgeliefert unter " + url
                    + " (" + kilobyte(datei) + " KB, SHA-1 " + datei.sha1() + ")");
            if (!datei.replaced().isEmpty()) {
                plugin.getLogger().info("Eigene Dateien aus pack-eigene uebernommen: "
                        + String.join(", ", datei.replaced()));
            }
            return new ResourcePacks(plugin, datei, server, url);
        } catch (IOException e) {
            plugin.getLogger().warning("Das Resourcepack konnte nicht bereitgestellt werden ("
                    + e.getMessage() + "). Das Kopfgeld laeuft in der Sparfassung weiter; "
                    + "bei einem belegten Port hilft ein anderer Wert unter resourcepack.port.");
            return null;
        }
    }

    private static long kilobyte(ResourcePackFile datei) {
        return Math.round(datei.size() / 1024.0);
    }

    /**
     * Die eigene Adresse im lokalen Netz.
     *
     * <p>{@code InetAddress.getLocalHost()} liefert auf vielen Systemen nur 127.0.0.1 - damit
     * kaeme kein einziger Mitspieler an das Pack. Deshalb werden die Netzwerkkarten selbst
     * durchgesehen und die erste Adresse im privaten Bereich genommen.
     */
    static String localAddress() {
        try {
            for (NetworkInterface karte : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!karte.isUp() || karte.isLoopback() || karte.isVirtual()) {
                    continue;
                }
                for (InetAddress adresse : Collections.list(karte.getInetAddresses())) {
                    if (adresse instanceof Inet4Address && adresse.isSiteLocalAddress()) {
                        return adresse.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            // Faellt unten auf localhost zurueck.
        }
        return "127.0.0.1";
    }

    /**
     * Holt das Pack ueber die eigene Adresse ab und vergleicht es mit dem, was ausgeliefert
     * werden soll.
     *
     * <p>Das beantwortet vor dem ersten Spieler die Frage, die sonst erst im Spiel auffiele:
     * ist die Adresse ueberhaupt erreichbar und stimmt der Hash?
     */
    boolean selfTest() {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build();
            HttpResponse<byte[]> antwort = client.send(
                    HttpRequest.newBuilder(URI.create(this.url))
                            .timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (antwort.statusCode() != 200) {
                this.plugin.getLogger().warning("Selbsttest des Resourcepacks: " + this.url
                        + " antwortet mit " + antwort.statusCode());
                return false;
            }
            if (antwort.body().length != this.file.size()) {
                this.plugin.getLogger().warning("Selbsttest des Resourcepacks: unter " + this.url
                        + " liegt eine andere Datei als erwartet");
                return false;
            }
            return true;
        } catch (Exception e) {
            this.plugin.getLogger().warning("Selbsttest des Resourcepacks fehlgeschlagen: "
                    + this.url + " ist nicht erreichbar (" + e.getMessage()
                    + "). Spieler bekommen die Sparfassung.");
            return false;
        }
    }

    /** Schickt die Anfrage an einen Spieler. */
    void send(Player player) {
        player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(this.url),
                        this.file.sha1()))
                // Kein Kick: der Zwang haengt ohnehin an require-resource-pack in den
                // server.properties, und wir wollen ihn ausdruecklich nicht.
                .required(false)
                // Nicht die Packs anderer Plugins verdraengen - die feste Kennung sorgt
                // dafuer, dass nur unser eigener Eintrag ersetzt wird.
                .replace(false)
                .prompt(Messages.mm(this.plugin.settings().packPrompt()))
                .build());
    }

    /** Hat dieser Spieler unser Pack geladen? */
    public boolean has(Player player) {
        return this.loaded.contains(player.getUniqueId());
    }

    /** Wie viele Spieler das Pack gerade geladen haben. */
    int loadedCount() {
        return this.loaded.size();
    }

    String url() {
        return this.url;
    }

    String sha1() {
        return this.file.sha1();
    }

    List<String> replaced() {
        return this.file.replaced();
    }

    void stop() {
        if (this.server != null) {
            this.server.stop();
        }
        this.loaded.clear();
    }

    /** Nur der Endzustand zaehlt - Zwischenstaende wuerden zu frueh umschalten. */
    static boolean isActive(PlayerResourcePackStatusEvent.Status status) {
        return status == PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;
    }

    /** Ist der Zustand nur eine Zwischenmeldung, nach der noch etwas kommt? */
    static boolean isIntermediate(PlayerResourcePackStatusEvent.Status status) {
        return status == PlayerResourcePackStatusEvent.Status.ACCEPTED
                || status == PlayerResourcePackStatusEvent.Status.DOWNLOADED;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStatus(PlayerResourcePackStatusEvent event) {
        // Andere Plugins duerfen eigene Packs schicken; nur unseres zaehlt hier.
        if (!PACK_ID.equals(event.getID())) {
            return;
        }
        UUID id = event.getPlayer().getUniqueId();
        if (isActive(event.getStatus())) {
            this.loaded.add(id);
        } else if (!isIntermediate(event.getStatus())) {
            this.loaded.remove(id);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.loaded.remove(event.getPlayer().getUniqueId());
    }
}
