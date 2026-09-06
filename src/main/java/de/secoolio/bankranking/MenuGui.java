package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Das Hauptmenue der Bank: der erste Blick nach dem Rechtsklick auf eine Figur.
 *
 * <p>Reines Anzeigefenster - jeder Klick wird abgebrochen, die Knoepfe oeffnen die Unterfenster
 * einen Tick spaeter.
 */
public final class MenuGui implements BankWindow {

    public static final int SIZE = 54;
    private static final int HEAD_SLOT = 4;
    private static final int DEPOSIT_SLOT = 20;
    private static final int TOP_SLOT = 22;
    private static final int ACCOUNT_SLOT = 24;
    private static final int RANK_SLOT = 40;
    private static final int CLOSE_SLOT = 49;

    private final BankRankingPlugin plugin;
    private final Inventory inventory;

    public MenuGui(BankRankingPlugin plugin, Player player) {
        this.plugin = plugin;
        this.inventory = plugin.getServer().createInventory(this, SIZE, Messages.mm(Messages.MENU_TITEL));
        decorate(player);
    }

    private void decorate(Player player) {
        double balance = this.plugin.playerData().get(player.getUniqueId());
        RankProgress progress = RankProgress.of(balance);
        ItemStack frame = GuiItems.filler(progress.rank().pane());
        ItemStack filler = GuiItems.filler();
        for (int slot = 0; slot < SIZE; slot++) {
            boolean border = slot < 9 || slot >= 45;
            this.inventory.setItem(slot, border ? frame : filler);
        }

        this.inventory.setItem(HEAD_SLOT, GuiItems.playerHead(player,
                Messages.MENU_KOPF_NAME.replace("<name>", player.getName()),
                GuiTexts.accountLines(this.plugin, player, balance, progress),
                this.plugin.settings().guiHeads()));

        this.inventory.setItem(DEPOSIT_SLOT, GuiItems.glowing(Material.CHEST,
                Messages.MENU_ABGEBEN_NAME, List.of(Messages.MENU_ABGEBEN_LORE)));
        this.inventory.setItem(TOP_SLOT, GuiItems.labelled(Material.NETHER_STAR,
                Messages.MENU_RANGLISTE_NAME, List.of(Messages.MENU_RANGLISTE_LORE)));
        this.inventory.setItem(ACCOUNT_SLOT, GuiItems.labelled(Material.BOOK,
                Messages.MENU_KONTO_NAME, List.of(Messages.MENU_KONTO_LORE)));
        this.inventory.setItem(RANK_SLOT, GuiItems.labelled(progress.rank().icon(),
                Messages.MENU_RANG_NAME.replace("<rang>", progress.rank().colored()),
                GuiTexts.progressLines(this.plugin, progress)));
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
            case DEPOSIT_SLOT -> {
                BankWindows.click(player);
                BankGui bank = new BankGui(this.plugin);
                bank.prepare(player);
                this.plugin.windows().openLater(player, bank);
            }
            case TOP_SLOT -> {
                BankWindows.click(player);
                this.plugin.windows().openLater(player, new TopListGui(this.plugin, player));
            }
            case ACCOUNT_SLOT -> {
                BankWindows.click(player);
                this.plugin.windows().openLater(player, new AccountGui(this.plugin, player));
            }
            case CLOSE_SLOT -> this.plugin.windows().closeLater(player);
            default -> {
                // Deko: nichts zu tun.
            }
        }
    }

    /** Die Zeilen, die mehrere Fenster gemeinsam benutzen. */
    static final class GuiTexts {

        private GuiTexts() {
        }

        static List<String> accountLines(BankRankingPlugin plugin, Player player,
                                         double balance, RankProgress progress) {
            int rank = plugin.playerData().rank(player.getUniqueId());
            List<String> lines = new ArrayList<>();
            for (String line : Messages.MENU_KOPF_LORE) {
                lines.add(line
                        .replace("<punkte>", Scorer.format(balance))
                        .replace("<platz>", rank == 0 ? Messages.KONTO_ANZEIGE_OHNE_PLATZ : String.valueOf(rank))
                        .replace("<rang>", progress.rank().colored())
                        .replace("<faktor>", Scorer.format(plugin.scorer().wealthFactor(balance) * 100.0)));
            }
            return lines;
        }

        static List<String> progressLines(BankRankingPlugin plugin, RankProgress progress) {
            if (progress.isHighest()) {
                return List.of(Messages.MENU_RANG_MAX_LORE);
            }
            List<String> lines = new ArrayList<>();
            for (String line : Messages.MENU_RANG_LORE) {
                lines.add(line
                        .replace("<naechster>", progress.next().colored())
                        .replace("<ab>", Scorer.format(progress.rank().nextAt()))
                        .replace("<balken>", plugin.settings().progressBar(progress))
                        .replace("<prozent>", String.valueOf(progress.percent()))
                        .replace("<rest>", Scorer.format(progress.remaining())));
            }
            return lines;
        }
    }
}
