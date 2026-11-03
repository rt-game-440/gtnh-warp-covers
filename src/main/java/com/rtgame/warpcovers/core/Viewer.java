package com.rtgame.warpcovers.core;

/** 正在操作界面的玩家。 */
public final class Viewer {

    private final String playerId;
    private final String name;

    /**
     * @param playerId 玩家 UUID 字符串
     * @param name     玩家显示名
     */
    public Viewer(String playerId, String name) {
        this.playerId = playerId == null ? "" : playerId;
        this.name = name == null ? "" : name;
    }

    public String playerId() {
        return playerId;
    }

    public String name() {
        return name;
    }

    /** 此玩家本人作为归属者时的 ID。 */
    public String personalOwnerId() {
        return personalOwnerId(playerId);
    }

    public static String personalOwnerId(String playerId) {
        return "player:" + playerId;
    }
}
