package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.airframe.OutsideCamera;
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
import net.minecraft.world.phys.Vec3;

/**
 * What a panel of the flight deck's curved row knows, kept by the block holding it (see {@link CockpitDisplayBlock}):
 * where it stands (see {@link Panel}), set when the airliner is built, and, showing an outside camera, which one,
 * picked with the buttons along its bottom (see {@link CameraButtons}) as on the camera display. The moving map is the
 * autopilot screen's, the track and the zoom both, taken from the screen standing beside it; touching the map zooms
 * it, there and on the screen alike.
 */
public class CockpitDisplayBlockEntity extends BlockEntity implements CameraScreen {
    /** How far round the panel the autopilot screen is looked for, in blocks: it stands right beside the row. */
    private static final int SCREEN_REACH = 6;
    /** How long after looking for the autopilot screen in vain it is looked for again, in game ticks. */
    private static final int SCREEN_SEARCH_EVERY = 40;

    @Nullable
    private Panel panel;
    private OutsideCamera camera = OutsideCamera.TAIL;
    /** Where the autopilot screen beside it was last found; not saved, found again after loading. */
    @Nullable
    private BlockPos screen;
    /** When the autopilot screen was last looked for and not found, in game ticks. */
    private long screenMissedAt = Long.MIN_VALUE;

    /**
     * Where the panel stands, from the middle of the floor of the block holding it, in the frame of the way the row
     * faces: x to the right of whoever looks at the panel, z toward them, in blocks. The bottom corners of its face,
     * left and right as they see it, and how high its bottom and top edges are. Measured that way, it holds however
     * the airliner is turned.
     */
    public record Panel(float leftX, float leftZ, float rightX, float rightZ, float bottom, float top) {
        public float width() {
            return (float) Math.hypot(rightX - leftX, rightZ - leftZ);
        }

        public float height() {
            return top - bottom;
        }

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("LeftX", leftX);
            tag.putFloat("LeftZ", leftZ);
            tag.putFloat("RightX", rightX);
            tag.putFloat("RightZ", rightZ);
            tag.putFloat("Bottom", bottom);
            tag.putFloat("Top", top);
            return tag;
        }

        static Panel load(CompoundTag tag) {
            return new Panel(tag.getFloat("LeftX"), tag.getFloat("LeftZ"), tag.getFloat("RightX"), tag.getFloat("RightZ"), tag.getFloat("Bottom"),
                tag.getFloat("Top"));
        }
    }

    /** A touch on the panel's face, in blocks from its bottom left corner, and how far along the look it is. */
    private record Touch(double across, double up, double along) {
    }

    public CockpitDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.COCKPIT_DISPLAY_ENTITY.get(), pos, state);
    }

    /** Tells the panel where it stands, when the airliner is built. */
    public void configure(Panel panel) {
        this.panel = panel;
        setChanged();
    }

    @Nullable
    public Panel panel() {
        return panel;
    }

    public CockpitDisplayBlock.Shows shows() {
        return getBlockState().getValue(CockpitDisplayBlock.SHOWS);
    }

    public Direction facing() {
        return getBlockState().getValue(CockpitDisplayBlock.FACING);
    }

    @Override
    public OutsideCamera camera() {
        return camera;
    }

    /** A point of the panel's frame among the airliner's blocks (or in the world, before it is assembled). */
    public Vec3 point(double x, double y, double z) {
        Direction right = facing().getCounterClockWise();
        Direction toward = facing();
        return Vec3.atBottomCenterOf(worldPosition)
            .add(right.getStepX() * x + toward.getStepX() * z, y, right.getStepZ() * x + toward.getStepZ() * z);
    }

    /** A direction of the panel's frame among the airliner's blocks. */
    private Vec3 direction(double x, double z) {
        Direction right = facing().getCounterClockWise();
        Direction toward = facing();
        return new Vec3(right.getStepX() * x + toward.getStepX() * z, 0, right.getStepZ() * x + toward.getStepZ() * z);
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
        return direction(-(panel.rightZ - panel.leftZ) / width, (panel.rightX - panel.leftX) / width);
    }

    @Override
    public double faceReach() {
        return panel == null ? 1 : Math.hypot(panel.width(), panel.height()) / 2;
    }

    /**
     * Where a look from the eye through a point meets the panel's face, if it does: in blocks from its bottom left
     * corner, and how far along the look. Both points among the airliner's blocks.
     */
    @Nullable
    private Touch touchAt(Vec3 eye, Vec3 through) {
        if (panel == null) {
            return null;
        }
        Vec3 corner = point(panel.leftX, panel.bottom, panel.leftZ);
        Vec3 across = point(panel.rightX, panel.bottom, panel.rightZ).subtract(corner).normalize();
        Vec3 out = faceOut();
        Vec3 look = through.subtract(eye);
        double facing = look.dot(out);
        if (facing >= 0) {
            // From behind, or along it: the face is drawn for whoever is in front.
            return null;
        }
        double along = corner.subtract(eye).dot(out) / facing;
        Vec3 at = eye.add(look.scale(along)).subtract(corner);
        double x = at.dot(across);
        double y = at.y;
        return x < 0 || x > panel.width() || y < 0 || y > panel.height() ? null : new Touch(x, y, along);
    }

    /**
     * The panel a look from the eye through a point of a part's block meets first, among those held round it; null for
     * none. Both points among the airliner's blocks.
     */
    @Nullable
    static CockpitDisplayBlockEntity touched(Level level, BlockPos part, Vec3 eye, Vec3 through) {
        CockpitDisplayBlockEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(part.offset(-CockpitDisplayBlock.HOLDER_REACH, -1, -CockpitDisplayBlock.HOLDER_REACH),
            part.offset(CockpitDisplayBlock.HOLDER_REACH, 1, CockpitDisplayBlock.HOLDER_REACH))) {
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
        for (BlockPos at : BlockPos.betweenClosed(part.offset(-CockpitDisplayBlock.HOLDER_REACH, -1, -CockpitDisplayBlock.HOLDER_REACH),
            part.offset(CockpitDisplayBlock.HOLDER_REACH, 1, CockpitDisplayBlock.HOLDER_REACH))) {
            if (level.getBlockEntity(at) instanceof CockpitDisplayBlockEntity panel && panel.standsIn(part)) {
                return panel;
            }
        }
        return null;
    }

    /** Whether the panel runs through a block: across its floor plan, and between its bottom and top edges. */
    boolean standsIn(BlockPos block) {
        if (panel == null) {
            return false;
        }
        Vec3 left = point(panel.leftX, panel.bottom, panel.leftZ);
        Vec3 right = point(panel.rightX, panel.bottom, panel.rightZ);
        boolean level = block.getY() < left.y + panel.height() && block.getY() + 1 > left.y;
        return level && CockpitDisplayBlock.crossesSquare(left.x, left.z, right.x, right.z, block.getX(), block.getZ());
    }

    /** Takes away every block the panel stands in but the one broken, none of them dropping anything. */
    void dismantle(BlockPos broken) {
        if (level == null) {
            return;
        }
        for (BlockPos at : BlockPos.betweenClosed(worldPosition.offset(-CockpitDisplayBlock.HOLDER_REACH, -1, -CockpitDisplayBlock.HOLDER_REACH),
            worldPosition.offset(CockpitDisplayBlock.HOLDER_REACH, 1, CockpitDisplayBlock.HOLDER_REACH))) {
            if (!at.equals(broken) && level.getBlockState(at).getBlock() instanceof CockpitDisplayBlock && standsIn(at)) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
    }

    /**
     * A touch on the panel, on the server: a camera's button shows that camera; the map zooms out a step. Whether it
     * did anything.
     */
    boolean touch(ServerLevel level, Vec3 eye, Vec3 through, ServerPlayer player) {
        Touch touch = touchAt(eye, through);
        if (touch == null) {
            return false;
        }
        return switch (shows()) {
            case CAMERA -> look(CameraButtons.at(touch.across(), touch.up()));
            case MAP -> zoom(level);
            case FLIGHT -> false;
        };
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

    /** Zooms the map out a step, on the autopilot screen it shares the map with; whether there is one. */
    private boolean zoom(ServerLevel level) {
        AutopilotScreenBlockEntity found = screen(level);
        if (found != null) {
            found.zoom();
        }
        return found != null;
    }

    /**
     * The autopilot screen beside the row, whose map the panel shows; null for none. Once missed, it is looked for again
     * only every {@link #SCREEN_SEARCH_EVERY} ticks, since the map panel asks every frame.
     */
    @Nullable
    public AutopilotScreenBlockEntity screen(Level level) {
        if (screen != null && level.getBlockEntity(screen) instanceof AutopilotScreenBlockEntity found) {
            return found;
        }
        screen = null;
        if (level.getGameTime() - screenMissedAt < SCREEN_SEARCH_EVERY) {
            return null;
        }
        for (BlockPos at : BlockPos.betweenClosed(worldPosition.offset(-SCREEN_REACH, -1, -SCREEN_REACH), worldPosition.offset(SCREEN_REACH, 1, SCREEN_REACH))) {
            if (level.getBlockEntity(at) instanceof AutopilotScreenBlockEntity found) {
                screen = at.immutable();
                return found;
            }
        }
        screenMissedAt = level.getGameTime();
        return null;
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
        tag.putString("Camera", camera.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        panel = tag.contains("Panel", Tag.TAG_COMPOUND) ? Panel.load(tag.getCompound("Panel")) : null;
        camera = OutsideCamera.named(tag.getString("Camera"), OutsideCamera.TAIL);
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
