package com.rtgame.warpcovers.gt;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.gtnhlib.capability.item.ItemSink;
import com.gtnewhorizon.gtnhlib.capability.item.ItemSource;
import com.gtnewhorizon.gtnhlib.item.ImmutableItemStack;
import com.gtnewhorizon.gtnhlib.item.InsertionItemStack;
import com.gtnewhorizon.gtnhlib.item.InventoryItemSource;
import com.gtnewhorizon.gtnhlib.item.InventoryIterator;
import com.gtnewhorizon.gtnhlib.item.StandardInventoryIterator;
import com.gtnewhorizon.gtnhlib.util.ItemUtil;
import com.rtgame.warpcovers.core.MachinePort;
import com.rtgame.warpcovers.core.ObjectStack;

import gregtech.api.interfaces.tileentity.ICoverable;

/**
 * 把 GT 方块（被覆盖板贴着的那一面）包装成物品机器端口。
 * 抽取和放入都走 GT 方块该面的物品接口（GTNHLib 的 ItemSource / ItemSink），哪些槽能抽、能放由方块自己的
 * 面规则决定，与 GT 传送带覆盖板一致。
 */
public final class GtItemPort implements MachinePort {

    private final WeakReference<ICoverable> tile;
    private final ForgeDirection side;
    private final String blockId;

    /** 最近一次实际抽取的来源位置，供紧接着的退回原样放回（例如 GT 机器只出不进的输出槽）。 */
    private String lastTakenKey;
    private final List<int[]> lastTaken = new ArrayList<int[]>();

    public GtItemPort(ICoverable tile, ForgeDirection side) {
        this.tile = new WeakReference<ICoverable>(tile);
        this.side = side;
        this.blockId = tile.getWorld().provider.dimensionId + ":"
            + tile.getXCoord()
            + ","
            + tile.getYCoord()
            + ","
            + tile.getZCoord();
    }

    @Override
    public String blockId() {
        return blockId;
    }

    @Override
    public boolean isAlive() {
        ICoverable coverable = tile.get();
        return coverable != null && !coverable.isDead() && !coverable.isInvalidTileEntity();
    }

    @Override
    public String machineName() {
        ICoverable coverable = tile.get();
        return coverable == null ? "" : coverable.getInventoryName();
    }

    // ---- 抽取 ----

    @Override
    public List<ObjectStack> extractable() {
        Map<String, Long> totals = new LinkedHashMap<String, Long>();
        InventoryIterator it = sourceIterator();
        while (it != null && it.hasNext()) {
            ImmutableItemStack stack = it.next();
            if (!canTake(it, stack)) {
                continue;
            }
            String key = ItemKeys.of(stack.getItem(), stack.getItemMeta(), stack.getTag());
            if (key != null) {
                Long sum = totals.get(key);
                totals.put(key, (sum == null ? 0L : sum) + stack.getStackSize());
            }
        }
        List<ObjectStack> result = new ArrayList<ObjectStack>(totals.size());
        for (Map.Entry<String, Long> entry : totals.entrySet()) {
            result.add(new ObjectStack(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    @Override
    public long extract(String key, long amount, boolean simulate) {
        if (!simulate) {
            lastTakenKey = key;
            lastTaken.clear();
        }
        if (amount <= 0) {
            return 0;
        }
        long taken = 0;
        InventoryIterator it = sourceIterator();
        // 自己数迭代位置：GTNHLib 迭代器的 previousIndex/nextIndex 返回的是相邻槽号，不是位置
        int position = -1;
        while (it != null && it.hasNext() && taken < amount) {
            ImmutableItemStack stack = it.next();
            position++;
            if (!canTake(it, stack) || !key.equals(ItemKeys.of(stack.getItem(), stack.getItemMeta(), stack.getTag()))) {
                continue;
            }
            int want = (int) Math.min(stack.getStackSize(), amount - taken);
            if (simulate) {
                taken += want;
                continue;
            }
            ItemStack got = it.extract(want, false);
            if (got != null && got.stackSize > 0) {
                taken += got.stackSize;
                lastTaken.add(new int[] { position, got.stackSize });
            }
        }
        return taken;
    }

    /** 迭代器当前位置是否有能从这一面抽出的物品。 */
    private static boolean canTake(InventoryIterator it, ImmutableItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return !(it instanceof CheckedIterator) || ((CheckedIterator) it).currentExtractable();
    }

    private InventoryIterator sourceIterator() {
        ICoverable coverable = tile.get();
        if (coverable == null) {
            return null;
        }
        ItemSource source = ItemUtil.getItemSource(coverable, side);
        if (source == null) {
            return null;
        }
        if (source.getClass() == InventoryItemSource.class) {
            // 普通物品栏：自己建迭代器，好在统计可抽数量时就排除面规则不许抽的槽（例如 GT 机器的输入槽）
            InventoryItemSource inventory = (InventoryItemSource) source;
            return new CheckedIterator(inventory.inv, inventory.side);
        }
        return source.sourceIterator();
    }

    // ---- 放入 ----

    @Override
    public long insert(String key, long amount, boolean simulate) {
        ItemStack proto = ItemKeys.prototype(key);
        ItemSink sink = sink();
        if (proto == null || sink == null || amount <= 0) {
            return 0;
        }
        int n = (int) Math.min(Integer.MAX_VALUE, amount);
        if (!simulate) {
            return n - sink.store(new InsertionItemStack(proto, n));
        }
        InventoryIterator it = sink.simulatedSinkIterator();
        if (it == null) {
            // 不支持模拟的物品接口：宁可不送，也不冒险送出后放不进
            return 0;
        }
        // 模拟迭代器不能 rewind、也不真正写入，只走一遍：逐槽累计还能放多少（同种物品的槽与空槽都算，
        // 迭代器的 insert 自己会检查面规则、物品是否相同和槽位上限），总容量与放入顺序无关
        int rest = n;
        while (rest > 0 && it.hasNext()) {
            it.next();
            rest = it.insert(new InsertionItemStack(proto, rest), false);
        }
        return n - rest;
    }

    private ItemSink sink() {
        ICoverable coverable = tile.get();
        return coverable == null ? null : ItemUtil.getItemSink(coverable, side);
    }

    // ---- 退回 ----

    /** 依次尝试：放回刚抽出的原槽位 → 按面规则正常放入 → 掉落在方块旁。物品不会凭空消失。 */
    @Override
    public long restore(String key, long amount) {
        ItemStack proto = ItemKeys.prototype(key);
        if (proto == null || amount <= 0) {
            return 0;
        }
        long rest = amount;
        if (key.equals(lastTakenKey)) {
            rest = putBack(proto, rest);
        }
        lastTakenKey = null;
        lastTaken.clear();
        if (rest > 0) {
            rest -= insert(key, rest, false);
        }
        if (rest > 0) {
            rest -= drop(proto, rest);
        }
        return amount - rest;
    }

    private long putBack(ItemStack proto, long amount) {
        long rest = amount;
        for (int[] record : lastTaken) {
            if (rest <= 0) {
                break;
            }
            InventoryIterator it = sourceIterator();
            if (it == null) {
                break;
            }
            int position = -1;
            while (position < record[0] && it.hasNext()) {
                it.next();
                position++;
            }
            if (position != record[0]) {
                continue;
            }
            int n = (int) Math.min(rest, record[1]);
            int left = it.insert(new InsertionItemStack(proto, n), true);
            rest -= n - left;
        }
        return rest;
    }

    private long drop(ItemStack proto, long amount) {
        ICoverable coverable = tile.get();
        World world = coverable == null ? null : coverable.getWorld();
        if (world == null || world.isRemote) {
            return 0;
        }
        double x = coverable.getXCoord() + 0.5 + side.offsetX * 0.75;
        double y = coverable.getYCoord() + 0.5 + side.offsetY * 0.75;
        double z = coverable.getZCoord() + 0.5 + side.offsetZ * 0.75;
        int max = Math.max(1, proto.getMaxStackSize());
        long dropped = 0;
        while (dropped < amount) {
            ItemStack stack = proto.copy();
            stack.stackSize = (int) Math.min(max, amount - dropped);
            world.spawnEntityInWorld(new EntityItem(world, x, y, z, stack));
            dropped += stack.stackSize;
        }
        return dropped;
    }

    /** 能判断「这一面能不能抽这个槽」的物品栏迭代器（基类的检查方法是 protected，只能在子类里调用）。 */
    private static final class CheckedIterator extends StandardInventoryIterator {

        CheckedIterator(IInventory inv, ForgeDirection side) {
            super(inv, side, StandardInventoryIterator.getInventorySlotIndices(inv, side));
        }

        boolean currentExtractable() {
            int slot = getCurrentSlot();
            ItemStack stack = getStackInSlot(slot);
            return stack != null && stack.stackSize > 0 && canExtract(stack, slot);
        }
    }
}
