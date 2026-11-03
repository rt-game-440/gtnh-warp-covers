package com.rtgame.warpcovers.gt;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nonnull;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fluids.Fluid;

import com.rtgame.warpcovers.ServerConfig;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.Endpoint;
import com.rtgame.warpcovers.core.FilterList;
import com.rtgame.warpcovers.core.InteractionState;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.common.covers.Cover;
import gregtech.api.covers.CoverContext;

/**
 * 流体折跃覆盖板：GT 覆盖板与频道核心之间的适配层。
 * 覆盖板自己持有频道 ID、交互状态和过滤名单（随覆盖板数据存盘）；传输规则全部在核心里。
 */
public class FluidWarpCover extends Cover {

    private static final String NBT_ENDPOINT = "ep";
    private static final String NBT_CHANNEL = "ch";
    private static final String NBT_STATE = "st";
    private static final String NBT_WHITELIST = "wl";
    private static final String NBT_KEYS = "fl";

    private String endpointId = UUID.randomUUID()
        .toString();
    /** 所连频道的内部 ID，空串表示未连接。 */
    private String channelId = "";

    private InteractionState state = InteractionState.EMPTY;
    private final FilterList filter = new FilterList();

    public FluidWarpCover(@Nonnull CoverContext context, ITexture coverTexture) {
        super(context, coverTexture);
    }

    // ---- 对外操作（GUI 议题落地前由自检场景调用） ----

    public String endpointId() {
        return endpointId;
    }

    public String channelId() {
        return channelId;
    }

    public InteractionState state() {
        return state;
    }

    public FilterList filter() {
        return filter;
    }

    /** 连接到频道；频道不存在时抛出 IllegalArgumentException。 */
    public void connect(String newChannelId) {
        ICoverable tile = getTile();
        if (tile == null) {
            throw new IllegalStateException("覆盖板没有贴在机器上");
        }
        ChannelHub hub = WarpServices.hub();
        hub.connect(newChannelId, endpointId, new GtFluidPort(tile, coverSide));
        channelId = newChannelId;
        state = InteractionState.EMPTY;
        hub.setFilter(endpointId, filter);
    }

    public void disconnect() {
        WarpServices.hub()
            .disconnect(endpointId);
        channelId = "";
        state = InteractionState.EMPTY;
    }

    public void setInteractionState(InteractionState newState) {
        WarpServices.hub()
            .setState(endpointId, newState);
        state = newState;
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
    public boolean isRedstoneSensitive(long aTimer) {
        return false;
    }

    @Override
    public void doCoverThings(byte aRedstone, long aTickTimer) {
        ICoverable tile = getTile();
        if (tile == null || !tile.isServerSide() || channelId.isEmpty()) {
            return;
        }
        ChannelHub hub = WarpServices.hub();
        Endpoint endpoint = hub.findEndpoint(endpointId);
        if (endpoint == null) {
            return;
        }
        if (!endpoint.isLoaded()) {
            hub.markLoaded(endpointId, new GtFluidPort(tile, coverSide));
        }
        hub.driveInput(endpointId, aTickTimer);
    }

    @Override
    public void onCoverUnload() {
        WarpServices.hub()
            .markUnloaded(endpointId);
    }

    @Override
    public void onCoverRemoval() {
        WarpServices.hub()
            .disconnect(endpointId);
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
        tag.setString(NBT_ENDPOINT, endpointId);
        tag.setString(NBT_CHANNEL, channelId);
        tag.setString(NBT_STATE, state.name());
        tag.setBoolean(NBT_WHITELIST, filter.isWhitelist());
        NBTTagList list = new NBTTagList();
        for (String key : filter.keys()) {
            list.appendTag(new NBTTagString(key));
        }
        tag.setTag(NBT_KEYS, list);
        return tag;
    }

    @Override
    protected void readDataFromNbt(NBTBase nbt) {
        if (!(nbt instanceof NBTTagCompound)) {
            return;
        }
        NBTTagCompound tag = (NBTTagCompound) nbt;
        if (tag.hasKey(NBT_ENDPOINT)) {
            endpointId = tag.getString(NBT_ENDPOINT);
        }
        channelId = tag.getString(NBT_CHANNEL);
        try {
            state = InteractionState.valueOf(tag.getString(NBT_STATE));
        } catch (IllegalArgumentException e) {
            state = InteractionState.EMPTY;
        }
        filter.setWhitelist(!tag.hasKey(NBT_WHITELIST) || tag.getBoolean(NBT_WHITELIST));
        List<String> keys = new ArrayList<String>();
        NBTTagList list = tag.getTagList(NBT_KEYS, 8);
        for (int i = 0; i < list.tagCount(); i++) {
            keys.add(list.getStringTagAt(i));
        }
        filter.replaceKeys(keys);
    }
}
