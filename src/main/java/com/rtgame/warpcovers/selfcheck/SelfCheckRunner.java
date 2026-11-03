package com.rtgame.warpcovers.selfcheck;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 自检执行器：「布置」成功后把场景名记入状态文件，「校验」时读取状态文件，
 * 因此执行器即使随服务器重启重建，两个阶段依然可以衔接。
 * 每个阶段的结果都会以 {@code [RESULT] <阶段> <场景> <PASS|FAIL> <详情>} 写入日志，供脚本读取。
 */
public final class SelfCheckRunner {

    private final Path stateFile;
    private final SelfCheckLog log;
    private final Map<String, SelfCheckScenario> scenarios = new LinkedHashMap<>();

    public SelfCheckRunner(Path stateFile, SelfCheckLog log, Collection<SelfCheckScenario> scenarios) {
        this.stateFile = stateFile;
        this.log = log;
        for (SelfCheckScenario scenario : scenarios) {
            this.scenarios.put(scenario.name(), scenario);
        }
    }

    public SelfCheckResult setup(String name) {
        SelfCheckScenario scenario = scenarios.get(name);
        if (scenario == null) {
            return record("setup", name, SelfCheckResult.fail("未知场景"));
        }
        try {
            scenario.setup();
            markSetUp(name);
        } catch (Exception | LinkageError e) {
            return record("setup", name, SelfCheckResult.fail("布置失败: " + describe(e)));
        }
        return record("setup", name, SelfCheckResult.pass(""));
    }

    public SelfCheckResult verify(String name) {
        SelfCheckScenario scenario = scenarios.get(name);
        if (scenario == null) {
            return record("verify", name, SelfCheckResult.fail("未知场景"));
        }
        if (!readSetUp().contains(name)) {
            return record("verify", name, SelfCheckResult.fail("尚未布置，无法校验"));
        }
        SelfCheckResult result;
        try {
            result = scenario.verify();
        } catch (Exception | LinkageError e) {
            result = SelfCheckResult.fail("校验异常: " + describe(e));
        }
        return record("verify", name, result);
    }

    private SelfCheckResult record(String stage, String name, SelfCheckResult result) {
        String line = "[RESULT] " + stage + " " + name + " " + (result.passed() ? "PASS" : "FAIL");
        if (!result.detail()
            .isEmpty()) {
            line += " " + result.detail();
        }
        log.write(line);
        return result;
    }

    private Set<String> readSetUp() {
        Set<String> names = new LinkedHashSet<>();
        if (Files.exists(stateFile)) {
            try {
                for (String line : Files.readAllLines(stateFile, StandardCharsets.UTF_8)) {
                    if (!line.trim()
                        .isEmpty()) {
                        names.add(line.trim());
                    }
                }
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }
        return names;
    }

    private void markSetUp(String name) throws IOException {
        Set<String> names = readSetUp();
        names.add(name);
        Path parent = stateFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(
            stateFile,
            String.join("\n", names)
                .getBytes(StandardCharsets.UTF_8));
    }

    /** 异常加上最前面几层调用栈，日志只有一行 [RESULT]，没有栈就很难定位。 */
    static String describe(Throwable e) {
        StringBuilder text = new StringBuilder(String.valueOf(e));
        StackTraceElement[] frames = e.getStackTrace();
        for (int i = 0; i < Math.min(8, frames.length); i++) {
            text.append(i == 0 ? " @ " : " <- ")
                .append(frames[i]);
        }
        return text.toString();
    }
}
