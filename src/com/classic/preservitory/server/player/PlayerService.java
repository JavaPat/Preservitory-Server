package com.classic.preservitory.server.player;

import com.classic.preservitory.server.definitions.ItemDefinition;
import com.classic.preservitory.server.definitions.ItemDefinitionManager;
import com.classic.preservitory.server.moderation.PlayerRole;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillService;
import com.classic.preservitory.server.quest.QuestProgress;
import com.classic.preservitory.server.quest.QuestService;
import com.classic.preservitory.server.quest.QuestState;

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
        // Combat skills start at level 3 except HITPOINTS, which starts at 2 (10 HP).
        int combatStartXp = com.classic.preservitory.server.player.skills.SkillSet.xpForLevel(3);
        int hitpointsStartXp = com.classic.preservitory.server.player.skills.SkillSet.xpForLevel(2);
        for (Skill skill : Skill.values()) {
            boolean isCombat = skill == Skill.ATTACK || skill == Skill.STRENGTH
                    || skill == Skill.DEFENCE || skill == Skill.HITPOINTS;
            int startLevel = skill == Skill.HITPOINTS ? 2 : (isCombat ? 3 : 1);
            int startXp = skill == Skill.HITPOINTS ? hitpointsStartXp : (isCombat ? combatStartXp : 0);
            data.skills.put(skill.name(), startLevel);
            data.skillXp.put(skill.name(), startXp);
        }
        data.hp = 10; // level 2 HITPOINTS × 5

        PlayerSaveSystem.save(data);
        return handleLogin(tempId, username, password, true);
    }

    // -----------------------------------------------------------------------
    //  Login
    // -----------------------------------------------------------------------

    public boolean handleLogin(String tempId, String username, String password) {
        return handleLogin(tempId, username, password, false);
    }

    private boolean handleLogin(String tempId, String username, String password, boolean firstLogin) {
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
                System.out.println("[PlayerService] Reconnecting session: " + username);

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

        // Load quest progress from save data
        session.quests.clear();
        for (var entry : data.quests.entrySet()) {
            try {
                int questId = Integer.parseInt(entry.getKey());
                QuestProgress progress = QuestProgress.deserialize(entry.getValue());
                if (progress.state != QuestState.NOT_STARTED) {
                    session.quests.put(questId, progress);
                }
            } catch (Exception ignored) {}
        }
        // Migrate legacy quest data (old saves only — before quests map was persisted)
        if (data.quests.isEmpty() && data.questState != null) {
            try {
                QuestState legacyState = switch (data.questState) {
                    case "COMPLETE", "COMPLETED" -> QuestState.COMPLETED;
                    case "IN_PROGRESS"           -> QuestState.IN_PROGRESS;
                    default                      -> null;
                };
                if (legacyState != null) {
                    session.quests.put(1, new QuestProgress(legacyState, 0)); // quest id 1 = guide_quest
                }
            } catch (Exception ignored) {}
        }

        // ✅ THEN apply HP correctly
        session.hp = data.hp;
        session.clampHp(); // 🔥 critical fix

        // Inventory — PlayerData stores itemId as String (JSON key); Inventory uses int.
        session.inventory.clear();
        for (var entry : data.inventory.entrySet()) {
            try {
                session.inventory.addItem(Integer.parseInt(entry.getKey()), entry.getValue());
            } catch (NumberFormatException ignored) {
                System.err.println("[PlayerService] Skipping invalid inventory key '" + entry.getKey()
                        + "' for player " + username);
            }
        }

        // Load equipment
        session.equipment.clear();
        for (var entry : data.equipment.entrySet()) {
            EquipSlot slot = EquipSlot.fromString(entry.getKey());
            if (slot != null && ItemDefinitionManager.exists(entry.getValue())) {
                session.equipment.equip(slot, entry.getValue());
            }
        }

        // ✅ Send state to client
        ClientHandler h = session.getHandler();
        if (h != null) {
            h.send(session.inventory.buildSnapshot());
            h.send(session.equipment.buildSnapshot());
            h.send(SkillService.buildSkillsPacket(session));
            h.send("PLAYER_HP " + session.hp + " " + session.getMaxHp());
            h.send("AUTH_OK " + username);
            // Send quest state immediately so the journal is populated without a quest action
            h.send(QuestService.buildQuestLogPacket(session));
        }

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

            // Clear transient UI state so a reconnecting client starts fresh
            session.activeDialogue = null;
            session.shopOpen       = false;
            session.activeNpcId    = null;
            session.pendingInteraction = null;

            session.disconnected = true;
            session.disconnectTime = System.currentTimeMillis();

            System.out.println("[PlayerService] Player disconnected (grace): " + session.username);
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

        data.rights = session.getRights();
        data.x = session.x;
        data.y = session.y;
        data.hp = session.hp;

        data.inventory.clear();
        for (var entry : session.inventory.getItems().entrySet()) {
            data.inventory.put(String.valueOf(entry.getKey()), entry.getValue());
        }

        data.skills.clear();
        for (var entry : session.skills.getLevels().entrySet()) {
            data.skills.put(entry.getKey().name(), entry.getValue());
        }

        data.skillXp.clear();
        for (var entry : session.skills.getXpMap().entrySet()) {
            data.skillXp.put(entry.getKey().name(), entry.getValue());
        }

        data.quests.clear();
        for (var entry : session.quests.entrySet()) {
            data.quests.put(String.valueOf(entry.getKey()), entry.getValue().serialize());
        }

        data.equipment.clear();
        for (var entry : session.equipment.getSlots().entrySet()) {
            data.equipment.put(entry.getKey().name(), entry.getValue());
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
    //  Equipment
    // -----------------------------------------------------------------------

    public void handleEquip(String playerId, int itemId) {
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.loggedIn) return;
        if (!ItemDefinitionManager.exists(itemId)) return;

        ItemDefinition def = ItemDefinitionManager.get(itemId);
        if (def.equipSlot == null) return;

        EquipSlot slot = EquipSlot.fromString(def.equipSlot);
        if (slot == null) return;

        if (session.inventory.countOf(itemId) < 1) return;

        // Remove the item being equipped from inventory first to free its slot,
        // then swap the previously equipped item (if any) back in.
        session.inventory.removeItem(itemId, 1);

        int existing = session.equipment.getItemInSlot(slot);
        if (existing != -1) {
            session.inventory.addItem(existing, 1);
            session.equipment.unequip(slot);
        }

        session.equipment.equip(slot, itemId);

        ClientHandler h = session.getHandler();
        if (h != null) {
            h.send(session.inventory.buildSnapshot());
            h.send(session.equipment.buildSnapshot());
        }

        System.out.println("[PlayerService] " + session.username
                + " equipped " + def.name + " (" + slot + ")");
    }

    public void handleUnequip(String playerId, String slotName) {
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.loggedIn) return;

        EquipSlot slot = EquipSlot.fromString(slotName);
        if (slot == null) return;

        int itemId = session.equipment.getItemInSlot(slot);
        if (itemId == -1) return;

        if (!session.inventory.hasSpace(itemId)) {
            session.sendInventoryFullMessage();
            return;
        }

        session.inventory.addItem(itemId, 1);
        session.equipment.unequip(slot);

        ClientHandler h = session.getHandler();
        if (h != null) {
            h.send(session.inventory.buildSnapshot());
            h.send(session.equipment.buildSnapshot());
        }

        System.out.println("[PlayerService] " + session.username + " unequipped " + slot);
    }

    // -----------------------------------------------------------------------
    //  Messaging
    // -----------------------------------------------------------------------

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
