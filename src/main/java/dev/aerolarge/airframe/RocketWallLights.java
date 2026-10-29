package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LightTone;
import dev.aerolarge.block.WallLightBlock;
import java.util.List;

/**
 * Wall washers round her rooms (see {@link WallLightBlock}): on every story with a wall of her cover to throw light
 * up, eight of them round it over head height, between her windows and the way to her lift, each fixed to her skin
 * where it stands at the room's edge; warm white, a cool blue in the bunk room and pink in the club. Not on the
 * observation deck, whose wall over its band of glass is all starlight, nor in the cockpit, glass all round. Laid
 * once the rooms have their furniture and before the LED strips, in cells along the skin the rooms keep clear.
 */
final class RocketWallLights implements RocketPiece {
    /** How far round from her front each of a story's wall washers is, in degrees: clear of the four ways. */
    private static final double[] ROUNDS = {30, 60, 120, 150, 210, 240, 300, 330};
    /** How many rows over its floor a story's wall washers are fixed: over the head of anyone walking past. */
    private static final int UP = 3;
    /** How many rows a wall washer needs open: its own and the one over it, for its light up the wall. */
    private static final int ROOM_ROWS = 2;
    /** The stories with no wall of cover to light: the observation deck. */
    private static final List<Integer> UNLIT = List.of(RocketObservationDeck.STORY);

    private final RocketLayout layout;

    RocketWallLights(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return layout.rooms();
    }

    @Override
    public void build() {
        List<Integer> floors = layout.floors();
        for (int story = 0; story < floors.size(); story++) {
            if (!UNLIT.contains(story)) {
                story(story, floors.get(story));
            }
        }
    }

    /** A story's wall washers, each where it can be fixed to her skin (see {@link SkinMount}). */
    private void story(int story, int floor) {
        LightTone tone = story == RocketBunks.STORY ? LightTone.COOL : story == RocketClub.STORY ? LightTone.PINK : LightTone.WARM;
        int y = floor + UP;
        for (double round : ROUNDS) {
            SkinMount mount = SkinMount.find(layout, round, y, ROOM_ROWS);
            if (mount != null) {
                layout.set(mount.at().getX(), y, mount.at().getZ(), AerolargeRegistry.WALL_LIGHT.get().shining(mount.facing(), tone));
            }
        }
    }
}
