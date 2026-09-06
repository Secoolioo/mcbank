package de.secoolio.bankranking;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;

/**
 * Merkt sich je Spieler und Material, wie viel Wert davon schon abgegeben wurde.
 *
 * <p>Wer denselben Rohstoff massenhaft abliefert, druckt damit den Preis - wie bei einem Markt,
 * den man mit Ware überschwemmt. Der Zähler baut sich mit der Zeit wieder ab (Halbwertszeit aus
 * der Konfiguration), sodass eine Pause den Preis erholt und eine Dauerfarm sich selbst entwertet.
 */
public final class Saturation {

    /** Zähler unter diesem Wert werden verworfen, damit players.yml nicht zuwuchert. */
    static final double FORGET_BELOW = 1.0;

    private final Map<Material, Double> amounts = new HashMap<>();
    private long lastDecay;

    public Saturation(long now) {
        this.lastDecay = Math.max(0L, now);
    }

    /** Laedt einen gespeicherten Zaehler. */
    public void put(Material material, double value) {
        if (Double.isFinite(value) && value >= FORGET_BELOW) {
            this.amounts.put(material, value);
        }
    }

    public void lastDecay(long millis) {
        this.lastDecay = Math.max(0L, millis);
    }

    public long lastDecay() {
        return this.lastDecay;
    }

    public Map<Material, Double> amounts() {
        return this.amounts;
    }

    /**
     * Rechnet den Zeitverfall bis jetzt ein. Nach jeder Halbwertszeit ist nur noch die Haelfte
     * des Zaehlers uebrig.
     */
    public void decay(long now, double halfLifeHours) {
        long elapsed = now - this.lastDecay;
        if (elapsed <= 0L || this.amounts.isEmpty()) {
            // Uhr steht oder laeuft rueckwaerts: nichts abbauen, aber auch nicht in die Zukunft rutschen.
            this.lastDecay = Math.max(this.lastDecay, Math.max(0L, now));
            return;
        }
        double factor = Math.pow(0.5, elapsed / 3_600_000.0 / halfLifeHours);
        this.lastDecay = now;
        if (!(factor < 1.0)) {
            return;
        }
        this.amounts.replaceAll((material, value) -> value * factor);
        forgetSmall();
    }

    /**
     * Entfernt alles, was zu klein oder keine gueltige Zahl mehr ist. Der einzige Ort mit dieser
     * Schwelle - Laden, Abbau und Speichern benutzen ihn gemeinsam.
     */
    public void forgetSmall() {
        this.amounts.values().removeIf(value -> !Double.isFinite(value) || value < FORGET_BELOW);
    }

    public double amount(Material material) {
        return this.amounts.getOrDefault(material, 0.0);
    }

    /** Wortgetreue Kopie, auch mit Werten unterhalb der Schwelle. */
    public Saturation copy() {
        Saturation copy = new Saturation(this.lastDecay);
        copy.amounts.putAll(this.amounts);
        return copy;
    }

    /** Unveraenderliche Sicht fuer die Bewertung. */
    public Map<Material, Double> snapshot() {
        return Map.copyOf(this.amounts);
    }

    /** Der einzige Schreibweg aus einer Einzahlung: die Zuwaechse einer gebuchten Abgabe. */
    public void commit(Map<Material, Double> deltas) {
        deltas.forEach((material, value) -> {
            if (Double.isFinite(value) && value > 0.0) {
                this.amounts.merge(material, value, Double::sum);
            }
        });
    }

    public boolean isEmpty() {
        return this.amounts.isEmpty();
    }
}
