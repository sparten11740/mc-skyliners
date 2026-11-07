package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.state.BlockState;

/**
 * One leg of the airliner's retractable landing gear, worked from the top of the leg (see
 * {@link RetractableLegBlockEntity}): the blocks of the leg below it (the strut, the axle, the wheel mounts with their
 * tires, the relays that steer and brake them, the ground crew's call button) go as the leg folds up into the belly and
 * come back as it comes down and locks. The airliner rolls on its real wheels again then: Offroad's wheel mount is a
 * Sable actor that finds the ground from wherever its block stands, each physics tick, so a mount put back mid-flight
 * carries the airliner as before.
 *
 * <p>Going up takes {@link #RAISE_TICKS}: the doors open, the leg swings up and the doors close behind it. Coming down
 * takes {@link #LOWER_TICKS}, and the wheels come back once it has swung down, if they have room under them. The flight
 * deck's gear lever works every leg (see {@link GearLeverBlockEntity}); each client draws it swinging (see the client's
 * {@code GearBayRenderer}).
 */
public class GearBayBlockEntity extends RetractableLegBlockEntity {
    /** How long the leg takes to swing up, doors and all, in ticks: eight seconds, about an A380's. */
    public static final int RAISE_TICKS = 160;
    /** How long it takes to swing down and lock, in ticks: six seconds, its weight helping it down. */
    public static final int LOWER_TICKS = 120;
    /**
     * How loud a leg going up or down is: past 1 it carries further, as the server sends a sound to everyone within 16
     * blocks for each unit of its volume, so the whole cabin over the gear hears it, as on an airliner.
     */
    private static final float HYDRAULICS_VOLUME = 3.0F;
    /** The wheels' tires, drawn on their mounts a block above the ground, reach down into the row under them. */
    private static final int TIRES_BELOW = 1;

    public GearBayBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.GEAR_BAY_ENTITY.get(), pos, state);
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
        return raising ? SkylinersRegistry.GEAR_RETRACT.get() : SkylinersRegistry.GEAR_EXTEND.get();
    }

    @Override
    protected float volume() {
        return HYDRAULICS_VOLUME;
    }

    @Override
    protected int reachBelow() {
        return TIRES_BELOW;
    }
}
