package com.rtgame.warpcovers.gt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.CreateFailure;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.EndpointStatus;
import com.rtgame.warpcovers.core.FilterList;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.PanelNotice;
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
        for (Channel channel : panel.visibleChannels(viewer)) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString("id", channel.id());
            tag.setString("n", channel.name());
            tag.setInteger("c", channel.color());
            tag.setString("o", channel.ownerName());
            tag.setBoolean("m", mine.equals(channel.ownerId()));
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
        return root;
    }

    /** 设置页：过滤名单。 */
    public static NBTTagCompound filter(EndpointConfig config) {
        NBTTagCompound root = new NBTTagCompound();
        FilterList filter = config.filter();
        root.setBoolean("wl", filter.isWhitelist());
        NBTTagList keys = new NBTTagList();
        for (String key : filter.keys()) {
            keys.appendTag(new NBTTagString(key));
        }
        root.setTag("keys", keys);
        return root;
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
                        tag.getBoolean("m")));
            }
        }
        String current = root == null ? "" : root.getString("cur");
        return new ChannelsView(rows, current, root == null ? InteractionState.EMPTY : readState(root.getString("st")));
    }

    public static FilterView parseFilter(NBTTagCompound root) {
        List<String> keys = new ArrayList<String>();
        boolean whitelist = true;
        if (root != null) {
            if (root.hasKey("wl")) {
                whitelist = root.getBoolean("wl");
            }
            NBTTagList list = root.getTagList("keys", TAG_STRING);
            for (int i = 0; i < list.tagCount(); i++) {
                keys.add(list.getStringTagAt(i));
            }
        }
        return new FilterView(whitelist, keys);
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

        ChannelRow(String id, String name, int color, String ownerName, boolean mine) {
            this.id = id;
            this.name = name;
            this.color = color;
            this.ownerName = ownerName;
            this.mine = mine;
        }
    }

    public static final class ChannelsView {

        public final List<ChannelRow> rows;
        public final String currentId;
        public final InteractionState state;

        ChannelsView(List<ChannelRow> rows, String currentId, InteractionState state) {
            this.rows = Collections.unmodifiableList(rows);
            this.currentId = currentId;
            this.state = state;
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

    public static final class FilterView {

        public final boolean whitelist;
        public final List<String> keys;

        FilterView(boolean whitelist, List<String> keys) {
            this.whitelist = whitelist;
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
