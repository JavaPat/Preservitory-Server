package com.classic.preservitory.server.world;

import com.classic.preservitory.server.definitions.EnemyDefinition;
import com.classic.preservitory.server.definitions.EnemyDefinitionManager;
import com.classic.preservitory.server.objects.EnemyData;
import com.classic.preservitory.server.objects.EnemyState;
import com.classic.preservitory.server.spawns.SpawnEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all server-side enemy state using a finite state machine (FSM).
 *
 * Each enemy transitions through {@link EnemyState} based on proximity to
 * players and internal timers.  All logic lives here; the client is a pure
 * renderer that reflects the latest ENEMIES snapshot.
 *
 * <h3>Update flow</h3>
 * {@link #update} runs two sequential phases each tick:
 * <ol>
 *   <li><b>FSM phase</b> — handles state transitions for IDLE, AGGRO, ATTACK,
 *       and DEAD.  Combat-driven movement (chasing) happens here via
 *       {@link EntityMovementSystem#stepToward}.</li>
 *   <li><b>Movement phase</b> — delegates to
 *       {@link EntityMovementSystem#update(Collection, long)}.  The movement
 *       system checks {@link com.classic.preservitory.server.objects.EnemyData#isWandering()}
 *       on each enemy and only moves those currently in WANDER state.</li>
 * </ol>
 *
 * This clean separation means:
 * <ul>
 *   <li>Movement logic lives exclusively in {@link EntityMovementSystem}.</li>
 *   <li>Combat logic lives exclusively here.</li>
 *   <li>No duplication with NPCManager movement.</li>
 * </ul>
 */
public class EnemyManager {

    // -----------------------------------------------------------------------
    //  Constants
    // -----------------------------------------------------------------------

    /** Fallback respawn delay if the definition does not specify one. */
    private static final long   RESPAWN_DELAY_MS  = 30_000L;
    private static final long   IDLE_DURATION_MS  =  1_500L;
    private static final int    TILE_SIZE         = TreeManager.TILE_SIZE;

    /** Pixel distance within which an enemy can land a melee hit. */
    private static final double ATTACK_RANGE_PX   = 2.5 * TILE_SIZE;  // ~80 px
    private static final double ATTACK_RANGE_SQ   = ATTACK_RANGE_PX * ATTACK_RANGE_PX;
    private static final double MAX_CHASE_DISTANCE_PX = 8.0 * TILE_SIZE;
    private static final double MAX_CHASE_DISTANCE_SQ = MAX_CHASE_DISTANCE_PX * MAX_CHASE_DISTANCE_PX;

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
    private final EntityMovementSystem movementSystem = new EntityMovementSystem();
    private final Random rng = new Random();

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public EnemyManager(List<SpawnEntry> spawns) {
        for (SpawnEntry spawn : spawns) {
            EnemyDefinition def = EnemyDefinitionManager.get(spawn.definitionId);
            int wanderRadiusPx  = def.wander ? def.wanderRadiusTiles * TILE_SIZE : 0;
            EnemyData e = new EnemyData(spawn.id, def.id, spawn.x, spawn.y,
                                        def.maxHp, def.attackCooldownMs,
                                        def.respawnDelayMs > 0 ? def.respawnDelayMs : RESPAWN_DELAY_MS,
                                        def.wander, wanderRadiusPx);
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
            e.hp = Math.max(0, Math.min(e.maxHp, e.hp - Math.max(0, amount)));
            if (e.hp == 0) {
                e.dyingUntilMs = System.currentTimeMillis() + 1_500L;
                transitionTo(e, EnemyState.DEAD);
                System.out.println("[EnemyManager] Enemy killed: " + id
                        + " (respawn in " + e.respawnDelayMs / 1000 + "s)");
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
    //  Update loop — called by WorldTickService
    // -----------------------------------------------------------------------

    /**
     * Tick every enemy for one time step using a two-phase approach:
     *
     * <ol>
     *   <li><b>FSM phase</b>: transitions and combat movement, one enemy at a time
     *       under the enemy's own monitor.</li>
     *   <li><b>Movement phase</b>: shared {@link EntityMovementSystem} processes all
     *       enemies.  The movement system skips enemies whose
     *       {@link EnemyData#isWandering()} returns false (i.e. any enemy not in
     *       WANDER state).</li>
     * </ol>
     *
     * @param deltaTimeMs     elapsed ms since last call
     * @param playerPositions current world-pixel positions keyed by player ID
     * @return combined result: broadcast flag and any attack events this tick
     */
    public UpdateResult update(long deltaTimeMs, Map<String, int[]> playerPositions) {
        boolean           changed = false;
        List<AttackEvent> attacks = new ArrayList<>();

        // Phase 1 — FSM transitions and combat movement
        for (EnemyData e : enemies.values()) {
            synchronized (e) {
                if (tickFsm(e, deltaTimeMs, playerPositions, attacks)) changed = true;
            }
        }

        // Phase 2 — wander movement (skips non-WANDER enemies via isWandering())
        if (movementSystem.update(enemies.values(), deltaTimeMs)) changed = true;

        return new UpdateResult(changed, attacks);
    }

    // -----------------------------------------------------------------------
    //  FSM dispatcher
    // -----------------------------------------------------------------------

    /**
     * Route this enemy to the correct FSM state handler.
     * WANDER state movement is intentionally absent — handled by Phase 2.
     *
     * @return {@code true} if a combat-driven position or alive-state changed.
     */
    private boolean tickFsm(EnemyData e, long dt,
                             Map<String, int[]> players,
                             List<AttackEvent> attacks) {
        if (e.state != EnemyState.DEAD && e.targetPlayerId != null) {
            return tickCombat(e, dt, players, attacks);
        }
        switch (e.state) {
            case IDLE:   return tickIdle  (e, dt);
            case AGGRO:  return tickAggro (e, dt, players, attacks);
            case ATTACK: return tickAttack(e, dt, players, attacks);
            case DEAD:   return tickDead  (e, dt);
            default:     return false;  // WANDER — movement handled by Phase 2
        }
    }

    // -----------------------------------------------------------------------
    //  State handlers
    // -----------------------------------------------------------------------

    /** IDLE — pause after respawn, then transition to WANDER. */
    private boolean tickIdle(EnemyData e, long dt) {
        e.stateTimer -= dt;
        if (e.stateTimer <= 0) {
            transitionTo(e, EnemyState.WANDER);
        }
        return false;
    }

    /**
     * AGGRO — chase target player. Transitions to ATTACK when in melee range,
     * or back to WANDER if the target is gone.
     */
    private boolean tickAggro(EnemyData e, long dt,
                               Map<String, int[]> players,
                               List<AttackEvent> attacks) {
        return tickCombat(e, dt, players, attacks);
    }

    /**
     * ATTACK — stand in melee range and hit on cooldown.
     * Re-enters AGGRO if the player moves out of range.
     */
    private boolean tickAttack(EnemyData e, long dt,
                                Map<String, int[]> players,
                                List<AttackEvent> attacks) {
        return tickCombat(e, dt, players, attacks);
    }

    /** DEAD — wait for respawn delay, then reset enemy to spawn and re-enter IDLE. */
    private boolean tickDead(EnemyData e, long dt) {
        e.stateTimer -= dt;
        if (e.stateTimer <= 0) {
            e.hp            = e.maxHp;
            e.x             = e.spawnX;
            e.y             = e.spawnY;
            e.targetX       = e.spawnX;
            e.targetY       = e.spawnY;
            e.wanderTimer   = 0L;
            e.attackTimerMs = 0L;
            transitionTo(e, EnemyState.IDLE);
            System.out.println("[EnemyManager] Enemy respawned: " + e.id);
            return true;
        }
        return false;
    }

    /**
     * Shared combat handler used whenever the enemy has a target.
     * Chases while out of melee range, attacks while in range, and disengages
     * if the target vanishes or leaves the chase radius.
     */
    private boolean tickCombat(EnemyData e, long dt,
                               Map<String, int[]> players,
                               List<AttackEvent> attacks) {
        if (e.targetPlayerId == null) {
            transitionTo(e, EnemyState.WANDER);
            return false;
        }

        int[] pos = players.get(e.targetPlayerId);
        if (pos == null) {
            clearTarget(e);
            transitionTo(e, EnemyState.WANDER);
            return false;
        }

        double distSq = distSq(e.x, e.y, pos[0], pos[1]);
        if (distSq > MAX_CHASE_DISTANCE_SQ) {
            clearTarget(e);
            transitionTo(e, EnemyState.WANDER);
            return false;
        }

        e.targetX = pos[0];
        e.targetY = pos[1];

        if (distSq > ATTACK_RANGE_SQ) {
            if (e.state != EnemyState.AGGRO) {
                transitionTo(e, EnemyState.AGGRO);
            }
            return EntityMovementSystem.stepToward(e, dt);
        }

        if (e.state != EnemyState.ATTACK) {
            transitionTo(e, EnemyState.ATTACK);
        }

        e.attackTimerMs -= dt;
        if (e.attackTimerMs <= 0) {
            e.attackTimerMs = e.attackCooldownMs;
            EnemyDefinition def = EnemyDefinitionManager.get(e.definitionId);
            int dmg = def.minDamage + rng.nextInt(Math.max(1, def.maxDamage - def.minDamage + 1));
            attacks.add(new AttackEvent(e.targetPlayerId, dmg));
        }
        return false;
    }

    // -----------------------------------------------------------------------
    //  State transition helper
    // -----------------------------------------------------------------------

    private void transitionTo(EnemyData e, EnemyState next) {
        e.state = next;
        switch (next) {
            case IDLE:
                e.stateTimer = IDLE_DURATION_MS;
                break;
            case WANDER:
                // Reset movement target to current position so the wander system picks a
                // fresh random target within the radius instead of chasing the player's
                // last known location.
                e.targetX = e.x;
                e.targetY = e.y;
                e.stateTimer = 0L;
                break;
            case DEAD:
                e.stateTimer = e.respawnDelayMs;
                break;
            default:
                e.stateTimer = 0L;
                break;
        }
        // wanderTimer is managed exclusively by EntityMovementSystem — never reset here
    }

    private void clearTarget(EnemyData e) {
        e.targetPlayerId = null;
        e.attackTimerMs = 0L;
    }

    // -----------------------------------------------------------------------
    //  Geometry
    // -----------------------------------------------------------------------

    private static double distSq(double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    // -----------------------------------------------------------------------
    //  Protocol builder
    // -----------------------------------------------------------------------

    /**
     * Build a full {@code ENEMIES} snapshot of all non-dead enemies.
     * Format per entry: {@code id x y hp maxHp;}
     */
    public String buildSnapshot() {
        long now = System.currentTimeMillis();
        StringBuilder sb = new StringBuilder("ENEMIES");
        for (EnemyData e : enemies.values()) {
            if (e.state == EnemyState.DEAD && now >= e.dyingUntilMs) continue;
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
