package com.rtgame.warpcovers.selfcheck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 自检日志：文件名为 {@code yyyy-MM-dd HH-mm-ss.log}，以创建对象（即本次启动）的时刻命名，
 * 同一次启动内的所有写入复用同一个文件。
 */
public final class SelfCheckLog {

    private static final DateTimeFormatter FILE_NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH-mm-ss'.log'");

    private final Path file;

    public SelfCheckLog(Path logDir, Clock clock) {
        this.file = logDir.resolve(FILE_NAME.format(LocalDateTime.now(clock)));
    }

    public Path file() {
        return file;
    }

    public synchronized void write(String line) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(
                file,
                (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
