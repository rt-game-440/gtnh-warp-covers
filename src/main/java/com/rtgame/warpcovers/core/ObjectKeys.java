package com.rtgame.warpcovers.core;

/**
 * 物体键的通用约定。物品键形如 {@code 注册名@损伤值}，带 NBT 时再接 {@code #NBT 编码}；
 * 流体键是流体注册名，不含 {@code #}。核心只需要知道 NBT 部分从哪里开始。
 */
public final class ObjectKeys {

    /** NBT 部分的分隔符。 */
    public static final char NBT_SEPARATOR = '#';

    private ObjectKeys() {}

    /** 去掉 NBT 部分后的键，即只看物品 ID 和损伤值（流体键原样返回）。 */
    public static String withoutNbt(String key) {
        int at = key.indexOf(NBT_SEPARATOR);
        return at < 0 ? key : key.substring(0, at);
    }

    /** 键是否带 NBT。 */
    public static boolean hasNbt(String key) {
        return key.indexOf(NBT_SEPARATOR) >= 0;
    }
}
