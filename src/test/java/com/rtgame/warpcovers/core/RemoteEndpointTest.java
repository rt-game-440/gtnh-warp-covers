package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RemoteEndpointTest {

    private static final Viewer ALICE = new Viewer("alice", "Alice");
    private static final Viewer BOB = new Viewer("bob", "Bob");

    private static final class RecordingHost implements EndpointHost {

        final List<DisconnectReason> reasons = new ArrayList<DisconnectReason>();
        int changes = 0;

        @Override
        public void onDisconnected(DisconnectReason reason) {
            reasons.add(reason);
        }

        @Override
        public void onEndpointChanged() {
            changes++;
        }
    }

    private ChannelHub hub;
    private Channel channel;
    private FakeMachine machine;
    private EndpointConfig config;
    private RecordingHost host;

    @BeforeEach
    void setUp() {
        hub = new ChannelHub(new FakeConfig());
        channel = hub
            .createChannel(ChannelType.ITEM, "主网", ALICE.personalOwnerId(), ALICE.name(), ChannelColors.DEFAULT)
            .channel();
        machine = new FakeMachine("M", 1000).with("stick", 3L);
        config = new EndpointConfig();
        host = new RecordingHost();
        hub.connect(channel.id(), "ep", machine.location, BOB.playerId(), config, machine, host);
    }

    private CoverPanel panelFor(RemoteEndpoint remote) {
        return new CoverPanel(hub, ChannelType.ITEM, remote, new Random(1));
    }

    @Test
    void 有连接权限时修改生效且操作者成为连接者() {
        RemoteEndpoint remote = new RemoteEndpoint(hub, ALICE);
        assertNull(remote.select("ep"));
        CoverPanel panel = panelFor(remote);

        assertTrue(panel.setState(ALICE, InteractionState.OUTPUT));
        panel.setPriority(5);
        panel.setRate(100);
        panel.setPeriod(40);
        panel.addFilterKey("stick");
        panel.setWhitelist(false);

        assertEquals(InteractionState.OUTPUT, config.state());
        assertEquals(5, config.priority());
        assertEquals(100, config.rate());
        assertEquals(40, config.period());
        assertTrue(
            config.filter()
                .keys()
                .contains("stick"));
        assertFalse(
            config.filter()
                .isWhitelist());
        assertEquals(
            ALICE.playerId(),
            hub.findEndpoint("ep")
                .connector());
        assertEquals(6, host.changes, "每次改动都要通知覆盖板存盘和同步外观");
    }

    @Test
    void 没有连接权限时不能选中() {
        RemoteEndpoint remote = new RemoteEndpoint(hub, BOB);

        assertEquals(new PanelNotice("no_connect_permission", "主网", true), remote.select("ep"));
        assertNull(remote.selected());
    }

    @Test
    void 未加载的连接端不能选中() {
        machine.alive = false;

        assertEquals(new PanelNotice("endpoint_unloaded", "", true), new RemoteEndpoint(hub, ALICE).select("ep"));
    }

    @Test
    void 不存在的连接端不能选中() {
        assertEquals(new PanelNotice("endpoint_missing", "", true), new RemoteEndpoint(hub, ALICE).select("没有"));
    }

    @Test
    void 选中后卸载的连接端不再能修改() {
        RemoteEndpoint remote = new RemoteEndpoint(hub, ALICE);
        assertNull(remote.select("ep"));
        CoverPanel panel = panelFor(remote);
        machine.alive = false;

        panel.setPriority(9);
        panel.addFilterKey("stick");
        panel.setState(ALICE, InteractionState.INPUT);
        remote.disconnect();

        assertEquals(0, config.priority());
        assertTrue(
            config.filter()
                .keys()
                .isEmpty());
        assertEquals(InteractionState.EMPTY, config.state());
        assertEquals(
            BOB.playerId(),
            hub.findEndpoint("ep")
                .connector(),
            "没有生效的操作不更换连接者");
        assertEquals(0, host.changes);
        assertTrue(host.reasons.isEmpty());
        assertTrue(
            remote.config()
                .filter()
                .keys()
                .isEmpty(),
            "被拒绝的改动也不留在副本里");
    }

    @Test
    void 选中后失去权限的不再能修改() {
        hub.setPermission(channel.id(), Identity.STRANGER, Permission.CONNECT, true);
        RemoteEndpoint remote = new RemoteEndpoint(hub, BOB);
        assertNull(remote.select("ep"));
        assertTrue(remote.isEditable());

        hub.setPermission(channel.id(), Identity.STRANGER, Permission.CONNECT, false);

        assertFalse(remote.isEditable());
        new CoverPanel(hub, ChannelType.ITEM, remote, new Random(1)).setPriority(9);
        assertEquals(0, config.priority());
    }

    @Test
    void 没有实际改动时不更换连接者() {
        RemoteEndpoint remote = new RemoteEndpoint(hub, ALICE);
        assertNull(remote.select("ep"));

        assertTrue(panelFor(remote).setState(ALICE, InteractionState.EMPTY));

        assertEquals(
            BOB.playerId(),
            hub.findEndpoint("ep")
                .connector());
    }

    @Test
    void 断开时通知覆盖板并说明是远程断开() {
        RemoteEndpoint remote = new RemoteEndpoint(hub, ALICE);
        assertNull(remote.select("ep"));

        remote.disconnect();

        assertNull(hub.findEndpoint("ep"));
        assertEquals(Arrays.asList(DisconnectReason.REMOTE), host.reasons);
        assertEquals("", remote.channelId());
    }

    @Test
    void 没有选中时操作没有效果() {
        RemoteEndpoint remote = new RemoteEndpoint(hub, ALICE);
        CoverPanel panel = panelFor(remote);

        panel.setPriority(1);
        remote.disconnect();

        assertEquals(0, config.priority());
        assertEquals(0, host.changes);
    }

    @Test
    void 本机存储取自选中的连接端() {
        RemoteEndpoint remote = new RemoteEndpoint(hub, ALICE);
        assertTrue(
            remote.storedObjects()
                .isEmpty());
        assertNull(remote.select("ep"));

        assertEquals(
            "stick",
            remote.storedObjects()
                .get(0)
                .key());
    }

    @Test
    void 连接端列表要求可见权限并按连接顺序排列() {
        hub.setPermission(channel.id(), Identity.STRANGER, Permission.VIEW, false);
        hub.connect(channel.id(), "ep", machine.location, ALICE.playerId(), config, machine, host);
        FakeMachine second = new FakeMachine("S", 1000);
        hub.connect(channel.id(), "ep2", second.location, ALICE.playerId(), new EndpointConfig(), second, null);
        ChannelAdmin admin = new ChannelAdmin(hub);

        List<Endpoint> rows = admin.endpointsOf(ALICE, channel.id());
        assertEquals(2, rows.size());
        assertSame(hub.findEndpoint("ep"), rows.get(0));
        assertSame(hub.findEndpoint("ep2"), rows.get(1));

        assertTrue(
            admin.endpointsOf(BOB, channel.id())
                .isEmpty());
        assertTrue(
            admin.endpointsOf(ALICE, "没有")
                .isEmpty());
    }
}
