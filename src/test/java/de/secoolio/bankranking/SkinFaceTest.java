package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.awt.image.BufferedImage;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prueft das Auslesen der Gesichtsflaeche aus einem Skin. */
class SkinFaceTest {

    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final int HAT_X = 40;

    private static BufferedImage skin(int hoehe) {
        BufferedImage bild = new BufferedImage(64, hoehe, BufferedImage.TYPE_INT_ARGB);
        // Die ganze Gesichtsflaeche in einem klaren Ton, damit Abweichungen auffallen.
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                bild.setRGB(FACE_X + x, FACE_Y + y, 0xFF204080);
            }
        }
        return bild;
    }

    @Test
    @DisplayName("Die Gesichtsflaeche wird an der richtigen Stelle gelesen")
    void readsFaceRegion() {
        SkinFace face = SkinFace.fromSkin(skin(64));
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                assertEquals(0x204080, face.at(x, y));
            }
        }
    }

    @Test
    @DisplayName("Ein deckender Hutpunkt ueberschreibt den Grundpunkt")
    void opaqueHatWins() {
        BufferedImage bild = skin(64);
        bild.setRGB(HAT_X + 2, FACE_Y + 3, 0xFFFF0000);
        assertEquals(0xFF0000, SkinFace.fromSkin(bild).at(2, 3));
    }

    @Test
    @DisplayName("Ein halbdurchsichtiger Hutpunkt wird gemischt statt einfach genommen")
    void translucentHatBlends() {
        BufferedImage bild = skin(64);
        // Halbe Deckkraft in Rot ueber 0x204080 ergibt ungefaehr die Mitte beider Farben.
        bild.setRGB(HAT_X + 1, FACE_Y + 1, 0x80FF0000);
        int gemischt = SkinFace.fromSkin(bild).at(1, 1);
        assertNotEquals(0xFF0000, gemischt, "der Hut darf den Grund nicht einfach ersetzen");
        assertNotEquals(0x204080, gemischt, "der Hut muss aber wirken");
        assertEquals(0x8F, (gemischt >> 16) & 0xFF, 2, "Rotanteil ungefaehr halb");
    }

    @Test
    @DisplayName("Ein durchsichtiger Hutpunkt laesst den Grund unveraendert")
    void transparentHatIgnored() {
        BufferedImage bild = skin(64);
        bild.setRGB(HAT_X + 4, FACE_Y + 4, 0x00FF0000);
        assertEquals(0x204080, SkinFace.fromSkin(bild).at(4, 4));
    }

    @Test
    @DisplayName("Auch der alte 64x32-Skin wird gelesen")
    void legacySkin() {
        assertEquals(0x204080, SkinFace.fromSkin(skin(32)).at(0, 0));
    }

    @Test
    @DisplayName("Ein Bild in fremder Groesse liefert das Ersatzgesicht statt Zufallspunkte")
    void wrongSizeFallsBack() {
        assertSame(SkinFace.UNKNOWN,
                SkinFace.fromSkin(new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)));
        assertSame(SkinFace.UNKNOWN, SkinFace.fromSkin(null));
    }

    @Test
    @DisplayName("Es gibt immer ein Gesicht, auch ohne Skin")
    void alwaysAFace() {
        assertSame(SkinFace.UNKNOWN, SkinFace.defaultFor(UUID.randomUUID()));
        assertEquals(64, SkinFace.UNKNOWN.rgb().length);
    }

    @Test
    @DisplayName("Ein Gesicht laesst sich nicht von aussen veraendern")
    void immutable() {
        SkinFace face = SkinFace.fromSkin(skin(64));
        int[] kopie = face.rgb();
        kopie[0] = 0xFFFFFF;
        assertEquals(0x204080, face.at(0, 0));

        assertThrows(IllegalArgumentException.class, () -> new SkinFace(new int[10]));
    }
}
