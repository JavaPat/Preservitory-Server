package com.classic.preservitory.server.definitions;

/**
 * Defines what a player must do to complete a {@link QuestStage}.
 * Currently only GATHER is tracked automatically by the server;
 * TALK is resolved by the dialogue system reaching a stage's dialogueId.
 */
public final class QuestObjective {

    public enum Type { GATHER, TALK, KILL }

    public final Type type;
    /** Item ID the player must have in inventory (GATHER only). */
    public final int  itemId;
    /** Quantity required (GATHER and KILL). */
    public final int  amount;
    /** Enemy definition ID the player must kill (KILL only). */
    public final int  targetId;

    public QuestObjective(Type type, int itemId, int amount, int targetId) {
        this.type     = type;
        this.itemId   = itemId;
        this.amount   = amount;
        this.targetId = targetId;
    }
}
