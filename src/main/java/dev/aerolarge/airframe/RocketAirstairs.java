package dev.aerolarge.airframe;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.AirstairControllerBlockEntity;
import dev.aerolarge.block.AirstairFlight;
import dev.aerolarge.block.RocketStairsBlockEntity;
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
 * airstairs, four blocks wide, kept by a stairs button (see {@link RocketStairsBlockEntity}). Her door is widened to
 * the flight's width by a column on her left: her axis runs through a block's middle, so the door and the stairs sit
 * half a block to her left of it, the airlock panel's side, where the new column opens onto floor kept clear before
 * her front window anyway. Stowed, the flight lies folded level in a bay under her entry floor, in the engine room's
 * top row, which nothing else takes: from behind the mouth in her skin below the door as far in as the folded flight
 * needs, its sections as few as fit there. The hatch over the mouth is her skin there, cut to her curve, which the
 * button takes away as the hatch opens and puts back as it shuts. The button stands inside by the door on her right,
 * on her skin at eye height; a call button stands low on the front of her front right fin's tip, where someone on the
 * ground reaches it. She is built with them away, to come down as soon as she stands.
 */
final class RocketAirstairs implements RocketPiece {
    /** The door's columns, across her from her axis, from her left to her right: one more on her left than the ladder's. */
    private static final int LEFT_COLUMN = -2;
    private static final int RIGHT_COLUMN = LEFT_COLUMN + RocketStairsBlockEntity.WIDTH - 1;
    /**
     * Where the button inside stands: so many blocks to her right of her axis, just past the door's edge, and so many
     * rows over her entry floor, at eye height.
     */
    private static final int BUTTON_ACROSS = RIGHT_COLUMN + 1;
    private static final int BUTTON_UP = 2;
    /** How many rows over her fins' feet the call button is: at the eyes of someone standing on the ground. */
    private static final int CALL_BUTTON_UP = 1;

    private final RocketLayout layout;

    RocketAirstairs(RocketLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted() {
        return AerolargeConfig.ROCKET_AIRSTAIRS.get();
    }

    @Override
    public void build() {
        if (layout.floors().isEmpty()) {
            return;
        }
        int floor = layout.floors().getFirst();
        BlockPos button = button(floor);
        if (button == null) {
            return;
        }
        int front = frontRow(floor);
        for (int x = LEFT_COLUMN; x <= RIGHT_COLUMN; x++) {
            layout.doorColumn(x);
        }
        // Seen from outside, looking at her front, the flight's left column is her right.
        BlockPos head = new BlockPos(RIGHT_COLUMN, floor + 1, front);
        int mouthRow = floor - 1;
        int mouth = mouth(mouthRow, front);
        int rise = floor + 1;
        int fitting = AirstairFlight.sectionsFor(rise, bayRoom(mouthRow, front + mouth));
        int sections = fitting > 0 ? fitting : AirstairFlight.MOST_SECTIONS;
        List<AirstairControllerBlockEntity.Step> hatch = hatch(mouthRow, button);
        float inset = hatchInset(front);
        layout.set(button.getX(), button.getY(), button.getZ(), AerolargeRegistry.ROCKET_STAIRS_BUTTON.get().defaultBlockState()
            .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.SOUTH));
        BlockPos fromButton = head.subtract(button);
        layout.fit(button, (blockEntity, rotation) -> rig(blockEntity, fromButton, rise, sections, hatch, inset, mouth, rotation));
        callButton();
    }

    /**
     * Where the button inside goes: on the inside of her skin just past the door's edge on her right, facing in; null
     * where her skin is not there to hold it.
     */
    @Nullable
    private BlockPos button(int floor) {
        int y = floor + BUTTON_UP;
        int z = insideSkin(BUTTON_ACROSS, y);
        boolean held = z < 0 && layout.isOpen(BUTTON_ACROSS, y, z) && !layout.isOpen(BUTTON_ACROSS, y, z - 1);
        return held ? new BlockPos(BUTTON_ACROSS, y, z) : null;
    }

    /** Her door's outermost row, along her: the frontmost of her skin over the door's width, where the sill's edge is. */
    private int frontRow(int floor) {
        int front = 0;
        for (int x = LEFT_COLUMN; x <= RIGHT_COLUMN; x++) {
            front = Math.min(front, layout.frontSkin(x, floor));
        }
        return front;
    }

    /** The first open block going in from her front at a block across and a row, past her skin. */
    private int insideSkin(int x, int y) {
        int z = layout.frontSkin(x, y);
        while (z < 0 && !layout.isOpen(x, y, z)) {
            z++;
        }
        return z;
    }

    /** How deep the bay's mouth is, in blocks in from the door's outermost row: as deep as her skin is, at its deepest. */
    private int mouth(int row, int front) {
        int deepest = 1;
        for (int x = LEFT_COLUMN; x <= RIGHT_COLUMN; x++) {
            deepest = Math.max(deepest, insideSkin(x, row) - front);
        }
        return deepest;
    }

    /** How many blocks of the row under her entry floor are open from the bay's start in, across all the door's columns. */
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

    /** The hatch over the bay's mouth: her skin in the row under her entry floor across the door, each block from the button. */
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
        BlockState fin = AerolargeRegistry.ZEPPELIN_FABRIC.get().defaultBlockState();
        for (int out = layout.reach(); out > 0; out--) {
            BlockPos at = new BlockPos(out, CALL_BUTTON_UP, -out - 2);
            if (layout.stateAt(out, CALL_BUTTON_UP, -out - 1).is(fin.getBlock()) && layout.isOpen(at.getX(), at.getY(), at.getZ())) {
                layout.set(at.getX(), at.getY(), at.getZ(), AerolargeRegistry.ROCKET_STAIRS_BUTTON.get().defaultBlockState()
                    .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.NORTH));
                return;
            }
        }
    }

    /**
     * Hands the button its airstairs, turned as the rocket was placed: built front north, they come down north, their
     * right as seen from outside to the west.
     */
    private static void rig(BlockEntity blockEntity, BlockPos head, int rise, int sections, List<AirstairControllerBlockEntity.Step> hatch,
                            float inset, int mouth, Rotation rotation) {
        if (blockEntity instanceof RocketStairsBlockEntity stairs) {
            stairs.rigAirstairs(head.rotate(rotation), rotation.rotate(Direction.NORTH), rotation.rotate(Direction.WEST), rise, sections,
                hatch.stream().map(part -> new AirstairControllerBlockEntity.Step(part.offset().rotate(rotation), part.state().rotate(rotation))).toList(),
                inset, mouth);
        }
    }
}
