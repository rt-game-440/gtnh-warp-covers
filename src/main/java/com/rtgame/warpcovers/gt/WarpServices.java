package com.rtgame.warpcovers.gt;

import com.rtgame.warpcovers.ServerConfig;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.ConfigPort;

/** 服务端共享的频道核心实例，以及把 {@link ServerConfig} 接到核心的配置端口。 */
public final class WarpServices {

    private static final ConfigPort CONFIG = new ConfigPort() {

        @Override
        public boolean isEnabled(ChannelType type) {
            return ServerConfig.enableFluids;
        }

        @Override
        public long globalRateCap(ChannelType type) {
            return ServerConfig.globalRateCap;
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

    private static ChannelHub hub = new ChannelHub(CONFIG);

    private WarpServices() {}

    public static ChannelHub hub() {
        return hub;
    }

    /** 服务器启动时调用：频道的持久化见后续议题，目前每次启动都是空白状态。 */
    public static void reset() {
        hub = new ChannelHub(CONFIG);
    }
}
