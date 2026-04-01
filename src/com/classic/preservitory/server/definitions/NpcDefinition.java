package com.classic.preservitory.server.definitions;

/**
 * Immutable definition of a stationary NPC, loaded from {@code cache/npcs/*.json}.
 *
 * Dialogue is no longer stored here — use {@link DialogueDefinitionManager} to look
 * up dialogue blocks by NPC id or specific dialogue id.
 */
public final class NpcDefinition {

    /** Numeric ID — the primary identifier. */
    public final int     id;

    /** String key matching the filename (e.g. "guide"). */
    public final String  key;

    public final String  name;
    public final boolean shopkeeper;

    /** Non-null when this NPC opens a shop; must match a registered shop ID. */
    public final String  shopId;

    public final String  questId;

    /** Whether this NPC wanders around its spawn point. */
    public final boolean wander;

    /** How far from spawn the NPC may wander, in tiles. Ignored when {@link #wander} is false. */
    public final int     wanderRadiusTiles;

    public NpcDefinition(int id,
                         String key,
                         String name,
                         boolean shopkeeper,
                         String shopId,
                         String questId,
                         boolean wander,
                         int wanderRadiusTiles) {
        this.id               = id;
        this.key              = key;
        this.name             = name;
        this.shopkeeper       = shopkeeper;
        this.shopId           = shopId;
        this.questId          = questId;
        this.wander           = wander;
        this.wanderRadiusTiles = wanderRadiusTiles;
    }
}
