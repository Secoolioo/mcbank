package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bukkit.Material;

/**
 * Die Kennzahlen eines Spielers fuer die Kontoseite.
 *
 * <p>Unveraenderlich: jede Einzahlung erzeugt einen neuen Datensatz. Dadurch laesst sich der alte
 * Stand bei einem Speicherfehler einfach wieder einsetzen.
 *
 * @param deposits  Anzahl der Einzahlungen
 * @param items     wie viele Gegenstaende insgesamt abgegeben wurden
 * @param biggest   die groesste Einzahlung, oder {@code null}
 * @param materials Stueckzahl je Material
 * @param recent    die letzten Einzahlungen, neueste zuerst
 */
public record PlayerStats(int deposits, long items, double totalPoints, Deposit biggest,
                          Map<Material, Long> materials, List<Deposit> recent) {

    /** So viele Einzahlungen merkt sich die Kontoseite. */
    public static final int RECENT_LIMIT = 5;

    public static final PlayerStats EMPTY =
            new PlayerStats(0, 0L, 0.0, null, Map.of(), List.of());

    /**
     * Eine einzelne Einzahlung.
     *
     * @param time   Zeitpunkt in Millisekunden
     * @param points gutgeschriebene Punkte
     * @param items  Anzahl der Gegenstaende
     * @param top    das Material, von dem am meisten dabei war
     */
    public record Deposit(long time, double points, int items, Material top) {
    }

    public PlayerStats {
        materials = Map.copyOf(materials);
        recent = List.copyOf(recent);
    }

    /** Schreibt eine Einzahlung fort und liefert den neuen Stand. */
    public PlayerStats record(Deposit deposit, Map<Material, Integer> itemsPerMaterial) {
        Map<Material, Long> merged = new LinkedHashMap<>(this.materials);
        itemsPerMaterial.forEach((material, count) -> merged.merge(material, count.longValue(), Long::sum));

        List<Deposit> newest = new ArrayList<>(this.recent.size() + 1);
        newest.add(deposit);
        for (Deposit older : this.recent) {
            if (newest.size() >= RECENT_LIMIT) {
                break;
            }
            newest.add(older);
        }

        Deposit best = this.biggest == null || deposit.points() > this.biggest.points()
                ? deposit
                : this.biggest;
        return new PlayerStats(this.deposits + 1, this.items + deposit.items(),
                Scorer.round1(this.totalPoints + deposit.points()), best, merged, newest);
    }

    /** Das Material, von dem dieser Spieler am meisten abgegeben hat. */
    public Optional<Map.Entry<Material, Long>> favourite() {
        return this.materials.entrySet().stream()
                .max(Comparator.<Map.Entry<Material, Long>>comparingLong(Map.Entry::getValue)
                        // Bei Gleichstand entscheidet der Name, damit die Anzeige nicht springt.
                        .thenComparing(entry -> entry.getKey().name(), Comparator.reverseOrder()));
    }

    /** Durchschnittliche Punkte je Einzahlung, ueber alle je getaetigten. */
    public double averagePoints() {
        return this.deposits <= 0 ? 0.0 : this.totalPoints / this.deposits;
    }

    public boolean isEmpty() {
        return this.deposits == 0 && this.items == 0L && this.biggest == null && this.materials.isEmpty();
    }

    /** Schreibt die Kennzahlen in einen Abschnitt der players.yml. */
    void write(org.bukkit.configuration.ConfigurationSection section) {
        section.set("einzahlungen", this.deposits);
        section.set("items", this.items);
        section.set("punkte-gesamt", this.totalPoints);
        if (this.biggest != null) {
            writeDeposit(section.createSection("groesste"), this.biggest);
        }
        if (!this.materials.isEmpty()) {
            org.bukkit.configuration.ConfigurationSection block = section.createSection("materialien");
            this.materials.forEach((material, count) ->
                    block.set(material.name().toLowerCase(java.util.Locale.ROOT), count));
        }
        if (!this.recent.isEmpty()) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Deposit deposit : this.recent) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("zeit", deposit.time());
                map.put("punkte", deposit.points());
                map.put("items", deposit.items());
                map.put("material", deposit.top() == null
                        ? null : deposit.top().name().toLowerCase(java.util.Locale.ROOT));
                list.add(map);
            }
            section.set("letzte", list);
        }
    }

    private static void writeDeposit(org.bukkit.configuration.ConfigurationSection target, Deposit deposit) {
        target.set("zeit", deposit.time());
        target.set("punkte", deposit.points());
        target.set("items", deposit.items());
        if (deposit.top() != null) {
            target.set("material", deposit.top().name().toLowerCase(java.util.Locale.ROOT));
        }
    }

    /**
     * Liest die Kennzahlen. Ein unbrauchbares Einzelfeld faellt auf seinen Standard zurueck; der
     * Spieler geht dadurch nie verloren.
     */
    static PlayerStats read(org.bukkit.configuration.ConfigurationSection section, long now) {
        if (section == null) {
            return EMPTY;
        }
        int deposits = Math.max(0, section.getInt("einzahlungen", 0));
        long items = Math.max(0L, section.getLong("items", 0L));
        double totalPoints = readPoints(section.getDouble("punkte-gesamt", 0.0));
        Deposit biggest = readDeposit(section.getConfigurationSection("groesste"), now);

        Map<Material, Long> materials = new LinkedHashMap<>();
        org.bukkit.configuration.ConfigurationSection block = section.getConfigurationSection("materialien");
        if (block != null) {
            for (String key : block.getKeys(false)) {
                Material material = Material.matchMaterial(key);
                long count = block.getLong(key, 0L);
                if (material != null && !material.isLegacy() && count > 0L) {
                    materials.put(material, count);
                }
            }
        }

        List<Deposit> recent = new ArrayList<>();
        for (Map<?, ?> raw : section.getMapList("letzte")) {
            Deposit deposit = readDeposit(raw, now);
            if (deposit != null && recent.size() < RECENT_LIMIT) {
                recent.add(deposit);
            }
        }
        return new PlayerStats(deposits, items, totalPoints, biggest, materials, recent);
    }

    private static double readPoints(double value) {
        return Double.isFinite(value) && value >= 0.0 ? value : 0.0;
    }

    private static Deposit readDeposit(org.bukkit.configuration.ConfigurationSection section, long now) {
        if (section == null) {
            return null;
        }
        return build(section.getLong("zeit", 0L), section.getDouble("punkte", 0.0),
                section.getInt("items", 0), section.getString("material"), now);
    }

    private static Deposit readDeposit(Map<?, ?> raw, long now) {
        Object time = raw.get("zeit");
        Object points = raw.get("punkte");
        Object items = raw.get("items");
        Object material = raw.get("material");
        return build(time instanceof Number n ? n.longValue() : 0L,
                points instanceof Number n ? n.doubleValue() : 0.0,
                items instanceof Number n ? n.intValue() : 0,
                material instanceof String name ? name : null, now);
    }

    private static Deposit build(long time, double points, int items, String materialName, long now) {
        Material material = materialName == null ? null : Material.matchMaterial(materialName);
        if (material != null && material.isLegacy()) {
            material = null;
        }
        return new Deposit(Math.min(Math.max(0L, time), now), readPoints(points), Math.max(0, items), material);
    }
}
