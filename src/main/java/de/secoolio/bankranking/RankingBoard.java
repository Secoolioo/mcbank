package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.scoreboard.Team;

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
    private boolean warnedAboutTakeover;
    /** Spieler, fuer die das Anlegen scheiterte - fuer sie wird es nicht jede Sekunde erneut versucht. */
    private final Set<UUID> failed = new HashSet<>();

    public RankingBoard(BankRankingPlugin plugin) {
        this.plugin = plugin;
    }

    /** Legt das Scoreboard fuer einen Spieler an und zeigt es ihm. */
    public void enable(Player player) {
        enable(player, null);
    }

    /**
     * Legt das Scoreboard fuer einen Spieler an.
     *
     * @param snapshot bereits sortierte Rangliste, falls der Aufrufer sie ohnehin schon hat
     */
    public void enable(Player player, List<Map.Entry<UUID, PlayerData.Entry>> snapshot) {
        if (!this.plugin.settings().sidebarEnabled()) {
            return;
        }
        try {
            ScoreboardManager manager = Bukkit.getScoreboardManager();
            Scoreboard board = manager.getNewScoreboard();
            Objective objective = board.registerNewObjective(OBJECTIVE, Criteria.DUMMY,
                    Messages.mm(this.plugin.settings().sidebarTitle()));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            objective.numberFormat(NumberFormat.blank());
            // Fuer jede Zeile ein Team anlegen. Der Eintrag selbst ist unsichtbar (nur Formatcodes),
            // der sichtbare Text steht im Praefix des Teams. Bewusst nur diese eine Technik:
            // wuerde zusaetzlich Score#customName gesetzt, erschiene jede Zeile doppelt.
            for (int index = 0; index < MAX_LINES; index++) {
                Team team = board.registerNewTeam("line" + index);
                team.addEntry(entryKey(index));
            }
            this.boards.put(player.getUniqueId(), board);
            player.setScoreboard(board);
            refresh(player, snapshot == null ? this.plugin.playerData().snapshot() : snapshot);
        } catch (RuntimeException ex) {
            // Nie den Login blockieren, aber den Grund sichtbar machen - und es nicht endlos wiederholen.
            this.boards.remove(player.getUniqueId());
            this.failed.add(player.getUniqueId());
            this.plugin.getLogger().severe("Rangliste konnte für " + player.getName()
                    + " nicht angezeigt werden: " + ex
                    + " - erneuter Versuch nach /bankranking reload oder /bankranking sidebar");
        }
    }

    /** Vergisst das Scoreboard eines Spielers (beim Verlassen). */
    public void forget(Player player) {
        this.boards.remove(player.getUniqueId());
        this.failed.remove(player.getUniqueId());
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
        try {
            Objective objective = board.getObjective(OBJECTIVE);
            if (objective == null) {
                return;
            }
            List<Component> lines = buildLines(player, snapshot);
            for (int index = 0; index < lines.size() && index < MAX_LINES; index++) {
                // Der Zahlenwert legt die Reihenfolge fest (absteigend von oben nach unten),
                // der sichtbare Text steht im Team-Praefix.
                objective.getScore(entryKey(index)).setScore(MAX_LINES - index);
                Team team = board.getTeam("line" + index);
                if (team != null) {
                    team.prefix(lines.get(index));
                }
            }
            for (int index = lines.size(); index < MAX_LINES; index++) {
                board.resetScores(entryKey(index));
            }
            // Hat ein anderes Plugin dem Spieler inzwischen ein eigenes Scoreboard gegeben,
            // wird unseres wieder gezeigt. Ein Server sieht immer nur ein Scoreboard je Spieler.
            if (player.getScoreboard() != board) {
                player.setScoreboard(board);
                if (!this.warnedAboutTakeover) {
                    this.warnedAboutTakeover = true;
                    this.plugin.getLogger().warning("Ein anderes Plugin setzt ebenfalls ein Scoreboard."
                            + " Die Rangliste wird deshalb regelmäßig neu gesetzt."
                            + " Wenn das stört: in der config.yml sidebar.aktiv auf false setzen.");
                }
            }
        } catch (RuntimeException ex) {
            this.plugin.getLogger().severe("Rangliste konnte für " + player.getName()
                    + " nicht aktualisiert werden: " + ex);
        }
    }

    /**
     * Sorgt dafuer, dass jeder Spieler die Rangliste auch wirklich sieht. Setzt ein anderes Plugin
     * dem Spieler ein eigenes Scoreboard, wird unseres hier zurueckgeholt; wer noch gar keines hat
     * (etwa weil er beim Start des Plugins schon online war), bekommt es jetzt.
     */
    public void guard() {
        if (!this.plugin.settings().sidebarEnabled()) {
            return;
        }
        List<Map.Entry<UUID, PlayerData.Entry>> snapshot = null;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (this.failed.contains(player.getUniqueId())) {
                continue;
            }
            Scoreboard own = this.boards.get(player.getUniqueId());
            if (own == null) {
                if (snapshot == null) {
                    snapshot = this.plugin.playerData().snapshot();
                }
                enable(player, snapshot);
                continue;
            }
            if (player.getScoreboard() != own) {
                player.setScoreboard(own);
                if (!this.warnedAboutTakeover) {
                    this.warnedAboutTakeover = true;
                    this.plugin.getLogger().warning("Ein anderes Plugin setzt ebenfalls ein Scoreboard."
                            + " Die Rangliste wird deshalb laufend neu gesetzt."
                            + " Wenn das stört: in der config.yml sidebar.aktiv auf false setzen.");
                }
            }
        }
    }

    /** Zeigt an, ob fuer diesen Spieler ein Ranglisten-Board angelegt ist. */
    public boolean hasBoard(Player player) {
        return this.boards.containsKey(player.getUniqueId());
    }

    /** Zeigt an, ob der Spieler gerade wirklich unsere Rangliste sieht. */
    public boolean isShowing(Player player) {
        Scoreboard board = this.boards.get(player.getUniqueId());
        return board != null && player.getScoreboard() == board;
    }

    /** Baut die Rangliste fuer einen Spieler neu auf, egal was vorher war. */
    public boolean reset(Player player) {
        this.boards.remove(player.getUniqueId());
        this.failed.remove(player.getUniqueId());
        enable(player, null);
        return this.boards.containsKey(player.getUniqueId());
    }

    /** Nach einem Reload: Titel neu setzen bzw. Sidebar ein- oder ausschalten. */
    public void reapply() {
        this.failed.clear();
        if (!this.plugin.settings().sidebarEnabled()) {
            shutdown();
            return;
        }
        // Die Rangliste einmal sortieren und fuer alle Spieler wiederverwenden.
        List<Map.Entry<UUID, PlayerData.Entry>> snapshot = this.plugin.playerData().snapshot();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Scoreboard board = this.boards.get(player.getUniqueId());
            if (board == null) {
                enable(player, snapshot);
                continue;
            }
            try {
                Objective objective = board.getObjective(OBJECTIVE);
                if (objective != null) {
                    objective.displayName(Messages.mm(this.plugin.settings().sidebarTitle()));
                }
                refresh(player, snapshot);
            } catch (RuntimeException ex) {
                // Ein einzelner Fehler darf den ganzen Reload nicht abbrechen.
                this.boards.remove(player.getUniqueId());
                this.failed.add(player.getUniqueId());
                this.plugin.getLogger().severe("Rangliste für " + player.getName()
                        + " konnte nicht neu aufgebaut werden: " + ex);
            }
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
        Rank stufe = Rank.of(ownPoints);
        lines.add(Messages.mm(Messages.SIDEBAR_ZEILE_RANG
                        .replace("<rang>", stufe.colored()),
                Placeholder.unparsed("faktor",
                        String.valueOf(Math.round(this.plugin.scorer().wealthFactor(ownPoints) * 100.0)))));
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
     * Eindeutiger, im Spiel unsichtbarer Eintragsname je Zeile: zwei Farbcodes, die der Client als
     * Formatierung liest und damit als leeren Text darstellt. Eintraege duerfen nicht mit '#'
     * beginnen, sonst blendet der Client sie in der Sidebar grundsaetzlich aus.
     */
    private static String entryKey(int index) {
        char code = "0123456789abcdef".charAt(index % 16);
        return "§" + code + "§r";
    }
}
