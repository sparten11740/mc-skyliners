package dev.skyliners.turret;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The freighter's laser cannon (see {@link LaserCannonBlockEntity}): a round gunmetal mount on the crown of her top
 * dome, its block's own model, with twin barrels on a swivel over it that the client's {@code LaserCannonRenderer}
 * draws turning to its gunner's aim. Set down by hand by any seat, it works there too.
 */
public class LaserCannonBlock extends BaseEntityBlock {
    private static final MapCodec<LaserCannonBlock> CODEC = simpleCodec(LaserCannonBlock::new);
    /** Its mount, to stand on and aim at: a low drum in the middle of its block. */
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 6, 13);

    public LaserCannonBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserCannonBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != SkylinersRegistry.LASER_CANNON_ENTITY.get()) {
            return null;
        }
        return level.isClientSide ? (tickLevel, pos, tickState, cannon) -> ((LaserCannonBlockEntity) cannon).clientTick()
            : (tickLevel, pos, tickState, cannon) -> ((LaserCannonBlockEntity) cannon).serverTick((ServerLevel) tickLevel);
    }
}
