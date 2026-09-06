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

import io.papermc.paper.connection.PlayerConfigurationConnection;
import io.papermc.paper.event.connection.configuration.AsyncPlayerConnectionConfigureEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackStatus;
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

    /**
     * Der Ausweichweg, wenn der eingebaute Webserver einen Spieler nicht erreicht.
     *
     * <p>Das ist der haeufigste Fall, den ein Betreiber nicht bemerkt: der Port ist in der
     * Firewall zu, das Plugin haelt alles fuer in Ordnung (sein Selbsttest geht ja nur an sich
     * selbst), und die Spieler bekommen still die Sparfassung. Statt darauf zu warten, dass
     * jemand das Log liest, wird bei einem fehlgeschlagenen Download einmal ueber diese
     * Adresse nachgereicht.
     */
    private static final String FALLBACK_URL =
            "https://github.com/Secoolioo/mcbank/releases/latest/download/kopfgeld.zip";

    private final BankRankingPlugin plugin;
    private final ResourcePackFile file;
    private final ResourcePackServer server;
    /**
     * Ab wie vielen verschiedenen Spielern mit gescheitertem Download der eingebaute Webserver
     * als unerreichbar gilt.
     *
     * <p>Ein einzelner Fehlschlag kann an einem Spieler liegen - zwei verschiedene nicht mehr.
     * Das ist die einzige Pruefung, die der Server ueberhaupt anstellen kann: seinen eigenen
     * Port von aussen zu testen ist ihm unmoeglich, denn sein Selbsttest laeuft nur gegen sich
     * selbst und gelingt auch dann, wenn die Firewall alle anderen aussperrt.
     */
    private static final int UMSCHALTEN_AB = 2;

    /** Nicht final: bei dauerhafter Unerreichbarkeit wird auf die Ablage umgestellt. */
    private volatile String url;
    private final Set<UUID> loaded = Collections.synchronizedSet(new HashSet<>());
    /** Wem schon einmal ueber den Ausweichweg nachgereicht wurde - genau einmal je Spieler. */
    private final Set<UUID> retried = Collections.synchronizedSet(new HashSet<>());
    /** Wer das Pack bereits in der Konfigurationsphase bekommen hat. */
    private final Set<UUID> configured = Collections.synchronizedSet(new HashSet<>());
    /** Bei wem der Download gescheitert ist - zaehlt je Spieler nur einmal. */
    private final Set<UUID> failed = Collections.synchronizedSet(new HashSet<>());

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

            ResourcePackServer server;
            try {
                server = ResourcePackServer.start("", settings.packPort(), datei);
            } catch (IOException e) {
                // Der haeufigste Grund ist ein belegter Port - etwa weil beim Neustart der alte
                // Serverprozess ihn noch haelt. Frueher schaltete das Plugin daraufhin das ganze
                // Pack ab, und zwar still: die Spieler bekamen ohne jede Meldung die Sparfassung,
                // also weder Plakatgrafik noch eigene Klaenge. Ein unerreichbarer eigener Port ist
                // aber kein Grund, auf das Pack zu verzichten - es liegt ja auch veroeffentlicht.
                plugin.getLogger().warning("Der eingebaute Webserver konnte nicht auf Port "
                        + settings.packPort() + " starten (" + e.getMessage() + "). Das Pack wird "
                        + "stattdessen ueber " + FALLBACK_URL + " ausgeliefert. Ein anderer Wert "
                        + "unter resourcepack.port bringt den eigenen Webserver zurueck.");
                return new ResourcePacks(plugin, datei, null, FALLBACK_URL);
            }
            String host = adresse.isEmpty() ? localAddress() : adresse;
            if ("127.0.0.1".equals(host) && adresse.isEmpty()) {
                // Ein stiller Rueckfall auf Loopback waere das Schlimmste: im Log staende eine
                // plausible Adresse, der Selbsttest gelaenge, und kein einziger Spieler kaeme
                // an das Pack - denn bei ihm zeigt 127.0.0.1 auf seinen eigenen Rechner.
                plugin.getLogger().warning("Es liess sich keine von aussen erreichbare Adresse "
                        + "ermitteln; das Pack wuerde unter 127.0.0.1 angeboten und waere fuer "
                        + "niemanden erreichbar. Bitte resourcepack.adresse in der config.yml "
                        + "setzen. Bis dahin wird das Pack ueber die oeffentliche Ablage "
                        + "ausgeliefert.");
                return new ResourcePacks(plugin, datei, server, FALLBACK_URL);
            }
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
     * Namensanfaenge von Schnittstellen, die kein Mitspieler je erreicht.
     *
     * <p>{@code NetworkInterface#isVirtual()} hilft hier nicht: das meldet nur Unter-
     * schnittstellen wie {@code eth0:1}. Eine Docker-Bruecke ist danach eine ganz normale
     * Karte - und ihre Adresse (etwa 172.17.0.1) landete als Pack-Adresse im Log, wo sie
     * plausibel aussieht und fuer niemanden erreichbar ist.
     */
    private static final List<String> KUENSTLICH = List.of(
            "docker", "br-", "veth", "virbr", "vmnet", "vboxnet", "tun", "tap", "wg", "zt",
            "lo", "cni", "flannel", "kube", "tailscale");

    /**
     * Die Adresse, unter der die Mitspieler diesen Server erreichen.
     *
     * <p>Zuerst wird die Betriebssystem-Wegetabelle befragt: eine UDP-Verbindung nach aussen
     * schickt nichts, legt aber die lokale Adresse fest, ueber die der Rechner nach draussen
     * spricht. Das ist die einzige Auskunft, die auch bei mehreren Netzwerkkarten stimmt.
     * Erst wenn das scheitert, werden die Karten selbst durchgesehen.
     */
    static String localAddress() {
        try (java.net.DatagramSocket sonde = new java.net.DatagramSocket()) {
            sonde.connect(InetAddress.getByName("1.1.1.1"), 9);
            InetAddress lokal = sonde.getLocalAddress();
            if (lokal instanceof Inet4Address && !lokal.isAnyLocalAddress()
                    && !lokal.isLoopbackAddress()) {
                return lokal.getHostAddress();
            }
        } catch (Exception e) {
            // Kein Weg nach draussen: dann eben ueber die Karten.
        }

        String oeffentlich = null;
        try {
            for (NetworkInterface karte : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!karte.isUp() || karte.isLoopback() || istKuenstlich(karte.getName())) {
                    continue;
                }
                for (InetAddress adresse : Collections.list(karte.getInetAddresses())) {
                    if (!(adresse instanceof Inet4Address) || adresse.isLoopbackAddress()) {
                        continue;
                    }
                    if (adresse.isSiteLocalAddress()) {
                        return adresse.getHostAddress();
                    }
                    // Eine oeffentliche Adresse ist besser als gar keine: ein gemieteter
                    // Server hat ueberhaupt keine private, und 127.0.0.1 erreicht niemand.
                    if (oeffentlich == null) {
                        oeffentlich = adresse.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            // Faellt unten zurueck.
        }
        return oeffentlich != null ? oeffentlich : "127.0.0.1";
    }

    private static boolean istKuenstlich(String name) {
        String klein = name.toLowerCase(java.util.Locale.ROOT);
        for (String anfang : KUENSTLICH) {
            if (klein.startsWith(anfang)) {
                return true;
            }
        }
        return false;
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

    /**
     * Schickt das Pack, sobald sich jemand verbindet - noch waehrend der Konfigurationsphase.
     *
     * <p>Das ist der entscheidende Unterschied zum ersten Entwurf, der es erst beim Beitritt
     * schickte. Ein Pack, das waehrend des Spiels ankommt, zwingt den Client zu einem
     * vollstaendigen Neuaufbau saemtlicher Ressourcen - alle Texturatlanten, alle Modelle.
     * Auf einem stark modifizierten Client dauert das so lange, dass der Render-Thread steht
     * und die Lebenszeichen des Servers unbeantwortet bleiben; der Server wirft den Spieler
     * daraufhin wegen Zeitueberschreitung hinaus, er verbindet neu, und das Ganze beginnt von
     * vorn. Genau dieser Kreis war im Client-Protokoll eines Spielers zu sehen.
     *
     * <p>In der Konfigurationsphase ist der Spieler noch nicht in der Welt. Der Client laedt
     * das Pack dort als Teil des ohnehin stattfindenden Ladevorgangs - kein zusaetzlicher
     * Neuaufbau, kein Einfrieren.
     */
    @EventHandler
    public void onConfigure(AsyncPlayerConnectionConfigureEvent event) {
        PlayerConfigurationConnection verbindung = event.getConnection();
        UUID id;
        try {
            id = verbindung.getProfile().getId();
        } catch (RuntimeException e) {
            return;
        }
        if (id == null) {
            return;
        }
        this.configured.add(id);
        send(verbindung.getAudience(), this.url, id,
                verbindung.getProfile().getName());
    }

    /**
     * Schickt die Anfrage an einen Spieler, der schon in der Welt ist.
     *
     * <p>Nur als Rueckfall: wer das Pack bereits beim Verbinden bekommen hat, bekommt es hier
     * nicht noch einmal - ein zweites Mal loeste genau den Neuaufbau aus, den die
     * Konfigurationsphase gerade vermeidet.
     */
    void send(Player player) {
        if (this.configured.contains(player.getUniqueId())) {
            return;
        }
        send(player, this.url);
    }

    private void send(Player player, String adresse) {
        send(player, adresse, player.getUniqueId(), player.getName());
    }

    private void send(Audience empfaenger, String adresse, UUID id, String name) {
        empfaenger.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(adresse),
                        this.file.sha1()))
                // Kein Kick: der Zwang haengt ohnehin an require-resource-pack in den
                // server.properties, und wir wollen ihn ausdruecklich nicht.
                .required(false)
                // Nicht die Packs anderer Plugins verdraengen - die feste Kennung sorgt
                // dafuer, dass nur unser eigener Eintrag ersetzt wird.
                .replace(false)
                .prompt(Messages.mm(this.plugin.settings().packPrompt()))
                // Der Rueckruf kommt auch in der Konfigurationsphase, in der es noch keinen
                // Player und damit kein PlayerResourcePackStatusEvent gibt.
                .callback((packId, status, audience) -> record(id, name, status, audience))
                .build());
    }

    /**
     * Haelt den Zustand fest - unabhaengig davon, aus welcher Phase er kommt.
     *
     * <p>In der Konfigurationsphase gibt es noch keinen Spieler und damit auch kein
     * {@link PlayerResourcePackStatusEvent}. Nur dieser Rueckruf kommt in beiden Phasen an,
     * deshalb haengt die Erkennung eines unerreichbaren Ports hier.
     */
    private void record(UUID id, String name, ResourcePackStatus status, Audience empfaenger) {
        if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) {
            this.loaded.add(id);
            return;
        }
        if (status.intermediate()) {
            return;
        }
        this.loaded.remove(id);
        if (status == ResourcePackStatus.FAILED_DOWNLOAD
                || status == ResourcePackStatus.INVALID_URL) {
            handleFailure(id, name, empfaenger);
        }
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
        this.retried.clear();
        this.configured.clear();
        this.failed.clear();
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
            return;
        }
        if (isIntermediate(event.getStatus())) {
            return;
        }
        this.loaded.remove(id);
        if (isReachabilityProblem(event.getStatus())) {
            handleFailure(id, event.getPlayer().getName(), event.getPlayer());
        }
    }

    /** Lag es an der Erreichbarkeit - oder hat der Spieler schlicht abgelehnt? */
    static boolean isReachabilityProblem(PlayerResourcePackStatusEvent.Status status) {
        return status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD
                || status == PlayerResourcePackStatusEvent.Status.INVALID_URL;
    }

    /**
     * Reicht das Pack einmalig ueber die oeffentliche Adresse nach.
     *
     * <p>Nur solange es unveraendert ist: hat der Betreiber eigene Dateien eingelegt, weicht
     * sein Pack vom veroeffentlichten ab, und der Client wuerde es wegen des anderen Hashes
     * ohnehin verwerfen.
     */
    private void handleFailure(UUID id, String name, Audience empfaenger) {
        if (!this.file.replaced().isEmpty() || FALLBACK_URL.equals(this.url)) {
            // Eigene Dateien im Pack: das veroeffentlichte weicht ab, sein Hash passt nicht,
            // und der Client wuerde es ohnehin verwerfen. Oder es laeuft laengst ueber die Ablage.
            return;
        }
        boolean neu = this.failed.add(id);
        if (neu && this.failed.size() >= UMSCHALTEN_AB) {
            String bisher = this.url;
            this.url = FALLBACK_URL;
            this.plugin.getLogger().warning("Bei " + this.failed.size() + " verschiedenen "
                    + "Spielern ist der Download unter " + bisher + " gescheitert. Der Port ist "
                    + "also von aussen nicht erreichbar - fast immer die Firewall des Servers. "
                    + "Alle weiteren Spieler bekommen das Pack ab sofort ueber " + FALLBACK_URL
                    + ". Dauerhaft behebt es: firewall-cmd --permanent --add-port="
                    + this.plugin.settings().packPort() + "/tcp && firewall-cmd --reload");
        }
        if (this.retried.add(id)) {
            this.plugin.getLogger().info(name + " konnte das Resourcepack nicht laden - "
                    + "es wird ueber " + FALLBACK_URL + " nachgereicht.");
            send(empfaenger, FALLBACK_URL, id, name);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        this.loaded.remove(event.getPlayer().getUniqueId());
        this.retried.remove(event.getPlayer().getUniqueId());
        this.configured.remove(event.getPlayer().getUniqueId());
    }

    /** Der Zustand je Spieler, fuer die Verwaltung. */
    java.util.Map<String, String> status() {
        java.util.Map<String, String> zeilen = new java.util.LinkedHashMap<>();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            zeilen.put(player.getName(), this.loaded.contains(player.getUniqueId())
                    ? "geladen"
                    : this.retried.contains(player.getUniqueId())
                            ? "Download gescheitert, ueber die oeffentliche Adresse nachgereicht"
                            : "kein Pack");
        }
        return zeilen;
    }
}
