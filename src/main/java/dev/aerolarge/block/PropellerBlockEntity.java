package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A propeller's spin on the client: how far it has spun up toward the signal its hub carries, and how far round its
 * blades have turned. A big propeller takes its time: it winds up over a few seconds and runs down slower still.
 */
public class PropellerBlockEntity extends BlockEntity {
    /** Share of full speed gained per tick spinning up, and lost per tick running down. */
    private static final float SPIN_UP = 0.01F;
    private static final float RUN_DOWN = 0.005F;
    /** How far round the blades turn per tick at full speed, in radians: fast, but slow enough to see the blades go. */
    private static final float FULL_SPEED = 0.45F;
    /** A whole turn, in radians. */
    private static final float FULL_TURN = (float) (2 * Math.PI);
    /** How far round the hub its blades reach, in blocks: the whole propeller is drawn while any of it is in view. */
    public static final float REACH = 6;

    private float spin;
    private float angle;
    private float lastAngle;

    public PropellerBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.PROPELLER_ENTITY.get(), pos, state);
    }

    void clientTick() {
        float wanted = getBlockState().getValue(ThrustRelayBlock.POWER) / 15F;
        spin = wanted > spin ? Math.min(wanted, spin + SPIN_UP) : Math.max(wanted, spin - RUN_DOWN);
        lastAngle = angle;
        angle += spin * FULL_SPEED;
        if (angle > FULL_TURN) {
            angle -= FULL_TURN;
            lastAngle -= FULL_TURN;
        }
    }

    /** How far round the blades have turned, in radians, between the last tick and this one. */
    public float angle(float partialTick) {
        return Mth.lerp(partialTick, lastAngle, angle);
    }

    /** The box the whole propeller is drawn in: the hub and its blades all round it. */
    public AABB renderBox() {
        return new AABB(worldPosition).inflate(REACH);
    }
}
