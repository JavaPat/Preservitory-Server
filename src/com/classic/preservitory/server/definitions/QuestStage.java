package com.classic.preservitory.server.definitions;

/**
 * A single step within a {@link QuestDefinition#stages} list.
 *
 * Fields:
 *   id          — 0-based stage index
 *   description — text shown in the quest journal detail pane for this stage
 *   dialogueId  — dialogue shown when the player talks to the NPC at this stage; null = no dialogue
 *   objective   — item/talk objective the player must complete before the stage advances; null = none
 *   action      — "COMPLETE_QUEST" to end the quest when the player reaches and completes this stage;
 *                 null for normal advancement to the next stage
 */
public final class QuestStage {

    public final int            id;
    public final String         description;
    public final String         dialogueId;   // null = no dialogue for this stage
    public final QuestObjective objective;    // null = no item/talk objective
    public final String         action;       // "COMPLETE_QUEST" or null

    public QuestStage(int id, String description, String dialogueId,
                      QuestObjective objective, String action) {
        this.id          = id;
        this.description = description != null ? description : "";
        this.dialogueId  = (dialogueId  != null && !dialogueId.isEmpty())  ? dialogueId  : null;
        this.objective   = objective;
        this.action      = (action      != null && !action.isEmpty())      ? action      : null;
    }
}
