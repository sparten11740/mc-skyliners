package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.GirderDecorBlock;
import dev.skyliners.block.GirderDecorBlock.Kind;
import dev.skyliners.block.GirderDecorBlock.Part;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * What hangs under the steel girders across her rooms' ceilings (see {@link GirderDecorBlock}), each room's own: in the
 * entry, mission-patch banners every other block; in the cargo bay, a chain hoist every third; in the mess, racks of
 * copper pots and pans two blocks long, a block apart; in the bunk room, warm fairy lights looping the whole way along;
 * in the greenhouse, a hanging basket every third block; at the pool, bunting the whole way along; in the club,
 * spotlights every other block, pink and cyan by turns; and in the lab, a cable tray the whole way along. Each hangs in
 * the block under a girder, touching it face to face, so it is assembled with her, and only where that block is open;
 * nothing is hung right by the shaft, whose glass is left to what is fixed to it. Laid straight after the girders (see
 * {@link RocketGirders}), in a row clear of what is laid after it: the wall washers lower down her wall, the posters on
 * the shaft and the LED strips along her skin under the ceiling.
 */
final class RocketGirderDecor implements RocketPiece {
    /**
     * How far out from her axis the first block anything hangs in is, in blocks: one clear of the shaft's glass, the
     * girder's first block over it left bare.
     */
    private static final int FROM = RocketLayout.SHAFT + 2;
    /** How each furnished story's girders are hung, by the story's index from the entry story up. */
    private static final Map<Integer, Hanging> BY_STORY = Map.of(
        RocketEntry.STORY, new Hanging(Kind.MISSION_PATCHES, 2, 1),
        RocketCargoHold.STORY, new Hanging(Kind.CHAIN_HOIST, 3, 1),
        RocketMess.STORY, new Hanging(Kind.POT_RACK, 3, 2),
        RocketBunks.STORY, new Hanging(Kind.FAIRY_LIGHTS, 2, 2),
        RocketGreenhouse.STORY, new Hanging(Kind.HANGING_BASKET, 3, 1),
        RocketPool.STORY, new Hanging(Kind.BUNTING, 2, 2),
        RocketClub.STORY, new Hanging(Kind.SPOTLIGHT, 2, 1),
        RocketLab.STORY, new Hanging(Kind.CABLE_TRAY, 1, 1));

    /**
     * How a room's girders are hung: with its kind, from {@link #FROM} every so many blocks out to her skin, each one
     * or two blocks long (a pair, see {@link Part}); one block long, each takes the other look of the pair from the
     * last.
     */
    private record Hanging(Kind kind, int every, int length) {
    }

    private final RocketLayout layout;

    RocketGirderDecor(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return layout.rooms();
    }

    @Override
    public void build() {
        for (Map.Entry<Integer, Hanging> room : BY_STORY.entrySet()) {
            if (room.getKey() < layout.floors().size()) {
                ceiling(room.getValue(), layout.storyTop(room.getKey()));
            }
        }
    }

    /** A story's decorations, under its girders in its highest row, out along each of the four ways. */
    private void ceiling(Hanging hanging, int top) {
        for (Direction way : Direction.Plane.HORIZONTAL) {
            int last = lastUnderGirder(way, top);
            int places = 0;
            for (int out = FROM; out + hanging.length() - 1 <= last; out += hanging.every()) {
                hang(hanging, way, top - 1, out, places++);
            }
        }
    }

    /**
     * How far out from her axis along a way the girder in a story's highest row reaches, in blocks: the last of an
     * unbroken run of it from {@link #FROM} out; short of {@link #FROM} where there is none.
     */
    private int lastUnderGirder(Direction way, int top) {
        int out = FROM;
        while (girderAt(new BlockPos(0, top, 0).relative(way, out))) {
            out++;
        }
        return out - 1;
    }

    private boolean girderAt(BlockPos at) {
        return layout.stateAt(at.getX(), at.getY(), at.getZ()).is(SkylinersRegistry.STEEL_GIRDER.get());
    }

    /**
     * Hangs a decoration along a way so many blocks out, in the row given, where every block of it is open; which look
     * it takes goes by how many places for one come before it along that way.
     */
    private void hang(Hanging hanging, Direction way, int y, int out, int before) {
        BlockPos start = new BlockPos(0, y, 0).relative(way, out);
        for (int along = 0; along < hanging.length(); along++) {
            BlockPos at = start.relative(way, along);
            if (!layout.isOpen(at.getX(), at.getY(), at.getZ())) {
                return;
            }
        }
        GirderDecorBlock decor = SkylinersRegistry.GIRDER_DECOR.get();
        for (int along = 0; along < hanging.length(); along++) {
            BlockPos at = start.relative(way, along);
            layout.set(at.getX(), at.getY(), at.getZ(), decor.hung(hanging.kind(), way.getAxis(), part(hanging, way, along, before)));
        }
    }

    /**
     * Which of a pair a block of a decoration along a way is, so many blocks out along it from the decoration's first,
     * with so many places for one before it along that way: of a pair, the first is the block further west or north;
     * one a block long takes the first look and the second by turns; one whose pair does not differ, the first.
     */
    private static Part part(Hanging hanging, Direction way, int along, int before) {
        if (!hanging.kind().twoLooks()) {
            return Part.FIRST;
        }
        if (hanging.length() == 1) {
            return before % 2 == 0 ? Part.FIRST : Part.SECOND;
        }
        boolean runsEastOrSouth = way.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        return (along == 0) == runsEastOrSouth ? Part.FIRST : Part.SECOND;
    }
}
