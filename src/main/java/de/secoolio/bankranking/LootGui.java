package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Die noch nicht abgeholte Beute eines Spielers.
 *
 * <p>Reines Anzeigefenster mit genau einem Knopf. Es gibt bewusst keinen Weg, einzelne
 * Gegenstaende herauszunehmen: jeder Einzelklick waere ein eigener Schreibvorgang und damit ein
 * eigener Zwischenzustand, in dem etwas schieflaufen koennte. Der Knopf ruft denselben Weg auf,
 * den auch die Auszahlung benutzt - ein Code-Pfad, ein Fehlerbild.
 */
public final class LootGui implements BankWindow {

    public static final int SIZE = 27;
    private static final int TAKE_SLOT = 22;
    private static final int CLOSE_SLOT = 26;

    private final BankRankingPlugin plugin;
    private final Inventory inventory;

    public LootGui(BankRankingPlugin plugin, Player owner) {
        this.plugin = plugin;
        this.inventory = plugin.getServer().createInventory(this, SIZE,
                Messages.mm(Messages.BEUTE_TITEL));
        decorate(owner);
    }

    private void decorate(Player owner) {
        ItemStack rahmen = GuiItems.filler(Material.ORANGE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            this.inventory.setItem(slot, slot < 9 || slot >= 18 ? rahmen : GuiItems.filler());
        }

        BountyData.Claim beute = this.plugin.bounties().data().claim(owner.getUniqueId());
        if (beute == null || beute.items().isEmpty()) {
            this.inventory.setItem(13, GuiItems.labelled(Material.PAPER, Messages.BEUTE_LEER, List.of()));
        } else {
            int slot = 9;
            for (ItemStack stack : BountyItems.toStacks(beute.items())) {
                if (slot >= 18) {
                    break;
                }
                this.inventory.setItem(slot++, stack);
            }
            List<String> lore = new ArrayList<>();
            lore.add(Messages.BEUTE_LORE.replace("<anzahl>",
                    String.valueOf(BountyItems.size(beute.items()))));
            for (Map.Entry<Material, Integer> e : beute.items().entrySet()) {
                lore.add("<dark_gray>- <gray>" + e.getValue() + "x <lang:"
                        + e.getKey().translationKey() + ">");
            }
            this.inventory.setItem(4, GuiItems.labelled(Material.CHEST, Messages.BEUTE_NAME, lore));
            this.inventory.setItem(TAKE_SLOT, GuiItems.checkButton(Messages.BEUTE_KNOPF_NAME,
                    List.of(Messages.BEUTE_KNOPF_LORE), this.plugin.settings().confirmHead()));
        }
        this.inventory.setItem(CLOSE_SLOT, GuiItems.labelled(Material.BARRIER,
                Messages.BUTTON_SCHLIESSEN_NAME, List.of(Messages.BUTTON_SCHLIESSEN_LORE)));
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        switch (event.getRawSlot()) {
            case TAKE_SLOT -> {
                BankWindows.click(player);
                this.plugin.bounties().deliver(player);
                this.plugin.windows().closeLater(player);
            }
            case CLOSE_SLOT -> this.plugin.windows().closeLater(player);
            default -> {
                // Die Anzeige der Gegenstaende ist nur Anzeige.
            }
        }
    }
}
