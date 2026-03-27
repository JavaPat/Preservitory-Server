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

    public Map<String, Integer> inventory = new HashMap<>();
    public Map<String, Integer> skills = new HashMap<>();
    public Map<String, Integer> skillXp = new HashMap<>();
    public String questState = "NOT_STARTED";
    public int questLogsChopped = 0;

    public PlayerData(String username) {
        this.username = username;
        this.rights = PlayerRole.PLAYER;
    }
}
