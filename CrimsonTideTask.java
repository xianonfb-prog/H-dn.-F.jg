package com.exotic.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Random;
import java.util.UUID;

/**
 * "The Crimson Tide" - a random, temporary shrink event during Bloodred Mutiny's main phase.
 * Fires on a random cooldown, gives a screen-title warning, then shrinks the safe radius down
 * to MutinyArena.TIDE_RADIUS over a short window and holds it there before resetting.
 *
 * Damage for standing outside the current radius is intentionally light (0.5 heart per 2s) -
 * disruptive and slightly dangerous, not a battle-royale-style ramping death zone.
 */
public class CrimsonTideTask extends BukkitRunnable {

    private static final Particle.DustOptions BLOOD_DUST = new Particle.DustOptions(Color.fromRGB(140, 0, 0), 1.4f);

    private static final long MIN_COOLDOWN_S = 90;   // 1.5 min minimum between tides
    private static final long MAX_COOLDOWN_S = 300;  // 5 min maximum between tides
    private static final int WARNING_SECONDS = 8;
    private static final int SHRINK_SECONDS = 25;
    private static final int HOLD_SECONDS = 40;

    private final ExoticPlugin plugin;
    private final MutinyEventManager mutiny;
    private final Random random = new Random();

    private long nextTriggerAt;
    private boolean active = false;

    public CrimsonTideTask(ExoticPlugin plugin, MutinyEventManager mutiny) {
        this.plugin = plugin;
        this.mutiny = mutiny;
        scheduleNext();
    }

    public boolean isActive() {
        return active;
    }

    private void scheduleNext() {
        long delaySeconds = MIN_COOLDOWN_S + random.nextInt((int) (MAX_COOLDOWN_S - MIN_COOLDOWN_S + 1));
        nextTriggerAt = System.currentTimeMillis() + delaySeconds * 1000L;
    }

    @Override
    public void run() {
        if (mutiny.phase() != MutinyEventManager.Phase.MAIN) {
            cancel();
            return;
        }
        if (active) return; // a full cycle is already running via its own runnable chain below
        if (System.currentTimeMillis() < nextTriggerAt) return;

        trigger();
    }

    private void trigger() {
        active = true;
        broadcastTitle("THE TIDE RISES", "Find high ground - " + WARNING_SECONDS + "s", NamedTextColor.DARK_RED);
        broadcastSound(Sound.ENTITY_WARDEN_ROAR, 1.5f);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (mutiny.phase() != MutinyEventManager.Phase.MAIN) { cancel(); active = false; return; }
                elapsed++;
                if (elapsed >= WARNING_SECONDS) {
                    cancel();
                    beginShrink();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void beginShrink() {
        double startRadius = MutinyArena.FULL_RADIUS;
        double endRadius = MutinyArena.TIDE_RADIUS;

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (mutiny.phase() != MutinyEventManager.Phase.MAIN) { cancel(); active = false; return; }
                elapsed++;
                double progress = Math.min(1.0, elapsed / (double) SHRINK_SECONDS);
                double radius = startRadius + (endRadius - startRadius) * progress;
                mutiny.setRadius(radius);
                drawBoundary(radius);
                if (elapsed % 2 == 0) applyTideDamage(radius);

                if (progress >= 1.0) {
                    cancel();
                    hold();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void hold() {
        broadcastTitle("THE TIDE HOLDS", "Stay inside the safe zone", NamedTextColor.RED);

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (mutiny.phase() != MutinyEventManager.Phase.MAIN) { cancel(); active = false; return; }
                elapsed++;
                drawBoundary(mutiny.currentRadius());
                if (elapsed % 2 == 0) applyTideDamage(mutiny.currentRadius());
                if (elapsed >= HOLD_SECONDS) {
                    cancel();
                    recede();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void recede() {
        mutiny.setRadius(MutinyArena.FULL_RADIUS);
        broadcastTitle("THE TIDE RECEDES", "", NamedTextColor.GRAY);
        broadcastSound(Sound.AMBIENT_CAVE, 0.8f);
        active = false;
        scheduleNext();
    }

    /** Light periodic damage (0.5 heart per 2s) for anyone outside the CURRENT tide radius -
     *  disruptive and slightly dangerous, intentionally not a ramping battle-royale death zone. */
    private void applyTideDamage(double radius) {
        org.bukkit.World world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
        if (world == null) return;
        Location center = MutinyArena.center(world);

        for (Player player : world.getPlayers()) {
            if (!mutiny.isParticipant(player.getUniqueId())) continue;
            if (!MutinyArena.withinRadius(player.getLocation(), center, radius)) {
                player.setNoDamageTicks(0);
                player.damage(1.0); // 0.5 heart
            }
        }
    }

    private void drawBoundary(double radius) {
        org.bukkit.World world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
        if (world == null) return;
        Location center = MutinyArena.center(world);

        int points = 64;
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            for (double y = 0; y < 3; y += 1.0) {
                Location point = new Location(world, x, center.getY() + y, z);
                world.spawnParticle(Particle.DUST, point, 2, 0.1, 0.1, 0.1, 0, BLOOD_DUST);
            }
        }
    }

    private void broadcastTitle(String main, String sub, NamedTextColor color) {
        for (UUID id : Bukkit.getOnlinePlayers().stream().map(Player::getUniqueId).toList()) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !mutiny.isParticipant(id)) continue;
            player.showTitle(Title.title(Component.text(main, color), Component.text(sub, NamedTextColor.GRAY)));
        }
    }

    private void broadcastSound(Sound sound, float volume) {
        org.bukkit.World world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
        if (world == null) return;
        Location center = MutinyArena.center(world);
        world.playSound(center, sound, volume, 1f);
    }
}
