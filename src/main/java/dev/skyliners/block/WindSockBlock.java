package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * An airfield's wind sock: a short steel pole with a collar at its top, and from the collar an orange-and-white
 * striped sock that flies with the wind (see {@link dev.skyliners.airport.Wind}): pointing the way it blows, hanging
 * limp when it is calm, half filled in rain and straight out in a storm, fluttering as it goes. The block is only the
 * pole; the sock is drawn on the client (see {@code WindSockRenderer}).
 */
public class WindSockBlock extends Block implements EntityBlock {
    private static final MapCodec<WindSockBlock> CODEC = simpleCodec(WindSockBlock::new);
    /** Where the sock hangs from, in blocks up from the bottom of the block, in the middle across. */
    public static final double HOOK = 14 / 16.0;
    /** The pole, and the collar round its top. */
    private static final VoxelShape SHAPE = Block.box(7, 0, 7, 9, 16, 9);

    public WindSockBlock(Properties properties) {
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

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindSockBlockEntity(pos, state);
    }
}
