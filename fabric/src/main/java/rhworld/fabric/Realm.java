package rhworld.fabric;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.util.StringRepresentable;

public enum Realm implements StringRepresentable {
    HEARTH("hearth"), EMBER("ember"), MIST("mist"), FROST("frost");

    public static final Codec<Realm> CODEC = StringRepresentable.fromEnum(Realm::values);

    private final String id;

    Realm(String id) {
        this.id = id;
    }

    public static Realm of(rhworld.mechanics.Realm realm) {
        return valueOf(realm.name());
    }

    public static Realm byDimension(ResourceKey<Level> dimension) {
        for (Realm r : values()) if (r.dimension().equals(dimension)) return r;
        return null;
    }

    public rhworld.mechanics.Realm mechanics() {
        return rhworld.mechanics.Realm.valueOf(name());
    }

    public ResourceKey<Level> dimension() {
        return this == HEARTH ? Level.OVERWORLD
                : ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(RhWorldMod.ID, id));
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
