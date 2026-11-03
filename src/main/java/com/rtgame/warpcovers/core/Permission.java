package com.rtgame.warpcovers.core;

/** 频道级权限项。「新增频道」按归属者生效，不在这里，见 {@link ChannelHub#canCreate}。 */
public enum Permission {

    VIEW,
    /** 连接、断开、调整交互状态。 */
    CONNECT,
    CONFIGURE,
    MANAGE_PERMISSIONS,
    DELETE;

    int bit() {
        return 1 << ordinal();
    }
}
