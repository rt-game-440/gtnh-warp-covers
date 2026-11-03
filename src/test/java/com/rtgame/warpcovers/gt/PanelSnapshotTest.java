package com.rtgame.warpcovers.gt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelGroup;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.ConfigPort;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.CreateFailure;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.EndpointStatus;
import com.rtgame.warpcovers.core.Identity;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.Location;
import com.rtgame.warpcovers.core.MachinePort;
import com.rtgame.warpcovers.core.ObjectStack;
import com.rtgame.warpcovers.core.PanelEndpoint;
import com.rtgame.warpcovers.core.Viewer;

class PanelSnapshotTest {

    private static final ConfigPort CONFIG = new ConfigPort() {

        @Override
        public boolean isEnabled(ChannelType type) {
            return true;
        }

        @Override
        public long globalRateCap(ChannelType type) {
            return 0;
        }

        @Override
        public int defaultPeriodTicks() {
            return 20;
        }

        @Override
        public int minPeriodTicks() {
            return 1;
        }
    };

    private ChannelHub hub;
    private Endpoint endpoint;
    private CoverPanel panel;
    private final Viewer alice = new Viewer("uuid-alice", "Alice");
    private final Viewer bob = new Viewer("uuid-bob", "Bob");

    @BeforeEach
    void setUp() {
        hub = new ChannelHub(CONFIG);
        endpoint = new Endpoint(hub);
        panel = new CoverPanel(hub, ChannelType.FLUID, endpoint, new Random(1));
    }

    @Test
    void 频道快照经网络往返后客户端看到名称颜色归属者和自己的标记() throws IOException {
        panel.createChannel(alice, "主网", true);
        Channel other2 = hub.createChannel(ChannelType.FLUID, "副网", bob.personalOwnerId(), "Bob", 0)
            .channel();
        hub.setPublic(other2.id(), true);
        hub.createChannel(ChannelType.ITEM, "物品频道");

        PanelSnapshot.ChannelsView view = PanelSnapshot.parseChannels(roundTrip(PanelSnapshot.channels(panel, alice)));

        assertEquals(2, view.rows.size(), "只列出流体频道");
        PanelSnapshot.ChannelRow main = view.rows.get(0);
        assertEquals("主网", main.name);
        assertEquals("Alice", main.ownerName);
        assertTrue(main.mine);
        assertEquals(
            hub.findChannel(main.id)
                .color(),
            main.color);
        PanelSnapshot.ChannelRow other = view.rows.get(1);
        assertEquals("副网", other.name);
        assertFalse(other.mine);
        assertTrue(view.isConnected(main.id));
        assertFalse(view.isConnected(other.id));
        assertEquals(InteractionState.EMPTY, view.state);
    }

    @Test
    void 不能新增频道的原因随快照下发() throws IOException {
        assertNull(PanelSnapshot.parseChannels(roundTrip(PanelSnapshot.channels(panel, alice))).createBlock);
        hub.setCreateAllowed(alice.personalOwnerId(), Identity.MANAGER, false);
        assertEquals(
            CreateFailure.NO_PERMISSION,
            PanelSnapshot.parseChannels(roundTrip(PanelSnapshot.channels(panel, alice))).createBlock);
    }

    @Test
    void 配置面板内容按权限下发() throws IOException {
        panel.createChannel(alice, "主网", false);
        Channel bobs = hub.createChannel(ChannelType.FLUID, "副网", bob.personalOwnerId(), "Bob", 0x123456)
            .channel();
        hub.setPublic(bobs.id(), true);
        hub.setNote(bobs.id(), "别人的备注");

        PanelSnapshot.ChannelsView view = PanelSnapshot.parseChannels(roundTrip(PanelSnapshot.channels(panel, alice)));

        PanelSnapshot.ChannelRow mine = view.rows.get(0);
        assertTrue(mine.hasConfigPanel());
        assertTrue(mine.canConfigure);
        assertTrue(mine.canManage());
        assertTrue(mine.canDelete);
        assertTrue(mine.canEditCreate());
        assertTrue(mine.createAllowed(Identity.MEMBER));
        assertFalse(mine.createAllowed(Identity.ALLY));

        PanelSnapshot.ChannelRow other = view.row(bobs.id());
        assertEquals("别人的备注", other.note);
        assertFalse(other.hasConfigPanel(), "公开频道的陌生人没有齿轮");
        assertNull(other.permissions);
        assertFalse(other.canEditCreate());
        assertNull(view.row("没有"));
    }

    @Test
    void 分组数量标签和筛选结果随快照下发() throws IOException {
        panel.createChannel(alice, "主网", false);
        panel.createChannel(alice, "副网", false);
        String mainId = hub.channels()
            .stream()
            .filter(
                c -> c.name()
                    .equals("主网"))
            .findFirst()
            .get()
            .id();
        panel.addTag(alice, mainId, "基地");
        panel.setSearch("主");

        PanelSnapshot.ChannelsView view = PanelSnapshot.parseChannels(roundTrip(PanelSnapshot.channels(panel, alice)));

        assertEquals(2, view.rows.size(), "不符合筛选的也下发，重名预检要用");
        PanelSnapshot.ChannelRow main = view.row(mainId);
        assertTrue(main.shown);
        assertEquals(ChannelGroup.OWN, main.group);
        assertEquals(java.util.Collections.singletonList("基地"), main.tags);
        assertEquals(java.util.Collections.singletonList("基地"), view.allTags);
        assertEquals(CreateFailure.DUPLICATE_NAME, view.precheck("副网"));
        for (PanelSnapshot.ChannelRow row : view.rows) {
            if (!row.id.equals(mainId)) {
                assertFalse(row.shown);
            }
        }
    }

    @Test
    void 标签筛选按全部到各标签再回到全部循环() throws IOException {
        panel.createChannel(alice, "主网", false);
        String id = hub.channels()
            .iterator()
            .next()
            .id();
        panel.addTag(alice, id, "a");
        panel.addTag(alice, id, "b");

        PanelSnapshot.ChannelsView all = PanelSnapshot.parseChannels(roundTrip(PanelSnapshot.channels(panel, alice)));
        assertEquals("", all.tagFilter);
        assertEquals("a", all.nextTagFilter());

        panel.setTagFilter("b");
        PanelSnapshot.ChannelsView last = PanelSnapshot.parseChannels(roundTrip(PanelSnapshot.channels(panel, alice)));
        assertEquals("b", last.tagFilter);
        assertEquals("", last.nextTagFilter());
    }

    @Test
    void 最近传输记录单独下发且不含不可见频道() throws IOException {
        panel.createChannel(alice, "主网", false);
        String mine = hub.channels()
            .iterator()
            .next()
            .id();
        Channel hidden = hub.createChannel(ChannelType.FLUID, "别人的", bob.personalOwnerId(), "Bob", 0)
            .channel();
        recordTransfer(mine, "water");
        recordTransfer(mine, "lava");
        recordTransfer(hidden.id(), "oil");

        NBTTagCompound root = roundTrip(PanelSnapshot.recents(panel, alice));

        assertEquals(java.util.Arrays.asList("lava", "water"), PanelSnapshot.parseRecents(root, mine));
        assertTrue(
            PanelSnapshot.parseRecents(root, hidden.id())
                .isEmpty());
    }

    /** 走真实传输留下记录：recordTransfer 是核心包内方法，这里用输入输出两台假机器。 */
    private void recordTransfer(String channelId, String key) {
        MemoryMachine source = new MemoryMachine("src-" + key + channelId);
        source.amount = 100;
        source.key = key;
        MemoryMachine sink = new MemoryMachine("dst-" + key + channelId);
        EndpointConfig in = new EndpointConfig();
        in.setState(InteractionState.INPUT);
        in.filter()
            .add(key);
        EndpointConfig out = new EndpointConfig();
        out.setState(InteractionState.OUTPUT);
        out.filter()
            .add(key);
        String inId = "in-" + key + channelId;
        String outId = "out-" + key + channelId;
        hub.connect(channelId, inId, new Location(0, nextX++, 0, 0, 0), "", in, source, null);
        hub.connect(channelId, outId, new Location(0, nextX++, 0, 0, 0), "", out, sink, null);
        hub.driveInput(inId, 1000 + nextX);
    }

    private int nextX = 0;

    /** 最小的假机器。 */
    private static final class MemoryMachine implements MachinePort {

        private final String id;
        String key = "";
        long amount = 0;

        MemoryMachine(String id) {
            this.id = id;
        }

        @Override
        public String blockId() {
            return id;
        }

        @Override
        public java.util.List<ObjectStack> extractable() {
            return amount > 0 ? java.util.Collections.singletonList(new ObjectStack(key, amount))
                : java.util.Collections.<ObjectStack>emptyList();
        }

        @Override
        public long extract(String wanted, long max, boolean simulate) {
            long taken = wanted.equals(key) ? Math.min(amount, max) : 0;
            if (!simulate) {
                amount -= taken;
            }
            return taken;
        }

        @Override
        public long insert(String wanted, long max, boolean simulate) {
            return max;
        }
    }

    @Test
    void 交互状态随快照下发() {
        panel.createChannel(alice, "主网", true);
        panel.setState(alice, InteractionState.OUTPUT);

        assertEquals(InteractionState.OUTPUT, PanelSnapshot.parseChannels(PanelSnapshot.channels(panel, alice)).state);
    }

    @Test
    void 客户端预检只拦自己名下的重名() {
        panel.createChannel(alice, "主网", false);
        panel.createChannel(bob, "副网", false);
        PanelSnapshot.ChannelsView view = PanelSnapshot.parseChannels(PanelSnapshot.channels(panel, alice));

        assertEquals(CreateFailure.DUPLICATE_NAME, view.precheck("  主网 "));
        assertNull(view.precheck("副网"), "别人的频道可以同名");
        assertEquals(CreateFailure.EMPTY_NAME, view.precheck("   "));
        assertEquals(CreateFailure.EMPTY_NAME, view.precheck(null));
        assertEquals(CreateFailure.NAME_TOO_LONG, view.precheck(repeat('长', ChannelHub.MAX_NAME_LENGTH + 1)));
        assertNull(view.precheck(repeat('长', ChannelHub.MAX_NAME_LENGTH)));
    }

    @Test
    void 名单快照保持顺序和黑白模式() throws IOException {
        EndpointConfig config = new EndpointConfig();
        config.filter()
            .setWhitelist(false);
        config.filter()
            .replaceKeys(Arrays.asList("water", "lava", "steam"));

        PanelSnapshot.FilterView view = PanelSnapshot.parseFilter(roundTrip(PanelSnapshot.filter(config)));

        assertFalse(view.whitelist);
        assertEquals(Arrays.asList("water", "lava", "steam"), view.keys);
    }

    @Test
    void 空快照解析为默认值() {
        NBTTagCompound empty = new NBTTagCompound();
        assertTrue(PanelSnapshot.parseChannels(empty).rows.isEmpty());
        assertEquals(InteractionState.EMPTY, PanelSnapshot.parseChannels(empty).state);
        assertTrue(PanelSnapshot.parseFilter(empty).whitelist);
        assertTrue(PanelSnapshot.parseFilter(empty).keys.isEmpty());
        PanelSnapshot.StatusView status = PanelSnapshot.parseStatus(empty);
        assertEquals(EndpointStatus.UNCONNECTED, status.status);
        assertNull(status.reason);
        assertEquals("", status.noticeKey);
    }

    @Test
    void 状态快照带最近断开原因和操作反馈() throws IOException {
        endpoint.lastReason = DisconnectReason.CHANNEL_DELETED;
        panel.createChannel(alice, "", false);

        PanelSnapshot.StatusView view = PanelSnapshot.parseStatus(roundTrip(PanelSnapshot.status(panel, 100)));

        assertEquals(EndpointStatus.UNCONNECTED, view.status);
        assertEquals(DisconnectReason.CHANNEL_DELETED, view.reason);
        assertEquals("empty_name", view.noticeKey);
        assertTrue(view.noticeError);
    }

    @Test
    void 连接后状态栏反映对端情况() {
        panel.createChannel(alice, "主网", true);
        panel.setState(alice, InteractionState.INPUT);

        PanelSnapshot.StatusView view = PanelSnapshot.parseStatus(PanelSnapshot.status(panel, 100));

        assertEquals(EndpointStatus.NO_PEER, view.status);
        assertEquals("created_connected", view.noticeKey);
        assertEquals("主网", view.noticeArg);
        assertFalse(view.noticeError);
    }

    private static String repeat(char c, int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            builder.append(c);
        }
        return builder.toString();
    }

    /** 模拟网络传输：走一遍二进制 NBT 序列化。 */
    private static NBTTagCompound roundTrip(NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        return CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    /** 不依赖 GT 的最小覆盖板端：连接后在核心里登记为未加载的连接端。 */
    private static final class Endpoint implements PanelEndpoint {

        private final ChannelHub hub;
        private final EndpointConfig config = new EndpointConfig();
        private String channelId = "";
        DisconnectReason lastReason;

        Endpoint(ChannelHub hub) {
            this.hub = hub;
        }

        @Override
        public String endpointId() {
            return "ep-test";
        }

        @Override
        public String channelId() {
            return channelId;
        }

        @Override
        public EndpointConfig config() {
            return config;
        }

        @Override
        public DisconnectReason lastReason() {
            return lastReason;
        }

        @Override
        public void connect(String newChannelId, String connector) {
            config.setState(InteractionState.EMPTY);
            hub.connect(newChannelId, endpointId(), Location.NOWHERE, connector, config, null, null);
            channelId = newChannelId;
        }

        @Override
        public void disconnect() {
            hub.disconnect(endpointId());
            channelId = "";
        }

        @Override
        public void configChanged() {
            hub.configChanged(endpointId());
        }
    }
}
