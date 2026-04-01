package com.classic.preservitory.server.world;

/**
 * Minimal contract required by {@link EntityMovementSystem} to move any entity.
 *
 * <p>Both {@link com.classic.preservitory.server.npc.NPCData} and
 * {@link com.classic.preservitory.server.objects.EnemyData} implement this interface,
 * allowing the movement system to be shared without duplicated logic.
 *
 * <p>The interface uses explicit get/set methods so that implementing classes can
 * keep their existing {@code public volatile} fields for direct access by other
 * systems (combat, FSM, snapshot builders) while still satisfying the contract.
 */
public interface MovableEntity {

    // -----------------------------------------------------------------------
    //  Current position
    // -----------------------------------------------------------------------

    double getX();
    void   setX(double v);
    double getY();
    void   setY(double v);

    // -----------------------------------------------------------------------
    //  Movement target
    // -----------------------------------------------------------------------

    double getTargetX();
    void   setTargetX(double v);
    double getTargetY();
    void   setTargetY(double v);

    // -----------------------------------------------------------------------
    //  Spawn anchor (read-only)
    // -----------------------------------------------------------------------

    int getSpawnX();
    int getSpawnY();

    // -----------------------------------------------------------------------
    //  Wander configuration (read-only, set from definition at construction)
    // -----------------------------------------------------------------------

    /** Whether this entity type is configured to wander (definition-level, immutable). */
    boolean isWanderer();

    /** Maximum wander distance from spawn in pixels. Zero when {@link #isWanderer()} is false. */
    int getWanderRadiusPx();

    /**
     * Whether this entity should be moved by the movement system right now.
     *
     * <p>For NPCs this equals {@link #isWanderer()} — NPCs always wander when configured to.
     * For enemies this is dynamic: {@code true} only when the FSM is in WANDER state,
     * so that combat states (AGGRO, ATTACK) suppress wander movement automatically.
     *
     * <p>{@link EntityMovementSystem#update} uses this as its primary gate.
     * An explicit {@code isWandering()} check is used instead of relying on
     * {@link #getWanderRadiusPx()} alone.
     */
    boolean isWandering();

    // -----------------------------------------------------------------------
    //  Wander timer
    //
    //  NPCData maps this to its dedicated `moveTimer` field.
    //  EnemyData maps this to its dedicated `wanderTimer` field — NOT stateTimer,
    //  which is reserved for FSM state durations (idle/dead).
    // -----------------------------------------------------------------------

    long getWanderTimer();
    void setWanderTimer(long v);

    // -----------------------------------------------------------------------
    //  Identity (used by debug logging)
    // -----------------------------------------------------------------------

    /** Stable runtime identifier, e.g. "goblin_1" or "guide". */
    String getEntityId();
}
