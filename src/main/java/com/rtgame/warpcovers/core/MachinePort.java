package com.rtgame.warpcovers.core;

import java.util.List;

/**
 * 机器端口：频道核心看到的「一台机器」。所有抽取和插入都遵守机器自己的面规则，
 * 核心不关心 GT 的细节。每个方法都必须区分「模拟」和「实际」。
 */
public interface MachinePort {

    /** 机器所在方块的标识；同一方块上的所有连接端返回相同值，用来识别自环。 */
    String blockId();

    /** 当前可以抽出的物体（模拟结果），每种物体一项。 */
    List<ObjectStack> extractable();

    /**
     * 抽取物体。
     *
     * @param simulate true 只模拟，不改变机器
     * @return 实际（或模拟）抽出的数量，不超过 amount
     */
    long extract(String key, long amount, boolean simulate);

    /**
     * 插入物体。
     *
     * @param simulate true 只模拟，不改变机器
     * @return 实际（或模拟）插入的数量，不超过 amount
     */
    long insert(String key, long amount, boolean simulate);

    /**
     * 把本端口刚抽出、却没能送出去的物体退回机器。和 {@link #insert} 不同，退回可以无视面规则
     * （例如退回 GT 机器的输出槽），实在放不下时端口可以把物体掉落在方块旁，保证不凭空消失。
     *
     * @return 退回（含掉落）的数量；小于 amount 的部分由核心计为丢失
     */
    default long restore(String key, long amount) {
        return insert(key, amount, false);
    }

    /** 机器是否仍然有效（方块被破坏、区块卸载后返回 false）。 */
    default boolean isAlive() {
        return true;
    }

    /** 机器名，缓存进连接端索引供管理界面显示。 */
    default String machineName() {
        return "";
    }
}
