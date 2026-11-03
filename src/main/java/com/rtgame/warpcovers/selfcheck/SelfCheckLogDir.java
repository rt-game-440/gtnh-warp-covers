package com.rtgame.warpcovers.selfcheck;

import java.io.File;
import java.nio.file.Path;

/** 自检日志目录：向上寻找项目根（含 settings.gradle.kts）下的 logs，找不到则用游戏目录下的 logs。 */
public final class SelfCheckLogDir {

    private SelfCheckLogDir() {}

    public static Path resolve(File gameDir) {
        File dir = gameDir.getAbsoluteFile();
        while (dir != null) {
            if (new File(dir, "settings.gradle.kts").isFile()) {
                return new File(dir, "logs").toPath();
            }
            dir = dir.getParentFile();
        }
        return new File(gameDir, "logs").toPath();
    }
}
