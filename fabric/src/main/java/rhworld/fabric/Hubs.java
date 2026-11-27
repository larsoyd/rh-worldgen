package rhworld.fabric;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;

public final class Hubs {
    private Hubs() {}

    public static Optional<BlockPos> origin(ServerLevel level) {
        var hub = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getValue(RhWorldMod.id("hub"));
        if (hub == null) return Optional.empty();
        var start = level.getChunk(0, 0).getStartForStructure(hub);
        if (start == null || !start.isValid()) return Optional.empty();
        return start.getPieces().stream()
                .filter(p -> p instanceof HubPieces.Hub)
                .map(p -> ((HubPieces.Hub) p).origin())
                .findFirst();
    }
}
