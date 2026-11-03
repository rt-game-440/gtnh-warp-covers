package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 周期与速率换算、分配算法、数字输入规范化这些纯规则。 */
class PeriodsTest {

    private static FakeConfig config(int defaultPeriod, int minPeriod) {
        FakeConfig config = new FakeConfig();
        config.defaultPeriod = defaultPeriod;
        config.minPeriod = minPeriod;
        return config;
    }

    @Test
    void 未设置周期时取默认周期() {
        assertEquals(20, Periods.effectivePeriod(0, config(20, 1)));
    }

    @Test
    void 单板周期优先于默认周期() {
        assertEquals(7, Periods.effectivePeriod(7, config(20, 1)));
    }

    @Test
    void 实际周期不低于最短周期() {
        assertEquals(10, Periods.effectivePeriod(5, config(20, 10)));
        assertEquals(10, Periods.effectivePeriod(0, config(5, 10)));
    }

    @Test
    void 配置异常时周期至少为1() {
        assertEquals(1, Periods.effectivePeriod(0, config(0, 0)));
    }

    @Test
    void 实际速率取单板与全局上限中较小的() {
        assertEquals(100, Periods.effectiveRate(100, 500));
        assertEquals(100, Periods.effectiveRate(500, 100));
        assertEquals(100, Periods.effectiveRate(0, 100));
        assertEquals(100, Periods.effectiveRate(100, 0));
        assertEquals(Long.MAX_VALUE, Periods.effectiveRate(0, 0));
    }

    @Test
    void GT调用间隔能整除周期且不超过20() {
        assertEquals(1, Periods.tickInterval(1));
        assertEquals(7, Periods.tickInterval(7));
        assertEquals(20, Periods.tickInterval(20));
        assertEquals(20, Periods.tickInterval(100));
        assertEquals(15, Periods.tickInterval(30));
        assertEquals(1, Periods.tickInterval(23));
    }

    @Test
    void 轮询平均分配且余数先给最前面的() {
        assertArrayEquals(
            new long[] { 34, 33, 33 },
            DistributionMode.ROUND_ROBIN.allocate(100, new long[] { 1000, 1000, 1000 }));
    }

    @Test
    void 轮询时放不下的份额匀给其他输出端() {
        assertArrayEquals(
            new long[] { 10, 45, 45 },
            DistributionMode.ROUND_ROBIN.allocate(100, new long[] { 10, 1000, 1000 }));
        assertArrayEquals(
            new long[] { 10, 0, 20 },
            DistributionMode.ROUND_ROBIN.allocate(100, new long[] { 10, 0, 20 }));
    }

    @Test
    void 轮询每份不足1时只给最前面的() {
        assertArrayEquals(new long[] { 1, 0, 0 }, DistributionMode.ROUND_ROBIN.allocate(1, new long[] { 5, 5, 5 }));
        assertArrayEquals(new long[] { 0, 1, 0 }, DistributionMode.ROUND_ROBIN.allocate(1, new long[] { 0, 5, 5 }));
    }

    @Test
    void 顺序依次填满() {
        assertArrayEquals(
            new long[] { 30, 70, 0 },
            DistributionMode.SEQUENTIAL.allocate(100, new long[] { 30, 80, 1000 }));
    }

    @Test
    void 大数分配不溢出() {
        long[] shares = DistributionMode.ROUND_ROBIN
            .allocate(Long.MAX_VALUE, new long[] { Long.MAX_VALUE, Long.MAX_VALUE });
        assertEquals(Long.MAX_VALUE, shares[0] + shares[1]);
    }

    @Test
    void 未知的分发方式键回落到轮询() {
        assertEquals(DistributionMode.ROUND_ROBIN, DistributionMode.byKey("nope"));
        assertEquals(DistributionMode.ROUND_ROBIN, DistributionMode.byKey(""));
        assertEquals(DistributionMode.SEQUENTIAL, DistributionMode.byKey("sequential"));
    }

    @Test
    void 数字输入精确保留大数并夹到范围内() {
        assertEquals("9007199254740993", NumberInput.normalize("9007199254740993", 0, Long.MAX_VALUE));
        assertEquals("-5", NumberInput.normalize(" -5 ", Integer.MIN_VALUE, Integer.MAX_VALUE));
        assertEquals("0", NumberInput.normalize("-5", 0, Integer.MAX_VALUE));
        assertEquals("2147483647", NumberInput.normalize("99999999999999999999999", 0, Integer.MAX_VALUE));
        assertEquals("0", NumberInput.normalize("abc", 0, Integer.MAX_VALUE));
        assertEquals("0", NumberInput.normalize("", 0, Integer.MAX_VALUE));
    }
}
