package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rhworld.engine.arith.Arith;
import rhworld.engine.json.Json;

class CanonicalTest {
    private static final Path GOLDEN = Path.of("..", "samples", "rides-rigged-1024.json");

    @Test
    void realmsAreLabelledByTheSmallerFactorInEitherOrder() throws IOException {
        Map<?, ?> key = (Map<?, ?>) Json.parse(Files.readString(Path.of("..", "samples", "rigged-1024", "gm-key.json")));
        BigInteger a = new BigInteger((String) key.get("p"));
        BigInteger b = new BigInteger((String) key.get("q"));
        BigInteger small = a.min(b);
        assertTrue(a.compareTo(b) > 0, "sample key lists the larger factor first");
        Warden fileOrder = new Warden(a, b);
        Warden swapped = new Warden(b, a);
        for (long x = 2; x < 400; x++) {
            BigInteger bx = BigInteger.valueOf(x);
            if (Arith.jacobi(bx, fileOrder.modulus()) == 0) continue;
            assertEquals(fileOrder.realm(bx), swapped.realm(bx), "x=" + x);
            assertEquals(Arith.jacobi(bx, small) == -1, (fileOrder.realm(bx).state() & 1) == 1, "x=" + x);
        }
    }

    @Test
    void whitespaceOutsideTheCanonicalDumpIsRefused() throws IOException {
        String text = Files.readString(GOLDEN);
        assertEquals(text, RideLog.fromJson(text).toJson());
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RideLog.fromJson(text.replaceFirst(",", ", ")));
        assertEquals("refused: ride log is not canonical json", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> RideLog.fromJson(text + "\n"));
        assertEquals("refused: ride log is not canonical json", e.getMessage());
    }

    @Test
    void malformedFieldsGivePlainReasons() throws IOException {
        String text = Files.readString(GOLDEN);
        String stringIndex = text.replaceFirst("\"index\":0", "\"index\":\"0\"");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> RideLog.fromJson(stringIndex));
        assertEquals("refused: malformed ride log field=index", e.getMessage());
        String noN = text.replaceFirst("\"n\":\"[0-9]+\",", "");
        e = assertThrows(IllegalArgumentException.class, () -> RideLog.fromJson(noN));
        assertEquals("refused: malformed ride log field=n", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> RideLog.fromJson("[1,2]"));
        assertEquals("refused: malformed ride log field=root", e.getMessage());
        e = assertThrows(IllegalArgumentException.class, () -> RideLog.fromJson("{\"format\""));
        assertEquals("refused: ride log is not json", e.getMessage());
    }
}
