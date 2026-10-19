package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.ArrayDeque;
import java.util.Deque;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The autopilot screen's workings: where everything is drawn on it, what a touch there does, which autopilot it
 * works (as an offset, so the link holds wherever the airliner is), and how far its map reaches. The left of the
 * screen is a moving map; touching it zooms out a step, round to the closest again. The right is a column of the
 * autopilot's buttons, lit for what it is doing; touching anywhere off the map opens the autopilot's settings screen,
 * where they are worked. Sizes are in blocks, from the screen's bottom left corner as the viewer sees it. The client
 * also keeps the track the airliner has flown here, for the map.
 */
public class AutopilotScreenBlockEntity extends BlockEntity {
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
            double right = WIDTH - MARGIN;
            double columnWidth = (right - LEFT - GAP) / 2;
            double rowHeight = (HEIGHT - 2 * MARGIN - (ROWS - 1) * GAP) / ROWS;
            double top = HEIGHT - MARGIN - row * (rowHeight + GAP);
            double x0 = column <= 0 ? LEFT : LEFT + columnWidth + GAP;
            double x1 = column == 0 ? LEFT + columnWidth : right;
            this.box = new Box(x0, top - rowHeight, x1, top);
        }

        /** The key of the button's label in the lang file. */
        public String label() {
            return "screen.aerolarge.autopilot." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** Where the autopilot it works is, from this block; null until the airliner is built. */
    @Nullable
    private BlockPos autopilot;
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
        setChanged();
    }

    /** The autopilot the screen works, if it is still there. */
    @Nullable
    public AutopilotBlockEntity autopilot() {
        if (level == null || autopilot == null) {
            return null;
        }
        return level.getBlockEntity(worldPosition.offset(autopilot)) instanceof AutopilotBlockEntity found ? found : null;
    }

    /** The map's width in world blocks. */
    public int range() {
        return RANGES[range];
    }

    /** A touch at this point of the screen, on the server: zooms the map, or anywhere else opens the settings. */
    void touch(double across, double up, ServerPlayer player) {
        if (MAP.contains(across, up)) {
            range = (range + 1) % RANGES.length;
            setChanged();
            if (level != null) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
            return;
        }
        AutopilotBlockEntity target = autopilot();
        if (target != null) {
            target.openScreen(player);
        } else {
            player.displayClientMessage(Component.translatable("message.aerolarge.autopilot_screen.unlinked"), true);
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
        tag.putInt("Range", range);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        autopilot = NbtUtils.readBlockPos(tag, "Autopilot").orElse(null);
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
