package com.rtgame.warpcovers.core;

/**
 * 连接端索引里的一条：某个覆盖板连在某个频道上。索引是「是否仍连接」的权威。
 * 连接端 ID、频道、连接顺序号不可变；位置、连接者、机器名随覆盖板加载刷新。
 */
public final class Endpoint {

    private final String endpointId;
    private final String channelId;
    private final long connectOrder;
    private Location location;
    /** 连接者的玩家 UUID 字符串；未知时为空串。 */
    private String connector;
    private String machineName = "";
    /** 覆盖板加载期间与覆盖板共用同一个对象；未加载时是最后一次同步的内容。 */
    private EndpointConfig config;
    /** 机器端口；为 null 表示连接端未加载。 */
    private MachinePort port;
    /** 覆盖板回调；与 port 同时存在。 */
    private EndpointHost host;
    /** 下次允许运行的世界时间。 */
    private long nextRunTime = Long.MIN_VALUE;
    private long lastSeenTime = Long.MIN_VALUE;
    /** 最近一次传输尝试得出的状态（只在内存里）。 */
    private EndpointStatus lastStatus;
    private long lastStatusTime = Long.MIN_VALUE;

    Endpoint(String endpointId, String channelId, long connectOrder, Location location, String connector,
        EndpointConfig config) {
        this.endpointId = endpointId;
        this.channelId = channelId;
        this.connectOrder = connectOrder;
        this.location = location;
        this.connector = connector == null ? "" : connector;
        this.config = config;
    }

    public String endpointId() {
        return endpointId;
    }

    public String channelId() {
        return channelId;
    }

    /** 连接顺序号：越小越早连上，「顺序」分发方式按它排。 */
    public long connectOrder() {
        return connectOrder;
    }

    public Location location() {
        return location;
    }

    public String connector() {
        return connector;
    }

    /** 缓存的机器名，覆盖板加载时刷新。 */
    public String machineName() {
        return machineName;
    }

    public EndpointConfig config() {
        return config;
    }

    public InteractionState state() {
        return config.state();
    }

    public FilterList filter() {
        return config.filter();
    }

    /** 覆盖板是否已加载且机器仍然有效。 */
    public boolean isLoaded() {
        return port != null && port.isAlive();
    }

    void setLocation(Location location) {
        this.location = location;
    }

    void setConnector(String connector) {
        this.connector = connector == null ? "" : connector;
    }

    void setMachineName(String machineName) {
        this.machineName = machineName == null ? "" : machineName;
    }

    void setConfig(EndpointConfig config) {
        this.config = config;
    }

    MachinePort port() {
        return port;
    }

    EndpointHost host() {
        return host;
    }

    void bind(MachinePort port, EndpointHost host) {
        this.port = port;
        this.host = host;
        if (port != null) {
            setMachineName(port.machineName());
        }
    }

    void unbind() {
        this.port = null;
        this.host = null;
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

    EndpointStatus lastStatus() {
        return lastStatus;
    }

    long lastStatusTime() {
        return lastStatusTime;
    }

    /** 记录输入端本周期的结果。 */
    void setStatus(EndpointStatus status, long now) {
        this.lastStatus = status;
        this.lastStatusTime = now;
    }

    /** 记录输出端被访问的结果；同一时刻有多个输入端时只升级不降级（传输中 &gt; 自身已满 &gt; 被名单过滤）。 */
    void noteReceived(EndpointStatus status, long now) {
        if (now == lastStatusTime && rank(lastStatus) >= rank(status)) {
            return;
        }
        setStatus(status, now);
    }

    private static int rank(EndpointStatus status) {
        if (status == EndpointStatus.TRANSFERRING) {
            return 3;
        }
        if (status == EndpointStatus.SELF_FULL) {
            return 2;
        }
        if (status == EndpointStatus.FILTERED) {
            return 1;
        }
        return 0;
    }
}
