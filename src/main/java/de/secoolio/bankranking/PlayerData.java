package de.secoolio.bankranking;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Das Punktekonto aller Spieler in plugins/BankRanking/players.yml.
 *
 * <p>Nur vom Hauptthread benutzen. Jede Einzahlung wird sofort gespeichert; geschrieben wird
 * ueber eine temporaere Datei mit anschliessendem atomaren Verschieben, damit ein Absturz
 * mitten im Schreiben die vorhandene Datei nie beschaedigt.
 */
public final class PlayerData {

    public record Entry(String name, double points) {
    }

    /** Ergebnis einer Einzahlung: ob gespeichert werden konnte und der neue Kontostand. */
    public record AddResult(boolean saved, double total) {
    }

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final File file;
    private final Logger log;
    private final Map<UUID, Entry> entries = new TreeMap<>();
    private boolean dirty;
    private boolean loadFailed;

    public PlayerData(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    /**
     * Laedt beim Start. Eine unlesbare Datei wird zur Seite gelegt und der Punktestand startet leer;
     * schlaegt schon das Zurseitelegen fehl, verweigert das Plugin spaeter jedes Speichern, damit die
     * noch rettbare Datei nicht ueberschrieben wird.
     */
    public void load() {
        this.entries.clear();
        this.dirty = false;
        this.loadFailed = false;
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(this.file);
        } catch (IOException | InvalidConfigurationException ex) {
            quarantine(ex);
            return;
        }
        readInto(yaml, this.entries);
    }

    /**
     * Laedt im laufenden Betrieb neu (z.B. /bankranking reload). Anders als beim Start wird nichts
     * zur Seite gelegt und der Speicherstand bleibt erhalten, wenn die Datei unlesbar ist.
     *
     * @return true, wenn neu geladen wurde
     */
    public boolean reload() {
        if (this.dirty) {
            save();
        }
        if (this.dirty) {
            this.log.warning("players.yml wurde NICHT neu geladen - es gibt noch ungespeicherte Punkte im Speicher");
            return false;
        }
        if (!this.file.exists()) {
            this.entries.clear();
            return true;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(this.file);
        } catch (IOException | InvalidConfigurationException ex) {
            this.log.severe("players.yml ist beschädigt (" + ex.getMessage()
                    + ") - die Punkte im Speicher bleiben unverändert");
            return false;
        }
        Map<UUID, Entry> fresh = new TreeMap<>();
        readInto(yaml, fresh);
        this.entries.clear();
        this.entries.putAll(fresh);
        this.loadFailed = false;
        return true;
    }

    private void readInto(YamlConfiguration yaml, Map<UUID, Entry> target) {
        ConfigurationSection section = yaml.getConfigurationSection("spieler");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            UUID id;
            try {
                id = UUID.fromString(key);
            } catch (IllegalArgumentException ex) {
                this.log.warning("players.yml: '" + key + "' ist keine gültige Spieler-ID - übersprungen");
                continue;
            }
            double points = section.getDouble(key + ".punkte", Double.NaN);
            if (!Double.isFinite(points) || points < 0.0) {
                this.log.warning("players.yml: Eintrag '" + key + "' hat keine gültige Punktzahl - übersprungen");
                continue;
            }
            String name = section.getString(key + ".name", "Unbekannt");
            target.put(id, new Entry(name, points));
        }
    }

    private void quarantine(Exception cause) {
        Path source = this.file.toPath();
        Path target = source.resolveSibling(this.file.getName() + ".corrupt-" + LocalDateTime.now().format(STAMP));
        try {
            Files.move(source, target);
            this.log.severe("players.yml ist beschädigt (" + cause.getMessage() + ") und wurde nach "
                    + target.getFileName() + " verschoben - die Punkte starten leer");
        } catch (IOException ex) {
            this.loadFailed = true;
            this.log.severe("players.yml ist beschädigt (" + cause.getMessage() + ") und konnte NICHT zur Seite"
                    + " gelegt werden (" + ex.getMessage() + ") - es wird nichts gespeichert, bitte die Datei"
                    + " von Hand prüfen");
        }
    }

    /** Bucht Punkte auf ein Konto und speichert sofort. Bei Speicherfehler wird zurueckgebucht. */
    public AddResult add(UUID id, String name, double delta) {
        Entry previous = this.entries.get(id);
        double total = Scorer.round1((previous == null ? 0.0 : previous.points()) + delta);
        this.entries.put(id, new Entry(name, total));
        this.dirty = true;
        if (save()) {
            return new AddResult(true, total);
        }
        if (previous == null) {
            this.entries.remove(id);
        } else {
            this.entries.put(id, previous);
        }
        return new AddResult(false, previous == null ? 0.0 : previous.points());
    }

    public double get(UUID id) {
        Entry entry = this.entries.get(id);
        return entry == null ? 0.0 : entry.points();
    }

    /** Platz in der Rangliste, 1-basiert; 0 wenn der Spieler noch keine Punkte hat. */
    public int rank(UUID id) {
        if (!this.entries.containsKey(id)) {
            return 0;
        }
        int place = 1;
        for (Map.Entry<UUID, Entry> entry : sorted()) {
            if (entry.getKey().equals(id)) {
                return place;
            }
            place++;
        }
        return 0;
    }

    public List<Map.Entry<UUID, Entry>> top(int count) {
        List<Map.Entry<UUID, Entry>> all = sorted();
        return all.subList(0, Math.min(count, all.size()));
    }

    /** Einmalige, sortierte Abschrift aller Konten - fuer Aufrufer, die mehrfach darauf zugreifen. */
    public List<Map.Entry<UUID, Entry>> snapshot() {
        return sorted();
    }

    private List<Map.Entry<UUID, Entry>> sorted() {
        List<Map.Entry<UUID, Entry>> all = new ArrayList<>(this.entries.entrySet());
        all.sort(Comparator
                .comparingDouble((Map.Entry<UUID, Entry> e) -> e.getValue().points()).reversed()
                .thenComparing(e -> e.getValue().name(), String.CASE_INSENSITIVE_ORDER));
        return all;
    }

    public int size() {
        return this.entries.size();
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void saveIfDirty() {
        if (this.dirty) {
            save();
        }
    }

    /** Schreibt players.yml atomar. Gibt false zurueck, wenn nichts geschrieben werden konnte. */
    public boolean save() {
        if (this.loadFailed) {
            this.log.severe("players.yml wird nicht gespeichert, weil die beschädigte Datei noch vorhanden ist");
            return false;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, Entry> entry : this.entries.entrySet()) {
            yaml.set("spieler." + entry.getKey() + ".name", entry.getValue().name());
            yaml.set("spieler." + entry.getKey() + ".punkte", entry.getValue().points());
        }
        Path target = this.file.toPath();
        Path temp = target.resolveSibling(this.file.getName() + ".tmp");
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            this.dirty = false;
            return true;
        } catch (IOException ex) {
            this.log.severe("players.yml konnte nicht gespeichert werden: " + ex.getMessage()
                    + " - die Punkte bleiben im Speicher, nächster Versuch bei der nächsten Einzahlung");
            return false;
        }
    }
}
