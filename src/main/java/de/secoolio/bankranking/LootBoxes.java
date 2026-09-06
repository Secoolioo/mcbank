package de.secoolio.bankranking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

/**
 * Die schwebende Belohnungskiste vor dem Killer.
 *
 * <p><strong>Kein echter Kistenblock.</strong> Das war die naheliegende Loesung und ist die
 * falsche: ein Block in der Welt macht den Weltzustand zum Eigentumsregister, und der ist das
 * eine, was das Plugin nicht kontrolliert. Eine Kiste neben einer fremden Kiste wird zur
 * Doppelkiste und legt die Beute offen; ein zurueckgespieltes Backup oder ein zurueckgesetzter
 * Chunk nimmt sie samt Inhalt mit, ohne dass ein Ereignis davon erzaehlt.
 *
 * <p>Deshalb liegen die Gegenstaende ausschliesslich in {@code kopfgelder.yml}, und was hier in
 * der Welt steht, ist reine Anzeige: eine schwebende Kiste, die sich anklicken laesst.
 * Verschwindet sie, ist nichts verloren - beim naechsten Serverstart steht sie wieder da, und
 * {@code /kopfgeld beute} funktioniert ohnehin von ueberall.
 */
public final class LootBoxes implements Listener {

    /** Wie weit vor dem Spieler die Kiste erscheint. */
    private static final double ABSTAND = 1.2;

    private final BankRankingPlugin plugin;
    private final NamespacedKey ownerKey;
    /** Je Besitzer die beiden Entitaeten: Anzeige und Klickflaeche. */
    private final Map<UUID, List<UUID>> gesetzt = new HashMap<>();

    public LootBoxes(BankRankingPlugin plugin) {
        this.plugin = plugin;
        this.ownerKey = new NamespacedKey(plugin, "loot_owner");
    }

    /**
     * Setzt die Kiste vor einen Spieler und merkt sich die Stelle.
     *
     * <p>Es wird kein Block geprueft und keiner ersetzt: Anzeige-Entitaeten haben keine
     * Kollision und duerfen in einem Block stehen. Genau das macht die Loesung so anspruchslos.
     */
    public void spawn(Player owner) {
        BountyData.Claim beute = this.plugin.bounties().data().claim(owner.getUniqueId());
        if (beute == null || beute.items().isEmpty()) {
            return;
        }
        Location vorne = owner.getLocation().clone();
        Vector richtung = vorne.getDirection().setY(0);
        if (richtung.lengthSquared() > 0.0) {
            vorne.add(richtung.normalize().multiply(ABSTAND));
        }
        vorne.add(0.0, 1.0, 0.0);

        remove(owner.getUniqueId());
        this.plugin.bounties().data().setClaimLocation(owner.getUniqueId(),
                vorne.getWorld().getName(), vorne.getX(), vorne.getY(), vorne.getZ());
        place(owner.getUniqueId(), vorne);

        this.plugin.effects().chestSpawned(vorne);
    }

    /** Stellt die beiden Entitaeten an eine Stelle. */
    private void place(UUID owner, Location ort) {
        World welt = ort.getWorld();
        if (welt == null || !welt.isChunkLoaded(ort.getBlockX() >> 4, ort.getBlockZ() >> 4)) {
            return;
        }
        List<UUID> ids = new ArrayList<>(2);

        ItemDisplay anzeige = welt.spawn(ort, ItemDisplay.class, e -> {
            e.setItemStack(ItemStack.of(Material.CHEST));
            e.setBillboard(Display.Billboard.FIXED);
            e.setViewRange(0.8f);
            // Auch nachts gut sichtbar: sonst findet der Killer seine Beute nicht wieder.
            e.setBrightness(new Display.Brightness(15, 15));
            mark(e, owner);
        });
        ids.add(anzeige.getUniqueId());

        Interaction klick = welt.spawn(ort.clone().add(0.0, -0.4, 0.0), Interaction.class, e -> {
            e.setInteractionWidth(1.0f);
            e.setInteractionHeight(1.0f);
            e.setResponsive(true);
            mark(e, owner);
        });
        ids.add(klick.getUniqueId());

        this.gesetzt.put(owner, ids);
    }

    private void mark(Entity entity, UUID owner) {
        entity.setPersistent(false);
        entity.setInvulnerable(true);
        entity.setGravity(false);
        entity.setSilent(true);
        entity.getPersistentDataContainer().set(this.ownerKey, PersistentDataType.STRING,
                owner.toString());
    }

    /** Nimmt die Kiste eines Spielers weg. */
    public void remove(UUID owner) {
        List<UUID> ids = this.gesetzt.remove(owner);
        if (ids == null) {
            return;
        }
        for (UUID id : ids) {
            Entity entity = this.plugin.getServer().getEntity(id);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    /** Die Kiste loest sich auf, weil die Beute abgeholt ist. */
    public void dissolve(UUID owner, Location ort) {
        remove(owner);
        if (ort != null && ort.getWorld() != null) {
            this.plugin.effects().chestGone(ort);
        }
    }

    /**
     * Stellt alle Kisten wieder auf, deren Chunk geladen ist.
     *
     * <p>Die Entitaeten ueberleben nichts - sie sind ausdruecklich nicht persistent. Dadurch
     * kann es keine verwaisten Kisten geben: was steht, stammt immer aus der Datei.
     */
    public void restoreAll() {
        for (BountyData.Claim beute : this.plugin.bounties().data().claims()) {
            if (beute.hasLocation() && !beute.items().isEmpty()) {
                Location ort = location(beute);
                if (ort != null) {
                    place(beute.owner(), ort);
                }
            }
        }
    }

    private Location location(BountyData.Claim beute) {
        World welt = this.plugin.getServer().getWorld(beute.world());
        return welt == null ? null : new Location(welt, beute.x(), beute.y(), beute.z());
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (BountyData.Claim beute : this.plugin.bounties().data().claims()) {
            if (!beute.hasLocation() || beute.items().isEmpty()
                    || this.gesetzt.containsKey(beute.owner())) {
                continue;
            }
            Location ort = location(beute);
            if (ort != null && ort.getWorld().equals(event.getWorld())
                    && (ort.getBlockX() >> 4) == event.getChunk().getX()
                    && (ort.getBlockZ() >> 4) == event.getChunk().getZ()) {
                place(beute.owner(), ort);
            }
        }
    }

    /**
     * Vergisst eine Kiste, deren Chunk entladen wurde.
     *
     * <p>Ohne das bliebe der Besitzer in der Merkliste stehen, und beim naechsten Laden des
     * Chunks haette {@link #onChunkLoad} ihn uebersprungen - die Kiste kaeme fuer den Rest
     * der Serverlaufzeit nie wieder.
     */
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        for (BountyData.Claim beute : this.plugin.bounties().data().claims()) {
            if (!beute.hasLocation() || !this.gesetzt.containsKey(beute.owner())) {
                continue;
            }
            Location ort = location(beute);
            if (ort != null && ort.getWorld().equals(event.getWorld())
                    && (ort.getBlockX() >> 4) == event.getChunk().getX()
                    && (ort.getBlockZ() >> 4) == event.getChunk().getZ()) {
                this.gesetzt.remove(beute.owner());
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        String besitzer = event.getRightClicked().getPersistentDataContainer()
                .get(this.ownerKey, PersistentDataType.STRING);
        if (besitzer == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!besitzer.equals(player.getUniqueId().toString())) {
            this.plugin.send(player, Messages.BEUTE_FREMD);
            this.plugin.effects().deny(player);
            return;
        }
        this.plugin.windows().open(player, new LootGui(this.plugin, player));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        // Schlagen, Pfeile, Explosionen: die Anzeige ist unantastbar. Der eigentliche Schutz
        // liegt ohnehin darin, dass hier keine Gegenstaende stecken.
        if (event.getEntity().getPersistentDataContainer().has(this.ownerKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
        }
    }

    /** Beim Herunterfahren: nichts stehen lassen, was ein Neuladen ueberdauert. */
    public void shutdown() {
        for (UUID owner : new ArrayList<>(this.gesetzt.keySet())) {
            remove(owner);
        }
    }
}
