package rhworld.fabric;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import rhworld.engine.contract.Verdict;

public final class LoadGuard {
    private LoadGuard() {}

    public static Optional<String> refusal(MinecraftServer server) {
        Session.set(null);
        var stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        var moduli = new HashSet<BigInteger>();
        for (var entry : stems.entrySet()) {
            if (!(entry.getValue().generator() instanceof HybridGenerator gen)) continue;
            String verdict = gen.keyVerdict();
            if (!verdict.equals("ok")) return refuse(verdict, "level", entry.getKey().identifier());
            moduli.add(gen.modulus().orElseThrow());
        }
        if (moduli.isEmpty()) return Optional.empty();
        if (moduli.size() > 1) return refuse("refused: realms disagree on the modulus", "realms", moduli.size());
        var key = OperatorKey.read(keyFile());
        if (key.refusal().isPresent()) return refuse(key.refusal().get(), "key_file", true);
        long start = System.nanoTime();
        var opened = Session.open(moduli.iterator().next(), key.factors());
        if (opened instanceof Verdict.Ok<Session> ok) {
            Session.set(ok.value());
            Log.line("load", "bits", ok.value().world().bits(), "world_id", ok.value().worldId(),
                    "operator_key", ok.value().season().isPresent(), "ms", (System.nanoTime() - start) / 1_000_000, "result", "ok");
            return Optional.empty();
        }
        return refuse(opened.toString(), "operator_key", key.factors().isPresent());
    }

    static Optional<String> refuse(String reason, Object... kv) {
        var fields = new Object[kv.length + 2];
        System.arraycopy(kv, 0, fields, 0, kv.length);
        fields[kv.length] = "result";
        fields[kv.length + 1] = reason;
        Log.line("load", fields);
        return Optional.of(reason);
    }

    static java.nio.file.Path keyFile() {
        String override = System.getProperty("rhworld.key");
        return override != null ? java.nio.file.Path.of(override)
                : FabricLoader.getInstance().getConfigDir().resolve("rhworld").resolve("key.json");
    }

    public static void check(MinecraftServer server) {
        refusal(server).ifPresent(reason -> {
            throw new IllegalStateException("rhworld " + reason);
        });
    }
}
