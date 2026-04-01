package com.classic.preservitory.server.woodcutting;

public final class AxeDefinition {

    public final int itemId;
    public final int levelRequired;
    public final double speedMultiplier;

    public AxeDefinition(int itemId, int levelRequired, double speedMultiplier) {
        this.itemId = itemId;
        this.levelRequired = levelRequired;
        this.speedMultiplier = speedMultiplier;
    }
}
