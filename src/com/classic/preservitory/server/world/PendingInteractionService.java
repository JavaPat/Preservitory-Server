package com.classic.preservitory.server.world;

import com.classic.preservitory.server.combat.CombatServices;
import com.classic.preservitory.server.npc.NPCData;
import com.classic.preservitory.server.objects.EnemyData;
import com.classic.preservitory.server.objects.EnemyState;
import com.classic.preservitory.server.objects.RockData;
import com.classic.preservitory.server.objects.TreeData;
import com.classic.preservitory.server.player.InteractionType;
import com.classic.preservitory.server.player.PendingInteraction;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.shops.ShopService;
import com.classic.preservitory.server.dialogue.DialogueService;
import com.classic.preservitory.server.woodcutting.WoodcuttingService;
import com.classic.preservitory.util.ValidationUtil;

import java.util.ArrayList;
import java.util.Map;

public final class PendingInteractionService {

    private final Map<String, PlayerSession> sessions;
    private final TreeManager treeManager;
    private final RockManager rockManager;
    private final NPCManager npcManager;
    private final EnemyManager enemyManager;
    private final GatheringService gatheringService;
    private final WoodcuttingService woodcuttingService;
    private final CombatServices combatServices;
    private final DialogueService dialogueService;
    private final ShopService shopService;

    public PendingInteractionService(Map<String, PlayerSession> sessions,
                                     TreeManager treeManager,
                                     RockManager rockManager,
                                     NPCManager npcManager,
                                     EnemyManager enemyManager,
                                     GatheringService gatheringService,
                                     WoodcuttingService woodcuttingService,
                                     CombatServices combatServices,
                                     DialogueService dialogueService,
                                     ShopService shopService) {
        this.sessions = sessions;
        this.treeManager = treeManager;
        this.rockManager = rockManager;
        this.npcManager = npcManager;
        this.enemyManager = enemyManager;
        this.gatheringService = gatheringService;
        this.woodcuttingService = woodcuttingService;
        this.combatServices = combatServices;
        this.dialogueService = dialogueService;
        this.shopService = shopService;
    }

    public void queueGather(String playerId, String targetId) {
        setPending(playerId, new PendingInteraction(InteractionType.GATHER, targetId, interactionDistancePx()));
    }

    public void queueAttack(String playerId, String targetId) {
        setPending(playerId, new PendingInteraction(InteractionType.ATTACK, targetId, interactionDistancePx()));
    }

    public void queueTalk(String playerId, String targetId) {
        setPending(playerId, new PendingInteraction(InteractionType.TALK, targetId, interactionDistancePx()));
    }

    public void queueShop(String playerId, String targetId) {
        setPending(playerId, new PendingInteraction(InteractionType.SHOP, targetId, interactionDistancePx()));
    }

    public void clear(String playerId) {
        PlayerSession session = sessions.get(playerId);
        if (session != null) {
            session.pendingInteraction = null;
        }
    }

    public void process(String playerId) {
        PlayerSession session = sessions.get(playerId);
        if (session != null) {
            process(session);
        }
    }

    public void processAll() {
        for (PlayerSession session : new ArrayList<>(sessions.values())) {
            if (session != null) {
                process(session);
            }
        }
    }

    private void setPending(String playerId, PendingInteraction pendingInteraction) {
        PlayerSession session = sessions.get(playerId);
        if (session == null || !session.loggedIn || !session.isAlive()) {
            return;
        }
        session.pendingInteraction = pendingInteraction;
        process(session);
    }

    private void process(PlayerSession session) {
        PendingInteraction pending = session.pendingInteraction;
        if (pending == null || !session.loggedIn || !session.isAlive()) {
            return;
        }

        TargetPosition target = resolveTarget(pending);
        if (target == null) {
            session.pendingInteraction = null;
            return;
        }

        double requiredDistanceSq = (double) pending.requiredDistance * pending.requiredDistance;
        if (!ValidationUtil.isWithinEntityRange(session.x, session.y, target.x, target.y, requiredDistanceSq)) {
            return;
        }

        session.pendingInteraction = null;
        switch (pending.type) {
            case GATHER -> executeGather(session.id, pending.targetId);
            case ATTACK -> combatServices.handleAttack(session.id, pending.targetId);
            case TALK -> dialogueService.handleTalk(session.id, pending.targetId);
            case SHOP -> shopService.openShop(session.id, pending.targetId);
        }
    }

    private void executeGather(String playerId, String targetId) {
        TreeData tree = treeManager.getTree(targetId);
        if (tree != null && tree.alive) {
            woodcuttingService.startChopping(playerId, targetId);
            return;
        }

        RockData rock = rockManager.getRock(targetId);
        if (rock != null && rock.alive) {
            gatheringService.handleMine(playerId, targetId);
        }
    }

    private TargetPosition resolveTarget(PendingInteraction pending) {
        return switch (pending.type) {
            case GATHER -> resolveGatherTarget(pending.targetId);
            case ATTACK -> resolveEnemyTarget(pending.targetId);
            case TALK, SHOP -> resolveNpcTarget(pending.targetId);
        };
    }

    private TargetPosition resolveGatherTarget(String targetId) {
        TreeData tree = treeManager.getTree(targetId);
        if (tree != null && tree.alive) {
            return new TargetPosition(tree.x, tree.y);
        }

        RockData rock = rockManager.getRock(targetId);
        if (rock != null && rock.alive) {
            return new TargetPosition(rock.x, rock.y);
        }
        return null;
    }

    private TargetPosition resolveNpcTarget(String targetId) {
        NPCData npc = npcManager.getNpc(targetId);
        return npc != null ? new TargetPosition((int) npc.x, (int) npc.y) : null;
    }

    private TargetPosition resolveEnemyTarget(String targetId) {
        EnemyData enemy = enemyManager.getEnemy(targetId);
        if (enemy == null) {
            return null;
        }
        synchronized (enemy) {
            if (enemy.state == EnemyState.DEAD || enemy.hp <= 0) {
                return null;
            }
            return new TargetPosition((int) enemy.x, (int) enemy.y);
        }
    }

    private int interactionDistancePx() {
        return (int) Math.round(TreeManager.TILE_SIZE * 1.7);
    }

    private record TargetPosition(int x, int y) {}
}
