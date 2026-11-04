package com.rtgame.warpcovers.selfcheck;

import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.CraftingManager;
import net.minecraft.item.crafting.IRecipe;

import com.rtgame.warpcovers.gt.ModItems;

import gregtech.api.recipe.RecipeMaps;
import gregtech.api.util.GTRecipe;

/** 配方自检（#17）：全部开启时，五种组装机配方与末影流体覆盖板转换配方都已注册。 */
public final class RecipesScenario implements SelfCheckScenario {

    @Override
    public String name() {
        return "recipes";
    }

    @Override
    public void setup() {
        String missing = missing();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("缺少配方: " + missing);
        }
    }

    @Override
    public SelfCheckResult verify() {
        String missing = missing();
        return missing.isEmpty() ? SelfCheckResult.pass("5 个组装机配方与转换配方均已注册") : SelfCheckResult.fail("缺少配方: " + missing);
    }

    private static boolean hasAssembler(Item output) {
        for (GTRecipe recipe : RecipeMaps.assemblerRecipes.getAllRecipes()) {
            if (recipe.mOutputs != null && recipe.mOutputs.length > 0
                && recipe.mOutputs[0] != null
                && recipe.mOutputs[0].getItem() == output) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static boolean hasCraftingRecipe(Item output) {
        for (IRecipe recipe : (List<IRecipe>) CraftingManager.getInstance()
            .getRecipeList()) {
            ItemStack out = recipe.getRecipeOutput();
            if (out != null && out.getItem() == output
                && !(recipe instanceof com.rtgame.warpcovers.gt.ClearConfigRecipe)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameItem(ItemStack a, ItemStack b) {
        return a != null && b != null
            && a.getItem() == b.getItem()
            && (a.getItemDamage() == b.getItemDamage()
                || a.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE
                || b.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE);
    }

    /** a 的每个输入都能被 b 的某个输入满足（物品相同且数量不超过）。 */
    private static boolean covered(GTRecipe a, GTRecipe b) {
        if (a.mInputs == null || a.mInputs.length == 0) {
            return false;
        }
        for (ItemStack need : a.mInputs) {
            if (need == null) {
                continue;
            }
            boolean ok = false;
            for (ItemStack have : b.mInputs) {
                if (sameItem(need, have) && have.stackSize >= need.stackSize) {
                    ok = true;
                    break;
                }
            }
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** 与我们的组装机配方互相包含（会抢配方或被抢）的其他配方，以及会吃掉末影覆盖板的其他工作台配方。 */
    @SuppressWarnings("unchecked")
    private static String conflicts() {
        StringBuilder sb = new StringBuilder();
        Item[] outputs = { ModItems.fluidCover, ModItems.itemCover, ModItems.energyCover, ModItems.aggregateCover,
            ModItems.manager };
        java.util.List<GTRecipe> ours = new java.util.ArrayList<GTRecipe>();
        java.util.List<GTRecipe> others = new java.util.ArrayList<GTRecipe>();
        for (GTRecipe recipe : RecipeMaps.assemblerRecipes.getAllRecipes()) {
            boolean mine = false;
            for (Item output : outputs) {
                if (recipe.mOutputs != null && recipe.mOutputs.length > 0
                    && recipe.mOutputs[0] != null
                    && recipe.mOutputs[0].getItem() == output) {
                    mine = true;
                }
            }
            (mine ? ours : others).add(recipe);
        }
        for (GTRecipe mine : ours) {
            for (GTRecipe other : others) {
                if (other.mInputs != null && other.mInputs.length > 0
                    && (covered(other, mine) || covered(mine, other))) {
                    sb.append(mine.mOutputs[0].getDisplayName())
                        .append(" 与 ")
                        .append(
                            other.mOutputs != null && other.mOutputs.length > 0 && other.mOutputs[0] != null
                                ? other.mOutputs[0].getDisplayName()
                                : "?")
                        .append(" 输入互相包含; ");
                }
            }
        }
        ItemStack ender = tectech.thing.CustomItemList.enderLinkFluidCover.get(1);
        net.minecraft.inventory.InventoryCrafting grid = new net.minecraft.inventory.InventoryCrafting(
            new net.minecraft.inventory.Container() {

                @Override
                public boolean canInteractWith(net.minecraft.entity.player.EntityPlayer player) {
                    return true;
                }
            },
            3,
            3);
        grid.setInventorySlotContents(0, ender);
        net.minecraft.world.World world = net.minecraft.server.MinecraftServer.getServer().worldServers[0];
        for (IRecipe recipe : (List<IRecipe>) CraftingManager.getInstance()
            .getRecipeList()) {
            if (recipe.matches(grid, world)) {
                ItemStack out = recipe.getRecipeOutput();
                if (out == null || out.getItem() != ModItems.fluidCover) {
                    sb.append("末影流体覆盖板单独放入合成台还会匹配: ")
                        .append(
                            out == null ? recipe.getClass()
                                .getSimpleName() : out.getDisplayName())
                        .append("; ");
                }
            }
        }
        return sb.toString();
    }

    private static String missing() {
        String conflicts = conflicts();
        if (!conflicts.isEmpty()) {
            return "冲突: " + conflicts;
        }
        StringBuilder sb = new StringBuilder();
        Item[] outputs = { ModItems.fluidCover, ModItems.itemCover, ModItems.energyCover, ModItems.aggregateCover,
            ModItems.manager };
        String[] names = { "流体", "物品", "能量", "聚合", "管理器" };
        for (int i = 0; i < outputs.length; i++) {
            if (!hasAssembler(outputs[i])) {
                sb.append(names[i])
                    .append("组装机配方 ");
            }
        }
        if (!hasCraftingRecipe(ModItems.fluidCover)) {
            sb.append("末影转换配方 ");
        }
        return sb.toString();
    }
}
