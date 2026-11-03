package com.rtgame.warpcovers.gt;

import javax.annotation.Nonnull;

import net.minecraftforge.fluids.Fluid;

import com.rtgame.warpcovers.core.ChannelType;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;

/**
 * 聚合折跃覆盖板：一块覆盖板同时连接流体、物品、能量三个频道，三个连接端的配置各自独立。
 * 可以贴在任何接受覆盖板的方块上；贴在没有内部储能的方块上时能量部分不工作。
 */
public class AggregateWarpCover extends BaseWarpCover {

    public AggregateWarpCover(@Nonnull CoverContext context, ITexture coverTexture) {
        super(context, coverTexture, ChannelType.FLUID, ChannelType.ITEM, ChannelType.ENERGY);
    }

    @Override
    protected String guiKey() {
        return "aggregate";
    }

    @Override
    protected ITexture composeTexture(ITexture base) {
        return CoverMarkTextures.composeSegments(
            base,
            part(ChannelType.FLUID).clientVisual(),
            part(ChannelType.ITEM).clientVisual(),
            part(ChannelType.ENERGY).clientVisual());
    }

    /** WAILA 里三个连接端各一行，由 {@link WarpWailaProvider} 添加；GT 的覆盖板行只能放一行，这里留空。 */
    @Override
    public String getDescription() {
        return "";
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
    public boolean letsItemsIn(int slot) {
        return true;
    }

    @Override
    public boolean letsItemsOut(int slot) {
        return true;
    }
}
