package com.rtgame.warpcovers.core;

import java.util.Collections;
import java.util.List;

/** 覆盖板界面操作的那个连接端（由覆盖板实现：连接、断开都要经过覆盖板，以保持覆盖板数据为配置权威）。 */
public interface PanelEndpoint {

    String endpointId();

    /** 所连频道的内部 ID，空串表示未连接。 */
    String channelId();

    /** 覆盖板持有的配置（与核心共享同一对象）。 */
    EndpointConfig config();

    /** 最近一次被动断开的原因；null 表示没有。 */
    DisconnectReason lastReason();

    /** 连接到频道，交互状态回到「空」。 */
    void connect(String channelId, String connector);

    /** 主动断开。 */
    void disconnect();

    /** 配置在共享对象上改过之后调用（标脏、通知同步）。 */
    void configChanged();

    /** 所贴机器现有、能取出的物体（模拟结果，不改变机器）。覆盖板未加载时为空。 */
    default List<ObjectStack> storedObjects() {
        return Collections.emptyList();
    }

    /** 这个连接端因所贴方块的限制而不工作时返回原因；正常时为 null。 */
    default EndpointStatus blockedStatus() {
        return null;
    }
}
