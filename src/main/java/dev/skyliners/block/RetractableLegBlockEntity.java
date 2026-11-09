package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * One retractable leg, worked from its top, where it meets the hull: the blocks of the leg below it, which it takes
 * away as the leg goes up into the belly and puts back as it comes down and locks. The airliner's gear legs swing up on
 * their wheels (see {@link GearBayBlockEntity}); the freighter's landing legs draw straight up into her belly (see
 * {@link LegBayBlockEntity}). Each kind says how long it takes each way, what it sounds like and how far below its
 * lowest block it reaches; the rest is the same for both.
 *
 * <p>Its blocks go as it starts up, so none hang under a leg drawn moving, and come back once it is all the way down,
 * if its foot has room under it; let down with the ship resting on its belly, it holds there until the ship is lifted
 * off the ground. Turned back halfway, it goes back from where it got to. It never goes up with the ship on it.
 *
 * <p>The server runs each leg and tells the clients when it starts moving; each client runs the same clock to draw it.
 * Its links to the leg's blocks turn with its block (see {@link Turning}), and so do the blocks it has taken away.
 */
public abstract class RetractableLegBlockEntity extends BlockEntity {
    /** Where the leg is: down and locked, going up, up and stowed, or coming down. */
    public enum Motion {
        DOWN, RAISING, UP, LOWERING;

        /** Whether the leg is moving. */
        public boolean moving() {
            return this == RAISING || this == LOWERING;
        }
    }

    /** One block of the leg, taken away: where it was from its top, what it was, and its block entity's data if any. */
    public record Part(BlockPos offset, BlockState state, @Nullable CompoundTag data) {
    }

    /** Its foot comes down only with its bottom at least this far above the ground under it, in blocks. */
    private static final double ROOM_BELOW = 0.25;
    /**
     * With the bottom of its foot nearer the ground than this, in blocks, the ship stands on it and the leg stays down:
     * the height the airliner's callouts count it on the ground at, its tires squashed or not.
     */
    private static final double ON_GROUND = 1.5;
    /**
     * How long a client still draws the leg's blocks once it has locked down, in ticks: its real blocks are back by
     * then, but the client takes a frame or so to draw them, and the leg would flash out of sight in between.
     */
    private static final int DRAWN_AFTER_LOCK = 3;
    /** A leg that telescopes: how often it goes a block further or back, in ticks, and how loud that is. */
    private static final int REACH_EVERY = 4;
    private static final float REACH_VOLUME = 0.5F;
    /** The legs loaded, so the controls' and the autopilot's links into a stowed leg are not taken for broken ones. */
    private static final Loaded<RetractableLegBlockEntity> LOADED = new Loaded<>(RetractableLegBlockEntity.class);

    /** The blocks of the leg below its top, as offsets from it, as the blueprint built them. */
    private List<BlockPos> leg = List.of();
    /** While the leg is not down: the blocks taken away, in the order they went, to come back the other way round. */
    private List<Part> stowed = List.of();
    private Motion motion = Motion.DOWN;
    /** How far through its movement the leg is, in ticks. */
    private int ticks;
    /** How many blocks longer than built the leg reaches now, and how many it has been told to. */
    private int reach;
    private int reachWanted;
    /**
     * On a client, the blocks it drew coming down, kept for {@link #DRAWN_AFTER_LOCK} once it has locked, and how many
     * ticks longer they are drawn.
     */
    private List<Part> lastDrawn = List.of();
    private int drawnFor;

    protected RetractableLegBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** How long the leg takes to go up, in ticks. */
    protected abstract int raiseTicks();

    /** How long it takes to come down and lock, in ticks. */
    protected abstract int lowerTicks();

    /** What it sounds like starting up, or starting down. */
    protected abstract SoundEvent sound(boolean raising);

    /** How loud it is: past 1 it carries further, the server sending it 16 blocks further for each unit. */
    protected abstract float volume();

    /** How many rows below its lowest block the leg reaches down: its tires below their mounts, or none for a foot. */
    protected abstract int reachBelow();

    /** How many blocks further than built the leg can reach: none, unless the kind of leg telescopes. */
    protected int mostReach() {
        return 0;
    }

    /** What it is lengthened with, a block at a time, if it telescopes (see {@link #mostReach}). */
    protected BlockState extension() {
        return Blocks.AIR.defaultBlockState();
    }

    /** The pitch its sound plays at: as recorded, unless the kind of leg sets each of its legs apart. */
    protected float pitch() {
        return 1.0F;
    }

    /** Wired when the ship is built: the blocks of the leg below its top, as offsets from it. */
    public void configure(List<BlockPos> leg) {
        this.leg = List.copyOf(leg);
        setChanged();
    }

    /** Where the leg is now: down, going up, up or coming down. */
    public Motion motion() {
        return motion;
    }

    /** The blocks taken away while the leg is not down, which the client draws moving. */
    public List<Part> stowed() {
        return stowed;
    }

    /**
     * What a client draws of the leg: the blocks taken away while it is not down, and those it drew coming down for a
     * moment after it has locked, where they stand again (see {@link #DRAWN_AFTER_LOCK}).
     */
    public List<Part> drawn() {
        if (motion != Motion.DOWN) {
            return stowed;
        }
        return drawnFor > 0 ? lastDrawn : List.of();
    }

    /** Whether a client still draws the leg a moment after it has locked down (see {@link #DRAWN_AFTER_LOCK}). */
    public boolean justLocked() {
        return motion == Motion.DOWN && drawnFor > 0;
    }

    /** How far up the leg has gone, from 0 down to 1 up, between ticks as drawn. */
    public float raised(float partialTick) {
        return switch (motion) {
            case DOWN -> 0;
            case UP -> 1;
            case RAISING -> Math.min(1, (ticks + partialTick) / raiseTicks());
            case LOWERING -> 1 - Math.min(1, (ticks + partialTick) / lowerTicks());
        };
    }

    /**
     * Starts the leg up, its blocks going at once; turned back on its way down, up from where it got to. False, and
     * nothing done, while it is up or going up already, or while the ship stands on it.
     */
    public boolean raise(ServerLevel level) {
        if (motion == Motion.UP || motion == Motion.RAISING) {
            return false;
        }
        if (motion == Motion.LOWERING) {
            start(level, Motion.RAISING, Math.round(raiseTicks() * (1 - ticks / (float) lowerTicks())));
            return true;
        }
        if (aboveGround(level) < ON_GROUND) {
            return false;
        }
        stow(level);
        start(level, Motion.RAISING, 0);
        return true;
    }

    /**
     * Starts the leg down; turned back on its way up, down from where it got to. False while it is down or going down.
     */
    public boolean lower(ServerLevel level) {
        if (motion == Motion.DOWN || motion == Motion.LOWERING) {
            return false;
        }
        int from = motion == Motion.RAISING ? Math.round(lowerTicks() * (1 - ticks / (float) raiseTicks())) : 0;
        start(level, Motion.LOWERING, from);
        return true;
    }

    /**
     * Whether the leg may come down: it is down or on its way already, or its foot would have room under it. Lying on
     * its belly, or too low over the ground, the ship leaves it up.
     */
    public boolean roomToLower(Level level) {
        return motion == Motion.DOWN || motion == Motion.LOWERING || aboveGround(level) >= ROOM_BELOW;
    }

    /** Whether the ship stands on this leg, so it may not go up. */
    public boolean bearsWeight(Level level) {
        return motion == Motion.DOWN && aboveGround(level) < ON_GROUND;
    }

    private void start(ServerLevel level, Motion next, int from) {
        motion = next;
        ticks = from;
        level.playSound(null, worldPosition, sound(next == Motion.RAISING), SoundSource.BLOCKS, volume(), pitch());
        changed();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RetractableLegBlockEntity leg) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        leg.stepReach(serverLevel);
        if (!leg.motion.moving()) {
            return;
        }
        leg.ticks++;
        if (leg.motion == Motion.RAISING && leg.ticks >= leg.raiseTicks()) {
            leg.motion = Motion.UP;
            leg.ticks = 0;
            leg.changed();
        } else if (leg.motion == Motion.LOWERING && leg.ticks >= leg.lowerTicks()) {
            leg.ticks = leg.lowerTicks();
            leg.lockDown(serverLevel);
        }
    }

    /** On a client: the same clock as the server's, to draw the leg moving between the server's word of it. */
    public static void clientTick(Level level, BlockPos pos, BlockState state, RetractableLegBlockEntity leg) {
        int last = leg.motion == Motion.RAISING ? leg.raiseTicks() : leg.lowerTicks();
        if (leg.motion.moving() && leg.ticks < last) {
            leg.ticks++;
        }
        leg.drawnFor = Math.max(0, leg.drawnFor - 1);
    }

    /**
     * How high the ground is under its foot, as the first air over it: the highest under its foot plate, which stands
     * on that. However the ship leans, the same ground.
     */
    public int groundUnder(Level level) {
        int footRow = footRow();
        return leg.stream().filter(offset -> offset.getY() == footRow)
            .mapToInt(offset -> groundAt(level, offset)).max().orElse(0);
    }

    /** How high the ground is under one block of the leg, as the first air over it. */
    private int groundAt(Level level, BlockPos offset) {
        Vec3 at = SableFlight.toWorld(level, Vec3.atBottomCenterOf(worldPosition.offset(offset)));
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(at.x), Mth.floor(at.z));
    }

    /** The row of its lowest blocks, its foot's, from its top. */
    private int footRow() {
        return leg.stream().mapToInt(BlockPos::getY).min().orElse(0);
    }

    /** How many blocks further than built it reaches now (see {@link #mostReach}). */
    public int reach() {
        return reach;
    }

    /** Tells a leg that telescopes how many blocks further than built to reach, which it goes to a block at a time. */
    public void reachTo(int blocks) {
        reachWanted = Mth.clamp(blocks, 0, mostReach());
    }

    /** Down and locked, a block nearer the reach it was told, every {@link #REACH_EVERY} ticks. */
    private void stepReach(ServerLevel level) {
        if (motion != Motion.DOWN || reach == reachWanted || level.getGameTime() % REACH_EVERY != 0) {
            return;
        }
        boolean longer = reachWanted > reach;
        moveFoot(level, reach, reach + (longer ? 1 : -1));
        SoundEvent sound = longer ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT;
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, REACH_VOLUME, pitch());
    }

    /** Its foot moved from one reach to another, the column over it lengthened or shortened to follow. */
    private void moveFoot(ServerLevel level, int from, int to) {
        int footRow = footRow();
        List<BlockPos> plate = leg.stream().filter(offset -> offset.getY() == footRow).toList();
        Map<BlockPos, BlockState> foot = new HashMap<>();
        for (BlockPos cell : plate) {
            BlockPos at = worldPosition.offset(cell).below(from);
            foot.put(cell, level.getBlockState(at));
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        for (int row = Math.min(from, to); row < Math.max(from, to); row++) {
            BlockPos column = worldPosition.above(footRow - row);
            BlockState state = to > from ? extension() : Blocks.AIR.defaultBlockState();
            level.setBlock(column, state, Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        foot.forEach((cell, state) -> level.setBlock(worldPosition.offset(cell).below(to), state, Block.UPDATE_ALL));
        reach = to;
        setChanged();
    }

    /** All the way down: the leg's blocks back where they were and locked, once its foot has room under it. */
    private void lockDown(ServerLevel level) {
        if (aboveGround(level) < ROOM_BELOW) {
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

    /** Takes the leg's blocks away, each kept with its data; what hangs on the leg first, so nothing falls off it. */
    private void stow(ServerLevel level) {
        reachWanted = 0;
        if (reach > 0) {
            moveFoot(level, reach, 0);
        }
        List<Part> parts = new ArrayList<>();
        for (BlockPos offset : leg) {
            BlockPos at = worldPosition.offset(offset);
            BlockState there = level.getBlockState(at);
            if (!there.isAir()) {
                BlockEntity blockEntity = level.getBlockEntity(at);
                parts.add(new Part(offset, there, blockEntity == null ? null : blockEntity.saveWithoutMetadata(level.registryAccess())));
            }
        }
        parts.sort(Comparator.comparingInt(RetractableLegBlockEntity::hangsOn).reversed());
        for (Part part : parts) {
            BlockPos at = worldPosition.offset(part.offset());
            // Emptied first, so a wheel mount's tire goes with it rather than dropping to the ground.
            Clearable.tryClear(level.getBlockEntity(at));
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        }
        stowed = List.copyOf(parts);
    }

    /**
     * How a block of the leg hangs on the rest: 0 for the leg itself, 1 for what stands beside it, 2 for what is fixed
     * to its face.
     */
    private static int hangsOn(Part part) {
        if (part.state().getBlock() instanceof FaceAttachedHorizontalDirectionalBlock) {
            return 2;
        }
        return part.state().is(SkylinersRegistry.LANDING_GEAR.get()) ? 0 : 1;
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
     * How high the bottom of the leg's foot is above the ground under it, in blocks, whether the leg is down or not:
     * the bottom of its lowest block, or of the row it reaches down to under that, followed out into the world from
     * among an assembled ship's blocks. Standing as blocks, the ship's own blocks over the leg count as the ground: it
     * stands on its leg.
     */
    public double aboveGround(Level level) {
        int lowest = leg.stream().mapToInt(BlockPos::getY).min().orElse(0);
        BlockPos bottom = worldPosition.above(lowest - reachBelow() - reach);
        Vec3 foot = SableFlight.toWorld(level, Vec3.atBottomCenterOf(bottom));
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(foot.x), Mth.floor(foot.z));
        return foot.y - ground;
    }

    /** Whether a block is one a retractable leg has taken away, so links to it are not mended as if broken. */
    public static boolean stows(Level level, BlockPos pos) {
        for (RetractableLegBlockEntity leg : LOADED.in(level)) {
            if (leg.holds(pos)) {
                return true;
            }
        }
        return false;
    }

    /** Where each block the legs within a box have taken away stood, so the ship is measured as with its legs down. */
    public static List<BlockPos> stowedWithin(Level level, BoundingBox box) {
        List<BlockPos> found = new ArrayList<>();
        for (RetractableLegBlockEntity leg : LOADED.in(level)) {
            if (box.isInside(leg.worldPosition)) {
                leg.stowed.forEach(part -> found.add(leg.worldPosition.offset(part.offset())));
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
        tag.putInt("Reach", reach);
        tag.putInt("ReachWanted", reachWanted);
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
        Motion was = motion;
        List<Part> drew = stowed;
        motion = parse(tag.getString("Motion"));
        if (level != null && level.isClientSide && was == Motion.LOWERING && motion == Motion.DOWN) {
            lastDrawn = drew;
            drawnFor = DRAWN_AFTER_LOCK;
        }
        ticks = tag.getInt("Ticks");
        reach = tag.getInt("Reach");
        reachWanted = tag.getInt("ReachWanted");
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
