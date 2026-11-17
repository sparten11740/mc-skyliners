package dev.skyliners.airframe;

import com.simibubi.create.content.contraptions.glue.SuperGlueEntity;
import dev.skyliners.Skyliners;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/**
 * The Super Glue an airliner is built with (see {@link AirframeBuild}), which her Physics Assembler takes as the
 * structure to assemble. As it assembles her, Sable carries along every stretch of it whose spot lies among her blocks;
 * a stretch whose spot is in the open air beside them, over a wing, stayed standing in the world where she took off,
 * unseen, and would glue whatever was built there later into one. So once she is assembled, the glue she left there
 * goes: her own, marked with where her autopilot stood as she was built, so another airliner's beside her is left alone.
 */
public final class AirframeGlue {
    /** The tag an airliner's own glue carries, followed by where her autopilot stood as she was built. */
    private static final String TAG = Skyliners.MOD_ID + "_airframe_glue_";
    /**
     * How far round her Physics Assembler the glue left behind is looked for, across and up or down, in blocks: further
     * than any of her reaches from it, her tail some 150 blocks back from it.
     */
    private static final int REACH_ACROSS = 200;
    private static final int REACH_UP = 64;

    private AirframeGlue() {
    }

    /** A stretch of glue an airliner is built with, marked as hers by where her autopilot stands, if she has one. */
    static SuperGlueEntity hers(SuperGlueEntity glue, @Nullable BlockPos autopilot) {
        if (autopilot != null) {
            glue.addTag(tag(autopilot));
        }
        return glue;
    }

    /** The tag of the glue of the airliner whose autopilot stood here as she was built. */
    private static String tag(BlockPos autopilot) {
        return TAG + autopilot.asLong();
    }

    /**
     * An airliner just assembled by her Physics Assembler at this spot, her autopilot standing at that one: the glue of
     * hers still standing in the world goes. Sable has carried the rest along by now, in the same call that took her
     * blocks.
     */
    public static void sweep(ServerLevel level, BlockPos assembler, BlockPos autopilot) {
        String tag = tag(autopilot);
        AABB round = new AABB(assembler).inflate(REACH_ACROSS, REACH_UP, REACH_ACROSS);
        level.getEntitiesOfClass(SuperGlueEntity.class, round, glue -> glue.getTags().contains(tag)).forEach(SuperGlueEntity::discard);
    }
}
