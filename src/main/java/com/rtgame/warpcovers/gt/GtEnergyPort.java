package com.rtgame.warpcovers.gt;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.rtgame.warpcovers.core.MachinePort;
import com.rtgame.warpcovers.core.ObjectStack;

import gregtech.api.interfaces.tileentity.IBasicEnergyContainer;
import gregtech.api.interfaces.tileentity.ICoverable;

/**
 * 把 GT 机器的内部储能包装成能量端口（ADR-0003 能量安全直灌）。
 * <ul>
 * <li>只有会向外供电的机器能被抽取，抽取量为 min（配额，储量）；</li>
 * <li>充入直接加进内部储能，最多充满剩余容量，不检查电压和电流，不会爆炸，也没有损耗。</li>
 * </ul>
 * 能量不分面：覆盖板贴在哪一面都操作同一份内部储能。
 */
final class GtEnergyPort implements MachinePort {

    /** 能量只有一种物体键。 */
    static final String EU = "eu";

    private final String blockId;
    private final EnergyStore store;

    GtEnergyPort(String blockId, EnergyStore store) {
        this.blockId = blockId;
        this.store = store;
    }

    static GtEnergyPort of(ICoverable tile) {
        String blockId = tile.getWorld().provider.dimensionId + ":"
            + tile.getXCoord()
            + ","
            + tile.getYCoord()
            + ","
            + tile.getZCoord();
        return new GtEnergyPort(blockId, new GtStore(tile));
    }

    /** 能否贴能量覆盖板：方块要有内部储能（导线、管道没有）。 */
    static boolean hasEnergyStorage(ICoverable tile) {
        return tile instanceof IBasicEnergyContainer && ((IBasicEnergyContainer) tile).getEUCapacity() > 0;
    }

    @Override
    public String blockId() {
        return blockId;
    }

    @Override
    public boolean isAlive() {
        return store.isAlive();
    }

    @Override
    public String machineName() {
        return store.machineName();
    }

    @Override
    public List<ObjectStack> extractable() {
        if (!store.isAlive() || !store.suppliesEnergy()) {
            return Collections.emptyList();
        }
        long stored = store.stored();
        if (stored <= 0) {
            return Collections.emptyList();
        }
        List<ObjectStack> result = new ArrayList<ObjectStack>(1);
        result.add(new ObjectStack(EU, stored));
        return result;
    }

    @Override
    public long extract(String key, long amount, boolean simulate) {
        if (!EU.equals(key) || amount <= 0 || !store.isAlive() || !store.suppliesEnergy()) {
            return 0;
        }
        long n = Math.min(amount, Math.max(0, store.stored()));
        if (n <= 0) {
            return 0;
        }
        return simulate ? n : store.take(n);
    }

    @Override
    public long insert(String key, long amount, boolean simulate) {
        if (!EU.equals(key) || amount <= 0 || !store.isAlive()) {
            return 0;
        }
        long n = Math.min(amount, Math.max(0, store.capacity() - store.stored()));
        if (n <= 0) {
            return 0;
        }
        if (!simulate) {
            store.add(n);
        }
        return n;
    }

    /** 退回刚抽出的能量：直接加回，不受剩余容量限制（抽之前本来就存得下），保证不丢失。 */
    @Override
    public long restore(String key, long amount) {
        if (!EU.equals(key) || amount <= 0 || !store.isAlive()) {
            return 0;
        }
        store.add(amount);
        return amount;
    }

    /** 基于 GT 方块 {@link IBasicEnergyContainer} 的储能操作；弱引用，机器被移除后端口自然失效。 */
    private static final class GtStore implements EnergyStore {

        private final WeakReference<ICoverable> tile;

        GtStore(ICoverable tile) {
            this.tile = new WeakReference<ICoverable>(tile);
        }

        private IBasicEnergyContainer container() {
            ICoverable coverable = tile.get();
            if (coverable == null || coverable.isDead() || coverable.isInvalidTileEntity()) {
                return null;
            }
            return coverable instanceof IBasicEnergyContainer ? (IBasicEnergyContainer) coverable : null;
        }

        @Override
        public boolean isAlive() {
            return container() != null;
        }

        @Override
        public String machineName() {
            ICoverable coverable = tile.get();
            return coverable == null ? "" : coverable.getInventoryName();
        }

        @Override
        public long stored() {
            IBasicEnergyContainer container = container();
            return container == null ? 0 : container.getStoredEU();
        }

        @Override
        public long capacity() {
            IBasicEnergyContainer container = container();
            return container == null ? 0 : container.getEUCapacity();
        }

        @Override
        public boolean suppliesEnergy() {
            IBasicEnergyContainer container = container();
            return container != null && container.getOutputVoltage() > 0;
        }

        @Override
        public void add(long amount) {
            IBasicEnergyContainer container = container();
            if (container != null) {
                // ignoreTooMuch=true：不按电压判断、不爆炸，调用方已按剩余容量限量
                container.increaseStoredEnergyUnits(amount, true);
            }
        }

        @Override
        public long take(long amount) {
            IBasicEnergyContainer container = container();
            if (container == null) {
                return 0;
            }
            long before = container.getStoredEU();
            long n = Math.min(amount, before);
            if (n <= 0 || !container.decreaseStoredEnergyUnits(n, false)) {
                return 0;
            }
            return n;
        }
    }
}
