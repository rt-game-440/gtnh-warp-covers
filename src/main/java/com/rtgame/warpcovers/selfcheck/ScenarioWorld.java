package com.rtgame.warpcovers.selfcheck;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.WorldInfo;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.gt.AggregateWarpCover;
import com.rtgame.warpcovers.gt.BaseWarpCover;
import com.rtgame.warpcovers.gt.EnergyWarpCover;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.ItemWarpCover;
import com.rtgame.warpcovers.gt.ModItems;
import com.rtgame.warpcovers.gt.WarpCover;

import gregtech.api.covers.CoverRegistry;
import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.common.covers.Cover;

/** 自检场景共用的世界操作：放置储罐、贴覆盖板、注水、手动驱动覆盖板等。 */
final class ScenarioWorld {

    private ScenarioWorld() {}

    static WorldServer world(int dimension) {
        return MinecraftServer.getServer()
            .worldServerForDimension(dimension);
    }

    static FakePlayer fakePlayer(WorldServer world) {
        return FakePlayerFactory.getMinecraft(world);
    }

    static IGregTechTileEntity placeTank(World world, int x, int y, int z) {
        return placeMachine(world, x, y, z, ItemList.Super_Tank_LV.get(1));
    }

    /** 把一个 GT 机器物品（可带 NBT，例如挖下来的机器）放到指定位置。 */
    static IGregTechTileEntity placeMachine(World world, int x, int y, int z, ItemStack stack) {
        loadChunk(world, x, z);
        world.setBlockToAir(x, y, z);
        boolean placed;
        try {
            placed = ((ItemBlock) stack.getItem()).placeBlockAt(
                stack,
                fakePlayer((WorldServer) world),
                world,
                x,
                y,
                z,
                1,
                0.5F,
                0.5F,
                0.5F,
                stack.getItemDamage());
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                "机器放置异常: dim=" + world.provider.dimensionId
                    + " "
                    + x
                    + ","
                    + y
                    + ","
                    + z
                    + " 现有方块="
                    + world.getBlock(x, y, z)
                    + " 物品="
                    + stack.getItem(),
                e);
        }
        IGregTechTileEntity tile = placed ? tileAt(world, x, y, z) : null;
        if (tile == null) {
            throw new IllegalStateException("机器放置失败: dim=" + world.provider.dimensionId + " " + x + "," + y + "," + z);
        }
        warmUp(world, x, y, z);
        return tile;
    }

    /** 像玩家挖掘一样取得掉落物（GT 会把覆盖板写进机器物品），然后移除方块。 */
    static ItemStack digMachine(World world, int x, int y, int z) {
        int meta = world.getBlockMetadata(x, y, z);
        ArrayList<ItemStack> drops = world.getBlock(x, y, z)
            .getDrops(world, x, y, z, meta, 0);
        world.setBlockToAir(x, y, z);
        if (drops.size() != 1) {
            throw new IllegalStateException("挖掘机器的掉落物数量不对: " + drops);
        }
        return drops.get(0);
    }

    /** GT 机器刚放下或刚加载时 mTickTimer 过小会拒绝流体读写（要求大于 5），手动推进几次更新。 */
    static void warmUp(World world, int x, int y, int z) {
        TileEntity tile = world.getTileEntity(x, y, z);
        for (int i = 0; i < 8; i++) {
            tile.updateEntity();
        }
    }

    /** 显式加载区块：非主世界的 provideChunk 可能返回空区块，loadChunk 总是真正加载。 */
    static void loadChunk(World world, int x, int z) {
        ((WorldServer) world).theChunkProviderServer.loadChunk(x >> 4, z >> 4);
    }

    static boolean chunkLoaded(World world, int x, int z) {
        return world.getChunkProvider()
            .chunkExists(x >> 4, z >> 4);
    }

    /**
     * 把区块存盘并真正卸载（与没有玩家时服务器卸载区块走同一条路）。
     *
     * <p>
     * GTNH 的 Hodgepodge 会把加载时尚未填充的新区块及其周围 8 个区块保护 600 tick 不被卸载，
     * 自检命令在同一 tick 内执行，等不到保护过期，这里先解除这块区块周围的保护。
     */
    static void unloadChunk(WorldServer world, int x, int z) {
        int cx = x >> 4;
        int cz = z >> 4;
        releaseUnloadProtection(world.provider.dimensionId, cx, cz);
        world.theChunkProviderServer.unloadChunksIfNotNearSpawn(cx, cz);
        world.theChunkProviderServer.unloadQueuedChunks();
    }

    private static final String HODGEPODGE_SCHEDULER = "com.mitchej123.hodgepodge.mixins.hooks.ChunkGenScheduler";

    /** 调 Hodgepodge 公开的 ChunkGenScheduler.onChunkUnload 清掉保护状态；没有 Hodgepodge 时什么都不做。 */
    private static void releaseUnloadProtection(int dimension, int cx, int cz) {
        Class<?> type;
        try {
            type = Class.forName(HODGEPODGE_SCHEDULER);
        } catch (ClassNotFoundException e) {
            return;
        }
        try {
            Object scheduler = type.getMethod("forDimension", int.class)
                .invoke(null, dimension);
            java.lang.reflect.Method release = type.getMethod("onChunkUnload", long.class);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    release.invoke(scheduler, ChunkCoordIntPair.chunkXZ2Int(cx + dx, cz + dz));
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("解除 Hodgepodge 区块卸载保护失败", e);
        }
    }

    /** 区块卸载失败时的诊断信息。 */
    static String chunkUnloadDiagnostics(WorldServer world, int x, int z) {
        ChunkCoordIntPair pos = new ChunkCoordIntPair(x >> 4, z >> 4);
        return "levelSaving=" + world.levelSaving
            + " persistent="
            + world.getPersistentChunks()
                .containsKey(pos)
            + " spawn="
            + world.getSpawnPoint().posX
            + ","
            + world.getSpawnPoint().posZ;
    }

    static IGregTechTileEntity tileAt(World world, int x, int y, int z) {
        loadChunk(world, x, z);
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof IGregTechTileEntity ? (IGregTechTileEntity) tile : null;
    }

    static void setEU(IGregTechTileEntity tile, long eu) {
        ((MetaTileEntity) tile.getMetaTileEntity()).setEUVar(eu);
        check("储能没设上: " + tile.getInventoryName(), tile.getStoredEU() == eu);
    }

    static void clearWeather(WorldServer world) {
        WorldInfo info = world.getWorldInfo();
        info.setRaining(false);
        info.setRainTime(1000000);
        info.setThundering(false);
        info.setThunderTime(1000000);
    }

    static IGregTechTileEntity requireTile(World world, int x, int y, int z) {
        IGregTechTileEntity tile = tileAt(world, x, y, z);
        if (tile == null) {
            throw new IllegalStateException(
                "找不到 GT 机器: dim=" + world.provider.dimensionId + " " + x + "," + y + "," + z);
        }
        return tile;
    }

    static FluidWarpCover attachCover(IGregTechTileEntity tile, ForgeDirection side) {
        return attach(tile, side, ModItems.fluidCover, FluidWarpCover.class);
    }

    static FluidWarpCover coverAt(IGregTechTileEntity tile, ForgeDirection side) {
        return coverAt(tile, side, FluidWarpCover.class);
    }

    static ItemWarpCover attachItemCover(IGregTechTileEntity tile, ForgeDirection side) {
        return attach(tile, side, ModItems.itemCover, ItemWarpCover.class);
    }

    static ItemWarpCover itemCoverAt(IGregTechTileEntity tile, ForgeDirection side) {
        return coverAt(tile, side, ItemWarpCover.class);
    }

    /** 按玩家右键贴覆盖板时 GT 的放置判断：能量覆盖板只能贴在有内部储能的方块上。 */
    static boolean canPlaceEnergyCover(IGregTechTileEntity tile, ForgeDirection side) {
        ItemStack stack = new ItemStack(ModItems.energyCover);
        return CoverRegistry.getCoverPlacer(stack)
            .isCoverPlaceable(side, stack, tile);
    }

    static EnergyWarpCover attachEnergyCover(IGregTechTileEntity tile, ForgeDirection side) {
        if (!canPlaceEnergyCover(tile, side)) {
            throw new IllegalStateException("GT 不允许在这里贴能量覆盖板: " + tile.getInventoryName() + " " + side);
        }
        return attach(tile, side, ModItems.energyCover, EnergyWarpCover.class);
    }

    static EnergyWarpCover energyCoverAt(IGregTechTileEntity tile, ForgeDirection side) {
        return coverAt(tile, side, EnergyWarpCover.class);
    }

    static AggregateWarpCover attachAggregateCover(IGregTechTileEntity tile, ForgeDirection side) {
        return attach(tile, side, ModItems.aggregateCover, AggregateWarpCover.class);
    }

    static AggregateWarpCover aggregateCoverAt(IGregTechTileEntity tile, ForgeDirection side) {
        return coverAt(tile, side, AggregateWarpCover.class);
    }

    private static <T extends BaseWarpCover> T attach(IGregTechTileEntity tile, ForgeDirection side, Item item,
        Class<T> type) {
        Cover cover = CoverRegistry.buildCover(new ItemStack(item), side, tile);
        tile.attachCover(cover);
        return coverAt(tile, side, type);
    }

    private static <T extends BaseWarpCover> T coverAt(IGregTechTileEntity tile, ForgeDirection side, Class<T> type) {
        Cover attached = tile.getCoverAtSide(side);
        if (!type.isInstance(attached)) {
            throw new IllegalStateException("该面没有" + type.getSimpleName() + ": " + side + " 实际 " + attached);
        }
        return type.cast(attached);
    }

    /** 从顶面注入水，返回实际注入量。 */
    static int pour(IGregTechTileEntity tank, int amount) {
        int filled = tank.fill(ForgeDirection.UP, new FluidStack(FluidRegistry.WATER, amount), true);
        if (filled != amount) {
            throw new IllegalStateException("注入水失败: 期望 " + amount + " 实际 " + filled);
        }
        return filled;
    }

    static void runCovers(long now, WarpCover... covers) {
        for (WarpCover cover : covers) {
            cover.tickAt(now);
        }
    }

    static int fluidIn(IGregTechTileEntity tank, String name) {
        FluidStack stack = tank.getTankInfo(ForgeDirection.UP)[0].fluid;
        return stack != null && name.equals(FluidRegistry.getFluidName(stack)) ? stack.amount : 0;
    }

    static int totalFluid(IGregTechTileEntity tank) {
        FluidStack stack = tank.getTankInfo(ForgeDirection.UP)[0].fluid;
        return stack == null ? 0 : stack.amount;
    }

    /** 删除指定名字的频道（世界跨次自检保留，避免旧频道越积越多）。 */
    static int deleteChannelsNamed(ChannelHub hub, String name) {
        List<String> ids = new ArrayList<String>();
        for (Channel channel : hub.channels()) {
            if (channel.name()
                .equals(name)) {
                ids.add(channel.id());
            }
        }
        for (String id : ids) {
            hub.deleteChannel(id);
        }
        return ids.size();
    }

    /** 设置频道的过滤名单（名单属于频道，两端共用）：先清空，再按给定内容重设。 */
    static void setFilter(ChannelHub hub, Channel channel, boolean whitelist, boolean matchNbt, String... keys) {
        hub.setFilterWhitelist(channel.id(), whitelist);
        hub.setFilterMatchNbt(channel.id(), matchNbt);
        hub.clearFilter(channel.id());
        for (String key : keys) {
            hub.addFilterKey(channel.id(), key);
        }
    }

    static Channel channelNamed(ChannelHub hub, String name) {
        Channel found = null;
        for (Channel channel : hub.channels()) {
            if (channel.name()
                .equals(name)) {
                if (found != null) {
                    throw new IllegalStateException("存在多个名为 " + name + " 的频道");
                }
                found = channel;
            }
        }
        if (found == null) {
            throw new IllegalStateException("找不到频道 " + name);
        }
        return found;
    }

    /** 读写 GT 方块实体上的私有/受保护布尔字段（沿继承链查找）。 */
    static boolean booleanField(Object target, String name) {
        try {
            return field(target, name).getBoolean(target);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    static void setBooleanField(Object target, String name, boolean value) {
        try {
            field(target, name).setBoolean(target, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // 继续向父类找
            }
        }
        throw new IllegalStateException("找不到字段 " + name + " 于 " + target.getClass());
    }

    static void expect(String what, long expected, long actual) {
        if (expected != actual) {
            throw new IllegalStateException(what + " 不对: 期望 " + expected + " 实际 " + actual);
        }
    }

    static void check(String what, boolean condition) {
        if (!condition) {
            throw new IllegalStateException(what);
        }
    }
}
