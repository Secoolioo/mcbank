package de.secoolio.bankranking;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

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

    /** Wert fuer alles, was in keiner Regel vorkommt. */
    public static final double FALLBACK = 0.5;

    private record Derived(Material source, int factor) {
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
                    "BRICK",
                    "CHERRY_LOG",
                    "COAL",
                    "CRIMSON_STEM",
                    "DARK_OAK_LOG",
                    "GLASS",
                    "GLOWSTONE_DUST",
                    "GLOW_INK_SAC",
                    "GUNPOWDER",
                    "HONEYCOMB",
                    "INK_SAC",
                    "JUNGLE_LOG",
                    "MANGROVE_LOG",
                    "NETHER_BRICK",
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

        putNuggets();

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

    private static void putNuggets() {
        // Ein Neuntel des Barrens: sonst waere es lohnend, Barren in Nuggets zu zerlegen.
        put(6.0 / 9.0, "IRON_NUGGET");
        put(8.0 / 9.0, "GOLD_NUGGET");
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
        Double direct = VALUES.get(material);
        if (direct != null) {
            return direct;
        }
        Derived derived = BLOCKS.get(material);
        if (derived != null) {
            return baseValue(derived.source(), fallback) * derived.factor();
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


    /** Anzahl der fest hinterlegten Materialien - nur fuer die Log-Zusammenfassung. */
    public static int size() {
        return VALUES.size();
    }
}
