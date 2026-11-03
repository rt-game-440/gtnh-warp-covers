package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChannelListingTest {

    private static final String TEAM = "team:1";
    private static final Viewer ALICE = new Viewer("alice", "Alice");
    private static final Viewer BOB = new Viewer("bob", "Bob");

    /** 假身份端口：可以指定 Alice 相对于某个归属者的身份。 */
    private static final class FakeIdentity implements IdentityPort {

        final Map<String, Identity> aliceIn = new HashMap<String, Identity>();

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public String ownerFor(String playerId) {
            return Viewer.personalOwnerId(playerId);
        }

        @Override
        public String ownerName(String ownerId, String playerName) {
            return playerName;
        }

        @Override
        public Identity identityOf(String playerId, String ownerId) {
            Identity identity = "alice".equals(playerId) ? aliceIn.get(ownerId) : null;
            return identity != null ? identity : PersonalIdentity.INSTANCE.identityOf(playerId, ownerId);
        }
    }

    private FakeIdentity identity;
    private ChannelHub hub;
    private FakePanelEndpoint endpoint;
    private CoverPanel panel;

    @BeforeEach
    void setUp() {
        identity = new FakeIdentity();
        hub = new ChannelHub(new FakeConfig(), identity);
        endpoint = new FakePanelEndpoint(hub, "ep", new FakeMachine("ep", 1000));
        panel = new CoverPanel(hub, ChannelType.FLUID, endpoint, new Random(1));
    }

    private Channel channel(String name, String ownerId) {
        return hub.createChannel(ChannelType.FLUID, name, ownerId, ownerId, 0)
            .channel();
    }

    private List<String> names(Viewer viewer) {
        List<String> names = new ArrayList<String>();
        for (ChannelEntry entry : panel.listing(viewer)) {
            names.add(entry.channel.name());
        }
        return names;
    }

    private List<String> matchedNames(Viewer viewer) {
        List<String> names = new ArrayList<String>();
        for (ChannelEntry entry : panel.listing(viewer)) {
            if (panel.isShown(viewer, entry)) {
                names.add(entry.channel.name());
            }
        }
        return names;
    }

    // ---- 分组与排序 ----

    @Test
    void 按自己盟友公开分组且组内按名称排序() {
        identity.aliceIn.put(TEAM, Identity.MEMBER);
        identity.aliceIn.put("team:2", Identity.ALLY);
        channel("甲-公开", "player:carol");
        hub.setPublic(channelNamed("甲-公开").id(), true);
        channel("乙-盟友", "team:2");
        channel("丁-队伍", TEAM);
        channel("丙-个人", ALICE.personalOwnerId());
        channel("看不到的私人频道", "player:dave");

        List<ChannelEntry> entries = panel.listing(ALICE);

        assertEquals(4, entries.size());
        assertEquals(ChannelGroup.OWN, entries.get(0).group);
        assertEquals(ChannelGroup.OWN, entries.get(1).group);
        assertEquals(ChannelGroup.ALLY, entries.get(2).group);
        assertEquals(ChannelGroup.PUBLIC, entries.get(3).group);
        assertEquals("乙-盟友", entries.get(2).channel.name());
        assertEquals("甲-公开", entries.get(3).channel.name());
    }

    private Channel channelNamed(String name) {
        for (Channel channel : hub.channels()) {
            if (channel.name()
                .equals(name)) {
                return channel;
            }
        }
        throw new IllegalArgumentException(name);
    }

    @Test
    void 已连接的频道跨组置顶() {
        channel("A-自己", ALICE.personalOwnerId());
        channel("B-自己", ALICE.personalOwnerId());
        Channel pub = channel("C-公开", "player:carol");
        hub.setPublic(pub.id(), true);

        panel.toggleConnection(ALICE, pub.id());

        assertEquals(Arrays.asList("C-公开", "A-自己", "B-自己"), names(ALICE));
        assertTrue(
            panel.listing(ALICE)
                .get(0).connected);
    }

    @Test
    void 列表项带输入输出连接端数量() {
        Channel channel = channel("主网", ALICE.personalOwnerId());
        connect(channel, "in1", InteractionState.INPUT);
        connect(channel, "in2", InteractionState.INPUT);
        connect(channel, "out", InteractionState.OUTPUT);
        connect(channel, "idle", InteractionState.EMPTY);

        ChannelEntry entry = panel.listing(ALICE)
            .get(0);

        assertEquals(2, entry.inputs);
        assertEquals(1, entry.outputs);
    }

    private void connect(Channel channel, String id, InteractionState state) {
        FakeMachine machine = new FakeMachine(id, 1000);
        EndpointConfig config = new EndpointConfig();
        config.setState(state);
        hub.connect(channel.id(), id, machine.location, "alice", config, machine, null);
    }

    // ---- 搜索 ----

    @Test
    void 按频道名和备注搜索且不区分大小写() {
        channel("Water主网", ALICE.personalOwnerId());
        Channel noted = channel("副网", ALICE.personalOwnerId());
        hub.setNote(noted.id(), "地下室的 WATER 备用");
        channel("岩浆", ALICE.personalOwnerId());

        panel.setSearch("water");
        assertEquals(Arrays.asList("Water主网", "副网"), matchedNames(ALICE));

        panel.setSearch("  岩浆 ");
        assertEquals(Collections.singletonList("岩浆"), matchedNames(ALICE));

        panel.setSearch("");
        assertEquals(3, matchedNames(ALICE).size());
    }

    // ---- 标签 ----

    @Test
    void 按标签筛选且可以和搜索组合() {
        Channel a = channel("主网", ALICE.personalOwnerId());
        Channel b = channel("副网", ALICE.personalOwnerId());
        channel("其他", ALICE.personalOwnerId());
        panel.addTag(ALICE, a.id(), "基地");
        panel.addTag(ALICE, b.id(), "基地");
        panel.addTag(ALICE, b.id(), "矿场");

        assertEquals(Arrays.asList("基地", "矿场"), panel.availableTags(ALICE));

        panel.setTagFilter("基地");
        assertEquals(Arrays.asList("主网", "副网"), sorted(matchedNames(ALICE)));
        panel.setSearch("副");
        assertEquals(Collections.singletonList("副网"), matchedNames(ALICE));
    }

    private static List<String> sorted(List<String> names) {
        List<String> copy = new ArrayList<String>(names);
        Collections.sort(copy);
        return copy;
    }

    @Test
    void 标签只有自己看得到() {
        Channel channel = channel("公共", "player:carol");
        hub.setPublic(channel.id(), true);
        panel.addTag(ALICE, channel.id(), "Alice的标签");

        assertEquals(
            Collections.singleton("Alice的标签"),
            panel.listing(ALICE)
                .get(0).tags);
        assertTrue(
            panel.listing(BOB)
                .get(0).tags.isEmpty());
        assertTrue(
            panel.availableTags(BOB)
                .isEmpty());
    }

    @Test
    void 给可见频道打标签不需要其他权限但不可见的不行() {
        Channel pub = channel("公开", "player:carol");
        hub.setPublic(pub.id(), true);
        Channel hidden = channel("私人", "player:carol");

        assertFalse(
            panel.addTag(ALICE, pub.id(), "可以")
                .isError());
        PanelNotice denied = panel.addTag(ALICE, hidden.id(), "不行");
        assertTrue(denied.isError());
        assertEquals("channel_missing", denied.key());
        assertTrue(
            hub.tags()
                .of("alice", hidden.id())
                .isEmpty());
    }

    @Test
    void 标签校验长度和数量() {
        Channel channel = channel("主网", ALICE.personalOwnerId());
        assertEquals(
            "tag_empty",
            panel.addTag(ALICE, channel.id(), "   ")
                .key());
        assertEquals(
            "tag_too_long",
            panel.addTag(ALICE, channel.id(), "12345678901234567")
                .key());
        for (int i = 0; i < PrivateTags.MAX_TAGS_PER_CHANNEL; i++) {
            assertFalse(
                panel.addTag(ALICE, channel.id(), "t" + i)
                    .isError());
        }
        assertEquals(
            "too_many_tags",
            panel.addTag(ALICE, channel.id(), "多一个")
                .key());
        assertFalse(
            panel.addTag(ALICE, channel.id(), "t0")
                .isError(),
            "重复标签不算超限");
    }

    @Test
    void 删除标签后筛选自动失效() {
        Channel channel = channel("主网", ALICE.personalOwnerId());
        channel("其他", ALICE.personalOwnerId());
        panel.addTag(ALICE, channel.id(), "基地");
        panel.setTagFilter("基地");
        assertEquals(1, matchedNames(ALICE).size());

        panel.removeTag(ALICE, channel.id(), "基地");

        assertEquals(2, matchedNames(ALICE).size(), "标签没了就不再按它筛选");
        assertEquals("", panel.tagFilter(ALICE));
    }

    @Test
    void 删除频道时清掉它的标签和传输记录() {
        Channel channel = channel("主网", ALICE.personalOwnerId());
        panel.addTag(ALICE, channel.id(), "基地");
        hub.recentTransfers()
            .record(channel.id(), "water");

        hub.deleteChannel(channel.id());

        assertTrue(
            hub.tags()
                .of("alice", channel.id())
                .isEmpty());
        assertTrue(
            hub.recentTransfers()
                .of(channel.id())
                .isEmpty());
    }

    // ---- 最近传输记录 ----

    @Test
    void 最近传输记录按最近使用排序并有上限() {
        RecentTransfers recents = new RecentTransfers();
        recents.record("c", "a");
        recents.record("c", "b");
        recents.record("c", "a");
        assertEquals(Arrays.asList("a", "b"), recents.of("c"));

        for (int i = 0; i < RecentTransfers.LIMIT + 5; i++) {
            recents.record("c", "k" + i);
        }
        List<String> keys = recents.of("c");
        assertEquals(RecentTransfers.LIMIT, keys.size());
        assertEquals("k" + (RecentTransfers.LIMIT + 4), keys.get(0));
        assertFalse(keys.contains("a"), "最久没用的被淘汰");
        assertTrue(
            recents.of("其他频道")
                .isEmpty());
    }

    @Test
    void 真实传输会留下记录且只记真正搬运过的() {
        Channel channel = channel("主网", ALICE.personalOwnerId());
        FakeMachine source = new FakeMachine("S", 1000);
        FakeMachine sink = new FakeMachine("T", 1000);
        source.with("water", 500L);
        EndpointConfig in = new EndpointConfig();
        in.setState(InteractionState.INPUT);
        in.filter()
            .add("water");
        EndpointConfig out = new EndpointConfig();
        out.setState(InteractionState.OUTPUT);
        out.filter()
            .add("water");
        hub.connect(channel.id(), "in", source.location, "alice", in, source, null);

        hub.driveInput("in", 100);
        assertTrue(
            hub.recentTransfers()
                .of(channel.id())
                .isEmpty(),
            "没有输出端时什么都没搬，不记录");

        hub.connect(channel.id(), "out", sink.location, "alice", out, sink, null);
        hub.driveInput("in", 200);

        assertEquals(
            Collections.singletonList("water"),
            hub.recentTransfers()
                .of(channel.id()));
        assertNull(hub.findChannel("没有"));
    }
}
