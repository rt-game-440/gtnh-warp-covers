package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConnectionActionTest {

    @Test
    void 未连接时是连接() {
        assertEquals(ConnectionAction.CONNECT, ConnectionAction.of("", "a"));
    }

    @Test
    void 连着别的频道时是切换() {
        assertEquals(ConnectionAction.SWITCH, ConnectionAction.of("a", "b"));
    }

    @Test
    void 选中已连接的频道时是断开() {
        assertEquals(ConnectionAction.DISCONNECT, ConnectionAction.of("a", "a"));
    }
}
