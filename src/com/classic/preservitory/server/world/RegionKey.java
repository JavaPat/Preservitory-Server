package com.classic.preservitory.server.world;

import java.util.Objects;

public final class RegionKey {

    public final int regionX;
    public final int regionY;

    public RegionKey(int regionX, int regionY) {
        this.regionX = regionX;
        this.regionY = regionY;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RegionKey)) return false;
        RegionKey k = (RegionKey) o;
        return regionX == k.regionX && regionY == k.regionY;
    }

    @Override
    public int hashCode() {
        return Objects.hash(regionX, regionY);
    }

    @Override
    public String toString() {
        return "R(" + regionX + "," + regionY + ")";
    }
}
