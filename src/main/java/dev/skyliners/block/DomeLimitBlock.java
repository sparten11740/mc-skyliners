package dev.skyliners.block;

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
 * The limit of the rocket's planetarium: an invisible block filling the gaps between the smooth sky the clients draw
 * inside her dome and the steps of its lining behind it, so nobody can climb or fly through the sky to the wall behind.
 * Nothing to see and nothing to aim at, so clicks go through to whatever is behind it, and it gives way to anything
 * built there; the sky then draws itself in front of that as usual. The sky takes no notice of it, as it takes none of
 * anything invisible. Only the blueprint places it.
 */
public class DomeLimitBlock extends Block {
    private static final MapCodec<DomeLimitBlock> CODEC = simpleCodec(DomeLimitBlock::new);

    public DomeLimitBlock(Properties properties) {
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}
