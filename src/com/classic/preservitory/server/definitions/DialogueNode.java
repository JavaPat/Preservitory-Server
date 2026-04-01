package com.classic.preservitory.server.definitions;

import java.util.List;

/**
 * A single node in a branching {@link DialogueDefinition}.
 *
 * <p>A node with a non-empty {@link #options} list presents the player with
 * choices; {@link #next} is ignored in that case.  A node with no options
 * is linear — the dialogue advances automatically to {@link #next} when the
 * player clicks "continue".  A node with no options and {@code next == null}
 * is <em>terminal</em>: closing it fires the optional {@link #action}.
 *
 * <p>Supported {@link #action} values:
 * <ul>
 *   <li>{@code "START_QUEST"}    — starts the quest bound to this dialogue's NPC</li>
 *   <li>{@code "COMPLETE_QUEST"} — completes the quest (requirements already checked)</li>
 * </ul>
 */
public final class DialogueNode {

    public final String id;
    public final String text;
    /** Player-selectable options.  Empty list = linear node. */
    public final List<DialogueOption> options;
    /** Next node id for linear flow.  {@code null} = terminal. */
    public final String next;
    /** Optional action string fired when this node closes the dialogue. */
    public final String action;

    public DialogueNode(String id,
                        String text,
                        List<DialogueOption> options,
                        String next,
                        String action) {
        this.id      = id;
        this.text    = text;
        this.options = (options != null && !options.isEmpty()) ? List.copyOf(options) : List.of();
        this.next    = next;
        this.action  = action;
    }

    public boolean hasOptions() { return !options.isEmpty(); }
    public boolean isTerminal() { return !hasOptions() && next == null; }
}
