package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A mail sack, as one parachuted from a zeppelin comes to rest (see {@link dev.aerolarge.item.MailSackItem}): a
 * canvas sack tied at the neck, a red band of the post round it. It can be picked up and set down again.
 */
public class MailSackBlock extends Block {
    private static final MapCodec<MailSackBlock> CODEC = simpleCodec(MailSackBlock::new);
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 12, 13);

    public MailSackBlock(Properties properties) {
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
}
