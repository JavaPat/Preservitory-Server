package com.classic.preservitory.server.objects;

public class TreeData {

    public final String id;

    /** String key sent over the wire to the client for sprite selection. */
    public final String typeId;

    /** Numeric ID into {@link com.classic.preservitory.server.definitions.ObjectDefinitionManager}. */
    public final int    definitionId;

    public final int    x;
    public final int    y;
    public volatile boolean alive;
    public volatile long    respawnTime; // countdown ms remaining; 0 while alive

    public TreeData(String id, String typeId, int definitionId, int x, int y) {
        this.id           = id;
        this.typeId       = typeId;
        this.definitionId = definitionId;
        this.x            = x;
        this.y            = y;
        this.alive        = true;
        this.respawnTime  = 0L;
    }

    /** Used when loading from the persistence file. */
    public TreeData(String id, String typeId, int definitionId, int x, int y,
                    boolean alive, long respawnTime) {
        this.id           = id;
        this.typeId       = typeId;
        this.definitionId = definitionId;
        this.x            = x;
        this.y            = y;
        this.alive        = alive;
        this.respawnTime  = respawnTime;
    }
}
