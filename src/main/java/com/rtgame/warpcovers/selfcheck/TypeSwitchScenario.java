package com.rtgame.warpcovers.selfcheck;

import static com.rtgame.warpcovers.selfcheck.ScenarioWorld.*;

import java.io.File;
import java.io.IOException;
import java.util.Random;

import net.minecraft.world.WorldServer;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.util.ForgeDirection;

import com.rtgame.warpcovers.ServerConfig;
import com.rtgame.warpcovers.core.Channel;
import com.rtgame.warpcovers.core.ChannelHub;
import com.rtgame.warpcovers.core.ChannelType;
import com.rtgame.warpcovers.core.CoverPanel;
import com.rtgame.warpcovers.core.EndpointStatus;
import com.rtgame.warpcovers.core.InteractionState;
import com.rtgame.warpcovers.gt.FluidWarpCover;
import com.rtgame.warpcovers.gt.WarpServices;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.common.covers.Cover;

/**
 * 服务器类型开关场景（#16）：流体功能被配置文件关闭后，已贴的覆盖板与频道数据保留但停止传输、
 * 状态为「服务器已禁用」；重新开启后恢复传输。配置经临时配置文件走真实的 {@link ServerConfig#load}。
 */
public final class TypeSwitchScenario implements SelfCheckScenario {

    private static final int X = 120;
    private static final int Z = 120;
    private static final int Y = 200;
    private static final String CHANNEL = "类型开关频道";

    @Override
    public String name() {
        return "type_switch";
    }

    private static void loadFluidSwitch(boolean enabled) throws IOException {
        File file = File.createTempFile("warpcovers-switch", ".cfg");
        try {
            Configuration config = new Configuration(file);
            config.get("types", "enableFluids", true)
                .set(enabled);
            config.save();
            ServerConfig.load(file);
        } finally {
            file.delete();
        }
    }

    @Override
    public void setup() {
        WorldServer world = world(0);
        IGregTechTileEntity tankA = placeTank(world, X, Y, Z);
        IGregTechTileEntity tankB = placeTank(world, X + 2, Y, Z);
        tankA.setFrontFacing(ForgeDirection.DOWN);
        tankB.setFrontFacing(ForgeDirection.DOWN);

        ChannelHub hub = WarpServices.hub();
        deleteChannelsNamed(hub, CHANNEL);
        Channel channel = hub.createChannel(ChannelType.FLUID, CHANNEL);
        FluidWarpCover input = attachCover(tankA, ForgeDirection.UP);
        FluidWarpCover output = attachCover(tankB, ForgeDirection.UP);
        for (FluidWarpCover cover : new FluidWarpCover[] { input, output }) {
            cover.connect(channel.id(), "selfcheck");
            cover.filter()
                .add("water");
        }
        input.setInteractionState(InteractionState.INPUT);
        output.setInteractionState(InteractionState.OUTPUT);

        try {
            expect("默认流体开关", 1, ServerConfig.enableFluids ? 1 : 0);
            pour(tankA, 1000);
            runCovers(0, input);
            expect("开启时 B 里的水", 1000, fluidIn(tankB, "water"));

            loadFluidSwitch(false);
            check("配置文件关闭流体后 enableFluids 为 false", !ServerConfig.enableFluids);
            check("物品开关不受影响", ServerConfig.enableItems && ServerConfig.enableEnergy);
            pour(tankA, 1000);
            runCovers(20, input);
            expect("关闭时 A 里的水（不传输）", 1000, fluidIn(tankA, "water"));
            expect("关闭时 B 里的水（不变）", 1000, fluidIn(tankB, "water"));
            check("关闭后频道仍在", hub.findChannel(channel.id()) != null);
            check("关闭后覆盖板仍连着频道", channel.id().equals(input.channelId()) && channel.id().equals(output.channelId()));
            expect("关闭时输入端状态", EndpointStatus.DISABLED.ordinal(), hub.statusOf(input.endpointId(), 20).ordinal());
            expect(
                "关闭时界面状态",
                EndpointStatus.DISABLED.ordinal(),
                new CoverPanel(hub, ChannelType.FLUID, input, new Random()).status(20)
                    .ordinal());

            loadFluidSwitch(true);
            check("重新开启后 enableFluids 为 true", ServerConfig.enableFluids);
            runCovers(40, input);
            expect("重新开启后 A 里的水", 0, fluidIn(tankA, "water"));
            expect("重新开启后 B 里的水", 2000, fluidIn(tankB, "water"));
        } catch (IOException e) {
            throw new IllegalStateException("无法写临时配置文件", e);
        } finally {
            ServerConfig.enableFluids = true;
        }
    }

    @Override
    public SelfCheckResult verify() {
        WorldServer world = world(0);
        IGregTechTileEntity tankA = tileAt(world, X, Y, Z);
        IGregTechTileEntity tankB = tileAt(world, X + 2, Y, Z);
        if (tankA == null || tankB == null) {
            return SelfCheckResult.fail("重启后有储罐丢失");
        }
        if (!ServerConfig.enableFluids) {
            return SelfCheckResult.fail("重启后流体开关没有恢复为开启（真实配置文件被改动）");
        }
        if (fluidIn(tankB, "water") != 2000) {
            return SelfCheckResult.fail("重启后 B 里的水不对: " + fluidIn(tankB, "water"));
        }
        ChannelHub hub = WarpServices.hub();
        Channel channel = channelNamed(hub, CHANNEL);
        Cover cover = tankA.getCoverAtSide(ForgeDirection.UP);
        if (channel == null || !(cover instanceof FluidWarpCover)
            || !channel.id()
                .equals(((FluidWarpCover) cover).channelId())) {
            return SelfCheckResult.fail("重启后频道或覆盖板连接丢失");
        }
        FluidWarpCover input = (FluidWarpCover) cover;
        if (hub.statusOf(input.endpointId(), 1000) == EndpointStatus.DISABLED) {
            return SelfCheckResult.fail("重启后输入端仍显示服务器已禁用");
        }
        return SelfCheckResult.pass("流体关闭时停止传输且数据保留，开启并重启后不再显示已禁用");
    }
}
