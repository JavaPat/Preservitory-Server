package com.classic.preservitory.server.world;

import com.classic.preservitory.server.net.BroadcastService;
import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.objects.RockData;
import com.classic.preservitory.server.objects.TreeData;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.quest.QuestService;
import com.classic.preservitory.util.ValidationUtil;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Handles player position updates and world region streaming.
 *
 * Responsible for:
 *   - Validating and applying position updates
 *   - Computing which regions become visible / invisible on movement
 *   - Sending ADD/REMOVE packets for trees and rocks as regions change
 *   - Syncing full world state (objects, NPCs, enemies, loot, inventory) on login or connect
 */
public class RegionService {

    private final Map<String, PlayerSession> sessions;
    private final TreeManager    treeManager;
    private final RockManager    rockManager;
    private final NPCManager     npcManager;
    private final EnemyManager   enemyManager;
    private final LootManager    lootManager;
    private final BroadcastService broadcastService;
    private final AtomicBoolean  playerDirty;

    public RegionService(Map<String, PlayerSession> sessions,
                         TreeManager treeManager,
                         RockManager rockManager,
                         NPCManager npcManager,
                         EnemyManager enemyManager,
                         LootManager lootManager,
                         BroadcastService broadcastService,
                         AtomicBoolean playerDirty) {
        this.sessions         = sessions;
        this.treeManager      = treeManager;
        this.rockManager      = rockManager;
        this.npcManager       = npcManager;
        this.enemyManager     = enemyManager;
        this.lootManager      = lootManager;
        this.broadcastService = broadcastService;
        this.playerDirty      = playerDirty;
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
    //  Session sync — called on connect and after login/register
    // -----------------------------------------------------------------------

    /**
     * Sends the full world snapshot (objects, NPCs, enemies, loot, inventory)
     * to a session and sets up its initial region membership.
     */
    public void syncSessionState(PlayerSession session) {
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
            h.send(lootManager.buildSnapshotForPlayer(session.id, System.currentTimeMillis()));

            if (session.loggedIn) {
                broadcastService.sendToPlayer(session.id, "PLAYER_HP " + session.hp + " " + session.getMaxHp());
                h.send(session.inventory.buildSnapshot());
                h.send(QuestService.buildQuestLogPacket(session));
            }
        }

        broadcastService.broadcastPositions();
    }

    // -----------------------------------------------------------------------
    //  Region streaming helpers
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
}
