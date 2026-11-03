package com.rtgame.warpcovers.selfcheck;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.ModItems;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.covers.Cover;
import gregtech.api.covers.CoverRegistry;

/**
 * 流体频道直通场景：主世界与下界各放储罐，贴上流体折跃覆盖板，建频道、设输入输出、加白名单，
 * 跑若干个运行周期后检查转移量与总量守恒；服务器重启后再检查罐内流体与覆盖板数据都还在。
 * <p>
 * 布置：
 * <ul>
 * <li>A（主世界）：水罐，顶面贴输入端，白名单 {水}；下面再贴一块输出端，用来验证「同一方块自环被跳过」</li>
 * <li>A2（主世界）：蒸馏水罐，顶面贴输入端，白名单 {水}，蒸馏水被名单挡住，不应被抽走</li>
 * <li>C（主世界）：顶面（=它的出口面）贴输出端，GT 规则不允许从出口面灌入，应收不到流体</li>
 * <li>B（下界）：空罐，顶面贴输出端，白名单 {水}，应收到全部水</li>
 * </ul>
 */
public final class FluidChannelScenario implements SelfCheckScenario {

    private static final int X = 20;
    private static final int Z = 20;
    private static final int OVERWORLD_Y = 200;
    private static final int NETHER_Y = 64;
    private static final int OTHER_AMOUNT = 2000;
    /** GT 储罐会拒绝高温流体（岩浆），所以用 IC2 蒸馏水当「被名单挡住的另一种流体」。 */
    private static final String OTHER_FLUID = "ic2distilledwater";
    /** 四个周期每个周期注入 1000，第一个周期额外多注入 500（用来验证周期未到不运行）。 */
    private static final int EXPECTED_WATER = 4 * 1000 + 500;

    @Override
    public String name() {
        return "fluid_channel";
    }

    @Override
    public void setup() {
        WorldServer overworld = world(0);
        WorldServer nether = world(-1);
        IGregTechTileEntity tankA = placeTank(overworld, X, OVERWORLD_Y, Z);
        IGregTechTileEntity tankA2 = placeTank(overworld, X + 2, OVERWORLD_Y, Z);
        IGregTechTileEntity tankC = placeTank(overworld, X + 4, OVERWORLD_Y, Z);
        IGregTechTileEntity tankB = placeTank(nether, X, NETHER_Y, Z);

        // 出口面朝下：顶面可以灌入也可以抽出；C 的出口面朝上，顶面不允许灌入
        tankA.setFrontFacing(ForgeDirection.DOWN);
        tankA2.setFrontFacing(ForgeDirection.DOWN);
        tankC.setFrontFacing(ForgeDirection.UP);
        tankB.setFrontFacing(ForgeDirection.DOWN);

        FluidStack lava = new FluidStack(FluidRegistry.getFluid(OTHER_FLUID), OTHER_AMOUNT);
        if (tankA2.fill(ForgeDirection.UP, lava, true) != OTHER_AMOUNT) {
            throw new IllegalStateException("无法向 A2 灌入蒸馏水");
        }

        ChannelHub hub = WarpServices.hub();
        Channel channel = hub.createChannel(ChannelType.FLUID, "自检频道");

        FluidWarpCover inputA = attachCover(tankA, ForgeDirection.UP);
        FluidWarpCover selfOutA = attachCover(tankA, ForgeDirection.DOWN);
        FluidWarpCover inputA2 = attachCover(tankA2, ForgeDirection.UP);
        FluidWarpCover outputC = attachCover(tankC, ForgeDirection.UP);
        FluidWarpCover outputB = attachCover(tankB, ForgeDirection.UP);

        // 新建覆盖板的名单是空白名单；先确认这一点再逐个加白名单
        if (!inputA.filter()
            .isWhitelist()
            || !inputA.filter()
                .keys()
                .isEmpty()) {
            throw new IllegalStateException("新覆盖板的过滤名单不是空白名单");
        }
        // 连接顺序：C 在 B 之前，如果无视 GT 面规则，水会先流进 C
        for (FluidWarpCover cover : new FluidWarpCover[] { inputA, selfOutA, inputA2, outputC, outputB }) {
            cover.connect(channel.id());
            if (cover.state() != InteractionState.EMPTY) {
                throw new IllegalStateException("新连接的交互状态不是空");
            }
            cover.filter()
                .add("water");
        }
        inputA.setInteractionState(InteractionState.INPUT);
        inputA2.setInteractionState(InteractionState.INPUT);
        selfOutA.setInteractionState(InteractionState.OUTPUT);
        outputC.setInteractionState(InteractionState.OUTPUT);
        outputB.setInteractionState(InteractionState.OUTPUT);

        long totalWater = 0;
        // 第一个周期：注入 1000 后在 t=0 运行
        totalWater += pour(tankA, 1000);
        runCovers(0, inputA, inputA2);
        expect("第 1 周期后 A 里的水", 0, fluidIn(tankA, "water"));
        expect("第 1 周期后 B 里的水", 1000, fluidIn(tankB, "water"));
        // 周期未到（t=10 < 20）：再注入 500 也不运行
        totalWater += pour(tankA, 500);
        runCovers(10, inputA, inputA2);
        expect("周期未到时 A 里的水", 500, fluidIn(tankA, "water"));
        // t=20 周期到：补上那 500
        runCovers(20, inputA, inputA2);
        expect("第 2 周期后 A 里的水", 0, fluidIn(tankA, "water"));
        // 之后每个周期注入 1000，共 3 个周期
        for (int period = 2; period <= 4; period++) {
            totalWater += pour(tankA, 1000);
            runCovers(period * 20L, inputA, inputA2);
            expect("第 " + (period + 1) + " 周期后 A 里的水", 0, fluidIn(tankA, "water"));
        }
        expect("注入总量", EXPECTED_WATER, totalWater);

        expect("A 里的水", 0, fluidIn(tankA, "water"));
        expect("B（下界）里的水", EXPECTED_WATER, fluidIn(tankB, "water"));
        expect("C（出口面）里的流体", 0, totalFluid(tankC));
        expect("A2 里的蒸馏水（被名单挡住）", OTHER_AMOUNT, fluidIn(tankA2, OTHER_FLUID));
        expect("A2 里的水", 0, fluidIn(tankA2, "water"));
        expect("总量（水）", EXPECTED_WATER, fluidIn(tankA, "water") + fluidIn(tankB, "water") + fluidIn(tankC, "water"));
    }

    @Override
    public SelfCheckResult verify() {
        WorldServer overworld = world(0);
        WorldServer nether = world(-1);
        IGregTechTileEntity tankA = tileAt(overworld, X, OVERWORLD_Y, Z);
        IGregTechTileEntity tankA2 = tileAt(overworld, X + 2, OVERWORLD_Y, Z);
        IGregTechTileEntity tankC = tileAt(overworld, X + 4, OVERWORLD_Y, Z);
        IGregTechTileEntity tankB = tileAt(nether, X, NETHER_Y, Z);
        if (tankA == null || tankA2 == null || tankC == null || tankB == null) {
            return SelfCheckResult.fail("重启后有储罐丢失");
        }
        long water = fluidIn(tankA, "water") + fluidIn(tankB, "water") + fluidIn(tankC, "water");
        if (fluidIn(tankB, "water") != EXPECTED_WATER || water != EXPECTED_WATER) {
            return SelfCheckResult
                .fail("重启后水量不对: B=" + fluidIn(tankB, "water") + " 总计=" + water + " 期望=" + EXPECTED_WATER);
        }
        if (fluidIn(tankA2, OTHER_FLUID) != OTHER_AMOUNT) {
            return SelfCheckResult.fail("重启后 A2 的蒸馏水不对: " + fluidIn(tankA2, OTHER_FLUID));
        }
        Cover cover = tankA.getCoverAtSide(ForgeDirection.UP);
        if (!(cover instanceof FluidWarpCover)) {
            return SelfCheckResult.fail("重启后 A 顶面没有流体折跃覆盖板");
        }
        FluidWarpCover input = (FluidWarpCover) cover;
        if (input.state() != InteractionState.INPUT || !input.filter()
            .isWhitelist()
            || !input.filter()
                .keys()
                .contains("water")
            || input.channelId()
                .isEmpty()) {
            return SelfCheckResult.fail("重启后覆盖板数据丢失: 状态=" + input.state() + " 名单=" + input.filter().keys());
        }
        Cover out = tankB.getCoverAtSide(ForgeDirection.UP);
        if (!(out instanceof FluidWarpCover) || ((FluidWarpCover) out).state() != InteractionState.OUTPUT) {
            return SelfCheckResult.fail("重启后 B 顶面的输出端覆盖板数据丢失");
        }
        return SelfCheckResult.pass("水量 " + EXPECTED_WATER + " 守恒，覆盖板数据在重启后依然存在");
    }

    // ---- 辅助 ----

    private static WorldServer world(int dimension) {
        return MinecraftServer.getServer()
            .worldServerForDimension(dimension);
    }

    private static IGregTechTileEntity placeTank(World world, int x, int y, int z) {
        loadChunk(world, x, z);
        world.setBlockToAir(x, y, z);
        ItemStack stack = ItemList.Super_Tank_LV.get(1);
        boolean placed;
        try {
            placed = ((ItemBlock) stack.getItem()).placeBlockAt(
                stack,
                FakePlayerFactory.getMinecraft((WorldServer) world),
                world,
                x,
                y,
                z,
                1,
                0.5F,
                0.5F,
                0.5F,
                stack.getItemDamage());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "储罐放置异常: dim=" + world.provider.dimensionId + " " + x + "," + y + "," + z
                    + " 现有方块="
                    + world.getBlock(x, y, z)
                    + " 物品="
                    + stack.getItem(),
                e);
        }
        IGregTechTileEntity tile = placed ? tileAt(world, x, y, z) : null;
        if (tile == null) {
            throw new IllegalStateException("储罐放置失败: dim=" + world.provider.dimensionId + " " + x + "," + y + "," + z);
        }
        warmUp(world, x, y, z);
        return tile;
    }

    /** GT 机器刚放下时 mTickTimer 过小会拒绝流体读写（要求大于 5），手动推进几次更新。 */
    private static void warmUp(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        for (int i = 0; i < 8; i++) {
            tile.updateEntity();
        }
    }

    /** 显式加载区块：非主世界的 provideChunk 可能返回空区块，loadChunk 总是真正加载。 */
    private static void loadChunk(World world, int x, int z) {
        ((WorldServer) world).theChunkProviderServer.loadChunk(x >> 4, z >> 4);
    }

    private static IGregTechTileEntity tileAt(World world, int x, int y, int z) {
        loadChunk(world, x, z);
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof IGregTechTileEntity ? (IGregTechTileEntity) tile : null;
    }

    private static FluidWarpCover attachCover(IGregTechTileEntity tile, ForgeDirection side) {
        Cover cover = CoverRegistry.buildCover(new ItemStack(ModItems.fluidCover), side, tile);
        tile.attachCover(cover);
        Cover attached = tile.getCoverAtSide(side);
        if (!(attached instanceof FluidWarpCover)) {
            throw new IllegalStateException("覆盖板贴上失败: " + attached);
        }
        return (FluidWarpCover) attached;
    }

    /** 从顶面向 A 注入水，返回实际注入量。 */
    private static int pour(IGregTechTileEntity tank, int amount) {
        int filled = tank.fill(ForgeDirection.UP, new FluidStack(FluidRegistry.WATER, amount), true);
        if (filled != amount) {
            throw new IllegalStateException("注入水失败: 期望 " + amount + " 实际 " + filled);
        }
        return filled;
    }

    private static void runCovers(long now, FluidWarpCover... covers) {
        for (FluidWarpCover cover : covers) {
            cover.doCoverThings((byte) 0, now);
        }
    }

    private static int fluidIn(IGregTechTileEntity tank, String name) {
        FluidStack stack = tank.getTankInfo(ForgeDirection.UP)[0].fluid;
        return stack != null && name.equals(FluidRegistry.getFluidName(stack)) ? stack.amount : 0;
    }

    private static int totalFluid(IGregTechTileEntity tank) {
        FluidStack stack = tank.getTankInfo(ForgeDirection.UP)[0].fluid;
        return stack == null ? 0 : stack.amount;
    }

    private static void expect(String what, long expected, long actual) {
        if (expected != actual) {
            throw new IllegalStateException(what + " 不对: 期望 " + expected + " 实际 " + actual);
        }
    }
}
