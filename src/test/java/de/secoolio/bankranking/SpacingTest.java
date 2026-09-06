package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prueft die Zerlegung eines Sprungs in Zweierpotenz-Zeichen. */
class SpacingTest {

    private final Spacing spacing = new Spacing(0xE010, 0xE020, 11);

    @Test
    @DisplayName("Jeder Sprung im moeglichen Bereich kommt genau zurueck")
    void roundTrip() {
        for (int pixel = -spacing.maximum(); pixel <= spacing.maximum(); pixel++) {
            assertEquals(pixel, spacing.widthOf(spacing.of(pixel)),
                    "Sprung " + pixel + " kam nicht zurueck");
        }
    }

    @Test
    @DisplayName("Ein Sprung um null erzeugt kein Zeichen")
    void zeroIsEmpty() {
        assertEquals("", spacing.of(0));
        assertEquals(0, spacing.widthOf(""));
    }

    @Test
    @DisplayName("Die Zerlegung ist die kuerzestmoegliche")
    void shortest() {
        // 60 = 32 + 16 + 8 + 4, also vier Zeichen. Mehr waere Verschwendung, weniger unmoeglich.
        assertEquals(4, spacing.of(-60).length());
        assertEquals(1, spacing.of(64).length());
        assertEquals(11, spacing.of(spacing.maximum()).length());
    }

    @Test
    @DisplayName("Ein zu grosser Sprung wird geklemmt statt still abgeschnitten")
    void clamped() {
        assertEquals(spacing.maximum(), spacing.widthOf(spacing.of(spacing.maximum() + 500)));
        assertEquals(-spacing.maximum(), spacing.widthOf(spacing.of(-spacing.maximum() - 500)));
    }

    @Test
    @DisplayName("Abstandszeichen lassen sich von sichtbaren unterscheiden")
    void recognises() {
        assertTrue(spacing.isSpacing(spacing.of(-1).charAt(0)));
        assertTrue(spacing.isSpacing(spacing.of(1024).charAt(0)));
        assertEquals(0, spacing.advanceOf('A'));
        assertEquals(0, spacing.advanceOf((char) 0xE200));
    }
}
