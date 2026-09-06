package de.secoolio.bankranking;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;

/**
 * Die eingebauten Grundwerte je Material.
 *
 * <p>Minecraft stuft im Spiel fast jeden Gegenstand als "common" ein - ein Diamant ist dort so
 * selten wie ein Block Erde. Diese Tabelle bewertet stattdessen den Aufwand, den ein Gegenstand
 * im Spiel kostet. Jeder Wert laesst sich in der config.yml unter punkte.material-basiswerte
 * einzeln ueberschreiben.
 *
 * <p>Abgeleitet werden: Bloecke aus ihrem Rohstoff (ein Diamantblock zaehlt wie neun Diamanten)
 * und Werkzeuge, Waffen und Ruestung aus ihrer Stufe (Holz bis Netherite).
 */
public final class MaterialValues {

    /** Wert fuer alles, was in keiner Regel vorkommt: die Ramsch-Stufe. */
    public static final double FALLBACK = 0.05;

    /** Ein Material, das sich verlustfrei aus einem anderen herstellen laesst. */
    private record Derived(Material source, double factor) {
    }

    private static final Map<Material, Double> VALUES = new EnumMap<>(Material.class);
    private static final Map<Material, Derived> BLOCKS = new EnumMap<>(Material.class);
    private static final Map<String, Double> TIERS = new LinkedHashMap<>();
    private static final String[] GEAR_SUFFIXES = {
            "_SWORD", "_PICKAXE", "_AXE", "_SHOVEL", "_HOE", "_SPEAR",
            "_HELMET", "_CHESTPLATE", "_LEGGINGS", "_BOOTS", "_HORSE_ARMOR"
    };

    private MaterialValues() {
    }

    static {
        put(0.05,
                    "ANDESITE",
                    "BASALT",
                    "BLACKSTONE",
                    "CALCITE",
                    "CLAY",
                    "CLAY_BALL",
                    "COARSE_DIRT",
                    "COBBLED_DEEPSLATE",
                    "COBBLESTONE",
                    "DEEPSLATE",
                    "DIORITE",
                    "DIRT",
                    "DRIPSTONE_BLOCK",
                    "END_STONE",
                    "FLINT",
                    "GRANITE",
                    "GRASS_BLOCK",
                    "GRAVEL",
                    "ICE",
                    "MAGMA_BLOCK",
                    "MOSS_BLOCK",
                    "MYCELIUM",
                    "NETHERRACK",
                    "PACKED_ICE",
                    "PODZOL",
                    "RED_SAND",
                    "RED_SANDSTONE",
                    "ROOTED_DIRT",
                    "SAND",
                    "SANDSTONE",
                    "SCULK",
                    "SNOWBALL",
                    "SNOW_BLOCK",
                    "SOUL_SAND",
                    "SOUL_SOIL",
                    "STONE",
                    "TERRACOTTA",
                    "TUFF");
        put(0.2,
                    "BAMBOO",
                    "BEETROOT_SEEDS",
                    "CACTUS",
                    "DRIED_KELP",
                    "FERN",
                    "KELP",
                    "MELON_SEEDS",
                    "PITCHER_POD",
                    "PUMPKIN_SEEDS",
                    "SEAGRASS",
                    "SHORT_GRASS",
                    "STICK",
                    "SUGAR_CANE",
                    "TORCHFLOWER_SEEDS",
                    "VINE",
                    "WHEAT_SEEDS");
        put(0.3,
                    "ACACIA_PLANKS",
                    "APPLE",
                    "BAKED_POTATO",
                    "BAMBOO_PLANKS",
                    "BEETROOT",
                    "BEETROOT_SOUP",
                    "BIRCH_PLANKS",
                    "BONE",
                    "BONE_MEAL",
                    "BOOK",
                    "BREAD",
                    "CARROT",
                    "CHARCOAL",
                    "CHERRY_PLANKS",
                    "CHORUS_FRUIT",
                    "COOKIE",
                    "CRIMSON_PLANKS",
                    "DARK_OAK_PLANKS",
                    "EGG",
                    "FEATHER",
                    "GLOW_BERRIES",
                    "JUNGLE_PLANKS",
                    "LEATHER",
                    "MANGROVE_PLANKS",
                    "MELON_SLICE",
                    "MUSHROOM_STEW",
                    "OAK_PLANKS",
                    "PALE_OAK_PLANKS",
                    "PAPER",
                    "POTATO",
                    "PUMPKIN_PIE",
                    "RABBIT_STEW",
                    "SPRUCE_PLANKS",
                    "STRING",
                    "SUSPICIOUS_STEW",
                    "WARPED_PLANKS",
                    "WHEAT",
                    "WHITE_WOOL");
        put(0.6,
                    "BEEF",
                    "CAKE",
                    "CHICKEN",
                    "COD",
                    "COOKED_BEEF",
                    "COOKED_CHICKEN",
                    "COOKED_COD",
                    "COOKED_MUTTON",
                    "COOKED_PORKCHOP",
                    "COOKED_RABBIT",
                    "COOKED_SALMON",
                    "HONEY_BOTTLE",
                    "MUTTON",
                    "PORKCHOP",
                    "PUFFERFISH",
                    "RABBIT",
                    "SALMON",
                    "TROPICAL_FISH");
        put(1.0,
                    "ACACIA_LOG",
                    "BIRCH_LOG",
                    "CHERRY_LOG",
                    "COAL",
                    "CRIMSON_STEM",
                    "DARK_OAK_LOG",
                    "GLOWSTONE_DUST",
                    "GLOW_INK_SAC",
                    "GUNPOWDER",
                    "HONEYCOMB",
                    "INK_SAC",
                    "JUNGLE_LOG",
                    "MANGROVE_LOG",
                    "OAK_LOG",
                    "OBSIDIAN",
                    "PALE_OAK_LOG",
                    "PRISMARINE_CRYSTALS",
                    "RABBIT_HIDE",
                    "ROTTEN_FLESH",
                    "SLIME_BALL",
                    "SPIDER_EYE",
                    "SPRUCE_LOG",
                    "SWEET_BERRIES",
                    "WARPED_STEM");
        put(2.0,
                    "AMETHYST_SHARD",
                    "COPPER_INGOT",
                    "COPPER_ORE",
                    "CRYING_OBSIDIAN",
                    "DEEPSLATE_COPPER_ORE",
                    "GLOWSTONE",
                    "PRISMARINE_SHARD",
                    "QUARTZ",
                    "RAW_COPPER",
                    "REDSTONE");
        put(3.0,
                    "COAL_ORE",
                    "DEEPSLATE_COAL_ORE",
                    "LAPIS_LAZULI",
                    "NETHER_QUARTZ_ORE");
        put(6.0,
                    "DEEPSLATE_IRON_ORE",
                    "DEEPSLATE_LAPIS_ORE",
                    "DEEPSLATE_REDSTONE_ORE",
                    "IRON_INGOT",
                    "IRON_ORE",
                    "LAPIS_ORE",
                    "RAW_IRON",
                    "REDSTONE_ORE");
        put(8.0,
                    "ARMADILLO_SCUTE",
                    "BLAZE_ROD",
                    "BREEZE_ROD",
                    "DEEPSLATE_GOLD_ORE",
                    "ENDER_PEARL",
                    "GOLD_INGOT",
                    "GOLD_ORE",
                    "NETHER_GOLD_ORE",
                    "PHANTOM_MEMBRANE",
                    "RAW_GOLD",
                    "TURTLE_SCUTE");
        put(20.0,
                    "DEEPSLATE_DIAMOND_ORE",
                    "DIAMOND",
                    "DIAMOND_ORE",
                    "ENDER_EYE",
                    "GHAST_TEAR",
                    "GOLDEN_APPLE",
                    "MUSIC_DISC_11",
                    "MUSIC_DISC_13",
                    "MUSIC_DISC_BLOCKS",
                    "MUSIC_DISC_CAT",
                    "MUSIC_DISC_CHIRP",
                    "MUSIC_DISC_FAR",
                    "MUSIC_DISC_MALL",
                    "MUSIC_DISC_MELLOHI",
                    "MUSIC_DISC_STAL",
                    "MUSIC_DISC_STRAD",
                    "MUSIC_DISC_WAIT",
                    "MUSIC_DISC_WARD");
        put(25.0,
                    "DEEPSLATE_EMERALD_ORE",
                    "ECHO_SHARD",
                    "EMERALD",
                    "EMERALD_ORE",
                    "GOAT_HORN",
                    "NAUTILUS_SHELL",
                    "OMINOUS_BOTTLE",
                    "SHULKER_SHELL");
        put(40.0,
                    "DISC_FRAGMENT_5",
                    "ENCHANTED_BOOK",
                    "MUSIC_DISC_5",
                    "MUSIC_DISC_CREATOR",
                    "MUSIC_DISC_OTHERSIDE",
                    "MUSIC_DISC_PIGSTEP",
                    "MUSIC_DISC_PRECIPICE",
                    "MUSIC_DISC_RELIC",
                    "RECOVERY_COMPASS",
                    "SNIFFER_EGG");
        put(60.0,
                    "ANCIENT_DEBRIS",
                    "HEART_OF_THE_SEA",
                    "NETHERITE_SCRAP",
                    "TRIDENT",
                    "WITHER_SKELETON_SKULL");
        put(120.0,
                    "BEACON",
                    "CONDUIT",
                    "ENCHANTED_GOLDEN_APPLE",
                    "HEAVY_CORE",
                    "NETHERITE_UPGRADE_SMITHING_TEMPLATE");
        put(200.0,
                    "ELYTRA",
                    "NETHERITE_INGOT",
                    "NETHER_STAR",
                    "TOTEM_OF_UNDYING");
        put(400.0,
                    "DRAGON_HEAD",
                    "MACE");
        put(2000.0,
                    "DRAGON_EGG");

        BLOCKS.put(Material.AMETHYST_BLOCK, new Derived(Material.AMETHYST_SHARD, 4));
        BLOCKS.put(Material.BONE_BLOCK, new Derived(Material.BONE_MEAL, 9));
        BLOCKS.put(Material.COAL_BLOCK, new Derived(Material.COAL, 9));
        BLOCKS.put(Material.COPPER_BLOCK, new Derived(Material.COPPER_INGOT, 9));
        BLOCKS.put(Material.DIAMOND_BLOCK, new Derived(Material.DIAMOND, 9));
        BLOCKS.put(Material.DRIED_KELP_BLOCK, new Derived(Material.DRIED_KELP, 9));
        BLOCKS.put(Material.EMERALD_BLOCK, new Derived(Material.EMERALD, 9));
        BLOCKS.put(Material.GOLD_BLOCK, new Derived(Material.GOLD_INGOT, 9));
        BLOCKS.put(Material.HAY_BLOCK, new Derived(Material.WHEAT, 9));
        BLOCKS.put(Material.IRON_BLOCK, new Derived(Material.IRON_INGOT, 9));
        BLOCKS.put(Material.LAPIS_BLOCK, new Derived(Material.LAPIS_LAZULI, 9));
        BLOCKS.put(Material.NETHERITE_BLOCK, new Derived(Material.NETHERITE_INGOT, 9));
        BLOCKS.put(Material.QUARTZ_BLOCK, new Derived(Material.QUARTZ, 4));
        BLOCKS.put(Material.RAW_COPPER_BLOCK, new Derived(Material.RAW_COPPER, 9));
        BLOCKS.put(Material.RAW_GOLD_BLOCK, new Derived(Material.RAW_GOLD, 9));
        BLOCKS.put(Material.RAW_IRON_BLOCK, new Derived(Material.RAW_IRON, 9));
        BLOCKS.put(Material.REDSTONE_BLOCK, new Derived(Material.REDSTONE, 9));
        BLOCKS.put(Material.SLIME_BLOCK, new Derived(Material.SLIME_BALL, 9));

        // Nuggets sind ein Neuntel ihres Barrens - sonst waere Zerlegen ein Gewinn.
        BLOCKS.put(Material.IRON_NUGGET, new Derived(Material.IRON_INGOT, 1.0 / 9.0));
        BLOCKS.put(Material.GOLD_NUGGET, new Derived(Material.GOLD_INGOT, 1.0 / 9.0));
        BLOCKS.put(Material.COPPER_NUGGET, new Derived(Material.COPPER_INGOT, 1.0 / 9.0));

        // Verlustfreie Ofen- und Werkbank-Rezepte: das Erzeugnis darf nie mehr wert sein als
        // sein Rohstoff, sonst laesst sich mit einer Farm und einem Ofen Punkte drucken.
        BLOCKS.put(Material.BRICK, new Derived(Material.CLAY_BALL, 1));
        BLOCKS.put(Material.BRICKS, new Derived(Material.BRICK, 4));
        BLOCKS.put(Material.GLASS, new Derived(Material.SAND, 1));
        BLOCKS.put(Material.GLASS_PANE, new Derived(Material.GLASS, 3.0 / 8.0));
        BLOCKS.put(Material.NETHER_BRICK, new Derived(Material.NETHERRACK, 1));
        BLOCKS.put(Material.NETHER_BRICKS, new Derived(Material.NETHER_BRICK, 4));
        BLOCKS.put(Material.SMOOTH_STONE, new Derived(Material.STONE, 1));
        BLOCKS.put(Material.STONE_BRICKS, new Derived(Material.STONE, 1));
        BLOCKS.put(Material.MOSSY_STONE_BRICKS, new Derived(Material.STONE, 1));
        BLOCKS.put(Material.CRACKED_STONE_BRICKS, new Derived(Material.STONE, 1));
        BLOCKS.put(Material.CHISELED_STONE_BRICKS, new Derived(Material.STONE, 1));
        BLOCKS.put(Material.STONE_SLAB, new Derived(Material.STONE, 0.5));
        BLOCKS.put(Material.STONE_BRICK_SLAB, new Derived(Material.STONE, 0.5));
        BLOCKS.put(Material.COBBLESTONE_SLAB, new Derived(Material.COBBLESTONE, 0.5));
        BLOCKS.put(Material.STONE_STAIRS, new Derived(Material.STONE, 1));
        BLOCKS.put(Material.STONE_BRICK_STAIRS, new Derived(Material.STONE, 1));
        BLOCKS.put(Material.COBBLESTONE_STAIRS, new Derived(Material.COBBLESTONE, 1));
        BLOCKS.put(Material.STONE_BRICK_WALL, new Derived(Material.STONE, 1));
        BLOCKS.put(Material.COBBLESTONE_WALL, new Derived(Material.COBBLESTONE, 1));
        BLOCKS.put(Material.POLISHED_ANDESITE, new Derived(Material.ANDESITE, 1));
        BLOCKS.put(Material.POLISHED_DIORITE, new Derived(Material.DIORITE, 1));
        BLOCKS.put(Material.POLISHED_GRANITE, new Derived(Material.GRANITE, 1));
        BLOCKS.put(Material.POLISHED_DEEPSLATE, new Derived(Material.COBBLED_DEEPSLATE, 1));
        BLOCKS.put(Material.DEEPSLATE_BRICKS, new Derived(Material.COBBLED_DEEPSLATE, 1));
        BLOCKS.put(Material.DEEPSLATE_TILES, new Derived(Material.COBBLED_DEEPSLATE, 1));
        BLOCKS.put(Material.SANDSTONE, new Derived(Material.SAND, 4));
        BLOCKS.put(Material.SMOOTH_SANDSTONE, new Derived(Material.SANDSTONE, 1));
        BLOCKS.put(Material.CUT_SANDSTONE, new Derived(Material.SANDSTONE, 1));

        // Fertige Ausruestung nach Stufe. Nachgeschlagen wird mit dem exakten Praefix, das beim
        // Abschneiden der Endung uebrig bleibt - die Reihenfolge in der Map spielt keine Rolle.
        TIERS.put("NETHERITE", 450.0);
        TIERS.put("CHAINMAIL", 25.0);
        TIERS.put("DIAMOND", 70.0);
        TIERS.put("LEATHER", 3.0);
        TIERS.put("WOODEN", 1.0);
        TIERS.put("COPPER", 6.0);
        TIERS.put("GOLDEN", 25.0);
        TIERS.put("TURTLE", 40.0);
        TIERS.put("STONE", 1.5);
        TIERS.put("IRON", 20.0);
    }

    private static void put(double value, String... names) {
        for (String name : names) {
            Material material = Material.getMaterial(name);
            if (material != null) {
                VALUES.put(material, value);
            }
        }
    }

    /**
     * Grundwert eines Materials, ohne Kategorie-Faktor und ohne Verzauberungen.
     *
     * @param fallback Wert fuer Materialien, die in keiner Regel vorkommen
     */
    public static double baseValue(Material material, double fallback) {
        return baseValue(material, fallback, Map.of());
    }

    /**
     * Grundwert eines Materials mit den Basiswerten aus der Konfiguration.
     *
     * <p>Reihenfolge: eigener Eintrag des Admins, eingebaute Tabelle, Ableitung aus dem Rohstoff
     * (dabei gilt ein Admin-Wert auch fuer alle abgeleiteten Formen), Ausruestungsstufe, Rueckfall.
     *
     * @param overrides Basiswerte aus punkte.material-basiswerte
     */
    public static double baseValue(Material material, double fallback, Map<Material, Double> overrides) {
        Double override = overrides.get(material);
        if (override != null) {
            return override;
        }
        Double direct = VALUES.get(material);
        if (direct != null) {
            return direct;
        }
        Derived derived = BLOCKS.get(material);
        if (derived != null) {
            return baseValue(derived.source(), fallback, overrides) * derived.factor();
        }
        String name = material.name();
        for (String suffix : GEAR_SUFFIXES) {
            if (name.endsWith(suffix)) {
                String tier = name.substring(0, name.length() - suffix.length());
                Double value = TIERS.get(tier);
                if (value != null) {
                    return value;
                }
            }
        }
        return fallback;
    }

    /**
     * Der Rohstoff, auf dessen Marktsaettigung dieses Material zaehlt.
     *
     * <p>Ein Eisenblock, ein Eisenbarren und ein Eisennugget belasten denselben Zaehler, sonst
     * liesse sich der gedrueckte Preis durch Umkraften umgehen.
     */
    public static Material saturationKey(Material material) {
        Material current = material;
        // Die Ableitungskette ist endlich und zyklenfrei; die Schranke schuetzt vor Tippfehlern.
        for (int step = 0; step < 8; step++) {
            Derived derived = BLOCKS.get(current);
            if (derived == null) {
                return current;
            }
            current = derived.source();
        }
        return current;
    }

    /** Der direkte Rohstoff einer abgeleiteten Form, falls es einen gibt. */
    public static Optional<Material> blockSource(Material material) {
        Derived derived = BLOCKS.get(material);
        return derived == null ? Optional.empty() : Optional.of(derived.source());
    }


    /** Anzahl der fest hinterlegten Materialien - nur fuer die Log-Zusammenfassung. */
    public static int size() {
        return VALUES.size();
    }
}
