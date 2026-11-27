package rhworld.mechanics;

import java.math.BigInteger;

public record Ride(long index, Colour colour, BigInteger before, BigInteger after, Realm reported) {}
