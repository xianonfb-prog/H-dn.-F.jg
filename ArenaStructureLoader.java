package com.exotic.plugin;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.structure.Structure;
import org.bukkit.structure.StructureManager;

import java.io.IOException;
import java.io.InputStream;
import java.util.Random;

/**
 * Owns the whole lifecycle of Bloodred Mutiny's arena: a dedicated void world that isn't
 * naturally reachable by anyone (no portals, no paths - the only way in is the event's own
 * teleport), pasted fresh on event start and wiped back to nothing on event end, so between
 * events it's genuinely as if it never existed - not just visually reset, but blown away.
 */
public final class ArenaStructureLoader {

    private static final String RESOURCE_PATH = "structures/crimson_mutiny_arena.nbt";

    private ArenaStructureLoader() {}

    /** Creates the dedicated arena world if it doesn't exist yet (idempotent - safe to call every
     *  event start). Uses the vanilla "Void" superflat preset so there's nothing to generate. */
    public static World ensureWorldLoaded(ExoticPlugin plugin) {
        World world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
        if (world != null) return world;

        WorldCreator creator = new WorldCreator(MutinyArena.WORLD_NAME);
        creator.type(WorldType.FLAT);
        creator.generatorSettings("{\"biome\":\"minecraft:the_void\",\"layers\":[]}");
        creator.generateStructures(false);

        world = Bukkit.createWorld(creator);
        if (world != null) {
            world.setSpawnLocation((int) MutinyArena.CENTER_X, (int) MutinyArena.CENTER_Y, (int) MutinyArena.CENTER_Z);
            world.setDifficulty(org.bukkit.Difficulty.NORMAL);
            world.setGameRule(org.bukkit.GameRule.DO_MOB_SPAWNING, false); // no wandering hostiles muddying the FFA
            plugin.getLogger().info("Created dedicated Mutiny arena world: " + MutinyArena.WORLD_NAME);
        } else {
            plugin.getLogger().warning("Failed to create Mutiny arena world: " + MutinyArena.WORLD_NAME);
        }
        return world;
    }

    /** Pastes the arena at MutinyArena's configured center. Returns false if the world isn't
     *  loaded or the bundled structure resource is missing. */
    public static boolean pasteArena(ExoticPlugin plugin) {
        World world = ensureWorldLoaded(plugin);
        if (world == null) return false;

        try (InputStream in = plugin.getResource(RESOURCE_PATH)) {
            if (in == null) return false;

            StructureManager manager = Bukkit.getStructureManager();
            Structure structure = manager.loadStructure(in);

            Location origin = structureOrigin(world);

            structure.place(
                    origin,
                    false,                                    // no entities in this structure
                    org.bukkit.block.structure.StructureRotation.NONE,
                    org.bukkit.Mirror.NONE,
                    0,                                        // palette index (only one palette present)
                    1.0f,                                     // integrity - 1.0 = every block placed
                    new Random()
            );

            plugin.getLogger().info("Crimson Mutiny arena pasted at " + origin);
            return true;
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to load bundled arena structure: " + e.getMessage());
            return false;
        }
    }

    /** Blows the entire arena footprint back to air - called right after an event concludes, so
     *  the arena world sits empty (as if the arena never existed) until the next event pastes it
     *  fresh again. Cheap one-time bulk operation, same order of magnitude as the paste itself. */
    public static void wipeArena(ExoticPlugin plugin) {
        World world = Bukkit.getWorld(MutinyArena.WORLD_NAME);
        if (world == null) return;

        Location origin = structureOrigin(world);
        int diameter = MutinyArena.STRUCTURE_RADIUS * 2 + 1;

        for (int x = 0; x < diameter; x++) {
            for (int y = 0; y < MutinyArena.STRUCTURE_HEIGHT; y++) {
                for (int z = 0; z < diameter; z++) {
                    world.getBlockAt(origin.getBlockX() + x, origin.getBlockY() + y, origin.getBlockZ() + z)
                            .setType(Material.AIR, false);
                }
            }
        }
        plugin.getLogger().info("Crimson Mutiny arena wiped clean.");
    }

    private static Location structureOrigin(World world) {
        int originX = (int) Math.floor(MutinyArena.CENTER_X) - MutinyArena.STRUCTURE_RADIUS;
        int originZ = (int) Math.floor(MutinyArena.CENTER_Z) - MutinyArena.STRUCTURE_RADIUS;
        int originY = (int) Math.floor(MutinyArena.CENTER_Y) - 1; // floor sits one below player-eye height
        return new Location(world, originX, originY, originZ);
    }
}
