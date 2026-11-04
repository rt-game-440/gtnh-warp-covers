package com.rtgame.warpcovers.gt;

import com.cleanroommc.modularui.api.widget.Interactable;
import com.cleanroommc.modularui.widget.ParentWidget;

/** 盖在控件上吃掉点击的容器：下面的按钮和输入框都点不到。 */
final class InputBlocker extends ParentWidget<InputBlocker> implements Interactable {

    @Override
    public Result onMousePressed(int mouseButton) {
        return Result.SUCCESS;
    }
}
