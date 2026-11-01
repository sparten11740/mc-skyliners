package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.autopilot.Craft;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The autopilot screen's workings: where everything is drawn on it, what a touch there does, which autopilot it
 * works (as an offset turned with the screen, so the link holds wherever the airliner is; see {@link Turning}), and
 * how far its map reaches. The left of the screen is a moving map; touching it zooms out a step, round to the closest
 * again. The right is a column of the autopilot's buttons, lit for what it is doing; touching anywhere off the map
 * opens the autopilot's settings screen, where they are worked. Sizes are in blocks, from the screen's bottom left
 * corner as the viewer sees it. The client also keeps the track the airliner has flown here, for the map.
 */
public class AutopilotScreenBlockEntity extends BlockEntity {
    /** How many rows of buttons a rocket's screen has, each taller than the airliner's: on and off, height, climb. */
    private static final int ROCKET_ROWS = 3;
    private static final List<Shown> ALL_BUTTONS = Arrays.stream(Button.values()).map(button -> new Shown(button, button.box)).toList();
    private static final List<Shown> ROCKET_BUTTONS = List.of(new Shown(Button.ENGAGE, Button.box(0, -1, ROCKET_ROWS)),
        new Shown(Button.ALTITUDE_DOWN, Button.box(1, 0, ROCKET_ROWS)), new Shown(Button.ALTITUDE_UP, Button.box(1, 1, ROCKET_ROWS)),
        new Shown(Button.SPEED_DOWN, Button.box(2, 0, ROCKET_ROWS)), new Shown(Button.SPEED_UP, Button.box(2, 1, ROCKET_ROWS)));

    /** The screen's size, and the margin around and between what is drawn on it. */
    public static final double WIDTH = AutopilotScreenBlock.COLUMNS;
    public static final double HEIGHT = AutopilotScreenBlock.ROWS;
    public static final double MARGIN = 0.06;
    /** The map, and above it the line of text saying what the autopilot is doing. */
    public static final Box MAP = new Box(MARGIN, MARGIN, 1.9, HEIGHT - 0.32);
    public static final Box HEADER = new Box(MARGIN, HEIGHT - 0.28, 1.9, HEIGHT - MARGIN);
    /** The map's width, in world blocks, at each zoom. */
    public static final int[] RANGES = {300, 600, 1200, 2400};
    private static final int DEFAULT_RANGE = 1;
    /** The track kept for the map: a point every so many ticks, and how many. */
    private static final int TRACK_EVERY = 10;
    private static final int TRACK_POINTS = 480;

    /** A rectangle on the screen. */
    public record Box(double x0, double y0, double x1, double y1) {
        public boolean contains(double x, double y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }
    }

    /** The buttons, top to bottom in two columns; the first spans both. */
    /** A button as the screen shows it, where it is drawn. */
    public record Shown(Button button, Box box) {
    }

    public enum Button {
        ENGAGE(0, -1),
        HEADING_LEFT(1, 0),
        HEADING_RIGHT(1, 1),
        ORBIT_LEFT(2, 0),
        ORBIT_RIGHT(2, 1),
        ALTITUDE_DOWN(3, 0),
        ALTITUDE_UP(3, 1),
        SPEED_DOWN(4, 0),
        SPEED_UP(4, 1),
        RADIUS_DOWN(5, 0),
        RADIUS_UP(5, 1);

        private static final double LEFT = 1.96;
        private static final double GAP = 0.04;
        private static final int ROWS = 6;

        public final Box box;

        Button(int row, int column) {
            this.box = box(row, column, ROWS);
        }

        /**
         * Where a button goes in a column of so many rows down the screen's right: at a row from the top, in the left
         * or right column, or spanning both for -1.
         */
        public static Box box(int row, int column, int rows) {
            double right = WIDTH - MARGIN;
            double columnWidth = (right - LEFT - GAP) / 2;
            double rowHeight = (HEIGHT - 2 * MARGIN - (rows - 1) * GAP) / rows;
            double top = HEIGHT - MARGIN - row * (rowHeight + GAP);
            double x0 = column <= 0 ? LEFT : LEFT + columnWidth + GAP;
            double x1 = column == 0 ? LEFT + columnWidth : right;
            return new Box(x0, top - rowHeight, x1, top);
        }

        /** The key of the button's label in the lang file. */
        public String label() {
            return "screen.aerolarge.autopilot." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where the autopilot it works is, from this block; null until the airliner is built. */
    @Nullable
    private BlockPos autopilot;
    /** Whether its link to the autopilot has been looked at since it was loaded, to mend it (see {@link Turning#mend}). */
    private boolean linkChecked;
    private int range = DEFAULT_RANGE;
    /** Where the airliner has been, in world blocks (x, z), oldest first; only on the client. */
    private final Deque<double[]> track = new ArrayDeque<>();
    private long lastTracked = Long.MIN_VALUE;

    public AutopilotScreenBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.AUTOPILOT_SCREEN_ENTITY.get(), pos, state);
    }

    /** Links the screen to its autopilot. */
    public void configure(BlockPos offset) {
        autopilot = offset;
        linkChecked = true;
        setChanged();
    }

    /** The autopilot the screen works, if it is still there. */
    @Nullable
    public AutopilotBlockEntity autopilot() {
        if (level == null || autopilot == null) {
            return null;
        }
        mendLink();
        return level.getBlockEntity(worldPosition.offset(autopilot)) instanceof AutopilotBlockEntity found ? found : null;
    }

    /** Once after loading, as the screen is first used, mends its link to the autopilot if it leads nowhere. */
    private void mendLink() {
        if (!linkChecked && level != null && autopilot != null) {
            linkChecked = true;
            Turning.mend(level, worldPosition, List.of(autopilot), state -> state.getBlock() instanceof AutopilotBlock, this::turnLink);
        }
    }

    /** Turns the link to the autopilot, mending it, and tells the clients, which draw what it flies on the screen. */
    private void turnLink(Rotation turn) {
        autopilot = Turning.turn(autopilot, turn);
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** The map's width in world blocks. */
    public int range() {
        return RANGES[range];
    }

    /**
     * A touch on one of the screen's tiles (see {@link AutopilotScreenBlock#tileAt}), on the server: on the map it
     * zooms it, anywhere else it opens the settings.
     */
    /**
     * The buttons the screen shows for the autopilot it works, and where: every one for an airliner or a zeppelin; for
     * a rocket only on and off, her height and her climb, on taller rows, as she flies no heading or circle of her own.
     */
    public List<Shown> buttons() {
        AutopilotBlockEntity autopilot = autopilot();
        return autopilot != null && autopilot.craft() == Craft.ROCKET ? ROCKET_BUTTONS : ALL_BUTTONS;
    }

    /** The button shown at a point on the face, across and up from its lower left corner; null for none there. */
    @Nullable
    public Button buttonAt(double across, double up) {
        for (Shown shown : buttons()) {
            if (shown.box().contains(across, up)) {
                return shown.button();
            }
        }
        return null;
    }

    void touch(int tile, ServerPlayer player) {
        mendLink();
        if (tile == AutopilotScreenBlock.MAP_TILE) {
            zoom();
            return;
        }
        AutopilotBlockEntity target = autopilot();
        if (target != null) {
            target.openScreen(player);
        } else {
            player.displayClientMessage(Component.translatable("message.aerolarge.autopilot_screen.unlinked"), true);
        }
    }

    /** Zooms the map out a step, round to the closest again, for everyone looking at it. */
    private void zoom() {
        range = (range + 1) % RANGES.length;
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** Adds where the airliner is now to the track, every so many ticks; the oldest point goes when it is full. */
    public void record(Vec3 position, long gameTime) {
        if (gameTime - lastTracked < TRACK_EVERY) {
            return;
        }
        lastTracked = gameTime;
        track.addLast(new double[]{position.x, position.z});
        if (track.size() > TRACK_POINTS) {
            track.removeFirst();
        }
    }

    public Iterable<double[]> track() {
        return track;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (autopilot != null) {
            tag.put("Autopilot", NbtUtils.writeBlockPos(autopilot));
        }
        Turning.save(tag, getBlockState());
        tag.putInt("Range", range);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        autopilot = Turning.turn(NbtUtils.readBlockPos(tag, "Autopilot").orElse(null), Turning.since(tag, getBlockState()));
        range = Math.floorMod(tag.contains("Range") ? tag.getInt("Range") : DEFAULT_RANGE, RANGES.length);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
