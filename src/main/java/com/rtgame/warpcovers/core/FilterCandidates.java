package com.rtgame.warpcovers.core;

import java.util.Collections;
import java.util.List;

/** 过滤名单快捷配置的候选：本机存储里现有的物体键，已去掉频道名单里已有的。 */
public final class FilterCandidates {

    public static final FilterCandidates NONE = new FilterCandidates(Collections.<String>emptyList());

    private final List<String> stored;

    public FilterCandidates(List<String> stored) {
        this.stored = Collections.unmodifiableList(stored);
    }

    /** 本机存储现有、能取出的物体。 */
    public List<String> stored() {
        return stored;
    }
}
