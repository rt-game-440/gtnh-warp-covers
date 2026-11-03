package com.rtgame.warpcovers.core;

/** 连接端位置：维度、方块坐标和所在面（0–5，与 ForgeDirection 序号一致）。不可变。 */
public final class Location {

    /** 测试与尚未贴到机器上的场合使用的占位位置。 */
    public static final Location NOWHERE = new Location(0, 0, -1, 0, 0);

    private final int dimension;
    private final int x;
    private final int y;
    private final int z;
    private final int side;

    public Location(int dimension, int x, int y, int z, int side) {
        this.dimension = dimension;
        this.x = x;
        this.y = y;
        this.z = z;
        this.side = side;
    }

    public int dimension() {
        return dimension;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public int side() {
        return side;
    }

    public int chunkX() {
        return x >> 4;
    }

    public int chunkZ() {
        return z >> 4;
    }

    /** 是否同一个方块（不看面）。 */
    public boolean sameBlock(Location other) {
        return other != null && dimension == other.dimension && x == other.x && y == other.y && z == other.z;
    }

    public boolean inChunk(int dim, int cx, int cz) {
        return dimension == dim && chunkX() == cx && chunkZ() == cz;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Location)) {
            return false;
        }
        Location other = (Location) o;
        return dimension == other.dimension && x == other.x && y == other.y && z == other.z && side == other.side;
    }

    @Override
    public int hashCode() {
        return (((dimension * 31 + x) * 31 + y) * 31 + z) * 31 + side;
    }

    @Override
    public String toString() {
        return dimension + ":" + x + "," + y + "," + z + "@" + side;
    }
}
