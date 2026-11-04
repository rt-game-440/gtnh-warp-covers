package com.rtgame.warpcovers.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DualPaneLayoutTest {

    private static int panelW(DualPaneLayout layout) {
        return layout.width + 2 * DualPaneLayout.PANEL_BORDER;
    }

    private static int panelH(DualPaneLayout layout) {
        return layout.height + 2 * DualPaneLayout.PANEL_BORDER;
    }

    private static void assertPanel(int screenW, int screenH, int expectedW, int expectedH) {
        DualPaneLayout layout = DualPaneLayout.forScreen(screenW, screenH);
        assertEquals(expectedW, panelW(layout), "宽 @" + screenW + "x" + screenH);
        assertEquals(expectedH, panelH(layout), "高 @" + screenW + "x" + screenH);
    }

    @Test
    void 大屏幕用上限() {
        assertPanel(960, 540, 480, 320);
        assertPanel(640, 360, 480, 320);
    }

    @Test
    void 缩放四时贴着屏幕留边() {
        assertPanel(480, 270, 468, 258);
    }

    @Test
    void 最紧的十六比九窗口仍放得下() {
        assertPanel(426, 240, 414, 236);
    }

    @Test
    void 窄屏继续收缩到屏幕内() {
        assertPanel(320, 256, 316, 244);
        assertPanel(320, 240, 316, 236);
    }

    @Test
    void 合法的屏幕尺寸都不超屏且两栏不重叠() {
        // 原版保证缩放大于 1 时 GUI 空间至少 320×240
        for (int w = 320; w <= 1200; w++) {
            for (int h = 240; h <= 700; h += 7) {
                DualPaneLayout layout = DualPaneLayout.forScreen(w, h);
                assertTrue(panelW(layout) <= w, "宽超屏 @" + w + "x" + h);
                assertTrue(panelH(layout) <= h, "高超屏 @" + w + "x" + h);
                assertEquals(layout.width, layout.rightX + layout.rightW);
                assertTrue(layout.leftW >= 162, "左栏放不下背包 @" + w);
                assertTrue(layout.rightW >= 130, "右栏放不下标签 @" + w);
            }
        }
    }
}
