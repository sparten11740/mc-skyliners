package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BarCounterBlock;
import dev.aerolarge.block.OvalWindowBlock;
import dev.aerolarge.block.SpeakerBlock;
import dev.aerolarge.block.TechnoDeckBlock;
import dev.aerolarge.block.TechnoDeckBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The techno club: its floor laid all over with the dance floor's black glass tiles round the shaft, speaker stacks
 * two high at the four diagonals facing in, a disco ball over each quarter of the floor, and behind the shaft at her
 * back the DJ deck that plays the room (see {@link TechnoDeckBlock}), facing the floor; along her left side, a bar
 * curving round the window there, stools before it; and its windows built at their darkest, so daylight does not wash
 * out the dance floor.
 */
final class RocketClub extends RocketRoom {
    /** Her techno club's story, from the entry story up. */
    static final int STORY = 6;
    /** How far in from the skin's inside the speakers stand, in blocks. */
    private static final double SPEAKERS_IN = 2.5;
    /** How far out from her axis the DJ deck and the disco balls are, in blocks. */
    private static final double DECK_OUT = 4.5;
    private static final double BALLS_OUT = 5;
    /** How far in from the skin's inside the bar's counter runs, and from and to how far round from her front. */
    private static final double BAR_IN = 3.5;
    private static final double BAR_FROM = 236;
    private static final double BAR_TO = 304;

    RocketClub(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        danceFloor();
        double out = wall() - SPEAKERS_IN;
        BlockState speaker = AerolargeRegistry.SPEAKER.get().defaultBlockState();
        for (double round = 45; round < 360; round += 90) {
            put(across(out, round), y, along(out, round), speaker.setValue(SpeakerBlock.FACING, inward(round)));
            put(across(out, round), y + 1, along(out, round), speaker.setValue(SpeakerBlock.FACING, inward(round)));
            put(across(BALLS_OUT, round), top(), along(BALLS_OUT, round), AerolargeRegistry.DISCO_BALL.get().defaultBlockState());
        }
        bar(y);
        deck(new BlockPos(0, y, (int) Math.round(DECK_OUT)));
        darken();
    }

    /** Its windows at their darkest, so that daylight does not wash out the dance floor's colours. */
    private void darken() {
        int reach = layout.reach();
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor() + 1, -reach, reach, top(), reach)) {
            BlockState state = layout.stateAt(at.getX(), at.getY(), at.getZ());
            if (state.is(AerolargeRegistry.OVAL_WINDOW.get())) {
                layout.set(at.getX(), at.getY(), at.getZ(), state.setValue(OvalWindowBlock.DIM, OvalWindowBlock.DARKEST));
            }
        }
    }

    /** The dance floor's tiles in place of every block of the room's floor. */
    private void danceFloor() {
        int reach = (int) Math.ceil(wall());
        BlockState tile = AerolargeRegistry.DANCE_FLOOR.get().defaultBlockState();
        for (BlockPos at : BlockPos.betweenClosed(-reach, floor(), -reach, reach, floor(), reach)) {
            if (layout.stateAt(at.getX(), at.getY(), at.getZ()).is(AerolargeRegistry.CABIN_FLOOR.get())) {
                layout.set(at.getX(), at.getY(), at.getZ(), tile);
            }
        }
    }

    /** The bar: its counter in a curve round her left side, facing in, and a stool before every other block of it. */
    private void bar(int y) {
        double out = wall() - BAR_IN;
        int stool = 0;
        for (BlockPos at : run(y, out, BAR_FROM, BAR_TO)) {
            Direction facing = inward(roundOf(at.getX(), at.getZ()));
            if (put(at.getX(), y, at.getZ(), AerolargeRegistry.BAR_COUNTER.get().defaultBlockState().setValue(BarCounterBlock.FACING, facing))
                && stool++ % 2 == 0) {
                put(at.getX() + facing.getStepX(), y, at.getZ() + facing.getStepZ(), AerolargeRegistry.BAR_STOOL.get().defaultBlockState());
            }
        }
    }

    /** The DJ deck at a block behind the shaft, facing the front, and the room it plays: the whole story. */
    private void deck(BlockPos deck) {
        if (!put(deck.getX(), deck.getY(), deck.getZ(), AerolargeRegistry.TECHNO_DECK.get().defaultBlockState().setValue(TechnoDeckBlock.FACING, Direction.NORTH))) {
            return;
        }
        int reach = (int) Math.ceil(wall());
        BlockPos from = new BlockPos(-reach, floor() + 1, -reach).subtract(deck);
        BlockPos to = new BlockPos(reach, top(), reach).subtract(deck);
        layout.fit(deck, (blockEntity, rotation) -> {
            if (blockEntity instanceof TechnoDeckBlockEntity techno) {
                techno.room(from.rotate(rotation), to.rotate(rotation));
            }
        });
    }
}
