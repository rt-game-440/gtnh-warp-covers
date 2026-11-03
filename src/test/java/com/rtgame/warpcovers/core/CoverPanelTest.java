package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CoverPanelTest {

    private static final Viewer ALICE = new Viewer("uuid-alice", "Alice");
    private static final Viewer BOB = new Viewer("uuid-bob", "Bob");

    private ChannelHub hub;
    private FakePanelEndpoint endpoint;
    private CoverPanel panel;

    @Test
    void 面板修改优先级速率周期并报告实际值() {
        FakeConfig config = new FakeConfig();
        config.rateCap = 500;
        config.minPeriod = 10;
        ChannelHub local = new ChannelHub(config);
        FakePanelEndpoint target = new FakePanelEndpoint(local, "ep-9", new FakeMachine("M2", 1000));
        CoverPanel model = new CoverPanel(local, ChannelType.FLUID, target, new java.util.Random(1));

        assertEquals(20, model.effectivePeriod(), "未设置时取默认周期");
        assertEquals(500, model.effectiveRate(), "未设置时取全局上限");

        model.setPriority(-3);
        model.setRate(1000);
        model.setPeriod(5);
        assertEquals(
            -3,
            target.config()
                .priority());
        assertEquals(
            1000,
            target.config()
                .rate());
        assertEquals(
            5,
            target.config()
                .period());
        assertEquals(500, model.effectiveRate(), "全局上限更小");
        assertEquals(10, model.effectivePeriod(), "不低于最短周期");

        model.setRate(-1);
        model.setPeriod(-1);
        assertEquals(
            0,
            target.config()
                .rate(),
            "负数当作不限");
        assertEquals(
            0,
            target.config()
                .period(),
            "负数当作跟随默认");
    }

    @BeforeEach
    void setUp() {
        hub = new ChannelHub(new FakeConfig());
        endpoint = new FakePanelEndpoint(hub, "ep-1", new FakeMachine("M", 1000));
        panel = new CoverPanel(hub, ChannelType.FLUID, endpoint, new Random(42));
    }

    private Channel create(Viewer viewer, String name) {
        PanelNotice notice = panel.createChannel(viewer, name, false);
        assertFalse(notice.isError(), "创建失败: " + notice);
        return byName(name.trim());
    }

    private Channel byName(String name) {
        for (Channel channel : hub.channels()) {
            if (channel.name()
                .equals(name)) {
                return channel;
            }
        }
        throw new AssertionError("没有频道 " + name);
    }

    private static List<String> names(List<Channel> channels) {
        List<String> result = new ArrayList<String>();
        for (Channel channel : channels) {
            result.add(channel.name());
        }
        return result;
    }

    // ---- 新增频道 ----

    @Test
    void 新频道归创建者本人并随机取染料色() {
        Channel channel = create(ALICE, "主网");

        assertEquals(ALICE.personalOwnerId(), channel.ownerId());
        assertEquals("Alice", channel.ownerName());
        assertTrue(ChannelColors.isDye(channel.color()), "颜色应为 16 种染料色之一");
        assertEquals(ChannelType.FLUID, channel.type());
        assertEquals(new PanelNotice("created", "主网", false), panel.notice());
        assertEquals("", endpoint.channelId, "没勾选时不连接");
    }

    @Test
    void 同归属者同类型重名被拒绝() {
        create(ALICE, "主网");

        PanelNotice notice = panel.createChannel(ALICE, "  主网 ", false);

        assertTrue(notice.isError());
        assertEquals("duplicate_name", notice.key());
        assertEquals(
            1,
            hub.channels()
                .size());
    }

    @Test
    void 不同归属者或不同类型可以同名() {
        create(ALICE, "主网");
        create(BOB, "主网");
        hub.createChannel(ChannelType.ITEM, "主网", ALICE.personalOwnerId(), "Alice", 0);

        assertEquals(
            3,
            hub.channels()
                .size());
    }

    @Test
    void 名字去首尾空白_空名和超长被拒绝() {
        assertEquals(
            "empty_name",
            panel.createChannel(ALICE, "   ", false)
                .key());
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i <= ChannelHub.MAX_NAME_LENGTH; i++) {
            longName.append('长');
        }
        assertEquals(
            "name_too_long",
            panel.createChannel(ALICE, longName.toString(), false)
                .key());
        assertTrue(
            hub.channels()
                .isEmpty());

        create(ALICE, "  空白  ");
        assertEquals("空白", byName("空白").name());
    }

    @Test
    void 勾选创建后直接连接() {
        PanelNotice notice = panel.createChannel(ALICE, "直连", true);

        Channel channel = byName("直连");
        assertEquals("created_connected", notice.key());
        assertEquals(channel.id(), endpoint.channelId);
        assertEquals("uuid-alice", endpoint.lastConnector);
        assertEquals(
            "uuid-alice",
            hub.findEndpoint("ep-1")
                .connector());
        assertEquals(InteractionState.EMPTY, endpoint.config.state());
    }

    // ---- 列表与连接 ----

    @Test
    void 列表只有本类型频道并按名称排序() {
        create(ALICE, "beta");
        create(ALICE, "Alpha");
        hub.setPublic(create(BOB, "gamma").id(), true);
        create(BOB, "私人频道看不到");
        hub.createChannel(ChannelType.ITEM, "物品频道");

        assertEquals(java.util.Arrays.asList("Alpha", "beta", "gamma"), names(panel.visibleChannels(ALICE)));
    }

    @Test
    void 点击连接再点断开() {
        Channel channel = create(ALICE, "主网");
        hub.setPublic(channel.id(), true);

        PanelNotice connected = panel.toggleConnection(BOB, channel.id());
        assertEquals(new PanelNotice("connected", "主网", false), connected);
        assertEquals(channel.id(), endpoint.channelId);
        assertEquals("uuid-bob", endpoint.lastConnector);
        assertSame(channel, panel.connectedChannel());

        PanelNotice disconnected = panel.toggleConnection(BOB, channel.id());
        assertEquals(new PanelNotice("disconnected", "主网", false), disconnected);
        assertEquals("", endpoint.channelId);
        assertNull(hub.findEndpoint("ep-1"));
        assertNull(panel.connectedChannel());
    }

    @Test
    void 已连接时点另一个频道直接切换() {
        Channel first = create(ALICE, "一");
        Channel second = create(ALICE, "二");
        panel.toggleConnection(ALICE, first.id());
        panel.setState(ALICE, InteractionState.INPUT);

        panel.toggleConnection(ALICE, second.id());

        assertEquals(second.id(), endpoint.channelId);
        assertTrue(
            first.endpoints()
                .isEmpty());
        assertEquals(
            1,
            second.endpoints()
                .size());
        assertEquals(InteractionState.EMPTY, endpoint.config.state(), "换频道后交互状态回到空");
    }

    @Test
    void 连接不存在或类型不符的频道报错() {
        Channel item = hub.createChannel(ChannelType.ITEM, "物品");

        assertEquals(
            "channel_missing",
            panel.toggleConnection(ALICE, "nope")
                .key());
        PanelNotice wrongType = panel.toggleConnection(ALICE, item.id());
        assertTrue(wrongType.isError());
        assertEquals("", endpoint.channelId);
    }

    // ---- 本端配置 ----

    @Test
    void 未连接时不能切换交互状态() {
        assertFalse(panel.setState(ALICE, InteractionState.INPUT));
        assertEquals(InteractionState.EMPTY, endpoint.config.state());
        assertEquals(0, endpoint.configChanges);
    }

    @Test
    void 已连接时切换交互状态并标记配置变更() {
        Channel channel = create(ALICE, "主网");
        panel.toggleConnection(ALICE, channel.id());

        assertTrue(panel.setState(ALICE, InteractionState.OUTPUT));
        assertEquals(
            InteractionState.OUTPUT,
            hub.findEndpoint("ep-1")
                .state());
        assertEquals(1, endpoint.configChanges);

        panel.setState(ALICE, InteractionState.OUTPUT);
        assertEquals(1, endpoint.configChanges, "状态没变不重复标记");
    }

    @Test
    void 名单增删清空与黑白切换() {
        assertTrue(panel.addFilterKey("water"));
        assertFalse(panel.addFilterKey("water"), "已在名单的不重复添加");
        assertFalse(panel.addFilterKey(""));
        panel.addFilterKey("lava");
        assertEquals(2, endpoint.configChanges);

        assertTrue(panel.removeFilterKey("water"));
        assertFalse(panel.removeFilterKey("water"));
        assertEquals(
            java.util.Collections.singleton("lava"),
            endpoint.config.filter()
                .keys());

        panel.setWhitelist(false);
        panel.setWhitelist(false);
        assertFalse(
            endpoint.config.filter()
                .isWhitelist());

        panel.clearFilter();
        panel.clearFilter();
        assertTrue(
            endpoint.config.filter()
                .keys()
                .isEmpty());
        assertEquals(5, endpoint.configChanges, "只有真正变化时才标记");
    }

    @Test
    void 能量覆盖板没有名单_改名单的操作一律忽略() {
        FakePanelEndpoint energy = new FakePanelEndpoint(hub, "ep-e", new FakeMachine("E", 1000));
        CoverPanel energyPanel = new CoverPanel(hub, ChannelType.ENERGY, energy, new Random(1));
        assertFalse(energyPanel.addFilterKey("eu"));
        energyPanel.setWhitelist(false);
        energyPanel.setMatchNbt(true);
        energyPanel.clearFilter();
        assertTrue(
            energy.config.filter()
                .keys()
                .isEmpty());
        assertTrue(
            energy.config.filter()
                .isWhitelist());
        assertFalse(
            energy.config.filter()
                .matchNbt());
        assertEquals(0, energy.configChanges);
    }

    @Test
    void 名单不限条数() {
        for (int i = 0; i < 500; i++) {
            panel.addFilterKey("fluid" + i);
        }
        assertEquals(
            500,
            endpoint.config.filter()
                .keys()
                .size());
    }

    @Test
    void 未连接时状态为未连接() {
        assertEquals(EndpointStatus.UNCONNECTED, panel.status(0));
    }
}
