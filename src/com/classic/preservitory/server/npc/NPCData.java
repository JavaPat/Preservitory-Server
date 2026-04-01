package com.classic.preservitory.server.npc;

import com.classic.preservitory.server.world.MovableEntity;

/**
 * Server-side runtime state for a single NPC instance.
 *
 * <p>Immutable identity fields ({@link #id}, {@link #definitionId}, {@link #spawnX},
 * {@link #spawnY}, {@link #name}, {@link #shopkeeper}, {@link #wanders},
 * {@link #wanderRadiusPx}) are set once at construction.
 *
 * <p>Movement state ({@link #x}, {@link #y}, {@link #targetX}, {@link #targetY},
 * {@link #moveTimer}) is volatile so the tick thread and network thread can read it
 * safely.  All writes are coordinated by {@link com.classic.preservitory.server.world.EntityMovementSystem},
 * which synchronises on the NPC instance before mutating.
 */
public class NPCData implements MovableEntity {

    // -----------------------------------------------------------------------
    //  Identity (immutable)
    // -----------------------------------------------------------------------

    public final String  id;

    /** Numeric ID into {@link com.classic.preservitory.server.definitions.NpcDefinitionManager}. */
    public final int     definitionId;

    /** World-pixel position where this NPC was spawned — used as the wander anchor. */
    public final int     spawnX;
    public final int     spawnY;

    public final String  name;
    public final boolean shopkeeper;

    /** Whether this NPC moves around its spawn point. */
    public final boolean wanders;

    /** Maximum wander distance from spawn in pixels. Zero when {@link #wanders} is false. */
    public final int     wanderRadiusPx;

    // -----------------------------------------------------------------------
    //  Movement state (mutable, volatile)
    // -----------------------------------------------------------------------

    /** Current world-pixel position (double for smooth per-tick interpolation). */
    public volatile double x;
    public volatile double y;

    /** Current movement destination. Equals spawn position until first wander pick. */
    public volatile double targetX;
    public volatile double targetY;

    /**
     * Countdown in milliseconds until the next wander target is picked.
     * Zero or negative means a new target should be chosen on the next tick.
     */
    public volatile long moveTimer;

    // -----------------------------------------------------------------------
    //  Construction
    // -----------------------------------------------------------------------

    public NPCData(String id, int definitionId, int x, int y,
                   String name, boolean shopkeeper,
                   boolean wanders, int wanderRadiusPx) {
        this.id             = id;
        this.definitionId   = definitionId;
        this.spawnX         = x;
        this.spawnY         = y;
        this.name           = name;
        this.shopkeeper     = shopkeeper;
        this.wanders        = wanders;
        this.wanderRadiusPx = wanderRadiusPx;
        this.x              = x;
        this.y              = y;
        this.targetX        = x;
        this.targetY        = y;
        this.moveTimer      = 0L;
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
    /** NPCs are always considered wandering when configured to — no FSM state to check. */
    @Override public boolean isWandering()           { return wanders; }
    @Override public long    getWanderTimer()        { return moveTimer; }
    @Override public void    setWanderTimer(long v)  { moveTimer = v; }
    @Override public String  getEntityId()           { return id; }
}
