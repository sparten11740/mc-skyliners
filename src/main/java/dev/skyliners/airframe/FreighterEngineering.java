package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.engineering.JumpDriveBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The freighter's engineering, the room astern of her core: her jump drive in its middle (see {@link JumpDriveBlock}),
 * facing in toward its doorway so whoever comes in meets its panel, with room all round it to watch its rings turn.
 * Laid where its block has her deck's floor under it and three rows open over it, with the row before it open to stand
 * in. Laid out nose north, as the rest of her; none with the server config's {@code freighter.engineering} off.
 */
final class FreighterEngineering {
    /** Where her jump drive stands: astern, so far out from her axis, in blocks, in the middle of the room. */
    private static final int DRIVE_OUT = 13;

    private FreighterEngineering() {
    }

    /** Lays engineering's machines in a freighter's layout whose deck's floor is the row given. */
    static void build(HullLayout layout, int deck) {
        if (!SkylinersConfig.FREIGHTER_ENGINEERING.get()) {
            return;
        }
        BlockPos drive = new BlockPos(0, deck + 1, DRIVE_OUT);
        if (standsFree(layout, deck, drive) && standsFree(layout, deck, drive.north())) {
            layout.set(drive.getX(), drive.getY(), drive.getZ(),
                SkylinersRegistry.JUMP_DRIVE.get().defaultBlockState().setValue(JumpDriveBlock.FACING, Direction.NORTH));
        }
    }

    /** Whether a block over her deck's floor has the floor under it and three rows open from it up. */
    private static boolean standsFree(HullLayout layout, int deck, BlockPos at) {
        boolean open = layout.isOpen(at.getX(), deck + 1, at.getZ()) && layout.isOpen(at.getX(), deck + 2, at.getZ())
            && layout.isOpen(at.getX(), deck + 3, at.getZ());
        return open && !layout.isOpen(at.getX(), deck, at.getZ());
    }
}
