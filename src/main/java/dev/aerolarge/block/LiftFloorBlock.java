package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The floor of a lift's car where it stands at a landing (see {@link LiftMachineBlockEntity}): laid in the shaft's
 * opening under the car as it comes in, and taken away as it sets off, so whoever is in the car stands and walks about
 * on it freely while it stands at a floor, and walks out when they like. It is not drawn, the car drawing its own floor
 * over it, and cannot be picked out or broken; only the lift lays it, so it has no item and gives nothing back.
 */
public class LiftFloorBlock extends Block {
    private static final MapCodec<LiftFloorBlock> CODEC = simpleCodec(LiftFloorBlock::new);

    public LiftFloorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to pick out: what is aimed at through it is what lies past it. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}
