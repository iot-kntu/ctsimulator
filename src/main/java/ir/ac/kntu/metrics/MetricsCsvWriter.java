package ir.ac.kntu.metrics;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

public final class MetricsCsvWriter {

    private static final List<String> HEADERS = List.of(
            "runId",
            "algorithm",
            "framework",
            "nNodes",
            "topology",
            "lossRate",
            "failureRate",
            "load",
            "seed",
            "decisionSuccess",
            "decisionLatencySlots",
            "decisionLatencyMs",
            "totalMessages",
            "successfulMessages",
            "failedMessages",
            "failedCollision",
            "failedDrop",
            "failedSilent",
            "failedFaulty",
            "failedNotListening",
            "p95Latency",
            "p99Latency",
            "throughput"
    );

    private MetricsCsvWriter() {
    }

    public static void append(Path path, RunMetrics metrics) throws IOException {
        if (path == null || metrics == null) {
            return;
        }
        Files.createDirectories(path.getParent());

        boolean writeHeader = !Files.exists(path) || Files.size(path) == 0;

        try (BufferedWriter writer = Files.newBufferedWriter(path,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            if (writeHeader) {
                writer.write(String.join(",", HEADERS));
                writer.newLine();
            }
            writer.write(toCsvLine(metrics));
            writer.newLine();
        }
    }

    private static String toCsvLine(RunMetrics metrics) {
        return String.join(",",
                escape(metrics.runId()),
                escape(metrics.algorithm()),
                escape(metrics.framework()),
                String.valueOf(metrics.nNodes()),
                escape(metrics.topology()),
                String.valueOf(metrics.lossRate()),
                String.valueOf(metrics.failureRate()),
                String.valueOf(metrics.load()),
                String.valueOf(metrics.seed()),
                String.valueOf(metrics.decisionSuccess()),
                String.valueOf(metrics.decisionLatencySlots()),
                nullable(metrics.decisionLatencyMs()),
                String.valueOf(metrics.totalMessages()),
                String.valueOf(metrics.successfulMessages()),
                String.valueOf(metrics.failedMessages()),
                String.valueOf(metrics.failedCollision()),
                String.valueOf(metrics.failedDrop()),
                String.valueOf(metrics.failedSilent()),
                String.valueOf(metrics.failedFaulty()),
                String.valueOf(metrics.failedNotListening()),
                nullable(metrics.p95Latency()),
                nullable(metrics.p99Latency()),
                nullable(metrics.throughput())
        );
    }

    private static String nullable(Double value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",") || value.contains("\"") || value.contains("\n");
        if (!needsQuotes) {
            return value;
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }
}
