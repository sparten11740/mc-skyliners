package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A lift panel: a slim digital panel on the wall by a lift's door, or on the wall of its cabin, for one of Create's
 * elevators. Across its top it shows the floor the cabin is at in big amber figures, with an arrow while it goes up or
 * down; under that it lists the lift's floors, the top one first, by the names their contacts were given, the floor the
 * cabin was last sent to lit up. Touching a floor's row sends the cabin there (see {@link Lift}). Beside a landing it
 * works the lift whose column of contacts is nearest it; aboard a cabin, Create carries it with the cabin and
 * {@link LiftPanelInteraction} takes the touch. Its renderer draws the screen live. FACING is the way the panel faces,
 * its back to whatever it is fixed to.
 */
public class LiftPanelBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<LiftPanelBlock> CODEC = simpleCodec(LiftPanelBlock::new);
    /** The panel's size in pixels of its block, across and up (centred on its face), and how thick it is. */
    public static final int WIDTH = 10;
    public static final int HEIGHT = 14;
    public static final int DEPTH = 1;
    /** The glass inside its bezel: half its width and half its height, in blocks from the middle of the face. */
    public static final double HALF_WIDTH = (WIDTH - 1) / 32.0;
    public static final double HALF_HEIGHT = (HEIGHT - 1) / 32.0;
    /** The indicator across the top of the glass, and the gap under it, in blocks. */
    public static final double INDICATOR = 0.3;
    private static final double GAP = 0.02;
    /** Where the rows of floors start under the indicator, how tall each is at most, and the margin under the last. */
    public static final double ROWS_TOP = HALF_HEIGHT - INDICATOR - GAP;
    private static final double ROW = 0.11;
    private static final double MARGIN = 0.02;
    private static final int SIDE = (16 - WIDTH) / 2;
    private static final int BOTTOM = (16 - HEIGHT) / 2;
    private static final Map<Direction, VoxelShape> SHAPES = Map.of(
        Direction.NORTH, Block.box(SIDE, BOTTOM, 16 - DEPTH, 16 - SIDE, 16 - BOTTOM, 16),
        Direction.SOUTH, Block.box(SIDE, BOTTOM, 0, 16 - SIDE, 16 - BOTTOM, DEPTH),
        Direction.WEST, Block.box(16 - DEPTH, BOTTOM, SIDE, 16, 16 - BOTTOM, 16 - SIDE),
        Direction.EAST, Block.box(0, BOTTOM, SIDE, DEPTH, 16 - BOTTOM, 16 - SIDE)
    );

    public LiftPanelBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** Against the side of a block it faces out from it; put anywhere else it faces whoever puts it there. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        return defaultBlockState().setValue(FACING, clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** How tall each row of floors is, in blocks, for a lift with this many: as tall as they all fit, up to a most. */
    public static double rowHeight(int floors) {
        return Math.min(ROW, (ROWS_TOP + HALF_HEIGHT - MARGIN) / Math.max(1, floors));
    }

    /** Which row of floors is at this height on the glass, up from its middle in blocks, counting from the top; or -1. */
    public static int row(double y, int floors) {
        int row = (int) Math.floor((ROWS_TOP - y) / rowHeight(floors));
        return row >= 0 && row < floors ? row : -1;
    }

    /** Touching the face, beside a landing: on the server, the cabin is sent to the floor touched, if any. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (hit.getDirection() != state.getValue(FACING)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            touch(level, Lift.beside(level, pos), hit.getLocation().y - pos.getY(), Vec3.atCenterOf(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * A touch on a panel's face this high up its block, wherever it is: the cabin, if there is one, is sent to the floor
     * whose row is there, with a click from the panel where it stands in the level.
     */
    static void touch(Level level, @Nullable AbstractContraptionEntity cabin, double up, Vec3 at) {
        if (cabin == null) {
            return;
        }
        List<Lift.Floor> floors = Lift.floors(cabin);
        int row = row(up - 0.5, floors.size());
        if (row >= 0 && Lift.send(level, cabin, floors.get(row))) {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.8F);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new LiftPanelBlockEntity(pos, state);
    }
}
