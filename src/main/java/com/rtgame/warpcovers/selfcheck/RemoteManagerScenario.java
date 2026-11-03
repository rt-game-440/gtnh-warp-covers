package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import java.util.Random;

import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelColors;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.Endpoint;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.RemoteEndpoint;
import com.rtgame.warpcovers.core.Viewer;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.ModItems;
import com.rtgame.warpcovers.gt.WarpManagerItem;
import com.rtgame.warpcovers.gt.WarpPart;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 折跃频道管理器：在主世界操作，远程修改下界里已加载的连接端，传输行为随之改变；操作者成为新的连接者；
 * 远程断开后覆盖板说明原因。重启后这些改动保留。管理器界面能在服务端构建。
 */
public final class RemoteManagerScenario implements SelfCheckScenario {

    private static final int X = 200;
    private static final int Y = 200;
    private static final int NETHER_Y = 100;
    private static final int Z = 100;
    private static final int SRC_X = X;
    private static final int DST_X = X;
    private static final int THIRD_X = X + 2;
    private static final String CHANNEL = "自检管理器";
    private static final Viewer OPERATOR = new Viewer("selfcheck-operator", "操作者");
    private static final String OLD_CONNECTOR = "selfcheck-old";
    private static final String WHITELISTED = "lava";

    private long now = 1000;

    private void run(FluidWarpCover... covers) {
        now += 40;
        runCovers(now, covers);
    }

    private static FluidWarpCover connected(IGregTechTileEntity tank, Channel channel, String connector,
        InteractionState state) {
        tank.setFrontFacing(ForgeDirection.DOWN);
        FluidWarpCover cover = attachCover(tank, ForgeDirection.UP);
        WarpPart part = cover.part(ChannelType.FLUID);
        part.connect(channel.id(), connector);
        // 默认的空白名单什么都不放行，这里先放行全部，由后面的远程修改来收紧
        part.config()
            .filter()
            .setWhitelist(false);
        part.setInteractionState(state);
        return cover;
    }

    @Override
    public String name() {
        return "remote_manager";
    }

    @Override
    public void setup() {
        clearWeather(world(0));
        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, CHANNEL);
        Channel channel = hub
            .createChannel(
                ChannelType.FLUID,
                CHANNEL,
                OPERATOR.personalOwnerId(),
                OPERATOR.name(),
                ChannelColors.DEFAULT)
            .channel();

        IGregTechTileEntity srcTank = placeTank(world(0), SRC_X, Y, Z);
        IGregTechTileEntity dstTank = placeTank(world(-1), DST_X, NETHER_Y, Z);
        IGregTechTileEntity thirdTank = placeTank(world(0), THIRD_X, Y, Z);
        FluidWarpCover src = connected(srcTank, channel, OPERATOR.playerId(), InteractionState.INPUT);
        FluidWarpCover dst = connected(dstTank, channel, OLD_CONNECTOR, InteractionState.EMPTY);
        FluidWarpCover third = connected(thirdTank, channel, OLD_CONNECTOR, InteractionState.EMPTY);

        pour(srcTank, 1000);
        run(dst, src);
        expect("对端是「空」时不传输", 0, fluidIn(dstTank, "water"));

        // 列表能看到另一维度的连接端，已加载
        String dstId = dst.part(ChannelType.FLUID)
            .endpointId();
        RemoteEndpoint remote = new RemoteEndpoint(hub, OPERATOR);
        CoverPanel panel = new CoverPanel(hub, ChannelType.FLUID, remote, new Random(1));
        boolean listed = false;
        for (Endpoint endpoint : panel.admin()
            .endpointsOf(OPERATOR, channel.id())) {
            if (endpoint.endpointId()
                .equals(dstId)) {
                listed = endpoint.isLoaded() && endpoint.location()
                    .dimension() == -1;
            }
        }
        check("连接端列表应包含下界里已加载的连接端", listed);

        // 没有权限的人选不中
        check("陌生人不应能选中连接端", new RemoteEndpoint(hub, new Viewer("selfcheck-stranger", "陌生人")).select(dstId) != null);

        // 远程改成输出：开始传输，操作者成为连接者
        check("应能选中已加载的连接端", remote.select(dstId) == null);
        check("应能远程修改交互状态", panel.setState(OPERATOR, InteractionState.OUTPUT));
        check(
            "覆盖板上的状态应随之改变",
            dst.part(ChannelType.FLUID)
                .config()
                .state() == InteractionState.OUTPUT);
        check(
            "操作者应成为新的连接者",
            OPERATOR.playerId()
                .equals(
                    hub.findEndpoint(dstId)
                        .connector()));
        run(dst, src);
        expect("远程改成输出后开始传输", 1000, fluidIn(dstTank, "water"));

        // 远程改名单：对端白名单不含水，不再传输
        panel.addFilterKey(WHITELISTED);
        panel.setWhitelist(true);
        panel.setRate(500);
        panel.setPriority(3);
        pour(srcTank, 1000);
        run(dst, src);
        expect("远程改白名单后不再传输", 1000, fluidIn(dstTank, "water"));
        expect("水留在源储罐", 1000, fluidIn(srcTank, "water"));

        // 远程断开：覆盖板说明原因
        WarpPart thirdPart = third.part(ChannelType.FLUID);
        check("应能选中第三个连接端", remote.select(thirdPart.endpointId()) == null);
        remote.disconnect();
        check(
            "远程断开后覆盖板应没有频道",
            thirdPart.channelId()
                .isEmpty());
        check("远程断开应说明原因", thirdPart.lastReason() == DisconnectReason.REMOTE);

        // 管理器界面能在服务端构建
        ItemStack item = new ItemStack(ModItems.manager);
        check("管理器物品应已注册", item.getItem() instanceof WarpManagerItem);
        ((WarpManagerItem) item.getItem()).buildUI(
            new GuiData(FakePlayerFactory.getMinecraft(world(0))),
            new PanelSyncManager(new ModularSyncManager(false), false),
            null);
    }

    @Override
    public SelfCheckResult verify() {
        ChannelHub hub = WarpServices.hub();
        FluidWarpCover dst = coverAt(requireTile(world(-1), DST_X, NETHER_Y, Z), ForgeDirection.UP);
        WarpPart part = dst.part(ChannelType.FLUID);
        if (part.channelId()
            .isEmpty()
            || part.config()
                .state() != InteractionState.OUTPUT) {
            return SelfCheckResult.fail(
                "重启后远程设置的状态丢失: " + part.channelId()
                    + " / "
                    + part.config()
                        .state());
        }
        if (!part.config()
            .filter()
            .keys()
            .contains(WHITELISTED)
            || part.config()
                .rate() != 500
            || part.config()
                .priority() != 3) {
            return SelfCheckResult.fail("重启后远程设置的名单、速率或优先级丢失");
        }
        Endpoint endpoint = hub.findEndpoint(part.endpointId());
        if (endpoint == null || !OPERATOR.playerId()
            .equals(endpoint.connector())) {
            return SelfCheckResult.fail("重启后连接者不是操作者: " + (endpoint == null ? "索引丢失" : endpoint.connector()));
        }
        FluidWarpCover third = coverAt(requireTile(world(0), THIRD_X, Y, Z), ForgeDirection.UP);
        if (!third.part(ChannelType.FLUID)
            .channelId()
            .isEmpty()) {
            return SelfCheckResult.fail("重启后远程断开的连接端不应恢复");
        }
        return SelfCheckResult.pass("远程修改另一维度的连接端后传输随之改变，连接者更换，重启后保留");
    }
}
