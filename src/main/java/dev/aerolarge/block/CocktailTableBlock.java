package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A high round cocktail table to stand at in the club, as high as the bar counter so that it goes with the bar stools:
 * a round top of dark glass, a ring of LED light glowing under its rim whatever the light around, on a chrome pole over
 * a round foot. It is round, so it has no front. What is looked at and clicked is the table itself; what stops a player
 * is a column round its top as high as a fence, so that whoever stands at it stands against its rim, and nobody climbs
 * on to it from a stool to walk across the room over the tables.
 */
public class CocktailTableBlock extends Block {
    private static final MapCodec<CocktailTableBlock> CODEC = simpleCodec(CocktailTableBlock::new);
    /** The round top, its LED ring, the pole and the foot. */
    private static final VoxelShape SHAPE = Shapes.or(
        Block.box(1, 14.5, 3, 15, 16, 13),
        Block.box(3, 14.5, 1, 13, 16, 15),
        Block.box(7, 1, 7, 9, 14.5, 9),
        Block.box(4, 0, 4, 12, 1, 12)
    );
    /** A column round the top, as high as a fence (a block and a half), from the floor up. */
    private static final VoxelShape COLLISION = Shapes.or(
        Block.box(1, 0, 3, 15, 24, 13),
        Block.box(3, 0, 1, 13, 24, 15)
    );

    public CocktailTableBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION;
    }
}
