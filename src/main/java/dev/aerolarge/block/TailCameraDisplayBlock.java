package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.airframe.CameraSpot;
import dev.aerolarge.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * buttons along its bottom left picks the camera (see {@link CameraButtons}); its renderer draws it all, upright or
 * hanging tilted from the crown.
 */
public class TailCameraDisplayBlock extends WideScreenBlock {
    private static final MapCodec<TailCameraDisplayBlock> CODEC = simpleCodec(TailCameraDisplayBlock::new);
    /**
     * Whether it is the large display, {@link #LARGE_COLUMNS} blocks wide and {@link #LARGE_ROWS} tall, as a zeppelin's
     * control car has, rather than the flight deck's three by one. Only an airframe's build puts one up, always
     * upright; put up by hand, a display is the small one.
     */
    public static final BooleanProperty LARGE = BooleanProperty.create("large");
    public static final int LARGE_COLUMNS = 4;
    public static final int LARGE_ROWS = 2;

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

    /**
     * Touching the face is the server's to make out, where the player looks from as the airliner lies: on a camera's
     * button, it shows that camera, for everyone; anywhere else it does nothing.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!onFace(state, hit.getDirection())) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            press(state, level, pos, hit, SableFlight.turnToPlot(level, pos, player.getLookAngle()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Shows the camera whose button was touched, if one was, with a click: one of the cameras it picks among. Its face
     * is drawn as a display a row tall at its rows' scale, so the buttons are laid out on it at that scale.
     */
    private static void press(BlockState state, Level level, BlockPos pos, BlockHitResult hit, Vec3 look) {
        Touch touch = touched(state, pos, hit, look);
        if (touch == null || !(level.getBlockEntity(origin(state, pos)) instanceof TailCameraDisplayBlockEntity display)) {
            return;
        }
        double scale = rows(state);
        CameraSpot camera = CameraButtons.at(display.camera().alongside(), touch.across() / scale, touch.up() / scale);
        if (camera != null) {
            display.look(camera);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F);
        }
    }
}
