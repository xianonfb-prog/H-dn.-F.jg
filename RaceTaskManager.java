package com.exotic.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * One race task is active server-wide at a time. Every online player gets
 * their OWN private BossBar tracking only their OWN progress toward the same
 * shared target - nobody can see anyone else's progress, keeping first place
 * anonymous and the race genuinely tense. First player to reach the target
 * wins the reward; everyone else's attempt just ends.
 */
public class RaceTaskManager {

    private static final long STANDARD_TASK_DURATION_MS = 30 * 60 * 1000L;
    private static final long INSANE_TASK_DURATION_MS = 45 * 60 * 1000L;
    private static final Random RANDOM = new Random();

    private long durationFor(TaskDifficulty difficulty) {
        return difficulty == TaskDifficulty.INSANE ? INSANE_TASK_DURATION_MS : STANDARD_TASK_DURATION_MS;
    }

    private final ExoticPlugin plugin;

    private RaceTaskDef activeTask;
    private long startedAt;
    private long expiresAt;
    private final Map<UUID, Integer> progress = new HashMap<>();
    private final Map<UUID, BossBar> bars = new HashMap<>();

    private boolean autoRunEnabled = false;
    private long nextAutoRunAt = 0;

    public RaceTaskManager(ExoticPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasActiveTask() {
        return activeTask != null;
    }

    public RaceTaskDef activeTask() {
        return activeTask;
    }

    public boolean isAutoRunEnabled() {
        return autoRunEnabled;
    }

    public void setAutoRunEnabled(boolean enabled) {
        this.autoRunEnabled = enabled;
        if (enabled) {
            nextAutoRunAt = System.currentTimeMillis() + randomInterval();
        }
    }

    private long randomInterval() {
        return (10 + RANDOM.nextInt(16)) * 60 * 1000L; // 10-25 minutes
    }

    public boolean startTask(RaceTaskDef def) {
        if (activeTask != null) return false; // strictly one at a time

        activeTask = def;
        startedAt = System.currentTimeMillis();
        expiresAt = startedAt + durationFor(def.difficulty);
        progress.clear();

        Bukkit.broadcast(Component.text(def.difficulty.label + "!", NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD)
                .appendNewline()
                .append(Component.text(def.name, NamedTextColor.YELLOW)));

        for (Player p : Bukkit.getOnlinePlayers()) {
            createBar(p);
        }
        return true;
    }

    private void createBar(Player player) {
        if (activeTask == null) return;
        BossBar bar = Bukkit.createBossBar(barTitle(0), barColor(activeTask.difficulty), BarStyle.SOLID);
        bar.addPlayer(player);
        bar.setProgress(0.0);
        bars.put(player.getUniqueId(), bar);
    }

    /** Call when a player joins mid-task so they get their own bar too. */
    public void onPlayerJoin(Player player) {
        if (activeTask != null && !bars.containsKey(player.getUniqueId())) {
            createBar(player);
        }
    }

    public void onPlayerQuit(Player player) {
        BossBar bar = bars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    private BarColor barColor(TaskDifficulty difficulty) {
        return switch (difficulty) {
            case EASY -> BarColor.GREEN;
            case MEDIUM -> BarColor.YELLOW;
            case HARD -> BarColor.RED;
            case INSANE -> BarColor.PURPLE;
        };
    }

    private String barTitle(int playerProgress) {
        long remainingMs = Math.max(0, expiresAt - System.currentTimeMillis());
        long minutes = remainingMs / 60000;
        long seconds = (remainingMs % 60000) / 1000;
        return String.format("%s | %s | %d/%d | %dm %02ds left",
                activeTask.difficulty.label, activeTask.name, playerProgress, activeTask.amount, minutes, seconds);
    }

    /** Called by RaceTaskListener whenever a player does something matching the active task. */
    public void progress(Player player, TrackType type, Object target, int amount) {
        if (activeTask == null || activeTask.trackType != type) return;
        if (!matches(target)) return;

        int newVal = progress.merge(player.getUniqueId(), amount, Integer::sum);
        BossBar bar = bars.get(player.getUniqueId());
        if (bar != null) {
            bar.setProgress(Math.min(1.0, (double) newVal / activeTask.amount));
            bar.setTitle(barTitle(newVal));
        }

        if (newVal >= activeTask.amount) {
            completeTask(player);
        }
    }

    private boolean matches(Object target) {
        if (activeTask.target == null) return true; // truly generic tasks (any ore, any hostile, travel, etc.)
        if (activeTask.target instanceof java.util.Set<?> set) return set.contains(target);
        return activeTask.target.equals(target);
    }

    private void completeTask(Player winner) {
        RaceTaskDef finished = activeTask;
        for (ItemStack reward : RaceTaskPool.rollReward(finished.difficulty)) {
            winner.getInventory().addItem(reward.clone());
        }

        Bukkit.broadcast(Component.text(winner.getName() + " won the " + finished.difficulty.label + "!", NamedTextColor.GREEN)
                .decorate(TextDecoration.BOLD));

        clearTask();
        if (autoRunEnabled) nextAutoRunAt = System.currentTimeMillis() + randomInterval();
    }

    private void clearTask() {
        activeTask = null;
        progress.clear();
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
    }

    /** Called once per second from PassiveTickTask. */
    public void tick() {
        if (activeTask != null) {
            if (System.currentTimeMillis() >= expiresAt) {
                Bukkit.broadcast(Component.text("The " + activeTask.difficulty.label + " has despawned - nobody claimed it in time.", NamedTextColor.GRAY));
                clearTask();
                if (autoRunEnabled) nextAutoRunAt = System.currentTimeMillis() + randomInterval();
            } else {
                for (Map.Entry<UUID, BossBar> entry : bars.entrySet()) {
                    int p = progress.getOrDefault(entry.getKey(), 0);
                    entry.getValue().setTitle(barTitle(p));
                }
            }
        } else if (autoRunEnabled && System.currentTimeMillis() >= nextAutoRunAt) {
            startTask(RaceTaskPool.randomTask(RaceTaskPool.weightedRandomDifficulty()));
        }
    }
}
