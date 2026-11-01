package dev.skyliners.autopilot;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.airframe.RocketSilhouette;
import dev.skyliners.compat.SableFlight;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A rocket's engines setting the ground alight under her: while they blaze close over it, lifting off or coming in to
 * land, fire catches here and there on the ground round under her, as wide as she is built and a little wider, and
 * burns on after she has gone as any fire does, catching the grass and whatever burns round it. Fire is put down only
 * where it could burn, never in water, and only while the world lets fire spread (the doFireTick game rule); the
 * server config's {@code rocket.groundFire} leaves it out. In the world, not aboard her: her own blocks are in her
 * plot.
 */
final class RocketGroundFire {
    /** How high over the ground her foot may be for her lit engines to set it alight, in blocks. */
    private static final double FIRE_UNDER = 10;
    /** How many spots under her a tick may catch fire: a few, so the ground goes up a patch at a time. */
    private static final int TRIES = 3;
    /** How far out from under her middle the ground catches, as a share of her body's widest radius. */
    private static final double SPREAD = 1.3;

    private RocketGroundFire() {
    }

    /**
     * Each tick of an assembled rocket's: fire caught at a few spots on the ground round under her while her lit
     * engines are close over it, given her radio altitude, how far under her middle her foot is, in blocks, and her
     * engines' share of full thrust.
     */
    static void tick(ServerLevel level, SableFlight.State flight, double radio, int footBelow, double thrust) {
        if (!SkylinersConfig.ROCKET_GROUND_FIRE.get() || thrust <= 0 || radio >= FIRE_UNDER
            || !level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            return;
        }
        Vec3 middle = flight.position();
        double reach = RocketSilhouette.body(RocketSilhouette.height() / 2) * RocketSilhouette.scaleOf(footBelow) * SPREAD;
        for (int spot = 0; spot < TRIES; spot++) {
            double way = level.getRandom().nextDouble() * Mth.TWO_PI;
            double out = reach * Math.sqrt(level.getRandom().nextDouble());
            int x = Mth.floor(middle.x + Math.cos(way) * out);
            int z = Mth.floor(middle.z + Math.sin(way) * out);
            ignite(level, new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z));
        }
    }

    /**
     * Fire on the ground at a spot, soul fire's blue on soul sand, if the spot is open air or a plant and fire would
     * burn there.
     */
    private static void ignite(ServerLevel level, BlockPos at) {
        BlockState there = level.getBlockState(at);
        if (!there.canBeReplaced() || !level.getFluidState(at).isEmpty()) {
            return;
        }
        BlockState fire = BaseFireBlock.getState(level, at);
        if (fire.canSurvive(level, at)) {
            level.setBlock(at, fire, Block.UPDATE_ALL);
        }
    }
}
