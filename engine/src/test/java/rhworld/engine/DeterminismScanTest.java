package rhworld.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class DeterminismScanTest {
    private static final Pattern BANNED = Pattern.compile(
            "\\bdouble\\b|\\bfloat\\b|isProbablePrime\\(\\s*\\d|java\\.util\\.Random\\b|\\bnew Random\\(|Math\\.random|ThreadLocalRandom"
                    + "|SecureRandom|currentTimeMillis|\\bHashMap\\b|\\bHashSet\\b|hashCode\\(\\)\\s*[%&]");

    @Test
    void mainSourcesUseNoNondeterministicApi() throws IOException {
        Path root = Path.of(System.getProperty("rhworld.engine.main", "src/main/java"));
        assertTrue(Files.isDirectory(root), "main sources not found at " + root.toAbsolutePath());
        List<String> hits = new ArrayList<>();
        int files = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path p : paths.filter(x -> x.toString().endsWith(".java")).sorted().toList()) {
                files++;
                List<String> lines = Files.readAllLines(p);
                for (int i = 0; i < lines.size(); i++) {
                    if (BANNED.matcher(lines.get(i)).find()) hits.add(root.relativize(p) + ":" + (i + 1) + " " + lines.get(i).strip());
                }
            }
        }
        assertTrue(files >= 5, "files=" + files);
        assertEquals(List.of(), hits);
    }
}
