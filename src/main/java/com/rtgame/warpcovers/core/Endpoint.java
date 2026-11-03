package com.rtgame.warpcovers.core;

/** 连在频道上的一个连接端。身份（endpointId）不可变。 */
public final class Endpoint {

    private final String endpointId;
    private final String channelId;
    private InteractionState state = InteractionState.EMPTY;
    private FilterList filter = new FilterList();
    /** 机器端口；为 null 表示连接端所在区块未加载。 */
    private MachinePort port;
    /** 下次允许运行的世界时间。 */
    private long nextRunTime = Long.MIN_VALUE;
    private long lastSeenTime = Long.MIN_VALUE;

    Endpoint(String endpointId, String channelId, MachinePort port) {
        this.endpointId = endpointId;
        this.channelId = channelId;
        this.port = port;
    }

    public String endpointId() {
        return endpointId;
    }

    public String channelId() {
        return channelId;
    }

    public InteractionState state() {
        return state;
    }

    public FilterList filter() {
        return filter;
    }

    public boolean isLoaded() {
        return port != null;
    }

    void setState(InteractionState state) {
        this.state = state;
    }

    void setFilter(FilterList filter) {
        this.filter = filter;
    }

    MachinePort port() {
        return port;
    }

    void setPort(MachinePort port) {
        this.port = port;
    }

    /** 上一次被驱动时调用方给出的时间；用来发现时钟回退（如 GT 机器重载后计时器归零）。 */
    long lastSeenTime() {
        return lastSeenTime;
    }

    void setLastSeenTime(long lastSeenTime) {
        this.lastSeenTime = lastSeenTime;
    }

    long nextRunTime() {
        return nextRunTime;
    }

    void setNextRunTime(long nextRunTime) {
        this.nextRunTime = nextRunTime;
    }
}
