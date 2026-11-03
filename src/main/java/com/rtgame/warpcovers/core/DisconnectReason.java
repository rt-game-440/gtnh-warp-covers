package com.rtgame.warpcovers.core;

/** 连接端被核心（而不是玩家自己）断开的原因，显示在状态栏。 */
public enum DisconnectReason {

    /** 频道被删除。 */
    CHANNEL_DELETED("channel_deleted"),
    /** 连接者失去「连接」权限。 */
    PERMISSION_LOST("permission_lost"),
    /** 贴上带配置的覆盖板时，贴者没有该频道的「连接」权限。 */
    NO_PERMISSION("no_permission"),
    /** 有权限的玩家用折跃频道管理器远程断开。 */
    REMOTE("remote");

    private final String key;

    DisconnectReason(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static DisconnectReason byKey(String key) {
        for (DisconnectReason reason : values()) {
            if (reason.key.equals(key)) {
                return reason;
            }
        }
        return null;
    }
}
