package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 频道配置面板的服务端逻辑：改名、备注、颜色、分发方式、权限、删除，每项都先查操作者的权限。
 * 与覆盖板无关，手持终端也用它。结果以 {@link PanelNotice} 返回，界面据此显示反馈。
 */
public final class ChannelAdmin {

    public static final int MAX_NOTE_LENGTH = 64;

    private final ChannelHub hub;

    public ChannelAdmin(ChannelHub hub) {
        this.hub = hub;
    }

    /** 频道的全部连接端，按连接顺序；操作者看不到这个频道时为空。 */
    public List<Endpoint> endpointsOf(Viewer viewer, String channelId) {
        Channel channel = hub.findChannel(channelId);
        if (channel == null || !hub.hasPermission(viewer.playerId(), channel, Permission.VIEW)) {
            return Collections.emptyList();
        }
        List<Endpoint> rows = new ArrayList<Endpoint>(channel.endpoints());
        Collections.sort(rows, new Comparator<Endpoint>() {

            @Override
            public int compare(Endpoint a, Endpoint b) {
                return Long.compare(a.connectOrder(), b.connectOrder());
            }
        });
        return rows;
    }

    public PanelNotice rename(Viewer viewer, String channelId, String rawName) {
        Channel channel = hub.findChannel(channelId);
        PanelNotice denied = deny(viewer, channel, Permission.CONFIGURE);
        if (denied != null) {
            return denied;
        }
        String name = rawName == null ? "" : rawName.trim();
        CreateFailure failure = hub.renameChannel(channelId, name);
        return failure != null ? PanelNotice.error(failure.key(), name) : PanelNotice.info("renamed", channel.name());
    }

    public PanelNotice setNote(Viewer viewer, String channelId, String rawNote) {
        Channel channel = hub.findChannel(channelId);
        PanelNotice denied = deny(viewer, channel, Permission.CONFIGURE);
        if (denied != null) {
            return denied;
        }
        String note = rawNote == null ? "" : rawNote.trim();
        if (note.length() > MAX_NOTE_LENGTH) {
            return PanelNotice.error("note_too_long", "");
        }
        hub.setNote(channelId, note);
        return saved(channel);
    }

    public PanelNotice setColor(Viewer viewer, String channelId, int color) {
        Channel channel = hub.findChannel(channelId);
        PanelNotice denied = deny(viewer, channel, Permission.CONFIGURE);
        if (denied != null) {
            return denied;
        }
        hub.setColor(channelId, color);
        return saved(channel);
    }

    public PanelNotice setDistribution(Viewer viewer, String channelId, DistributionMode mode) {
        Channel channel = hub.findChannel(channelId);
        PanelNotice denied = deny(viewer, channel, Permission.CONFIGURE);
        if (denied != null) {
            return denied;
        }
        hub.setDistribution(channelId, mode);
        return saved(channel);
    }

    /** 编辑权限矩阵的一格。失权的连接端立即断开。管理者的「修改权限」不能取消，否则频道再也没人能改。 */
    public PanelNotice setPermission(Viewer viewer, String channelId, Identity who, Permission permission,
        boolean allowed) {
        Channel channel = hub.findChannel(channelId);
        PanelNotice denied = deny(viewer, channel, Permission.MANAGE_PERMISSIONS);
        if (denied != null) {
            return denied;
        }
        if (who == Identity.MANAGER && permission == Permission.MANAGE_PERMISSIONS && !allowed) {
            return PanelNotice.error("manager_lockout", "");
        }
        hub.setPermission(channelId, who, permission, allowed);
        return saved(channel);
    }

    /** 切换陌生人的私人/公开预设。失权的连接端立即断开。 */
    public PanelNotice setPublic(Viewer viewer, String channelId, boolean isPublic) {
        Channel channel = hub.findChannel(channelId);
        PanelNotice denied = deny(viewer, channel, Permission.MANAGE_PERMISSIONS);
        if (denied != null) {
            return denied;
        }
        hub.setPublic(channelId, isPublic);
        return saved(channel);
    }

    /** 编辑该频道归属者的「新增频道」：只有该归属者的管理者可以改。 */
    public PanelNotice setCreateAllowed(Viewer viewer, String channelId, Identity who, boolean allowed) {
        Channel channel = hub.findChannel(channelId);
        if (channel == null) {
            return PanelNotice.error("channel_missing", "");
        }
        if (hub.identityOf(viewer.playerId(), channel) != Identity.MANAGER) {
            return PanelNotice.error("no_manage_permission", channel.name());
        }
        hub.setCreateAllowed(channel.ownerId(), who, allowed);
        return saved(channel);
    }

    /** 删除频道：所有连接端恢复为未连接，未加载的下次加载时同步。 */
    public PanelNotice delete(Viewer viewer, String channelId) {
        Channel channel = hub.findChannel(channelId);
        PanelNotice denied = deny(viewer, channel, Permission.DELETE);
        if (denied != null) {
            return denied;
        }
        String name = channel.name();
        hub.deleteChannel(channelId);
        return PanelNotice.info("deleted", name);
    }

    private PanelNotice deny(Viewer viewer, Channel channel, Permission needed) {
        if (channel == null) {
            return PanelNotice.error("channel_missing", "");
        }
        if (hub.hasPermission(viewer.playerId(), channel, needed)) {
            return null;
        }
        switch (needed) {
            case CONFIGURE:
                return PanelNotice.error("no_configure_permission", channel.name());
            case DELETE:
                return PanelNotice.error("no_delete_permission", channel.name());
            default:
                return PanelNotice.error("no_manage_permission", channel.name());
        }
    }

    private static PanelNotice saved(Channel channel) {
        return PanelNotice.info("saved", channel.name());
    }
}
