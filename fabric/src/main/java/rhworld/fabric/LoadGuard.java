package rhworld.fabric;

import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;

public final class LoadGuard {
    private LoadGuard() {}

    public static Optional<String> refusal(MinecraftServer server) {
        var stems = server.registryAccess().lookupOrThrow(Registries.LEVEL_STEM);
        for (var entry : stems.entrySet()) {
            if (!(entry.getValue().generator() instanceof HybridGenerator gen)) continue;
            String verdict = gen.keyVerdict();
            if (!verdict.equals("ok")) {
                Log.line("load", "level", entry.getKey().identifier(), "result", verdict);
                return Optional.of(verdict);
            }
        }
        return Optional.empty();
    }

    public static void check(MinecraftServer server) {
        refusal(server).ifPresent(reason -> {
            throw new IllegalStateException("rhworld " + reason);
        });
    }
}
