package de.secoolio.bankranking;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.Material;

/**
 * Ordnet ein Material einer Kategorie zu. Arbeitet ausschliesslich mit dem Material-Namen
 * und einem injizierten "essbar"-Test, damit die Zuordnung ohne laufenden Server testbar bleibt
 * (org.bukkit.Tag und Material#isEdible() brauchen die Server-Registry).
 */
public final class CategoryClassifier {

    private static final Set<String> WAFFEN = Set.of(
            "BOW", "CROSSBOW", "TRIDENT", "MACE", "ARROW", "SPECTRAL_ARROW", "TIPPED_ARROW");

    private static final Set<String> WERKZEUGE = Set.of(
            "SHEARS", "FLINT_AND_STEEL", "FISHING_ROD", "BRUSH", "SPYGLASS", "COMPASS",
            "RECOVERY_COMPASS", "CLOCK", "LEAD", "NAME_TAG", "SADDLE", "BUCKET");

    private static final Set<String> RUESTUNG = Set.of(
            "ELYTRA", "SHIELD", "WOLF_ARMOR", "TURTLE_HELMET");

    private static final Set<String> RESSOURCEN = Set.of(
            "DIAMOND", "EMERALD", "LAPIS_LAZULI", "QUARTZ", "COAL", "CHARCOAL", "REDSTONE", "FLINT",
            "STRING", "LEATHER", "FEATHER", "BONE", "BONE_MEAL", "SLIME_BALL", "GUNPOWDER",
            "BLAZE_ROD", "BREEZE_ROD", "ENDER_PEARL", "ENDER_EYE", "GHAST_TEAR", "NETHER_STAR",
            "HEART_OF_THE_SEA", "NAUTILUS_SHELL", "SHULKER_SHELL", "PHANTOM_MEMBRANE", "RABBIT_HIDE",
            "INK_SAC", "GLOW_INK_SAC", "HONEYCOMB", "PAPER", "BOOK", "STICK", "STONE", "COBBLESTONE",
            "DEEPSLATE", "COBBLED_DEEPSLATE", "DIRT", "SAND", "RED_SAND", "GRAVEL", "CLAY",
            "CLAY_BALL", "OBSIDIAN", "CRYING_OBSIDIAN", "NETHERRACK", "SOUL_SAND", "SOUL_SOIL",
            "GLOWSTONE", "GLOWSTONE_DUST", "END_STONE", "WHEAT", "SUGAR_CANE", "BAMBOO", "KELP",
            "AMETHYST_SHARD", "ECHO_SHARD", "PRISMARINE_SHARD", "PRISMARINE_CRYSTALS", "COPPER_INGOT");

    private static final Set<String> BLOCK_PREFIXES = Set.of(
            "DIAMOND", "EMERALD", "GOLD", "IRON", "COPPER", "NETHERITE", "LAPIS", "REDSTONE",
            "COAL", "QUARTZ", "AMETHYST");

    private final Map<Material, Category> overrides;
    private final Predicate<Material> edible;

    public CategoryClassifier(Map<Material, Category> overrides, Predicate<Material> edible) {
        this.overrides = Map.copyOf(overrides);
        this.edible = edible;
    }

    public Category classify(Material material) {
        Category override = this.overrides.get(material);
        if (override != null) {
            return override;
        }
        String n = material.name();

        if (RUESTUNG.contains(n)
                || n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS") || n.endsWith("_HORSE_ARMOR") || n.endsWith("_HARNESS")) {
            return Category.RUESTUNG;
        }
        if (WAFFEN.contains(n) || n.endsWith("_SWORD") || n.endsWith("_SPEAR")) {
            return Category.WAFFEN;
        }
        if (WERKZEUGE.contains(n)
                || n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || n.endsWith("_BUCKET")) {
            return Category.WERKZEUGE;
        }
        if (n.equals("CAKE") || this.edible.test(material)) {
            return Category.NAHRUNG;
        }
        if (RESSOURCEN.contains(n)
                || n.startsWith("RAW_")
                || n.endsWith("_INGOT") || n.endsWith("_NUGGET") || n.endsWith("_ORE")
                || n.endsWith("_SCRAP") || n.endsWith("_SHARD") || n.endsWith("_DUST")
                || n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_PLANKS")
                || n.endsWith("_SMITHING_TEMPLATE")
                || isMineralBlock(n)) {
            return Category.RESSOURCEN;
        }
        return Category.SONSTIGES;
    }

    private static boolean isMineralBlock(String name) {
        if (!name.endsWith("_BLOCK")) {
            return false;
        }
        for (String prefix : BLOCK_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}
