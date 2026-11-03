package com.rtgame.warpcovers.core;

/** 输入端一次运行的结果。 */
public final class TransferResult {

    static final TransferResult NONE = new TransferResult(0, 0);

    private final long transferred;
    private final long lost;

    TransferResult(long transferred, long lost) {
        this.transferred = transferred;
        this.lost = lost;
    }

    /** 这次运行从输入端搬到输出端的总量。 */
    public long transferred() {
        return transferred;
    }

    /** 无处可退而丢失的量。正常情况下恒为 0，非 0 表示机器端口违反了约定。 */
    public long lost() {
        return lost;
    }
}
