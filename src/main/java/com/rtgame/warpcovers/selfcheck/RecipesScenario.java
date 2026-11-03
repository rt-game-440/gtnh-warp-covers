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
        return missing.isEmpty() ? SelfCheckResult.pass("5 个组装机配方与转换配方均已注册")
            : SelfCheckResult.fail("缺少配方: " + missing);
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
            if (out != null && out.getItem() == output && !(recipe instanceof com.rtgame.warpcovers.gt.ClearConfigRecipe)) {
                return true;
            }
        }
        return false;
    }

    private static String missing() {
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
