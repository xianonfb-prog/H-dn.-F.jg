package com.exotic.plugin;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class SanguineAscendancyTask extends BukkitRunnable {

    private static final Particle.DustOptions BLOOD_RED = new Particle.DustOptions(Color.fromRGB(180, 0, 0), 1.3f);

    private final ExoticPlugin plugin;
    private final CombatListener combat;
    private final Player wielder;

    public SanguineAscendancyTask(ExoticPlugin plugin, CombatListener combat, Player wielder) {
        this.plugin = plugin;
        this.combat = combat;
        this.wielder = wielder;
    }

    public void start() {
        runTaskTimer(plugin, 0L, 20L); // once per second
    }

    @Override
    public void run() {
        if (!wielder.isOnline() || combat.bloodAscendancyActive.getOrDefault(wielder.getUniqueId(), 0L) <= System.currentTimeMillis()) {
            combat.convertBonusToAbsorption(wielder);
            cancel();
            return;
        }

        for (Entity e : wielder.getNearbyEntities(AxeType.RING_RADIUS, AxeType.RING_RADIUS, AxeType.RING_RADIUS)) {
            if (!(e instanceof Player target) || target.equals(wielder)) continue;

            combat.trueDamage(target, 1.0); // 1 true damage per second, players only
            combat.addBonusHealth(wielder, 1.0);
            drawTether(wielder.getLocation().add(0, 1, 0), target.getLocation().add(0, 1, 0));
        }
    }

    /** Draws a red particle line between two points - the "lifeforce taken" visual, reused by melee hits too. */
    public static void drawTether(Location from, Location to) {
        double distance = from.distance(to);
        org.bukkit.util.Vector direction = to.toVector().subtract(from.toVector()).normalize();
        for (double d = 0; d < distance; d += 0.4) {
            Location point = from.clone().add(direction.clone().multiply(d));
            from.getWorld().spawnParticle(Particle.DUST, point, 2, 0.05, 0.05, 0.05, 0, BLOOD_RED);
        }
    }
}
