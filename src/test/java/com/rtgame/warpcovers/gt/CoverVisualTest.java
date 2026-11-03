package com.rtgame.warpcovers.gt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.google.common.io.ByteStreams;
import com.rtgame.warpcovers.core.InteractionState;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

class CoverVisualTest {

    private static CoverVisual connected(int color, InteractionState state) {
        return new CoverVisual(true, color, state, "主网", 100, "transferring");
    }

    @Test
    void 未连接时印记为灰色且没有箭头() {
        CoverVisual visual = new CoverVisual(false, 0x123456, InteractionState.INPUT, "", 0, "unconnected");

        assertEquals(CoverVisual.UNCONNECTED_COLOR, visual.markColor());
        assertEquals(CoverVisual.Arrow.NONE, visual.arrow());
    }

    @Test
    void 连接后使用频道颜色且箭头随交互状态() {
        assertEquals(0x123456, connected(0x123456, InteractionState.EMPTY).markColor());
        assertEquals(CoverVisual.Arrow.NONE, connected(0, InteractionState.EMPTY).arrow());
        assertEquals(CoverVisual.Arrow.OUTWARD, connected(0, InteractionState.INPUT).arrow());
        assertEquals(CoverVisual.Arrow.INWARD, connected(0, InteractionState.OUTPUT).arrow());
    }

    @Test
    void 只有颜色或箭头变化才算外观变化() {
        CoverVisual base = connected(0x123456, InteractionState.INPUT);

        assertTrue(base.sameLook(new CoverVisual(true, 0x123456, InteractionState.INPUT, "改名", 5, "waiting")));
        assertFalse(base.sameLook(connected(0x654321, InteractionState.INPUT)));
        assertFalse(base.sameLook(connected(0x123456, InteractionState.OUTPUT)));
        assertFalse(base.sameLook(CoverVisual.UNCONNECTED));
    }

    @Test
    void 经数据包往返后内容不变() {
        CoverVisual visual = new CoverVisual(
            true,
            0xABCDEF,
            InteractionState.OUTPUT,
            "中文频道",
            Long.MAX_VALUE,
            "peer_full");
        ByteBuf buf = Unpooled.buffer();
        visual.write(buf);
        byte[] bytes = new byte[buf.readableBytes()];
        buf.readBytes(bytes);

        CoverVisual read = CoverVisual.read(ByteStreams.newDataInput(bytes));

        assertTrue(read.connected);
        assertEquals(0xABCDEF, read.color);
        assertEquals(InteractionState.OUTPUT, read.state);
        assertEquals("中文频道", read.channelName);
        assertEquals(Long.MAX_VALUE, read.rate);
        assertEquals("peer_full", read.statusKey);
    }
}
