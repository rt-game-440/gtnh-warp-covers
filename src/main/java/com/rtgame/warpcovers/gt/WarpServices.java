package com.rtgame.warpcovers.gt;

import com.rtgame.warpcovers.ServerConfig;
import com.rtgame.warpcovers.WarpCovers;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.ConfigPort;

/**
 * 服务端共享的频道核心实例，以及把 {@link ServerConfig} 接到核心的配置端口。
 * 核心实例属于主世界存档数据：主世界加载时装上，服务器停止时卸下。
 */
public final class WarpServices {

    static final ConfigPort CONFIG = new ConfigPort() {

        @Override
        public boolean isEnabled(ChannelType type) {
            switch (type) {
                case ITEM:
                    return ServerConfig.enableItems;
                case ENERGY:
                    return ServerConfig.enableEnergy;
                default:
                    return ServerConfig.enableFluids;
            }
        }

        @Override
        public long globalRateCap(ChannelType type) {
            switch (type) {
                case ITEM:
                    return ServerConfig.globalRateCapItems;
                case ENERGY:
                    return ServerConfig.globalRateCapEnergy;
                default:
                    return ServerConfig.globalRateCapFluids;
            }
        }

        @Override
        public int defaultPeriodTicks() {
            return ServerConfig.defaultPeriodTicks;
        }

        @Override
        public int minPeriodTicks() {
            return ServerConfig.minPeriodTicks;
        }
    };

    private static ChannelHub hub;
    private static boolean warned;

    private WarpServices() {}

    /** 当前世界的频道核心。主世界数据还没装上时返回一个临时的空核心（不会存盘），并只警告一次。 */
    public static ChannelHub hub() {
        if (hub == null) {
            if (!warned) {
                warned = true;
                WarpCovers.LOG.warn("折跃频道数据尚未加载，使用临时的空频道表");
            }
            hub = new ChannelHub(CONFIG);
        }
        return hub;
    }

    public static boolean isInstalled() {
        return hub != null && !warned;
    }

    /** 主世界加载时装上存档里的核心。 */
    static void install(ChannelHub loaded) {
        hub = loaded;
        warned = false;
    }

    /** 服务器停止时卸下，避免单人游戏切换存档时串数据。 */
    static void clear() {
        hub = null;
        warned = false;
    }
}
