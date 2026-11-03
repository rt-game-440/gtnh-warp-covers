package com.rtgame.warpcovers.selfcheck;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class DevEnvironmentTest {

    @Test
    void 开发环境标记为真时判定为开发环境() {
        Map<String, Object> blackboard = new HashMap<>();
        blackboard.put("fml.deobfuscatedEnvironment", Boolean.TRUE);

        assertTrue(DevEnvironment.isDevelopment(blackboard));
    }

    @Test
    void 发布环境没有标记时不是开发环境() {
        assertFalse(DevEnvironment.isDevelopment(new HashMap<String, Object>()));
    }

    @Test
    void 标记为假时不是开发环境() {
        Map<String, Object> blackboard = new HashMap<>();
        blackboard.put("fml.deobfuscatedEnvironment", Boolean.FALSE);

        assertFalse(DevEnvironment.isDevelopment(blackboard));
    }
}
