package com.classic.preservitory.server.definitions;

import java.util.List;
import java.util.Map;

/**
 * Immutable definition of a single dialogue block, loaded from {@code cache/dialogues/*.json}.
 *
 * <p>Supports two formats:
 * <ul>
 *   <li><strong>Linear</strong> — a {@code "lines"} JSON array; each entry is displayed
 *       in order.  {@link #isNodeBased()} returns {@code false}.</li>
 *   <li><strong>Node-based</strong> — a {@code "nodes"} JSON array of {@link DialogueNode}
 *       objects, supporting branching options.  {@link #isNodeBased()} returns {@code true}.</li>
 * </ul>
 *
 * <p>Use {@link DialogueDefinitionManager#get(String)} to look up by id, or
 * {@link DialogueDefinitionManager#getByNpcId(int)} to get the primary (first) dialogue
 * for a simple non-quest NPC.
 */
public final class DialogueDefinition {

    /** Unique string identifier, e.g. {@code "guide_start"}, {@code "merchant_default"}. */
    public final String id;

    /** Numeric NPC id that speaks this dialogue. Must exist in {@link NpcDefinitionManager}. */
    public final int npcId;

    /**
     * Ordered lines for linear dialogues. Empty for node-based dialogues.
     * @see #isNodeBased()
     */
    public final List<String> lines;

    /**
     * Node map for branching dialogues, keyed by node id.
     * Empty for linear dialogues.
     */
    public final Map<String, DialogueNode> nodes;

    /**
     * Id of the first node to display. {@code null} for linear dialogues.
     */
    public final String startNodeId;

    /** Constructs a linear (lines-based) dialogue definition. */
    public DialogueDefinition(String id, int npcId, List<String> lines) {
        this.id          = id;
        this.npcId       = npcId;
        this.lines       = List.copyOf(lines);
        this.nodes       = Map.of();
        this.startNodeId = null;
    }

    /** Constructs a node-based (branching) dialogue definition. */
    public DialogueDefinition(String id, int npcId,
                              Map<String, DialogueNode> nodes, String startNodeId) {
        this.id          = id;
        this.npcId       = npcId;
        this.lines       = List.of();
        this.nodes       = Map.copyOf(nodes);
        this.startNodeId = startNodeId;
    }

    /** Returns {@code true} if this dialogue uses the node-based (branching) format. */
    public boolean isNodeBased() { return startNodeId != null; }
}
