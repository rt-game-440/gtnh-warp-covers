package com.rtgame.warpcovers.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 过滤名单：黑名单或白名单。新建的名单是空白名单，也就是什么都不传。 */
public final class FilterList {

    private boolean whitelist = true;
    /** 物品名单是否连 NBT 一起匹配；流体键不含 NBT，不受此项影响。 */
    private boolean matchNbt = false;
    private final Set<String> keys = new LinkedHashSet<String>();

    public boolean isWhitelist() {
        return whitelist;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    public boolean matchNbt() {
        return matchNbt;
    }

    public void setMatchNbt(boolean matchNbt) {
        this.matchNbt = matchNbt;
    }

    /** @return 原来不在名单里 */
    public boolean add(String key) {
        return keys.add(key);
    }

    /** @return 原来在名单里 */
    public boolean remove(String key) {
        return keys.remove(key);
    }

    public void clear() {
        keys.clear();
    }

    public Set<String> keys() {
        return Collections.unmodifiableSet(keys);
    }

    public void replaceKeys(Collection<String> newKeys) {
        keys.clear();
        keys.addAll(newKeys);
    }

    /**
     * 该物体是否通过名单。白名单只放行名单内的；黑名单放行名单外的。
     * 关闭「匹配 NBT」时只比较物品 ID 和损伤值：名单项和待检物体都去掉 NBT 部分再比。
     */
    public boolean allows(String key) {
        return whitelist == contains(key);
    }

    /** 该物体是否已被名单覆盖：不匹配 NBT 时，只差 NBT 的物体算同一种。 */
    public boolean contains(String key) {
        if (matchNbt) {
            return keys.contains(key);
        }
        String base = ObjectKeys.withoutNbt(key);
        if (keys.contains(base)) {
            return true;
        }
        for (String entry : keys) {
            if (ObjectKeys.hasNbt(entry) && ObjectKeys.withoutNbt(entry)
                .equals(base)) {
                return true;
            }
        }
        return false;
    }

    public FilterList copy() {
        FilterList copy = new FilterList();
        copy.whitelist = whitelist;
        copy.matchNbt = matchNbt;
        copy.keys.addAll(keys);
        return copy;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof FilterList)) {
            return false;
        }
        FilterList other = (FilterList) o;
        return whitelist == other.whitelist && matchNbt == other.matchNbt
            && keys.equals(other.keys)
            && new java.util.ArrayList<String>(keys).equals(new java.util.ArrayList<String>(other.keys));
    }

    @Override
    public int hashCode() {
        return keys.hashCode() * 4 + (whitelist ? 2 : 0) + (matchNbt ? 1 : 0);
    }
}
