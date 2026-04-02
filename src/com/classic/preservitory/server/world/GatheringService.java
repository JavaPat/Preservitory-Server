package com.classic.preservitory.server.world;

import com.classic.preservitory.server.definitions.ItemDefinitionManager;
import com.classic.preservitory.server.definitions.ItemIds;
import com.classic.preservitory.server.gathering.GatheringRolls;
import com.classic.preservitory.server.gathering.ResourceDefinition;
import com.classic.preservitory.server.gathering.ResourceDefinitionManager;
import com.classic.preservitory.server.gathering.SkillType;
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

    private final Map<String, PlayerSession> sessions;
    private final TreeManager treeManager;
    private final RockManager rockManager;
    private final LootManager lootManager;
    private final BroadcastService broadcastService;
    private final QuestService questService;
    private final ResourceDefinitionManager resourceDefinitionManager;

    public GatheringService(Map<String, PlayerSession> sessions, TreeManager treeManager, RockManager rockManager,
                            LootManager lootManager,
                            BroadcastService broadcastService,
                            QuestService questService,
                            ResourceDefinitionManager resourceDefinitionManager) {
        this.sessions = sessions;
        this.treeManager = treeManager;
        this.rockManager = rockManager;
        this.lootManager = lootManager;
        this.broadcastService = broadcastService;
        this.questService = questService;
        this.resourceDefinitionManager = resourceDefinitionManager;
    }

    public void handleDrop(String playerId, int itemId) {
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.loggedIn || !session.isAlive()) return;
        if (!ItemDefinitionManager.exists(itemId)) return;

        boolean stackable = ItemDefinitionManager.get(itemId).stackable;
        int amount = stackable ? session.inventory.countOf(itemId) : 1;
        if (amount <= 0) return;
        if (!session.inventory.removeItem(itemId, amount)) return;

        LootData spawned = lootManager.spawnDrop(session.x, session.y, itemId, amount, playerId);
        broadcastService.sendToPlayer(playerId, session.inventory.buildSnapshot());
        broadcastService.sendToPlayer(playerId, LootManager.buildAddMessage(spawned));
        System.out.println("[GatheringService] " + playerId + " dropped " + amount + "x itemId=" + itemId);
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
        handleGathering(playerId, rockId, SkillType.MINING);
    }

    public void handleChop(String playerId, String treeId) {
        handleGathering(playerId, treeId, SkillType.WOODCUTTING);
    }

    public void handleGathering(String playerId, String objectId, SkillType skillType) {
        if (!ValidationUtil.isValidObjectId(objectId)) return;
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.isAlive()) return;
        if (!session.loggedIn) {
            broadcastService.sendToPlayer(playerId, "SYSTEM Login first with /login <name> or /register <name>.");
            return;
        }

        long cooldownMs = skillType == SkillType.WOODCUTTING ? CHOP_COOLDOWN_MS : MINE_COOLDOWN_MS;
        if (!ValidationUtil.consumeCooldown(session, skillType.actionType(), cooldownMs)) return;

        if (skillType == SkillType.WOODCUTTING) {
            TreeData tree = treeManager.getTree(objectId);
            if (tree == null || !tree.alive) return;
            if (!ValidationUtil.isWithinEntityRange(session.x, session.y, tree.x, tree.y, GATHER_RANGE_SQ)) return;
            handleResourceGather(session, objectId, tree.definitionId, tree.x, tree.y);
            return;
        }

        RockData rock = rockManager.getRock(objectId);
        if (rock == null || !rock.alive) return;
        if (!ValidationUtil.isWithinEntityRange(session.x, session.y, rock.x, rock.y, GATHER_RANGE_SQ)) return;
        handleResourceGather(session, objectId, rock.definitionId, rock.x, rock.y);
    }

    private void handleResourceGather(PlayerSession session, String objectId, int definitionId, int x, int y) {
        ResourceDefinition resource = resourceDefinitionManager.get(definitionId);
        if (!session.inventory.hasSpace(resource.rewardItemId)) {
            session.sendInventoryFullMessage();
            broadcastService.sendToPlayer(session.id, "STOP_ACTION");
            return;
        }

        int level = session.skills.getLevel(resource.skillType.skill());
        if (!GatheringRolls.rollSuccess(resource, level)) {
            return;
        }

        if (!depleteResource(resource, objectId)) return;
        if (!session.inventory.addItem(resource.rewardItemId, 1)) {
            session.sendInventoryFullMessage();
            broadcastService.sendToPlayer(session.id, "STOP_ACTION");
            return;
        }

        session.skills.addXp(resource.skillType.skill(), resource.experience);
        ClientHandler h = session.getHandler();
        if (h != null) {
            h.send(session.inventory.buildSnapshot());
            h.send(SkillService.buildSkillsPacket(session));
        }

        questService.checkAndAdvanceGatherObjective(session, resource.rewardItemId);
        broadcastService.sendToPlayer(session.id, "SKILL_XP " + resource.skillType.packetName() + " " + resource.experience);
        broadcastResourceRemoval(resource, objectId, x, y);
        System.out.println("[GatheringService] " + resource.skillType + " gathered: " + objectId
                + " -> respawn in " + resource.respawnTime + "ms");
    }

    private boolean depleteResource(ResourceDefinition resource, String objectId) {
        return switch (resource.skillType) {
            case WOODCUTTING -> treeManager.chopTree(objectId, resource.respawnTime);
            case MINING -> rockManager.mineRock(objectId, resource.respawnTime);
        };
    }

    private void broadcastResourceRemoval(ResourceDefinition resource, String objectId, int x, int y) {
        RegionKey region;
        String msg;
        if (resource.skillType == SkillType.WOODCUTTING) {
            region = TreeManager.getRegionForPosition(x, y);
            msg = TreeManager.buildRemoveMessage(objectId);
        } else {
            region = RockManager.getRegionForPosition(x, y);
            msg = RockManager.buildRemoveMessage(objectId);
        }

        for (PlayerSession s : new java.util.ArrayList<>(sessions.values())) {
            if (s != null && s.canSeeRegion(region)) {
                broadcastService.sendToPlayer(s.id, msg);
            }
        }
    }

    public void handleLegacyChop(String playerId, String treeId) {
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

        handleResourceGather(session, treeId, tree.definitionId, tree.x, tree.y);
    }
}
