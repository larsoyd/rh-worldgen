package rhworld.fabric;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum Realm implements StringRepresentable {
    HEARTH("hearth"), EMBER("ember"), MIST("mist"), FROST("frost");

    public static final Codec<Realm> CODEC = StringRepresentable.fromEnum(Realm::values);

    private final String id;

    Realm(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
