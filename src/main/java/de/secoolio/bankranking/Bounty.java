package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

import org.bukkit.Material;

/**
 * Der Topf, der auf einem Spieler liegt.
 *
 * <p>Unveraenderlich wie {@link PlayerStats}: jede Aenderung erzeugt einen neuen Datensatz.
 * Der Rollback in {@link BountyData} setzt dadurch einfach die alte Referenz zurueck, statt
 * tief kopieren zu muessen.
 *
 * <p>Die Einsaetze bleiben einzeln stehen und werden nicht zu einer Summe verschmolzen. Drei
 * Dinge haengen daran: der Killer bekommt am Ende genau diese Gegenstaende, eine Aufhebung muss
 * jedem seinen eigenen Einsatz zurueckgeben, und wer selbst eingezahlt hat, darf nicht kassieren.
 *
 * @param target      wessen Kopf gesucht wird
 * @param name        sein zuletzt bekannter Name, nur zur Anzeige
 * @param since       wann der erste Einsatz gelegt wurde
 * @param stakes      die Einsaetze, aelteste zuerst
 * @param lastPayout  die letzte Auszahlung, oder {@code null}; bleibt stehen, auch wenn der
 *                    Topf leer ist, weil die Sperrfristen daran haengen
 */
public record Bounty(UUID target, String name, long since, List<Stake> stakes, Payout lastPayout) {

    /**
     * Ein einzelner Einsatz.
     *
     * @param items Material auf Stueckzahl; immer groesser als null
     */
    public record Stake(UUID from, String name, long time, Map<Material, Integer> items) {
        public Stake {
            items = Map.copyOf(items);
        }
    }

    /** Die letzte Auszahlung auf dieses Ziel. */
    public record Payout(long time, UUID killer, String name) {
    }

    public Bounty {
        stakes = List.copyOf(stakes);
    }

    /** Ein Topf ohne Einsaetze, aber mit Gedaechtnis fuer die Sperrfrist. */
    static Bounty empty(UUID target, String name, long now) {
        return new Bounty(target, name, now, List.of(), null);
    }

    /** Alle Gegenstaende des Topfes zusammengezaehlt. */
    public Map<Material, Integer> total() {
        Map<Material, Integer> summe = new EnumMap<>(Material.class);
        for (Stake stake : this.stakes) {
            stake.items().forEach((material, anzahl) -> summe.merge(material, anzahl, Integer::sum));
        }
        return summe;
    }

    /** Wie viele Gegenstaende insgesamt im Topf liegen. */
    public int itemCount() {
        int anzahl = 0;
        for (Stake stake : this.stakes) {
            for (int stueck : stake.items().values()) {
                anzahl += stueck;
            }
        }
        return anzahl;
    }

    /** Wer eingezahlt hat, in der Reihenfolge des ersten Einsatzes. */
    public Set<UUID> placers() {
        Set<UUID> wer = new LinkedHashSet<>();
        for (Stake stake : this.stakes) {
            wer.add(stake.from());
        }
        return wer;
    }

    /** Hat dieser Spieler selbst auf den Kopf gesetzt? */
    public boolean hasStakeFrom(UUID spieler) {
        for (Stake stake : this.stakes) {
            if (stake.from().equals(spieler)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return this.stakes.isEmpty();
    }

    /**
     * Der Wert des Topfes in Bankpunkten.
     *
     * <p>Nur zur Anzeige. Ausgezahlt werden immer die Gegenstaende selbst, nie ein Betrag -
     * sonst entstuende aus dem Kopfgeld heraus neues Vermoegen.
     *
     * @param wert liefert den Grundwert eines Materials; im Betrieb aus {@code MaterialValues}
     */
    public double value(ToDoubleFunction<Material> wert) {
        double summe = 0.0;
        for (Map.Entry<Material, Integer> e : total().entrySet()) {
            summe += wert.applyAsDouble(e.getKey()) * e.getValue();
        }
        return Scorer.round1(summe);
    }

    /** Ein neuer Topf mit einem zusaetzlichen Einsatz. */
    public Bounty withStake(Stake stake) {
        List<Stake> neu = new ArrayList<>(this.stakes);
        neu.add(stake);
        // Der Zeitpunkt des ersten Einsatzes ist der Beginn des Kopfgelds; spaetere Einsaetze
        // verschieben ihn nicht, sonst waere die Anzeige "seit ..." irrefuehrend.
        long seit = this.stakes.isEmpty() ? stake.time() : this.since;
        return new Bounty(this.target, this.name, seit, neu, this.lastPayout);
    }

    /** Derselbe Topf unter einem aktualisierten Namen des Gejagten. */
    public Bounty withName(String name) {
        return name == null || name.equals(this.name)
                ? this
                : new Bounty(this.target, name, this.since, this.stakes, this.lastPayout);
    }

    /** Der geleerte Topf nach einer Auszahlung; die Sperrfrist beginnt zu laufen. */
    public Bounty paidOut(Payout payout) {
        return new Bounty(this.target, this.name, payout.time(), List.of(), payout);
    }

    /** Der geleerte Topf nach einer Aufhebung - ohne Auszahlung, also ohne neue Sperrfrist. */
    public Bounty cleared() {
        return new Bounty(this.target, this.name, this.since, List.of(), this.lastPayout);
    }
}
