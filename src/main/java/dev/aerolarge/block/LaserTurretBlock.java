package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The laser path clearer's turret, in the underside of the nose: the belly's skin with a hatch in it, behind which the
 * turret waits; armed from the flight deck, it drops out under the nose and fires along the airliner's flight path (see
 * {@link LaserTurretBlockEntity}). FACING is the way the nose points. PART is what the state looks like (see
 * {@link Part}): only the hatch is ever placed.
 */
public class LaserTurretBlock extends BaseEntityBlock {
    private static final MapCodec<LaserTurretBlock> CODEC = simpleCodec(LaserTurretBlock::new);
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);

    /**
     * What a state of the turret looks like: the belly's skin with its hatch, the block as placed, or one of the two
     * parts of the turret out of its hatch, which its renderer draws below the block, turned toward where it fires:
     * its head, a housing on a stalk that swings round, and its barrel, which swings with the head and tilts up and
     * down. Blocks saved before the turret turned have a gun property instead, always false: it is dropped as they
     * load, and they come back as the hatch.
     */
    public enum Part implements StringRepresentable {
        HATCH,
        HEAD,
        BARREL;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public LaserTurretBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.HATCH));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** Put down by hand, the nose is the way whoever puts it down faces, so it fires that way. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.setValue(FACING, mirror.mirror(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserTurretBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
            ? createTickerHelper(type, AerolargeRegistry.LASER_TURRET_ENTITY.get(), LaserTurretBlockEntity::clientTick)
            : createTickerHelper(type, AerolargeRegistry.LASER_TURRET_ENTITY.get(), LaserTurretBlockEntity::serverTick);
    }
}
