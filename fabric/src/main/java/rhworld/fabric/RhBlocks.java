package rhworld.fabric;

import java.util.function.Function;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import rhworld.mechanics.Colour;

public final class RhBlocks {
    public static final Block HUB_STONE = register("hub_stone", Block::new, BlockBehaviour.Properties.of().strength(-1f, 3600000f));
    public static final Block GATE_WARM = register("gate_warm", p -> new GateBlock(Colour.WARM, p), gate());
    public static final Block GATE_COLD = register("gate_cold", p -> new GateBlock(Colour.COLD, p), gate());
    public static final Block SIGIL = register("sigil", SigilBlock::new, BlockBehaviour.Properties.of().strength(-1f, 3600000f).lightLevel(s -> 10));
    public static final Block SHRINE = register("shrine", ShrineBlock::new, BlockBehaviour.Properties.of().strength(-1f, 3600000f).noCollision().lightLevel(s -> 7));

    private RhBlocks() {}

    static void init() {}

    private static BlockBehaviour.Properties gate() {
        return BlockBehaviour.Properties.of().strength(-1f, 3600000f).noCollision().noLootTable().lightLevel(s -> 11);
    }

    private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {
        var key = ResourceKey.create(Registries.BLOCK, RhWorldMod.id(name));
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(props.setId(key)));
    }
}
