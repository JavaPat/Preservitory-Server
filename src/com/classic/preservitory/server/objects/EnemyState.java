package com.classic.preservitory.server.objects;

/**
 * The finite states an enemy can occupy.
 *
 * <pre>
 *   DEAD ──(respawn)──► IDLE ──(timer)──► WANDER ──(player near)──► AGGRO
 *    ▲                                        ▲                         │
 *    │                                        └────(lost player)────────┤
 *    │                                                                  ▼
 *    └──────────────────────────(hp = 0)────────────────────────── ATTACK
 *                                                                       │
 *                                              AGGRO ◄──(left melee)───┘
 * </pre>
 */
public enum EnemyState {
    /** Briefly standing still at spawn point after respawning. */
    IDLE,

    /** Moving to random tiles near the spawn point. */
    WANDER,

    /** Chasing a player who entered aggro range. */
    AGGRO,

    /** Attacking a player within melee range. */
    ATTACK,

    /** Dead — waiting for the respawn timer to expire. */
    DEAD
}
