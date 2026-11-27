package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LiveStateTest {
    private static final Season SEASON = new Season(Keys.sample("rigged-1024"));

    @Test
    void ridesOnALivePlayerDoNoWorkThatGrowsWithTheLog() {
        SeedChain c = new SeedChain(new byte[32], 300);
        Season.Player p = SEASON.join(c.commitment(), Custody.PLAYER);
        SEASON.hold("alice", p);
        AtomicInteger loads = new AtomicInteger();
        long copiesBefore = p.log().copies();
        for (int r = 0; r < 300; r++) {
            Season.Player live = SEASON.resume("alice", () -> {
                loads.incrementAndGet();
                return p.toJson();
            });
            assertSame(p, live, "ride=" + r);
            SEASON.ride(live, r % 2 == 0 ? Colour.WARM : Colour.COLD, c.opening(r));
        }
        assertEquals(0, loads.get());
        assertEquals(copiesBefore, p.log().copies());
        assertEquals(300, p.log().size());
    }

    @Test
    void anUnknownPlayerIsRestoredOnceThenKeptLive() {
        SeedChain c = new SeedChain(new byte[32], 3);
        Season.Player stored = SEASON.join(c.commitment(), Custody.SERVER);
        SEASON.ride(stored, Colour.WARM, c.opening(0));
        String json = stored.toJson();
        AtomicInteger loads = new AtomicInteger();
        Season.Player first = SEASON.resume("bob", () -> {
            loads.incrementAndGet();
            return json;
        });
        Season.Player second = SEASON.resume("bob", () -> {
            loads.incrementAndGet();
            return json;
        });
        assertSame(first, second);
        assertEquals(1, loads.get());
        assertEquals(stored.attunement(), first.attunement());
        assertSame(first, SEASON.release("bob").orElseThrow());
        assertTrue(SEASON.release("bob").isEmpty());
    }
}
