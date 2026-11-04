package com.rtgame.warpcovers.core;

/**
 * 双栏界面的尺寸：按屏幕的 GUI 空间算。屏幕够大时用上限，放不下才收缩；多出来的宽度大半给左栏，高度给频道列表和分页卡片。
 * 宽高都是内容区，GT 面板四周另有 {@link #PANEL_BORDER} 像素边距。
 */
public final class DualPaneLayout {

    /** GT 面板单边的边距。 */
    public static final int PANEL_BORDER = 5;

    private static final int MAX_PANEL_W = 480;
    private static final int MAX_PANEL_H = 320;
    /** 平时不小于这个尺寸；屏幕更窄时才继续收缩。 */
    private static final int COMFORT_PANEL_W = 340;
    /** 再小就放不下背包、右栏标签或连接分页。 */
    private static final int MIN_PANEL_W = 316;
    private static final int MIN_PANEL_H = 236;
    /** 面板到屏幕边缘的距离：平时和放不下时。 */
    private static final int SCREEN_MARGIN = 6;
    private static final int TIGHT_MARGIN = 2;
    private static final int MIN_RIGHT_W = 130;
    private static final int COLUMN_GAP = 4;

    public final int width;
    public final int height;
    public final int leftW;
    public final int rightX;
    public final int rightW;

    private DualPaneLayout(int panelW, int panelH) {
        width = panelW - 2 * PANEL_BORDER;
        height = panelH - 2 * PANEL_BORDER;
        rightW = Math.max(MIN_RIGHT_W, width * 2 / 5);
        leftW = width - rightW - COLUMN_GAP;
        rightX = leftW + COLUMN_GAP;
    }

    /** 服务端不知道屏幕大小，也用不到尺寸，取最小布局。 */
    public static DualPaneLayout minimum() {
        return new DualPaneLayout(COMFORT_PANEL_W, MIN_PANEL_H);
    }

    public static DualPaneLayout forScreen(int screenW, int screenH) {
        return new DualPaneLayout(
            fit(screenW, MAX_PANEL_W, COMFORT_PANEL_W, MIN_PANEL_W),
            fit(screenH, MAX_PANEL_H, MIN_PANEL_H, MIN_PANEL_H));
    }

    private static int fit(int screen, int max, int comfort, int min) {
        int size = Math.max(Math.min(max, screen - 2 * SCREEN_MARGIN), comfort);
        return Math.max(Math.min(size, screen - 2 * TIGHT_MARGIN), min);
    }
}
