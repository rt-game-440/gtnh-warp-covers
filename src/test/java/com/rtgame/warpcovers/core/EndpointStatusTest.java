package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 状态栏状态：输入端取最近周期结果，输出端取最近被访问结果，过期为等待中。 */
class EndpointStatusTest {

    private FakeConfig config;
    private ChannelHub hub;
    private Channel channel;

    @BeforeEach
    void setUp() {
        config = new FakeConfig();
        hub = new ChannelHub(config);
        channel = hub.createChannel(ChannelType.FLUID, "状态频道");
    }

    private FakeMachine attach(String id, FakeMachine machine, InteractionState state, String... whitelist) {
        EndpointConfig cfg = new EndpointConfig();
        cfg.setState(state);
        for (String key : whitelist) {
            cfg.filter()
                .add(key);
        }
        if (whitelist.length == 0) {
            cfg.filter()
                .setWhitelist(false);
        }
        hub.connect(channel.id(), id, machine.location, "tester", cfg, machine, null);
        return machine;
    }

    private EndpointStatus status(String id, long now) {
        return hub.statusOf(id, now);
    }

    @Test
    void 未连接与频道删除后为未连接() {
        assertEquals(EndpointStatus.UNCONNECTED, status("nope", 0));
        attach("in", new FakeMachine("A", 1000), InteractionState.INPUT);
        hub.deleteChannel(channel.id());
        assertEquals(EndpointStatus.UNCONNECTED, status("in", 0));
    }

    @Test
    void 交互状态为空时为空闲() {
        attach("in", new FakeMachine("A", 1000), InteractionState.EMPTY);
        assertEquals(EndpointStatus.IDLE, status("in", 0));
    }

    @Test
    void 类型被禁用时为服务器已禁用() {
        attach("in", new FakeMachine("A", 1000), InteractionState.INPUT);
        config.fluidEnabled = false;
        assertEquals(EndpointStatus.DISABLED, status("in", 0));
    }

    @Test
    void 没有对端() {
        attach("in", new FakeMachine("A", 1000).with("water", 100), InteractionState.INPUT);
        attach("out", new FakeMachine("B", 1000), InteractionState.EMPTY);

        assertEquals(EndpointStatus.NO_PEER, status("in", 0));
        hub.driveInput("in", 0);
        assertEquals(EndpointStatus.NO_PEER, status("in", 0));

        hub.findEndpoint("out")
            .config()
            .setState(InteractionState.OUTPUT);
        hub.findEndpoint("in")
            .config()
            .setState(InteractionState.EMPTY);
        assertEquals(EndpointStatus.NO_PEER, status("out", 0), "输出端也要有输入端才算有对端");
    }

    @Test
    void 对端未加载() {
        attach("in", new FakeMachine("A", 1000).with("water", 100), InteractionState.INPUT);
        attach("out", new FakeMachine("B", 1000), InteractionState.OUTPUT);
        hub.unbind("out", null);

        hub.driveInput("in", 0);
        assertEquals(EndpointStatus.PEER_UNLOADED, status("in", 0));
    }

    @Test
    void 传输中_两端都显示传输中() {
        attach("in", new FakeMachine("A", 1000).with("water", 100), InteractionState.INPUT);
        attach("out", new FakeMachine("B", 1000), InteractionState.OUTPUT);

        assertEquals(EndpointStatus.WAITING, status("in", 0), "还没跑过周期");
        hub.driveInput("in", 0);

        assertEquals(EndpointStatus.TRANSFERRING, status("in", 5));
        assertEquals(EndpointStatus.TRANSFERRING, status("out", 5));
    }

    @Test
    void 对端已满_输出端显示自身已满() {
        attach("in", new FakeMachine("A", 1000).with("water", 100), InteractionState.INPUT);
        attach("out", new FakeMachine("B", 10).with("lava", 10), InteractionState.OUTPUT);

        hub.driveInput("in", 0);

        assertEquals(EndpointStatus.PEER_FULL, status("in", 0));
        assertEquals(EndpointStatus.SELF_FULL, status("out", 0));
    }

    @Test
    void 被自身名单过滤() {
        attach("in", new FakeMachine("A", 1000).with("water", 100), InteractionState.INPUT, "lava");
        attach("out", new FakeMachine("B", 1000), InteractionState.OUTPUT);

        hub.driveInput("in", 0);
        assertEquals(EndpointStatus.FILTERED, status("in", 0));
    }

    @Test
    void 被对端名单过滤_两端都显示被过滤() {
        attach("in", new FakeMachine("A", 1000).with("water", 100), InteractionState.INPUT);
        attach("out", new FakeMachine("B", 1000), InteractionState.OUTPUT, "lava");

        hub.driveInput("in", 0);
        assertEquals(EndpointStatus.FILTERED, status("in", 0));
        assertEquals(EndpointStatus.FILTERED, status("out", 0));
    }

    @Test
    void 来源为空() {
        attach("in", new FakeMachine("A", 1000), InteractionState.INPUT);
        attach("out", new FakeMachine("B", 1000), InteractionState.OUTPUT);

        hub.driveInput("in", 0);
        assertEquals(EndpointStatus.SOURCE_EMPTY, status("in", 0));
    }

    @Test
    void 同一时刻输出端只升级不降级() {
        attach("in1", new FakeMachine("A", 1000).with("water", 100), InteractionState.INPUT);
        attach("in2", new FakeMachine("C", 1000).with("lava", 100), InteractionState.INPUT);
        attach("out", new FakeMachine("B", 1000), InteractionState.OUTPUT, "water");

        hub.driveInput("in1", 0);
        hub.driveInput("in2", 0);

        assertEquals(EndpointStatus.TRANSFERRING, status("out", 0));
        assertEquals(EndpointStatus.FILTERED, status("in2", 0));
    }

    @Test
    void 结果过期后为等待中() {
        attach("in", new FakeMachine("A", 1000).with("water", 100), InteractionState.INPUT);
        attach("out", new FakeMachine("B", 1000), InteractionState.OUTPUT);
        hub.driveInput("in", 100);

        long window = 2 * 20 + 20;
        assertEquals(EndpointStatus.TRANSFERRING, status("in", 100 + window));
        assertEquals(EndpointStatus.WAITING, status("in", 100 + window + 1));
        assertEquals(EndpointStatus.WAITING, status("in", 50), "时钟回退也视为过期");
    }
}
