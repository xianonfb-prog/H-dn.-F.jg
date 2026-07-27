package com.exotic.plugin;

public enum TaskDifficulty {
    EASY(35, "Easy Race Task"),
    MEDIUM(30, "Medium Race Task"),
    HARD(20, "Hard Race Task"),
    INSANE(15, "Insane Task");

    public final int autoRunWeight;
    public final String label;

    TaskDifficulty(int autoRunWeight, String label) {
        this.autoRunWeight = autoRunWeight;
        this.label = label;
    }
}
