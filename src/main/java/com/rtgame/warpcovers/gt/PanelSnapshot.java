package com.rtgame.warpcovers.gt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelEntry;
import com.rtgame.warpcovers.core.ChannelGroup;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelPermissions;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.CreateFailure;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.DistributionMode;
import com.rtgame.warpcovers.core.Endpoint;
import com.rtgame.warpcovers.core.EndpointStatus;
import com.rtgame.warpcovers.core.FilterCandidates;
import com.rtgame.warpcovers.core.FilterList;
import com.rtgame.warpcovers.core.Identity;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.Location;
import com.rtgame.warpcovers.core.PanelNotice;
import com.rtgame.warpcovers.core.Permission;
import com.rtgame.warpcovers.core.RemoteEndpoint;
import com.rtgame.warpcovers.core.Viewer;

/**
 * 覆盖板界面的快照：服务端按玩家构建，客户端只读解析。
 * 服务器是唯一权威，客户端改动都以请求发回服务端，服务端处理后下一次快照带回结果。
 */
public final class PanelSnapshot {

    private static final int TAG_COMPOUND = 10;
    private static final int TAG_STRING = 8;

    private PanelSnapshot() {}

    // ---- 服务端构建 ----

    /** 频道页：可见频道（名称、颜色、归属者）、本端当前频道和交互状态。 */
    public static NBTTagCompound channels(CoverPanel panel, Viewer viewer) {
        NBTTagCompound root = new NBTTagCompound();
        String mine = panel.ownerFor(viewer);
        NBTTagList list = new NBTTagList();
        for (ChannelEntry entry : panel.listing(viewer)) {
            Channel channel = entry.channel;
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", channel.id());
            tag.setString("g", entry.group.name());
            tag.setBoolean("hid", !panel.isShown(viewer, entry));
            tag.setInteger("in", entry.inputs);
            tag.setInteger("out", entry.outputs);
            tag.setTag("tags", stringList(entry.tags));
            tag.setString("n", channel.name());
            tag.setInteger("c", channel.color());
            tag.setString("o", channel.ownerName());
            tag.setBoolean("m", mine.equals(channel.ownerId()));
            tag.setString("nt", channel.note());
            if (channel.type()
                .hasFilter()) {
                // 频道页每行只显示名单的概要；完整名单在配置面板打开时单独同步
                tag.setBoolean(
                    "fw",
                    channel.filter()
                        .isWhitelist());
                tag.setInteger(
                    "fc",
                    channel.filter()
                        .keys()
                        .size());
            }
            writeAccess(panel.hub(), viewer, channel, tag);
            list.appendTag(tag);
        }
        root.setTag("list", list);
        Channel current = panel.connectedChannel();
        root.setString("cur", current == null ? "" : current.id());
        root.setString(
            "st",
            panel.endpoint()
                .config()
                .state()
                .name());
        CreateFailure blocked = panel.createBlock(viewer);
        root.setString("cb", blocked == null ? "" : blocked.key());
        root.setTag("allTags", stringList(panel.availableTags(viewer)));
        root.setString("tf", panel.tagFilter(viewer));
        return root;
    }

    /**
     * 各频道最近传输过的种类，单独同步：它随传输频繁变化，放进频道快照会让列表不停重建、滚动位置丢失。
     */
    public static NBTTagCompound recents(CoverPanel panel, Viewer viewer) {
        NBTTagCompound root = new NBTTagCompound();
        for (Channel channel : panel.visibleChannels(viewer)) {
            List<String> keys = panel.hub()
                .recentTransfers()
                .of(channel.id());
            if (!keys.isEmpty()) {
                root.setTag(channel.id(), stringList(keys));
            }
        }
        return root;
    }

    private static NBTTagList stringList(Iterable<String> values) {
        NBTTagList list = new NBTTagList();
        for (String value : values) {
            list.appendTag(new NBTTagString(value));
        }
        return list;
    }

    private static List<String> readStrings(NBTTagCompound root, String key) {
        List<String> result = new ArrayList<String>();
        if (root != null) {
            NBTTagList list = root.getTagList(key, TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                result.add(list.getStringTagAt(i));
            }
        }
        return result;
    }

    /** 某频道最近传输过的种类，最近的在前。 */
    public static List<String> parseRecents(NBTTagCompound root, String channelId) {
        return readStrings(root, channelId);
    }

    /** 频道配置面板要用的内容：操作者有相应权限才下发。 */
    private static void writeAccess(ChannelHub hub, Viewer viewer, Channel channel, NBTTagCompound tag) {
        String player = viewer.playerId();
        boolean configure = hub.hasPermission(player, channel, Permission.CONFIGURE);
        boolean manage = hub.hasPermission(player, channel, Permission.MANAGE_PERMISSIONS);
        boolean delete = hub.hasPermission(player, channel, Permission.DELETE);
        tag.setBoolean("cfg", configure);
        tag.setBoolean("del", delete);
        tag.setString(
            "dist",
            channel.distribution()
                .key());
        if (manage) {
            tag.setIntArray(
                "perm",
                channel.permissions()
                    .toMasks());
        }
        if (hub.identityOf(player, channel) == Identity.MANAGER) {
            tag.setInteger("new", hub.createPolicyMask(channel.ownerId()));
        }
    }

    /**
     * 频道配置面板正在看的频道的过滤名单。频道不存在、没有「可见」权限或没有名单（能量）时是空的默认名单；
     * 完整名单只在配置面板打开时同步，频道页每行只带概要（见 {@link #channels}）。
     */
    public static NBTTagCompound filter(ChannelHub hub, Viewer viewer, String channelId) {
        NBTTagCompound root = new NBTTagCompound();
        root.setString("ch", channelId);
        Channel channel = channelId.isEmpty() ? null : hub.findChannel(channelId);
        FilterList filter = channel != null && channel.type()
            .hasFilter() && hub.hasPermission(viewer.playerId(), channel, Permission.VIEW) ? channel.filter()
                : Channel.defaultFilter();
        root.setBoolean("wl", filter.isWhitelist());
        root.setBoolean("nbt", filter.matchNbt());
        NBTTagList keys = new NBTTagList();
        for (String key : filter.keys()) {
            keys.appendTag(new NBTTagString(key));
        }
        root.setTag("keys", keys);
        return root;
    }

    /** 管理器：一个频道的全部连接端。 */
    public static NBTTagCompound endpoints(CoverPanel panel, Viewer viewer, String channelId) {
        NBTTagCompound root = new NBTTagCompound();
        root.setString("ch", channelId);
        NBTTagList rows = new NBTTagList();
        for (Endpoint endpoint : panel.admin()
            .endpointsOf(viewer, channelId)) {
            Channel channel = panel.hub()
                .findChannel(endpoint.channelId());
            rows.appendTag(
                endpointRow(
                    endpoint,
                    endpoint.isLoaded() && panel.hub()
                        .hasPermission(viewer.playerId(), channel, Permission.CONNECT)));
        }
        root.setTag("rows", rows);
        return root;
    }

    /** 管理器：正在远程修改的连接端；没有选中时为空。 */
    public static NBTTagCompound selected(RemoteEndpoint remote) {
        Endpoint endpoint = remote.selected();
        return endpoint == null ? new NBTTagCompound() : endpointRow(endpoint, remote.isEditable());
    }

    private static NBTTagCompound endpointRow(Endpoint endpoint, boolean editable) {
        NBTTagCompound tag = new NBTTagCompound();
        Location location = endpoint.location();
        tag.setString("id", endpoint.endpointId());
        tag.setInteger("dim", location.dimension());
        tag.setInteger("x", location.x());
        tag.setInteger("y", location.y());
        tag.setInteger("z", location.z());
        tag.setString("mn", endpoint.machineName());
        tag.setString(
            "st",
            endpoint.state()
                .name());
        tag.setBoolean("ld", endpoint.isLoaded());
        tag.setBoolean("ed", editable);
        return tag;
    }

    /** @return 没有选中时为 null */
    public static EndpointRow parseSelected(NBTTagCompound root) {
        return root == null || !root.hasKey("id") ? null : new EndpointRow(root);
    }

    public static List<EndpointRow> parseEndpoints(NBTTagCompound root, String channelId) {
        List<EndpointRow> rows = new ArrayList<EndpointRow>();
        if (root != null && channelId.equals(root.getString("ch"))) {
            NBTTagList list = root.getTagList("rows", TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                rows.add(new EndpointRow(list.getCompoundTagAt(i)));
            }
        }
        return rows;
    }

    /** 快捷配置的候选。 */
    public static NBTTagCompound candidates(FilterCandidates candidates) {
        NBTTagCompound root = new NBTTagCompound();
        root.setTag("stored", stringList(candidates.stored()));
        return root;
    }

    public static FilterCandidates parseCandidates(NBTTagCompound root) {
        return new FilterCandidates(readStrings(root, "stored"));
    }

    /** 状态栏：当前状态、最近断开原因、最近一次操作反馈。 */
    public static NBTTagCompound status(CoverPanel panel, long now) {
        NBTTagCompound root = new NBTTagCompound();
        root.setString(
            "s",
            panel.status(now)
                .key());
        DisconnectReason reason = panel.endpoint()
            .lastReason();
        root.setString("r", reason == null ? "" : reason.key());
        PanelNotice notice = panel.notice();
        if (notice != null) {
            root.setString("n", notice.key());
            root.setString("na", notice.arg());
            root.setBoolean("err", notice.isError());
        }
        return root;
    }

    // ---- 客户端解析 ----

    public static ChannelsView parseChannels(NBTTagCompound root) {
        List<ChannelRow> rows = new ArrayList<ChannelRow>();
        if (root != null) {
            NBTTagList list = root.getTagList("list", TAG_COMPOUND);
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound tag = list.getCompoundTagAt(i);
                rows.add(
                    new ChannelRow(
                        tag.getString("id"),
                        tag.getString("n"),
                        tag.getInteger("c"),
                        tag.getString("o"),
                        tag.getBoolean("m"),
                        tag.getString("nt"),
                        tag.getBoolean("cfg"),
                        tag.getBoolean("del"),
                        DistributionMode.byKey(tag.getString("dist")),
                        tag.hasKey("perm") ? ChannelPermissions.fromMasks(tag.getIntArray("perm")) : null,
                        tag.hasKey("new") ? tag.getInteger("new") : -1,
                        readGroup(tag.getString("g")),
                        !tag.getBoolean("hid"),
                        tag.getInteger("in"),
                        tag.getInteger("out"),
                        readStrings(tag, "tags"),
                        tag.hasKey("fw") || tag.hasKey("fc"),
                        tag.getBoolean("fw"),
                        tag.getInteger("fc")));
            }
        }
        String current = root == null ? "" : root.getString("cur");
        return new ChannelsView(
            rows,
            current,
            root == null ? InteractionState.EMPTY : readState(root.getString("st")),
            root == null ? null : CreateFailure.byKey(root.getString("cb")),
            readStrings(root, "allTags"),
            root == null ? "" : root.getString("tf"));
    }

    /** 解析名单快照；快照不是这个频道的（刚打开配置面板、服务端还没切过来）时按默认的空黑名单处理。 */
    public static FilterView parseFilter(NBTTagCompound root, String channelId) {
        if (root == null || !channelId.equals(root.getString("ch"))) {
            return new FilterView(false, false, new ArrayList<String>());
        }
        return parseFilter(root);
    }

    public static FilterView parseFilter(NBTTagCompound root) {
        List<String> keys = new ArrayList<String>();
        boolean whitelist = false;
        boolean matchNbt = false;
        if (root != null) {
            if (root.hasKey("wl")) {
                whitelist = root.getBoolean("wl");
            }
            matchNbt = root.getBoolean("nbt");
            NBTTagList list = root.getTagList("keys", TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                keys.add(list.getStringTagAt(i));
            }
        }
        return new FilterView(whitelist, matchNbt, keys);
    }

    public static StatusView parseStatus(NBTTagCompound root) {
        if (root == null || !root.hasKey("s")) {
            return new StatusView(EndpointStatus.UNCONNECTED, null, "", "", false);
        }
        EndpointStatus status = EndpointStatus.byKey(root.getString("s"));
        return new StatusView(
            status == null ? EndpointStatus.UNCONNECTED : status,
            DisconnectReason.byKey(root.getString("r")),
            root.getString("n"),
            root.getString("na"),
            root.getBoolean("err"));
    }

    private static ChannelGroup readGroup(String name) {
        try {
            return ChannelGroup.valueOf(name);
        } catch (IllegalArgumentException e) {
            return ChannelGroup.PUBLIC;
        }
    }

    private static InteractionState readState(String name) {
        try {
            return InteractionState.valueOf(name);
        } catch (IllegalArgumentException e) {
            return InteractionState.EMPTY;
        }
    }

    // ---- 视图 ----

    public static final class ChannelRow {

        public final String id;
        public final String name;
        public final int color;
        public final String ownerName;
        public final boolean mine;
        public final String note;
        public final boolean canConfigure;
        public final boolean canDelete;
        public final DistributionMode distribution;
        /** 权限矩阵；操作者没有「修改权限」时为 null。 */
        public final ChannelPermissions permissions;
        /** 归属者级「新增频道」的身份位掩码；操作者不是管理者时为 -1。 */
        public final int createMask;
        public final ChannelGroup group;
        /** 是否符合当前的搜索和标签筛选。不符合的行也下发，配置面板和重名预检要用。 */
        public final boolean shown;
        public final int inputs;
        public final int outputs;
        /** 查看者自己的私人标签。 */
        public final List<String> tags;
        /** 频道是否有过滤名单（能量没有）。 */
        public final boolean hasFilter;
        public final boolean filterWhitelist;
        public final int filterCount;

        ChannelRow(String id, String name, int color, String ownerName, boolean mine, String note, boolean canConfigure,
            boolean canDelete, DistributionMode distribution, ChannelPermissions permissions, int createMask,
            ChannelGroup group, boolean shown, int inputs, int outputs, List<String> tags, boolean hasFilter,
            boolean filterWhitelist, int filterCount) {
            this.hasFilter = hasFilter;
            this.filterWhitelist = filterWhitelist;
            this.filterCount = filterCount;
            this.group = group;
            this.shown = shown;
            this.inputs = inputs;
            this.outputs = outputs;
            this.tags = Collections.unmodifiableList(tags);
            this.note = note;
            this.canConfigure = canConfigure;
            this.canDelete = canDelete;
            this.distribution = distribution;
            this.permissions = permissions;
            this.createMask = createMask;
            this.id = id;
            this.name = name;
            this.color = color;
            this.ownerName = ownerName;
            this.mine = mine;
        }

        public boolean canManage() {
            return permissions != null;
        }

        /** 有任何一项可编辑的内容时显示齿轮；有过滤名单的频道对所有人显示，没有权限的人在里面只读。 */
        public boolean hasConfigPanel() {
            return canConfigure || canDelete || canManage() || hasFilter;
        }

        public boolean canEditCreate() {
            return createMask >= 0;
        }

        public boolean createAllowed(Identity who) {
            return canEditCreate() && (createMask & (1 << who.ordinal())) != 0;
        }
    }

    public static final class ChannelsView {

        public final List<ChannelRow> rows;
        public final String currentId;
        public final InteractionState state;
        /** 当前不能新增频道的原因（没有权限、达到上限）；能新增时为 null。 */
        public final CreateFailure createBlock;
        /** 查看者用过的全部标签，按字典序。 */
        public final List<String> allTags;
        /** 当前的标签筛选；空串表示不筛选。 */
        public final String tagFilter;

        ChannelsView(List<ChannelRow> rows, String currentId, InteractionState state, CreateFailure createBlock,
            List<String> allTags, String tagFilter) {
            this.createBlock = createBlock;
            this.allTags = Collections.unmodifiableList(allTags);
            this.tagFilter = tagFilter;
            this.rows = Collections.unmodifiableList(rows);
            this.currentId = currentId;
            this.state = state;
        }

        /** 标签筛选的下一个选项：全部 → 第一个标签 → … → 最后一个标签 → 全部。 */
        public String nextTagFilter() {
            int index = allTags.indexOf(tagFilter);
            return index + 1 < allTags.size() ? allTags.get(index + 1) : "";
        }

        /** 按 ID 查找行；频道已不可见时为 null。 */
        public ChannelRow row(String channelId) {
            for (ChannelRow row : rows) {
                if (row.id.equals(channelId)) {
                    return row;
                }
            }
            return null;
        }

        public boolean isConnected(String channelId) {
            return !currentId.isEmpty() && currentId.equals(channelId);
        }

        /**
         * 客户端预检新频道名（服务端会再查一次）：空名、超长、和自己名下的同类型频道重名。
         *
         * @return 不通过的原因；通过时为 null
         */
        public CreateFailure precheck(String rawName) {
            String name = rawName == null ? "" : rawName.trim();
            if (name.isEmpty()) {
                return CreateFailure.EMPTY_NAME;
            }
            if (name.length() > ChannelHub.MAX_NAME_LENGTH) {
                return CreateFailure.NAME_TOO_LONG;
            }
            for (ChannelRow row : rows) {
                if (row.mine && row.name.equals(name)) {
                    return CreateFailure.DUPLICATE_NAME;
                }
            }
            return null;
        }
    }

    public static final class EndpointRow {

        public final String id;
        public final int dimension;
        public final int x;
        public final int y;
        public final int z;
        public final String machineName;
        public final InteractionState state;
        public final boolean loaded;
        public final boolean editable;

        EndpointRow(NBTTagCompound tag) {
            id = tag.getString("id");
            dimension = tag.getInteger("dim");
            x = tag.getInteger("x");
            y = tag.getInteger("y");
            z = tag.getInteger("z");
            machineName = tag.getString("mn");
            state = readState(tag.getString("st"));
            loaded = tag.getBoolean("ld");
            editable = tag.getBoolean("ed");
        }
    }

    public static final class FilterView {

        public final boolean whitelist;
        /** 物品名单是否连 NBT 一起匹配。 */
        public final boolean matchNbt;
        public final List<String> keys;

        FilterView(boolean whitelist, boolean matchNbt, List<String> keys) {
            this.whitelist = whitelist;
            this.matchNbt = matchNbt;
            this.keys = Collections.unmodifiableList(keys);
        }
    }

    public static final class StatusView {

        public final EndpointStatus status;
        /** 最近一次被动断开的原因；没有时为 null。 */
        public final DisconnectReason reason;
        /** 最近一次操作反馈的语言键后缀；没有时为空串。 */
        public final String noticeKey;
        public final String noticeArg;
        public final boolean noticeError;

        StatusView(EndpointStatus status, DisconnectReason reason, String noticeKey, String noticeArg,
            boolean noticeError) {
            this.status = status;
            this.reason = reason;
            this.noticeKey = noticeKey;
            this.noticeArg = noticeArg;
            this.noticeError = noticeError;
        }
    }
}
