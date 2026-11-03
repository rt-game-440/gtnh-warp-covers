package com.rtgame.warpcovers.gt;

import java.util.Locale;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.EndpointStatus;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.MachinePort;
import com.rtgame.warpcovers.core.PanelEndpoint;

import gregtech.api.interfaces.tileentity.ICoverable;

/**
 * 覆盖板上的一个连接端（单类型覆盖板一个，聚合覆盖板三个）：界面操作的对象，
 * 各自有频道、配置和显示信息。
 */
public final class WarpPart implements PanelEndpoint {

    private final ChannelType type;
    private final BaseWarpCover cover;
    private final CoverEndpoint endpoint;
    /** 客户端最近收到的显示信息。 */
    private CoverVisual visual = CoverVisual.UNCONNECTED;

    WarpPart(ChannelType type, BaseWarpCover cover) {
        this.type = type;
        this.cover = cover;
        this.endpoint = new CoverEndpoint(new CoverEndpoint.PortFactory() {

            @Override
            public MachinePort create(ICoverable tile, ForgeDirection side) {
                return createPort(tile, side);
            }
        }, new Runnable() {

            @Override
            public void run() {
                onChanged();
            }
        });
    }

    private MachinePort createPort(ICoverable tile, ForgeDirection side) {
        switch (type) {
            case FLUID:
                return new GtFluidPort(tile, side);
            case ITEM:
                return new GtItemPort(tile, side);
            default:
                return GtEnergyPort.of(tile);
        }
    }

    private void onChanged() {
        ICoverable tile = cover.getTile();
        if (tile != null) {
            CoverEndpoint.markTileDirty(tile);
            tile.issueCoverUpdate(cover.getSide());
        }
    }

    public ChannelType channelType() {
        return type;
    }

    public CoverEndpoint endpoint() {
        return endpoint;
    }

    @Override
    public String endpointId() {
        return endpoint.endpointId();
    }

    @Override
    public String channelId() {
        return endpoint.channelId();
    }

    @Override
    public EndpointConfig config() {
        return endpoint.config();
    }

    @Override
    public DisconnectReason lastReason() {
        return endpoint.lastReason();
    }

    /** 连接到频道；频道不存在时抛出 IllegalArgumentException。 */
    @Override
    public void connect(String newChannelId, String connector) {
        ICoverable tile = cover.getTile();
        if (tile == null) {
            throw new IllegalStateException("覆盖板没有贴在机器上");
        }
        endpoint.connect(tile, cover.getSide(), newChannelId, connector);
    }

    @Override
    public void disconnect() {
        endpoint.disconnect();
    }

    @Override
    public void configChanged() {
        endpoint.configChanged();
    }

    public void setInteractionState(InteractionState state) {
        endpoint.setState(state);
    }

    /** 能量部分贴在没有内部储能的方块（导线、管道、储罐等）上时不工作；聚合覆盖板才会遇到。 */
    @Override
    public EndpointStatus blockedStatus() {
        ICoverable tile = cover.getTile();
        return type == ChannelType.ENERGY && tile != null && !GtEnergyPort.hasEnergyStorage(tile)
            ? EndpointStatus.NO_ENERGY_STORAGE
            : null;
    }

    // ---- 显示信息 ----

    /** 服务端按当前频道算出显示信息；客户端返回最近收到的。 */
    public CoverVisual currentVisual() {
        ICoverable tile = cover.getTile();
        if (tile == null || !tile.isServerSide() || !WarpServices.isInstalled()) {
            return visual;
        }
        ChannelHub hub = WarpServices.hub();
        Channel channel = hub.findChannel(channelId());
        EndpointStatus blocked = blockedStatus();
        if (channel == null) {
            return blocked == null ? CoverVisual.UNCONNECTED
                : new CoverVisual(false, 0, InteractionState.EMPTY, "", Long.MAX_VALUE, blocked.key());
        }
        EndpointStatus status = blocked != null ? blocked
            : hub.statusOf(
                endpointId(),
                MinecraftServer.getServer()
                    .getTickCounter());
        return new CoverVisual(
            true,
            channel.color(),
            config().state(),
            channel.name(),
            hub.effectiveRate(type, config()),
            status.key());
    }

    /** @return 外观是否变化 */
    boolean applyVisual(CoverVisual next) {
        boolean changed = !next.sameLook(visual);
        visual = next;
        return changed;
    }

    /** 客户端的印记外观信息。 */
    CoverVisual clientVisual() {
        return visual;
    }

    /** WAILA 文本（客户端）：频道名、交互状态、速率、当前状态。 */
    String describe() {
        if (!visual.connected) {
            return StatCollector.translateToLocal("warpcovers.status." + visual.statusKey);
        }
        String rate = visual.rate == Long.MAX_VALUE ? StatCollector.translateToLocal("warpcovers.waila.rate_unlimited")
            : StatCollector.translateToLocalFormatted("warpcovers.waila.rate", visual.rate);
        return StatCollector.translateToLocalFormatted(
            "warpcovers.waila.line",
            visual.channelName,
            StatCollector.translateToLocal(
                "warpcovers.state." + visual.state.name()
                    .toLowerCase(Locale.ROOT)),
            rate,
            StatCollector.translateToLocal("warpcovers.status." + visual.statusKey));
    }
}
