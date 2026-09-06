package de.secoolio.bankranking;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import net.kyori.adventure.key.Key;

/**
 * Die Masse der eigenen Schrift im Resourcepack.
 *
 * <p>Grafik und Code muessen sich ueber jeden einzelnen Bildpunkt einig sein: verschaetzt sich
 * die Java-Seite bei der Breite eines Buchstabens, verrutscht das ganze Plakat. Deshalb misst
 * das Bauskript jede Zeichenbreite am fertigen Bild und legt sie zusammen mit allen anderen
 * Massen in {@code pack/metrics.properties} ab. Diese Klasse liest sie - es gibt genau eine
 * Quelle der Wahrheit, und sie liegt neben der Grafik, nicht im Code.
 *
 * <p>Wird die Grafik geaendert, ohne die Masse nachzuziehen, faellt das im Test auf und nicht
 * erst im Spiel.
 */
final class PackFont {

    /** Wo die Datei im Jar liegt. */
    static final String RESOURCE = "/pack/metrics.properties";

    private final Key font;
    private final int posterWidth;
    private final int posterHeight;

    private final String stripChars;
    private final int stripAdvance;

    private final String faceRows;
    private final int facePixel;
    private final int faceAdvance;
    private final int faceTop;

    private final String smallChars;
    private final int[] smallWidths;
    private final int smallTop;

    private final String largeChars;
    private final int largeBase;
    private final int[] largeWidths;
    private final int largeTop;

    private final String[] itemNames;
    private final int itemBase;
    private final int[] itemWidths;
    private final int itemTop;

    private final Spacing spacing;

    private PackFont(Properties p) {
        this.font = Key.key(require(p, "font"));
        this.posterWidth = number(p, "plakat.breite");
        this.posterHeight = number(p, "plakat.hoehe");

        this.stripChars = require(p, "plakat.streifen");
        this.stripAdvance = number(p, "plakat.streifen-advance");

        this.faceRows = require(p, "gesicht.zeilen");
        this.facePixel = number(p, "gesicht.pixel");
        this.faceAdvance = number(p, "gesicht.advance");
        this.faceTop = number(p, "gesicht.oben");

        this.smallChars = require(p, "klein.zeichen");
        this.smallWidths = numbers(p, "klein.breiten", this.smallChars.length());
        this.smallTop = number(p, "klein.oben");

        this.largeChars = require(p, "gross.zeichen");
        this.largeBase = number(p, "gross.basis");
        this.largeWidths = numbers(p, "gross.breiten", this.largeChars.length());
        this.largeTop = number(p, "gross.oben");

        this.itemNames = require(p, "item.namen").trim().split("\\s+");
        this.itemBase = number(p, "item.basis");
        this.itemWidths = numbers(p, "item.breiten", this.itemNames.length);
        this.itemTop = number(p, "item.oben");

        this.spacing = new Spacing(number(p, "abstand.plus"), number(p, "abstand.minus"),
                number(p, "abstand.potenzen"));

        if (this.faceRows.length() != 8) {
            throw new IllegalStateException("gesicht.zeilen braucht acht Zeichen, hat aber "
                    + this.faceRows.length());
        }
        if (this.stripChars.isEmpty()) {
            throw new IllegalStateException("plakat.streifen ist leer");
        }
    }

    /** Liest die Masse. Der Aufrufer entscheidet, was ein Fehlschlag bedeutet. */
    static PackFont load(InputStream in) throws IOException {
        Properties p = new Properties();
        // Ausdruecklich UTF-8: die Codepoints der Glyphen liegen im Privatnutzungsbereich,
        // und der voreingestellte Zeichensatz von Properties wuerde sie zerstoeren.
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            p.load(reader);
        }
        return new PackFont(p);
    }

    private static String require(Properties p, String key) {
        String wert = p.getProperty(key);
        if (wert == null || wert.isEmpty()) {
            throw new IllegalStateException("metrics.properties: " + key + " fehlt");
        }
        return wert;
    }

    private static int number(Properties p, String key) {
        try {
            return Integer.parseInt(require(p, key).trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("metrics.properties: " + key + " ist keine Zahl", e);
        }
    }

    private static int[] numbers(Properties p, String key, int expected) {
        String[] teile = require(p, key).trim().split("\\s+");
        if (teile.length != expected) {
            throw new IllegalStateException("metrics.properties: " + key + " hat " + teile.length
                    + " Werte, erwartet waren " + expected);
        }
        int[] werte = new int[expected];
        for (int i = 0; i < expected; i++) {
            werte[i] = Integer.parseInt(teile[i]);
        }
        return werte;
    }

    Key font() {
        return this.font;
    }

    Spacing spacing() {
        return this.spacing;
    }

    int posterWidth() {
        return this.posterWidth;
    }

    int posterHeight() {
        return this.posterHeight;
    }

    String stripChars() {
        return this.stripChars;
    }

    /** Vorschub eines Plakatstreifens; ein Abstandszeichen von -1 schliesst die Luecke. */
    int stripAdvance() {
        return this.stripAdvance;
    }

    /** Das Zeichen, das die Punkte der Skin-Zeile {@code row} zeichnet. */
    char faceRow(int row) {
        return this.faceRows.charAt(row);
    }

    /** Kantenlaenge eines Skin-Pixels in Textpixeln. */
    int facePixel() {
        return this.facePixel;
    }

    int faceAdvance() {
        return this.faceAdvance;
    }

    /** Kantenlaenge des ganzen Gesichts in Textpixeln. */
    int faceSize() {
        return 8 * this.facePixel;
    }

    int faceTop() {
        return this.faceTop;
    }

    int smallTop() {
        return this.smallTop;
    }

    int largeTop() {
        return this.largeTop;
    }

    /** Kennt die kleine Schrift dieses Zeichen? */
    boolean hasSmall(char zeichen) {
        return this.smallChars.indexOf(zeichen) >= 0;
    }

    /** Vorschub eines Zeichens der kleinen Schrift; 0 wenn es sie nicht kennt. */
    int smallWidth(char zeichen) {
        int i = this.smallChars.indexOf(zeichen);
        return i < 0 ? 0 : this.smallWidths[i];
    }

    /**
     * Das Zeichen der grossen Schrift fuer eine Ziffer, oder 0.
     *
     * <p>Die grossen Ziffern liegen im Privatnutzungsbereich und nicht auf den echten Ziffern:
     * sonst haetten zwei Provider denselben Codepoint und der eine verdraengte den anderen.
     */
    char large(char ziffer) {
        int i = this.largeChars.indexOf(ziffer);
        return i < 0 ? 0 : (char) (this.largeBase + i);
    }

    /** Vorschub einer Ziffer der grossen Schrift; 0 wenn es sie nicht gibt. */
    int largeWidth(char ziffer) {
        int i = this.largeChars.indexOf(ziffer);
        return i < 0 ? 0 : this.largeWidths[i];
    }

    /**
     * Das Sinnbild eines Materials, oder 0.
     *
     * <p>Der Name ist der kleingeschriebene Bukkit-Name, also {@code diamond} oder
     * {@code netherite_block} - so steht er auch in {@code metrics.properties}.
     */
    char item(String material) {
        int i = indexOfItem(material);
        return i < 0 ? 0 : (char) (this.itemBase + i);
    }

    int itemWidth(String material) {
        int i = indexOfItem(material);
        return i < 0 ? 0 : this.itemWidths[i];
    }

    private int indexOfItem(String material) {
        for (int i = 0; i < this.itemNames.length; i++) {
            if (this.itemNames[i].equals(material)) {
                return i;
            }
        }
        return -1;
    }

    /** Die Materialien in der Reihenfolge, in der das Pack sie kennt - wertvollstes zuerst. */
    java.util.List<String> itemNames() {
        return java.util.List.of(this.itemNames);
    }

    int itemTop() {
        return this.itemTop;
    }

    /** Die Breite eines Namens in der kleinen Schrift, unbekannte Zeichen uebersprungen. */
    int smallTextWidth(String text) {
        int breite = 0;
        for (int i = 0; i < text.length(); i++) {
            breite += smallWidth(text.charAt(i));
        }
        return breite;
    }

    /** Die Breite eines Betrags in der grossen Schrift. */
    int largeTextWidth(String text) {
        int breite = 0;
        for (int i = 0; i < text.length(); i++) {
            breite += largeWidth(text.charAt(i));
        }
        return breite;
    }
}
