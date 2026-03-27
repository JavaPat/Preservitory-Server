package com.classic.preservitory.server.world;

import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.objects.LootData;
import com.classic.preservitory.server.objects.RockData;
import com.classic.preservitory.server.objects.TreeData;
import com.classic.preservitory.server.player.ActionType;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillService;
import com.classic.preservitory.util.ValidationUtil;

import java.util.Map;

public class GatheringService {

    private static final double GATHER_RANGE_PX = TreeManager.TILE_SIZE * 1.6;
    private static final double GATHER_RANGE_SQ = GATHER_RANGE_PX * GATHER_RANGE_PX;
    private static final long CHOP_COOLDOWN_MS = 800L;
    private static final long MINE_COOLDOWN_MS = 800L;
    private static final long PICKUP_COOLDOWN_MS = 150L;
    private static final int CHOP_XP = 25;
    private static final int MINE_XP = 20;

    private final Map<String, PlayerSession> sessions;
    private final TreeManager treeManager;
    private final RockManager rockManager;
    private final LootManager lootManager;
    private final BroadcastService broadcastService;

    public GatheringService(Map<String, PlayerSession> sessions, TreeManager treeManager, RockManager rockManager,
                            LootManager lootManager,
                            BroadcastService broadcastService) {
        this.sessions = sessions;
        this.treeManager = treeManager;
        this.rockManager = rockManager;
        this.lootManager = lootManager;
        this.broadcastService = broadcastService;
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

        LootData d = lootManager.pickup(lootId);
        if (d == null) return;

        session.inventory.addItem(d.itemName, d.count);
        broadcastService.broadcastAll(LootManager.buildRemoveMessage(lootId));
        broadcastService.sendToPlayer(session.id, session.inventory.buildSnapshot());
        System.out.println("[Server] " + playerId + " picked up " + d.count
                + "x " + d.itemName + " (" + lootId + ")");
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

        if (!rockManager.mineRock(rockId)) return;

        ClientHandler h = session.getHandler();
        if (h != null) {
            session.inventory.addItem("Ore", 1);
            h.send(session.inventory.buildSnapshot());

            session.skills.addXp(Skill.MINING, MINE_XP);
            h.send(SkillService.buildSkillsPacket(session));
        } else {
            session.inventory.addItem("Ore", 1);
            session.skills.addXp(Skill.MINING, MINE_XP);
        }

        broadcastService.sendToPlayer(session.id, "SKILL_XP mining " + MINE_XP);

        System.out.println("[Server] Rock mined: " + rockId + " -> respawn in 8000ms");
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

        if (!treeManager.chopTree(treeId)) return;

        session.inventory.addItem("Logs", 1);

        ClientHandler h = session.getHandler();

        if (h != null) {
            h.send(session.inventory.buildSnapshot());
        }

        session.skills.addXp(Skill.WOODCUTTING, CHOP_XP);
        session.questSystem.onLogChopped();

        broadcastService.sendToPlayer(session.id, "SKILL_XP woodcutting " + CHOP_XP);

        if (h != null) {
            h.send(SkillService.buildSkillsPacket(session));
        }

        System.out.println("[Server] Tree chopped: " + treeId);
        RegionKey region = TreeManager.getRegionForPosition(tree.x, tree.y);
        String msg = TreeManager.buildRemoveMessage(treeId);
        for (PlayerSession s : new java.util.ArrayList<>(sessions.values())) {
            if (s != null && s.canSeeRegion(region)) {
                broadcastService.sendToPlayer(s.id, msg);
            }
        }
    }
}
