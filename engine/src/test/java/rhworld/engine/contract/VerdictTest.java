package rhworld.engine.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class VerdictTest {
    @Test
    void refusedRendersReasonAndMapsThrough() {
        Verdict<Integer> r = new Verdict.Refused<>("small factor");
        assertEquals("refused: small factor", r.toString());
        assertEquals(new Verdict.Refused<String>("small factor"), r.map(String::valueOf));
        assertEquals(new Verdict.Ok<>("7"), new Verdict.Ok<>(7).map(String::valueOf));
        assertEquals(new Verdict.Refused<Integer>("b"), new Verdict.Ok<>(1).then(x -> new Verdict.Refused<>("b")));
    }

    @Test
    void refusalNeedsAReason() {
        assertThrows(IllegalArgumentException.class, () -> new Verdict.Refused<>(" "));
    }
}
