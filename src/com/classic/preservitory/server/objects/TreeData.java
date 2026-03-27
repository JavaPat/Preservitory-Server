package com.classic.preservitory.server.objects;

public class TreeData {

    public final String id;
    public final String typeId;
    public final int    x;
    public final int    y;
    public volatile boolean alive;
    public volatile long    respawnTime; // epoch-ms; 0 while alive

    public TreeData(String id, String typeId, int x, int y) {
        this.id          = id;
        this.typeId      = typeId;
        this.x           = x;
        this.y           = y;
        this.alive       = true;
        this.respawnTime = 0L;
    }

    /** Used when loading from the persistence file. */
    public TreeData(String id, String typeId, int x, int y, boolean alive, long respawnTime) {
        this.id          = id;
        this.typeId      = typeId;
        this.x           = x;
        this.y           = y;
        this.alive       = alive;
        this.respawnTime = respawnTime;
    }
}
