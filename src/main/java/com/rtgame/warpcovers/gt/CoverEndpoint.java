package com.rtgame.warpcovers.gt;

import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.EndpointHost;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.Location;
import com.rtgame.warpcovers.core.MachinePort;
import com.rtgame.warpcovers.core.Permission;
import com.rtgame.warpcovers.core.ReconcileResult;
import com.rtgame.warpcovers.core.TransferResult;

import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 覆盖板上的一个连接端：持有连接端 ID、所连频道和配置（覆盖板数据是配置的权威），
 * 负责与核心索引对账、绑定和解绑。单类型覆盖板有一个，聚合覆盖板有三个。
 */
public final class CoverEndpoint implements EndpointHost {

    /** 为所贴方块的某一面创建机器端口。 */
    public interface PortFactory {

        MachinePort create(ICoverable tile, ForgeDirection side);
    }

    public static final String NBT_ENDPOINT = "ep";
    public static final String NBT_CHANNEL = "ch";
    public static final String NBT_CHANNEL_NAME = "chn";
    public static final String NBT_REASON = "lr";

    private final PortFactory portFactory;
    private final Runnable changeListener;

    private String endpointId = newId();
    /** 所连频道的内部 ID，空串表示未连接。 */
    private String channelId = "";
    /** 频道名缓存：只用于物品提示和频道已不在时的显示。 */
    private String channelName = "";
    private final EndpointConfig config = new EndpointConfig();
    /** 最近一次被动断开的原因；null 表示没有。 */
    private DisconnectReason lastReason;
    /** 贴上它的玩家（UUID 串）：下一次重贴时作为连接者。 */
    private String pendingConnector;

    /** 当前绑定的核心；null 表示还没对账或已卸载。 */
    private ChannelHub boundHub;

    public CoverEndpoint(PortFactory portFactory, Runnable changeListener) {
        this.portFactory = portFactory;
        this.changeListener = changeListener;
    }

    private static String newId() {
        return UUID.randomUUID()
            .toString();
    }

    public String endpointId() {
        return endpointId;
    }

    public String channelId() {
        return channelId;
    }

    public boolean isConnected() {
        return !channelId.isEmpty();
    }

    public String channelName() {
        return channelName;
    }

    public EndpointConfig config() {
        return config;
    }

    public DisconnectReason lastReason() {
        return lastReason;
    }

    /** 没有任何需要随物品保留的内容（掉落时可以和空白覆盖板堆叠）。 */
    public boolean isBlank() {
        return channelId.isEmpty() && config.equals(new EndpointConfig());
    }

    // ---- 生命周期 ----

    /** 玩家把覆盖板贴上时调用：记下贴者，下一次对账若需重贴就以他为连接者。 */
    public void onPlayerAttach(ICoverable tile, ForgeDirection side, String playerUuid) {
        pendingConnector = playerUuid;
        if (tile.isServerSide()) {
            ensureBound(tile, side);
        }
    }

    /** 覆盖板运行周期：对账（必要时）后驱动输入端传输。 */
    public TransferResult tick(ICoverable tile, ForgeDirection side, long now) {
        if (!tile.isServerSide() || isGhost(tile)) {
            return null;
        }
        ensureBound(tile, side);
        if (channelId.isEmpty()) {
            return null;
        }
        return boundHub.driveInput(endpointId, now);
    }

    /**
     * 必要时与核心对账：第一次运行、核心换了（重开存档）、或者索引里已不是本覆盖板持有。
     */
    public void ensureBound(ICoverable tile, ForgeDirection side) {
        ChannelHub hub = WarpServices.hub();
        if (hub == boundHub && (channelId.isEmpty() || hub.isBoundTo(endpointId, this))) {
            return;
        }
        boundHub = hub;
        Location location = locationOf(tile, side);
        MachinePort port = portFactory.create(tile, side);
        ReconcileResult result = hub.reconcile(endpointId, channelId, location, config, port, this);
        switch (result.kind()) {
            case CONNECTED:
                refreshChannelName(hub);
                break;
            case DISCONNECTED:
                clearChannel(result.reason());
                break;
            case REATTACH:
                // 物品化后重贴：新的连接端 ID，排在连接顺序末尾，贴者为连接者；贴者没有连接权限时只断开频道
                String connector = connectorFor(tile);
                Channel channel = hub.findChannel(channelId);
                if (hub.identity()
                    .isReady() && !hub.hasPermission(connector, channel, Permission.CONNECT)) {
                    clearChannel(DisconnectReason.NO_PERMISSION);
                    break;
                }
                endpointId = newId();
                hub.connect(channelId, endpointId, location, connector, config, port, this);
                refreshChannelName(hub);
                changeListener.run();
                break;
            default:
                break;
        }
        pendingConnector = null;
    }

    /**
     * 方块实体已不是世界里该位置上的那一个（区块卸载后同一 tick 内又被重新加载时，旧实体还会再更新一次）。
     * 这种「幽灵」实体的覆盖板不能动作，否则会把索引抢回到旧实体上。
     */
    private static boolean isGhost(ICoverable tile) {
        if (!(tile instanceof TileEntity)) {
            return false;
        }
        TileEntity te = (TileEntity) tile;
        return te.isInvalid() || te.getWorldObj() == null
            || te.getWorldObj()
                .getTileEntity(te.xCoord, te.yCoord, te.zCoord) != te;
    }

    /** 覆盖板随区块卸载：连接端留在频道上，但不再参与传输。 */
    public void onUnload() {
        if (boundHub != null) {
            boundHub.unbind(endpointId, this);
            boundHub = null;
        }
    }

    /** 覆盖板被撬下或机器被挖掉（物品化）：从索引移除，但保留频道和配置以便写进物品。 */
    public void onItemized(ICoverable tile) {
        if (tile == null || !tile.isServerSide()) {
            return;
        }
        WarpServices.hub()
            .disconnect(endpointId);
        boundHub = null;
    }

    // ---- 玩家操作 ----

    /** 连接到频道（交互状态回到「空」）。频道不存在时抛出 IllegalArgumentException。 */
    public void connect(ICoverable tile, ForgeDirection side, String newChannelId, String connector) {
        ChannelHub hub = WarpServices.hub();
        config.setState(InteractionState.EMPTY);
        hub.connect(
            newChannelId,
            endpointId,
            locationOf(tile, side),
            connector,
            config,
            portFactory.create(tile, side),
            this);
        boundHub = hub;
        channelId = newChannelId;
        lastReason = null;
        refreshChannelName(hub);
        changeListener.run();
    }

    /** 主动断开（不记原因）。 */
    public void disconnect() {
        WarpServices.hub()
            .disconnect(endpointId);
        channelId = "";
        channelName = "";
        lastReason = null;
        config.setState(InteractionState.EMPTY);
        changeListener.run();
    }

    public void setState(InteractionState state) {
        config.setState(state);
        WarpServices.hub()
            .configChanged(endpointId);
        changeListener.run();
    }

    /** 名单、优先级等配置在共享对象上改过之后调用。 */
    public void configChanged() {
        WarpServices.hub()
            .configChanged(endpointId);
        changeListener.run();
    }

    // ---- EndpointHost ----

    @Override
    public void onDisconnected(DisconnectReason reason) {
        clearChannel(reason);
    }

    @Override
    public void onEndpointChanged() {
        changeListener.run();
    }

    private void clearChannel(DisconnectReason reason) {
        channelId = "";
        channelName = "";
        config.setState(InteractionState.EMPTY);
        lastReason = reason;
        changeListener.run();
    }

    private void refreshChannelName(ChannelHub hub) {
        Channel channel = hub.findChannel(channelId);
        if (channel != null) {
            channelName = channel.name();
        }
    }

    private String connectorFor(ICoverable tile) {
        if (pendingConnector != null) {
            return pendingConnector;
        }
        // 随机器物品放下的覆盖板没有贴者：用机器主人兜底
        if (tile instanceof IGregTechTileEntity) {
            UUID owner = ((IGregTechTileEntity) tile).getOwnerUuid();
            if (owner != null) {
                return owner.toString();
            }
        }
        return "";
    }

    static Location locationOf(ICoverable tile, ForgeDirection side) {
        return new Location(
            tile.getWorld().provider.dimensionId,
            tile.getXCoord(),
            tile.getYCoord(),
            tile.getZCoord(),
            side.ordinal());
    }

    static void markTileDirty(ICoverable tile) {
        if (tile instanceof TileEntity) {
            ((TileEntity) tile).markDirty();
        }
    }

    // ---- NBT ----

    /** 写进覆盖板数据（随方块存盘）。 */
    public void writeNbt(NBTTagCompound tag) {
        tag.setString(NBT_ENDPOINT, endpointId);
        if (!channelName.isEmpty()) {
            tag.setString(NBT_CHANNEL_NAME, channelName);
        }
        if (lastReason != null) {
            tag.setString(NBT_REASON, lastReason.key());
        }
        writeConfig(tag);
    }

    /**
     * 写进物品：不带连接端 ID（重贴总是新 ID），但带频道名供提示显示。
     *
     * @param serverSide 在服务端时从核心取最新的频道名，否则用缓存
     */
    public void writeItemNbt(NBTTagCompound tag, boolean serverSide) {
        if (!channelId.isEmpty()) {
            Channel channel = serverSide && WarpServices.isInstalled() ? WarpServices.hub()
                .findChannel(channelId) : null;
            tag.setString(NBT_CHANNEL_NAME, channel != null ? channel.name() : channelName);
        }
        writeConfig(tag);
    }

    private void writeConfig(NBTTagCompound tag) {
        tag.setString(NBT_CHANNEL, channelId);
        ConfigNbt.write(config, tag);
    }

    public void readNbt(NBTTagCompound tag) {
        if (tag.hasKey(NBT_ENDPOINT)) {
            endpointId = tag.getString(NBT_ENDPOINT);
        }
        channelId = tag.getString(NBT_CHANNEL);
        channelName = tag.getString(NBT_CHANNEL_NAME);
        lastReason = DisconnectReason.byKey(tag.getString(NBT_REASON));
        ConfigNbt.read(tag, config);
        boundHub = null;
    }
}
