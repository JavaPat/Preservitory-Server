package com.classic.preservitory.server;

import com.classic.preservitory.server.combat.CombatServices;
import com.classic.preservitory.server.commands.CommandManager;
import com.classic.preservitory.server.commands.staff.Kick;
import com.classic.preservitory.server.commands.staff.Mute;
import com.classic.preservitory.server.commands.staff.Unmute;
import com.classic.preservitory.server.content.NpcDefinition;
import com.classic.preservitory.server.moderation.ModerationSystem;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.npc.NPCData;
import com.classic.preservitory.server.objects.RockData;
import com.classic.preservitory.server.objects.TreeData;
import com.classic.preservitory.server.player.PlayerService;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.player.ShopSystem;
import com.classic.preservitory.server.quest.Quest;
import com.classic.preservitory.util.ValidationUtil;
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
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class GameServer {

    private static final int PORT = Constants.PORT;
    private static final long LOGIN_PROTECTION_MS = 5_000L;
    private static final double NPC_INTERACT_RANGE_PX = TreeManager.TILE_SIZE * 1.7;
    private static final double NPC_INTERACT_RANGE_SQ = NPC_INTERACT_RANGE_PX * NPC_INTERACT_RANGE_PX;

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
    private final CommandManager commandManager = new CommandManager(this);

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
        playerService.startSessionCleanupThread();

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
        session.protectedUntilMs = System.currentTimeMillis() + LOGIN_PROTECTION_MS;
        syncSessionState(session);
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

        ClientHandler handler = session.getHandler();

        if (handler != null) {
            if (!toLoad.isEmpty())   sendRegionLoad(handler, toLoad);
            if (!toUnload.isEmpty()) sendRegionUnload(handler, toUnload);
        }
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

        if (message.startsWith("::") || message.startsWith("/")) {

            String command = message.replaceFirst("^[:/]+", "");

            if (!commandManager.handle(fromId, command)) {
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

        PlayerSession session = sessions.get(fromId);

        String role = session.getRights().name();
        String username = session.username;

        System.out.println("CHAT [" + role + "] " + username + ": " + clean);

        broadcastService.broadcastAll(
                "CHAT " + username + " " + role + " " + clean
        );
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

    public void handleLogin(String id, String username, String password) {
        if (playerService.handleLogin(id, username, password)) {
            PlayerSession session = sessions.get(id);
            if (session != null) {
                syncSessionState(session);
            }
        }
    }

    public void handleRegister(String id, String username, String password) {
        if (playerService.handleRegister(id, username, password)) {
            PlayerSession session = sessions.get(id);
            if (session != null) {
                syncSessionState(session);
            }
        }
    }

    public void handleTalk(String playerId, String npcId) {
        if (!ValidationUtil.isValidObjectId(npcId)) return;

        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.isAlive()) return;
        if (!session.loggedIn) {
            sendToPlayer(playerId, "Login first with /login <name> or /register <name>.");
            return;
        }

        NPCData npc = npcManager.getNpc(npcId);
        if (npc == null) return;
        NpcDefinition definition = npcManager.getDefinition(npc.definitionId);
        if (definition == null) return;
        if (!ValidationUtil.isWithinEntityRange(session.x, session.y, npc.x, npc.y, NPC_INTERACT_RANGE_SQ)) return;

        session.activeNpcId = npcId;
        session.shopOpen = false;

        String[] lines;
        boolean openShop = false;

        if ("getting_started".equals(definition.questId)) {
            Quest quest = session.questSystem.getGettingStarted();
            if (quest.getState() == Quest.State.NOT_STARTED) {
                quest.start();
            } else if (quest.getState() == Quest.State.IN_PROGRESS && quest.isLogsStepDone()) {
                quest.complete();
                if (definition.questRewardCoins > 0) {
                    session.inventory.addItem("Coins", definition.questRewardCoins);
                }

                ClientHandler h = session.getHandler();
                if (h != null) {
                    h.send(session.inventory.buildSnapshot());
                }

                broadcastService.sendToPlayer(session.id, "SYSTEM Quest complete: Getting Started.");
            }

            Quest questState = session.questSystem.getGettingStarted();
            if (questState.getState() == Quest.State.NOT_STARTED) {
                lines = definition.dialogueStart.toArray(String[]::new);
            } else if (questState.getState() == Quest.State.IN_PROGRESS && questState.isLogsStepDone()) {
                lines = definition.dialogueReadyToComplete.toArray(String[]::new);
            } else if (questState.getState() == Quest.State.IN_PROGRESS) {
                lines = definition.dialogueInProgress.toArray(String[]::new);
            } else {
                lines = definition.dialogueComplete.toArray(String[]::new);
            }
            openShop = questState.getState() == Quest.State.COMPLETE && definition.shopkeeper;
        } else {
            List<String> fallback = definition.dialogueComplete.isEmpty()
                    ? List.of(definition.name + ": Hello there.")
                    : definition.dialogueComplete;
            lines = fallback.toArray(String[]::new);
            openShop = definition.shopkeeper;
        }

        ClientHandler h = session.getHandler();
        if (h != null) {
            h.send(buildDialogueMessage(npcId, lines, openShop));
        }

        if (openShop) {
            session.shopOpen = true;

            if (h != null) {
                h.send(new ShopSystem(definition.stockPrices, definition.sellPrices).buildSnapshot());
            }
        }
    }

    public void handleBuy(String playerId, String itemName) {
        PlayerSession session = sessions.get(playerId);
        if (session == null) return;
        if (!session.loggedIn) {
            sendToPlayer(playerId, "Login first with /login <name> or /register <name>.");
            return;
        }
        if (!session.shopOpen) return;
        NPCData npc = session.activeNpcId != null ? npcManager.getNpc(session.activeNpcId) : null;
        if (npc == null) return;
        NpcDefinition definition = npcManager.getDefinition(npc.definitionId);
        if (definition == null) return;

        String error = new ShopSystem(definition.stockPrices, definition.sellPrices)
                .buyItem(itemName, session.inventory);
        if (error != null) {
            sendToPlayer(playerId, error);
            return;
        }

        ClientHandler h = session.getHandler();
        if (h != null) {
            h.send(session.inventory.buildSnapshot());
        }

        sendToPlayer(playerId, "Bought " + itemName + ".");
    }

    public void handleSell(String playerId, String itemName) {
        PlayerSession session = sessions.get(playerId);
        if (session == null) return;
        if (!session.loggedIn) {
            sendToPlayer(playerId, "Login first with /login <name> or /register <name>.");
            return;
        }
        if (!session.shopOpen) return;
        NPCData npc = session.activeNpcId != null ? npcManager.getNpc(session.activeNpcId) : null;
        if (npc == null) return;
        NpcDefinition definition = npcManager.getDefinition(npc.definitionId);
        if (definition == null) return;

        String error = new ShopSystem(definition.stockPrices, definition.sellPrices)
                .sellItem(itemName, session.inventory);
        if (error != null) {
            sendToPlayer(playerId, error);
            return;
        }

        ClientHandler h = session.getHandler();
        if (h != null) {
            h.send(session.inventory.buildSnapshot());
        }

        sendToPlayer(playerId, "Sold " + itemName + ".");
    }

    public void handleShopClose(String playerId) {
        PlayerSession session = sessions.get(playerId);
        if (session == null) return;
        session.shopOpen = false;
        session.activeNpcId = null;
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
        ClientHandler h = s.getHandler();
        if (h != null) {
            h.send("SYSTEM You have been kicked.");
            h.disconnect();
        }
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

    public PlayerService getPlayerService() {
        return playerService;
    }

    // -----------------------------------------------------------------------
    //  Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) throws IOException {
        new GameServer().start();
    }

    private void syncSessionState(PlayerSession session) {
        RegionKey region = TreeManager.getRegionForPosition(session.x, session.y);
        Set<RegionKey> visible = treeManager.getVisibleRegions(region);

        synchronized (session) {
            session.currentRegion = region;
            session.loadedRegions.clear();
            session.loadedRegions.addAll(visible);
            session.clampHp();
        }

        ClientHandler h = session.getHandler();

        if (h != null) {
            h.send(treeManager.buildStateForRegions(visible));
            h.send(rockManager.buildStateForRegions(visible));
            h.send(npcManager.buildSnapshot());
            h.send(enemyManager.buildSnapshot());
            h.send(lootManager.buildSnapshot());

            if (session.loggedIn) {
                broadcastService.sendToPlayer(session.id, "PLAYER_HP " + session.hp + " " + session.getMaxHp());
                h.send(session.inventory.buildSnapshot());
            }
        }

        broadcastService.broadcastPositions();
    }

    private String buildDialogueMessage(String npcId, String[] lines, boolean openShop) {
        StringBuilder sb = new StringBuilder("DIALOGUE\t")
                .append(npcId).append('\t')
                .append(openShop ? '1' : '0').append('\t');
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(lines[i].replace('|', '/'));
        }
        return sb.toString();
    }
}
