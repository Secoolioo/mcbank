package de.secoolio.bankranking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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

        /** Dieselbe Bewertung mit der tatsaechlich gutgeschriebenen Punktzahl. */
        Valuation withCredit(double credited) {
            double factor = this.rawPoints > 0.0 ? credited / this.rawPoints : 1.0;
            return new Valuation(this.facts, this.category, this.base, this.multiplier,
                    this.rarityFactor, this.enchantBonus, this.rawPoints, factor, credited);
        }
    }

    /**
     * Das Ergebnis einer ganzen Einzahlung.
     *
     * @param saturationDeltas Zuwaechse je Rohstoffgruppe; leer, wenn die Marktsaettigung aus ist.
     *                         Sie werden erst gebucht, wenn die Einzahlung wirklich gespeichert ist.
     */
    public record Deposit(List<Valuation> valuations, int itemCount, double rawTotal,
                          double afterSaturation, double wealthFactor, double total,
                          Map<Material, Double> saturationDeltas) {
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
        Category category = this.classifier.classify(facts.material());
        // Grundwert: eigener Eintrag aus der Config (auch fuer abgeleitete Formen), sonst die Tabelle.
        double base = MaterialValues.baseValue(facts.material(), this.settings.fallbackValue(),
                this.settings.materialBase());
        double multiplier = this.settings.multiplier(category);
        double rarityFactor = this.settings.rarityBase(facts.rarity());
        double bonus = this.settings.enchantBonusPerLevel() * facts.enchantLevelSum();
        double raw = base * facts.amount() * multiplier * rarityFactor + bonus;
        return new Valuation(facts, category, base, multiplier, rarityFactor, bonus, raw, 1.0, raw);
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
     * Wie viel das naechste Item bei diesem Kontostand noch zählt - nur fuer Anzeigen.
     *
     * <p>Weiche Kurve statt Stufen, damit es an keiner Grenze springt:
     * {@code (schwelle / (schwelle + kontostand)) ^ stärke}, nach unten begrenzt.
     */
    public double wealthFactor(double balance) {
        if (!this.settings.wealthEnabled() || balance <= 0.0) {
            return 1.0;
        }
        double factor = Math.pow(this.settings.wealthThreshold()
                / (this.settings.wealthThreshold() + balance), this.settings.wealthStrength());
        return Math.min(1.0, Math.max(this.settings.wealthFloor(), factor));
    }

    /** Wie viel das naechste Item eines Rohstoffs noch zählt - nur fuer Anzeigen. */
    public double saturationFactor(double given) {
        if (!this.settings.saturationEnabled() || given <= 0.0) {
            return 1.0;
        }
        double factor = this.settings.saturationThreshold() / (this.settings.saturationThreshold() + given);
        return Math.min(1.0, Math.max(this.settings.saturationFloor(), factor));
    }

    /**
     * Die Gutschrift fuer {@code raw} Rohpunkte eines Rohstoffs, von dem schon {@code given}
     * abgegeben wurden.
     *
     * <p>Gebucht wird die Flaeche unter der Preiskurve statt ihres Randwertes. Dadurch ist es egal,
     * ob jemand einen grossen oder viele kleine Stapel abgibt und in welcher Reihenfolge sie liegen.
     */
    public double saturationCredit(double given, double raw) {
        if (!this.settings.saturationEnabled() || raw <= 0.0) {
            return Math.max(0.0, raw);
        }
        double threshold = this.settings.saturationThreshold();
        double floor = this.settings.saturationFloor();
        double start = Math.max(0.0, given);
        // Ab dieser Menge greift nur noch der Mindestfaktor.
        double floorAt = floor > 0.0 ? threshold * (1.0 / floor - 1.0) : Double.POSITIVE_INFINITY;
        if (start >= floorAt) {
            return raw * floor;
        }
        double untilFloor = floorAt - start;
        if (raw <= untilFloor) {
            return threshold * Math.log((threshold + start + raw) / (threshold + start));
        }
        return threshold * Math.log((threshold + floorAt) / (threshold + start))
                + (raw - untilFloor) * floor;
    }

    /**
     * Die Gutschrift fuer {@code amount} bereits marktbereinigte Punkte ab dem Kontostand
     * {@code balance}.
     *
     * <p>Ebenfalls die Flaeche unter der Kurve: eine grosse Einzahlung bringt genau so viel wie
     * viele kleine, Horten lohnt sich also nicht.
     */
    public double wealthCredit(double balance, double amount) {
        if (!this.settings.wealthEnabled() || amount <= 0.0) {
            return Math.max(0.0, amount);
        }
        double threshold = this.settings.wealthThreshold();
        double strength = this.settings.wealthStrength();
        double floor = this.settings.wealthFloor();
        double start = Math.max(0.0, balance);
        if (strength <= 0.0) {
            return amount;
        }
        // Ab diesem Kontostand greift nur noch der Mindestfaktor.
        double floorAt = floor > 0.0
                ? threshold * (Math.pow(floor, -1.0 / strength) - 1.0)
                : Double.POSITIVE_INFINITY;
        if (start >= floorAt) {
            return amount * floor;
        }
        double needed = rawNeeded(floorAt, threshold, strength) - rawNeeded(start, threshold, strength);
        if (amount <= needed) {
            return gainedBalance(start, amount, threshold, strength);
        }
        return (floorAt - start) + (amount - needed) * floor;
    }

    /** Stammfunktion: wie viele Rohpunkte noetig sind, um von 0 auf diesen Kontostand zu kommen. */
    private static double rawNeeded(double balance, double threshold, double strength) {
        return Math.pow(threshold + balance, strength + 1.0)
                / ((strength + 1.0) * Math.pow(threshold, strength));
    }

    /** Umkehrung: welcher Kontostand-Zuwachs sich aus {@code amount} Rohpunkten ergibt. */
    private static double gainedBalance(double balance, double amount, double threshold, double strength) {
        double target = rawNeeded(balance, threshold, strength) + amount;
        double reached = Math.pow(target * (strength + 1.0) * Math.pow(threshold, strength),
                1.0 / (strength + 1.0)) - threshold;
        return Math.max(0.0, reached - balance);
    }

    /**
     * Rechnet eine Einzahlung durch, ohne irgendetwas zu verändern.
     *
     * @param given   bisher abgegebene Rohpunkte je Rohstoffgruppe
     * @param balance Kontostand vor dieser Einzahlung
     */
    public Deposit deposit(List<ItemFacts> stacks, Map<Material, Double> given, double balance) {
        List<Valuation> valuations = new ArrayList<>();
        Map<Material, Double> pending = new EnumMap<>(Material.class);
        int itemCount = 0;
        double raw = 0.0;
        double credited = 0.0;
        for (ItemFacts facts : stacks) {
            Valuation base = value(facts);
            Material key = MaterialValues.saturationKey(facts.material());
            double before = given.getOrDefault(key, 0.0) + pending.getOrDefault(key, 0.0);
            double credit = saturationCredit(before, base.rawPoints());
            valuations.add(base.withCredit(credit));
            itemCount += facts.amount();
            raw += base.rawPoints();
            credited += credit;
            if (this.settings.saturationEnabled()) {
                pending.merge(key, base.rawPoints(), Double::sum);
            }
        }
        double total = wealthCredit(balance, credited);
        double wealth = credited > 0.0 ? total / credited : 1.0;
        return new Deposit(valuations, itemCount, round1(raw), round1(credited), wealth,
                round1(total), Map.copyOf(pending));
    }

    /** Bukkit-Adapter: die Bewertungsgrundlagen mehrerer Stapel. */
    public static List<ItemFacts> factsOf(List<ItemStack> stacks) {
        List<ItemFacts> facts = new ArrayList<>(stacks.size());
        for (ItemStack stack : stacks) {
            facts.add(facts(stack));
        }
        return facts;
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
