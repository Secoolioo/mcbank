package de.secoolio.bankranking;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Was als Kopfgeld-Einsatz zaehlt und wie daraus wieder Gegenstaende werden.
 *
 * <p>Reine Rechnung auf {@link Material} und Stueckzahlen. Bewusst kein {@code ItemStack} in
 * der Ablage: die erlaubten Gegenstaende sind unveraenderte Vanilla-Gegenstaende, damit ist
 * Material und Anzahl eine verlustfreie Darstellung. Sie ist in der YAML-Datei von Hand lesbar
 * und reparierbar - anders als eine kodierte Zeichenkette, in der niemand einen Fehler findet.
 */
final class BountyItems {

    /**
     * Was eingesetzt werden darf.
     *
     * <p>Bloecke sind dabei, weil sie sich verlustfrei zurueckbauen lassen (neun zu eins) und
     * ein grosser Topf sonst nie in ein Inventar passte. Netherit-Bruchstueck und uralte
     * Truemmer fehlen: der Umtausch in Barren kostet zusaetzlich Gold, ist also nicht
     * verlustfrei, und ein Zwischenprodukt ist kein Zahlungsmittel.
     */
    static final Set<Material> ALLOWED = Set.of(
            Material.EMERALD, Material.EMERALD_BLOCK,
            Material.DIAMOND, Material.DIAMOND_BLOCK,
            Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK);

    private BountyItems() {
    }

    /**
     * Darf dieser Stapel in den Topf?
     *
     * <p>Verlangt wird ein unveraenderter Gegenstand: keine Verzauberung, kein eigener Name,
     * keine Datenkomponenten. Ein Topf verschmilzt die Einsaetze mehrerer Spieler, das geht nur
     * mit vertretbaren Gegenstaenden. Und nur so bleibt Material samt Anzahl verlustfrei.
     */
    static boolean isAllowed(ItemStack stack) {
        if (stack == null || stack.getAmount() <= 0 || !ALLOWED.contains(stack.getType())) {
            return false;
        }
        return stack.isSimilar(ItemStack.of(stack.getType()));
    }

    /** Zaehlt die erlaubten Stapel zusammen. Nicht erlaubte werden uebergangen. */
    static Map<Material, Integer> count(List<ItemStack> stacks) {
        Map<Material, Integer> summe = new EnumMap<>(Material.class);
        for (ItemStack stack : stacks) {
            if (isAllowed(stack)) {
                summe.merge(stack.getType(), stack.getAmount(), Integer::sum);
            }
        }
        return summe;
    }

    /** Wie viele Stapel nicht angenommen wuerden. */
    static int rejected(List<ItemStack> stacks) {
        int anzahl = 0;
        for (ItemStack stack : stacks) {
            if (stack != null && stack.getAmount() > 0 && !isAllowed(stack)) {
                anzahl++;
            }
        }
        return anzahl;
    }

    /** Zwei Zaehlungen zusammenfuehren. */
    static Map<Material, Integer> merge(Map<Material, Integer> a, Map<Material, Integer> b) {
        Map<Material, Integer> summe = new EnumMap<>(Material.class);
        summe.putAll(a);
        b.forEach((material, anzahl) -> summe.merge(material, anzahl, Integer::sum));
        return summe;
    }

    /** Die Gesamtzahl der Gegenstaende. */
    static int size(Map<Material, Integer> items) {
        int anzahl = 0;
        for (int stueck : items.values()) {
            anzahl += stueck;
        }
        return anzahl;
    }

    /**
     * Macht aus der Zaehlung wieder Stapel, aufgeteilt nach der Hoechststapelgroesse.
     *
     * <p>Ohne die Aufteilung wuerde {@code addItem} einen zu grossen Stapel zwar annehmen, ihn
     * aber auf mehrere Plaetze verteilen - und die Restmenge waere nicht mehr verlaesslich
     * zu bestimmen.
     */
    static List<ItemStack> toStacks(Map<Material, Integer> items) {
        List<ItemStack> stapel = new java.util.ArrayList<>();
        // Ueber eine EnumMap, damit die Reihenfolge fest ist. Der Kopierkonstruktor von
        // EnumMap wirft bei einer leeren Fremdmap, deshalb wird umgefuellt statt kopiert.
        Map<Material, Integer> sortiert = new EnumMap<>(Material.class);
        sortiert.putAll(items);
        for (Map.Entry<Material, Integer> e : sortiert.entrySet()) {
            int rest = e.getValue();
            int hoechst = Math.max(1, e.getKey().getMaxStackSize());
            while (rest > 0) {
                int jetzt = Math.min(rest, hoechst);
                stapel.add(ItemStack.of(e.getKey(), jetzt));
                rest -= jetzt;
            }
        }
        return stapel;
    }

    /** Die Materialien in fester Reihenfolge, damit Anzeigen nicht springen. */
    static Set<Material> ordered() {
        Set<Material> reihe = new LinkedHashSet<>();
        reihe.add(Material.NETHERITE_BLOCK);
        reihe.add(Material.NETHERITE_INGOT);
        reihe.add(Material.EMERALD_BLOCK);
        reihe.add(Material.EMERALD);
        reihe.add(Material.DIAMOND_BLOCK);
        reihe.add(Material.DIAMOND);
        return reihe;
    }

    /** Der Schluessel, unter dem ein Material in der YAML-Datei steht. */
    static String key(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }
}
