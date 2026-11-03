package com.rtgame.warpcovers.core;

/** 频道列表的分组，按声明顺序显示。 */
public enum ChannelGroup {

    /** 自己或本队伍。 */
    OWN,
    ALLY,
    /** 能看到的其他频道（对陌生人公开）。 */
    PUBLIC;

    public static ChannelGroup of(Identity identity) {
        switch (identity) {
            case MANAGER:
            case MEMBER:
                return OWN;
            case ALLY:
                return ALLY;
            default:
                return PUBLIC;
        }
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
