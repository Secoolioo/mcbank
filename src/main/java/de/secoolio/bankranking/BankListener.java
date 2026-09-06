package de.secoolio.bankranking;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.GameRules;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

/** Alle Ereignisse: NPC-Bedienung und -Schutz, das Bank-Fenster und die Rangliste. */
public final class BankListener implements Listener {

    private final BankRankingPlugin plugin;
    /** In welchem Tick ein Spieler zuletzt ein Fenster geoeffnet hat (gegen doppelte Zustellung). */
    private final Map<UUID, Integer> lastOpenTick = new HashMap<>();
    /** Ob beim Tod das Inventar erhalten bleibt - gemerkt aus PlayerDeathEvent. */
    private final Map<UUID, Boolean> keepOnDeath = new HashMap<>();

    public BankListener(BankRankingPlugin plugin) {
        this.plugin = plugin;
    }

    // ----- NPC -----

    /**
     * Lauscht bewusst auf die Oberklasse: Paper verschickt fuer einen Rechtsklick auf ein Entity
     * PlayerInteractAtEntityEvent, das sich die Handler-Liste mit PlayerInteractEntityEvent teilt.
     * So werden beide Zustellwege abgedeckt, ohne dass das Fenster doppelt aufgeht. Abgebrochen wird
     * fuer beide Haende, damit keine Vanilla-Aktion am NPC passiert; geoeffnet nur fuer die Haupthand.
     */
    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Integer id = this.plugin.npcs().npcIdOf(event.getRightClicked());
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        if (!player.hasPermission("bankranking.use")) {
            this.plugin.send(player, Messages.KEINE_RECHTE);
            return;
        }
        // Manche Clients schicken fuer einen Rechtsklick zwei Pakete; im selben Tick oeffnen wir nur einmal.
        int tick = this.plugin.getServer().getCurrentTick();
        if (Integer.valueOf(tick).equals(this.lastOpenTick.get(player.getUniqueId()))) {
            return;
        }
        this.lastOpenTick.put(player.getUniqueId(), tick);
        if (event.getRightClicked() instanceof Mannequin mannequin) {
            this.plugin.npcs().anchor(mannequin, id);
        }
        new BankGui(this.plugin).open(player);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (this.plugin.npcs().npcIdOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPortal(EntityPortalEvent event) {
        if (this.plugin.npcs().npcIdOf(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTarget(EntityTargetEvent event) {
        if (event.getTarget() != null && this.plugin.npcs().npcIdOf(event.getTarget()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (this.plugin.npcs().npcIdOf(event.getEntered()) != null) {
            event.setCancelled(true);
        }
    }

    /** Wurde ein NPC doch entfernt (z.B. durch ein anderes Plugin), wird er neu gesetzt. */
    @EventHandler
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getCause() == EntityRemoveEvent.Cause.UNLOAD || this.plugin.npcs().isSelfRemoving()) {
            return;
        }
        Integer id = this.plugin.npcs().npcIdOf(event.getEntity());
        if (id == null || !this.plugin.isEnabled()) {
            return;
        }
        this.plugin.getLogger().warning("Bank-NPC #" + id + " wurde entfernt (" + event.getCause()
                + ") - er wird neu gesetzt");
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> this.plugin.npcs().ensure(id, true));
    }

    // ----- Bank-Fenster -----

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof BankGui gui)) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            event.setCancelled(true);
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < BankGui.SIZE) {
            if (rawSlot == BankGui.CONFIRM_SLOT) {
                // Deckt auch Zahlentasten, Zweithand-Tausch, Fallenlassen und Kreativ-Klonen ab:
                // all diese Klicks melden den Zielplatz als Rohslot.
                event.setCancelled(true);
                ClickType click = event.getClick();
                if (click == ClickType.LEFT || click == ClickType.RIGHT
                        || click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
                    gui.confirm(player);
                }
                return;
            }
            if (!BankGui.isDepositSlot(rawSlot)) {
                // Rahmen, Wertanzeige und Kontostand sind nur Anzeige.
                event.setCancelled(true);
                return;
            }
        }
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR && gui.isButton(event.getCursor())) {
            // Ein Doppelklick saugt passende Items aus beiden Inventaren - auch den Knopf.
            event.setCancelled(true);
            return;
        }
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                && rawSlot >= BankGui.SIZE
                && gui.isButton(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }
        if (touchesDeposit(event, rawSlot)) {
            gui.requestUpdate(player);
        }
    }

    /** Kann dieser Klick ueberhaupt einen Ablageplatz veraendert haben? */
    private static boolean touchesDeposit(InventoryClickEvent event, int rawSlot) {
        InventoryAction action = event.getAction();
        if (action == InventoryAction.NOTHING || rawSlot < 0) {
            return false;
        }
        if (rawSlot < BankGui.SIZE) {
            return true;
        }
        // Klicks im eigenen Inventar erreichen das Bank-Fenster nur ueber diese Aktionen.
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY
                || action == InventoryAction.COLLECT_TO_CURSOR
                || action == InventoryAction.HOTBAR_SWAP
                || action.name().endsWith("_BUNDLE");
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof BankGui gui)) {
            return;
        }
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < BankGui.SIZE && !BankGui.isDepositSlot(rawSlot)) {
                // Ein Zieh-Vorgang laesst sich nur ganz oder gar nicht abbrechen.
                event.setCancelled(true);
                return;
            }
        }
        if (event.getWhoClicked() instanceof Player player) {
            gui.requestUpdate(player);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder(false) instanceof BankGui gui)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        // Beim Tod alles am Todesort fallen lassen - aber nur, wenn das Inventar ueberhaupt geleert
        // wird. Massgeblich ist, was das Todes-Ereignis entschieden hat: ein Gräber-Plugin kann das
        // Behalten auch dann anordnen, wenn die Weltregel es nicht vorsieht.
        boolean death = event.getReason() == InventoryCloseEvent.Reason.DEATH;
        Boolean remembered = this.keepOnDeath.remove(player.getUniqueId());
        boolean keepInventory = remembered != null
                ? remembered
                : Boolean.TRUE.equals(player.getWorld().getGameRuleValue(GameRules.KEEP_INVENTORY));
        gui.refund(player, death && !keepInventory);
    }

    // ----- Rangliste -----

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.plugin.ranking().enable(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.plugin.ranking().forget(event.getPlayer());
        this.lastOpenTick.remove(event.getPlayer().getUniqueId());
        this.keepOnDeath.remove(event.getPlayer().getUniqueId());
    }

    /** Nach dem Laden einer Welt die dort stehenden NPCs wieder sichern. */
    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        String world = event.getWorld().getName();
        for (NpcManager.NpcEntry entry : this.plugin.npcs().entries()) {
            if (entry.world().equals(world)) {
                this.plugin.getServer().getScheduler().runTask(this.plugin,
                        () -> this.plugin.npcs().ensure(entry.id(), false));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        // Laeuft vor dem Schliessen des Fensters: hier steht fest, ob das Inventar erhalten bleibt.
        this.keepOnDeath.put(player.getUniqueId(), event.getKeepInventory());
        // Die Todes-Statistik wird erst nach diesem Ereignis hochgezaehlt.
        this.plugin.getServer().getScheduler().runTask(this.plugin, () -> {
            if (player.isOnline()) {
                this.plugin.ranking().refresh(player);
            }
        });
    }
}
