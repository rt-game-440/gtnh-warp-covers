package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.capability.item.ItemSink;
import com.gtnewhorizon.gtnhlib.capability.item.ItemSource;
import com.gtnewhorizon.gtnhlib.util.ItemUtil;
import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.FilterList;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.gt.GtItemPort;
import com.rtgame.warpcovers.gt.ItemKeys;
import com.rtgame.warpcovers.gt.ItemWarpCover;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEBasicMachine;
import gregtech.common.covers.Cover;

/**
 * 物品频道直通场景：主世界与下界各放 GT 箱式缓存，贴物品折跃覆盖板，检查跨维度传输、ID+损伤值匹配、
 * 「匹配 NBT」开关、同方块自环、GT 面规则与总量守恒；挖下机器再放回后覆盖板配置保留；服务器重启后再检查一遍。
 * <p>
 * 布置（频道「物品自检频道」，连接顺序即下面的顺序）：
 * <ul>
 * <li>A（主世界，箱式缓存）：顶面输入端，白名单 {白色羊毛, 木棍}，匹配 NBT 关。内有白色羊毛、橙色羊毛（同 ID 不同损伤值，应被挡）、
 * 木棍、改名木棍（NBT 不同，匹配 NBT 关时应放行）。底面输出端，白名单 {白色羊毛}，用来验证同方块自环被跳过</li>
 * <li>A2（主世界，箱式缓存）：顶面输入端，白名单 {木棍}，匹配 NBT 开。普通木棍应传走，改名木棍应留下</li>
 * <li>M（主世界，LV 打粉机）：顶面输入端，空黑名单。输入槽的铁锭按 GT 面规则不能抽，输出槽的燧石可以抽</li>
 * <li>C（主世界，箱式缓存）：背面（缓存不许从背面放入）贴输出端，空黑名单，应收不到任何东西</li>
 * <li>B（下界，箱式缓存）：顶面输出端，空黑名单，应收到全部放行的物品</li>
 * <li>D/D2（主世界）：配置好的物品覆盖板随机器挖下、放回后配置与频道都还在</li>
 * </ul>
 */
public final class ItemChannelScenario implements SelfCheckScenario {

    private static final String CHANNEL = "物品自检频道";
    private static final int X = 80;
    private static final int Z = 20;
    private static final int OVERWORLD_Y = 200;
    private static final int NETHER_Y = 64;

    private static final int WHITE_WOOL = 100;
    private static final int ORANGE_WOOL = 30;
    private static final int A_STICKS = 20;
    private static final int A_NAMED_STICKS = 6;
    private static final int A2_STICKS = 10;
    private static final int A2_NAMED_STICKS = 4;
    private static final int M_INGOTS = 8;
    private static final int M_FLINT = 16;

    private static final int A_X = X;
    private static final int A2_X = X + 2;
    private static final int M_X = X + 4;
    private static final int C_X = X + 6;
    private static final int D_X = X + 8;
    private static final int D2_X = X + 10;
    private static final int E_X = X + 12;
    private static final int F_X = X + 14;
    private static final int B_X = X;

    @Override
    public String name() {
        return "item_channel";
    }

    private static ItemStack namedStick(int count) {
        ItemStack stack = new ItemStack(Items.stick, count);
        stack.setStackDisplayName("自检木棍");
        return stack;
    }

    private static String whiteWool() {
        return ItemKeys.of(new ItemStack(Blocks.wool, 1, 0));
    }

    private static String orangeWool() {
        return ItemKeys.of(new ItemStack(Blocks.wool, 1, 1));
    }

    private static String stick() {
        return ItemKeys.of(new ItemStack(Items.stick));
    }

    private static String namedStick() {
        return ItemKeys.of(namedStick(1));
    }

    private static String ingot() {
        return ItemKeys.of(new ItemStack(Items.iron_ingot));
    }

    private static String flint() {
        return ItemKeys.of(new ItemStack(Items.flint));
    }

    @Override
    public void setup() {
        WorldServer overworld = world(0);
        WorldServer nether = world(-1);
        IGregTechTileEntity a = placeMachine(overworld, A_X, OVERWORLD_Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        IGregTechTileEntity a2 = placeMachine(
            overworld,
            A2_X,
            OVERWORLD_Y,
            Z,
            ItemList.Automation_ChestBuffer_LV.get(1));
        IGregTechTileEntity m = placeMachine(overworld, M_X, OVERWORLD_Y, Z, ItemList.Machine_LV_Macerator.get(1));
        IGregTechTileEntity c = placeMachine(overworld, C_X, OVERWORLD_Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        IGregTechTileEntity b = placeMachine(nether, B_X, NETHER_Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));

        // 箱式缓存不许从背面放入：A、A2、B 背面朝北/朝下以外的方向，C 的背面朝上（=贴输出端的那一面）
        a.setFrontFacing(ForgeDirection.NORTH);
        a2.setFrontFacing(ForgeDirection.NORTH);
        b.setFrontFacing(ForgeDirection.UP);
        c.setFrontFacing(ForgeDirection.DOWN);
        check("C 的背面应朝上", c.getBackFacing() == ForgeDirection.UP);
        m.setFrontFacing(ForgeDirection.NORTH);

        a.setInventorySlotContents(0, new ItemStack(Blocks.wool, 64, 0));
        a.setInventorySlotContents(1, new ItemStack(Blocks.wool, WHITE_WOOL - 64, 0));
        a.setInventorySlotContents(2, new ItemStack(Blocks.wool, ORANGE_WOOL, 1));
        a.setInventorySlotContents(3, new ItemStack(Items.stick, A_STICKS));
        a.setInventorySlotContents(4, namedStick(A_NAMED_STICKS));
        a2.setInventorySlotContents(0, new ItemStack(Items.stick, A2_STICKS));
        a2.setInventorySlotContents(1, namedStick(A2_NAMED_STICKS));
        MTEBasicMachine macerator = (MTEBasicMachine) m.getMetaTileEntity();
        m.setInventorySlotContents(macerator.getInputSlot(), new ItemStack(Items.iron_ingot, M_INGOTS));
        m.setInventorySlotContents(macerator.getOutputSlot(), new ItemStack(Items.flint, M_FLINT));

        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, CHANNEL);
        Channel channel = hub.createChannel(ChannelType.ITEM, CHANNEL);

        ItemWarpCover inputA = attachItemCover(a, ForgeDirection.UP);
        ItemWarpCover selfOutA = attachItemCover(a, ForgeDirection.DOWN);
        ItemWarpCover inputA2 = attachItemCover(a2, ForgeDirection.UP);
        ItemWarpCover inputM = attachItemCover(m, ForgeDirection.UP);
        ItemWarpCover outputC = attachItemCover(c, ForgeDirection.UP);
        ItemWarpCover outputB = attachItemCover(b, ForgeDirection.UP);
        check(
            "新覆盖板的匹配 NBT 应默认关闭",
            !inputA.filter()
                .matchNbt());

        for (ItemWarpCover cover : new ItemWarpCover[] { inputA, selfOutA, inputA2, inputM, outputC, outputB }) {
            cover.connect(channel.id(), "selfcheck");
        }
        inputA.filter()
            .add(whiteWool());
        inputA.filter()
            .add(stick());
        selfOutA.filter()
            .add(whiteWool());
        inputA2.filter()
            .add(stick());
        inputA2.filter()
            .setMatchNbt(true);
        inputM.filter()
            .setWhitelist(false);
        outputC.filter()
            .setWhitelist(false);
        outputB.filter()
            .setWhitelist(false);

        inputA.setInteractionState(InteractionState.INPUT);
        selfOutA.setInteractionState(InteractionState.OUTPUT);
        inputA2.setInteractionState(InteractionState.INPUT);
        inputM.setInteractionState(InteractionState.INPUT);
        outputC.setInteractionState(InteractionState.OUTPUT);
        outputB.setInteractionState(InteractionState.OUTPUT);

        for (int period = 0; period < 3; period++) {
            runCovers(period * 20L, inputA, inputA2, inputM);
        }

        String problem = checkContents(a, a2, m, c, b);
        check(problem + diagnostics(hub, inputA, a, b), problem == null);

        digAndReplace(overworld, hub, channel);
        restoreNeverLoses(overworld);
    }

    /** 检查各机器里的物品数量；没问题返回 null。 */
    private static String checkContents(IGregTechTileEntity a, IGregTechTileEntity a2, IGregTechTileEntity m,
        IGregTechTileEntity c, IGregTechTileEntity b) {
        StringBuilder problems = new StringBuilder();
        expectCount(problems, "A 里的白色羊毛（应全部传走，且没有自环回 A）", 0, count(a, whiteWool()));
        expectCount(problems, "A 里的橙色羊毛（损伤值不同，被白名单挡住）", ORANGE_WOOL, count(a, orangeWool()));
        expectCount(problems, "A 里的木棍", 0, count(a, stick()));
        expectCount(problems, "A 里的改名木棍（匹配 NBT 关，应放行）", 0, count(a, namedStick()));
        expectCount(problems, "A2 里的木棍", 0, count(a2, stick()));
        expectCount(problems, "A2 里的改名木棍（匹配 NBT 开，应被挡住）", A2_NAMED_STICKS, count(a2, namedStick()));
        expectCount(problems, "M 输入槽里的铁锭（面规则不许抽）", M_INGOTS, count(m, ingot()));
        expectCount(problems, "M 里的燧石", 0, count(m, flint()));
        expectCount(problems, "C（背面）里的物品", 0, total(c));
        expectCount(problems, "B（下界）里的白色羊毛", WHITE_WOOL, count(b, whiteWool()));
        expectCount(problems, "B 里的木棍", A_STICKS + A2_STICKS, count(b, stick()));
        expectCount(problems, "B 里的改名木棍", A_NAMED_STICKS, count(b, namedStick()));
        expectCount(problems, "B 里的燧石", M_FLINT, count(b, flint()));
        expectCount(problems, "B 里的物品总数", WHITE_WOOL + A_STICKS + A2_STICKS + A_NAMED_STICKS + M_FLINT, total(b));
        long all = total(a) + total(a2) + total(m) + total(c) + total(b);
        expectCount(
            problems,
            "物品总数（守恒）",
            WHITE_WOOL + ORANGE_WOOL + A_STICKS + A_NAMED_STICKS + A2_STICKS + A2_NAMED_STICKS + M_INGOTS + M_FLINT,
            all);
        return problems.length() == 0 ? null : problems.toString();
    }

    /** 配置好的物品覆盖板随机器挖下来、放回去后，名单、匹配 NBT 开关和频道都还在。 */
    private static void digAndReplace(WorldServer overworld, ChannelHub hub, Channel channel) {
        IGregTechTileEntity d = placeMachine(overworld, D_X, OVERWORLD_Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        ItemWarpCover cover = attachItemCover(d, ForgeDirection.UP);
        cover.connect(channel.id(), "selfcheck");
        cover.filter()
            .add(namedStick());
        cover.filter()
            .setMatchNbt(true);
        cover.filter()
            .setWhitelist(false);
        cover.setInteractionState(InteractionState.INPUT);
        EndpointConfig before = cover.config()
            .copy();

        ItemStack machine = digMachine(overworld, D_X, OVERWORLD_Y, Z);
        check(
            "机器物品里没有覆盖板数据",
            machine.hasTagCompound() && machine.getTagCompound()
                .hasKey("gt.covers"));
        IGregTechTileEntity d2 = placeMachine(overworld, D2_X, OVERWORLD_Y, Z, machine);
        ItemWarpCover placed = itemCoverAt(d2, ForgeDirection.UP);
        check("放回后物品覆盖板配置不对: " + placed.config(), before.equals(placed.config()));
        check(
            "放回后覆盖板不再记得频道",
            channel.id()
                .equals(placed.channelId()));
        runCovers(1000, placed);
        check("放回后没有重新接入频道", hub.findEndpoint(placed.endpointId()) != null);
    }

    /**
     * 退回不丢物品：GT 机器输出槽只出不进，抽出后送不出去时必须放回原槽；机器放不下时掉在方块旁。
     * （E 与 F 只在布置阶段使用，检查完就拆掉，不参与重启后的校验。）
     */
    private static void restoreNeverLoses(WorldServer overworld) {
        IGregTechTileEntity e = placeMachine(overworld, E_X, OVERWORLD_Y, Z, ItemList.Machine_LV_Macerator.get(1));
        e.setFrontFacing(ForgeDirection.NORTH);
        MTEBasicMachine macerator = (MTEBasicMachine) e.getMetaTileEntity();
        e.setInventorySlotContents(macerator.getOutputSlot(), new ItemStack(Items.flint, M_FLINT));
        GtItemPort port = new GtItemPort(e, ForgeDirection.UP);
        check("输出槽应能被抽出 5 个燧石", port.extract(flint(), 5, false) == 5);
        check("退回应全部算数", port.restore(flint(), 5) == 5);
        ItemStack output = e.getStackInSlot(macerator.getOutputSlot());
        check("燧石没有放回输出槽: " + output, output != null && output.getItem() == Items.flint && output.stackSize == M_FLINT);
        check("输出槽退回不应掉落物品", droppedNear(overworld, E_X, OVERWORLD_Y, Z) == 0);
        // 拆掉方块时 GT 会把里面的东西掉出来，一并清掉
        overworld.setBlockToAir(E_X, OVERWORLD_Y, Z);
        clearDroppedNear(overworld, E_X, OVERWORLD_Y, Z);

        IGregTechTileEntity f = placeMachine(overworld, F_X, OVERWORLD_Y, Z, ItemList.Automation_ChestBuffer_LV.get(1));
        f.setFrontFacing(ForgeDirection.NORTH);
        for (int slot = 0; slot < f.getSizeInventory(); slot++) {
            f.setInventorySlotContents(slot, new ItemStack(Blocks.cobblestone, 64));
        }
        GtItemPort full = new GtItemPort(f, ForgeDirection.UP);
        check("放满的缓存不应还能放入木棍", full.insert(stick(), 3, true) == 0);
        check("放不下时退回也应全部算数（掉落）", full.restore(stick(), 3) == 3);
        check("放不下的木棍应掉在方块旁", droppedNear(overworld, F_X, OVERWORLD_Y, Z) == 3);
        overworld.setBlockToAir(F_X, OVERWORLD_Y, Z);
        clearDroppedNear(overworld, F_X, OVERWORLD_Y, Z);
    }

    private static List<EntityItem> itemsNear(WorldServer world, int x, int y, int z) {
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(x - 1, y - 1, z - 1, x + 2, y + 2, z + 2);
        List<?> found = world.getEntitiesWithinAABB(EntityItem.class, box);
        List<EntityItem> items = new ArrayList<EntityItem>();
        for (Object entity : found) {
            items.add((EntityItem) entity);
        }
        return items;
    }

    private static int droppedNear(WorldServer world, int x, int y, int z) {
        int sum = 0;
        for (EntityItem item : itemsNear(world, x, y, z)) {
            sum += item.getEntityItem().stackSize;
        }
        return sum;
    }

    private static void clearDroppedNear(WorldServer world, int x, int y, int z) {
        for (EntityItem item : itemsNear(world, x, y, z)) {
            item.setDead();
        }
    }

    @Override
    public SelfCheckResult verify() {
        WorldServer overworld = world(0);
        WorldServer nether = world(-1);
        IGregTechTileEntity a = tileAt(overworld, A_X, OVERWORLD_Y, Z);
        IGregTechTileEntity a2 = tileAt(overworld, A2_X, OVERWORLD_Y, Z);
        IGregTechTileEntity m = tileAt(overworld, M_X, OVERWORLD_Y, Z);
        IGregTechTileEntity c = tileAt(overworld, C_X, OVERWORLD_Y, Z);
        IGregTechTileEntity b = tileAt(nether, B_X, NETHER_Y, Z);
        IGregTechTileEntity d2 = tileAt(overworld, D2_X, OVERWORLD_Y, Z);
        if (a == null || a2 == null || m == null || c == null || b == null || d2 == null) {
            return SelfCheckResult.fail("重启后有机器丢失");
        }
        String problem = checkContents(a, a2, m, c, b);
        if (problem != null) {
            return SelfCheckResult.fail("重启后物品数量不对: " + problem);
        }

        ItemWarpCover inputA = coverOrNull(a, ForgeDirection.UP);
        if (inputA == null || inputA.state() != InteractionState.INPUT
            || inputA.channelId()
                .isEmpty()
            || !sameFilter(inputA.filter(), true, false, whiteWool(), stick())) {
            return SelfCheckResult.fail("重启后 A 顶面的物品覆盖板数据丢失: " + describe(inputA));
        }
        ItemWarpCover inputA2 = coverOrNull(a2, ForgeDirection.UP);
        if (inputA2 == null || !sameFilter(inputA2.filter(), true, true, stick())) {
            return SelfCheckResult.fail("重启后 A2 的匹配 NBT 开关或名单丢失: " + describe(inputA2));
        }
        ItemWarpCover placed = coverOrNull(d2, ForgeDirection.UP);
        if (placed == null || placed.state() != InteractionState.INPUT
            || !sameFilter(placed.filter(), false, true, namedStick())) {
            return SelfCheckResult.fail("重启后挖下又放回的物品覆盖板配置丢失: " + describe(placed));
        }
        ItemWarpCover outputB = coverOrNull(b, ForgeDirection.UP);
        if (outputB == null || outputB.state() != InteractionState.OUTPUT) {
            return SelfCheckResult.fail("重启后 B 顶面的输出端覆盖板数据丢失");
        }
        return SelfCheckResult.pass("跨维度物品传输、ID/损伤值/NBT 匹配、面规则与守恒正确，覆盖板配置在挖下放回和重启后依然存在");
    }

    /** 传输失败时附带的诊断：输入端状态、两端的 GTNHLib 物品接口。 */
    private static String diagnostics(ChannelHub hub, ItemWarpCover input, IGregTechTileEntity from,
        IGregTechTileEntity to) {
        ItemSource source = ItemUtil.getItemSource(from, ForgeDirection.UP);
        ItemSink sink = ItemUtil.getItemSink(to, ForgeDirection.UP);
        return " | 输入端状态=" + hub.statusOf(input.endpointId(), 40)
            + " 来源="
            + (source == null ? "null"
                : source.getClass()
                    .getName())
            + " 去处="
            + (sink == null ? "null"
                : sink.getClass()
                    .getName())
            + " 模拟迭代器="
            + (sink == null ? "-" : String.valueOf(sink.simulatedSinkIterator() != null));
    }

    private static ItemWarpCover coverOrNull(IGregTechTileEntity tile, ForgeDirection side) {
        Cover cover = tile.getCoverAtSide(side);
        return cover instanceof ItemWarpCover ? (ItemWarpCover) cover : null;
    }

    private static boolean sameFilter(FilterList filter, boolean whitelist, boolean matchNbt, String... keys) {
        if (filter.isWhitelist() != whitelist || filter.matchNbt() != matchNbt
            || filter.keys()
                .size() != keys.length) {
            return false;
        }
        for (String key : keys) {
            if (!filter.keys()
                .contains(key)) {
                return false;
            }
        }
        return true;
    }

    private static String describe(ItemWarpCover cover) {
        return cover == null ? "没有覆盖板"
            : "状态=" + cover.state()
                + " 白名单="
                + cover.filter()
                    .isWhitelist()
                + " 匹配NBT="
                + cover.filter()
                    .matchNbt()
                + " 名单="
                + cover.filter()
                    .keys();
    }

    /** 机器各槽里指定物品键的总数。 */
    private static long count(IGregTechTileEntity tile, String key) {
        long sum = 0;
        for (int slot = 0; slot < tile.getSizeInventory(); slot++) {
            ItemStack stack = tile.getStackInSlot(slot);
            if (stack != null && stack.stackSize > 0 && key.equals(ItemKeys.of(stack))) {
                sum += stack.stackSize;
            }
        }
        return sum;
    }

    private static long total(IGregTechTileEntity tile) {
        long sum = 0;
        for (int slot = 0; slot < tile.getSizeInventory(); slot++) {
            ItemStack stack = tile.getStackInSlot(slot);
            if (stack != null && stack.stackSize > 0) {
                sum += stack.stackSize;
            }
        }
        return sum;
    }

    private static void expectCount(StringBuilder problems, String what, long expected, long actual) {
        if (expected != actual) {
            problems.append(what)
                .append(": 期望 ")
                .append(expected)
                .append(" 实际 ")
                .append(actual)
                .append("; ");
        }
    }
}
