package de.secoolio.bankranking;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Material;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prueft, wie die Belohnung in Worte gefasst wird.
 *
 * <p>Der Grund fuer diese Klasse ist eine Rueckmeldung aus dem Spiel: die Belohnung stand
 * frueher als nackte Punktzahl da, und der Spieler fragte "was soll der Wert sein?". Jetzt
 * steht dort, was er tatsaechlich bekommt.
 */
class BountyItemsTest {

    private static Map<Material, Integer> items(Object... paare) {
        Map<Material, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < paare.length; i += 2) {
            map.put((Material) paare[i], (Integer) paare[i + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("Eine einzelne Sorte steht einfach da")
    void singleKind() {
        assertEquals("32 Diamanten", BountyItems.describe(items(Material.DIAMOND, 32)));
        assertEquals("1 Diamant", BountyItems.describe(items(Material.DIAMOND, 1)));
        assertEquals("1 Smaragd", BountyItems.describe(items(Material.EMERALD, 1)));
        assertEquals("4 Netherit-Barren", BountyItems.describe(items(Material.NETHERITE_INGOT, 4)));
    }

    @Test
    @DisplayName("Das Wertvollste steht vorn")
    void mostValuableFirst() {
        String text = BountyItems.describe(items(
                Material.DIAMOND, 12, Material.NETHERITE_BLOCK, 2));
        assertEquals("2 Netheritbloecke und 12 Diamanten", text);
    }

    @Test
    @DisplayName("Mehr als zwei Sorten werden zusammengefasst, damit die Zeile nicht ausufert")
    void manyKindsSummarised() {
        String text = BountyItems.describe(items(
                Material.NETHERITE_BLOCK, 1, Material.EMERALD_BLOCK, 2,
                Material.DIAMOND, 30, Material.EMERALD, 7));
        assertTrue(text.startsWith("1 Netheritblock, 2 Smaragdbloecke"), text);
        assertTrue(text.endsWith("und 37 weitere"), text);
    }

    @Test
    @DisplayName("Ein leerer Topf sagt das auch")
    void empty() {
        assertEquals("nichts", BountyItems.describe(Map.of()));
    }

    @Test
    @DisplayName("Das Sinnbild ist immer das wertvollste Material")
    void headline() {
        assertEquals(Material.NETHERITE_BLOCK, BountyItems.headline(items(
                Material.DIAMOND, 64, Material.NETHERITE_BLOCK, 1)));
        assertEquals(Material.EMERALD, BountyItems.headline(items(Material.EMERALD, 3)));
        assertNull(BountyItems.headline(Map.of()));
    }

    @Test
    @DisplayName("Grosse Mengen werden auf Stapelgroessen aufgeteilt")
    void stacksSplit() {
        // Ohne die Aufteilung koennte addItem die Restmenge nicht verlaesslich melden.
        assertArrayEquals(new int[]{64, 64, 64, 8}, BountyItems.split(200, 64));
        assertArrayEquals(new int[]{64}, BountyItems.split(64, 64));
        assertArrayEquals(new int[]{1}, BountyItems.split(1, 64));
        assertArrayEquals(new int[]{}, BountyItems.split(0, 64));
        // Ein unsinniger Hoechstwert darf keine Endlosschleife ergeben.
        assertArrayEquals(new int[]{1, 1, 1}, BountyItems.split(3, 0));
    }

    @Test
    @DisplayName("Java und Resourcepack kennen dieselbe Reihenfolge der Materialien")
    void rankMatchesPack() throws java.io.IOException {
        // Weichen die beiden ab, zeigt das Plakat das falsche Sinnbild - und zwar leise.
        PackFont font = PackFontTest.bundled();
        var ausPack = font.itemNames();
        var ausJava = BountyItems.rank().stream()
                .map(m -> m.name().toLowerCase(java.util.Locale.ROOT)).toList();
        assertEquals(ausJava, ausPack);
    }

    @Test
    @DisplayName("Die Belohnung wird zu Posten mit Sinnbild, wertvollstes zuerst")
    void lootOrder() {
        var posten = BountyItems.loot(items(
                Material.DIAMOND, 64, Material.NETHERITE_BLOCK, 2, Material.EMERALD, 9));
        assertEquals(3, posten.size());
        assertEquals("netherite_block", posten.get(0).material());
        assertEquals(2, posten.get(0).count());
        assertEquals("emerald", posten.get(1).material());
        assertEquals("diamond", posten.get(2).material());
    }

    @Test
    @DisplayName("Zwei Zaehlungen lassen sich zusammenfuehren")
    void merge() {
        Map<Material, Integer> summe = BountyItems.merge(
                items(Material.DIAMOND, 10, Material.EMERALD, 2),
                items(Material.DIAMOND, 5));
        assertEquals(Map.of(Material.DIAMOND, 15, Material.EMERALD, 2), summe);
        assertEquals(17, BountyItems.size(summe));
    }
}
