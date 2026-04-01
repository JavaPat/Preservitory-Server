package com.classic.preservitory.server.player;

import com.classic.preservitory.server.moderation.PlayerRole;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class PlayerData implements Serializable {

    private static final long serialVersionUID = 1L;

    public String username;
    public String passwordHash;
    public PlayerRole rights;
    public int x, y;
    public int hp;

    public Map<String, Integer> inventory  = new HashMap<>();
    public Map<String, Integer> skills     = new HashMap<>();
    public Map<String, Integer> skillXp   = new HashMap<>();
    /** Equipment slot name → itemId. e.g. {"WEAPON": 1004} */
    public Map<String, Integer> equipment  = new HashMap<>();

    /**
     * Quest progress: questId (as string) → QuestState name.
     * e.g. {"1": "IN_PROGRESS"}
     */
    public Map<String, String> quests = new HashMap<>();

    // Legacy fields — kept only for migrating old save files. Do not write to these.
    public String questState      = null;
    public int    questLogsChopped = 0;

    public PlayerData(String username) {
        this.username = username;
        this.rights = PlayerRole.PLAYER;
    }
}
