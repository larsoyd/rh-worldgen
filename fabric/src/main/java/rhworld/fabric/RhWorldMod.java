package rhworld.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;

public final class RhWorldMod implements ModInitializer {
    public static final String ID = "rhworld";
    public static final StructureType<HubStructure> HUB_TYPE = () -> HubStructure.CODEC;
    public static final StructurePieceType HUB_PIECE = HubPieces.Hub::new;
    public static final StructurePieceType SHRINE_PIECE = HubPieces.Shrine::new;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("hybrid"), HybridGenerator.CODEC);
        RhBlocks.init();
        PlayerData.init();
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE, id("hub"), HUB_TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_PIECE, id("hub"), HUB_PIECE);
        Registry.register(BuiltInRegistries.STRUCTURE_PIECE, id("shrine"), SHRINE_PIECE);
        Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, id("origin"), OriginPlacement.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(LoadGuard::check);
        ServerPlayerEvents.AFTER_RESPAWN.register(Lifecycle::afterRespawn);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Lifecycle.join(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PlayerData.release(handler.player));
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, env) -> RhCommands.register(dispatcher));
    }
}
