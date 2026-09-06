package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RankProgressTest {

    @Test
    @DisplayName("Der Fortschritt zählt innerhalb des aktuellen Rangs")
    void countsWithinTheRank() {
        RankProgress start = RankProgress.of(0.0);
        assertEquals(Rank.BRONZE, start.rank());
        assertEquals(Rank.SILBER, start.next());
        assertEquals(0, start.percent());
        assertEquals(1000.0, start.remaining(), 1e-9);

        RankProgress almost = RankProgress.of(999.0);
        assertEquals(Rank.BRONZE, almost.rank());
        assertEquals(99, almost.percent());

        RankProgress silver = RankProgress.of(1000.0);
        assertEquals(Rank.SILBER, silver.rank());
        assertEquals(0, silver.percent());
        assertEquals(4000.0, silver.remaining(), 1e-9);
    }

    @Test
    @DisplayName("Auf der höchsten Stufe gibt es keinen nächsten Rang mehr")
    void highestRankHasNoNext() {
        RankProgress top = RankProgress.of(500_000.0);
        assertEquals(Rank.NETHERITE, top.rank());
        assertNull(top.next());
        assertTrue(top.isHighest());
        assertEquals(100, top.percent());
        assertEquals(0.0, top.remaining(), 1e-9);
    }

    @Test
    @DisplayName("Der Balken benutzt nur das gewählte Zeichen und zwei Farben")
    void barUsesOnlyTheChosenSymbol() {
        RankProgress half = RankProgress.of(500.0);
        String bar = half.bar(10, "|");
        assertEquals(10, bar.chars().filter(c -> c == '|').count());
        assertTrue(bar.startsWith(RankProgress.FILLED_COLOR));
        assertTrue(bar.contains(RankProgress.EMPTY_COLOR));
        assertFalse(bar.replace(RankProgress.FILLED_COLOR, "").replace(RankProgress.EMPTY_COLOR, "")
                .chars().anyMatch(c -> c != '|'));

        // 50 Prozent von zehn Zeichen sind fünf gefüllte.
        String filledPart = bar.substring(RankProgress.FILLED_COLOR.length(),
                bar.indexOf(RankProgress.EMPTY_COLOR));
        assertEquals(5, filledPart.length());
    }

    @Test
    @DisplayName("Der Balken ist erst voll, wenn der Rang wirklich erreicht ist")
    void barMatchesPercent() {
        RankProgress almost = RankProgress.of(999.0);
        String bar = almost.bar(10, "|");
        String filled = bar.substring(RankProgress.FILLED_COLOR.length(), bar.indexOf(RankProgress.EMPTY_COLOR));
        assertEquals(9, filled.length(), "99 Prozent dürfen keinen vollen Balken zeigen");
        assertEquals(99, almost.percent());

        RankProgress reached = RankProgress.of(1000.0);
        assertEquals(0, reached.percent());
    }

    @Test
    @DisplayName("Ein negativer Punktestand ergibt den untersten Rang ohne Fortschritt")
    void negativePointsAreHarmless() {
        RankProgress progress = RankProgress.of(-50.0);
        assertEquals(Rank.BRONZE, progress.rank());
        assertEquals(0, progress.percent());
    }
}
