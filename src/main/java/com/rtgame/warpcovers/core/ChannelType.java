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

    public static ChannelType byKey(String key) {
        for (ChannelType type : values()) {
            if (type.key.equals(key)) {
                return type;
            }
        }
        return null;
    }
}
