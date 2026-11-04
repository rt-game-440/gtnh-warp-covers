package com.rtgame.warpcovers.gt;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;

import com.rtgame.warpcovers.WarpCovers;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.EndpointConfig;
import com.rtgame.warpcovers.core.InteractionState;

/**
 * 折跃覆盖板物品。撬下或挖掉机器后，覆盖板的全部配置存在物品 NBT 的 {@value #TAG} 里，
 * 提示里显示摘要；单独放进合成台可以清空成空白覆盖板。
 */
public class WarpCoverItem extends Item {

    public static final String TAG = "warpcovers";

    /** 物品上的覆盖板配置；空白覆盖板返回 null。 */
    public static NBTTagCompound configOf(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof WarpCoverItem)
            || !stack.hasTagCompound()
            || !stack.getTagCompound()
                .hasKey(TAG, 10)) {
            return null;
        }
        return stack.getTagCompound()
            .getCompoundTag(TAG);
    }

    public static void setConfig(ItemStack stack, NBTTagCompound config) {
        NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
        root.setTag(TAG, config);
        stack.setTagCompound(root);
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        NBTTagCompound tag = configOf(stack);
        if (tag == null) {
            return;
        }
        if (stack.getItem() == ModItems.aggregateCover) {
            // 聚合覆盖板：三个连接端各一段
            for (ChannelType type : ChannelType.values()) {
                lines.add(EnumChatFormatting.AQUA + tr("warpcovers.gui.section." + type.key()));
                addEndpointLines(tag.getCompoundTag(type.key()), lines);
            }
        } else {
            addEndpointLines(tag, lines);
        }
        lines.add(EnumChatFormatting.DARK_GRAY + tr("warpcovers.tooltip.clear_hint"));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private static void addEndpointLines(NBTTagCompound tag, List lines) {
        String channelId = tag.getString(CoverEndpoint.NBT_CHANNEL);
        if (channelId.isEmpty()) {
            lines.add(EnumChatFormatting.GRAY + tr("warpcovers.tooltip.unconnected"));
        } else {
            lines.add(tr("warpcovers.tooltip.channel", tag.getString(CoverEndpoint.NBT_CHANNEL_NAME)));
        }
        EndpointConfig config = new EndpointConfig();
        ConfigNbt.read(tag, config);
        lines.add(tr("warpcovers.tooltip.state", tr(stateKey(config.state()))));
        if (config.priority() != 0) {
            lines.add(tr("warpcovers.tooltip.priority", config.priority()));
        }
        if (config.rate() > 0) {
            lines.add(tr("warpcovers.tooltip.rate", config.rate()));
        }
        if (config.period() > 0) {
            lines.add(tr("warpcovers.tooltip.period", config.period()));
        }
    }

    public static String stateKey(InteractionState state) {
        return WarpCovers.MODID + ".state."
            + state.name()
                .toLowerCase();
    }

    private static String tr(String key, Object... args) {
        return StatCollector.translateToLocalFormatted(key, args);
    }
}
