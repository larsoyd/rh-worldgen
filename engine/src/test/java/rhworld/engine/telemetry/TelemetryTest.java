package rhworld.engine.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class TelemetryTest {
    private static String capture(java.util.function.Function<PrintStream, Telemetry> make) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(buf, true, StandardCharsets.UTF_8);
        Telemetry t = make.apply(out);
        t.event("c1.admit", "bits", 1024, "budget", 503791L, "n", BigInteger.valueOf(15), "reason", "small factor");
        t.event("empty");
        return buf.toString(StandardCharsets.UTF_8);
    }

    @Test
    void linesUseTagAndKeyValues() {
        assertEquals("[c1.admit] bits=1024 budget=503791 n=15 reason=\"small factor\"\n[empty]\n", capture(Telemetry::lines));
    }

    @Test
    void jsonLinesKeepFieldsAndTag() {
        assertEquals("{\"bits\":1024,\"budget\":503791,\"n\":\"15\",\"reason\":\"small factor\",\"tag\":\"c1.admit\"}\n{\"tag\":\"empty\"}\n",
                capture(Telemetry::jsonLines));
    }

    @Test
    void teeWritesBothAndNoneWritesNothing() {
        ByteArrayOutputStream a = new ByteArrayOutputStream(), b = new ByteArrayOutputStream();
        Telemetry t = Telemetry.tee(Telemetry.lines(new PrintStream(a, true, StandardCharsets.UTF_8)),
                Telemetry.jsonLines(new PrintStream(b, true, StandardCharsets.UTF_8)));
        t.event("x", "k", 1);
        Telemetry.NONE.event("x", "k", 1);
        assertEquals("[x] k=1\n", a.toString(StandardCharsets.UTF_8));
        assertEquals("{\"k\":1,\"tag\":\"x\"}\n", b.toString(StandardCharsets.UTF_8));
    }

    @Test
    void oddFieldListIsRejected() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Telemetry.NONE.event("x", "k"));
    }
}
