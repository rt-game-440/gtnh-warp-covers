package com.rtgame.warpcovers;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** 服务器配置骨架，默认值见需求文档第 6 节。 */
public final class ServerConfig {

    public static boolean enableItems = true;
    public static boolean enableFluids = true;
    public static boolean enableEnergy = true;
    /** 物品的全局速率上限（每周期），0 表示不限。 */
    public static long globalRateCapItems = 0;
    /** 流体的全局速率上限（每周期，单位 L），0 表示不限。 */
    public static long globalRateCapFluids = 0;
    /** 能量的全局速率上限（每周期，单位 EU），0 表示不限。 */
    public static long globalRateCapEnergy = 0;
    /** 默认传输周期（tick）。 */
    public static int defaultPeriodTicks = 20;
    /** 最短传输周期（tick）。 */
    public static int minPeriodTicks = 1;
    /** 每个所有者每种类型的频道数上限，0 表示不限。 */
    public static int maxChannelsPerOwnerPerType = 0;

    private ServerConfig() {}

    /** Forge 配置没有 long 类型，能量上限可能超过 int，所以按字符串读。写错或为负时当作不限。 */
    private static long readCap(Configuration config, String name, String comment) {
        String raw = config.getString(name, "limits", "0", comment);
        try {
            return Math.max(0, Long.parseLong(raw.trim()));
        } catch (NumberFormatException e) {
            WarpCovers.LOG.warn("配置项 {} 的值 {} 不是整数，按不限处理", name, raw);
            return 0;
        }
    }

    public static void load(File file) {
        Configuration config = new Configuration(file);
        enableItems = config.getBoolean("enableItems", "types", true, "启用物品折跃");
        enableFluids = config.getBoolean("enableFluids", "types", true, "启用流体折跃");
        enableEnergy = config.getBoolean("enableEnergy", "types", true, "启用能量折跃");
        globalRateCapItems = readCap(config, "globalRateCapItems", "物品全局速率上限（每周期），0 表示不限");
        globalRateCapFluids = readCap(config, "globalRateCapFluids", "流体全局速率上限（每周期，单位 L），0 表示不限");
        globalRateCapEnergy = readCap(config, "globalRateCapEnergy", "能量全局速率上限（每周期，单位 EU），0 表示不限");
        defaultPeriodTicks = config.getInt("defaultPeriodTicks", "limits", 20, 1, Integer.MAX_VALUE, "默认传输周期（tick）");
        minPeriodTicks = config.getInt("minPeriodTicks", "limits", 1, 1, Integer.MAX_VALUE, "最短传输周期（tick）");
        maxChannelsPerOwnerPerType = config
            .getInt("maxChannelsPerOwnerPerType", "limits", 0, 0, Integer.MAX_VALUE, "每个所有者每种类型的频道数上限，0 表示不限");
        if (config.hasChanged()) {
            config.save();
        }
    }
}
