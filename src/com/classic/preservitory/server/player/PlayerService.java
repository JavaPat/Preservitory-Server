package com.classic.preservitory.server.player;

import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillService;

import java.util.Map;

public class PlayerService {

    private final Map<String, PlayerSession> sessions;
    private final BroadcastService broadcastService;

    public PlayerService(Map<String, PlayerSession> sessions, BroadcastService broadcastService) {
        this.sessions = sessions;
        this.broadcastService = broadcastService;
    }

    // -----------------------------------------------------------------------
    //  Register
    // -----------------------------------------------------------------------

    public void handleRegister(String tempId, String username) {
        if (PlayerSaveSystem.exists(username)) {
            sendToPlayer(tempId, "Username already exists.");
            return;
        }

        PlayerData data = new PlayerData(username);
        data.x = PlayerSession.PLAYER_SPAWN_X;
        data.y = PlayerSession.PLAYER_SPAWN_Y;

        // ✅ Use dynamic HP
        data.hp = 10; // safe default OR derive from level 1

        PlayerSaveSystem.save(data);
        sendToPlayer(tempId, "Registered successfully. Please login.");
    }

    // -----------------------------------------------------------------------
    //  Login
    // -----------------------------------------------------------------------

    public void handleLogin(String tempId, String username) {
        PlayerSession session = sessions.get(tempId);
        if (session == null) return;

        PlayerData data = PlayerSaveSystem.load(username);
        if (data == null) {
            sendToPlayer(tempId, "Account not found.");
            return;
        }

        session.username = username;
        session.loggedIn = true;

        session.x = data.x;
        session.y = data.y;

        // ✅ Load skills FIRST
        session.skills.reset();

        for (var entry : data.skills.entrySet()) {
            try {
                Skill skill = Skill.valueOf(entry.getKey().toUpperCase());
                session.skills.setLevel(skill, entry.getValue());
            } catch (Exception ignored) {}
        }

        for (var entry : data.skillXp.entrySet()) {
            try {
                Skill skill = Skill.valueOf(entry.getKey().toUpperCase());
                session.skills.setXp(skill, entry.getValue());
            } catch (Exception ignored) {}
        }

        // ✅ THEN apply HP correctly
        session.hp = data.hp;
        session.clampHp(); // 🔥 critical fix

        // Inventory
        session.inventory.clear();
        for (var entry : data.inventory.entrySet()) {
            session.inventory.addItem(entry.getKey(), entry.getValue());
        }

        // ✅ Send state to client
        session.handler.send(session.inventory.buildSnapshot());
        session.handler.send(SkillService.buildSkillsPacket(session));

        sendToPlayer(tempId, "Welcome back, " + username + "!");
        broadcastService.broadcastPositions();
    }

    // -----------------------------------------------------------------------
    //  Disconnect
    // -----------------------------------------------------------------------

    public void removePlayer(String playerId) {
        PlayerSession session = sessions.get(playerId);

        if (session != null && session.loggedIn && session.username != null) {
            savePlayer(session);
        }

        sessions.remove(playerId);

        System.out.println("[Server] Player disconnected: " + playerId);

        broadcastService.broadcastAll("DISCONNECT " + playerId);
        broadcastService.broadcastPositions();
    }

    // -----------------------------------------------------------------------
    //  Save
    // -----------------------------------------------------------------------

    private void savePlayer(PlayerSession session) {
        PlayerData data = new PlayerData(session.username);

        data.x = session.x;
        data.y = session.y;
        data.hp = session.hp;

        data.inventory.putAll(session.inventory.getItems());

        for (var entry : session.skills.getLevels().entrySet()) {
            data.skills.put(entry.getKey().name(), entry.getValue());
        }

        for (var entry : session.skills.getXpMap().entrySet()) {
            data.skillXp.put(entry.getKey().name(), entry.getValue());
        }

        PlayerSaveSystem.save(data);

        System.out.println("[SaveSystem] Saved player: " + session.username);
    }

    // -----------------------------------------------------------------------
    //  Auto-save
    // -----------------------------------------------------------------------

    public void startAutoSaveThread() {
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(30_000);

                    for (PlayerSession s : sessions.values()) {
                        if (s.loggedIn && s.username != null) {
                            savePlayer(s);
                        }
                    }

                    System.out.println("[SaveSystem] Auto-saved players");

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "auto-save");

        thread.setDaemon(true);
        thread.start();
    }

    // -----------------------------------------------------------------------
    //  Messaging
    // -----------------------------------------------------------------------

    private void sendToPlayer(String playerId, String message) {
        PlayerSession s = sessions.get(playerId);

        if (s != null && s.handler != null) {
            broadcastService.sendToPlayer(playerId, "SYSTEM " + message);
        }
    }
}