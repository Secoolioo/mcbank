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

import com.destroystokyo.paper.profile.PlayerProfile;
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

    /** Wie lange der Beitritt auf eine Antwort aus der Konfigurationsphase wartet. */
    private static final long NACHREICHEN_TICKS = 200L;

    /** Nicht final: bei dauerhafter Unerreichbarkeit wird auf die Ablage umgestellt. */
    private volatile String url;
    /** Wie ausgeliefert wird - oder warum nicht. */
    private volatile PackStatus.Delivery delivery;
    /** Die fertige Anfrage samt Hash; bei fremder Adresse aus der echten Quelle. */
    private volatile ResourcePackInfo info;
    private final Set<UUID> loaded = Collections.synchronizedSet(new HashSet<>());
    /** Wem schon einmal ueber den Ausweichweg nachgereicht wurde - genau einmal je Spieler. */
    private final Set<UUID> retried = Collections.synchronizedSet(new HashSet<>());
    /** Wer das Pack bereits in der Konfigurationsphase bekommen hat. */
    private final Set<UUID> configured = Collections.synchronizedSet(new HashSet<>());
    /** Bei wem der Download gescheitert ist - zaehlt je Spieler nur einmal. */
    private final Set<UUID> failed = Collections.synchronizedSet(new HashSet<>());
    /** An wen ueberhaupt je eine Anfrage hinausging - der Unterschied zu "abgelehnt". */
    private final Set<UUID> offered = Collections.synchronizedSet(new HashSet<>());
    /** Wer in dieser Sitzung schon einen Hinweis bekommen hat - hoechstens einer je Beitritt. */
    private final Set<UUID> hingewiesen = Collections.synchronizedSet(new HashSet<>());
    /** Wer gerade laedt: hat angenommen oder heruntergeladen, aber noch nicht fertig. */
    private final Set<UUID> pending = Collections.synchronizedSet(new HashSet<>());
    /** Der letzte Endzustand je Spieler. */
    private final java.util.Map<UUID, ResourcePackStatus> answer =
            new java.util.concurrent.ConcurrentHashMap<>();

    private ResourcePacks(BankRankingPlugin plugin, ResourcePackFile file,
                          ResourcePackServer server, String url,
                          PackStatus.Delivery delivery) {
        this.plugin = plugin;
        this.file = file;
        this.server = server;
        this.url = url;
        this.delivery = delivery;
        this.info = file == null ? null
                : ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(url), file.sha1());
        if (delivery == PackStatus.Delivery.FREMDE_ADRESSE) {
            adoptRemoteHash(url);
        }
    }

    /**
     * Holt den Hash aus der Datei, die dort tatsaechlich liegt.
     *
     * <p>Das behebt einen Fehler, der die ganze Auslieferung lahmlegte und dabei voellig
     * stumm blieb: liegt im Ordner pack-eigene irgendeine Datei, packt das Plugin das Pack
     * selbst neu, und ein selbst gepacktes ZIP hat nie denselben SHA-1 wie das von Gradle
     * gebaute - auch bei byteweise gleichem Inhalt, denn Gradle schreibt Verzeichniseintraege
     * und einen anderen Nullzeitstempel. Angekuendigt wurde trotzdem der lokale Hash, waehrend
     * die fremde Adresse das veroeffentlichte Pack lieferte. Der Client verglich und verwarf.
     *
     * <p>Bei einer fremden Adresse ist der lokale Bau also keine verlaessliche Quelle. Bis der
     * echte Hash vorliegt, wird weiter mit dem lokalen ausgeliefert - das ist nicht schlechter
     * als bisher und wird binnen Sekunden ersetzt.
     */
    private void adoptRemoteHash(String adresse) {
        try {
            ResourcePackInfo.resourcePackInfo()
                    .id(PACK_ID)
                    .uri(URI.create(adresse))
                    .computeHashAndBuild(runnable ->
                            this.plugin.getServer().getScheduler()
                                    .runTaskAsynchronously(this.plugin, runnable))
                    .thenAccept(fertig -> {
                        this.info = fertig;
                        this.plugin.getLogger().info("Hash der ausgelieferten Datei: "
                                + fertig.hash()
                                + (this.file != null && fertig.hash().equals(this.file.sha1())
                                        ? " (gleich dem hier gebauten)"
                                        : " (weicht vom hier gebauten ab - es gilt der echte)"));
                    })
                    .exceptionally(fehler -> {
                        this.plugin.getLogger().warning("Der Hash von " + adresse
                                + " liess sich nicht bestimmen (" + fehler
                                + "). Es gilt der hier gebaute.");
                        return null;
                    });
        } catch (RuntimeException e) {
            this.plugin.getLogger().warning("Der Hash von " + adresse
                    + " liess sich nicht bestimmen (" + e + "). Es gilt der hier gebaute.");
        }
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
            // Diese Zeile ist wichtiger, als sie aussieht: ohne sie war das Abschalten des
            // Packs der einzige Zustand, der ueberhaupt keine Spur hinterliess. Weder Server-
            // noch Client-Log erwaehnten das Thema mit einem Wort, und die Suche nach der
            // Ursache lief zwangslaeufig ins Leere.
            plugin.getLogger().info("Resourcepack ist in der config.yml abgeschaltet "
                    + "(resourcepack.aktiv: false) - das Kopfgeld zeigt die Sparfassung.");
            // Kein null mehr: "abgeschaltet" ist ein Zustand des Objekts. Frueher fehlte
            // dann das Objekt, der Listener wurde nicht angemeldet, und JEDER Beitritt blieb
            // stumm - waehrend die Ursache genau einmal beim Start im Log stand, also dort,
            // wo der Betreiber im Zweifel nicht nachsieht.
            return new ResourcePacks(plugin, null, null, "",
                    PackStatus.Delivery.ABGESCHALTET);
        }
        try {
            ResourcePackFile datei = ResourcePackFile.build(plugin.getDataFolder().toPath(),
                    plugin.getLogger());
            try {
                Path ablage = plugin.getDataFolder().toPath()
                        .resolve("pack").resolve("kopfgeld.zip");
                datei.writeTo(ablage);
            } catch (IOException e) {
                // Diese Kopie dient allein dem Nachsehen von Hand; ausgeliefert wird ohnehin
                // aus dem Speicher. Sie lag bisher im grossen Schutzblock, und damit riss ein
                // blosses Rechteproblem im Plugin-Ordner die gesamte Auslieferung mit.
                plugin.getLogger().warning("Die Kopie des Packs liess sich nicht ablegen ("
                        + e.getMessage() + "). Ausgeliefert wird trotzdem.");
            }

            String adresse = settings.packAddress();
            if (adresse.startsWith("http://") || adresse.startsWith("https://")) {
                // Fremde Ablage: der eingebaute Webserver wird gar nicht erst gestartet.
                plugin.getLogger().info("Resourcepack kommt von " + adresse
                        + " (SHA-1 " + datei.sha1() + ", " + kilobyte(datei) + " KB)");
                return new ResourcePacks(plugin, datei, null, adresse,
                        PackStatus.Delivery.FREMDE_ADRESSE);
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
                return new ResourcePacks(plugin, datei, null, FALLBACK_URL,
                        PackStatus.Delivery.RUECKFALL_PORT);
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
                return new ResourcePacks(plugin, datei, server, FALLBACK_URL,
                        PackStatus.Delivery.RUECKFALL_ADRESSE);
            }
            String url = server.url(host);
            plugin.getLogger().info("Resourcepack wird ausgeliefert unter " + url
                    + " (" + kilobyte(datei) + " KB, SHA-1 " + datei.sha1() + ")");
            if (!datei.replaced().isEmpty()) {
                plugin.getLogger().info("Eigene Dateien aus pack-eigene uebernommen: "
                        + String.join(", ", datei.replaced()));
            }
            return new ResourcePacks(plugin, datei, server, url,
                    PackStatus.Delivery.EIGENER_SERVER);
        } catch (IOException e) {
            plugin.getLogger().warning("Das Resourcepack konnte nicht bereitgestellt werden ("
                    + e.getMessage() + "). Das Kopfgeld laeuft in der Sparfassung weiter; "
                    + "bei einem belegten Port hilft ein anderer Wert unter resourcepack.port. "
                    + "Es wird stattdessen ueber " + FALLBACK_URL + " ausgeliefert.");
            return new ResourcePacks(plugin, null, null, FALLBACK_URL,
                    PackStatus.Delivery.RUECKFALL_BAUFEHLER);
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
    /** Das Ergebnis des Selbsttests, in einem Satz, fuer Log und Chat zugleich. */
    record SelfTest(boolean ok, String text) {
    }

    /**
     * Holt das Pack von der eigenen Adresse und vergleicht es mit dem, was angekuendigt wird.
     *
     * <p>Verglichen wird der SHA-1, nicht die Groesse. Bei einer fremden Adresse ist die
     * lokale Groesse gar kein Massstab - genau diese Verwechslung hat die Auslieferung schon
     * einmal stillschweigend zerlegt. Der Hash ist das, worauf der Client selbst prueft.
     */
    SelfTest selfTest() {
        String adresse = this.url;
        if (this.delivery == PackStatus.Delivery.ABGESCHALTET) {
            return new SelfTest(false, "Das Resourcepack ist in der config.yml abgeschaltet.");
        }
        if (this.info == null) {
            return new SelfTest(false, "Es liegt kein baubares Pack vor.");
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3)).build();
            HttpResponse<byte[]> antwort = client.send(
                    HttpRequest.newBuilder(URI.create(adresse))
                            .timeout(Duration.ofSeconds(10)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (antwort.statusCode() != 200) {
                return new SelfTest(false, adresse + " antwortet mit "
                        + antwort.statusCode() + ".");
            }
            String gefunden = sha1Of(antwort.body());
            String erwartet = sha1();
            if (!gefunden.equalsIgnoreCase(erwartet)) {
                return new SelfTest(false, "Unter " + adresse + " liegt eine andere Datei als "
                        + "angekuendigt (dort " + gefunden + ", angekuendigt " + erwartet
                        + "). Jeder Client verwirft das Pack.");
            }
            return new SelfTest(true, adresse + " liefert " + kilobyte(antwort.body().length)
                    + " KB mit dem erwarteten Hash.");
        } catch (Exception e) {
            return new SelfTest(false, adresse + " ist nicht erreichbar (" + e.getMessage()
                    + "). Spieler bekommen die Sparfassung.");
        }
    }

    private static long kilobyte(int bytes) {
        return Math.round(bytes / 1024.0);
    }

    private static String sha1Of(byte[] daten) {
        try {
            byte[] roh = java.security.MessageDigest.getInstance("SHA-1").digest(daten);
            StringBuilder sb = new StringBuilder(roh.length * 2);
            for (byte b : roh) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 fehlt in dieser Laufzeit", e);
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
        if (this.delivery == PackStatus.Delivery.ABGESCHALTET) {
            return;
        }
        PlayerConfigurationConnection verbindung = event.getConnection();
        UUID id = null;
        try {
            // Beides hier drin: der zweite Profilzugriff lag frueher ausserhalb des Schutzes,
            // ausgerechnet in dem Fenster, in dem die Vormerkung schon gesetzt war. Eine
            // Ausnahme dort haette den Spieler als versorgt gelten lassen, ohne dass je etwas
            // hinausging - und der Beitrittsweg haette geschwiegen.
            PlayerProfile profil = verbindung.getProfile();
            id = profil.getId();
            String name = profil.getName();
            if (id == null) {
                return;
            }
            this.configured.add(id);
            send(verbindung.getAudience(), this.url, id, name);
        } catch (RuntimeException e) {
            // Ein asynchrones Event verschluckt Ausnahmen fuer den Spieler unsichtbar. Ohne
            // diese Zeile bliebe der haeufigste Fehlerfall voellig spurlos.
            if (id != null) {
                this.configured.remove(id);
            }
            this.plugin.getLogger().warning("Das Pack liess sich in der Konfigurationsphase "
                    + "nicht verschicken (" + e + "). Es wird beim Beitritt nachgereicht.");
        }
    }

    /**
     * Schickt die Anfrage an einen Spieler, der schon in der Welt ist.
     *
     * <p>Dies ist das Sicherheitsnetz unter der Konfigurationsphase, und es haengt bewusst am
     * <em>Ergebnis</em> statt am Versuch: uebersprungen wird nur, wer das Pack nachweislich
     * geladen hat. Es am Versuch aufzuhaengen war ein Fehler - lieferte die Konfigurationsphase
     * still nichts aus, galt der Spieler trotzdem als versorgt, der Beitrittsweg schwieg
     * ebenfalls, und niemand bekam je ein Pack. Genau so sah es im Client-Protokoll aus: keine
     * Pack-Meldung, kein Fehlschlag, gar nichts.
     *
     * <p>Hat die Konfigurationsphase getragen, steht der Spieler laengst in {@code loaded} -
     * der Client meldet den Zustand noch dort, bevor er die Welt betritt. Dann passiert hier
     * nichts, und der teure Neuaufbau mitten im Spiel bleibt aus.
     */
    void send(Player player) {
        if (this.delivery == PackStatus.Delivery.ABGESCHALTET
                || this.loaded.contains(player.getUniqueId())) {
            return;
        }
        send(player, this.url);
    }

    /**
     * Der Beitritt als Sicherheitsnetz unter der Konfigurationsphase.
     *
     * <p>Hier sofort noch einmal zu schicken waere ein Wettlauf: die Antwort des Clients laeuft
     * ueber einen Netz-Thread und kann dem Beitritt nachlaufen. Ein zweites Mal schicken
     * erzwaenge dann genau den Neuaufbau mitten im Spiel, den die Konfigurationsphase gerade
     * vermeidet - also gewartet, bis feststeht, ob ueberhaupt eine Antwort kam.
     */
    void sendOnJoin(Player player) {
        UUID id = player.getUniqueId();
        if (this.delivery == PackStatus.Delivery.ABGESCHALTET || this.loaded.contains(id)) {
            return;
        }
        if (!this.configured.contains(id)) {
            // Die Konfigurationsphase hat diesen Spieler nie gesehen - sofort nachreichen.
            send(player, this.url);
            return;
        }
        this.plugin.getServer().getScheduler().runTaskLater(this.plugin, () -> {
            // pending zaehlt mit: wer gerade laedt, hat geantwortet genug. Ohne diese
            // Bedingung bekam ein Spieler mitten im Download eine zweite Anfrage.
            if (!player.isOnline() || this.answer.containsKey(id)
                    || this.pending.contains(id)) {
                return;
            }
            this.plugin.getLogger().warning(player.getName() + " hat auf das Pack aus der "
                    + "Konfigurationsphase nicht geantwortet - es wird jetzt nachgereicht.");
            send(player, this.url);
        }, NACHREICHEN_TICKS);
    }

    private void send(Player player, String adresse) {
        send(player, adresse, player.getUniqueId(), player.getName());
    }

    private void send(Audience empfaenger, String adresse, UUID id, String name) {
        // Ohne diese Zeile war der gesamte Sendeweg im Log unsichtbar: es liess sich nicht
        // unterscheiden, ob ein Spieler abgelehnt hatte oder ob nie etwas hinausgegangen war.
        ResourcePackInfo anfrage = this.info;
        if (anfrage == null) {
            // Ohne gebautes Pack gibt es keinen Hash und damit keine gueltige Anfrage.
            return;
        }
        if (!adresse.equals(anfrage.uri().toString())) {
            // Der Ausweichweg schickt an eine andere Adresse als die gespeicherte.
            anfrage = ResourcePackInfo.resourcePackInfo(PACK_ID, URI.create(adresse),
                    anfrage.hash());
        }
        this.offered.add(id);
        this.plugin.getLogger().info("Pack-Anfrage an " + name + " -> " + adresse);
        empfaenger.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(anfrage)
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
        if (status.intermediate()) {
            // ANGENOMMEN und HERUNTERGELADEN sind keine Antwort, aber ein Lebenszeichen: der
            // Client arbeitet noch. Frueher gingen sie wortlos verloren, und das Nachreichen
            // beim Beitritt schoss deshalb mitten in einen laufenden Download - also genau
            // der Ressourcen-Neuaufbau im laufenden Spiel, den die Konfigurationsphase
            // vermeiden soll.
            this.pending.add(id);
            return;
        }
        this.pending.remove(id);
        ResourcePackStatus vorher = this.answer.put(id, status);
        if (status == ResourcePackStatus.SUCCESSFULLY_LOADED) {
            this.loaded.add(id);
        } else {
            this.loaded.remove(id);
        }
        if (vorher != status) {
            // Beide Wege - das Bukkit-Ereignis und der Adventure-Rueckruf - laufen hier
            // durch. Ohne diesen Vergleich stuende jede Antwort doppelt im Log.
            this.plugin.getLogger().info(status == ResourcePackStatus.SUCCESSFULLY_LOADED
                    ? "Pack geladen von " + name
                    : "Pack-Antwort von " + name + ": " + status);
        }
        if (PackStatus.istErreichbarkeitsproblem(status)) {
            handleFailure(id, name, empfaenger);
        }
    }

    /** Hat dieser Spieler unser Pack geladen? */
    public boolean has(Player player) {
        return this.loaded.contains(player.getUniqueId());
    }

    /**
     * Schickt sofort und ohne jede Ruecksicht auf den bisherigen Zustand.
     *
     * <p>Nur fuer den Verwaltungsbefehl: damit sich die ganze Kette im Spiel pruefen laesst,
     * ohne den Server neu zu starten.
     */
    /**
     * Sagt einem Administrator beim Beitritt, wenn mit der Auslieferung etwas nicht stimmt.
     *
     * <p>Der Grund fuer diese Methode ist eine Fehlersuche, die Stunden gekostet hat: die
     * Ursache stand die ganze Zeit im Server-Log, und genau dorthin konnte der Betreiber nicht
     * sehen. Gegen Spam vier Sperren: das Recht, ein tatsaechliches Problem, hoechstens eine
     * Zeile je Beitritt, und der Schalter resourcepack.hinweis.
     */
    void notifyAdmin(Player player) {
        if (!this.plugin.settings().packHint()
                || !player.hasPermission("bankranking.admin")
                || !this.hingewiesen.add(player.getUniqueId())) {
            return;
        }
        PackStatus.Delivery zustand = this.delivery;
        if (!zustand.istProblem() && !hashKonflikt()) {
            return;
        }
        this.plugin.send(player, Messages.PACK_HINWEIS_ADMIN);
        this.plugin.send(player, Messages.text(zustand));
        this.plugin.send(player, hashKonflikt()
                ? Messages.PACK_HASH_KONFLIKT
                : Messages.schritt(zustand));
    }

    void sendNow(Player player) {
        this.loaded.remove(player.getUniqueId());
        this.pending.remove(player.getUniqueId());
        this.answer.remove(player.getUniqueId());
        send(player, this.url);
    }

    /** Wie weit das Pack bei diesem Spieler gekommen ist. */
    PackStatus.Reach reachOf(UUID id) {
        return PackStatus.reachOf(this.offered.contains(id), this.pending.contains(id),
                this.answer.get(id), this.retried.contains(id));
    }

    /** Wie viele Spieler das Pack gerade geladen haben. */
    int loadedCount() {
        return this.loaded.size();
    }

    String url() {
        return this.url;
    }

    String sha1() {
        ResourcePackInfo anfrage = this.info;
        return anfrage != null ? anfrage.hash() : this.file == null ? "" : this.file.sha1();
    }

    /** Wie ausgeliefert wird - oder warum nicht. */
    PackStatus.Delivery delivery() {
        return this.delivery;
    }

    /** Liegen eigene Dateien vor, waehrend von fremder Adresse ausgeliefert wird? */
    boolean hashKonflikt() {
        return this.delivery == PackStatus.Delivery.FREMDE_ADRESSE && !replaced().isEmpty();
    }

    List<String> replaced() {
        return this.file == null ? List.of() : this.file.replaced();
    }

    void stop() {
        if (this.server != null) {
            this.server.stop();
        }
        this.loaded.clear();
        this.retried.clear();
        this.configured.clear();
        this.failed.clear();
        this.hingewiesen.clear();
        this.offered.clear();
        this.pending.clear();
        this.answer.clear();
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
        // Genau dieselbe Buchfuehrung wie der Adventure-Rueckruf. Zwei getrennte Zaehlwege
        // fuer denselben Vorgang waren der Grund, warum der Zustand je nach Blickwinkel
        // anders aussah.
        record(event.getPlayer().getUniqueId(), event.getPlayer().getName(),
                PackStatus.of(event.getStatus()), event.getPlayer());
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
        this.hingewiesen.remove(event.getPlayer().getUniqueId());
        this.offered.remove(event.getPlayer().getUniqueId());
        this.pending.remove(event.getPlayer().getUniqueId());
        this.answer.remove(event.getPlayer().getUniqueId());
    }

    /** Der Zustand je Spieler, fuer die Verwaltung. */
    java.util.Map<String, String> status() {
        java.util.Map<String, String> zeilen = new java.util.LinkedHashMap<>();
        for (Player player : this.plugin.getServer().getOnlinePlayers()) {
            zeilen.put(player.getName(), Messages.text(reachOf(player.getUniqueId())));
        }
        return zeilen;
    }
}
