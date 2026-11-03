package com.rtgame.warpcovers.gt;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

import com.rtgame.warpcovers.core.MachinePort;
import com.rtgame.warpcovers.core.ObjectStack;

import gregtech.api.interfaces.tileentity.ICoverable;

/**
 * 把 GT 方块（被覆盖板贴着的那一面）包装成流体机器端口。
 * 抽取和插入都走 GT 方块自己的 {@link IFluidHandler}，所以面规则与 GT 泵覆盖板一致：
 * 从覆盖板所在面抽取，向覆盖板所在面插入。
 */
final class GtFluidPort implements MachinePort {

    private final WeakReference<ICoverable> tile;
    private final ForgeDirection side;

    GtFluidPort(ICoverable tile, ForgeDirection side) {
        this.tile = new WeakReference<ICoverable>(tile);
        this.side = side;
    }

    private IFluidHandler handler() {
        ICoverable coverable = tile.get();
        return coverable instanceof IFluidHandler ? (IFluidHandler) coverable : null;
    }

    @Override
    public String blockId() {
        ICoverable coverable = tile.get();
        if (coverable == null) {
            return "gone";
        }
        return coverable.getWorld().provider.dimensionId + ":"
            + coverable.getXCoord()
            + ","
            + coverable.getYCoord()
            + ","
            + coverable.getZCoord();
    }

    @Override
    public List<ObjectStack> extractable() {
        List<ObjectStack> result = new ArrayList<ObjectStack>();
        IFluidHandler handler = handler();
        if (handler == null) {
            return result;
        }
        FluidTankInfo[] infos = handler.getTankInfo(side);
        if (infos == null) {
            return result;
        }
        Set<String> seen = new HashSet<String>();
        for (FluidTankInfo info : infos) {
            if (info == null || info.fluid == null || info.fluid.amount <= 0 || info.fluid.getFluid() == null) {
                continue;
            }
            String key = FluidRegistry.getFluidName(info.fluid);
            if (!seen.add(key)) {
                continue;
            }
            // 用 GT 方块自己的面规则模拟抽取，得到真正抽得出的量
            FluidStack drained = handler.drain(side, new FluidStack(info.fluid.getFluid(), Integer.MAX_VALUE), false);
            if (drained != null && drained.amount > 0) {
                result.add(new ObjectStack(key, drained.amount));
            }
        }
        return result;
    }

    @Override
    public int extract(String key, int amount, boolean simulate) {
        IFluidHandler handler = handler();
        Fluid fluid = FluidRegistry.getFluid(key);
        if (handler == null || fluid == null || amount <= 0) {
            return 0;
        }
        FluidStack drained = handler.drain(side, new FluidStack(fluid, amount), !simulate);
        return drained == null ? 0 : drained.amount;
    }

    @Override
    public int insert(String key, int amount, boolean simulate) {
        IFluidHandler handler = handler();
        Fluid fluid = FluidRegistry.getFluid(key);
        if (handler == null || fluid == null || amount <= 0) {
            return 0;
        }
        return handler.fill(side, new FluidStack(fluid, amount), !simulate);
    }
}
