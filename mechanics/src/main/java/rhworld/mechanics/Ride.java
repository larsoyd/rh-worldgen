package rhworld.mechanics;

import java.math.BigInteger;

public record Ride(long index, Colour colour, String opening, BigInteger before, BigInteger after, Realm reported) {}
