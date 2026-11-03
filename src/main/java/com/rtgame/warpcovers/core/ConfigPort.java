package com.rtgame.warpcovers.core;

/** 配置端口：服务器级配置，由调用方提供。 */
public interface ConfigPort {

    /** 该类型是否被服务器启用；禁用时该类型的连接端不参与传输。 */
    boolean isEnabled(ChannelType type);

    /** 该类型单个连接端每次运行的全局速率上限；0 表示不限。 */
    long globalRateCap(ChannelType type);

    /** 输入端默认的运行周期（tick）。 */
    int defaultPeriodTicks();

    /** 运行周期的下限（tick）。 */
    int minPeriodTicks();
}
