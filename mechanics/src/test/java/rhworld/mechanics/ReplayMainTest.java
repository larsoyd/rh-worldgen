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
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rhworld.engine.cli.Cli;
import rhworld.engine.json.Json;

class ReplayMainTest {
    private static final Path GOLDEN = Path.of("..", "samples", "rides-rigged-1024.json");

    @TempDir
    Path tmp;

    private record Run(int exit, List<String> out, List<String> err) {
        String last() {
            return err.getLast();
        }

        String all() {
            return String.join("\n", out) + "\n--\n" + String.join("\n", err);
        }
    }

    private static Run launch(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exit = Launcher.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exit, out.toString(StandardCharsets.UTF_8).lines().toList(), err.toString(StandardCharsets.UTF_8).lines().toList());
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
        Run r = launch("replay", "--log", GOLDEN.toString());
        assertEquals(0, r.exit(), r.all());
        assertTrue(r.err().getFirst().startsWith("[replay] rides=8 dice=player n_bits=1024"), r.all());
        assertTrue(r.err().get(1).startsWith("[audit] rides_warm="), r.all());
        assertEquals("[rhworld] verdict=ok rides=8", r.last());
    }

    @Test
    void jsonTelemetryEndsWithAVerdictRecord() {
        Run r = launch("replay", "--log", GOLDEN.toString(), "--telemetry", "json");
        assertEquals(0, r.exit(), r.all());
        Map<?, ?> last = (Map<?, ?>) Json.parse(r.last());
        assertEquals("verdict", last.get("tag"));
        assertEquals("ok", last.get("verdict"));
    }

    @Test
    void reportOfTheWrongColourIsRefused() throws IOException {
        Path file = tampered("(\"colour\":\"warm\",\"index\":0,\"opening\":\"[0-9a-f]{64}\",\"reported\":\")[a-z]+", "$1mist");
        Run r = launch("replay", "--log", file.toString());
        assertEquals(2, r.exit(), r.all());
        assertEquals("refused: reported realm has the wrong colour index=0", r.last());
    }

    @Test
    void forgedArrivalIsRefused() throws IOException {
        String text = Files.readString(GOLDEN);
        int at = text.lastIndexOf("\"after\":\"") + 9;
        char d = text.charAt(at + 5);
        Path file = tmp.resolve("forged.json");
        Files.writeString(file, text.substring(0, at + 5) + (d == '1' ? '2' : '1') + text.substring(at + 6));
        Run r = launch("replay", "--log", file.toString());
        assertEquals(2, r.exit(), r.all());
        assertEquals("refused: ride after differs from the replay index=7", r.last());
    }

    @Test
    void brokenChainIsRefusedWhileReading() throws IOException {
        String text = Files.readString(GOLDEN);
        int at = text.indexOf("\"opening\":\"") + 11;
        Path file = tmp.resolve("chain.json");
        Files.writeString(file, text.substring(0, at) + (text.charAt(at) == 'a' ? 'b' : 'a') + text.substring(at + 1));
        Run r = launch("replay", "--log", file.toString());
        assertEquals(2, r.exit(), r.all());
        assertEquals("refused: opening does not follow the chain index=0", r.last());
    }

    @Test
    void usageAndMissingFilesExitOne() {
        Run r = launch("replay");
        assertEquals(1, r.exit(), r.all());
        assertEquals("error: replay needs --log <file>", r.last());
        r = launch("replay", "--log", "no-such-file.json");
        assertEquals(1, r.exit(), r.all());
        assertEquals("error: io cannot read log file=no-such-file.json", r.last());
    }

    @Test
    void otherCommandsGoToTheEngine() {
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int engine = Cli.run(new String[] {"verify"}, new PrintStream(new ByteArrayOutputStream()), new PrintStream(err, true, StandardCharsets.UTF_8));
        Run r = launch("verify");
        assertEquals(engine, r.exit());
        assertEquals(err.toString(StandardCharsets.UTF_8).lines().toList(), r.err());
    }

    @Test
    void noCommandPrintsBothUsages() {
        Run r = launch();
        assertEquals(1, r.exit());
        assertTrue(r.err().getFirst().startsWith("usage: rhworld replay --log <file>"), r.all());
        assertTrue(r.err().stream().anyMatch(l -> l.startsWith("usage: rhworld certify")), r.all());
    }
}
