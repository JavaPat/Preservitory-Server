package com.classic.preservitory.server.quest;

/**
 * Data model for a single quest.
 *
 * The "Getting Started" quest has three stages:
 *   NOT_STARTED → talk to the Guide (state → IN_PROGRESS)
 *   IN_PROGRESS → chop 3 logs     (tracked by logsChopped counter)
 *   IN_PROGRESS + isLogsStepDone() → return to Guide (state → COMPLETE)
 */
public class Quest {

    public enum State { NOT_STARTED, IN_PROGRESS, COMPLETE }

    private final String name;
    private State state;
    private int logsChopped;

    public Quest(String name) {
        this.name = name;
        this.state = State.NOT_STARTED;
        this.logsChopped = 0;
    }

    // -----------------------------------------------------------------------
    //  State transitions
    // -----------------------------------------------------------------------

    public void start() {
        state = State.IN_PROGRESS;
    }

    public void complete() {
        state = State.COMPLETE;
    }

    /** Record one log chopped (only counts while the quest is IN_PROGRESS). */
    public void incrementLogsChopped() {
        if (state == State.IN_PROGRESS) logsChopped++;
    }

    /** True once the player has chopped the required 3 logs. */
    public boolean isLogsStepDone() { return logsChopped >= 3; }

    // -----------------------------------------------------------------------
    //  Getters / setters (setters used by SaveSystem only)
    // -----------------------------------------------------------------------

    public String getName() {
        return name;
    }

    public State getState() {
        return state;
    }

    public int getLogsChopped() {
        return logsChopped;
    }

    public void setState(State s) {
        this.state = s;
    }

    public void setLogsChopped(int n) {
        this.logsChopped = n;
    }
}
