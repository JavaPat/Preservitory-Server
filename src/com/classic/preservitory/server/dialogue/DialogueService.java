package com.classic.preservitory.server.dialogue;

import com.classic.preservitory.server.definitions.DialogueDefinition;
import com.classic.preservitory.server.definitions.DialogueDefinitionManager;
import com.classic.preservitory.server.definitions.DialogueNode;
import com.classic.preservitory.server.definitions.DialogueOption;
import com.classic.preservitory.server.definitions.NpcDefinition;
import com.classic.preservitory.server.definitions.NpcDefinitionManager;
import com.classic.preservitory.server.definitions.QuestDefinition;
import com.classic.preservitory.server.definitions.QuestDefinitionManager;
import com.classic.preservitory.server.definitions.QuestObjective;
import com.classic.preservitory.server.definitions.QuestStage;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.npc.NPCData;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.quest.QuestProgress;
import com.classic.preservitory.server.quest.QuestService;
import com.classic.preservitory.server.quest.QuestState;
import com.classic.preservitory.server.shops.Shop;
import com.classic.preservitory.server.shops.ShopManager;
import com.classic.preservitory.server.world.NPCManager;
import com.classic.preservitory.server.world.TreeManager;
import com.classic.preservitory.util.ValidationUtil;

import java.util.List;
import java.util.Map;

/**
 * Handles all NPC dialogue interactions: starting, advancing, and closing dialogue sessions.
 *
 * Responsible for:
 *   - Determining which dialogue to show based on quest state (not started / in progress / complete)
 *   - Sending DIALOGUE and DIALOGUE_OPTIONS packets
 *   - Firing quest transitions (start / advance / complete) when a dialogue session ends
 *   - Opening shops after quest-complete dialogues
 *
 * Quest state is never modified during handleTalk — transitions are deferred to
 * handleDialogueNext / handleDialogueOption so they only fire after the player
 * has read all lines.
 */
public class DialogueService {

    private static final double INTERACT_RANGE_PX = TreeManager.TILE_SIZE * 1.7;
    private static final double INTERACT_RANGE_SQ  = INTERACT_RANGE_PX * INTERACT_RANGE_PX;

    private final Map<String, PlayerSession> sessions;
    private final NPCManager     npcManager;
    private final QuestService   questService;
    private final ShopManager    shopManager;
    private final BroadcastService broadcastService;

    public DialogueService(Map<String, PlayerSession> sessions,
                           NPCManager npcManager,
                           QuestService questService,
                           ShopManager shopManager,
                           BroadcastService broadcastService) {
        this.sessions         = sessions;
        this.npcManager       = npcManager;
        this.questService     = questService;
        this.shopManager      = shopManager;
        this.broadcastService = broadcastService;
    }

    // -----------------------------------------------------------------------
    //  handleTalk — initiates a dialogue session with an NPC
    // -----------------------------------------------------------------------

    public void handleTalk(String playerId, String npcId) {
        if (!ValidationUtil.isValidObjectId(npcId)) return;

        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.isAlive()) return;
        if (!session.loggedIn) {
            broadcastService.sendToPlayer(playerId, "SYSTEM Login first with /login <name> or /register <name>.");
            return;
        }

        NPCData npc = npcManager.getNpc(npcId);
        if (npc == null) return;
        NpcDefinition definition = NpcDefinitionManager.get(npc.definitionId);
        if (definition == null) return;
        if (!ValidationUtil.isWithinEntityRange(session.x, session.y, (int) npc.x, (int) npc.y, INTERACT_RANGE_SQ)) return;

        session.activeNpcId = npcId;
        session.shopOpen    = false;

        // ---- Determine which dialogue to show and any pending quest action ----
        // Quest state transitions are NOT applied here — they are deferred to
        // handleDialogueNext() / handleDialogueOption() after the player has read all lines.
        DialogueDefinition dlg           = null;
        int                pendingQuestId = 0;
        DialogueSession.QuestAction pendingAction = DialogueSession.QuestAction.NONE;
        String             shopIdAfter    = null;

        if (definition.questId != null) {
            QuestDefinition quest = QuestDefinitionManager.getByKey(definition.questId);
            if (quest != null) {
                QuestState state = questService.getState(session, quest.id);

                if (state == QuestState.NOT_STARTED) {
                    dlg            = getFirstDialogue(quest.startDialogueIds);
                    pendingQuestId = quest.id;
                    pendingAction  = DialogueSession.QuestAction.START;

                } else if (state == QuestState.IN_PROGRESS) {
                    if (!quest.stages.isEmpty()) {
                        // ---- Staged quest path ----
                        QuestProgress progress     = questService.getProgress(session, quest.id);
                        QuestStage    currentStage = questService.getCurrentStage(quest, progress.currentStageId);

                        if (currentStage != null && currentStage.dialogueId != null) {
                            dlg = DialogueDefinitionManager.get(currentStage.dialogueId);
                            if (dlg != null) {
                                pendingQuestId = quest.id;
                                boolean objectiveMet;
                                if (currentStage.objective == null
                                        || currentStage.objective.type == QuestObjective.Type.TALK) {
                                    objectiveMet = true;
                                } else {
                                    objectiveMet = session.inventory.countOf(currentStage.objective.itemId)
                                            >= currentStage.objective.amount;
                                }
                                if (!objectiveMet) {
                                    pendingAction = DialogueSession.QuestAction.NONE;
                                } else if ("COMPLETE_QUEST".equals(currentStage.action)) {
                                    pendingAction = DialogueSession.QuestAction.COMPLETE;
                                } else {
                                    pendingAction = DialogueSession.QuestAction.ADVANCE_STAGE;
                                }
                            }
                        }
                    } else {
                        // ---- Single-stage (legacy) quest path ----
                        if (questService.requirementsMet(session, quest)) {
                            dlg            = getFirstDialogue(quest.readyToCompleteDialogueIds);
                            pendingQuestId = quest.id;
                            pendingAction  = DialogueSession.QuestAction.COMPLETE;
                        } else {
                            dlg = getFirstDialogue(quest.inProgressDialogueIds);
                        }
                    }

                } else { // COMPLETED
                    dlg = getFirstDialogue(quest.completedDialogueIds);
                    if (definition.shopkeeper) shopIdAfter = definition.shopId;
                }
            } else {
                // Quest key referenced by NPC definition not found — treat as plain NPC.
                dlg = DialogueDefinitionManager.getByNpcId(definition.id);
                if (definition.shopkeeper) shopIdAfter = definition.shopId;
            }
        } else {
            dlg = DialogueDefinitionManager.getByNpcId(definition.id);
            if (definition.shopkeeper) shopIdAfter = definition.shopId;
        }

        if (dlg == null) return;

        // For node-based dialogues the terminal node's action field drives quest
        // transitions — clear the session-level pendingAction so it does not fire twice.
        if (dlg.isNodeBased()) pendingAction = DialogueSession.QuestAction.NONE;

        // ---- Close any previous dialogue before starting a new one ----
        DialogueSession previous = session.activeDialogue;
        if (previous != null) {
            ClientHandler ph = session.getHandler();
            if (ph != null) ph.send("DIALOGUE_CLOSE\t" + previous.npcInstanceId);
        }

        // ---- Start dialogue session — send first line ----
        session.activeDialogue = new DialogueSession(dlg, npcId, shopIdAfter, pendingQuestId, pendingAction);
        sendDialogueLine(session, session.activeDialogue.getCurrentLine(), npcId);
    }

    // -----------------------------------------------------------------------
    //  handleDialogueNext — player acknowledged the current line
    // -----------------------------------------------------------------------

    public void handleDialogueNext(String playerId) {
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.loggedIn) return;

        // Synchronize on the session to prevent two rapid DIALOGUE_NEXT packets
        // from advancing the index twice concurrently.
        synchronized (session) {
            DialogueSession dialogue = session.activeDialogue;
            if (dialogue == null) return;

            if (dialogue.isNodeBased()) {
                // ---- Node-based dialogue ----
                if (dialogue.hasOptions()) {
                    // Player must select an option — DIALOGUE_NEXT is ignored
                    return;
                }
                if (!dialogue.isNodeTerminal()) {
                    dialogue.advanceNode();
                    // Guard: if the target node id is missing from the map (should be caught
                    // by the loader, but defend at runtime), close cleanly with a warning.
                    if (dialogue.getCurrentNode() == null) {
                        System.err.println("[DialogueService] Dialogue node not found after advance"
                                + " — closing session for " + playerId);
                        closeDialogueSession(session, dialogue);
                        return;
                    }
                    sendDialogueLine(session, dialogue.getCurrentLine(), dialogue.npcInstanceId);
                } else {
                    // Terminal node acknowledged — fire node action then close
                    fireNodeAction(session, dialogue);
                    closeDialogueSession(session, dialogue);
                }

            } else {
                // ---- Linear dialogue ----
                if (dialogue.hasNext()) {
                    dialogue.next();
                    sendDialogueLine(session, dialogue.getCurrentLine(), dialogue.npcInstanceId);
                } else {
                    // Last line acknowledged — execute deferred quest action then close.
                    if (dialogue.questAction != DialogueSession.QuestAction.NONE) {
                        QuestDefinition quest = QuestDefinitionManager.get(dialogue.questId);
                        if (quest != null) {
                            if (dialogue.questAction == DialogueSession.QuestAction.START
                                    && questService.getState(session, quest.id) == QuestState.NOT_STARTED) {
                                questService.startQuest(session, quest);

                            } else if (dialogue.questAction == DialogueSession.QuestAction.COMPLETE
                                    && questService.getState(session, quest.id) == QuestState.IN_PROGRESS) {
                                questService.completeQuest(session, quest);

                            } else if (dialogue.questAction == DialogueSession.QuestAction.ADVANCE_STAGE
                                    && questService.getState(session, quest.id) == QuestState.IN_PROGRESS) {
                                questService.advanceStage(session, quest);
                            }
                        }
                    }
                    closeDialogueSession(session, dialogue);
                }
            }
        }
    }

    // -----------------------------------------------------------------------
    //  handleDialogueOption — player selected a branching option (0-based index)
    // -----------------------------------------------------------------------

    public void handleDialogueOption(String playerId, int optionIndex) {
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.loggedIn) return;

        synchronized (session) {
            DialogueSession dialogue = session.activeDialogue;
            if (dialogue == null || !dialogue.isNodeBased() || !dialogue.hasOptions()) return;

            DialogueNode node = dialogue.getCurrentNode();
            if (node == null || optionIndex < 0 || optionIndex >= node.options.size()) {
                System.err.println("[DialogueService] Invalid dialogue option index " + optionIndex
                        + " (node=" + node + ") from player " + playerId);
                return;
            }

            dialogue.selectOption(optionIndex);

            DialogueNode next = dialogue.getCurrentNode();
            if (next == null) {
                // Option with null next — close dialogue without firing any action
                closeDialogueSession(session, dialogue);
            } else {
                sendDialogueLine(session, next.text, dialogue.npcInstanceId);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Private helpers
    // -----------------------------------------------------------------------

    private void sendDialogueLine(PlayerSession session, String line, String npcInstanceId) {
        ClientHandler h = session.getHandler();
        if (h == null) return;
        h.send("DIALOGUE\t" + npcInstanceId + "\t" + line);

        // If the current node has options, send them as a follow-up packet.
        DialogueSession ds = session.activeDialogue;
        if (ds != null && ds.isNodeBased() && ds.hasOptions()) {
            DialogueNode node = ds.getCurrentNode();
            StringBuilder sb = new StringBuilder("DIALOGUE_OPTIONS");
            for (DialogueOption opt : node.options) {
                sb.append('\t').append(opt.text);
            }
            h.send(sb.toString());
        }
    }

    /**
     * Fires the {@link DialogueNode#action} of the current terminal node
     * (node-based dialogues only).
     * Must be called inside {@code synchronized(session)}.
     */
    private void fireNodeAction(PlayerSession session, DialogueSession dialogue) {
        DialogueNode node = dialogue.getCurrentNode();
        if (node == null || node.action == null) return;

        QuestDefinition quest = QuestDefinitionManager.get(dialogue.questId);
        if (quest == null) return;

        switch (node.action) {
            case "START_QUEST":
                if (questService.getState(session, quest.id) == QuestState.NOT_STARTED)
                    questService.startQuest(session, quest);
                break;
            case "COMPLETE_QUEST":
                if (questService.getState(session, quest.id) == QuestState.IN_PROGRESS)
                    questService.completeQuest(session, quest);
                break;
            case "ADVANCE_STAGE":
                if (questService.getState(session, quest.id) == QuestState.IN_PROGRESS)
                    questService.advanceStage(session, quest);
                break;
            default:
                System.err.println("[DialogueService] Unknown node action '" + node.action
                        + "' in dialogue questId=" + dialogue.questId);
        }
    }

    /**
     * Clears the active dialogue, sends DIALOGUE_CLOSE, and opens any pending shop.
     * Must be called inside {@code synchronized(session)}.
     */
    private void closeDialogueSession(PlayerSession session, DialogueSession dialogue) {
        session.activeDialogue = null;
        ClientHandler h = session.getHandler();
        if (h != null) h.send("DIALOGUE_CLOSE\t" + dialogue.npcInstanceId);

        String shopId = dialogue.shopIdAfter;
        if (shopId != null) {
            session.shopOpen = true;
            Shop shop = shopManager.getShop(shopId);
            if (h != null && shop != null) {
                h.send(shop.buildSnapshot());
            } else if (shop == null) {
                System.err.println("[DialogueService] Shop not found for id: " + shopId);
            }
        }
    }

    private DialogueDefinition getFirstDialogue(List<String> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return DialogueDefinitionManager.get(ids.get(0));
    }
}
