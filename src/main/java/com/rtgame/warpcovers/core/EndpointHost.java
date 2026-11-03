package com.rtgame.warpcovers.core;

/** 已加载连接端的宿主（覆盖板）：核心通过它把变化通知回去。 */
public interface EndpointHost {

    /** 核心断开了这个连接端（频道被删除、连接者失权等）。宿主应清空频道并记下原因。 */
    void onDisconnected(DisconnectReason reason);

    /** 连接端配置被远程修改，或所连频道的显示属性（如颜色）变化；宿主应存盘并同步外观。 */
    void onEndpointChanged();
}
