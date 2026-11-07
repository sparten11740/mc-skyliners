package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.ShellBlock;
import dev.skyliners.block.ShellGeometry;
import dev.skyliners.block.StandingWindowBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's portholes (see {@link dev.skyliners.block.PortholeBlock}) along her two long passages: down the outer
 * wall of the tunnel in each prong, and down both sides of the round tube out to her cockpit, wherever that wall is her
 * skin with open sky straight out past it, so whoever walks them sees out. Each takes the place of a whole block of her
 * cover, flush with it inside and out as one set by hand is, its frame taking the cover's look beside it, outside and
 * in; only where the cover runs flat for a block either side of it along her, so it sits in a flat stretch, never on a
 * step of her outline, and where the passage is open in front of it inside, so nothing of her gallery stands before it
 * (see {@link FreighterGallery}). It is as near a standing person's eyes as her skin allows: at them, or a row higher
 * where her skin is cut there, as down the outer side of her tube. Down each wall from her bow they stand at least so
 * far apart, which along her prongs, whose outer walls step in a block every seven toward their tips, puts one in each
 * flat stretch.
 *
 * <p>Never on her prongs' inner walls along her loading gap, where her gallery's posters hang. Laid out in her own
 * frame, nose north, before her lights, which fix no lamp over one (see {@link DarkCorners}); each touches her cover
 * face to face, so it is assembled with her. None with the server config's {@code freighter.portholes} off.
 */
final class FreighterPortholes {
    /** The rows over her deck a porthole may be in, the first that fits taken: a standing person's eyes, one higher. */
    private static final int[] ROWS_UP = {2, 3};
    /** How far apart along her the portholes down one wall stand at least, in blocks. */
    private static final int APART = 5;
    /**
     * How many blocks straight out past a porthole must be open sky, outside her hull, so it looks out at the sky and
     * not at her own hull close by, as down her prong beside her cockpit's pod.
     */
    private static final int SKY_CLEAR = 3;
    /** The passages whose walls take portholes. */
    private static final Set<FreighterShape.Part> PASSAGES =
        Set.of(FreighterShape.Part.PRONG, FreighterShape.Part.TUBE);
    /** The ways a porthole can look out of her: to either side of her, as her passages run along her length. */
    private static final List<Direction> OUT = List.of(Direction.WEST, Direction.EAST);

    /** A wall portholes are set down: the passage it bounds and the way it looks out. */
    private record Wall(FreighterShape.Part passage, Direction out) {
    }

    private final HullLayout layout;
    private final int deck;
    private final double scale;
    /** How far along her the last porthole set down each wall stands. */
    private final Map<Wall, Integer> last = new HashMap<>();

    private FreighterPortholes(HullLayout layout, int deck, double scale) {
        this.layout = layout;
        this.deck = deck;
        this.scale = scale;
    }

    /** Sets the portholes in a freighter's layout whose deck's floor is the row given, at the scale given. */
    static void build(HullLayout layout, int deck, double scale) {
        if (SkylinersConfig.FREIGHTER_PORTHOLES.get()) {
            new FreighterPortholes(layout, deck, scale).setAll();
        }
    }

    /** Looks along her from her bow, across her at each step, for where portholes go. */
    private void setAll() {
        HullLayout.Box box = layout.airBox();
        for (int z = box.minZ(); z <= box.maxZ(); z++) {
            for (int x = box.minX(); x <= box.maxX(); x++) {
                setAt(x, z);
            }
        }
    }

    /** A porthole in a column of her, looking out either way, where one fits far enough from the last down its wall. */
    private void setAt(int x, int z) {
        for (Direction out : OUT) {
            Wall wall = wall(x, z, out);
            if (wall != null) {
                setAt(x, z, out, wall);
            }
        }
    }

    /** A porthole in a column of a wall, looking out the way given, where one fits far enough from the last down it. */
    private void setAt(int x, int z, Direction out, Wall wall) {
        int y = row(x, z, out);
        Integer before = last.get(wall);
        if (y >= 0 && (before == null || z - before >= APART)) {
            BlockState porthole = SkylinersRegistry.PORTHOLE.get().defaultBlockState()
                .setValue(StandingWindowBlock.FACING, out);
            layout.set(x, y, z, porthole);
            last.put(wall, z);
        }
    }

    /**
     * The wall of one of her passages a column of her would be in, looking out the way given: the passage inside it at
     * a standing person's eyes, and for a prong only its outer wall, looking away from her middle line; null for none.
     */
    @Nullable
    private Wall wall(int x, int z, Direction out) {
        int inside = x - out.getStepX();
        double up = (deck + ROWS_UP[0] + 0.5) / scale;
        FreighterShape.Part passage = FreighterShape.nearest(inside / scale, up, z / scale);
        boolean outer = passage != FreighterShape.Part.PRONG || out.getStepX() == Integer.signum(x);
        return PASSAGES.contains(passage) && outer ? new Wall(passage, out) : null;
    }

    /** The row of a column a porthole looking out the way given fits in, the first of {@link #ROWS_UP}; -1 for none. */
    private int row(int x, int z, Direction out) {
        for (int up : ROWS_UP) {
            if (fits(x, deck + up, z, out)) {
                return deck + up;
            }
        }
        return -1;
    }

    /**
     * Whether a porthole fits in a block looking out the way given: a whole block of her cover in her skin, the
     * passage open before it inside, open sky {@link #SKY_CLEAR} blocks straight out past it, and the cover flat for a
     * block either side of it along her, facing the sky as it does.
     */
    private boolean fits(int x, int y, int z, Direction out) {
        int dx = out.getStepX();
        boolean walled = HullLayout.skin(layout.stateAt(x, y + 1, z)) && HullLayout.skin(layout.stateAt(x, y - 1, z));
        boolean flat = facesSky(x, y, z - 1, dx) && facesSky(x, y, z + 1, dx);
        return facesSky(x, y, z, dx) && walled && flat && openInside(x - dx, y, z) && skyOut(x, y, z, dx);
    }

    /** Whether a block is a whole block of her cover with open sky right outside it, so many blocks across her on. */
    private boolean facesSky(int x, int y, int z, int dx) {
        BlockState there = layout.stateAt(x, y, z);
        boolean cover = there.is(SkylinersRegistry.ZEPPELIN_COVER.get());
        return cover && ShellBlock.shape(there) == ShellGeometry.WHOLE && sky(x + dx, y, z);
    }

    /** Whether so many blocks straight out past a block, across her, are all open sky. */
    private boolean skyOut(int x, int y, int z, int dx) {
        for (int out = 1; out <= SKY_CLEAR; out++) {
            if (!sky(x + out * dx, y, z)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a block is open sky: nothing of hers there, outside her hull. */
    private boolean sky(int x, int y, int z) {
        return layout.isOpen(x, y, z) && layout.surface().depth(x, y + 0.5, z) > 0;
    }

    /** Whether a block is open inside her hull, nothing standing in it. */
    private boolean openInside(int x, int y, int z) {
        return layout.isOpen(x, y, z) && layout.surface().depth(x, y + 0.5, z) < 0;
    }
}
