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
 * {@code freighter.climbBoost} off), the slam button beside it, the spin button, the boost button, the hyperspace
 * jump button (her flight readouts with {@code freighter.hyperspaceJump} off), within reach of either seat, the
 * scanner, and her flight readouts right before the co-pilot, which need no touching. The ring holds seven slices at
 * most, each in a block of its own; with {@code freighter.spin} off there is no spin button, the scanner stands in the
 * middle and her engines' gauges behind the jump button. With {@code freighter.slamLanding} off, her flight readouts
 * stand beside the climb button and a bank of toggles before the co-pilot. Each slice is a console module of its own
 * in the block its middle falls in, wired to her control head; one whose block is not open is left out. Laid out nose
 * north; none with the server config's {@code freighter.console} off.
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
    /**
     * The modules round the ring, left to right as her pilots see them: with her spin button, or with her engines'
     * gauges in its place, where the server does not let her spin.
     */
    private static final List<Supplier<? extends Block>> MODULES = List.of(FreighterConsole::climbOrSwitches, FreighterConsole::slamOrReadouts,
        SkylinersRegistry.CONSOLE_SPIN, SkylinersRegistry.CONSOLE_BOOST, FreighterConsole::jumpOrReadouts, SkylinersRegistry.CONSOLE_SCANNER,
        FreighterConsole::readoutsOrSwitches);
    private static final List<Supplier<? extends Block>> MODULES_NO_SPIN = List.of(FreighterConsole::climbOrSwitches,
        FreighterConsole::slamOrReadouts, SkylinersRegistry.CONSOLE_BOOST, SkylinersRegistry.CONSOLE_SCANNER, FreighterConsole::jumpOrReadouts,
        SkylinersRegistry.CONSOLE_ENGINES, FreighterConsole::readoutsOrSwitches);

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

    /** The slam landing's button, or her flight readouts where the server does not let her slam down. */
    private static Block slamOrReadouts() {
        return SkylinersConfig.FREIGHTER_SLAM_LANDING.get() ? SkylinersRegistry.CONSOLE_SLAM.get() : SkylinersRegistry.CONSOLE_READOUTS.get();
    }

    /**
     * Her flight readouts, moved here where the slam button takes their place beside the climb button; a bank of
     * toggles where the server does not let her slam down and they stand there still.
     */
    private static Block readoutsOrSwitches() {
        return SkylinersConfig.FREIGHTER_SLAM_LANDING.get() ? SkylinersRegistry.CONSOLE_READOUTS.get() : SkylinersRegistry.CONSOLE_SWITCHES.get();
    }

    /** Lays her console round her control head, its block given, in a freighter's layout. */
    static void build(HullLayout layout, BlockPos head) {
        if (!SkylinersConfig.FREIGHTER_CONSOLE.get()) {
            return;
        }
        List<Supplier<? extends Block>> modules = SkylinersConfig.FREIGHTER_SPIN.get() ? MODULES : MODULES_NO_SPIN;
        double step = 2 * SWEEP / modules.size();
        for (int slice = 0; slice < modules.size(); slice++) {
            double from = -SWEEP + step * slice;
            module(layout, head, modules.get(slice).get(), from, from + step, slice == 0, slice == modules.size() - 1);
        }
    }

    /**
     * One slice of the ring, between two bearings clockwise from her bow, the first or the last of it or neither, its
     * module set in the block its middle falls in, where that is open, and told once it stands its slice and the way to
     * her control head.
     */
    private static void module(HullLayout layout, BlockPos head, Block kind, double from, double to, boolean first, boolean last) {
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
        BlockState module = kind.defaultBlockState().setValue(ConsoleBlock.FACING, facing).setValue(ConsoleBlock.MOUNT, Mount.FLOOR);
        layout.set(at.getX(), at.getY(), at.getZ(), module);
        ConsoleShape.Facet facet = new ConsoleShape.Facet(middleX - (at.getX() + 0.5), middleZ - (at.getZ() + 0.5), INNER, OUTER, from, to,
            first, last);
        BlockPos toHead = head.subtract(at);
        layout.fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof ConsoleBlockEntity console) {
                console.arc(facet, toHead, rotation);
            }
        });
    }
}
