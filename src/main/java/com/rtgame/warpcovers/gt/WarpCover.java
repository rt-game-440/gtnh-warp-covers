package com.rtgame.warpcovers.gt;

import java.util.Locale;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.io.ByteArrayDataInput;
import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.FilterList;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.MachinePort;
import com.rtgame.warpcovers.core.PanelEndpoint;
import com.rtgame.warpcovers.core.Periods;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.covers.Cover;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;
import io.netty.buffer.ByteBuf;

/**
 * 单类型折跃覆盖板：GT 覆盖板与频道核心之间的适配层。
 * 覆盖板自己持有连接端（频道、交互状态、名单等，随覆盖板数据存盘）；传输规则全部在核心里。
 * 撬下或挖掉机器时配置随物品保留。子类只决定频道类型、机器端口和放行哪种物体。
 */
public abstract class WarpCover extends Cover implements EndpointCarrier, PanelEndpoint {

    private final CoverEndpoint endpoint;
    /** 客户端最近收到的显示信息。 */
    private CoverVisual visual = CoverVisual.UNCONNECTED;
    /** 按 visual 合成的贴图缓存；外观变化时清空。 */
    private ITexture composedTexture;

    protected WarpCover(@Nonnull CoverContext context, ITexture coverTexture) {
        super(context, coverTexture);
        endpoint = new CoverEndpoint(new CoverEndpoint.PortFactory() {

            @Override
            public MachinePort create(ICoverable tile, ForgeDirection side) {
                return createPort(tile, side);
            }
        }, new Runnable() {

            @Override
            public void run() {
                ICoverable tile = getTile();
                if (tile != null) {
                    CoverEndpoint.markTileDirty(tile);
                    tile.issueCoverUpdate(coverSide);
                }
            }
        });
        // 从带配置的物品贴上：物品里的配置就是覆盖板的配置（连接端 ID 总是新的）
        NBTTagCompound itemConfig = WarpCoverItem.configOf(context.getCoverItem());
        if (itemConfig != null) {
            endpoint.readNbt(itemConfig);
        }
    }

    /** 覆盖板能连接的频道类型。 */
    public abstract ChannelType channelType();

    /** 把覆盖板所在的那一面包装成机器端口。 */
    protected abstract MachinePort createPort(ICoverable tile, ForgeDirection side);

    // ---- 对外操作（界面经 CoverPanel 调用，自检场景直接调用） ----

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

    public InteractionState state() {
        return endpoint.config()
            .state();
    }

    public FilterList filter() {
        return endpoint.config()
            .filter();
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
        endpoint.connect(requireTile(), coverSide, newChannelId, connector);
    }

    @Override
    public void disconnect() {
        endpoint.disconnect();
    }

    @Override
    public void configChanged() {
        endpoint.configChanged();
    }

    public void setInteractionState(InteractionState newState) {
        endpoint.setState(newState);
    }

    private ICoverable requireTile() {
        ICoverable tile = getTile();
        if (tile == null) {
            throw new IllegalStateException("覆盖板没有贴在机器上");
        }
        return tile;
    }

    @Override
    public boolean carriesEndpoint(String endpointId) {
        return endpoint.endpointId()
            .equals(endpointId);
    }

    // ---- GT 覆盖板接口 ----

    @Override
    public int getMinimumTickRate() {
        // GT 每 tick 都读这个值，所以改周期立即生效；是否到期由核心判断。endpoint 在父类构造期间还没赋值
        if (endpoint == null) {
            return Periods.MAX_TICK_INTERVAL;
        }
        return Periods.tickInterval(
            Periods.effectivePeriod(
                endpoint.config()
                    .period(),
                WarpServices.CONFIG));
    }

    @Override
    public boolean allowsTickRateAddition() {
        return false;
    }

    @Override
    public boolean allowsCopyPasteTool() {
        // 复制粘贴会把连接端 ID 一起复制，造成两个覆盖板共用一个连接端
        return false;
    }

    @Override
    public boolean isRedstoneSensitive(long aTimer) {
        return false;
    }

    @Override
    public void onPlayerAttach(EntityPlayer player, ItemStack coverItem) {
        ICoverable tile = getTile();
        if (tile != null) {
            endpoint.onPlayerAttach(
                tile,
                coverSide,
                player == null ? null
                    : player.getUniqueID()
                        .toString());
        }
    }

    @Override
    public void doCoverThings(byte aRedstone, long aTickTimer) {
        // 不用 aTickTimer：那是每台机器各自的计时器。同一频道的连接端必须共用一个时钟，输出端的接收窗口才成立
        ICoverable tile = getTile();
        if (tile != null && tile.isServerSide()) {
            tickAt(
                MinecraftServer.getServer()
                    .getTickCounter());
        }
    }

    /** 以指定时间运行一次（自检场景用它控制时间）。 */
    public void tickAt(long now) {
        ICoverable tile = getTile();
        if (tile != null) {
            endpoint.tick(tile, coverSide, now);
        }
    }

    @Override
    public void onCoverUnload() {
        endpoint.onUnload();
    }

    @Override
    public void onCoverRemoval() {
        endpoint.onItemized(getTile());
    }

    @Override
    public void onBaseTEDestroyed() {
        endpoint.onItemized(getTile());
    }

    /** 撬下时掉落的物品：带全部配置（没有配置时就是空白覆盖板，能和其他空白的堆叠）。 */
    @Override
    public ItemStack asItemStack() {
        ItemStack stack = super.asItemStack();
        if (stack != null && !endpoint.isBlank()) {
            NBTTagCompound config = new NBTTagCompound();
            ICoverable tile = getTile();
            endpoint.writeItemNbt(config, tile != null && tile.isServerSide());
            WarpCoverItem.setConfig(stack, config);
        }
        return stack;
    }

    @Override
    public boolean hasCoverGUI() {
        return true;
    }

    @Override
    protected @Nonnull CoverBaseGui<?> getCoverGui() {
        return new WarpCoverGui(this);
    }

    @Override
    public boolean alwaysLookConnected() {
        return true;
    }

    // ---- 外观与 WAILA ----

    /** 服务端按当前频道算出显示信息；客户端返回最近收到的。 */
    public CoverVisual currentVisual() {
        ICoverable tile = getTile();
        if (tile == null || !tile.isServerSide() || !WarpServices.isInstalled()) {
            return visual;
        }
        ChannelHub hub = WarpServices.hub();
        Channel channel = hub.findChannel(endpoint.channelId());
        if (channel == null) {
            return CoverVisual.UNCONNECTED;
        }
        return new CoverVisual(
            true,
            channel.color(),
            state(),
            channel.name(),
            hub.effectiveRate(channelType(), config()),
            hub.statusOf(
                endpoint.endpointId(),
                MinecraftServer.getServer()
                    .getTickCounter())
                .key());
    }

    @Override
    public boolean isDataNeededOnClient() {
        return true;
    }

    @Override
    protected void writeDataToByteBuf(ByteBuf buf) {
        currentVisual().write(buf);
    }

    @Override
    protected void readDataFromPacket(ByteArrayDataInput in) {
        CoverVisual next = CoverVisual.read(in);
        boolean lookChanged = !next.sameLook(visual);
        visual = next;
        if (lookChanged) {
            composedTexture = null;
            ICoverable tile = getTile();
            if (tile instanceof IGregTechTileEntity) {
                ((IGregTechTileEntity) tile).issueTextureUpdate();
            }
        }
    }

    @Override
    public ITexture getOverlayTexture() {
        if (composedTexture == null) {
            composedTexture = CoverMarkTextures.compose(super.getOverlayTexture(), visual);
        }
        return composedTexture;
    }

    @Override
    public ITexture getSpecialFaceTexture() {
        return getOverlayTexture();
    }

    /** WAILA 的覆盖板行（GT 只给一行）：频道名、交互状态、速率、当前状态。 */
    @Override
    public String getDescription() {
        if (!visual.connected) {
            return StatCollector.translateToLocal("warpcovers.status.unconnected");
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

    // ---- 存盘 ----

    @Override
    protected @Nonnull NBTBase saveDataToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        endpoint.writeNbt(tag);
        return tag;
    }

    @Override
    protected void readDataFromNbt(NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            endpoint.readNbt((NBTTagCompound) nbt);
        }
    }
}
