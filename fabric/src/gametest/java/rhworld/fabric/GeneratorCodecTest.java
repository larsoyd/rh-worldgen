package rhworld.fabric;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.math.BigInteger;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.chunk.ChunkGenerator;

public final class GeneratorCodecTest {
    static final String DELEGATE = """
            {"type":"minecraft:noise","biome_source":{"type":"minecraft:fixed","biome":"minecraft:desert"},"settings":"minecraft:overworld"}""";

    static ChunkGenerator decode(GameTestHelper helper, String extra) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var json = JsonParser.parseString("{\"type\":\"rhworld:hybrid\"," + extra + "\"delegate\":" + DELEGATE + "}");
        return ChunkGenerator.CODEC.parse(ops, json).getOrThrow(m -> new AssertionError("decode failed: " + m));
    }

    @GameTest
    public void modulusSurvivesRoundTrip(GameTestHelper helper) {
        var gen = (HybridGenerator) decode(helper, "\"realm\":\"ember\",\"modulus\":\"1000000000000000000000000000007\",");
        helper.assertValueEqual(gen.modulus().orElseThrow(), new BigInteger("1000000000000000000000000000007"), "modulus");
        helper.assertValueEqual(gen.realm(), Realm.EMBER, "realm");
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var again = (HybridGenerator) ChunkGenerator.CODEC.parse(ops,
                ChunkGenerator.CODEC.encodeStart(ops, gen).getOrThrow()).getOrThrow();
        helper.assertValueEqual(again.modulus(), gen.modulus(), "modulus after round trip");
        helper.assertValueEqual(again.realm(), gen.realm(), "realm after round trip");
        helper.succeed();
    }

    @GameTest
    public void missingModulusIsRefused(GameTestHelper helper) {
        var gen = (HybridGenerator) decode(helper, "\"realm\":\"hearth\",");
        helper.assertValueEqual(gen.modulus().isPresent(), false, "modulus present");
        helper.assertValueEqual(gen.keyVerdict(), "refused: no modulus", "verdict");
        helper.succeed();
    }

    @GameTest
    public void unknownRealmDoesNotDecode(GameTestHelper helper) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        var json = JsonParser.parseString("{\"type\":\"rhworld:hybrid\",\"realm\":\"void\",\"delegate\":" + DELEGATE + "}");
        helper.assertTrue(ChunkGenerator.CODEC.parse(ops, json).isError(), "realm void decoded");
        helper.succeed();
    }
}
