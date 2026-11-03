package com.rtgame.warpcovers;

import java.io.File;

import net.minecraftforge.common.config.Configuration;

/** 服务器配置骨架，默认值见需求文档第 6 节。 */
public final class ServerConfig {

    public static boolean enableItems = true;
    public static boolean enableFluids = true;
    public static boolean enableEnergy = true;
    /** 全局速率上限，0 表示不限。 */
    public static int globalRateCap = 0;
    /** 默认传输周期（tick）。 */
    public static int defaultPeriodTicks = 20;
    /** 最短传输周期（tick）。 */
    public static int minPeriodTicks = 1;
    /** 每个所有者每种类型的频道数上限，0 表示不限。 */
    public static int maxChannelsPerOwnerPerType = 0;

    private ServerConfig() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        enableItems = config.getBoolean("enableItems", "types", true, "启用物品折跃");
        enableFluids = config.getBoolean("enableFluids", "types", true, "启用流体折跃");
        enableEnergy = config.getBoolean("enableEnergy", "types", true, "启用能量折跃");
        globalRateCap = config.getInt("globalRateCap", "limits", 0, 0, Integer.MAX_VALUE, "全局速率上限，0 表示不限");
        defaultPeriodTicks = config.getInt("defaultPeriodTicks", "limits", 20, 1, Integer.MAX_VALUE, "默认传输周期（tick）");
        minPeriodTicks = config.getInt("minPeriodTicks", "limits", 1, 1, Integer.MAX_VALUE, "最短传输周期（tick）");
        maxChannelsPerOwnerPerType = config
            .getInt("maxChannelsPerOwnerPerType", "limits", 0, 0, Integer.MAX_VALUE, "每个所有者每种类型的频道数上限，0 表示不限");
        if (config.hasChanged()) {
            config.save();
        }
    }
}
