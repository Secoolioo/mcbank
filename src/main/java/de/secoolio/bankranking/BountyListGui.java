package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Alle laufenden Kopfgelder auf einen Blick, nach Hoehe sortiert.
 *
 * <p>Reines Anzeigefenster. Ein Klick auf einen Steckbrief fuehrt in das Einsatzfenster, damit
 * sich ein bestehender Topf erhoehen laesst.
 */
public final class BountyListGui implements BankWindow {

    public static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int LOOT_SLOT = 51;
    private static final int CLOSE_SLOT = 53;
    private static final int MAX_EINZAHLER = 5;

    private final BankRankingPlugin plugin;
    private final Inventory inventory;
    private final Map<Integer, Bounty> slots = new LinkedHashMap<>();

    public BountyListGui(BankRankingPlugin plugin) {
        this.plugin = plugin;
        this.inventory = plugin.getServer().createInventory(this, SIZE,
                Messages.mm(Messages.KOPFGELD_LISTE_TITEL));
        decorate();
    }

    private void decorate() {
        BountyService bounties = this.plugin.bounties();
        ItemStack rahmen = GuiItems.filler(Material.RED_STAINED_GLASS_PANE);
        ItemStack fueller = GuiItems.filler();
        for (int slot = 0; slot < SIZE; slot++) {
            this.inventory.setItem(slot, slot < 9 || slot >= 45 ? rahmen : fueller);
        }

        List<Bounty> offen = new ArrayList<>(bounties.data().active());
        offen.sort(Comparator.comparingDouble((Bounty pot) -> -bounties.value(pot))
                .thenComparing(Bounty::name, String.CASE_INSENSITIVE_ORDER));

        double gesamt = 0.0;
        for (Bounty pot : offen) {
            gesamt += bounties.value(pot);
        }
        this.inventory.setItem(INFO_SLOT, GuiItems.labelled(Material.WITHER_SKELETON_SKULL,
                Messages.KOPFGELD_LISTE_NAME,
                List.of(Messages.KOPFGELD_LISTE_KOPF
                        .replace("<anzahl>", String.valueOf(offen.size()))
                        .replace("<wert>", bounties.format(gesamt)))));

        int index = 0;
        for (int row = 1; row <= 4 && index < offen.size(); row++) {
            for (int column = 1; column <= 7 && index < offen.size(); column++) {
                int slot = row * 9 + column;
                Bounty pot = offen.get(index++);
                this.inventory.setItem(slot, steckbrief(pot));
                this.slots.put(slot, pot);
            }
        }
        if (offen.isEmpty()) {
            this.inventory.setItem(22, GuiItems.labelled(Material.PAPER,
                    Messages.KOPFGELD_LISTE_LEER, List.of()));
        }

        this.inventory.setItem(BACK_SLOT, GuiItems.labelled(Material.ARROW,
                Messages.BUTTON_ZURUECK_NAME, List.of(Messages.BUTTON_ZURUECK_LORE)));
        this.inventory.setItem(CLOSE_SLOT, GuiItems.labelled(Material.BARRIER,
                Messages.BUTTON_SCHLIESSEN_NAME, List.of(Messages.BUTTON_SCHLIESSEN_LORE)));
    }

    private ItemStack steckbrief(Bounty pot) {
        BountyService bounties = this.plugin.bounties();
        List<String> lore = new ArrayList<>();
        lore.add(Messages.KOPFGELD_ZIEL_TOPF
                .replace("<wert>", bounties.format(bounties.value(pot)))
                .replace("<einsaetze>", String.valueOf(pot.stakes().size())));
        lore.add("");
        int gezeigt = 0;
        for (Bounty.Stake stake : pot.stakes()) {
            if (gezeigt++ >= MAX_EINZAHLER) {
                lore.add("<dark_gray>... und " + (pot.stakes().size() - MAX_EINZAHLER) + " weitere");
                break;
            }
            lore.add("<dark_gray>- <gray>" + stake.name() + " <dark_gray>"
                    + bounties.format(bounties.value(stake.items())));
        }
        lore.add("");
        lore.add(Messages.KOPFGELD_ZIEL_KLICK);
        return GuiItems.glow(GuiItems.namedHead(pot.target(), pot.name(),
                Messages.KOPFGELD_ZIEL_GESUCHT.replace("<name>", pot.name()), lore,
                this.plugin.settings().guiHeads()));
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        Bounty pot = this.slots.get(slot);
        if (pot != null) {
            if (pot.target().equals(player.getUniqueId())) {
                this.plugin.send(player, Messages.KOPFGELD_SELBST);
                this.plugin.effects().deny(player);
                return;
            }
            this.plugin.effects().bountyClick(player);
            this.plugin.windows().openLater(player,
                    new BountyStakeGui(this.plugin, pot.target(), pot.name()));
            return;
        }
        switch (slot) {
            case BACK_SLOT -> {
                BankWindows.click(player);
                this.plugin.windows().openLater(player, new BountyGui(this.plugin, player, 0));
            }
            case LOOT_SLOT -> {
                BankWindows.click(player);
                this.plugin.windows().openLater(player, new LootGui(this.plugin, player));
            }
            case CLOSE_SLOT -> this.plugin.windows().closeLater(player);
            default -> {
                // Rahmen und Fueller tun nichts.
            }
        }
    }
}
