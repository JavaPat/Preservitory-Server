package com.classic.preservitory.server.quest;

/**
 * Mutable runtime quest progress for one player.
 * Replaces the bare {@link QuestState} previously stored in {@code PlayerSession#quests}.
 *
 * Serialized as {@code "STATE:stageId:progress"} (e.g. {@code "IN_PROGRESS:1:3"}).
 * Legacy values with one colon are treated as progressAmount=0.
 * Legacy values with no colon are treated as stageId=0 and progressAmount=0.
 */
public final class QuestProgress {

    public QuestState state;
    public int        currentStageId;
    /** How many items the player has gathered toward the current GATHER objective. */
    public int        progressAmount;

    public QuestProgress(QuestState state, int currentStageId) {
        this.state          = state;
        this.currentStageId = currentStageId;
        this.progressAmount = 0;
    }

    /** Serializes to {@code "STATE:stageId:progress"} for storage in player save files. */
    public String serialize() {
        return state.name() + ":" + currentStageId + ":" + progressAmount;
    }

    /**
     * Parses a value produced by {@link #serialize()}, or legacy formats.
     * Returns NOT_STARTED/0/0 on any parse failure.
     */
    public static QuestProgress deserialize(String value) {
        if (value == null) return new QuestProgress(QuestState.NOT_STARTED, 0);
        String[] parts = value.split(":", 3);
        if (parts.length == 1) {
            // Legacy: bare state name
            try {
                return new QuestProgress(QuestState.valueOf(parts[0]), 0);
            } catch (IllegalArgumentException ignored) {
                return new QuestProgress(QuestState.NOT_STARTED, 0);
            }
        }
        try {
            QuestState state   = QuestState.valueOf(parts[0]);
            int        stageId = Integer.parseInt(parts[1]);
            int        progress = parts.length >= 3 ? Integer.parseInt(parts[2]) : 0;
            QuestProgress qp = new QuestProgress(state, stageId);
            qp.progressAmount = Math.max(0, progress);
            return qp;
        } catch (Exception ignored) {
            return new QuestProgress(QuestState.NOT_STARTED, 0);
        }
    }
}
