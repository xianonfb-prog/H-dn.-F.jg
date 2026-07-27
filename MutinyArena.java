package com.exotic.plugin;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.List;
import java.util.Random;

/**
 * Static arena configuration for Bloodred Mutiny's event.
 *
 * TESTING PHASE NOTE: coordinates below are placeholders centered at world spawn-ish values.
 * Once the real crimson arena is hand-built (or procedurally generated) and pasted in-world,
 * update WORLD_NAME, CENTER_X/Y/Z, RESPAWN_POINTS, and TIE_POSITIONS to match its actual location.
 */
public final class MutinyArena {

    private MutinyArena() {}

    public static final String WORLD_NAME = "mutiny_arena"; // dedicated world - not naturally reachable by anyone
    public static final double CENTER_X = 0.5;
    public static final double CENTER_Y = 101;
    public static final double CENTER_Z = 0.5;

    // Structure footprint - must match generate_arena.py's RADIUS/SIZE_Y exactly.
    public static final int STRUCTURE_RADIUS = 40;
    public static final int STRUCTURE_HEIGHT = 7;

    /** Normal playable boundary radius during the main 45-minute phase. */
    public static final double FULL_RADIUS = 40.0;
    /** Shrunk boundary radius while a Crimson Tide event is active. */
    public static final double TIDE_RADIUS = 15.0;

    // Multiple respawn points spread around the arena so a single spot can't be camped.
    public static final List<double[]> RESPAWN_POINTS = List.of(
            new double[]{ 0.5, 101, 30.5 },
            new double[]{ 21.5, 101, -21.5 },
            new double[]{ -21.5, 101, -21.5 },
            new double[]{ 21.5, 101, 21.5 },
            new double[]{ -21.5, 101, 21.5 },
            new double[]{ 0.5, 101, -30.5 }
    );

    // Distinct starting positions for tie sudden-death - spread evenly around the arena edge
    // so nobody starts closer to another than the rest. Supports up to 6 tied players; if more
    // than 6 ever tie, positions wrap around and pair players up (acceptable for testing phase).
    public static final List<double[]> TIE_POSITIONS = RESPAWN_POINTS;

    public static Location center(World world) {
        return new Location(world, CENTER_X, CENTER_Y, CENTER_Z);
    }

    public static Location randomRespawn(World world, Random random) {
        double[] p = RESPAWN_POINTS.get(random.nextInt(RESPAWN_POINTS.size()));
        return new Location(world, p[0], p[1], p[2]);
    }

    public static Location tiePosition(World world, int index) {
        double[] p = TIE_POSITIONS.get(index % TIE_POSITIONS.size());
        return new Location(world, p[0], p[1], p[2]);
    }

    public static boolean withinRadius(Location loc, Location center, double radius) {
        if (!loc.getWorld().equals(center.getWorld())) return false;
        double dx = loc.getX() - center.getX();
        double dz = loc.getZ() - center.getZ();
        return (dx * dx + dz * dz) <= radius * radius;
    }
}
