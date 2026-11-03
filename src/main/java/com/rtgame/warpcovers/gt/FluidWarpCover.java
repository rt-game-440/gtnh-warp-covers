package com.rtgame.warpcovers.gt;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;

import com.rtgame.warpcovers.ServerConfig;
import com.rtgame.warpcovers.core.DisconnectReason;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.FilterList;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.core.MachinePort;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.common.covers.Cover;

/**
 * 流体折跃覆盖板：GT 覆盖板与频道核心之间的适配层。
 * 覆盖板自己持有连接端（频道、交互状态、名单等，随覆盖板数据存盘）；传输规则全部在核心里。
 * 撬下或挖掉机器时配置随物品保留。
 */
public class FluidWarpCover extends Cover implements EndpointCarrier {

    private final CoverEndpoint endpoint;

    public FluidWarpCover(@Nonnull CoverContext context, ITexture coverTexture) {
        super(context, coverTexture);
        endpoint = new CoverEndpoint(new CoverEndpoint.PortFactory() {

            @Override
            public MachinePort create(ICoverable tile, ForgeDirection side) {
                return new GtFluidPort(tile, side);
            }
        }, new Runnable() {

            @Override
            public void run() {
                ICoverable tile = getTile();
                if (tile != null) {
                    CoverEndpoint.markTileDirty(tile);
                }
            }
        });
        // 从带配置的物品贴上：物品里的配置就是覆盖板的配置（连接端 ID 总是新的）
        NBTTagCompound itemConfig = WarpCoverItem.configOf(context.getCoverItem());
        if (itemConfig != null) {
            endpoint.readNbt(itemConfig);
        }
    }

    // ---- 对外操作（GUI 议题落地前由自检场景调用） ----

    public CoverEndpoint endpoint() {
        return endpoint;
    }

    public String endpointId() {
        return endpoint.endpointId();
    }

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

    public EndpointConfig config() {
        return endpoint.config();
    }

    public DisconnectReason lastReason() {
        return endpoint.lastReason();
    }

    /** 连接到频道；频道不存在时抛出 IllegalArgumentException。 */
    public void connect(String newChannelId, String connector) {
        endpoint.connect(requireTile(), coverSide, newChannelId, connector);
    }

    public void disconnect() {
        endpoint.disconnect();
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
        // 核心按配置的周期自己判断是否到期；覆盖板每个周期被 GT 调用一次
        return Math.max(1, ServerConfig.defaultPeriodTicks);
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
        ICoverable tile = getTile();
        if (tile != null) {
            endpoint.tick(tile, coverSide, aTickTimer);
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
    public boolean letsFluidIn(Fluid fluid) {
        return true;
    }

    @Override
    public boolean letsFluidOut(Fluid fluid) {
        return true;
    }

    @Override
    public boolean alwaysLookConnected() {
        return true;
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
