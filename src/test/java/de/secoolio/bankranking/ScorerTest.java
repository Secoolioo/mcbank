package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ScorerTest {

    private final Scorer scorer = TestSupport.scorer();

    private double points(Material material, int amount, ItemRarity rarity, int levels) {
        return this.scorer.value(new Scorer.ItemFacts(material, amount, rarity, levels)).points();
    }

    @Test
    @DisplayName("Wertvolles zählt deutlich mehr als Massenware")
    void valuablesBeatDirt() {
        double dirt = points(Material.DIRT, 1, ItemRarity.COMMON, 0);
        double cobble = points(Material.COBBLESTONE, 1, ItemRarity.COMMON, 0);
        double iron = points(Material.IRON_INGOT, 1, ItemRarity.COMMON, 0);
        double diamond = points(Material.DIAMOND, 1, ItemRarity.COMMON, 0);
        double emerald = points(Material.EMERALD, 1, ItemRarity.COMMON, 0);
        double netherite = points(Material.NETHERITE_INGOT, 1, ItemRarity.COMMON, 0);

        assertTrue(dirt < cobble * 2, "Erde und Bruchstein liegen beide im Ramsch-Bereich");
        assertTrue(iron > dirt * 50, "Eisen muss deutlich über Erde liegen");
        assertTrue(diamond > iron * 2, "Diamant muss über Eisen liegen");
        assertTrue(emerald > diamond, "Smaragd ist der teuerste normale Edelstein");
        assertTrue(netherite > emerald * 5, "Netherite ist die Spitze");
        assertEquals(0.05, dirt, 1e-9);
        assertEquals(20.0, diamond, 1e-9);
        assertEquals(25.0, emerald, 1e-9);
    }

    @Test
    @DisplayName("Ein Block zählt wie neun Rohstoffe")
    void blocksAreNineTimesTheIngot() {
        assertEquals(9 * points(Material.DIAMOND, 1, ItemRarity.COMMON, 0),
                points(Material.DIAMOND_BLOCK, 1, ItemRarity.COMMON, 0), 1e-9);
        assertEquals(9 * points(Material.IRON_INGOT, 1, ItemRarity.COMMON, 0),
                points(Material.IRON_BLOCK, 1, ItemRarity.COMMON, 0), 1e-9);
    }

    @Test
    @DisplayName("Werkzeuge und Rüstung richten sich nach ihrer Stufe")
    void gearFollowsTier() {
        double wooden = points(Material.WOODEN_SWORD, 1, ItemRarity.COMMON, 0);
        double iron = points(Material.IRON_SWORD, 1, ItemRarity.COMMON, 0);
        double diamond = points(Material.DIAMOND_SWORD, 1, ItemRarity.COMMON, 0);
        double netherite = points(Material.NETHERITE_SWORD, 1, ItemRarity.COMMON, 0);
        assertTrue(wooden < iron && iron < diamond && diamond < netherite);
        // Waffen zählen doppelt: Diamantschwert 70 * 2
        assertEquals(140.0, diamond, 1e-9);
        // Rüstung zählt anderthalbfach: Diamanthelm 70 * 1.5
        assertEquals(105.0, points(Material.DIAMOND_HELMET, 1, ItemRarity.COMMON, 0), 1e-9);
    }

    @Test
    @DisplayName("Anzahl, Kategorie, Seltenheit und Verzauberung greifen zusammen")
    void fullFormula() {
        // 64 Bruchstein: 0.05 * 64 * 1.0 (Ressourcen) * 1.0 (common)
        assertEquals(3.2, points(Material.COBBLESTONE, 64, ItemRarity.COMMON, 0), 1e-9);
        // Diamantschwert mit 8 Verzauberungsstufen: 70 * 1 * 2.0 + 8 * 2.0
        assertEquals(156.0, points(Material.DIAMOND_SWORD, 1, ItemRarity.COMMON, 8), 1e-9);
        // Elytra ist episch: 200 * 1 * 1.5 (Rüstung) * 3.0 (epic)
        assertEquals(900.0, points(Material.ELYTRA, 1, ItemRarity.EPIC, 0), 1e-9);
        // Nahrung wird halbiert: gebratenes Rindfleisch 0.6 * 64 * 0.5
        assertEquals(19.2, points(Material.COOKED_BEEF, 64, ItemRarity.COMMON, 0), 1e-9);
    }

    @Test
    @DisplayName("Der Verzauberungsbonus gilt einmal pro Stapel, nicht pro Item")
    void bonusPerStack() {
        double withoutBonus = points(Material.ARROW, 64, ItemRarity.COMMON, 0);
        assertEquals(withoutBonus + 2.0, points(Material.ARROW, 64, ItemRarity.COMMON, 1), 1e-9);
    }

    @Test
    @DisplayName("Unbekannte Materialien bekommen den Standardwert")
    void unknownMaterialUsesFallback() {
        Scorer.Valuation valuation = this.scorer.value(
                new Scorer.ItemFacts(Material.PINK_BED, 1, ItemRarity.COMMON, 0));
        assertEquals(Settings.DEFAULT_FALLBACK_VALUE, valuation.base(), 1e-9);
    }

    @Test
    @DisplayName("Ein Material-Basiswert aus der Config ersetzt den eingebauten Wert")
    void materialOverride() {
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  material-basiswerte:
                    diamond: 100.0
                """), new TestSupport.RecordingLogger());
        Scorer custom = new Scorer(settings,
                new CategoryClassifier(settings.categoryOverrides(), TestSupport.EDIBLE::contains));
        Scorer.Valuation valuation = custom.value(new Scorer.ItemFacts(Material.DIAMOND, 2, ItemRarity.COMMON, 0));
        assertEquals(100.0, valuation.base(), 1e-9);
        assertEquals(200.0, valuation.points(), 1e-9);
    }

    @Test
    @DisplayName("Die Summe einer Einzahlung wird auf eine Nachkommastelle gerundet")
    void totalRounds() {
        List<Scorer.Valuation> valuations = List.of(
                this.scorer.value(new Scorer.ItemFacts(Material.DIRT, 1, ItemRarity.COMMON, 0)),
                this.scorer.value(new Scorer.ItemFacts(Material.DIRT, 1, ItemRarity.COMMON, 0)));
        assertEquals(0.1, this.scorer.total(valuations), 1e-9);
        assertEquals(0.3, Scorer.round1(0.25), 1e-9);
        assertEquals(0.3, Scorer.round1(0.1 + 0.2), 1e-9);
        assertEquals(1234.6, Scorer.round1(1234.56), 1e-9);
        assertEquals(0.0, Scorer.round1(Double.NaN), 1e-9);
    }

    @Test
    @DisplayName("Punkte werden deutsch mit einer Nachkommastelle angezeigt")
    void formatsGerman() {
        assertEquals("1234,5", Scorer.format(1234.5));
        assertEquals("2,0", Scorer.format(2));
        assertEquals("0,0", Scorer.format(0));
        assertEquals("1000000,0", Scorer.format(1000000));
    }
}
