package com.rtgame.warpcovers.gt;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;
import net.minecraft.util.ResourceLocation;

import com.rtgame.warpcovers.WarpCovers;

import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;

/** 覆盖板的中心印记和交互状态箭头。印记是白底灰度图，按频道颜色染色；箭头不染色。 */
final class CoverMarkTextures {

    private static final Icon MARK = new Icon("cover_mark");
    private static final Icon ARROW_OUT = new Icon("cover_arrow_out");
    private static final Icon ARROW_IN = new Icon("cover_arrow_in");
    private static final Icon SEGMENT_LEFT = new Icon("cover_segment_left");
    private static final Icon SEGMENT_MIDDLE = new Icon("cover_segment_middle");
    private static final Icon SEGMENT_RIGHT = new Icon("cover_segment_right");

    private CoverMarkTextures() {}

    /** 在方块贴图拼接时注册图标。 */
    static void register() {
        GregTechAPI.sGTBlockIconload.add(MARK);
        GregTechAPI.sGTBlockIconload.add(ARROW_OUT);
        GregTechAPI.sGTBlockIconload.add(ARROW_IN);
        GregTechAPI.sGTBlockIconload.add(SEGMENT_LEFT);
        GregTechAPI.sGTBlockIconload.add(SEGMENT_MIDDLE);
        GregTechAPI.sGTBlockIconload.add(SEGMENT_RIGHT);
    }

    /** 底图（表示覆盖板类型）+ 染色的印记 + 箭头。 */
    static ITexture compose(ITexture base, CoverVisual visual) {
        ITexture mark = TextureFactory.of(MARK, tint(visual));
        switch (visual.arrow()) {
            case OUTWARD:
                return TextureFactory.of(base, mark, TextureFactory.of(ARROW_OUT));
            case INWARD:
                return TextureFactory.of(base, mark, TextureFactory.of(ARROW_IN));
            default:
                return TextureFactory.of(base, mark);
        }
    }

    /** 聚合覆盖板：印记分三段，从左到右是流体、物品、能量频道的颜色。 */
    static ITexture composeSegments(ITexture base, CoverVisual left, CoverVisual middle, CoverVisual right) {
        return TextureFactory.of(
            base,
            TextureFactory.of(SEGMENT_LEFT, tint(left)),
            TextureFactory.of(SEGMENT_MIDDLE, tint(middle)),
            TextureFactory.of(SEGMENT_RIGHT, tint(right)));
    }

    private static short[] tint(CoverVisual visual) {
        int color = visual.markColor();
        return new short[] { (short) (color >> 16 & 0xFF), (short) (color >> 8 & 0xFF), (short) (color & 0xFF), 0 };
    }

    private static final class Icon implements IIconContainer, Runnable {

        private final String name;
        private IIcon icon;

        Icon(String name) {
            this.name = name;
        }

        @Override
        public void run() {
            icon = GregTechAPI.sBlockIcons.registerIcon(WarpCovers.MODID + ":" + name);
        }

        @Override
        public IIcon getIcon() {
            return icon;
        }

        @Override
        public IIcon getOverlayIcon() {
            return null;
        }

        @Override
        public ResourceLocation getTextureFile() {
            return TextureMap.locationBlocksTexture;
        }
    }
}
