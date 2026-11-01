package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.AirstairControllerBlockEntity;
import dev.skyliners.block.AirstairFlight;
import dev.skyliners.block.RocketStairsBlockEntity;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;

/**
 * Her airstairs at the front door, in place of her crew ladder (see {@link RocketLadder}): the airliner's integral
 * airstairs, as wide as her door, three blocks round her axis across the flat of her front, kept by a stairs button
 * (see {@link RocketStairsBlockEntity}). Across the doorway, in its outermost row with her skin either side for its
 * leaves to slide into, stand her sliding airlock doors (see {@code AirlockDoors}), shut, kept with the stairs, to open
 * as they come out. Stowed, the flight lies folded level in a bay under her entry floor, in the engine room's top row,
 * which nothing else takes: from behind the mouth in her skin below the door as far in as the folded flight needs, its
 * sections as few as fit there. The hatch over the mouth is her skin there, cut to her curve, which the button takes
 * away as the hatch opens and puts back as it shuts. The button stands inside by the door on her right at eye height
 * (see {@link #button}); a call button stands low on the front of her front right fin's tip, where someone on the
 * ground reaches it. She is built with them away, to come down as soon as she stands.
 */
final class RocketAirstairs implements RocketPiece {
    /** The door's columns, across her from her axis, from her left to her right, and how many there are. */
    private static final int LEFT_COLUMN = -RocketLayout.DOOR_HALF;
    private static final int RIGHT_COLUMN = RocketLayout.DOOR_HALF;
    private static final int WIDTH = RIGHT_COLUMN - LEFT_COLUMN + 1;
    /**
     * Where the button inside stands where it can: so many blocks to her right of her axis, just past the door's edge,
     * and so many rows over her entry floor, at eye height.
     */
    private static final int BUTTON_ACROSS = RIGHT_COLUMN + 1;
    private static final int BUTTON_UP = 2;
    /** How many rows over her fins' feet the call button is: at the eyes of someone standing on the ground. */
    private static final int CALL_BUTTON_UP = 1;
    /** How many rows in from the doorway's outermost the leaves may stand at most, looking for skin either side. */
    private static final int LEAVES_DEEPEST = 2;
    /**
     * How many rows below her fins' feet the flight is built to reach on, where the folded flight still fits her bay:
     * standing on uneven ground, the ground under her door lower than her fins, the stairs still come down to it.
     */
    private static final int LOWER_GROUND = 6;

    private final RocketLayout layout;

    RocketAirstairs(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return SkylinersConfig.ROCKET_AIRSTAIRS.get();
    }

    @Override
    public void build() {
        if (layout.floors().isEmpty()) {
            return;
        }
        int floor = layout.floors().getFirst();
        int front = frontRow(floor);
        int leaves = leavesRow(floor, front);
        Button spot = button(floor, leaves);
        if (spot == null) {
            return;
        }
        BlockPos button = spot.at();
        // Seen from outside, looking at her front, the flight's left column is her right.
        BlockPos head = new BlockPos(RIGHT_COLUMN, floor + 1, front);
        BlockPos doorway = new BlockPos(RIGHT_COLUMN, floor + 1, leaves);
        doors(doorway);
        int mouthRow = floor - 1;
        int mouth = mouth(mouthRow, front);
        int bay = bayRoom(mouthRow, front + mouth);
        int reaching = AirstairFlight.sectionsFor(floor + 1 + LOWER_GROUND, bay);
        int rise = reaching > 0 ? floor + 1 + LOWER_GROUND : floor + 1;
        int fitting = reaching > 0 ? reaching : AirstairFlight.sectionsFor(rise, bay);
        int sections = fitting > 0 ? fitting : AirstairFlight.MOST_SECTIONS;
        List<AirstairControllerBlockEntity.Step> hatch = hatch(mouthRow, button);
        float inset = hatchInset(front);
        layout.set(button.getX(), button.getY(), button.getZ(), SkylinersRegistry.ROCKET_STAIRS_BUTTON.get().defaultBlockState()
            .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, spot.facing()));
        Rigging rigging = new Rigging(head.subtract(button), doorway.subtract(button), rise, sections, hatch, inset, mouth);
        layout.fit(button, (blockEntity, rotation) -> rig(blockEntity, rigging, rotation));
        callButton();
    }

    /**
     * Where the button inside goes, and the way it faces: on the inside of her skin just past the door's edge on her
     * right, facing in, where there is room before it to stand and press it; where there is not, as in the smallest
     * rocket, whose lift shaft stands just behind it, on the doorway's side on her right, just in from the doors,
     * facing across the doorway. Null where her skin is not there to hold it either way.
     */
    @Nullable
    private Button button(int floor, int leaves) {
        int y = floor + BUTTON_UP;
        int z = insideSkin(BUTTON_ACROSS, y);
        boolean held = z < 0 && layout.isOpen(BUTTON_ACROSS, y, z) && !layout.isOpen(BUTTON_ACROSS, y, z - 1);
        if (held && layout.isOpen(BUTTON_ACROSS, y, z + 1)) {
            return new Button(new BlockPos(BUTTON_ACROSS, y, z), Direction.SOUTH);
        }
        int inside = leaves + 1;
        boolean onJamb = layout.isOpen(RIGHT_COLUMN, y, inside) && !layout.isOpen(RIGHT_COLUMN + 1, y, inside);
        return onJamb ? new Button(new BlockPos(RIGHT_COLUMN, y, inside), Direction.WEST) : null;
    }

    /** Where the button inside stands, and the way it faces, built front north. */
    private record Button(BlockPos at, Direction facing) {
    }

    /** Her door's outermost row, along her: the frontmost of her skin across the door, where the sill's edge is. */
    private int frontRow(int floor) {
        int front = 0;
        for (int x = LEFT_COLUMN; x <= RIGHT_COLUMN; x++) {
            front = Math.min(front, layout.frontSkin(x, floor));
        }
        return front;
    }

    /**
     * The row along her the airlock doors' leaves stand in: the doorway's outermost row where her skin stands in both
     * columns either side of it all the way up the door, so the leaves slide into her skin and leave no gap beside
     * them where her curve draws back from the door's edges.
     */
    private int leavesRow(int floor, int front) {
        for (int z = front; z <= front + LEAVES_DEEPEST && z < 0; z++) {
            if (jambsAt(floor, z)) {
                return z;
            }
        }
        return front;
    }

    /** Whether her skin stands in both columns either side of the doorway at a row along her, all the way up. */
    private boolean jambsAt(int floor, int z) {
        for (int y = floor + 1; y <= floor + RocketLayout.DOOR_ROWS; y++) {
            if (layout.isOpen(LEFT_COLUMN - 1, y, z) || layout.isOpen(RIGHT_COLUMN + 1, y, z)) {
                return false;
            }
        }
        return true;
    }

    /** The airlock doors' blocks across the doorway, from its bottom left seen from outside, shut. */
    private void doors(BlockPos corner) {
        BlockState shut = SkylinersRegistry.AIRLOCK_DOOR.get().shut(Direction.NORTH);
        for (int x = LEFT_COLUMN; x <= RIGHT_COLUMN; x++) {
            for (int up = 0; up < RocketLayout.DOOR_ROWS; up++) {
                layout.set(x, corner.getY() + up, corner.getZ(), shut);
            }
        }
    }

    /** The first open block going in from her front at a block across and a row, past her skin. */
    private int insideSkin(int x, int y) {
        int z = layout.frontSkin(x, y);
        while (z < 0 && !layout.isOpen(x, y, z)) {
            z++;
        }
        return z;
    }

    /** How deep the bay's mouth is, in blocks in from the door's outermost row: her skin's depth at its deepest. */
    private int mouth(int row, int front) {
        int deepest = 1;
        for (int x = LEFT_COLUMN; x <= RIGHT_COLUMN; x++) {
            deepest = Math.max(deepest, insideSkin(x, row) - front);
        }
        return deepest;
    }

    /** How many blocks of the row under her entry floor are open from the bay's start in, across the door's columns. */
    private int bayRoom(int row, int start) {
        int room = 0;
        while (start + room <= layout.reach() && openAcross(row, start + room)) {
            room++;
        }
        return room;
    }

    private boolean openAcross(int y, int z) {
        for (int x = LEFT_COLUMN; x <= RIGHT_COLUMN; x++) {
            if (!layout.isOpen(x, y, z)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The hatch over the bay's mouth: her skin in the row under her entry floor across the door, each block from the
     * button.
     */
    private List<AirstairControllerBlockEntity.Step> hatch(int row, BlockPos button) {
        List<AirstairControllerBlockEntity.Step> hatch = new ArrayList<>();
        for (int x = LEFT_COLUMN; x <= RIGHT_COLUMN; x++) {
            for (int z = layout.frontSkin(x, row); z < insideSkin(x, row); z++) {
                hatch.add(new AirstairControllerBlockEntity.Step(new BlockPos(x, row, z).subtract(button), layout.stateAt(x, row, z)));
            }
        }
        return List.copyOf(hatch);
    }

    /**
     * How far in from the door's outermost row's outer face her skin's surface lies, in blocks, across the door's
     * middle: where the hatch is drawn as it swings.
     */
    private float hatchInset(int front) {
        double middle = (LEFT_COLUMN + RIGHT_COLUMN) / 2.0;
        double radius = layout.radius();
        double surface = -Math.sqrt(Math.max(0, radius * radius - middle * middle));
        return (float) Math.max(0, surface - (front - 0.5));
    }

    /**
     * The call button, low on the front of her front right fin's tip, facing her front: on the outermost of the fin's
     * blocks at that row that has open air before it.
     */
    private void callButton() {
        BlockState fin = SkylinersRegistry.ZEPPELIN_FABRIC.get().defaultBlockState();
        for (int out = layout.reach(); out > 0; out--) {
            BlockPos at = new BlockPos(out, CALL_BUTTON_UP, -out - 2);
            if (layout.stateAt(out, CALL_BUTTON_UP, -out - 1).is(fin.getBlock()) && layout.isOpen(at.getX(), at.getY(), at.getZ())) {
                layout.set(at.getX(), at.getY(), at.getZ(), SkylinersRegistry.ROCKET_STAIRS_BUTTON.get().defaultBlockState()
                    .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
                return;
            }
        }
    }

    /**
     * Hands the button its airstairs and the doors across their doorway, turned as the rocket was placed: built front
     * north, they come down north, their right as seen from outside to the west.
     */
    private static void rig(BlockEntity blockEntity, Rigging rigging, Rotation rotation) {
        if (blockEntity instanceof RocketStairsBlockEntity stairs) {
            Direction out = rotation.rotate(Direction.NORTH);
            Direction right = rotation.rotate(Direction.WEST);
            stairs.rigAirstairs(rigging.head().rotate(rotation), out, right, WIDTH, rigging.rise(), rigging.sections(),
                rigging.hatch().stream().map(part -> new AirstairControllerBlockEntity.Step(part.offset().rotate(rotation), part.state().rotate(rotation))).toList(),
                rigging.inset(), rigging.mouth());
            stairs.rigDoors(rigging.doorway().rotate(rotation), out, right, WIDTH, RocketLayout.DOOR_ROWS);
        }
    }

    /**
     * What the button is handed, built front north: from it, the stairs' head at the sill's edge and the doors'
     * bottom right block seen from inside; how far the stairs come down and in how many sections; the hatch; and the
     * bay's hatch inset and mouth (see {@link #hatchInset}, {@link #mouth}).
     */
    private record Rigging(BlockPos head, BlockPos doorway, int rise, int sections, List<AirstairControllerBlockEntity.Step> hatch, float inset,
                           int mouth) {
    }
}
