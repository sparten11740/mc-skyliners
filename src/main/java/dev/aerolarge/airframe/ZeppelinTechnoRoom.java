package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.ClimbingRopeBlock;
import dev.aerolarge.block.SpeakerBlock;
import dev.aerolarge.block.TechnoDeckBlock;
import dev.aerolarge.block.TechnoDeckBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The secret techno room: a black box hung under the gangway towards the stern, between two rings, with no door at
 * all; the way in is a secret hatch in the gangway's grating, just like the rest of it, a rope down through it to the
 * room's floor (see {@link dev.aerolarge.block.SecretHatchBlock}). Inside, a dance floor of black glass tiles, big
 * speakers along both walls, a disco ball over the middle and, at the forward end, the DJ deck that plays the room
 * (see {@link TechnoDeckBlock}). It hangs from the gangway face to face, every block on the next, so it is assembled
 * with the zeppelin; it is well clear of the gas cells over the gangway. It is as big at every size, but where the
 * bay between the gangway's posts is too short for it, on a smaller hull, it is cut short to fill the bay, as a ring
 * passing through it would break it; and it is left out where it would not lie wholly inside the hull.
 */
final class ZeppelinTechnoRoom implements ZeppelinPiece {
    /**
     * The room inside its walls: how far either side of the middle and how deep under the gangway, in blocks; where its
     * middle is, how far aft of the nose's tip, in metres along the hull; and how long it is at most, in blocks.
     */
    private static final int HALF_WIDTH = 5;
    private static final int DEPTH = 7;
    private static final double MIDDLE = 169;
    private static final int LONGEST = 15;
    /**
     * How far forward of the room's back the hatch is, and how far apart the speakers stand along the walls, in blocks.
     */
    private static final int HATCH_FORWARD = 2;
    private static final int SPEAKER_SPACING = 3;

    private final ZeppelinLayout layout;

    ZeppelinTechnoRoom(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.TECHNO_ROOM.get();
    }

    /** At every size, in the bay its middle falls in. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        Span span = span();
        int ceiling = layout.gangwayRow() - 1;
        int floor = ceiling - DEPTH;
        if (span.back() < span.front() || !clear(span, floor, ceiling)) {
            return;
        }
        shell(span, floor, ceiling);
        BlockState black = AerolargeRegistry.DANCE_FLOOR.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-HALF_WIDTH, floor, span.front(), HALF_WIDTH, floor, span.back())) {
            layout.set(at.getX(), at.getY(), at.getZ(), black);
        }
        speakers(span, floor);
        layout.set(0, ceiling - 1, (span.front() + span.back()) / 2, AerolargeRegistry.DISCO_BALL.get().defaultBlockState());
        BlockPos deck = new BlockPos(0, floor + 1, span.front() + 1);
        layout.set(deck.getX(), deck.getY(), deck.getZ(), AerolargeRegistry.TECHNO_DECK.get().defaultBlockState().setValue(TechnoDeckBlock.FACING, Direction.SOUTH));
        BlockPos from = new BlockPos(-HALF_WIDTH, floor + 1, span.front()).subtract(deck);
        BlockPos to = new BlockPos(HALF_WIDTH, ceiling - 1, span.back()).subtract(deck);
        layout.fit(deck, (blockEntity, rotation) -> {
            if (blockEntity instanceof TechnoDeckBlockEntity techno) {
                techno.room(from.rotate(rotation), to.rotate(rotation));
            }
        });
        hatch(span, floor, ceiling);
    }

    /** The rows the room's inside runs from and to along the hull. */
    private record Span(int front, int back) {
    }

    /**
     * Where along the hull the room's inside runs: round its middle, as long as it is, but inside the bay between the
     * gangway's posts it falls in, with a wall at either end clear of them.
     */
    private Span span() {
        int middle = (int) Math.round(MIDDLE * layout.scale());
        int fore = Integer.MIN_VALUE;
        int aft = Integer.MAX_VALUE;
        for (int ring = 0; ring < ZeppelinSize.rings(); ring++) {
            int post = (int) Math.floor(layout.hullSize().ringAt(ring));
            fore = post <= middle ? Math.max(fore, post) : fore;
            aft = post > middle ? Math.min(aft, post) : aft;
        }
        int length = Math.min(LONGEST, aft - fore - 3);
        int front = Mth.clamp(middle - (length - 1) / 2, fore + 2, aft - 2 - (length - 1));
        return new Span(front, front + length - 1);
    }

    /** Whether nothing stands where the room would, walls and all, under the gangway, and all of it is in the hull. */
    private boolean clear(Span span, int floor, int ceiling) {
        for (BlockPos at : BlockPos.betweenClosed(-HALF_WIDTH - 1, floor, span.front() - 1, HALF_WIDTH + 1, ceiling, span.back() + 1)) {
            if (!layout.isOpen(at.getX(), at.getY(), at.getZ()) || !layout.inHull(at.getX(), at.getY(), at.getZ())) {
                return false;
            }
        }
        return true;
    }

    /** The room's black walls and ceiling round it, its inside laid as air, so nothing else is laid there. */
    private void shell(Span span, int floor, int ceiling) {
        BlockState wall = AerolargeRegistry.CLUB_WALL.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-HALF_WIDTH - 1, floor, span.front() - 1, HALF_WIDTH + 1, ceiling, span.back() + 1)) {
            boolean edge = Math.abs(at.getX()) == HALF_WIDTH + 1 || at.getZ() == span.front() - 1 || at.getZ() == span.back() + 1 || at.getY() == ceiling;
            layout.set(at.getX(), at.getY(), at.getZ(), edge ? wall : Blocks.AIR.defaultBlockState());
        }
    }

    /** Speaker cabinets two high along both walls, facing in, every few blocks. */
    private void speakers(Span span, int floor) {
        BlockState speaker = AerolargeRegistry.SPEAKER.get().defaultBlockState();
        for (int z = span.front() + 2; z <= span.back() - 1; z += SPEAKER_SPACING) {
            for (int y = floor + 1; y <= floor + 2; y++) {
                layout.set(-HALF_WIDTH, y, z, speaker.setValue(SpeakerBlock.FACING, Direction.EAST));
                layout.set(HALF_WIDTH, y, z, speaker.setValue(SpeakerBlock.FACING, Direction.WEST));
            }
        }
    }

    /** The secret hatch in the gangway over the room, and the rope from it down through the ceiling to the floor. */
    private void hatch(Span span, int floor, int ceiling) {
        int z = span.back() - HATCH_FORWARD;
        layout.set(0, layout.gangwayRow(), z, AerolargeRegistry.SECRET_HATCH.get().defaultBlockState());
        for (int y = floor + 1; y <= ceiling; y++) {
            layout.set(0, y, z, AerolargeRegistry.CLIMBING_ROPE.get().defaultBlockState().setValue(ClimbingRopeBlock.UP, y < ceiling)
                .setValue(ClimbingRopeBlock.DOWN, y > floor + 1));
        }
    }
}
