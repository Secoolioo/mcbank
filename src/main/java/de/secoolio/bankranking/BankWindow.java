package de.secoolio.bankranking;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;

/**
 * Ein Fenster der Bank.
 *
 * <p>Der Ereignis-Empfaenger erkennt an dieser Schnittstelle, dass ein Inventar zur Bank gehoert,
 * und reicht Klicks an das Fenster selbst weiter. Aus einem Inventar-Ereignis heraus darf kein
 * Fenster geoeffnet oder geschlossen werden - dafuer gibt es {@link BankWindows}.
 */
public interface BankWindow extends InventoryHolder {

    /** Reagiert auf einen Klick. Anzeigefenster brechen ihn einfach ab. */
    void handleClick(InventoryClickEvent event, Player player);

    /** Zieh-Vorgaenge sind ueberall verboten, ausser im Abgabe-Fenster. */
    default void handleDrag(InventoryDragEvent event, Player player) {
        event.setCancelled(true);
    }

    /**
     * Wird beim Schliessen aufgerufen.
     *
     * @param dropAll ob eingelegte Gegenstaende fallen gelassen statt zurueckgegeben werden
     */
    default void onClosed(Player player, boolean dropAll) {
    }
}
