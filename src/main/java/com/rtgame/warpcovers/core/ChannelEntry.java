package com.rtgame.warpcovers.core;

import java.util.Collection;
import java.util.Locale;
import java.util.SortedSet;

/** 频道列表的一项：某个玩家眼中的频道。 */
public final class ChannelEntry {

    public final Channel channel;
    public final ChannelGroup group;
    public final boolean connected;
    public final int inputs;
    public final int outputs;
    /** 该玩家自己的私人标签。 */
    public final SortedSet<String> tags;

    ChannelEntry(Channel channel, ChannelGroup group, boolean connected, SortedSet<String> tags) {
        this.channel = channel;
        this.group = group;
        this.connected = connected;
        this.tags = tags;
        int in = 0;
        int out = 0;
        for (Endpoint endpoint : channel.endpoints()) {
            if (endpoint.state() == InteractionState.INPUT) {
                in++;
            } else if (endpoint.state() == InteractionState.OUTPUT) {
                out++;
            }
        }
        this.inputs = in;
        this.outputs = out;
    }

    /** 搜索词（频道名或备注包含，不区分大小写，空串不过滤）和标签筛选（空串不过滤）是否都符合。 */
    boolean matches(String query, String tag) {
        return containsTag(tags, tag) && (query.isEmpty() || containsIgnoreCase(channel.name(), query)
            || containsIgnoreCase(channel.note(), query));
    }

    private static boolean containsTag(Collection<String> tags, String tag) {
        return tag.isEmpty() || tags.contains(tag);
    }

    private static boolean containsIgnoreCase(String text, String query) {
        return text.toLowerCase(Locale.ROOT)
            .contains(query.toLowerCase(Locale.ROOT));
    }
}
