package com.rtgame.warpcovers;

import java.io.File;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import net.minecraft.launchwrapper.Launch;

import com.rtgame.warpcovers.selfcheck.DevEnvironment;
import com.rtgame.warpcovers.selfcheck.HullSmokeScenario;
import com.rtgame.warpcovers.selfcheck.SelfCheckCommand;
import com.rtgame.warpcovers.selfcheck.SelfCheckLog;
import com.rtgame.warpcovers.selfcheck.SelfCheckLogDir;
import com.rtgame.warpcovers.selfcheck.SelfCheckRunner;
import com.rtgame.warpcovers.selfcheck.SelfCheckScenario;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;

public class CommonProxy {

    public void preInit(FMLPreInitializationEvent event) {
        WarpCovers.LOG.info("WarpCovers {} 正在加载", Tags.VERSION);
        ServerConfig.load(event.getSuggestedConfigurationFile());
    }

    public void init(FMLInitializationEvent event) {}

    public void postInit(FMLPostInitializationEvent event) {}

    public void serverStarting(FMLServerStartingEvent event) {
        // 自检命令仅开发环境注册；发布版不暴露
        if (!DevEnvironment.isDevelopment(Launch.blackboard)) {
            return;
        }
        File gameDir = Launch.minecraftHome != null ? Launch.minecraftHome : new File(".");
        Path logDir = SelfCheckLogDir.resolve(gameDir);
        List<SelfCheckScenario> scenarios = Arrays.<SelfCheckScenario>asList(new HullSmokeScenario());
        SelfCheckRunner runner = new SelfCheckRunner(
            new File(gameDir, "warpcovers-selfcheck-state.txt").toPath(),
            new SelfCheckLog(logDir, Clock.systemDefaultZone()),
            scenarios);
        List<String> names = Arrays.asList(
            scenarios.get(0)
                .name());
        event.registerServerCommand(new SelfCheckCommand(runner, names));
        WarpCovers.LOG.info("开发环境：已注册 /warpcovers_selfcheck");
    }
}
