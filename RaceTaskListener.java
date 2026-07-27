package com.exotic.plugin;

import org.bukkit.Material;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerShearEntityEvent;
import org.bukkit.event.raid.RaidStopEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class RaceTaskListener implements Listener {

    private final ExoticPlugin plugin;

    private static final Set<Material> ORE_MATERIALS = Set.of(
            Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE,
            Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.NETHER_QUARTZ_ORE, Material.ANCIENT_DEBRIS
    );

    // Anti-loophole state - see onBlockPlace/onDrop below for why these exist.
    private final Set<String> playerPlacedBlocks = new HashSet<>();
    private final Set<UUID> playerDroppedItemIds = new HashSet<>();

    public RaceTaskListener(ExoticPlugin plugin) {
        this.plugin = plugin;
    }

    private String blockKey(org.bukkit.block.Block block) {
        return block.getWorld().getName() + ":" + block.getX() + "," + block.getY() + "," + block.getZ();
    }

    /** Tracks player-placed blocks so mine-place-remine cheesing doesn't count toward race progress. */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        playerPlacedBlocks.add(blockKey(event.getBlock()));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Material type = event.getBlock().getType();

        // If this block was placed by a player (not naturally generated), don't let it
        // count - closes the "mine it, place it back, mine it again" loophole.
        boolean wasPlayerPlaced = playerPlacedBlocks.remove(blockKey(event.getBlock()));
        if (wasPlayerPlaced) return;

        plugin.raceTasks().progress(player, TrackType.BREAK_BLOCK, type, 1);

        if (ORE_MATERIALS.contains(type)) {
            plugin.raceTasks().progress(player, TrackType.ANY_ORE_MINE, null, 1);
        }
    }

    /** Tracks items a player just dropped so drop-and-repickup cheesing doesn't count toward race progress. */
    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        playerDroppedItemIds.add(event.getItemDrop().getUniqueId());
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // If this exact item entity was just dropped by a player, don't count picking
        // it back up - closes the "drop what I already have, pick it back up" loophole.
        if (playerDroppedItemIds.remove(event.getItem().getUniqueId())) return;

        Material type = event.getItem().getItemStack().getType();
        plugin.raceTasks().progress(player, TrackType.COLLECT_ITEM, type, event.getItem().getItemStack().getAmount());
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;

        plugin.raceTasks().progress(killer, TrackType.KILL_ENTITY, event.getEntityType(), 1);

        if (event.getEntity() instanceof Monster) {
            plugin.raceTasks().progress(killer, TrackType.ANY_HOSTILE_KILL, null, 1);
        }
    }

    @EventHandler
    public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        plugin.raceTasks().progress(event.getPlayer(), TrackType.CATCH_FISH, null, 1);
    }

    @EventHandler
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player player)) return;
        plugin.raceTasks().progress(player, TrackType.TAME_ANIMAL, event.getEntityType(), 1);
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (!(event.getBreeder() instanceof Player player)) return;
        if (!(event.getEntity() instanceof Animals)) return;
        plugin.raceTasks().progress(player, TrackType.BREED_ANIMAL, null, 1);
    }

    @EventHandler
    public void onShear(PlayerShearEntityEvent event) {
        if (event.getEntity().getType() != org.bukkit.entity.EntityType.SHEEP) return;
        plugin.raceTasks().progress(event.getPlayer(), TrackType.SHEAR_SHEEP, null, 1);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getWorld() != event.getTo().getWorld()) return;
        double distance = event.getFrom().distance(event.getTo());
        if (distance < 0.1) return; // ignore tiny look-around jitter
        plugin.raceTasks().progress(event.getPlayer(), TrackType.TRAVEL_DISTANCE, null, (int) Math.round(distance));
    }

    @EventHandler
    public void onRaidStop(RaidStopEvent event) {
        if (event.getRaid().getStatus() != org.bukkit.Raid.RaidStatus.VICTORY) return;
        var heroes = event.getRaid().getHeroes();
        if (heroes.size() != 1) return; // only counts if truly solo
        var player = org.bukkit.Bukkit.getPlayer(heroes.iterator().next());
        if (player != null) {
            plugin.raceTasks().progress(player, TrackType.SOLO_CLEAR_RAID, null, 1);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.raceTasks().onPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.raceTasks().onPlayerQuit(event.getPlayer());
    }
}
