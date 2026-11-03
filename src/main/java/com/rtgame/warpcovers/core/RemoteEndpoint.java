package com.rtgame.warpcovers.core;

import java.util.Collections;
import java.util.List;

/**
 * 折跃频道管理器正在远程修改的连接端。只有已加载、且操作者有所在频道「连接」权限的连接端才能改；
 * 改动后操作者成为新的连接者。不能改时 {@link #config()} 返回一份用完即弃的副本，
 * 这样覆盖板界面的各个操作不用逐个判断，直接作用在副本上不产生任何效果。
 */
public final class RemoteEndpoint implements PanelEndpoint {

    private final ChannelHub hub;
    private final Viewer viewer;
    private String selectedId = "";
    private EndpointConfig scratch = new EndpointConfig();

    public RemoteEndpoint(ChannelHub hub, Viewer viewer) {
        this.hub = hub;
        this.viewer = viewer;
    }

    /**
     * 选中要修改的连接端。
     *
     * @return 不能修改时的原因；可以修改时为 null，此时才会选中
     */
    public PanelNotice select(String endpointId) {
        Endpoint endpoint = hub.findEndpoint(endpointId);
        if (endpoint == null) {
            return PanelNotice.error("endpoint_missing", "");
        }
        if (!endpoint.isLoaded()) {
            return PanelNotice.error("endpoint_unloaded", "");
        }
        Channel channel = hub.findChannel(endpoint.channelId());
        if (channel == null || !hub.hasPermission(viewer.playerId(), channel, Permission.CONNECT)) {
            return PanelNotice.error("no_connect_permission", channel == null ? "" : channel.name());
        }
        selectedId = endpointId;
        return null;
    }

    public void clear() {
        selectedId = "";
    }

    /** 当前选中的连接端；没有选中或已被断开时为 null。 */
    public Endpoint selected() {
        return selectedId.isEmpty() ? null : hub.findEndpoint(selectedId);
    }

    /** 选中的连接端现在能否修改：选中之后它可能已被卸载，操作者也可能已经失去权限。 */
    public boolean isEditable() {
        Endpoint endpoint = selected();
        if (endpoint == null || !endpoint.isLoaded()) {
            return false;
        }
        Channel channel = hub.findChannel(endpoint.channelId());
        return channel != null && hub.hasPermission(viewer.playerId(), channel, Permission.CONNECT);
    }

    @Override
    public String endpointId() {
        return selectedId;
    }

    @Override
    public String channelId() {
        Endpoint endpoint = selected();
        return endpoint == null ? "" : endpoint.channelId();
    }

    @Override
    public EndpointConfig config() {
        return isEditable() ? selected().config() : scratch;
    }

    @Override
    public DisconnectReason lastReason() {
        return null;
    }

    /** 管理器没有连接操作。 */
    @Override
    public void connect(String channelId, String connector) {}

    @Override
    public void disconnect() {
        if (isEditable()) {
            hub.disconnect(selectedId, DisconnectReason.REMOTE);
        }
    }

    @Override
    public void configChanged() {
        if (isEditable()) {
            hub.remoteConfigChanged(selectedId, viewer.playerId());
        } else {
            scratch = new EndpointConfig();
        }
    }

    @Override
    public List<ObjectStack> storedObjects() {
        return isEditable() ? selected().port()
            .extractable() : Collections.<ObjectStack>emptyList();
    }
}
