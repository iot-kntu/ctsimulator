package ir.ac.kntu.simulation;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.logging.LogManager;

final class LoggingConfigurator {

    private static final String LOGGING_PROPERTIES = "logging.properties";
    private static final Path LOG_DIR = Path.of("logs");
    private static boolean configured = false;

    private LoggingConfigurator() {
    }

    static void configure() throws IOException {
        if (configured) {
            return;
        }

        try (InputStream inputStream = ClassLoader.getSystemClassLoader().getResourceAsStream(LOGGING_PROPERTIES)) {
            if (inputStream == null) {
                System.err.println("Cannot find logging config file, is package corrupted??");
                return;
            }

            File logDir = LOG_DIR.toAbsolutePath().toFile();
            if (!logDir.exists()) {
                logDir.mkdir();
            }

            LogManager.getLogManager().readConfiguration(inputStream);
            configured = true;
        }
    }
}
