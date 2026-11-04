package com.rtgame.warpcovers.core;

/** 频道配置面板顶部的连接按钮此刻做什么：由覆盖板连着的频道和面板选中的频道决定。 */
public enum ConnectionAction {

    /** 覆盖板未连接：连上选中的频道。 */
    CONNECT,
    /** 覆盖板连着别的频道：直接换到选中的频道，不二次确认。 */
    SWITCH,
    /** 选中的正是已连接的频道：断开。 */
    DISCONNECT;

    /**
     * @param connectedId 覆盖板所连频道的 ID，空串表示未连接
     * @param selectedId  面板选中的频道 ID
     */
    public static ConnectionAction of(String connectedId, String selectedId) {
        if (connectedId.isEmpty()) {
            return CONNECT;
        }
        return connectedId.equals(selectedId) ? DISCONNECT : SWITCH;
    }
}
