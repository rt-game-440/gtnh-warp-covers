package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.DistributionMode;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 分发场景：一个输入端 S；高优先级输出端 H（每周期只收 300）；同优先级的 L1、L2。
 * <ol>
 * <li>轮询：1000 里 H 先收 300，其余 700 由 L1、L2 平分</li>
 * <li>顺序：H 先收 300，L1 限 400 先填，剩下的 300 给 L2</li>
 * <li>输入端速率 100、周期 40：周期未到不运行，到期每次只搬 100</li>
 * </ol>
 * 重启后校验流体总量、分发方式和覆盖板上的优先级、速率、周期都还在。
 */
public final class DistributionScenario implements SelfCheckScenario {

    private static final String CHANNEL = "自检分发频道";
    private static final int X = 60;
    private static final int Y = 200;
    private static final int Z = 60;

    private static final int H_RATE = 300;
    private static final int H_PRIORITY = 5;
    private static final int L1_RATE = 400;
    private static final int S_RATE = 100;
    private static final int S_PERIOD = 40;

    private static final int EXPECTED_H = 300 + 300 + 200;
    private static final int EXPECTED_L1 = 350 + 400;
    private static final int EXPECTED_L2 = 350 + 300;
    private static final int TOTAL = 1000 + 1000 + 200;

    @Override
    public String name() {
        return "distribution";
    }

    private static IGregTechTileEntity placeFacingDown(WorldServer world, int x) {
        IGregTechTileEntity tank = placeTank(world, x, Y, Z);
        tank.setFrontFacing(ForgeDirection.DOWN);
        return tank;
    }

    @Override
    public void setup() {
        WorldServer world = world(0);
        IGregTechTileEntity source = placeFacingDown(world, X);
        IGregTechTileEntity high = placeFacingDown(world, X + 2);
        IGregTechTileEntity low1 = placeFacingDown(world, X + 4);
        IGregTechTileEntity low2 = placeFacingDown(world, X + 6);

        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, CHANNEL);
        Channel channel = hub.createChannel(ChannelType.FLUID, CHANNEL);
        check("新频道的分发方式应为轮询", channel.distribution() == DistributionMode.ROUND_ROBIN);

        FluidWarpCover in = attachCover(source, ForgeDirection.UP);
        FluidWarpCover coverHigh = attachCover(high, ForgeDirection.UP);
        FluidWarpCover coverLow1 = attachCover(low1, ForgeDirection.UP);
        FluidWarpCover coverLow2 = attachCover(low2, ForgeDirection.UP);
        for (FluidWarpCover cover : new FluidWarpCover[] { in, coverHigh, coverLow1, coverLow2 }) {
            cover.connect(channel.id(), "selfcheck");
        }
        in.setInteractionState(InteractionState.INPUT);
        coverHigh.setInteractionState(InteractionState.OUTPUT);
        coverLow1.setInteractionState(InteractionState.OUTPUT);
        coverLow2.setInteractionState(InteractionState.OUTPUT);
        coverHigh.config()
            .setPriority(H_PRIORITY);
        coverHigh.config()
            .setRate(H_RATE);
        coverHigh.configChanged();

        // 轮询：高优先级先收，同级平分
        pour(source, 1000);
        runCovers(0, in);
        expect("轮询后 H", 300, fluidIn(high, "water"));
        expect("轮询后 L1", 350, fluidIn(low1, "water"));
        expect("轮询后 L2", 350, fluidIn(low2, "water"));
        expect("轮询后 S", 0, fluidIn(source, "water"));

        // 顺序：高优先级先收，同级按连接顺序依次填
        check("切换分发方式", hub.setDistribution(channel.id(), DistributionMode.SEQUENTIAL));
        coverLow1.config()
            .setRate(L1_RATE);
        coverLow1.configChanged();
        pour(source, 1000);
        runCovers(20, in);
        expect("顺序后 H", 600, fluidIn(high, "water"));
        expect("顺序后 L1", 750, fluidIn(low1, "water"));
        expect("顺序后 L2", 650, fluidIn(low2, "water"));
        expect("顺序后 S", 0, fluidIn(source, "water"));

        // 输入端的速率和周期：改周期后下一次运行就按新周期判断
        in.config()
            .setRate(S_RATE);
        in.config()
            .setPeriod(S_PERIOD);
        in.configChanged();
        pour(source, 200);
        runCovers(40, in);
        expect("新周期未到时 S", 200, fluidIn(source, "water"));
        runCovers(60, in);
        expect("第一次按新周期运行后 S", 100, fluidIn(source, "water"));
        runCovers(100, in);
        expect("第二次按新周期运行后 S", 0, fluidIn(source, "water"));
        expect("H 的总量", EXPECTED_H, fluidIn(high, "water"));
        expect(
            "总量",
            TOTAL,
            fluidIn(source, "water") + fluidIn(high, "water") + fluidIn(low1, "water") + fluidIn(low2, "water"));
    }

    @Override
    public SelfCheckResult verify() {
        WorldServer world = world(0);
        IGregTechTileEntity source = tileAt(world, X, Y, Z);
        IGregTechTileEntity high = tileAt(world, X + 2, Y, Z);
        IGregTechTileEntity low1 = tileAt(world, X + 4, Y, Z);
        IGregTechTileEntity low2 = tileAt(world, X + 6, Y, Z);
        if (source == null || high == null || low1 == null || low2 == null) {
            return SelfCheckResult.fail("重启后有储罐丢失");
        }
        int h = fluidIn(high, "water");
        int l1 = fluidIn(low1, "water");
        int l2 = fluidIn(low2, "water");
        if (h != EXPECTED_H || l1 != EXPECTED_L1 || l2 != EXPECTED_L2) {
            return SelfCheckResult.fail("重启后水量不对: H=" + h + " L1=" + l1 + " L2=" + l2);
        }
        Channel channel = channelNamed(WarpServices.hub(), CHANNEL);
        if (channel.distribution() != DistributionMode.SEQUENTIAL) {
            return SelfCheckResult.fail("重启后分发方式丢失: " + channel.distribution());
        }
        EndpointConfig in = coverAt(source, ForgeDirection.UP).config();
        if (in.rate() != S_RATE || in.period() != S_PERIOD) {
            return SelfCheckResult.fail("重启后输入端速率或周期丢失: " + in.rate() + " / " + in.period());
        }
        EndpointConfig out = coverAt(high, ForgeDirection.UP).config();
        if (out.priority() != H_PRIORITY || out.rate() != H_RATE) {
            return SelfCheckResult.fail("重启后输出端优先级或速率丢失: " + out.priority() + " / " + out.rate());
        }
        return SelfCheckResult.pass("优先级、轮询、顺序、速率、周期均符合预期，重启后配置与水量都在");
    }
}
