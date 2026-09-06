package de.secoolio.bankranking;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

/**
 * Verwaltet beliebig viele Bank-NPCs (Mannequins mit Spieler-Skin) und ihre Datei npcs.yml.
 *
 * <p>Jeder NPC hat eine feste Nummer, die als Kennzeichen im Entity gespeichert wird und
 * einen Neustart uebersteht. Die Entity-UUID dient nur dazu, den NPC schneller wiederzufinden.
 */
public final class NpcManager {

    /** Erlaubte Spielernamen fuer den Skin (Vorgabe der Paper-Profil-API). */
    private static final Pattern SKIN_PATTERN = Pattern.compile("^[!-~]{1,16}$");

    /** Hoechstens so viele automatische Respawns je NPC innerhalb des Zeitfensters. */
    private static final int RESPAWN_LIMIT = 3;
    private static final long RESPAWN_WINDOW_MS = 5L * 60L * 1000L;

    public record NpcEntry(int id, String world, double x, double y, double z, float yaw, float pitch,
                           String skin, UUID entityId) {

        NpcEntry withEntity(UUID entityId) {
            return new NpcEntry(this.id, this.world, this.x, this.y, this.z, this.yaw, this.pitch, this.skin, entityId);
        }

        NpcEntry withSkin(String skin) {
            return new NpcEntry(this.id, this.world, this.x, this.y, this.z, this.yaw, this.pitch, skin, this.entityId);
        }

        int blockX() {
            return (int) Math.floor(this.x);
        }

        int blockY() {
            return (int) Math.floor(this.y);
        }

        int blockZ() {
            return (int) Math.floor(this.z);
        }
    }

    private final BankRankingPlugin plugin;
    private final Logger log;
    private final File file;
    private final Map<Integer, NpcEntry> npcs = new TreeMap<>();
    /** NPC-Bloecke, die das Plugin nicht lesen konnte - sie werden unveraendert zurueckgeschrieben. */
    private final Map<String, Map<String, Object>> unreadable = new LinkedHashMap<>();
    private final Map<Integer, int[]> respawns = new HashMap<>();
    private final Map<Integer, Long> respawnWindowStart = new HashMap<>();
    private int nextId = 1;
    private int selfRemoval;
    private boolean loadFailed;

    public NpcManager(BankRankingPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
        this.file = new File(plugin.getDataFolder(), "npcs.yml");
    }

    public static boolean isValidSkinName(String name) {
        return name != null && SKIN_PATTERN.matcher(name).matches();
    }

    public Collection<NpcEntry> entries() {
        return List.copyOf(this.npcs.values());
    }

    public NpcEntry get(int id) {
        return this.npcs.get(id);
    }

    public int count() {
        return this.npcs.size();
    }

    public boolean isSelfRemoving() {
        return this.selfRemoval > 0;
    }

    /** Nummer des Bank-NPCs, zu dem dieses Entity gehoert, oder null. */
    public Integer npcIdOf(Entity entity) {
        if (!(entity instanceof Mannequin)) {
            return null;
        }
        Integer id = entity.getPersistentDataContainer().get(this.plugin.npcKey(), PersistentDataType.INTEGER);
        if (id == null) {
            return null;
        }
        return this.npcs.containsKey(id) ? id : null;
    }

    public void load() {
        this.npcs.clear();
        this.unreadable.clear();
        this.nextId = 1;
        this.loadFailed = false;
        if (!this.file.exists()) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(this.file);
        } catch (IOException | InvalidConfigurationException ex) {
            // Nicht speichern, solange die beschaedigte Datei da ist: sonst wuerde der naechste
            // /spawnrank die letzte reparierbare Fassung ueberschreiben und Nummern neu vergeben.
            this.loadFailed = true;
            this.log.severe("npcs.yml konnte nicht gelesen werden (" + ex.getMessage()
                    + ") - es werden keine NPCs verwaltet und nichts gespeichert, bitte die Datei prüfen");
            return;
        }
        ConfigurationSection section = yaml.getConfigurationSection("npcs");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                int id;
                try {
                    id = Integer.parseInt(key);
                } catch (NumberFormatException ex) {
                    keepUnreadable(section, key, "keine gültige NPC-Nummer");
                    continue;
                }
                ConfigurationSection npc = section.getConfigurationSection(key);
                if (npc == null) {
                    keepUnreadable(section, key, "keinen lesbaren Inhalt");
                    continue;
                }
                String world = npc.getString("world");
                String skin = npc.getString("skin");
                if (world == null || skin == null) {
                    keepUnreadable(section, key, "keine vollständigen Angaben");
                    continue;
                }
                if (!isValidSkinName(skin)) {
                    keepUnreadable(section, key, "einen ungültigen Skin-Namen '" + skin + "'");
                    continue;
                }
                if (!hasNumber(npc, "x") || !hasNumber(npc, "y") || !hasNumber(npc, "z")) {
                    keepUnreadable(section, key, "keine gültigen Koordinaten");
                    continue;
                }
                UUID entityId = null;
                String rawUuid = npc.getString("entity-uuid");
                if (rawUuid != null) {
                    try {
                        entityId = UUID.fromString(rawUuid);
                    } catch (IllegalArgumentException ex) {
                        this.log.warning("npcs.yml: NPC #" + id + " hat eine ungültige entity-uuid - wird ignoriert");
                    }
                }
                this.npcs.put(id, new NpcEntry(id, world,
                        npc.getDouble("x"), npc.getDouble("y"), npc.getDouble("z"),
                        (float) npc.getDouble("yaw"), (float) npc.getDouble("pitch"), skin, entityId));
            }
        }
        this.nextId = Math.max(yaml.getInt("naechste-id", 1),
                this.npcs.keySet().stream().mapToInt(Integer::intValue).max().orElse(0) + 1);
    }

    private static boolean hasNumber(ConfigurationSection section, String key) {
        return section.get(key) instanceof Number;
    }

    /**
     * Merkt sich einen unlesbaren NPC-Block wortgetreu. Ohne das wuerde ein Tippfehler in npcs.yml
     * beim naechsten Speichern den Eintrag loeschen und die Figur als herrenlose Statue zuruecklassen.
     */
    private void keepUnreadable(ConfigurationSection section, String key, String reason) {
        this.unreadable.put(key, YamlSections.copyOf(section.getConfigurationSection(key)));
        this.log.warning("npcs.yml: Eintrag '" + key + "' hat " + reason
                + " - er wird beim Speichern unverändert übernommen, bitte von Hand prüfen");
        try {
            // Die Nummer bleibt belegt, damit ein neuer NPC sie nicht doppelt vergibt.
            this.nextId = Math.max(this.nextId, Integer.parseInt(key) + 1);
        } catch (NumberFormatException ignored) {
            // Kein Zahlenschluessel: dann kann er auch nicht kollidieren.
        }
    }

    public void save() {
        if (this.loadFailed) {
            this.log.severe("npcs.yml wird nicht gespeichert, weil die beschädigte Datei noch vorhanden ist");
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("naechste-id", this.nextId);
        this.unreadable.forEach((key, values) -> YamlSections.restore(yaml, "npcs." + key, values));
        for (NpcEntry entry : this.npcs.values()) {
            String base = "npcs." + entry.id() + ".";
            yaml.set(base + "world", entry.world());
            yaml.set(base + "x", entry.x());
            yaml.set(base + "y", entry.y());
            yaml.set(base + "z", entry.z());
            yaml.set(base + "yaw", (double) entry.yaw());
            yaml.set(base + "pitch", (double) entry.pitch());
            yaml.set(base + "skin", entry.skin());
            yaml.set(base + "entity-uuid", entry.entityId() == null ? null : entry.entityId().toString());
        }
        Path target = this.file.toPath();
        Path temp = target.resolveSibling(this.file.getName() + ".tmp");
        try {
            Path parent = target.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            // Erst vollstaendig danebenschreiben, dann umbenennen: ein Absturz mitten im Speichern
            // kann die vorhandene Datei so nicht halb ueberschreiben.
            Files.writeString(temp, yaml.saveToString(), StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            this.log.severe("npcs.yml konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    /** True, wenn npcs.yml beschaedigt ist und deshalb nichts geschrieben werden darf. */
    public boolean isLocked() {
        return this.loadFailed;
    }

    /** Setzt einen neuen NPC an die Position des Admins. Gibt null zurueck, wenn das Spawnen scheitert. */
    public NpcEntry place(Player admin, String skin) {
        if (this.loadFailed) {
            return null;
        }
        Location location = admin.getLocation().clone();
        location.setPitch(0.0f);
        location.setYaw(location.getYaw() + 180.0f);
        World world = location.getWorld();
        int id = this.nextId;
        NpcEntry entry = new NpcEntry(id, world.getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(), skin, null);

        Mannequin mannequin = spawn(entry, location);
        if (mannequin == null) {
            return null;
        }
        entry = entry.withEntity(mannequin.getUniqueId());
        this.npcs.put(id, entry);
        this.nextId = id + 1;
        world.addPluginChunkTicket(location.getBlockX() >> 4, location.getBlockZ() >> 4, this.plugin);
        save();
        this.log.info("Bank-NPC #" + id + " gesetzt von " + admin.getName() + " bei " + describe(entry)
                + " (Skin: " + skin + ")");
        return entry;
    }

    /** Ergebnis von {@link #remove(int)}. */
    public enum RemoveResult {
        ENTFERNT,
        UNBEKANNT,
        WELT_FEHLT
    }

    public RemoveResult remove(int id) {
        NpcEntry entry = this.npcs.get(id);
        if (entry == null) {
            return RemoveResult.UNBEKANNT;
        }
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            // Datensatz behalten: sonst bliebe die Figur unerreichbar in der entladenen Welt stehen.
            this.log.warning("Bank-NPC #" + id + " konnte nicht entfernt werden - die Welt '" + entry.world()
                    + "' ist nicht geladen");
            return RemoveResult.WELT_FEHLT;
        }
        this.npcs.remove(id);
        this.selfRemoval++;
        try {
            // Auch ausserhalb des gespeicherten Chunks suchen, falls die Figur verschoben wurde.
            if (entry.entityId() != null && Bukkit.getEntity(entry.entityId()) instanceof Mannequin moved) {
                moved.remove();
            }
            Chunk chunk = world.getChunkAt(entry.blockX() >> 4, entry.blockZ() >> 4);
            chunk.load();
            for (Entity entity : chunk.getEntities()) {
                Integer tag = rawTag(entity);
                if (tag != null && tag == id) {
                    entity.remove();
                }
            }
            if (!hasOtherNpcInChunk(entry.world(), chunk.getX(), chunk.getZ())) {
                world.removePluginChunkTicket(chunk.getX(), chunk.getZ(), this.plugin);
            }
        } finally {
            this.selfRemoval--;
        }
        this.respawns.remove(id);
        this.respawnWindowStart.remove(id);
        save();
        this.log.info("Bank-NPC #" + id + " entfernt");
        return RemoveResult.ENTFERNT;
    }

    public boolean setSkin(int id, String skin) {
        NpcEntry entry = this.npcs.get(id);
        if (entry == null) {
            return false;
        }
        NpcEntry updated = entry.withSkin(skin);
        this.npcs.put(id, updated);
        save();
        Mannequin mannequin = findLoaded(updated);
        if (mannequin != null) {
            harden(mannequin, updated);
        }
        return true;
    }

    /** Sorgt dafuer, dass alle NPCs vorhanden, geschuetzt und an ihrem Platz sind. */
    public void ensureAll() {
        if (this.npcs.isEmpty()) {
            this.log.info("Kein Bank-NPC gesetzt. Mit /spawnrank einen platzieren.");
            return;
        }
        for (NpcEntry entry : entries()) {
            ensure(entry.id(), false);
        }
    }

    public void ensure(int id, boolean afterRemoval) {
        NpcEntry entry = this.npcs.get(id);
        if (entry == null) {
            return;
        }
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            this.log.warning("Welt '" + entry.world() + "' von Bank-NPC #" + id
                    + " ist nicht geladen - NPC wird übersprungen");
            return;
        }
        int chunkX = entry.blockX() >> 4;
        int chunkZ = entry.blockZ() >> 4;
        world.addPluginChunkTicket(chunkX, chunkZ, this.plugin);
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        chunk.load();

        Location location = location(entry, world);

        // Zuerst die gespeicherte Figur ueberall suchen: wurde sie verschoben (oder steht sie in einem
        // anderen Chunk), wird sie zurueckgeholt statt ein zweites Mal gespawnt.
        if (entry.entityId() != null && Bukkit.getEntity(entry.entityId()) instanceof Mannequin known
                && rawTagMatches(known, id)) {
            harden(known, entry);
            if (!known.getWorld().equals(world) || known.getLocation().distanceSquared(location) > 1.0) {
                known.teleport(location);
            }
            return;
        }

        Mannequin found = null;
        List<Entity> duplicates = new ArrayList<>();
        for (Entity entity : chunk.getEntities()) {
            Integer tag = rawTag(entity);
            if (tag == null || tag != id || !(entity instanceof Mannequin mannequin)) {
                continue;
            }
            if (found == null || (entry.entityId() != null && entry.entityId().equals(mannequin.getUniqueId()))) {
                if (found != null) {
                    duplicates.add(found);
                }
                found = mannequin;
            } else {
                duplicates.add(entity);
            }
        }
        if (!duplicates.isEmpty()) {
            this.selfRemoval++;
            try {
                duplicates.forEach(Entity::remove);
            } finally {
                this.selfRemoval--;
            }
            this.log.warning(duplicates.size() + " doppelte(r) Bank-NPC #" + id + " entfernt");
        }

        if (found != null) {
            harden(found, entry);
            if (found.getLocation().distanceSquared(location) > 1.0) {
                found.teleport(location);
            }
            if (entry.entityId() == null || !entry.entityId().equals(found.getUniqueId())) {
                this.npcs.put(id, entry.withEntity(found.getUniqueId()));
                save();
            }
            return;
        }

        if (afterRemoval && !budgetAllows(id)) {
            this.log.warning("Bank-NPC #" + id + " wurde zu oft entfernt - kein automatischer Respawn mehr."
                    + " Mit /bankranking removenpc " + id + " aufräumen oder den Server neu starten.");
            return;
        }
        Mannequin spawned = spawn(entry, location);
        if (spawned == null) {
            return;
        }
        this.npcs.put(id, entry.withEntity(spawned.getUniqueId()));
        save();
        this.log.info("Bank-NPC #" + id + " neu gespawnt bei " + describe(entry));
    }

    /** Holt einen abgedrifteten NPC an seine gespeicherte Position zurueck. */
    public void anchor(Mannequin mannequin, int id) {
        NpcEntry entry = this.npcs.get(id);
        if (entry == null) {
            return;
        }
        World world = mannequin.getWorld();
        if (!world.getName().equals(entry.world())) {
            return;
        }
        Location location = location(entry, world);
        if (mannequin.getLocation().distanceSquared(location) > 1.0) {
            mannequin.teleport(location);
        }
    }

    private boolean budgetAllows(int id) {
        long now = System.currentTimeMillis();
        long start = this.respawnWindowStart.getOrDefault(id, 0L);
        if (now - start > RESPAWN_WINDOW_MS) {
            this.respawnWindowStart.put(id, now);
            this.respawns.put(id, new int[]{0});
        }
        int[] counter = this.respawns.computeIfAbsent(id, key -> new int[]{0});
        if (counter[0] >= RESPAWN_LIMIT) {
            return false;
        }
        counter[0]++;
        return true;
    }

    private Mannequin spawn(NpcEntry entry, Location location) {
        try {
            Mannequin mannequin = location.getWorld().spawn(location, Mannequin.class, m -> harden(m, entry));
            if (!mannequin.isValid()) {
                this.log.severe("Bank-NPC #" + entry.id() + " konnte nicht gespawnt werden"
                        + " - ein anderes Plugin hat das Spawnen verhindert");
                return null;
            }
            return mannequin;
        } catch (RuntimeException ex) {
            this.log.severe("Bank-NPC #" + entry.id() + " konnte nicht gespawnt werden: " + ex);
            return null;
        }
    }

    private void harden(Mannequin mannequin, NpcEntry entry) {
        mannequin.setProfile(ResolvableProfile.resolvableProfile().name(entry.skin()).build());
        mannequin.setImmovable(true);
        mannequin.setInvulnerable(true);
        mannequin.setGravity(false);
        mannequin.setSilent(true);
        mannequin.setPersistent(true);
        mannequin.setCollidable(false);
        mannequin.setCanPickupItems(false);
        mannequin.setRemoveWhenFarAway(false);
        mannequin.customName(Messages.mm(this.plugin.settings().npcName()));
        mannequin.setCustomNameVisible(true);
        String description = this.plugin.settings().npcDescription();
        mannequin.setDescription(description == null || description.isBlank() ? null : Messages.mm(description));
        mannequin.getPersistentDataContainer().set(this.plugin.npcKey(), PersistentDataType.INTEGER, entry.id());
    }

    /** Wendet Namen, Beschreibung und Skin nach einem /bankranking reload erneut an. */
    public void applySettings() {
        for (NpcEntry entry : entries()) {
            Mannequin mannequin = findLoaded(entry);
            if (mannequin != null) {
                harden(mannequin, entry);
            }
        }
    }

    private Mannequin findLoaded(NpcEntry entry) {
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            return null;
        }
        if (entry.entityId() != null && Bukkit.getEntity(entry.entityId()) instanceof Mannequin mannequin
                && rawTagMatches(mannequin, entry.id())) {
            return mannequin;
        }
        int chunkX = entry.blockX() >> 4;
        int chunkZ = entry.blockZ() >> 4;
        // Erst fragen, dann holen: getChunkAt wuerde den Chunk laden und notfalls erzeugen, und
        // das mitten in einem Anzeige-Befehl auf dem Hauptthread.
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return null;
        }
        Chunk chunk = world.getChunkAt(chunkX, chunkZ);
        for (Entity entity : chunk.getEntities()) {
            Integer tag = rawTag(entity);
            if (tag != null && tag == entry.id() && entity instanceof Mannequin mannequin) {
                return mannequin;
            }
        }
        return null;
    }

    public boolean isLoaded(NpcEntry entry) {
        return findLoaded(entry) != null;
    }

    private boolean rawTagMatches(Entity entity, int id) {
        Integer tag = rawTag(entity);
        return tag != null && tag == id;
    }

    private Integer rawTag(Entity entity) {
        return entity.getPersistentDataContainer().get(this.plugin.npcKey(), PersistentDataType.INTEGER);
    }

    private boolean hasOtherNpcInChunk(String world, int chunkX, int chunkZ) {
        for (NpcEntry other : this.npcs.values()) {
            if (other.world().equals(world) && (other.blockX() >> 4) == chunkX && (other.blockZ() >> 4) == chunkZ) {
                return true;
            }
        }
        return false;
    }

    private static Location location(NpcEntry entry, World world) {
        return new Location(world, entry.x(), entry.y(), entry.z(), entry.yaw(), entry.pitch());
    }

    public static String describe(NpcEntry entry) {
        return entry.world() + " " + entry.blockX() + "/" + entry.blockY() + "/" + entry.blockZ();
    }
}
