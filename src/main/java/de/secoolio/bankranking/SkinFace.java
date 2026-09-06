package de.secoolio.bankranking;

import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.UUID;

/**
 * Die 8x8 Bildpunkte des Gesichts aus einem Spieler-Skin.
 *
 * <p>Ein Resourcepack kann kein Spielergesicht mitliefern - es weiss ja nicht, wer kuenftig auf
 * dem Server spielt. Deshalb liefert das Pack nur einen weissen Punkt, und der Server faerbt
 * ihn vierundsechzig Mal einzeln ein. Diese Klasse besorgt die dafuer noetigen Farben.
 *
 * <p>Reine Rechnung: kein Netz, kein Server, keine Bukkit-Klasse. Damit laesst sie sich gegen
 * ein selbst gebautes Bild pruefen.
 */
record SkinFace(int[] rgb) {

    /** Kantenlaenge des Gesichts im Skin. */
    static final int SIZE = 8;

    /** Lage der Gesichtsflaeche und der Hut-Ebene in jedem Minecraft-Skin. */
    private static final int FACE_X = 8;
    private static final int FACE_Y = 8;
    private static final int HAT_X = 40;
    private static final int HAT_Y = 8;

    /**
     * Das Ersatzgesicht, wenn kein Skin zu bekommen ist.
     *
     * <p>Bewusst ein eigener Entwurf und keine Kopie der mitgelieferten Minecraft-Skins: die
     * gehoeren Mojang, und ein schlichter Umriss erfuellt denselben Zweck.
     */
    static final SkinFace UNKNOWN = unknownFace();

    SkinFace {
        if (rgb.length != SIZE * SIZE) {
            throw new IllegalArgumentException("Ein Gesicht hat " + SIZE * SIZE
                    + " Bildpunkte, nicht " + rgb.length);
        }
        rgb = rgb.clone();
    }

    /** Die Farbe eines Bildpunkts als 0xRRGGBB. */
    int at(int x, int y) {
        return this.rgb[y * SIZE + x];
    }

    @Override
    public int[] rgb() {
        return this.rgb.clone();
    }

    /**
     * Liest die Gesichtsflaeche aus einem Skin.
     *
     * @return das Gesicht, oder {@link #UNKNOWN} wenn das Bild kein brauchbarer Skin ist
     */
    static SkinFace fromSkin(BufferedImage skin) {
        // Skins sind 64 breit und 32 oder 64 hoch. Alles andere kommt nicht von Mojang und
        // wird nicht ausgewertet - lieber ein Ersatzgesicht als ein zufaellig ausgelesener
        // Bildausschnitt.
        if (skin == null || skin.getWidth() != 64 || (skin.getHeight() != 64 && skin.getHeight() != 32)) {
            return UNKNOWN;
        }

        int[] werte = new int[SIZE * SIZE];
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int grund = skin.getRGB(FACE_X + x, FACE_Y + y);
                int hut = skin.getRGB(HAT_X + x, HAT_Y + y);
                werte[y * SIZE + x] = blend(grund, hut);
            }
        }
        return new SkinFace(werte);
    }

    /**
     * Legt die Hut-Ebene ueber die Grundebene.
     *
     * <p>Echtes Mischen nach Deckkraft, nicht "Hut vorhanden, also Hut nehmen": moderne Skins
     * benutzen halbdurchsichtige Hut-Punkte fuer Brillen und Schleier, und die wuerden sonst
     * als undurchsichtige Flecken erscheinen.
     */
    private static int blend(int grund, int hut) {
        int deckung = (hut >>> 24) & 0xFF;
        if (deckung == 0) {
            return grund & 0xFFFFFF;
        }
        if (deckung == 0xFF) {
            return hut & 0xFFFFFF;
        }
        double a = deckung / 255.0;
        int r = (int) Math.round(((hut >> 16) & 0xFF) * a + ((grund >> 16) & 0xFF) * (1 - a));
        int g = (int) Math.round(((hut >> 8) & 0xFF) * a + ((grund >> 8) & 0xFF) * (1 - a));
        int b = (int) Math.round((hut & 0xFF) * a + (grund & 0xFF) * (1 - a));
        return (r << 16) | (g << 8) | b;
    }

    /** Fuer Spieler ohne Skin - unabhaengig von der Kennung immer dasselbe Gesicht. */
    static SkinFace defaultFor(UUID id) {
        return UNKNOWN;
    }

    private static SkinFace unknownFace() {
        int hintergrund = 0x2B2118;
        int umriss = 0x6E5A44;
        int auge = 0xC8B48C;
        int[] werte = new int[SIZE * SIZE];
        Arrays.fill(werte, hintergrund);
        for (int x = 1; x < SIZE - 1; x++) {
            werte[1 * SIZE + x] = umriss;
        }
        for (int y = 2; y < SIZE - 1; y++) {
            werte[y * SIZE + 1] = umriss;
            werte[y * SIZE + SIZE - 2] = umriss;
        }
        werte[3 * SIZE + 2] = auge;
        werte[3 * SIZE + 5] = auge;
        werte[5 * SIZE + 3] = umriss;
        werte[5 * SIZE + 4] = umriss;
        return new SkinFace(werte);
    }
}
