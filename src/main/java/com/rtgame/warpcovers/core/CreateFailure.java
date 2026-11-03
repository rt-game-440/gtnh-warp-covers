package com.rtgame.warpcovers.core;

/** 新增频道被拒绝的原因。界面按 {@code warpcovers.create_failure.<key>} 显示。 */
public enum CreateFailure {

    EMPTY_NAME("empty_name"),
    NAME_TOO_LONG("name_too_long"),
    /** 同一归属者下已有同类型同名频道。 */
    DUPLICATE_NAME("duplicate_name"),
    /** 没有在该归属者下新增频道的权限。 */
    NO_PERMISSION("no_create_permission"),
    /** 该归属者这种类型的频道数已达服务器上限。 */
    LIMIT_REACHED("limit_reached");

    private final String key;

    CreateFailure(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    /** 按键查找；未知键返回 null。 */
    public static CreateFailure byKey(String key) {
        for (CreateFailure failure : values()) {
            if (failure.key.equals(key)) {
                return failure;
            }
        }
        return null;
    }
}
