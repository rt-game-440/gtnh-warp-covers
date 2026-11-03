package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.Viewer;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** 频道列表（#11）：真实传输留下最近传输记录，重启后记录清空、私人标签还在。 */
public final class ChannelListingScenario implements SelfCheckScenario {

    private static final int X = 120;
    private static final int Y = 200;
    private static final int Z = 100;
    private static final String NAME = "自检列表";
    private static final String TAG = "自检标签";
    private static final Viewer OWNER = new Viewer("00000000-0000-4000-8000-00000000c001", "owner");

    @Override
    public String name() {
        return "channel_listing";
    }

    @Override
    public void setup() {
        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, NAME);
        Channel channel = hub.createChannel(ChannelType.FLUID, NAME, OWNER.personalOwnerId(), OWNER.name(), 0xABABAB)
            .channel();
        IGregTechTileEntity in = placeTank(world(0), X, Y, Z);
        IGregTechTileEntity out = placeTank(world(0), X + 2, Y, Z);
        FluidWarpCover input = connect(in, channel, InteractionState.INPUT);
        FluidWarpCover output = connect(out, channel, InteractionState.OUTPUT);
        check(
            "传输前不应有记录",
            hub.recentTransfers()
                .of(channel.id())
                .isEmpty());

        pour(in, 1000);
        runCovers(1_000_000_000L, output, input);
        expect("应完成传输", 1000, fluidIn(out, "water"));
        check(
            "传输后应记录流体种类",
            hub.recentTransfers()
                .of(channel.id())
                .contains("water"));

        check("打标签应成功", hub.addTag(OWNER.playerId(), channel.id(), TAG) == null);
    }

    private static FluidWarpCover connect(IGregTechTileEntity tank, Channel channel, InteractionState state) {
        tank.setFrontFacing(ForgeDirection.DOWN);
        FluidWarpCover cover = attachCover(tank, ForgeDirection.UP);
        cover.connect(channel.id(), OWNER.playerId());
        cover.filter()
            .add("water");
        cover.setInteractionState(state);
        return cover;
    }

    @Override
    public SelfCheckResult verify() {
        ChannelHub hub = WarpServices.hub();
        Channel channel = channelNamed(hub, NAME);
        if (!hub.recentTransfers()
            .of(channel.id())
            .isEmpty()) {
            return SelfCheckResult.fail("最近传输记录不应跨重启保留");
        }
        if (!hub.tags()
            .of(OWNER.playerId(), channel.id())
            .contains(TAG)) {
            return SelfCheckResult.fail("重启后私人标签丢失");
        }
        return SelfCheckResult.pass("真实传输留下记录；重启后记录清空、标签保留");
    }
}
