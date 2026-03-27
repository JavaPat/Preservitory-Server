package com.classic.preservitory.server.quest;

import com.classic.preservitory.server.Constants;

/**
 * Manages the game's quests and provides dialogue lines for the Guide NPC.
 *
 * Currently contains one quest: "Getting Started".
 *   Step 1 — Talk to the Guide NPC  (automatically completes on first dialogue)
 *   Step 2 — Chop 3 logs
 *   Step 3 — Return to the Guide NPC (dialogue triggers completion + reward)
 */
public class QuestSystem {

    private final Quest gettingStarted;

    public QuestSystem() {

        gettingStarted = new Quest("Getting Started");
    }

    // -----------------------------------------------------------------------
    //  Event hooks (called by GamePanel)
    // -----------------------------------------------------------------------

    /** Call this every time the player successfully chops a log. */
    public void onLogChopped() {
        gettingStarted.incrementLogsChopped();
    }

    // -----------------------------------------------------------------------
    //  Dialogue builder
    // -----------------------------------------------------------------------

    /**
     * Returns the dialogue lines the Guide NPC should speak,
     * tailored to the current quest state.
     */
    public String[] getGuideDialogue() {
        Quest.State state = gettingStarted.getState();

        if (state == Quest.State.NOT_STARTED) {
            return new String[]{
                    "Welcome to " + Constants.SERVER_NAME + ", adventurer!",
                    "I'm the town Guide. Let me give you a task.",
                    "Go and chop 3 logs from a nearby tree.",
                    "Return to me with them for a reward!",
                    "[Quest Started: Getting Started]"
            };
        }

        if (state == Quest.State.IN_PROGRESS) {
            if (gettingStarted.isLogsStepDone()) {
                return new String[]{
                        "You got the logs! Well done, adventurer!",
                        "As promised, here is your reward.",
                        "50 Coins and bonus XP — you've earned it!",
                        "[Quest Complete: Getting Started]"
                };
            } else {
                return new String[]{
                        "You need to chop 3 logs from a nearby tree.",
                        "Progress: " + gettingStarted.getLogsChopped() + " / 3 logs chopped.",
                        "Come back once you have them!"
                };
            }
        }

        // COMPLETE
        return new String[]{
                "Welcome back! You've proven yourself.",
                "Feel free to explore and browse my wares.",
                "[Open Shop]"
        };
    }

    // -----------------------------------------------------------------------
    //  Getter
    // -----------------------------------------------------------------------

    public Quest getGettingStarted() { return gettingStarted; }
}
