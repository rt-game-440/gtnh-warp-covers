package com.rtgame.warpcovers.gt;

import java.nio.charset.StandardCharsets;

import com.google.common.io.ByteArrayDataInput;
import com.rtgame.warpcovers.core.InteractionState;

import io.netty.buffer.ByteBuf;

/** 客户端要显示的覆盖板信息：印记外观和 WAILA 文本。服务端算好，经 GT 的覆盖板同步通道下发。 */
public final class CoverVisual {

    /** 未连接时印记的灰色。 */
    public static final int UNCONNECTED_COLOR = 0x808080;

    public static final CoverVisual UNCONNECTED = new CoverVisual(
        false,
        0,
        InteractionState.EMPTY,
        "",
        Long.MAX_VALUE,
        "unconnected");

    /** 交互状态箭头：以机器为参照，输入是物体离开机器（向外），输出是进入机器（向内）。 */
    public enum Arrow {
        NONE,
        OUTWARD,
        INWARD
    }

    public final boolean connected;
    public final int color;
    public final InteractionState state;
    public final String channelName;
    /** 实际生效的速率；{@link Long#MAX_VALUE} 表示不限。 */
    public final long rate;
    public final String statusKey;

    public CoverVisual(boolean connected, int color, InteractionState state, String channelName, long rate,
        String statusKey) {
        this.connected = connected;
        this.color = color & 0xFFFFFF;
        this.state = state;
        this.channelName = channelName;
        this.rate = rate;
        this.statusKey = statusKey;
    }

    public int markColor() {
        return connected ? color : UNCONNECTED_COLOR;
    }

    public Arrow arrow() {
        if (!connected) {
            return Arrow.NONE;
        }
        switch (state) {
            case INPUT:
                return Arrow.OUTWARD;
            case OUTPUT:
                return Arrow.INWARD;
            default:
                return Arrow.NONE;
        }
    }

    /** 外观是否相同：只有外观变了客户端才需要重绘区块，看着覆盖板时数据包很频繁。 */
    public boolean sameLook(CoverVisual other) {
        return markColor() == other.markColor() && arrow() == other.arrow();
    }

    public void write(ByteBuf buf) {
        buf.writeBoolean(connected);
        buf.writeInt(color);
        buf.writeByte(state.ordinal());
        writeString(buf, channelName);
        buf.writeLong(rate);
        writeString(buf, statusKey);
    }

    public static CoverVisual read(ByteArrayDataInput in) {
        boolean connected = in.readBoolean();
        int color = in.readInt();
        InteractionState[] states = InteractionState.values();
        int state = in.readByte();
        String name = readString(in);
        long rate = in.readLong();
        String status = readString(in);
        return new CoverVisual(
            connected,
            color,
            state >= 0 && state < states.length ? states[state] : InteractionState.EMPTY,
            name,
            rate,
            status);
    }

    private static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }

    private static String readString(ByteArrayDataInput in) {
        byte[] bytes = new byte[in.readInt()];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
