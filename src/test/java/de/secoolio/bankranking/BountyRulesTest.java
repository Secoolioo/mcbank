package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Prueft den Missbrauchsschutz beim Kassieren. */
class BountyRulesTest {

    private static final UUID ZIEL = UUID.randomUUID();
    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final long JETZT = 10_000_000L;
    private static final long KASSIER_SPERRE = 900_000L;      // 15 Minuten
    private static final long AUSSETZ_SPERRE = 300_000L;      // 5 Minuten

    private static Bounty topf(UUID einzahler, Bounty.Payout letzte) {
        return new Bounty(ZIEL, "Steve", 1L,
                List.of(new Bounty.Stake(einzahler, "Wer", 1L, Map.of(Material.DIAMOND, 10))),
                letzte);
    }

    @Test
    @DisplayName("Ein fremder Killer bekommt den Topf")
    void strangerGetsIt() {
        assertEquals(BountyRules.Outcome.AUSZAHLEN,
                BountyRules.check(topf(ALEX, null), BOB, JETZT, KASSIER_SPERRE));
    }

    @Test
    @DisplayName("Kein oder leerer Topf ergibt keine Auszahlung")
    void noPot() {
        assertEquals(BountyRules.Outcome.KEIN_TOPF,
                BountyRules.check(null, BOB, JETZT, KASSIER_SPERRE));
        assertEquals(BountyRules.Outcome.KEIN_TOPF,
                BountyRules.check(Bounty.empty(ZIEL, "Steve", 1L), BOB, JETZT, KASSIER_SPERRE));
    }

    @Test
    @DisplayName("Wer selbst eingezahlt hat, kassiert nicht")
    void placerGetsNothing() {
        assertEquals(BountyRules.Outcome.SELBST_EINGEZAHLT,
                BountyRules.check(topf(ALEX, null), ALEX, JETZT, KASSIER_SPERRE));
    }

    @Test
    @DisplayName("Derselbe Killer kassiert bei demselben Opfer nicht sofort erneut")
    void sameKillerBlocked() {
        Bounty.Payout gerade = new Bounty.Payout(JETZT - 60_000L, BOB, "Bob");
        assertEquals(BountyRules.Outcome.KILLER_GESPERRT,
                BountyRules.check(topf(ALEX, gerade), BOB, JETZT, KASSIER_SPERRE));
    }

    @Test
    @DisplayName("Ein anderer Killer ist von der Sperre nicht betroffen")
    void otherKillerFree() {
        Bounty.Payout gerade = new Bounty.Payout(JETZT - 60_000L, BOB, "Bob");
        UUID dritter = UUID.randomUUID();
        assertEquals(BountyRules.Outcome.AUSZAHLEN,
                BountyRules.check(topf(ALEX, gerade), dritter, JETZT, KASSIER_SPERRE));
    }

    @Test
    @DisplayName("Nach Ablauf der Sperre darf derselbe Killer wieder kassieren")
    void cooldownExpires() {
        Bounty.Payout lange = new Bounty.Payout(JETZT - KASSIER_SPERRE - 1L, BOB, "Bob");
        assertEquals(BountyRules.Outcome.AUSZAHLEN,
                BountyRules.check(topf(ALEX, lange), BOB, JETZT, KASSIER_SPERRE));
    }

    @Test
    @DisplayName("Eine rueckwaerts gestellte Uhr sperrt nicht auf ewig")
    void clockGoneBackwards() {
        // Steht in der Datei ein Zeitpunkt in der Zukunft, gilt er als gerade eben - sonst
        // waere die Sperre praktisch unendlich.
        Bounty.Payout zukunft = new Bounty.Payout(JETZT + 999_999_999L, BOB, "Bob");
        assertTrue(BountyRules.isClaimBlocked(topf(ALEX, zukunft), BOB, JETZT, KASSIER_SPERRE));
        assertEquals(AUSSETZ_SPERRE,
                BountyRules.postCooldownLeft(topf(ALEX, zukunft), JETZT, AUSSETZ_SPERRE));
    }

    @Test
    @DisplayName("Die Aussetz-Sperre laeuft nach der Auszahlung ab")
    void postCooldown() {
        assertEquals(0L, BountyRules.postCooldownLeft(topf(ALEX, null), JETZT, AUSSETZ_SPERRE));

        Bounty.Payout vorEinerMinute = new Bounty.Payout(JETZT - 60_000L, BOB, "Bob");
        assertEquals(AUSSETZ_SPERRE - 60_000L,
                BountyRules.postCooldownLeft(topf(ALEX, vorEinerMinute), JETZT, AUSSETZ_SPERRE));

        Bounty.Payout lange = new Bounty.Payout(JETZT - AUSSETZ_SPERRE, BOB, "Bob");
        assertEquals(0L, BountyRules.postCooldownLeft(topf(ALEX, lange), JETZT, AUSSETZ_SPERRE));
    }

    @Test
    @DisplayName("Der Topf kennt seine Einzahler und seinen Wert")
    void potBasics() {
        Bounty pot = new Bounty(ZIEL, "Steve", 1L, List.of(
                new Bounty.Stake(ALEX, "Alex", 1L, Map.of(Material.DIAMOND, 10)),
                new Bounty.Stake(BOB, "Bob", 2L, Map.of(Material.DIAMOND, 5,
                        Material.EMERALD_BLOCK, 2))), null);

        assertEquals(Map.of(Material.DIAMOND, 15, Material.EMERALD_BLOCK, 2), pot.total());
        assertEquals(17, pot.itemCount());
        assertTrue(pot.hasStakeFrom(ALEX));
        assertFalse(pot.hasStakeFrom(UUID.randomUUID()));
        // Zwei Materialien zu je zehn Punkten Grundwert: 15 + 2 Stueck macht 170.
        assertEquals(170.0, pot.value(material -> 10.0));
    }

    @Test
    @DisplayName("Ein Einsatz verschiebt den Beginn des Kopfgelds nicht")
    void sinceStaysAtFirstStake() {
        Bounty erst = Bounty.empty(ZIEL, "Steve", 100L)
                .withStake(new Bounty.Stake(ALEX, "Alex", 500L, Map.of(Material.DIAMOND, 1)));
        Bounty dann = erst.withStake(new Bounty.Stake(BOB, "Bob", 9000L,
                Map.of(Material.DIAMOND, 1)));
        assertEquals(500L, dann.since(), "'seit' gehoert dem ersten Einsatz");
    }
}
