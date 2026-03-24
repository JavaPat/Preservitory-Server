package com.classic.preservitory.server.objects;

public class EnemyData {

    public final String id;
    public final int    spawnX;   // original spawn position — never changes
    public final int    spawnY;
    public final int    maxHp;

    // Position — double for smooth per-tick movement.
    // buildSnapshot() casts to int when building the protocol message.
    public volatile double     x;
    public volatile double     y;

    // Current movement destination
    public volatile double     targetX;
    public volatile double     targetY;

    /**
     * General-purpose state timer (ms).  Its meaning depends on the current FSM state:
     *   IDLE   → countdown until transition to WANDER
     *   WANDER → countdown until next wander-target pick
     *   DEAD   → countdown until respawn (transition to IDLE)
     *   AGGRO / ATTACK → unused (0)
     */
    public volatile long       stateTimer;

    /** Fixed time between attacks (ms). Set once at construction. */
    public final long          attackCooldownMs;

    /** Countdown until the next attack is allowed (ms). 0 = ready to attack. */
    public volatile long       attackTimerMs;

    public volatile int        hp;
    public volatile EnemyState state;

    /** The ID of the player this enemy is currently targeting (null = no target). */
    public volatile String     targetPlayerId;

    public EnemyData(String id, int x, int y, int maxHp) {
        this.id               = id;
        this.spawnX           = x;
        this.spawnY           = y;
        this.x                = x;
        this.y                = y;
        this.targetX          = x;
        this.targetY          = y;
        this.stateTimer       = 0L;   // WANDER: pick first target immediately on tick 1
        this.maxHp            = maxHp;
        this.hp               = maxHp;
        this.state            = EnemyState.WANDER;  // active from first tick
        this.attackCooldownMs = 2_000L;
        this.attackTimerMs    = 0L;   // ready to attack on first contact
    }
}
