package com.rtgame.warpcovers.gt;

import javax.annotation.Nonnull;

import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.MachinePort;

import gregtech.api.covers.CoverContext;
import gregtech.api.covers.CoverPlacer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;

/**
 * 能量折跃覆盖板：直接读写 GT 机器的内部储能（ADR-0003）。
 * 只能贴在有内部储能的方块上，导线和管道贴不上；没有过滤名单。
 */
public class EnergyWarpCover extends WarpCover {

    /** 放置限制：GT 右键贴覆盖板时会先问这个判断。 */
    static final CoverPlacer PLACER = CoverPlacer.builder()
        .onlyPlaceIf((side, coverItem, tile) -> GtEnergyPort.hasEnergyStorage(tile))
        .build();

    public EnergyWarpCover(@Nonnull CoverContext context, ITexture coverTexture) {
        super(context, coverTexture);
    }

    @Override
    public ChannelType channelType() {
        return ChannelType.ENERGY;
    }

    @Override
    protected MachinePort createPort(ICoverable tile, ForgeDirection side) {
        return GtEnergyPort.of(tile);
    }
}
