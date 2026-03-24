package com.classic.preservitory.server.world;

import com.classic.preservitory.server.objects.EnemyData;
import com.classic.preservitory.server.objects.EnemyState;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all server-side enemy state using a finite state machine (FSM).
 *
 * Each enemy transitions through {@link EnemyState} based on proximity to
 * players and internal timers.  All logic lives here; the client is a pure
 * renderer that reflects the latest ENEMIES snapshot.
 *
 * Current behaviour is intentionally passive: goblins wander and can be
 * attacked by players, but they do not auto-aggro or retaliate yet.
 */
public class EnemyManager {

    // -----------------------------------------------------------------------
    //  Constants
    // -----------------------------------------------------------------------

    private static final long   RESPAWN_DELAY_MS  = 30_000L;
    private static final long   IDLE_DURATION_MS  =  1_500L;  // pause after respawn
    private static final int    TILE_SIZE         = 32;
    private static final int    GOBLIN_MAX_HP     = 10;

    /** Movement speed in pixels per millisecond (~1.25 tiles/sec). */
    private static final double SPEED_PX_PER_MS   = 0.040;

    /** How far from its spawn an enemy may wander (px). */
    private static final int    WANDER_RADIUS_PX  = 3 * TILE_SIZE;  // 96 px

    /** Min/max ms between wander-target picks. */
    private static final long   WANDER_MIN_MS     = 3_000L;
    private static final long   WANDER_MAX_MS     = 6_000L;

    /** Stop moving when this close to the target (avoids jitter). */
    private static final double ARRIVE_THRESHOLD  = 2.0;

    /** Pixel distance within which an enemy can land a melee hit. */
    private static final double ATTACK_RANGE_PX  = 2.5 * TILE_SIZE;  // ~80 px
    private static final double ATTACK_RANGE_SQ  = ATTACK_RANGE_PX * ATTACK_RANGE_PX;

    /** Min/max damage per enemy hit. */
    private static final int    ENEMY_DMG_MIN     = 1;
    private static final int    ENEMY_DMG_MAX     = 3;

    private static final int    WORLD_W           = 30 * TILE_SIZE;  // 960 px
    private static final int    WORLD_H           = 24 * TILE_SIZE;  // 768 px

    // -----------------------------------------------------------------------
    //  Result types returned by update()
    // -----------------------------------------------------------------------

    /** One enemy→player melee hit that occurred this tick. */
    public static final class AttackEvent {
        public final String playerId;
        public final int    damage;
        public AttackEvent(String playerId, int damage) {
            this.playerId = playerId;
            this.damage   = damage;
        }
    }

    /** Combined result of a single {@link #update} call. */
    public static final class UpdateResult {
        /** True if any enemy position or alive-state changed — broadcast needed. */
        public final boolean           positionChanged;
        /** All enemy→player hits that fired this tick (may be empty). */
        public final List<AttackEvent> attacks;
        public UpdateResult(boolean positionChanged, List<AttackEvent> attacks) {
            this.positionChanged = positionChanged;
            this.attacks         = attacks;
        }
    }

    // -----------------------------------------------------------------------
    //  State
    // -----------------------------------------------------------------------

    private final ConcurrentHashMap<String, EnemyData> enemies = new ConcurrentHashMap<>();
    private final Random rng = new Random();

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public EnemyManager() {
        int[][] positions = {
                { 8,  6}, {13,  5}, { 7, 11},
                {18,  7}, {11, 13},
                {24, 10}, {22, 17}
        };
        for (int i = 0; i < positions.length; i++) {
            int x = positions[i][0] * TILE_SIZE;
            int y = positions[i][1] * TILE_SIZE;
            EnemyData e = new EnemyData("G" + i, x, y, GOBLIN_MAX_HP);
            enemies.put(e.id, e);
        }
        System.out.println("[EnemyManager] Spawned " + enemies.size() + " enemies.");
    }

    // -----------------------------------------------------------------------
    //  Queries
    // -----------------------------------------------------------------------

    public EnemyData getEnemy(String id) {
        return enemies.get(id);
    }

    // -----------------------------------------------------------------------
    //  Mutation — player attacking enemy
    // -----------------------------------------------------------------------

    /**
     * Apply damage to an enemy from a player hit.
     *
     * @return {@code true} if the enemy was just killed by this hit.
     */
    public boolean damageEnemy(String id, int amount) {
        EnemyData e = enemies.get(id);
        if (e == null) return false;
        synchronized (e) {
            if (e.state == EnemyState.DEAD) return false;
            int appliedDamage = Math.max(0, amount);
            e.hp = Math.max(0, Math.min(e.maxHp, e.hp - appliedDamage));
            if (e.hp == 0) {
                transitionTo(e, EnemyState.DEAD);
                System.out.println("[EnemyManager] Enemy killed: " + id
                        + " (respawn in " + RESPAWN_DELAY_MS / 1000 + "s)");
                return true;
            }
        }
        return false;
    }

    /**
     * Make the enemy retaliate against the given player.
     * Called by GameServer when a player attacks this enemy.
     */
    public void engagePlayer(String enemyId, String playerId) {
        EnemyData e = enemies.get(enemyId);
        if (e == null) return;
        synchronized (e) {
            if (e.state == EnemyState.DEAD) return;
            e.targetPlayerId = playerId;
            if (e.state != EnemyState.ATTACK) {
                transitionTo(e, EnemyState.AGGRO);
            }
        }
    }

    // -----------------------------------------------------------------------
    //  Update loop — called by the broadcast thread
    // -----------------------------------------------------------------------

    /**
     * Tick every enemy through its FSM for one time step.
     *
     * @param deltaTimeMs     elapsed ms since last call
     * @param playerPositions current world-pixel positions keyed by player ID
     * @return combined result: broadcast flag and any attack events this tick
     */
    public UpdateResult update(long deltaTimeMs, Map<String, int[]> playerPositions) {
        boolean           changed = false;
        List<AttackEvent> attacks = new ArrayList<>();

        for (EnemyData e : enemies.values()) {
            synchronized (e) {
                if (tickEnemy(e, deltaTimeMs, playerPositions, attacks)) {
                    changed = true;
                }
            }
        }

        return new UpdateResult(changed, attacks);
    }

    // -----------------------------------------------------------------------
    //  FSM dispatcher
    // -----------------------------------------------------------------------

    /**
     * Route this enemy to the correct state handler.
     *
     * @return {@code true} if the enemy's position or alive-state changed.
     */
    private boolean tickEnemy(EnemyData e, long dt,
                               Map<String, int[]> players,
                               List<AttackEvent> attacks) {
        switch (e.state) {
            case IDLE:   return tickIdle  (e, dt, players);
            case WANDER: return tickWander(e, dt, players);
            case AGGRO:  return tickAggro (e, dt, players, attacks);
            case ATTACK: return tickAttack(e, dt, players, attacks);
            case DEAD:   return tickDead  (e, dt);
            default:     return false;
        }
    }

    // -----------------------------------------------------------------------
    //  State handlers
    // -----------------------------------------------------------------------

    /**
     * IDLE — enemy stands motionless at its spawn for a short duration after
     * respawning, then transitions to WANDER.
     */
    private boolean tickIdle(EnemyData e, long dt, Map<String, int[]> players) {
        e.stateTimer -= dt;
        if (e.stateTimer <= 0) {
            transitionTo(e, EnemyState.WANDER);
        }
        return false; // no movement while idle
    }

    /**
     * WANDER — enemy picks random tiles near its spawn and walks between them.
     */
    private boolean tickWander(EnemyData e, long dt, Map<String, int[]> players) {
        e.stateTimer -= dt;
        if (e.stateTimer <= 0) {
            pickWanderTarget(e);
            e.stateTimer = WANDER_MIN_MS
                    + (long)(rng.nextDouble() * (WANDER_MAX_MS - WANDER_MIN_MS));
        }

        return stepToward(e, dt);
    }

    /**
     * AGGRO — enemy chases its target player. Transitions to ATTACK when in
     * melee range, or back to WANDER if the target has gone.
     */
    private boolean tickAggro(EnemyData e, long dt,
                               Map<String, int[]> players,
                               List<AttackEvent> attacks) {
        if (e.targetPlayerId == null) {
            transitionTo(e, EnemyState.WANDER);
            return false;
        }
        int[] pos = players.get(e.targetPlayerId);
        if (pos == null) {
            // Player disconnected or dead — give up
            e.targetPlayerId = null;
            transitionTo(e, EnemyState.WANDER);
            return false;
        }
        if (distSq(e.x, e.y, pos[0], pos[1]) <= ATTACK_RANGE_SQ) {
            transitionTo(e, EnemyState.ATTACK);
            return false;
        }
        e.targetX = pos[0];
        e.targetY = pos[1];
        return stepToward(e, dt);
    }

    /**
     * ATTACK — enemy stands in range and hits the target player on a cooldown.
     * Chases again if the player moves out of melee range.
     */
    private boolean tickAttack(EnemyData e, long dt,
                                Map<String, int[]> players,
                                List<AttackEvent> attacks) {
        if (e.targetPlayerId == null) {
            transitionTo(e, EnemyState.WANDER);
            return false;
        }
        int[] pos = players.get(e.targetPlayerId);
        if (pos == null) {
            e.targetPlayerId = null;
            transitionTo(e, EnemyState.WANDER);
            return false;
        }
        if (distSq(e.x, e.y, pos[0], pos[1]) > ATTACK_RANGE_SQ) {
            transitionTo(e, EnemyState.AGGRO);
            return false;
        }
        e.attackTimerMs -= dt;
        if (e.attackTimerMs <= 0) {
            e.attackTimerMs = e.attackCooldownMs;
            int dmg = ENEMY_DMG_MIN + rng.nextInt(ENEMY_DMG_MAX - ENEMY_DMG_MIN + 1);
            attacks.add(new AttackEvent(e.targetPlayerId, dmg));
        }
        return false;
    }

    /**
     * DEAD — counts down the respawn timer, then resets the enemy to its
     * spawn position and transitions to IDLE.
     */
    private boolean tickDead(EnemyData e, long dt) {
        e.stateTimer -= dt;
        if (e.stateTimer <= 0) {
            e.hp          = e.maxHp;
            e.x           = e.spawnX;
            e.y           = e.spawnY;
            e.targetX     = e.spawnX;
            e.targetY     = e.spawnY;
            e.attackTimerMs = 0L;
            transitionTo(e, EnemyState.IDLE);
            System.out.println("[EnemyManager] Enemy respawned: " + e.id);
            return true;  // now visible in snapshot again
        }
        return false;
    }

    // -----------------------------------------------------------------------
    //  State transition helper
    // -----------------------------------------------------------------------

    /**
     * Change {@code e}'s state and initialise any entry data for the new state.
     * Callers are responsible for resetting unrelated data (e.g. HP on respawn).
     */
    private void transitionTo(EnemyData e, EnemyState next) {
        e.state = next;
        switch (next) {
            case IDLE:
                e.stateTimer = IDLE_DURATION_MS;
                break;
            case WANDER:
                pickWanderTarget(e);
                e.stateTimer = WANDER_MIN_MS
                        + (long)(rng.nextDouble() * (WANDER_MAX_MS - WANDER_MIN_MS));
                break;
            case DEAD:
                e.stateTimer = RESPAWN_DELAY_MS;
                break;
            default:
                e.stateTimer = 0L;  // AGGRO and ATTACK have no entry timer
                break;
        }
    }

    // -----------------------------------------------------------------------
    //  Movement helpers
    // -----------------------------------------------------------------------

    private static double distSq(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    /**
     * Advance {@code e} one step toward its current target.
     *
     * @return {@code true} if the position actually changed.
     */
    private boolean stepToward(EnemyData e, long dt) {
        double dx   = e.targetX - e.x;
        double dy   = e.targetY - e.y;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist <= ARRIVE_THRESHOLD) return false;

        double step = SPEED_PX_PER_MS * dt;
        if (step >= dist) {
            e.x = e.targetX;
            e.y = e.targetY;
        } else {
            e.x += (dx / dist) * step;
            e.y += (dy / dist) * step;
        }
        return true;
    }

    /**
     * Choose a random tile within {@link #WANDER_RADIUS_PX} of the enemy's
     * spawn, snapped to the tile grid and clamped to the world boundary.
     */
    private void pickWanderTarget(EnemyData e) {
        int minX = Math.max(0,       e.spawnX - WANDER_RADIUS_PX);
        int maxX = Math.min(WORLD_W, e.spawnX + WANDER_RADIUS_PX);
        int minY = Math.max(0,       e.spawnY - WANDER_RADIUS_PX);
        int maxY = Math.min(WORLD_H, e.spawnY + WANDER_RADIUS_PX);

        int col = (minX / TILE_SIZE) + rng.nextInt(
                Math.max(1, (maxX / TILE_SIZE) - (minX / TILE_SIZE) + 1));
        int row = (minY / TILE_SIZE) + rng.nextInt(
                Math.max(1, (maxY / TILE_SIZE) - (minY / TILE_SIZE) + 1));

        e.targetX = col * TILE_SIZE;
        e.targetY = row * TILE_SIZE;
    }

    // -----------------------------------------------------------------------
    //  Protocol builder
    // -----------------------------------------------------------------------

    /**
     * Build a full {@code ENEMIES} snapshot of all non-dead enemies.
     * Format per entry: {@code id x y hp maxHp;}
     * Position is truncated to integer pixels for the wire format.
     */
    public String buildSnapshot() {
        StringBuilder sb = new StringBuilder("ENEMIES");
        for (EnemyData e : enemies.values()) {
            if (e.state == EnemyState.DEAD) continue;
            sb.append(' ').append(e.id)
              .append(' ').append((int) e.x)
              .append(' ').append((int) e.y)
              .append(' ').append(e.hp)
              .append(' ').append(e.maxHp)
              .append(';');
        }
        return sb.toString();
    }
}
