package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
    @DisplayName("64 Bruchstein zaehlen 64 Punkte (Ressourcen x1)")
    void cobblestone() {
        assertEquals(64.0, points(Material.COBBLESTONE, 64, ItemRarity.COMMON, 0), 1e-9);
    }

    @Test
    @DisplayName("Diamantschwert: ohne Verzauberung 2, mit 8 Stufen 18 Punkte")
    void diamondSword() {
        assertEquals(2.0, points(Material.DIAMOND_SWORD, 1, ItemRarity.COMMON, 0), 1e-9);
        assertEquals(18.0, points(Material.DIAMOND_SWORD, 1, ItemRarity.COMMON, 8), 1e-9);
        Scorer.Valuation valuation = this.scorer.value(
                new Scorer.ItemFacts(Material.DIAMOND_SWORD, 1, ItemRarity.COMMON, 8));
        assertEquals(16.0, valuation.enchantBonus(), 1e-9);
        assertEquals(2.0, valuation.multiplier(), 1e-9);
        assertEquals(Category.WAFFEN, valuation.category());
    }

    @Test
    @DisplayName("Verzaubertes Buch (rare) mit einer Stufe: 9 Punkte")
    void enchantedBook() {
        assertEquals(9.0, points(Material.ENCHANTED_BOOK, 1, ItemRarity.RARE, 1), 1e-9);
    }

    @Test
    @DisplayName("Nahrung wird halbiert, Elytra ist episch mal Ruestungsfaktor")
    void foodAndElytra() {
        assertEquals(1.5, points(Material.BREAD, 3, ItemRarity.COMMON, 0), 1e-9);
        assertEquals(32.0, points(Material.COOKED_BEEF, 64, ItemRarity.COMMON, 0), 1e-9);
        assertEquals(22.5, points(Material.ELYTRA, 1, ItemRarity.EPIC, 0), 1e-9);
    }

    @Test
    @DisplayName("Der Verzauberungsbonus gilt einmal pro Stapel, nicht pro Item")
    void bonusPerStack() {
        assertEquals(128.0 + 2.0, points(Material.ARROW, 64, ItemRarity.COMMON, 1), 1e-9);
    }

    @Test
    @DisplayName("Ein Material-Basiswert aus der Config ersetzt den Seltenheitswert")
    void materialOverride() {
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  material-basiswerte:
                    diamond: 20.0
                """), new TestSupport.RecordingLogger());
        Scorer custom = new Scorer(settings,
                new CategoryClassifier(settings.categoryOverrides(), TestSupport.EDIBLE::contains));
        Scorer.Valuation valuation = custom.value(new Scorer.ItemFacts(Material.DIAMOND, 2, ItemRarity.COMMON, 0));
        assertEquals(20.0, valuation.base(), 1e-9);
        assertEquals(40.0, valuation.points(), 1e-9);
    }

    @Test
    @DisplayName("Die Summe einer Einzahlung wird auf eine Nachkommastelle gerundet")
    void totalRounds() {
        List<Scorer.Valuation> valuations = List.of(
                this.scorer.value(new Scorer.ItemFacts(Material.BREAD, 1, ItemRarity.COMMON, 0)),
                this.scorer.value(new Scorer.ItemFacts(Material.COOKED_BEEF, 1, ItemRarity.COMMON, 0)));
        assertEquals(1.0, this.scorer.total(valuations), 1e-9);
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
