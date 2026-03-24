package com.classic.preservitory.server.player;

import com.classic.preservitory.server.net.ClientHandler;
import com.classic.preservitory.server.player.skills.Skill;
import com.classic.preservitory.server.player.skills.SkillSet;
import com.classic.preservitory.server.world.RegionKey;
import com.classic.preservitory.server.world.TreeManager;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Set;

public class PlayerSession {

    public static final int PLAYER_SPAWN_X = 12 * TreeManager.TILE_SIZE;
    public static final int PLAYER_SPAWN_Y = 9 * TreeManager.TILE_SIZE;

    public int getMaxHp() {
        return skills.getLevel(Skill.HITPOINTS) * 5;
    }

    public final SkillSet skills = new SkillSet();
    public final String id;
    public final ClientHandler handler;
    public String username = null;
    public boolean loggedIn = false;
    public final PlayerInventory inventory = new PlayerInventory();
    public final EnumMap<ActionType, Long> lastActionAtMs = new EnumMap<>(ActionType.class);

    public volatile int x = PLAYER_SPAWN_X, y = PLAYER_SPAWN_Y;
    public volatile int hp;
    public volatile long lastMoveAtMs = System.currentTimeMillis();
    public volatile long protectedUntilMs = 0L;
    public RegionKey currentRegion = new RegionKey(0, 0);
    public final Set<RegionKey> loadedRegions = new HashSet<>();
    public CombatStyle combatStyle = CombatStyle.ACCURATE;

    public PlayerSession(String id, ClientHandler handler) {
        this.id = id;
        this.handler = handler;
        this.hp = getMaxHp();
        this.currentRegion = TreeManager.getRegionForPosition(x, y);
    }

    public boolean canSeeRegion(RegionKey region) {
        synchronized (this) {
            return loadedRegions.contains(region);
        }
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public boolean isSpawnProtected() {
        return System.currentTimeMillis() < protectedUntilMs;
    }

    public void clampHp() {
        int max = getMaxHp();
        if (hp > max) hp = max;
        if (hp < 0) hp = 0;
    }
}
