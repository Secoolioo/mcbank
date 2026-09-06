package de.secoolio.bankranking;

/**
 * Waagerechte Spruenge im Plakat, zusammengesetzt aus unsichtbaren Zeichen.
 *
 * <p>Das Resourcepack legt Zeichen an, deren einzige Wirkung ein Vorschub ist: je eines fuer
 * jede Zweierpotenz von 1 bis 1024, einmal nach rechts und einmal nach links. Damit laesst sich
 * jeder ganzzahlige Abstand aus hoechstens elf Zeichen zusammensetzen, und der Cursor im Titel
 * laesst sich frei bewegen, statt nur von links nach rechts zu laufen.
 *
 * <p>Negative Vorschuebe wirken nur, wenn der Server die Nachricht schickt. Tippt ein Spieler
 * dieselben Zeichen in den Chat, klemmt der Client sie auf null - genau deshalb laesst sich das
 * Verfahren nicht missbrauchen.
 */
final class Spacing {

    private final int plus;
    private final int minus;
    private final int powers;

    /**
     * @param plus   Codepoint des Vorschubs +1; die groesseren folgen unmittelbar darauf
     * @param minus  Codepoint des Vorschubs -1
     * @param powers wie viele Zweierpotenzen es gibt
     */
    Spacing(int plus, int minus, int powers) {
        this.plus = plus;
        this.minus = minus;
        this.powers = powers;
    }

    /** Der groesste Sprung, der sich in einem Stueck darstellen laesst. */
    int maximum() {
        return (1 << this.powers) - 1;
    }

    /**
     * Die Zeichenfolge fuer einen Sprung um {@code pixels} Textpixel.
     *
     * <p>Ein Sprung, der groesser ist als {@link #maximum()}, wird auf das Moegliche geklemmt.
     * Das kann im Plakat nicht vorkommen - es ist nur 120 Pixel breit -, aber ein still
     * abgeschnittener Sprung waere schwerer zu finden als ein sichtbar falsch sitzendes Bild.
     */
    String of(int pixels) {
        if (pixels == 0) {
            return "";
        }
        int base = pixels > 0 ? this.plus : this.minus;
        int rest = Math.min(Math.abs(pixels), maximum());
        StringBuilder text = new StringBuilder();
        for (int i = this.powers - 1; i >= 0; i--) {
            int step = 1 << i;
            if (rest >= step) {
                rest -= step;
                text.append((char) (base + i));
            }
        }
        return text.toString();
    }

    /** Der Gesamtvorschub einer Zeichenfolge aus Abstandszeichen. */
    int widthOf(String text) {
        int summe = 0;
        for (int i = 0; i < text.length(); i++) {
            summe += advanceOf(text.charAt(i));
        }
        return summe;
    }

    /** Der Vorschub eines einzelnen Zeichens, oder 0 wenn es kein Abstandszeichen ist. */
    int advanceOf(char zeichen) {
        if (zeichen >= this.plus && zeichen < this.plus + this.powers) {
            return 1 << (zeichen - this.plus);
        }
        if (zeichen >= this.minus && zeichen < this.minus + this.powers) {
            return -(1 << (zeichen - this.minus));
        }
        return 0;
    }

    /** Gehoert das Zeichen zu den Abstandszeichen? */
    boolean isSpacing(char zeichen) {
        return advanceOf(zeichen) != 0;
    }
}
