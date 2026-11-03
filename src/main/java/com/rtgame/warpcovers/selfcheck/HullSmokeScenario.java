package com.rtgame.warpcovers.selfcheck;

import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayerFactory;

import gregtech.api.enums.ItemList;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 冒烟场景：在主世界高空（相当于虚空）放置一台 LV 机器外壳，服务器重启后确认它还在。
 * 用来证明「自检命令 + 跨重启两阶段 + 日志」整条链路可用。
 */
public final class HullSmokeScenario implements SelfCheckScenario {

    private static final int X = 0;
    private static final int Y = 200;
    private static final int Z = 0;

    @Override
    public String name() {
        return "gt_hull_smoke";
    }

    @Override
    public void setup() {
        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        // 先加载区块，否则在未加载区块里放置方块会被丢弃
        world.getChunkFromBlockCoords(X, Z);
        world.setBlockToAir(X, Y, Z);
        ItemStack stack = ItemList.Hull_LV.get(1);
        boolean placed = ((ItemBlock) stack.getItem()).placeBlockAt(
            stack,
            FakePlayerFactory.getMinecraft(world),
            world,
            X,
            Y,
            Z,
            1,
            0.5F,
            0.5F,
            0.5F,
            stack.getItemDamage());
        if (!placed || !(world.getTileEntity(X, Y, Z) instanceof IGregTechTileEntity)) {
            throw new IllegalStateException("机器外壳放置失败");
        }
    }

    @Override
    public SelfCheckResult verify() {
        WorldServer world = MinecraftServer.getServer()
            .worldServerForDimension(0);
        world.getChunkFromBlockCoords(X, Z);
        TileEntity tile = world.getTileEntity(X, Y, Z);
        if (!(tile instanceof IGregTechTileEntity)) {
            return SelfCheckResult.fail("重启后 (" + X + "," + Y + "," + Z + ") 处没有 GT 机器");
        }
        int expected = ItemList.Hull_LV.get(1)
            .getItemDamage();
        int actual = ((IGregTechTileEntity) tile).getMetaTileID();
        if (actual != expected) {
            return SelfCheckResult.fail("机器种类不对: 期望 " + expected + " 实际 " + actual);
        }
        return SelfCheckResult.pass("LV 机器外壳在重启后依然存在");
    }
}
