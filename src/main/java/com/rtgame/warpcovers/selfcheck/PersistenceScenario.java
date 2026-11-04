package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.Endpoint;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.Location;
import com.rtgame.warpcovers.gt.ConfigNbt;
import com.rtgame.warpcovers.gt.CoverEndpoint;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.WarpCoverItem;
import com.rtgame.warpcovers.gt.WarpEvents;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.covers.CoverRegistry;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 持久化与物品化（#4）：
 * <ol>
 * <li>重启：主世界输入 → 下界输出，重启后频道、索引、覆盖板配置都在，并且继续传输</li>
 * <li>区块卸载再加载：远处区块里的一对连接端在区块存盘卸载、重新加载后保持同一连接端 ID 并继续传输；
 * 区块加载时清理掉该区块中找不到覆盖板的残留索引与墓碑</li>
 * <li>挖机器：覆盖板随机器物品带走全部配置，放到别处后以新连接端 ID 重新接入频道并继续传输</li>
 * <li>撬下：服务器端立即从索引移除并发出标准同步，掉落物带配置和提示；单独合成得到空白覆盖板；
 * 重贴后恢复配置、新连接端 ID、贴者为连接者、排在连接顺序末尾</li>
 * </ol>
 */
public final class PersistenceScenario implements SelfCheckScenario {

    private static final String CHANNEL_RESTART = "自检-重启持久化";
    private static final String CHANNEL_CHUNK = "自检-区块重载";
    private static final String CHANNEL_DIG = "自检-挖机器";
    private static final String CHANNEL_PRY = "自检-撬下重贴";

    private static final int Y = 200;
    private static final int NETHER_Y = 64;
    private static final int Z = 20;
    /** 重启：主世界 A 输入、下界 B 输出。 */
    private static final int A_X = 40;
    /** 挖机器：H 输入；G 输出被挖起来放到 G2。 */
    private static final int H_X = 44;
    private static final int G_X = 46;
    private static final int G2_X = 48;
    /** 撬下：L 输入；K 输出被撬下再贴回。 */
    private static final int L_X = 52;
    private static final int K_X = 54;
    /** 区块重载：远离出生点、同一区块内的 E 输入和 D 输出。 */
    private static final int FAR = 2000;
    private static final int E_X = FAR;
    private static final int D_X = FAR + 2;
    private static final String RESIDUE_ID = "selfcheck-residue";
    private static final String TOMBSTONE_ID = "selfcheck-tombstone";

    private static final int UP = ForgeDirection.UP.ordinal();

    /** 手动驱动覆盖板用的时钟：远大于服务器 tick 计数，保证每次都到期。 */
    private long now;

    @Override
    public String name() {
        return "persistence";
    }

    // ---- 布置 ----

    @Override
    public void setup() {
        ChannelHub hub = WarpServices.hub();
        for (String name : Arrays.asList(CHANNEL_RESTART, CHANNEL_CHUNK, CHANNEL_DIG, CHANNEL_PRY)) {
            deleteChannelsNamed(hub, name);
        }
        now = 1_000_000_000L;
        setupRestart(hub);
        setupChunkReload(hub);
        setupDig(hub);
        setupPry(hub);
    }

    private void setupRestart(ChannelHub hub) {
        IGregTechTileEntity a = placeTank(world(0), A_X, Y, Z);
        IGregTechTileEntity b = placeTank(world(-1), A_X, NETHER_Y, Z);
        Channel channel = hub.createChannel(ChannelType.FLUID, CHANNEL_RESTART);
        // 名单属于频道：白名单放行水和岩浆
        setFilter(hub, channel, true, false, "water", "lava");
        FluidWarpCover in = connected(hub, a, channel, InteractionState.INPUT);
        in.config()
            .setPriority(7);
        hub.configChanged(in.endpointId());
        FluidWarpCover out = connected(hub, b, channel, InteractionState.OUTPUT);

        pour(a, 1000);
        run(out, in);
        expect("重启场景：A 里的水", 0, fluidIn(a, "water"));
        expect("重启场景：B（下界）里的水", 1000, fluidIn(b, "water"));
    }

    private void setupChunkReload(ChannelHub hub) {
        WorldServer overworld = world(0);
        IGregTechTileEntity e = placeTank(overworld, E_X, Y, FAR);
        IGregTechTileEntity d = placeTank(overworld, D_X, Y, FAR);
        Channel channel = hub.createChannel(ChannelType.FLUID, CHANNEL_CHUNK);
        FluidWarpCover in = connected(hub, e, channel, InteractionState.INPUT);
        FluidWarpCover out = connected(hub, d, channel, InteractionState.OUTPUT);
        pour(e, 1000);
        run(out, in);
        expect("区块场景：卸载前 D 里的水", 1000, fluidIn(d, "water"));

        // 残留：同一区块里一条没有覆盖板的索引条目，和一条没有覆盖板的墓碑
        hub.connect(
            channel.id(),
            RESIDUE_ID,
            new Location(0, FAR + 4, Y, FAR, UP),
            "selfcheck",
            new EndpointConfig(),
            null,
            null);
        hub.connect(
            channel.id(),
            TOMBSTONE_ID,
            new Location(0, FAR + 6, Y, FAR, UP),
            "selfcheck",
            new EndpointConfig(),
            null,
            null);
        hub.disconnect(TOMBSTONE_ID, DisconnectReason.PERMISSION_LOST);
        check("残留条目没写进索引", hub.findEndpoint(RESIDUE_ID) != null);
        check(
            "墓碑没留下",
            hub.tombstones()
                .containsKey(TOMBSTONE_ID));

        String inId = in.endpointId();
        String outId = out.endpointId();
        for (int i = 0; i < 50 && chunkLoaded(overworld, FAR, FAR); i++) {
            unloadChunk(overworld, FAR, FAR);
        }
        if (chunkLoaded(overworld, FAR, FAR)) {
            throw new IllegalStateException("区块没能卸载: " + chunkUnloadDiagnostics(overworld, FAR, FAR));
        }

        IGregTechTileEntity e2 = requireTile(overworld, E_X, Y, FAR);
        IGregTechTileEntity d2 = requireTile(overworld, D_X, Y, FAR);
        check("重新加载后方块实体应是新对象", e2 != e && d2 != d);
        warmUp(overworld, E_X, Y, FAR);
        warmUp(overworld, D_X, Y, FAR);
        FluidWarpCover in2 = coverAt(e2, ForgeDirection.UP);
        FluidWarpCover out2 = coverAt(d2, ForgeDirection.UP);
        check("重新加载后输入端 ID 变了", inId.equals(in2.endpointId()));
        check("重新加载后输出端 ID 变了", outId.equals(out2.endpointId()));

        pour(e2, 1000);
        run(out2, in2);
        expect("区块场景：重新加载后 D 里的水", 2000, fluidIn(d2, "water"));
        expect("区块场景：重新加载后 E 里的水", 0, fluidIn(e2, "water"));
        check("输入端没有绑定到新的覆盖板", hub.isBoundTo(inId, in2.endpoint()));
        check("输出端没有绑定到新的覆盖板", hub.isBoundTo(outId, out2.endpoint()));

        int removed = WarpEvents.drainPendingChunks();
        check("区块加载后残留索引条目没被清理（清理数=" + removed + "）", hub.findEndpoint(RESIDUE_ID) == null);
        check(
            "区块加载后残留墓碑没被清理（清理数=" + removed + "）",
            !hub.tombstones()
                .containsKey(TOMBSTONE_ID));
        check("区块清理误删了真实连接端", hub.findEndpoint(inId) != null && hub.findEndpoint(outId) != null);
    }

    private void setupDig(ChannelHub hub) {
        WorldServer overworld = world(0);
        IGregTechTileEntity h = placeTank(overworld, H_X, Y, Z);
        IGregTechTileEntity g = placeTank(overworld, G_X, Y, Z);
        Channel channel = hub.createChannel(ChannelType.FLUID, CHANNEL_DIG);
        FluidWarpCover in = connected(hub, h, channel, InteractionState.INPUT);
        FluidWarpCover out = connected(hub, g, channel, InteractionState.OUTPUT);
        out.config()
            .setPriority(3);
        hub.configChanged(out.endpointId());
        EndpointConfig before = out.config()
            .copy();
        String oldId = out.endpointId();

        ItemStack machine = digMachine(overworld, G_X, Y, Z);
        check("挖掉机器后索引里仍有该连接端", hub.findEndpoint(oldId) == null);
        check(
            "机器物品里没有覆盖板数据",
            machine.hasTagCompound() && machine.getTagCompound()
                .hasKey("gt.covers"));

        IGregTechTileEntity g2 = placeMachine(overworld, G2_X, Y, Z, machine);
        g2.setFrontFacing(ForgeDirection.DOWN);
        FluidWarpCover out2 = coverAt(g2, ForgeDirection.UP);
        check("放回后覆盖板配置不对: " + out2.config(), before.equals(out2.config()));
        check(
            "放回后覆盖板不再记得频道",
            channel.id()
                .equals(out2.channelId()));

        run(out2);
        check("放回后应分配新的连接端 ID", !oldId.equals(out2.endpointId()));
        Endpoint entry = hub.findEndpoint(out2.endpointId());
        check("放回后没有重新接入频道", entry != null);
        check("放回后索引位置不对: " + entry.location(), new Location(0, G2_X, Y, Z, UP).equals(entry.location()));
        check(
            "放回后应排在连接顺序末尾",
            entry.connectOrder() > hub.findEndpoint(in.endpointId())
                .connectOrder());

        pour(h, 1000);
        run(in);
        expect("挖机器场景：放回的输出端收到的水", 1000, fluidIn(g2, "water"));
    }

    private void setupPry(ChannelHub hub) {
        WorldServer overworld = world(0);
        IGregTechTileEntity l = placeTank(overworld, L_X, Y, Z);
        IGregTechTileEntity k = placeTank(overworld, K_X, Y, Z);
        Channel channel = hub.createChannel(ChannelType.FLUID, CHANNEL_PRY);
        // 黑名单：挡住岩浆，水照常通过
        setFilter(hub, channel, false, false, "lava");
        FluidWarpCover in = connected(hub, l, channel, InteractionState.INPUT);
        FluidWarpCover out = connected(hub, k, channel, InteractionState.OUTPUT);
        out.config()
            .setPriority(-2);
        hub.configChanged(out.endpointId());
        EndpointConfig before = out.config()
            .copy();
        String oldId = out.endpointId();

        setBooleanField(k, "mNeedsTileUpdate", false);
        ItemStack drop = k.detachCover(ForgeDirection.UP);
        check("撬下后服务器端索引里仍有该连接端", hub.findEndpoint(oldId) == null);
        check("撬下后该面仍有折跃覆盖板", !(k.getCoverAtSide(ForgeDirection.UP) instanceof FluidWarpCover));
        check("撬下后没有发出 GT 标准的方块同步", booleanField(k, "mNeedsTileUpdate"));

        NBTTagCompound tag = WarpCoverItem.configOf(drop);
        check("撬下的物品没有带配置", tag != null);
        check(
            "撬下的物品没有记住频道",
            channel.id()
                .equals(tag.getString(CoverEndpoint.NBT_CHANNEL)));
        check("撬下的物品不应带连接端 ID", !tag.hasKey(CoverEndpoint.NBT_ENDPOINT));
        EndpointConfig carried = new EndpointConfig();
        ConfigNbt.read(tag, carried);
        check("撬下的物品配置不对: " + carried, before.equals(carried));
        List<String> lines = tooltip(drop, fakePlayer(overworld));
        check("物品提示里没有频道名: " + lines, containsText(lines, CHANNEL_PRY));

        ItemStack cleared = craftAlone(drop, overworld);
        check(
            "单独合成没有得到空白覆盖板: " + cleared,
            cleared != null && cleared.getItem() == drop.getItem() && WarpCoverItem.configOf(cleared) == null);
        check("合成清空不应改动原物品", WarpCoverItem.configOf(drop) != null);

        FakePlayer player = fakePlayer(overworld);
        CoverRegistry.getCoverPlacer(drop)
            .placeCover(player, drop, k, ForgeDirection.UP);
        FluidWarpCover out2 = coverAt(k, ForgeDirection.UP);
        check("重贴后配置不对: " + out2.config(), before.equals(out2.config()));
        check("重贴后应分配新的连接端 ID", !oldId.equals(out2.endpointId()));
        Endpoint entry = hub.findEndpoint(out2.endpointId());
        check(
            "重贴后没有重新接入频道",
            entry != null && channel.id()
                .equals(entry.channelId()));
        check(
            "重贴后连接者应是贴者: " + entry.connector(),
            player.getUniqueID()
                .toString()
                .equals(entry.connector()));
        List<Endpoint> order = channel.endpoints();
        check(
            "重贴后应排在连接顺序末尾",
            order.get(order.size() - 1)
                .endpointId()
                .equals(out2.endpointId()));

        pour(l, 1000);
        run(in);
        expect("撬下场景：重贴的输出端收到的水", 1000, fluidIn(k, "water"));
    }

    // ---- 校验 ----

    @Override
    public SelfCheckResult verify() {
        now = 2_000_000_000L;
        ChannelHub hub = WarpServices.hub();
        try {
            verifyRestart(hub);
            verifyChunkReload(hub);
            verifyDig(hub);
            verifyPry(hub);
        } catch (IllegalStateException e) {
            return SelfCheckResult.fail(e.getMessage());
        }
        return SelfCheckResult.pass("重启、区块重载、挖机器、撬下重贴后频道与配置均保留并继续传输");
    }

    private void verifyRestart(ChannelHub hub) {
        Channel channel = channelNamed(hub, CHANNEL_RESTART);
        IGregTechTileEntity a = requireTile(world(0), A_X, Y, Z);
        IGregTechTileEntity b = requireTile(world(-1), A_X, NETHER_Y, Z);
        // GT 机器刚从存档读出来时 tick 计数为 0，灌入/抽出要等几 tick 后才接受
        warmUp(world(0), A_X, Y, Z);
        warmUp(world(-1), A_X, NETHER_Y, Z);
        FluidWarpCover in = coverAt(a, ForgeDirection.UP);
        FluidWarpCover out = coverAt(b, ForgeDirection.UP);
        check(
            "重启后 A 不再记得频道",
            channel.id()
                .equals(in.channelId()));
        check(
            "重启后 A 的配置不对: " + in.config(),
            in.state() == InteractionState.INPUT && in.config()
                .priority() == 7
                && channel.filter()
                    .isWhitelist()
                && channel.filter()
                    .keys()
                    .containsAll(Arrays.asList("water", "lava")));
        check("重启后索引里没有 A", hub.findEndpoint(in.endpointId()) != null);
        check("重启后索引里没有 B", hub.findEndpoint(out.endpointId()) != null);
        expect("重启后 B 里的水", 1000, fluidIn(b, "water"));

        pour(a, 1000);
        run(out, in);
        expect("重启后继续传输：B 里的水", 2000, fluidIn(b, "water"));
        expect("重启后继续传输：A 里的水", 0, fluidIn(a, "water"));
    }

    private void verifyChunkReload(ChannelHub hub) {
        channelNamed(hub, CHANNEL_CHUNK);
        IGregTechTileEntity e = requireTile(world(0), E_X, Y, FAR);
        IGregTechTileEntity d = requireTile(world(0), D_X, Y, FAR);
        FluidWarpCover in = coverAt(e, ForgeDirection.UP);
        FluidWarpCover out = coverAt(d, ForgeDirection.UP);
        check("区块场景重启后索引里没有输入端", hub.findEndpoint(in.endpointId()) != null);
        check("区块场景重启后索引里没有输出端", hub.findEndpoint(out.endpointId()) != null);
        check("区块场景的残留条目在重启后又出现了", hub.findEndpoint(RESIDUE_ID) == null);
        check(
            "区块场景的残留墓碑在重启后又出现了",
            !hub.tombstones()
                .containsKey(TOMBSTONE_ID));
        expect("区块场景重启后 D 里的水", 2000, fluidIn(d, "water"));
    }

    private void verifyDig(ChannelHub hub) {
        Channel channel = channelNamed(hub, CHANNEL_DIG);
        check("挖走的机器位置上还有 GT 机器", tileAt(world(0), G_X, Y, Z) == null);
        IGregTechTileEntity g2 = requireTile(world(0), G2_X, Y, Z);
        FluidWarpCover out = coverAt(g2, ForgeDirection.UP);
        check(
            "挖机器场景重启后输出端不再记得频道",
            channel.id()
                .equals(out.channelId()));
        check(
            "挖机器场景重启后配置不对: " + out.config(),
            out.state() == InteractionState.OUTPUT && out.config()
                .priority() == 3);
        Endpoint entry = hub.findEndpoint(out.endpointId());
        check("挖机器场景重启后索引不对", entry != null && new Location(0, G2_X, Y, Z, UP).equals(entry.location()));
        for (Endpoint other : hub.endpoints()) {
            check("挖走的机器位置仍有索引条目", !new Location(0, G_X, Y, Z, UP).equals(other.location()));
        }
        expect("挖机器场景重启后 G2 里的水", 1000, fluidIn(g2, "water"));
    }

    private void verifyPry(ChannelHub hub) {
        Channel channel = channelNamed(hub, CHANNEL_PRY);
        IGregTechTileEntity k = requireTile(world(0), K_X, Y, Z);
        FluidWarpCover out = coverAt(k, ForgeDirection.UP);
        Endpoint entry = hub.findEndpoint(out.endpointId());
        check(
            "撬下场景重启后索引里没有重贴的输出端",
            entry != null && channel.id()
                .equals(entry.channelId()));
        check(
            "撬下场景重启后连接者不对: " + entry.connector(),
            fakePlayer(world(0)).getUniqueID()
                .toString()
                .equals(entry.connector()));
        check(
            "撬下场景重启后配置不对: " + out.config(),
            out.config()
                .priority() == -2
                && !channel.filter()
                    .isWhitelist()
                && channel.filter()
                    .keys()
                    .contains("lava")
                && !channel.filter()
                    .keys()
                    .contains("water"));
        expect("撬下场景重启后 K 里的水", 1000, fluidIn(k, "water"));
    }

    // ---- 辅助 ----

    /** 贴一块覆盖板，接入频道，设交互状态；顶面贴，出口面朝下。名单在频道上，默认放行一切。 */
    private static FluidWarpCover connected(ChannelHub hub, IGregTechTileEntity tank, Channel channel,
        InteractionState state) {
        tank.setFrontFacing(ForgeDirection.DOWN);
        FluidWarpCover cover = attachCover(tank, ForgeDirection.UP);
        cover.connect(channel.id(), "selfcheck");
        cover.setInteractionState(state);
        hub.configChanged(cover.endpointId());
        return cover;
    }

    /** 依次驱动覆盖板（输出端先对账绑定，输入端再传输）。每次调用推进时钟，保证周期到期。 */
    private void run(FluidWarpCover... covers) {
        now += 1000;
        runCovers(now, covers);
    }

    @SuppressWarnings("unchecked")
    private static List<String> tooltip(ItemStack stack, EntityPlayer player) {
        List<String> lines = new ArrayList<String>();
        ((WarpCoverItem) stack.getItem()).addInformation(stack, player, lines, false);
        return lines;
    }

    private static boolean containsText(List<String> lines, String text) {
        for (String line : lines) {
            if (line.contains(text)) {
                return true;
            }
        }
        return false;
    }

    /** 把物品单独放进 3x3 合成格，返回合成结果。 */
    private static ItemStack craftAlone(ItemStack stack, WorldServer world) {
        InventoryCrafting grid = new InventoryCrafting(new Container() {

            @Override
            public boolean canInteractWith(EntityPlayer player) {
                return true;
            }
        }, 3, 3);
        grid.setInventorySlotContents(4, stack.copy());
        return CraftingManager.getInstance()
            .findMatchingRecipe(grid, world);
    }
}
