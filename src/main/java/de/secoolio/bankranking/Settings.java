package de.secoolio.bankranking;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemRarity;

/**
 * Unveraenderliche, gepruefte Momentaufnahme der config.yml.
 *
 * <p>Wirft nie: jeder unbrauchbare Wert faellt auf den Standardwert zurueck und erzeugt eine
 * deutsche Warnung im Server-Log, die den genauen Pfad nennt. Die Klasse kommt ohne Bukkit-Statics
 * aus und ist damit ohne laufenden Server testbar.
 */
public final class Settings {

    /** Seltenheits-Faktoren: sie multiplizieren den Materialwert, sie ersetzen ihn nicht. */
    public static final double DEFAULT_COMMON = 1.0;
    public static final double DEFAULT_UNCOMMON = 1.5;
    public static final double DEFAULT_RARE = 2.0;
    public static final double DEFAULT_EPIC = 3.0;
    /** Grundwert fuer Materialien ohne eigenen Eintrag: die Ramsch-Stufe. */
    public static final double DEFAULT_FALLBACK_VALUE = MaterialValues.FALLBACK;
    /** Obergrenze fuer eigene Basiswerte, damit keine Rechnung ins Unendliche laeuft. */
    public static final double MAX_BASE_VALUE = 1_000_000.0;
    /** Ab diesem Kontostand zaehlen Items nur noch die Haelfte (bei Staerke 1.0). */
    public static final double DEFAULT_WEALTH_THRESHOLD = 5000.0;
    /** Wie steil die Wohlstands-Bremse greift. */
    public static final double DEFAULT_WEALTH_STRENGTH = 0.6;
    /** So weit sinkt der Wertfaktor hoechstens. */
    public static final double DEFAULT_WEALTH_FLOOR = 0.05;
    /** Ab so vielen abgegebenen Rohpunkten je Material zaehlt es nur noch die Haelfte. */
    public static final double DEFAULT_SATURATION_THRESHOLD = 5000.0;
    /** Nach so vielen Stunden ist der Saettigungszaehler halbiert. */
    public static final double DEFAULT_SATURATION_HALF_LIFE = 24.0;
    /** So weit sinkt der Preis eines einzelnen Materials hoechstens. */
    public static final double DEFAULT_SATURATION_FLOOR = 0.1;
    public static final double DEFAULT_ENCHANT_BONUS = 2.0;
    public static final String DEFAULT_NPC_NAME = "<gold><bold>Bank</bold></gold>";
    public static final String DEFAULT_NPC_DESCRIPTION = "<gray>Rechtsklick: Items abgeben";
    public static final String DEFAULT_SIDEBAR_TITLE = "<gold><bold>Rangliste</bold></gold>";

    /** Die Zahlenwerte beider Bremsen, gebuendelt statt als Index-Array. */
    private record Damping(boolean wealthEnabled, double wealthThreshold, double wealthStrength,
                           double wealthFloor, boolean saturationEnabled, double saturationThreshold,
                           double saturationHalfLife, double saturationFloor) {
    }

    private final Map<ItemRarity, Double> rarityBase;
    private final Map<Category, Double> categoryMultiplier;
    private final double enchantBonusPerLevel;
    private final double fallbackValue;
    private final boolean wealthEnabled;
    private final double wealthThreshold;
    private final double wealthStrength;
    private final double wealthFloor;
    private final boolean saturationEnabled;
    private final double saturationThreshold;
    private final double saturationHalfLife;
    private final double saturationFloor;
    private final Map<Material, Double> materialBase;
    private final Map<Material, Category> categoryOverrides;
    private final String npcName;
    private final String npcDescription;
    private final boolean confirmHead;
    private final boolean sidebarEnabled;
    private final String sidebarTitle;

    private Settings(Map<ItemRarity, Double> rarityBase,
                     Map<Category, Double> categoryMultiplier,
                     double enchantBonusPerLevel,
                     double fallbackValue,
                     Damping damping,
                     Map<Material, Double> materialBase,
                     Map<Material, Category> categoryOverrides,
                     String npcName,
                     String npcDescription,
                     boolean confirmHead,
                     boolean sidebarEnabled,
                     String sidebarTitle) {
        this.rarityBase = rarityBase;
        this.categoryMultiplier = categoryMultiplier;
        this.enchantBonusPerLevel = enchantBonusPerLevel;
        this.fallbackValue = fallbackValue;
        this.wealthEnabled = damping.wealthEnabled();
        this.wealthThreshold = damping.wealthThreshold();
        this.wealthStrength = damping.wealthStrength();
        this.wealthFloor = damping.wealthFloor();
        this.saturationEnabled = damping.saturationEnabled();
        this.saturationThreshold = damping.saturationThreshold();
        this.saturationHalfLife = damping.saturationHalfLife();
        this.saturationFloor = damping.saturationFloor();
        this.materialBase = Map.copyOf(materialBase);
        this.categoryOverrides = Map.copyOf(categoryOverrides);
        this.npcName = npcName;
        this.npcDescription = npcDescription;
        this.confirmHead = confirmHead;
        this.sidebarEnabled = sidebarEnabled;
        this.sidebarTitle = sidebarTitle;
    }

    public static Settings load(ConfigurationSection c, Logger log) {
        if (c.get("punkte.seltenheit", null) != null) {
            log.warning("config.yml: 'punkte.seltenheit' wird seit Version 1.1 nicht mehr benutzt."
                    + " Die Seltenheit ist jetzt ein Faktor unter 'punkte.seltenheit-faktoren';"
                    + " den Wert eines Materials legst du unter 'punkte.material-basiswerte' fest.");
        }
        Map<ItemRarity, Double> rarity = new EnumMap<>(ItemRarity.class);
        for (ItemRarity value : ItemRarity.values()) {
            String path = "punkte.seltenheit-faktoren." + value.name().toLowerCase(Locale.ROOT);
            rarity.put(value, readPositive(c, path, defaultRarity(value), log));
        }

        Map<Category, Double> multipliers = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            String path = "punkte.kategorien." + category.configKey();
            multipliers.put(category, readPositive(c, path, category.defaultMultiplier(), log));
        }

        double bonus = readPositive(c, "punkte.verzauberung-bonus-pro-stufe", DEFAULT_ENCHANT_BONUS, log);
        double fallback = readPositive(c, "punkte.standardwert", DEFAULT_FALLBACK_VALUE, log);

        Damping damping = new Damping(
                readFlag(c, "punkte.wohlstands-bremse.aktiv", true, log),
                readStrictlyPositive(c, "punkte.wohlstands-bremse.schwelle", DEFAULT_WEALTH_THRESHOLD, log),
                readPositive(c, "punkte.wohlstands-bremse.staerke", DEFAULT_WEALTH_STRENGTH, log),
                readFactor(c, "punkte.wohlstands-bremse.mindestfaktor", DEFAULT_WEALTH_FLOOR, log),
                readFlag(c, "punkte.markt-saettigung.aktiv", true, log),
                readStrictlyPositive(c, "punkte.markt-saettigung.schwelle", DEFAULT_SATURATION_THRESHOLD, log),
                readStrictlyPositive(c, "punkte.markt-saettigung.erholung-stunden",
                        DEFAULT_SATURATION_HALF_LIFE, log),
                readFactor(c, "punkte.markt-saettigung.mindestfaktor", DEFAULT_SATURATION_FLOOR, log));

        Map<Material, Double> materialBase = new HashMap<>();
        ConfigurationSection baseSection = c.getConfigurationSection("punkte.material-basiswerte");
        if (baseSection != null) {
            for (String key : baseSection.getKeys(false)) {
                Material material = parseMaterial(key, "punkte.material-basiswerte", log);
                if (material == null) {
                    continue;
                }
                Object raw = baseSection.get(key);
                if (!(raw instanceof Number number) || !isUsable(number.doubleValue())
                        || number.doubleValue() > MAX_BASE_VALUE) {
                    log.warning("config.yml: Wert für '" + key + "' unter punkte.material-basiswerte ist ungültig"
                            + " (Zahl zwischen 0 und " + MAX_BASE_VALUE + " erwartet) - Eintrag ignoriert");
                    continue;
                }
                materialBase.put(material, number.doubleValue());
            }
        }

        Map<Material, Category> overrides = new HashMap<>();
        ConfigurationSection overrideSection = c.getConfigurationSection("punkte.kategorie-ueberschreibungen");
        if (overrideSection != null) {
            for (String key : overrideSection.getKeys(false)) {
                Material material = parseMaterial(key, "punkte.kategorie-ueberschreibungen", log);
                if (material == null) {
                    continue;
                }
                Category category = Category.parse(overrideSection.getString(key)).orElse(null);
                if (category == null) {
                    log.warning("config.yml: Unbekannte Kategorie '" + overrideSection.getString(key) + "' für '"
                            + key + "' unter punkte.kategorie-ueberschreibungen - Eintrag ignoriert");
                    continue;
                }
                overrides.put(material, category);
            }
        }

        String npcName = readMiniMessage(c, "npc.name", DEFAULT_NPC_NAME, log);
        String npcDescription = readMiniMessage(c, "npc.beschreibung", DEFAULT_NPC_DESCRIPTION, log);
        boolean confirmHead = readFlag(c, "gui.haken-kopf", true, log);
        boolean sidebarEnabled = readFlag(c, "sidebar.aktiv", true, log);
        String sidebarTitle = readMiniMessage(c, "sidebar.titel", DEFAULT_SIDEBAR_TITLE, log);

        return new Settings(rarity, multipliers, bonus, fallback, damping, materialBase, overrides,
                npcName, npcDescription, confirmHead, sidebarEnabled, sidebarTitle);
    }

    private static double defaultRarity(ItemRarity rarity) {
        return switch (rarity) {
            case COMMON -> DEFAULT_COMMON;
            case UNCOMMON -> DEFAULT_UNCOMMON;
            case RARE -> DEFAULT_RARE;
            case EPIC -> DEFAULT_EPIC;
        };
    }

    private static double readPositive(ConfigurationSection c, String path, double fallback, Logger log) {
        // Zwei-Argument-Form: sie fragt die im Jar mitgelieferten Standardwerte nicht ab, damit ein
        // fehlender Schluessel in der Datei des Servers auch wirklich als fehlend gemeldet wird.
        Object raw = c.get(path, null);
        if (raw == null) {
            log.warning("config.yml: '" + path + "' fehlt - Standardwert " + fallback + " wird verwendet");
            return fallback;
        }
        if (!(raw instanceof Number number)) {
            log.warning("config.yml: '" + path + "' ist keine Zahl - Standardwert " + fallback + " wird verwendet");
            return fallback;
        }
        double value = number.doubleValue();
        if (!isUsable(value)) {
            log.warning("config.yml: '" + path + "' = " + value + " ist ungültig (muss >= 0 sein)"
                    + " - Standardwert " + fallback + " wird verwendet");
            return fallback;
        }
        return value;
    }

    /** Wie readPositive, aber der Wert muss echt groesser als null sein (Schwellen, Halbwertszeit). */
    private static double readStrictlyPositive(ConfigurationSection c, String path, double fallback, Logger log) {
        double value = readPositive(c, path, fallback, log);
        if (value <= 0.0) {
            log.warning("config.yml: '" + path + "' = " + value + " ist ungültig (muss größer als 0 sein)"
                    + " - Standardwert " + fallback + " wird verwendet");
            return fallback;
        }
        return value;
    }

    /** Ein Faktor zwischen 0 und 1; groessere Werte wuerden aus einer Bremse einen Verstaerker machen. */
    private static double readFactor(ConfigurationSection c, String path, double fallback, Logger log) {
        double value = readPositive(c, path, fallback, log);
        if (value > 1.0) {
            log.warning("config.yml: '" + path + "' = " + value + " ist ungültig (muss zwischen 0 und 1 liegen)"
                    + " - Standardwert " + fallback + " wird verwendet");
            return fallback;
        }
        return value;
    }

    /** Liest einen Schalter; fehlt er, gilt der Standard. Ein Wert in Anfuehrungszeichen wird gemeldet. */
    private static boolean readFlag(ConfigurationSection c, String path, boolean fallback, Logger log) {
        Object raw = c.get(path, null);
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Boolean flag) {
            return flag;
        }
        log.warning("config.yml: '" + path + "' ist kein Wahrheitswert (true oder false ohne Anführungszeichen)"
                + " - Schalter bleibt " + (fallback ? "an" : "aus"));
        return fallback;
    }

    private static boolean isUsable(double value) {
        return Double.isFinite(value) && value >= 0.0;
    }

    /**
     * Liest einen Text und prueft ihn einmal als MiniMessage.
     *
     * <p>Unbekannte Angaben zeigt MiniMessage einfach als Text an, statt zu scheitern - dieser
     * Versuch ist also nur ein Sicherheitsnetz fuer den Fall, dass eine kuenftige Fassung strenger
     * wird. Er stellt sicher, dass ein Konfigurationsfehler niemals einen Ablauf abbricht.
     */
    private static String readMiniMessage(ConfigurationSection c, String path, String fallback, Logger log) {
        String value = c.getString(path);
        if (value == null) {
            return fallback;
        }
        try {
            MiniMessage.miniMessage().deserialize(value);
            return value;
        } catch (RuntimeException ex) {
            log.warning("config.yml: '" + path + "' ist kein gültiges MiniMessage-Format (" + ex.getMessage()
                    + ") - Standardtext wird verwendet");
            return fallback;
        }
    }

    private static Material parseMaterial(String key, String path, Logger log) {
        Material material = Material.matchMaterial(key);
        if (material == null || material.isLegacy()) {
            log.warning("config.yml: Unbekanntes Material '" + key + "' unter " + path + " - Eintrag ignoriert");
            return null;
        }
        return material;
    }

    public double rarityBase(ItemRarity rarity) {
        return this.rarityBase.getOrDefault(rarity, DEFAULT_COMMON);
    }

    public double multiplier(Category category) {
        return this.categoryMultiplier.getOrDefault(category, category.defaultMultiplier());
    }

    public double enchantBonusPerLevel() {
        return this.enchantBonusPerLevel;
    }

    public double fallbackValue() {
        return this.fallbackValue;
    }

    public boolean wealthEnabled() {
        return this.wealthEnabled;
    }

    public double wealthThreshold() {
        return this.wealthThreshold;
    }

    public double wealthStrength() {
        return this.wealthStrength;
    }

    public double wealthFloor() {
        return this.wealthFloor;
    }

    public boolean saturationEnabled() {
        return this.saturationEnabled;
    }

    public double saturationThreshold() {
        return this.saturationThreshold;
    }

    public double saturationHalfLife() {
        return this.saturationHalfLife;
    }

    public double saturationFloor() {
        return this.saturationFloor;
    }

    public Map<Material, Double> materialBase() {
        return this.materialBase;
    }

    public Map<Material, Category> categoryOverrides() {
        return this.categoryOverrides;
    }

    public String npcName() {
        return this.npcName;
    }

    public String npcDescription() {
        return this.npcDescription;
    }

    /** Soll der Abgeben-Knopf ein Spielerkopf mit Haken sein? Sonst ein gruener Farbstoff. */
    public boolean confirmHead() {
        return this.confirmHead;
    }

    public boolean sidebarEnabled() {
        return this.sidebarEnabled;
    }

    public String sidebarTitle() {
        return this.sidebarTitle;
    }

    /** Einzeilige Zusammenfassung fuer das Server-Log beim Laden. */
    public String summaryLine() {
        StringBuilder sb = new StringBuilder();
        sb.append(MaterialValues.size()).append(" Materialwerte eingebaut | Seltenheit x");
        for (ItemRarity rarity : ItemRarity.values()) {
            sb.append(rarity.name().toLowerCase(Locale.ROOT)).append('=')
                    .append(Scorer.format(rarityBase(rarity))).append(' ');
        }
        sb.append("| ");
        for (Category category : Category.values()) {
            sb.append(category.configKey()).append('=').append(Scorer.format(multiplier(category))).append(' ');
        }
        sb.append("| Bonus ").append(Scorer.format(this.enchantBonusPerLevel)).append("/Stufe | ")
                .append("Wohlstands-Bremse ").append(this.wealthEnabled ? "an" : "aus").append(", ")
                .append("Markt-Sättigung ").append(this.saturationEnabled ? "an" : "aus").append(" | ")
                .append(this.materialBase.size()).append(" Material-Basiswerte, ")
                .append(this.categoryOverrides.size()).append(" Kategorie-Überschreibungen");
        return sb.toString();
    }
}
