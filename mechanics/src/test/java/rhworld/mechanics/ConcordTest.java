package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

class ConcordTest {
    @Test
    void completeOnlyWithAllFourSigils() {
        Concord c = new Concord();
        assertTrue(c.grant(Realm.HEARTH));
        assertFalse(c.grant(Realm.HEARTH));
        assertTrue(c.grant(Realm.EMBER));
        assertTrue(c.grant(Realm.MIST));
        assertFalse(c.complete());
        assertTrue(c.grant(Realm.FROST));
        assertTrue(c.complete());
        assertEquals(EnumSet.allOf(Realm.class), c.sigils());
    }

    @Test
    void stateRoundTripsThroughJson() {
        Concord c = new Concord();
        c.grant(Realm.FROST);
        c.grant(Realm.HEARTH);
        assertEquals("{\"format\":\"rhworld.concord/1\",\"sigils\":[\"hearth\",\"frost\"]}", c.toJson());
        assertEquals(c.sigils(), Concord.fromJson(c.toJson()).sigils());
        assertThrows(IllegalArgumentException.class, () -> Concord.fromJson("{\"format\":\"x\",\"sigils\":[]}"));
    }

    @Test
    void steeredRidesFinishTheConcordOnTheRiggedKey() {
        Warden warden = Keys.sample("rigged-1024");
        WildGate gate = WildGate.forModulus(warden.modulus());
        byte[] seed = new byte[Dice.SEED_BYTES];
        seed[2] = 9;
        Concord c = new Concord();
        c.grant(warden.realm(BigInteger.ONE));
        BigInteger x = BigInteger.ONE;
        int rides = 0;
        while (!c.complete() && rides < 40) {
            Colour need = c.sigils().contains(Realm.EMBER) ? Colour.COLD : Colour.WARM;
            x = gate.ride(x, need, Dice.bits(seed, rides, gate.size()));
            c.grant(warden.realm(x));
            rides++;
        }
        System.out.println("[concord] key=rigged-1024 rides=" + rides);
        assertTrue(c.complete(), "rides=" + rides);
    }
}
