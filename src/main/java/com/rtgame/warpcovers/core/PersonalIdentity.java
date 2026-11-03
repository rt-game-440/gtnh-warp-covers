package com.rtgame.warpcovers.core;

/** 纯个人回退：没有安装 ServerUtilities 时频道只能归个人，本人是管理者，其他人都是陌生人。 */
public final class PersonalIdentity implements IdentityPort {

    public static final PersonalIdentity INSTANCE = new PersonalIdentity();

    private PersonalIdentity() {}

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public String ownerFor(String playerId) {
        return Viewer.personalOwnerId(playerId);
    }

    @Override
    public String ownerName(String ownerId, String playerName) {
        return playerName;
    }

    @Override
    public Identity identityOf(String playerId, String ownerId) {
        return Viewer.personalOwnerId(playerId)
            .equals(ownerId) ? Identity.MANAGER : Identity.STRANGER;
    }
}
