package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A lift's picture board, a slim screen a block wide and two tall (a {@link WideScreenBlock}) by a landing's door and
 * on the back of the car's shaft there: the ship's floors stacked as she stands, the lowest at the bottom, each an icon
 * on its story's colour, the one it stands at outlined and the one the car is at lit (see the client's {@code
 * LiftBoardRenderer}). Touch a floor and its lift takes you there from this one (see {@link LiftMachineBlockEntity}).
 */
public class LiftBoardBlock extends WideScreenBlock {
    private static final MapCodec<LiftBoardBlock> CODEC = simpleCodec(LiftBoardBlock::new);
    /** How many blocks tall the board is. */
    public static final int BOARD_ROWS = 2;
    /** The margin round its floors, in blocks on its face. */
    public static final double MARGIN = 0.07;
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
        return 1;
    }

    @Override
    protected int rowsOf(BlockState state) {
        return BOARD_ROWS;
    }

    @Override
    protected BlockEntity newScreen(BlockPos pos, BlockState state) {
        return new LiftBoardBlockEntity(pos, state);
    }

    /** How tall each floor's cell is on a face this tall for a lift with so many floors, in blocks. */
    public static double cell(double height, int floors) {
        return (height - 2 * MARGIN) / Math.max(1, floors);
    }

    /** The floor whose cell is at a height on a face this tall, counted from the lowest; -1 off them. */
    public static int floorAt(double up, double height, int floors) {
        int floor = (int) Math.floor((up - MARGIN) / cell(height, floors));
        return floor >= 0 && floor < floors ? floor : -1;
    }

    /** Touching a floor is the server's to make out, where the player looks from as the ship lies. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }
        if (level instanceof ServerLevel server) {
            Touch touch = touched(state, pos, hit, SableFlight.turnToPlot(level, pos, player.getLookAngle()));
            BlockPos origin = origin(state, pos);
            if (touch != null && server.getBlockEntity(origin) instanceof LiftBoardBlockEntity board) {
                ask(server, origin, board, touch);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Asks the board's lift for the floor touched, with a tick if it takes the ride and a low note if it is busy. */
    private static void ask(ServerLevel level, BlockPos origin, LiftBoardBlockEntity board, Touch touch) {
        LiftMachineBlockEntity lift = board.lift();
        if (lift == null) {
            return;
        }
        int floor = floorAt(touch.up(), BOARD_ROWS * UPRIGHT_HEIGHT, lift.landings().size());
        if (floor < 0) {
            return;
        }
        boolean taken = lift.ask(level, board.landing(), floor);
        level.playSound(null, origin, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.5F, taken ? TOUCHED_PITCH : BUSY_PITCH);
    }
}
