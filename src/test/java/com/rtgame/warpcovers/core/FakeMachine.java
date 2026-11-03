package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 假机器：每种物体一个槽，共用一个总容量。可以让「实际插入」比「模拟」少，也可以拒绝退回。 */
final class FakeMachine implements MachinePort {

    final String blockId;
    final Map<String, Integer> contents = new LinkedHashMap<String, Integer>();
    int capacity;
    /** 实际插入时比模拟少收的量（模拟≠实际）。 */
    int insertShortfall = 0;
    /** 拒绝一切插入（用来模拟退不回去）。 */
    boolean refuseInsert = false;
    int extractCalls = 0;

    FakeMachine(String blockId, int capacity) {
        this.blockId = blockId;
        this.capacity = capacity;
    }

    FakeMachine with(String key, int amount) {
        contents.put(key, amount);
        return this;
    }

    int total() {
        int sum = 0;
        for (int value : contents.values()) {
            sum += value;
        }
        return sum;
    }

    int amountOf(String key) {
        Integer value = contents.get(key);
        return value == null ? 0 : value;
    }

    @Override
    public String blockId() {
        return blockId;
    }

    @Override
    public List<ObjectStack> extractable() {
        List<ObjectStack> list = new ArrayList<ObjectStack>();
        for (Map.Entry<String, Integer> entry : contents.entrySet()) {
            if (entry.getValue() > 0) {
                list.add(new ObjectStack(entry.getKey(), entry.getValue()));
            }
        }
        return list;
    }

    @Override
    public int extract(String key, int amount, boolean simulate) {
        int taken = Math.min(amount, amountOf(key));
        if (!simulate) {
            extractCalls++;
            contents.put(key, amountOf(key) - taken);
        }
        return taken;
    }

    @Override
    public int insert(String key, int amount, boolean simulate) {
        if (refuseInsert) {
            return 0;
        }
        int accepted = Math.min(amount, capacity - total());
        if (!simulate) {
            accepted = Math.max(0, accepted - insertShortfall);
            contents.put(key, amountOf(key) + accepted);
        }
        return accepted;
    }
}
