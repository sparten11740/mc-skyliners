package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.AerolargeRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The laser path clearer's switch, on the flight deck's centre console: a big button under a flip-up guard, a red laser
 * beam pictured beside it, the button glowing red while the armed laser clears the way and amber while, low down with
 * the engines idle, it holds its fire at blocks and shoots only hostile mobs. Use it to arm the laser, its turret
 * dropping out under the nose with a rising whine, and again to disarm it (see {@link LaserSwitchBlockEntity}). FACING
 * is the way its face looks, toward the pilots.
 */
public class LaserSwitchBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<LaserSwitchBlock> CODEC = simpleCodec(LaserSwitchBlock::new);
    public static final BooleanProperty ARMED = BooleanProperty.create("armed");
    /** Whether the armed laser, low down, holds its fire at blocks: the button glows amber rather than red. */
    public static final BooleanProperty HOLDING = BooleanProperty.create("holding");
    /** A module of the console with the button and its guard on it. */
    private static final VoxelShape SHAPE = ConsoleModule.with(3, 3);

    public LaserSwitchBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(ARMED, false).setValue(HOLDING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ARMED, HOLDING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof LaserSwitchBlockEntity laser) {
            laser.press(serverLevel, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LaserSwitchBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide || type != AerolargeRegistry.LASER_SWITCH_ENTITY.get() ? null : LaserSwitchBlock::tick;
    }

    private static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
        if (blockEntity instanceof LaserSwitchBlockEntity laser) {
            laser.tick();
        }
    }
}
