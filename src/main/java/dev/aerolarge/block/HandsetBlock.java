package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.radio.Broadcasts;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The captain's handset on the flight deck: a cradle with the receiver in it. Picked up, the next line is a cabin
 * announcement; picked up while sneaking, a radio call to the tower.
 */
public class HandsetBlock extends RadioBlock {
    private static final MapCodec<HandsetBlock> CODEC = simpleCodec(HandsetBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 5, 13);

    public HandsetBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected Broadcasts.Kind kind(Player player) {
        return player.isShiftKeyDown() ? Broadcasts.Kind.RADIO : Broadcasts.Kind.ANNOUNCEMENT;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
