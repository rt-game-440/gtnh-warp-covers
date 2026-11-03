package com.rtgame.warpcovers.gt;

import java.util.ArrayDeque;
import java.util.Deque;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.WorldEvent;

import com.rtgame.warpcovers.WarpCovers;
import com.rtgame.warpcovers.core.EndpointPresence;
import com.rtgame.warpcovers.core.Location;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import gregtech.api.interfaces.tileentity.ICoverable;

/** 存档加载、区块加载清理等世界事件。 */
public final class WarpEvents {

    /** 每 tick 最多处理的区块数：大量区块同时加载时把清理摊到后面几个 tick。 */
    private static final int CHUNKS_PER_TICK = 64;

    private final Deque<int[]> pendingChunks = new ArrayDeque<int[]>();

    private static WarpEvents instance;

    public static void register() {
        instance = new WarpEvents();
        MinecraftForge.EVENT_BUS.register(instance);
        FMLCommonHandler.instance()
            .bus()
            .register(instance);
    }

    /** 服务器停止后调用：卸下核心，清掉待处理的区块。 */
    public static void onServerStopped() {
        if (instance != null) {
            instance.pendingChunks.clear();
        }
        WarpServices.clear();
    }

    @SubscribeEvent
    public void onWorldLoad(WorldEvent.Load event) {
        World world = event.world;
        if (world.isRemote || world.provider.dimensionId != 0 || !(world instanceof WorldServer)) {
            return;
        }
        WarpWorldData data = WarpWorldData.loadOrCreate(world);
        WarpServices.install(data.hub());
        pendingChunks.clear();
        WarpCovers.LOG.info(
            "折跃频道数据已加载：{} 个频道，{} 个连接端",
            data.hub()
                .channels()
                .size(),
            data.hub()
                .endpoints()
                .size());
    }

    /** 区块加载时先记下，等本 tick 结束再检查：此时区块里的方块实体都已就位。 */
    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.world.isRemote || !WarpServices.isInstalled()) {
            return;
        }
        pendingChunks.add(
            new int[] { event.world.provider.dimensionId, event.getChunk().xPosition, event.getChunk().zPosition });
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !pendingChunks.isEmpty()) {
            processPending(CHUNKS_PER_TICK);
        }
    }

    /** 立刻处理所有待检查的区块，返回清理掉的记录数。供自检在同一条命令内验证清理结果。 */
    public static int drainPendingChunks() {
        return instance == null ? 0 : instance.processPending(Integer.MAX_VALUE);
    }

    private int processPending(int limit) {
        if (!WarpServices.isInstalled()) {
            pendingChunks.clear();
            return 0;
        }
        int total = 0;
        for (int i = 0; i < limit && !pendingChunks.isEmpty(); i++) {
            int[] chunk = pendingChunks.poll();
            int removed = WarpServices.hub()
                .cleanChunk(chunk[0], chunk[1], chunk[2], PRESENCE);
            if (removed > 0) {
                WarpCovers.LOG.info("清理了区块 {}:{},{} 中 {} 条找不到覆盖板的连接端记录", chunk[0], chunk[1], chunk[2], removed);
            }
            total += removed;
        }
        return total;
    }

    /** 到世界里查看某个位置是否还贴着携带该连接端的覆盖板。区块没加载时不下结论。 */
    static final EndpointPresence PRESENCE = new EndpointPresence() {

        @Override
        public Presence check(String endpointId, Location location) {
            World world = DimensionManager.getWorld(location.dimension());
            if (world == null || !world.getChunkProvider()
                .chunkExists(location.chunkX(), location.chunkZ())) {
                return Presence.UNKNOWN;
            }
            TileEntity tile = world.getTileEntity(location.x(), location.y(), location.z());
            if (!(tile instanceof ICoverable)) {
                return Presence.ABSENT;
            }
            Object cover = ((ICoverable) tile).getCoverAtSide(ForgeDirection.getOrientation(location.side()));
            return cover instanceof EndpointCarrier && ((EndpointCarrier) cover).carriesEndpoint(endpointId)
                ? Presence.PRESENT
                : Presence.ABSENT;
        }
    };
}
