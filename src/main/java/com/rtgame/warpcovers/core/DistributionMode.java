package com.rtgame.warpcovers.core;

/** 频道的分发方式：决定同一输出优先级内各输出端如何分配（需求 4.3）。 */
public enum DistributionMode {

    /** 轮询（默认）：在同级输出端之间轮流、尽量平均地分配。 */
    ROUND_ROBIN("round_robin"),
    /** 顺序：按连接到频道的先后顺序依次填满。 */
    SEQUENTIAL("sequential");

    private final String key;

    DistributionMode(String key) {
        this.key = key;
    }

    /** 稳定的小写键，用于存盘、同步与语言文件。 */
    public String key() {
        return key;
    }

    /**
     * 把 want 分给一组输出端。
     *
     * @param want 要分出去的总量
     * @param room 各输出端最多能收多少，下标 0 排在最前（轮询时由调用方把指针位置转到最前）
     * @return 各输出端分到的量，总和不超过 want，每项不超过 room
     */
    public long[] allocate(long want, long[] room) {
        return this == SEQUENTIAL ? fillInOrder(want, room) : levelOut(want, room);
    }

    private static long[] fillInOrder(long want, long[] room) {
        long[] shares = new long[room.length];
        long remaining = want;
        for (int i = 0; i < room.length && remaining > 0; i++) {
            shares[i] = Math.min(remaining, Math.max(0, room[i]));
            remaining -= shares[i];
        }
        return shares;
    }

    /** 水位法：尽量平均，放不下的份额匀给其他输出端，除不尽的余数从最前面开始各给 1。 */
    private static long[] levelOut(long want, long[] room) {
        long[] shares = new long[room.length];
        long remaining = want;
        while (remaining > 0) {
            int open = 0;
            for (int i = 0; i < room.length; i++) {
                if (shares[i] < room[i]) {
                    open++;
                }
            }
            if (open == 0) {
                break;
            }
            long even = Math.max(1, remaining / open);
            for (int i = 0; i < room.length && remaining > 0; i++) {
                if (shares[i] < room[i]) {
                    long give = Math.min(even, room[i] - shares[i]);
                    shares[i] += give;
                    remaining -= give;
                }
            }
        }
        return shares;
    }

    /** 按键查找；未知键返回默认的轮询。 */
    public static DistributionMode byKey(String key) {
        for (DistributionMode mode : values()) {
            if (mode.key.equals(key)) {
                return mode;
            }
        }
        return ROUND_ROBIN;
    }
}
