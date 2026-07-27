package com.exotic.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Constant background hazard for Bloodred Mutiny's arena: random floor points telegraph
 * briefly, then spike for modest damage. Runs the whole 45-minute event, independent of
 * Crimson Tide - paused only while a Tide is actively shrinking/holding, so the two hazards
 * don't stack damage on top of each other in an already-tightened space.
 *
 * PERFORMANCE NOTE: locations are pre-computed ONCE at construction into a fixed pool, rather
 * than scanning live blocks every tick - this is the optimization flagged earlier as the actual
 * lag risk for this feature. Purely particle/damage-based, no real block placement, so there's
 * nothing to clean up afterward either.
 */
public class BoneSpikeTask extends BukkitRunnable {

    private static final Particle.DustOptions BONE_DUST = new Particle.DustOptions(Color.fromRGB(230, 220, 200), 1.0f);

    private static final int POOL_SIZE = 24;
    private static final int SPIKES_PER_CYCLE = 3;
    private static final int CYCLE_INTERVAL_SECONDS = 4;
    private static final int TELEGRAPH_SECONDS = 1;
    private static final double SPIKE_DAMAGE = 2.0; // 1 heart - modest, not absurd
    private static final double HIT_RADIUS = 1.4;

    private final ExoticPlugin plugin;
    private final MutinyEventManager mutiny;
    private final Random random = new Random();
    private final List<double[]> pool = new ArrayList<>();

    private int secondsSinceLastCycle = 0;

    public BoneSpikeTask(ExoticPlugin plugin, MutinyEventManager mutiny) {
        this.plugin = plugin;
        this.mutiny = mutiny;
        buildPool();
    }

    private void buildPool() {
        for (int i = 0; i < POOL_SIZE; i++) {
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = random.nextDouble() * (MutinyArena.FULL_RADIUS - 3); // stay off the outer wall
            double x = MutinyArena.CENTER_X + dist * Math.cos(angle);
            double z = MutinyArena.CENTER_Z + dist * Math.sin(angle);
            pool.add(new double[]{ x, MutinyArena.CENTER_Y, z });
        }
    }

    @Override
    public void run() {
        if (mutiny.phase() != MutinyEventManager.Phase.MAIN) {
            cancel();
            return;
        }

        // Paused during an active Crimson Tide so the two hazards never stack.
        if (isTideActive()) return;

        secondsSinceLastCycle++;
        if (secondsSinceLastCycle < CYCLE_INTERVAL_SECONDS) return;
        secondsSinceLastCycle = 0;

        runCycle();
    }

    private boolean isTideActive() {
        return mutiny.currentRadius() < MutinyArena.FULL_RADIUS;
    }

    private void runCycle() {
        World world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
        if (world == null) return;

        List<double[]> chosen = new ArrayList<>();
        List<double[]> shuffled = new ArrayList<>(pool);
        java.util.Collections.shuffle(shuffled, random);
        for (int i = 0; i < Math.min(SPIKES_PER_CYCLE, shuffled.size()); i++) {
            chosen.add(shuffled.get(i));
        }

        for (double[] point : chosen) {
            Location loc = new Location(world, point[0], point[1], point[2]);
            telegraph(loc);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (mutiny.phase() != MutinyEventManager.Phase.MAIN) { cancel(); return; }
                for (double[] point : chosen) {
                    Location loc = new Location(world, point[0], point[1], point[2]);
                    spike(loc);
                }
            }
        }.runTaskLater(plugin, TELEGRAPH_SECONDS * 20L);
    }

    private void telegraph(Location loc) {
        loc.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.1, 0), 12, 0.3, 0.05, 0.3, 0, BONE_DUST);
        loc.getWorld().playSound(loc, Sound.BLOCK_BONE_BLOCK_BREAK, 0.6f, 0.7f);
    }

    private void spike(Location loc) {
        World world = loc.getWorld();
        world.spawnParticle(Particle.DUST, loc.clone().add(0, 0.5, 0), 30, 0.2, 0.6, 0.2, 0, BONE_DUST);
        world.playSound(loc, Sound.ENTITY_SKELETON_SHOOT, 1f, 0.6f);
        world.playSound(loc, Sound.BLOCK_BONE_BLOCK_HIT, 1f, 0.8f);

        for (Player player : world.getPlayers()) {
            if (!mutiny.isParticipant(player.getUniqueId())) continue;
            if (player.getLocation().distance(loc) <= HIT_RADIUS) {
                player.setNoDamageTicks(0);
                player.damage(SPIKE_DAMAGE);
            }
        }
    }
}
