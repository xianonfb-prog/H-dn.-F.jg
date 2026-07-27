package com.exotic.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Bloodred Mutiny's server event ("The Mutiny"): a 45-minute continuous FFA deathmatch in
 * a dedicated arena. Kills are tracked on a live leaderboard; whoever has the most when the
 * timer ends wins the axe. Ties resolve via a short sudden-death round instead of a coin flip.
 *
 * Ability-disable rule: every Exotic weapon's ABILITY is disabled for participants while this
 * event is active (checked centrally via {@link #abilitiesDisabled(Player)}), but PASSIVES stay
 * fully active - see PassiveListener's onInteract/onSwing dispatch checks.
 */
public class MutinyEventManager {

    public enum Phase { INACTIVE, MAIN, TIE_COUNTDOWN, TIE_SUDDEN_DEATH }

    private static final long MAIN_DURATION_MS = 45 * 60 * 1000L;
    private static final long TIE_COUNTDOWN_TICKS = 200L; // 10 seconds

    private final ExoticPlugin plugin;
    private final Random random = new Random();

    private Phase phase = Phase.INACTIVE;
    private long expiresAt;
    private boolean halfwayAnnounced;
    private boolean tenMinAnnounced;
    private boolean oneMinAnnounced;

    // Everyone currently in the event (main phase participants). Stays populated through
    // TIE_COUNTDOWN/TIE_SUDDEN_DEATH for the non-tied players too, since they're just sent home
    // immediately at the start of tie resolution rather than needing separate tracking.
    private final Set<UUID> participants = new HashSet<>();
    private final Set<UUID> tiedPlayers = new HashSet<>();
    private final Set<UUID> eliminatedThisSuddenDeath = new HashSet<>();

    private final Map<UUID, Integer> kills = new HashMap<>();
    private final Map<UUID, ItemStack[]> inventorySnapshots = new HashMap<>();
    private final Map<UUID, ItemStack[]> armorSnapshots = new HashMap<>();
    private final Map<UUID, Location> originalLocations = new HashMap<>();

    // Blocks placed by participants inside the arena while the event is active, keyed by placer -
    // reset to air the moment that specific player dies (per-death, not per-event-end).
    private final Map<UUID, Set<Location>> placedBlocksByPlayer = new HashMap<>();

    // Live boundary radius - shrinks to MutinyArena.TIDE_RADIUS while a Crimson Tide is active.
    private volatile double currentRadius = MutinyArena.FULL_RADIUS;

    private CrimsonTideTask tideTask;
    private BoneSpikeTask spikeTask;

    public MutinyEventManager(ExoticPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------

    public boolean isActive() {
        return phase != Phase.INACTIVE;
    }

    public Phase phase() {
        return phase;
    }

    public double currentRadius() {
        return currentRadius;
    }

    public void setRadius(double radius) {
        this.currentRadius = radius;
    }

    public boolean isParticipant(UUID id) {
        return participants.contains(id);
    }

    /** Abilities off, passives on, for anyone currently in the event - checked from PassiveListener. */
    public boolean abilitiesDisabled(Player player) {
        return isActive() && participants.contains(player.getUniqueId());
    }

    public boolean startEvent(List<Player> entrants) {
        if (isActive()) return false;
        if (entrants.size() < 2) return false;

        boolean pasted = ArenaStructureLoader.pasteArena(plugin);
        if (!pasted) return false;

        World world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
        if (world == null) return false;

        phase = Phase.MAIN; // marked active immediately so a second start can't race in during the buildup
        // Sentinel far-future value so tick() can't misread a stale/zero expiresAt as "already
        // expired" during the 3s atmospheric buildup below, before beginArenaEntry sets the real
        // expiry. Real bug caught in review: without this, tick() would call endMainPhase() before
        // anyone was even teleported in.
        expiresAt = Long.MAX_VALUE;
        currentRadius = MutinyArena.FULL_RADIUS;
        kills.clear();
        participants.clear();
        tiedPlayers.clear();
        eliminatedThisSuddenDeath.clear();
        inventorySnapshots.clear();
        armorSnapshots.clear();
        originalLocations.clear();
        placedBlocksByPlayer.clear();
        halfwayAnnounced = false;
        tenMinAnnounced = false;
        oneMinAnnounced = false;

        for (Player player : entrants) {
            UUID id = player.getUniqueId();
            participants.add(id);
            kills.put(id, 0);
            placedBlocksByPlayer.put(id, new HashSet<>());
            originalLocations.put(id, player.getLocation().clone());
            snapshotInventory(player);

            player.sendMessage(Component.text("The blade stirs beneath Bloodred Mutiny's hilt.", NamedTextColor.DARK_RED));
            player.sendMessage(Component.text("It has chosen no one yet.", NamedTextColor.RED));
            player.playSound(player.getLocation(), Sound.AMBIENT_CAVE, 1f, 0.6f);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                beginArenaEntry(entrants, world);
            }
        }.runTaskLater(plugin, 60L); // 3s of dread before anyone's actually pulled in

        return true;
    }

    private void beginArenaEntry(List<Player> entrants, World world) {
        expiresAt = System.currentTimeMillis() + MAIN_DURATION_MS;

        for (Player player : entrants) {
            if (!player.isOnline()) continue; // disconnected during the 3s buildup - skip, don't teleport a stale reference
            player.teleport(MutinyArena.randomRespawn(world, random));
            player.sendMessage(Component.text("Blood will be shed. Only one leaves wearing its favor.", NamedTextColor.DARK_RED));
            player.showTitle(Title.title(
                    Component.text("THE MUTINY", NamedTextColor.DARK_RED),
                    Component.text("Most kills in 45 minutes wins the blade.", NamedTextColor.RED)
            ));
        }

        Bukkit.broadcast(Component.text(
                "The Mutiny has begun - " + entrants.size() + " players fight for Bloodred Mutiny.",
                NamedTextColor.DARK_RED));

        spikeTask = new BoneSpikeTask(plugin, this);
        spikeTask.runTaskTimer(plugin, 20L, 20L);
        tideTask = new CrimsonTideTask(plugin, this);
        tideTask.runTaskTimer(plugin, 20L, 20L);
    }

    /** Called every second from ExoticPlugin's scheduler while active. */
    public void tick() {
        if (phase != Phase.MAIN) return;
        long remaining = expiresAt - System.currentTimeMillis();

        if (!halfwayAnnounced && remaining <= MAIN_DURATION_MS / 2) {
            halfwayAnnounced = true;
            announceToParticipants("Half the hour bleeds away.", "The others have already chosen sides.", NamedTextColor.DARK_RED);
        }
        if (!tenMinAnnounced && remaining <= 10 * 60 * 1000L) {
            tenMinAnnounced = true;
            announceToParticipants("TEN MINUTES", "The blade grows impatient.", NamedTextColor.RED);
        }
        if (!oneMinAnnounced && remaining <= 60 * 1000L) {
            oneMinAnnounced = true;
            announceToParticipants("ONE MINUTE", "Blood remembers who spilled it last.", NamedTextColor.DARK_RED);
        }

        if (remaining <= 0) {
            endMainPhase();
        }
    }

    private void announceToParticipants(String main, String sub, NamedTextColor color) {
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            player.showTitle(Title.title(Component.text(main, color), Component.text(sub, NamedTextColor.GRAY)));
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 0.6f, 0.8f);
        }
    }

    private void snapshotInventory(Player player) {
        PlayerInventory inv = player.getInventory();
        inventorySnapshots.put(player.getUniqueId(), inv.getStorageContents().clone());
        armorSnapshots.put(player.getUniqueId(), inv.getArmorContents().clone());
    }

    private void restoreInventory(Player player) {
        ItemStack[] storage = inventorySnapshots.get(player.getUniqueId());
        ItemStack[] armor = armorSnapshots.get(player.getUniqueId());
        PlayerInventory inv = player.getInventory();
        inv.clear();
        if (storage != null) inv.setStorageContents(storage);
        if (armor != null) inv.setArmorContents(armor);
    }

    // ---------------------------------------------------------------
    // Kills / deaths
    // ---------------------------------------------------------------

    public void registerKill(Player killer) {
        if (!participants.contains(killer.getUniqueId())) return;
        kills.merge(killer.getUniqueId(), 1, Integer::sum);
    }

    /** Called on any participant death - resets whatever THEY placed, regardless of event phase. */
    public void onParticipantDeath(Player player) {
        resetPlacedBlocks(player.getUniqueId());

        if (phase == Phase.TIE_SUDDEN_DEATH && tiedPlayers.contains(player.getUniqueId())) {
            eliminatedThisSuddenDeath.add(player.getUniqueId());
            checkSuddenDeathWinner();
        }
    }

    private void resetPlacedBlocks(UUID id) {
        Set<Location> placed = placedBlocksByPlayer.get(id);
        if (placed == null || placed.isEmpty()) return;
        for (Location loc : placed) {
            Block block = loc.getBlock();
            block.setType(org.bukkit.Material.AIR);
        }
        placed.clear();
    }

    public void trackPlacedBlock(Player player, Block block) {
        if (!isActive() || !participants.contains(player.getUniqueId())) return;
        placedBlocksByPlayer.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(block.getLocation());
    }

    public void untrackBrokenBlock(Player player, Block block) {
        Set<Location> placed = placedBlocksByPlayer.get(player.getUniqueId());
        if (placed != null) placed.remove(block.getLocation());
    }

    // ---------------------------------------------------------------
    // Respawn handling (main phase only - sudden death is single-life, see MutinyListener)
    // ---------------------------------------------------------------

    public Location respawnLocation(Player player) {
        World world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
        if (world == null) world = player.getWorld();
        return MutinyArena.randomRespawn(world, random);
    }

    public void giveRespawnInvulnerability(Player player) {
        player.setNoDamageTicks(60); // 3s
    }

    public ItemStack[] storageSnapshot(UUID id) {
        return inventorySnapshots.get(id);
    }

    public ItemStack[] armorSnapshot(UUID id) {
        return armorSnapshots.get(id);
    }

    /** Called from MutinyListener's PlayerRespawnEvent - decides WHERE they respawn. Eliminated
     *  sudden-death players go home instead of back into the arena; everyone else during MAIN
     *  goes to a fresh random arena spawn point. */
    public Location handleRespawn(Player player) {
        UUID id = player.getUniqueId();
        if (phase == Phase.TIE_SUDDEN_DEATH && eliminatedThisSuddenDeath.contains(id)) {
            Location home = originalLocations.get(id);
            return home != null ? home : player.getWorld().getSpawnLocation();
        }
        return respawnLocation(player);
    }

    /** Called one tick after respawn (inventory changes don't reliably stick DURING the respawn
     *  event itself) - restores the right inventory for whichever case handleRespawn resolved. */
    public void postRespawnRestore(Player player) {
        UUID id = player.getUniqueId();
        if (phase == Phase.TIE_SUDDEN_DEATH && eliminatedThisSuddenDeath.contains(id)) {
            restoreInventory(player);
            combatCleanup(player);
            player.sendMessage(Component.text("You were eliminated from the tiebreaker and sent home.", NamedTextColor.GRAY));
            return;
        }
        if (phase == Phase.MAIN && participants.contains(id)) {
            restoreInventory(player);
            giveRespawnInvulnerability(player);
        }
    }

    /** Handles a participant who was OFFLINE when the event concluded - their gear/location would
     *  otherwise be silently lost since sendPlayerHome() can't reach a null Player. Called from
     *  MutinyListener's PlayerJoinEvent on every join to check for a pending return. */
    public void applyPendingReturnIfAny(Player player) {
        PendingReturn pending = pendingReturns.remove(player.getUniqueId());
        if (pending == null) return;
        PlayerInventory inv = player.getInventory();
        inv.clear();
        if (pending.storage != null) inv.setStorageContents(pending.storage);
        if (pending.armor != null) inv.setArmorContents(pending.armor);
        if (pending.home != null) player.teleport(pending.home);
        player.sendMessage(Component.text("Your gear from The Mutiny has been restored.", NamedTextColor.GRAY));
    }

    /** If this player is still an active participant (online or not) when they reconnect mid-event,
     *  called from MutinyListener's PlayerJoinEvent to pull them back into the arena. */
    public void rejoinIfParticipant(Player player) {
        UUID id = player.getUniqueId();
        if (!isActive() || !participants.contains(id)) return;
        if (phase == Phase.MAIN) {
            World world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
            if (world == null) world = player.getWorld();
            player.teleport(MutinyArena.randomRespawn(world, random));
            restoreInventory(player);
            giveRespawnInvulnerability(player);
            player.sendMessage(Component.text("Welcome back to The Mutiny.", NamedTextColor.DARK_RED));
        }
    }

    private static final class PendingReturn {
        final Location home;
        final ItemStack[] storage;
        final ItemStack[] armor;

        PendingReturn(Location home, ItemStack[] storage, ItemStack[] armor) {
            this.home = home;
            this.storage = storage;
            this.armor = armor;
        }
    }

    private final Map<UUID, PendingReturn> pendingReturns = new HashMap<>();

    /** Called from CommandHandler's /exotic event axe1 progress - skips whatever's left of the
     *  45-minute timer and resolves right now, exactly like a natural timer expiry would (winner,
     *  no-winner, or tie -> sudden death). Only valid during MAIN phase. */
    public boolean forceResolveNow() {
        if (phase != Phase.MAIN) return false;
        endMainPhase();
        return true;
    }

    // ---------------------------------------------------------------
    // Main phase end -> leaderboard resolution
    // ---------------------------------------------------------------

    private void endMainPhase() {
        if (tideTask != null) tideTask.cancel();
        if (spikeTask != null) spikeTask.cancel();

        int max = kills.values().stream().max(Integer::compareTo).orElse(0);

        if (max == 0) {
            Bukkit.broadcast(Component.text(
                    "Nobody proved worthy tonight. Bloodred Mutiny sleeps a while longer, unclaimed.",
                    NamedTextColor.GRAY));
            concludeEvent(null);
            return;
        }

        List<UUID> topScorers = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : kills.entrySet()) {
            if (entry.getValue() == max) topScorers.add(entry.getKey());
        }

        if (topScorers.size() == 1) {
            Player winner = Bukkit.getPlayer(topScorers.get(0));
            concludeEvent(winner);
        } else {
            beginTieResolution(topScorers);
        }
    }

    // ---------------------------------------------------------------
    // Tie resolution
    // ---------------------------------------------------------------

    private void beginTieResolution(List<UUID> tied) {
        phase = Phase.TIE_COUNTDOWN;
        tiedPlayers.clear();
        tiedPlayers.addAll(tied);
        eliminatedThisSuddenDeath.clear();

        World world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
        List<String> names = new ArrayList<>();

        // Send everyone NOT tied home immediately - they're done, no reward this run.
        for (UUID id : participants) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            if (!tied.contains(id)) {
                sendPlayerHome(player);
            }
        }

        int i = 0;
        for (UUID id : tied) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            names.add(player.getName());
            Location dest = world != null ? MutinyArena.tiePosition(world, i) : player.getLocation();
            player.teleport(dest);

            // "Absolute stun" - reuses the same stunned-map system Hand Of Zeus uses, so both the
            // real player movement-lock (StunEnforcerTask) and attack-blocking (CombatListener)
            // apply automatically. Blindness on top so they can't even see to pre-aim.
            long stunUntil = System.currentTimeMillis() + (TIE_COUNTDOWN_TICKS * 50L);
            plugin.combat().stunned.put(id, stunUntil);
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, (int) TIE_COUNTDOWN_TICKS + 10, 1, false, false));
            i++;
        }

        Bukkit.broadcast(Component.text(
                "A tie is an insult to the blade. " + String.join(", ", names) + " will settle this the only way it accepts.",
                NamedTextColor.DARK_RED));

        runTieCountdown(tied, (int) (TIE_COUNTDOWN_TICKS / 20));
    }

    private void runTieCountdown(List<UUID> tied, int secondsRemaining) {
        for (UUID id : tied) {
            Player player = Bukkit.getPlayer(id);
            if (player == null) continue;
            if (secondsRemaining > 0) {
                player.showTitle(Title.title(
                        Component.text(String.valueOf(secondsRemaining), NamedTextColor.DARK_RED),
                        Component.text("Sudden death begins...", NamedTextColor.RED)
                ));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
            } else {
                player.showTitle(Title.title(
                        Component.text("FIGHT", NamedTextColor.DARK_RED),
                        Component.text("Last one standing wins.", NamedTextColor.RED)
                ));
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1.2f);
            }
        }

        if (secondsRemaining <= 0) {
            phase = Phase.TIE_SUDDEN_DEATH;
            return;
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                runTieCountdown(tied, secondsRemaining - 1);
            }
        }.runTaskLater(plugin, 20L);
    }

    private void checkSuddenDeathWinner() {
        List<UUID> remaining = new ArrayList<>();
        for (UUID id : tiedPlayers) {
            if (!eliminatedThisSuddenDeath.contains(id)) remaining.add(id);
        }
        if (remaining.size() > 1) return;

        Player winner = remaining.isEmpty() ? null : Bukkit.getPlayer(remaining.get(0));
        concludeEvent(winner);
    }

    // ---------------------------------------------------------------
    // Conclusion
    // ---------------------------------------------------------------

    private void concludeEvent(Player winner) {
        if (winner != null) {
            ItemStack axe = ExoticItem.byId("axe1").build();
            SwordUtil.bindToOwner(axe, winner.getUniqueId());
            winner.getInventory().addItem(axe);
            winner.sendMessage(Component.text("The blade has made its choice. You rise drenched in victory.", NamedTextColor.DARK_RED));
            Bukkit.broadcast(Component.text(
                    winner.getName() + " rises from Bloodred Mutiny drenched in the blood of the fallen.",
                    NamedTextColor.DARK_RED));
        }

        for (UUID id : new HashSet<>(participants)) {
            resetPlacedBlocks(id);
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                sendPlayerHome(player);
            } else {
                // Offline at conclusion - queue their gear/location for whenever they next join,
                // rather than losing it silently (see applyPendingReturnIfAny).
                pendingReturns.put(id, new PendingReturn(
                        originalLocations.get(id), inventorySnapshots.get(id), armorSnapshots.get(id)));
            }
        }

        ArenaStructureLoader.wipeArena(plugin);

        phase = Phase.INACTIVE;
        currentRadius = MutinyArena.FULL_RADIUS;
        participants.clear();
        tiedPlayers.clear();
        eliminatedThisSuddenDeath.clear();
        kills.clear();
        inventorySnapshots.clear();
        armorSnapshots.clear();
        originalLocations.clear();
        placedBlocksByPlayer.clear();
    }

    private void sendPlayerHome(Player player) {
        Location home = originalLocations.get(player.getUniqueId());
        restoreInventory(player);
        combatCleanup(player);
        if (home != null) player.teleport(home);
        player.sendMessage(Component.text("You've been returned from The Mutiny.", NamedTextColor.GRAY));
    }

    private void combatCleanup(Player player) {
        plugin.combat().stunned.remove(player.getUniqueId());
        player.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    /** Used by CommandHandler for an operator-forced early stop (testing phase convenience). */
    public void forceStop() {
        if (!isActive()) return;
        if (tideTask != null) tideTask.cancel();
        if (spikeTask != null) spikeTask.cancel();
        concludeEvent(null);
        Bukkit.broadcast(Component.text("The Mutiny was stopped early by an operator.", NamedTextColor.GRAY));
    }
}
