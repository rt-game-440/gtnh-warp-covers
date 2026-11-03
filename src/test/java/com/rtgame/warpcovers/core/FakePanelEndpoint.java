package com.rtgame.warpcovers.core;

/** 假覆盖板连接端：直接操作核心，记录配置变更次数。 */
final class FakePanelEndpoint implements PanelEndpoint {

    private final ChannelHub hub;
    final FakeMachine machine;
    final String endpointId;
    final EndpointConfig config = new EndpointConfig();
    String channelId = "";
    DisconnectReason lastReason;
    String lastConnector;
    int configChanges = 0;

    FakePanelEndpoint(ChannelHub hub, String endpointId, FakeMachine machine) {
        this.hub = hub;
        this.endpointId = endpointId;
        this.machine = machine;
    }

    @Override
    public String endpointId() {
        return endpointId;
    }

    @Override
    public String channelId() {
        return channelId;
    }

    @Override
    public EndpointConfig config() {
        return config;
    }

    @Override
    public DisconnectReason lastReason() {
        return lastReason;
    }

    @Override
    public void connect(String newChannelId, String connector) {
        config.setState(InteractionState.EMPTY);
        hub.connect(newChannelId, endpointId, machine.location, connector, config, machine, null);
        channelId = newChannelId;
        lastConnector = connector;
        lastReason = null;
    }

    @Override
    public void disconnect() {
        hub.disconnect(endpointId);
        channelId = "";
        config.setState(InteractionState.EMPTY);
    }

    @Override
    public void configChanged() {
        configChanges++;
        hub.configChanged(endpointId);
    }
}
