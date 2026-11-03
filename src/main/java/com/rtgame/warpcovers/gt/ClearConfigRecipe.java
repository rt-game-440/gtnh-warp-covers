package com.rtgame.warpcovers.gt;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.world.World;

/** 合成台里只放一个带配置的折跃覆盖板 → 同种空白覆盖板。 */
public final class ClearConfigRecipe implements IRecipe {

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        return findOnly(grid) != null;
    }

    @Override
    public ItemStack getCraftingResult(InventoryCrafting grid) {
        ItemStack configured = findOnly(grid);
        if (configured == null) {
            return null;
        }
        return new ItemStack(configured.getItem(), 1, configured.getItemDamage());
    }

    /** 网格里恰好只有一个物品、并且是带配置的折跃覆盖板时返回它。 */
    private static ItemStack findOnly(InventoryCrafting grid) {
        ItemStack found = null;
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack stack = grid.getStackInSlot(i);
            if (stack == null) {
                continue;
            }
            if (found != null || WarpCoverItem.configOf(stack) == null) {
                return null;
            }
            found = stack;
        }
        return found;
    }

    @Override
    public int getRecipeSize() {
        return 1;
    }

    @Override
    public ItemStack getRecipeOutput() {
        return null;
    }
}
