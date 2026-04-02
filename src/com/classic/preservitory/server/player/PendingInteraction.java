package com.classic.preservitory.server.player;

public final class PendingInteraction {

    public final InteractionType type;
    public final String targetId;
    public final int requiredDistance;

    public PendingInteraction(InteractionType type, String targetId, int requiredDistance) {
        this.type = type;
        this.targetId = targetId;
        this.requiredDistance = requiredDistance;
    }
}
