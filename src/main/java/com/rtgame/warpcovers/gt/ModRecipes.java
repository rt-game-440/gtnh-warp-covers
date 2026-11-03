package com.rtgame.warpcovers.gt;

import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import com.rtgame.warpcovers.ServerConfig;
import com.rtgame.warpcovers.WarpCovers;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.GTValues;
import gregtech.api.enums.ItemList;
import gregtech.api.enums.Materials;
import gregtech.api.enums.TierEU;
import gregtech.api.recipe.RecipeMaps;
import tectech.thing.CustomItemList;

/**
 * 生存合成配方（草案，需求方确认后定稿，见 issue #17）。被服务器关闭的类型不注册它的配方；
 * 聚合覆盖板要求三类全开，管理器要求至少一类开启。
 */
public final class ModRecipes {

    private static final int SOLDER = 576;
    private static final int DURATION = 400;

    private ModRecipes() {}

    /** 按类型开关算出应注册的配方键：fluid、item、energy、convert（转换）、aggregate、manager。 */
    public static java.util.Set<String> plan(boolean fluid, boolean item, boolean energy) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<String>();
        if (fluid) {
            keys.add("fluid");
            keys.add("convert");
        }
        if (item) {
            keys.add("item");
        }
        if (energy) {
            keys.add("energy");
        }
        if (fluid && item && energy) {
            keys.add("aggregate");
        }
        if (fluid || item || energy) {
            keys.add("manager");
        }
        return keys;
    }

    /** 在 GT 配方表就绪后（postInit）调用。 */
    public static void register() {
        boolean fluid = ServerConfig.enableFluids;
        boolean item = ServerConfig.enableItems;
        boolean energy = ServerConfig.enableEnergy;
        if (fluid) {
            assemble(
                new ItemStack(ModItems.fluidCover),
                DURATION,
                SOLDER,
                ItemList.Electric_Pump_LuV.get(2),
                ItemList.Sensor_LuV.get(1),
                ItemList.Circuit_Master.get(2));
            registerConversion();
        }
        if (item) {
            assemble(
                new ItemStack(ModItems.itemCover),
                DURATION,
                SOLDER,
                ItemList.Conveyor_Module_LuV.get(2),
                ItemList.Robot_Arm_LuV.get(1),
                ItemList.Circuit_Master.get(2));
        }
        if (energy) {
            assemble(
                new ItemStack(ModItems.energyCover),
                DURATION,
                SOLDER,
                ItemList.Hatch_Energy_LuV.get(1),
                ItemList.Hatch_Dynamo_LuV.get(1),
                ItemList.Field_Generator_LuV.get(1),
                ItemList.Circuit_Master.get(2));
        }
        if (fluid && item && energy) {
            GTValues.RA.stdBuilder()
                .itemInputs(
                    new ItemStack(ModItems.fluidCover),
                    new ItemStack(ModItems.itemCover),
                    new ItemStack(ModItems.energyCover),
                    ItemList.Circuit_Master.get(4))
                .fluidInputs(Materials.Polytetrafluoroethylene.getMolten(1440))
                .itemOutputs(new ItemStack(ModItems.aggregateCover))
                .duration(600)
                .eut(TierEU.RECIPE_LuV)
                .addTo(RecipeMaps.assemblerRecipes);
        }
        if (fluid || item || energy) {
            assemble(
                new ItemStack(ModItems.manager),
                DURATION,
                SOLDER,
                ItemList.Sensor_LuV.get(2),
                ItemList.Emitter_LuV.get(1),
                ItemList.Circuit_Master.get(4));
        }
        WarpCovers.LOG.info("已注册折跃覆盖板配方（流体 {}、物品 {}、能量 {}）", fluid, item, energy);
    }

    private static void assemble(ItemStack output, int duration, int solderAmount, ItemStack... inputs) {
        GTValues.RA.stdBuilder()
            .itemInputs(inputs)
            .fluidInputs(Materials.SolderingAlloy.getMolten(solderAmount))
            .itemOutputs(output)
            .duration(duration)
            .eut(TierEU.RECIPE_LuV)
            .addTo(RecipeMaps.assemblerRecipes);
    }

    /** 末影流体覆盖板 → 空白的流体折跃覆盖板，不迁移旧配置。 */
    private static void registerConversion() {
        ItemStack ender = CustomItemList.enderLinkFluidCover.get(1);
        if (ender == null || ender.getItem() == null) {
            WarpCovers.LOG.warn("找不到末影流体覆盖板，跳过转换配方");
            return;
        }
        GameRegistry.addShapelessRecipe(
            new ItemStack(ModItems.fluidCover),
            new ItemStack(ender.getItem(), 1, OreDictionary.WILDCARD_VALUE));
    }
}
