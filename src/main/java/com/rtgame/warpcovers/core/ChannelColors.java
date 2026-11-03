package com.rtgame.warpcovers.core;

import java.util.Random;

/** 16 种染料颜色（与原版染料 RGB 一致），新频道随机取一种。 */
public final class ChannelColors {

    /** 按染料 meta 0–15：黑、红、绿、棕、蓝、紫、青、淡灰、灰、粉、黄绿、黄、淡蓝、品红、橙、白。 */
    private static final int[] DYES = { 0x1E1B1B, 0xB3312C, 0x3B511A, 0x51301A, 0x253192, 0x7B2FBE, 0x287697, 0xABABAB,
        0x434343, 0xD88198, 0x41CD34, 0xDECF2A, 0x6689D3, 0xC354CD, 0xEB8844, 0xF0F0F0 };

    /** 旧存档没有颜色时使用（淡灰）。 */
    public static final int DEFAULT = 0xABABAB;

    private ChannelColors() {}

    public static int count() {
        return DYES.length;
    }

    public static int dye(int index) {
        return DYES[Math.floorMod(index, DYES.length)];
    }

    public static int random(Random random) {
        return DYES[random.nextInt(DYES.length)];
    }

    /** 解析 6 位十六进制 RGB（可带 #）；不合法时返回 -1。 */
    public static int parseHex(String text) {
        if (text == null) {
            return -1;
        }
        String hex = text.trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        if (hex.length() != 6) {
            return -1;
        }
        for (int i = 0; i < 6; i++) {
            if (Character.digit(hex.charAt(i), 16) < 0) {
                return -1;
            }
        }
        return Integer.parseInt(hex, 16);
    }

    public static String toHex(int color) {
        return String.format("%06X", color & 0xFFFFFF);
    }

    /** 是否为 16 种预设色之一。 */
    public static boolean isDye(int color) {
        for (int dye : DYES) {
            if (dye == (color & 0xFFFFFF)) {
                return true;
            }
        }
        return false;
    }
}
