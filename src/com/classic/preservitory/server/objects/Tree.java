package com.classic.preservitory.server.objects;

public class Tree {

    private final String id;
    private final int x;
    private final int y;
    private boolean alive;

    public Tree(String id, int x, int y) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.alive = true;
    }

    public String getId() { return id; }
    public int getX() { return x; }
    public int getY() { return y; }

    public boolean isAlive() { return alive; }
    public void setAlive(boolean alive) { this.alive = alive; }
}
