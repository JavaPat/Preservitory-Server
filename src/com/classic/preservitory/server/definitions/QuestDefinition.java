package com.classic.preservitory.server.definitions;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Immutable definition of a quest, loaded from {@code cache/quests/*.json}.
 *
 * Dialogue selection per player state:
 *   NOT_STARTED            → startDialogueIds
 *   IN_PROGRESS (no items) → inProgressDialogueIds
 *   IN_PROGRESS (items ok) → readyToCompleteDialogueIds  (quest completes on this talk)
 *   COMPLETED              → completedDialogueIds
 */
public final class QuestDefinition {

    /** Numeric ID — primary key used in player save data. */
    public final int    id;

    /** String key matching the JSON filename and NpcDefinition.questId. */
    public final String key;

    public final String name;

    /** Dialogue shown when the player has never started this quest. */
    public final List<String> startDialogueIds;

    /** Dialogue shown while the quest is active but requirements are not yet met. */
    public final List<String> inProgressDialogueIds;

    /** Dialogue shown at the moment requirements are met — quest completes during this talk. */
    public final List<String> readyToCompleteDialogueIds;

    /** Dialogue shown on every subsequent visit after the quest is complete. */
    public final List<String> completedDialogueIds;

    /** itemId → quantity the player must have in inventory to complete the quest. */
    public final Map<Integer, Integer> requiredItems;

    /** itemId → quantity awarded on completion. */
    public final Map<Integer, Integer> rewardItems;

    /** Flat XP amount granted to {@link #rewardXpSkill} on completion (0 = none). */
    public final int    rewardXp;

    /** Skill name (uppercase, e.g. "WOODCUTTING") that receives {@link #rewardXp}. */
    public final String rewardXpSkill;

    /**
     * Ordered list of quest stages. Empty for single-stage (legacy) quests —
     * those use {@link #readyToCompleteDialogueIds} / {@link #requiredItems} instead.
     */
    public final List<QuestStage> stages;

    public QuestDefinition(int id,
                           String key,
                           String name,
                           List<String> startDialogueIds,
                           List<String> inProgressDialogueIds,
                           List<String> readyToCompleteDialogueIds,
                           List<String> completedDialogueIds,
                           Map<Integer, Integer> requiredItems,
                           Map<Integer, Integer> rewardItems,
                           int rewardXp,
                           String rewardXpSkill,
                           List<QuestStage> stages) {
        this.id                        = id;
        this.key                       = key;
        this.name                      = name;
        this.startDialogueIds          = Collections.unmodifiableList(startDialogueIds);
        this.inProgressDialogueIds     = Collections.unmodifiableList(inProgressDialogueIds);
        this.readyToCompleteDialogueIds = Collections.unmodifiableList(readyToCompleteDialogueIds);
        this.completedDialogueIds      = Collections.unmodifiableList(completedDialogueIds);
        this.requiredItems             = Collections.unmodifiableMap(requiredItems);
        this.rewardItems               = Collections.unmodifiableMap(rewardItems);
        this.rewardXp                  = rewardXp;
        this.rewardXpSkill             = rewardXpSkill != null ? rewardXpSkill : "";
        this.stages                    = stages != null
                                         ? Collections.unmodifiableList(new ArrayList<>(stages))
                                         : Collections.emptyList();
    }
}
