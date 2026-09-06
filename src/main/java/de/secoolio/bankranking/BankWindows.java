package de.secoolio.bankranking;

import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Oeffnet und schliesst die Bank-Fenster.
 *
 * <p>Alle Wege fuehren hier durch, weil Bukkit es verbietet, aus einem Inventar-Ereignis heraus
 * ein Fenster zu wechseln: solche Wechsel laufen deshalb immer einen Tick spaeter.
 */
public final class BankWindows {

    private final BankRankingPlugin plugin;

    public BankWindows(BankRankingPlugin plugin) {
        this.plugin = plugin;
    }

    /** Oeffnet sofort - nur ausserhalb von Inventar-Ereignissen erlaubt (etwa beim Rechtsklick). */
    public void open(Player player, BankWindow window) {
        player.openInventory(window.getInventory());
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, SoundCategory.MASTER, 0.6f, 1.4f);
        this.plugin.progressBar().show(player);
    }

    /** Oeffnet im naechsten Tick - der Weg aus einem Klick heraus. */
    public void openLater(Player player, BankWindow window) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
                player.openInventory(window.getInventory());
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN,
                        SoundCategory.MASTER, 0.7f, 1.0f);
                this.plugin.progressBar().show(player);
            }
        });
    }

    /** Schliesst im naechsten Tick. */
    public void closeLater(Player player) {
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
                player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE,
                        SoundCategory.MASTER, 0.6f, 1.2f);
                player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            }
        });
    }

    /** Schaut der Spieler gerade in ein Fenster der Bank? */
    public static boolean isOurs(Player player) {
        return player.getOpenInventory().getTopInventory().getHolder(false) instanceof BankWindow;
    }

    /** Ein kurzer Ton fuer einen gedrueckten Knopf. */
    public static void click(Player player) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.4f, 1.0f);
    }
}
