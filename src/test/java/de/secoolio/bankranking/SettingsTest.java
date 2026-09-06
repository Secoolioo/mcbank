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
        assertEquals(1.5, settings.rarityBase(ItemRarity.UNCOMMON), 1e-9);
        assertEquals(2.0, settings.rarityBase(ItemRarity.RARE), 1e-9);
        assertEquals(3.0, settings.rarityBase(ItemRarity.EPIC), 1e-9);
        assertEquals(Settings.DEFAULT_FALLBACK_VALUE, settings.fallbackValue(), 1e-9);
        assertEquals(0.05, settings.fallbackValue(), 1e-9);
        assertTrue(settings.confirmHead());
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
        assertTrue(settings.wealthEnabled());
        assertTrue(settings.saturationEnabled());
        assertEquals(Settings.DEFAULT_WEALTH_THRESHOLD, settings.wealthThreshold(), 1e-9);
        assertEquals(Settings.DEFAULT_WEALTH_STRENGTH, settings.wealthStrength(), 1e-9);
        assertEquals(Settings.DEFAULT_SATURATION_THRESHOLD, settings.saturationThreshold(), 1e-9);
        assertEquals(Settings.DEFAULT_SATURATION_HALF_LIFE, settings.saturationHalfLife(), 1e-9);
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
        assertEquals(Settings.DEFAULT_FALLBACK_VALUE, settings.fallbackValue(), 1e-9);
        assertEquals(Settings.DEFAULT_NPC_NAME, settings.npcName());
        // vier Seltenheits-Faktoren, sechs Kategorien, Verzauberungs-Bonus, Standardwert,
        // je drei Zahlenwerte der beiden Bremsen, die Länge des Fortschrittsbalkens sowie
        // Mindesteinsatz, beide Kopfgeld-Sperren und der Plakat-Mindestabstand
        assertEquals(23, log.warnings().size(), () -> "Warnungen: " + log.warnings());
    }

    @Test
    @DisplayName("Ein fehlender Einzelwert faellt mit Warnung auf den Standard zurueck")
    void missingValueWarns() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  seltenheit-faktoren:
                    common: 1.0
                    uncommon: 1.5
                    epic: 3.0
                  kategorien:
                    waffen: 2.0
                    werkzeuge: 1.5
                    ruestung: 1.5
                    ressourcen: 1.0
                    nahrung: 0.5
                    sonstiges: 1.0
                  verzauberung-bonus-pro-stufe: 2.0
                  standardwert: 0.5
                """), log);
        assertEquals(2.0, settings.rarityBase(ItemRarity.RARE), 1e-9);
        assertEquals(1, log.warningsContaining("punkte.seltenheit-faktoren.rare"));
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
    @DisplayName("Ein Mindestfaktor über 1 würde die Bremse umdrehen und wird abgelehnt")
    void factorAboveOneIsRejected() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  wohlstands-bremse:
                    mindestfaktor: 5.0
                  markt-saettigung:
                    mindestfaktor: 2.0
                """), log);
        assertEquals(Settings.DEFAULT_WEALTH_FLOOR, settings.wealthFloor(), 1e-9);
        assertEquals(Settings.DEFAULT_SATURATION_FLOOR, settings.saturationFloor(), 1e-9);
        assertEquals(2, log.warningsContaining("zwischen 0 und 1"));
    }

    @Test
    @DisplayName("Schwelle und Halbwertszeit dürfen nicht null sein")
    void zeroThresholdsAreRejected() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  wohlstands-bremse:
                    schwelle: 0
                  markt-saettigung:
                    schwelle: 0
                    erholung-stunden: 0
                """), log);
        assertEquals(Settings.DEFAULT_WEALTH_THRESHOLD, settings.wealthThreshold(), 1e-9);
        assertEquals(Settings.DEFAULT_SATURATION_THRESHOLD, settings.saturationThreshold(), 1e-9);
        assertEquals(Settings.DEFAULT_SATURATION_HALF_LIFE, settings.saturationHalfLife(), 1e-9);
        assertEquals(3, log.warningsContaining("größer als 0"));
    }

    @Test
    @DisplayName("Ein Schalter in Anführungszeichen wird gemeldet statt still übergangen")
    void quotedSwitchIsReported() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings settings = Settings.load(TestSupport.config("""
                sidebar:
                  aktiv: "false"
                """), log);
        assertTrue(settings.sidebarEnabled(), "der Schalter bleibt auf dem Standard");
        assertEquals(1, log.warningsContaining("kein Wahrheitswert"));
    }

    @Test
    @DisplayName("Ein unbekannter Tag im Titel wird als Text übernommen und bricht nichts ab")
    void unknownTagIsHarmless() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        String titel = "<gradient:nichts>Rangliste</gradient>";
        Settings settings = Settings.load(TestSupport.config("""
                sidebar:
                  titel: "<gradient:nichts>Rangliste</gradient>"
                """), log);
        // MiniMessage wirft bei unbekannten Tags nicht, sondern zeigt sie als Text.
        // Die Prüfung beim Laden ist deshalb nur ein Sicherheitsnetz und meldet hier nichts.
        assertEquals(titel, settings.sidebarTitle());
        assertEquals(0, log.warningsContaining("MiniMessage"));
    }

    @Test
    @DisplayName("Ein absurd großer Basiswert wird nicht übernommen")
    void hugeBaseValueIsRejected() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings settings = Settings.load(TestSupport.config("""
                punkte:
                  material-basiswerte:
                    dirt: 1.0E308
                """), log);
        assertTrue(settings.materialBase().isEmpty());
        assertEquals(1, log.warningsContaining("material-basiswerte"));
    }

    @Test
    @DisplayName("Der alte Schlüssel punkte.seltenheit wird gemeldet")
    void oldRarityKeyWarns() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings.load(TestSupport.config("""
                punkte:
                  seltenheit:
                    common: 1.0
                """), log);
        assertEquals(1, log.warningsContaining("wird seit Version 1.1 nicht mehr benutzt"));
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

    @Test
    @DisplayName("Ein Port ausserhalb des freien Bereichs wird gemeldet")
    void packPortChecked() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        Settings s = Settings.load(TestSupport.config("resourcepack:\n  port: 80\n"), log);
        assertEquals(Settings.DEFAULT_PACK_PORT, s.packPort());
        assertTrue(log.warnings().stream().anyMatch(w -> w.contains("resourcepack.port")),
                log.warnings().toString());

        Settings gross = Settings.load(TestSupport.config("resourcepack:\n  port: 99999\n"),
                new TestSupport.RecordingLogger());
        assertEquals(Settings.DEFAULT_PACK_PORT, gross.packPort());
    }

    @Test
    @DisplayName("Ein gueltiger Port wird uebernommen, eine fehlende Angabe faellt still zurueck")
    void packPortAccepted() {
        TestSupport.RecordingLogger log = new TestSupport.RecordingLogger();
        assertEquals(9000, Settings.load(TestSupport.config("resourcepack:\n  port: 9000\n"),
                log).packPort());
        assertEquals(Settings.DEFAULT_PACK_PORT,
                Settings.load(TestSupport.config(""), log).packPort());
    }
}
