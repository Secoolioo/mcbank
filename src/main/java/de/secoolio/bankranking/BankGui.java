package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Ein geoeffnetes Bank-Fenster. Pro Oeffnung wird eine eigene Instanz erzeugt, die zugleich als
 * Kennzeichen dient ({@code inventory.getHolder(false) instanceof BankGui}).
 *
 * <p>Ruft nie {@code closeInventory()} oder {@code openInventory()} aus einem Inventar-Event heraus auf.
 */
public final class BankGui implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int CONFIRM_SLOT = 26;

    private final BankRankingPlugin plugin;
    private final Inventory inventory;
    private final ItemStack button;

    public BankGui(BankRankingPlugin plugin) {
        this.plugin = plugin;
        this.inventory = plugin.getServer().createInventory(this, SIZE, Messages.mm(Messages.GUI_TITEL));
        this.button = createButton(plugin);
        this.inventory.setItem(CONFIRM_SLOT, this.button);
    }

    private static ItemStack createButton(BankRankingPlugin plugin) {
        ItemStack stack = ItemStack.of(Material.EMERALD);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Messages.mm(Messages.BUTTON_NAME));
        List<Component> lore = Arrays.stream(Messages.BUTTON_LORE).map(Messages::mm).toList();
        meta.lore(lore);
        // Markierung, damit kein normaler Smaragd des Spielers je als Knopf durchgeht.
        meta.getPersistentDataContainer().set(plugin.buttonKey(), PersistentDataType.BOOLEAN, true);
        stack.setItemMeta(meta);
        return stack;
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    public boolean isButton(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.isSimilar(this.button);
    }

    public void open(Player player) {
        player.openInventory(this.inventory);
    }

    /** Verrechnet den Inhalt. Das Fenster bleibt danach offen und leer. */
    public void confirm(Player player) {
        List<ItemStack> deposited = new ArrayList<>();
        for (int slot = 0; slot < CONFIRM_SLOT; slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                deposited.add(stack.clone());
            }
        }
        if (deposited.isEmpty()) {
            this.plugin.send(player, Messages.BANK_LEER);
            return;
        }

        Scorer.Unpacked unpacked = Scorer.unpack(deposited);
        List<Scorer.Valuation> valuations = new ArrayList<>();
        int itemCount = 0;
        for (ItemStack stack : unpacked.valuables()) {
            valuations.add(this.plugin.scorer().value(Scorer.facts(stack)));
            itemCount += stack.getAmount();
        }
        double total = this.plugin.scorer().total(valuations);
        if (total <= 0.0) {
            this.plugin.send(player, Messages.BANK_WERTLOS);
            return;
        }

        // Erst buchen und speichern, dann die Items entfernen: schlaegt das Speichern fehl,
        // behaelt der Spieler alles.
        PlayerData.AddResult result = this.plugin.playerData().add(player.getUniqueId(), player.getName(), total);
        if (!result.saved()) {
            this.plugin.send(player, Messages.BANK_FEHLER);
            return;
        }

        for (int slot = 0; slot < CONFIRM_SLOT; slot++) {
            this.inventory.setItem(slot, null);
        }

        boolean dropped = false;
        for (ItemStack empty : unpacked.emptiedContainers()) {
            for (ItemStack leftover : player.getInventory().addItem(empty).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped = true;
            }
        }

        this.plugin.send(player, Messages.BANK_BESTAETIGT,
                Placeholder.unparsed("anzahl", String.valueOf(itemCount)),
                Placeholder.unparsed("punkte", Scorer.format(total)),
                Placeholder.unparsed("gesamt", Scorer.format(result.total())));
        if (!unpacked.emptiedContainers().isEmpty()) {
            this.plugin.send(player, Messages.BANK_BEHAELTER_ZURUECK);
        }
        if (dropped) {
            this.plugin.send(player, Messages.BANK_ZURUECK_BODEN);
        }
        this.plugin.getLogger().info("Einzahlung: " + player.getName() + " +" + Scorer.format(total)
                + " (" + valuations.size() + " Stapel, " + itemCount + " Items) -> " + Scorer.format(result.total()));
        this.plugin.ranking().refreshAll();
    }

    /**
     * Gibt alle eingelegten Items zurueck. Leert jeden Slot, bevor er ihn zurueckgibt, damit ein
     * zweiter Aufruf nichts doppelt herausgibt.
     */
    public void refund(Player player, boolean dropAll) {
        boolean returned = false;
        boolean dropped = false;
        for (int slot = 0; slot < CONFIRM_SLOT; slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack == null || stack.isEmpty()) {
                continue;
            }
            this.inventory.setItem(slot, null);
            returned = true;
            if (dropAll) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
                dropped = true;
                continue;
            }
            for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped = true;
            }
        }
        if (returned) {
            this.plugin.send(player, Messages.BANK_ZURUECK);
            if (dropped) {
                this.plugin.send(player, Messages.BANK_ZURUECK_BODEN);
            }
        }
    }
}
