package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueft alle Spielertexte auf einmal.
 *
 * <p>MiniMessage wirft bei einem unbekannten Tag nicht, sondern zeigt ihn als Text an. Ein
 * Tippfehler in einer Farbe faellt also nicht beim Start auf, sondern erst, wenn ein Spieler
 * eine Zeile wie {@code <dark_redd>GESUCHT} im Chat liest. Dieser Test faengt das ab.
 */
class MessagesTest {

    /** Alles, was in spitzen Klammern steht und kein bekannter Platzhalter ist. */
    private static final Pattern TAG = Pattern.compile("<([a-z_]+)(:[^>]*)?>");

    /**
     * Platzhalter, die der Code selbst ersetzt. Sie sind kein MiniMessage-Tag und duerfen
     * im Rohtext stehen bleiben.
     */
    private static final List<String> PLATZHALTER = List.of(
            "name", "punkte", "platz", "wert", "rang", "naechster", "faktor", "anzahl",
            "nr", "pos", "skin", "gesamt", "seite", "seiten", "einsaetze", "rest", "mindest",
            "von", "killer", "tode", "tag", "balken", "prozent", "abstand", "material",
            "zeit", "datum", "items", "kopf", "hash", "adresse", "zustand", "wer", "grund",
            "menge", "faellig", "beste", "durchschnitt", "titel", "text",
            "ab", "schnitt", "roh", "status", "ziel", "basis", "seltenheit", "kategorie",
            "stufen", "bonus", "stapel", "platzfarbe", "namensfarbe");

    private static List<String> alleTexte() throws IllegalAccessException {
        List<String> texte = new ArrayList<>();
        for (Field feld : Messages.class.getDeclaredFields()) {
            if (!Modifier.isStatic(feld.getModifiers()) || !Modifier.isPublic(feld.getModifiers())) {
                continue;
            }
            Object wert = feld.get(null);
            if (wert instanceof String einzeln) {
                texte.add(einzeln);
            } else if (wert instanceof String[] mehrere) {
                texte.addAll(List.of(mehrere));
            }
        }
        return texte;
    }

    @Test
    @DisplayName("Jeder Text laesst sich lesen und hinterlaesst keine rohen Tags")
    void everyMessageParses() throws IllegalAccessException {
        List<String> texte = alleTexte();
        assertTrue(texte.size() > 100, "es sollten deutlich mehr als hundert Texte sein, sind aber "
                + texte.size());

        List<String> auffaellig = new ArrayList<>();
        for (String text : texte) {
            String sichtbar = PlainTextComponentSerializer.plainText()
                    .serialize(Messages.mm(text));
            Matcher m = TAG.matcher(sichtbar);
            while (m.find()) {
                if (!PLATZHALTER.contains(m.group(1))) {
                    auffaellig.add(text + "  ->  unbekannter Tag <" + m.group(1) + ">");
                }
            }
        }
        assertTrue(auffaellig.isEmpty(), () -> "Diese Texte enthalten Tags, die MiniMessage nicht "
                + "kennt - sie stuenden im Spiel als roher Text da:\n  "
                + String.join("\n  ", auffaellig));
    }

    @Test
    @DisplayName("Kein Text enthaelt noch das alte Punkte-Sinnbild bei der Belohnung")
    void noLeftoverPointSymbol() throws IllegalAccessException {
        // Die Belohnung wird als Gegenstaende beschrieben, nicht als Punktzahl. Ein
        // uebersehenes Sternchen an einer Stelle waere genau der Widerspruch, nach dem
        // im Spiel gefragt wurde.
        for (String text : alleTexte()) {
            if (text.contains("<wert>") || text.contains("<mindest>")) {
                assertFalse(text.contains("✦"),
                        "Belohnung mit Punkte-Sinnbild: " + text);
            }
        }
    }

    @Test
    @DisplayName("Das Chat-Plakat hat mehrere Zeilen und traegt das Gesicht")
    void chatPosterShape() {
        assertTrue(Messages.KOPFGELD_PLAKAT_CHAT.length >= 3);
        boolean mitKopf = false;
        for (String zeile : Messages.KOPFGELD_PLAKAT_CHAT) {
            mitKopf |= zeile.contains("<kopf>");
        }
        assertTrue(mitKopf, "ohne <kopf> fehlt das Gesicht in der Sparfassung");
    }
}
