package com.rtgame.warpcovers.gt;

import com.cleanroommc.modularui.api.drawable.IDrawable;
import com.cleanroommc.modularui.drawable.GuiDraw;
import com.cleanroommc.modularui.screen.viewport.GuiContext;
import com.cleanroommc.modularui.theme.WidgetTheme;

/**
 * 频道配置面板的文件夹式分页样式，后续分页都沿用。
 * 选中的标签和内容卡片同色，并盖住卡片上沿，两者连成一块；未选中的标签更深、更矮，退到卡片后面。
 */
final class PaneStyle {

    private static final int CARD_FILL = 0xFFD6D6D6;
    private static final int CARD_LIGHT = 0xFFFFFFFF;
    private static final int CARD_DARK = 0xFF7E7E7E;

    /** 内容卡片。 */
    static final IDrawable CARD = new Bevel(CARD_FILL, CARD_LIGHT, CARD_DARK, 0, true);
    /** 选中的标签：底边敞开，正好盖住卡片上沿。 */
    static final IDrawable TAB_SELECTED = new Bevel(CARD_FILL, CARD_LIGHT, CARD_DARK, 0, false);
    /** 未选中的标签：比选中的矮 2 像素，并留出卡片上沿。 */
    static final IDrawable TAB_IDLE = new Bevel(0xFF6A6A6A, 0xFF8A8A8A, 0xFF4A4A4A, 2, false) {

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
            super.draw(context, x, y, width, height - 1, theme);
        }
    };
    /** 绿色按钮：可执行的连接操作、已选中的交互状态。 */
    static final IDrawable GREEN_BUTTON = new Bevel(0xFF2E9E2E, 0xFF74D874, 0xFF145C14, 0, true);
    /** 灰显的「连接」分页盖在内容上的半透明层，颜色取自卡片。 */
    static final int GREY_OUT = 0xB0D6D6D6;

    private PaneStyle() {}

    /** 斜面矩形：上、左边亮，右、下边暗。 */
    private static class Bevel implements IDrawable {

        private final int fill;
        private final int light;
        private final int dark;
        private final int topInset;
        private final boolean bottomEdge;

        Bevel(int fill, int light, int dark, int topInset, boolean bottomEdge) {
            this.fill = fill;
            this.light = light;
            this.dark = dark;
            this.topInset = topInset;
            this.bottomEdge = bottomEdge;
        }

        @Override
        public void draw(GuiContext context, int x, int y, int width, int height, WidgetTheme theme) {
            int top = y + topInset;
            int h = height - topInset;
            GuiDraw.drawRect(x, top, width, h, fill);
            GuiDraw.drawRect(x, top, width, 1, light);
            GuiDraw.drawRect(x, top, 1, h, light);
            GuiDraw.drawRect(x + width - 1, top, 1, h, dark);
            if (bottomEdge) {
                GuiDraw.drawRect(x, top + h - 1, width, 1, dark);
            }
        }
    }
}
