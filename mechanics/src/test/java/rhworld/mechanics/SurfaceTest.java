package rhworld.mechanics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SurfaceTest {
    private static final Set<String> PUBLIC_CLASSES = Set.of("Audit", "Colour", "Concord", "Custody", "Dice", "Launcher", "Realm", "Replay",
            "Ride", "RideLog", "Season", "SeedChain", "WildGate");

    @Test
    void onlyTheSeasonFacingTypesArePublic() throws IOException, URISyntaxException, ClassNotFoundException {
        Path dir = Path.of(Season.class.getResource("Season.class").toURI()).getParent();
        Set<String> found = new TreeSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.toList()) {
                String name = f.getFileName().toString();
                if (!name.endsWith(".class") || name.contains("$")) continue;
                Class<?> c = Class.forName("rhworld.mechanics." + name.replace(".class", ""));
                if (Modifier.isPublic(c.getModifiers()) && !name.endsWith("Test.class")) found.add(c.getSimpleName());
            }
        }
        assertEquals(new TreeSet<>(PUBLIC_CLASSES), found);
    }

    @Test
    void theHiddenHalfIsReachableOnlyThroughSeason() {
        Set<String> seasonCtors = new TreeSet<>();
        for (Constructor<?> c : Season.class.getConstructors()) seasonCtors.add(c.getParameterTypes()[0].getSimpleName());
        assertEquals(Set.of("GmWorld"), seasonCtors);
        Set<String> gateStatics = new TreeSet<>();
        for (Method m : WildGate.class.getMethods()) if (Modifier.isStatic(m.getModifiers()) && m.getDeclaringClass() == WildGate.class) gateStatics.add(m.getName());
        assertEquals(Set.of("of"), gateStatics);
        for (Method m : Audit.class.getMethods())
            for (Class<?> p : m.getParameterTypes()) assertEquals(false, p == Warden.class, "public method takes the key " + m);
    }
}
