package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The autopilot screen on the flight deck: a big touch screen, {@link #COLUMNS} blocks wide and {@link #ROWS} tall,
 * with a moving map and the autopilot's buttons (see {@link AutopilotScreenBlockEntity}). Every block of it is this
 * block; PART says which, counting along the bottom row from the viewer's left and then up, and part 0 holds the
 * screen's block entity and draws the whole of it. FACING is the way the screen faces, toward the pilots. Using any
 * part touches the screen where it was used: the map zooms, anywhere else opens the autopilot's settings.
 */
public class AutopilotScreenBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<AutopilotScreenBlock> CODEC = simpleCodec(AutopilotScreenBlock::new);
    public static final int COLUMNS = 3;
    public static final int ROWS = 2;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, COLUMNS * ROWS - 1);

    public AutopilotScreenBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(PART, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    /** The viewer's right, looking at the screen. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    /** Where the part holding the screen is, from any part. */
    public static BlockPos origin(BlockState state, BlockPos pos) {
        int part = state.getValue(PART);
        return pos.relative(right(state.getValue(FACING)), -(part % COLUMNS)).below(part / COLUMNS);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            BlockPos origin = origin(state, pos);
            if (level.getBlockEntity(origin) instanceof AutopilotScreenBlockEntity screen) {
                // Where on the screen it was touched, in blocks from its bottom left corner as the viewer sees it.
                Vec3 right = Vec3.atLowerCornerOf(right(state.getValue(FACING)).getNormal());
                Vec3 from = Vec3.atCenterOf(origin);
                Vec3 touch = hit.getLocation().subtract(from);
                double across = touch.x * right.x + touch.z * right.z + 0.5;
                double up = touch.y + 0.5;
                screen.touch(across, up, serverPlayer);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new AutopilotScreenBlockEntity(pos, state) : null;
    }
}
