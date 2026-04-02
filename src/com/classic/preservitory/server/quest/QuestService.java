package com.classic.preservitory.server.quest;

import com.classic.preservitory.server.definitions.QuestDefinition;
import com.classic.preservitory.server.definitions.QuestDefinitionManager;
import com.classic.preservitory.server.definitions.QuestObjective;
import com.classic.preservitory.server.definitions.QuestStage;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillService;

import java.util.ArrayList;
import java.util.Map;

/**
 * All quest-state transitions and reward logic.
 *
 * GameServer holds one shared instance and delegates to it from handleTalk().
 * No quest-specific knowledge lives in GameServer — all of it is here.
 */
public class QuestService {

    private final BroadcastService broadcastService;

    public QuestService(BroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    // -----------------------------------------------------------------------
    //  State queries
    // -----------------------------------------------------------------------

    public QuestProgress getProgress(PlayerSession session, int questId) {
        QuestProgress p = session.quests.get(questId);
        return p != null ? p : new QuestProgress(QuestState.NOT_STARTED, 0);
    }

    public QuestState getState(PlayerSession session, int questId) {
        return getProgress(session, questId).state;
    }

    /**
     * Returns the {@link QuestStage} with the given id within {@code quest.stages},
     * or {@code null} if not found.
     */
    public QuestStage getCurrentStage(QuestDefinition quest, int stageId) {
        for (QuestStage s : quest.stages) {
            if (s.id == stageId) return s;
        }
        return null;
    }

    // -----------------------------------------------------------------------
    //  Transitions
    // -----------------------------------------------------------------------

    /**
     * Mark the quest as started.  Only transitions from NOT_STARTED.
     * For staged quests, auto-advances past any initial stage that has neither
     * a dialogueId nor an objective so the journal shows a meaningful first task.
     */
    public void startQuest(PlayerSession session, QuestDefinition quest) {
        QuestProgress progress = new QuestProgress(QuestState.IN_PROGRESS, 0);
        session.quests.put(quest.id, progress);

        // Auto-skip marker stage(s) at the start (no dialogue, no objective, no action)
        if (!quest.stages.isEmpty()) {
            QuestStage stage = getCurrentStage(quest, 0);
            if (stage != null && stage.dialogueId == null && stage.objective == null
                    && stage.action == null) {
                QuestStage next = getCurrentStage(quest, 1);
                if (next != null) progress.currentStageId = 1;
            }
        }

        broadcastService.sendToPlayer(session.id, "QUEST_START\t" + quest.name);
        broadcastService.sendToPlayer(session.id, buildQuestLogPacket(session));
        System.out.println("[QuestService] Quest started: " + quest.name
                + " for player " + session.username);
    }

    /**
     * Returns true if the player's inventory satisfies every requirement defined
     * in {@link QuestDefinition#requiredItems}.  Does NOT modify any state.
     * Used by the single-stage (legacy) quest path only.
     */
    public boolean requirementsMet(PlayerSession session, QuestDefinition quest) {
        for (Map.Entry<Integer, Integer> req : quest.requiredItems.entrySet()) {
            if (session.inventory.countOf(req.getKey()) < req.getValue()) return false;
        }
        return true;
    }

    /**
     * Advance the player to the next stage of a staged quest.
     * If the current stage has {@code action="COMPLETE_QUEST"}, the quest is completed instead.
     * Should be called inside {@code synchronized(session)}.
     */
    public void advanceStage(PlayerSession session, QuestDefinition quest) {
        QuestProgress progress = session.quests.get(quest.id);
        if (progress == null || progress.state != QuestState.IN_PROGRESS) return;

        QuestStage currentStage = getCurrentStage(quest, progress.currentStageId);
        if (currentStage == null) return;

        if ("COMPLETE_QUEST".equals(currentStage.action)) {
            completeQuest(session, quest);
            return;
        }

        int nextId = progress.currentStageId + 1;
        QuestStage nextStage = getCurrentStage(quest, nextId);
        if (nextStage == null) {
            System.err.println("[QuestService] Quest '" + quest.key
                    + "' has no stage " + nextId + " — cannot advance for " + session.username);
            return;
        }

        progress.currentStageId = nextId;
        progress.progressAmount = 0;

        // Notify the client of the new objective so it appears in the chatbox
        if (nextStage.description != null && !nextStage.description.isEmpty()) {
            broadcastService.sendToPlayer(session.id,
                    "QUEST_STAGE\t" + quest.name + "\t" + nextStage.description);
        }

        broadcastService.sendToPlayer(session.id, buildQuestLogPacket(session));
        System.out.println("[QuestService] Quest '" + quest.name + "' → stage " + nextId
                + " for player " + session.username);

        // Auto-advance again if the new stage is also a marker (no dialogue, no objective, no action)
        if (nextStage.dialogueId == null && nextStage.objective == null
                && nextStage.action == null) {
            advanceStage(session, quest);
        }
    }

    /**
     * Complete the quest: consume required items (single-stage quests only), grant rewards,
     * mark COMPLETED.
     */
    public void completeQuest(PlayerSession session, QuestDefinition quest) {
        // Consume required items (single-stage quests only — staged quests manage items via objectives)
        if (quest.stages.isEmpty()) {
            for (Map.Entry<Integer, Integer> req : quest.requiredItems.entrySet()) {
                session.inventory.removeItem(req.getKey(), req.getValue());
            }
        }

        // Grant item rewards — log and notify if the inventory is full rather than silently losing items.
        // TODO: once LootManager supports player-triggered spawns, drop the item at the player's feet.
        for (Map.Entry<Integer, Integer> reward : quest.rewardItems.entrySet()) {
            if (!session.inventory.addItem(reward.getKey(), reward.getValue())) {
                System.err.println("[QuestService] Inventory full — reward item " + reward.getKey()
                        + " x" + reward.getValue() + " not granted to " + session.username
                        + " on quest '" + quest.key + "'. TODO: spawn on ground.");
                session.sendInventoryFullMessage();
            }
        }

        // Grant XP reward
        boolean xpGranted = false;
        if (quest.rewardXp > 0 && !quest.rewardXpSkill.isBlank()) {
            try {
                Skill skill = Skill.valueOf(quest.rewardXpSkill.toUpperCase());
                session.skills.addXp(skill, quest.rewardXp);
                broadcastService.sendToPlayer(session.id,
                        "SKILL_XP " + quest.rewardXpSkill.toLowerCase() + " " + quest.rewardXp);
                ClientHandler h = session.getHandler();
                if (h != null) h.send(SkillService.buildSkillsPacket(session));
                xpGranted = true;
            } catch (IllegalArgumentException ignored) {
                System.err.println("[QuestService] Unknown skill '" + quest.rewardXpSkill
                        + "' in quest '" + quest.key + "'");
            }
        }

        // Mark complete and push inventory to client
        QuestProgress progress = session.quests.computeIfAbsent(quest.id,
                k -> new QuestProgress(QuestState.IN_PROGRESS, 0));
        progress.state = QuestState.COMPLETED;
        ClientHandler h = session.getHandler();
        if (h != null) h.send(session.inventory.buildSnapshot());

        // Send feedback packets — order: completion, then each reward, then xp, then log.
        broadcastService.sendToPlayer(session.id, "QUEST_COMPLETE\t" + quest.name);
        for (Map.Entry<Integer, Integer> reward : quest.rewardItems.entrySet()) {
            broadcastService.sendToPlayer(session.id,
                    "QUEST_REWARD\t" + reward.getKey() + "\t" + reward.getValue());
        }
        if (xpGranted) {
            broadcastService.sendToPlayer(session.id,
                    "QUEST_XP\t" + quest.rewardXpSkill.toLowerCase() + "\t" + quest.rewardXp);
        }

        broadcastService.sendToPlayer(session.id, buildQuestLogPacket(session));
        System.out.println("[QuestService] Quest complete: " + quest.name
                + " for player " + session.username);
    }

    /**
     * Called when the player kills an enemy.
     * Increments kill count for any active KILL objective matching {@code enemyDefinitionId}.
     */
    public void checkAndAdvanceKillObjective(PlayerSession session, int enemyDefinitionId) {
        for (Map.Entry<Integer, QuestProgress> entry :
                new ArrayList<>(session.quests.entrySet())) {

            QuestProgress progress = entry.getValue();
            if (progress.state != QuestState.IN_PROGRESS) continue;

            QuestDefinition quest = QuestDefinitionManager.get(entry.getKey());
            if (quest == null || quest.stages.isEmpty()) continue;

            QuestStage stage = getCurrentStage(quest, progress.currentStageId);
            if (stage == null || stage.objective == null) continue;
            if (stage.objective.type != QuestObjective.Type.KILL) continue;
            if (stage.objective.targetId != enemyDefinitionId) continue;

            if (progress.progressAmount < stage.objective.amount) {
                progress.progressAmount++;
                if (progress.progressAmount >= stage.objective.amount) {
                    broadcastService.sendToPlayer(session.id,
                            "QUEST_OBJECTIVE_COMPLETE\t" + quest.name);
                    advanceStage(session, quest);
                } else {
                    broadcastService.sendToPlayer(session.id, buildQuestLogPacket(session));
                }
            }
        }
    }

    /**
     * Called after the player gathers an item (chop/mine/pickup).
     * Increments per-stage progress and advances the stage when the objective is met.
     */
    public void checkAndAdvanceGatherObjective(PlayerSession session, int itemId) {
        for (Map.Entry<Integer, QuestProgress> entry :
                new ArrayList<>(session.quests.entrySet())) {

            QuestProgress progress = entry.getValue();
            if (progress.state != QuestState.IN_PROGRESS) continue;

            QuestDefinition quest = QuestDefinitionManager.get(entry.getKey());
            if (quest == null || quest.stages.isEmpty()) continue;

            QuestStage stage = getCurrentStage(quest, progress.currentStageId);
            if (stage == null || stage.objective == null) continue;
            if (stage.objective.type != QuestObjective.Type.GATHER) continue;
            if (stage.objective.itemId != itemId) continue;

            // Only act if there is still progress to make — guards against duplicate
            // QUEST_OBJECTIVE_COMPLETE if advanceStage somehow failed to change the stage.
            if (progress.progressAmount < stage.objective.amount) {
                progress.progressAmount++;
                if (progress.progressAmount >= stage.objective.amount) {
                    // Just reached the goal — notify then advance
                    broadcastService.sendToPlayer(session.id,
                            "QUEST_OBJECTIVE_COMPLETE\t" + quest.name);
                    advanceStage(session, quest);
                } else {
                    // Partial progress — push updated log so client shows live counter
                    broadcastService.sendToPlayer(session.id, buildQuestLogPacket(session));
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Quest log packet
    // -----------------------------------------------------------------------

    /**
     * Builds the full QUEST_LOG packet for the given session.
     *
     * Format: {@code QUEST_LOG\t<id>:<name>:<state>:<stageId>:<progressAmount>:<requiredAmount>:<stageDesc>|...}
     *
     * <ul>
     *   <li>{@code progressAmount} / {@code requiredAmount} — used by the client to render
     *       live objective counters like "(3/5)". Both are 0 for non-GATHER stages.</li>
     *   <li>{@code stageDesc} is always the last field so it may safely contain {@code ':'}.
     *       The {@code '|'} character is stripped to protect the entry separator.</li>
     * </ul>
     *
     * The quest list is sourced from {@link QuestDefinitionManager#values()} so the
     * client always receives every defined quest. Missing player progress defaults
     * to {@link QuestState#NOT_STARTED}.
     */
    public static String buildQuestLogPacket(PlayerSession session) {
        StringBuilder sb = new StringBuilder("QUEST_LOG\t");
        boolean first = true;
        for (QuestDefinition def : QuestDefinitionManager.values()) {
            QuestProgress progress = session.quests.get(def.id);
            if (progress == null) {
                progress = new QuestProgress(QuestState.NOT_STARTED, 0);
            }

            String stageDesc    = "";
            int    progressAmt  = 0;
            int    requiredAmt  = 0;

            if (!def.stages.isEmpty() && progress.state == QuestState.IN_PROGRESS) {
                QuestStage stage = null;
                for (QuestStage s : def.stages) {
                    if (s.id == progress.currentStageId) { stage = s; break; }
                }
                if (stage != null) {
                    stageDesc   = stage.description.replace('|', ' ');
                    if (stage.objective != null
                            && (stage.objective.type == QuestObjective.Type.GATHER
                             || stage.objective.type == QuestObjective.Type.KILL)) {
                        progressAmt = Math.min(progress.progressAmount, stage.objective.amount);
                        requiredAmt = stage.objective.amount;
                    }
                }
            }

            if (!first) sb.append('|');
            sb.append(def.id)
              .append(':').append(def.name)
              .append(':').append(progress.state.name())
              .append(':').append(progress.currentStageId)
              .append(':').append(progressAmt)
              .append(':').append(requiredAmt)
              .append(':').append(stageDesc);
            first = false;
        }
        return sb.toString();
    }
}
