package com.rtgame.warpcovers.gt;

import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidContainerItem;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.integration.recipeviewer.RecipeViewerGhostIngredientSlot;
import com.cleanroommc.modularui.widget.Widget;

import gregtech.api.util.GTUtility;

/**
 * 名单的添加区：拿着物品或流体容器点一下，或者从 NEI 拖进来，就把对应的物品键或流体名交给接收方。
 * 它不是真正的槽位，不占用也不吞掉任何物品，也不需要和服务端同步槽位，所以能放在纯客户端的配置弹窗里；
 * 键只是请求，是否合法、有没有权限都由服务端判断。
 */
final class FilterDropSlot extends Widget<FilterDropSlot>
    implements Interactable, RecipeViewerGhostIngredientSlot<ItemStack> {

    private final Function<ItemStack, String> keyOf;
    private final Consumer<String> sink;

    /**
     * @param keyOf 把物品转成名单键；认不出时返回 null
     * @param sink  收到名单键时调用
     */
    FilterDropSlot(Function<ItemStack, String> keyOf, Consumer<String> sink) {
        this.keyOf = keyOf;
        this.sink = sink;
    }

    /** 物品名单：物品键（注册名、损伤值、NBT）。 */
    static String itemKey(ItemStack stack) {
        return stack == null ? null : ItemKeys.of(stack);
    }

    /** 流体名单：流体容器（桶、GT 单元、流体展示物品等）里装的流体名。 */
    static String fluidKey(ItemStack stack) {
        if (stack == null) {
            return null;
        }
        FluidStack fluid = FluidContainerRegistry.getFluidForFilledItem(stack);
        if (fluid == null && stack.getItem() instanceof IFluidContainerItem) {
            fluid = ((IFluidContainerItem) stack.getItem()).getFluid(stack);
        }
        if (fluid == null) {
            fluid = GTUtility.getFluidFromDisplayStack(stack);
        }
        return fluid == null || fluid.getFluid() == null ? null : FluidRegistry.getFluidName(fluid);
    }

    @Override
    public Result onMousePressed(int mouseButton) {
        Minecraft mc = Minecraft.getMinecraft();
        ItemStack held = mc.thePlayer == null ? null : mc.thePlayer.inventory.getItemStack();
        return accept(held) ? Result.SUCCESS : Result.IGNORE;
    }

    @Override
    public boolean handleDragAndDrop(ItemStack stack, int button) {
        return accept(stack);
    }

    private boolean accept(ItemStack stack) {
        String key = stack == null ? null : keyOf.apply(stack);
        if (key == null || key.isEmpty()) {
            return false;
        }
        sink.accept(key);
        return true;
    }
}
