package com.classic.preservitory.server;

import com.classic.preservitory.server.combat.CombatServices;
import com.classic.preservitory.server.commands.CommandManager;
import com.classic.preservitory.server.commands.staff.Kick;
import com.classic.preservitory.server.commands.staff.Mute;
import com.classic.preservitory.server.commands.staff.Unmute;
import com.classic.preservitory.server.moderation.ModerationSystem;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.objects.RockData;
import com.classic.preservitory.server.objects.TreeData;
import com.classic.preservitory.server.player.PlayerService;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.util.ValidationUtil;
import com.classic.preservitory.server.world.EnemyManager;
import com.classic.preservitory.server.world.GatheringService;
import com.classic.preservitory.server.world.LootManager;
import com.classic.preservitory.server.world.NPCManager;
import com.classic.preservitory.server.world.RegionKey;
import com.classic.preservitory.server.world.RockManager;
import com.classic.preservitory.server.world.TreeManager;
import com.classic.preservitory.server.world.WorldTickService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameServer {

    private static final int PORT = Constants.PORT;
    private static final long LOGIN_PROTECTION_MS = 5_000L;

    // -----------------------------------------------------------------------
    //  World managers
    // -----------------------------------------------------------------------

    private final TreeManager treeManager  = new TreeManager();
    private final RockManager rockManager  = new RockManager();
    private final NPCManager npcManager   = new NPCManager();
    private final EnemyManager enemyManager = new EnemyManager();
    private final LootManager lootManager  = new LootManager();

    // -----------------------------------------------------------------------
    //  Sessions — must be declared before any service that receives it
    // -----------------------------------------------------------------------

    private final ConcurrentHashMap<String, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final AtomicBoolean playerDirty = new AtomicBoolean(false);
    private int nextId = 1;

    // -----------------------------------------------------------------------
    //  Services — declared after sessions
    // -----------------------------------------------------------------------

    private final BroadcastService broadcastService =
            new BroadcastService(sessions);

    private final PlayerService playerService =
            new PlayerService(sessions, broadcastService);

    private final GatheringService gatheringService =
            new GatheringService(sessions, treeManager, rockManager, lootManager, broadcastService);

    private final CombatServices combatServices =
            new CombatServices(sessions, enemyManager, lootManager, broadcastService);

    private final WorldTickService worldTickService =
            new WorldTickService(sessions, treeManager, rockManager, enemyManager,
                                 broadcastService, combatServices, playerDirty);

    // -----------------------------------------------------------------------
    //  Moderation / commands
    // -----------------------------------------------------------------------

    private final ModerationSystem moderationSystem = new ModerationSystem();
    private final CommandManager   commandManager   = new CommandManager();

    private void registerCommands() {
        commandManager.register(new Mute(this, moderationSystem));
        commandManager.register(new Kick(this, moderationSystem));
        commandManager.register(new Unmute(this, moderationSystem));
    }

    // -----------------------------------------------------------------------
    //  Start
    // -----------------------------------------------------------------------

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("[Server] Listening on port " + PORT);
        registerCommands();
        worldTickService.start();
        playerService.startAutoSaveThread();

        while (true) {
            Socket socket = serverSocket.accept();
            String id = "P" + (nextId++);
            System.out.println("[Server] Player connected: " + id);

            ClientHandler handler = new ClientHandler(socket, id, this);
            PlayerSession  session = new PlayerSession(id, handler);
            sessions.put(id, session);

            new Thread(handler, "client-" + id).start();
        }
    }

    // -----------------------------------------------------------------------
    //  Session access (used by ClientHandler)
    // -----------------------------------------------------------------------

    public PlayerSession getSession(String id) {
        return sessions.get(id);
    }

    // -----------------------------------------------------------------------
    //  Connection lifecycle
    // -----------------------------------------------------------------------

    public void onConnect(PlayerSession session) {
        session.hp = session.getMaxHp();
        session.protectedUntilMs = System.currentTimeMillis() + LOGIN_PROTECTION_MS;

        Set<RegionKey> visible = treeManager.getVisibleRegions(session.currentRegion);
        synchronized (session) {
            session.loadedRegions.addAll(visible);
        }

        broadcastService.sendToPlayer(session.id, "PLAYER_HP " + session.hp);
        session.handler.send(treeManager.buildStateForRegions(visible));
        session.handler.send(rockManager.buildStateForRegions(visible));
        session.handler.send(npcManager.buildSnapshot());
        session.handler.send(enemyManager.buildSnapshot());
        session.handler.send(lootManager.buildSnapshot());
        session.handler.send(session.inventory.buildSnapshot());
        broadcastService.broadcastPositions();
    }

    // -----------------------------------------------------------------------
    //  Position update
    // -----------------------------------------------------------------------

    public void updatePosition(String id, int x, int y) {
        PlayerSession session = sessions.get(id);
        if (session == null || !session.loggedIn || !session.isAlive()) return;
        if (!ValidationUtil.isWithinWorldBounds(x, y)) return;
        if (!ValidationUtil.isValidMovement(session, x, y)) return;

        session.x = x;
        session.y = y;
        session.lastMoveAtMs = System.currentTimeMillis();
        playerDirty.set(true);

        RegionKey newRegion = TreeManager.getRegionForPosition(x, y);

        Set<RegionKey> toLoad;
        Set<RegionKey> toUnload;

        synchronized (session) {
            if (newRegion.equals(session.currentRegion)) return;
            session.currentRegion = newRegion;

            Set<RegionKey> newVisible = treeManager.getVisibleRegions(newRegion);
            toLoad   = new HashSet<>(newVisible);
            toLoad.removeAll(session.loadedRegions);
            toUnload = new HashSet<>(session.loadedRegions);
            toUnload.removeAll(newVisible);

            session.loadedRegions.clear();
            session.loadedRegions.addAll(newVisible);
        }

        if (!toLoad.isEmpty())   sendRegionLoad(session.handler, toLoad);
        if (!toUnload.isEmpty()) sendRegionUnload(session.handler, toUnload);
    }

    // -----------------------------------------------------------------------
    //  Region streaming
    // -----------------------------------------------------------------------

    private void sendRegionLoad(ClientHandler handler, Set<RegionKey> regions) {
        for (RegionKey region : regions) {
            for (TreeData t : treeManager.getTreesInRegion(region)) {
                if (t.alive) handler.send(TreeManager.buildAddMessage(t));
            }
            for (RockData r : rockManager.getRocksInRegion(region)) {
                if (r.alive) handler.send(RockManager.buildAddMessage(r));
            }
        }
    }

    private void sendRegionUnload(ClientHandler handler, Set<RegionKey> regions) {
        for (RegionKey region : regions) {
            for (TreeData t : treeManager.getTreesInRegion(region)) {
                handler.send(TreeManager.buildRemoveMessage(t.id));
            }
            for (RockData r : rockManager.getRocksInRegion(region)) {
                handler.send(RockManager.buildRemoveMessage(r.id));
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Chat
    // -----------------------------------------------------------------------

    public void broadcastChat(String fromId, String message) {
        message = message.trim();

        if (message.startsWith("/")) {
            if (!commandManager.handle(fromId, message)) {
                sendToPlayer(fromId, "Unknown command.");
            }
            return;
        }

        String clean = com.classic.preservitory.util.ChatFilter.filter(message);
        if (clean == null) {
            sendToPlayer(fromId, "Invalid message.");
            return;
        }

        ModerationSystem.Result result = moderationSystem.handleMessage(fromId, message, clean);
        if (!result.allowed) {
            if (result.message != null) sendToPlayer(fromId, result.message);
            return;
        }
        if (result.message != null) sendToPlayer(fromId, result.message);

        System.out.println("[Chat] " + fromId + ": " + clean);
        broadcastService.broadcastAll("CHAT " + fromId + " " + clean);
    }

    // -----------------------------------------------------------------------
    //  Delegation to services
    // -----------------------------------------------------------------------

    public void handleChop(String id, String treeId) {
        gatheringService.handleChop(id, treeId);
    }

    public void handleMine(String id, String rockId) {
        gatheringService.handleMine(id, rockId);
    }

    public void handleAttack(String id, String enemyId) {
        combatServices.handleAttack(id, enemyId);
    }

    public void handlePickup(String id, String lootId) {
        gatheringService.handlePickup(id, lootId);
    }

    public void handleLogin(String id, String username) {
        playerService.handleLogin(id, username);
    }

    public void handleRegister(String id, String username) {
        playerService.handleRegister(id, username);
    }

    public void removePlayer(String playerId) {
        playerService.removePlayer(playerId);
    }

    // -----------------------------------------------------------------------
    //  Kick / messaging
    // -----------------------------------------------------------------------

    public boolean disconnectPlayer(String playerId) {
        PlayerSession s = sessions.get(playerId);
        if (s == null) return false;
        s.handler.send("SYSTEM You have been kicked.");
        s.handler.disconnect();
        System.out.println("[Server] " + playerId + " was kicked");
        return true;
    }

    public void sendToPlayer(String playerId, String message) {
        PlayerSession s = sessions.get(playerId);
        if (s != null) broadcastService.sendToPlayer(playerId, "SYSTEM " + message);
    }

    public void broadcastSystem(String message) {
        broadcastService.broadcastAll("SYSTEM " + message);
    }

    // -----------------------------------------------------------------------
    //  Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }
}
