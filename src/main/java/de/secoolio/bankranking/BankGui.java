package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/**
 * Ein geoeffnetes Bank-Fenster: sechs Reihen mit Rahmen, 28 freien Plaetzen in der Mitte,
 * einer mitlaufenden Wertanzeige und dem Haken-Knopf zum Abgeben.
 *
 * <p>Pro Oeffnung entsteht eine eigene Instanz, die zugleich als Kennzeichen dient
 * ({@code inventory.getHolder(false) instanceof BankGui}). Aus einem Inventar-Ereignis heraus wird
 * nie {@code closeInventory()} oder {@code openInventory()} aufgerufen.
 */
public final class BankGui implements InventoryHolder {

    public static final int SIZE = 54;
    /** Der Haken-Knopf, unten in der Mitte. */
    public static final int CONFIRM_SLOT = 49;
    /** Zeigt laufend den Wert der eingelegten Items. */
    public static final int VALUE_SLOT = 45;
    /** Zeigt den eigenen Kontostand. */
    public static final int ACCOUNT_SLOT = 53;

    /** Die 28 freien Plaetze in der Mitte (vier Reihen zu sieben). */
    private static final int[] DEPOSIT_SLOTS = buildDepositSlots();

    private final BankRankingPlugin plugin;
    private final Inventory inventory;
    private final ItemStack button;
    private boolean updateQueued;

    public BankGui(BankRankingPlugin plugin) {
        this.plugin = plugin;
        this.inventory = plugin.getServer().createInventory(this, SIZE, Messages.mm(Messages.GUI_TITEL));
        this.button = GuiItems.checkButton(Messages.BUTTON_NAME, List.of(Messages.BUTTON_LORE),
                plugin.settings().confirmHead());
        decorate();
    }

    private static int[] buildDepositSlots() {
        int[] slots = new int[28];
        int index = 0;
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 7; column++) {
                slots[index++] = row * 9 + column;
            }
        }
        return slots;
    }

    /** Ist dieser Rohslot einer der freien Ablageplaetze? */
    public static boolean isDepositSlot(int rawSlot) {
        for (int slot : DEPOSIT_SLOTS) {
            if (slot == rawSlot) {
                return true;
            }
        }
        return false;
    }

    private void decorate() {
        ItemStack filler = GuiItems.filler();
        for (int slot = 0; slot < SIZE; slot++) {
            if (!isDepositSlot(slot)) {
                this.inventory.setItem(slot, filler);
            }
        }
        this.inventory.setItem(CONFIRM_SLOT, this.button);
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
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, SoundCategory.MASTER, 0.6f, 1.4f);
        updateInfo(player);
    }

    /** Alle eingelegten Stapel, als Kopien. */
    private List<ItemStack> deposited() {
        List<ItemStack> stacks = new ArrayList<>();
        for (int slot : DEPOSIT_SLOTS) {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack != null && !stack.isEmpty()) {
                stacks.add(stack.clone());
            }
        }
        return stacks;
    }

    /** Schreibt Wertanzeige und Kontostand neu. Wird nach jeder Aenderung im Fenster aufgerufen. */
    public void updateInfo(Player player) {
        List<ItemStack> stacks = deposited();
        if (stacks.isEmpty()) {
            this.inventory.setItem(VALUE_SLOT, GuiItems.labelled(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    Messages.WERT_ANZEIGE_LEER_NAME, List.of(Messages.WERT_ANZEIGE_LEER_LORE)));
        } else {
            Scorer.Deposit preview = preview(player, stacks);
            List<String> lore = new ArrayList<>();
            for (String line : Messages.WERT_ANZEIGE_LORE) {
                lore.add(line.replace("<anzahl>", String.valueOf(preview.itemCount()))
                        .replace("<roh>", Scorer.format(preview.rawTotal()))
                        .replace("<punkte>", Scorer.format(preview.total()))
                        .replace("<prozent>", percent(preview.rawTotal(), preview.total())));
            }
            this.inventory.setItem(VALUE_SLOT,
                    GuiItems.labelled(Material.GOLD_INGOT, Messages.WERT_ANZEIGE_NAME, lore));
        }

        int rank = this.plugin.playerData().rank(player.getUniqueId());
        double balance = this.plugin.playerData().get(player.getUniqueId());
        Rank stufe = Rank.of(balance);
        double wealth = this.plugin.scorer().wealthFactor(balance);
        List<String> accountLore = new ArrayList<>();
        for (String line : Messages.KONTO_ANZEIGE_LORE) {
            accountLore.add(line
                    .replace("<punkte>", Scorer.format(balance))
                    .replace("<platz>", rank == 0 ? Messages.KONTO_ANZEIGE_OHNE_PLATZ : String.valueOf(rank))
                    .replace("<rang>", stufe.colored())
                    .replace("<faktor>", Scorer.format(wealth * 100.0)));
        }
        this.inventory.setItem(ACCOUNT_SLOT,
                GuiItems.labelled(Material.BOOK, Messages.KONTO_ANZEIGE_NAME, accountLore));
    }

    /**
     * Rechnet eine Einzahlung durch, ohne etwas zu buchen: auf einer Kopie der Marktsaettigung,
     * damit die Anzeige den Zaehler nicht hochtreibt.
     */
    private Scorer.Deposit preview(Player player, List<ItemStack> stacks) {
        Scorer.Unpacked unpacked = Scorer.unpack(stacks);
        return this.plugin.scorer().deposit(Scorer.factsOf(unpacked.valuables()),
                this.plugin.saturationView(player), this.plugin.playerData().get(player.getUniqueId()));
    }

    private static String percent(double raw, double actual) {
        if (raw <= 0.0) {
            return "100";
        }
        return String.valueOf(Math.round(actual / raw * 100.0));
    }

    /** Verrechnet den Inhalt. Das Fenster bleibt danach offen und leer. */
    public void confirm(Player player) {
        List<ItemStack> stacks = deposited();
        if (stacks.isEmpty()) {
            deny(player, Messages.BANK_LEER);
            return;
        }

        Scorer.Unpacked unpacked = Scorer.unpack(stacks);
        // Nur rechnen - die Marktsaettigung wird erst mit dem Buchen fortgeschrieben.
        double before = this.plugin.playerData().get(player.getUniqueId());
        Scorer.Deposit deposit = this.plugin.scorer().deposit(Scorer.factsOf(unpacked.valuables()),
                this.plugin.saturationView(player), before);
        double total = deposit.total();
        int itemCount = deposit.itemCount();
        if (total <= 0.0) {
            deny(player, Messages.BANK_WERTLOS);
            return;
        }

        // Erst buchen und speichern, dann die Items entfernen: schlaegt das Speichern fehl,
        // behaelt der Spieler alles und der Marktpreis bleibt unberuehrt.
        PlayerData.AddResult result = this.plugin.playerData().add(player.getUniqueId(), player.getName(),
                total, deposit.saturationDeltas());
        if (!result.saved()) {
            deny(player, Messages.BANK_FEHLER);
            return;
        }

        for (int slot : DEPOSIT_SLOTS) {
            this.inventory.setItem(slot, null);
        }

        boolean dropped = false;
        for (ItemStack empty : unpacked.emptiedContainers()) {
            for (ItemStack leftover : player.getInventory().addItem(empty).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
                dropped = true;
            }
        }

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, SoundCategory.MASTER, 0.7f, 1.6f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.MASTER, 0.9f, 1.2f);
        this.plugin.send(player, Messages.BANK_BESTAETIGT,
                Placeholder.unparsed("anzahl", String.valueOf(itemCount)),
                Placeholder.unparsed("punkte", Scorer.format(total)),
                Placeholder.unparsed("gesamt", Scorer.format(result.total())));
        if (deposit.total() < deposit.rawTotal() - 0.05) {
            this.plugin.send(player, Messages.BANK_GEDAEMPFT,
                    Placeholder.unparsed("roh", Scorer.format(deposit.rawTotal())),
                    Placeholder.unparsed("prozent", percent(deposit.rawTotal(), deposit.total())));
        }
        Rank beforeRank = Rank.of(before);
        Rank after = Rank.of(result.total());
        if (after != beforeRank) {
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE,
                    SoundCategory.MASTER, 0.8f, 1.0f);
            this.plugin.send(player, Messages.RANG_AUFSTIEG,
                    Placeholder.parsed("rang", after.colored()));
        }
        if (!unpacked.emptiedContainers().isEmpty()) {
            this.plugin.send(player, Messages.BANK_BEHAELTER_ZURUECK);
        }
        if (dropped) {
            this.plugin.send(player, Messages.BANK_ZURUECK_BODEN);
        }
        this.plugin.getLogger().info("Einzahlung: " + player.getName() + " +" + Scorer.format(total)
                + " (roh " + Scorer.format(deposit.rawTotal()) + ", " + deposit.valuations().size()
                + " Stapel, " + itemCount + " Items) -> " + Scorer.format(result.total()));
        updateInfo(player);
        this.plugin.ranking().refreshAll();
    }

    private void deny(Player player, String message) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, SoundCategory.MASTER, 0.7f, 1.0f);
        this.plugin.send(player, message);
        // Die Anzeige darf den abgelehnten Zustand nicht ueberleben.
        updateInfo(player);
    }

    /**
     * Gibt alle eingelegten Items zurueck. Leert jeden Platz, bevor er ihn zurueckgibt, damit ein
     * zweiter Aufruf nichts doppelt herausgibt.
     */
    public void refund(Player player, boolean dropAll) {
        boolean returned = false;
        boolean dropped = false;
        for (int slot : DEPOSIT_SLOTS) {
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
            if (dropAll) {
                this.plugin.send(player, Messages.BANK_TOD_BODEN);
            } else {
                this.plugin.send(player, Messages.BANK_ZURUECK);
                if (dropped) {
                    this.plugin.send(player, Messages.BANK_ZURUECK_BODEN);
                }
            }
        }
    }

    /**
     * Bittet um eine Aktualisierung der Anzeige im naechsten Tick. Mehrere Klicks im selben Tick
     * ergeben nur eine Neuberechnung.
     */
    public void requestUpdate(Player player) {
        if (this.updateQueued) {
            return;
        }
        this.updateQueued = true;
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            this.updateQueued = false;
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder(false) == this) {
                updateInfo(player);
            }
        });
    }

    /** Nur fuer die Klick-Regeln: die Liste der Ablageplaetze. */
    public static int[] depositSlots() {
        return Arrays.copyOf(DEPOSIT_SLOTS, DEPOSIT_SLOTS.length);
    }
}
