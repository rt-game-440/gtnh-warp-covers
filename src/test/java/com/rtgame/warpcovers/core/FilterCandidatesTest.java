package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FilterCandidatesTest {

    private ChannelHub hub;
    private Channel channel;
    private FakePanelEndpoint self;
    private CoverPanel panel;
    private int nextPeer = 0;

    @BeforeEach
    void setUp() {
        hub = new ChannelHub(new FakeConfig());
        channel = hub.createChannel(ChannelType.ITEM, "频道");
        self = new FakePanelEndpoint(hub, "self", new FakeMachine("self", 1000));
        panel = new CoverPanel(hub, ChannelType.ITEM, self, new Random(1));
    }

    private void connectSelf(InteractionState state) {
        self.connect(channel.id(), "");
        self.config.setState(state);
    }

    /** 同频道的另一端：给定交互状态和名单内容。 */
    private EndpointConfig peer(Channel target, InteractionState state, boolean whitelist, String... keys) {
        EndpointConfig config = new EndpointConfig();
        config.setState(state);
        config.filter()
            .setWhitelist(whitelist);
        for (String key : keys) {
            config.filter()
                .add(key);
        }
        FakeMachine machine = new FakeMachine("peer" + nextPeer, 1000);
        hub.connect(target.id(), "peer-" + nextPeer++, machine.location, "", config, machine, null);
        return config;
    }

    @Test
    void 输入端汇总各输出端的白名单() {
        connectSelf(InteractionState.INPUT);
        peer(channel, InteractionState.OUTPUT, true, "b", "a");
        peer(channel, InteractionState.OUTPUT, true, "a", "c");
        peer(channel, InteractionState.INPUT, true, "同为输入不算");

        assertEquals(
            Arrays.asList("a", "b", "c"),
            panel.filterCandidates()
                .peers());
    }

    @Test
    void 输出端汇总各输入端的白名单() {
        connectSelf(InteractionState.OUTPUT);
        peer(channel, InteractionState.INPUT, true, "a");
        peer(channel, InteractionState.OUTPUT, true, "同为输出不算");

        assertEquals(
            Collections.singletonList("a"),
            panel.filterCandidates()
                .peers());
    }

    @Test
    void 未加载的对端也能汇总保存时的白名单() {
        connectSelf(InteractionState.INPUT);
        EndpointConfig saved = new EndpointConfig();
        saved.setState(InteractionState.OUTPUT);
        saved.filter()
            .add("a");
        hub.restoreEndpoint("远端", channel.id(), 99, new FakeMachine("远端", 1).location, "", "", saved);

        assertEquals(
            Collections.singletonList("a"),
            panel.filterCandidates()
                .peers());
    }

    @Test
    void 对端黑名单和其他频道不算候选() {
        connectSelf(InteractionState.INPUT);
        peer(channel, InteractionState.OUTPUT, false, "黑名单项");
        peer(hub.createChannel(ChannelType.ITEM, "别的频道"), InteractionState.OUTPUT, true, "别的频道项");

        assertTrue(
            panel.filterCandidates()
                .peers()
                .isEmpty());
    }

    @Test
    void 空状态或未连接时没有对端来源() {
        assertTrue(
            panel.filterCandidates()
                .peers()
                .isEmpty(),
            "未连接");

        connectSelf(InteractionState.EMPTY);
        peer(channel, InteractionState.OUTPUT, true, "a");
        peer(channel, InteractionState.INPUT, true, "b");
        assertTrue(
            panel.filterCandidates()
                .peers()
                .isEmpty(),
            "空状态");
    }

    @Test
    void 本机存储不要求连接频道且每种一项() {
        self.machine.with("water", 100L)
            .with("lava", 5L);

        assertEquals(
            Arrays.asList("water", "lava"),
            panel.filterCandidates()
                .stored());
    }

    @Test
    void 已在名单的不再作为候选() {
        connectSelf(InteractionState.INPUT);
        self.config.filter()
            .add("a");
        peer(channel, InteractionState.OUTPUT, true, "a", "b");
        self.machine.with("a", 1L)
            .with("c", 1L);

        FilterCandidates candidates = panel.filterCandidates();
        assertEquals(Collections.singletonList("b"), candidates.peers());
        assertEquals(Collections.singletonList("c"), candidates.stored());
    }

    @Test
    void 不匹配NBT时只差NBT的物体算同一种() {
        self.config.filter()
            .setMatchNbt(false);
        self.config.filter()
            .add("stick@0");
        self.machine.with("stick@0#{a:1}", 1L)
            .with("axe@0#{a:1}", 1L)
            .with("axe@0#{a:2}", 1L);

        assertEquals(
            Collections.singletonList("axe@0#{a:1}"),
            panel.filterCandidates()
                .stored());

        self.config.filter()
            .setMatchNbt(true);
        assertEquals(
            Arrays.asList("stick@0#{a:1}", "axe@0#{a:1}", "axe@0#{a:2}"),
            panel.filterCandidates()
                .stored(),
            "匹配 NBT 时是三种不同的物体");
    }

    @Test
    void 能量类型没有名单也就没有候选() {
        self.machine.with("eu", 100L);
        CoverPanel energy = new CoverPanel(hub, ChannelType.ENERGY, self, new Random(1));

        FilterCandidates candidates = energy.filterCandidates();
        assertTrue(
            candidates.peers()
                .isEmpty());
        assertTrue(
            candidates.stored()
                .isEmpty());
    }

    @Test
    void 流体类型同样汇总() {
        Channel fluid = hub.createChannel(ChannelType.FLUID, "流体");
        FakePanelEndpoint tank = new FakePanelEndpoint(hub, "tank", new FakeMachine("tank", 1000).with("lava", 5L));
        tank.connect(fluid.id(), "");
        tank.config.setState(InteractionState.OUTPUT);
        peer(fluid, InteractionState.INPUT, true, "water");

        FilterCandidates candidates = new CoverPanel(hub, ChannelType.FLUID, tank, new Random(1)).filterCandidates();
        assertEquals(Collections.singletonList("water"), candidates.peers());
        assertEquals(Collections.singletonList("lava"), candidates.stored());
    }

    @Test
    void 候选数量有上限() {
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
    void 一次加入多项时已有的不重复且只通知一次() {
        self.config.filter()
            .add("a");

        int added = panel.addFilterKeys(Arrays.asList("a", "b", "b", "c"));

        assertEquals(2, added);
        assertEquals(
            3,
            self.config.filter()
                .keys()
                .size());
        assertEquals(1, self.configChanges);
        assertEquals(new PanelNotice("filter_added", "2", false), panel.notice());
    }

    @Test
    void 不匹配NBT时加入只差NBT的物体不算新增() {
        self.config.filter()
            .add("axe@0");

        assertEquals(0, panel.addFilterKeys(Collections.singletonList("axe@0#{a:1}")));
        assertEquals(0, self.configChanges);
        assertEquals(new PanelNotice("filter_added_none", "", false), panel.notice());
    }

    @Test
    void 能量类型不接受加入() {
        CoverPanel energy = new CoverPanel(hub, ChannelType.ENERGY, self, new Random(1));

        assertEquals(0, energy.addFilterKeys(Collections.singletonList("a")));
        assertTrue(
            self.config.filter()
                .keys()
                .isEmpty());
    }
}
