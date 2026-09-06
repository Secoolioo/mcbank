package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    @DisplayName("Die Wohlstands-Bremse sinkt mit dem Kontostand und nie unter den Mindestfaktor")
    void wealthFactorFalls() {
        assertEquals(1.0, this.scorer.wealthFactor(0), 1e-9);
        assertEquals(1.0, this.scorer.wealthFactor(-100), 1e-9);
        assertEquals(0.66, this.scorer.wealthFactor(5_000), 0.01);
        assertEquals(0.24, this.scorer.wealthFactor(50_000), 0.01);
        assertEquals(0.13, this.scorer.wealthFactor(150_000), 0.01);
        assertTrue(this.scorer.wealthFactor(100_000_000) >= Settings.DEFAULT_WEALTH_FLOOR);
        assertTrue(this.scorer.wealthFactor(10_000) < this.scorer.wealthFactor(5_000));
    }

    @Test
    @DisplayName("Die Marktsättigung sinkt mit der abgegebenen Menge")
    void saturationFalls() {
        assertEquals(1.0, this.scorer.saturationFactor(0), 1e-9);
        assertEquals(0.5, this.scorer.saturationFactor(5_000), 1e-9);
        assertEquals(0.2, this.scorer.saturationFactor(20_000), 1e-9);
        assertTrue(this.scorer.saturationFactor(10_000_000) >= Settings.DEFAULT_SATURATION_FLOOR);
    }

    @Test
    @DisplayName("Beide Bremsen lassen sich abschalten")
    void dampingCanBeDisabled() {
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  wohlstands-bremse:
                    aktiv: false
                  markt-saettigung:
                    aktiv: false
                """), new TestSupport.RecordingLogger());
        Scorer plain = new Scorer(settings,
                new CategoryClassifier(settings.categoryOverrides(), TestSupport.EDIBLE::contains));
        assertEquals(1.0, plain.wealthFactor(1_000_000), 1e-9);
        assertEquals(1.0, plain.saturationFactor(1_000_000), 1e-9);
    }

    @Test
    @DisplayName("Ränge richten sich nach dem Kontostand")
    void ranks() {
        assertEquals(Rank.BRONZE, Rank.of(0));
        assertEquals(Rank.BRONZE, Rank.of(999));
        assertEquals(Rank.SILBER, Rank.of(1_000));
        assertEquals(Rank.GOLD, Rank.of(5_000));
        assertEquals(Rank.PLATIN, Rank.of(20_000));
        assertEquals(Rank.DIAMANT, Rank.of(50_000));
        assertEquals(Rank.NETHERITE, Rank.of(1_000_000));
        assertEquals(1_000, Rank.BRONZE.nextAt());
        assertEquals(0, Rank.NETHERITE.nextAt());
    }

    private Scorer.ItemFacts facts(Material material, int amount) {
        return new Scorer.ItemFacts(material, amount, ItemRarity.COMMON, 0);
    }

    @Test
    @DisplayName("Die Reihenfolge der Stapel im Fenster ändert das Ergebnis nicht")
    void depositIsOrderIndependent() {
        List<Scorer.ItemFacts> stacks = List.of(
                facts(Material.IRON_INGOT, 64), facts(Material.IRON_BLOCK, 32),
                facts(Material.IRON_NUGGET, 64), facts(Material.DIAMOND, 12),
                facts(Material.IRON_INGOT, 7));
        double expected = this.scorer.deposit(stacks, Map.of(), 0.0).total();
        List<Scorer.ItemFacts> reversed = new ArrayList<>(stacks);
        Collections.reverse(reversed);
        assertEquals(expected, this.scorer.deposit(reversed, Map.of(), 0.0).total(), 1e-6);
        List<Scorer.ItemFacts> shuffled = List.of(stacks.get(3), stacks.get(0), stacks.get(4),
                stacks.get(2), stacks.get(1));
        assertEquals(expected, this.scorer.deposit(shuffled, Map.of(), 0.0).total(), 1e-6);
    }

    @Test
    @DisplayName("Viele kleine Einzahlungen bringen so viel wie eine große")
    void hoardingBringsNoAdvantage() {
        // 100 Portionen zu je 1000 Rohpunkten, jeweils mit dem gewachsenen Kontostand gerechnet
        double balance = 0.0;
        for (int i = 0; i < 100; i++) {
            balance += this.scorer.wealthCredit(balance, 1000.0);
        }
        double atOnce = this.scorer.wealthCredit(0.0, 100_000.0);
        assertEquals(atOnce, balance, atOnce * 1e-6);
    }

    @Test
    @DisplayName("Die Sättigungs-Gutschrift ist additiv")
    void saturationCreditIsAdditive() {
        for (double given : new double[] {0.0, 2000.0, 60_000.0}) {
            double whole = this.scorer.saturationCredit(given, 8000.0);
            double split = this.scorer.saturationCredit(given, 3000.0)
                    + this.scorer.saturationCredit(given + 3000.0, 5000.0);
            assertEquals(whole, split, 1e-6, "given=" + given);
        }
    }

    @Test
    @DisplayName("Umkraften in Blöcke oder Nuggets umgeht die Marktsättigung nicht")
    void saturationGroupsResourceForms() {
        assertEquals(Material.IRON_INGOT, MaterialValues.saturationKey(Material.IRON_BLOCK));
        assertEquals(Material.IRON_INGOT, MaterialValues.saturationKey(Material.IRON_NUGGET));
        assertEquals(Material.COPPER_INGOT, MaterialValues.saturationKey(Material.COPPER_NUGGET));
        assertEquals(Material.STONE, MaterialValues.saturationKey(Material.STONE_BRICK_SLAB));
        assertEquals(Material.DIAMOND, MaterialValues.saturationKey(Material.DIAMOND));

        Scorer.Deposit deposit = this.scorer.deposit(
                List.of(facts(Material.IRON_INGOT, 64), facts(Material.IRON_BLOCK, 8)), Map.of(), 0.0);
        assertEquals(Set.of(Material.IRON_INGOT), deposit.saturationDeltas().keySet());
    }

    @Test
    @DisplayName("Ohne Marktsättigung wird kein Zähler fortgeschrieben")
    void disabledSaturationRecordsNothing() {
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  markt-saettigung:
                    aktiv: false
                """), new TestSupport.RecordingLogger());
        Scorer plain = new Scorer(settings,
                new CategoryClassifier(settings.categoryOverrides(), TestSupport.EDIBLE::contains));
        Scorer.Deposit deposit = plain.deposit(List.of(facts(Material.IRON_INGOT, 64)), Map.of(), 0.0);
        assertTrue(deposit.saturationDeltas().isEmpty());
    }

    @Test
    @DisplayName("Verarbeitete Formen sind nie mehr wert als ihr Rohstoff")
    void derivedFormsDoNotPrintPoints() {
        double stone = this.scorer.value(facts(Material.STONE, 1)).rawPoints();
        assertEquals(stone, this.scorer.value(facts(Material.STONE_BRICKS, 1)).rawPoints(), 1e-9);
        assertEquals(stone / 2, this.scorer.value(facts(Material.STONE_SLAB, 1)).rawPoints(), 1e-9);
        assertEquals(this.scorer.value(facts(Material.SAND, 1)).rawPoints(),
                this.scorer.value(facts(Material.GLASS, 1)).rawPoints(), 1e-9);
        assertEquals(this.scorer.value(facts(Material.CLAY_BALL, 1)).rawPoints(),
                this.scorer.value(facts(Material.BRICK, 1)).rawPoints(), 1e-9);
        assertEquals(this.scorer.value(facts(Material.COPPER_INGOT, 9)).rawPoints(),
                this.scorer.value(facts(Material.COPPER_NUGGET, 9)).rawPoints() * 9, 1e-9);
    }

    @Test
    @DisplayName("Ein eigener Basiswert gilt auch für alle abgeleiteten Formen")
    void overridePropagatesToDerivedForms() {
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  material-basiswerte:
                    iron_ingot: 1.0
                """), new TestSupport.RecordingLogger());
        Scorer custom = new Scorer(settings,
                new CategoryClassifier(settings.categoryOverrides(), TestSupport.EDIBLE::contains));
        assertEquals(1.0, custom.value(facts(Material.IRON_INGOT, 1)).base(), 1e-9);
        assertEquals(9.0, custom.value(facts(Material.IRON_BLOCK, 1)).base(), 1e-9);
        assertEquals(1.0 / 9.0, custom.value(facts(Material.IRON_NUGGET, 1)).base(), 1e-9);
    }

    @Test
    @DisplayName("Die Bewertung verändert die übergebene Sättigungs-Sicht nicht")
    void depositLeavesTheViewAlone() {
        Map<Material, Double> given = Map.of(Material.IRON_INGOT, 1000.0);
        this.scorer.deposit(List.of(facts(Material.IRON_INGOT, 64)), given, 0.0);
        assertEquals(1000.0, given.get(Material.IRON_INGOT), 1e-9);
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
