package com.rtgame.warpcovers.core;

/** 由调用方回答「这个位置上的覆盖板是否还是这个连接端」，用于清理残留索引。 */
public interface EndpointPresence {

    enum Presence {
        /** 位置上的覆盖板就是这个连接端。 */
        PRESENT,
        /** 位置已加载，但上面没有这个连接端的覆盖板。 */
        ABSENT,
        /** 无法判断（如区块未加载），保持不动。 */
        UNKNOWN
    }

    Presence check(String endpointId, Location location);
}
