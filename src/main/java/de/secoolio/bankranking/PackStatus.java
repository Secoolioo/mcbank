package de.secoolio.bankranking;

import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * Der Zustand der Pack-Auslieferung, als reine Rechnung.
 *
 * <p>Diese Klasse ist aus einem Fehlschlag entstanden, der Stunden gekostet hat: das Pack kam
 * bei niemandem an, und weder Server- noch Client-Protokoll sagten, woran es lag. Die Frage
 * "warum sieht dieser Spieler kein Plakat?" hatte schlicht keine Antwort, weil das Plugin die
 * noetigen Unterscheidungen gar nicht traf - "kein Pack" bedeutete gleichzeitig "nie
 * geschickt", "laeuft noch", "abgelehnt" und "Download gescheitert".
 *
 * <p>Hier stehen deshalb alle Unterscheidungen an einem Ort und ohne Bukkit-Abhaengigkeit,
 * damit sie ohne laufenden Server geprueft werden koennen.
 */
final class PackStatus {

    private PackStatus() {
    }

    /** Wie das Pack ausgeliefert wird - oder warum nicht. */
    enum Delivery {
        /** Eigener Webserver, alles nach Plan. */
        EIGENER_SERVER,
        /** Feste Adresse aus der config.yml; der eigene Webserver laeuft gar nicht erst. */
        FREMDE_ADRESSE,
        /** Der eigene Port liess sich nicht binden - es geht ueber die Ablage. */
        RUECKFALL_PORT,
        /** Keine erreichbare eigene Adresse gefunden - es geht ueber die Ablage. */
        RUECKFALL_ADRESSE,
        /** Das Pack liess sich nicht bauen - es geht ueber die Ablage. */
        RUECKFALL_BAUFEHLER,
        /** Gar keine Auslieferung moeglich. */
        KEIN_PACK,
        /** In der config.yml abgeschaltet. */
        ABGESCHALTET;

        /** Soll der Betreiber daraufhin etwas tun? */
        boolean istProblem() {
            return this == RUECKFALL_PORT || this == RUECKFALL_ADRESSE
                    || this == RUECKFALL_BAUFEHLER || this == KEIN_PACK;
        }
    }

    /** Wie weit das Pack bei einem einzelnen Spieler gekommen ist. */
    enum Reach {
        GELADEN,
        UNTERWEGS,
        ANGEBOT_LAEUFT,
        NIE_GESCHICKT,
        ABGELEHNT,
        DOWNLOAD_GESCHEITERT,
        NACHGEREICHT
    }

    /**
     * Die Wahrheitstabelle je Spieler.
     *
     * @param angeboten    ob ueberhaupt je eine Anfrage hinausging
     * @param unterwegs    ob der Client zuletzt ein Lebenszeichen gab (angenommen, geladen)
     * @param antwort      der letzte Endzustand, oder {@code null} wenn noch keiner kam
     * @param nachgereicht ob bereits ueber die oeffentliche Ablage nachgereicht wurde
     */
    static Reach reachOf(boolean angeboten, boolean unterwegs, ResourcePackStatus antwort,
                         boolean nachgereicht) {
        if (antwort == ResourcePackStatus.SUCCESSFULLY_LOADED) {
            return Reach.GELADEN;
        }
        if (antwort == ResourcePackStatus.DECLINED) {
            return Reach.ABGELEHNT;
        }
        if (antwort != null) {
            // Alles andere ist ein Fehlschlag. Wurde schon nachgereicht, ist das die
            // wichtigere Auskunft - der Betreiber soll sehen, dass sich etwas getan hat.
            return nachgereicht ? Reach.NACHGEREICHT : Reach.DOWNLOAD_GESCHEITERT;
        }
        if (unterwegs) {
            return Reach.UNTERWEGS;
        }
        return angeboten ? Reach.ANGEBOT_LAEUFT : Reach.NIE_GESCHICKT;
    }

    /**
     * Uebersetzt den Bukkit-Zustand in den von Adventure.
     *
     * <p>Beide Aufzaehlungen haben dieselben acht Werte; der Switch ist trotzdem vollstaendig
     * ausgeschrieben statt ueber den Namen abgebildet. Kommt in einer kuenftigen Fassung ein
     * neunter Wert dazu, bricht damit die Uebersetzung - statt still zu verschwinden.
     */
    static ResourcePackStatus of(PlayerResourcePackStatusEvent.Status status) {
        return switch (status) {
            case SUCCESSFULLY_LOADED -> ResourcePackStatus.SUCCESSFULLY_LOADED;
            case DECLINED -> ResourcePackStatus.DECLINED;
            case FAILED_DOWNLOAD -> ResourcePackStatus.FAILED_DOWNLOAD;
            case ACCEPTED -> ResourcePackStatus.ACCEPTED;
            case DOWNLOADED -> ResourcePackStatus.DOWNLOADED;
            case INVALID_URL -> ResourcePackStatus.INVALID_URL;
            case FAILED_RELOAD -> ResourcePackStatus.FAILED_RELOAD;
            case DISCARDED -> ResourcePackStatus.DISCARDED;
        };
    }

    /** Ist das ein Fehlschlag, bei dem sich ein anderer Weg lohnt? */
    static boolean istErreichbarkeitsproblem(ResourcePackStatus status) {
        return status == ResourcePackStatus.FAILED_DOWNLOAD
                || status == ResourcePackStatus.INVALID_URL;
    }

    /** Das gewaehlte Ziel: wie ausgeliefert wird und unter welcher Adresse. */
    record Target(Delivery delivery, String url) {
    }

    /**
     * Die Zielwahl beim Start, herausgeloest aus dem Ablauf und damit pruefbar.
     *
     * @param aktiv           Schalter aus der config.yml
     * @param adresse         der eingetragene Wert; leer bedeutet "selbst ermitteln"
     * @param serverGestartet ob der eigene Webserver laeuft
     * @param eigeneAdresse   die ermittelte eigene Adresse, oder {@code null}
     * @param rueckfall       die oeffentliche Ablage
     */
    static Target choose(boolean aktiv, String adresse, boolean serverGestartet,
                         String eigeneAdresse, String rueckfall) {
        if (!aktiv) {
            return new Target(Delivery.ABGESCHALTET, "");
        }
        if (adresse != null && (adresse.startsWith("http://") || adresse.startsWith("https://"))) {
            return new Target(Delivery.FREMDE_ADRESSE, adresse);
        }
        if (!serverGestartet) {
            return new Target(Delivery.RUECKFALL_PORT, rueckfall);
        }
        if (eigeneAdresse == null || eigeneAdresse.isBlank()
                || "127.0.0.1".equals(eigeneAdresse)) {
            // Loopback waere das Schlimmste: im Log staende eine plausible Adresse, der
            // Selbsttest gelaenge, und kein einziger Spieler kaeme an das Pack.
            return new Target(Delivery.RUECKFALL_ADRESSE, rueckfall);
        }
        return new Target(Delivery.EIGENER_SERVER, eigeneAdresse);
    }

    /**
     * Liegt das Pack in einem Netz, das der Spieler gar nicht erreichen kann?
     *
     * <p>Der haeufigste stille Fehler ist eine Docker-Bruecke: das Plugin traegt 172.17.0.1
     * als Pack-Adresse ein, das sieht im Log plausibel aus, und niemand kommt heran. Ein
     * Vergleich mit der Adresse, aus der der Spieler kommt, faellt sofort auf.
     */
    static boolean widerspruch(String packHost, String spielerHost) {
        if (packHost == null || spielerHost == null) {
            return false;
        }
        String a = netz(packHost);
        String b = netz(spielerHost);
        return a != null && b != null && !a.equals(b);
    }

    /** Die ersten beiden Stellen einer IPv4-Adresse, sonst {@code null}. */
    private static String netz(String host) {
        String[] teile = host.split("\\.");
        if (teile.length != 4) {
            return null;
        }
        for (String teil : teile) {
            if (teil.isEmpty() || !teil.chars().allMatch(Character::isDigit)) {
                return null;
            }
        }
        return teile[0] + "." + teile[1];
    }
}
