package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.ClimbingRopeBlock;
import dev.aerolarge.block.SpeakerBlock;
import dev.aerolarge.block.TechnoDeckBlock;
import dev.aerolarge.block.TechnoDeckBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The secret techno room: a black box hung under the gangway towards the stern, between two rings, with no door at
 * all; the way in is a secret hatch in the gangway's grating, just like the rest of it, a rope down through it to the
 * room's floor (see {@link dev.aerolarge.block.SecretHatchBlock}). Inside, a dance floor of black glass tiles, big
 * speakers along both walls, a disco ball over the middle and, at the forward end, the DJ deck that plays the room
 * (see {@link TechnoDeckBlock}). It hangs from the gangway face to face, every block on the next, so it is assembled
 * with the zeppelin; it is well clear of the gas cells over the gangway.
 */
final class ZeppelinTechnoRoom implements ZeppelinPiece {
    /**
     * The room inside its walls, in blocks: how far either side of the middle, how deep under the gangway, and where
     * along.
     */
    private static final int HALF_WIDTH = 5;
    private static final int DEPTH = 7;
    private static final int FRONT = 331;
    private static final int BACK = 345;
    /**
     * How far aft of the room's front the hatch is, and how far apart the speakers stand along the walls, in blocks.
     */
    private static final int HATCH_AFT = 12;
    private static final int SPEAKER_SPACING = 3;

    private final ZeppelinLayout layout;

    ZeppelinTechnoRoom(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return AerolargeConfig.TECHNO_ROOM.get();
    }

    @Override
    public void build() {
        int ceiling = layout.gangwayRow() - 1;
        int floor = ceiling - DEPTH;
        if (!clear(floor, ceiling)) {
            return;
        }
        shell(floor, ceiling);
        BlockState black = AerolargeRegistry.DANCE_FLOOR.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-HALF_WIDTH, floor, FRONT, HALF_WIDTH, floor, BACK)) {
            layout.set(at.getX(), at.getY(), at.getZ(), black);
        }
        speakers(floor);
        layout.set(0, ceiling - 1, (FRONT + BACK) / 2, AerolargeRegistry.DISCO_BALL.get().defaultBlockState());
        BlockPos deck = new BlockPos(0, floor + 1, FRONT + 1);
        layout.set(deck.getX(), deck.getY(), deck.getZ(), AerolargeRegistry.TECHNO_DECK.get().defaultBlockState().setValue(TechnoDeckBlock.FACING, Direction.SOUTH));
        BlockPos from = new BlockPos(-HALF_WIDTH, floor + 1, FRONT).subtract(deck);
        BlockPos to = new BlockPos(HALF_WIDTH, ceiling - 1, BACK).subtract(deck);
        layout.fit(deck, (blockEntity, rotation) -> {
            if (blockEntity instanceof TechnoDeckBlockEntity techno) {
                techno.room(from.rotate(rotation), to.rotate(rotation));
            }
        });
        hatch(floor, ceiling);
    }

    /** Whether nothing stands where the room would, walls and all, under the gangway. */
    private boolean clear(int floor, int ceiling) {
        for (BlockPos at : BlockPos.betweenClosed(-HALF_WIDTH - 1, floor, FRONT - 1, HALF_WIDTH + 1, ceiling, BACK + 1)) {
            if (!layout.isOpen(at.getX(), at.getY(), at.getZ())) {
                return false;
            }
        }
        return true;
    }

    /** The room's black walls and ceiling round it, its inside laid as air, so nothing else is laid there. */
    private void shell(int floor, int ceiling) {
        BlockState wall = AerolargeRegistry.CLUB_WALL.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-HALF_WIDTH - 1, floor, FRONT - 1, HALF_WIDTH + 1, ceiling, BACK + 1)) {
            boolean edge = Math.abs(at.getX()) == HALF_WIDTH + 1 || at.getZ() == FRONT - 1 || at.getZ() == BACK + 1 || at.getY() == ceiling;
            layout.set(at.getX(), at.getY(), at.getZ(), edge ? wall : Blocks.AIR.defaultBlockState());
        }
    }

    /** Speaker cabinets two high along both walls, facing in, every few blocks. */
    private void speakers(int floor) {
        BlockState speaker = AerolargeRegistry.SPEAKER.get().defaultBlockState();
        for (int z = FRONT + 2; z <= BACK - 1; z += SPEAKER_SPACING) {
            for (int y = floor + 1; y <= floor + 2; y++) {
                layout.set(-HALF_WIDTH, y, z, speaker.setValue(SpeakerBlock.FACING, Direction.EAST));
                layout.set(HALF_WIDTH, y, z, speaker.setValue(SpeakerBlock.FACING, Direction.WEST));
            }
        }
    }

    /** The secret hatch in the gangway over the room, and the rope from it down through the ceiling to the floor. */
    private void hatch(int floor, int ceiling) {
        int z = FRONT + HATCH_AFT;
        layout.set(0, layout.gangwayRow(), z, AerolargeRegistry.SECRET_HATCH.get().defaultBlockState());
        for (int y = floor + 1; y <= ceiling; y++) {
            layout.set(0, y, z, AerolargeRegistry.CLIMBING_ROPE.get().defaultBlockState().setValue(ClimbingRopeBlock.UP, y < ceiling)
                .setValue(ClimbingRopeBlock.DOWN, y > floor + 1));
        }
    }
}
