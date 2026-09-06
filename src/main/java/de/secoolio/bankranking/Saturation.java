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
    private static final double FORGET_BELOW = 1.0;

    private final Map<Material, Double> amounts = new HashMap<>();
    private long lastDecay;

    public Saturation(long now) {
        this.lastDecay = now;
    }

    /** Laedt einen gespeicherten Zaehler. */
    public void put(Material material, double value) {
        if (value >= FORGET_BELOW) {
            this.amounts.put(material, value);
        }
    }

    public void lastDecay(long millis) {
        this.lastDecay = millis;
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
        if (halfLifeHours <= 0.0 || now <= this.lastDecay || this.amounts.isEmpty()) {
            this.lastDecay = Math.max(this.lastDecay, now);
            return;
        }
        double hours = (now - this.lastDecay) / 3_600_000.0;
        double factor = Math.pow(0.5, hours / halfLifeHours);
        this.lastDecay = now;
        this.amounts.entrySet().removeIf(entry -> {
            double left = entry.getValue() * factor;
            entry.setValue(left);
            return left < FORGET_BELOW;
        });
    }

    public double amount(Material material) {
        return this.amounts.getOrDefault(material, 0.0);
    }

    public void add(Material material, double value) {
        this.amounts.merge(material, value, Double::sum);
    }

    public boolean isEmpty() {
        return this.amounts.isEmpty();
    }
}
