package rhworld.mechanics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import rhworld.engine.json.Json;

public final class Concord {
    public static final String FORMAT = "rhworld.concord/1";

    private final EnumSet<Realm> sigils = EnumSet.noneOf(Realm.class);

    public boolean grant(Realm realm) {
        return sigils.add(realm);
    }

    public Set<Realm> sigils() {
        return sigils.isEmpty() ? EnumSet.noneOf(Realm.class) : EnumSet.copyOf(sigils);
    }

    public boolean complete() {
        return sigils.size() == Realm.values().length;
    }

    public String toJson() {
        return Json.write(toMap());
    }

    Map<String, Object> toMap() {
        List<String> names = new ArrayList<>();
        for (Realm r : sigils) names.add(r.name().toLowerCase(Locale.ROOT));
        return Map.of("format", FORMAT, "sigils", names);
    }

    public static Concord fromJson(String text) {
        return fromMap((Map<?, ?>) Json.parse(text));
    }

    static Concord fromMap(Map<?, ?> root) {
        if (!FORMAT.equals(root.get("format"))) throw new IllegalArgumentException("refused: unknown concord format=" + root.get("format"));
        Concord c = new Concord();
        for (Object name : (List<?>) root.get("sigils")) c.grant(Realm.valueOf(((String) name).toUpperCase(Locale.ROOT)));
        return c;
    }
}
