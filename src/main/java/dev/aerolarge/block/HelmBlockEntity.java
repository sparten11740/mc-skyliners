package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A ship's wheel on the client: how far round it is drawn, swinging smoothly to the notch it is set to rather than
 * jumping there, as a heavy wheel would turn under a helmsman's hands.
 */
public class HelmBlockEntity extends BlockEntity {
    /** How far round a notch turns the wheel, in radians: an eighth of a turn, a spoke's width. */
    private static final float NOTCH = (float) (Math.PI / 4);
    /** Share of the way to where it is set it swings each tick. */
    private static final float SWING = 0.25F;

    private float angle;
    private float lastAngle;
    /** Whether it has been drawn since it was loaded: a wheel loaded already turned starts where it is set. */
    private boolean placed;

    public HelmBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.HELM_ENTITY.get(), pos, state);
    }

    void clientTick() {
        float wanted = (float) (HelmBlock.turn(getBlockState()) * HelmBlock.MOST * NOTCH);
        if (!placed) {
            placed = true;
            angle = wanted;
        }
        lastAngle = angle;
        angle += (wanted - angle) * SWING;
    }

    /** How far round the wheel is drawn, in radians clockwise as its helmsman sees it, between last tick and this. */
    public float angle(float partialTick) {
        return Mth.lerp(partialTick, lastAngle, angle);
    }
}
