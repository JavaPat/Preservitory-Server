package com.classic.preservitory.server.objects;

public class LootData {

    public final String id;
    public final int    x;
    public final int    y;
    public final String itemName;
    public final int    count;

    public LootData(String id, int x, int y, String itemName, int count) {
        this.id       = id;
        this.x        = x;
        this.y        = y;
        this.itemName = itemName;
        this.count    = count;
    }
}
