package com.classic.preservitory.server.definitions;

/**
 * A single player-selectable option within a {@link DialogueNode}.
 *
 * {@code next} is the id of the node to advance to when this option is
 * selected.  A {@code null} next value means "close dialogue immediately"
 * (e.g. a "Nothing, thanks" option).
 */
public final class DialogueOption {

    public final String text;
    /** Target node id, or {@code null} to close the dialogue. */
    public final String next;

    public DialogueOption(String text, String next) {
        this.text = text;
        this.next = next;
    }
}
