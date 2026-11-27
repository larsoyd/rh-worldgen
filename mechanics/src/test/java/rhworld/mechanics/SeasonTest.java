package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import rhworld.engine.arith.Arith;

class SeasonTest {
    private static final Warden RIGGED = Keys.sample("rigged-1024");
    private static final Season SEASON = new Season(RIGGED);

    private static SeedChain chain(int tag) {
        byte[] top = new byte[Dice.SEED_BYTES];
        top[3] = (byte) tag;
        return new SeedChain(top, 64);
    }

    private static Season.Player play(SeedChain c, int rides) {
        Season.Player p = SEASON.join(c.commitment(), Custody.PLAYER);
        for (int r = 0; r < rides; r++) SEASON.ride(p, r % 3 == 0 ? Colour.COLD : Colour.WARM, c.opening(r));
        return p;
    }

    @Test
    void newPlayerStartsInHearth() {
        Season.Player p = SEASON.join(chain(1).commitment(), Custody.PLAYER);
        assertEquals(BigInteger.ONE, p.attunement());
        assertEquals(Realm.HEARTH, SEASON.realm(p));
    }

    @Test
    void ridesKeepTheRealmInvariant() {
        SeedChain c = chain(2);
        Season.Player p = SEASON.join(c.commitment(), Custody.PLAYER);
        for (int r = 0; r < 30; r++) {
            Colour colour = r % 3 == 0 ? Colour.COLD : Colour.WARM;
            Ride ride = SEASON.ride(p, colour, c.opening(r));
            assertEquals(r, ride.index());
            assertEquals(ride.after(), p.attunement());
            assertEquals(ride.reported(), SEASON.realm(p), "ride=" + r);
            assertEquals(colour, ride.reported().colour());
            assertEquals(colour, Colour.ofJacobi(Arith.jacobi(p.attunement(), SEASON.modulus())));
        }
        assertEquals(30, p.log().rides().size());
    }

    @Test
    void wrongOpeningIsRefusedAndChangesNothing() {
        SeedChain c = chain(3);
        Season.Player p = SEASON.join(c.commitment(), Custody.PLAYER);
        SEASON.ride(p, Colour.WARM, c.opening(0));
        BigInteger before = p.attunement();
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> SEASON.ride(p, Colour.WARM, c.opening(2)));
        assertEquals("refused: opening does not follow the chain index=1", e.getMessage());
        assertEquals(before, p.attunement());
        assertEquals(1, p.log().rides().size());
    }

    @Test
    void honestLogReplaysFromThePublicKeyAlone() {
        Season.Player p = play(chain(4), 25);
        assertEquals(Optional.empty(), Replay.check(p.log()));
        assertEquals(Optional.empty(), Replay.check(RideLog.fromJson(p.log().toJson())));
    }

    @Test
    void replayNamesAForgedArrival() {
        Season.Player p = play(chain(5), 10);
        RideLog forged = new RideLog(p.log().modulus(), p.log().commitment(), Custody.PLAYER);
        for (Ride r : p.log().rides()) {
            if (r.index() == 6) {
                BigInteger fake = r.after().multiply(BigInteger.valueOf(4)).mod(SEASON.modulus());
                forged.add(new Ride(r.index(), r.colour(), r.opening(), r.before(), fake, r.reported()));
                break;
            }
            forged.add(r);
        }
        assertEquals(Optional.of("refused: ride after differs from the replay index=6"), Replay.check(forged));
    }

    @Test
    void replayNamesAReportOfTheWrongColour() {
        Season.Player p = play(chain(6), 4);
        RideLog forged = new RideLog(p.log().modulus(), p.log().commitment(), Custody.PLAYER);
        for (Ride r : p.log().rides()) {
            Realm shown = r.index() == 2 ? r.reported().plus(Realm.MIST) : r.reported();
            forged.add(new Ride(r.index(), r.colour(), r.opening(), r.before(), r.after(), shown));
        }
        assertEquals(Optional.of("refused: reported realm has the wrong colour index=2"), Replay.check(forged));
    }

    @Test
    void sigilsAreCollectedInTheCurrentRealm() {
        SeedChain c = chain(7);
        Season.Player p = SEASON.join(c.commitment(), Custody.PLAYER);
        assertTrue(SEASON.collect(p));
        assertFalse(SEASON.collect(p));
        int r = 0;
        while (!p.concord().complete() && r < 40) {
            Colour need = p.concord().sigils().contains(Realm.EMBER) ? Colour.COLD : Colour.WARM;
            SEASON.ride(p, need, c.opening(r++));
            SEASON.collect(p);
        }
        assertTrue(p.concord().complete(), "rides=" + r);
    }

    @Test
    void restoredPlayerContinuesTheChain() {
        SeedChain c = chain(8);
        Season.Player p = play(c, 5);
        SEASON.collect(p);
        Season.Player back = SEASON.restore(p.toJson());
        assertEquals(p.attunement(), back.attunement());
        assertEquals(p.log().rides(), back.log().rides());
        assertEquals(p.concord().sigils(), back.concord().sigils());
        assertEquals(p.toJson(), back.toJson());
        SEASON.ride(back, Colour.WARM, c.opening(5));
        assertEquals(6, back.log().rides().size());
    }

    @Test
    void serverHeldDiceAreMarkedThroughRestoreAndAudit() {
        SeedChain c = chain(10);
        Season.Player p = SEASON.join(c.commitment(), Custody.SERVER);
        for (int r = 0; r < 4; r++) SEASON.ride(p, Colour.WARM, c.opening(r));
        Season.Player back = SEASON.restore(p.toJson());
        assertEquals(Custody.SERVER, back.log().custody());
        assertTrue(back.log().toJson().contains("\"dice\":\"server-held\""), back.log().toJson());
        assertTrue(Audit.of(back.log(), 0x1p-30).line().endsWith(" dice=server-held condition8=not_met"));
    }

    @Test
    void restoreRefusesAnotherKeyOrABrokenState() {
        Season.Player p = play(chain(9), 3);
        Season other = new Season(Keys.sample("random-1024"));
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> other.restore(p.toJson()));
        assertEquals("refused: player state belongs to another key", e.getMessage());
        String json = p.toJson().replace("\"attunement\":\"" + p.attunement(), "\"attunement\":\"" + p.attunement().add(BigInteger.TWO));
        e = assertThrows(IllegalArgumentException.class, () -> SEASON.restore(json));
        assertEquals("refused: attunement is not where the last ride ended", e.getMessage());
    }
}
