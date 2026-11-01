package dev.aerolarge.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The flight deck's centre console is built of its controls themselves: each is drawn as a module of the console, a
 * low slab the size of its block with its lever, knob or switch on top, so that side by side they read as one
 * console. The slab is kept low enough that from the pilots' seats the console in front of the autopilot screen hides
 * hardly any of it.
 */
final class ConsoleModule {
    /** How tall the console's slab is, in pixels. */
    static final int HEIGHT = 6;
    /** The slab alone, for a control that lies flat on it. */
    static final VoxelShape SLAB = Block.box(0, 0, 0, 16, HEIGHT, 16);
    /**
     * Where a control stands behind its picture plate, on a module whose plate takes the half nearest the pilots (as
     * the rocket's controls do): the middle of the module across, this many pixels from its pilots' edge.
     */
    static final double BEHIND_PLATE = 11.25;

    private ConsoleModule() {
    }

    /**
     * The slab with a control standing on it: reaching this many pixels either way from the middle of the block and
     * this many pixels above the slab. Square, so the shape holds whichever way the module faces.
     */
    static VoxelShape with(double halfWidth, double height) {
        return Shapes.or(SLAB, Block.box(8 - halfWidth, HEIGHT, 8 - halfWidth, 8 + halfWidth, HEIGHT + height, 8 + halfWidth));
    }

    /**
     * The slab with a control standing behind its picture plate (see {@link #BEHIND_PLATE}), reaching this many pixels
     * either way from its middle and this many above the slab: drawn with the pilots at its north side, to be turned
     * with the module (see {@link TurnedShapes}).
     */
    static VoxelShape behindPlate(double halfWidth, double height) {
        return Shapes.or(SLAB, Block.box(8 - halfWidth, HEIGHT, BEHIND_PLATE - halfWidth, 8 + halfWidth, HEIGHT + height, BEHIND_PLATE + halfWidth));
    }
}
