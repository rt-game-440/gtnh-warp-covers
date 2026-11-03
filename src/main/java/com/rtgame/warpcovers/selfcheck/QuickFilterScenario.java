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
import com.rtgame.warpcovers.gt.AggregateWarpCover;
import com.rtgame.warpcovers.gt.ItemKeys;
import com.rtgame.warpcovers.gt.WarpPart;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 过滤名单快捷配置：真实 GT 机器的存储能列为候选（物品和流体），对端白名单能汇总，
 * 一次加入多项、已有项不重复；重启后加载的连接端同样能汇总。
 */
public final class QuickFilterScenario implements SelfCheckScenario {

    private static final int X = 180;
    private static final int Y = 200;
    private static final int Z = 100;
    private static final int CHEST_X = X;
    private static final int PEER_X = X + 2;
    private static final int TANK_X = X + 4;
    private static final int TANK_PEER_X = X + 6;
    private static final String ITEM = "自检快捷-物品";
    private static final String FLUID = "自检快捷-流体";
    private static final String LAVA = "lava";
    private static final String WATER = "water";

    private static String key(net.minecraft.item.Item item) {
        return ItemKeys.of(new ItemStack(item));
    }

    private static List<String> list(String... keys) {
        return Arrays.asList(keys);
    }

    private static CoverPanel panelOf(WarpPart part, ChannelType type) {
        return new CoverPanel(WarpServices.hub(), type, part, new Random(1));
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

        // 物品：本端（输入）机器里有木棍和煤；对端（输出）白名单里有煤和钻石
        IGregTechTileEntity chest = placeMachine(world, CHEST_X, Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        IGregTechTileEntity peer = placeMachine(world, PEER_X, Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        chest.setFrontFacing(ForgeDirection.NORTH);
        peer.setFrontFacing(ForgeDirection.NORTH);
        chest.setInventorySlotContents(0, new ItemStack(Items.stick, 4));
        chest.setInventorySlotContents(1, new ItemStack(Items.coal, 4));
        WarpPart chestPart = attachAggregateCover(chest, ForgeDirection.UP).part(ChannelType.ITEM);
        WarpPart peerPart = attachAggregateCover(peer, ForgeDirection.UP).part(ChannelType.ITEM);
        chestPart.connect(item.id(), "selfcheck");
        peerPart.connect(item.id(), "selfcheck");
        chestPart.setInteractionState(InteractionState.INPUT);
        peerPart.setInteractionState(InteractionState.OUTPUT);
        peerPart.config()
            .filter()
            .add(key(Items.coal));
        peerPart.config()
            .filter()
            .add(key(Items.diamond));
        peerPart.configChanged();

        CoverPanel panel = panelOf(chestPart, ChannelType.ITEM);
        FilterCandidates candidates = panel.filterCandidates();
        check(
            "物品对端候选应含煤和钻石，实际 " + candidates.peers(),
            candidates.peers()
                .containsAll(list(key(Items.coal), key(Items.diamond)))
                && candidates.peers()
                    .size() == 2);
        check(
            "物品存储候选应含木棍和煤，实际 " + candidates.stored(),
            candidates.stored()
                .containsAll(list(key(Items.stick), key(Items.coal)))
                && candidates.stored()
                    .size() == 2);

        // 对端和本机都有煤：加入后两个来源都不再列出
        expect("一次加入两项", 2, panel.addFilterKeys(list(key(Items.coal), key(Items.stick))));
        expect("再次加入已有项", 0, panel.addFilterKeys(list(key(Items.coal))));
        candidates = panel.filterCandidates();
        check(
            "加入后对端候选只剩钻石，实际 " + candidates.peers(),
            candidates.peers()
                .equals(list(key(Items.diamond))));
        check(
            "加入后存储候选应为空，实际 " + candidates.stored(),
            candidates.stored()
                .isEmpty());

        // 流体：本端（输出）储罐里有水；对端（输入）白名单里有岩浆
        IGregTechTileEntity tank = placeTank(world, TANK_X, Y, Z);
        IGregTechTileEntity tankPeer = placeTank(world, TANK_PEER_X, Y, Z);
        tank.setFrontFacing(ForgeDirection.DOWN);
        tankPeer.setFrontFacing(ForgeDirection.DOWN);
        pour(tank, 1000);
        WarpPart tankPart = attachAggregateCover(tank, ForgeDirection.UP).part(ChannelType.FLUID);
        WarpPart tankPeerPart = attachAggregateCover(tankPeer, ForgeDirection.UP).part(ChannelType.FLUID);
        tankPart.connect(fluid.id(), "selfcheck");
        tankPeerPart.connect(fluid.id(), "selfcheck");
        tankPart.setInteractionState(InteractionState.OUTPUT);
        tankPeerPart.setInteractionState(InteractionState.INPUT);
        tankPeerPart.config()
            .filter()
            .add(LAVA);
        tankPeerPart.configChanged();

        FilterCandidates fluids = panelOf(tankPart, ChannelType.FLUID).filterCandidates();
        check(
            "流体对端候选应为岩浆，实际 " + fluids.peers(),
            fluids.peers()
                .equals(list(LAVA)));
        check(
            "流体存储候选应为水，实际 " + fluids.stored(),
            fluids.stored()
                .equals(list(WATER)));
    }

    @Override
    public SelfCheckResult verify() {
        AggregateWarpCover chest = aggregateCoverAt(requireTile(world(0), CHEST_X, Y, Z), ForgeDirection.UP);
        WarpPart chestPart = chest.part(ChannelType.ITEM);
        if (!chestPart.config()
            .filter()
            .keys()
            .containsAll(list(key(Items.coal), key(Items.stick)))) {
            return SelfCheckResult.fail(
                "重启后名单丢失: " + chestPart.config()
                    .filter()
                    .keys());
        }
        FilterCandidates candidates = panelOf(chestPart, ChannelType.ITEM).filterCandidates();
        if (!candidates.peers()
            .equals(list(key(Items.diamond)))) {
            return SelfCheckResult.fail("重启后对端候选应只剩钻石，实际 " + candidates.peers());
        }
        // 没有玩家时这里的区块不 tick，而 GT 机器 tick 不够时拒绝抽取；玩家开着界面时不会遇到
        warmUp(world(0), TANK_X, Y, Z);
        AggregateWarpCover tank = aggregateCoverAt(requireTile(world(0), TANK_X, Y, Z), ForgeDirection.UP);
        FilterCandidates fluids = panelOf(tank.part(ChannelType.FLUID), ChannelType.FLUID).filterCandidates();
        if (!fluids.peers()
            .equals(list(LAVA))
            || !fluids.stored()
                .equals(list(WATER))) {
            return SelfCheckResult.fail(
                "重启后流体候选不对: " + fluids.peers()
                    + " / "
                    + fluids.stored()
                    + "，储罐水量 "
                    + fluidIn(requireTile(world(0), TANK_X, Y, Z), WATER));
        }
        return SelfCheckResult.pass("物品和流体的两个候选来源都正确，加入不重复，重启后仍可汇总");
    }
}
