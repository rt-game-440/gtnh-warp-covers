package com.rtgame.warpcovers.gt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nonnull;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import com.google.common.io.ByteArrayDataInput;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.Periods;

import gregtech.api.covers.CoverContext;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.tileentity.ICoverable;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.covers.Cover;
import gregtech.common.gui.modularui.cover.base.CoverBaseGui;
import io.netty.buffer.ByteBuf;

/**
 * 折跃覆盖板的公共部分：GT 覆盖板与频道核心之间的适配层。
 * 覆盖板持有一个或多个连接端（{@link WarpPart}，频道、交互状态、名单等随覆盖板数据存盘）；传输规则全部在核心里。
 * 撬下或挖掉机器时配置随物品保留。
 */
public abstract class BaseWarpCover extends Cover implements EndpointCarrier {

    private final List<WarpPart> parts;
    /** 按各连接端外观合成的贴图缓存；外观变化时清空。 */
    private ITexture composedTexture;

    protected BaseWarpCover(@Nonnull CoverContext context, ITexture coverTexture, ChannelType... types) {
        super(context, coverTexture);
        List<WarpPart> created = new ArrayList<WarpPart>();
        for (ChannelType type : types) {
            created.add(new WarpPart(type, this));
        }
        parts = Collections.unmodifiableList(created);
        // 从带配置的物品贴上：物品里的配置就是覆盖板的配置（连接端 ID 总是新的）
        NBTTagCompound itemConfig = WarpCoverItem.configOf(context.getCoverItem());
        if (itemConfig != null) {
            readParts(itemConfig);
        }
    }

    public final List<WarpPart> parts() {
        return parts;
    }

    /** 某类型的连接端；没有时为 null。 */
    public final WarpPart part(ChannelType type) {
        for (WarpPart part : parts) {
            if (part.channelType() == type) {
                return part;
            }
        }
        return null;
    }

    /** 界面 ID 的后缀。 */
    protected abstract String guiKey();

    final String guiId() {
        return "warpcovers." + guiKey();
    }

    /** 底图加上表示频道和交互状态的印记。 */
    protected abstract ITexture composeTexture(ITexture base);

    @Override
    public boolean carriesEndpoint(String endpointId) {
        for (WarpPart part : parts) {
            if (part.endpointId()
                .equals(endpointId)) {
                return true;
            }
        }
        return false;
    }

    // ---- GT 覆盖板接口 ----

    @Override
    public int getMinimumTickRate() {
        // GT 每 tick 都读这个值，所以改周期立即生效；是否到期由核心判断。parts 在父类构造期间还没赋值
        if (parts == null) {
            return Periods.MAX_TICK_INTERVAL;
        }
        int interval = Periods.MAX_TICK_INTERVAL;
        for (WarpPart part : parts) {
            interval = Math.min(
                interval,
                Periods.tickInterval(
                    Periods.effectivePeriod(
                        part.config()
                            .period(),
                        WarpServices.CONFIG)));
        }
        return interval;
    }

    @Override
    public boolean allowsTickRateAddition() {
        return false;
    }

    @Override
    public boolean allowsCopyPasteTool() {
        // 复制粘贴会把连接端 ID 一起复制，造成两个覆盖板共用一个连接端
        return false;
    }

    @Override
    public boolean isRedstoneSensitive(long aTimer) {
        return false;
    }

    @Override
    public void onPlayerAttach(EntityPlayer player, ItemStack coverItem) {
        ICoverable tile = getTile();
        if (tile == null) {
            return;
        }
        String uuid = player == null ? null
            : player.getUniqueID()
                .toString();
        for (WarpPart part : parts) {
            part.endpoint()
                .onPlayerAttach(tile, coverSide, uuid);
        }
    }

    @Override
    public void doCoverThings(byte aRedstone, long aTickTimer) {
        // 不用 aTickTimer：那是每台机器各自的计时器。同一频道的连接端必须共用一个时钟，输出端的接收窗口才成立
        ICoverable tile = getTile();
        if (tile != null && tile.isServerSide()) {
            tickAt(
                MinecraftServer.getServer()
                    .getTickCounter());
        }
    }

    /** 以指定时间运行一次（自检场景用它控制时间）。 */
    public void tickAt(long now) {
        ICoverable tile = getTile();
        if (tile != null) {
            for (WarpPart part : parts) {
                part.endpoint()
                    .tick(tile, coverSide, now);
            }
        }
    }

    @Override
    public void onCoverUnload() {
        for (WarpPart part : parts) {
            part.endpoint()
                .onUnload();
        }
    }

    @Override
    public void onCoverRemoval() {
        itemize();
    }

    @Override
    public void onBaseTEDestroyed() {
        itemize();
    }

    private void itemize() {
        for (WarpPart part : parts) {
            part.endpoint()
                .onItemized(getTile());
        }
    }

    /** 撬下时掉落的物品：带全部配置（没有配置时就是空白覆盖板，能和其他空白的堆叠）。 */
    @Override
    public ItemStack asItemStack() {
        ItemStack stack = super.asItemStack();
        if (stack == null) {
            return null;
        }
        boolean blank = true;
        for (WarpPart part : parts) {
            blank &= part.endpoint()
                .isBlank();
        }
        if (!blank) {
            ICoverable tile = getTile();
            boolean serverSide = tile != null && tile.isServerSide();
            NBTTagCompound config = new NBTTagCompound();
            for (WarpPart part : parts) {
                part.endpoint()
                    .writeItemNbt(childTag(config, part), serverSide);
            }
            WarpCoverItem.setConfig(stack, config);
        }
        return stack;
    }

    @Override
    public boolean hasCoverGUI() {
        return true;
    }

    @Override
    protected @Nonnull CoverBaseGui<?> getCoverGui() {
        return new WarpCoverGui(this);
    }

    @Override
    public boolean alwaysLookConnected() {
        return true;
    }

    // ---- 外观与客户端同步 ----

    @Override
    public boolean isDataNeededOnClient() {
        return true;
    }

    @Override
    protected void writeDataToByteBuf(ByteBuf buf) {
        for (WarpPart part : parts) {
            part.currentVisual()
                .write(buf);
        }
    }

    @Override
    protected void readDataFromPacket(ByteArrayDataInput in) {
        boolean lookChanged = false;
        for (WarpPart part : parts) {
            lookChanged |= part.applyVisual(CoverVisual.read(in));
        }
        if (lookChanged) {
            composedTexture = null;
            ICoverable tile = getTile();
            if (tile instanceof IGregTechTileEntity) {
                ((IGregTechTileEntity) tile).issueTextureUpdate();
            }
        }
    }

    @Override
    public ITexture getOverlayTexture() {
        if (composedTexture == null) {
            composedTexture = composeTexture(super.getOverlayTexture());
        }
        return composedTexture;
    }

    @Override
    public ITexture getSpecialFaceTexture() {
        return getOverlayTexture();
    }

    // ---- 存盘 ----

    /** 单连接端的数据直接放在根上（与之前的存档兼容）；多连接端按类型各占一个子标签。 */
    private NBTTagCompound childTag(NBTTagCompound root, WarpPart part) {
        if (parts.size() == 1) {
            return root;
        }
        String key = part.channelType()
            .key();
        if (!root.hasKey(key, 10)) {
            root.setTag(key, new NBTTagCompound());
        }
        return root.getCompoundTag(key);
    }

    private void readParts(NBTTagCompound root) {
        for (WarpPart part : parts) {
            part.endpoint()
                .readNbt(childTag(root, part));
        }
    }

    @Override
    protected @Nonnull NBTBase saveDataToNbt() {
        NBTTagCompound tag = new NBTTagCompound();
        for (WarpPart part : parts) {
            part.endpoint()
                .writeNbt(childTag(tag, part));
        }
        return tag;
    }

    @Override
    protected void readDataFromNbt(NBTBase nbt) {
        if (nbt instanceof NBTTagCompound) {
            readParts((NBTTagCompound) nbt);
        }
    }
}
