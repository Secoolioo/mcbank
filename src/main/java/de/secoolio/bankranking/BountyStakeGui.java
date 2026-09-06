package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Hier wird der Einsatz hineingelegt.
 *
 * <p>Aufbau und Klickregeln sind bewusst dieselben wie im Abgabe-Fenster: dieselben 28 Plaetze,
 * derselbe Haken unten in der Mitte, dasselbe Versprechen, dass Schliessen alles zurueckgibt.
 *
 * <p>Nicht erlaubte Gegenstaende werden beim Hineinlegen nicht abgefangen, sondern nur gezaehlt
 * und rot gemeldet; bestaetigen laesst sich erst, wenn keine mehr darin liegen. Eine Filterung
 * auf Klick-Ebene waere bei Zieh-Vorgaengen und Umschalt-Klicks nicht zuverlaessig, und
 * "Fenster schliessen = alles zurueck" soll uneingeschraenkt wahr bleiben.
 */
public final class BountyStakeGui implements BankWindow {

    public static final int SIZE = 54;
    private static final int TARGET_SLOT = 4;
    private static final int BACK_SLOT = 45;
    private static final int VALUE_SLOT = 47;
    private static final int CONFIRM_SLOT = 49;
    private static final int POT_SLOT = 51;
    private static final int CLOSE_SLOT = 53;

    private static final int[] STAKE_SLOTS = buildStakeSlots();

    private final BankRankingPlugin plugin;
    private final Inventory inventory;
    private final UUID target;
    private final String targetName;
    private final ItemStack button;
    private boolean updateQueued;

    public BountyStakeGui(BankRankingPlugin plugin, UUID target, String targetName) {
        this.plugin = plugin;
        this.target = target;
        this.targetName = targetName;
        this.inventory = plugin.getServer().createInventory(this, SIZE,
                Messages.mm(Messages.KOPFGELD_EINSATZ_TITEL,
                        Placeholder.unparsed("name", targetName)));
        this.button = GuiItems.checkButton(Messages.KOPFGELD_BUTTON_NAME,
                List.of(Messages.KOPFGELD_BUTTON_LORE), plugin.settings().confirmHead());
        decorate();
        updateInfo();
    }

    private static int[] buildStakeSlots() {
        int[] slots = new int[28];
        int index = 0;
        for (int row = 1; row <= 4; row++) {
            for (int column = 1; column <= 7; column++) {
                slots[index++] = row * 9 + column;
            }
        }
        return slots;
    }

    static boolean isStakeSlot(int rawSlot) {
        for (int slot : STAKE_SLOTS) {
            if (slot == rawSlot) {
                return true;
            }
        }
        return false;
    }

    private void decorate() {
        ItemStack rahmen = GuiItems.filler(Material.RED_STAINED_GLASS_PANE);
        for (int slot = 0; slot < SIZE; slot++) {
            if (!isStakeSlot(slot)) {
                this.inventory.setItem(slot, rahmen);
            }
        }
        this.inventory.setItem(CONFIRM_SLOT, this.button);
        this.inventory.setItem(BACK_SLOT, GuiItems.labelled(Material.ARROW,
                Messages.BUTTON_ZURUECK_NAME, List.of(Messages.BUTTON_ZURUECK_LORE)));
        this.inventory.setItem(CLOSE_SLOT, GuiItems.labelled(Material.BARRIER,
                Messages.BUTTON_SCHLIESSEN_NAME, List.of(Messages.BUTTON_SCHLIESSEN_LORE)));
    }

    /** Die Gegenstaende, die gerade im Fenster liegen. */
    private List<ItemStack> contents() {
        List<ItemStack> liegend = new ArrayList<>();
        for (int slot : STAKE_SLOTS) {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack != null && !stack.getType().isAir()) {
                liegend.add(stack);
            }
        }
        return liegend;
    }

    /** Frischt Wertanzeige und Topf-Anzeige auf. */
    private void updateInfo() {
        BountyService bounties = this.plugin.bounties();
        List<ItemStack> liegend = contents();
        Map<Material, Integer> einsatz = BountyItems.count(liegend);
        int abgelehnt = BountyItems.rejected(liegend);

        List<String> lore = new ArrayList<>();
        if (einsatz.isEmpty()) {
            lore.add(Messages.KOPFGELD_EINSATZ_LEER);
        } else {
            lore.add(Messages.KOPFGELD_EINSATZ_WERT
                    .replace("<wert>", bounties.format(bounties.value(einsatz))));
        }
        if (abgelehnt > 0) {
            lore.add(Messages.KOPFGELD_EINSATZ_ABGELEHNT
                    .replace("<anzahl>", String.valueOf(abgelehnt)));
        }
        lore.add("");
        lore.add(Messages.KOPFGELD_EINSATZ_ERLAUBT);

        this.inventory.setItem(VALUE_SLOT, GuiItems.labelled(Material.GOLD_INGOT,
                Messages.KOPFGELD_EINSATZ_NAME
                        .replace("<anzahl>", String.valueOf(BountyItems.size(einsatz))),
                lore));

        Bounty topf = bounties.data().pot(this.target);
        List<String> topfLore = new ArrayList<>();
        if (topf == null || topf.isEmpty()) {
            topfLore.add(Messages.KOPFGELD_ZIEL_KEIN_TOPF);
        } else {
            topfLore.add(Messages.KOPFGELD_ZIEL_TOPF
                    .replace("<wert>", bounties.format(bounties.value(topf)))
                    .replace("<einsaetze>", String.valueOf(topf.stakes().size())));
            for (Bounty.Stake stake : topf.stakes()) {
                topfLore.add("<dark_gray>- <gray>" + stake.name());
            }
        }
        long sperre = bounties.postCooldownLeft(this.target);
        if (sperre > 0L) {
            topfLore.add(Messages.KOPFGELD_ZIEL_GESPERRT
                    .replace("<rest>", BountyService.Zeit.kurz(sperre)));
        }
        this.inventory.setItem(TARGET_SLOT, GuiItems.namedHead(this.target, this.targetName,
                Messages.KOPFGELD_ZIEL_GESUCHT.replace("<name>", this.targetName), topfLore,
                this.plugin.settings().guiHeads()));
        this.inventory.setItem(POT_SLOT, GuiItems.labelled(Material.BOOK,
                Messages.KOPFGELD_LISTE_NAME, List.of(Messages.KOPFGELD_LISTE_LORE)));
    }

    @Override
    public Inventory getInventory() {
        return this.inventory;
    }

    @Override
    public void handleClick(InventoryClickEvent event, Player player) {
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < SIZE && !isStakeSlot(rawSlot)) {
            event.setCancelled(true);
            handleButton(rawSlot, event, player);
            return;
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && isButton(event.getCursor())) {
            // Ein Doppelklick saugt passende Items aus beiden Inventaren - auch den Knopf.
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && rawSlot >= SIZE && isButton(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        requestUpdate(player);
    }

    private boolean isButton(ItemStack stack) {
        return stack != null && stack.isSimilar(this.button);
    }

    private void handleButton(int rawSlot, InventoryClickEvent event, Player player) {
        switch (rawSlot) {
            case CONFIRM_SLOT -> {
                ClickType klick = event.getClick();
                // Zahlentasten, Zweithand-Tausch und Kreativ-Klonen melden denselben Rohslot,
                // sollen aber nichts ausloesen.
                if (klick == ClickType.LEFT || klick == ClickType.RIGHT
                        || klick == ClickType.SHIFT_LEFT || klick == ClickType.SHIFT_RIGHT) {
                    confirm(player);
                }
            }
            case BACK_SLOT -> {
                BankWindows.click(player);
                this.plugin.windows().openLater(player, new BountyGui(this.plugin, player, 0));
            }
            case POT_SLOT -> {
                BankWindows.click(player);
                this.plugin.windows().openLater(player, new BountyListGui(this.plugin));
            }
            case CLOSE_SLOT -> this.plugin.windows().closeLater(player);
            default -> {
                // Rahmen und Anzeigen tun nichts.
            }
        }
    }

    @Override
    public void handleDrag(InventoryDragEvent event, Player player) {
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < SIZE && !isStakeSlot(rawSlot)) {
                // Greift ein Zieh-Vorgang auch nur einen Rahmenplatz an, wird er ganz verworfen.
                event.setCancelled(true);
                return;
            }
        }
        requestUpdate(player);
    }

    /**
     * Bucht den Einsatz.
     *
     * <p>Die Reihenfolge ist die entscheidende Stelle: erst rechnen, dann buchen und speichern,
     * und <strong>erst nach erfolgreichem Speichern</strong> die Plaetze leeren. Scheitert das
     * Speichern, bleibt alles im Fenster liegen und der Spieler nimmt es wieder mit.
     */
    private void confirm(Player player) {
        List<ItemStack> liegend = contents();
        if (BountyItems.rejected(liegend) > 0) {
            this.plugin.send(player, Messages.KOPFGELD_NUR_MATERIALIEN);
            this.plugin.effects().deny(player);
            return;
        }
        Map<Material, Integer> einsatz = BountyItems.count(liegend);
        BountyService bounties = this.plugin.bounties();

        BountyService.PlaceResult ergebnis =
                bounties.place(player, this.target, this.targetName, einsatz);
        if (ergebnis != BountyService.PlaceResult.OK) {
            meldung(player, ergebnis);
            this.plugin.effects().deny(player);
            updateInfo();
            return;
        }
        for (int slot : STAKE_SLOTS) {
            this.inventory.setItem(slot, null);
        }
        updateInfo();
        this.plugin.windows().closeLater(player);
    }

    private void meldung(Player player, BountyService.PlaceResult ergebnis) {
        switch (ergebnis) {
            case ABGESCHALTET -> this.plugin.send(player, Messages.KOPFGELD_AUS);
            case DATEI_GESPERRT -> this.plugin.send(player, Messages.KOPFGELD_GESPERRT);
            case AUF_SICH_SELBST -> this.plugin.send(player, Messages.KOPFGELD_SELBST);
            case ZIEL_BESCHAEDIGT -> this.plugin.send(player, Messages.KOPFGELD_ZIEL_BESCHAEDIGT,
                    Placeholder.unparsed("name", this.targetName));
            case ZU_KLEIN -> this.plugin.send(player, Messages.KOPFGELD_ZU_KLEIN,
                    Placeholder.unparsed("mindest",
                            this.plugin.bounties().format(this.plugin.settings().bountyMinStake())));
            case SPERRFRIST -> this.plugin.send(player, Messages.KOPFGELD_AUSSETZ_SPERRE,
                    Placeholder.unparsed("name", this.targetName),
                    Placeholder.unparsed("rest", BountyService.Zeit.kurz(
                            this.plugin.bounties().postCooldownLeft(this.target))));
            case SPEICHERFEHLER -> this.plugin.send(player, Messages.KOPFGELD_FEHLER);
            case OK -> {
                // kommt hier nicht vor
            }
        }
    }

    /** Sammelt mehrere Klicks eines Ticks zu einer einzigen Neuberechnung. */
    private void requestUpdate(Player player) {
        if (this.updateQueued) {
            return;
        }
        this.updateQueued = true;
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            this.updateQueued = false;
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder(false) == this) {
                updateInfo();
            }
        });
    }

    @Override
    public void onClosed(Player player, boolean dropAll) {
        for (int slot : STAKE_SLOTS) {
            ItemStack stack = this.inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            // Erst leeren, dann zurueckgeben: sonst koennte ein zweiter Aufruf dieselben
            // Gegenstaende ein zweites Mal herausgeben.
            this.inventory.setItem(slot, null);
            if (dropAll) {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            } else {
                for (ItemStack rest : player.getInventory().addItem(stack).values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
            }
        }
    }
}
