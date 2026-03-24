package com.classic.preservitory.server.player;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class PlayerData implements Serializable {

    private static final long serialVersionUID = 1L;

    public String username;
    public int x, y;
    public int hp;

    public Map<String, Integer> inventory = new HashMap<>();
    public Map<String, Integer> skills = new HashMap<>();
    public Map<String, Integer> skillXp = new HashMap<>();

    public PlayerData(String username) {
        this.username = username;
    }
}