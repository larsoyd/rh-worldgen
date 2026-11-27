package rhworld.fabric;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

public final class ShrineBlock extends Block {
    public static final int[] PRIMES = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97};
    public static final IntegerProperty PRIME_INDEX = IntegerProperty.create("prime_index", 0, PRIMES.length - 1);

    public ShrineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(PRIME_INDEX, 0));
    }

    public static int prime(BlockState state) {
        return PRIMES[state.getValue(PRIME_INDEX)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PRIME_INDEX);
    }
}
