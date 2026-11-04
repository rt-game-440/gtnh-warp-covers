package com.rtgame.warpcovers.core;

/**
 * 连接端配置：交互状态、优先级、速率、周期。过滤名单属于频道，不在这里。
 * 覆盖板持有并且是它的权威；覆盖板加载期间，核心索引与覆盖板共用同一个对象。
 */
public final class EndpointConfig {

    private InteractionState state = InteractionState.EMPTY;
    /** 输出优先级，越大越先收。 */
    private int priority = 0;
    /** 每个周期的速率上限；0 表示不限（仍受全局上限约束）。 */
    private long rate = 0;
    /** 运行周期（tick）；0 表示跟随服务器默认周期。 */
    private int period = 0;

    public InteractionState state() {
        return state;
    }

    public void setState(InteractionState state) {
        this.state = state;
    }

    public int priority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public long rate() {
        return rate;
    }

    public void setRate(long rate) {
        this.rate = Math.max(0, rate);
    }

    public int period() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = Math.max(0, period);
    }

    public EndpointConfig copy() {
        EndpointConfig copy = new EndpointConfig();
        copy.copyFrom(this);
        return copy;
    }

    public void copyFrom(EndpointConfig other) {
        state = other.state;
        priority = other.priority;
        rate = other.rate;
        period = other.period;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EndpointConfig)) {
            return false;
        }
        EndpointConfig other = (EndpointConfig) o;
        return state == other.state && priority == other.priority && rate == other.rate && period == other.period;
    }

    @Override
    public int hashCode() {
        return (state.hashCode() * 31 + priority) * 31 + (int) rate * 7 + period;
    }
}
