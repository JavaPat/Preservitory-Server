package com.classic.preservitory.server.objects;

/**
 * A ground-loot item in the world — identified by integer itemId, not item name.
 */
public class LootData {

    public final String id;       // unique loot instance ID (e.g. "L1")
    public final int    x;
    public final int    y;
    public final int    itemId;   // references ItemDefinition
    public final int    count;
    public final String ownerId;
    public final long   spawnTime;

    public LootData(String id, int x, int y, int itemId, int count, String ownerId, long spawnTime) {
        this.id     = id;
        this.x      = x;
        this.y      = y;
        this.itemId = itemId;
        this.count  = count;
        this.ownerId = ownerId;
        this.spawnTime = spawnTime;
    }
}
