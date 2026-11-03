package com.rtgame.warpcovers.gt;

import javax.annotation.Nonnull;

import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.MachinePort;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;

/**
 * 物品折跃覆盖板：经 GT 方块所在面的物品接口抽取和放入，面规则与 GT 传送带覆盖板一致。
 * 覆盖板放行所有槽位，具体哪些槽能抽、能放由机器自己决定。
 */
public class ItemWarpCover extends WarpCover {

    public ItemWarpCover(@Nonnull CoverContext context, ITexture coverTexture) {
        super(context, coverTexture);
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.ITEM;
    }

    @Override
    protected MachinePort createPort(ICoverable tile, ForgeDirection side) {
        return new GtItemPort(tile, side);
    }

    @Override
    public boolean letsItemsIn(int slot) {
        return true;
    }

    @Override
    public boolean letsItemsOut(int slot) {
        return true;
    }
}
