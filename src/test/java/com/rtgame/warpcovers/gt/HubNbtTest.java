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
import java.util.List;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;

import org.junit.jupiter.api.Test;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelColors;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.ConfigPort;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.DistributionMode;
import com.rtgame.warpcovers.core.Endpoint;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.FilterList;
import com.rtgame.warpcovers.core.Identity;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.Location;
import com.rtgame.warpcovers.core.Permission;

/** 世界存档数据与覆盖板配置的 NBT 往返。 */
class HubNbtTest {

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

    /** 走一遍真实的二进制序列化，确认没有依赖内存对象。 */
    private static NBTTagCompound throughBytes(NBTTagCompound tag) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        CompressedStreamTools.write(tag, new DataOutputStream(bytes));
        return CompressedStreamTools.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }

    @Test
    void 频道索引与墓碑存档往返() throws IOException {
        ChannelHub hub = new ChannelHub(CONFIG);
        Channel water = hub.createChannel(ChannelType.FLUID, "水频道");
        Channel ore = hub.createChannel(ChannelType.ITEM, "矿石频道");
        EndpointConfig inputConfig = new EndpointConfig();
        inputConfig.setState(InteractionState.INPUT);
        hub.connect(water.id(), "in", new Location(0, 1, 2, 3, 4), "uuid-a", inputConfig, null, null);
        hub.connect(water.id(), "out", new Location(-1, -5, 64, 9, 2), "uuid-b", new EndpointConfig(), null, null);
        hub.connect(ore.id(), "gone", new Location(1, 0, 0, 0, 0), "uuid-c", new EndpointConfig(), null, null);
        hub.disconnect("gone", DisconnectReason.PERMISSION_LOST);

        ChannelHub restored = new ChannelHub(CONFIG);
        HubNbt.read(throughBytes(HubNbt.write(hub)), restored);

        assertEquals(
            2,
            restored.channels()
                .size());
        Channel restoredWater = restored.findChannel(water.id());
        assertEquals("水频道", restoredWater.name());
        assertEquals(ChannelType.FLUID, restoredWater.type());
        assertEquals(
            ChannelType.ITEM,
            restored.findChannel(ore.id())
                .type());

        List<Endpoint> order = restoredWater.endpoints();
        assertEquals(
            Arrays.asList("in", "out"),
            Arrays.asList(
                order.get(0)
                    .endpointId(),
                order.get(1)
                    .endpointId()));
        Endpoint in = restored.findEndpoint("in");
        assertEquals(new Location(0, 1, 2, 3, 4), in.location());
        assertEquals("uuid-a", in.connector());
        assertEquals(InteractionState.INPUT, in.state());
        assertEquals(
            new Location(-1, -5, 64, 9, 2),
            restored.findEndpoint("out")
                .location());

        assertNull(restored.findEndpoint("gone"));
        assertEquals(
            DisconnectReason.PERMISSION_LOST,
            restored.tombstones()
                .get("gone")
                .reason());
        assertEquals(hub.nextConnectOrder(), restored.nextConnectOrder());
    }

    @Test
    void 频道颜色与归属者存档往返() throws IOException {
        ChannelHub hub = new ChannelHub(CONFIG);
        Channel channel = hub.createChannel(ChannelType.FLUID, "红色频道", "player:uuid-a", "Alice", 0xB3312C)
            .channel();

        ChannelHub restored = new ChannelHub(CONFIG);
        HubNbt.read(throughBytes(HubNbt.write(hub)), restored);

        Channel back = restored.findChannel(channel.id());
        assertEquals(0xB3312C, back.color());
        assertEquals("player:uuid-a", back.ownerId());
        assertEquals("Alice", back.ownerName());
    }

    @Test
    void 分发方式存档往返且旧存档默认轮询() throws IOException {
        ChannelHub hub = new ChannelHub(CONFIG);
        Channel sequential = hub.createChannel(ChannelType.FLUID, "顺序频道");
        Channel roundRobin = hub.createChannel(ChannelType.FLUID, "轮询频道");
        hub.setDistribution(sequential.id(), DistributionMode.SEQUENTIAL);

        NBTTagCompound root = throughBytes(HubNbt.write(hub));
        ChannelHub restored = new ChannelHub(CONFIG);
        HubNbt.read(root, restored);
        assertEquals(
            DistributionMode.SEQUENTIAL,
            restored.findChannel(sequential.id())
                .distribution());
        assertEquals(
            DistributionMode.ROUND_ROBIN,
            restored.findChannel(roundRobin.id())
                .distribution());

        NBTTagList channels = root.getTagList("channels", 10);
        for (int i = 0; i < channels.tagCount(); i++) {
            channels.getCompoundTagAt(i)
                .removeTag("distribution");
        }
        ChannelHub old = new ChannelHub(CONFIG);
        HubNbt.read(root, old);
        assertEquals(
            DistributionMode.ROUND_ROBIN,
            old.findChannel(sequential.id())
                .distribution());
    }

    @Test
    void 旧存档缺颜色与归属者时取默认() {
        NBTTagCompound root = HubNbt.write(new ChannelHub(CONFIG));
        NBTTagCompound old = new NBTTagCompound();
        old.setString("id", "old");
        old.setString("type", "fluid");
        old.setString("name", "旧频道");
        root.getTagList("channels", 10)
            .appendTag(old);

        ChannelHub restored = new ChannelHub(CONFIG);
        HubNbt.read(root, restored);

        Channel back = restored.findChannel("old");
        assertEquals(ChannelColors.DEFAULT, back.color());
        assertEquals("", back.ownerId());
        assertEquals("", back.ownerName());
    }

    @Test
    void 备注权限和新增策略存档往返且旧存档取默认() throws IOException {
        ChannelHub hub = new ChannelHub(CONFIG);
        Channel channel = hub.createChannel(ChannelType.FLUID, "带备注", "player:uuid-a", "Alice", 0xB3312C)
            .channel();
        hub.setNote(channel.id(), "基地主水网");
        hub.setPublic(channel.id(), true);
        hub.setPermission(channel.id(), Identity.ALLY, Permission.CONNECT, false);
        hub.setCreateAllowed("player:uuid-a", Identity.MEMBER, false);

        NBTTagCompound root = throughBytes(HubNbt.write(hub));
        ChannelHub restored = new ChannelHub(CONFIG);
        HubNbt.read(root, restored);

        Channel back = restored.findChannel(channel.id());
        assertEquals("基地主水网", back.note());
        assertTrue(
            back.permissions()
                .isPublic());
        assertFalse(
            back.permissions()
                .allows(Identity.ALLY, Permission.CONNECT));
        assertFalse(restored.isCreateAllowed("player:uuid-a", Identity.MEMBER));

        NBTTagCompound tag = root.getTagList("channels", 10)
            .getCompoundTagAt(0);
        tag.removeTag("note");
        tag.removeTag("perms");
        root.removeTag("createPolicies");
        ChannelHub old = new ChannelHub(CONFIG);
        HubNbt.read(root, old);
        Channel oldChannel = old.findChannel(channel.id());
        assertEquals("", oldChannel.note());
        assertTrue(
            oldChannel.permissions()
                .isPrivate());
        assertTrue(old.isCreateAllowed("player:uuid-a", Identity.MEMBER));
    }

    @Test
    void 私人标签存档往返且删除的频道不恢复() throws IOException {
        ChannelHub hub = new ChannelHub(CONFIG);
        Channel channel = hub.createChannel(ChannelType.FLUID, "主网");
        Channel gone = hub.createChannel(ChannelType.FLUID, "已删");
        hub.addTag("uuid-a", channel.id(), "基地");
        hub.addTag("uuid-a", channel.id(), "矿场");
        hub.addTag("uuid-b", channel.id(), "B的");
        hub.addTag("uuid-a", gone.id(), "残留");
        hub.recentTransfers();

        NBTTagCompound root = throughBytes(HubNbt.write(hub));
        // 模拟频道条目已不在存档里、标签还在
        NBTTagList channels = root.getTagList("channels", 10);
        for (int i = channels.tagCount() - 1; i >= 0; i--) {
            if (gone.id()
                .equals(
                    channels.getCompoundTagAt(i)
                        .getString("id"))) {
                channels.removeTag(i);
            }
        }
        ChannelHub restored = new ChannelHub(CONFIG);
        HubNbt.read(root, restored);

        assertEquals(
            java.util.Arrays.asList("基地", "矿场"),
            new java.util.ArrayList<String>(
                restored.tags()
                    .of("uuid-a", channel.id())));
        assertEquals(
            java.util.Collections.singleton("B的"),
            restored.tags()
                .of("uuid-b", channel.id()));
        assertTrue(
            restored.tags()
                .of("uuid-a", gone.id())
                .isEmpty());
    }

    @Test
    void 存档带版本号() {
        NBTTagCompound root = HubNbt.write(new ChannelHub(CONFIG));
        assertEquals(HubNbt.VERSION, root.getInteger("version"));
    }

    @Test
    void 无法识别的频道类型被跳过() {
        NBTTagCompound root = HubNbt.write(new ChannelHub(CONFIG));
        NBTTagCompound bad = new NBTTagCompound();
        bad.setString("id", "x");
        bad.setString("type", "mana");
        root.getTagList("channels", 10)
            .appendTag(bad);
        ChannelHub restored = new ChannelHub(CONFIG);
        HubNbt.read(root, restored);
        assertTrue(
            restored.channels()
                .isEmpty());
    }

    @Test
    void 覆盖板配置往返且保持对象身份() throws IOException {
        EndpointConfig config = new EndpointConfig();
        config.setState(InteractionState.OUTPUT);
        config.setPriority(-3);
        config.setRate(12345678901L);
        config.setPeriod(40);

        NBTTagCompound tag = new NBTTagCompound();
        ConfigNbt.write(config, tag);
        EndpointConfig target = new EndpointConfig();
        EndpointConfig before = target;
        ConfigNbt.read(throughBytes(tag), target);

        assertTrue(before == target);
        assertEquals(config, target);
    }

    @Test
    void 缺失字段取默认配置() {
        EndpointConfig target = new EndpointConfig();
        ConfigNbt.read(new NBTTagCompound(), target);
        assertEquals(new EndpointConfig(), target);
    }

    @Test
    void 覆盖板连接端数据往返() throws IOException {
        CoverEndpoint original = new CoverEndpoint(null, new Runnable() {

            @Override
            public void run() {}
        });
        original.config()
            .setState(InteractionState.INPUT);
        NBTTagCompound tag = new NBTTagCompound();
        original.writeNbt(tag);

        CoverEndpoint copy = new CoverEndpoint(null, null);
        copy.readNbt(throughBytes(tag));
        assertEquals(original.endpointId(), copy.endpointId());
        assertEquals(original.config(), copy.config());
        assertEquals("", copy.channelId());
        assertNull(copy.lastReason());
    }

    @Test
    void 物品数据不带连接端ID() throws IOException {
        CoverEndpoint original = new CoverEndpoint(null, null);
        original.config()
            .setPriority(7);
        NBTTagCompound tag = new NBTTagCompound();
        original.writeItemNbt(tag, false);
        assertTrue(!tag.hasKey(CoverEndpoint.NBT_ENDPOINT));

        CoverEndpoint placed = new CoverEndpoint(null, null);
        String freshId = placed.endpointId();
        placed.readNbt(throughBytes(tag));
        assertEquals(freshId, placed.endpointId(), "物品里没有 ID 时保留新覆盖板自己的 ID");
        assertEquals(
            7,
            placed.config()
                .priority());
    }

    @Test
    void 配置里的旧名单字段读取时被忽略() throws IOException {
        NBTTagCompound tag = new NBTTagCompound();
        ConfigNbt.write(new EndpointConfig(), tag);
        // 旧存档里名单曾存在覆盖板配置中
        tag.setBoolean("wl", true);
        tag.setBoolean("nbt", true);
        NBTTagList old = new NBTTagList();
        old.appendTag(new NBTTagString("water"));
        tag.setTag("fl", old);

        EndpointConfig target = new EndpointConfig();
        ConfigNbt.read(throughBytes(tag), target);

        assertEquals(new EndpointConfig(), target);
    }

    @Test
    void 频道名单往返() throws IOException {
        ChannelHub hub = new ChannelHub(CONFIG);
        Channel channel = hub.createChannel(ChannelType.FLUID, "流体网");
        hub.setFilterWhitelist(channel.id(), true);
        hub.setFilterMatchNbt(channel.id(), true);
        hub.addFilterKey(channel.id(), "water");
        hub.addFilterKey(channel.id(), "lava");

        ChannelHub restored = new ChannelHub(CONFIG);
        HubNbt.read(throughBytes(HubNbt.write(hub)), restored);

        FilterList filter = restored.findChannel(channel.id())
            .filter();
        assertTrue(filter.isWhitelist());
        assertTrue(filter.matchNbt());
        assertEquals(Arrays.asList("water", "lava"), new java.util.ArrayList<String>(filter.keys()));
    }

    @Test
    void 旧存档的频道没有名单字段时读成默认黑名单() throws IOException {
        ChannelHub hub = new ChannelHub(CONFIG);
        Channel channel = hub.createChannel(ChannelType.ITEM, "物品网");
        hub.setFilterWhitelist(channel.id(), true);
        hub.addFilterKey(channel.id(), "stick");
        NBTTagCompound root = HubNbt.write(hub);
        NBTTagCompound channelTag = root.getTagList("channels", 10)
            .getCompoundTagAt(0);
        assertTrue(channelTag.hasKey("filter"));
        channelTag.removeTag("filter");

        ChannelHub restored = new ChannelHub(CONFIG);
        HubNbt.read(throughBytes(root), restored);

        FilterList filter = restored.findChannel(channel.id())
            .filter();
        assertFalse(filter.isWhitelist());
        assertFalse(filter.matchNbt());
        assertTrue(
            filter.keys()
                .isEmpty());
    }

    @Test
    void 能量频道不写名单() {
        ChannelHub hub = new ChannelHub(CONFIG);
        hub.createChannel(ChannelType.ENERGY, "电网");
        NBTTagCompound channelTag = HubNbt.write(hub)
            .getTagList("channels", 10)
            .getCompoundTagAt(0);
        assertFalse(channelTag.hasKey("filter"));
    }
}
