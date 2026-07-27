package com.exotic.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class ExoticPlugin extends JavaPlugin {

    private CooldownManager cooldownManager;
    private TrialSystem trialSystem;
    private ScoreboardManager scoreboardManager;
    private PersistenceManager persistenceManager;
    private RaceTaskManager raceTaskManager;
    private CombatListener combatListener;
    private MutinyEventManager mutinyEventManager;

    @Override
    public void onEnable() {
        cooldownManager = new CooldownManager();
        trialSystem = new TrialSystem(this);
        scoreboardManager = new ScoreboardManager(this);
        persistenceManager = new PersistenceManager(this);
        raceTaskManager = new RaceTaskManager(this);
        mutinyEventManager = new MutinyEventManager(this);

        combatListener = new CombatListener(this);
        ThunderstormManager storm = new ThunderstormManager(this, combatListener);
        ZeusAbilityListener zeus = new ZeusAbilityListener(this, combatListener, storm);

        getServer().getPluginManager().registerEvents(combatListener, this);
        getServer().getPluginManager().registerEvents(new PassiveListener(this, combatListener, zeus), this);
        getServer().getPluginManager().registerEvents(new SoulboundListener(this), this);
        getServer().getPluginManager().registerEvents(zeus, this);
        getServer().getPluginManager().registerEvents(new RaceTaskListener(this), this);
        getServer().getPluginManager().registerEvents(new MutinyListener(this, mutinyEventManager), this);

        CommandHandler handler = new CommandHandler(this);
        getCommand("exotic").setExecutor(handler);
        getCommand("exotic").setTabCompleter(handler);

        new PassiveTickTask(this, combatListener, storm).runTaskTimer(this, 20L, 20L);
        new AbilityParticleTask(combatListener).runTaskTimer(this, 0L, 2L);
        new StunEnforcerTask(combatListener).runTaskTimer(this, 0L, 1L);
        getServer().getScheduler().runTaskTimer(this, () -> raceTaskManager.tick(), 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> mutinyEventManager.tick(), 20L, 20L);

        // Restore trials/cooldowns from disk (survives restarts)
        persistenceManager.load();

        // Autosave every 5 minutes in case of a crash between restarts
        getServer().getScheduler().runTaskTimer(this, () -> persistenceManager.save(), 6000L, 6000L);

        getLogger().info("Exotic enabled - 6 swords + Staff + Hand Of Zeus + Bloodred Mutiny + Race Tasks loaded.");
    }

    @Override
    public void onDisable() {
        if (persistenceManager != null) persistenceManager.save();
        getLogger().info("Exotic disabled.");
    }

    public CooldownManager cooldowns() { return cooldownManager; }
    public TrialSystem trials() { return trialSystem; }
    public ScoreboardManager scoreboards() { return scoreboardManager; }
    public RaceTaskManager raceTasks() { return raceTaskManager; }
    public CombatListener combat() { return combatListener; }
    public MutinyEventManager mutiny() { return mutinyEventManager; }
}
