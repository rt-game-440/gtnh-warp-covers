package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import java.util.Random;

import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;

import com.cleanroommc.modularui.value.sync.ModularSyncManager;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.cleanroommc.modularui.widgets.layout.Flow;
import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.EndpointStatus;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.gt.AggregateWarpCover;
import com.rtgame.warpcovers.gt.BaseWarpCover;
import com.rtgame.warpcovers.gt.ItemKeys;
import com.rtgame.warpcovers.gt.ModItems;
import com.rtgame.warpcovers.gt.WarpCoverGui;
import com.rtgame.warpcovers.gt.WarpPart;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.covers.CoverRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.modularui2.CoverGuiData;
import gregtech.api.util.GTOreDictUnificator;

/**
 * 聚合覆盖板（#13）：同一 tick 里物品和能量经同一块覆盖板传输、流体经另一块传输，各部分配置独立；
 * 贴在导线上时能量部分停用并说明原因、流体部分不受影响；撬下变物品再贴上配置全部保留；重启后配置保留。
 */
public final class AggregateCoverScenario implements SelfCheckScenario {

    private static final int X = 160;
    private static final int Y = 200;
    private static final int Z = 100;
    private static final int SRC_X = X;
    private static final int DST_X = X + 2;
    private static final int TANK_SRC_X = X + 4;
    private static final int TANK_DST_X = X + 6;
    private static final int CABLE_X = X + 8;
    private static final int REPLACED_X = X + 10;
    private static final int BATTERY_X = X + 12;
    private static final String FLUID = "自检聚合-流体";
    private static final String ITEM = "自检聚合-物品";
    private static final String ENERGY = "自检聚合-能量";
    private static final String CONNECTOR = "selfcheck";
    private static final long SRC_EU = 2000;
    private static final int STICKS = 16;
    private static final long FIRST_RATE = 7;
    private static final long THIRD_RATE = 9;

    @Override
    public String name() {
        return "aggregate_cover";
    }

    @Override
    public void setup() {
        WorldServer world = world(0);
        // 下雨会让露天的 GT 电器随机爆炸，与本场景无关，先排除
        clearWeather(world);
        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, FLUID);
        deleteChannelsNamed(hub, ITEM);
        deleteChannelsNamed(hub, ENERGY);
        Channel fluid = hub.createChannel(ChannelType.FLUID, FLUID);
        Channel item = hub.createChannel(ChannelType.ITEM, ITEM);
        Channel energy = hub.createChannel(ChannelType.ENERGY, ENERGY);

        // 同一块覆盖板同时传输物品和能量：箱式缓存既有格子也有储能（只收不供），所以物品输入、能量输出
        IGregTechTileEntity src = placeMachine(world, SRC_X, Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        IGregTechTileEntity dst = placeMachine(world, DST_X, Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        src.setFrontFacing(ForgeDirection.NORTH);
        dst.setFrontFacing(ForgeDirection.NORTH);
        src.setInventorySlotContents(0, new ItemStack(Items.stick, STICKS));
        setEU(src, 0);
        IGregTechTileEntity battery = placeMachine(world, BATTERY_X, Y, Z, ItemList.Battery_Buffer_1by1_IV.get(1));
        battery.setFrontFacing(ForgeDirection.NORTH);
        setEU(battery, SRC_EU);
        long totalEu = src.getStoredEU() + battery.getStoredEU();
        AggregateWarpCover srcCover = attachAggregateCover(src, ForgeDirection.UP);
        AggregateWarpCover dstCover = attachAggregateCover(dst, ForgeDirection.UP);
        AggregateWarpCover batteryCover = attachAggregateCover(battery, ForgeDirection.UP);
        check(
            "聚合覆盖板应有三个连接端",
            srcCover.parts()
                .size() == 3);

        WarpPart srcItem = srcCover.part(ChannelType.ITEM);
        WarpPart srcEnergy = srcCover.part(ChannelType.ENERGY);
        WarpPart dstItem = dstCover.part(ChannelType.ITEM);
        WarpPart batteryEnergy = batteryCover.part(ChannelType.ENERGY);
        connect(srcItem, item, InteractionState.INPUT);
        connect(dstItem, item, InteractionState.OUTPUT);
        connect(srcEnergy, energy, InteractionState.OUTPUT);
        connect(batteryEnergy, energy, InteractionState.INPUT);
        String stick = ItemKeys.of(new ItemStack(Items.stick));
        srcItem.config()
            .filter()
            .add(stick);
        dstItem.config()
            .filter()
            .add(stick);
        check(
            "三个连接端的 ID 应各不相同",
            !srcItem.endpointId()
                .equals(srcEnergy.endpointId()));
        check(
            "未连接的流体部分不受影响",
            srcCover.part(ChannelType.FLUID)
                .channelId()
                .isEmpty());
        check(
            "各部分的交互状态应独立",
            srcCover.part(ChannelType.FLUID)
                .config()
                .state() == InteractionState.EMPTY);

        // 流体：另一对覆盖板，同时运行
        IGregTechTileEntity tankSrc = placeTank(world, TANK_SRC_X, Y, Z);
        IGregTechTileEntity tankDst = placeTank(world, TANK_DST_X, Y, Z);
        tankSrc.setFrontFacing(ForgeDirection.DOWN);
        tankDst.setFrontFacing(ForgeDirection.DOWN);
        AggregateWarpCover tankSrcCover = attachAggregateCover(tankSrc, ForgeDirection.UP);
        AggregateWarpCover tankDstCover = attachAggregateCover(tankDst, ForgeDirection.UP);
        connect(tankSrcCover.part(ChannelType.FLUID), fluid, InteractionState.INPUT);
        connect(tankDstCover.part(ChannelType.FLUID), fluid, InteractionState.OUTPUT);
        tankSrcCover.part(ChannelType.FLUID)
            .config()
            .filter()
            .add("water");
        tankDstCover.part(ChannelType.FLUID)
            .config()
            .filter()
            .add("water");
        pour(tankSrc, 1000);

        long now = 1_000_000_000L;
        tankDstCover.tickAt(now);
        dstCover.tickAt(now);
        srcCover.tickAt(now);
        batteryCover.tickAt(now);
        tankSrcCover.tickAt(now);

        check("物品应传到对端", dst.getStackInSlot(0) != null && dst.getStackInSlot(0).stackSize > 0);
        check("能量应经同一块覆盖板传到", src.getStoredEU() > 0);
        expect("能量总量（守恒）", totalEu, src.getStoredEU() + battery.getStoredEU());
        expect("流体应传到对端", 1000, fluidIn(tankDst, "water"));

        // 界面在服务端构建不应报错（构建时同步管理器还没绑定容器，曾因此打开就崩溃）
        buildGui(srcCover, src, ForgeDirection.UP);
        buildGui(attachCover(tankDst, ForgeDirection.NORTH), tankDst, ForgeDirection.NORTH);

        // 导线：能量部分停用并说明原因，流体部分正常
        IGregTechTileEntity cable = placeMachine(
            world,
            CABLE_X,
            Y,
            Z,
            GTOreDictUnificator.get(OrePrefixes.cableGt01, Materials.Tin, 1L));
        AggregateWarpCover cableCover = attachAggregateCover(cable, ForgeDirection.UP);
        WarpPart cableEnergy = cableCover.part(ChannelType.ENERGY);
        WarpPart cableFluid = cableCover.part(ChannelType.FLUID);
        connect(cableEnergy, energy, InteractionState.INPUT);
        connect(cableFluid, fluid, InteractionState.OUTPUT);
        check("导线上能量部分应停用", cableEnergy.blockedStatus() == EndpointStatus.NO_ENERGY_STORAGE);
        check("导线上流体部分不应停用", cableFluid.blockedStatus() == null);
        check(
            "导线上能量部分的状态栏应说明原因",
            new CoverPanel(hub, ChannelType.ENERGY, cableEnergy, new Random()).status(now)
                == EndpointStatus.NO_ENERGY_STORAGE);
        check(
            "导线上流体部分的状态栏不应说停用",
            new CoverPanel(hub, ChannelType.FLUID, cableFluid, new Random()).status(now)
                != EndpointStatus.NO_ENERGY_STORAGE);
        long beforeCableRun = src.getStoredEU() + battery.getStoredEU();
        cableCover.tickAt(now + 100);
        expect("导线上的能量输入端不应产生能量", beforeCableRun, src.getStoredEU() + battery.getStoredEU());

        // 变成物品后配置全部保留
        srcCover.part(ChannelType.FLUID)
            .config()
            .setRate(FIRST_RATE);
        srcCover.part(ChannelType.ENERGY)
            .config()
            .setRate(THIRD_RATE);
        ItemStack dropped = srcCover.asItemStack();
        check("物品化的物品类型", dropped.getItem() == ModItems.aggregateCover);
        IGregTechTileEntity replaced = placeMachine(world, REPLACED_X, Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        replaced.attachCover(CoverRegistry.buildCover(dropped, ForgeDirection.UP, replaced));
        AggregateWarpCover restored = aggregateCoverAt(replaced, ForgeDirection.UP);
        check(
            "物品化后流体部分的速率应保留",
            restored.part(ChannelType.FLUID)
                .config()
                .rate() == FIRST_RATE);
        check(
            "物品化后能量部分的速率应保留",
            restored.part(ChannelType.ENERGY)
                .config()
                .rate() == THIRD_RATE);
        check(
            "物品化后物品部分的名单应保留",
            restored.part(ChannelType.ITEM)
                .config()
                .filter()
                .keys()
                .contains(stick));
        check(
            "物品化后物品部分的频道应保留",
            item.id()
                .equals(
                    restored.part(ChannelType.ITEM)
                        .channelId()));
    }

    private static void buildGui(BaseWarpCover cover, IGregTechTileEntity tile, ForgeDirection side) {
        new WarpCoverGui(cover).addUIWidgets(
            new PanelSyncManager(new ModularSyncManager(false), false),
            Flow.column(),
            new CoverGuiData(
                FakePlayerFactory.getMinecraft(world(0)),
                cover.getCoverID(),
                tile.getXCoord(),
                tile.getYCoord(),
                tile.getZCoord(),
                side,
                false));
    }

    private static void connect(WarpPart part, Channel channel, InteractionState state) {
        part.connect(channel.id(), CONNECTOR);
        part.setInteractionState(state);
    }

    @Override
    public SelfCheckResult verify() {
        ChannelHub hub = WarpServices.hub();
        AggregateWarpCover src = aggregateCoverAt(requireTile(world(0), SRC_X, Y, Z), ForgeDirection.UP);
        Channel item = channelNamed(hub, ITEM);
        Channel energy = channelNamed(hub, ENERGY);
        WarpPart srcItem = src.part(ChannelType.ITEM);
        WarpPart srcEnergy = src.part(ChannelType.ENERGY);
        WarpPart srcFluid = src.part(ChannelType.FLUID);
        if (!item.id()
            .equals(srcItem.channelId())
            || !energy.id()
                .equals(srcEnergy.channelId())) {
            return SelfCheckResult.fail("重启后频道连接丢失: " + srcItem.channelId() + " / " + srcEnergy.channelId());
        }
        if (srcItem.config()
            .state() != InteractionState.INPUT
            || srcEnergy.config()
                .state() != InteractionState.OUTPUT) {
            return SelfCheckResult.fail("重启后交互状态丢失");
        }
        if (srcFluid.config()
            .rate() != FIRST_RATE
            || srcEnergy.config()
                .rate() != THIRD_RATE) {
            return SelfCheckResult.fail(
                "重启后速率丢失: " + srcFluid.config()
                    .rate()
                    + " / "
                    + srcEnergy.config()
                        .rate());
        }
        if (!srcFluid.channelId()
            .isEmpty()) {
            return SelfCheckResult.fail("重启后未连接的部分不应有频道");
        }
        AggregateWarpCover cable = aggregateCoverAt(requireTile(world(0), CABLE_X, Y, Z), ForgeDirection.UP);
        if (cable.part(ChannelType.ENERGY)
            .blockedStatus() != EndpointStatus.NO_ENERGY_STORAGE) {
            return SelfCheckResult.fail("重启后导线上的能量部分应仍停用");
        }
        return SelfCheckResult.pass("三类同时传输、导线上能量停用、物品化和重启后配置都保留");
    }
}
