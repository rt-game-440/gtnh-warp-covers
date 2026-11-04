package com.rtgame.warpcovers.gt;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;

import com.rtgame.warpcovers.core.DualPaneLayout;

/** 只能在客户端调用：这个类引用了仅客户端的类。 */
final class ClientScreen {

    private ClientScreen() {}

    static DualPaneLayout dualPaneLayout() {
        Minecraft mc = Minecraft.getMinecraft();
        ScaledResolution resolution = new ScaledResolution(mc, mc.displayWidth, mc.displayHeight);
        return DualPaneLayout.forScreen(resolution.getScaledWidth(), resolution.getScaledHeight());
    }
}
