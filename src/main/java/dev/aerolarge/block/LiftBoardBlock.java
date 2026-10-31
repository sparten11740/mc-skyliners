package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A lift's picture board, a slim screen a block wide and two tall (a {@link WideScreenBlock}) by a landing's door and
 * on the back of the car's shaft there: the ship's floors as buttons two to a row, the lowest at the bottom, each an
 * icon on its story's colour, the one it stands at outlined and the one the car is at lit (see the client's {@code
 * LiftBoardRenderer}). Touch the board outside the doors and the car comes and opens for you; touch a floor on the
 * board inside it and it takes you there (see {@link LiftMachineBlockEntity}). Whichever the player looks at lights up.
 */
public class LiftBoardBlock extends WideScreenBlock implements TouchPanelBlock {
    private static final MapCodec<LiftBoardBlock> CODEC = simpleCodec(LiftBoardBlock::new);
    /** How many blocks wide and tall the board is. */
    public static final int BOARD_COLUMNS = 1;
    public static final int BOARD_ROWS = 2;
    /** The margin round its floors, in blocks on its face. */
    public static final double MARGIN = 0.07;
    /** How many floors' buttons stand side by side in a row: two, as a lift's buttons do, so each is nearly square. */
    public static final int ROW_BUTTONS = 2;
    /**
     * The board's face at its rows' scale, as its renderer draws it (see the client's WideScreenFace): a row tall and
     * as wide as its columns are for its rows. Its buttons are laid out on it in these units, which the touch is turned
     * into.
     */
    public static final double FACE_WIDTH = (double) BOARD_COLUMNS / BOARD_ROWS;
    public static final double FACE_HEIGHT = UPRIGHT_HEIGHT;
    /** The pitch of the board's tick as a floor is touched, and of its low note when the lift is busy. */
    private static final float TOUCHED_PITCH = 1.6F;
    private static final float BUSY_PITCH = 0.6F;

    public LiftBoardBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected int columnsOf(BlockState state) {
        return BOARD_COLUMNS;
    }

    @Override
    protected int rowsOf(BlockState state) {
        return BOARD_ROWS;
    }

    @Override
    protected BlockEntity newScreen(BlockPos pos, BlockState state) {
        return new LiftBoardBlockEntity(pos, state);
    }

    /**
     * Its floors' buttons on a face this wide and tall, for a lift with so many floors (see {@link TouchPanel}):
     * {@link #ROW_BUTTONS} to a row, the lowest floors at the bottom, the first of each row on the left as the board is
     * seen; a last floor alone in its row has the whole of it, as the cockpit at the top of a small rocket does.
     */
    public static TouchPanel.Layout buttons(int floors, double width, double height) {
        return TouchPanel.Layout.grid(floors, ROW_BUTTONS, new TouchPanel.Tile(MARGIN, MARGIN, width - MARGIN, height - MARGIN), false);
    }

    /**
     * The floor a hit on the board picks, as touching it there would: inside the car the floor whose button it lands
     * on, outside the doors the board's own floor wherever on the board it lands, the lift it calls there; -1 off the
     * buttons inside, off the face, or with no lift.
     */
    @Override
    public BlockPos panelOrigin(BlockState state, BlockPos pos) {
        return origin(state, pos);
    }

    @Override
    public int tileAt(BlockGetter level, BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look) {
        BlockPos origin = origin(state, pos);
        Touch touch = touched(state, pos, hit, look);
        if (touch == null || !(level.getBlockEntity(origin) instanceof LiftBoardBlockEntity board) || board.lift() == null) {
            return -1;
        }
        LiftMachineBlockEntity lift = board.lift();
        if (!lift.inside(origin, board.landing())) {
            return board.landing();
        }
        return buttons(lift.landings().size(), FACE_WIDTH, FACE_HEIGHT).at(touch.across() / BOARD_ROWS, touch.up() / BOARD_ROWS);
    }

    /** Touching a floor is the server's to make out, where the player looks from as the ship lies. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            int floor = tileAt(level, state, pos, hit, SableFlight.turnToPlot(level, pos, player.getLookAngle()));
            BlockPos origin = origin(state, pos);
            if (floor >= 0 && server.getBlockEntity(origin) instanceof LiftBoardBlockEntity board) {
                ask(server, origin, board, floor);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Asks the board's lift for the floor touched (see {@link #tileAt}), with a tick if it takes the touch and a low
     * note if it is busy: from outside the doors, to come and open for whoever is waiting; from inside the car, to go
     * to the floor touched.
     */
    private static void ask(ServerLevel level, BlockPos origin, LiftBoardBlockEntity board, int floor) {
        LiftMachineBlockEntity lift = board.lift();
        if (lift == null) {
            return;
        }
        boolean inside = lift.inside(origin, board.landing());
        boolean taken = inside ? lift.ask(level, board.landing(), floor) : lift.call(level, board.landing());
        level.playSound(null, origin, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, taken ? TOUCHED_PITCH : BUSY_PITCH);
    }
}
