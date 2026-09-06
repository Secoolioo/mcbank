package de.secoolio.bankranking;

import java.util.UUID;

/**
 * Die Regeln, nach denen ein Kopfgeld ausgezahlt oder verweigert wird.
 *
 * <p>Reine Entscheidung ohne Server: dieselbe Grundlage fuer die Auszahlung beim Tod, fuer die
 * Anzeige im Fenster und fuer die Meldungen. Nur so laesst sich der Missbrauchsschutz pruefen,
 * ohne zwei Spieler und einen laufenden Server zu brauchen.
 */
final class BountyRules {

    /** Was mit einem Topf beim Tod des Gejagten geschieht. */
    enum Outcome {
        /** Der Killer bekommt den ganzen Topf. */
        AUSZAHLEN,
        /** Es gibt nichts zu holen. */
        KEIN_TOPF,
        /** Der Killer hat selbst eingezahlt - der Topf bleibt stehen. */
        SELBST_EINGEZAHLT,
        /** Derselbe Killer hat hier gerade erst kassiert - der Topf bleibt stehen. */
        KILLER_GESPERRT
    }

    private BountyRules() {
    }

    /**
     * Darf dieser Killer diesen Topf kassieren?
     *
     * @param pot           der Topf, oder {@code null}
     * @param killer        wer getoetet hat
     * @param now           jetzt, in Millisekunden
     * @param claimCooldown wie lange derselbe Killer bei demselben Opfer aussetzen muss
     */
    static Outcome check(Bounty pot, UUID killer, long now, long claimCooldown) {
        if (pot == null || pot.isEmpty()) {
            return Outcome.KEIN_TOPF;
        }
        if (pot.hasStakeFrom(killer)) {
            // Bewusst flach: wer selbst eingezahlt hat, bekommt gar nichts. Zahlte man ihm
            // die fremden Anteile aus, waere ein Einsatz von einem Diamanten die Eintritts-
            // karte, jeden anderen Topf abzuraeumen.
            return Outcome.SELBST_EINGEZAHLT;
        }
        if (isClaimBlocked(pot, killer, now, claimCooldown)) {
            return Outcome.KILLER_GESPERRT;
        }
        return Outcome.AUSZAHLEN;
    }

    /** Hat derselbe Killer bei diesem Opfer gerade erst kassiert? */
    static boolean isClaimBlocked(Bounty pot, UUID killer, long now, long claimCooldown) {
        Bounty.Payout letzte = pot == null ? null : pot.lastPayout();
        return letzte != null
                && letzte.killer().equals(killer)
                && elapsed(letzte.time(), now) < claimCooldown;
    }

    /** Wie lange auf dieses Opfer noch kein neues Kopfgeld gesetzt werden darf; 0 = frei. */
    static long postCooldownLeft(Bounty pot, long now, long postCooldown) {
        Bounty.Payout letzte = pot == null ? null : pot.lastPayout();
        if (letzte == null) {
            return 0L;
        }
        return Math.max(0L, postCooldown - elapsed(letzte.time(), now));
    }

    /**
     * Die verstrichene Zeit, gegen eine rueckwaerts laufende Uhr abgesichert.
     *
     * <p>Steht in der Datei ein Zeitpunkt in der Zukunft - etwa weil die Systemuhr des Servers
     * korrigiert wurde -, waere die Sperre sonst praktisch ewig. Ein Zeitpunkt in der Zukunft
     * gilt deshalb als gerade eben.
     */
    private static long elapsed(long zeitpunkt, long now) {
        return Math.max(0L, now - Math.min(zeitpunkt, now));
    }
}
