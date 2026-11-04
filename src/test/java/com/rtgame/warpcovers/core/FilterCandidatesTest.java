package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 快捷配置的候选：只有本机存储里现有的物体，去掉频道名单已覆盖的；加入的是频道的名单。 */
class FilterCandidatesTest {

    private static final Viewer VIEWER = new Viewer("v", "V");

    private ChannelHub hub;
    private Channel channel;
    private FakePanelEndpoint self;
    private CoverPanel panel;

    @BeforeEach
    void setUp() {
        hub = new ChannelHub(new FakeConfig());
        channel = hub.createChannel(ChannelType.ITEM, "频道");
        self = new FakePanelEndpoint(hub, "self", new FakeMachine("self", 1000));
        panel = new CoverPanel(hub, ChannelType.ITEM, self, new Random(1));
    }

    private void connectSelf() {
        self.connect(channel.id(), "");
        self.config.setState(InteractionState.INPUT);
    }

    @Test
    void 本机存储每种一项() {
        connectSelf();
        self.machine.with("water", 100L)
            .with("lava", 5L);

        assertEquals(
            Arrays.asList("water", "lava"),
            panel.filterCandidates()
                .stored());
    }

    @Test
    void 未连接频道时没有候选() {
        self.machine.with("water", 100L);

        assertTrue(
            panel.filterCandidates()
                .stored()
                .isEmpty());
    }

    @Test
    void 已在频道名单的不再作为候选() {
        connectSelf();
        hub.addFilterKey(channel.id(), "a");
        self.machine.with("a", 1L)
            .with("c", 1L);

        assertEquals(
            Collections.singletonList("c"),
            panel.filterCandidates()
                .stored());
    }

    @Test
    void 不匹配NBT时只差NBT的物体算同一种() {
        connectSelf();
        hub.addFilterKey(channel.id(), "stick@0");
        self.machine.with("stick@0#{a:1}", 1L)
            .with("axe@0#{a:1}", 1L)
            .with("axe@0#{a:2}", 1L);

        assertEquals(
            Collections.singletonList("axe@0#{a:1}"),
            panel.filterCandidates()
                .stored());

        hub.setFilterMatchNbt(channel.id(), true);
        assertEquals(
            Arrays.asList("stick@0#{a:1}", "axe@0#{a:1}", "axe@0#{a:2}"),
            panel.filterCandidates()
                .stored(),
            "匹配 NBT 时是三种不同的物体");
    }

    @Test
    void 能量类型没有名单也就没有候选() {
        self.machine.with("eu", 100L);
        Channel energyChannel = hub.createChannel(ChannelType.ENERGY, "电");
        CoverPanel energy = new CoverPanel(hub, ChannelType.ENERGY, self, new Random(1));
        self.connect(energyChannel.id(), "");

        assertTrue(
            energy.filterCandidates()
                .stored()
                .isEmpty());
    }

    @Test
    void 流体类型同样汇总() {
        Channel fluid = hub.createChannel(ChannelType.FLUID, "流体");
        FakePanelEndpoint tank = new FakePanelEndpoint(hub, "tank", new FakeMachine("tank", 1000).with("lava", 5L));
        tank.connect(fluid.id(), "");
        tank.config.setState(InteractionState.OUTPUT);

        FilterCandidates candidates = new CoverPanel(hub, ChannelType.FLUID, tank, new Random(1)).filterCandidates();
        assertEquals(Collections.singletonList("lava"), candidates.stored());
    }

    @Test
    void 候选数量有上限() {
        connectSelf();
        for (int i = 0; i < CoverPanel.MAX_FILTER_CANDIDATES + 5; i++) {
            self.machine.with("k" + i, 1L);
        }

        assertEquals(
            CoverPanel.MAX_FILTER_CANDIDATES,
            panel.filterCandidates()
                .stored()
                .size());
    }

    @Test
    void 一次加入多项时已有的不重复_加入的是频道名单() {
        connectSelf();
        hub.addFilterKey(channel.id(), "a");

        PanelNotice notice = panel.addFilterKeys(VIEWER, Arrays.asList("a", "b", "b", "c"));

        assertEquals(new PanelNotice("filter_added", "2", false), notice);
        assertEquals(
            3,
            channel.filter()
                .keys()
                .size());
        assertEquals(notice, panel.notice());
        assertEquals(0, self.configChanges, "名单不属于覆盖板");
    }

    @Test
    void 不匹配NBT时加入只差NBT的物体不算新增() {
        connectSelf();
        hub.addFilterKey(channel.id(), "axe@0");

        PanelNotice notice = panel.addFilterKeys(VIEWER, Collections.singletonList("axe@0#{a:1}"));

        assertEquals(new PanelNotice("filter_added_none", "", false), notice);
        assertEquals(
            1,
            channel.filter()
                .keys()
                .size());
    }

    @Test
    void 能量类型不接受加入() {
        Channel energyChannel = hub.createChannel(ChannelType.ENERGY, "电");
        CoverPanel energy = new CoverPanel(hub, ChannelType.ENERGY, self, new Random(1));
        self.connect(energyChannel.id(), "");

        assertTrue(
            energy.addFilterKeys(VIEWER, Collections.singletonList("a"))
                .isError());
        assertTrue(
            energyChannel.filter()
                .keys()
                .isEmpty());
    }
}
