package com.exotic.plugin;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Runs every tick, enforcing stun for every entity currently in combat.stunned
 * and cleaning up expired entries. Kept as one central task (rather than a
 * per-instance timer like IceCageTask) specifically because the bolt-chain
 * ability needs to safely OVERRIDE an in-progress stun's duration without a
 * race between two separate expiring timers.
 *
 * FIX (previously known bug): velocity-zeroing only stops MOBS, since their
 * movement is physics-driven. Players are client-input-driven and barely
 * notice velocity resets, so this never actually stunned a player's movement -
 * only their attacks were blocked (separate check in CombatListener). Players
 * now get setWalkSpeed(0) (and setFlySpeed(0) if currently flying) for the
 * duration instead, restored back to vanilla defaults the moment the stun
 * entry clears.
 */
public class StunEnforcerTask extends BukkitRunnable {

    private static final float DEFAULT_WALK_SPEED = 0.2f;
    private static final float DEFAULT_FLY_SPEED = 0.1f;

    private final CombatListener combat;

    // Tracks which players currently have their speed locked by this task, so
    // we know exactly who to restore when their stun clears (and don't clobber
    // speed on players who were never stunned).
    private final Set<UUID> speedLocked = new HashSet<>();

    public StunEnforcerTask(CombatListener combat) {
        this.combat = combat;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = combat.stunned.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            UUID id = entry.getKey();

            if (entry.getValue() <= now) {
                it.remove();
                restoreSpeed(id);
                continue;
            }

            Entity entity = Bukkit.getEntity(id);
            if (entity == null) continue;

            if (entity instanceof Player player) {
                lockSpeed(player);
            } else {
                // Non-player mobs are physics-driven - velocity-zeroing works fine for them.
                Vector v = entity.getVelocity();
                entity.setVelocity(new Vector(0, Math.min(v.getY(), 0), 0));
            }
        }

        // Catch anyone whose stun cleared via direct map removal elsewhere (not just expiry above).
        speedLocked.removeIf(id -> {
            if (combat.stunned.containsKey(id)) return false;
            restoreSpeed(id);
            return true;
        });
    }

    private void lockSpeed(Player player) {
        speedLocked.add(player.getUniqueId());
        if (player.getWalkSpeed() != 0f) player.setWalkSpeed(0f);
        if (player.isFlying() && player.getFlySpeed() != 0f) player.setFlySpeed(0f);
    }

    private void restoreSpeed(UUID id) {
        if (!speedLocked.remove(id)) return;
        Player player = Bukkit.getPlayer(id);
        if (player == null) return;
        player.setWalkSpeed(DEFAULT_WALK_SPEED);
        player.setFlySpeed(DEFAULT_FLY_SPEED);
    }
}
