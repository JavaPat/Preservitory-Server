package com.classic.preservitory.server.woodcutting;

import com.classic.preservitory.server.gathering.GatheringRolls;
import com.classic.preservitory.server.gathering.ResourceDefinition;
import com.classic.preservitory.server.gathering.ResourceDefinitionManager;
import com.classic.preservitory.server.gathering.SkillType;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.objects.TreeData;
import com.classic.preservitory.server.player.ActionType;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillService;
import com.classic.preservitory.server.quest.QuestService;
import com.classic.preservitory.server.world.RegionKey;
import com.classic.preservitory.server.world.TreeManager;
import com.classic.preservitory.util.ValidationUtil;

import java.util.ArrayList;
import java.util.Map;

public final class WoodcuttingService {

    private static final double CHOP_RANGE_PX = TreeManager.TILE_SIZE * 1.6;
    private static final double CHOP_RANGE_SQ = CHOP_RANGE_PX * CHOP_RANGE_PX;
    private static final long CHOP_INTERVAL_MS = 2_000L;
    private static final long START_COOLDOWN_MS = 250L;

    private final Map<String, PlayerSession> sessions;
    private final TreeManager treeManager;
    private final ResourceDefinitionManager resourceDefinitionManager;
    private final AxeDefinitionManager axeDefinitionManager;
    private final BroadcastService broadcastService;
    private final QuestService questService;

    public WoodcuttingService(Map<String, PlayerSession> sessions,
                              TreeManager treeManager,
                              ResourceDefinitionManager resourceDefinitionManager,
                              AxeDefinitionManager axeDefinitionManager,
                              BroadcastService broadcastService,
                              QuestService questService) {
        this.sessions = sessions;
        this.treeManager = treeManager;
        this.resourceDefinitionManager = resourceDefinitionManager;
        this.axeDefinitionManager = axeDefinitionManager;
        this.broadcastService = broadcastService;
        this.questService = questService;
    }

    public void startChopping(String playerId, String treeId) {
        if (!ValidationUtil.isValidObjectId(treeId)) {
            return;
        }

        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.isAlive()) {
            return;
        }
        if (!session.loggedIn) {
            //broadcastService.sendToPlayer(playerId, "SYSTEM Login first with /login <name> or /register <name>.");
            return;
        }
        if (!ValidationUtil.consumeCooldown(session, ActionType.CHOP, START_COOLDOWN_MS)) {
            return;
        }

        TreeData tree = treeManager.getTree(treeId);
        if (tree == null || !tree.alive) {
            stopChopping(session, true);
            return;
        }
        if (!ValidationUtil.isWithinEntityRange(session.x, session.y, tree.x, tree.y, CHOP_RANGE_SQ)) {
            stopChopping(session, true);
            return;
        }

        ResourceDefinition resource = resourceDefinitionManager.get(tree.definitionId, SkillType.WOODCUTTING);
        if (resource == null) {
            sendGatherFail(session, "You can't chop that tree.");
            return;
        }
        int woodcuttingLevel = session.skills.getLevel(Skill.WOODCUTTING);
        if (axeDefinitionManager.getBestAvailable(session) == null) {
            sendGatherFail(session, "You need an axe to chop this tree.");
            return;
        }
        if (woodcuttingLevel < resource.levelRequired) {
            sendGatherFail(session, "You need Woodcutting level " + resource.levelRequired + " to chop this tree.");
            return;
        }
        if (!session.inventory.hasSpace(resource.rewardItemId)) {
            sendGatherFail(session, "Your inventory is full.");
            return;
        }

        session.activeTreeId = treeId;
        session.lastChopTime = System.currentTimeMillis();
        broadcastService.sendToPlayer(session.id, "START_GATHERING\twoodcutting\t" + treeId);
    }

    public void tick(long now) {
        for (PlayerSession session : new ArrayList<>(sessions.values())) {
            if (session == null || session.activeTreeId == null) {
                continue;
            }
            tickPlayer(session, now);
        }
    }

    private void tickPlayer(PlayerSession session, long now) {
        if (!session.loggedIn || !session.isAlive()) {
            stopChopping(session, false);
            return;
        }

        String activeTreeId = session.activeTreeId;
        TreeData tree = treeManager.getTree(activeTreeId);
        if (tree == null || !tree.alive) {
            stopChopping(session, true);
            return;
        }
        if (!ValidationUtil.isWithinEntityRange(session.x, session.y, tree.x, tree.y, CHOP_RANGE_SQ)) {
            stopChopping(session, true);
            return;
        }
        AxeDefinition axe = axeDefinitionManager.getBestAvailable(session);
        if (axe == null) {
            stopChopping(session, true);
            return;
        }
        if (now - session.lastChopTime < getChopDelayMs(axe)) {
            return;
        }

        ResourceDefinition resource = resourceDefinitionManager.get(tree.definitionId, SkillType.WOODCUTTING);
        if (!session.inventory.hasSpace(resource.rewardItemId)) {
            session.sendInventoryFullMessage();
            stopChopping(session, true);
            return;
        }

        session.lastChopTime = now;

        int woodcuttingLevel = session.skills.getLevel(Skill.WOODCUTTING);
        if (!GatheringRolls.rollSuccess(resource, woodcuttingLevel)) {
            return;
        }

        if (!session.inventory.addItem(resource.rewardItemId, 1)) {
            session.sendInventoryFullMessage();
            stopChopping(session, true);
            return;
        }

        session.skills.addXp(Skill.WOODCUTTING, resource.experience);
        ClientHandler handler = session.getHandler();
        if (handler != null) {
            handler.send(session.inventory.buildSnapshot());
            handler.send(SkillService.buildSkillsPacket(session));
        }
        questService.checkAndAdvanceGatherObjective(session, resource.rewardItemId);
        broadcastService.sendToPlayer(session.id, "SKILL_XP woodcutting " + resource.experience);

        if (GatheringRolls.rollDepletion(resource, woodcuttingLevel)
                && treeManager.chopTree(tree.id, resource.respawnTime)) {
            broadcastTreeRemoval(tree.id, tree.x, tree.y);
            stopChoppingTree(tree.id);
        }
    }

    private long getChopDelayMs(AxeDefinition axe) {
        return Math.max(600L, Math.round(CHOP_INTERVAL_MS * axe.speedMultiplier));
    }

    private void stopChoppingTree(String treeId) {
        for (PlayerSession session : new ArrayList<>(sessions.values())) {
            if (session != null && treeId.equals(session.activeTreeId)) {
                stopChopping(session, true);
            }
        }
    }

    private void broadcastTreeRemoval(String treeId, int treeX, int treeY) {
        RegionKey region = TreeManager.getRegionForPosition(treeX, treeY);
        String msg = TreeManager.buildRemoveMessage(treeId);
        for (PlayerSession session : new ArrayList<>(sessions.values())) {
            if (session != null && session.canSeeRegion(region)) {
                broadcastService.sendToPlayer(session.id, msg);
            }
        }
    }

    private void stopChopping(PlayerSession session, boolean sendStopAction) {
        session.activeTreeId = null;
        session.lastChopTime = 0L;
        if (sendStopAction) {
            broadcastService.sendToPlayer(session.id, "STOP_ACTION");
        }
    }

    private void sendGatherFail(PlayerSession session, String message) {
        stopChopping(session, false);
        broadcastService.sendToPlayer(session.id, "GATHER_FAIL\t" + message);
    }
}
