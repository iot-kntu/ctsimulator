package ir.ac.kntu.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

public final class MetricsCsvWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final List<String> HEADERS_V1 = List.of(
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

    private static final List<String> HEADERS_V2 = List.of(
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
            "throughput",
            "totalDecisions",
            "successfulDecisions"
    );

    private static final List<String> HEADERS = List.of(
            "runId",
            "algorithm",
            "framework",
            "nNodes",
            "topology",
            "lossRate",
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
            "throughput",
            "totalDecisions",
            "successfulDecisions"
    );

    private MetricsCsvWriter() {
    }

    public static void append(Path path, RunMetrics metrics) throws IOException {
        if (path == null || metrics == null) {
            return;
        }
        Files.createDirectories(path.getParent());

        boolean writeHeader = !Files.exists(path) || Files.size(path) == 0;
        if (!writeHeader) {
            migrateIfNeeded(path);
            writeHeader = Files.size(path) == 0;
        }

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
                nullable(metrics.throughput()),
                String.valueOf(metrics.totalDecisions()),
                String.valueOf(metrics.successfulDecisions())
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

    private static void migrateIfNeeded(Path path) throws IOException {
        if (!Files.exists(path) || Files.size(path) == 0) {
            return;
        }
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) {
            return;
        }

        String currentHeader = lines.get(0).trim();
        String expectedHeader = String.join(",", HEADERS);
        if (currentHeader.equals(expectedHeader)) {
            return;
        }

        String v1Header = String.join(",", HEADERS_V1);
        String v2Header = String.join(",", HEADERS_V2);
        boolean oldV1 = currentHeader.equals(v1Header);
        boolean oldV2 = currentHeader.equals(v2Header);
        if (!oldV1 && !oldV2) {
            // Unknown header; avoid rewriting user data.
            return;
        }

        Path backup = path.resolveSibling(path.getFileName().toString() + ".bak");
        Files.copy(path, backup, StandardCopyOption.REPLACE_EXISTING);

        List<String> upgraded = new ArrayList<>(lines.size() + 1);
        upgraded.add(expectedHeader);

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line == null || line.isBlank()) {
                continue;
            }

            String lineWithoutFailureRate = removeColumnFromCsvLine(line, 6);
            if (!oldV1) {
                upgraded.add(lineWithoutFailureRate);
                continue;
            }

            String runId = extractRunId(line);
            if (runId.isBlank()) {
                upgraded.add(lineWithoutFailureRate + ",,");
                continue;
            }

            DecisionCounts counts = loadDecisionCounts(path.getParent(), runId);
            if (counts == null) {
                upgraded.add(lineWithoutFailureRate + ",,");
            } else {
                upgraded.add(lineWithoutFailureRate + "," + counts.totalDecisions + "," + counts.successfulDecisions);
            }
        }

        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        Files.write(tmp, upgraded);
        Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
    }

    private static String extractRunId(String csvLine) {
        int comma = csvLine.indexOf(',');
        if (comma <= 0) {
            return "";
        }
        return csvLine.substring(0, comma).trim();
    }

    private static String removeColumnFromCsvLine(String csvLine, int columnIndex) {
        List<String> columns = parseCsvLine(csvLine);
        if (columnIndex >= 0 && columnIndex < columns.size()) {
            columns.remove(columnIndex);
        }
        StringBuilder rebuilt = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                rebuilt.append(',');
            }
            rebuilt.append(escape(columns.get(i)));
        }
        return rebuilt.toString();
    }

    private static List<String> parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            fields.add("");
            return fields;
        }
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
                continue;
            }
            if (ch == ',' && !inQuotes) {
                fields.add(field.toString());
                field.setLength(0);
                continue;
            }
            field.append(ch);
        }
        fields.add(field.toString());
        return fields;
    }

    private static DecisionCounts loadDecisionCounts(Path resultsDir, String runId) {
        if (resultsDir == null || runId == null || runId.isBlank()) {
            return null;
        }
        Path detail = resultsDir.resolve("run_" + runId + "_detail.json");
        if (!Files.exists(detail)) {
            return null;
        }
        try {
            RunDetail run = MAPPER.readValue(detail.toFile(), RunDetail.class);
            int total = run.decisions() != null ? run.decisions().size() : 0;
            int nNodes = run.nNodes();
            int successful = 0;
            if (run.decisions() != null) {
                for (RunDetail.DecisionDetail decision : run.decisions().values()) {
                    if (decision == null || decision.startTime() == null || decision.decisionTimes() == null) {
                        continue;
                    }
                    if (decision.decisionTimes().size() == nNodes) {
                        successful++;
                    }
                }
            }
            return new DecisionCounts(total, successful);
        } catch (Exception ignored) {
            return null;
        }
    }

    private record DecisionCounts(int totalDecisions, int successfulDecisions) {
    }
}
