package dev.skyliners.airframe;

import static dev.skyliners.airframe.ZeppelinShape.*;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.NavLightBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The zeppelin dressed for the night, as an airship's lights are and as a ship's are when she is dressed overall: a
 * red navigation light on each port engine car and a green one on each starboard car, shining out to the side; a
 * white light on the tail; white strobes double-flashing on the tips of the fins; a red beacon under the belly,
 * blinking as the mast's does over the Sky Deck; and a line of warm bulbs along the crown from the nose to the top of
 * the upper fin, broken only by the Sky Deck. Their glow is drawn (see {@link NavLightBlock}), so they light up
 * nothing round them and read from far off at night. Each stands on a block of the zeppelin's, face to face, so it is
 * assembled with it, and nothing else stands on them. The {@code navigationLights} switch leaves them off a zeppelin
 * built from then on.
 */
final class ZeppelinLights implements ZeppelinPiece {
    /** How many rows apart the bulbs along the crown stand. */
    private static final int BULB_SPACING = 4;
    /** Where along the hull the beacon under the belly hangs, as a share of its length from the nose. */
    private static final double BEACON_SHARE = 0.5;
    private static final Direction[] FINS = {Direction.UP, Direction.DOWN, Direction.EAST, Direction.WEST};

    private final ZeppelinLayout layout;

    ZeppelinLights(ZeppelinLayout layout) {
        this.layout = layout;
    }

    /** How far out from the axis a fin's tip can reach at most, in blocks, and a little more. */
    private int finFarthest() {
        return (int) Math.ceil((RADIUS + FIN_SPAN) * layout.scale()) + 2;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return SkylinersConfig.NAVIGATION_LIGHTS.get();
    }

    /** On the engine cars, the tail, the fins' tips, the belly and the crown, wherever they are at the size. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    /** The lights, once the engine cars and the fins stand, and the Sky Deck the bulbs leave room for. */
    @Override
    public void build() {
        for (BlockPos car : layout.engineCars()) {
            engineCarLight(car);
        }
        tailLight();
        int bulbsTo = layout.length();
        for (Direction way : FINS) {
            BlockPos tip = finTip(way);
            if (tip == null) {
                continue;
            }
            finStrobe(tip, way);
            bulbsTo = way == Direction.UP ? tip.getZ() : bulbsTo;
        }
        bellyBeacon();
        crownBulbs(bulbsTo);
    }

    /** An engine car's light: on the outside of its pod, at its middle, shining out, red to port and green to starboard. */
    private void engineCarLight(BlockPos middle) {
        int side = Integer.signum(middle.getX());
        int x = middle.getX();
        while (!layout.isOpen(x + side, middle.getY(), middle.getZ())) {
            x += side;
        }
        NavLightBlock.Kind kind = side > 0 ? NavLightBlock.Kind.GREEN : NavLightBlock.Kind.RED;
        light(x + side, middle.getY(), middle.getZ(), side > 0 ? Direction.EAST : Direction.WEST, kind);
    }

    /** The white light at the tip of the tail, on the hull's axis, shining aft. */
    private void tailLight() {
        int axis = layout.axis();
        for (int z = layout.length() + 1; z > 0; z--) {
            if (!layout.isOpen(0, axis, z)) {
                light(0, axis, z + 1, Direction.SOUTH, NavLightBlock.Kind.WHITE);
                return;
            }
        }
    }

    /**
     * A strobe at a fin's tip, shining out past it; under the lower fin, whose tip the tail wheel stands under, aft of
     * the tip instead.
     */
    private void finStrobe(BlockPos tip, Direction way) {
        BlockPos at = way == Direction.DOWN ? tip.south() : tip.relative(way);
        light(at.getX(), at.getY(), at.getZ(), way == Direction.DOWN ? Direction.SOUTH : way, NavLightBlock.Kind.STROBE);
    }

    /** The farthest block of a fin out from the axis the way given, the aftmost of those as far out; null for none. */
    @Nullable
    private BlockPos finTip(Direction way) {
        BlockPos tip = null;
        int farthest = 0;
        for (int z = (int) Math.floor(FIN_ROOT_FRONT * layout.scale()); z <= Math.ceil(FIN_ROOT_BACK * layout.scale()); z++) {
            int out = finOut(way, z);
            if (out > 0 && out >= farthest) {
                farthest = out;
                tip = new BlockPos(way.getStepX() * out, layout.axis() + way.getStepY() * out, z);
            }
        }
        return tip;
    }

    /** How far out from the axis a fin's blocks reach at a row, the way given; 0 where it has none there. */
    private int finOut(Direction way, int z) {
        int out = 0;
        for (int k = 1; k <= finFarthest(); k++) {
            int x = way.getStepX() * k;
            int y = layout.axis() + way.getStepY() * k;
            if (layout.stateAt(x, y, z).is(SkylinersRegistry.ZEPPELIN_FABRIC.get()) && !layout.inHull(x, y, z)) {
                out = k;
            }
        }
        return out;
    }

    /** The red beacon under the belly, at the lowest point of the hull there, shining down. */
    private void bellyBeacon() {
        int z = (int) Math.round(layout.length() * BEACON_SHARE);
        int y = 1;
        while (y < layout.axis() && layout.stateAt(0, y, z).isAir()) {
            y++;
        }
        light(0, y - 1, z, Direction.DOWN, NavLightBlock.Kind.BEACON);
    }

    /**
     * The warm bulbs along the crown, every few rows from the nose up to a row, each on top of the topmost block
     * there where that is the cover or the upper fin, so the line climbs the fin's leading edge and stops short of the
     * Sky Deck.
     */
    private void crownBulbs(int to) {
        for (int z = 0; z < to; z += BULB_SPACING) {
            int top = layout.axis() + finFarthest();
            while (top > layout.axis() && layout.stateAt(0, top, z).isAir()) {
                top--;
            }
            BlockState under = layout.stateAt(0, top, z);
            if (under.is(SkylinersRegistry.ZEPPELIN_COVER.get()) || under.is(SkylinersRegistry.ZEPPELIN_FABRIC.get())) {
                light(0, top + 1, z, Direction.UP, NavLightBlock.Kind.BULB);
            }
        }
    }

    /** A light shining the way given, where nothing of the zeppelin stands yet. */
    private void light(int x, int y, int z, Direction facing, NavLightBlock.Kind kind) {
        if (y >= 0 && layout.isOpen(x, y, z)) {
            layout.set(x, y, z, SkylinersRegistry.NAV_LIGHT.get().defaultBlockState().setValue(NavLightBlock.FACING, facing).setValue(NavLightBlock.KIND, kind));
        }
    }
}
