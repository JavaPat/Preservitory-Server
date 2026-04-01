package com.classic.preservitory.server.spawns;

/** A single spawn point loaded from a JSON file in cache/spawns/. */
public final class SpawnEntry {

    /** Unique spawn ID used for entity tracking and debug output. */
    public final String id;
    public final int    definitionId;
    public final int    x;
    public final int    y;

    public SpawnEntry(String id, int definitionId, int x, int y) {
        this.id           = id;
        this.definitionId = definitionId;
        this.x            = x;
        this.y            = y;
    }
}
