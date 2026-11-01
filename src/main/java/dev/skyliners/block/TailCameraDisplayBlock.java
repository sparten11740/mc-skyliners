package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The camera display on the flight deck, like an A380's taxiing camera screen: a wide screen (see
 * {@link WideScreenBlock}) for the picture from one of the airliner's outside cameras, the one on top of the fin
 * looking forward over the whole airliner at first. A zeppelin has a larger one, over the livery board in her control
 * car, showing her own cameras, her flyby camera at first, drawn as the small one is at twice its size. A row of
 * buttons along its bottom left picks the camera (see {@link CameraButtons}), the tiles of a touch panel (see {@link
 * TouchPanelBlock}), so the one the crosshair rests on lights up; its renderer draws it all, upright or hanging tilted
 * from the crown.
 */
public class TailCameraDisplayBlock extends WideScreenBlock implements TouchPanelBlock {
    private static final MapCodec<TailCameraDisplayBlock> CODEC = simpleCodec(TailCameraDisplayBlock::new);
    /**
     * Whether it is the large display, {@link #LARGE_COLUMNS} blocks wide and {@link #LARGE_ROWS} tall, as a zeppelin's
     * control car has, rather than the flight deck's three by one. Only an airframe's build puts one up, always
     * upright; put up by hand, a display is the small one.
     */
    public static final BooleanProperty LARGE = BooleanProperty.create("large");
    public static final int LARGE_COLUMNS = 4;
    public static final int LARGE_ROWS = 2;
    /** How loud a camera's button clicks as it is touched, and how high. */
    private static final float CLICK_VOLUME = 0.4F;
    private static final float CLICK_PITCH = 1.6F;

    public TailCameraDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LARGE, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LARGE);
    }

    @Override
    protected int columnsOf(BlockState state) {
        return state.getValue(LARGE) ? LARGE_COLUMNS : COLUMNS;
    }

    @Override
    protected int rowsOf(BlockState state) {
        return state.getValue(LARGE) ? LARGE_ROWS : ROWS;
    }

    @Override
    protected BlockEntity newScreen(BlockPos pos, BlockState state) {
        return new TailCameraDisplayBlockEntity(pos, state);
    }

    @Override
    public BlockPos panelOrigin(BlockState state, BlockPos pos) {
        return origin(state, pos);
    }

    /**
     * The camera button a hit on the face lands on, as touching it there would pick it: its camera's place among the
     * cameras of the shown camera's airframe; -1 off the buttons or off the face. The face is drawn as a display a row
     * tall at its rows' scale, so the buttons are laid out on it at that scale, and the touch is turned into it.
     */
    @Override
    public int tileAt(BlockGetter level, BlockState state, BlockPos pos, BlockHitResult hit, Vec3 look) {
        Touch touch = touched(state, pos, hit, look);
        if (touch == null || !(level.getBlockEntity(origin(state, pos)) instanceof TailCameraDisplayBlockEntity display)) {
            return -1;
        }
        double scale = rows(state);
        return CameraButtons.layout(display.camera().alongside().size()).at(touch.across() / scale, touch.up() / scale);
    }

    /**
     * Touching the face is the server's to make out, where the player looks from as the airliner lies: on a camera's
     * button, it shows that camera, for everyone, with a click; anywhere else it does nothing.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            press(state, level, pos, tileAt(level, state, pos, hit, SableFlight.turnToPlot(level, pos, player.getLookAngle())));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Shows the camera whose button was touched, if one was (-1 for none), with a click. */
    private static void press(BlockState state, Level level, BlockPos pos, int button) {
        if (button >= 0 && level.getBlockEntity(origin(state, pos)) instanceof TailCameraDisplayBlockEntity display) {
            display.look(display.camera().alongside().get(button));
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, CLICK_VOLUME, CLICK_PITCH);
        }
    }
}
