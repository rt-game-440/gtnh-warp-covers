package com.rtgame.warpcovers.gt;

import javax.annotation.Nonnull;

import net.minecraftforge.fluids.Fluid;

import com.rtgame.warpcovers.core.ChannelType;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;

/** 流体折跃覆盖板：经 GT 方块自己的流体接口抽取和灌入，面规则与 GT 泵覆盖板一致。 */
public class FluidWarpCover extends WarpCover {

    public FluidWarpCover(@Nonnull CoverContext context, ITexture coverTexture) {
        super(context, coverTexture, ChannelType.FLUID);
    }

    @Override
    public boolean letsFluidIn(Fluid fluid) {
        return true;
    }

    @Override
    public boolean letsFluidOut(Fluid fluid) {
        return true;
    }
}
