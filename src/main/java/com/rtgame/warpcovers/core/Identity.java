package com.rtgame.warpcovers.core;

/** 玩家相对于某个归属者的身份（需求 4.4）。 */
public enum Identity {

    /** 队长和管理员；归属者是个人时就是本人。 */
    MANAGER,
    /** 队伍成员。 */
    MEMBER,
    /** 与该队伍结盟的玩家。 */
    ALLY,
    /** 其他所有人。 */
    STRANGER
}
