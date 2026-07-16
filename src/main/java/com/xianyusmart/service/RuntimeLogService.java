package com.xianyusmart.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Provides a small, read-only window into the application's current runtime log.
 *
 * <p>The logback configuration writes aggregate logs below
 * {@code ./logs/yyyy-MM-dd/all.log}. The most recently modified log file is
 * selected because a long-running application can keep writing to its startup
 * date folder. Only the end of that file is read so the dashboard remains
 * responsive even when a log file is large.</p>
 */
@Service
public class RuntimeLogService {

    private static final int DEFAULT_LINE_LIMIT = 200;
    private static final int MIN_LINE_LIMIT = 20;
    private static final int MAX_LINE_LIMIT = 500;
    private static final int MAX_READ_BYTES = 512 * 1024;

    public RuntimeLogTail tail(Integer requestedLines) {
        int lineLimit = normalizeLineLimit(requestedLines);
        Path logFile = resolveActiveLogFile();

        if (!Files.isRegularFile(logFile)) {
            return new RuntimeLogTail(List.of(), false, "当前暂无运行日志，应用产生运行记录后会显示在这里。");
        }

        try {
            List<String> lines = readLastLines(logFile, lineLimit);
            String message = lines.isEmpty() ? "当前暂无运行日志。" : "";
            return new RuntimeLogTail(lines, true, message);
        } catch (IOException exception) {
            return new RuntimeLogTail(List.of(), false, "暂时无法读取运行日志，请稍后刷新重试。");
        }
    }

    private Path resolveActiveLogFile() {
        String dateFolder = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        Path logHome = Path.of(System.getProperty("user.dir"), "logs");
        Path currentDayLog = logHome.resolve(dateFolder).resolve("all.log");
        if (!Files.isDirectory(logHome)) {
            return currentDayLog;
        }

        try (Stream<Path> paths = Files.list(logHome)) {
            return paths
                    .map(path -> path.resolve("all.log"))
                    .filter(Files::isRegularFile)
                    .max(Comparator.comparingLong(this::lastModifiedMillis))
                    .orElse(currentDayLog);
        } catch (IOException exception) {
            return currentDayLog;
        }
    }

    private long lastModifiedMillis(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private List<String> readLastLines(Path logFile, int lineLimit) throws IOException {
        try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.READ)) {
            long fileSize = channel.size();
            if (fileSize == 0) {
                return List.of();
            }

            long startPosition = Math.max(0, fileSize - MAX_READ_BYTES);
            ByteBuffer buffer = ByteBuffer.allocate((int) (fileSize - startPosition));
            channel.position(startPosition);
            while (buffer.hasRemaining() && channel.read(buffer) != -1) {
                // Continue reading until the selected tail window is complete.
            }

            buffer.flip();
            String content = StandardCharsets.UTF_8.decode(buffer).toString();
            if (startPosition > 0) {
                int firstNewLine = content.indexOf('\n');
                content = firstNewLine >= 0 ? content.substring(firstNewLine + 1) : "";
            }

            List<String> allLines = content.lines().toList();
            int fromIndex = Math.max(0, allLines.size() - lineLimit);
            return List.copyOf(allLines.subList(fromIndex, allLines.size()));
        }
    }

    private int normalizeLineLimit(Integer requestedLines) {
        if (requestedLines == null) {
            return DEFAULT_LINE_LIMIT;
        }
        return Math.max(MIN_LINE_LIMIT, Math.min(MAX_LINE_LIMIT, requestedLines));
    }

    public record RuntimeLogTail(List<String> lines, boolean available, String message) {
    }
}
