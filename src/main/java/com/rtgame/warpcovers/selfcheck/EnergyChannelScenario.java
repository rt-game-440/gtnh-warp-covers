package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.gt.EnergyWarpCover;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTOreDictUnificator;
import gregtech.common.covers.Cover;

/**
 * 能量频道场景（ADR-0003）：主世界的电源经能量折跃覆盖板给下界的 LV 机器直接充能。
 * 检查跨维度供电、IV 电源给 LV 机器充能不爆炸、充满即止、只抽会向外供电的机器、总量守恒、
 * 能量频道不看名单（全部保持默认的空白名单）、导线和没有储能的方块贴不上；挖下放回与重启后配置保留。
 * <p>
 * 布置（频道「能量自检频道」，驱动顺序 T、G、S、Q）：
 * <ul>
 * <li>T（主世界，HV→MV 变压器）、G（主世界，LV 柴油发电机）：少量储能，输入端，应被抽光</li>
 * <li>S（主世界，IV 电池箱）：大量储能，输入端，补足剩下的需求</li>
 * <li>Q（主世界，LV 打粉机）：有储能但不向外供电，输入端，不应被抽</li>
 * <li>L（下界，LV 打粉机）、K（下界，LV 电池箱）：空的输出端，应正好充满且不爆炸</li>
 * <li>W（主世界，锡导线）、N（主世界，LV 超级储罐）：贴不上能量覆盖板</li>
 * <li>D/D2（主世界，LV 打粉机）：配置好的能量覆盖板随机器挖下、放回后配置与频道都还在</li>
 * </ul>
 */
public final class EnergyChannelScenario implements SelfCheckScenario {

    private static final String CHANNEL = "能量自检频道";
    private static final int X = 120;
    private static final int Z = 20;
    private static final int OVERWORLD_Y = 200;
    private static final int NETHER_Y = 64;

    private static final long S_EU = 200000;
    private static final long T_EU = 1000;
    private static final long G_EU = 500;
    private static final long Q_EU = 1000;

    private static final int S_X = X;
    private static final int T_X = X + 2;
    private static final int G_X = X + 4;
    private static final int Q_X = X + 6;
    private static final int W_X = X + 8;
    private static final int N_X = X + 10;
    private static final int D_X = X + 12;
    private static final int D2_X = X + 14;
    private static final int L_X = X;
    private static final int K_X = X + 2;

    @Override
    public String name() {
        return "energy_channel";
    }

    @Override
    public void setup() {
        WorldServer overworld = world(0);
        WorldServer nether = world(-1);
        // 下雨会让露天的 GT 电器随机爆炸，与本场景要验证的「直灌不爆炸」无关，先排除
        clearWeather(overworld);

        IGregTechTileEntity s = placeMachine(overworld, S_X, OVERWORLD_Y, Z, ItemList.Battery_Buffer_1by1_IV.get(1));
        IGregTechTileEntity t = placeMachine(overworld, T_X, OVERWORLD_Y, Z, ItemList.Transformer_HV_MV.get(1));
        IGregTechTileEntity g = placeMachine(overworld, G_X, OVERWORLD_Y, Z, ItemList.Generator_Diesel_LV.get(1));
        IGregTechTileEntity q = placeMachine(overworld, Q_X, OVERWORLD_Y, Z, ItemList.Machine_LV_Macerator.get(1));
        IGregTechTileEntity l = placeMachine(nether, L_X, NETHER_Y, Z, ItemList.Machine_LV_Macerator.get(1));
        IGregTechTileEntity k = placeMachine(nether, K_X, NETHER_Y, Z, ItemList.Battery_Buffer_1by1_LV.get(1));
        // 下界机器四周可能有岩浆（GT 电器挨着岩浆会着火爆炸），用石头围起来
        encase(nether, L_X, NETHER_Y, Z);
        encase(nether, K_X, NETHER_Y, Z);

        setEU(s, S_EU);
        setEU(t, tEu(t));
        setEU(g, gEu(g));
        setEU(q, qEu(q));
        setEU(l, 0);
        setEU(k, 0);
        long demand = l.getEUCapacity() + k.getEUCapacity();
        check("场景前提：T、G 的储能应小于下界两台机器的总容量 " + demand, tEu(t) + gEu(g) < demand && demand < S_EU);
        long totalBefore = total(s, t, g, q, l, k);

        placementRules(overworld, s);

        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, CHANNEL);
        Channel channel = hub.createChannel(ChannelType.ENERGY, CHANNEL);
        EnergyWarpCover inputT = attachEnergyCover(t, ForgeDirection.UP);
        EnergyWarpCover inputG = attachEnergyCover(g, ForgeDirection.UP);
        EnergyWarpCover inputS = attachEnergyCover(s, ForgeDirection.UP);
        EnergyWarpCover inputQ = attachEnergyCover(q, ForgeDirection.UP);
        EnergyWarpCover outputL = attachEnergyCover(l, ForgeDirection.UP);
        EnergyWarpCover outputK = attachEnergyCover(k, ForgeDirection.UP);
        for (EnergyWarpCover cover : new EnergyWarpCover[] { inputT, inputG, inputS, inputQ, outputL, outputK }) {
            cover.connect(channel.id(), "selfcheck");
        }
        inputT.setInteractionState(InteractionState.INPUT);
        inputG.setInteractionState(InteractionState.INPUT);
        inputS.setInteractionState(InteractionState.INPUT);
        inputQ.setInteractionState(InteractionState.INPUT);
        outputL.setInteractionState(InteractionState.OUTPUT);
        outputK.setInteractionState(InteractionState.OUTPUT);

        for (int period = 0; period < 3; period++) {
            runCovers(period * 20L, inputT, inputG, inputS, inputQ);
        }

        String problem = checkEnergy(s, t, g, q, l, k);
        check(
            problem + " | S 状态="
                + hub.statusOf(inputS.endpointId(), 40)
                + " Q 状态="
                + hub.statusOf(inputQ.endpointId(), 40),
            problem == null);
        expect("能量总量（守恒）", totalBefore, total(s, t, g, q, l, k));

        digAndReplace(overworld, hub, channel);
    }

    /** 导线和没有内部储能的方块贴不上能量覆盖板，电池箱贴得上。 */
    private static void placementRules(WorldServer overworld, IGregTechTileEntity battery) {
        IGregTechTileEntity cable = placeMachine(
            overworld,
            W_X,
            OVERWORLD_Y,
            Z,
            GTOreDictUnificator.get(OrePrefixes.cableGt01, Materials.Tin, 1L));
        IGregTechTileEntity tank = placeTank(overworld, N_X, OVERWORLD_Y, Z);
        check("电池箱应能贴能量覆盖板", canPlaceEnergyCover(battery, ForgeDirection.UP));
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            check("导线不应能贴能量覆盖板（" + side + "）", !canPlaceEnergyCover(cable, side));
        }
        check("没有储能的超级储罐不应能贴能量覆盖板", !canPlaceEnergyCover(tank, ForgeDirection.UP));
    }

    /** 检查各机器储能；没问题返回 null。 */
    private static String checkEnergy(IGregTechTileEntity s, IGregTechTileEntity t, IGregTechTileEntity g,
        IGregTechTileEntity q, IGregTechTileEntity l, IGregTechTileEntity k) {
        StringBuilder problems = new StringBuilder();
        long demand = l.getEUCapacity() + k.getEUCapacity();
        expectEu(problems, "L（下界 LV 打粉机，IV 电源直灌）应正好充满", l.getEUCapacity(), l.getStoredEU());
        expectEu(problems, "K（下界 LV 电池箱）应正好充满", k.getEUCapacity(), k.getStoredEU());
        expectEu(problems, "T（变压器）应被抽光", 0, t.getStoredEU());
        expectEu(problems, "G（发电机）应被抽光", 0, g.getStoredEU());
        expectEu(problems, "S（IV 电池箱）只补足剩下的需求", S_EU - (demand - tEu(t) - gEu(g)), s.getStoredEU());
        expectEu(problems, "Q（不向外供电的打粉机）不应被抽", qEu(q), q.getStoredEU());
        return problems.length() == 0 ? null : problems.toString();
    }

    /** 配置好的能量覆盖板随机器挖下来、放回去后，状态和频道都还在。 */
    private static void digAndReplace(WorldServer overworld, ChannelHub hub, Channel channel) {
        // 用不向外供电的打粉机当输入端：留在频道里也不会改变别的机器的储能
        IGregTechTileEntity d = placeMachine(overworld, D_X, OVERWORLD_Y, Z, ItemList.Machine_LV_Macerator.get(1));
        EnergyWarpCover cover = attachEnergyCover(d, ForgeDirection.NORTH);
        cover.connect(channel.id(), "selfcheck");
        cover.setInteractionState(InteractionState.INPUT);
        EndpointConfig before = cover.config()
            .copy();

        ItemStack machine = digMachine(overworld, D_X, OVERWORLD_Y, Z);
        check(
            "机器物品里没有覆盖板数据",
            machine.hasTagCompound() && machine.getTagCompound()
                .hasKey("gt.covers"));
        IGregTechTileEntity d2 = placeMachine(overworld, D2_X, OVERWORLD_Y, Z, machine);
        EnergyWarpCover placed = energyCoverAt(d2, ForgeDirection.NORTH);
        check("放回后能量覆盖板配置不对: " + placed.config(), before.equals(placed.config()));
        check(
            "放回后覆盖板不再记得频道",
            channel.id()
                .equals(placed.channelId()));
        runCovers(1000, placed);
        check("放回后没有重新接入频道", hub.findEndpoint(placed.endpointId()) != null);
    }

    @Override
    public SelfCheckResult verify() {
        WorldServer overworld = world(0);
        WorldServer nether = world(-1);
        IGregTechTileEntity s = tileAt(overworld, S_X, OVERWORLD_Y, Z);
        IGregTechTileEntity t = tileAt(overworld, T_X, OVERWORLD_Y, Z);
        IGregTechTileEntity g = tileAt(overworld, G_X, OVERWORLD_Y, Z);
        IGregTechTileEntity q = tileAt(overworld, Q_X, OVERWORLD_Y, Z);
        IGregTechTileEntity l = tileAt(nether, L_X, NETHER_Y, Z);
        IGregTechTileEntity k = tileAt(nether, K_X, NETHER_Y, Z);
        IGregTechTileEntity d2 = tileAt(overworld, D2_X, OVERWORLD_Y, Z);
        if (l == null || k == null) {
            return SelfCheckResult.fail("重启后下界的 LV 机器不见了（可能爆炸了）");
        }
        if (s == null || t == null || g == null || q == null || d2 == null) {
            return SelfCheckResult.fail("重启后有机器丢失");
        }
        String problem = checkEnergy(s, t, g, q, l, k);
        if (problem != null) {
            return SelfCheckResult.fail("重启后储能不对: " + problem);
        }
        String[] names = { "T", "G", "S", "Q" };
        IGregTechTileEntity[] inputs = { t, g, s, q };
        for (int i = 0; i < inputs.length; i++) {
            EnergyWarpCover cover = coverOrNull(inputs[i], ForgeDirection.UP);
            if (cover == null || cover.state() != InteractionState.INPUT
                || cover.channelId()
                    .isEmpty()) {
                return SelfCheckResult.fail("重启后 " + names[i] + " 的能量覆盖板数据丢失");
            }
        }
        for (IGregTechTileEntity output : new IGregTechTileEntity[] { l, k }) {
            EnergyWarpCover cover = coverOrNull(output, ForgeDirection.UP);
            if (cover == null || cover.state() != InteractionState.OUTPUT) {
                return SelfCheckResult.fail("重启后下界输出端的能量覆盖板数据丢失");
            }
        }
        EnergyWarpCover placed = coverOrNull(d2, ForgeDirection.NORTH);
        if (placed == null || placed.state() != InteractionState.INPUT
            || placed.channelId()
                .isEmpty()) {
            return SelfCheckResult.fail("重启后挖下又放回的能量覆盖板配置丢失");
        }
        return SelfCheckResult.pass("跨维度供电、IV 电源直灌 LV 机器不爆炸、充满即止、只抽供电机器与守恒正确，导线贴不上，覆盖板配置在挖下放回和重启后依然存在");
    }

    private static long tEu(IGregTechTileEntity t) {
        return Math.min(t.getEUCapacity(), T_EU);
    }

    private static long gEu(IGregTechTileEntity g) {
        return Math.min(g.getEUCapacity(), G_EU);
    }

    private static long qEu(IGregTechTileEntity q) {
        return Math.min(q.getEUCapacity(), Q_EU);
    }

    private static void setEU(IGregTechTileEntity tile, long eu) {
        ((MetaTileEntity) tile.getMetaTileEntity()).setEUVar(eu);
        check("储能没设上: " + tile.getInventoryName(), tile.getStoredEU() == eu);
    }

    private static long total(IGregTechTileEntity... tiles) {
        long sum = 0;
        for (IGregTechTileEntity tile : tiles) {
            sum += tile.getStoredEU();
        }
        return sum;
    }

    private static void expectEu(StringBuilder problems, String what, long expected, long actual) {
        if (expected != actual) {
            problems.append(what)
                .append(": 期望 ")
                .append(expected)
                .append(" EU，实际 ")
                .append(actual)
                .append(" EU；");
        }
    }

    private static void clearWeather(WorldServer world) {
        WorldInfo info = world.getWorldInfo();
        info.setRaining(false);
        info.setRainTime(1000000);
        info.setThundering(false);
        info.setThunderTime(1000000);
    }

    /** 把方块六个面的邻居换成石头。 */
    private static void encase(World world, int x, int y, int z) {
        for (ForgeDirection side : ForgeDirection.VALID_DIRECTIONS) {
            world.setBlock(x + side.offsetX, y + side.offsetY, z + side.offsetZ, Blocks.stone);
        }
    }

    private static EnergyWarpCover coverOrNull(IGregTechTileEntity tile, ForgeDirection side) {
        Cover cover = tile.getCoverAtSide(side);
        return cover instanceof EnergyWarpCover ? (EnergyWarpCover) cover : null;
    }
}
