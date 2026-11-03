package com.rtgame.warpcovers.core;

/** 一份传输物体：不透明的物体键加数量。流体的物体键是流体注册名（只按种类匹配）。 */
public final class ObjectStack {

    private final String key;
    private final long amount;

    public ObjectStack(String key, long amount) {
        this.key = key;
        this.amount = amount;
    }

    public String key() {
        return key;
    }

    public long amount() {
        return amount;
    }

    @Override
    public String toString() {
        return key + "×" + amount;
    }
}
