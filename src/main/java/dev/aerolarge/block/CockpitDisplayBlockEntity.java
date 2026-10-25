package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.OutsideCamera;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.CockpitKeys.Function;
import dev.aerolarge.block.CockpitKeys.Key;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * What a panel of the flight deck's curved row knows, kept by the block holding it (see {@link CockpitDisplayBlock}):
 * where it stands (see {@link Panel}), set when the airliner is built, and what the sloped touch panel under its screen
 * holds (see {@link Desk}); what it is linked to (see {@link Link}): the autopilot, and the flight deck's switches,
 * whose blocks stand out of sight under the floor and are worked from the panel's keys (see {@link CockpitKeys});
 * showing an outside camera, which one, picked with the buttons along its bottom (see {@link CameraButtons}); and
 * showing the moving map, how far it reaches, zoomed with a touch, and on the client the track flown, for the map.
 */
public class CockpitDisplayBlockEntity extends BlockEntity implements CameraScreen {
    /** The track kept for the map: a point every so many ticks, and how many. */
    private static final int TRACK_EVERY = 10;
    private static final int TRACK_POINTS = 480;
    /** Which of the map's reaches it starts at (see {@link AutopilotScreenBlockEntity#RANGES}). */
    private static final int DEFAULT_RANGE = 1;

    @Nullable
    private Panel panel;
    private Desk desk = Desk.NONE;
    /** What it is linked to, as offsets from this block, turned with it (see {@link Turning}). */
    private final Map<Link, BlockPos> links = new EnumMap<>(Link.class);
    private OutsideCamera camera = OutsideCamera.TAIL;
    /** How far the map reaches: which of the autopilot screen's reaches (see {@link AutopilotScreenBlockEntity#RANGES}). */
    private int range = DEFAULT_RANGE;
    /** Where the airliner has been, in world blocks (x, z), oldest first; only on the client. */
    private final Deque<double[]> track = new ArrayDeque<>();
    private long lastTracked = Long.MIN_VALUE;

    /**
     * Where the panel stands, from the middle of the floor of the block holding it, in the frame of the way the row
     * faces: x to the right of whoever looks at it, z toward them, in blocks. The bottom corners of its screen's face,
     * left and right as they see it, and how high its bottom and top edges are; how far the sloped touch panel under
     * the screen reaches out toward them from the face, dropping as far at 45 degrees, with the desk under it down to
     * the floor (none where it is 0); and how far in from each end the slope's front edge stops, where it meets the
     * slope of the panel beside it turned further toward them, mitred so the two meet without one overlapping the
     * other. Measured that way, it holds however the airliner is turned.
     */
    public record Panel(float leftX, float leftZ, float rightX, float rightZ, float bottom, float top, float desk, float leftCut, float rightCut) {
        public float width() {
            return (float) Math.hypot(rightX - leftX, rightZ - leftZ);
        }

        public float height() {
            return top - bottom;
        }

        /** How long the slope is, from its front edge up to the screen. */
        public float slope() {
            return (float) (desk * Math.sqrt(2));
        }

        /** Whether a place on the slope, in blocks from its front left corner as it would be uncut, is on it. */
        public boolean onSlope(double across, double up) {
            double cutShare = 1 - up / slope();
            return up >= 0 && up <= slope() && across >= leftCut * cutShare && across <= width() - rightCut * cutShare;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("LeftX", leftX);
            tag.putFloat("LeftZ", leftZ);
            tag.putFloat("RightX", rightX);
            tag.putFloat("RightZ", rightZ);
            tag.putFloat("Bottom", bottom);
            tag.putFloat("Top", top);
            tag.putFloat("Desk", desk);
            tag.putFloat("LeftCut", leftCut);
            tag.putFloat("RightCut", rightCut);
            return tag;
        }

        static Panel load(CompoundTag tag) {
            return new Panel(tag.getFloat("LeftX"), tag.getFloat("LeftZ"), tag.getFloat("RightX"), tag.getFloat("RightZ"), tag.getFloat("Bottom"),
                tag.getFloat("Top"), tag.getFloat("Desk"), tag.getFloat("LeftCut"), tag.getFloat("RightCut"));
        }
    }

    /** What the sloped touch panel under the screen holds (see {@link CockpitKeys#desk}). */
    public enum Desk {
        /** Nothing: a plain slope. */
        NONE,
        /** The nose wheel tiller: a key to steer left, a key to steer right. */
        TILLER,
        /** The parking brake, and the landing gear lever with its lamps. */
        GROUND,
        /** The autopilot's buttons. */
        AUTOPILOT
    }

    /** What a panel works: the autopilot, or one of the flight deck's switches out of sight under its floor. */
    public enum Link {
        AUTOPILOT, TILLER, BRAKE, GEAR, CABIN_LIGHTS, SEATBELTS, JUMP_LIGHTS, SHADES, LASER, DROP
    }

    /** A touch on the panel, in blocks from the bottom left corner of its screen or its slope, and how far along the look. */
    private record Touch(boolean onSlope, double across, double up, double along) {
    }

    public CockpitDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.COCKPIT_DISPLAY_ENTITY.get(), pos, state);
    }

    /** Tells the panel where it stands, what its slope holds and what it works, when the airliner is built. */
    public void configure(Panel panel, Desk desk, Map<Link, BlockPos> links) {
        this.panel = panel;
        this.desk = desk;
        this.links.clear();
        this.links.putAll(links);
        setChanged();
    }

    @Nullable
    public Panel panel() {
        return panel;
    }

    public Desk desk() {
        return desk;
    }

    public CockpitDisplayBlock.Shows shows() {
        return getBlockState().getValue(CockpitDisplayBlock.SHOWS);
    }

    public Direction facing() {
        return getBlockState().getValue(CockpitDisplayBlock.FACING);
    }

    /** The block a link leads to, among the airliner's blocks; null where it has none. */
    @Nullable
    public BlockPos linked(Link link) {
        BlockPos offset = links.get(link);
        return offset == null ? null : worldPosition.offset(offset);
    }

    /** The autopilot it works, if it is still there. */
    @Nullable
    public AutopilotBlockEntity autopilot() {
        BlockPos at = linked(Link.AUTOPILOT);
        return level != null && at != null && level.getBlockEntity(at) instanceof AutopilotBlockEntity found ? found : null;
    }

    /** The systems page's tiles on the screen, for drawing them; none unless it shows that page. */
    public List<Key> screenKeys() {
        boolean systems = panel != null && shows() == CockpitDisplayBlock.Shows.SYSTEMS;
        return systems ? CockpitKeys.systems(panel.width(), panel.height(), links.keySet()) : List.of();
    }

    /** The keys on the slope under the screen, between its mitred ends, for drawing them. */
    public List<Key> slopeKeys() {
        return panel == null ? List.of() : CockpitKeys.desk(desk, panel.leftCut(), panel.width() - panel.rightCut(), panel.slope());
    }

    @Override
    public OutsideCamera camera() {
        return camera;
    }

    /** The map's width, in world blocks. */
    public int range() {
        return AutopilotScreenBlockEntity.RANGES[range];
    }

    /** Adds where the airliner is now to the map's track, every so many ticks; the oldest point goes when it is full. */
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

    /** A point of the panel's frame among the airliner's blocks (or in the world, before it is assembled). */
    public Vec3 point(double x, double y, double z) {
        return Vec3.atBottomCenterOf(worldPosition).add(direction(x, y, z));
    }

    /** A direction of the panel's frame among the airliner's blocks. */
    private Vec3 direction(double x, double y, double z) {
        Direction right = facing().getCounterClockWise();
        Direction toward = facing();
        return new Vec3(right.getStepX() * x + toward.getStepX() * z, y, right.getStepZ() * x + toward.getStepZ() * z);
    }

    @Override
    public Vec3 faceMiddle() {
        if (panel == null) {
            return Vec3.atCenterOf(worldPosition);
        }
        return point((panel.leftX + panel.rightX) / 2, (panel.bottom + panel.top) / 2, (panel.leftZ + panel.rightZ) / 2);
    }

    /** Square to the face, toward whoever looks at it. */
    @Override
    public Vec3 faceOut() {
        if (panel == null) {
            return Vec3.atLowerCornerOf(facing().getNormal());
        }
        float width = panel.width();
        return direction(-(panel.rightZ - panel.leftZ) / width, 0, (panel.rightX - panel.leftX) / width);
    }

    /** Along the face, from its left edge to its right, as whoever looks at it sees it. */
    private Vec3 faceAcross() {
        float width = panel.width();
        return direction((panel.rightX - panel.leftX) / width, 0, (panel.rightZ - panel.leftZ) / width);
    }

    @Override
    public double faceReach() {
        return panel == null ? 1 : Math.hypot(panel.width(), panel.height()) / 2;
    }

    /**
     * Where a look from the eye through a point meets the panel, on its screen or its slope, whichever it meets first;
     * null if it meets neither. Both points among the airliner's blocks.
     */
    @Nullable
    private Touch touchAt(Vec3 eye, Vec3 through) {
        if (panel == null) {
            return null;
        }
        Vec3 look = through.subtract(eye);
        Vec3 out = faceOut();
        Vec3 corner = point(panel.leftX, panel.bottom, panel.leftZ);
        Touch screen = onPlane(eye, look, corner, new Vec3(0, 1, 0), out, false);
        if (screen != null && (screen.across() < 0 || screen.across() > panel.width() || screen.up() < 0 || screen.up() > panel.height())) {
            screen = null;
        }
        if (panel.desk <= 0) {
            return screen;
        }
        // Up the slope it runs back toward the screen and up, at 45 degrees; it faces up and out.
        Vec3 front = corner.add(out.scale(panel.desk)).add(0, -panel.desk, 0);
        Vec3 up = out.scale(-1).add(0, 1, 0).normalize();
        Touch slope = onPlane(eye, look, front, up, out.add(0, 1, 0).normalize(), true);
        if (slope != null && !panel.onSlope(slope.across(), slope.up())) {
            slope = null;
        }
        return screen == null || slope != null && slope.along() < screen.along() ? slope : screen;
    }

    /**
     * Where a look meets a plane of the panel, across it and up it the given way from a corner, the plane facing out;
     * null from behind.
     */
    @Nullable
    private Touch onPlane(Vec3 eye, Vec3 look, Vec3 corner, Vec3 up, Vec3 out, boolean onSlope) {
        double facing = look.dot(out);
        if (facing >= 0) {
            return null;
        }
        double along = corner.subtract(eye).dot(out) / facing;
        Vec3 at = eye.add(look.scale(along)).subtract(corner);
        return new Touch(onSlope, at.dot(faceAcross()), at.dot(up), along);
    }

    /**
     * The panel a look from the eye through a point of a part's block meets first, among those held round it; null for
     * none. Both points among the airliner's blocks.
     */
    @Nullable
    static CockpitDisplayBlockEntity touched(Level level, BlockPos part, Vec3 eye, Vec3 through) {
        CockpitDisplayBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos at : round(part)) {
            Touch touch = level.getBlockEntity(at) instanceof CockpitDisplayBlockEntity panel ? panel.touchAt(eye, through) : null;
            if (touch != null && touch.along() < best) {
                best = touch.along();
                nearest = (CockpitDisplayBlockEntity) level.getBlockEntity(at);
            }
        }
        return nearest;
    }

    /** The panel a part's block stands in, held round it; null for none. */
    @Nullable
    static CockpitDisplayBlockEntity holding(Level level, BlockPos part) {
        for (BlockPos at : round(part)) {
            if (level.getBlockEntity(at) instanceof CockpitDisplayBlockEntity panel && panel.standsIn(part)) {
                return panel;
            }
        }
        return null;
    }

    /** The blocks round a part where the block holding its panel may be, the panel's two rows either way. */
    private static Iterable<BlockPos> round(BlockPos part) {
        int reach = CockpitDisplayBlock.HOLDER_REACH;
        return BlockPos.betweenClosed(part.offset(-reach, -CockpitDisplayBlock.ROWS, -reach), part.offset(reach, CockpitDisplayBlock.ROWS, reach));
    }

    /**
     * Whether the panel runs through a block: its screen through the rows it stands in, across the floor plan, and the
     * desk under it through the rows below, from the slab's back out to the slope's front edge.
     */
    boolean standsIn(BlockPos block) {
        if (panel == null) {
            return false;
        }
        int row = block.getY() - worldPosition.getY();
        Vec3 left = point(panel.leftX, 0, panel.leftZ);
        Vec3 right = point(panel.rightX, 0, panel.rightZ);
        if (row < panel.top && row + 1 > panel.bottom) {
            return CockpitDisplayBlock.crossesSquare(left.x, left.z, right.x, right.z, block.getX(), block.getZ());
        }
        if (row >= panel.bottom || row < 0 || panel.desk <= 0) {
            return false;
        }
        Vec3 back = faceOut().scale(-CockpitDisplayBlock.DEPTH);
        Vec3 out = faceOut().scale(panel.desk + CockpitDisplayBlock.DEPTH);
        return CockpitDisplayBlock.crossesBand(left.x + back.x, left.z + back.z, right.x + back.x, right.z + back.z, out.x, out.z, block.getX(),
            block.getZ());
    }

    /** Takes away every block the panel stands in but the one broken, none of them dropping anything. */
    void dismantle(BlockPos broken) {
        if (level == null) {
            return;
        }
        for (BlockPos at : round(worldPosition)) {
            if (!at.equals(broken) && level.getBlockState(at).getBlock() instanceof CockpitDisplayBlock && standsIn(at)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    /**
     * A touch on the panel, on the server: on the slope, its key works what it is for; on the screen, a camera's button
     * shows that camera, the map zooms out a step, and a systems tile works its switch. Whether it did anything.
     */
    boolean touch(ServerLevel level, Vec3 eye, Vec3 through, ServerPlayer player) {
        Touch touch = touchAt(eye, through);
        if (touch == null) {
            return false;
        }
        if (touch.onSlope()) {
            return work(level, CockpitKeys.at(slopeKeys(), touch.across(), touch.up()), player);
        }
        return switch (shows()) {
            case CAMERA -> look(CameraButtons.at(touch.across(), touch.up()));
            case MAP -> zoom();
            case SYSTEMS -> work(level, CockpitKeys.at(screenKeys(), touch.across(), touch.up()), player);
            case FLIGHT -> false;
        };
    }

    /**
     * Works what a key is for, as whoever touched it, sneaking or not: one of the autopilot's buttons, the tiller a
     * notch either way, or the switch it uses where it stands out of sight. Whether there was a key.
     */
    private boolean work(ServerLevel level, @Nullable Function function, ServerPlayer player) {
        BlockPos at = function == null ? null : linked(function.link);
        if (at == null) {
            return false;
        }
        if (function.button != null) {
            if (level.getBlockEntity(at) instanceof AutopilotBlockEntity found) {
                found.press(function.button, player);
            }
            return true;
        }
        if (function == Function.TILLER_LEFT || function == Function.TILLER_RIGHT) {
            TillerBlock.steer(level, at, player, function == Function.TILLER_RIGHT);
            return true;
        }
        level.getBlockState(at).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
        return true;
    }

    /** Shows another camera, for everyone looking at the panel; whether it was one. */
    private boolean look(@Nullable OutsideCamera picked) {
        if (picked == null || picked == camera) {
            return picked != null;
        }
        camera = picked;
        changed();
        return true;
    }

    /** Zooms the map out a step, round to the closest again, for everyone looking at it. */
    private boolean zoom() {
        range = (range + 1) % AutopilotScreenBlockEntity.RANGES.length;
        changed();
        return true;
    }

    /** Saves, and tells the clients, which draw what it shows. */
    private void changed() {
        setChanged();
        if (level instanceof ServerLevel server) {
            server.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (panel != null) {
            tag.put("Panel", panel.save());
        }
        tag.putString("Desk", desk.name());
        CompoundTag linkTag = new CompoundTag();
        links.forEach((link, offset) -> linkTag.putLong(link.name(), offset.asLong()));
        tag.put("Links", linkTag);
        Turning.save(tag, getBlockState());
        tag.putString("Camera", camera.name());
        tag.putInt("Range", range);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        panel = tag.contains("Panel", Tag.TAG_COMPOUND) ? Panel.load(tag.getCompound("Panel")) : null;
        desk = named(Desk.class, tag.getString("Desk"), Desk.NONE);
        links.clear();
        CompoundTag linkTag = tag.getCompound("Links");
        for (Link link : Link.values()) {
            if (linkTag.contains(link.name(), Tag.TAG_LONG)) {
                links.put(link, Turning.turn(BlockPos.of(linkTag.getLong(link.name())), Turning.since(tag, getBlockState())));
            }
        }
        camera = OutsideCamera.named(tag.getString("Camera"), OutsideCamera.TAIL);
        range = Math.floorMod(tag.contains("Range") ? tag.getInt("Range") : DEFAULT_RANGE, AutopilotScreenBlockEntity.RANGES.length);
    }

    /** An enum value saved by name, or the fallback for a name it does not know. */
    private static <E extends Enum<E>> E named(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            return fallback;
        }
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
