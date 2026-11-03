package com.rtgame.warpcovers.core;

/** 新增频道被拒绝的原因。界面按 {@code warpcovers.create_failure.<key>} 显示。 */
public enum CreateFailure {

    EMPTY_NAME("empty_name"),
    NAME_TOO_LONG("name_too_long"),
    /** 同一归属者下已有同类型同名频道。 */
    DUPLICATE_NAME("duplicate_name");

    private final String key;

    CreateFailure(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }
}
