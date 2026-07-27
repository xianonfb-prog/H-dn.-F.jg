package com.exotic.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class MutinyListener implements Listener {

    private final ExoticPlugin plugin;
    private final MutinyEventManager mutiny;

    public MutinyListener(ExoticPlugin plugin, MutinyEventManager mutiny) {
        this.plugin = plugin;
        this.mutiny = mutiny;
    }

    // ---------------------------------------------------------------
    // Deaths - kill tracking, no drops, no XP, block reset
    // ---------------------------------------------------------------

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getPlayer();
        if (!mutiny.isParticipant(victim.getUniqueId())) return;

        // No items drop during the event - snapshot restore on respawn handles gear instead.
        event.getDrops().clear();
        event.setDroppedExp(0);

        Player killer = victim.getKiller();
        if (killer != null && mutiny.phase() == MutinyEventManager.Phase.MAIN) {
            mutiny.registerKill(killer);
        }

        // Resets whatever THIS player placed in the arena - per-death, not per-event-end.
        mutiny.onParticipantDeath(victim);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!mutiny.isParticipant(player.getUniqueId())) return;

        Location dest = mutiny.handleRespawn(player);
        event.setRespawnLocation(dest);

        // Inventory changes don't reliably stick DURING the respawn event itself - apply next tick.
        Bukkit.getScheduler().runTask(plugin, () -> mutiny.postRespawnRestore(player));
    }

    // ---------------------------------------------------------------
    // Block placement - tracked so it can be reset on that player's death
    // ---------------------------------------------------------------

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        if (!mutiny.isParticipant(player.getUniqueId())) return;
        mutiny.trackPlacedBlock(player, event.getBlock());
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!mutiny.isParticipant(player.getUniqueId())) return;
        mutiny.untrackBrokenBlock(player, event.getBlock());
    }

    // ---------------------------------------------------------------
    // Boundary enforcement - hard wall at the full arena radius, always, so nobody can just
    // walk to the edge and sit out the fight risk-free. (The Crimson Tide's inner shrink is a
    // separate, softer damage zone handled by CrimsonTideTask itself.)
    // ---------------------------------------------------------------

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!mutiny.isActive() || !mutiny.isParticipant(player.getUniqueId())) return;
        if (event.getTo() == null) return;

        var world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
        if (world == null || !event.getTo().getWorld().equals(world)) return;

        if (!MutinyArena.withinRadius(event.getTo(), MutinyArena.center(world), MutinyArena.FULL_RADIUS)) {
            event.setTo(event.getFrom());
        }
    }

    // ---------------------------------------------------------------
    // Hunger lock - 45 minutes of continuous fighting shouldn't be handicapped by starvation.
    // ---------------------------------------------------------------

    @EventHandler
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!mutiny.isParticipant(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    // ---------------------------------------------------------------
    // Disconnect handling - rejoin mid-event pulls them back in; joining after the event
    // concluded while they were offline applies whatever gear/location was queued for them.
    // ---------------------------------------------------------------

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Intentionally a no-op: kill count and snapshots are keyed by UUID (not online session),
        // so they already survive a disconnect with no extra bookkeeping needed here.
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        mutiny.applyPendingReturnIfAny(player);
        mutiny.rejoinIfParticipant(player);
    }
}
