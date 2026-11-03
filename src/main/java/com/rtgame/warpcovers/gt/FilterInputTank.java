package com.rtgame.warpcovers.gt;

import java.util.function.Consumer;

import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidTank;

/**
 * 名单输入用的虚拟储罐：界面上的标记流体槽把它当储罐，放入流体（光标上的流体容器或 NEI 拖入）
 * 只把流体名交给接收方加入名单，自己永远是空的。客户端构建时不传接收方，任何改动都以服务端为准。
 */
final class FilterInputTank implements IFluidTank {

    private final Consumer<String> sink;

    /** @param sink 收到流体名时调用；客户端传 null */
    FilterInputTank(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public FluidStack getFluid() {
        return null;
    }

    @Override
    public int getFluidAmount() {
        return 0;
    }

    @Override
    public int getCapacity() {
        return 1000;
    }

    @Override
    public FluidTankInfo getInfo() {
        return new FluidTankInfo(this);
    }

    @Override
    public int fill(FluidStack resource, boolean doFill) {
        if (resource == null || resource.getFluid() == null) {
            return 0;
        }
        int amount = Math.max(1, resource.amount);
        if (doFill && sink != null) {
            String name = FluidRegistry.getFluidName(resource);
            if (name != null) {
                sink.accept(name);
            }
        }
        return amount;
    }

    @Override
    public FluidStack drain(int maxDrain, boolean doDrain) {
        return null;
    }
}
