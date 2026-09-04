package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CategoryClassifierTest {

    private final CategoryClassifier classifier = TestSupport.classifier();

    @ParameterizedTest
    @CsvSource({
            "DIAMOND_SWORD, WAFFEN",
            "WOODEN_SWORD, WAFFEN",
            "BOW, WAFFEN",
            "CROSSBOW, WAFFEN",
            "TRIDENT, WAFFEN",
            "MACE, WAFFEN",
            "ARROW, WAFFEN",
            "DIAMOND_PICKAXE, WERKZEUGE",
            "IRON_AXE, WERKZEUGE",
            "WOODEN_SHOVEL, WERKZEUGE",
            "GOLDEN_HOE, WERKZEUGE",
            "SHEARS, WERKZEUGE",
            "FISHING_ROD, WERKZEUGE",
            "WATER_BUCKET, WERKZEUGE",
            "BUCKET, WERKZEUGE",
            "TURTLE_HELMET, RUESTUNG",
            "NETHERITE_CHESTPLATE, RUESTUNG",
            "DIAMOND_BOOTS, RUESTUNG",
            "SHIELD, RUESTUNG",
            "ELYTRA, RUESTUNG",
            "DIAMOND_HORSE_ARMOR, RUESTUNG",
            "BREAD, NAHRUNG",
            "GOLDEN_APPLE, NAHRUNG",
            "CAKE, NAHRUNG",
            "IRON_INGOT, RESSOURCEN",
            "RAW_GOLD, RESSOURCEN",
            "DIAMOND, RESSOURCEN",
            "DEEPSLATE_DIAMOND_ORE, RESSOURCEN",
            "OAK_LOG, RESSOURCEN",
            "OAK_PLANKS, RESSOURCEN",
            "COBBLESTONE, RESSOURCEN",
            "NETHERITE_SCRAP, RESSOURCEN",
            "DIAMOND_BLOCK, RESSOURCEN",
            "AMETHYST_SHARD, RESSOURCEN",
            "TORCH, SONSTIGES",
            "CHEST, SONSTIGES",
            "ENCHANTED_BOOK, SONSTIGES",
            "TOTEM_OF_UNDYING, SONSTIGES"
    })
    @DisplayName("Materialien landen in der erwarteten Kategorie")
    void classifiesMaterials(String material, Category expected) {
        assertEquals(expected, this.classifier.classify(Material.valueOf(material)));
    }

    @Test
    @DisplayName("Spitzhacke und Axt sind Werkzeug, nicht Waffe")
    void pickaxeIsNotWeapon() {
        assertEquals(Category.WERKZEUGE, this.classifier.classify(Material.DIAMOND_PICKAXE));
        assertEquals(Category.WERKZEUGE, this.classifier.classify(Material.WOODEN_AXE));
    }

    @Test
    @DisplayName("Ueberschreibungen aus der Config gewinnen")
    void overridesWin() {
        CategoryClassifier withOverrides = new CategoryClassifier(
                Map.of(Material.DIAMOND_AXE, Category.WAFFEN, Material.DIAMOND_SWORD, Category.SONSTIGES),
                TestSupport.EDIBLE::contains);
        assertEquals(Category.WAFFEN, withOverrides.classify(Material.DIAMOND_AXE));
        assertEquals(Category.SONSTIGES, withOverrides.classify(Material.DIAMOND_SWORD));
    }

    @Test
    @DisplayName("Kategorie-Namen werden tolerant gelesen")
    void parsesCategoryNames() {
        assertEquals(Category.RUESTUNG, Category.parse("Rüstung").orElseThrow());
        assertEquals(Category.RUESTUNG, Category.parse("RUESTUNG").orElseThrow());
        assertEquals(Category.RUESTUNG, Category.parse("ruestung").orElseThrow());
        assertEquals(Category.NAHRUNG, Category.parse(" nahrung ").orElseThrow());
        assertTrue(Category.parse("gibtsnicht").isEmpty());
        assertTrue(Category.parse(null).isEmpty());
    }
}
