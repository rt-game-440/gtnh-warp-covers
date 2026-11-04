package com.rtgame.warpcovers.gt;

import javax.annotation.Nonnull;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.FilterList;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.PanelEndpoint;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;

/** 单类型折跃覆盖板：只有一个连接端。子类决定频道类型和放行哪种物体。 */
public abstract class WarpCover extends BaseWarpCover implements PanelEndpoint {

    private final WarpPart part;

    protected WarpCover(@Nonnull CoverContext context, ITexture coverTexture, ChannelType type) {
        super(context, coverTexture, type);
        part = parts().get(0);
    }

    /** 覆盖板能连接的频道类型。 */
    public final ChannelType channelType() {
        return part.channelType();
    }

    // ---- 对外操作（自检场景直接调用） ----

    public CoverEndpoint endpoint() {
        return part.endpoint();
    }

    @Override
    public String endpointId() {
        return part.endpointId();
    }

    @Override
    public String channelId() {
        return part.channelId();
    }

    public InteractionState state() {
        return part.config()
            .state();
    }

    /** 所连频道的过滤名单（只读用）；未连接或频道没有名单时是一份空的默认名单。 */
    public FilterList filter() {
        Channel channel = WarpServices.isInstalled() ? WarpServices.hub()
            .findChannel(channelId()) : null;
        return channel == null || !channel.type()
            .hasFilter() ? Channel.defaultFilter() : channel.filter();
    }

    @Override
    public EndpointConfig config() {
        return part.config();
    }

    @Override
    public DisconnectReason lastReason() {
        return part.lastReason();
    }

    /** 连接到频道；频道不存在时抛出 IllegalArgumentException。 */
    @Override
    public void connect(String newChannelId, String connector) {
        part.connect(newChannelId, connector);
    }

    @Override
    public void disconnect() {
        part.disconnect();
    }

    @Override
    public void configChanged() {
        part.configChanged();
    }

    public void setInteractionState(InteractionState newState) {
        part.setInteractionState(newState);
    }

    public CoverVisual currentVisual() {
        return part.currentVisual();
    }

    // ---- 外观与 WAILA ----

    @Override
    protected String guiKey() {
        return channelType().key();
    }

    @Override
    protected ITexture composeTexture(ITexture base) {
        return CoverMarkTextures.compose(base, part.clientVisual());
    }

    /** WAILA 的覆盖板行（GT 只给一行）：频道名、交互状态、速率、当前状态。 */
    @Override
    public String getDescription() {
        return part.describe();
    }
}
