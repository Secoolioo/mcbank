package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Die Auswahl: auf wen soll ein Kopfgeld ausgesetzt werden?
 *
 * <p>Reines Anzeigefenster, jeder Klick wird abgebrochen. Geblaettert wird, indem eine neue
 * Instanz mit der naechsten Seite geoeffnet wird - dasselbe Muster wie bei allen anderen
 * Fenstern, wo je Oeffnung eine Instanz entsteht und es keinen veraenderlichen Zustand gibt.
 */
public final class BountyGui implements BankWindow {

    public static final int SIZE = 54;
    private static final int INFO_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int PREV_SLOT = 48;
    private static final int PAGE_SLOT = 49;
    private static final int NEXT_SLOT = 50;
    private static final int LIST_SLOT = 51;
    private static final int CLOSE_SLOT = 53;

    private final BankRankingPlugin plugin;
    private final Inventory inventory;
    private final int page;
    private final int pageCount;
    /** Welcher Platz zu welchem Ziel gehoert. */
    private final Map<Integer, BountyTargets.Target> slots = new LinkedHashMap<>();

    public BountyGui(BankRankingPlugin plugin, Player viewer, int page) {
        this.plugin = plugin;
        this.inventory = plugin.getServer().createInventory(this, SIZE,
                Messages.mm(Messages.KOPFGELD_TITEL));

        List<BountyTargets.Target> alle = candidates(plugin, viewer);
        this.pageCount = BountyTargets.pageCount(alle.size(), BountyTargets.PER_PAGE);
        this.page = BountyTargets.clampPage(page, this.pageCount);
        decorate(BountyTargets.page(alle, this.page, BountyTargets.PER_PAGE));
    }

    /** Alle, auf die sich ein Kopfgeld setzen liesse. */
    static List<BountyTargets.Target> candidates(BankRankingPlugin plugin, Player viewer) {
        Map<UUID, String> online = new LinkedHashMap<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            online.put(player.getUniqueId(), player.getName());
        }
        Map<UUID, String> konten = new LinkedHashMap<>();
        for (Map.Entry<UUID, PlayerData.Entry> eintrag : plugin.playerData().snapshot()) {
            konten.put(eintrag.getKey(), eintrag.getValue().name());
        }
        return BountyTargets.candidates(online, konten, plugin.bounties().data().pots(),
                viewer.getUniqueId());
    }

    private void decorate(List<BountyTargets.Target> seite) {
        ItemStack rahmen = GuiItems.filler(Material.RED_STAINED_GLASS_PANE);
        ItemStack fueller = GuiItems.filler();
        for (int slot = 0; slot < SIZE; slot++) {
            this.inventory.setItem(slot, slot < 9 || slot >= 45 ? rahmen : fueller);
        }

        this.inventory.setItem(INFO_SLOT, GuiItems.labelled(Material.WITHER_SKELETON_SKULL,
                Messages.KOPFGELD_KOPF_NAME, List.of(Messages.KOPFGELD_KOPF_LORE)));

        int index = 0;
        for (int row = 1; row <= 4 && index < seite.size(); row++) {
            for (int column = 1; column <= 7 && index < seite.size(); column++) {
                int slot = row * 9 + column;
                BountyTargets.Target ziel = seite.get(index++);
                this.inventory.setItem(slot, head(ziel));
                this.slots.put(slot, ziel);
            }
        }
        if (seite.isEmpty()) {
            this.inventory.setItem(22, GuiItems.labelled(Material.PAPER,
                    Messages.KOPFGELD_NIEMAND, List.of()));
        }

        this.inventory.setItem(BACK_SLOT, GuiItems.labelled(Material.ARROW,
                Messages.BUTTON_ZURUECK_NAME, List.of(Messages.BUTTON_ZURUECK_LORE)));
        this.inventory.setItem(PAGE_SLOT, GuiItems.labelled(Material.PAPER,
                Messages.KOPFGELD_SEITE_NAME
                        .replace("<seite>", String.valueOf(this.page + 1))
                        .replace("<seiten>", String.valueOf(this.pageCount)), List.of()));
        if (this.page > 0) {
            this.inventory.setItem(PREV_SLOT, GuiItems.labelled(Material.SPECTRAL_ARROW,
                    Messages.KOPFGELD_SEITE_ZURUECK, List.of()));
        }
        if (this.page + 1 < this.pageCount) {
            this.inventory.setItem(NEXT_SLOT, GuiItems.labelled(Material.SPECTRAL_ARROW,
                    Messages.KOPFGELD_SEITE_VOR, List.of()));
        }
        this.inventory.setItem(LIST_SLOT, GuiItems.labelled(Material.WITHER_SKELETON_SKULL,
                Messages.KOPFGELD_LISTE_NAME, List.of(Messages.KOPFGELD_LISTE_LORE)));
        this.inventory.setItem(CLOSE_SLOT, GuiItems.labelled(Material.BARRIER,
                Messages.BUTTON_SCHLIESSEN_NAME, List.of(Messages.BUTTON_SCHLIESSEN_LORE)));
    }

    private ItemStack head(BountyTargets.Target ziel) {
        BountyService bounties = this.plugin.bounties();
        Bounty topf = bounties.data().pot(ziel.id());
        boolean gejagt = topf != null && !topf.isEmpty();

        List<String> lore = new ArrayList<>();
        lore.add(ziel.online() ? Messages.KOPFGELD_ZIEL_ONLINE : Messages.KOPFGELD_ZIEL_OFFLINE);
        if (gejagt) {
            lore.add(Messages.KOPFGELD_ZIEL_TOPF
                    .replace("<wert>", bounties.reward(topf))
                    .replace("<einsaetze>", String.valueOf(topf.stakes().size())));
        } else {
            lore.add(Messages.KOPFGELD_ZIEL_KEIN_TOPF);
        }
        long sperre = bounties.postCooldownLeft(ziel.id());
        if (sperre > 0L) {
            lore.add(Messages.KOPFGELD_ZIEL_GESPERRT
                    .replace("<rest>", BountyService.Zeit.kurz(sperre)));
        } else {
            lore.add("");
            lore.add(Messages.KOPFGELD_ZIEL_KLICK);
        }

        String name = (gejagt ? Messages.KOPFGELD_ZIEL_GESUCHT : Messages.KOPFGELD_ZIEL_FREI)
                .replace("<name>", ziel.name());
        ItemStack kopf = GuiItems.namedHead(ziel.id(), ziel.name(), name, lore,
                this.plugin.settings().guiHeads());
        return gejagt ? GuiItems.glow(kopf) : kopf;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player player) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        BountyTargets.Target ziel = this.slots.get(slot);
        if (ziel != null) {
            this.plugin.effects().bountyClick(player);
            this.plugin.windows().openLater(player,
                    new BountyStakeGui(this.plugin, ziel.id(), ziel.name()));
            return;
        }
        switch (slot) {
            case BACK_SLOT -> {
                BankWindows.click(player);
                this.plugin.windows().openLater(player, new MenuGui(this.plugin, player));
            }
            case PREV_SLOT -> blaettern(player, this.page - 1);
            case NEXT_SLOT -> blaettern(player, this.page + 1);
            case LIST_SLOT -> {
                BankWindows.click(player);
                this.plugin.windows().openLater(player, new BountyListGui(this.plugin));
            }
            case CLOSE_SLOT -> this.plugin.windows().closeLater(player);
            default -> {
                // Rahmen und Fueller tun nichts.
            }
        }
    }

    private void blaettern(Player player, int seite) {
        if (seite < 0 || seite >= this.pageCount) {
            return;
        }
        this.plugin.effects().bountyPage(player);
        this.plugin.windows().openLater(player, new BountyGui(this.plugin, player, seite));
    }
}
