package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.Material;
import org.bukkit.inventory.ItemRarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SettingsTest {

    @Test
    @DisplayName("Die ausgelieferte config.yml enthaelt genau die Standardwerte und erzeugt keine Warnung")
    void bundledConfigMatchesDefaults() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings settings = Settings.load(TestSupport.bundledConfig(), log);

        assertEquals(1.0, settings.rarityBase(ItemRarity.COMMON), 1e-9);
        assertEquals(3.0, settings.rarityBase(ItemRarity.UNCOMMON), 1e-9);
        assertEquals(7.0, settings.rarityBase(ItemRarity.RARE), 1e-9);
        assertEquals(15.0, settings.rarityBase(ItemRarity.EPIC), 1e-9);
        assertEquals(2.0, settings.multiplier(Category.WAFFEN), 1e-9);
        assertEquals(1.5, settings.multiplier(Category.WERKZEUGE), 1e-9);
        assertEquals(1.5, settings.multiplier(Category.RUESTUNG), 1e-9);
        assertEquals(1.0, settings.multiplier(Category.RESSOURCEN), 1e-9);
        assertEquals(0.5, settings.multiplier(Category.NAHRUNG), 1e-9);
        assertEquals(1.0, settings.multiplier(Category.SONSTIGES), 1e-9);
        assertEquals(2.0, settings.enchantBonusPerLevel(), 1e-9);
        assertTrue(settings.materialBase().isEmpty());
        assertTrue(settings.categoryOverrides().isEmpty());
        assertTrue(settings.sidebarEnabled());
        assertEquals(Settings.DEFAULT_NPC_NAME, settings.npcName());
        assertEquals(Settings.DEFAULT_SIDEBAR_TITLE, settings.sidebarTitle());
        assertTrue(log.warnings().isEmpty(), () -> "unerwartete Warnungen: " + log.warnings());
    }

    @Test
    @DisplayName("Eine leere Config liefert die Standardwerte mit Warnungen")
    void emptyConfigFallsBack() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings settings = Settings.load(TestSupport.config(""), log);
        assertEquals(1.0, settings.rarityBase(ItemRarity.COMMON), 1e-9);
        assertEquals(2.0, settings.enchantBonusPerLevel(), 1e-9);
        assertEquals(Settings.DEFAULT_NPC_NAME, settings.npcName());
        assertEquals(11, log.warnings().size(), () -> "Warnungen: " + log.warnings());
    }

    @Test
    @DisplayName("Ein fehlender Einzelwert faellt mit Warnung auf den Standard zurueck")
    void missingValueWarns() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  seltenheit:
                    common: 1.0
                    uncommon: 3.0
                    epic: 15.0
                  kategorien:
                    waffen: 2.0
                    werkzeuge: 1.5
                    ruestung: 1.5
                    ressourcen: 1.0
                    nahrung: 0.5
                    sonstiges: 1.0
                  verzauberung-bonus-pro-stufe: 2.0
                """), log);
        assertEquals(7.0, settings.rarityBase(ItemRarity.RARE), 1e-9);
        assertEquals(1, log.warningsContaining("punkte.seltenheit.rare"));
    }

    @Test
    @DisplayName("Negative, unendliche und nicht-numerische Werte werden abgelehnt")
    void invalidValuesWarn() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  kategorien:
                    nahrung: -1.0
                    waffen: "zwei"
                  verzauberung-bonus-pro-stufe: 2.0
                """), log);
        assertEquals(0.5, settings.multiplier(Category.NAHRUNG), 1e-9);
        assertEquals(2.0, settings.multiplier(Category.WAFFEN), 1e-9);
        assertEquals(1, log.warningsContaining("punkte.kategorien.nahrung"));
        assertEquals(1, log.warningsContaining("punkte.kategorien.waffen"));
    }

    @Test
    @DisplayName("Unbekannte Materialien und Kategorien werden uebersprungen")
    void unknownEntriesSkipped() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  material-basiswerte:
                    diamond: 20.0
                    gibtsnicht: 5.0
                    legacy_stone: 3.0
                    emerald: -2.0
                  kategorie-ueberschreibungen:
                    arrow: waffen
                    cake: dessert
                """), log);
        assertEquals(1, settings.materialBase().size());
        assertEquals(20.0, settings.materialBase().get(Material.DIAMOND), 1e-9);
        assertEquals(1, settings.categoryOverrides().size());
        assertEquals(Category.WAFFEN, settings.categoryOverrides().get(Material.ARROW));
        assertEquals(1, log.warningsContaining("gibtsnicht"));
        assertEquals(1, log.warningsContaining("legacy_stone"));
        assertEquals(1, log.warningsContaining("emerald"));
        assertEquals(1, log.warningsContaining("dessert"));
    }

    @Test
    @DisplayName("Die Sidebar laesst sich abschalten")
    void sidebarCanBeDisabled() {
        Settings settings = Settings.load(TestSupport.config("""
                sidebar:
                  aktiv: false
                  titel: "<red>Top"
                """), new TestSupport.RecordingLogger());
        assertFalse(settings.sidebarEnabled());
        assertEquals("<red>Top", settings.sidebarTitle());
    }
}
