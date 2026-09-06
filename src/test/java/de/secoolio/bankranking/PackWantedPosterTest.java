package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueft den Aufbau der Plakat-Zeile.
 *
 * <p>Der wichtigste Test ist die Nullbreite: der Client zentriert einen Titel um seine halbe
 * Breite, und nur bei Gesamtbreite null landet das Plakat verlaesslich in der Bildschirmmitte.
 * Waere das falsch, saesse das Plakat schief - und zwar je nach Namenslaenge unterschiedlich
 * schief, was im Spiel schwer zu deuten waere.
 */
class PackWantedPosterTest {

    private static PackWantedPoster poster() throws IOException {
        return new PackWantedPoster(PackFontTest.bundled());
    }

    private static SkinFace einfarbig(int farbe) {
        int[] werte = new int[64];
        Arrays.fill(werte, farbe);
        return new SkinFace(werte);
    }

    private static SkinFace schachbrett() {
        int[] werte = new int[64];
        for (int i = 0; i < 64; i++) {
            werte[i] = ((i / 8 + i % 8) % 2 == 0) ? 0x000000 : 0xFFFFFF;
        }
        return new SkinFace(werte);
    }

    private static List<PackWantedPoster.Loot> loot(Object... paare) {
        List<PackWantedPoster.Loot> liste = new java.util.ArrayList<>();
        for (int i = 0; i < paare.length; i += 2) {
            liste.add(new PackWantedPoster.Loot((String) paare[i], (Integer) paare[i + 1]));
        }
        return liste;
    }

    @Test
    @DisplayName("Die Zeile hat die Gesamtbreite null, egal wie Name und Belohnung aussehen")
    void totalAdvanceIsZero() throws IOException {
        PackWantedPoster p = poster();
        List<List<PackWantedPoster.Loot>> beute = List.of(
                loot(),
                loot("diamond", 1),
                loot("diamond", 999),
                loot("netherite_block", 2, "diamond", 64),
                loot("netherite_block", 1, "emerald", 12, "diamond", 7),
                loot("unbekannt", 5));
        for (String name : new String[]{"A", "Secoolioo", "EinSehrLangerName123", "", "___"}) {
            for (List<PackWantedPoster.Loot> b : beute) {
                assertEquals(0, p.totalAdvance(SkinFace.UNKNOWN, name, b),
                        "Name '" + name + "', Beute " + b);
            }
        }
    }

    @Test
    @DisplayName("Mehr als zwei Posten passen nicht nebeneinander und entfallen")
    void atMostTwoLootEntries() throws IOException {
        Component drei = poster().render(SkinFace.UNKNOWN, "HANS",
                loot("netherite_block", 1, "emerald", 2, "diamond", 3));
        Component zwei = poster().render(SkinFace.UNKNOWN, "HANS",
                loot("netherite_block", 1, "emerald", 2));
        assertEquals(zwei.children().size(), drei.children().size(),
                "der dritte Posten darf keine zusaetzliche Komponente erzeugen");
    }

    @Test
    @DisplayName("Ein einfarbiges Gesicht braucht nur eine Komponente je Zeile")
    void runLengthCollapsesRows() throws IOException {
        Component c = poster().render(einfarbig(0x804020), "HANS", loot("diamond", 100));
        // Fuenf Komponenten sind nicht vom Gesicht: Hintergrund, Name, das Sinnbild, die
        // Zahl daneben und der Ausgleich am Ende. Acht Gesichtszeilen zu je einer ergeben
        // zusammen dreizehn.
        assertEquals(13, c.children().size(),
                "Zusammenhaengende Punkte gleicher Farbe muessen zu einer Komponente werden");
    }

    @Test
    @DisplayName("Ein Schachbrett-Gesicht erzeugt jede Farbe einzeln")
    void checkerboardIsWorstCase() throws IOException {
        Component c = poster().render(schachbrett(), "HANS", loot("diamond", 100));
        // Acht Zeilen zu acht Farbwechseln plus die fuenf Komponenten aussen herum.
        assertEquals(69, c.children().size());
    }

    @Test
    @DisplayName("Der Name wird gross gesetzt, gekuerzt und von unbekannten Zeichen befreit")
    void nameIsSanitised() throws IOException {
        PackWantedPoster p = poster();
        assertEquals("HANS", p.shorten("Hans"));
        assertEquals("HANS_99", p.shorten("hans_99"));
        // Die Plakatschrift kennt keine Umlaute; sie fallen weg statt ersetzt zu werden.
        // Bei Minecraft-Namen kann das nicht vorkommen, ueber Bruecken wie Geyser schon.
        assertEquals("HNSS", p.shorten("Hänß"));
        assertEquals(16, p.shorten("ABCDEFGHIJKLMNOPQRSTUVWXYZ").length());
    }

    @Test
    @DisplayName("Aus dem Betrag bleiben nur Zeichen, fuer die es eine grosse Ziffer gibt")
    void amountIsSanitised() throws IOException {
        PackWantedPoster p = poster();
        assertEquals("12.500", p.digitsOnly("12.500"));
        assertEquals("1234", p.digitsOnly("1a2b3c4"));
    }

    @Test
    @DisplayName("Ein Name, von dem nichts uebrig bleibt, laesst das Plakat nicht leer")
    void nameNeverEmpty() throws IOException {
        PackWantedPoster p = poster();
        assertEquals("UNBEKANNT", p.shorten("***"));
        assertEquals("UNBEKANNT", p.shorten(""));
        assertEquals(0, p.totalAdvance(SkinFace.UNKNOWN, "***", loot("diamond", 1)),
                "auch der Rueckfallname muss die Zeile mittig lassen");
    }

    @Test
    @DisplayName("Die Zeile traegt die eigene Schrift und keinen Textschatten")
    void styleIsSet() throws IOException {
        Component c = poster().render(SkinFace.UNKNOWN, "HANS", loot("diamond", 100));
        assertEquals("bankranking:kopfgeld", String.valueOf(c.style().font()));
        assertTrue(c.style().shadowColor() != null && c.style().shadowColor().alpha() == 0,
                "Der Vanilla-Schatten wuerde die Glyphen unsauber wirken lassen");
    }

    @Test
    @DisplayName("Das Plakat besteht nur aus Zeichen des eigenen Bereichs und Grossbuchstaben")
    void onlyKnownCharacters() throws IOException {
        String text = PlainTextComponentSerializer.plainText()
                .serialize(poster().render(SkinFace.UNKNOWN, "Secoolioo",
                        loot("netherite_block", 3, "diamond", 64)));
        for (char z : text.toCharArray()) {
            boolean eigenerBereich = z >= 0xE000 && z <= 0xF8FF;
            boolean namensZeichen = (z >= 'A' && z <= 'Z') || (z >= '0' && z <= '9')
                    || z == '_' || z == '-' || z == '.';
            assertTrue(eigenerBereich || namensZeichen,
                    "unerwartetes Zeichen U+" + Integer.toHexString(z));
        }
    }
}
