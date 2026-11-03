package com.rtgame.warpcovers.core;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** 过滤名单：黑名单或白名单。新建的名单是空白名单，也就是什么都不传。 */
public final class FilterList {

    private boolean whitelist = true;
    private final Set<String> keys = new LinkedHashSet<String>();

    public boolean isWhitelist() {
        return whitelist;
    }

    public void setWhitelist(boolean whitelist) {
        this.whitelist = whitelist;
    }

    public void add(String key) {
        keys.add(key);
    }

    public void remove(String key) {
        keys.remove(key);
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

    /** 该物体是否通过名单。白名单只放行名单内的；黑名单放行名单外的。 */
    public boolean allows(String key) {
        return whitelist == keys.contains(key);
    }
}
