package com.classic.preservitory.server.definitions;

/**
 * Immutable definition of a single item, loaded from {@code cache/items/*.json}.
 *
 * Add new fields here as needed (e.g. equipSlot, weight, sprite).
 * Never store per-player state here — this is shared, read-only data.
 */
public final class ItemDefinition {

    public final int     id;
    public final String  name;
    public final int     value;     // base value in coins
    public final boolean stackable;
    public final boolean tradable;

    /** Equipment slot name ("WEAPON", "HELMET"), or {@code null} if not equippable. */
    public final String  equipSlot;

    /** Bonus added to the player's effective attack level in combat. */
    public final int     attackBonus;

    /** Bonus added to the player's effective strength level in combat. */
    public final int     strengthBonus;

    public ItemDefinition(int id, String name, int value, boolean stackable, boolean tradable,
                          String equipSlot, int attackBonus, int strengthBonus) {
        this.id            = id;
        this.name          = name;
        this.value         = value;
        this.stackable     = stackable;
        this.tradable      = tradable;
        this.equipSlot     = equipSlot;
        this.attackBonus   = attackBonus;
        this.strengthBonus = strengthBonus;
    }
}
