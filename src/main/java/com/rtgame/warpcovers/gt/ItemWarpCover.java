package com.rtgame.warpcovers.gt;

import javax.annotation.Nonnull;

import com.rtgame.warpcovers.core.ChannelType;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;

/**
 * 物品折跃覆盖板：经 GT 方块所在面的物品接口抽取和放入，面规则与 GT 传送带覆盖板一致。
 * 覆盖板放行所有槽位，具体哪些槽能抽、能放由机器自己决定。
 */
public class ItemWarpCover extends WarpCover {

    public ItemWarpCover(@Nonnull CoverContext context, ITexture coverTexture) {
        super(context, coverTexture, ChannelType.ITEM);
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
