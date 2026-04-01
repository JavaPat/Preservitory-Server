package com.classic.preservitory.server.definitions;

/**
 * Compile-time constants for all item IDs.
 *
 * Each constant must match the {@code "id"} field in the corresponding
 * {@code cache/items/*.json} file.  When you add a new item JSON, add its
 * constant here so server code can reference it by name rather than a magic number.
 */
public final class ItemIds {

    private ItemIds() {}

    // -----------------------------------------------------------------------
    //  Currency
    // -----------------------------------------------------------------------
    public static final int COINS          = 1;

    // -----------------------------------------------------------------------
    //  Resources
    // -----------------------------------------------------------------------
    public static final int LOGS           = 2;
    public static final int ORE            = 3;

    // -----------------------------------------------------------------------
    //  Equipment / shop items
    // -----------------------------------------------------------------------
    public static final int BRONZE_AXE     = 100;
    public static final int BRONZE_PICKAXE = 102;
    public static final int LOBSTER        = 103;
    public static final int BRONZE_SWORD   = 104;
    public static final int BRONZE_HELMET  = 105;
    public static final int IRON_SWORD     = 106;
    public static final int IRON_HELMET    = 107;
    public static final int STEEL_SWORD    = 108;
}
