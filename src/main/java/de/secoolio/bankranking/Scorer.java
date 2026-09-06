package de.secoolio.bankranking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.BundleContents;
import io.papermc.paper.datacomponent.item.ItemContainerContents;
import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Die Punkte-Formel und der Adapter zu Bukkit-Items.
 *
 * <p>{@link #value(ItemFacts)} und {@link #total(List)} sind rein und ohne Server testbar.
 * {@link #facts(ItemStack)} und {@link #unpack(List)} fassen als einzige Stellen echte ItemStacks an.
 */
public final class Scorer {

    /** Maximale Verschachtelungstiefe beim Auspacken von Behaeltern (Buendel in Shulker in ...). */
    private static final int MAX_UNPACK_DEPTH = 4;

    /** Alles, was fuer die Bewertung eines Stapels noetig ist - ohne Bukkit-Laufzeitobjekte. */
    public record ItemFacts(Material material, int amount, ItemRarity rarity, int enchantLevelSum) {
    }

    /** Das Ergebnis der Bewertung eines Stapels, aufgeschluesselt fuer /bankranking wert. */
    public record Valuation(ItemFacts facts, Category category, double base, double multiplier,
                            double rarityFactor, double enchantBonus, double rawPoints,
                            double saturationFactor, double points) {
    }

    /** Das Ergebnis einer ganzen Einzahlung, mit beiden Bremsen. */
    public record Deposit(List<Valuation> valuations, int itemCount, double rawTotal,
                          double wealthFactor, double total) {
    }

    /** Ergebnis des Auspackens: zu bewertende Items und leere Behaelter, die zurueckgehen. */
    public record Unpacked(List<ItemStack> valuables, List<ItemStack> emptiedContainers) {
    }

    private final Settings settings;
    private final CategoryClassifier classifier;

    public Scorer(Settings settings, CategoryClassifier classifier) {
        this.settings = settings;
        this.classifier = classifier;
    }

    /** Bewertet einen Stapel ohne die beiden Bremsen. */
    public Valuation value(ItemFacts facts) {
        return value(facts, 1.0);
    }

    /**
     * Bewertet einen Stapel.
     *
     * @param saturationFactor Marktsättigung dieses Materials (1.0 = unberührter Preis)
     */
    public Valuation value(ItemFacts facts, double saturationFactor) {
        Category category = this.classifier.classify(facts.material());
        // Grundwert: eigener Eintrag aus der Config, sonst die eingebaute Materialtabelle.
        double base = this.settings.materialBase().getOrDefault(facts.material(),
                MaterialValues.baseValue(facts.material(), this.settings.fallbackValue()));
        double multiplier = this.settings.multiplier(category);
        double rarityFactor = this.settings.rarityBase(facts.rarity());
        double bonus = this.settings.enchantBonusPerLevel() * facts.enchantLevelSum();
        double raw = base * facts.amount() * multiplier * rarityFactor + bonus;
        return new Valuation(facts, category, base, multiplier, rarityFactor, bonus,
                raw, saturationFactor, raw * saturationFactor);
    }

    /** Summe einer Einzahlung, auf eine Nachkommastelle gerundet. */
    public double total(List<Valuation> valuations) {
        double sum = 0.0;
        for (Valuation valuation : valuations) {
            sum += valuation.points();
        }
        return round1(sum);
    }

    /**
     * Wie viel ein Item bei diesem Kontostand noch zählt.
     *
     * <p>Weiche Kurve statt Stufen, damit es an keiner Grenze springt:
     * {@code (schwelle / (schwelle + kontostand)) ^ stärke}, nach unten begrenzt.
     */
    public double wealthFactor(double balance) {
        if (!this.settings.wealthEnabled() || balance <= 0.0) {
            return 1.0;
        }
        double threshold = this.settings.wealthThreshold();
        if (threshold <= 0.0) {
            return 1.0;
        }
        double factor = Math.pow(threshold / (threshold + balance), this.settings.wealthStrength());
        return Math.max(this.settings.wealthFloor(), Math.min(1.0, factor));
    }

    /**
     * Wie viel ein Material noch zählt, wenn davon schon {@code given} Rohpunkte abgegeben wurden.
     */
    public double saturationFactor(double given) {
        if (!this.settings.saturationEnabled() || given <= 0.0) {
            return 1.0;
        }
        double threshold = this.settings.saturationThreshold();
        if (threshold <= 0.0) {
            return 1.0;
        }
        double factor = threshold / (threshold + given);
        return Math.max(this.settings.saturationFloor(), Math.min(1.0, factor));
    }

    /**
     * Bewertet eine ganze Einzahlung: jeder Stapel drückt den Preis seines Materials weiter,
     * am Ende greift die Wohlstands-Bremse auf die Summe.
     *
     * @param saturation gelesen und fortgeschrieben; {@code null} schaltet die Sättigung ab
     */
    public Deposit deposit(List<ItemStack> stacks, Saturation saturation, double balance) {
        List<Valuation> valuations = new ArrayList<>();
        int itemCount = 0;
        double raw = 0.0;
        for (ItemStack stack : stacks) {
            ItemFacts facts = facts(stack);
            double given = saturation == null ? 0.0 : saturation.amount(facts.material());
            Valuation valuation = value(facts, saturationFactor(given));
            valuations.add(valuation);
            itemCount += facts.amount();
            raw += valuation.rawPoints();
            if (saturation != null) {
                // Der nächste Stapel desselben Materials ist schon weniger wert.
                saturation.add(facts.material(), valuation.rawPoints());
            }
        }
        double afterSaturation = 0.0;
        for (Valuation valuation : valuations) {
            afterSaturation += valuation.points();
        }
        double wealth = wealthFactor(balance);
        return new Deposit(valuations, itemCount, round1(raw), wealth, round1(afterSaturation * wealth));
    }

    public static double round1(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /** Deutsche Anzeige mit einer Nachkommastelle, z.B. "1234,5". */
    public static String format(double value) {
        return String.format(Locale.GERMANY, "%.1f", value);
    }

    /**
     * Liest die Bewertungsgrundlagen aus einem echten ItemStack.
     *
     * <p>Die Seltenheit kommt vom Item-Typ selbst ({@link ItemType#getItemRarity()}), also der
     * eingebauten Vanilla-Seltenheit. Damit zaehlt eine per Befehl gesetzte rarity-Komponente nicht.
     */
    public static ItemFacts facts(ItemStack stack) {
        Material material = stack.getType();
        ItemRarity rarity = ItemRarity.COMMON;
        ItemType type = material.asItemType();
        if (type != null && type.getItemRarity() != null) {
            rarity = type.getItemRarity();
        }
        int levels = 0;
        for (int level : stack.getEnchantments().values()) {
            levels += level;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta instanceof EnchantmentStorageMeta storage) {
            for (int level : storage.getStoredEnchants().values()) {
                levels += level;
            }
        }
        return new ItemFacts(material, stack.getAmount(), rarity, levels);
    }

    /**
     * Packt gefuellte Shulker-Boxen und Buendel aus: der Inhalt wird bewertet, der leere Behaelter
     * geht an den Spieler zurueck.
     */
    public static Unpacked unpack(List<ItemStack> input) {
        List<ItemStack> valuables = new ArrayList<>();
        List<ItemStack> emptied = new ArrayList<>();
        for (ItemStack stack : input) {
            unpackInto(stack, valuables, emptied, 0);
        }
        return new Unpacked(valuables, emptied);
    }

    private static void unpackInto(ItemStack stack, List<ItemStack> valuables, List<ItemStack> emptied, int depth) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        if (depth < MAX_UNPACK_DEPTH) {
            List<ItemStack> inner = new ArrayList<>();
            boolean hasContainer = false;
            boolean hasBundle = false;

            ItemContainerContents container = stack.getData(DataComponentTypes.CONTAINER);
            if (container != null && !container.contents().isEmpty()) {
                inner.addAll(container.contents());
                hasContainer = true;
            }
            BundleContents bundle = stack.getData(DataComponentTypes.BUNDLE_CONTENTS);
            if (bundle != null && !bundle.contents().isEmpty()) {
                inner.addAll(bundle.contents());
                hasBundle = true;
            }

            if (hasContainer || hasBundle) {
                ItemStack empty = stack.clone();
                if (hasContainer) {
                    // resetData stellt den Vanilla-Standard (leerer Behaelter) her.
                    // unsetData wuerde die Komponente als "entfernt" markieren - das ist ein anderes Item.
                    empty.resetData(DataComponentTypes.CONTAINER);
                }
                if (hasBundle) {
                    empty.resetData(DataComponentTypes.BUNDLE_CONTENTS);
                }
                emptied.add(empty);
                for (ItemStack item : inner) {
                    unpackInto(item, valuables, emptied, depth + 1);
                }
                return;
            }
        }
        if (hasContents(stack)) {
            // Zu tief verschachtelt: unveraendert und ungewertet zurueckgeben, statt den Inhalt zu vernichten.
            emptied.add(stack.clone());
            return;
        }
        valuables.add(stack);
    }

    /** Traegt dieser Stapel noch Inhalt in einer Shulker-Box oder einem Buendel? */
    private static boolean hasContents(ItemStack stack) {
        ItemContainerContents container = stack.getData(DataComponentTypes.CONTAINER);
        if (container != null && !container.contents().isEmpty()) {
            return true;
        }
        BundleContents bundle = stack.getData(DataComponentTypes.BUNDLE_CONTENTS);
        return bundle != null && !bundle.contents().isEmpty();
    }
}
