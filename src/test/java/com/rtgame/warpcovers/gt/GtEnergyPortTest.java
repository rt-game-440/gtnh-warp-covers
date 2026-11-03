package com.rtgame.warpcovers.gt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.rtgame.warpcovers.core.ObjectStack;

class GtEnergyPortTest {

    /** 假储能：记录充入与扣除，扣除永远成功（扣不到负数）。 */
    private static final class FakeStore implements EnergyStore {

        long stored;
        long capacity;
        boolean supplies;
        boolean alive = true;

        FakeStore(long stored, long capacity, boolean supplies) {
            this.stored = stored;
            this.capacity = capacity;
            this.supplies = supplies;
        }

        @Override
        public boolean isAlive() {
            return alive;
        }

        @Override
        public String machineName() {
            return "假机器";
        }

        @Override
        public long stored() {
            return stored;
        }

        @Override
        public long capacity() {
            return capacity;
        }

        @Override
        public boolean suppliesEnergy() {
            return supplies;
        }

        @Override
        public void add(long amount) {
            stored += amount;
        }

        @Override
        public long take(long amount) {
            long n = Math.min(amount, stored);
            stored -= n;
            return n;
        }
    }

    private static GtEnergyPort port(FakeStore store) {
        return new GtEnergyPort("0:0,0,0", store);
    }

    @Test
    void 供电机器可抽_抽取量为配额与储量的较小值() {
        FakeStore store = new FakeStore(5000, 100000, true);
        GtEnergyPort port = port(store);
        List<ObjectStack> offered = port.extractable();
        assertEquals(1, offered.size());
        assertEquals(
            GtEnergyPort.EU,
            offered.get(0)
                .key());
        assertEquals(
            5000,
            offered.get(0)
                .amount());

        assertEquals(1200, port.extract(GtEnergyPort.EU, 1200, true));
        assertEquals(5000, store.stored, "模拟不扣");
        assertEquals(1200, port.extract(GtEnergyPort.EU, 1200, false));
        assertEquals(3800, store.stored);
        assertEquals(3800, port.extract(GtEnergyPort.EU, 999999, false), "配额大于储量时抽光储量");
        assertEquals(0, store.stored);
        assertTrue(
            port.extractable()
                .isEmpty());
    }

    @Test
    void 不向外供电的机器不被抽取() {
        FakeStore store = new FakeStore(5000, 10000, false);
        GtEnergyPort port = port(store);
        assertTrue(
            port.extractable()
                .isEmpty());
        assertEquals(0, port.extract(GtEnergyPort.EU, 100, false));
        assertEquals(5000, store.stored);
    }

    @Test
    void 充入最多充满剩余容量_不看电压() {
        FakeStore store = new FakeStore(700, 1000, false);
        GtEnergyPort port = port(store);
        assertEquals(300, port.insert(GtEnergyPort.EU, 8192, true));
        assertEquals(700, store.stored, "模拟不充");
        assertEquals(300, port.insert(GtEnergyPort.EU, 8192, false));
        assertEquals(1000, store.stored);
        assertEquals(0, port.insert(GtEnergyPort.EU, 1, false), "满了就不再充");
        assertEquals(1000, store.stored, "不会超过上限");
    }

    @Test
    void 退回全部加回_不丢能量() {
        FakeStore store = new FakeStore(1000, 1000, true);
        GtEnergyPort port = port(store);
        assertEquals(400, port.extract(GtEnergyPort.EU, 400, false));
        assertEquals(400, port.restore(GtEnergyPort.EU, 400));
        assertEquals(1000, store.stored);
    }

    @Test
    void 未知物体键和失效机器一律不处理() {
        FakeStore store = new FakeStore(500, 1000, true);
        GtEnergyPort port = port(store);
        assertEquals(0, port.extract("minecraft:stone@0", 100, false));
        assertEquals(0, port.insert("water", 100, false));
        assertEquals(500, store.stored);

        store.alive = false;
        assertTrue(
            port.extractable()
                .isEmpty());
        assertEquals(0, port.extract(GtEnergyPort.EU, 100, false));
        assertEquals(0, port.insert(GtEnergyPort.EU, 100, false));
        assertEquals(500, store.stored);
    }
}
