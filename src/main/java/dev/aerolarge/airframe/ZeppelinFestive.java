package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.NavLightBlock;
import it.unimi.dsi.fastutil.HashCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A festive zeppelin's dress, as the Festive Zeppelin Blueprint builds it (its livery {@link Livery#FESTIVE} paints the
 * rest): fairy lights round the hull at every ring, red, gold, green and blue in turn, twinkling one colour after
 * another so they seem to chase round; a wreath of evergreen garland round the tip of the nose, dotted with red
 * berries, a red ribbon bow under it; and a gold star in place of the red beacon on the Sky Deck's mast. All of it is
 * Aerolarge's own blocks, each on the zeppelin face to face, so its assembly takes it along (outside the hull it takes
 * only Aerolarge's blocks and the other mods' it is configured with: a wreath of vanilla leaves stayed in the air and
 * held the nose down), and nothing else stands on it.
 */
final class ZeppelinFestive implements ZeppelinPiece {
    /** How far apart the fairy lights round a ring are, in blocks round the hull. */
    private static final double FAIRY_SPACING = 8;
    private static final NavLightBlock.Kind[] FAIRY = {
        NavLightBlock.Kind.FAIRY_RED, NavLightBlock.Kind.FAIRY_GOLD, NavLightBlock.Kind.FAIRY_GREEN, NavLightBlock.Kind.FAIRY_BLUE
    };
    /**
     * How far out from the nose's tip the wreath lies, inside and out, in blocks: on a full-size nose, and a quarter in
     * on a half-size one's, as at its full width it would look twice as big on her. And about one in so many a berry.
     */
    private static final Wreath FULL_WREATH = new Wreath(5.5, 7.5);
    private static final Wreath HALF_WREATH = new Wreath(4, 5.5);
    private static final int BERRY_ONE_IN = 7;
    /** How far forward of the nose's tip the wreath is looked for on its cover, in rows. */
    private static final int NOSE_DEPTH = 40;

    private final ZeppelinLayout layout;

    /** How far out from the nose's tip a wreath lies, inside and out, in blocks. */
    private record Wreath(double inner, double outer) {
    }

    ZeppelinFestive(ZeppelinLayout layout) {
        this.layout = layout;
    }

    /** At every size, the wreath sized to her nose. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return options.livery() == Livery.FESTIVE;
    }

    @Override
    public void build() {
        for (int ring = 0; ring <= CELLS; ring++) {
            fairyLights((int) Math.floor(ringAt(ring) * layout.scale()));
        }
        wreath();
        star();
    }

    /** The fairy lights round the hull at a row, every few blocks round it, each on the cover where it faces out. */
    private void fairyLights(int z) {
        double radius = layout.radiusAt(z);
        int count = (int) Math.floor(2 * Math.PI * radius / FAIRY_SPACING);
        for (int k = 0; k < count; k++) {
            double round = 2 * Math.PI * k / count;
            fairyLight(z, round, FAIRY[k % FAIRY.length]);
        }
    }

    /**
     * A fairy light on the cover at a row, so far round from the crown: out from the outermost block of the cover the
     * way it faces most there.
     */
    private void fairyLight(int z, double round, NavLightBlock.Kind kind) {
        double across = Math.sin(round);
        double up = Math.cos(round);
        Direction out = Math.abs(across) > Math.abs(up) ? (across > 0 ? Direction.EAST : Direction.WEST) : (up > 0 ? Direction.UP : Direction.DOWN);
        double radius = layout.radiusAt(z) + 2;
        BlockPos outer = null;
        for (double r = radius; r > 0 && outer == null; r -= 0.5) {
            BlockPos at = new BlockPos((int) Math.round(across * r), (int) Math.round(layout.axis() + up * r), z);
            if (layout.stateAt(at.getX(), at.getY(), at.getZ()).is(AerolargeRegistry.ZEPPELIN_COVER.get())) {
                outer = at;
            }
        }
        BlockPos light = outer == null ? null : outer.relative(out);
        if (light != null && light.getY() >= 0 && layout.isOpen(light.getX(), light.getY(), light.getZ())) {
            layout.set(light.getX(), light.getY(), light.getZ(),
                AerolargeRegistry.NAV_LIGHT.get().defaultBlockState().setValue(NavLightBlock.FACING, out).setValue(NavLightBlock.KIND, kind));
        }
    }

    /**
     * The wreath round the tip of the nose: spruce on the front of the cover in a ring round the hull's axis, every so
     * often a red berry, and a red bow at the bottom of it.
     */
    private void wreath() {
        BlockState spruce = AerolargeRegistry.FESTIVE_GARLAND.get().defaultBlockState();
        BlockState red = AerolargeRegistry.FESTIVE_BOW.get().defaultBlockState();
        Wreath wreath = switch (layout.hullSize()) {
            case HALF -> HALF_WREATH;
            case FULL -> FULL_WREATH;
        };
        int reach = (int) Math.ceil(wreath.outer());
        for (BlockPos across : BlockPos.betweenClosed(-reach, layout.axis() - reach, 0, reach, layout.axis() + reach, 0)) {
            double from = Math.hypot(across.getX(), across.getY() - layout.axis());
            if (from < wreath.inner() || from > wreath.outer()) {
                continue;
            }
            boolean bow = across.getY() < layout.axis() - wreath.inner() && Math.abs(across.getX()) <= 1;
            boolean berry = Math.floorMod(HashCommon.mix(across.asLong()), (long) BERRY_ONE_IN) == 0;
            onNose(across.getX(), across.getY(), bow || berry ? red : spruce);
        }
    }

    /** Something on the front of the nose's cover at a place across it, where nothing stands yet. */
    private void onNose(int x, int y, BlockState state) {
        int front = front(x, y);
        if (front >= 0 && layout.isOpen(x, y, front - 1)) {
            layout.set(x, y, front - 1, state);
        }
    }

    /** The row of the nose's cover furthest forward at a place across it; -1 where none is. */
    private int front(int x, int y) {
        for (int z = -1; z < NOSE_DEPTH; z++) {
            if (layout.stateAt(x, y, z).is(AerolargeRegistry.ZEPPELIN_COVER.get())) {
                return z;
            }
        }
        return -1;
    }

    /** The gold star on the Sky Deck's mast, in the red beacon's place. */
    private void star() {
        BlockPos beacon = ZeppelinSkyDeck.beacon(layout);
        BlockState there = layout.stateAt(beacon.getX(), beacon.getY(), beacon.getZ());
        if (there.is(AerolargeRegistry.NAV_LIGHT.get())) {
            layout.set(beacon.getX(), beacon.getY(), beacon.getZ(), there.setValue(NavLightBlock.KIND, NavLightBlock.Kind.STAR));
        }
    }
}
