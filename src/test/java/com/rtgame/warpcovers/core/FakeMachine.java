package com.rtgame.warpcovers.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 假机器：每种物体一个槽，共用一个总容量。可以让「实际插入」比「模拟」少，也可以拒绝退回。 */
final class FakeMachine implements MachinePort {

    private static int nextX = 0;

    final String blockId;
    /** 每台假机器一个独立位置（同 blockId 的共用 x）。 */
    final Location location;
    final Map<String, Long> contents = new LinkedHashMap<String, Long>();
    long capacity;
    /** 实际插入时比模拟少收的量（模拟≠实际）。 */
    long insertShortfall = 0;
    /** 拒绝一切插入（用来模拟退不回去）。 */
    boolean refuseInsert = false;
    boolean alive = true;
    String machineName;
    int extractCalls = 0;

    FakeMachine(String blockId, long capacity) {
        this(blockId, capacity, new Location(0, nextX++, 64, 0, 0));
    }

    FakeMachine(String blockId, long capacity, Location location) {
        this.blockId = blockId;
        this.capacity = capacity;
        this.location = location;
        this.machineName = "假机器" + blockId;
    }

    FakeMachine with(String key, long amount) {
        contents.put(key, amount);
        return this;
    }

    long total() {
        long sum = 0;
        for (long value : contents.values()) {
            sum += value;
        }
        return sum;
    }

    long amountOf(String key) {
        Long value = contents.get(key);
        return value == null ? 0 : value;
    }

    @Override
    public String blockId() {
        return blockId;
    }

    @Override
    public boolean isAlive() {
        return alive;
    }

    @Override
    public String machineName() {
        return machineName;
    }

    @Override
    public List<ObjectStack> extractable() {
        List<ObjectStack> list = new ArrayList<ObjectStack>();
        for (Map.Entry<String, Long> entry : contents.entrySet()) {
            if (entry.getValue() > 0) {
                list.add(new ObjectStack(entry.getKey(), entry.getValue()));
            }
        }
        return list;
    }

    @Override
    public long extract(String key, long amount, boolean simulate) {
        long taken = Math.min(amount, amountOf(key));
        if (!simulate) {
            extractCalls++;
            contents.put(key, amountOf(key) - taken);
        }
        return taken;
    }

    @Override
    public long insert(String key, long amount, boolean simulate) {
        if (refuseInsert) {
            return 0;
        }
        long accepted = Math.min(amount, capacity - total());
        if (!simulate) {
            accepted = Math.max(0, accepted - insertShortfall);
            contents.put(key, amountOf(key) + accepted);
        }
        return accepted;
    }
}
