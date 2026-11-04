package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import java.util.Random;

import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.ConnectionAction;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.PanelEndpoint;
import com.rtgame.warpcovers.core.Viewer;
import com.rtgame.warpcovers.gt.BaseWarpCover;
import com.rtgame.warpcovers.gt.EnergyWarpCover;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.ItemWarpCover;
import com.rtgame.warpcovers.gt.WarpCoverGui;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.modularui2.CoverGuiData;

/**
 * 单类型覆盖板的双栏界面（#18）：在服务端把三种覆盖板的整个界面构建一次，抓构建期错误；
 * 走连接按钮的连接、切换流程，改「连接」分页的设置，重启后校验都保留。
 */
public final class CoverGuiScenario implements SelfCheckScenario {

    private static final int X = 130;
    private static final int Y = 200;
    private static final int Z = 100;
    private static final String FIRST = "自检界面-一";
    private static final String SECOND = "自检界面-二";
    private static final Viewer VIEWER = new Viewer("00000000-0000-4000-8000-00000000c001", "viewer");
    private static final int PRIORITY = 7;
    private static final long RATE = 33;
    private static final int PERIOD = 40;

    @Override
    public String name() {
        return "cover_gui";
    }

    @Override
    public void setup() {
        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, FIRST);
        deleteChannelsNamed(hub, SECOND);
        Channel first = hub.createChannel(ChannelType.FLUID, FIRST);
        Channel second = hub.createChannel(ChannelType.FLUID, SECOND);

        IGregTechTileEntity tank = placeTank(world(0), X, Y, Z);
        FluidWarpCover fluid = attachCover(tank, ForgeDirection.UP);
        IGregTechTileEntity buffer = placeMachine(world(0), X + 2, Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        ItemWarpCover item = attachItemCover(buffer, ForgeDirection.UP);
        EnergyWarpCover energy = attachEnergyCover(buffer, ForgeDirection.NORTH);
        buildGui(fluid, tank, ForgeDirection.UP, false);
        buildGui(fluid, tank, ForgeDirection.UP, true);
        buildGui(item, buffer, ForgeDirection.UP, false);
        buildGui(energy, buffer, ForgeDirection.NORTH, false);

        PanelEndpoint endpoint = fluid.parts()
            .get(0);
        CoverPanel panel = new CoverPanel(hub, ChannelType.FLUID, endpoint, new Random());

        check("未连接时按钮应是连接", panel.connectionAction(first.id()) == ConnectionAction.CONNECT);
        panel.applyConnection(VIEWER, first.id(), ConnectionAction.CONNECT);
        check(
            "连接后应连着第一个频道",
            first.id()
                .equals(endpoint.channelId()));
        check("选中已连接频道时按钮应是断开", panel.connectionAction(first.id()) == ConnectionAction.DISCONNECT);
        check("选中别的频道时按钮应是切换", panel.connectionAction(second.id()) == ConnectionAction.SWITCH);
        check("连接后输入应生效", panel.setState(VIEWER, InteractionState.INPUT));
        panel.setPriority(PRIORITY);
        panel.setRate(RATE);
        panel.setPeriod(PERIOD);

        panel.applyConnection(VIEWER, second.id(), ConnectionAction.SWITCH);
        check(
            "切换后应连着第二个频道",
            second.id()
                .equals(endpoint.channelId()));
        check(
            "切换后旧频道不应保留这个连接端",
            first.endpoints()
                .isEmpty());
        expect(
            "切换后新频道的连接端数",
            1,
            second.endpoints()
                .size());
        check(
            "切换后交互状态应为空",
            endpoint.config()
                .state() == InteractionState.EMPTY);
        check("切换后输出应生效", panel.setState(VIEWER, InteractionState.OUTPUT));
    }

    private static void buildGui(BaseWarpCover cover, IGregTechTileEntity tile, ForgeDirection side, boolean popUp) {
        // 服务端不能 new UISettings，基类构建面板时也用不到
        new WarpCoverGui(cover).createStandalonePanel(
            new PanelSyncManager(new ModularSyncManager(false), false),
            null,
            new CoverGuiData(
                FakePlayerFactory.getMinecraft(world(0)),
                cover.getCoverID(),
                tile.getXCoord(),
                tile.getYCoord(),
                tile.getZCoord(),
                side,
                popUp));
    }

    @Override
    public SelfCheckResult verify() {
        ChannelHub hub = WarpServices.hub();
        Channel second = channelNamed(hub, SECOND);
        IGregTechTileEntity tank = requireTile(world(0), X, Y, Z);
        FluidWarpCover fluid = coverAt(tank, ForgeDirection.UP);
        PanelEndpoint endpoint = fluid.parts()
            .get(0);
        EndpointConfig config = endpoint.config();
        if (!second.id()
            .equals(endpoint.channelId())) {
            return SelfCheckResult.fail("重启后连接丢失: " + endpoint.channelId());
        }
        if (config.state() != InteractionState.OUTPUT || config.priority() != PRIORITY
            || config.rate() != RATE
            || config.period() != PERIOD) {
            return SelfCheckResult.fail(
                "重启后「连接」分页的设置丢失: " + config.state()
                    + " / "
                    + config.priority()
                    + " / "
                    + config.rate()
                    + " / "
                    + config.period());
        }
        buildGui(fluid, tank, ForgeDirection.UP, false);
        return SelfCheckResult.pass("双栏界面可构建，连接与切换符合规则，交互状态、优先级、速率、周期重启后保留");
    }
}
