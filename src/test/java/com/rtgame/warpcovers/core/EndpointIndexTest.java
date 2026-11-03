package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 连接端索引：覆盖板加载对账、物品化、断开原因与区块清理。 */
class EndpointIndexTest {

    /** 记录收到的断开通知。 */
    private static final class FakeHost implements EndpointHost {

        final List<DisconnectReason> reasons = new ArrayList<DisconnectReason>();

        @Override
        public void onDisconnected(DisconnectReason reason) {
            reasons.add(reason);
        }

        @Override
        public void onEndpointChanged() {}
    }

    private ChannelHub hub;
    private Channel channel;
    private int dirtyCount;

    @BeforeEach
    void setUp() {
        hub = new ChannelHub(new FakeConfig());
        hub.setDirtyListener(new Runnable() {

            @Override
            public void run() {
                dirtyCount++;
            }
        });
        channel = hub.createChannel(ChannelType.FLUID, "索引频道");
        dirtyCount = 0;
    }

    private Endpoint connect(String id, FakeMachine machine, EndpointHost host) {
        return hub.connect(channel.id(), id, machine.location, "player-a", new EndpointConfig(), machine, host);
    }

    private ReconcileResult reconcile(String id, String channelId, FakeMachine machine, EndpointConfig config,
        EndpointHost host) {
        return hub.reconcile(id, channelId, machine.location, config, machine, host);
    }

    @Test
    void 连接端记录位置连接者与连接顺序() {
        FakeMachine a = new FakeMachine("A", 100);
        FakeMachine b = new FakeMachine("B", 100);
        Endpoint first = connect("a", a, null);
        Endpoint second = connect("b", b, null);
        assertEquals(a.location, first.location());
        assertEquals("player-a", first.connector());
        assertEquals("假机器A", first.machineName());
        assertTrue(first.connectOrder() < second.connectOrder());
        assertTrue(dirtyCount > 0, "连接应当标脏");
    }

    @Test
    void 重新加载对账同步覆盖板配置并刷新机器名() {
        FakeMachine a = new FakeMachine("A", 100);
        FakeHost oldHost = new FakeHost();
        connect("a", a, oldHost);
        hub.unbind("a", oldHost);
        assertFalse(
            hub.findEndpoint("a")
                .isLoaded());

        EndpointConfig coverConfig = new EndpointConfig();
        coverConfig.setState(InteractionState.OUTPUT);
        coverConfig.setPriority(5);
        a.machineName = "改名后的机器";
        FakeHost newHost = new FakeHost();
        dirtyCount = 0;
        ReconcileResult result = reconcile("a", channel.id(), a, coverConfig, newHost);

        assertEquals(ReconcileResult.Kind.CONNECTED, result.kind());
        Endpoint endpoint = hub.findEndpoint("a");
        assertSame(coverConfig, endpoint.config(), "覆盖板配置是权威，索引直接共享它");
        assertEquals(InteractionState.OUTPUT, endpoint.state());
        assertEquals("改名后的机器", endpoint.machineName());
        assertTrue(endpoint.isLoaded());
        assertTrue(hub.isBoundTo("a", newHost));
        assertTrue(dirtyCount > 0);
    }

    @Test
    void 未连接频道的覆盖板对账时清掉同ID残留() {
        FakeMachine a = new FakeMachine("A", 100);
        connect("a", a, null);
        ReconcileResult result = reconcile("a", "", a, new EndpointConfig(), null);
        assertEquals(ReconcileResult.Kind.NOT_CONNECTED, result.kind());
        assertNull(hub.findEndpoint("a"));
        assertTrue(
            channel.endpoints()
                .isEmpty());
    }

    @Test
    void 已加载的连接端在频道删除时立即收到通知() {
        FakeHost host = new FakeHost();
        connect("a", new FakeMachine("A", 100), host);
        hub.deleteChannel(channel.id());
        assertEquals(Collections.singletonList(DisconnectReason.CHANNEL_DELETED), host.reasons);
        assertNull(hub.findEndpoint("a"));
        assertNull(hub.findChannel(channel.id()));
    }

    @Test
    void 未加载时频道被删除_下次加载发现并断开() {
        FakeMachine a = new FakeMachine("A", 100);
        FakeHost host = new FakeHost();
        connect("a", a, host);
        hub.unbind("a", host);
        hub.deleteChannel(channel.id());
        assertTrue(host.reasons.isEmpty(), "未加载的覆盖板不应收到回调");

        ReconcileResult result = reconcile("a", channel.id(), a, new EndpointConfig(), new FakeHost());
        assertEquals(ReconcileResult.Kind.DISCONNECTED, result.kind());
        assertEquals(DisconnectReason.CHANNEL_DELETED, result.reason());
    }

    @Test
    void 未加载时按原因断开_留墓碑并在下次加载时消费() {
        FakeMachine a = new FakeMachine("A", 100);
        FakeHost host = new FakeHost();
        connect("a", a, host);
        hub.unbind("a", host);
        hub.disconnect("a", DisconnectReason.PERMISSION_LOST);

        assertNull(hub.findEndpoint("a"));
        assertTrue(
            hub.tombstones()
                .containsKey("a"));
        assertTrue(host.reasons.isEmpty());

        ReconcileResult result = reconcile("a", channel.id(), a, new EndpointConfig(), new FakeHost());
        assertEquals(ReconcileResult.Kind.DISCONNECTED, result.kind());
        assertEquals(DisconnectReason.PERMISSION_LOST, result.reason());
        assertTrue(
            hub.tombstones()
                .isEmpty(),
            "墓碑只用一次");
    }

    @Test
    void 已加载时按原因断开_当场回调不留墓碑() {
        FakeHost host = new FakeHost();
        connect("a", new FakeMachine("A", 100), host);
        hub.disconnect("a", DisconnectReason.PERMISSION_LOST);
        assertEquals(Collections.singletonList(DisconnectReason.PERMISSION_LOST), host.reasons);
        assertTrue(
            hub.tombstones()
                .isEmpty());
    }

    @Test
    void 物品化后重贴需要新连接端ID并排到末尾() {
        FakeMachine a = new FakeMachine("A", 100);
        FakeMachine b = new FakeMachine("B", 100);
        Endpoint original = connect("a", a, null);
        connect("b", b, null);

        hub.disconnect("a"); // 撬下：从索引移除
        assertNull(hub.findEndpoint("a"));

        FakeMachine moved = new FakeMachine("C", 100);
        ReconcileResult result = reconcile("a", channel.id(), moved, new EndpointConfig(), new FakeHost());
        assertEquals(ReconcileResult.Kind.REATTACH, result.kind());

        Endpoint reattached = hub
            .connect(channel.id(), "a2", moved.location, "player-b", new EndpointConfig(), moved, null);
        assertEquals("player-b", reattached.connector());
        assertTrue(reattached.connectOrder() > original.connectOrder());
        List<Endpoint> order = channel.endpoints();
        assertEquals(
            "b",
            order.get(0)
                .endpointId());
        assertEquals(
            "a2",
            order.get(1)
                .endpointId());
    }

    @Test
    void 物品化时频道已删除_重贴得到频道已删除() {
        FakeMachine a = new FakeMachine("A", 100);
        connect("a", a, null);
        hub.disconnect("a");
        hub.deleteChannel(channel.id());
        ReconcileResult result = reconcile("a", channel.id(), a, new EndpointConfig(), null);
        assertEquals(ReconcileResult.Kind.DISCONNECTED, result.kind());
        assertEquals(DisconnectReason.CHANNEL_DELETED, result.reason());
    }

    @Test
    void 同ID覆盖板在别处被复制时按重贴处理且不影响原连接端() {
        FakeMachine a = new FakeMachine("A", 100);
        FakeHost originalHost = new FakeHost();
        connect("a", a, originalHost);

        FakeMachine copy = new FakeMachine("B", 100);
        ReconcileResult result = reconcile("a", channel.id(), copy, new EndpointConfig(), new FakeHost());
        assertEquals(ReconcileResult.Kind.REATTACH, result.kind());
        Endpoint endpoint = hub.findEndpoint("a");
        assertNotNull(endpoint);
        assertEquals(a.location, endpoint.location());
        assertTrue(hub.isBoundTo("a", originalHost));
    }

    @Test
    void 索引与覆盖板记的频道不一致时以索引为准() {
        Channel other = hub.createChannel(ChannelType.FLUID, "另一个频道");
        FakeMachine a = new FakeMachine("A", 100);
        connect("a", a, null);
        hub.unbind("a", null);
        ReconcileResult result = reconcile("a", other.id(), a, new EndpointConfig(), new FakeHost());
        assertEquals(ReconcileResult.Kind.REATTACH, result.kind());
        assertNull(hub.findEndpoint("a"), "旧频道里的条目应被移除");
        assertTrue(
            channel.endpoints()
                .isEmpty());
    }

    @Test
    void 卸载只解绑仍由该覆盖板持有的连接端() {
        FakeMachine a = new FakeMachine("A", 100);
        FakeHost host = new FakeHost();
        connect("a", a, host);
        hub.unbind("a", new FakeHost());
        assertTrue(
            hub.findEndpoint("a")
                .isLoaded(),
            "别的覆盖板卸载不能解绑");
        hub.unbind("a", host);
        assertFalse(
            hub.findEndpoint("a")
                .isLoaded());
    }

    @Test
    void 区块清理只删该区块内未加载且找不到覆盖板的条目() {
        FakeMachine inChunk = new FakeMachine("A", 100, new Location(0, 3, 64, 5, 1));
        FakeMachine inChunkPresent = new FakeMachine("B", 100, new Location(0, 4, 64, 5, 1));
        FakeMachine inChunkLoaded = new FakeMachine("C", 100, new Location(0, 6, 64, 5, 1));
        FakeMachine otherChunk = new FakeMachine("D", 100, new Location(0, 40, 64, 5, 1));
        FakeMachine otherDim = new FakeMachine("E", 100, new Location(-1, 3, 64, 5, 1));
        connect("gone", inChunk, null);
        connect("present", inChunkPresent, null);
        connect("loaded", inChunkLoaded, new FakeHost());
        connect("far", otherChunk, null);
        connect("nether", otherDim, null);
        hub.unbind("gone", null);
        hub.unbind("present", null);
        hub.unbind("far", null);
        hub.unbind("nether", null);
        hub.disconnect("present", DisconnectReason.PERMISSION_LOST); // 留下墓碑，覆盖板仍在
        connect("present2", inChunkPresent, null);
        hub.unbind("present2", null);

        final Set<String> present = new HashSet<String>();
        present.add("present");
        present.add("present2");
        dirtyCount = 0;
        int removed = hub.cleanChunk(0, 0, 0, new EndpointPresence() {

            @Override
            public Presence check(String endpointId, Location location) {
                return present.contains(endpointId) ? Presence.PRESENT : Presence.ABSENT;
            }
        });

        assertEquals(1, removed);
        assertNull(hub.findEndpoint("gone"));
        assertNotNull(hub.findEndpoint("present2"));
        assertNotNull(hub.findEndpoint("loaded"));
        assertNotNull(hub.findEndpoint("far"));
        assertNotNull(hub.findEndpoint("nether"));
        assertTrue(
            hub.tombstones()
                .containsKey("present"));
        assertTrue(dirtyCount > 0);
    }

    @Test
    void 区块清理在无法判断时保留条目() {
        FakeMachine a = new FakeMachine("A", 100, new Location(0, 3, 64, 5, 1));
        connect("a", a, null);
        hub.unbind("a", null);
        int removed = hub.cleanChunk(0, 0, 0, new EndpointPresence() {

            @Override
            public Presence check(String endpointId, Location location) {
                return Presence.UNKNOWN;
            }
        });
        assertEquals(0, removed);
        assertNotNull(hub.findEndpoint("a"));
    }

    @Test
    void 区块清理删除找不到覆盖板的墓碑() {
        FakeMachine a = new FakeMachine("A", 100, new Location(0, 3, 64, 5, 1));
        connect("a", a, null);
        hub.unbind("a", null);
        hub.disconnect("a", DisconnectReason.PERMISSION_LOST);
        int removed = hub.cleanChunk(0, 0, 0, new EndpointPresence() {

            @Override
            public Presence check(String endpointId, Location location) {
                return Presence.ABSENT;
            }
        });
        assertEquals(1, removed);
        assertTrue(
            hub.tombstones()
                .isEmpty());
    }

    @Test
    void 恢复索引保持连接顺序且新连接排在后面() {
        ChannelHub restored = new ChannelHub(new FakeConfig());
        restored.restoreChannel("ch", ChannelType.FLUID, "恢复频道");
        Location loc = new Location(7, 1, 2, 3, 4);
        restored.restoreEndpoint("late", "ch", 9, loc, "p", "机器", InteractionState.OUTPUT);
        restored.restoreEndpoint("early", "ch", 2, loc, "p", "机器", InteractionState.INPUT);
        restored.restoreEndpoint("orphan", "missing", 1, loc, "p", "机器", InteractionState.INPUT);
        restored.restoreNextConnectOrder(5);

        Channel ch = restored.findChannel("ch");
        assertEquals(
            "early",
            ch.endpoints()
                .get(0)
                .endpointId());
        assertEquals(
            "late",
            ch.endpoints()
                .get(1)
                .endpointId());
        assertNull(restored.findEndpoint("orphan"), "频道缺失的条目丢弃");
        assertEquals(
            InteractionState.INPUT,
            restored.findEndpoint("early")
                .state());
        assertEquals(
            "机器",
            restored.findEndpoint("early")
                .machineName());
        assertEquals(10, restored.nextConnectOrder());
        assertFalse(
            restored.findEndpoint("early")
                .isLoaded(),
            "恢复的条目在覆盖板加载前视为未加载");
    }
}
