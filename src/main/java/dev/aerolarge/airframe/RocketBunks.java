package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.BunkBlock;
import dev.aerolarge.block.CrewLockerBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;

/**
 * The bunk room: bunk beds two high against her skin between the windows, a pair at each of the four diagonals, their
 * reading lights on and their blankets each in a colour of their own; and crew lockers either side of the way out
 * and of the back window, one for each bunk's sleeper.
 */
final class RocketBunks extends RocketRoom {
    /** Her bunk room's story, from the entry story up. */
    static final int STORY = 3;
    /** How far in from the skin's inside the bunks and the lockers stand, in blocks. */
    private static final double ALONG_SKIN = 2.5;
    /** How far round either side of each diagonal the bunks of its pair are, in degrees. */
    private static final double PAIR_APART = 11;
    /** How far round from her front the lockers are, in degrees: either side of the way out and of the back window. */
    private static final double[] LOCKERS = {22, 338, 158, 202};
    /** The blankets' colours, taken in turn. */
    private static final DyeColor[] BLANKETS = {DyeColor.BLUE, DyeColor.GRAY, DyeColor.CYAN, DyeColor.LIGHT_BLUE};

    private int blankets;

    RocketBunks(RocketLayout layout) {
        super(layout, STORY);
    }

    @Override
    public void build() {
        int y = floor() + 1;
        double out = wall() - ALONG_SKIN;
        for (double diagonal = 45; diagonal < 360; diagonal += 90) {
            for (int side = -1; side <= 1; side += 2) {
                double round = diagonal + side * PAIR_APART;
                bunkBed(across(out, round), y, along(out, round), inward(round));
            }
        }
        for (double round : LOCKERS) {
            put(across(out, round), y, along(out, round), AerolargeRegistry.CREW_LOCKER.get().defaultBlockState()
                .setValue(CrewLockerBlock.FACING, inward(round)));
        }
    }

    /** A bunk bed facing the way given, its head at the block and its foot beside it, and a second bunk over it. */
    private void bunkBed(int x, int y, int z, Direction facing) {
        Direction foot = BunkBlock.footward(facing);
        int footX = x + foot.getStepX();
        int footZ = z + foot.getStepZ();
        for (int bunk = y; bunk <= y + 1; bunk++) {
            if (!free(x, bunk, z) || !free(footX, bunk, footZ)) {
                return;
            }
            BlockState head = AerolargeRegistry.BUNK.get().defaultBlockState().setValue(BunkBlock.FACING, facing)
                .setValue(BunkBlock.COLOR, BLANKETS[blankets++ % BLANKETS.length]).setValue(BunkBlock.LIT, true);
            layout.set(x, bunk, z, head);
            layout.set(footX, bunk, footZ, head.setValue(BunkBlock.PART, BedPart.FOOT));
        }
    }
}
