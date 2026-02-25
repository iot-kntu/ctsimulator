package ir.ac.kntu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import ir.ac.kntu.experiment.ExperimentBatchRunner;

public final class ExperimentBatchLauncher {

    // private static final List<String> CONFIGS = List.of("configs/exp.yaml", "configs/exp2.yaml", "configs/star.yaml");
    private static final List<String> CONFIGS = List.of("configs/star.yaml");

    private ExperimentBatchLauncher() {
    }

    public static void main(String[] args) {
        Path root = Path.of(System.getProperty("user.dir"));
        for (String config : CONFIGS) {
            Path configPath = root.resolve(config).normalize();
            if (!Files.exists(configPath)) {
                throw new IllegalStateException("Config not found: " + configPath);
            }
            ExperimentBatchRunner.main(new String[] { configPath.toString() });
        }
    }
}
