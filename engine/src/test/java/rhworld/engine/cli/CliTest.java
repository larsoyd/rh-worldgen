package rhworld.engine.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rhworld.engine.Golden;
import rhworld.engine.json.Json;

class CliTest {
    @TempDir
    Path dir;

    record Run(int code, String out, String err) {
        String lastErr() {
            String[] lines = err.strip().split("\n");
            return lines[lines.length - 1];
        }
    }

    static Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(), err = new ByteArrayOutputStream();
        int code = Cli.run(args, new PrintStream(out, true, StandardCharsets.UTF_8), new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(code, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    static Map<String, Object> golden(int i) {
        return Golden.<Map<String, Object>>list("modulus").get(i);
    }

    @SuppressWarnings("unchecked")
    static String factor(int i) {
        return ((List<String>) golden(i).get("factors")).get(0);
    }

    @Test
    void certifyThenVerify() throws IOException {
        Path cert = dir.resolve("cert.json");
        Run c = run("certify", "--n", (String) golden(0).get("n"), "--out", cert.toString());
        assertEquals(0, c.code(), c.err());
        assertTrue(c.lastErr().startsWith("[rhworld] verdict=accepted world_id="), c.lastErr());
        assertTrue(c.err().contains("[c1.admit] bits=1024 "), c.err());
        Run v = run("verify", cert.toString());
        assertEquals(0, v.code(), v.err());
        assertTrue(v.lastErr().contains("verdict=verified") && v.lastErr().contains("revealed=false"), v.lastErr());
    }

    @Test
    void certifyWritesToStdoutAndReadsAGmFile() throws IOException {
        Path gm = dir.resolve("gm.json");
        Files.writeString(gm, "{\"n\":\"" + golden(3).get("n") + "\",\"p\":\"" + factor(3) + "\",\"bits\":512}\n");
        Run c = run("certify", "--gm", gm.toString());
        assertEquals(0, c.code(), c.err());
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) Json.parse(c.out().strip());
        assertTrue(((Map<?, ?>) m.get("deferred")).get("reveal") != null);
        Path cert = dir.resolve("c.json");
        Files.writeString(cert, c.out());
        Run v = run("verify", cert.toString());
        assertEquals(0, v.code(), v.err());
        assertTrue(v.lastErr().contains("revealed=true") && v.lastErr().contains("factors_status=mr25-probable"), v.lastErr());
    }

    @Test
    void revealByFlagWithProofs() throws IOException {
        BigInteger p = rhworld.engine.arith.PocklingtonAccess.smoothPrime(700), q = rhworld.engine.arith.PocklingtonAccess.smoothPrime(710);
        Path proofs = dir.resolve("proofs.json");
        Files.writeString(proofs, Json.write(Map.of("p", rhworld.engine.arith.PocklingtonAccess.certificate(p),
                "q", rhworld.engine.arith.PocklingtonAccess.certificate(q))));
        Path cert = dir.resolve("cert.json");
        Run c = run("certify", "--n", p.multiply(q).toString(), "--p", q.toString(), "--proofs", proofs.toString(), "--out", cert.toString());
        assertEquals(0, c.code(), c.err());
        assertTrue(c.lastErr().contains("revealed=true"), c.lastErr());
        Run v = run("verify", cert.toString());
        assertTrue(v.lastErr().contains("factors_status=proved"), v.lastErr());
        Run bad = run("certify", "--n", p.multiply(q).toString(), "--p", "7");
        assertEquals(2, bad.code());
        assertEquals("refused: revealed factor does not divide n", bad.lastErr());
    }

    @Test
    void refusalsExitTwoWithTheReasonLast() throws IOException {
        Run c = run("certify", "--n", (String) golden(12).get("n"));
        assertEquals(2, c.code(), c.err());
        assertEquals(golden(12).get("refused"), c.lastErr());
        Path cert = dir.resolve("cert.json");
        run("certify", "--n", (String) golden(1).get("n"), "--out", cert.toString());
        Files.writeString(cert, Files.readString(cert).replace("\"budget_exact\":true", "\"budget_exact\":false"));
        Run v = run("verify", cert.toString());
        assertEquals(2, v.code());
        assertEquals("refused: field mismatch recomputed.budget_exact", v.lastErr());
    }

    @Test
    void jsonTelemetryIsOneObjectPerLine() {
        Run c = run("certify", "--n", (String) golden(0).get("n"), "--telemetry", "json");
        assertEquals(0, c.code(), c.err());
        String[] lines = c.err().strip().split("\n");
        assertTrue(lines.length >= 4, c.err());
        for (String l : lines) assertTrue(Json.parse(l) instanceof Map<?, ?> m && m.containsKey("tag"), l);
        assertEquals("verdict", ((Map<?, ?>) Json.parse(lines[lines.length - 1])).get("tag"));
    }

    @Test
    void usageErrorsExitOne() {
        assertEquals(1, run().code());
        assertEquals(1, run("frobnicate").code());
        assertEquals(1, run("certify").code());
        assertEquals(1, run("certify", "--n", "12x").code());
        assertEquals(1, run("verify", dir.resolve("missing.json").toString()).code());
        assertTrue(run("certify", "--n").lastErr().startsWith("error: "));
    }

    @Test
    void benchReportsEveryModulus() throws IOException {
        Path f = dir.resolve("moduli.json");
        Files.writeString(f, "[{\"label\":\"a\",\"n\":\"" + golden(0).get("n") + "\",\"p\":\"" + factor(0) + "\"},{\"label\":\"b\",\"n\":\""
                + golden(12).get("n") + "\"}]");
        Run b = run("bench", "--moduli", f.toString(), "--repeat", "2");
        assertEquals(0, b.code(), b.err());
        String[] lines = b.out().strip().split("\n");
        assertEquals(2, lines.length, b.out());
        Map<?, ?> a = (Map<?, ?>) Json.parse(lines[0]);
        assertEquals("a", a.get("label"));
        assertEquals("accepted", a.get("verdict"));
        assertEquals(golden(0).get("g2"), a.get("g2"));
        for (String k : List.of("open_us", "certify_us", "verify_us", "reveal_us", "subset_product_us", "primes")) {
            assertTrue(a.get(k) instanceof BigInteger, k + " in " + lines[0]);
        }
        Map<?, ?> r = (Map<?, ?>) Json.parse(lines[1]);
        assertEquals("refused: small factor", r.get("verdict"));
    }
}
