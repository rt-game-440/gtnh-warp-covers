package com.rtgame.warpcovers.gt;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

import com.rtgame.warpcovers.core.ChannelHub;

/**
 * 折跃频道的世界存档数据：频道表、连接端索引（以后还有玩家标签、归属者级设置）合在一份里，
 * 挂在主世界的共享存储上（存为 data/warpcovers.dat）。核心有变更时标脏，随世界保存写盘。
 */
public final class WarpWorldData extends WorldSavedData {

    public static final String NAME = "warpcovers";

    private final ChannelHub hub = new ChannelHub(WarpServices.CONFIG);

    /** {@link net.minecraft.world.storage.MapStorage} 通过反射调用这个构造函数。 */
    public WarpWorldData(String name) {
        super(name);
        hub.setDirtyListener(new Runnable() {

            @Override
            public void run() {
                markDirty();
            }
        });
    }

    public ChannelHub hub() {
        return hub;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        HubNbt.read(tag, hub);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        HubNbt.write(hub, tag);
    }

    /** 从主世界的共享存储读取，没有就新建一份。 */
    public static WarpWorldData loadOrCreate(World overworld) {
        WarpWorldData data = (WarpWorldData) overworld.mapStorage.loadData(WarpWorldData.class, NAME);
        if (data == null) {
            data = new WarpWorldData(NAME);
            overworld.mapStorage.setData(NAME, data);
        }
        return data;
    }
}
