package com.rtgame.warpcovers.core;

/** 频道类型。三类频道互相独立。 */
public enum ChannelType {

    FLUID("fluid"),
    ITEM("item"),
    ENERGY("energy");

    private final String key;

    ChannelType(String key) {
        this.key = key;
    }

    /** 稳定的小写键，用于存盘、配置与语言文件。 */
    public String key() {
        return key;
    }

    /** 能量没有过滤名单（需求 4.3）：核心对能量频道跳过名单判断，界面也不显示名单。 */
    public boolean hasFilter() {
        return this != ENERGY;
    }

    public static ChannelType byKey(String key) {
        for (ChannelType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }
}
