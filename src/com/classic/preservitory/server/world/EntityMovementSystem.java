package com.classic.preservitory.server.world;

import java.util.Collection;
import java.util.Random;

/**
 * Shared movement system for all wandering entities (NPCs and enemies).
 *
 * <p>Any entity that implements {@link MovableEntity} can have its wander
 * movement driven by this class.  There is no duplication between NPC and
 * enemy movement — both are handled here.
 *
 * <p>Wander behaviour per entity:
 * <ol>
 *   <li>Walk toward the current target at {@value #SPEED_PX_PER_MS} px/ms.</li>
 *   <li>On arrival (within {@value #ARRIVE_THRESHOLD} px), count down the idle
 *       timer.  When it expires, pick a new random tile within
 *       {@link MovableEntity#getWanderRadiusPx()} of the spawn point and reset
 *       the timer to a random value in [{@value #MOVE_DELAY_MIN_MS},
 *       {@value #MOVE_DELAY_MAX_MS}] ms.</li>
 *   <li>Repeat.</li>
 * </ol>
 *
 * <p>The gate for each entity is {@link MovableEntity#isWandering()}, not just
 * {@link MovableEntity#isWanderer()} or a radius check.  This means an enemy in
 * AGGRO or ATTACK state is automatically excluded even though it is passed to
 * {@link #update}.
 *
 * <h3>Thread safety</h3>
 * {@link #update} synchronises on each entity before mutating it.
 * {@link #stepToward} is a static utility that expects the caller to hold the
 * entity's monitor.
 *
 * <h3>Debug logging</h3>
 * Set {@link #DEBUG} to {@code true} at startup to log target picks.
 */
public final class EntityMovementSystem {

    /** Set to true to enable per-entity target-pick log lines. */
    public static boolean DEBUG = false;

    /** Movement speed in pixels per millisecond (~1.25 tiles/sec). */
    public static final double SPEED_PX_PER_MS   = 0.040;

    /** Stop moving when this close to the target — avoids floating-point jitter. */
    public static final double ARRIVE_THRESHOLD  = 2.0;

    /** Minimum idle pause between wander moves (ms). */
    public static final long   MOVE_DELAY_MIN_MS = 2_000L;

    /** Maximum idle pause between wander moves (ms). */
    public static final long   MOVE_DELAY_MAX_MS = 5_000L;

    private static final int   TILE_SIZE = TreeManager.TILE_SIZE;
    private static final int   WORLD_W   = 64 * TILE_SIZE;   // 2048 px
    private static final int   WORLD_H   = 64 * TILE_SIZE;   // 2048 px

    private final Random rng = new Random();

    // -----------------------------------------------------------------------
    //  Public API — the only entry point for external callers
    // -----------------------------------------------------------------------

    /**
     * Tick all currently-wandering entities in the collection.
     *
     * <p>Each entity is checked via {@link MovableEntity#isWandering()} — entities
     * that are not wandering right now (e.g. enemies in combat) are skipped without
     * any mutation.
     *
     * @param entities    all live entity instances (may include non-wandering ones)
     * @param deltaTimeMs elapsed milliseconds since the last tick
     * @return {@code true} if any entity position changed (broadcast needed)
     */
    public boolean update(Collection<? extends MovableEntity> entities, long deltaTimeMs) {
        boolean changed = false;
        for (MovableEntity entity : entities) {
            // Pre-check outside the lock to skip obvious non-wanderers cheaply.
            // The check is repeated inside the lock because state can change between
            // the two points (e.g. an enemy in WANDER can be killed mid-tick).
            if (!entity.isWandering()) continue;
            synchronized (entity) {
                // Re-evaluate inside the lock — state may have changed since the
                // pre-check (volatile field, different thread).  This prevents
                // moving an enemy that transitioned out of WANDER before we acquired
                // the monitor.
                if (!entity.isWandering()) continue;
                if (tickWander(entity, deltaTimeMs)) changed = true;
            }
        }
        return changed;
    }

    // -----------------------------------------------------------------------
    //  Static utility — usable by combat-driven movement (EnemyManager aggro)
    // -----------------------------------------------------------------------

    /**
     * Advance {@code entity} one step toward its current target.
     * Caller must hold the entity's monitor.
     *
     * @return {@code true} if the position actually changed.
     */
    public static boolean stepToward(MovableEntity entity, long dt) {
        double dx   = entity.getTargetX() - entity.getX();
        double dy   = entity.getTargetY() - entity.getY();
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist <= ARRIVE_THRESHOLD) return false;

        double step = SPEED_PX_PER_MS * dt;
        if (step >= dist) {
            entity.setX(entity.getTargetX());
            entity.setY(entity.getTargetY());
        } else {
            entity.setX(entity.getX() + (dx / dist) * step);
            entity.setY(entity.getY() + (dy / dist) * step);
        }
        return true;
    }

    // -----------------------------------------------------------------------
    //  Private wander tick — called only from update()
    // -----------------------------------------------------------------------

    /**
     * Advance one entity through the arrival-then-wait wander cycle.
     *
     * <ul>
     *   <li>If the entity has NOT yet arrived at its target: step toward it.</li>
     *   <li>If arrived: count down the idle timer.  When the timer expires,
     *       pick a new random target and reset the timer.</li>
     * </ul>
     *
     * The timer only counts down after arrival, preventing mid-walk direction
     * changes.  No new target is chosen while the entity is still moving.
     *
     * @param entity entity to update (caller holds the monitor)
     * @param dt     elapsed ms
     * @return {@code true} if position changed
     */
    private boolean tickWander(MovableEntity entity, long dt) {
        double dx = entity.getTargetX() - entity.getX();
        double dy = entity.getTargetY() - entity.getY();
        boolean atTarget = (dx * dx + dy * dy) <= ARRIVE_THRESHOLD * ARRIVE_THRESHOLD;

        if (atTarget) {
            entity.setWanderTimer(entity.getWanderTimer() - dt);
            if (entity.getWanderTimer() <= 0) {
                pickWanderTarget(entity);
                long delay = MOVE_DELAY_MIN_MS
                        + (long) (rng.nextDouble() * (MOVE_DELAY_MAX_MS - MOVE_DELAY_MIN_MS));
                entity.setWanderTimer(delay);
            }
            return false;
        }

        return stepToward(entity, dt);
    }

    // -----------------------------------------------------------------------
    //  Target selection — called only from tickWander()
    // -----------------------------------------------------------------------

    /**
     * Choose a random tile within the entity's wander radius, snapped to the
     * tile grid and clamped to world bounds.
     *
     * <p>If the picked tile falls outside the circular radius (square bounding-box
     * overshoot), the target falls back to the spawn position so the entity always
     * remains within its configured range.
     *
     * <p>No-op — and no target change — when {@link MovableEntity#getWanderRadiusPx()}
     * is zero, which should not happen because {@link MovableEntity#isWandering()}
     * is the primary gate.
     */
    private void pickWanderTarget(MovableEntity entity) {
        if (entity.getWanderRadiusPx() <= 0) return;

        int radiusTiles = entity.getWanderRadiusPx() / TILE_SIZE;
        int spawnTileX  = entity.getSpawnX() / TILE_SIZE;
        int spawnTileY  = entity.getSpawnY() / TILE_SIZE;

        int minTileX = Math.max(0,              spawnTileX - radiusTiles);
        int maxTileX = Math.min(WORLD_W / TILE_SIZE, spawnTileX + radiusTiles);
        int minTileY = Math.max(0,              spawnTileY - radiusTiles);
        int maxTileY = Math.min(WORLD_H / TILE_SIZE, spawnTileY + radiusTiles);

        int col = minTileX + rng.nextInt(Math.max(1, maxTileX - minTileX + 1));
        int row = minTileY + rng.nextInt(Math.max(1, maxTileY - minTileY + 1));

        double targetX = col * TILE_SIZE;
        double targetY = row * TILE_SIZE;

        // Circular constraint — reject corners of the bounding box that exceed the radius
        double tdx = targetX - entity.getSpawnX();
        double tdy = targetY - entity.getSpawnY();
        double r   = entity.getWanderRadiusPx();
        if (tdx * tdx + tdy * tdy > r * r) {
            targetX = entity.getSpawnX();
            targetY = entity.getSpawnY();
        }

        entity.setTargetX(targetX);
        entity.setTargetY(targetY);

        if (DEBUG) {
            System.out.println("[EntityMovement] " + entity.getEntityId()
                    + " new target (" + (int) targetX + "," + (int) targetY + ")");
        }
    }
}
