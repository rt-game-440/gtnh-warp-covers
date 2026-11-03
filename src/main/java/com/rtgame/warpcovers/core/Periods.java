package com.rtgame.warpcovers.core;

/** 运行周期与速率的换算规则（需求 4.3「通用」）。 */
public final class Periods {

    /** 覆盖板至少每隔这么多 tick 被 GT 调用一次（用于对账与及时发现周期修改）。 */
    public static final int MAX_TICK_INTERVAL = 20;

    private Periods() {}

    /**
     * 实际运行周期：覆盖板设置的周期（0 表示跟随服务器默认）与最短周期取大，至少 1 tick。
     * 服主调高最短周期后，已有覆盖板立即按新下限运行。
     */
    public static long effectivePeriod(int configured, ConfigPort config) {
        long wanted = configured > 0 ? configured : config.defaultPeriodTicks();
        return Math.max(1, Math.max(config.minPeriodTicks(), wanted));
    }

    /**
     * 实际速率（每个周期最多搬运多少）：覆盖板速率（0 表示不限）与该类型全局上限（0 表示不限）取小；
     * 都不限时返回 {@link Long#MAX_VALUE}。
     */
    public static long effectiveRate(long configured, long globalCap) {
        long own = configured > 0 ? configured : Long.MAX_VALUE;
        long cap = globalCap > 0 ? globalCap : Long.MAX_VALUE;
        return Math.min(own, cap);
    }

    /**
     * GT 调用覆盖板的间隔：周期不超过 {@link #MAX_TICK_INTERVAL} 时就等于周期；更长时取能整除周期、且不超过
     * {@link #MAX_TICK_INTERVAL} 的最大值，保证每个周期都准时到达，同时至少每 20 tick 对账一次。
     */
    public static int tickInterval(long period) {
        if (period <= 1) {
            return 1;
        }
        if (period <= MAX_TICK_INTERVAL) {
            return (int) period;
        }
        for (int candidate = MAX_TICK_INTERVAL; candidate > 1; candidate--) {
            if (period % candidate == 0) {
                return candidate;
            }
        }
        return 1;
    }
}
