package de.secoolio.bankranking;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** Die Kontoseite: Rang, Fortschritt und die eigenen Zahlen. */
public final class AccountGui implements BankWindow {

    public static final int SIZE = 54;
    private static final int HEAD_SLOT = 4;
    private static final int RANK_SLOT = 20;
    private static final int DEPOSITS_SLOT = 22;
    private static final int BIGGEST_SLOT = 24;
    private static final int FAVOURITE_SLOT = 29;
    private static final int RECENT_SLOT = 31;
    private static final int FACTOR_SLOT = 33;
    private static final int BACK_SLOT = 45;
    private static final int CLOSE_SLOT = 53;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.systemDefault());

    private final BankRankingPlugin plugin;
    private final Inventory inventory;

    public AccountGui(BankRankingPlugin plugin, Player player) {
        this.plugin = plugin;
        this.inventory = plugin.getServer().createInventory(this, SIZE,
                Messages.mm(Messages.KONTO_TITEL.replace("<name>", player.getName())));
        decorate(player);
    }

    private void decorate(Player player) {
        double balance = this.plugin.playerData().get(player.getUniqueId());
        RankProgress progress = RankProgress.of(balance);
        PlayerStats stats = this.plugin.playerData().stats(player.getUniqueId());

        ItemStack frame = GuiItems.filler(progress.rank().pane());
        ItemStack filler = GuiItems.filler();
        for (int slot = 0; slot < SIZE; slot++) {
            boolean border = slot < 9 || slot >= 45;
            this.inventory.setItem(slot, border ? frame : filler);
        }

        this.inventory.setItem(HEAD_SLOT, GuiItems.playerHead(player,
                Messages.MENU_KOPF_NAME.replace("<name>", player.getName()),
                MenuGui.GuiTexts.accountLines(this.plugin, player, balance, progress),
                this.plugin.settings().guiHeads()));
        this.inventory.setItem(RANK_SLOT, GuiItems.labelled(progress.rank().icon(),
                Messages.MENU_RANG_NAME.replace("<rang>", progress.rank().colored()),
                MenuGui.GuiTexts.progressLines(this.plugin, progress)));

        List<String> depositLore = new ArrayList<>();
        for (String line : Messages.KONTO_EINZAHLUNGEN_LORE) {
            depositLore.add(line
                    .replace("<anzahl>", String.valueOf(stats.deposits()))
                    .replace("<items>", String.valueOf(stats.items()))
                    .replace("<schnitt>", Scorer.format(stats.averagePoints())));
        }
        this.inventory.setItem(DEPOSITS_SLOT,
                GuiItems.labelled(Material.CLOCK, Messages.KONTO_EINZAHLUNGEN_NAME, depositLore));

        this.inventory.setItem(BIGGEST_SLOT, stats.biggest() == null
                ? GuiItems.labelled(Material.GOLD_BLOCK, Messages.KONTO_GROESSTE_NAME,
                        List.of(Messages.KONTO_LEER))
                : GuiItems.labelled(Material.GOLD_BLOCK, Messages.KONTO_GROESSTE_NAME,
                        depositLines(stats.biggest())));

        this.inventory.setItem(FAVOURITE_SLOT, stats.favourite()
                .map(entry -> GuiItems.glowing(entry.getKey(), Messages.KONTO_LIEBLING_NAME,
                        List.of(Messages.KONTO_LIEBLING_LORE
                                .replace("<material>", materialName(entry.getKey()))
                                .replace("<anzahl>", String.valueOf(entry.getValue())))))
                .orElseGet(() -> GuiItems.labelled(Material.PAPER, Messages.KONTO_LIEBLING_NAME,
                        List.of(Messages.KONTO_LEER))));

        List<String> recent = new ArrayList<>();
        if (stats.recent().isEmpty()) {
            recent.add(Messages.KONTO_LEER);
        } else {
            for (PlayerStats.Deposit deposit : stats.recent()) {
                recent.add(Messages.KONTO_LETZTE_ZEILE
                        .replace("<datum>", STAMP.format(Instant.ofEpochMilli(deposit.time())))
                        .replace("<punkte>", Scorer.format(deposit.points()))
                        .replace("<items>", String.valueOf(deposit.items())));
            }
        }
        this.inventory.setItem(RECENT_SLOT,
                GuiItems.labelled(Material.WRITABLE_BOOK, Messages.KONTO_LETZTE_NAME, recent));

        List<String> factorLore = new ArrayList<>();
        for (String line : Messages.KONTO_FAKTOR_LORE) {
            factorLore.add(line.replace("<faktor>",
                    Scorer.format(this.plugin.scorer().wealthFactor(balance) * 100.0)));
        }
        this.inventory.setItem(FACTOR_SLOT,
                GuiItems.labelled(Material.EXPERIENCE_BOTTLE, Messages.KONTO_FAKTOR_NAME, factorLore));

        this.inventory.setItem(BACK_SLOT, GuiItems.labelled(Material.ARROW,
                Messages.BUTTON_ZURUECK_NAME, List.of()));
        this.inventory.setItem(CLOSE_SLOT, GuiItems.labelled(Material.BARRIER,
                Messages.BUTTON_SCHLIESSEN_NAME, List.of(Messages.BUTTON_SCHLIESSEN_LORE)));
    }

    private List<String> depositLines(PlayerStats.Deposit deposit) {
        List<String> lines = new ArrayList<>();
        for (String line : Messages.KONTO_GROESSTE_LORE) {
            lines.add(line
                    .replace("<punkte>", Scorer.format(deposit.points()))
                    .replace("<items>", String.valueOf(deposit.items()))
                    .replace("<material>", materialName(deposit.top()))
                    .replace("<datum>", STAMP.format(Instant.ofEpochMilli(deposit.time()))));
        }
        return lines;
    }

    /** Der Materialname in der Sprache des Spielers - dafuer sorgt der Client selbst. */
    private static String materialName(Material material) {
        return material == null ? "-" : "<lang:" + material.translationKey() + ">";
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
