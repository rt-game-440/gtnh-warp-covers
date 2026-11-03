package com.rtgame.warpcovers.selfcheck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SelfCheckLogTest {

    /** 可手动拨动的时钟，用来模拟同一次启动里时间流逝。 */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void 日志文件名按启动时间命名并位于日志目录(@TempDir Path logDir) {
        Clock clock = Clock.fixed(Instant.parse("2026-11-03T09:30:05Z"), ZoneOffset.UTC);

        SelfCheckLog log = new SelfCheckLog(logDir, clock);

        assertEquals(logDir.resolve("2026-11-03 09-30-05.log"), log.file());
    }

    @Test
    void 同一次启动的多次写入复用同一个文件(@TempDir Path logDir) throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-03T09:30:05Z"));
        SelfCheckLog log = new SelfCheckLog(logDir, clock);

        log.write("第一行");
        clock.advanceSeconds(90);
        log.write("第二行");

        List<String> lines = Files.readAllLines(log.file(), StandardCharsets.UTF_8);
        assertEquals(Arrays.asList("第一行", "第二行"), lines);
        try (java.util.stream.Stream<Path> files = Files.list(logDir)) {
            assertEquals(1, files.count());
        }
        assertTrue(Files.exists(logDir.resolve("2026-11-03 09-30-05.log")));
    }

    @Test
    void 日志目录不存在时自动创建(@TempDir Path root) throws Exception {
        Path logDir = root.resolve("logs");
        SelfCheckLog log = new SelfCheckLog(logDir, Clock.fixed(Instant.parse("2026-11-03T09:30:05Z"), ZoneOffset.UTC));

        log.write("hello");

        assertEquals(Arrays.asList("hello"), Files.readAllLines(log.file(), StandardCharsets.UTF_8));
    }
}
