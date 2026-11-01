package dev.skyliners.block;

import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * The tone a rocket light glows in, the halo light, the wall washer and the floor light alike: warm white, a cool
 * night-light blue or pink. Used, a light goes on to the next, with a soft click.
 */
public enum LightTone implements StringRepresentable {
    WARM,
    COOL,
    PINK;

    /** A light's tone, saved by name. */
    public static final EnumProperty<LightTone> PROPERTY = EnumProperty.create("tone", LightTone.class);
    /** How loud the click is as a light goes on to its next tone. */
    private static final float CLICK_VOLUME = 0.6F;

    /** The tone after this one, round to the first again. */
    public LightTone next() {
        return values()[(ordinal() + 1) % values().length];
    }

    /** A light used: it goes on to its next tone with a click, on the server. */
    static InteractionResult turn(BlockState state, Level level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            server.setBlock(pos, state.setValue(PROPERTY, state.getValue(PROPERTY).next()), Block.UPDATE_ALL);
            server.playSound(null, pos, SoundEvents.COPPER_BULB_TURN_ON, SoundSource.BLOCKS, CLICK_VOLUME, 1.0F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
