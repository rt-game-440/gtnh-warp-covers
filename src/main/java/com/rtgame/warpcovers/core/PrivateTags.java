package com.rtgame.warpcovers.core;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/** 玩家给频道打的私人标签：只有本人看得到，不需要频道权限，随世界存档保存。 */
public final class PrivateTags {

    public static final int MAX_TAG_LENGTH = 16;
    public static final int MAX_TAGS_PER_CHANNEL = 8;

    /** 玩家 → 频道 → 标签。 */
    private final Map<String, Map<String, TreeSet<String>>> byPlayer = new HashMap<String, Map<String, TreeSet<String>>>();

    /** @return 没有加入的原因（语言键后缀）；成功或已存在时为 null */
    String add(String playerId, String channelId, String tag) {
        if (tag.isEmpty()) {
            return "tag_empty";
        }
        if (tag.length() > MAX_TAG_LENGTH) {
            return "tag_too_long";
        }
        Map<String, TreeSet<String>> channels = byPlayer.get(playerId);
        if (channels == null) {
            channels = new HashMap<String, TreeSet<String>>();
            byPlayer.put(playerId, channels);
        }
        TreeSet<String> tags = channels.get(channelId);
        if (tags == null) {
            tags = new TreeSet<String>();
            channels.put(channelId, tags);
        }
        if (!tags.contains(tag) && tags.size() >= MAX_TAGS_PER_CHANNEL) {
            return "too_many_tags";
        }
        tags.add(tag);
        return null;
    }

    boolean remove(String playerId, String channelId, String tag) {
        Map<String, TreeSet<String>> channels = byPlayer.get(playerId);
        TreeSet<String> tags = channels == null ? null : channels.get(channelId);
        return tags != null && tags.remove(tag);
    }

    public SortedSet<String> of(String playerId, String channelId) {
        Map<String, TreeSet<String>> channels = byPlayer.get(playerId);
        TreeSet<String> tags = channels == null ? null : channels.get(channelId);
        return tags == null ? Collections.unmodifiableSortedSet(new TreeSet<String>())
            : Collections.unmodifiableSortedSet(tags);
    }

    void forgetChannel(String channelId) {
        Iterator<Map<String, TreeSet<String>>> players = byPlayer.values()
            .iterator();
        while (players.hasNext()) {
            Map<String, TreeSet<String>> channels = players.next();
            channels.remove(channelId);
            if (channels.isEmpty()) {
                players.remove();
            }
        }
    }

    /** 持久化用：玩家 → 频道 → 标签。 */
    public Map<String, Map<String, TreeSet<String>>> all() {
        return Collections.unmodifiableMap(byPlayer);
    }

    /** 从存档恢复；不做校验，存档里有什么就恢复什么。 */
    public void restore(String playerId, String channelId, Iterable<String> tags) {
        Map<String, TreeSet<String>> channels = byPlayer.get(playerId);
        if (channels == null) {
            channels = new HashMap<String, TreeSet<String>>();
            byPlayer.put(playerId, channels);
        }
        TreeSet<String> set = new TreeSet<String>();
        for (String tag : tags) {
            set.add(tag);
        }
        if (!set.isEmpty()) {
            channels.put(channelId, set);
        }
    }
}
