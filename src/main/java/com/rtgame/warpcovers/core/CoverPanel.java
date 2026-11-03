package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 覆盖板界面的服务端逻辑：列出频道、新增频道、连接/断开、切换交互状态、编辑过滤名单。
 * 与 GT 和界面框架无关；界面每次打开创建一个，操作反馈保存在 {@link #notice()} 里。
 */
public final class CoverPanel {

    private static final Comparator<Channel> BY_NAME = new Comparator<Channel>() {

        @Override
        public int compare(Channel a, Channel b) {
            int byName = String.CASE_INSENSITIVE_ORDER.compare(a.name(), b.name());
            return byName != 0 ? byName
                : a.id()
                    .compareTo(b.id());
        }
    };

    private final ChannelHub hub;
    private final ChannelType type;
    private final PanelEndpoint endpoint;
    private final Random random;
    private PanelNotice notice;

    public CoverPanel(ChannelHub hub, ChannelType type, PanelEndpoint endpoint, Random random) {
        this.hub = hub;
        this.type = type;
        this.endpoint = endpoint;
        this.random = random;
    }

    public ChannelType type() {
        return type;
    }

    public PanelEndpoint endpoint() {
        return endpoint;
    }

    /** 最近一次操作的反馈；没有时为 null。 */
    public PanelNotice notice() {
        return notice;
    }

    // ---- 频道列表 ----

    /** 该玩家能看到的本类型频道，按名称排序。 */
    public List<Channel> visibleChannels(Viewer viewer) {
        List<Channel> result = new ArrayList<Channel>();
        for (Channel channel : hub.channels()) {
            if (channel.type() == type) {
                result.add(channel);
            }
        }
        Collections.sort(result, BY_NAME);
        return result;
    }

    /** 新频道归到谁名下。 */
    public String ownerFor(Viewer viewer) {
        return viewer.personalOwnerId();
    }

    /** 当前连接的频道；未连接或频道已不在时为 null。 */
    public Channel connectedChannel() {
        String channelId = endpoint.channelId();
        return channelId.isEmpty() ? null : hub.findChannel(channelId);
    }

    /**
     * 新增频道（16 种染料色随机取一）。名字非法或重名时不创建，返回的反馈说明原因。
     *
     * @param connectAfter 创建后直接把本覆盖板连上去
     */
    public PanelNotice createChannel(Viewer viewer, String name, boolean connectAfter) {
        ChannelCreation creation = hub
            .createChannel(type, name, ownerFor(viewer), viewer.name(), ChannelColors.random(random));
        if (!creation.succeeded()) {
            return report(
                PanelNotice.error(
                    creation.failure()
                        .key(),
                    name == null ? "" : name.trim()));
        }
        Channel channel = creation.channel();
        if (connectAfter) {
            endpoint.connect(channel.id(), viewer.playerId());
            return report(PanelNotice.info("created_connected", channel.name()));
        }
        return report(PanelNotice.info("created", channel.name()));
    }

    /** 点击频道：未连该频道则连上（连接者为该玩家），已连则断开。 */
    public PanelNotice toggleConnection(Viewer viewer, String channelId) {
        Channel current = connectedChannel();
        if (current != null && current.id()
            .equals(channelId)) {
            endpoint.disconnect();
            return report(PanelNotice.info("disconnected", current.name()));
        }
        Channel target = hub.findChannel(channelId);
        if (target == null || target.type() != type) {
            return report(PanelNotice.error("channel_missing", ""));
        }
        endpoint.connect(target.id(), viewer.playerId());
        return report(PanelNotice.info("connected", target.name()));
    }

    // ---- 本端配置 ----

    /** 切换交互状态；未连接时忽略。 */
    public boolean setState(InteractionState state) {
        if (state == null || connectedChannel() == null) {
            return false;
        }
        if (endpoint.config()
            .state() != state) {
            endpoint.config()
                .setState(state);
            endpoint.configChanged();
        }
        return true;
    }

    public void setWhitelist(boolean whitelist) {
        if (!type.hasFilter()) {
            return;
        }
        FilterList filter = endpoint.config()
            .filter();
        if (filter.isWhitelist() != whitelist) {
            filter.setWhitelist(whitelist);
            endpoint.configChanged();
        }
    }

    public void setMatchNbt(boolean matchNbt) {
        if (!type.hasFilter()) {
            return;
        }
        FilterList filter = endpoint.config()
            .filter();
        if (filter.matchNbt() != matchNbt) {
            filter.setMatchNbt(matchNbt);
            endpoint.configChanged();
        }
    }

    /** 往名单里加一种物体；已在名单里时返回 false。 */
    public boolean addFilterKey(String key) {
        if (key == null || key.isEmpty() || !type.hasFilter()) {
            return false;
        }
        boolean added = endpoint.config()
            .filter()
            .add(key);
        if (added) {
            endpoint.configChanged();
        }
        return added;
    }

    public boolean removeFilterKey(String key) {
        boolean removed = endpoint.config()
            .filter()
            .remove(key);
        if (removed) {
            endpoint.configChanged();
        }
        return removed;
    }

    public void clearFilter() {
        if (!type.hasFilter()) {
            return;
        }
        FilterList filter = endpoint.config()
            .filter();
        if (!filter.keys()
            .isEmpty()) {
            filter.clear();
            endpoint.configChanged();
        }
    }

    // ---- 状态栏 ----

    public EndpointStatus status(long now) {
        if (endpoint.channelId()
            .isEmpty()) {
            return EndpointStatus.UNCONNECTED;
        }
        return hub.statusOf(endpoint.endpointId(), now);
    }

    private PanelNotice report(PanelNotice result) {
        notice = result;
        return result;
    }
}
