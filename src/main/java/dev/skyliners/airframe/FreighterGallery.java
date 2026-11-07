package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.PlanterBlock;
import dev.skyliners.block.SpacePoster;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's galleries, in her two long passages. Along the tunnel down her port prong (see {@link
 * FreighterShape#PRONG_TIP}), space posters hang on its inner wall, the straight one along her loading gap, looking out
 * across the tunnel, each with a floor light under it shining up at it as her paintings have (see {@link
 * FreighterPaintings}) where it hangs clear of the floor, so the long bare tunnel is a walk past pictures out to her
 * prong's tip: from her disc's end toward the tip, each whole at the first place along the wall it fits, a gap between
 * them, as high as the tunnel lets them be. Along the round tube out to her cockpit (see {@link
 * FreighterShape#TUBE_OUT}), whose curve leaves a ledge down either side of its floor, the fleet's souvenir models
 * stand on the ledges, their noses all pointing on to her cockpit, planters between them, every other one on the other
 * side where both sides have a ledge.
 *
 * <p>Laid out in her own frame, nose north, so her port prong is to the west and her cockpit's tube to the east. None
 * with the server config's {@code freighter.gallery} off.
 */
final class FreighterGallery {
    /**
     * The posters hung, from her disc's end of the tunnel toward her prong's tip: those no taller than its three rows
     * of headroom (the quasar, four tall, is not among them).
     */
    private static final List<SpacePoster> POSTERS =
        List.of(SpacePoster.EARTHRISE, SpacePoster.BLACK_HOLE, SpacePoster.SOLAR_SYSTEM);
    /** How many blocks of wall are left between one poster and the next. */
    private static final int APART = 2;
    /** How many rows over the tunnel's floor a poster's lowest row may be, the first that fits taken, in blocks. */
    private static final int[] UP = {2, 1};
    /** How far ahead of her axis the tunnel is looked for along her prong, in metres: from her bulkhead to the tip. */
    private static final double FROM = -FreighterLayout.BULKHEAD;
    /** How far out from her middle line her prong's inner wall is looked for at most, in metres. */
    private static final double WALL_SEARCH = FreighterShape.PRONG_OUT_ROOT;
    /**
     * The fleet's models along her cockpit's tube's ledges, two by two, from her disc's end on: each pair stands
     * after two planters, the first pair first.
     */
    private static final List<List<Supplier<? extends Block>>> MODELS = List.of(
        List.of(SkylinersRegistry.MODEL_AIRLINER, SkylinersRegistry.MODEL_ZEPPELIN),
        List.of(SkylinersRegistry.MODEL_ROCKET, SkylinersRegistry.MODEL_FREIGHTER));
    /** What grows in the planters between the models, each planter of a pair the other. */
    private static final PlanterBlock.Plant[] PLANTS = {PlanterBlock.Plant.GREENERY, PlanterBlock.Plant.FLOWERS};
    /** How far apart along her tube one piece on its ledges is from the next, on the other side, in blocks. */
    private static final int TUBE_STEP = 2;
    /** How many blocks short of her cockpit's door her tube's ledges are left clear. */
    private static final int TUBE_ENDS = 2;
    /** How far out either side of her tube's middle its ledges are looked for, in blocks. */
    private static final int LEDGE_SEARCH = 6;

    private final HullLayout layout;
    private final int deck;
    private final double scale;

    private FreighterGallery(HullLayout layout, int deck, double scale) {
        this.layout = layout;
        this.deck = deck;
        this.scale = scale;
    }

    /** Hangs the gallery in a freighter's layout whose deck's floor is the row given, at the scale given. */
    static void build(HullLayout layout, int deck, double scale) {
        if (SkylinersConfig.FREIGHTER_GALLERY.get()) {
            FreighterGallery gallery = new FreighterGallery(layout, deck, scale);
            gallery.hang();
            gallery.lineTube();
        }
    }

    /** Hangs each poster at the first place along the wall past the one before it where it fits. */
    private void hang() {
        int z = (int) Math.round(FROM * scale);
        int tip = (int) Math.round(FreighterShape.PRONG_TIP * scale);
        for (SpacePoster poster : POSTERS) {
            z = hangFrom(poster, z, tip);
        }
    }

    /**
     * Hangs a poster at the first place from a row across her prong on toward its tip where it fits, its edge nearest
     * her disc on that row or past it; gives the row of the next poster's edge nearest her disc, {@link #APART} blocks
     * of wall past this one's far edge, or the row given where it fits nowhere. A poster looking west runs from its
     * middle column {@code columns / 2} blocks toward the tip and the rest toward her disc.
     */
    private int hangFrom(SpacePoster poster, int from, int tip) {
        int half = poster.columns() / 2;
        for (int z = from - (poster.columns() - half - 1); z - half >= tip; z--) {
            if (hangAt(poster, z)) {
                return z - half - 1 - APART;
            }
        }
        return from;
    }

    /** Hangs a poster with its middle column on a row across her prong, as high as fits, if it fits there. */
    private boolean hangAt(SpacePoster poster, int z) {
        Integer x = tunnelEdge(z);
        if (x == null) {
            return false;
        }
        for (int up : UP) {
            BlockPos middle = new BlockPos(x, deck + up + poster.rowsUnderMiddle(), z);
            if (FreighterPaintings.hangWhole(layout, deck, poster, middle, Direction.WEST)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The column of the tunnel along her port prong nearest her middle line on a row across it, the one against its
     * inner wall: the first open over her deck's floor walking out from her loading gap, past its wall; null for none.
     */
    private Integer tunnelEdge(int z) {
        int farthest = (int) Math.round(-WALL_SEARCH * scale);
        boolean pastWall = false;
        for (int x = 0; x >= farthest; x--) {
            boolean open = layout.isOpen(x, deck + 1, z) && !layout.isOpen(x, deck, z);
            if (pastWall && open) {
                return x;
            }
            pastWall |= !layout.isOpen(x, deck + 1, z);
        }
        return null;
    }

    /**
     * Stands the fleet's models and planters along her cockpit's tube's ledges, from her disc's end toward her
     * cockpit, a piece every {@link #TUBE_STEP} blocks, each on the other side from the one before where that side has
     * a ledge there, or else on the side that has.
     */
    private void lineTube() {
        int middle = (int) Math.round(FreighterShape.TUBE_OUT * scale);
        int from = (int) Math.round(FreighterShape.TUBE_FROM * scale);
        int to = (int) Math.round(FreighterShape.POD_FROM * scale) + TUBE_ENDS;
        int placed = 0;
        for (int z = from; z >= to; z -= TUBE_STEP) {
            int side = placed % 2 == 0 ? 1 : -1;
            BlockPos spot = ledgeAt(middle, z, side);
            if (spot == null) {
                side = -side;
                spot = ledgeAt(middle, z, side);
            }
            if (spot != null) {
                layout.set(spot.getX(), spot.getY(), spot.getZ(), piece(placed, side));
                placed++;
            }
        }
    }

    /**
     * The piece the one given in turn along her tube is, facing the way it should on the side given: a model of the
     * fleet pointing on to her cockpit, or a planter facing across the tube.
     */
    private BlockState piece(int turn, int side) {
        int pair = turn / 2;
        boolean model = pair % 2 == 0 && pair / 2 < MODELS.size();
        if (model) {
            Block block = MODELS.get(pair / 2).get(turn % 2).get();
            return block.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH);
        }
        Direction across = side > 0 ? Direction.WEST : Direction.EAST;
        return PlanterBlock.planted(SkylinersRegistry.PLANTER.get(), PLANTS[turn % PLANTS.length], across);
    }

    /**
     * Where something can stand on the ledge on one side of her tube on a row across it: over the first block out from
     * its middle that rises over her deck's floor, if that has a firm top and room over it; null for none.
     */
    private BlockPos ledgeAt(int middle, int z, int side) {
        for (int out = 0; out <= LEDGE_SEARCH; out++) {
            int x = middle + side * out;
            if (!layout.isOpen(x, deck + 1, z)) {
                return ledgeTop(x, z) && layout.isOpen(x, deck + 2, z) ? new BlockPos(x, deck + 2, z) : null;
            }
        }
        return null;
    }

    /** Whether a block over her deck's floor beside her tube's floor is a ledge to stand something on. */
    private boolean ledgeTop(int x, int z) {
        BlockState ledge = layout.stateAt(x, deck + 1, z);
        return !ledge.isAir() && ledge.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP);
    }
}
