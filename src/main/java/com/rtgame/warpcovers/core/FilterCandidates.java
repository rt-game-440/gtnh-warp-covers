package com.rtgame.warpcovers.core;

import java.util.Collections;
import java.util.List;

/** 过滤名单快捷配置的候选：两个来源各一份物体键，都已去掉名单里已有的。 */
public final class FilterCandidates {

    public static final FilterCandidates NONE = new FilterCandidates(
        Collections.<String>emptyList(),
        Collections.<String>emptyList());

    private final List<String> peers;
    private final List<String> stored;

    public FilterCandidates(List<String> peers, List<String> stored) {
        this.peers = Collections.unmodifiableList(peers);
        this.stored = Collections.unmodifiableList(stored);
    }

    /** 对端白名单：本端是输入端时汇总各输出端的白名单，本端是输出端时汇总各输入端的白名单。 */
    public List<String> peers() {
        return peers;
    }

    /** 本机存储现有、能取出的物体。 */
    public List<String> stored() {
        return stored;
    }
}
