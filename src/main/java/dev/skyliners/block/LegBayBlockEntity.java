package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One of the freighter's landing legs, worked from its bay in her belly (see {@link RetractableLegBlockEntity}): its
 * strut, its foot plate and anything fixed to it, her ramp's call button on one of them, go as the leg draws up and
 * come back as it comes down and locks. Going up takes {@link #RAISE_TICKS}: the strut's sleeves unlock and draw in,
 * the strut telescopes up into the bay and the foot plate comes up with it, tucked flat under her belly. Coming down
 * takes {@link #LOWER_TICKS}, the same the other way. Her autopilot works her legs (see {@code FreighterLegs}); each
 * client draws them moving (see the client's {@code LegBayRenderer}).
 */
public class LegBayBlockEntity extends RetractableLegBlockEntity {
    /** How long a leg takes to draw up, in ticks: six seconds. */
    public static final int RAISE_TICKS = 120;
    /** How long it takes to come down and lock, in ticks: five seconds, its weight helping it down. */
    public static final int LOWER_TICKS = 100;
    /**
     * How loud a leg going up or down is: past 1 it carries further, the server sending it 16 blocks further for each
     * unit, so it is heard across her decks and from the ground under her.
     */
    private static final float HYDRAULICS_VOLUME = 2.0F;
    /** Her foot plate's bottom is the leg's lowest point: it reaches no further down. */
    private static final int FOOT_BELOW = 0;
    /**
     * How far apart her legs' sounds are pitched, at most, either side of as recorded: all five start together, and the
     * same sound five times over in step would ring hollow, so each leg plays its own a little higher or lower.
     */
    private static final float PITCH_SPREAD = 0.06F;
    /** How many pitches her legs' sounds are spread over. */
    private static final int PITCHES = 5;

    public LegBayBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LEG_BAY_ENTITY.get(), pos, state);
    }

    @Override
    protected int raiseTicks() {
        return RAISE_TICKS;
    }

    @Override
    protected int lowerTicks() {
        return LOWER_TICKS;
    }

    @Override
    protected SoundEvent sound(boolean raising) {
        return raising ? SkylinersRegistry.FREIGHTER_LEGS_UP.get() : SkylinersRegistry.FREIGHTER_LEGS_DOWN.get();
    }

    @Override
    protected float volume() {
        return HYDRAULICS_VOLUME;
    }

    @Override
    protected int reachBelow() {
        return FOOT_BELOW;
    }

    /** Each leg its own pitch, from where its bay stands, within {@link #PITCH_SPREAD} either side of as recorded. */
    @Override
    protected float pitch() {
        int step = Math.floorMod(worldPosition.hashCode(), PITCHES) - PITCHES / 2;
        return 1 + PITCH_SPREAD * step / (PITCHES / 2);
    }
}
