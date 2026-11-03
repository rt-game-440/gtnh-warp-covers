package com.rtgame.warpcovers.gt;

/**
 * 能量端口用到的机器内部储能操作。单独抽成接口，让抽取与充入规则可以脱离 GT 做单测；
 * 正式实现见 {@link GtEnergyPort#of}。
 */
interface EnergyStore {

    boolean isAlive();

    String machineName();

    /** 当前储量（EU）。 */
    long stored();

    /** 储能上限（EU）。 */
    long capacity();

    /** 机器是否会向外供电（发电机、电池箱、变压器等）；只有这类机器能当输入端被抽取。 */
    boolean suppliesEnergy();

    /** 直接加进内部储能：不检查电压电流，也不封顶，调用方负责不超过剩余容量。 */
    void add(long amount);

    /** 从内部储能扣除，返回实际扣掉的量。 */
    long take(long amount);
}
