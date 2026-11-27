package rhworld.fabric;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.Optional;
import net.fabricmc.loader.api.FabricLoader;
import rhworld.mechanics.Warden;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;

public final class LoadGuard {
    private LoadGuard() {}

    public static Optional<String> refusal(MinecraftServer server) {
        Session.set(null);
        var stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        var moduli = new HashSet<BigInteger>();
        for (var entry : stems.entrySet()) {
            if (!(entry.getValue().generator() instanceof HybridGenerator gen)) continue;
            String verdict = gen.keyVerdict();
            if (!verdict.equals("ok")) {
                Log.line("load", "level", entry.getKey().identifier(), "result", verdict);
                return Optional.of(verdict);
            }
            moduli.add(gen.modulus().orElseThrow());
        }
        if (moduli.isEmpty()) return Optional.empty();
        String key = OperatorKey.load(FabricLoader.getInstance().getConfigDir().resolve("rhworld").resolve("key.json"), moduli);
        Log.line("load", "realms", moduli.size(), "operator_key", OperatorKey.current().isPresent(), "result", key);
        if (!key.equals("ok")) return Optional.of(key);
        try {
            var warden = OperatorKey.current().map(k -> new Warden(k.p(), k.q()));
            var session = Session.create(moduli.iterator().next(), warden);
            Session.set(session);
            Log.line("session", "bits", session.n().bitLength(), "gate_primes", session.gate().size(), "witness", session.gate().witness());
        } catch (IllegalArgumentException | IllegalStateException e) {
            String m = String.valueOf(e.getMessage());
            Log.line("load", "result", m);
            return Optional.of(m.startsWith("refused:") ? m : "refused: " + m);
        }
        return Optional.empty();
    }

    public static void check(MinecraftServer server) {
        refusal(server).ifPresent(reason -> {
            throw new IllegalStateException("rhworld " + reason);
        });
    }
}
