package com.rtgame.warpcovers.core;

/** 身份端口：谁属于哪个归属者。有 ServerUtilities 时按队伍判定，否则见 {@link PersonalIdentity}。 */
public interface IdentityPort {

    /**
     * 身份数据是否可用。ServerUtilities 的数据还没加载或已经关闭时为 false，
     * 此时不做任何失权判定，否则会把所有人都当成陌生人断开。
     */
    boolean isReady();

    /** 这个玩家现在新建的频道归谁：在队伍里归队伍，否则归本人。 */
    String ownerFor(String playerId);

    /** 归属者的显示名。 */
    String ownerName(String ownerId, String playerName);

    /** 玩家相对于归属者的身份。 */
    Identity identityOf(String playerId, String ownerId);
}
