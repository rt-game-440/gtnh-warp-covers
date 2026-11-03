package com.rtgame.warpcovers.selfcheck;

import java.util.Map;

/**
 * 判定是否处于开发（反混淆）环境。自检命令只在开发环境注册，发布版不暴露。
 */
public final class DevEnvironment {

    private static final String DEOBF_KEY = "fml.deobfuscatedEnvironment";

    private DevEnvironment() {}

    /** @param blackboard Forge 的 {@code Launch.blackboard} */
    public static boolean isDevelopment(Map<String, Object> blackboard) {
        return Boolean.TRUE.equals(blackboard.get(DEOBF_KEY));
    }
}
