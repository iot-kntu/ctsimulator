package ir.ac.kntu.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class MetricsDetailWriter {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private MetricsDetailWriter() {
    }

    public static void write(Path path, RunDetail detail) throws IOException {
        if (path == null || detail == null) {
            return;
        }
        Files.createDirectories(path.getParent());
        MAPPER.writeValue(path.toFile(), detail);
    }
}
