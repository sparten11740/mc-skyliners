package dev.aerolarge.airframe;

import static dev.aerolarge.airframe.ZeppelinShape.*;

import dev.aerolarge.AerolargeRegistry;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Inside a balloon: a climbing rope up from the gangway through an open mouth in the floor of a gas cell just forward
 * of the middle, one of the biggest, into its hot air, to a little railed gallery among its glowing burners, lit at its
 * corners, the rope hung from a gantry over it. Up to the cell's floor the rope climbs in a cage of girders, as the Sky
 * Shaft's does. The hot air stays put over the open mouth, as a real hot air balloon's does over its open throat, so
 * the cell lifts as before. The gallery stands on posts down to the cell's floor, the rope's foot on the gangway, all
 * of them this mod's own blocks joined face to face, so they are assembled with the zeppelin.
 */
final class ZeppelinCellGallery implements ZeppelinPiece {
    /** Which gas cell the rope climbs into: one of the biggest, just forward of the middle. */
    private static final int CELL = 7;
    /** How far out from the middle the rope hangs, in blocks: beside the gangway, past its rail, between burners. */
    private static final int ROPE_OUT = 2;
    /** How far over the cell's gas floor the gallery stands, in blocks: over its burners. */
    private static final int GALLERY_RISE = 4;
    /** How high over the gallery the gantry's beam is, in blocks, the rope hanging from it: over a climber's head. */
    private static final int GANTRY_HEIGHT = 4;

    private final ZeppelinLayout layout;

    ZeppelinCellGallery(ZeppelinLayout layout) {
        this.layout = layout;
    }

    /** Lays the gallery and its rope, once the cells and the gangway are laid. */
    @Override
    public void build() {
        int z = (int) Math.floor(ZeppelinShape.Cell.of(CELL).middle() * SCALE);
        int walkway = layout.gangwayRow();
        int gallery = gasFloor(z, walkway) + GALLERY_RISE;
        ZeppelinRopes.foot(layout, ROPE_OUT, z);
        for (int y = walkway + 1; !layout.inCell(CELL, ROPE_OUT, y, z); y++) {
            ZeppelinRopes.cageRow(layout, ROPE_OUT, y, z);
        }
        galleryFloor(z, gallery, AerolargeRegistry.ZEPPELIN_CATWALK.get().defaultBlockState());
        gantry(z, gallery);
        ZeppelinRopes.rope(layout, ROPE_OUT, z, gallery + GANTRY_HEIGHT - 1);
    }

    /** The lowest row of the cell's hot air over the rope, just over its envelope's floor. */
    private int gasFloor(int z, int walkway) {
        int y = walkway + 1;
        while (!layout.inGas(CELL, ROPE_OUT, y, z)) {
            y++;
        }
        return y;
    }

    /**
     * The gallery's grating round the rope, three blocks by three, a lamp in each corner, a rail round it and a post
     * from the middles of its port and starboard sides down to the cell's floor.
     */
    private void galleryFloor(int z, int gallery, BlockState catwalk) {
        BlockState lamp = AerolargeRegistry.CABIN_LIGHT.get().defaultBlockState();
        for (int x = ROPE_OUT - 1; x <= ROPE_OUT + 1; x++) {
            for (int along = z - 1; along <= z + 1; along++) {
                layout.set(x, gallery, along, x != ROPE_OUT && along != z ? lamp : catwalk);
            }
        }
        for (int at = -1; at <= 1; at++) {
            layout.railAlong(ROPE_OUT - 1, gallery, z + at, Direction.WEST);
            layout.railAlong(ROPE_OUT + 1, gallery, z + at, Direction.EAST);
            layout.railAlong(ROPE_OUT + at, gallery, z - 1, Direction.NORTH);
            layout.railAlong(ROPE_OUT + at, gallery, z + 1, Direction.SOUTH);
        }
        post(ROPE_OUT - 1, gallery, z);
        post(ROPE_OUT + 1, gallery, z);
    }

    /** A girder post from under the gallery down to the cell's envelope. */
    private void post(int x, int gallery, int z) {
        for (int y = gallery - 1; layout.inGas(CELL, x, y, z); y--) {
            layout.set(x, y, z, AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState());
        }
    }

    /** The gantry over the rope's top: a post either side of it and the beam across them, which the rope hangs from. */
    private void gantry(int z, int gallery) {
        BlockState girder = AerolargeRegistry.ZEPPELIN_GIRDER.get().defaultBlockState();
        for (int y = gallery + 1; y < gallery + GANTRY_HEIGHT; y++) {
            layout.set(ROPE_OUT, y, z - 1, girder);
            layout.set(ROPE_OUT, y, z + 1, girder);
        }
        for (int along = z - 1; along <= z + 1; along++) {
            layout.set(ROPE_OUT, gallery + GANTRY_HEIGHT, along, girder);
        }
    }
}
