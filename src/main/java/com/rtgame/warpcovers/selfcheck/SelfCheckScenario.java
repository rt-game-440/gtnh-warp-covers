package com.rtgame.warpcovers.selfcheck;

/**
 * 一个跨服务器重启的自检场景：重启前「布置」，重启后「校验」。
 */
public interface SelfCheckScenario {

    /** 场景名，用于命令参数与状态文件。 */
    String name();

    /** 布置阶段：在世界中放置需要跨重启保留的内容。失败请抛异常。 */
    void setup();

    /** 校验阶段：服务器重启后检查布置的内容是否仍然正确。 */
    SelfCheckResult verify();
}
