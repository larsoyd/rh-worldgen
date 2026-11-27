package rhworld.fabric;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.InsideBlockEffectApplier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public final class GateBlock extends Block {
    private final Colour colour;

    public GateBlock(Colour colour, Properties properties) {
        super(properties);
        this.colour = colour;
    }

    public Colour colour() {
        return colour;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity,
            InsideBlockEffectApplier effects, boolean intersects) {
        if (entity instanceof ServerPlayer player) GateHandler.enter(player, colour);
    }

    public enum Colour { WARM, COLD }
}
