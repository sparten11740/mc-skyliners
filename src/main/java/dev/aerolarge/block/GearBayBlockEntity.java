package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * One leg of the retractable landing gear, worked from the top of the leg: the blocks of the leg below it (the strut,
 * the axle, the wheel mounts with their tires, the relays that steer and brake them, the ground crew's call button),
 * which it takes away as the leg folds up into the belly and puts back as it comes down and locks. The airliner rolls
 * on its real wheels again then: Offroad's wheel mount is a Sable actor that finds the ground from wherever its block
 * stands, each physics tick, so a mount put back mid-flight carries the airliner as before.
 *
 * <p>Going up takes {@link #RAISE_TICKS}: the doors open, the leg swings up and the doors close behind it. Its blocks go
 * as it starts up, so none hang under a leg drawn swinging. Coming down takes {@link #LOWER_TICKS}, and its blocks come
 * back once it has swung down, if its wheels have room under them; lowered with the airliner on its belly, it holds
 * there until the airliner is lifted off the ground (a hop frees it). Turned back halfway, it goes back from where it
 * got to. It never goes up with the airliner standing on its wheels.
 *
 * <p>The flight deck's gear lever works every leg (see {@link GearLeverBlockEntity}). The server runs each leg and tells
 * the clients when it starts moving; each client runs the same clock to draw it (see the client's
 * {@code GearBayRenderer}). Its links to the leg's blocks turn with its block (see {@link Turning}), and so do the
 * blocks it has taken away.
 */
public class GearBayBlockEntity extends BlockEntity {
    /** Where the leg is: down and locked, swinging up, up and stowed, or swinging down. */
    public enum Motion {
        DOWN, RAISING, UP, LOWERING;

        /** Whether the leg is swinging. */
        public boolean moving() {
            return this == RAISING || this == LOWERING;
        }
    }

    /** One block of the leg, taken away: where it was from the bay, what it was, and its block entity's data, if any. */
    public record Part(BlockPos offset, BlockState state, @Nullable CompoundTag data) {
    }

    /** How long the leg takes to swing up, doors and all, in ticks: eight seconds, about an A380's. */
    public static final int RAISE_TICKS = 160;
    /** How long it takes to swing down and lock, in ticks: six seconds, its weight helping it down. */
    public static final int LOWER_TICKS = 120;
    /** The wheels come down only with their bottom at least this far above the ground under them, in blocks. */
    private static final double ROOM_BELOW = 0.25;
    /**
     * With the bottom of its wheels nearer the ground than this, in blocks, the airliner stands on them and the leg
     * stays down: the height the callouts count the airliner on the ground at, its tires squashed or not.
     */
    private static final double ON_GROUND = 1.5;
    /**
     * How loud a leg going up or down is: past 1 it carries further, as the server sends a sound to everyone within 16
     * blocks for each unit of its volume, so the whole cabin over the gear hears it, as on an airliner.
     */
    private static final float HYDRAULICS_VOLUME = 3.0F;
    /** The legs loaded, so the controls' and the autopilot's links into a stowed leg are not taken for broken ones. */
    private static final Loaded<GearBayBlockEntity> LOADED = new Loaded<>(GearBayBlockEntity.class);

    /** The blocks of the leg below the bay, as offsets from it, as the blueprint built them. */
    private List<BlockPos> leg = List.of();
    /** While the leg is not down: the blocks taken away, in the order they went, to come back the other way round. */
    private List<Part> stowed = List.of();
    private Motion motion = Motion.DOWN;
    /** How far through its swing the leg is, in ticks. */
    private int ticks;

    public GearBayBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.GEAR_BAY_ENTITY.get(), pos, state);
    }

    /** Wired when the airliner is built: the blocks of the leg below it, as offsets from it. */
    public void configure(List<BlockPos> leg) {
        this.leg = List.copyOf(leg);
        setChanged();
    }

    public Motion motion() {
        return motion;
    }

    /** The blocks taken away while the leg is not down, which the client draws swinging. */
    public List<Part> stowed() {
        return stowed;
    }

    /** How far up the leg has swung, from 0 down to 1 up, between ticks as drawn. */
    public float raised(float partialTick) {
        return switch (motion) {
            case DOWN -> 0;
            case UP -> 1;
            case RAISING -> Math.min(1, (ticks + partialTick) / RAISE_TICKS);
            case LOWERING -> 1 - Math.min(1, (ticks + partialTick) / LOWER_TICKS);
        };
    }

    /**
     * Starts the leg up, its blocks going at once; turned back on its way down, up from where it got to. False, and
     * nothing done, while it is up or going up already, or while the airliner stands on its wheels.
     */
    public boolean raise(ServerLevel level) {
        if (motion == Motion.UP || motion == Motion.RAISING) {
            return false;
        }
        if (motion == Motion.LOWERING) {
            start(level, Motion.RAISING, Math.round(RAISE_TICKS * (1 - ticks / (float) LOWER_TICKS)));
            return true;
        }
        if (wheelsAboveGround(level) < ON_GROUND) {
            return false;
        }
        stow(level);
        start(level, Motion.RAISING, 0);
        return true;
    }

    /** Starts the leg down; turned back on its way up, down from where it got to. False while it is down or going down. */
    public boolean lower(ServerLevel level) {
        if (motion == Motion.DOWN || motion == Motion.LOWERING) {
            return false;
        }
        int from = motion == Motion.RAISING ? Math.round(LOWER_TICKS * (1 - ticks / (float) RAISE_TICKS)) : 0;
        start(level, Motion.LOWERING, from);
        return true;
    }

    /** Whether the airliner stands on this leg's wheels, so it may not go up. */
    public boolean bearsWeight(Level level) {
        return motion == Motion.DOWN && wheelsAboveGround(level) < ON_GROUND;
    }

    private void start(ServerLevel level, Motion next, int from) {
        motion = next;
        ticks = from;
        level.playSound(null, worldPosition, next == Motion.RAISING ? AerolargeRegistry.GEAR_RETRACT.get() : AerolargeRegistry.GEAR_EXTEND.get(),
            SoundSource.BLOCKS, HYDRAULICS_VOLUME, 1.0F);
        changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GearBayBlockEntity bay) {
        if (!bay.motion.moving() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        bay.ticks++;
        if (bay.motion == Motion.RAISING && bay.ticks >= RAISE_TICKS) {
            bay.motion = Motion.UP;
            bay.ticks = 0;
            bay.changed();
        } else if (bay.motion == Motion.LOWERING && bay.ticks >= LOWER_TICKS) {
            bay.ticks = LOWER_TICKS;
            bay.lockDown(serverLevel);
        }
    }

    /** On a client: the same clock as the server's, to draw the leg swinging between the server's word of it. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, GearBayBlockEntity bay) {
        int last = bay.motion == Motion.RAISING ? RAISE_TICKS : LOWER_TICKS;
        if (bay.motion.moving() && bay.ticks < last) {
            bay.ticks++;
        }
    }

    /** Swung down: the leg's blocks back where they were and locked, once its wheels have room under them. */
    private void lockDown(ServerLevel level) {
        if (wheelsAboveGround(level) < ROOM_BELOW) {
            return;
        }
        for (int i = stowed.size() - 1; i >= 0; i--) {
            put(level, stowed.get(i));
        }
        stowed = List.of();
        motion = Motion.DOWN;
        ticks = 0;
        changed();
    }

    /** Takes the leg's blocks away, keeping each with its data; what hangs on the leg first, so nothing falls off it. */
    private void stow(ServerLevel level) {
        List<Part> parts = new ArrayList<>();
        for (BlockPos offset : leg) {
            BlockPos at = worldPosition.offset(offset);
            BlockState there = level.getBlockState(at);
            if (!there.isAir()) {
                BlockEntity blockEntity = level.getBlockEntity(at);
                parts.add(new Part(offset, there, blockEntity == null ? null : blockEntity.saveWithoutMetadata(level.registryAccess())));
            }
        }
        parts.sort(Comparator.comparingInt(GearBayBlockEntity::hangsOn).reversed());
        for (Part part : parts) {
            BlockPos at = worldPosition.offset(part.offset());
            // Emptied first, so a wheel mount's tire goes with it rather than dropping to the ground.
            Clearable.tryClear(level.getBlockEntity(at));
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        stowed = List.copyOf(parts);
    }

    /** How a block of the leg hangs on the rest: 0 for the leg itself, 1 for what stands beside it, 2 for what is fixed to its face. */
    private static int hangsOn(Part part) {
        if (part.state().getBlock() instanceof FaceAttachedHorizontalDirectionalBlock) {
            return 2;
        }
        return part.state().is(AerolargeRegistry.LANDING_GEAR.get()) ? 0 : 1;
    }

    /** Puts one block of the leg back with its data, unless something else has taken its place. */
    private void put(ServerLevel level, Part part) {
        BlockPos at = worldPosition.offset(part.offset());
        BlockState there = level.getBlockState(at);
        if (!there.isAir() && !there.canBeReplaced()) {
            return;
        }
        level.setBlock(at, part.state(), Block.UPDATE_ALL);
        BlockEntity blockEntity = level.getBlockEntity(at);
        if (blockEntity != null && part.data() != null) {
            blockEntity.loadWithComponents(part.data(), level.registryAccess());
            blockEntity.setChanged();
            level.sendBlockUpdated(at, part.state(), part.state(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * How high the bottom of the leg's wheels is above the ground under them, in blocks, whether the leg is down or
     * not: the row under its lowest block, where the tires reach, followed out into the world from among an assembled
     * airliner's blocks. Standing as blocks, the airliner's own blocks over the leg count as the ground: it stands on
     * its wheels.
     */
    public double wheelsAboveGround(Level level) {
        int lowest = leg.stream().mapToInt(BlockPos::getY).min().orElse(0);
        Vec3 wheels = SableFlight.toWorld(level, Vec3.atBottomCenterOf(worldPosition.above(lowest - 1)));
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(wheels.x), Mth.floor(wheels.z));
        return wheels.y - ground;
    }

    /** Whether a block is one a leg of the gear has taken away, so links to it are not mended as if broken. */
    public static boolean stows(Level level, BlockPos pos) {
        for (GearBayBlockEntity bay : LOADED.in(level)) {
            if (bay.holds(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Where every block the legs within a box have taken away stood, so the airliner can be measured as with its gear down. */
    public static List<BlockPos> stowedWithin(Level level, BoundingBox box) {
        List<BlockPos> found = new ArrayList<>();
        for (GearBayBlockEntity bay : LOADED.in(level)) {
            if (box.isInside(bay.worldPosition)) {
                bay.stowed.forEach(part -> found.add(bay.worldPosition.offset(part.offset())));
            }
        }
        return found;
    }

    private boolean holds(BlockPos pos) {
        return stowed.stream().anyMatch(part -> worldPosition.offset(part.offset()).equals(pos));
    }

    /** Saves, and tells the clients how the leg moves. */
    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Leg", Relays.write(leg));
        tag.putString("Motion", motion.name());
        tag.putInt("Ticks", ticks);
        ListTag parts = new ListTag();
        for (Part part : stowed) {
            CompoundTag saved = new CompoundTag();
            saved.put("Offset", NbtUtils.writeBlockPos(part.offset()));
            saved.put("State", NbtUtils.writeBlockState(part.state()));
            if (part.data() != null) {
                saved.put("Data", part.data());
            }
            parts.add(saved);
        }
        tag.put("Stowed", parts);
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        leg = Turning.turn(Relays.read(tag.getList("Leg", Tag.TAG_INT_ARRAY)), turn);
        motion = parse(tag.getString("Motion"));
        ticks = tag.getInt("Ticks");
        List<Part> parts = new ArrayList<>();
        for (Tag entry : tag.getList("Stowed", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) entry;
            BlockPos offset = NbtUtils.readBlockPos(saved, "Offset").orElse(BlockPos.ZERO).rotate(turn);
            BlockState state = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), saved.getCompound("State")).rotate(turn);
            parts.add(new Part(offset, state, saved.contains("Data") ? saved.getCompound("Data") : null));
        }
        stowed = List.copyOf(parts);
    }

    private static Motion parse(String name) {
        try {
            return Motion.valueOf(name);
        } catch (IllegalArgumentException e) {
            return Motion.DOWN;
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
