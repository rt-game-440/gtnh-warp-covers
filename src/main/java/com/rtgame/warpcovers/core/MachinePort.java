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
    int extract(String key, int amount, boolean simulate);

    /**
     * 插入物体。
     *
     * @param simulate true 只模拟，不改变机器
     * @return 实际（或模拟）插入的数量，不超过 amount
     */
    int insert(String key, int amount, boolean simulate);
}
