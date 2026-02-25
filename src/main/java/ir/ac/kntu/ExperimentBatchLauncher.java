package ir.ac.kntu;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import ir.ac.kntu.experiment.ExperimentBatchRunner;

public final class ExperimentBatchLauncher {

    // private static final List<String> CONFIGS = List.of(
    //         "configs/random/exp4.yaml",
    //         "configs/random/exp6.yaml",
    //         "configs/star/exp6.yaml",
    //         "configs/star/exp8.yaml",
    //         "configs/tree/exp6.yaml",
    //         "configs/ring/exp6.yaml",
    //         "configs/partial-mesh/exp6.yaml",
    //         "configs/full-mesh/exp6.yaml",
    //         "configs/line/exp6.yaml",
    //         "configs/grid/exp6.yaml");
    private static final List<String> CONFIGS = List.of("configs/grid/exp6.yaml");

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
