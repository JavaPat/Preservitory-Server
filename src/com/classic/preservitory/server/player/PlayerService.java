package com.classic.preservitory.server.player;

import com.classic.preservitory.server.moderation.PlayerRole;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillService;
import com.classic.preservitory.server.quest.Quest;

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

    public boolean handleRegister(String tempId, String username, String password) {
        username = username == null ? null : username.trim().toLowerCase();
        if (username == null || username.isBlank()) {
            sendAuthFailure(tempId, "Invalid username.");
            return false;
        }
        if (password == null || password.isBlank() || password.contains(" ")) {
            sendAuthFailure(tempId, "Invalid password.");
            return false;
        }

        if (PlayerSaveSystem.exists(username)) {
            sendAuthFailure(tempId, "Username already exists.");
            return false;
        }

        PlayerData data = new PlayerData(username);
        data.passwordHash = PasswordUtil.hash(password);
        data.x = PlayerSession.PLAYER_SPAWN_X;
        data.y = PlayerSession.PLAYER_SPAWN_Y;
        for (Skill skill : Skill.values()) {
            data.skills.put(skill.name(), 1);
            data.skillXp.put(skill.name(), 0);
        }
        data.hp = 5;

        PlayerSaveSystem.save(data);
        return handleLogin(tempId, username, password);
    }

    // -----------------------------------------------------------------------
    //  Login
    // -----------------------------------------------------------------------

    public boolean handleLogin(String tempId, String username, String password) {
        PlayerSession session = sessions.get(tempId);
        if (session == null) return false;

        username = username == null ? null : username.trim().toLowerCase();
        if (username == null || username.isBlank()) {
            sendAuthFailure(tempId, "Invalid username.");
            return false;
        }
        if (password == null || password.isBlank()) {
            sendAuthFailure(tempId, "Invalid password.");
            return false;
        }

        PlayerData data = PlayerSaveSystem.load(username);
        if (data == null) {
            sendAuthFailure(tempId, "Account not found.");
            return false;
        }

        if (data.passwordHash == null || data.passwordHash.isBlank()) {
            sendAuthFailure(tempId, "Account is corrupted. Please re-register.");
            return false;
        }

        if (!PasswordUtil.matches(password, data.passwordHash)) {
            sendAuthFailure(tempId, "Incorrect password.");
            return false;
        }

        PlayerSession existing = getSessionByUsername(username);

        if (existing != null) {
            if (!existing.disconnected) {
                sendAuthFailure(tempId, "Account already logged in.");
                return false;
            }

            long elapsed = System.currentTimeMillis() - existing.disconnectTime;

            if (elapsed <= 30_000) {
                System.out.println("[Auth] Reconnecting session: " + username);

                String oldId = getSessionId(existing);

                if (oldId != null) {
                    sessions.remove(oldId);
                }

                sessions.put(tempId, existing);
                existing.setHandler(session.getHandler());
                existing.disconnected = false;
                session = existing;

            } else {
                // Expired session → allow new login
                String oldId = getSessionId(existing);
                if (oldId != null) {
                    sessions.remove(oldId);
                }
            }
        }

        session.username = username;
        session.loggedIn = true;
        session.playerData = data;

        if (session.playerData.rights == null) {
            session.playerData.rights = PlayerRole.PLAYER;
        }

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

        try {
            session.questSystem.getGettingStarted()
                    .setState(Quest.State.valueOf(data.questState));
        } catch (Exception ignored) {
            session.questSystem.getGettingStarted().setState(Quest.State.NOT_STARTED);
        }
        session.questSystem.getGettingStarted().setLogsChopped(data.questLogsChopped);

        // ✅ THEN apply HP correctly
        session.hp = data.hp;
        session.clampHp(); // 🔥 critical fix

        // Inventory
        session.inventory.clear();
        for (var entry : data.inventory.entrySet()) {
            session.inventory.addItem(entry.getKey(), entry.getValue());
        }

        // ✅ Send state to client
        ClientHandler h = session.getHandler();
        if (h != null) {
            h.send(session.inventory.buildSnapshot());
            h.send(SkillService.buildSkillsPacket(session));
            h.send("PLAYER_HP " + session.hp + " " + session.getMaxHp());
            h.send("AUTH_OK " + username);
        }

        sendToPlayer(tempId, "Welcome back, " + username + "!");
        broadcastService.broadcastPositions();
        return true;
    }

    // -----------------------------------------------------------------------
    //  Disconnect
    // -----------------------------------------------------------------------

    public void removePlayer(String playerId) {
        PlayerSession session = sessions.get(playerId);

        if (session != null && session.loggedIn && session.username != null) {
            savePlayer(session);

            // 🔥 NEW: mark as disconnected instead of removing
            session.disconnected = true;
            session.disconnectTime = System.currentTimeMillis();

            System.out.println("[Server] Player disconnected (grace): " + session.username);
        } else {
            sessions.remove(playerId);
        }

        broadcastService.broadcastAll("DISCONNECT " + playerId);
        broadcastService.broadcastPositions();
    }

    // -----------------------------------------------------------------------
    //  Save
    // -----------------------------------------------------------------------

    private void savePlayer(PlayerSession session) {
        PlayerData data = session.playerData;

        if (data == null) return;

        data.passwordHash = data.passwordHash; // already set
        data.rights = session.getRights();
        data.x = session.x;
        data.y = session.y;
        data.hp = session.hp;

        data.inventory.clear();
        data.inventory.putAll(session.inventory.getItems());

        data.skills.clear();
        for (var entry : session.skills.getLevels().entrySet()) {
            data.skills.put(entry.getKey().name(), entry.getValue());
        }

        data.skillXp.clear();
        for (var entry : session.skills.getXpMap().entrySet()) {
            data.skillXp.put(entry.getKey().name(), entry.getValue());
        }

        data.questState = session.questSystem.getGettingStarted().getState().name();
        data.questLogsChopped = session.questSystem.getGettingStarted().getLogsChopped();

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

    public void startSessionCleanupThread() {
        Thread thread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(10_000);

                    long now = System.currentTimeMillis();

                    sessions.values().removeIf(s -> {
                        if (s.disconnected && (now - s.disconnectTime > 30_000)) {
                            System.out.println("[SessionCleanup] Removed expired session: " + s.username);
                            return true;
                        }
                        return false;
                    });

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "session-cleanup");

        thread.setDaemon(true);
        thread.start();
    }

    // -----------------------------------------------------------------------
    //  Messaging
    // -----------------------------------------------------------------------

    private void sendToPlayer(String playerId, String message) {
        PlayerSession s = sessions.get(playerId);

        if (s == null) return;

        ClientHandler h = s.getHandler();
        if (h != null) {
            broadcastService.sendToPlayer(playerId, "SYSTEM " + message);
        }
    }

    private void sendAuthFailure(String playerId, String message) {
        PlayerSession s = sessions.get(playerId);
        if (s == null) return;

        ClientHandler h = s.getHandler();
        if (h != null) {
            h.send("AUTH_FAIL " + message);
        }
    }

    public PlayerSession getSessionByUsername(String username) {
        if (username == null) return null;

        for (PlayerSession session : sessions.values()) {
            if (session.username != null &&
                    session.username.equalsIgnoreCase(username)) {
                return session;
            }
        }
        return null;
    }

    private String getSessionId(PlayerSession target) {
        for (Map.Entry<String, PlayerSession> entry : sessions.entrySet()) {
            if (entry.getValue() == target) {
                return entry.getKey();
            }
        }
        return null;
    }
}
