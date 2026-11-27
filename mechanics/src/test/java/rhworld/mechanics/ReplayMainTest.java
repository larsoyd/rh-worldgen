package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayMainTest {
    private static final Path GOLDEN = Path.of("..", "samples", "rides-rigged-1024.json");

    @TempDir
    Path tmp;

    private record Run(int exit, List<String> lines) {
        String last() {
            return lines.getLast();
        }
    }

    private static Run run(String... args) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        int exit = ReplayMain.run(args, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return new Run(exit, bytes.toString(StandardCharsets.UTF_8).lines().toList());
    }

    private Path tampered(String from, String to) throws IOException {
        String text = Files.readString(GOLDEN);
        assertTrue(Pattern.compile(from).matcher(text).find(), from);
        Path file = tmp.resolve("tampered.json");
        Files.writeString(file, text.replaceFirst(from, to));
        return file;
    }

    @Test
    void goldenLogReplaysClean() {
        Run r = run("--log", GOLDEN.toString());
        assertEquals(0, r.exit(), String.join("\n", r.lines()));
        assertTrue(r.lines().getFirst().startsWith("[replay] rides=8 dice=player n_bits=1024"), r.lines().getFirst());
        assertTrue(r.lines().get(1).startsWith("[audit] rides_warm="), r.lines().get(1));
        assertEquals("[replay] result=ok", r.last());
    }

    @Test
    void reportOfTheWrongColourIsRefused() throws IOException {
        Run r = run("--log", tampered("(\"colour\":\"warm\",\"index\":0,\"opening\":\"[0-9a-f]{64}\",\"reported\":\")[a-z]+", "$1mist").toString());
        assertEquals(2, r.exit(), String.join("\n", r.lines()));
        assertEquals("refused: reported realm has the wrong colour index=0", r.last());
    }

    @Test
    void forgedArrivalIsRefused() throws IOException {
        String text = Files.readString(GOLDEN);
        int at = text.lastIndexOf("\"after\":\"") + 9;
        char d = text.charAt(at + 5);
        Path file = tmp.resolve("forged.json");
        Files.writeString(file, text.substring(0, at + 5) + (d == '1' ? '2' : '1') + text.substring(at + 6));
        Run r = run("--log", file.toString());
        assertEquals(2, r.exit(), String.join("\n", r.lines()));
        assertEquals("refused: ride after differs from the replay index=7", r.last());
    }

    @Test
    void brokenChainIsRefusedWhileReading() throws IOException {
        String text = Files.readString(GOLDEN);
        int at = text.indexOf("\"opening\":\"") + 11;
        Path file = tmp.resolve("chain.json");
        Files.writeString(file, text.substring(0, at) + (text.charAt(at) == 'a' ? 'b' : 'a') + text.substring(at + 1));
        Run r = run("--log", file.toString());
        assertEquals(2, r.exit());
        assertEquals("refused: opening does not follow the chain index=0", r.last());
    }

    @Test
    void usageAndMissingFilesExitOne() {
        Run r = run();
        assertEquals(1, r.exit());
        assertEquals("refused: usage replay --log <file>", r.last());
        r = run("--log", "no-such-file.json");
        assertEquals(1, r.exit());
        assertEquals("refused: cannot read log file=no-such-file.json", r.last());
    }
}
