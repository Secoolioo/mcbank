package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import io.papermc.paper.scoreboard.numbers.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

/**
 * Die Rangliste am rechten Bildschirmrand (Scoreboard-Sidebar).
 *
 * <p>Jeder Spieler bekommt ein eigenes Scoreboard, weil die Zeilen "Du: Platz ..." und "Tode"
 * persoenlich sind. Der Zeilentext steht im {@code customName} des Eintrags; die Eintragsnamen
 * selbst sind reine Farbcodes, damit sie auch dann nicht stoeren, wenn ein Client den
 * benutzerdefinierten Namen nicht anzeigt.
 */
public final class RankingBoard {

    private static final String OBJECTIVE = "bankranking";
    private static final int TOP_COUNT = 3;
    private static final int MAX_LINES = 15;
    private static final int TICKS_PER_DAY = 24000;

    private final BankRankingPlugin plugin;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    public RankingBoard(BankRankingPlugin plugin) {
        this.plugin = plugin;
    }

    /** Legt das Scoreboard fuer einen Spieler an und zeigt es ihm. */
    public void enable(Player player) {
        if (!this.plugin.settings().sidebarEnabled()) {
            return;
        }
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard board = manager.getNewScoreboard();
        Objective objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                Messages.mm(this.plugin.settings().sidebarTitle()));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.numberFormat(NumberFormat.blank());
        this.boards.put(player.getUniqueId(), board);
        player.setScoreboard(board);
        refresh(player);
    }

    /** Vergisst das Scoreboard eines Spielers (beim Verlassen). */
    public void forget(Player player) {
        this.boards.remove(player.getUniqueId());
    }

    public void refreshAll() {
        if (this.boards.isEmpty()) {
            return;
        }
        // Die Rangliste einmal sortieren und fuer alle Spieler wiederverwenden.
        List<Map.Entry<UUID, PlayerData.Entry>> snapshot = this.plugin.playerData().snapshot();
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player, snapshot);
        }
    }

    public void refresh(Player player) {
        refresh(player, this.plugin.playerData().snapshot());
    }

    private void refresh(Player player, List<Map.Entry<UUID, PlayerData.Entry>> snapshot) {
        Scoreboard board = this.boards.get(player.getUniqueId());
        if (board == null) {
            return;
        }
        Objective objective = board.getObjective(OBJECTIVE);
        if (objective == null) {
            return;
        }
        List<Component> lines = buildLines(player, snapshot);
        for (int index = 0; index < lines.size() && index < MAX_LINES; index++) {
            Score score = objective.getScore(entryKey(index));
            score.customName(lines.get(index));
            score.setScore(MAX_LINES - index);
        }
        for (int index = lines.size(); index < MAX_LINES; index++) {
            board.resetScores(entryKey(index));
        }
    }

    /** Nach einem Reload: Titel neu setzen bzw. Sidebar ein- oder ausschalten. */
    public void reapply() {
        if (!this.plugin.settings().sidebarEnabled()) {
            shutdown();
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = this.boards.get(player.getUniqueId());
            if (board == null) {
                enable(player);
                continue;
            }
            Objective objective = board.getObjective(OBJECTIVE);
            if (objective != null) {
                objective.displayName(Messages.mm(this.plugin.settings().sidebarTitle()));
            }
            refresh(player);
        }
    }

    /**
     * Setzt alle Spieler zurueck auf das normale Scoreboard des Servers - aber nur die, die gerade
     * wirklich unser Board sehen. Hat ein anderes Plugin inzwischen uebernommen, bleibt es dabei.
     */
    public void shutdown() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard own = this.boards.get(player.getUniqueId());
            if (own != null && player.getScoreboard() == own) {
                player.setScoreboard(manager.getMainScoreboard());
            }
        }
        this.boards.clear();
    }

    private List<Component> buildLines(Player player, List<Map.Entry<UUID, PlayerData.Entry>> snapshot) {
        List<Component> lines = new ArrayList<>();
        List<Map.Entry<UUID, PlayerData.Entry>> top = snapshot.subList(0, Math.min(TOP_COUNT, snapshot.size()));
        if (top.isEmpty()) {
            lines.add(Messages.mm(Messages.REICHSTE_LEER));
        } else {
            int place = 1;
            for (Map.Entry<UUID, PlayerData.Entry> entry : top) {
                lines.add(Messages.mm(Messages.SIDEBAR_ZEILE_TOP,
                        Placeholder.unparsed("platz", String.valueOf(place)),
                        Placeholder.unparsed("name", shorten(entry.getValue().name())),
                        Placeholder.unparsed("punkte", Scorer.format(entry.getValue().points()))));
                place++;
            }
        }

        lines.add(Component.empty());

        int rank = 0;
        double ownPoints = 0.0;
        for (int index = 0; index < snapshot.size(); index++) {
            if (snapshot.get(index).getKey().equals(player.getUniqueId())) {
                rank = index + 1;
                ownPoints = snapshot.get(index).getValue().points();
                break;
            }
        }
        if (rank == 0) {
            lines.add(Messages.mm(Messages.SIDEBAR_ZEILE_ICH_LEER));
        } else {
            lines.add(Messages.mm(Messages.SIDEBAR_ZEILE_ICH,
                    Placeholder.unparsed("platz", String.valueOf(rank)),
                    Placeholder.unparsed("punkte", Scorer.format(ownPoints))));
        }
        lines.add(Messages.mm(Messages.SIDEBAR_ZEILE_TODE,
                Placeholder.unparsed("tode", String.valueOf(player.getStatistic(Statistic.DEATHS)))));
        lines.add(Messages.mm(Messages.SIDEBAR_ZEILE_TAG,
                Placeholder.unparsed("tag", String.valueOf(worldDay()))));
        return lines;
    }

    private static long worldDay() {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return 0L;
        }
        return worlds.get(0).getFullTime() / TICKS_PER_DAY;
    }

    private static String shorten(String name) {
        return name.length() <= 16 ? name : name.substring(0, 16);
    }

    /**
     * Eindeutiger, im Spiel unsichtbarer Eintragsname je Zeile (Paragraf-Zeichen plus Farbzeichen).
     * Eintraege duerfen nicht mit '#' beginnen, sonst blendet der Client sie in der Sidebar aus.
     */
    private static String entryKey(int index) {
        return "§" + "0123456789abcdef".charAt(index % 16);
    }
}
