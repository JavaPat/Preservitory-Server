package com.classic.preservitory.server.dialogue;

import com.classic.preservitory.server.definitions.DialogueDefinition;
import com.classic.preservitory.server.definitions.DialogueNode;

/**
 * Per-player runtime state for an in-progress dialogue.
 *
 * <p>Created when a player talks to an NPC and destroyed once the last line
 * is acknowledged.  Only one dialogue can be active per player at a time.
 *
 * <p>Supports two traversal modes:
 * <ul>
 *   <li><strong>Linear</strong> — classic flat list of lines.  Use
 *       {@link #getCurrentLine()}, {@link #hasNext()}, {@link #next()},
 *       {@link #isFinished()}.</li>
 *   <li><strong>Node-based</strong> — branching node tree.  Use
 *       {@link #getCurrentNode()}, {@link #hasOptions()},
 *       {@link #selectOption(int)}, {@link #advanceNode()},
 *       {@link #isNodeTerminal()}.</li>
 * </ul>
 *
 * <p>Post-dialogue actions (shop open, quest transitions) are stored here so
 * that they fire after the player has read the final line, not when dialogue starts.
 * For node-based dialogues the {@code action} field on the terminal
 * {@link DialogueNode} drives the quest transition instead of {@link #questAction}.
 */
public final class DialogueSession {

    /** Quest state transition to execute when this dialogue session closes (linear mode). */
    public enum QuestAction { NONE, START, COMPLETE, ADVANCE_STAGE }

    private final DialogueDefinition definition;

    /** NPC instance id (e.g. "npc_0") — used in outgoing packets. */
    public final String npcInstanceId;

    /** Open this shop when the last line is acknowledged. Null = no shop. */
    public final String shopIdAfter;

    /** Quest ID associated with the pending action. 0 when {@link #questAction} is NONE. */
    public final int questId;

    /**
     * Quest state transition for linear dialogues.
     * Node-based dialogues use {@link DialogueNode#action} instead.
     */
    public final QuestAction questAction;

    // ---- Linear traversal state ----
    private int index = 0;

    // ---- Node-based traversal state ----
    /** Current node id; null when dialogue has been closed via a null-next option. */
    private String currentNodeId;

    public DialogueSession(DialogueDefinition definition,
                           String npcInstanceId,
                           String shopIdAfter,
                           int questId,
                           QuestAction questAction) {
        this.definition    = definition;
        this.npcInstanceId = npcInstanceId;
        this.shopIdAfter   = shopIdAfter;
        this.questId       = questId;
        this.questAction   = questAction != null ? questAction : QuestAction.NONE;
        this.currentNodeId = definition.isNodeBased() ? definition.startNodeId : null;
    }

    // -----------------------------------------------------------------------
    //  Mode query
    // -----------------------------------------------------------------------

    public boolean isNodeBased() { return definition.isNodeBased(); }

    // -----------------------------------------------------------------------
    //  Node-based API
    // -----------------------------------------------------------------------

    /**
     * Returns the current {@link DialogueNode}, or {@code null} if the current
     * node id has been set to {@code null} (e.g. a null-next option was chosen).
     */
    public DialogueNode getCurrentNode() {
        if (!isNodeBased() || currentNodeId == null) return null;
        return definition.nodes.get(currentNodeId);
    }

    /** True when the current node has player-selectable options. */
    public boolean hasOptions() {
        DialogueNode node = getCurrentNode();
        return node != null && node.hasOptions();
    }

    /**
     * Selects the option at {@code index} and updates {@link #currentNodeId}
     * to the option's {@code next} value (which may be {@code null} = close).
     * No-op if the index is out of range.
     */
    public void selectOption(int index) {
        DialogueNode node = getCurrentNode();
        if (node == null || index < 0 || index >= node.options.size()) return;
        currentNodeId = node.options.get(index).next;
    }

    /**
     * Advances to the next node by following the current node's {@code next}
     * reference.  No-op if the current node is terminal or null.
     */
    public void advanceNode() {
        DialogueNode node = getCurrentNode();
        if (node != null) currentNodeId = node.next;
    }

    /**
     * Returns {@code true} when there is no further node to advance to:
     * the current node has no options and no {@code next}, or the
     * current node id is {@code null}.
     */
    public boolean isNodeTerminal() {
        DialogueNode node = getCurrentNode();
        return node == null || node.isTerminal();
    }

    // -----------------------------------------------------------------------
    //  Linear API (kept for backward compatibility)
    // -----------------------------------------------------------------------

    /** The line the player is currently reading (linear mode). */
    public String getCurrentLine() {
        if (isNodeBased()) {
            DialogueNode node = getCurrentNode();
            return node != null ? node.text : "";
        }
        return definition.lines.get(index);
    }

    /** True if there is at least one more line after the current one (linear mode). */
    public boolean hasNext() {
        return index + 1 < definition.lines.size();
    }

    /** Advance to the next line (linear mode). No-op if already at the last line. */
    public void next() {
        if (hasNext()) index++;
    }

    /** True when the current line is the last one (linear mode). */
    public boolean isFinished() {
        return !hasNext();
    }
}
