package de.secoolio.bankranking;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Registriert /spawnrank, /kontostand, /reichste und /bankranking ueber die Paper-Befehls-API. */
public final class BankCommands {

    private static final int TOP_COUNT = 10;

    private BankCommands() {
    }

    public static void register(BankRankingPlugin plugin, Commands registrar) {
        registrar.register(Commands.literal("spawnrank")
                .requires(source -> source.getSender().hasPermission("bankranking.admin"))
                .executes(ctx -> spawn(plugin, ctx, null))
                .then(Commands.argument("skin", StringArgumentType.word())
                        .executes(ctx -> spawn(plugin, ctx, StringArgumentType.getString(ctx, "skin"))))
                .build(), "Setzt einen Bank-NPC an deine Position", List.of("spawnRank"));

        registrar.register(Commands.literal("kontostand")
                .requires(source -> source.getSender().hasPermission("bankranking.kontostand"))
                .executes(ctx -> {
                    Player player = playerOf(plugin, ctx);
                    if (player == null) {
                        return Command.SINGLE_SUCCESS;
                    }
                    double points = plugin.playerData().get(player.getUniqueId());
                    int rank = plugin.playerData().rank(player.getUniqueId());
                    String suffix = rank == 0 ? "" : Messages.KONTOSTAND_PLATZ.replace("<platz>", String.valueOf(rank));
                    plugin.send(player, Messages.KONTOSTAND.replace("<platz>", suffix),
                            Placeholder.unparsed("punkte", Scorer.format(points)));
                    Rank stufe = Rank.of(points);
                    long faktor = Math.round(plugin.scorer().wealthFactor(points) * 100.0);
                    player.sendMessage(Messages.mm(Messages.KONTOSTAND_RANG
                            .replace("<rang>", stufe.colored())
                            .replace("<faktor>", String.valueOf(faktor))
                            .replace("<naechster>", stufe.nextAt() == 0
                                    ? Messages.KONTOSTAND_HOECHSTER
                                    : Scorer.format(stufe.nextAt()))));
                    return Command.SINGLE_SUCCESS;
                })
                .build(), "Zeigt deinen Punktestand");

        registrar.register(Commands.literal("reichste")
                .requires(source -> source.getSender().hasPermission("bankranking.reichste"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    List<Map.Entry<UUID, PlayerData.Entry>> top = plugin.playerData().top(TOP_COUNT);
                    if (top.isEmpty()) {
                        plugin.send(sender, Messages.REICHSTE_LEER);
                        return Command.SINGLE_SUCCESS;
                    }
                    plugin.send(sender, Messages.REICHSTE_KOPF);
                    int place = 1;
                    for (Map.Entry<UUID, PlayerData.Entry> entry : top) {
                        sender.sendMessage(Messages.mm(Messages.REICHSTE_ZEILE,
                                Placeholder.unparsed("platz", String.valueOf(place)),
                                Placeholder.unparsed("name", entry.getValue().name()),
                                Placeholder.unparsed("punkte", Scorer.format(entry.getValue().points()))));
                        place++;
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .build(), "Zeigt die reichsten Spieler");

        registrar.register(Commands.literal("bankranking")
                .requires(source -> source.getSender().hasPermission("bankranking.admin"))
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    for (String line : Messages.ADMIN_HILFE) {
                        sender.sendMessage(Messages.mm(line));
                    }
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("list").executes(ctx -> list(plugin, ctx)))
                .then(Commands.literal("removenpc")
                        .then(Commands.argument("nr", IntegerArgumentType.integer(1))
                                .executes(ctx -> removeNpc(plugin, ctx))))
                .then(Commands.literal("skin")
                        .then(Commands.argument("nr", IntegerArgumentType.integer(1))
                                .then(Commands.argument("spieler", StringArgumentType.word())
                                        .executes(ctx -> setSkin(plugin, ctx)))))
                .then(Commands.literal("reload").executes(ctx -> {
                    boolean ok = plugin.reload();
                    plugin.send(ctx.getSource().getSender(), ok ? Messages.RELOAD_OK : Messages.RELOAD_TEILWEISE);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("wert").executes(ctx -> value(plugin, ctx)))
                .then(Commands.literal("sidebar").executes(ctx -> sidebar(plugin, ctx)))
                .then(Commands.literal("pack").executes(ctx -> pack(plugin, ctx)))
                .build(), "Verwaltung des Bank-Plugins");
    }

    /**
     * Zeigt, ob das Resourcepack ankommt.
     *
     * <p>Ohne diesen Befehl merkt ein Betreiber nie, dass die Spieler still die Sparfassung
     * bekommen: der Selbsttest des Plugins geht nur an sich selbst und sagt nichts darueber,
     * ob ein Mitspieler durch die Firewall kommt.
     */
    private static int pack(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        ResourcePacks packs = plugin.packs();
        if (packs == null) {
            plugin.send(sender, Messages.PACK_AUS);
            return Command.SINGLE_SUCCESS;
        }
        plugin.send(sender, Messages.PACK_ADRESSE,
                Placeholder.unparsed("adresse", packs.url()));
        plugin.send(sender, Messages.PACK_HASH,
                Placeholder.unparsed("hash", packs.sha1()));
        if (!plugin.bounties().hasPoster()) {
            // Ohne Schriftmasse gibt es kein Plakat, nur die Sparfassung - das darf nicht
            // still bleiben, sonst sucht der Betreiber den Fehler beim Pack.
            plugin.send(sender, Messages.PACK_KEIN_PLAKAT);
        }
        Map<String, String> zustand = packs.status();
        if (zustand.isEmpty()) {
            plugin.send(sender, Messages.PACK_NIEMAND);
            return Command.SINGLE_SUCCESS;
        }
        zustand.forEach((name, text) -> sender.sendMessage(Messages.mm(Messages.PACK_SPIELER,
                Placeholder.unparsed("name", name), Placeholder.unparsed("zustand", text))));
        return Command.SINGLE_SUCCESS;
    }

    private static int spawn(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx, String skin) {
        Player player = playerOf(plugin, ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        String name = skin == null ? player.getName() : skin;
        if (!NpcManager.isValidSkinName(name)) {
            plugin.send(player, Messages.NPC_SKIN_UNGUELTIG, Placeholder.unparsed("skin", name));
            return Command.SINGLE_SUCCESS;
        }
        if (plugin.npcs().isLocked()) {
            plugin.send(player, Messages.NPC_GESPERRT);
            return Command.SINGLE_SUCCESS;
        }
        NpcManager.NpcEntry entry = plugin.npcs().place(player, name);
        if (entry == null) {
            plugin.send(player, Messages.NPC_SPAWN_FEHLER);
            return Command.SINGLE_SUCCESS;
        }
        plugin.send(player, Messages.NPC_GESETZT,
                Placeholder.unparsed("nr", String.valueOf(entry.id())),
                Placeholder.unparsed("pos", NpcManager.describe(entry)),
                Placeholder.unparsed("skin", name));
        return Command.SINGLE_SUCCESS;
    }

    private static int list(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        if (plugin.npcs().count() == 0) {
            plugin.send(sender, Messages.NPC_LISTE_LEER);
            return Command.SINGLE_SUCCESS;
        }
        plugin.send(sender, Messages.NPC_LISTE_KOPF);
        for (NpcManager.NpcEntry entry : plugin.npcs().entries()) {
            String status = plugin.npcs().isLoaded(entry) ? Messages.NPC_STATUS_DA : Messages.NPC_STATUS_FEHLT;
            sender.sendMessage(Messages.mm(Messages.NPC_LISTE_ZEILE.replace("<status>", status),
                    Placeholder.unparsed("nr", String.valueOf(entry.id())),
                    Placeholder.unparsed("pos", NpcManager.describe(entry)),
                    Placeholder.unparsed("skin", entry.skin())));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int removeNpc(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        int id = IntegerArgumentType.getInteger(ctx, "nr");
        String number = String.valueOf(id);
        switch (plugin.npcs().remove(id)) {
            case ENTFERNT -> plugin.send(sender, Messages.NPC_ENTFERNT, Placeholder.unparsed("nr", number));
            case WELT_FEHLT -> plugin.send(sender, Messages.NPC_WELT_FEHLT, Placeholder.unparsed("nr", number));
            case UNBEKANNT -> plugin.send(sender, Messages.NPC_UNBEKANNT, Placeholder.unparsed("nr", number));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static int setSkin(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        int id = IntegerArgumentType.getInteger(ctx, "nr");
        String skin = StringArgumentType.getString(ctx, "spieler");
        if (!NpcManager.isValidSkinName(skin)) {
            plugin.send(sender, Messages.NPC_SKIN_UNGUELTIG, Placeholder.unparsed("skin", skin));
            return Command.SINGLE_SUCCESS;
        }
        if (!plugin.npcs().setSkin(id, skin)) {
            plugin.send(sender, Messages.NPC_UNBEKANNT, Placeholder.unparsed("nr", String.valueOf(id)));
            return Command.SINGLE_SUCCESS;
        }
        plugin.send(sender, Messages.NPC_SKIN_GEAENDERT,
                Placeholder.unparsed("nr", String.valueOf(id)),
                Placeholder.unparsed("skin", skin));
        return Command.SINGLE_SUCCESS;
    }

    /** Zeigt den Zustand der Rangliste und baut sie neu auf. */
    private static int sidebar(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        Player player = playerOf(plugin, ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        boolean enabled = plugin.settings().sidebarEnabled();
        plugin.send(player, Messages.SIDEBAR_KOPF);
        player.sendMessage(Messages.mm(Messages.SIDEBAR_STATUS_CONFIG
                .replace("<wert>", enabled ? Messages.SIDEBAR_JA : Messages.SIDEBAR_NEIN)));
        player.sendMessage(Messages.mm(Messages.SIDEBAR_STATUS_BOARD
                .replace("<wert>", plugin.ranking().hasBoard(player) ? Messages.SIDEBAR_JA : Messages.SIDEBAR_NEIN)));
        player.sendMessage(Messages.mm(Messages.SIDEBAR_STATUS_SICHTBAR
                .replace("<wert>", plugin.ranking().isShowing(player) ? Messages.SIDEBAR_JA : Messages.SIDEBAR_NEIN)));
        if (!enabled) {
            plugin.send(player, Messages.SIDEBAR_AUS);
            return Command.SINGLE_SUCCESS;
        }
        plugin.send(player, plugin.ranking().reset(player) ? Messages.SIDEBAR_NEU : Messages.SIDEBAR_FEHLER);
        return Command.SINGLE_SUCCESS;
    }

    private static int value(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        Player player = playerOf(plugin, ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.isEmpty()) {
            plugin.send(player, Messages.WERT_LEER);
            return Command.SINGLE_SUCCESS;
        }
        // Gefuellte Behaelter zuerst auspacken, sonst wuerde nur die leere Huelle bewertet.
        Scorer.Unpacked unpacked = Scorer.unpack(java.util.List.of(stack));
        // Ein Pfad fuer Einzel-Item und Behaelter: beide Male zaehlt, was der Spieler wirklich bekaeme.
        double balance = plugin.playerData().get(player.getUniqueId());
        Scorer.Deposit deposit = plugin.scorer().deposit(Scorer.factsOf(unpacked.valuables()),
                plugin.saturationView(player), balance);

        if (unpacked.valuables().size() != 1 || !unpacked.emptiedContainers().isEmpty()) {
            plugin.send(player, Messages.WERT_KOPF,
                    Placeholder.unparsed("material", stack.getType().name()),
                    Placeholder.unparsed("anzahl", String.valueOf(stack.getAmount())));
            player.sendMessage(Messages.mm(Messages.WERT_BEHAELTER,
                    Placeholder.unparsed("stapel", String.valueOf(deposit.valuations().size()))));
            player.sendMessage(Messages.mm(Messages.WERT_GESAMT,
                    Placeholder.unparsed("punkte", Scorer.format(deposit.rawTotal()))));
            player.sendMessage(Messages.mm(Messages.WERT_ENDWERT,
                    Placeholder.unparsed("punkte", Scorer.format(deposit.total()))));
            return Command.SINGLE_SUCCESS;
        }

        Scorer.Valuation valuation = deposit.valuations().get(0);
        plugin.send(player, Messages.WERT_KOPF,
                Placeholder.unparsed("material", valuation.facts().material().name()),
                Placeholder.unparsed("anzahl", String.valueOf(valuation.facts().amount())));
        player.sendMessage(Messages.mm(Messages.WERT_GRUNDWERT,
                Placeholder.unparsed("basis", Scorer.format(valuation.base()))));
        player.sendMessage(Messages.mm(Messages.WERT_SELTENHEIT,
                Placeholder.unparsed("seltenheit", valuation.facts().rarity().name()),
                Placeholder.unparsed("faktor", Scorer.format(valuation.rarityFactor()))));
        player.sendMessage(Messages.mm(Messages.WERT_KATEGORIE,
                Placeholder.unparsed("kategorie", valuation.category().displayName()),
                Placeholder.unparsed("faktor", Scorer.format(valuation.multiplier()))));
        player.sendMessage(Messages.mm(Messages.WERT_VERZAUBERUNG,
                Placeholder.unparsed("stufen", String.valueOf(valuation.facts().enchantLevelSum())),
                Placeholder.unparsed("bonus", Scorer.format(valuation.enchantBonus()))));
        player.sendMessage(Messages.mm(Messages.WERT_SUMME,
                Placeholder.unparsed("basis", Scorer.format(valuation.base())),
                Placeholder.unparsed("anzahl", String.valueOf(valuation.facts().amount())),
                Placeholder.unparsed("faktor", Scorer.format(valuation.multiplier())),
                Placeholder.unparsed("seltenheit", Scorer.format(valuation.rarityFactor())),
                Placeholder.unparsed("bonus", Scorer.format(valuation.enchantBonus())),
                Placeholder.unparsed("punkte", Scorer.format(valuation.rawPoints()))));
        player.sendMessage(Messages.mm(Messages.WERT_SAETTIGUNG,
                Placeholder.unparsed("faktor", String.valueOf(Math.round(valuation.saturationFactor() * 100.0)))));
        player.sendMessage(Messages.mm(Messages.WERT_WOHLSTAND
                        .replace("<rang>", Rank.of(balance).colored()),
                Placeholder.unparsed("faktor", String.valueOf(Math.round(deposit.wealthFactor() * 100.0)))));
        player.sendMessage(Messages.mm(Messages.WERT_ENDWERT,
                Placeholder.unparsed("punkte", Scorer.format(deposit.total()))));
        return Command.SINGLE_SUCCESS;
    }

    private static Player playerOf(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        plugin.send(ctx.getSource().getSender(), Messages.NUR_SPIELER);
        return null;
    }
}
