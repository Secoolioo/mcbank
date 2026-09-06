package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.kyori.adventure.resource.ResourcePackStatus;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueft die Unterscheidungen, die beim Fehlschlag dieser Funktion gefehlt haben.
 *
 * <p>Das Pack kam bei niemandem an, und die einzige Auskunft des Plugins lautete "kein Pack" -
 * gleichbedeutend fuer "nie geschickt", "laeuft noch", "abgelehnt" und "Download gescheitert".
 * Genau diese vier auseinanderzuhalten ist der Zweck von {@link PackStatus}.
 */
class PackStatusTest {

    private static final String ABLAGE = "https://example.invalid/kopfgeld.zip";

    @Test
    @DisplayName("Wer nie eine Anfrage bekam, gilt nicht als Ablehner")
    void nieGeschicktIstNichtAbgelehnt() {
        assertEquals(PackStatus.Reach.NIE_GESCHICKT,
                PackStatus.reachOf(false, false, null, false));
        assertEquals(PackStatus.Reach.ANGEBOT_LAEUFT,
                PackStatus.reachOf(true, false, null, false));
    }

    @Test
    @DisplayName("Ein laufender Download ist keine Antwort, aber auch kein Fehlschlag")
    void laufenderDownload() {
        assertEquals(PackStatus.Reach.UNTERWEGS,
                PackStatus.reachOf(true, true, null, false));
    }

    @Test
    @DisplayName("Jeder Endzustand landet in seinem eigenen Fach")
    void endzustaende() {
        assertEquals(PackStatus.Reach.GELADEN, PackStatus.reachOf(true, false,
                ResourcePackStatus.SUCCESSFULLY_LOADED, false));
        assertEquals(PackStatus.Reach.ABGELEHNT, PackStatus.reachOf(true, false,
                ResourcePackStatus.DECLINED, false));
        assertEquals(PackStatus.Reach.DOWNLOAD_GESCHEITERT, PackStatus.reachOf(true, false,
                ResourcePackStatus.FAILED_DOWNLOAD, false));
        assertEquals(PackStatus.Reach.NACHGEREICHT, PackStatus.reachOf(true, false,
                ResourcePackStatus.FAILED_DOWNLOAD, true));
    }

    @Test
    @DisplayName("Geladen schlaegt alles - auch ein frueherer Fehlschlag mit Nachreichung")
    void geladenSchlaegtAlles() {
        assertEquals(PackStatus.Reach.GELADEN, PackStatus.reachOf(true, true,
                ResourcePackStatus.SUCCESSFULLY_LOADED, true));
    }

    @Test
    @DisplayName("Jeder Bukkit-Zustand hat eine Entsprechung")
    void jederZustandUebersetzt() {
        for (PlayerResourcePackStatusEvent.Status status
                : PlayerResourcePackStatusEvent.Status.values()) {
            assertNotNull(PackStatus.of(status), "Keine Entsprechung fuer " + status);
        }
    }

    @Test
    @DisplayName("Jeder Auslieferungszustand hat einen Text, jedes Problem einen naechsten Schritt")
    void jederZustandHatText() {
        for (PackStatus.Delivery zustand : PackStatus.Delivery.values()) {
            assertFalse(Messages.text(zustand).isBlank(), "Kein Text fuer " + zustand);
            if (zustand.istProblem()) {
                assertFalse(Messages.schritt(zustand).isBlank(),
                        "Kein naechster Schritt fuer " + zustand);
            }
        }
        for (PackStatus.Reach reichweite : PackStatus.Reach.values()) {
            assertFalse(Messages.text(reichweite).isBlank(), "Kein Text fuer " + reichweite);
        }
    }

    @Test
    @DisplayName("Abgeschaltet ist kein Problem, ein Rueckfall schon")
    void problemzustaende() {
        assertFalse(PackStatus.Delivery.ABGESCHALTET.istProblem());
        assertFalse(PackStatus.Delivery.EIGENER_SERVER.istProblem());
        assertFalse(PackStatus.Delivery.FREMDE_ADRESSE.istProblem());
        assertTrue(PackStatus.Delivery.RUECKFALL_PORT.istProblem());
        assertTrue(PackStatus.Delivery.KEIN_PACK.istProblem());
    }

    @Test
    @DisplayName("Die Zielwahl kennt jeden Ausgang")
    void zielwahl() {
        assertEquals(PackStatus.Delivery.ABGESCHALTET,
                PackStatus.choose(false, "", true, "192.168.1.5", ABLAGE).delivery());
        assertEquals(PackStatus.Delivery.FREMDE_ADRESSE,
                PackStatus.choose(true, ABLAGE, false, null, ABLAGE).delivery());
        assertEquals(PackStatus.Delivery.RUECKFALL_PORT,
                PackStatus.choose(true, "", false, "192.168.1.5", ABLAGE).delivery());
        // Loopback ist der gefaehrlichste Fall: er sieht im Log richtig aus und erreicht
        // trotzdem niemanden, weil 127.0.0.1 beim Spieler auf dessen eigenen Rechner zeigt.
        assertEquals(PackStatus.Delivery.RUECKFALL_ADRESSE,
                PackStatus.choose(true, "", true, "127.0.0.1", ABLAGE).delivery());
        assertEquals(PackStatus.Delivery.EIGENER_SERVER,
                PackStatus.choose(true, "", true, "192.168.1.5", ABLAGE).delivery());
    }

    @Test
    @DisplayName("Eine Docker-Adresse faellt gegen die Adresse des Spielers auf")
    void netzwiderspruch() {
        assertTrue(PackStatus.widerspruch("172.17.0.1", "192.168.54.20"));
        assertFalse(PackStatus.widerspruch("192.168.54.16", "192.168.54.20"));
        // Namen und IPv6 lassen sich so nicht vergleichen - dann lieber gar keine Aussage
        // als eine falsche Warnung.
        assertFalse(PackStatus.widerspruch("example.org", "192.168.54.20"));
        assertFalse(PackStatus.widerspruch(null, "192.168.54.20"));
    }
}
