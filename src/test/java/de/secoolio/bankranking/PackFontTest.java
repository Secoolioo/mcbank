package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prueft, dass Code und Grafik dieselben Masse benutzen. */
class PackFontTest {

    static PackFont bundled() throws IOException {
        try (InputStream in = PackFont.class.getResourceAsStream(PackFont.RESOURCE)) {
            assertNotNull(in, "pack/metrics.properties fehlt im Jar - erzeugt build_pack.py");
            return PackFont.load(in);
        }
    }

    @Test
    @DisplayName("Die mitgelieferten Masse lassen sich lesen")
    void bundledLoads() throws IOException {
        PackFont font = bundled();
        assertEquals("bankranking", font.font().namespace());
        assertEquals("kopfgeld", font.font().value());
        assertTrue(font.posterWidth() > 0);
        assertTrue(font.posterHeight() > 0);
    }

    @Test
    @DisplayName("Die vier Streifen ergeben zusammen genau die Plakatbreite")
    void stripsCoverPoster() throws IOException {
        PackFont font = bundled();
        int breite = font.stripChars().length() * (font.stripAdvance() - 1);
        assertEquals(font.posterWidth(), breite,
                "Streifenzahl mal Vorschub muss die Plakatbreite ergeben, sonst klafft eine "
                        + "Luecke oder die Spalten ueberlappen");
    }

    @Test
    @DisplayName("Das Gesicht ist acht Punkte breit und passt zum Punktabstand")
    void faceGeometry() throws IOException {
        PackFont font = bundled();
        assertEquals(8 * font.facePixel(), font.faceSize());
        assertEquals(font.facePixel() + 1, font.faceAdvance(),
                "Minecraft setzt zwischen zwei Glyphen einen Pixel Abstand");
        for (int row = 0; row < 8; row++) {
            assertTrue(font.faceRow(row) >= 0xE000, "Gesichtszeichen muessen im "
                    + "Privatnutzungsbereich liegen, damit sie keine echten Zeichen verdraengen");
        }
    }

    @Test
    @DisplayName("Jedes Zeichen der eigenen Schriften hat eine Breite groesser null")
    void everyGlyphHasWidth() throws IOException {
        PackFont font = bundled();
        for (char z : "ABCXYZ0189_-.".toCharArray()) {
            assertTrue(font.smallWidth(z) > 0, "keine Breite fuer " + z);
        }
        for (char z : "0123456789".toCharArray()) {
            assertTrue(font.largeWidth(z) > 0, "keine Breite fuer die grosse " + z);
            assertTrue(font.large(z) >= 0xE000, "grosse Ziffern gehoeren in den "
                    + "Privatnutzungsbereich, sonst verdraengen sie die kleinen");
        }
    }

    @Test
    @DisplayName("Ein unbekanntes Zeichen hat die Breite null und wird nicht gesetzt")
    void unknownGlyph() throws IOException {
        PackFont font = bundled();
        assertEquals(0, font.smallWidth('ä'));
        assertEquals(0, font.largeWidth('A'));
        assertEquals(0, font.large('A'));
    }

    @Test
    @DisplayName("Eine unvollstaendige Datei wird gemeldet statt still falsch gelesen")
    void incompleteFails() {
        String text = "font=bankranking:kopfgeld\nplakat.breite=120\n";
        assertThrows(IllegalStateException.class,
                () -> PackFont.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    @DisplayName("Zu wenige Einzelbreiten werden gemeldet")
    void widthCountChecked() throws IOException {
        String text = new String(PackFont.class.getResourceAsStream(PackFont.RESOURCE)
                .readAllBytes(), StandardCharsets.UTF_8)
                .replaceAll("(?m)^klein\\.breiten=.*$", "klein.breiten=1 2 3");
        IllegalStateException fehler = assertThrows(IllegalStateException.class,
                () -> PackFont.load(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8))));
        assertTrue(fehler.getMessage().contains("klein.breiten"), fehler.getMessage());
    }
}
