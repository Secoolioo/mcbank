package de.secoolio.bankranking;

/**
 * Die Rangstufen nach Kontostand. Sie sind reine Anzeige - gerechnet wird mit der weichen
 * Kurve in {@link Scorer#wealthFactor(double)}, damit es keine Sprünge an den Grenzen gibt.
 */
public enum Rank {

    BRONZE("Bronze", "<color:#cd7f32>", 0),
    SILBER("Silber", "<gray>", 1_000),
    GOLD("Gold", "<gold>", 5_000),
    PLATIN("Platin", "<aqua>", 20_000),
    DIAMANT("Diamant", "<blue>", 50_000),
    NETHERITE("Netherite", "<dark_purple>", 150_000);

    private final String displayName;
    private final String color;
    private final int from;

    Rank(String displayName, String color, int from) {
        this.displayName = displayName;
        this.color = color;
        this.from = from;
    }

    public String displayName() {
        return this.displayName;
    }

    /** Rangname mit Farbe im MiniMessage-Format. */
    public String colored() {
        return this.color + this.displayName;
    }

    public int from() {
        return this.from;
    }

    /** Ab wie vielen Punkten der nächste Rang beginnt; 0 beim höchsten Rang. */
    public int nextAt() {
        Rank[] all = values();
        return ordinal() + 1 < all.length ? all[ordinal() + 1].from : 0;
    }

    public static Rank of(double points) {
        Rank current = BRONZE;
        for (Rank rank : values()) {
            if (points >= rank.from) {
                current = rank;
            }
        }
        return current;
    }
}
