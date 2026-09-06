package de.secoolio.bankranking;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Material;

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

    /** Die Farbe dieses Rangs als MiniMessage-Anfang, z.B. "<gold>". */
    public String color() {
        return this.color;
    }

    /** Rahmenfarbe im Fenster. */
    public Material pane() {
        return switch (this) {
            case BRONZE -> Material.ORANGE_STAINED_GLASS_PANE;
            case SILBER -> Material.WHITE_STAINED_GLASS_PANE;
            case GOLD -> Material.YELLOW_STAINED_GLASS_PANE;
            case PLATIN -> Material.LIGHT_BLUE_STAINED_GLASS_PANE;
            case DIAMANT -> Material.BLUE_STAINED_GLASS_PANE;
            case NETHERITE -> Material.PURPLE_STAINED_GLASS_PANE;
        };
    }

    /** Sinnbild dieses Rangs. */
    public Material icon() {
        return switch (this) {
            case BRONZE -> Material.COPPER_INGOT;
            case SILBER -> Material.IRON_INGOT;
            case GOLD -> Material.GOLD_INGOT;
            case PLATIN -> Material.PRISMARINE_CRYSTALS;
            case DIAMANT -> Material.DIAMOND;
            case NETHERITE -> Material.NETHERITE_INGOT;
        };
    }

    /** Farbe des Fortschrittsbalkens am oberen Bildrand. */
    public BossBar.Color barColor() {
        return switch (this) {
            case BRONZE -> BossBar.Color.RED;
            case SILBER -> BossBar.Color.WHITE;
            case GOLD -> BossBar.Color.YELLOW;
            case PLATIN -> BossBar.Color.GREEN;
            case DIAMANT -> BossBar.Color.BLUE;
            case NETHERITE -> BossBar.Color.PURPLE;
        };
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
