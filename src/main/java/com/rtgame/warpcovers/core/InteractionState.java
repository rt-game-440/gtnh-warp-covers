package com.rtgame.warpcovers.core;

/** 连接端相对于所连频道的交互状态。新连接默认为 {@link #EMPTY}。 */
public enum InteractionState {
    /** 输入：机器 → 频道。 */
    INPUT,
    /** 输出：频道 → 机器。 */
    OUTPUT,
    /** 空：不动作。 */
    EMPTY
}
