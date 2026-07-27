package com.exotic.plugin;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.enchantments.Enchantment;

import java.util.*;

public final class RaceTaskPool {

    private RaceTaskPool() {}

    private static final Random RANDOM = new Random();

    // Material category sets - used instead of null targets to avoid ambiguity
    // between different "any X" tasks that share the same TrackType.
    public static final Set<Material> LOG_MATERIALS = Set.of(
            Material.OAK_LOG, Material.SPRUCE_LOG, Material.BIRCH_LOG, Material.JUNGLE_LOG,
            Material.ACACIA_LOG, Material.DARK_OAK_LOG, Material.MANGROVE_LOG, Material.CHERRY_LOG,
            Material.CRIMSON_STEM, Material.WARPED_STEM
    );
    public static final Set<Material> LEAVES_MATERIALS = Set.of(
            Material.OAK_LEAVES, Material.SPRUCE_LEAVES, Material.BIRCH_LEAVES, Material.JUNGLE_LEAVES,
            Material.ACACIA_LEAVES, Material.DARK_OAK_LEAVES, Material.MANGROVE_LEAVES, Material.CHERRY_LEAVES,
            Material.AZALEA_LEAVES, Material.FLOWERING_AZALEA_LEAVES
    );
    public static final Set<Material> FLOWER_MATERIALS = Set.of(
            Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM, Material.AZURE_BLUET,
            Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP, Material.PINK_TULIP,
            Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.SUNFLOWER,
            Material.LILAC, Material.ROSE_BUSH, Material.PEONY, Material.TORCHFLOWER
    );
    public static final Set<Material> STONE_VARIANT_MATERIALS = Set.of(
            Material.ANDESITE, Material.DIORITE, Material.GRANITE
    );

    // ---------------------------------------------------------------
    // Task definitions
    // ---------------------------------------------------------------
    public static final List<RaceTaskDef> EASY = List.of(
            new RaceTaskDef("Collect 64 Dirt", TaskDifficulty.EASY, TrackType.COLLECT_ITEM, Material.DIRT, 64),
            new RaceTaskDef("Collect 32 Sand", TaskDifficulty.EASY, TrackType.COLLECT_ITEM, Material.SAND, 32),
            new RaceTaskDef("Break 20 Logs", TaskDifficulty.EASY, TrackType.BREAK_BLOCK, LOG_MATERIALS, 20),
            new RaceTaskDef("Collect 16 String", TaskDifficulty.EASY, TrackType.COLLECT_ITEM, Material.STRING, 16),
            new RaceTaskDef("Collect 16 Wheat", TaskDifficulty.EASY, TrackType.COLLECT_ITEM, Material.WHEAT, 16),
            new RaceTaskDef("Kill 10 Zombies", TaskDifficulty.EASY, TrackType.KILL_ENTITY, EntityType.ZOMBIE, 10),
            new RaceTaskDef("Mine 40 Cobblestone", TaskDifficulty.EASY, TrackType.BREAK_BLOCK, Material.STONE, 40),
            new RaceTaskDef("Collect 10 Feathers", TaskDifficulty.EASY, TrackType.COLLECT_ITEM, Material.FEATHER, 10),
            new RaceTaskDef("Travel 300 Blocks", TaskDifficulty.EASY, TrackType.TRAVEL_DISTANCE, null, 300),
            new RaceTaskDef("Collect 20 Flowers", TaskDifficulty.EASY, TrackType.COLLECT_ITEM, FLOWER_MATERIALS, 20),
            new RaceTaskDef("Mine 20 Coal Ore", TaskDifficulty.EASY, TrackType.BREAK_BLOCK, Material.COAL_ORE, 20),
            new RaceTaskDef("Collect 20 Bones", TaskDifficulty.EASY, TrackType.COLLECT_ITEM, Material.BONE, 20),
            new RaceTaskDef("Mine 30 Andesite/Diorite/Granite", TaskDifficulty.EASY, TrackType.BREAK_BLOCK, STONE_VARIANT_MATERIALS, 30),
            new RaceTaskDef("Collect 10 Gunpowder", TaskDifficulty.EASY, TrackType.COLLECT_ITEM, Material.GUNPOWDER, 10),
            new RaceTaskDef("Kill 8 Spiders", TaskDifficulty.EASY, TrackType.KILL_ENTITY, EntityType.SPIDER, 8),
            new RaceTaskDef("Collect 30 Sugar Cane", TaskDifficulty.EASY, TrackType.COLLECT_ITEM, Material.SUGAR_CANE, 30),
            new RaceTaskDef("Collect 20 Kelp", TaskDifficulty.EASY, TrackType.COLLECT_ITEM, Material.KELP, 20),
            new RaceTaskDef("Mine 16 Copper Ore", TaskDifficulty.EASY, TrackType.BREAK_BLOCK, Material.COPPER_ORE, 16),
            new RaceTaskDef("Break 30 Leaves", TaskDifficulty.EASY, TrackType.BREAK_BLOCK, LEAVES_MATERIALS, 30),
            new RaceTaskDef("Kill 8 Husks", TaskDifficulty.EASY, TrackType.KILL_ENTITY, EntityType.HUSK, 8)
    );

    public static final List<RaceTaskDef> MEDIUM = List.of(
            new RaceTaskDef("Mine 20 Iron Ore", TaskDifficulty.MEDIUM, TrackType.BREAK_BLOCK, Material.IRON_ORE, 20),
            new RaceTaskDef("Mine 10 Gold Ore", TaskDifficulty.MEDIUM, TrackType.BREAK_BLOCK, Material.GOLD_ORE, 10),
            new RaceTaskDef("Kill 10 Skeletons", TaskDifficulty.MEDIUM, TrackType.KILL_ENTITY, EntityType.SKELETON, 10),
            new RaceTaskDef("Kill 8 Creepers", TaskDifficulty.MEDIUM, TrackType.KILL_ENTITY, EntityType.CREEPER, 8),
            new RaceTaskDef("Mine 40 Deepslate", TaskDifficulty.MEDIUM, TrackType.BREAK_BLOCK, Material.DEEPSLATE, 40),
            new RaceTaskDef("Breed 6 Animals", TaskDifficulty.MEDIUM, TrackType.BREED_ANIMAL, null, 6),
            new RaceTaskDef("Mine 2 Diamond Ore", TaskDifficulty.MEDIUM, TrackType.BREAK_BLOCK, Material.DIAMOND_ORE, 2),
            new RaceTaskDef("Collect 20 Leather", TaskDifficulty.MEDIUM, TrackType.COLLECT_ITEM, Material.LEATHER, 20),
            new RaceTaskDef("Mine 30 Redstone Ore", TaskDifficulty.MEDIUM, TrackType.BREAK_BLOCK, Material.REDSTONE_ORE, 30),
            new RaceTaskDef("Collect 10 Obsidian", TaskDifficulty.MEDIUM, TrackType.COLLECT_ITEM, Material.OBSIDIAN, 10),
            new RaceTaskDef("Kill 15 Drowned", TaskDifficulty.MEDIUM, TrackType.KILL_ENTITY, EntityType.DROWNED, 15),
            new RaceTaskDef("Mine 16 Quartz", TaskDifficulty.MEDIUM, TrackType.BREAK_BLOCK, Material.NETHER_QUARTZ_ORE, 16),
            new RaceTaskDef("Mine 5 Emerald Ore", TaskDifficulty.MEDIUM, TrackType.BREAK_BLOCK, Material.EMERALD_ORE, 5),
            new RaceTaskDef("Kill 15 Strays", TaskDifficulty.MEDIUM, TrackType.KILL_ENTITY, EntityType.STRAY, 15),
            new RaceTaskDef("Collect 15 Amethyst Shards", TaskDifficulty.MEDIUM, TrackType.COLLECT_ITEM, Material.AMETHYST_SHARD, 15),
            new RaceTaskDef("Mine 20 Lapis Ore", TaskDifficulty.MEDIUM, TrackType.BREAK_BLOCK, Material.LAPIS_ORE, 20)
    );

    public static final List<RaceTaskDef> HARD = List.of(
            new RaceTaskDef("Defeat 40 Hostile Mobs", TaskDifficulty.HARD, TrackType.ANY_HOSTILE_KILL, null, 40),
            new RaceTaskDef("Kill 20 Spiders", TaskDifficulty.HARD, TrackType.KILL_ENTITY, EntityType.SPIDER, 20),
            new RaceTaskDef("Mine 10 Diamond Ore", TaskDifficulty.HARD, TrackType.BREAK_BLOCK, Material.DIAMOND_ORE, 10),
            new RaceTaskDef("Collect 16 Blaze Rods", TaskDifficulty.HARD, TrackType.COLLECT_ITEM, Material.BLAZE_ROD, 16),
            new RaceTaskDef("Kill 15 Endermen", TaskDifficulty.HARD, TrackType.KILL_ENTITY, EntityType.ENDERMAN, 15),
            new RaceTaskDef("Mine 6 Ancient Debris", TaskDifficulty.HARD, TrackType.BREAK_BLOCK, Material.ANCIENT_DEBRIS, 6),
            new RaceTaskDef("Kill 20 Wither Skeletons", TaskDifficulty.HARD, TrackType.KILL_ENTITY, EntityType.WITHER_SKELETON, 20),
            new RaceTaskDef("Defeat 20 Hoglins", TaskDifficulty.HARD, TrackType.KILL_ENTITY, EntityType.HOGLIN, 20),
            new RaceTaskDef("Kill 25 Piglins", TaskDifficulty.HARD, TrackType.KILL_ENTITY, EntityType.PIGLIN, 25),
            new RaceTaskDef("Collect 20 Ghast Tears", TaskDifficulty.HARD, TrackType.COLLECT_ITEM, Material.GHAST_TEAR, 20)
    );

    public static final List<RaceTaskDef> INSANE = List.of(
            new RaceTaskDef("Collect 550 Cobblestone", TaskDifficulty.INSANE, TrackType.BREAK_BLOCK, Material.STONE, 550),
            new RaceTaskDef("Defeat 80 Hostile Mobs", TaskDifficulty.INSANE, TrackType.ANY_HOSTILE_KILL, null, 80),
            new RaceTaskDef("Mine 200 Total Ore, Any Type", TaskDifficulty.INSANE, TrackType.ANY_ORE_MINE, null, 200),
            new RaceTaskDef("Kill 60 Piglins", TaskDifficulty.INSANE, TrackType.KILL_ENTITY, EntityType.PIGLIN, 60),
            new RaceTaskDef("Collect 650 Logs", TaskDifficulty.INSANE, TrackType.BREAK_BLOCK, LOG_MATERIALS, 650),
            new RaceTaskDef("Mine 64 Diamond Ore", TaskDifficulty.INSANE, TrackType.BREAK_BLOCK, Material.DIAMOND_ORE, 64),
            new RaceTaskDef("Defeat 40 Endermen", TaskDifficulty.INSANE, TrackType.KILL_ENTITY, EntityType.ENDERMAN, 40),
            new RaceTaskDef("Defeat 5 Ravagers", TaskDifficulty.INSANE, TrackType.KILL_ENTITY, EntityType.RAVAGER, 5),
            new RaceTaskDef("Mine 15 Ancient Debris", TaskDifficulty.INSANE, TrackType.BREAK_BLOCK, Material.ANCIENT_DEBRIS, 15)
    );

    public static RaceTaskDef randomTask(TaskDifficulty difficulty) {
        List<RaceTaskDef> pool = switch (difficulty) {
            case EASY -> EASY;
            case MEDIUM -> MEDIUM;
            case HARD -> HARD;
            case INSANE -> INSANE;
        };
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    public static RaceTaskDef randomTaskAnyDifficulty() {
        return randomTask(TaskDifficulty.values()[RANDOM.nextInt(4)]);
    }

    /** Weighted pick for the auto-run scheduler - rarer as difficulty increases. */
    public static TaskDifficulty weightedRandomDifficulty() {
        int total = 0;
        for (TaskDifficulty d : TaskDifficulty.values()) total += d.autoRunWeight;
        int roll = RANDOM.nextInt(total);
        int cumulative = 0;
        for (TaskDifficulty d : TaskDifficulty.values()) {
            cumulative += d.autoRunWeight;
            if (roll < cumulative) return d;
        }
        return TaskDifficulty.EASY;
    }

    // ---------------------------------------------------------------
    // Reward pools - equal odds within each tier
    // ---------------------------------------------------------------
    public static List<ItemStack> rollReward(TaskDifficulty difficulty) {
        List<List<ItemStack>> pool = switch (difficulty) {
            case EASY -> EASY_REWARDS;
            case MEDIUM -> MEDIUM_REWARDS;
            case HARD -> HARD_REWARDS;
            case INSANE -> INSANE_REWARDS;
        };
        return pool.get(RANDOM.nextInt(pool.size()));
    }

    private static ItemStack potion(Material base, org.bukkit.potion.PotionType type) {
        ItemStack item = new ItemStack(base);
        var meta = (org.bukkit.inventory.meta.PotionMeta) item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack enchantedBook(Enchantment enchant, int level) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(enchant, level, true);
        book.setItemMeta(meta);
        return book;
    }

    private static List<ItemStack> enchantedBooks(Enchantment enchant, int level, int copies) {
        List<ItemStack> books = new ArrayList<>();
        for (int i = 0; i < copies; i++) books.add(enchantedBook(enchant, level));
        return books;
    }

    // -------------------- EASY --------------------
    // Identity: fast consumable value - useful to grab even mid-late game, not just early gear.
    private static final List<List<ItemStack>> EASY_REWARDS = List.of(
            List.of(new ItemStack(Material.GOLDEN_APPLE, 14)),
            List.of(new ItemStack(Material.GOLDEN_CARROT, 28)),
            List.of(new ItemStack(Material.EXPERIENCE_BOTTLE, 36)),
            List.of(new ItemStack(Material.ENDER_PEARL, 12)),
            List.of(potion(Material.SPLASH_POTION, org.bukkit.potion.PotionType.STRONG_HEALING)),
            List.of(potion(Material.SPLASH_POTION, org.bukkit.potion.PotionType.FIRE_RESISTANCE)),
            List.of(new ItemStack(Material.DIAMOND, 4)),
            List.of(new ItemStack(Material.IRON_BLOCK, 2)),
            List.of(enchantedBook(Enchantment.EFFICIENCY, 3)),
            List.of(enchantedBook(Enchantment.UNBREAKING, 3))
    );

    // -------------------- MEDIUM --------------------
    // Identity: combat-prep materials - gearing toward a real PvP-ready loadout.
    private static final List<List<ItemStack>> MEDIUM_REWARDS = List.of(
            List.of(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1)),
            List.of(new ItemStack(Material.DIAMOND, 12)),
            List.of(new ItemStack(Material.DIAMOND_BLOCK, 1)),
            List.of(new ItemStack(Material.NETHERITE_SCRAP, 3)),
            List.of(new ItemStack(Material.ENDER_PEARL, 18)),
            List.of(potion(Material.SPLASH_POTION, org.bukkit.potion.PotionType.STRENGTH)),
            concat(enchantedBook(Enchantment.SHARPNESS, 3), enchantedBook(Enchantment.UNBREAKING, 2)),
            concat(enchantedBook(Enchantment.PROTECTION, 3), enchantedBook(Enchantment.UNBREAKING, 2)),
            List.of(enchantedBook(Enchantment.MENDING, 1)),
            List.of(new ItemStack(Material.EXPERIENCE_BOTTLE, 56))
    );

    // -------------------- HARD --------------------
    // Identity: gear-defining netherite/upgrade tier - strongly useful.
    private static final List<List<ItemStack>> HARD_REWARDS = List.of(
            List.of(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 3)),
            List.of(new ItemStack(Material.NETHERITE_INGOT, 3)),
            List.of(new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 2)),
            List.of(new ItemStack(Material.GOLD_BLOCK, 14)),
            enchantedBooks(Enchantment.SHARPNESS, 4, 2),
            enchantedBooks(Enchantment.PROTECTION, 4, 2),
            enchantedBooks(Enchantment.FIRE_ASPECT, 2, 2),
            enchantedBooks(Enchantment.MENDING, 1, 2),
            List.of(new ItemStack(Material.SHULKER_BOX, 1)),
            List.of(new ItemStack(Material.TOTEM_OF_UNDYING, 2)),
            List.of(new ItemStack(Material.DIAMOND_BLOCK, 3)),
            List.of(potion(Material.SPLASH_POTION, org.bukkit.potion.PotionType.STRENGTH))
    );

    // -------------------- INSANE --------------------
    // Identity: build-completing, run-defining rewards.
    private static final List<List<ItemStack>> INSANE_REWARDS = List.of(
            List.of(new ItemStack(Material.ELYTRA, 1)),
            List.of(new ItemStack(Material.NETHERITE_INGOT, 12)),
            List.of(new ItemStack(Material.NETHERITE_INGOT, 10), new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 3)),
            concat(
                    enchantedBooks(Enchantment.PROTECTION, 4, 2),
                    enchantedBooks(Enchantment.MENDING, 1, 2),
                    enchantedBooks(Enchantment.UNBREAKING, 3, 2)
            ),
            concat(
                    enchantedBooks(Enchantment.SHARPNESS, 5, 2),
                    enchantedBooks(Enchantment.UNBREAKING, 3, 2),
                    enchantedBooks(Enchantment.MENDING, 1, 2),
                    enchantedBooks(Enchantment.FIRE_ASPECT, 2, 2)
            ),
            List.of(new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 5)),
            List.of(new ItemStack(Material.TOTEM_OF_UNDYING, 4)),
            List.of(new ItemStack(Material.SHULKER_BOX, 3)),
            List.of(new ItemStack(Material.GOLD_BLOCK, 40), new ItemStack(Material.DIAMOND_BLOCK, 4)),
            List.of(new ItemStack(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE, 5))
    );

    @SafeVarargs
    private static List<ItemStack> concat(ItemStack... items) {
        return new ArrayList<>(List.of(items));
    }

    @SafeVarargs
    private static List<ItemStack> concat(List<ItemStack>... lists) {
        List<ItemStack> result = new ArrayList<>();
        for (List<ItemStack> list : lists) result.addAll(list);
        return result;
    }
}
