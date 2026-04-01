package com.classic.preservitory.server.objects;

import com.classic.preservitory.server.world.MovableEntity;

public class EnemyData implements MovableEntity {

    public final String id;

    /** Numeric ID into {@link com.classic.preservitory.server.definitions.EnemyDefinitionManager}. */
    public final int    definitionId;

    public final int    spawnX;
    public final int    spawnY;
    public final int    maxHp;

    /** Whether this enemy wanders around its spawn point. */
    public final boolean wanders;

    /** Maximum wander distance from spawn in pixels. Zero when {@link #wanders} is false. */
    public final int     wanderRadiusPx;

    // Position — double for smooth per-tick movement.
    public volatile double x;
    public volatile double y;

    // Current movement destination
    public volatile double targetX;
    public volatile double targetY;

    /**
     * FSM state timer (ms). Meaning depends on state:
     *   IDLE → countdown until WANDER
     *   DEAD → countdown until respawn
     *   AGGRO / ATTACK / WANDER → unused (0)
     *
     * This field is intentionally NOT used for wander movement timing.
     * See {@link #wanderTimer} for that purpose.
     */
    public volatile long stateTimer;

    /**
     * Dedicated wander movement timer (ms).
     * Counts down after arrival at target; when it reaches zero a new wander
     * target is picked.  Kept separate from {@link #stateTimer} so that
     * FSM state durations and movement delays never share state.
     */
    public volatile long wanderTimer;

    /** Fixed time between attacks (ms). */
    public final long     attackCooldownMs;

    /** How long this enemy type waits before respawning (ms). */
    public final long     respawnDelayMs;

    /** Countdown until next attack (ms). 0 = ready. */
    public volatile long  attackTimerMs;

    public volatile int   hp;
    public volatile EnemyState state;

    /** ID of the player this enemy is targeting (null = no target). */
    public volatile String targetPlayerId;

    /**
     * Timestamp (ms) until which a freshly-killed enemy stays visible in the
     * snapshot at 0 HP, giving clients a brief death animation window.
     * 0 = no dying window active.
     */
    public volatile long dyingUntilMs;

    public EnemyData(String id, int definitionId, int x, int y, int maxHp,
                     long attackCooldownMs, long respawnDelayMs,
                     boolean wanders, int wanderRadiusPx) {
        this.id               = id;
        this.definitionId     = definitionId;
        this.spawnX           = x;
        this.spawnY           = y;
        this.x                = x;
        this.y                = y;
        this.targetX          = x;
        this.targetY          = y;
        this.stateTimer       = 0L;
        this.wanderTimer      = 0L;
        this.maxHp            = maxHp;
        this.hp               = maxHp;
        this.state            = EnemyState.WANDER;
        this.attackCooldownMs = attackCooldownMs;
        this.respawnDelayMs   = respawnDelayMs;
        this.attackTimerMs    = 0L;
        this.wanders          = wanders;
        this.wanderRadiusPx   = wanderRadiusPx;
    }

    // -----------------------------------------------------------------------
    //  MovableEntity implementation
    // -----------------------------------------------------------------------

    @Override public double  getX()                  { return x; }
    @Override public void    setX(double v)          { x = v; }
    @Override public double  getY()                  { return y; }
    @Override public void    setY(double v)          { y = v; }
    @Override public double  getTargetX()            { return targetX; }
    @Override public void    setTargetX(double v)    { targetX = v; }
    @Override public double  getTargetY()            { return targetY; }
    @Override public void    setTargetY(double v)    { targetY = v; }
    @Override public int     getSpawnX()             { return spawnX; }
    @Override public int     getSpawnY()             { return spawnY; }
    @Override public boolean isWanderer()            { return wanders; }
    @Override public int     getWanderRadiusPx()     { return wanderRadiusPx; }
    @Override public String  getEntityId()           { return id; }

    /**
     * Returns {@code true} only when the FSM is in WANDER state AND this enemy
     * is configured to wander.  Combat states (AGGRO, ATTACK) suppress movement
     * automatically — no external toggle required.
     */
    @Override public boolean isWandering() { return wanders && state == EnemyState.WANDER; }

    /** Delegates to the dedicated {@link #wanderTimer} field, not {@link #stateTimer}. */
    @Override public long    getWanderTimer()        { return wanderTimer; }
    @Override public void    setWanderTimer(long v)  { wanderTimer = v; }
}
