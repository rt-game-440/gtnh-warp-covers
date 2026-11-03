package com.rtgame.warpcovers.gt;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.common.covers.Cover;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;
import mcp.mobius.waila.api.IWailaRegistrar;

/**
 * 聚合覆盖板的 WAILA 信息：三个连接端各一行。单类型覆盖板用 GT 自带的覆盖板行就够了。
 * 数据来自 GT 在玩家看着方块时推送的覆盖板数据包。经 IMC 注册，只有装了 WAILA 才会加载这个类。
 */
public final class WarpWailaProvider implements IWailaDataProvider {

    /** WAILA 按 IMC 消息反射调用。 */
    public static void register(IWailaRegistrar registrar) {
        registrar.registerBodyProvider(new WarpWailaProvider(), ICoverable.class);
    }

    @Override
    public List<String> getWailaBody(ItemStack stack, List<String> tips, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        TileEntity tile = accessor.getTileEntity();
        if (tile instanceof ICoverable) {
            Cover cover = ((ICoverable) tile).getCoverAtSide(accessor.getSide());
            if (cover instanceof AggregateWarpCover) {
                for (WarpPart part : ((AggregateWarpCover) cover).parts()) {
                    tips.add(
                        StatCollector.translateToLocal(
                            "warpcovers.gui.section." + part.channelType()
                                .key())
                            + ": "
                            + part.describe());
                }
            }
        }
        return tips;
    }

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack stack, List<String> tips, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tips;
    }

    @Override
    public List<String> getWailaTail(ItemStack stack, List<String> tips, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        return tips;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x,
        int y, int z) {
        return tag;
    }
}
