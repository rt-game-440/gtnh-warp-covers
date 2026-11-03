package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 聚合覆盖板在核心里就是一台机器上的三个连接端：各连一个频道，互不影响。 */
class AggregateEndpointsTest {

    private ChannelHub hub;

    @BeforeEach
    void setUp() {
        hub = new ChannelHub(new FakeConfig());
    }

    private String connect(Channel channel, String id, FakeMachine machine, InteractionState state, String key) {
        EndpointConfig config = new EndpointConfig();
        config.setState(state);
        if (key != null) {
            config.filter()
                .add(key);
        }
        hub.connect(channel.id(), id, machine.location, "", config, machine, null);
        return id;
    }

    @Test
    void 三类频道在同一台机器上同时传输且配置各自独立() {
        Channel fluid = hub.createChannel(ChannelType.FLUID, "流体");
        Channel item = hub.createChannel(ChannelType.ITEM, "物品");
        Channel energy = hub.createChannel(ChannelType.ENERGY, "能量");
        FakeMachine source = new FakeMachine("S", 10000).with("water", 100L)
            .with("stick", 10L)
            .with("eu", 500L);
        FakeMachine fluidSink = new FakeMachine("F", 10000);
        FakeMachine itemSink = new FakeMachine("I", 10000);
        FakeMachine energySink = new FakeMachine("E", 10000);
        connect(fluid, "s-f", source, InteractionState.INPUT, "water");
        connect(item, "s-i", source, InteractionState.INPUT, "stick");
        connect(energy, "s-e", source, InteractionState.INPUT, null);
        connect(fluid, "d-f", fluidSink, InteractionState.OUTPUT, "water");
        connect(item, "d-i", itemSink, InteractionState.OUTPUT, "stick");
        connect(energy, "d-e", energySink, InteractionState.OUTPUT, null);

        hub.driveInput("s-f", 100);
        hub.driveInput("s-i", 100);
        hub.driveInput("s-e", 100);

        assertEquals(100, fluidSink.amountOf("water"));
        assertEquals(10, itemSink.amountOf("stick"));
        assertEquals(500, energySink.amountOf("eu"));
        assertEquals(0, fluidSink.amountOf("stick"), "各频道只传自己的物体");
        assertEquals(0, itemSink.amountOf("water"));
        assertNotEquals(
            hub.findEndpoint("s-f")
                .channelId(),
            hub.findEndpoint("s-i")
                .channelId());
    }

    /** 能量部分贴在导线上：连接端报告停用。 */
    private static final class BlockedEndpoint implements PanelEndpoint {

        final FakePanelEndpoint delegate;
        final EndpointStatus blocked;

        BlockedEndpoint(FakePanelEndpoint delegate, EndpointStatus blocked) {
            this.delegate = delegate;
            this.blocked = blocked;
        }

        @Override
        public String endpointId() {
            return delegate.endpointId();
        }

        @Override
        public String channelId() {
            return delegate.channelId();
        }

        @Override
        public EndpointConfig config() {
            return delegate.config();
        }

        @Override
        public DisconnectReason lastReason() {
            return delegate.lastReason();
        }

        @Override
        public void connect(String channelId, String connector) {
            delegate.connect(channelId, connector);
        }

        @Override
        public void disconnect() {
            delegate.disconnect();
        }

        @Override
        public void configChanged() {
            delegate.configChanged();
        }

        @Override
        public EndpointStatus blockedStatus() {
            return blocked;
        }
    }

    @Test
    void 停用的连接端状态栏说明原因且不论是否已连接() {
        Channel energy = hub.createChannel(ChannelType.ENERGY, "能量");
        FakePanelEndpoint fake = new FakePanelEndpoint(hub, "cable", new FakeMachine("C", 0));
        CoverPanel blocked = new CoverPanel(
            hub,
            ChannelType.ENERGY,
            new BlockedEndpoint(fake, EndpointStatus.NO_ENERGY_STORAGE),
            new Random(1));

        assertEquals(EndpointStatus.NO_ENERGY_STORAGE, blocked.status(0));
        fake.connect(energy.id(), "");
        assertEquals(EndpointStatus.NO_ENERGY_STORAGE, blocked.status(0));
    }

    @Test
    void 普通连接端不受影响() {
        FakePanelEndpoint fake = new FakePanelEndpoint(hub, "ep", new FakeMachine("M", 10));
        CoverPanel panel = new CoverPanel(hub, ChannelType.FLUID, fake, new Random(1));

        assertEquals(EndpointStatus.UNCONNECTED, panel.status(0));
    }
}
