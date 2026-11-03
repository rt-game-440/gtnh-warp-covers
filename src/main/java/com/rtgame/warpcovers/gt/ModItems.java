package com.rtgame.warpcovers.gt;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import com.rtgame.warpcovers.WarpCovers;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;
import gregtech.api.covers.CoverRegistry;

/** 模组物品：折跃覆盖板物品及其创造模式标签页。真正的合成配方见后续议题。 */
public final class ModItems {

    public static Item fluidCover;

    public static final CreativeTabs TAB = new CreativeTabs(WarpCovers.MODID) {

        @Override
        public Item getTabIconItem() {
            return fluidCover;
        }
    };

    private ModItems() {}

    /** 注册物品并把它登记为 GT 覆盖板。必须在 GT 完成预加载之后（init 阶段）调用。 */
    public static void register() {
        fluidCover = new Item();
        fluidCover.setUnlocalizedName(WarpCovers.MODID + ".cover.fluid");
        fluidCover.setTextureName(WarpCovers.MODID + ":warp_cover_fluid");
        fluidCover.setCreativeTab(TAB);
        GameRegistry.registerItem(fluidCover, "warp_cover_fluid");

        final ITexture texture = TextureFactory.of(Textures.BlockIcons.OVERLAY_ADV_PUMP);
        CoverRegistry.registerCover(new ItemStack(fluidCover), texture, context -> new FluidWarpCover(context, texture));
    }
}
