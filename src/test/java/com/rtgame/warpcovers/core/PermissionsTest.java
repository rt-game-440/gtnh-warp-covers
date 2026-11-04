package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PermissionsTest {

    private static final String TEAM = "team:1";

    /** 假身份端口：表里有队伍和身份，其余按个人规则。 */
    private static final class FakeIdentity implements IdentityPort {

        boolean ready = true;
        final Map<String, String> teamOf = new HashMap<String, String>();
        final Map<String, Identity> inTeam = new HashMap<String, Identity>();

        void join(String player, Identity identity) {
            teamOf.put(player, TEAM);
            inTeam.put(player, identity);
        }

        void leave(String player) {
            teamOf.remove(player);
            inTeam.remove(player);
        }

        @Override
        public boolean isReady() {
            return ready;
        }

        @Override
        public String ownerFor(String playerId) {
            String team = teamOf.get(playerId);
            return team != null ? team : Viewer.personalOwnerId(playerId);
        }

        @Override
        public String ownerName(String ownerId, String playerName) {
            return TEAM.equals(ownerId) ? "队伍" : playerName;
        }

        @Override
        public Identity identityOf(String playerId, String ownerId) {
            if (TEAM.equals(ownerId)) {
                Identity identity = inTeam.get(playerId);
                return identity == null ? Identity.STRANGER : identity;
            }
            return PersonalIdentity.INSTANCE.identityOf(playerId, ownerId);
        }
    }

    /** 记录断开原因的覆盖板回调。 */
    private static final class RecordingHost implements EndpointHost {

        final List<DisconnectReason> reasons = new ArrayList<DisconnectReason>();

        @Override
        public void onDisconnected(DisconnectReason reason) {
            reasons.add(reason);
        }

        @Override
        public void onEndpointChanged() {}
    }

    private FakeConfig config;
    private FakeIdentity identity;
    private ChannelHub hub;
    private final Viewer alice = new Viewer("alice", "Alice");
    private final Viewer bob = new Viewer("bob", "Bob");

    @BeforeEach
    void setUp() {
        config = new FakeConfig();
        identity = new FakeIdentity();
        hub = new ChannelHub(config, identity);
    }

    private Channel teamChannel(String name) {
        return hub.createChannel(ChannelType.FLUID, name, TEAM, "队伍", 0)
            .channel();
    }

    private CoverPanel panel(String endpointId) {
        return new CoverPanel(
            hub,
            ChannelType.FLUID,
            new FakePanelEndpoint(hub, endpointId, new FakeMachine(endpointId, 1000)),
            new Random(1));
    }

    private RecordingHost connectLoaded(Channel channel, String endpointId, String connector) {
        RecordingHost host = new RecordingHost();
        FakeMachine machine = new FakeMachine(endpointId, 1000);
        hub.connect(channel.id(), endpointId, machine.location, connector, new EndpointConfig(), machine, host);
        return host;
    }

    // ---- 默认矩阵 ----

    private static void assertRow(ChannelPermissions permissions, Identity who, boolean view, boolean connect,
        boolean configure, boolean manage, boolean delete) {
        assertEquals(view, permissions.allows(who, Permission.VIEW), who + " 可见");
        assertEquals(connect, permissions.allows(who, Permission.CONNECT), who + " 连接");
        assertEquals(configure, permissions.allows(who, Permission.CONFIGURE), who + " 修改配置");
        assertEquals(manage, permissions.allows(who, Permission.MANAGE_PERMISSIONS), who + " 修改权限");
        assertEquals(delete, permissions.allows(who, Permission.DELETE), who + " 删除");
    }

    @Test
    void 默认矩阵符合需求且陌生人默认私人() {
        ChannelPermissions permissions = new ChannelPermissions();
        assertRow(permissions, Identity.MANAGER, true, true, true, true, true);
        assertRow(permissions, Identity.MEMBER, true, true, true, false, false);
        assertRow(permissions, Identity.ALLY, true, true, false, false, false);
        assertRow(permissions, Identity.STRANGER, false, false, false, false, false);
        assertFalse(permissions.isPublic());
    }

    @Test
    void 公开预设让陌生人可见可连接() {
        ChannelPermissions permissions = new ChannelPermissions();
        permissions.applyStrangerPreset(true);
        assertRow(permissions, Identity.STRANGER, true, true, false, false, false);
        assertTrue(permissions.isPublic());
    }

    @Test
    void 权限矩阵存取往返() {
        ChannelPermissions permissions = new ChannelPermissions();
        permissions.set(Identity.ALLY, Permission.CONNECT, false);
        ChannelPermissions back = ChannelPermissions.fromMasks(permissions.toMasks());
        assertArrayEquals(permissions.toMasks(), back.toMasks());
        assertFalse(back.allows(Identity.ALLY, Permission.CONNECT));
        assertArrayEquals(
            new ChannelPermissions().toMasks(),
            ChannelPermissions.fromMasks(new int[] { 1 })
                .toMasks());
    }

    // ---- 身份与归属 ----

    @Test
    void 个人回退下本人是管理者其他人是陌生人() {
        String owner = Viewer.personalOwnerId("alice");
        assertEquals(Identity.MANAGER, PersonalIdentity.INSTANCE.identityOf("alice", owner));
        assertEquals(Identity.STRANGER, PersonalIdentity.INSTANCE.identityOf("bob", owner));
        assertEquals(owner, PersonalIdentity.INSTANCE.ownerFor("alice"));
    }

    @Test
    void 在队伍里新建的频道归队伍否则归本人() {
        identity.join("alice", Identity.MEMBER);
        CoverPanel panel = panel("p");
        panel.createChannel(alice, "队伍的", false);
        panel.createChannel(bob, "个人的", false);
        Map<String, Channel> byName = new HashMap<String, Channel>();
        for (Channel channel : hub.channels()) {
            byName.put(channel.name(), channel);
        }
        assertEquals(
            TEAM,
            byName.get("队伍的")
                .ownerId());
        assertEquals(
            "队伍",
            byName.get("队伍的")
                .ownerName());
        assertEquals(
            Viewer.personalOwnerId("bob"),
            byName.get("个人的")
                .ownerId());
    }

    @Test
    void 入队后已有的个人频道仍归个人() {
        CoverPanel panel = panel("p");
        panel.createChannel(alice, "旧频道", false);
        identity.join("alice", Identity.MEMBER);
        assertEquals(
            Viewer.personalOwnerId("alice"),
            hub.channels()
                .iterator()
                .next()
                .ownerId());
        panel.createChannel(alice, "新频道", false);
        assertEquals(
            2,
            panel.visibleChannels(alice)
                .size(),
            "个人频道和队伍频道都能看到");
    }

    // ---- 面板权限检查 ----

    @Test
    void 列表只显示有可见权限的频道() {
        Channel channel = teamChannel("队伍频道");
        CoverPanel panel = panel("p");
        assertTrue(
            panel.visibleChannels(alice)
                .isEmpty(),
            "陌生人看不到私人频道");
        identity.join("alice", Identity.ALLY);
        assertEquals(
            1,
            panel.visibleChannels(alice)
                .size(),
            "盟友可见");
        identity.leave("alice");
        hub.setPublic(channel.id(), true);
        assertEquals(
            1,
            panel.visibleChannels(alice)
                .size(),
            "公开频道陌生人可见");
    }

    @Test
    void 连接断开和切换状态都需要连接权限() {
        Channel channel = teamChannel("队伍频道");
        identity.join("alice", Identity.MEMBER);
        CoverPanel panel = panel("p");

        PanelNotice denied = panel.toggleConnection(bob, channel.id());
        assertTrue(denied.isError());
        assertEquals("no_connect_permission", denied.key());
        assertNull(hub.findEndpoint("p"));

        assertFalse(
            panel.toggleConnection(alice, channel.id())
                .isError());
        assertNotNull(hub.findEndpoint("p"));

        assertFalse(panel.setState(bob, InteractionState.INPUT), "陌生人不能改交互状态");
        assertEquals(
            InteractionState.EMPTY,
            hub.findEndpoint("p")
                .state());
        assertTrue(panel.setState(alice, InteractionState.INPUT));

        assertEquals(
            "no_connect_permission",
            panel.toggleConnection(bob, channel.id())
                .key(),
            "陌生人不能断开");
        assertNotNull(hub.findEndpoint("p"));
    }

    @Test
    void 切换要求对旧频道有连接权限() {
        Channel old = teamChannel("旧频道");
        identity.join("alice", Identity.MEMBER);
        CoverPanel panel = panel("p");
        panel.toggleConnection(alice, old.id());
        hub.findEndpoint("p")
            .config()
            .setState(InteractionState.INPUT);
        // 陌生人只对新频道有连接权限，对旧频道没有
        Channel target = teamChannel("公开频道");
        hub.setPublic(target.id(), true);

        PanelNotice denied = panel.toggleConnection(bob, target.id());

        assertTrue(denied.isError());
        assertEquals("no_connect_permission", denied.key());
        assertEquals("旧频道", denied.arg(), "报错应指出缺权限的是旧频道");
        assertEquals(
            old.id(),
            hub.findEndpoint("p")
                .channelId(),
            "连接不变");
        assertEquals(
            InteractionState.INPUT,
            hub.findEndpoint("p")
                .state(),
            "交互状态不变");
    }

    @Test
    void 切换要求对新频道有连接权限() {
        Channel old = teamChannel("旧频道");
        identity.join("alice", Identity.MEMBER);
        CoverPanel panel = panel("p");
        panel.toggleConnection(alice, old.id());
        hub.setPublic(old.id(), true);
        Channel target = teamChannel("私人频道");

        PanelNotice denied = panel.toggleConnection(bob, target.id());

        assertEquals("no_connect_permission", denied.key());
        assertEquals("私人频道", denied.arg());
        assertEquals(
            old.id(),
            hub.findEndpoint("p")
                .channelId());
    }

    @Test
    void 对两个频道都有连接权限时切换成功且旧频道不再保留连接端() {
        Channel old = teamChannel("旧频道");
        Channel target = teamChannel("新频道");
        identity.join("alice", Identity.MEMBER);
        CoverPanel panel = panel("p");
        panel.toggleConnection(alice, old.id());

        assertFalse(
            panel.toggleConnection(alice, target.id())
                .isError());

        assertTrue(
            old.endpoints()
                .isEmpty());
        assertEquals(
            target.id(),
            hub.findEndpoint("p")
                .channelId());
    }

    @Test
    void 修改覆盖板自身设置不需要频道权限() {
        Channel channel = teamChannel("队伍频道");
        identity.join("alice", Identity.MEMBER);
        CoverPanel panel = panel("p");
        panel.toggleConnection(alice, channel.id());
        // 这些操作不带玩家参数：任何能打开界面的人都可以改
        panel.setPriority(3);
        panel.setRate(10);
        panel.setPeriod(40);
        assertEquals(
            3,
            hub.findEndpoint("p")
                .config()
                .priority());
    }

    // ---- 新增频道 ----

    @Test
    void 新增频道默认管理者和组员可以() {
        identity.join("alice", Identity.MANAGER);
        identity.join("bob", Identity.MEMBER);
        assertNull(hub.canCreate("alice", ChannelType.FLUID));
        assertNull(hub.canCreate("bob", ChannelType.FLUID));
        assertNull(hub.canCreate("carol", ChannelType.FLUID), "个人归属者下本人就是管理者");
    }

    @Test
    void 归属者关闭组员新增后组员被拒绝() {
        identity.join("bob", Identity.MEMBER);
        hub.setCreateAllowed(TEAM, Identity.MEMBER, false);
        assertEquals(CreateFailure.NO_PERMISSION, hub.canCreate("bob", ChannelType.FLUID));
        CoverPanel panel = panel("p");
        PanelNotice notice = panel.createChannel(bob, "不行", false);
        assertTrue(notice.isError());
        assertEquals("no_create_permission", notice.key());
        assertTrue(
            hub.channels()
                .isEmpty());
    }

    // ---- 频道数上限 ----

    @Test
    void 频道数上限按归属者按类型生效() {
        config.maxChannels = 1;
        ChannelHub local = hub;
        CoverPanel panel = panel("p");
        assertNull(local.canCreate("alice", ChannelType.FLUID));
        assertFalse(
            panel.createChannel(alice, "第一个", false)
                .isError());
        assertEquals(CreateFailure.LIMIT_REACHED, local.canCreate("alice", ChannelType.FLUID));
        assertEquals(
            "limit_reached",
            panel.createChannel(alice, "第二个", false)
                .key());
        assertNull(local.canCreate("alice", ChannelType.ITEM), "其他类型不受影响");
        assertNull(local.canCreate("bob", ChannelType.FLUID), "其他归属者不受影响");
    }

    @Test
    void 默认不限频道数() {
        assertEquals(0, config.maxChannelsPerOwnerPerType());
        CoverPanel panel = panel("p");
        for (int i = 0; i < 20; i++) {
            assertFalse(
                panel.createChannel(alice, "频道" + i, false)
                    .isError());
        }
    }

    // ---- 失权断开 ----

    @Test
    void 退队后已加载的连接端立即断开() {
        Channel channel = teamChannel("队伍频道");
        identity.join("bob", Identity.MEMBER);
        RecordingHost host = connectLoaded(channel, "ep", "bob");
        assertEquals(0, hub.reevaluateConnections());
        identity.leave("bob");
        assertEquals(1, hub.reevaluateConnections());
        assertNull(hub.findEndpoint("ep"));
        assertEquals(1, host.reasons.size());
        assertEquals(DisconnectReason.PERMISSION_LOST, host.reasons.get(0));
    }

    @Test
    void 未加载的连接端失权后下次加载时同步() {
        Channel channel = teamChannel("队伍频道");
        identity.join("bob", Identity.MEMBER);
        FakeMachine machine = new FakeMachine("M", 1000);
        EndpointConfig endpointConfig = new EndpointConfig();
        hub.connect(channel.id(), "ep", machine.location, "bob", endpointConfig, machine, null);
        hub.unbind("ep", null);
        identity.leave("bob");
        assertEquals(1, hub.reevaluateConnections());
        assertNull(hub.findEndpoint("ep"));
        assertTrue(
            hub.tombstones()
                .containsKey("ep"));
        ReconcileResult result = hub.reconcile("ep", channel.id(), machine.location, endpointConfig, machine, null);
        assertEquals(ReconcileResult.Kind.DISCONNECTED, result.kind());
        assertEquals(DisconnectReason.PERMISSION_LOST, result.reason());
    }

    @Test
    void 公开改私人后陌生人连接者被断开而组员不受影响() {
        Channel channel = teamChannel("队伍频道");
        identity.join("alice", Identity.MEMBER);
        hub.setPublic(channel.id(), true);
        RecordingHost strangerHost = connectLoaded(channel, "s", "carol");
        RecordingHost memberHost = connectLoaded(channel, "m", "alice");
        hub.setPublic(channel.id(), false);
        assertNull(hub.findEndpoint("s"), "改私人后立即断开，不用等定时复查");
        assertEquals(DisconnectReason.PERMISSION_LOST, strangerHost.reasons.get(0));
        assertNotNull(hub.findEndpoint("m"));
        assertTrue(memberHost.reasons.isEmpty());
    }

    @Test
    void 权限矩阵变化后立即复查() {
        Channel channel = teamChannel("队伍频道");
        identity.join("alice", Identity.ALLY);
        connectLoaded(channel, "a", "alice");
        hub.setPermission(channel.id(), Identity.ALLY, Permission.CONNECT, false);
        assertNull(hub.findEndpoint("a"));
    }

    @Test
    void 身份数据不可用时不做失权判定() {
        Channel channel = teamChannel("队伍频道");
        identity.join("bob", Identity.MEMBER);
        connectLoaded(channel, "ep", "bob");
        identity.leave("bob");
        identity.ready = false;
        assertEquals(0, hub.reevaluateConnections());
        assertNotNull(hub.findEndpoint("ep"));
    }

    @Test
    void 没有归属者的频道不判定权限() {
        Channel channel = hub.createChannel(ChannelType.FLUID, "无归属");
        connectLoaded(channel, "ep", "");
        assertEquals(0, hub.reevaluateConnections());
        assertNotNull(hub.findEndpoint("ep"));
    }

    // ---- 解散转移 ----

    @Test
    void 解散后频道转给原队长且同名加后缀() {
        Channel team = teamChannel("主网");
        Channel other = teamChannel("副网");
        Channel teamItem = hub.createChannel(ChannelType.ITEM, "主网", TEAM, "队伍", 0)
            .channel();
        String leader = Viewer.personalOwnerId("alice");
        hub.createChannel(ChannelType.FLUID, "主网", leader, "Alice", 0);

        assertEquals(3, hub.transferOwner(TEAM, leader, "Alice"));

        assertEquals(leader, team.ownerId());
        assertEquals("Alice", team.ownerName());
        assertEquals("主网 2", team.name());
        assertEquals("副网", other.name());
        assertEquals("主网", teamItem.name(), "不同类型不算重名");
    }

    @Test
    void 后缀继续递增且不超过名字长度上限() {
        String longName = "12345678901234567890123456789012";
        Channel a = teamChannel(longName);
        Channel b = teamChannel("x");
        String leader = Viewer.personalOwnerId("alice");
        hub.createChannel(ChannelType.FLUID, longName, leader, "Alice", 0);
        hub.createChannel(ChannelType.FLUID, "x", leader, "Alice", 0);
        hub.createChannel(ChannelType.FLUID, "x 2", leader, "Alice", 0);
        hub.transferOwner(TEAM, leader, "Alice");
        assertEquals(
            ChannelHub.MAX_NAME_LENGTH,
            a.name()
                .length());
        assertTrue(
            a.name()
                .endsWith(" 2"));
        assertEquals("x 3", b.name());
    }

    @Test
    void 解散后原组员失权而原队长保留() {
        Channel channel = teamChannel("主网");
        identity.join("alice", Identity.MANAGER);
        identity.join("bob", Identity.MEMBER);
        connectLoaded(channel, "leader", "alice");
        connectLoaded(channel, "member", "bob");
        hub.transferOwner(TEAM, Viewer.personalOwnerId("alice"), "Alice");
        identity.leave("alice");
        identity.leave("bob");
        assertEquals(1, hub.reevaluateConnections());
        assertNotNull(hub.findEndpoint("leader"));
        assertNull(hub.findEndpoint("member"));
    }
}
