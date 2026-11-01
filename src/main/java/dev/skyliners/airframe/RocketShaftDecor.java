package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.ShaftDecorBlock;
import dev.skyliners.block.ShaftDecorBlock.Along;
import dev.skyliners.block.ShaftDecorBlock.Kind;
import dev.skyliners.block.ShaftDecorBlock.Row;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What each of the rocket's rooms hangs on the outside of her glass lift shaft (see {@link ShaftDecorBlock}), so the
 * dark glass column up through her rooms belongs to each of them: on the observation deck, her chillout lounge, deep
 * blue velvet curtains drawn back to the corners of each of the shaft's faces with gold tiebacks, a valance across the
 * top between them, so the cold glass feels cosy; on the entry story a departures board of where she flies; in the
 * cargo bay hazard-striped rubber guards round the shaft's corners and a load chart; in the mess a chalkboard menu; in
 * the bunk room warm fairy lights strung across the glass; in the greenhouse ivy climbing the corners and trailing
 * along the top; at the pool a life ring on a hook; in the club glowing neon tubes running up the glass, pink and cyan;
 * and in the lab a whiteboard of a molecule and a graph.
 *
 * <p>Each piece goes in the ring of cells against the shaft's glass and its corner girders, the walkway round it, which
 * stays walkable as nothing collides with them: only in open cells, never where a poster, the lift's door or its call
 * button, or anything else stands, and on the shaft's front only before its corner girders, as the doorway, the lift's
 * call button and the LED panel beside it have the middle three. A piece of several blocks is laid whole or not at all.
 * Every block touches the shaft face to face, so it is assembled with her. Laid after the posters, which have the first
 * pick of the shaft's faces, and before the LED strips, whose panel by each lift door is kept clear.
 */
final class RocketShaftDecor implements RocketPiece {
    /** How far out from her axis the cells against the shaft's walls are, in blocks: the walkway's ring round it. */
    private static final int OUT = RocketLayout.SHAFT + 1;
    /**
     * How far to either side of a face's middle its cells reach, in blocks, the last before its corner girders; and how
     * far its glass reaches between them.
     */
    private static final int CORNER = RocketLayout.SHAFT;
    private static final int GLASS = CORNER - 1;
    /**
     * How far to either side of the front's middle the lift's doorway, its call button and LED panel reach, in blocks.
     */
    private static final int LIFT_HALF = 1;
    /** How many rows over the floor a board's lower row is: at eye height, as the lift's boards are. */
    private static final int BOARD_UP = 2;
    /** How many rows over the floor the pool's life rings hang, at chest height. */
    private static final int RING_UP = 2;
    /** How many rows of the cargo bay's corner guards stand over its floor. */
    private static final int GUARD_ROWS = 2;
    /**
     * How many rows over the floor the bunk room's fairy lights are strung, over the heads of anyone walking past, and
     * how many under the ceiling at least, clear of her girders and anything hung off them.
     */
    private static final int STRING_UP = 6;
    private static final int STRING_UNDER = 3;
    /** How many rows under the ceiling the greenhouse's ivy stops, clear of her girders running under it. */
    private static final int IVY_UNDER = 1;
    /** The fewest rows a curtain, a column of ivy or a neon tube stands, from the floor up, to be laid at all. */
    private static final int SHORTEST = 4;
    /** How far up a curtain is tied back, as a share of its height over its hem. */
    private static final double TIEBACK = 1.0 / 3;
    /** The places across a face where its corner columns stand, and where the club's tubes do. */
    private static final int[] CORNERS = {-CORNER, CORNER};
    private static final int[] TUBES = {-CORNER, -GLASS, GLASS, CORNER};
    /** Each furnished story's decoration, by the story's index from the entry story up. */
    private static final Map<Integer, Kind> BY_STORY = Map.of(
        RocketEntry.STORY, Kind.ENTRY,
        RocketCargoHold.STORY, Kind.CARGO,
        RocketMess.STORY, Kind.MESS,
        RocketBunks.STORY, Kind.BUNKS,
        RocketGreenhouse.STORY, Kind.GREENHOUSE,
        RocketPool.STORY, Kind.POOL,
        RocketClub.STORY, Kind.CLUB,
        RocketLab.STORY, Kind.LAB,
        RocketObservationDeck.STORY, Kind.OBSERVATION);

    private final RocketLayout layout;

    RocketShaftDecor(RocketLayout layout) {
        this.layout = layout;
    }

    /** One of the shaft's four faces on a story: the way it looks out, the story's floor's row and its ceiling's. */
    private record Face(Direction facing, int floor, int ceiling) {
    }

    /** A block of a piece to lay on a face: so far to the viewer's right of its middle, at a row, its row of it. */
    private record Piece(int along, int y, Row row) {
    }

    @Override
    public boolean wanted() {
        return layout.rooms();
    }

    @Override
    public void build() {
        List<Integer> floors = layout.floors();
        for (int story = 0; story < floors.size(); story++) {
            Kind kind = BY_STORY.get(story);
            if (kind != null) {
                story(kind, story, floors.get(story));
            }
        }
    }

    /** A story's decoration on each of the shaft's faces, under the story's ceiling, the observation deck's its own. */
    private void story(Kind kind, int story, int floor) {
        int ceiling = RocketObservationDeck.ceilingOf(layout, story, floor, layout.storyTop(story) + 1);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            dress(new Face(facing, floor, ceiling), kind);
        }
    }

    /** A face of the shaft dressed as its story's room has it. */
    private void dress(Face face, Kind kind) {
        switch (kind) {
            case ENTRY, MESS, LAB -> board(face, kind);
            case CARGO -> {
                guards(face);
                board(face, kind);
            }
            case BUNKS -> fairyLights(face);
            case GREENHOUSE -> ivy(face);
            case POOL -> lifeRing(face);
            case CLUB -> neon(face);
            case OBSERVATION -> curtains(face);
        }
    }

    /**
     * A board across the face's middle three cells over the glass, two rows tall from eye height: where it is seen,
     * with open air out in front of it.
     */
    private void board(Face face, Kind kind) {
        int low = face.floor() + BOARD_UP;
        if (!RocketPosters.inView(layout, face.facing(), low)) {
            return;
        }
        List<Piece> pieces = new ArrayList<>();
        for (int along = -GLASS; along <= GLASS; along++) {
            pieces.add(new Piece(along, low, Row.BOTTOM));
            pieces.add(new Piece(along, low + 1, Row.TOP));
        }
        lay(face, kind, pieces);
    }

    /** The cargo bay's rubber guards round the shaft's corners, before each corner girder, from the floor up. */
    private void guards(Face face) {
        for (int along : CORNERS) {
            if (reach(face, along, face.floor() + GUARD_ROWS) == GUARD_ROWS) {
                column(face, Kind.CARGO, along, new Row[]{Row.BOTTOM, Row.TOP});
            }
        }
    }

    /**
     * The bunk room's fairy lights strung across the whole face over head height, the strands hanging from them in the
     * row under them.
     */
    private void fairyLights(Face face) {
        int y = Math.min(face.floor() + STRING_UP, face.ceiling() - STRING_UNDER);
        List<Piece> pieces = new ArrayList<>();
        for (int along = -CORNER; along <= CORNER; along++) {
            pieces.add(new Piece(along, y, Row.TOP));
            pieces.add(new Piece(along, y - 1, Row.UPPER));
        }
        lay(face, Kind.BUNKS, pieces);
    }

    /** The pool's life ring, on its hook in the middle of the face at chest height, where it is seen. */
    private void lifeRing(Face face) {
        int y = face.floor() + RING_UP;
        if (RocketPosters.inView(layout, face.facing(), y)) {
            lay(face, Kind.POOL, List.of(new Piece(0, y, Row.MIDDLE)));
        }
    }

    /** The club's neon tubes, each running up the face from the floor to the ceiling, or as far up as it is open. */
    private void neon(Face face) {
        for (int along : TUBES) {
            int rows = reach(face, along, face.ceiling() - 1);
            if (rows >= SHORTEST) {
                column(face, Kind.CLUB, along, upRows(rows));
            }
        }
    }

    /**
     * The greenhouse's ivy: climbing up each corner girder from the floor, a row clear of the ceiling, and trailing
     * across the glass between them along their top, its strands hanging into the row under it wherever that is free.
     */
    private void ivy(Face face) {
        int headed = framed(face, Kind.GREENHOUSE, face.ceiling() - 1 - IVY_UNDER, RocketShaftDecor::upRows);
        int y = face.floor() + headed - 1;
        for (int along = -GLASS; along <= GLASS && headed > 0; along++) {
            if (free(face, along, y)) {
                put(face, Kind.GREENHOUSE, new Piece(along, y, Row.UPPER));
            }
        }
    }

    /**
     * The observation deck's curtains: drawn back before each corner girder from the floor to the ceiling, tied back a
     * third of the way up, under a valance across the glass between them.
     */
    private void curtains(Face face) {
        framed(face, Kind.OBSERVATION, face.ceiling() - 1, RocketShaftDecor::curtainRows);
    }

    /**
     * A frame round a face's glass: a column up each corner girder from the floor, its rows named from how many there
     * are, and across the glass between them in their top row, the frame's header. The
     * columns stand as tall as both reach under the highest row given, but lower, to the highest row at which the
     * header fits, if that leaves them {@link #SHORTEST} rows; nothing where they reach fewer. How many rows tall the
     * columns stand under the header, or none where it has none.
     */
    private int framed(Face face, Kind kind, int highest, IntFunction<Row[]> named) {
        int rows = Math.min(reach(face, -CORNER, highest), reach(face, CORNER, highest));
        if (rows < SHORTEST) {
            return 0;
        }
        int headed = headed(face, rows);
        for (int along : CORNERS) {
            column(face, kind, along, named.apply(headed > 0 ? headed : rows));
        }
        for (int along = -GLASS; along <= GLASS && headed > 0; along++) {
            put(face, kind, new Piece(along, face.floor() + headed, Row.TOP));
        }
        return headed;
    }

    /**
     * How many rows tall a frame's columns of so many rows at most stand under its header: the most, at least
     * {@link #SHORTEST}, at whose top the face's cells over its glass are all free; none where there is no such row.
     */
    private int headed(Face face, int rows) {
        for (int tall = rows; tall >= SHORTEST; tall--) {
            if (acrossFree(face, face.floor() + tall)) {
                return tall;
            }
        }
        return 0;
    }

    /** Whether every cell of a face over its glass, between its corner girders, is free at a row. */
    private boolean acrossFree(Face face, int y) {
        for (int along = -GLASS; along <= GLASS; along++) {
            if (!free(face, along, y)) {
                return false;
            }
        }
        return true;
    }

    /** The rows of a column so many tall, from the bottom up: its bottom, its middle rows and its top. */
    private static Row[] upRows(int rows) {
        Row[] named = new Row[rows];
        for (int row = 0; row < named.length; row++) {
            named[row] = row == 0 ? Row.BOTTOM : row == named.length - 1 ? Row.TOP : Row.MIDDLE;
        }
        return named;
    }

    /**
     * The rows of a curtain so many tall, from the bottom up: its hem, its fall under its tieback, the tieback a
     * {@link #TIEBACK} of the way up, its fall over it, and its valance at the top.
     */
    private static Row[] curtainRows(int rows) {
        Row[] named = upRows(rows);
        int tie = (int) Math.max(1, Math.round((rows - 1) * TIEBACK));
        for (int row = 1; row < named.length - 1; row++) {
            named[row] = row < tie ? Row.LOWER : row == tie ? Row.MIDDLE : Row.UPPER;
        }
        return named;
    }

    /** A column of a piece from the floor up at a place across the face, its rows named from the bottom up. */
    private void column(Face face, Kind kind, int along, Row[] rows) {
        for (int row = 0; row < rows.length; row++) {
            put(face, kind, new Piece(along, face.floor() + 1 + row, rows[row]));
        }
    }

    /** Lays every block of a piece where all of them are free, and none where any is not; whether it did. */
    private boolean lay(Face face, Kind kind, List<Piece> pieces) {
        if (!pieces.stream().allMatch(piece -> free(face, piece.along(), piece.y()))) {
            return false;
        }
        pieces.forEach(piece -> put(face, kind, piece));
        return true;
    }

    private void put(Face face, Kind kind, Piece piece) {
        BlockPos at = at(face, piece.along(), piece.y());
        BlockState state = SkylinersRegistry.SHAFT_DECOR.get().piece(kind, face.facing(), Along.of(piece.along()), piece.row());
        layout.set(at.getX(), at.getY(), at.getZ(), state);
    }

    /**
     * How many rows up a column of a face's cells is free from the floor, as far as the highest row given at most.
     */
    private int reach(Face face, int along, int highest) {
        int rows = 0;
        while (face.floor() + 1 + rows <= highest && free(face, along, face.floor() + 1 + rows)) {
            rows++;
        }
        return rows;
    }

    /**
     * Whether a piece can go in a cell of a face: open air inside her with the shaft's glass or a corner girder behind
     * it, and on the front not in its middle three, the lift's.
     */
    private boolean free(Face face, int along, int y) {
        if (face.facing() == Direction.NORTH && Math.abs(along) <= LIFT_HALF) {
            return false;
        }
        BlockPos at = at(face, along, y);
        BlockPos behind = at.relative(face.facing().getOpposite());
        BlockState wall = layout.stateAt(behind.getX(), behind.getY(), behind.getZ());
        return layout.isOpen(at.getX(), y, at.getZ()) && layout.inside(at.getX(), y, at.getZ())
            && (wall.is(SkylinersRegistry.LIFT_GLASS.get()) || wall.is(SkylinersRegistry.STEEL_GIRDER.get()));
    }

    /** The cell of a face so many blocks to the viewer's right of its middle, at a row: just outside the shaft. */
    private static BlockPos at(Face face, int along, int y) {
        Direction facing = face.facing();
        Direction right = ShaftDecorBlock.right(facing);
        return new BlockPos(facing.getStepX() * OUT + right.getStepX() * along, y, facing.getStepZ() * OUT + right.getStepZ() * along);
    }
}
