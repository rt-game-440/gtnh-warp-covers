package com.rtgame.warpcovers.gt;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import com.cleanroommc.modularui.api.IGuiHolder;
import com.cleanroommc.modularui.factory.GuiData;
import com.cleanroommc.modularui.factory.GuiFactories;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;

/** 折跃频道管理器：手持右键打开，不用走到机器跟前就能管理频道、远程修改已加载的连接端。 */
public final class WarpManagerItem extends Item implements IGuiHolder<GuiData> {

    public WarpManagerItem() {
        setMaxStackSize(1);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (!world.isRemote) {
            GuiFactories.item()
                .open(player);
        }
        return stack;
    }

    @Override
    public ModularPanel buildUI(GuiData data, PanelSyncManager syncManager, UISettings settings) {
        return WarpCoverGui.managerPanel(data.getPlayer(), syncManager);
    }
}
