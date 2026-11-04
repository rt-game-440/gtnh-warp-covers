package com.rtgame.warpcovers.core;

/** 连接端当前状态，显示在覆盖板界面底部状态栏。界面按 {@code warpcovers.status.<key>} 显示。 */
public enum EndpointStatus {

    /** 没连任何频道。 */
    UNCONNECTED("unconnected"),
    /** 服务器配置禁用了该类型。 */
    DISABLED("disabled"),
    /** 已连接但交互状态为「空」。 */
    IDLE("idle"),
    /** 已连接，等待下一个周期或还没有新的结果。 */
    WAITING("waiting"),
    /** 最近一个周期有物体经过。 */
    TRANSFERRING("transferring"),
    /** 频道里没有可传给 / 可接收自的对端。 */
    NO_PEER("no_peer"),
    /** 对端都在未加载的区块里。 */
    PEER_UNLOADED("peer_unloaded"),
    /** 对端（输出端）都满了。 */
    PEER_FULL("peer_full"),
    /** 本端（输出端）自己的机器满了。 */
    SELF_FULL("self_full"),
    /** 物体被频道的过滤名单挡下。 */
    FILTERED("filtered"),
    /** 本端（输入端）机器里没有可抽取的物体。 */
    SOURCE_EMPTY("source_empty"),
    /** 聚合覆盖板贴在没有内部储能的方块上，能量部分不工作。 */
    NO_ENERGY_STORAGE("no_energy_storage");

    private final String key;

    EndpointStatus(String key) {
        this.key = key;
    }

    public String key() {
        return key;
    }

    public static EndpointStatus byKey(String key) {
        for (EndpointStatus status : values()) {
            if (status.key.equals(key)) {
                return status;
            }
        }
        return null;
    }
}
