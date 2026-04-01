package com.classic.preservitory.server;

import com.classic.preservitory.server.combat.CombatServices;
import com.classic.preservitory.server.commands.CommandManager;
import com.classic.preservitory.server.commands.staff.Kick;
import com.classic.preservitory.server.commands.staff.Mute;
import com.classic.preservitory.server.commands.staff.Unmute;
import com.classic.preservitory.server.definitions.DefinitionValidator;
import com.classic.preservitory.server.definitions.DialogueDefinitionLoader;
import com.classic.preservitory.server.definitions.DialogueDefinitionManager;
import com.classic.preservitory.server.definitions.EnemyDefinitionLoader;
import com.classic.preservitory.server.definitions.EnemyDefinitionManager;
import com.classic.preservitory.server.definitions.ItemDefinitionLoader;
import com.classic.preservitory.server.definitions.ItemDefinitionManager;
import com.classic.preservitory.server.definitions.NpcDefinitionLoader;
import com.classic.preservitory.server.definitions.NpcDefinitionManager;
import com.classic.preservitory.server.definitions.ObjectDefinitionLoader;
import com.classic.preservitory.server.definitions.ObjectDefinitionManager;
import com.classic.preservitory.server.definitions.QuestDefinitionLoader;
import com.classic.preservitory.server.definitions.QuestDefinitionManager;
import com.classic.preservitory.server.dialogue.DialogueService;
import com.classic.preservitory.server.moderation.ModerationSystem;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ChatService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.player.PlayerService;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.quest.QuestService;
import com.classic.preservitory.server.shops.ShopDefinitionLoader;
import com.classic.preservitory.server.shops.ShopManager;
import com.classic.preservitory.server.shops.ShopService;
import com.classic.preservitory.server.spawns.SpawnEntry;
import com.classic.preservitory.server.spawns.SpawnLoader;
import com.classic.preservitory.server.spawns.SpawnValidator;
import com.classic.preservitory.server.woodcutting.AxeDefinitionManager;
import com.classic.preservitory.server.woodcutting.TreeDefinitionManager;
import com.classic.preservitory.server.woodcutting.WoodcuttingService;
import com.classic.preservitory.server.world.EnemyManager;
import com.classic.preservitory.server.world.GatheringService;
import com.classic.preservitory.server.world.LootManager;
import com.classic.preservitory.server.world.NPCManager;
import com.classic.preservitory.server.world.RegionService;
import com.classic.preservitory.server.world.RockManager;
import com.classic.preservitory.server.world.TreeManager;
import com.classic.preservitory.server.world.WorldTickService;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Entry point and orchestrator for the game server.
 *
 * Responsibilities:
 *   - Load all definitions and initialise world managers and services
 *   - Accept incoming client connections and create per-player threads
 *   - Expose a delegation API for {@link com.classic.preservitory.server.net.GameMessageHandler}
 *     and the command system
 *
 * All gameplay logic lives in dedicated services:
 *   - {@link RegionService}   — position updates and world region streaming
 *   - {@link DialogueService} — NPC dialogue flow and quest transitions
 *   - {@link ShopService}     — item buy/sell transactions
 *   - {@link ChatService}     — chat routing, filtering, and moderation
 *   - {@link GatheringService}, {@link CombatServices}, {@link PlayerService} — unchanged
 */
public class GameServer {

    private static final int PORT = Constants.PORT;

    // -----------------------------------------------------------------------
    //  Sessions
    // -----------------------------------------------------------------------

    private final ConcurrentHashMap<String, PlayerSession> sessions    = new ConcurrentHashMap<>();
    private final AtomicBoolean playerDirty = new AtomicBoolean(false);
    private int nextId = 1;

    // -----------------------------------------------------------------------
    //  World managers — initialised in start() after definitions are loaded
    // -----------------------------------------------------------------------

    private TreeManager  treeManager;
    private RockManager  rockManager;
    private NPCManager   npcManager;
    private EnemyManager enemyManager;
    private LootManager  lootManager;

    // -----------------------------------------------------------------------
    //  Services — initialised in start() after world managers
    // -----------------------------------------------------------------------

    private BroadcastService broadcastService;
    private PlayerService    playerService;
    private GatheringService gatheringService;
    private CombatServices   combatServices;
    private WorldTickService worldTickService;
    private QuestService     questService;
    private WoodcuttingService woodcuttingService;

    // Extracted services (previously inlined in GameServer)
    private RegionService    regionService;
    private DialogueService  dialogueService;
    private ShopService      shopService;
    private ChatService      chatService;

    // -----------------------------------------------------------------------
    //  Moderation / commands
    // -----------------------------------------------------------------------

    private final ShopManager shopManager = new ShopManager();
    private final ModerationSystem moderationSystem = new ModerationSystem();
    private final CommandManager commandManager = new CommandManager(this);

    private void registerCommands() {
        commandManager.register(new Mute(this, moderationSystem));
        commandManager.register(new Kick(this, moderationSystem));
        commandManager.register(new Unmute(this, moderationSystem));
    }

    // -----------------------------------------------------------------------
    //  Start — load definitions, build world, wire services, accept connections
    // -----------------------------------------------------------------------

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT);
        System.out.println("[GameServer] Listening on port " + PORT);

        // Load all definitions before any world manager constructor runs.
        ItemDefinitionManager.load(ItemDefinitionLoader.loadAll());
        ObjectDefinitionManager.load(ObjectDefinitionLoader.loadAll());
        EnemyDefinitionManager.load(EnemyDefinitionLoader.loadAll());
        NpcDefinitionManager.load(NpcDefinitionLoader.loadAll());
        DialogueDefinitionManager.load(DialogueDefinitionLoader.loadAll());
        QuestDefinitionManager.load(QuestDefinitionLoader.loadAll());
        ShopDefinitionLoader.loadAll(shopManager);
        DefinitionValidator.validateAll(shopManager);

        // Load spawn data — must run after definitions are loaded.
        List<SpawnEntry> npcSpawns = SpawnLoader.loadNpcSpawns();
        List<SpawnEntry> enemySpawns = SpawnLoader.loadEnemySpawns();
        List<SpawnEntry> objectSpawns = SpawnLoader.loadObjectSpawns();
        SpawnValidator.validateUniqueIds(npcSpawns, enemySpawns, objectSpawns);

        // Initialise world managers with their spawn lists.
        treeManager  = new TreeManager(objectSpawns);
        rockManager  = new RockManager(objectSpawns);
        npcManager   = new NPCManager(npcSpawns);
        enemyManager = new EnemyManager(enemySpawns);
        lootManager  = new LootManager();

        // Initialise services that depend on world managers.
        broadcastService = new BroadcastService(sessions);
        questService     = new QuestService(broadcastService);
        playerService    = new PlayerService(sessions, broadcastService);
        gatheringService = new GatheringService(sessions, treeManager, rockManager, lootManager, broadcastService, questService);
        woodcuttingService = new WoodcuttingService(sessions, treeManager, new TreeDefinitionManager(),
                new AxeDefinitionManager(),
                broadcastService, questService);
        combatServices   = new CombatServices(sessions, enemyManager, lootManager, broadcastService, questService);
        worldTickService = new WorldTickService(sessions, treeManager, rockManager, npcManager,
                                                enemyManager, lootManager, broadcastService, combatServices,
                                                woodcuttingService, playerDirty);

        // Initialise extracted services.
        regionService   = new RegionService(sessions, treeManager, rockManager, npcManager,
                                            enemyManager, lootManager, broadcastService, playerDirty);
        dialogueService = new DialogueService(sessions, npcManager, questService, shopManager, broadcastService);
        shopService     = new ShopService(sessions, shopManager, npcManager, broadcastService);
        chatService     = new ChatService(sessions, broadcastService, commandManager, moderationSystem);

        registerCommands();
        worldTickService.start();
        playerService.startAutoSaveThread();
        playerService.startSessionCleanupThread();

        while (true) {
            Socket socket = serverSocket.accept();
            String id = "P" + (nextId++);
            System.out.println("[GameServer] Player connected: " + id);

            ClientHandler  handler = new ClientHandler(socket, id, this);
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
        session.protectedUntilMs = System.currentTimeMillis() + 5_000L;
        regionService.syncSessionState(session);
    }

    // -----------------------------------------------------------------------
    //  Delegation — GameMessageHandler routes all packets through these methods
    // -----------------------------------------------------------------------

    public void updatePosition(String id, int x, int y) {
        regionService.updatePosition(id, x, y);
    }

    public void broadcastChat(String fromId, String message) {
        chatService.broadcastChat(fromId, message);
    }

    public void handleChop(String id, String treeId) {
        woodcuttingService.startChopping(id, treeId);
    }

    public void handleMine(String id, String rockId) {
        gatheringService.handleMine(id, rockId);
    }

    public void handleAttack(String id, String enemyId) {
        combatServices.handleAttack(id, enemyId);
    }

    public void handleCombatStyle(String id, String style) {
        PlayerSession session = sessions.get(id);
        if (session == null || !session.loggedIn) return;
        try {
            session.combatStyle = com.classic.preservitory.server.player.CombatStyle.valueOf(style.toUpperCase());
        } catch (IllegalArgumentException ignored) {}
    }

    public void handlePickup(String id, String lootId) {
        gatheringService.handlePickup(id, lootId);
    }

    public void handleEquip(String id, int itemId) {
        playerService.handleEquip(id, itemId);
    }

    public void handleUnequip(String id, String slot) {
        playerService.handleUnequip(id, slot);
    }

    public void handleLogin(String id, String username, String password) {
        if (playerService.handleLogin(id, username, password)) {
            PlayerSession session = sessions.get(id);
            if (session != null) regionService.syncSessionState(session);
        }
    }

    public void handleRegister(String id, String username, String password) {
        if (playerService.handleRegister(id, username, password)) {
            PlayerSession session = sessions.get(id);
            if (session != null) regionService.syncSessionState(session);
        }
    }

    public void handleTalk(String playerId, String npcId) {
        dialogueService.handleTalk(playerId, npcId);
    }

    public void handleDialogueNext(String playerId) {
        dialogueService.handleDialogueNext(playerId);
    }

    public void handleDialogueOption(String playerId, int optionIndex) {
        dialogueService.handleDialogueOption(playerId, optionIndex);
    }

    public void handleBuy(String playerId, String itemIdStr) {
        shopService.handleBuy(playerId, itemIdStr);
    }

    public void handleSell(String playerId, String itemIdStr) {
        shopService.handleSell(playerId, itemIdStr);
    }

    public void handleShopClose(String playerId) {
        shopService.handleShopClose(playerId);
    }

    public void removePlayer(String playerId) {
        playerService.removePlayer(playerId);
    }

    // -----------------------------------------------------------------------
    //  Admin API — used by commands (Kick, Mute, Unmute)
    // -----------------------------------------------------------------------

    public boolean disconnectPlayer(String playerId) {
        PlayerSession s = sessions.get(playerId);
        if (s == null) return false;
        ClientHandler h = s.getHandler();
        if (h != null) {
            h.send("SYSTEM You have been kicked.");
            h.disconnect();
        }
        System.out.println("[GameServer] " + playerId + " was kicked");
        return true;
    }

    public void sendToPlayer(String playerId, String message) {
        broadcastService.sendToPlayer(playerId, "SYSTEM " + message);
    }

    public void broadcastSystem(String message) {
        broadcastService.broadcastAll("SYSTEM " + message);
    }

    public PlayerService getPlayerService() {
        return playerService;
    }
}
