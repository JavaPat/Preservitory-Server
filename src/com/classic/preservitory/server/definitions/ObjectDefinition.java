package com.classic.preservitory.server.definitions;

/**
 * Immutable definition of a world object (tree, rock, etc.),
 * loaded from {@code cache/objects/*.json}.
 */
public final class ObjectDefinition {

    public enum Type { TREE, ROCK }

    /** Numeric ID — the primary identifier. */
    public final int    id;

    /** String key matching the map's {@code "definition"} field (e.g. "tree"). */
    public final String key;

    public final String name;
    public final Type   type;

    /** Item granted to the player when this object is gathered. */
    public final int    resourceItemId;

    /** XP awarded per successful gather action. */
    public final int    xp;

    /** Time in milliseconds before this object respawns after being depleted. */
    public final long   respawnMs;

    /**
     * Key used by the client to look up the sprite in {@code AssetManager}.
     * Defaults to {@link #key} when not specified in JSON.
     */
    public final String spriteKey;

    public ObjectDefinition(int id, String key, String name, Type type,
                            int resourceItemId, int xp, long respawnMs, String spriteKey) {
        this.id             = id;
        this.key            = key;
        this.name           = name;
        this.type           = type;
        this.resourceItemId = resourceItemId;
        this.xp             = xp;
        this.respawnMs      = respawnMs;
        this.spriteKey      = spriteKey;
    }
}
