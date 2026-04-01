package com.classic.preservitory.server.definitions;

import java.util.List;

/**
 * Immutable definition of an enemy type, loaded from {@code cache/enemies/*.json}.
 *
 * Combat stats, HP, and drop table all live here — never hardcoded in EnemyManager
 * or CombatServices.
 */
public final class EnemyDefinition {

    /** Numeric ID — the primary identifier. */
    public final int    id;

    /** String key used in map/code references (e.g. "goblin"). Derived from filename. */
    public final String key;

    public final String name;
    public final int    maxHp;
    public final int    attack;
    public final int    defense;
    public final int    minDamage;
    public final int    maxDamage;
    public final long   attackCooldownMs;

    /** How long (ms) before this enemy respawns after death. */
    public final long   respawnDelayMs;

    public final List<DropEntry> dropTable;

    /** Whether this enemy type wanders around its spawn point. */
    public final boolean wander;

    /** Wander distance in tiles. Zero when {@link #wander} is false. */
    public final int     wanderRadiusTiles;

    public EnemyDefinition(int id, String key, String name,
                           int maxHp, int attack, int defense,
                           int minDamage, int maxDamage, long attackCooldownMs,
                           long respawnDelayMs,
                           List<DropEntry> dropTable,
                           boolean wander, int wanderRadiusTiles) {
        this.id               = id;
        this.key              = key;
        this.name             = name;
        this.maxHp            = maxHp;
        this.attack           = attack;
        this.defense          = defense;
        this.minDamage        = minDamage;
        this.maxDamage        = maxDamage;
        this.attackCooldownMs = attackCooldownMs;
        this.respawnDelayMs   = respawnDelayMs;
        this.dropTable        = List.copyOf(dropTable);
        this.wander           = wander;
        this.wanderRadiusTiles = wanderRadiusTiles;
    }

    /** One entry in a drop table — item, drop chance, and quantity range. */
    public static final class DropEntry {
        public final int    itemId;
        public final double chance;
        public final int    minAmount;
        public final int    maxAmount;

        public DropEntry(int itemId, double chance, int minAmount, int maxAmount) {
            this.itemId    = itemId;
            this.chance    = chance;
            this.minAmount = Math.max(1, minAmount);
            this.maxAmount = Math.max(this.minAmount, maxAmount);
        }
    }
}
