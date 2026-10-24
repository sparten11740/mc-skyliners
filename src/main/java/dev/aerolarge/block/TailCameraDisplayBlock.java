package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.airframe.OutsideCamera;
import dev.aerolarge.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The camera display on the flight deck, like an A380's taxiing camera screen: a wide screen (see
 * {@link WideScreenBlock}) for the picture from one of the airliner's outside cameras, the one on top of the fin
 * looking forward over the whole airliner at first. A row of buttons along its bottom left picks the camera (see
 * {@link CameraButtons}); its renderer draws it all, upright or hanging tilted from the crown.
 */
public class TailCameraDisplayBlock extends WideScreenBlock {
    private static final MapCodec<TailCameraDisplayBlock> CODEC = simpleCodec(TailCameraDisplayBlock::new);

    public TailCameraDisplayBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
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

    /** Shows the camera whose button was touched, if one was, with a click. */
    private static void press(BlockState state, Level level, BlockPos pos, BlockHitResult hit, Vec3 look) {
        Touch touch = touched(state, pos, hit, look);
        OutsideCamera camera = touch != null ? CameraButtons.at(touch.across(), touch.up()) : null;
        if (camera != null && level.getBlockEntity(origin(state, pos)) instanceof TailCameraDisplayBlockEntity display) {
            display.look(camera);
            level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F);
        }
    }
}
