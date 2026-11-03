package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.TreeSet;

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

    /** 每个来源最多列出的候选数，防止物品极多的机器撑大同步数据。 */
    public static final int MAX_FILTER_CANDIDATES = 256;

    private final ChannelHub hub;
    private final ChannelType type;
    private final PanelEndpoint endpoint;
    private final Random random;
    private final ChannelAdmin admin;
    private PanelNotice notice;
    private String search = "";
    private String tagFilter = "";

    public CoverPanel(ChannelHub hub, ChannelType type, PanelEndpoint endpoint, Random random) {
        this.hub = hub;
        this.type = type;
        this.endpoint = endpoint;
        this.random = random;
        this.admin = new ChannelAdmin(hub);
    }

    /** 频道配置面板的操作；结果交给 {@link #report} 显示。 */
    public ChannelAdmin admin() {
        return admin;
    }

    public ChannelHub hub() {
        return hub;
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

    /** 该玩家有「可见」权限的本类型频道，按名称排序。 */
    public List<Channel> visibleChannels(Viewer viewer) {
        List<Channel> result = new ArrayList<Channel>();
        for (Channel channel : hub.channels()) {
            if (channel.type() == type && hub.hasPermission(viewer.playerId(), channel, Permission.VIEW)) {
                result.add(channel);
            }
        }
        Collections.sort(result, BY_NAME);
        return result;
    }

    /** 频道列表：已连接的置顶，其余按分组（自己或本队伍、盟友、公开）、再按名称排序。 */
    public List<ChannelEntry> listing(Viewer viewer) {
        Channel current = connectedChannel();
        List<ChannelEntry> result = new ArrayList<ChannelEntry>();
        for (Channel channel : visibleChannels(viewer)) {
            result.add(
                new ChannelEntry(
                    channel,
                    ChannelGroup.of(hub.identityOf(viewer.playerId(), channel)),
                    channel == current,
                    hub.tags()
                        .of(viewer.playerId(), channel.id())));
        }
        // visibleChannels 已按名称排好，这里是稳定排序
        Collections.sort(result, new Comparator<ChannelEntry>() {

            @Override
            public int compare(ChannelEntry a, ChannelEntry b) {
                if (a.connected != b.connected) {
                    return a.connected ? -1 : 1;
                }
                return a.group.compareTo(b.group);
            }
        });
        return result;
    }

    /** 这一项是否符合当前的搜索词和标签筛选。 */
    public boolean isShown(Viewer viewer, ChannelEntry entry) {
        return entry.matches(search, tagFilter(viewer));
    }

    public void setSearch(String text) {
        search = text == null ? "" : text.trim();
    }

    public void setTagFilter(String tag) {
        tagFilter = tag == null ? "" : tag;
    }

    /** 当前生效的标签筛选；选中的标签已不存在时为空串（不再筛选）。 */
    public String tagFilter(Viewer viewer) {
        return availableTags(viewer).contains(tagFilter) ? tagFilter : "";
    }

    /** 该玩家在可见频道上用过的全部标签，按字典序。 */
    public List<String> availableTags(Viewer viewer) {
        TreeSet<String> all = new TreeSet<String>();
        for (Channel channel : visibleChannels(viewer)) {
            all.addAll(
                hub.tags()
                    .of(viewer.playerId(), channel.id()));
        }
        return new ArrayList<String>(all);
    }

    /** 给可见频道打私人标签：不需要其他权限。 */
    public PanelNotice addTag(Viewer viewer, String channelId, String rawTag) {
        Channel channel = hub.findChannel(channelId);
        if (channel == null || !hub.hasPermission(viewer.playerId(), channel, Permission.VIEW)) {
            return report(PanelNotice.error("channel_missing", ""));
        }
        String tag = rawTag == null ? "" : rawTag.trim();
        String failure = hub.addTag(viewer.playerId(), channelId, tag);
        return report(failure != null ? PanelNotice.error(failure, tag) : PanelNotice.info("tag_added", tag));
    }

    public PanelNotice removeTag(Viewer viewer, String channelId, String tag) {
        hub.removeTag(viewer.playerId(), channelId, tag);
        return report(PanelNotice.info("tag_removed", tag));
    }

    /** 新频道归到谁名下。 */
    public String ownerFor(Viewer viewer) {
        return hub.identity()
            .ownerFor(viewer.playerId());
    }

    /** 该玩家现在不能新增频道的原因；能新增时为 null。 */
    public CreateFailure createBlock(Viewer viewer) {
        return hub.canCreate(viewer.playerId(), type);
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
        String trimmed = name == null ? "" : name.trim();
        CreateFailure blocked = createBlock(viewer);
        if (blocked != null) {
            return report(PanelNotice.error(blocked.key(), trimmed));
        }
        String ownerId = ownerFor(viewer);
        ChannelCreation creation = hub.createChannel(
            type,
            name,
            ownerId,
            hub.identity()
                .ownerName(ownerId, viewer.name()),
            ChannelColors.random(random));
        if (!creation.succeeded()) {
            return report(
                PanelNotice.error(
                    creation.failure()
                        .key(),
                    trimmed));
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
            if (!hub.hasPermission(viewer.playerId(), current, Permission.CONNECT)) {
                return report(PanelNotice.error("no_connect_permission", current.name()));
            }
            endpoint.disconnect();
            return report(PanelNotice.info("disconnected", current.name()));
        }
        Channel target = hub.findChannel(channelId);
        if (target == null || target.type() != type) {
            return report(PanelNotice.error("channel_missing", ""));
        }
        if (!hub.hasPermission(viewer.playerId(), target, Permission.CONNECT)) {
            return report(PanelNotice.error("no_connect_permission", target.name()));
        }
        endpoint.connect(target.id(), viewer.playerId());
        return report(PanelNotice.info("connected", target.name()));
    }

    // ---- 本端配置 ----

    /** 切换交互状态；未连接时忽略，没有「连接」权限时拒绝。 */
    public boolean setState(Viewer viewer, InteractionState state) {
        Channel current = connectedChannel();
        if (state == null || current == null) {
            return false;
        }
        if (!hub.hasPermission(viewer.playerId(), current, Permission.CONNECT)) {
            report(PanelNotice.error("no_connect_permission", current.name()));
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

    /**
     * 快捷配置的候选：对端白名单和本机存储，都去掉名单已覆盖的。对端按本端状态取相反状态的连接端，
     * 本端是「空」或未连接时没有对端来源；对端是黑名单时名单内容是「不要」的物体，不算候选。
     */
    public FilterCandidates filterCandidates() {
        if (!type.hasFilter()) {
            return FilterCandidates.NONE;
        }
        FilterList filter = endpoint.config()
            .filter();
        TreeSet<String> peerKeys = new TreeSet<String>();
        InteractionState wanted = oppositeOf(
            endpoint.config()
                .state());
        String channelId = endpoint.channelId();
        if (wanted != null && !channelId.isEmpty()) {
            for (Endpoint peer : hub.endpoints()) {
                if (peer.channelId()
                    .equals(channelId)
                    && !peer.endpointId()
                        .equals(endpoint.endpointId())
                    && peer.state() == wanted
                    && peer.filter()
                        .isWhitelist()) {
                    peerKeys.addAll(
                        peer.filter()
                            .keys());
                }
            }
        }
        List<String> storedKeys = new ArrayList<String>();
        for (ObjectStack stack : endpoint.storedObjects()) {
            storedKeys.add(stack.key());
        }
        return new FilterCandidates(uncovered(peerKeys, filter), uncovered(storedKeys, filter));
    }

    private static InteractionState oppositeOf(InteractionState state) {
        switch (state) {
            case INPUT:
                return InteractionState.OUTPUT;
            case OUTPUT:
                return InteractionState.INPUT;
            default:
                return null;
        }
    }

    /** 去掉名单已覆盖的和彼此重复的（按名单的匹配方式判断）；超过上限的部分丢弃。 */
    private static List<String> uncovered(Iterable<String> keys, FilterList filter) {
        FilterList seen = new FilterList();
        seen.setMatchNbt(filter.matchNbt());
        List<String> result = new ArrayList<String>();
        for (String key : keys) {
            if (result.size() >= MAX_FILTER_CANDIDATES) {
                break;
            }
            if (!filter.contains(key) && !seen.contains(key)) {
                seen.add(key);
                result.add(key);
            }
        }
        return result;
    }

    /** 一次往名单里加多种物体，已被名单覆盖的跳过。 @return 实际加入的数量 */
    public int addFilterKeys(Collection<String> keys) {
        if (!type.hasFilter()) {
            return 0;
        }
        FilterList filter = endpoint.config()
            .filter();
        int added = 0;
        for (String key : keys) {
            if (key != null && !key.isEmpty() && !filter.contains(key) && filter.add(key)) {
                added++;
            }
        }
        if (added > 0) {
            endpoint.configChanged();
            report(PanelNotice.info("filter_added", Integer.toString(added)));
        } else {
            report(PanelNotice.info("filter_added_none", ""));
        }
        return added;
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

    /** 输出优先级：越大越先收。 */
    public void setPriority(int priority) {
        EndpointConfig config = endpoint.config();
        if (config.priority() != priority) {
            config.setPriority(priority);
            endpoint.configChanged();
        }
    }

    /** 每个运行周期最多搬运多少；0 表示不限（仍受全局上限约束）。负数当作 0。 */
    public void setRate(long rate) {
        EndpointConfig config = endpoint.config();
        long value = Math.max(0, rate);
        if (config.rate() != value) {
            config.setRate(value);
            endpoint.configChanged();
        }
    }

    /** 运行周期（tick）；0 表示跟随服务器默认周期。负数当作 0。 */
    public void setPeriod(int period) {
        EndpointConfig config = endpoint.config();
        int value = Math.max(0, period);
        if (config.period() != value) {
            config.setPeriod(value);
            endpoint.configChanged();
        }
    }

    /** 实际生效的运行周期（tick）。 */
    public long effectivePeriod() {
        return hub.effectivePeriod(endpoint.config());
    }

    /** 实际生效的速率；{@link Long#MAX_VALUE} 表示不限。 */
    public long effectiveRate() {
        return hub.effectiveRate(type, endpoint.config());
    }

    // ---- 状态栏 ----

    public EndpointStatus status(long now) {
        EndpointStatus blocked = endpoint.blockedStatus();
        if (blocked != null) {
            return blocked;
        }
        if (!hub.isEnabled(type)) {
            return EndpointStatus.DISABLED;
        }
        if (endpoint.channelId()
            .isEmpty()) {
            return EndpointStatus.UNCONNECTED;
        }
        return hub.statusOf(endpoint.endpointId(), now);
    }

    /** 记下操作反馈，界面在状态栏显示。 */
    public PanelNotice report(PanelNotice result) {
        notice = result;
        return result;
    }
}
