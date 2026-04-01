package com.classic.preservitory.server.player;

/** The equipment slots a player can fill. */
public enum EquipSlot {

    WEAPON,
    HELMET;

    /** @return the matching slot, or {@code null} if the name is invalid. */
    public static EquipSlot fromString(String s) {
        if (s == null || s.isBlank()) return null;
        try { return valueOf(s.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
