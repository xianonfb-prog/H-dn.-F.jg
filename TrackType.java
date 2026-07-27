package com.exotic.plugin;

public enum TrackType {
    BREAK_BLOCK,        // target = Material
    COLLECT_ITEM,       // target = Material, via pickup
    KILL_ENTITY,        // target = EntityType
    ANY_ORE_MINE,       // target = null, matches any ore block
    ANY_HOSTILE_KILL,   // target = null, matches any Monster
    TRAVEL_DISTANCE,    // target = null
    TAME_ANIMAL,        // target = EntityType or null for any
    BREED_ANIMAL,       // target = null, any animal breed
    CATCH_FISH,         // target = null
    SHEAR_SHEEP,        // target = null
    SOLO_CLEAR_RAID     // target = null, only counts if the player was the sole hero
}
