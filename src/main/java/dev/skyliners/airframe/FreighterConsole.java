package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.console.ConsoleBlock;
import dev.skyliners.console.ConsoleBlockEntity;
import dev.skyliners.console.ConsoleShape;
import dev.skyliners.block.Mount;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's console (see {@link ConsoleBlock}): a half-round desk curving round her pilots' seats, its middle at
 * her control head between them, from in front of the pilot's seat round ahead of the head to in front of the
 * co-pilot's, every slice of it turned toward the head, so whichever seat she is flown from it faces her. Left to right
 * as they see it: the climb button right before the pilot (a bank of toggles with the server config's
 * {@code freighter.climbBoost} off), her flight readouts behind it, which need no touching, the boost button, the
 * scanner in the middle, the hyperspace jump button (her flight readouts again with {@code freighter.hyperspaceJump}
 * off), her engines, behind the next, and a bank of toggles right before the co-pilot. Each slice is a console module
 * of its own in the block its middle falls in, wired to her control head; one whose block is not open is left out.
 * Laid out nose north; none with the server config's {@code freighter.console} off.
 */
final class FreighterConsole {
    /** The ring's inner and outer circles round her control head's middle, in blocks: the desk's near and far edges. */
    private static final double INNER = 1.95;
    private static final double OUTER = 2.7;
    /**
     * How far round the ring runs either side of straight ahead, in degrees: to just in front of each pilot's seat,
     * which stand two blocks either side of the head.
     */
    private static final double SWEEP = 72;
    /** The modules round the ring, left to right as her pilots see them. */
    private static final List<Supplier<? extends Block>> MODULES = List.of(FreighterConsole::climbOrSwitches, SkylinersRegistry.CONSOLE_READOUTS,
        SkylinersRegistry.CONSOLE_BOOST, SkylinersRegistry.CONSOLE_SCANNER, FreighterConsole::jumpOrReadouts, SkylinersRegistry.CONSOLE_ENGINES,
        SkylinersRegistry.CONSOLE_SWITCHES);

    private FreighterConsole() {
    }

    /** The hyperspace jump's button, or her flight readouts where the server does not let her jump. */
    private static Block jumpOrReadouts() {
        return SkylinersConfig.FREIGHTER_HYPERSPACE_JUMP.get() ? SkylinersRegistry.CONSOLE_JUMP.get() : SkylinersRegistry.CONSOLE_READOUTS.get();
    }

    /** The climb boost's button, or a bank of toggles where the server does not let her climb so. */
    private static Block climbOrSwitches() {
        return SkylinersConfig.FREIGHTER_CLIMB_BOOST.get() ? SkylinersRegistry.CONSOLE_CLIMB.get() : SkylinersRegistry.CONSOLE_SWITCHES.get();
    }

    /** Lays her console round her control head, its block given, in a freighter's layout. */
    static void build(HullLayout layout, BlockPos head) {
        if (!SkylinersConfig.FREIGHTER_CONSOLE.get()) {
            return;
        }
        double step = 2 * SWEEP / MODULES.size();
        for (int slice = 0; slice < MODULES.size(); slice++) {
            double from = -SWEEP + step * slice;
            module(layout, head, slice, from, from + step);
        }
    }

    /**
     * One slice of the ring, between two bearings clockwise from her bow, its module set in the block its middle falls
     * in, where that is open, and told once it stands its slice and the way to her control head.
     */
    private static void module(HullLayout layout, BlockPos head, int slice, double from, double to) {
        double middleX = head.getX() + 0.5;
        double middleZ = head.getZ() + 0.5;
        double bearing = Math.toRadians((from + to) / 2);
        double out = (INNER + OUTER) / 2;
        BlockPos at = BlockPos.containing(middleX + out * Math.sin(bearing), head.getY(), middleZ - out * Math.cos(bearing));
        if (!layout.isOpen(at.getX(), at.getY(), at.getZ())) {
            return;
        }
        // Facing back toward the head, the way whoever works it stands.
        Direction facing = Direction.getNearest((float) -Math.sin(bearing), 0, (float) Math.cos(bearing));
        BlockState module = MODULES.get(slice).get().defaultBlockState().setValue(ConsoleBlock.FACING, facing).setValue(ConsoleBlock.MOUNT, Mount.FLOOR);
        layout.set(at.getX(), at.getY(), at.getZ(), module);
        ConsoleShape.Facet facet = new ConsoleShape.Facet(middleX - (at.getX() + 0.5), middleZ - (at.getZ() + 0.5), INNER, OUTER, from, to,
            slice == 0, slice == MODULES.size() - 1);
        BlockPos toHead = head.subtract(at);
        layout.fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof ConsoleBlockEntity console) {
                console.arc(facet, toHead, rotation);
            }
        });
    }
}
