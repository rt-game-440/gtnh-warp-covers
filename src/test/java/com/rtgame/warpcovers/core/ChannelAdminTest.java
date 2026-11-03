package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChannelAdminTest {

    private static final Viewer ALICE = new Viewer("alice", "Alice");
    private static final Viewer BOB = new Viewer("bob", "Bob");

    /** 记录回调的覆盖板。 */
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
    private ChannelAdmin admin;
    private Channel channel;

    @BeforeEach
    void setUp() {
        hub = new ChannelHub(new FakeConfig());
        admin = new ChannelAdmin(hub);
        channel = owned(ALICE, "主网");
    }

    private Channel owned(Viewer owner, String name) {
        return hub.createChannel(ChannelType.FLUID, name, owner.personalOwnerId(), owner.name(), ChannelColors.DEFAULT)
            .channel();
    }

    private RecordingHost connect(String endpointId, String connector) {
        RecordingHost host = new RecordingHost();
        FakeMachine machine = new FakeMachine(endpointId, 1000);
        hub.connect(channel.id(), endpointId, machine.location, connector, new EndpointConfig(), machine, host);
        return host;
    }

    private static void assertDenied(String key, PanelNotice notice) {
        assertTrue(notice.isError(), notice.toString());
        assertEquals(key, notice.key());
    }

    // ---- 改名 ----

    @Test
    void 改名不影响已连接的覆盖板() {
        RecordingHost host = connect("ep", "alice");
        String id = channel.id();

        PanelNotice notice = admin.rename(ALICE, id, "  新名字 ");

        assertFalse(notice.isError());
        assertEquals("新名字", channel.name());
        assertEquals(id, channel.id());
        assertSame(channel, hub.findChannel(id));
        assertEquals(
            id,
            hub.findEndpoint("ep")
                .channelId());
        assertTrue(host.reasons.isEmpty());
        assertEquals(1, host.changes, "宿主刷新频道名缓存");
    }

    @Test
    void 改名校验重名空名和超长() {
        owned(ALICE, "副网");
        assertDenied("duplicate_name", admin.rename(ALICE, channel.id(), "副网"));
        assertDenied("empty_name", admin.rename(ALICE, channel.id(), "   "));
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i <= ChannelHub.MAX_NAME_LENGTH; i++) {
            tooLong.append('长');
        }
        assertDenied("name_too_long", admin.rename(ALICE, channel.id(), tooLong.toString()));
        assertEquals("主网", channel.name());
    }

    @Test
    void 改成自己原来的名字不算重名() {
        assertFalse(
            admin.rename(ALICE, channel.id(), "主网")
                .isError());
    }

    @Test
    void 别人名下同名不算重名() {
        owned(BOB, "副网");
        assertFalse(
            admin.rename(ALICE, channel.id(), "副网")
                .isError());
    }

    // ---- 备注、颜色、分发 ----

    @Test
    void 改备注颜色和分发方式() {
        RecordingHost host = connect("ep", "alice");

        assertFalse(
            admin.setNote(ALICE, channel.id(), " 基地主水网 ")
                .isError());
        assertEquals("基地主水网", channel.note());

        assertFalse(
            admin.setColor(ALICE, channel.id(), 0x123456)
                .isError());
        assertEquals(0x123456, channel.color());
        assertEquals(1, host.changes, "颜色变化通知连接端同步外观");

        assertFalse(
            admin.setDistribution(ALICE, channel.id(), DistributionMode.SEQUENTIAL)
                .isError());
        assertEquals(DistributionMode.SEQUENTIAL, channel.distribution());
    }

    @Test
    void 改色通知该频道所有已加载的连接端() {
        RecordingHost first = connect("a", "alice");
        RecordingHost second = connect("b", "alice");
        RecordingHost other = new RecordingHost();
        FakeMachine machine = new FakeMachine("other", 1000);
        Channel otherChannel = owned(ALICE, "副网");
        hub.connect(otherChannel.id(), "c", machine.location, "alice", new EndpointConfig(), machine, other);

        admin.setColor(ALICE, channel.id(), 0x123456);
        assertEquals(1, first.changes);
        assertEquals(1, second.changes);
        assertEquals(0, other.changes, "别的频道不受影响");

        hub.unbind("b", second);
        admin.setColor(ALICE, channel.id(), 0x654321);
        assertEquals(2, first.changes);
        assertEquals(1, second.changes, "未加载的连接端不通知，加载后由客户端请求数据时取到新颜色");

        admin.setColor(ALICE, channel.id(), 0x654321);
        assertEquals(2, first.changes, "颜色没变不通知");
    }

    @Test
    void 备注不能超长() {
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i <= ChannelAdmin.MAX_NOTE_LENGTH; i++) {
            tooLong.append('长');
        }
        assertDenied("note_too_long", admin.setNote(ALICE, channel.id(), tooLong.toString()));
        assertEquals("", channel.note());
    }

    @Test
    void 没有修改配置权限时全部被拒绝() {
        String id = channel.id();
        assertDenied("no_configure_permission", admin.rename(BOB, id, "抢走"));
        assertDenied("no_configure_permission", admin.setNote(BOB, id, "抢走"));
        assertDenied("no_configure_permission", admin.setColor(BOB, id, 1));
        assertDenied("no_configure_permission", admin.setDistribution(BOB, id, DistributionMode.SEQUENTIAL));
        assertEquals("主网", channel.name());
        assertEquals("", channel.note());
        assertEquals(ChannelColors.DEFAULT, channel.color());
        assertEquals(DistributionMode.ROUND_ROBIN, channel.distribution());
    }

    @Test
    void 公开频道的陌生人也不能改配置() {
        hub.setPublic(channel.id(), true);
        assertDenied("no_configure_permission", admin.rename(BOB, channel.id(), "抢走"));
    }

    // ---- 权限 ----

    @Test
    void 编辑权限后失权的连接端立即断开() {
        hub.setPublic(channel.id(), true);
        RecordingHost stranger = connect("s", "bob");

        assertFalse(
            admin.setPermission(ALICE, channel.id(), Identity.STRANGER, Permission.CONNECT, false)
                .isError());

        assertFalse(
            channel.permissions()
                .allows(Identity.STRANGER, Permission.CONNECT));
        assertNull(hub.findEndpoint("s"));
        assertEquals(DisconnectReason.PERMISSION_LOST, stranger.reasons.get(0));
    }

    @Test
    void 公开改私人后陌生人的连接端断开() {
        admin.setPublic(ALICE, channel.id(), true);
        assertTrue(
            channel.permissions()
                .isPublic());
        RecordingHost stranger = connect("s", "bob");
        RecordingHost manager = connect("m", "alice");

        admin.setPublic(ALICE, channel.id(), false);

        assertFalse(
            channel.permissions()
                .isPublic());
        assertEquals(DisconnectReason.PERMISSION_LOST, stranger.reasons.get(0));
        assertNotNull(hub.findEndpoint("m"));
        assertTrue(manager.reasons.isEmpty());
    }

    @Test
    void 没有修改权限权限时不能编辑() {
        assertDenied(
            "no_manage_permission",
            admin.setPermission(BOB, channel.id(), Identity.STRANGER, Permission.VIEW, true));
        assertDenied("no_manage_permission", admin.setPublic(BOB, channel.id(), true));
        assertFalse(
            channel.permissions()
                .isPublic());
    }

    @Test
    void 管理者的修改权限不能取消() {
        assertDenied(
            "manager_lockout",
            admin.setPermission(ALICE, channel.id(), Identity.MANAGER, Permission.MANAGE_PERMISSIONS, false));
        assertTrue(
            channel.permissions()
                .allows(Identity.MANAGER, Permission.MANAGE_PERMISSIONS));
        assertFalse(
            admin.setPermission(ALICE, channel.id(), Identity.MANAGER, Permission.DELETE, false)
                .isError());
    }

    @Test
    void 只有管理者能改归属者级的新增频道() {
        String owner = channel.ownerId();
        assertDenied("no_manage_permission", admin.setCreateAllowed(BOB, channel.id(), Identity.MEMBER, false));
        assertTrue(hub.isCreateAllowed(owner, Identity.MEMBER));

        // 即使把修改权限授予陌生人，归属者级的新增频道仍只归管理者
        admin.setPermission(ALICE, channel.id(), Identity.STRANGER, Permission.MANAGE_PERMISSIONS, true);
        assertDenied("no_manage_permission", admin.setCreateAllowed(BOB, channel.id(), Identity.MEMBER, false));

        assertFalse(
            admin.setCreateAllowed(ALICE, channel.id(), Identity.MEMBER, false)
                .isError());
        assertFalse(hub.isCreateAllowed(owner, Identity.MEMBER));
    }

    // ---- 删除 ----

    @Test
    void 删除后所有连接端恢复未连接并记原因() {
        RecordingHost loaded = connect("a", "alice");
        FakeMachine machine = new FakeMachine("b", 1000);
        EndpointConfig unloadedConfig = new EndpointConfig();
        hub.connect(channel.id(), "b", machine.location, "alice", unloadedConfig, machine, null);
        hub.unbind("b", null);
        String id = channel.id();

        PanelNotice notice = admin.delete(ALICE, id);

        assertFalse(notice.isError());
        assertEquals("deleted", notice.key());
        assertNull(hub.findChannel(id));
        assertNull(hub.findEndpoint("a"));
        assertEquals(DisconnectReason.CHANNEL_DELETED, loaded.reasons.get(0));
        ReconcileResult late = hub.reconcile("b", id, machine.location, unloadedConfig, machine, null);
        assertEquals(ReconcileResult.Kind.DISCONNECTED, late.kind());
        assertEquals(DisconnectReason.CHANNEL_DELETED, late.reason());
    }

    @Test
    void 没有删除权限时不能删() {
        assertDenied("no_delete_permission", admin.delete(BOB, channel.id()));
        assertNotNull(hub.findChannel(channel.id()));
    }

    @Test
    void 频道不存在时给出提示() {
        assertDenied("channel_missing", admin.rename(ALICE, "没有", "x"));
        assertDenied("channel_missing", admin.delete(ALICE, "没有"));
        assertDenied("channel_missing", admin.setCreateAllowed(ALICE, "没有", Identity.MEMBER, true));
    }

    // ---- 颜色输入 ----

    @Test
    void 十六进制颜色解析() {
        assertEquals(0xB3312C, ChannelColors.parseHex("B3312C"));
        assertEquals(0xB3312C, ChannelColors.parseHex(" #b3312c "));
        assertEquals(-1, ChannelColors.parseHex("12345"));
        assertEquals(-1, ChannelColors.parseHex("GGGGGG"));
        assertEquals(-1, ChannelColors.parseHex("-12345"));
        assertEquals(-1, ChannelColors.parseHex(null));
        assertEquals("00FF00", ChannelColors.toHex(0x00FF00));
    }
}
