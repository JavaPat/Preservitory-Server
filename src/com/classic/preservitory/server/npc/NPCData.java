package com.classic.preservitory.server.npc;

/** Immutable server-side record for a stationary NPC. */
public class NPCData {

    public final String  id;
    public final int     x;
    public final int     y;
    public final String  name;
    public final boolean shopkeeper;

    public NPCData(String id, int x, int y, String name, boolean shopkeeper) {
        this.id         = id;
        this.x          = x;
        this.y          = y;
        this.name       = name;
        this.shopkeeper = shopkeeper;
    }
}
