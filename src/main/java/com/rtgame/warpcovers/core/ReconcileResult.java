package com.rtgame.warpcovers.core;

/** 覆盖板加载时与连接端索引对账的结论。 */
public final class ReconcileResult {

    public enum Kind {
        /** 仍然连接；索引已按覆盖板数据更新。 */
        CONNECTED,
        /** 覆盖板本来就没连频道。 */
        NOT_CONNECTED,
        /** 已被断开（见 {@link #reason()}），覆盖板应清空频道。 */
        DISCONNECTED,
        /**
         * 索引里没有这个连接端，但覆盖板记着一个仍存在的频道：覆盖板是从物品重新贴上的
         * （撬下后重贴、挖掉机器后重放、复制出来的副本）。调用方应分配新的连接端 ID，
         * 以贴者为连接者做权限检查后重新连接。
         */
        REATTACH
    }

    private static final ReconcileResult CONNECTED_RESULT = new ReconcileResult(Kind.CONNECTED, null);
    private static final ReconcileResult NOT_CONNECTED_RESULT = new ReconcileResult(Kind.NOT_CONNECTED, null);
    private static final ReconcileResult REATTACH_RESULT = new ReconcileResult(Kind.REATTACH, null);

    private final Kind kind;
    private final DisconnectReason reason;

    private ReconcileResult(Kind kind, DisconnectReason reason) {
        this.kind = kind;
        this.reason = reason;
    }

    static ReconcileResult connected() {
        return CONNECTED_RESULT;
    }

    static ReconcileResult notConnected() {
        return NOT_CONNECTED_RESULT;
    }

    static ReconcileResult reattach() {
        return REATTACH_RESULT;
    }

    static ReconcileResult disconnected(DisconnectReason reason) {
        return new ReconcileResult(Kind.DISCONNECTED, reason);
    }

    public Kind kind() {
        return kind;
    }

    /** 仅 {@link Kind#DISCONNECTED} 时非空。 */
    public DisconnectReason reason() {
        return reason;
    }

    @Override
    public String toString() {
        return reason == null ? kind.name() : kind + "(" + reason + ")";
    }
}
