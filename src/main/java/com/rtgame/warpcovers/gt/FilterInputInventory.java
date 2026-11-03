package com.rtgame.warpcovers.gt;

import java.util.function.Consumer;

import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.IItemHandlerModifiable;

/**
 * 名单输入用的虚拟单格物品栏：界面上的标记物品槽把它当物品栏，放入物品（光标上的物品或 NEI 拖入）
 * 只把物品键交给接收方加入名单，自己永远是空的，不会吞掉玩家的物品。客户端构建时不传接收方，任何改动都以服务端为准。
 */
final class FilterInputInventory implements IItemHandlerModifiable {

    private final Consumer<String> sink;

    /** @param sink 收到物品键时调用；客户端传 null */
    FilterInputInventory(Consumer<String> sink) {
        this.sink = sink;
    }

    @Override
    public int getSlots() {
        return 1;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return null;
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (stack == null || stack.getItem() == null || sink == null) {
            return;
        }
        String key = ItemKeys.of(stack);
        if (key != null) {
            sink.accept(key);
        }
    }

    @Override
    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        // 只接受标记操作，真正的物品放入一律退回
        return stack;
    }

    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return null;
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }
}
