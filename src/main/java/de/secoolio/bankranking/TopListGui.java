package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Die Rangliste als Fenster: Treppchen fuer die ersten drei, darunter die Plaetze vier bis zehn
 * und ganz unten der eigene Stand mit dem Abstand nach oben und unten.
 */
public final class TopListGui implements BankWindow {

    public static final int SIZE = 54;
    private static final int TITLE_SLOT = 4;
    /** Die Plaetze eins bis drei auf dem Treppchen, hoechster in der Mitte. */
    private static final int[] PODIUM_SLOTS = {13, 11, 15};
    private static final int[] PODIUM_BLOCKS = {22, 20, 24};
    private static final Material[] PODIUM_MATERIALS = {
            Material.GOLD_BLOCK, Material.IRON_BLOCK, Material.COPPER_BLOCK};
    /** Die Plaetze vier bis zehn in einer Reihe. */
    private static final int[] LIST_SLOTS = {28, 29, 30, 31, 32, 33, 34};
    private static final int OWN_SLOT = 40;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;
    private static final int SHOWN = 10;

    private final BankRankingPlugin plugin;
    private final Inventory inventory;

    public TopListGui(BankRankingPlugin plugin, Player player) {
        this.plugin = plugin;
        this.inventory = plugin.getServer().createInventory(this, SIZE, Messages.mm(Messages.TOP_TITEL));
        decorate(player);
    }

    private void decorate(Player player) {
        ItemStack frame = GuiItems.filler(Material.YELLOW_STAINED_GLASS_PANE);
        ItemStack filler = GuiItems.filler();
        for (int slot = 0; slot < SIZE; slot++) {
            boolean border = slot < 9 || slot >= 45;
            this.inventory.setItem(slot, border ? frame : filler);
        }

        List<Map.Entry<UUID, PlayerData.Entry>> top = this.plugin.playerData().top(SHOWN);
        this.inventory.setItem(TITLE_SLOT, GuiItems.labelled(Material.NETHER_STAR,
                Messages.MENU_RANGLISTE_NAME,
                List.of(Messages.TOP_KOPF_LORE.replace("<anzahl>",
                        String.valueOf(this.plugin.playerData().size())))));

        boolean heads = this.plugin.settings().guiHeads();
        for (int place = 0; place < 3; place++) {
            this.inventory.setItem(PODIUM_BLOCKS[place], GuiItems.labelled(PODIUM_MATERIALS[place],
                    Messages.TOP_PODEST[place], List.of()));
            if (place < top.size()) {
                this.inventory.setItem(PODIUM_SLOTS[place],
                        entryHead(top.get(place), place + 1, player, heads));
            } else {
                this.inventory.setItem(PODIUM_SLOTS[place],
                        GuiItems.labelled(Material.PAPER, Messages.TOP_FREI, List.of()));
            }
        }
        for (int index = 0; index < LIST_SLOTS.length; index++) {
            int place = index + 3;
            if (place < top.size()) {
                this.inventory.setItem(LIST_SLOTS[index], entryHead(top.get(place), place + 1, player, heads));
            } else {
                this.inventory.setItem(LIST_SLOTS[index],
                        GuiItems.labelled(Material.PAPER, Messages.TOP_FREI, List.of()));
            }
        }

        this.inventory.setItem(OWN_SLOT, ownStand(player, heads));
        this.inventory.setItem(BACK_SLOT, GuiItems.labelled(Material.ARROW,
                Messages.BUTTON_ZURUECK_NAME, List.of()));
        this.inventory.setItem(CLOSE_SLOT, GuiItems.labelled(Material.BARRIER,
                Messages.BUTTON_SCHLIESSEN_NAME, List.of(Messages.BUTTON_SCHLIESSEN_LORE)));
    }

    private ItemStack entryHead(Map.Entry<UUID, PlayerData.Entry> entry, int place, Player viewer,
                                boolean heads) {
        PlayerData.Entry account = entry.getValue();
        Rank rank = Rank.of(account.points());
        String name = place <= 3
                ? Messages.TOP_PLATZ_NAME[place - 1].replace("<name>", account.name())
                : Messages.TOP_PLATZ_WEITER
                        .replace("<platz>", String.valueOf(place))
                        .replace("<name>", account.name());
        List<String> lore = new ArrayList<>();
        for (String line : Messages.TOP_EINTRAG_LORE) {
            lore.add(line
                    .replace("<punkte>", Scorer.format(account.points()))
                    .replace("<rang>", rank.colored()));
        }
        if (entry.getKey().equals(viewer.getUniqueId())) {
            lore.add(Messages.TOP_DAS_BIST_DU);
        }
        return GuiItems.namedHead(entry.getKey(), account.name(), name, lore, heads);
    }

    private ItemStack ownStand(Player player, boolean heads) {
        double balance = this.plugin.playerData().get(player.getUniqueId());
        int place = this.plugin.playerData().rank(player.getUniqueId());
        List<String> lore = new ArrayList<>();
        if (place == 0) {
            lore.add(Messages.TOP_ICH_LEER);
        } else {
            List<Map.Entry<UUID, PlayerData.Entry>> all = this.plugin.playerData().snapshot();
            lore.add(Messages.TOP_ICH_PUNKTE.replace("<punkte>", Scorer.format(balance)));
            if (place == 1) {
                double lead = all.size() > 1 ? balance - all.get(1).getValue().points() : balance;
                lore.add(Messages.TOP_ICH_FUEHRT.replace("<abstand>", Scorer.format(lead)));
            } else {
                PlayerData.Entry ahead = all.get(place - 2).getValue();
                lore.add(Messages.TOP_ICH_VOR
                        .replace("<name>", ahead.name())
                        .replace("<abstand>", Scorer.format(ahead.points() - balance)));
            }
            if (place < all.size()) {
                PlayerData.Entry behind = all.get(place).getValue();
                lore.add(Messages.TOP_ICH_HINTER
                        .replace("<name>", behind.name())
                        .replace("<abstand>", Scorer.format(balance - behind.points())));
            }
        }
        String name = Messages.TOP_ICH_NAME.replace("<platz>",
                place == 0 ? Messages.KONTO_ANZEIGE_OHNE_PLATZ : String.valueOf(place));
        return GuiItems.playerHead(player, name, lore, heads);
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        switch (event.getRawSlot()) {
            case BACK_SLOT -> {
                BankWindows.click(player);
                this.plugin.windows().openLater(player, new MenuGui(this.plugin, player));
            }
            case CLOSE_SLOT -> this.plugin.windows().closeLater(player);
            default -> {
                // Nur Anzeige.
            }
        }
    }
}
