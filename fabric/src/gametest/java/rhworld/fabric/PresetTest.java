package rhworld.fabric;

import java.math.BigInteger;
import java.util.Map;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

public final class PresetTest {
    static final Map<String, Realm> REALMS = Map.of(
            "minecraft:overworld", Realm.HEARTH, "rhworld:ember", Realm.EMBER,
            "rhworld:mist", Realm.MIST, "rhworld:frost", Realm.FROST);

    static void checkPreset(GameTestHelper helper, String name) {
        var presets = helper.getLevel().registryAccess().lookupOrThrow(Registries.WORLD_PRESET);
        var preset = presets.getOrThrow(ResourceKey.create(Registries.WORLD_PRESET, RhWorldMod.id(name)));
        helper.assertTrue(preset.is(WorldPresetTags.NORMAL), name + " not listed in the create world screen");
        var stems = preset.value().createWorldDimensions().dimensions();
        BigInteger modulus = null;
        for (var e : REALMS.entrySet()) {
            LevelStem stem = stems.get(ResourceKey.create(Registries.LEVEL_STEM, net.minecraft.resources.Identifier.parse(e.getKey())));
            helper.assertTrue(stem != null, name + " has no " + e.getKey());
            helper.assertTrue(stem.generator() instanceof HybridGenerator, e.getKey() + " is not hybrid");
            var gen = (HybridGenerator) stem.generator();
            helper.assertValueEqual(gen.realm(), e.getValue(), e.getKey() + " realm");
            helper.assertTrue(gen.delegate() instanceof NoiseBasedChunkGenerator, e.getKey() + " delegate is not noise");
            helper.assertTrue(gen.modulus().isPresent(), e.getKey() + " has no modulus");
            if (modulus == null) modulus = gen.modulus().get();
            helper.assertValueEqual(gen.modulus().get(), modulus, e.getKey() + " modulus differs between realms");
        }
        helper.assertValueEqual(modulus.bitLength(), 1024, name + " modulus bits");
    }

    @GameTest
    public void wildPresetHasFourKeyedRealms(GameTestHelper helper) {
        checkPreset(helper, "wild");
        helper.succeed();
    }

    @GameTest
    public void riggedPresetHasFourKeyedRealms(GameTestHelper helper) {
        checkPreset(helper, "wild_rigged");
        helper.succeed();
    }
}
