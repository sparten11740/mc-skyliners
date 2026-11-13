package dev.skyliners.exterior;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;

/**
 * What can be picked out of each of the outside fittings (see {@link HullFittingBlock}) as it stands on a floor, is
 * fixed to a wall and hangs under a ceiling, drawn facing north, as their models are (see
 * tools/generate_resources.mjs): roughly the parts of each that hold still, kept within its block but for the big
 * dish's bowl over its turntable.
 */
public final class HullFittings {
    /**
     * A satellite dish: on a floor its post and turntable, the dish turning over it; on a wall its plate and arm, the
     * dish sweeping at its end; under a ceiling its hanger, the dish turning under it (see {@link SatelliteDishBlock}).
     */
    public static final HullFittingBlock.Outline SATELLITE_DISH = new HullFittingBlock.Outline(Block.box(3, 0, 3, 13, 14, 13),
        Block.box(4, 2, 1, 12, 14, 16), Block.box(3, 2, 3, 13, 16, 13));
    /** Where the satellite dish's mounts hold its dish: on the turntable, at the arm's end, under the hanger. */
    public static final SatelliteDishBlock.Dish SMALL_DISH = new SatelliteDishBlock.Dish(new float[]{8, 10.5F, 8}, new float[]{8, 10, 5.5F},
        new float[]{8, 9, 8}, 1, 35, 60);
    /**
     * The big satellite dish: on a floor its pedestal and column up to the turntable at the top of its block, and over
     * it the middle of its bowl as it turns round tipped up at the sky, to pick out; on a wall its heavy plate and twin
     * arms; under a ceiling its plate and column down to the ring at the foot of its block. Its whole bowl is solid
     * besides, out past its block, wherever it is turned (see {@link SatelliteDishBlock#getCollisionShape}).
     */
    public static final HullFittingBlock.Outline BIG_SATELLITE_DISH = new HullFittingBlock.Outline(
        Shapes.or(Block.box(1, 0, 1, 15, 16, 15), Block.box(2, 16, 2, 14, 28, 14)), Block.box(3, 2, 1, 13, 14, 16), Block.box(2, 0, 2, 14, 16, 14));
    /** Where the big dish's mounts hold its dish, three times the small one's size. */
    public static final SatelliteDishBlock.Dish BIG_DISH = new SatelliteDishBlock.Dish(new float[]{8, 16, 8}, new float[]{8, 12, 3},
        new float[]{8, 1, 8}, 3, 45, 20);
    /** An antenna mast: standing on its flange, jutting from a wall on its hinge, or hanging as a blade. */
    public static final HullFittingBlock.Outline ANTENNA_MAST = new HullFittingBlock.Outline(Block.box(6, 0, 6, 10, 16, 10),
        Block.box(5, 2, 2, 11, 12, 16), Block.box(6, 0, 3, 10, 16, 14));
    /** A sensor dome: a radome on its drum, a blister on a wall plate, or a radome hanging under its ring. */
    public static final HullFittingBlock.Outline SENSOR_DOME = new HullFittingBlock.Outline(Block.box(2.5, 0, 2.5, 13.5, 12, 13.5),
        Block.box(2.5, 2.5, 5, 13.5, 13.5, 16), Block.box(2.5, 4, 2.5, 13.5, 16, 13.5));
    /** A heat sink: a bank of fins standing on its base, out from its wall plate, or hanging from its top plate. */
    public static final HullFittingBlock.Outline HEAT_SINK = new HullFittingBlock.Outline(Block.box(0, 0, 0.5, 16, 14, 14),
        Block.box(1, 0, 3.5, 15, 15, 16), Block.box(0, 2, 0.5, 16, 16, 14));
    /** An exhaust vent: a stack with its rain cap, a louvred box on a wall, or a nozzle pointing down. */
    public static final HullFittingBlock.Outline EXHAUST_VENT = new HullFittingBlock.Outline(Block.box(3, 0, 3, 13, 16, 13),
        Block.box(1.5, 2, 8.5, 14.5, 15, 16), Block.box(3, 4.5, 3, 13, 16, 13));
    /**
     * A vent grille, flush with what holds it: a slatted grating lying on a floor, a hooded louvred grille on a wall,
     * or an intake with a hub on its struts under a ceiling.
     */
    public static final HullFittingBlock.Outline VENT_GRILLE = new HullFittingBlock.Outline(Block.box(1, 0, 1, 15, 2, 15),
        Block.box(1, 1, 13.5, 15, 15, 16), Block.box(2, 13.5, 2, 14, 16, 14));
    /** Hull machinery: a box of works with its pipes on a floor, a run of conduit on a wall, or a tank hung up. */
    public static final HullFittingBlock.Outline HULL_MACHINERY = new HullFittingBlock.Outline(Block.box(0, 0, 1.5, 16, 8, 16),
        Block.box(0, 0, 10.5, 16, 16, 16), Block.box(0, 5.5, 4.5, 16, 16, 14));
    /** A hull floodlight: a lamp on a yoke on a floor, on a bracket on a wall, or a housing hung under a ceiling. */
    public static final HullFittingBlock.Outline HULL_FLOODLIGHT = new HullFittingBlock.Outline(Block.box(3, 0, 3, 13, 12, 13),
        Block.box(4, 3, 3, 12, 12, 16), Block.box(3, 8, 3, 13, 16, 13));

    /**
     * A hull light strip: a lit channel lying on a floor, a hooded strip along a wall, or a channel under a ceiling.
     */
    public static final HullFittingBlock.Outline HULL_LIGHT_STRIP = new HullFittingBlock.Outline(Block.box(0, 0, 6, 16, 2, 10),
        Block.box(0, 6, 12.5, 16, 10, 16), Block.box(0, 14, 6, 16, 16, 10));

    private HullFittings() {
    }
}
