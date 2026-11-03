package com.rtgame.warpcovers.selfcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelfCheckRunnerTest {

    /** 用内存列表代替“世界”，模拟布置阶段写入、校验阶段读取。 */
    private static final class FakeScenario implements SelfCheckScenario {

        final List<String> world;
        boolean failSetup;
        boolean verifyCalled;

        FakeScenario(List<String> world) {
            this.world = world;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public void setup() {
            if (failSetup) {
                throw new IllegalStateException("布置炸了");
            }
            world.add("machine");
        }

        @Override
        public SelfCheckResult verify() {
            verifyCalled = true;
            return world.contains("machine") ? SelfCheckResult.pass("机器仍在") : SelfCheckResult.fail("机器丢失");
        }
    }

    private static SelfCheckLog newLog(Path dir) {
        return new SelfCheckLog(dir, Clock.fixed(Instant.parse("2026-11-03T09:30:05Z"), ZoneOffset.UTC));
    }

    private static SelfCheckRunner newRunner(Path dir, SelfCheckScenario scenario) {
        return new SelfCheckRunner(dir.resolve("state.txt"), newLog(dir.resolve("logs")), Arrays.asList(scenario));
    }

    @Test
    void 布置之后即使重建执行器也能校验通过(@TempDir Path dir) {
        List<String> persistentWorld = new ArrayList<>();

        SelfCheckResult setup = newRunner(dir, new FakeScenario(persistentWorld)).setup("fake");
        // 模拟服务器重启：执行器和场景对象全部重建，只有状态文件与“世界”留存
        SelfCheckResult verify = newRunner(dir, new FakeScenario(persistentWorld)).verify("fake");

        assertTrue(setup.passed());
        assertTrue(verify.passed());
    }

    @Test
    void 未布置就校验会失败且不会执行场景校验(@TempDir Path dir) {
        FakeScenario scenario = new FakeScenario(new ArrayList<>());

        SelfCheckResult verify = newRunner(dir, scenario).verify("fake");

        assertFalse(verify.passed());
        assertFalse(scenario.verifyCalled);
    }

    @Test
    void 校验失败会如实报告并写入结果行(@TempDir Path dir) throws Exception {
        List<String> world = new ArrayList<>();
        newRunner(dir, new FakeScenario(world)).setup("fake");
        world.clear(); // 机器在“重启”后丢了
        SelfCheckLog log = newLog(dir.resolve("logs"));
        SelfCheckRunner runner = new SelfCheckRunner(
            dir.resolve("state.txt"),
            log,
            Arrays.asList(new FakeScenario(world)));

        SelfCheckResult verify = runner.verify("fake");

        assertFalse(verify.passed());
        assertEquals("机器丢失", verify.detail());
        List<String> lines = Files.readAllLines(log.file(), StandardCharsets.UTF_8);
        assertEquals("[RESULT] verify fake FAIL 机器丢失", lines.get(lines.size() - 1));
    }

    @Test
    void 布置成功时写入通过结果行(@TempDir Path dir) throws Exception {
        SelfCheckLog log = newLog(dir.resolve("logs"));
        SelfCheckRunner runner = new SelfCheckRunner(
            dir.resolve("state.txt"),
            log,
            Arrays.asList(new FakeScenario(new ArrayList<>())));

        runner.setup("fake");

        List<String> lines = Files.readAllLines(log.file(), StandardCharsets.UTF_8);
        assertEquals("[RESULT] setup fake PASS", lines.get(lines.size() - 1));
    }

    @Test
    void 布置抛异常算失败且不会被记为已布置(@TempDir Path dir) {
        FakeScenario scenario = new FakeScenario(new ArrayList<>());
        scenario.failSetup = true;
        SelfCheckRunner runner = newRunner(dir, scenario);

        SelfCheckResult setup = runner.setup("fake");
        SelfCheckResult verify = runner.verify("fake");

        assertFalse(setup.passed());
        assertFalse(verify.passed());
        assertFalse(scenario.verifyCalled);
    }

    @Test
    void 未知场景名直接失败(@TempDir Path dir) {
        SelfCheckRunner runner = newRunner(dir, new FakeScenario(new ArrayList<>()));

        assertFalse(
            runner.setup("nope")
                .passed());
        assertFalse(
            runner.verify("nope")
                .passed());
    }
}
