package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.gt.CoverVisual;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 覆盖板外观（#12）：连接、切换交互状态、频道改色、断开时，真实覆盖板都标记待同步且算出的外观正确；
 * 重启后外观仍能从频道算出。贴图本身要目视检查，这里不覆盖。
 */
public final class CoverAppearanceScenario implements SelfCheckScenario {

    private static final int X = 140;
    private static final int Y = 200;
    private static final int Z = 100;
    private static final String NAME = "自检外观";
    private static final String OWNER = "00000000-0000-4000-8000-00000000d001";
    private static final int COLOR = 0x123456;
    private static final int NEW_COLOR = 0xABCDEF;

    @Override
    public String name() {
        return "cover_appearance";
    }

    @Override
    public void setup() {
        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, NAME);
        Channel channel = hub.createChannel(ChannelType.FLUID, NAME, "player:" + OWNER, "owner", COLOR)
            .channel();

        FluidWarpCover first = attach(X);
        FluidWarpCover second = attach(X + 2);
        check(
            "未连接时印记为灰色",
            first.currentVisual()
                .markColor() == CoverVisual.UNCONNECTED_COLOR);
        check(
            "未连接时没有箭头",
            first.currentVisual()
                .arrow() == CoverVisual.Arrow.NONE);

        first.setNeedsUpdate(false);
        first.connect(channel.id(), OWNER);
        check("连接后应标记同步", first.needsUpdate());
        check(
            "连接后印记为频道颜色",
            first.currentVisual()
                .markColor() == COLOR);
        second.connect(channel.id(), OWNER);

        first.setNeedsUpdate(false);
        first.setInteractionState(InteractionState.INPUT);
        check("改交互状态后应标记同步", first.needsUpdate());
        check(
            "输入端箭头向外",
            first.currentVisual()
                .arrow() == CoverVisual.Arrow.OUTWARD);
        second.setInteractionState(InteractionState.OUTPUT);
        check(
            "输出端箭头向内",
            second.currentVisual()
                .arrow() == CoverVisual.Arrow.INWARD);

        first.setNeedsUpdate(false);
        second.setNeedsUpdate(false);
        hub.setColor(channel.id(), NEW_COLOR);
        check("改色后第一个连接端应标记同步", first.needsUpdate());
        check("改色后第二个连接端应标记同步", second.needsUpdate());
        check(
            "改色后第一个连接端外观更新",
            first.currentVisual()
                .markColor() == NEW_COLOR);
        check(
            "改色后第二个连接端外观更新",
            second.currentVisual()
                .markColor() == NEW_COLOR);
        check("WAILA 数据带频道名", NAME.equals(first.currentVisual().channelName));

        // 第三个覆盖板用来校验断开
        FluidWarpCover third = attach(X + 4);
        third.connect(channel.id(), OWNER);
        third.setNeedsUpdate(false);
        third.disconnect();
        check("断开后应标记同步", third.needsUpdate());
        check(
            "断开后印记回到灰色",
            third.currentVisual()
                .markColor() == CoverVisual.UNCONNECTED_COLOR);
    }

    private static FluidWarpCover attach(int x) {
        IGregTechTileEntity tank = placeTank(world(0), x, Y, Z);
        tank.setFrontFacing(ForgeDirection.DOWN);
        return attachCover(tank, ForgeDirection.UP);
    }

    @Override
    public SelfCheckResult verify() {
        FluidWarpCover first = coverAt(requireTile(world(0), X, Y, Z), ForgeDirection.UP);
        FluidWarpCover second = coverAt(requireTile(world(0), X + 2, Y, Z), ForgeDirection.UP);
        CoverVisual in = first.currentVisual();
        CoverVisual out = second.currentVisual();
        if (in.markColor() != NEW_COLOR || in.arrow() != CoverVisual.Arrow.OUTWARD) {
            return SelfCheckResult.fail("重启后输入端外观不对: " + in.markColor() + " / " + in.arrow());
        }
        if (out.markColor() != NEW_COLOR || out.arrow() != CoverVisual.Arrow.INWARD) {
            return SelfCheckResult.fail("重启后输出端外观不对: " + out.markColor() + " / " + out.arrow());
        }
        return SelfCheckResult.pass("连接、交互状态、改色、断开都会触发同步，外观正确且重启后仍正确");
    }
}
