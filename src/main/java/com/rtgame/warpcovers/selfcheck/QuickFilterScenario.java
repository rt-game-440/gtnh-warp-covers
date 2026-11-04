package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.FilterCandidates;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.PanelNotice;
import com.rtgame.warpcovers.core.Viewer;
import com.rtgame.warpcovers.gt.AggregateWarpCover;
import com.rtgame.warpcovers.gt.ItemKeys;
import com.rtgame.warpcovers.gt.WarpPart;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 过滤名单快捷配置：真实 GT 机器的存储能列为候选（物品和流体），一次加入多项、已有项不重复，
 * 加入的是频道的名单；重启后名单还在，候选仍按名单去重。
 */
public final class QuickFilterScenario implements SelfCheckScenario {

    private static final int X = 180;
    private static final int Y = 200;
    private static final int Z = 100;
    private static final int CHEST_X = X;
    private static final int TANK_X = X + 4;
    private static final String ITEM = "自检快捷-物品";
    private static final String FLUID = "自检快捷-流体";
    private static final String WATER = "water";
    private static final Viewer VIEWER = new Viewer("selfcheck", "selfcheck");

    private static String key(net.minecraft.item.Item item) {
        return ItemKeys.of(new ItemStack(item));
    }

    private static List<String> list(String... keys) {
        return Arrays.asList(keys);
    }

    private static CoverPanel panelOf(WarpPart part, ChannelType type) {
        return new CoverPanel(WarpServices.hub(), type, part, new Random(1));
    }

    /** 一次加入的反馈里带的实际加入数量；没有新项时为 0。 */
    private static int addedCount(PanelNotice notice) {
        return "filter_added".equals(notice.key()) ? Integer.parseInt(notice.arg()) : 0;
    }

    @Override
    public String name() {
        return "quick_filter";
    }

    @Override
    public void setup() {
        WorldServer world = world(0);
        clearWeather(world);
        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, ITEM);
        deleteChannelsNamed(hub, FLUID);
        Channel item = hub.createChannel(ChannelType.ITEM, ITEM);
        Channel fluid = hub.createChannel(ChannelType.FLUID, FLUID);

        // 物品：机器里有木棍和煤；候选来自本机存储，加入的是频道名单
        IGregTechTileEntity chest = placeMachine(world, CHEST_X, Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        chest.setFrontFacing(ForgeDirection.NORTH);
        chest.setInventorySlotContents(0, new ItemStack(Items.stick, 4));
        chest.setInventorySlotContents(1, new ItemStack(Items.coal, 4));
        WarpPart chestPart = attachAggregateCover(chest, ForgeDirection.UP).part(ChannelType.ITEM);
        chestPart.connect(item.id(), "selfcheck");
        chestPart.setInteractionState(InteractionState.INPUT);

        CoverPanel panel = panelOf(chestPart, ChannelType.ITEM);
        FilterCandidates candidates = panel.filterCandidates();
        check(
            "物品存储候选应含木棍和煤，实际 " + candidates.stored(),
            candidates.stored()
                .containsAll(list(key(Items.stick), key(Items.coal)))
                && candidates.stored()
                    .size() == 2);

        expect(
            "一次加入两项（含重复）",
            2,
            addedCount(panel.addFilterKeys(VIEWER, list(key(Items.coal), key(Items.stick), key(Items.coal)))));
        expect("再次加入已有项", 0, addedCount(panel.addFilterKeys(VIEWER, list(key(Items.coal)))));
        check(
            "加入的是频道名单",
            item.filter()
                .keys()
                .containsAll(list(key(Items.coal), key(Items.stick))));
        check(
            "加入后存储候选应为空，实际 " + panel.filterCandidates()
                .stored(),
            panel.filterCandidates()
                .stored()
                .isEmpty());
        // 留一项不在名单里，重启后候选里还应列出它
        panel.removeFilterKey(VIEWER, key(Items.stick));
        check(
            "移除后木棍重新成为候选",
            panel.filterCandidates()
                .stored()
                .equals(list(key(Items.stick))));

        // 流体：储罐里有水
        IGregTechTileEntity tank = placeTank(world, TANK_X, Y, Z);
        tank.setFrontFacing(ForgeDirection.DOWN);
        pour(tank, 1000);
        WarpPart tankPart = attachAggregateCover(tank, ForgeDirection.UP).part(ChannelType.FLUID);
        tankPart.connect(fluid.id(), "selfcheck");
        tankPart.setInteractionState(InteractionState.OUTPUT);
        FilterCandidates fluids = panelOf(tankPart, ChannelType.FLUID).filterCandidates();
        check(
            "流体存储候选应为水，实际 " + fluids.stored(),
            fluids.stored()
                .equals(list(WATER)));
    }

    @Override
    public SelfCheckResult verify() {
        ChannelHub hub = WarpServices.hub();
        Channel item = channelNamed(hub, ITEM);
        AggregateWarpCover chest = aggregateCoverAt(requireTile(world(0), CHEST_X, Y, Z), ForgeDirection.UP);
        WarpPart chestPart = chest.part(ChannelType.ITEM);
        if (!item.filter()
            .keys()
            .equals(new java.util.LinkedHashSet<String>(list(key(Items.coal))))) {
            return SelfCheckResult.fail(
                "重启后频道名单不对: " + item.filter()
                    .keys());
        }
        FilterCandidates candidates = panelOf(chestPart, ChannelType.ITEM).filterCandidates();
        if (!candidates.stored()
            .equals(list(key(Items.stick)))) {
            return SelfCheckResult.fail("重启后存储候选应只剩木棍，实际 " + candidates.stored());
        }
        // 没有玩家时这里的区块不 tick，而 GT 机器 tick 不够时拒绝抽取；玩家开着界面时不会遇到
        warmUp(world(0), TANK_X, Y, Z);
        AggregateWarpCover tank = aggregateCoverAt(requireTile(world(0), TANK_X, Y, Z), ForgeDirection.UP);
        FilterCandidates fluids = panelOf(tank.part(ChannelType.FLUID), ChannelType.FLUID).filterCandidates();
        if (!fluids.stored()
            .equals(list(WATER))) {
            return SelfCheckResult
                .fail("重启后流体候选不对: " + fluids.stored() + "，储罐水量 " + fluidIn(requireTile(world(0), TANK_X, Y, Z), WATER));
        }
        return SelfCheckResult.pass("存储候选正确，加入的是频道名单且不重复，重启后名单与候选仍然正确");
    }
}
