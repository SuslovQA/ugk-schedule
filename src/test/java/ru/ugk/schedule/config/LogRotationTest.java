package ru.ugk.schedule.config;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

class LogRotationTest {
    @TempDir Path directory;

    @Test void compressesLargeLogsInsideDateDirectoryWithoutLosingMessages() throws Exception {
        String xml;
        try (var input = getClass().getResourceAsStream("/logback-spring.xml")) {
            xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        // Supply the Spring properties directly in this isolated Logback context.
        xml = xml.replaceAll("<springProperty[^>]*/>", "");
        var context = new LoggerContext();
        context.setMDCAdapter(new LogbackMDCAdapter());
        context.putProperty("APP_LOG_DIRECTORY", directory.toString().replace('\\', '/'));
        context.putProperty("APP_LOG_MAX_FILE_SIZE", "1KB");
        context.putProperty("APP_LOG_HISTORY", "0");
        var configurator = new JoranConfigurator();
        configurator.setContext(context);
        LocalDate date = LocalDate.now();
        try {
            configurator.doConfigure(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
            context.getLogger("ROOT").detachAppender("CONSOLE");
            for (int i = 0; i < 100; i++) context.getLogger("rotation-test").info("event-{} {}", i, "Ж".repeat(100));
        } finally {
            context.stop(); // Waits for asynchronous compression to finish.
        }
        try (var paths = Files.walk(directory)) {
            var files = paths.filter(Files::isRegularFile).toList();
            assertThat(files).anyMatch(p -> p.toString().endsWith(".gz"));
            assertThat(files).anyMatch(p -> p.toString().endsWith(".log"));
            assertThat(files).allMatch(p -> p.getParent().getFileName().toString().equals(date.toString())
                    || p.getParent().getFileName().toString().equals(LocalDate.now().toString()));
            var content = new StringBuilder();
            for (Path file : files) {
                if (file.toString().endsWith(".gz")) {
                    try (var zip = new GZIPInputStream(Files.newInputStream(file))) {
                        content.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
                    }
                } else content.append(Files.readString(file));
            }
            assertThat(content.toString().lines().count()).isEqualTo(100);
            for (int i = 0; i < 100; i++) assertThat(content.toString()).contains("event-" + i + " ");
        }
    }
}
