package com.classic.preservitory.server.objects;

public class RockData {

    public final String id;
    public final String typeId;
    public final int    x;
    public final int    y;
    public volatile boolean alive;
    public volatile long    respawnTime; // countdown ms remaining; 0 while solid

    public RockData(String id, String typeId, int x, int y) {
        this.id          = id;
        this.typeId      = typeId;
        this.x           = x;
        this.y           = y;
        this.alive       = true;
        this.respawnTime = 0L;
    }
}
