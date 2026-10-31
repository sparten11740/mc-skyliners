package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The autopilot screen on the flight deck: a big touch screen, {@link #COLUMNS} blocks wide and {@link #ROWS} tall and
 * as slim as the tail camera display, standing against the back of its blocks, with a moving map and the autopilot's
 * buttons (see {@link AutopilotScreenBlockEntity}). Every block of it is this block; PART says which, counting along
 * the bottom row from the viewer's left and then up, and part 0 holds the screen's block entity and draws the whole of
 * it. FACING is the way the screen faces, toward the pilots. Using any part touches the screen where it was used: the
 * map zooms, anywhere else opens the autopilot's settings. It is a touch panel of those two tiles (see
 * {@link TouchPanel}), so whichever the crosshair rests on lights up as on every other panel.
 */
public class AutopilotScreenBlock extends HorizontalDirectionalBlock implements EntityBlock, TouchPanelBlock {
    private static final MapCodec<AutopilotScreenBlock> CODEC = simpleCodec(AutopilotScreenBlock::new);
    public static final int COLUMNS = 3;
    public static final int ROWS = 2;
    /** Its tiles: the moving map, which a touch zooms, and the rest of its face, where a touch opens the settings. */
    public static final int MAP_TILE = 0;
    public static final int SETTINGS_TILE = 1;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, COLUMNS * ROWS - 1);
    /**
     * How thick the screen is, in pixels of its block, against the back of it: as thick as the tail camera display's
     * upright screen, so that one stood on top of it makes a single sheet of glass with it.
     */
    public static final int DEPTH = TailCameraDisplayBlock.DEPTH;
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            SHAPES.put(facing, TailCameraDisplayBlock.againstBack(facing, 0, 16, DEPTH));
        }
    }

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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
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

    /**
     * The tile a hit on the screen's face lands on, measured from the bottom left corner of the whole screen: the map
     * where it is drawn (see {@link AutopilotScreenBlockEntity#MAP}), else the rest of the face; -1 off its face.
     */
    @Override
    public int tileAt(BlockGetter level, BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look) {
        WideScreenBlock.Touch touch = TouchPanel.touched(state.getValue(FACING), origin(state, pos), hit);
        if (touch == null) {
            return -1;
        }
        return AutopilotScreenBlockEntity.MAP.contains(touch.across(), touch.up()) ? MAP_TILE : SETTINGS_TILE;
    }

    @Override
    public BlockPos panelOrigin(BlockState state, BlockPos pos) {
        return origin(state, pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        int tile = tileAt(level, state, pos, hit, player.getLookAngle());
        if (tile < 0) {
            return InteractionResult.PASS;
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(origin(state, pos)) instanceof AutopilotScreenBlockEntity screen) {
            screen.touch(tile, serverPlayer);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? new AutopilotScreenBlockEntity(pos, state) : null;
    }
}
