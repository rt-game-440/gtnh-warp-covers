package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelAdmin;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.DistributionMode;
import com.rtgame.warpcovers.core.Identity;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.Permission;
import com.rtgame.warpcovers.core.Viewer;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 频道配置面板（#10）：在真实覆盖板上验证改名后继续传输、频道名缓存刷新；权限收紧后连接端断开；
 * 删除后连接端恢复为未连接并记原因。重启后校验改过的属性和断开结果都已存盘。
 */
public final class ChannelConfigScenario implements SelfCheckScenario {

    private static final int X = 100;
    private static final int Y = 200;
    private static final int Z = 100;
    private static final String OLD_NAME = "自检配置-旧名";
    private static final String NEW_NAME = "自检配置-新名";
    private static final String DELETED = "自检配置-将删除";
    private static final Viewer OWNER = new Viewer("00000000-0000-4000-8000-00000000b001", "owner");
    private static final String STRANGER = "00000000-0000-4000-8000-00000000b002";

    private long now = 1_000_000_000L;

    @Override
    public String name() {
        return "channel_config";
    }

    @Override
    public void setup() {
        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, OLD_NAME);
        deleteChannelsNamed(hub, NEW_NAME);
        deleteChannelsNamed(hub, DELETED);
        ChannelAdmin admin = new ChannelAdmin(hub);

        // 改名：连接端不动，继续传输
        IGregTechTileEntity in = placeTank(world(0), X, Y, Z);
        IGregTechTileEntity out = placeTank(world(0), X + 2, Y, Z);
        IGregTechTileEntity outsider = placeTank(world(0), X + 4, Y, Z);
        Channel channel = owned(hub, OLD_NAME);
        FluidWarpCover input = connect(in, channel, OWNER.playerId(), InteractionState.INPUT);
        FluidWarpCover output = connect(out, channel, OWNER.playerId(), InteractionState.OUTPUT);
        expectOk(
            admin.rename(OWNER, channel.id(), NEW_NAME)
                .isError(),
            "改名");
        check(
            "改名后覆盖板的频道 ID 不应变化",
            channel.id()
                .equals(input.channelId()));
        check(
            "改名后频道名缓存应刷新",
            NEW_NAME.equals(
                input.endpoint()
                    .channelName()));
        pour(in, 1000);
        now += 1000;
        runCovers(now, output, input);
        expect("改名后继续传输", 1000, fluidIn(out, "water"));

        // 属性
        expectOk(
            admin.setNote(OWNER, channel.id(), "自检备注")
                .isError(),
            "改备注");
        expectOk(
            admin.setColor(OWNER, channel.id(), 0x123456)
                .isError(),
            "改色");
        expectOk(
            admin.setDistribution(OWNER, channel.id(), DistributionMode.SEQUENTIAL)
                .isError(),
            "改分发");

        // 权限收紧：公开频道上的陌生人连接端断开
        expectOk(
            admin.setPublic(OWNER, channel.id(), true)
                .isError(),
            "改公开");
        FluidWarpCover strangerCover = connect(outsider, channel, STRANGER, InteractionState.OUTPUT);
        expectOk(
            admin.setPermission(OWNER, channel.id(), Identity.STRANGER, Permission.CONNECT, false)
                .isError(),
            "收紧权限");
        check(
            "收紧后陌生人的连接端应断开",
            strangerCover.channelId()
                .isEmpty());
        check("断开原因应为连接者失权", strangerCover.lastReason() == DisconnectReason.PERMISSION_LOST);
        check(
            "管理者的连接端不应受影响",
            channel.id()
                .equals(input.channelId()));

        // 无权限者的操作被拒绝
        Viewer stranger = new Viewer(STRANGER, "stranger");
        check(
            "陌生人不能改名",
            admin.rename(stranger, channel.id(), "抢走")
                .isError());
        check(
            "陌生人不能删除",
            admin.delete(stranger, channel.id())
                .isError());

        // 删除
        IGregTechTileEntity doomedTank = placeTank(world(0), X + 6, Y, Z);
        Channel doomed = owned(hub, DELETED);
        FluidWarpCover doomedCover = connect(doomedTank, doomed, OWNER.playerId(), InteractionState.INPUT);
        expectOk(
            admin.delete(OWNER, doomed.id())
                .isError(),
            "删除");
        check("删除后频道应消失", hub.findChannel(doomed.id()) == null);
        check(
            "删除后连接端应未连接",
            doomedCover.channelId()
                .isEmpty());
        check("删除原因应为频道已被删除", doomedCover.lastReason() == DisconnectReason.CHANNEL_DELETED);
        check("删除后交互状态应回到空", doomedCover.state() == InteractionState.EMPTY);
    }

    private static Channel owned(ChannelHub hub, String name) {
        return hub.createChannel(ChannelType.FLUID, name, OWNER.personalOwnerId(), OWNER.name(), 0xABABAB)
            .channel();
    }

    private static FluidWarpCover connect(IGregTechTileEntity tank, Channel channel, String connector,
        InteractionState state) {
        tank.setFrontFacing(ForgeDirection.DOWN);
        FluidWarpCover cover = attachCover(tank, ForgeDirection.UP);
        cover.connect(channel.id(), connector);
        cover.setInteractionState(state);
        return cover;
    }

    private static void expectOk(boolean isError, String what) {
        check(what + "应成功", !isError);
    }

    @Override
    public SelfCheckResult verify() {
        ChannelHub hub = WarpServices.hub();
        Channel channel = channelNamed(hub, NEW_NAME);
        if (!"自检备注".equals(channel.note()) || channel.color() != 0x123456
            || channel.distribution() != DistributionMode.SEQUENTIAL) {
            return SelfCheckResult
                .fail("重启后频道属性丢失: " + channel.note() + " / " + channel.color() + " / " + channel.distribution());
        }
        if (channel.permissions()
            .allows(Identity.STRANGER, Permission.CONNECT)) {
            return SelfCheckResult.fail("重启后权限修改丢失");
        }
        FluidWarpCover input = coverAt(requireTile(world(0), X, Y, Z), ForgeDirection.UP);
        if (!channel.id()
            .equals(input.channelId())) {
            return SelfCheckResult.fail("重启后改名频道的连接端丢失");
        }
        FluidWarpCover strangerCover = coverAt(requireTile(world(0), X + 4, Y, Z), ForgeDirection.UP);
        if (strangerCover.lastReason() != DisconnectReason.PERMISSION_LOST) {
            return SelfCheckResult.fail("重启后失权原因丢失: " + strangerCover.lastReason());
        }
        FluidWarpCover doomedCover = coverAt(requireTile(world(0), X + 6, Y, Z), ForgeDirection.UP);
        if (!doomedCover.channelId()
            .isEmpty() || doomedCover.lastReason() != DisconnectReason.CHANNEL_DELETED) {
            return SelfCheckResult.fail("重启后删除原因丢失: " + doomedCover.lastReason());
        }
        return SelfCheckResult.pass("改名不影响连接、权限收紧即断开、删除恢复未连接，重启后属性和原因都在");
    }
}
