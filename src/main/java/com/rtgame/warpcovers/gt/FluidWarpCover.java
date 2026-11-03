package com.rtgame.warpcovers.gt;

import javax.annotation.Nonnull;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;

import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.MachinePort;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;

/** 流体折跃覆盖板：经 GT 方块自己的流体接口抽取和灌入，面规则与 GT 泵覆盖板一致。 */
public class FluidWarpCover extends WarpCover {

    public FluidWarpCover(@Nonnull CoverContext context, ITexture coverTexture) {
        super(context, coverTexture);
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.FLUID;
    }

    @Override
    protected MachinePort createPort(ICoverable tile, ForgeDirection side) {
        return new GtFluidPort(tile, side);
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
