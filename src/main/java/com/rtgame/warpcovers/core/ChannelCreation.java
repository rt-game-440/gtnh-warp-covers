package com.rtgame.warpcovers.core;

/** 新增频道的结果：成功时带频道，失败时带原因。 */
public final class ChannelCreation {

    private final Channel channel;
    private final CreateFailure failure;

    private ChannelCreation(Channel channel, CreateFailure failure) {
        this.channel = channel;
        this.failure = failure;
    }

    static ChannelCreation success(Channel channel) {
        return new ChannelCreation(channel, null);
    }

    static ChannelCreation failure(CreateFailure failure) {
        return new ChannelCreation(null, failure);
    }

    public boolean succeeded() {
        return channel != null;
    }

    /** 成功时的频道，失败时为 null。 */
    public Channel channel() {
        return channel;
    }

    /** 失败原因，成功时为 null。 */
    public CreateFailure failure() {
        return failure;
    }
}
