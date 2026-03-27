package com.classic.preservitory.server.world;

import com.classic.preservitory.server.Constants;
import com.classic.preservitory.server.combat.CombatServices;
import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.objects.RockData;
import com.classic.preservitory.server.objects.TreeData;
import com.classic.preservitory.server.player.PlayerSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class WorldTickService {

    private static final long BROADCAST_MS = Constants.BROADCAST_MS;

    private final Map<String, PlayerSession> sessions;
    private final TreeManager treeManager;
    private final RockManager rockManager;
    private final EnemyManager enemyManager;
    private final BroadcastService broadcastService;
    private final CombatServices combatServices;
    private final AtomicBoolean playerDirty;

    public WorldTickService(Map<String, PlayerSession> sessions,
                            TreeManager treeManager,
                            RockManager rockManager,
                            EnemyManager enemyManager,
                            BroadcastService broadcastService,
                            CombatServices combatServices,
                            AtomicBoolean playerDirty) {
        this.sessions = sessions;
        this.treeManager = treeManager;
        this.rockManager = rockManager;
        this.enemyManager = enemyManager;
        this.broadcastService = broadcastService;
        this.combatServices = combatServices;
        this.playerDirty = playerDirty;
    }

    public void start() {
        Thread thread = new Thread(() -> {
            long lastTickAt = System.currentTimeMillis();
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(BROADCAST_MS);
                    long now = System.currentTimeMillis();
                    long deltaTimeMs = now - lastTickAt;
                    lastTickAt = now;

                    if (playerDirty.compareAndSet(true, false)) {
                        broadcastService.broadcastPositions();
                    }

                    // Tick trees
                    List<TreeData> respawnedTrees = treeManager.update(deltaTimeMs);
                    for (TreeData t : respawnedTrees) {
                        System.out.println("[Server] Tree respawned: " + t.id);
                        RegionKey region = TreeManager.getRegionForPosition(t.x, t.y);
                        String msg = TreeManager.buildAddMessage(t);
                        for (PlayerSession s : new ArrayList<>(sessions.values())) {
                            if (s.canSeeRegion(region)) broadcastService.sendToPlayer(s.id, msg);
                        }
                    }

                    // Tick rocks
                    List<RockData> respawnedRocks = rockManager.update(deltaTimeMs);
                    for (RockData r : respawnedRocks) {
                        System.out.println("[Server] Rock respawned: " + r.id);
                        RegionKey region = RockManager.getRegionForPosition(r.x, r.y);
                        String msg = RockManager.buildAddMessage(r);
                        for (PlayerSession s : new ArrayList<>(sessions.values())) {
                            if (s.canSeeRegion(region)) broadcastService.sendToPlayer(s.id, msg);
                        }
                    }

                    // Tick enemies
                    Map<String, int[]> playerPositions = new HashMap<>();

                    for (PlayerSession s : new ArrayList<>(sessions.values())) {
                        if (!s.isAlive() || s.isSpawnProtected()) continue;
                        playerPositions.put(s.id, new int[]{s.x, s.y});
                    }
                    EnemyManager.UpdateResult enemyResult = enemyManager.update(deltaTimeMs, playerPositions);
                    if (enemyResult.positionChanged) {
                        broadcastService.broadcastAll(enemyManager.buildSnapshot());
                    }
                    for (EnemyManager.AttackEvent attack : enemyResult.attacks) {
                        combatServices.damagePlayer(attack.playerId, attack.damage);
                    }
                    combatServices.handleRespawns();

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "server-broadcaster");

        thread.setDaemon(true);
        thread.start();
    }
}
