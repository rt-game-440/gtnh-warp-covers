package com.rtgame.warpcovers.gt;

import java.util.function.IntConsumer;

import com.cleanroommc.modularui.api.UpOrDown;
import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.widget.ParentWidget;

/** 能响应鼠标滚轮的容器：滚轮向上回调 -1，向下回调 +1，由使用方决定怎么翻。 */
final class ScrollArea extends ParentWidget<ScrollArea> implements Interactable {

    private final IntConsumer onScroll;

    ScrollArea(IntConsumer onScroll) {
        this.onScroll = onScroll;
    }

    @Override
    public boolean onMouseScroll(UpOrDown scrollDirection, int amount) {
        onScroll.accept(scrollDirection.isUp() ? -1 : 1);
        return true;
    }
}
