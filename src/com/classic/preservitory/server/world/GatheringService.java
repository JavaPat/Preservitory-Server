package com.classic.preservitory.server.world;

import com.classic.preservitory.server.definitions.ItemIds;
import com.classic.preservitory.server.definitions.ObjectDefinition;
import com.classic.preservitory.server.definitions.ObjectDefinitionManager;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.objects.LootData;
import com.classic.preservitory.server.objects.RockData;
import com.classic.preservitory.server.objects.TreeData;
import com.classic.preservitory.server.player.ActionType;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillService;
import com.classic.preservitory.server.quest.QuestService;
import com.classic.preservitory.util.ValidationUtil;

import java.util.Map;

public class GatheringService {

    private static final double GATHER_RANGE_PX = TreeManager.TILE_SIZE * 1.6;
    private static final double GATHER_RANGE_SQ = GATHER_RANGE_PX * GATHER_RANGE_PX;
    private static final long CHOP_COOLDOWN_MS = 800L;
    private static final long MINE_COOLDOWN_MS = 800L;
    private static final long PICKUP_COOLDOWN_MS = 150L;

    // Fallback XP values used only when an ObjectDefinition is not found.
    private static final int FALLBACK_CHOP_XP = 25;
    private static final int FALLBACK_MINE_XP = 20;

    private final Map<String, PlayerSession> sessions;
    private final TreeManager treeManager;
    private final RockManager rockManager;
    private final LootManager lootManager;
    private final BroadcastService broadcastService;
    private final QuestService questService;

    public GatheringService(Map<String, PlayerSession> sessions, TreeManager treeManager, RockManager rockManager,
                            LootManager lootManager,
                            BroadcastService broadcastService,
                            QuestService questService) {
        this.sessions = sessions;
        this.treeManager = treeManager;
        this.rockManager = rockManager;
        this.lootManager = lootManager;
        this.broadcastService = broadcastService;
        this.questService = questService;
    }

    public void handlePickup(String playerId, String lootId) {
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.isAlive()) return;
        if (!session.loggedIn) {
            broadcastService.sendToPlayer(playerId, "SYSTEM Login first with /login <name> or /register <name>.");
            return;
        }
        if (!ValidationUtil.isValidObjectId(lootId)) return;
        if (!ValidationUtil.consumeCooldown(session, ActionType.PICKUP, PICKUP_COOLDOWN_MS)) return;

        LootData loot = lootManager.get(lootId);
        if (loot == null) return;
        if (!ValidationUtil.isWithinEntityRange(session.x, session.y, loot.x, loot.y, GATHER_RANGE_SQ)) return;
        if (!lootManager.canPickup(playerId, loot, System.currentTimeMillis())) {
            broadcastService.sendToPlayer(session.id, "SYSTEM That item is still private.");
            broadcastService.sendToPlayer(session.id, "STOP_ACTION");
            return;
        }

        if (!session.inventory.hasSpace(loot.itemId, loot.count)) {
            session.sendInventoryFullMessage();
            broadcastService.sendToPlayer(session.id, "STOP_ACTION");
            return;
        }

        LootData d = lootManager.pickup(lootId);
        if (d == null) return; // race: another player picked it up first

        if (!session.inventory.addItem(d.itemId, d.count)) {
            lootManager.restore(d);
            session.sendInventoryFullMessage();
            broadcastService.sendToPlayer(session.id, "STOP_ACTION");
            return;
        }

        broadcastService.broadcastAll(LootManager.buildRemoveMessage(lootId));
        broadcastService.sendToPlayer(session.id, session.inventory.buildSnapshot());
        questService.checkAndAdvanceGatherObjective(session, d.itemId);
        System.out.println("[GatheringService] " + playerId + " picked up " + d.count
                + "x itemId=" + d.itemId + " (" + lootId + ")");
    }

    public void handleMine(String playerId, String rockId) {
        if (!ValidationUtil.isValidObjectId(rockId)) return;
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.isAlive()) return;
        if (!session.loggedIn) {
            broadcastService.sendToPlayer(playerId, "SYSTEM Login first with /login <name> or /register <name>.");
            return;
        }
        if (!ValidationUtil.consumeCooldown(session, ActionType.MINE, MINE_COOLDOWN_MS)) return;

        RockData rock = rockManager.getRock(rockId);
        if (rock == null || !rock.alive) return;
        if (!ValidationUtil.isWithinEntityRange(session.x, session.y, rock.x, rock.y, GATHER_RANGE_SQ)) return;

        ObjectDefinition rockDef   = ObjectDefinitionManager.get(rock.definitionId);
        int resourceItemId = rockDef.resourceItemId > 0 ? rockDef.resourceItemId : ItemIds.ORE;
        int mineXp         = rockDef.xp > 0             ? rockDef.xp             : FALLBACK_MINE_XP;

        if (!session.inventory.hasSpace(resourceItemId)) {
            session.sendInventoryFullMessage();
            broadcastService.sendToPlayer(session.id, "STOP_ACTION");
            return;
        }

        if (!rockManager.mineRock(rockId)) return;

        session.inventory.addItem(resourceItemId, 1);
        session.skills.addXp(Skill.MINING, mineXp);

        ClientHandler h = session.getHandler();
        if (h != null) {
            h.send(session.inventory.buildSnapshot());
            h.send(SkillService.buildSkillsPacket(session));
        }

        questService.checkAndAdvanceGatherObjective(session, resourceItemId);
        broadcastService.sendToPlayer(session.id, "SKILL_XP mining " + mineXp);

        long respawnMs = rockDef.respawnMs > 0 ? rockDef.respawnMs : 8_000L;
        System.out.println("[GatheringService] Rock mined: " + rockId + " -> respawn in " + respawnMs + "ms");
        RegionKey region = RockManager.getRegionForPosition(rock.x, rock.y);
        String msg = RockManager.buildRemoveMessage(rockId);
        for (PlayerSession s : new java.util.ArrayList<>(sessions.values())) {
            if (s != null && s.canSeeRegion(region)) {
                broadcastService.sendToPlayer(s.id, msg);
            }
        }
    }

    public void handleChop(String playerId, String treeId) {
        if (!ValidationUtil.isValidObjectId(treeId)) return;
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.isAlive()) return;
        if (!session.loggedIn) {
            broadcastService.sendToPlayer(playerId, "SYSTEM Login first with /login <name> or /register <name>.");
            return;
        }
        if (!ValidationUtil.consumeCooldown(session, ActionType.CHOP, CHOP_COOLDOWN_MS)) return;

        TreeData tree = treeManager.getTree(treeId);
        if (tree == null || !tree.alive) return;
        if (!ValidationUtil.isWithinEntityRange(session.x, session.y, tree.x, tree.y, GATHER_RANGE_SQ)) return;

        ObjectDefinition treeDef   = ObjectDefinitionManager.get(tree.definitionId);
        int resourceItemId = treeDef.resourceItemId > 0 ? treeDef.resourceItemId : ItemIds.LOGS;
        int chopXp         = treeDef.xp > 0             ? treeDef.xp             : FALLBACK_CHOP_XP;

        if (!session.inventory.hasSpace(resourceItemId)) {
            session.sendInventoryFullMessage();
            broadcastService.sendToPlayer(session.id, "STOP_ACTION");
            return;
        }

        if (!treeManager.chopTree(treeId)) return;

        session.inventory.addItem(resourceItemId, 1);

        ClientHandler h = session.getHandler();

        if (h != null) {
            h.send(session.inventory.buildSnapshot());
        }

        session.skills.addXp(Skill.WOODCUTTING, chopXp);

        questService.checkAndAdvanceGatherObjective(session, resourceItemId);
        broadcastService.sendToPlayer(session.id, "SKILL_XP woodcutting " + chopXp);

        if (h != null) {
            h.send(SkillService.buildSkillsPacket(session));
        }

        System.out.println("[GatheringService] Tree chopped: " + treeId);
        RegionKey region = TreeManager.getRegionForPosition(tree.x, tree.y);
        String msg = TreeManager.buildRemoveMessage(treeId);
        for (PlayerSession s : new java.util.ArrayList<>(sessions.values())) {
            if (s != null && s.canSeeRegion(region)) {
                broadcastService.sendToPlayer(s.id, msg);
            }
        }
    }
}
