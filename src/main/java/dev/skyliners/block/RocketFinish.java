package dev.skyliners.block;

import java.util.Locale;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/**
 * How one of the rocket's rooms is finished inside: its walls (her cover's inner faces, lined; see
 * {@link RocketLinedCoverBlock} and {@link RocketLiningPanelBlock}), the dado along their foot, its floor and its
 * ceiling (see {@link RocketFloorBlock}). Each room has its own, so no two look alike: the entry's riveted steel over a
 * hazard band, the cargo bay's plywood, the mess's oak, the bunk room's quilted blue, the greenhouse's whitewashed
 * brick, the pool's mosaic, the club's black foam, the lab's sage tiles, the lounge's walnut and the cockpit's padding,
 * and over the cockpit the dome's night sky, deep blue and full of stars. Saved by name in the blocks' states.
 */
public enum RocketFinish implements StringRepresentable {
    ENTRY, CARGO, MESS, BUNKS, GREENHOUSE, POOL, CLUB, LAB, LOUNGE, COCKPIT, DOME;

    /** The property a block keeps the finish of its room in. */
    public static final EnumProperty<RocketFinish> PROPERTY = EnumProperty.create("finish", RocketFinish.class);

    /** The finish of her stories in order from the entry story up, her cockpit's last. */
    private static final RocketFinish[] BY_STORY = {ENTRY, CARGO, MESS, BUNKS, GREENHOUSE, POOL, CLUB, LAB, LOUNGE};

    /** The finish of a story from the entry story up; the cockpit's for any past her last room. */
    public static RocketFinish ofStory(int story) {
        return story >= 0 && story < BY_STORY.length ? BY_STORY[story] : COCKPIT;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
