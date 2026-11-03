package com.rtgame.warpcovers.core;

import java.math.BigInteger;

/**
 * 界面数字输入框的规范化：只认纯整数，超范围时夹到边界，不是整数时当作 0（再夹到范围内）。
 * 不用 ModularUI 自带的数字模式，因为它经过 double 计算，大数会失去精度。
 */
public final class NumberInput {

    private NumberInput() {}

    public static String normalize(String text, long min, long max) {
        return Long.toString(parse(text, min, max));
    }

    public static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }

    public static long parse(String text, long min, long max) {
        BigInteger value;
        try {
            value = new BigInteger(text == null ? "" : text.trim());
        } catch (NumberFormatException e) {
            value = BigInteger.ZERO;
        }
        if (value.compareTo(BigInteger.valueOf(min)) < 0) {
            return min;
        }
        if (value.compareTo(BigInteger.valueOf(max)) > 0) {
            return max;
        }
        return value.longValue();
    }
}
