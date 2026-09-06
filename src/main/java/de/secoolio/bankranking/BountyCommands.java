package de.secoolio.bankranking;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * Registriert {@code /kopfgeld}.
 *
 * <p>Der Spielername ist bewusst ein einfaches Wort und kein Spieler-Argument der Paper-API:
 * das loest nur Online-Spieler auf, und ein Kopfgeld auf einen gerade abwesenden Freund waere
 * damit unmoeglich. Aufgeloest wird gegen dieselbe Kandidatenliste, die auch das Fenster zeigt -
 * niemals ueber {@code Bukkit.getOfflinePlayer(String)}, der bei unbekannten Namen bei Mojang
 * nachschlaegt und dabei den Serverpuls blockiert.
 */
public final class BountyCommands {

    private BountyCommands() {
    }

    public static void register(BankRankingPlugin plugin, Commands registrar) {
        SuggestionProvider<CommandSourceStack> namen = (ctx, builder) -> {
            Player sender = ctx.getSource().getExecutor() instanceof Player p ? p : null;
            if (sender != null) {
                for (BountyTargets.Target ziel : BountyGui.candidates(plugin, sender)) {
                    if (ziel.name().toLowerCase(java.util.Locale.ROOT)
                            .startsWith(builder.getRemaining().toLowerCase(java.util.Locale.ROOT))) {
                        builder.suggest(ziel.name());
                    }
                }
            }
            return builder.buildFuture();
        };

        registrar.register(Commands.literal("kopfgeld")
                .requires(source -> source.getSender().hasPermission("bankranking.kopfgeld"))
                .executes(ctx -> open(plugin, ctx))
                .then(Commands.literal("liste").executes(ctx -> {
                    Player player = playerOf(plugin, ctx);
                    if (player != null) {
                        plugin.windows().open(player, new BountyListGui(plugin));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("beute").executes(ctx -> {
                    Player player = playerOf(plugin, ctx);
                    if (player != null) {
                        plugin.windows().open(player, new LootGui(plugin, player));
                    }
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("auf")
                        .then(Commands.argument("spieler", StringArgumentType.word())
                                .suggests(namen)
                                .executes(ctx -> stake(plugin, ctx))))
                .then(Commands.literal("aufheben")
                        .requires(source -> source.getSender()
                                .hasPermission("bankranking.kopfgeld.admin"))
                        .then(Commands.argument("spieler", StringArgumentType.word())
                                .suggests(namen)
                                .executes(ctx -> cancel(plugin, ctx))))
                .build(), "Kopfgeld aussetzen, einsehen und Beute abholen", List.of("bounty"));
    }

    private static int open(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        Player player = playerOf(plugin, ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        if (!plugin.settings().bountyEnabled()) {
            plugin.send(player, Messages.KOPFGELD_AUS);
            return Command.SINGLE_SUCCESS;
        }
        if (plugin.bounties().data().isLocked()) {
            plugin.send(player, Messages.KOPFGELD_GESPERRT);
            return Command.SINGLE_SUCCESS;
        }
        plugin.windows().open(player, new BountyGui(plugin, player, 0));
        return Command.SINGLE_SUCCESS;
    }

    private static int stake(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        Player player = playerOf(plugin, ctx);
        if (player == null) {
            return Command.SINGLE_SUCCESS;
        }
        BountyTargets.Target ziel = resolve(plugin, player,
                StringArgumentType.getString(ctx, "spieler"));
        if (ziel == null) {
            plugin.send(player, Messages.KOPFGELD_UNBEKANNT,
                    Placeholder.unparsed("name", StringArgumentType.getString(ctx, "spieler")));
            return Command.SINGLE_SUCCESS;
        }
        plugin.windows().open(player, new BountyStakeGui(plugin, ziel.id(), ziel.name()));
        return Command.SINGLE_SUCCESS;
    }

    private static int cancel(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        String eingabe = StringArgumentType.getString(ctx, "spieler");
        UUID ziel = null;
        String name = eingabe;
        for (Bounty pot : plugin.bounties().data().active()) {
            if (pot.name().equalsIgnoreCase(eingabe)) {
                ziel = pot.target();
                name = pot.name();
                break;
            }
        }
        if (ziel == null) {
            plugin.send(ctx.getSource().getSender(), Messages.KOPFGELD_UNBEKANNT,
                    Placeholder.unparsed("name", eingabe));
            return Command.SINGLE_SUCCESS;
        }
        Bounty pot = plugin.bounties().data().pot(ziel);
        int einsaetze = pot.stakes().size();
        Map<UUID, String> einzahler = new java.util.LinkedHashMap<>();
        for (Bounty.Stake stake : pot.stakes()) {
            einzahler.put(stake.from(), stake.name());
        }

        if (!plugin.bounties().data().cancel(ziel, System.currentTimeMillis())) {
            plugin.send(ctx.getSource().getSender(), Messages.KOPFGELD_FEHLER);
            return Command.SINGLE_SUCCESS;
        }
        plugin.send(ctx.getSource().getSender(), Messages.KOPFGELD_AUFGEHOBEN,
                Placeholder.unparsed("name", name),
                Placeholder.unparsed("anzahl", String.valueOf(einsaetze)));

        // Wer gerade da ist, erfaehrt es sofort und bekommt seinen Einsatz gleich zugestellt.
        for (Map.Entry<UUID, String> e : einzahler.entrySet()) {
            Player einzahlerSpieler = plugin.getServer().getPlayer(e.getKey());
            if (einzahlerSpieler != null) {
                plugin.send(einzahlerSpieler, Messages.KOPFGELD_ZURUECK,
                        Placeholder.unparsed("name", name));
                plugin.bounties().deliver(einzahlerSpieler);
            }
        }
        plugin.bounties().refreshAll(ziel);
        return Command.SINGLE_SUCCESS;
    }

    /** Sucht einen Namen in derselben Liste, die auch das Fenster zeigt. */
    private static BountyTargets.Target resolve(BankRankingPlugin plugin, Player viewer, String name) {
        for (BountyTargets.Target ziel : BountyGui.candidates(plugin, viewer)) {
            if (ziel.name().equalsIgnoreCase(name)) {
                return ziel;
            }
        }
        return null;
    }

    private static Player playerOf(BankRankingPlugin plugin, CommandContext<CommandSourceStack> ctx) {
        if (ctx.getSource().getExecutor() instanceof Player player) {
            return player;
        }
        plugin.send(ctx.getSource().getSender(), Messages.NUR_SPIELER);
        return null;
    }
}
