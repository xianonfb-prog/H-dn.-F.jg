package com.exotic.plugin;

public class RaceTaskDef {
    public final String name;
    public final TaskDifficulty difficulty;
    public final TrackType trackType;
    public final Object target; // Material, EntityType, or null depending on trackType
    public final int amount;

    public RaceTaskDef(String name, TaskDifficulty difficulty, TrackType trackType, Object target, int amount) {
        this.name = name;
        this.difficulty = difficulty;
        this.trackType = trackType;
        this.target = target;
        this.amount = amount;
    }
}
