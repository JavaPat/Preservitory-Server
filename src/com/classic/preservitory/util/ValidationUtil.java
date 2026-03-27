package com.classic.preservitory.util;

import com.classic.preservitory.server.player.ActionType;
import com.classic.preservitory.server.player.PlayerSession;
import com.classic.preservitory.server.world.TreeManager;

public class ValidationUtil {

    private static final int WORLD_WIDTH_PX = 30 * TreeManager.TILE_SIZE;
    private static final int WORLD_HEIGHT_PX = 24 * TreeManager.TILE_SIZE;
    private static final double MAX_PLAYER_SPEED_PX_PER_SEC = 175.0D;
    private static final double MOVE_TOLERANCE_PX = 16.0D;
    private static final long MIN_MOVE_WINDOW_MS = 50L;

    public static boolean isValidObjectId(String id) {
        return id != null && !id.isBlank() && id.length() <= 32;
    }

    public static boolean isWithinWorldBounds(int x, int y) {
        return x >= 0 && x <= WORLD_WIDTH_PX && y >= 0 && y <= WORLD_HEIGHT_PX;
    }

    public static boolean isWithinRange(int ax, int ay, int bx, int by, double maxDistanceSq) {
        long dx = (long) bx - ax;
        long dy = (long) by - ay;
        return (double) (dx * dx + dy * dy) <= maxDistanceSq;
    }

    public static boolean isWithinEntityRange(int playerX, int playerY, int entityX, int entityY, double maxDistanceSq) {
        int playerCenterX = playerX + TreeManager.TILE_SIZE / 2;
        int playerCenterY = playerY + TreeManager.TILE_SIZE / 2;
        int entityCenterX = entityX + TreeManager.TILE_SIZE / 2;
        int entityCenterY = entityY + TreeManager.TILE_SIZE / 2;
        return isWithinRange(playerCenterX, playerCenterY, entityCenterX, entityCenterY, maxDistanceSq);
    }

    public static boolean consumeCooldown(PlayerSession session, ActionType actionType, long cooldownMs) {
        long now = System.currentTimeMillis();
        synchronized (session) {
            long lastAt = session.lastActionAtMs.getOrDefault(actionType, 0L);
            if (now - lastAt < cooldownMs) return false;
            session.lastActionAtMs.put(actionType, now);
            return true;
        }
    }

    public static boolean isValidMovement(PlayerSession session, int nextX, int nextY) {
        long now = System.currentTimeMillis();
        long elapsedMs = Math.max(MIN_MOVE_WINDOW_MS, now - session.lastMoveAtMs);
        double allowedDistance = (MAX_PLAYER_SPEED_PX_PER_SEC * elapsedMs / 1000.0) + MOVE_TOLERANCE_PX;
        long dx = (long) nextX - session.x;
        long dy = (long) nextY - session.y;
        return (dx * dx + dy * dy) <= (allowedDistance * allowedDistance);
    }
}
