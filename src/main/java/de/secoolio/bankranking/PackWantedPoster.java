package de.secoolio.bankranking;

import java.util.Locale;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.Style;
import net.kyori.adventure.text.format.TextColor;

/**
 * Baut das bildschirmfuellende WANTED-Plakat als eine einzige Textzeile.
 *
 * <p>Der Client zentriert einen Titel um die Bildschirmmitte, indem er ihn um seine halbe
 * Breite nach links rueckt. Die Zeile wird deshalb so aufgebaut, dass ihre Gesamtbreite
 * <strong>genau null</strong> ist: dann steht der Cursor zu Beginn garantiert in der Mitte, und
 * jede Position darin ist absolut statt relativ. Ein einziger Ort rechnet, statt zwei Seiten
 * symmetrisch aufzufuellen.
 *
 * <p>Kein Bukkit, kein Server, kein Spieler - nur Masse und Farben. Genau deshalb laesst sich
 * die Nullbreite im Test nachrechnen, statt sie im Spiel zu suchen.
 */
final class PackWantedPoster {

    /** Farbe des Namens auf dem Papier. */
    private static final TextColor NAME_COLOR = TextColor.color(0x3A281A);
    /** Farbe des Betrags - Siegellackrot. */
    private static final TextColor AMOUNT_COLOR = TextColor.color(0x7A1812);
    /** Die Streifen tragen ihre eigenen Farben und duerfen nicht eingefaerbt werden. */
    private static final TextColor PLAIN = TextColor.color(0xFFFFFF);

    /** So viele Zeichen darf ein Name auf dem Plakat haben. */
    private static final int NAME_LIMIT = 16;

    private final PackFont font;
    private final Style style;

    PackWantedPoster(PackFont font) {
        this.font = font;
        // Die eigene Schrift gilt fuer die ganze Zeile; der Vanilla-Schatten wuerde die
        // Glyphen unsauber wirken lassen und wird abgeschaltet.
        this.style = Style.style().font(font.font()).shadowColor(ShadowColor.none()).build();
    }

    /**
     * Das fertige Plakat.
     *
     * @param face   das Gesicht des Gejagten
     * @param name   sein Name; laenger als {@value #NAME_LIMIT} Zeichen wird gekuerzt
     * @param amount der Betrag, bereits als Text formatiert
     */
    Component render(SkinFace face, String name, String amount) {
        return layout(face, name, amount).build(this.style);
    }

    /**
     * Der Gesamtvorschub der Zeile. Muss null sein, sonst sitzt das Plakat nicht mittig.
     *
     * <p>Nur fuer den Test - im Betrieb interessiert das Ergebnis, nicht die Zwischenrechnung.
     */
    int totalAdvance(SkinFace face, String name, String amount) {
        return layout(face, name, amount).gesamt();
    }

    private Zeile layout(SkinFace face, String name, String amount) {
        Zeile zeile = new Zeile(this.font.spacing());

        zeile.jumpTo(-this.font.posterWidth() / 2);
        appendStrips(zeile);

        zeile.jumpTo(-this.font.faceSize() / 2);
        appendFace(zeile, face);

        String kurz = shorten(name);
        zeile.jumpTo(-this.font.smallTextWidth(kurz) / 2);
        appendSmall(zeile, kurz);

        String betrag = digitsOnly(amount);
        zeile.jumpTo(-this.font.largeTextWidth(betrag) / 2);
        appendLarge(zeile, betrag);

        // Der Ausgleich am Ende ist es, der die Zeile mittig macht.
        zeile.jumpTo(0);
        return zeile;
    }

    /** Der Hintergrund: vier Spalten, dazwischen je ein Pixel zurueck. */
    private void appendStrips(Zeile zeile) {
        String streifen = this.font.stripChars();
        String zurueck = this.font.spacing().of(-1);
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < streifen.length(); i++) {
            text.append(streifen.charAt(i)).append(zurueck);
            zeile.advance(this.font.stripAdvance() - 1);
        }
        zeile.coloured(text.toString(), PLAIN);
    }

    /**
     * Das Gesicht, Zeile fuer Zeile.
     *
     * <p>Nebeneinanderliegende Punkte gleicher Farbe teilen sich eine Komponente. Das geht nur,
     * weil die Abstandszeichen unsichtbar und damit farbneutral sind - sie duerfen mitten in
     * einem eingefaerbten Stueck stehen, ohne es zu zerreissen. Aus rund 136 Komponenten je
     * Gesicht werden so meist zwanzig bis dreissig.
     */
    private void appendFace(Zeile zeile, SkinFace face) {
        char zurueck = this.font.spacing().of(-1).charAt(0);
        int anfang = zeile.cursor();
        for (int row = 0; row < SkinFace.SIZE; row++) {
            char glyph = this.font.faceRow(row);
            int col = 0;
            while (col < SkinFace.SIZE) {
                int farbe = face.at(col, row);
                StringBuilder lauf = new StringBuilder();
                while (col < SkinFace.SIZE && face.at(col, row) == farbe) {
                    lauf.append(glyph).append(zurueck);
                    zeile.advance(this.font.faceAdvance() - 1);
                    col++;
                }
                zeile.coloured(lauf.toString(), TextColor.color(farbe));
            }
            zeile.jumpTo(anfang);
        }
    }

    private void appendSmall(Zeile zeile, String text) {
        StringBuilder sichtbar = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sichtbar.append(text.charAt(i));
            zeile.advance(this.font.smallWidth(text.charAt(i)));
        }
        zeile.coloured(sichtbar.toString(), NAME_COLOR);
    }

    private void appendLarge(Zeile zeile, String text) {
        StringBuilder sichtbar = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            sichtbar.append(this.font.large(text.charAt(i)));
            zeile.advance(this.font.largeWidth(text.charAt(i)));
        }
        zeile.coloured(sichtbar.toString(), AMOUNT_COLOR);
    }

    /**
     * Nur was die kleine Schrift kennt, in Grossbuchstaben, hoechstens 16 Zeichen.
     *
     * <p>Minecraft laesst in Namen ohnehin nur Buchstaben, Ziffern und den Unterstrich zu.
     * Ueber eine Bruecke wie Geyser koennen aber Namen mit Sonderzeichen auf dem Server
     * landen; von denen bliebe hier unter Umstaenden nichts uebrig. Ein Plakat ohne Namen
     * waere unbrauchbar, deshalb der Rueckfall.
     */
    String shorten(String name) {
        StringBuilder gefiltert = new StringBuilder();
        String gross = name.toUpperCase(Locale.ROOT);
        for (int i = 0; i < gross.length() && gefiltert.length() < NAME_LIMIT; i++) {
            char z = gross.charAt(i);
            if (this.font.hasSmall(z)) {
                gefiltert.append(z);
            }
        }
        return gefiltert.isEmpty() ? "UNBEKANNT" : gefiltert.toString();
    }

    /** Nur die Zeichen, fuer die es eine grosse Ziffer gibt. */
    String digitsOnly(String betrag) {
        StringBuilder gefiltert = new StringBuilder();
        for (int i = 0; i < betrag.length(); i++) {
            if (this.font.large(betrag.charAt(i)) != 0) {
                gefiltert.append(betrag.charAt(i));
            }
        }
        return gefiltert.toString();
    }

    /**
     * Sammelt die Zeile und fuehrt dabei Buch ueber den Cursor.
     *
     * <p>Unsichtbare Abstaende werden zurueckgehalten und dem naechsten eingefaerbten Stueck
     * vorangestellt, statt eine eigene Komponente zu bekommen. Das spart bei jedem Sprung eine.
     */
    private static final class Zeile {
        private final Spacing spacing;
        private final TextComponent.Builder root = Component.text();
        private final StringBuilder offen = new StringBuilder();
        private int cursor;
        private int gesamt;

        Zeile(Spacing spacing) {
            this.spacing = spacing;
        }

        int cursor() {
            return this.cursor;
        }

        /** Merkt einen Vorschub, ohne etwas zu zeichnen. */
        void advance(int pixel) {
            this.cursor += pixel;
            this.gesamt += pixel;
        }

        /** Setzt den Cursor auf eine absolute Stelle, gemessen von der Bildschirmmitte. */
        void jumpTo(int ziel) {
            int sprung = ziel - this.cursor;
            if (sprung == 0) {
                return;
            }
            this.offen.append(this.spacing.of(sprung));
            advance(sprung);
        }

        void coloured(String text, TextColor farbe) {
            if (text.isEmpty() && this.offen.isEmpty()) {
                return;
            }
            this.root.append(Component.text(this.offen + text, farbe));
            this.offen.setLength(0);
        }

        Component build(Style style) {
            if (!this.offen.isEmpty()) {
                this.root.append(Component.text(this.offen.toString()));
                this.offen.setLength(0);
            }
            return this.root.style(style).build();
        }

        /** Der Gesamtvorschub der Zeile. */
        int gesamt() {
            return this.gesamt;
        }
    }
}
