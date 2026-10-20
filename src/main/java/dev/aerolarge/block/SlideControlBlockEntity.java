package dev.aerolarge.block;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.FrameProtection;
import dev.aerolarge.compat.SableFlight;
import dev.aerolarge.entity.SlideRide;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A passenger door's evacuation slide, its gas bottle and what it knows, kept in its control in the cabin wall beside
 * the door; its renderer draws it (see {@link EvacuationSlide} for how it is built and comes out). Packed, it is in the
 * door's lining and nothing of it shows. Worked, the door opens, and once it is open the pack drops out onto the sill
 * and the slide unrolls and inflates down to the ground with a rush of gas, in two seconds; then the control lays the
 * steps under it (see {@link SlideStepBlock}) and whoever steps onto it from the doorway, players and villagers alike,
 * is carried down it (see {@link SlideRide}). Where it lands is measured from the world as it sets out, aboard an
 * assembled airliner too: at the first of its angles that comes down onto the ground within its reach, clear of the
 * airliner itself and anything else in its way. It only comes out while the airliner stands still, and not with the
 * ground out of reach, as in flight: it refuses with a bass note.
 *
 * <p>Worked again, it deflates and rolls itself back up into the doorway once nobody is on it (a chime says it is
 * waiting), and the door shuts behind it, as it does behind the airstairs: an open door with no slide is a long drop.
 * It goes away by itself the same way as soon as the airliner moves, and when the door it serves is shut. The server
 * runs it and tells every client where it is going; each client runs the same clock to draw it in between.
 */
public class SlideControlBlockEntity extends BlockEntity {
    /** How often a slide that is out looks whether the airliner has started to move, in ticks. */
    private static final int WATCH_TICKS = 10;
    /** While someone is on the slide, how often going away looks again whether they have got off. */
    private static final int CLEAR_CHECK_TICKS = 10;
    /** Faster than this, in blocks a second, the airliner is moving: the slide will not come out, and goes if out. */
    private static final double MOVING = 1.0;
    /** How far down the slide reaches, in blocks per unit of scale: past the sill's height over the wheels' ground. */
    private static final double REACH_PER_SCALE = 8;
    /** How far a door is looked for round a control put up by hand, in blocks across and up and down. */
    private static final int DOOR_SEARCH = 3;
    /** How far out of the doorway someone has to be to be on the slide, in blocks: their middle past the hull's face. */
    private static final float ON_FROM = 0.1F;
    /**
     * How far in from the doorway's edges a rider sits, between the rails even at the slide's narrow head, and how far
     * past them someone on the slide may be, in blocks.
     */
    private static final float SEAT_INSET = 0.6F;
    private static final float LANE_MARGIN = 0.5F;
    /** How far short of where riders stop someone is no longer on the slide but off its toe, in blocks. */
    private static final float OFF_SHORT = 0.4F;
    /** How far over the ground under the toe someone's feet are up on the slide, not on the ground by it, in blocks. */
    private static final float OFF_GROUND = 0.25F;
    private static final float GAS_VOLUME = 1.6F;
    private static final float LEVER_VOLUME = 0.8F;

    /** Where the door it serves is from the control: its bottom left part; null for none. */
    @Nullable
    private BlockPos door;
    /** Out or coming out; and how far through coming out it is, in ticks, now and a tick ago. */
    private boolean deployed;
    private int ticks;
    private int lastTicks;
    /** The slide as it was laid out for the ground below; null while packed. */
    @Nullable
    private EvacuationSlide.Plan plan;
    /** Whether the steps are laid under it. */
    private boolean laid;
    /** Going away is held up by someone on it; the chime has sounded. */
    private boolean held;
    private int cooldown;
    /** How far open the door was a tick ago, while the slide waits for it: shutting again, it calls the slide off. */
    private float doorWas;

    public SlideControlBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.SLIDE_CONTROL_ENTITY.get(), pos, state);
    }

    /** Hands the control the door it serves, where it is from it: its bottom left part. Built packed. */
    public void configure(BlockPos door) {
        this.door = door;
        deployed = false;
        ticks = 0;
        lastTicks = 0;
        plan = null;
        laid = false;
        held = false;
        setChanged();
    }

    /** Put up by hand: it serves the nearest plug door round it. */
    void findDoor() {
        if (level == null || door != null) {
            return;
        }
        BlockPos nearest = null;
        for (BlockPos at : BlockPos.betweenClosed(worldPosition.offset(-DOOR_SEARCH, -DOOR_SEARCH, -DOOR_SEARCH),
            worldPosition.offset(DOOR_SEARCH, DOOR_SEARCH, DOOR_SEARCH))) {
            if (PlugDoorBlock.isController(level.getBlockState(at)) && (nearest == null || at.distSqr(worldPosition) < nearest.distSqr(worldPosition))) {
                nearest = at.immutable();
            }
        }
        if (nearest != null) {
            configure(nearest.subtract(worldPosition));
        }
    }

    /** The served door's bottom left part, where it is; null when the control serves no plug door. */
    @Nullable
    public BlockPos served() {
        if (level == null || door == null) {
            return null;
        }
        BlockPos at = worldPosition.offset(door);
        return PlugDoorBlock.isController(level.getBlockState(at)) ? at : null;
    }

    @Nullable
    private PlugDoorBlockEntity servedDoor() {
        BlockPos at = served();
        return at == null || level == null ? null : PlugDoorBlockEntity.of(level, at);
    }

    /** The slide as laid out; null while packed. */
    @Nullable
    public EvacuationSlide.Plan plan() {
        return plan;
    }

    /** How far through coming out the slide is, 0 packed to 1 out, between the last tick and this one. */
    public float progress(float partialTick) {
        return Mth.lerp(partialTick, lastTicks, ticks) / EvacuationSlide.INFLATE;
    }

    /** Worked from inside: brings the slide out if it is packed or going away, sends it away if it is out or coming out. */
    public void toggle(@Nullable Player player) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        server.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, LEVER_VOLUME, deployed ? 0.5F : 0.6F);
        if (deployed) {
            retract(server);
        } else {
            deploy(server, player);
        }
    }

    /** Sets the slide coming out, unless the airliner is moving or the slide has nowhere to come down. */
    private void deploy(ServerLevel level, @Nullable Player player) {
        PlugDoorBlockEntity served = servedDoor();
        Frame frame = frame();
        if (served == null || frame == null) {
            refuse(level, player, "no_door");
            return;
        }
        if (moving(level)) {
            refuse(level, player, "moving");
            return;
        }
        if (ticks == 0) {
            EvacuationSlide.Outcome outcome = EvacuationSlide.plan((x, y, z) -> cell(level, frame, x, y, z), maxDrop());
            if (outcome.plan() == null) {
                refuse(level, player, outcome.refusal().name().toLowerCase(Locale.ROOT));
                return;
            }
            plan = outcome.plan();
        } else if (ticks < EvacuationSlide.INFLATE) {
            gas(level, AerolargeRegistry.SLIDE_INFLATE.get());
        }
        deployed = true;
        held = false;
        doorWas = served.progress(1);
        served.set(true);
        lever();
        sync();
    }

    /** Sets the slide going away: at once, or once nobody is on it; the door shuts once it is packed. */
    private void retract(ServerLevel level) {
        deployed = false;
        lever();
        if (ticks == 0) {
            // Called off before it came out: the door shuts again.
            plan = null;
            shutDoor();
        } else if (laid && anyoneOn(level)) {
            held = true;
            cooldown = CLEAR_CHECK_TICKS;
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
        } else {
            deflate(level);
        }
        sync();
    }

    /** Takes the steps up and lets the gas out: the slide deflates and rolls itself back up. */
    private void deflate(ServerLevel level) {
        if (laid) {
            takeUp(level);
        }
        gas(level, AerolargeRegistry.SLIDE_DEFLATE.get());
    }

    private void refuse(ServerLevel level, @Nullable Player player, String why) {
        level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
        if (player != null) {
            player.displayClientMessage(Component.translatable("message.aerolarge.slide." + why), true);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SlideControlBlockEntity slide) {
        if (level instanceof ServerLevel server) {
            slide.tick(server);
        }
    }

    private void tick(ServerLevel level) {
        lastTicks = ticks;
        if (plan == null) {
            return;
        }
        PlugDoorBlockEntity served = servedDoor();
        if (served == null) {
            stowAway();
            return;
        }
        float doorOpen = served.progress(1);
        if (deployed && (level.getGameTime() % WATCH_TICKS == 0 && moving(level) || ticks > 0 && doorOpen < 1 || ticks == 0 && doorOpen < doorWas)) {
            // Moving off, or the door shut on it: it goes away.
            retract(level);
        }
        doorWas = doorOpen;
        if (deployed) {
            if (ticks < EvacuationSlide.INFLATE && doorOpen >= 1) {
                if (ticks == 0) {
                    gas(level, AerolargeRegistry.SLIDE_INFLATE.get());
                }
                ticks++;
                if (ticks == EvacuationSlide.INFLATE) {
                    lay(level);
                }
                if (ticks == 1 || ticks == EvacuationSlide.INFLATE) {
                    sync();
                }
                setChanged();
            } else if (ticks == EvacuationSlide.INFLATE) {
                carry(level);
            }
        } else if (held) {
            // Anyone still stepping on gets a ride down rather than a fall.
            carry(level);
            if (--cooldown <= 0) {
                cooldown = CLEAR_CHECK_TICKS;
                if (!anyoneOn(level)) {
                    held = false;
                    deflate(level);
                    sync();
                }
            }
        } else if (ticks > 0) {
            ticks--;
            if (ticks == 0) {
                plan = null;
                shutDoor();
                sync();
            }
            setChanged();
        }
    }

    public static void clientTick(Level level, BlockPos pos, BlockState state, SlideControlBlockEntity slide) {
        slide.lastTicks = slide.ticks;
        if (slide.plan == null || slide.held) {
            return;
        }
        if (slide.deployed) {
            PlugDoorBlockEntity served = slide.servedDoor();
            if (slide.ticks < EvacuationSlide.INFLATE && served != null && served.progress(1) >= 1) {
                slide.ticks++;
            }
        } else {
            slide.ticks = Math.max(0, slide.ticks - 1);
        }
    }

    /** The door's frame: where its bottom left part is, the way out of the hull and the way across it to the right. */
    @Nullable
    private Frame frame() {
        BlockPos at = served();
        if (at == null || level == null) {
            return null;
        }
        BlockState part = level.getBlockState(at);
        return new Frame(at, part.getValue(PlugDoorBlock.FACING), PlugDoorBlock.right(part));
    }

    /**
     * The door's own frame (see {@link EvacuationSlide}) among the blocks it stands in: x across the doorway to the
     * right as seen from outside, y up from the sill, z out, from the corner of its bottom left part.
     */
    private record Frame(BlockPos door, Direction out, Direction right) {
        BlockPos cell(int x, int y, int z) {
            return door.relative(right, x).above(y).relative(out, z);
        }

        Vec3 point(double x, double y, double z) {
            Vec3 corner = Vec3.atBottomCenterOf(door);
            return corner.add(Vec3.atLowerCornerOf(right.getNormal()).scale(x - 0.5)).add(0, y, 0)
                .add(Vec3.atLowerCornerOf(out.getNormal()).scale(z - 0.5));
        }

        /** A point among the blocks seen in the frame: x across, y up, z out. */
        Vec3 local(Vec3 point) {
            Vec3 from = point.subtract(Vec3.atBottomCenterOf(door));
            return new Vec3(from.dot(Vec3.atLowerCornerOf(right.getNormal())) + 0.5, from.y, from.dot(Vec3.atLowerCornerOf(out.getNormal())) + 0.5);
        }
    }

    /**
     * What is in a block of the slide's way, in the door's frame: looked at in the world where that block is, and in
     * any assembled airliner there. The airliner's own frame, and the airstairs', is nothing to come down on.
     */
    private static EvacuationSlide.Cell cell(ServerLevel level, Frame frame, int x, int y, int z) {
        Vec3 point = SableFlight.toWorld(level, Vec3.atCenterOf(frame.cell(x, y, z)));
        BlockPos hit = SableFlight.find(level, point, SlideControlBlockEntity::solid);
        if (hit == null) {
            return EvacuationSlide.Cell.OPEN;
        }
        BlockState state = level.getBlockState(hit);
        return state.is(FrameProtection.UNBREAKABLE) || state.getBlock() instanceof AirstairStepBlock
            ? EvacuationSlide.Cell.BLOCKED : EvacuationSlide.Cell.GROUND;
    }

    private static boolean solid(BlockState state) {
        return !state.isAir() && !state.canBeReplaced() && !(state.getBlock() instanceof SlideStepBlock);
    }

    /** How far down the slide reaches, at the server's scale. */
    private static float maxDrop() {
        return (float) (REACH_PER_SCALE * AerolargeConfig.SCALE.get());
    }

    private boolean moving(ServerLevel level) {
        return SableFlight.velocity(level, Vec3.atCenterOf(worldPosition)).length() > MOVING;
    }

    /** Lays the steps under the lane. */
    private void lay(ServerLevel level) {
        Frame frame = frame();
        if (frame == null || plan == null) {
            return;
        }
        BlockState block = AerolargeRegistry.SLIDE_STEP.get().defaultBlockState();
        for (int[] step : EvacuationSlide.steps(plan)) {
            BlockPos at = frame.cell(step[0], step[1], step[2]);
            BlockState there = level.getBlockState(at);
            if (there.isAir() || there.canBeReplaced()) {
                level.setBlock(at, SlideStepBlock.step(block, frame.out().getOpposite(), step[3]), Block.UPDATE_ALL);
            }
        }
        laid = true;
    }

    /** Takes the steps up: only the steps' own blocks, whatever stands in their place. */
    private void takeUp(ServerLevel level) {
        Frame frame = frame();
        if (frame != null && plan != null) {
            for (int[] step : EvacuationSlide.steps(plan)) {
                BlockPos at = frame.cell(step[0], step[1], step[2]);
                if (level.getBlockState(at).getBlock() instanceof SlideStepBlock) {
                    level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        laid = false;
    }

    /** Whether a block is one of the steps this control has laid. */
    public boolean holds(Level level, BlockPos pos) {
        Frame frame = frame();
        if (!laid || level != this.level || frame == null || plan == null) {
            return false;
        }
        for (int[] step : EvacuationSlide.steps(plan)) {
            if (pos.equals(frame.cell(step[0], step[1], step[2]))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Everyone alive round the slide, in the world: the slide's frame from a little in from the doorway to past its
     * toe, from the ground up over the doorway, projected out of the airliner's blocks wherever it is assembled.
     */
    private <T extends Entity> List<T> around(ServerLevel level, Frame frame, EvacuationSlide.Path path, Class<T> type) {
        AABB all = null;
        for (int corner = 0; corner < 8; corner++) {
            double x = (corner & 1) == 0 ? -1 : EvacuationSlide.WIDTH + 1;
            double y = (corner & 2) == 0 ? path.groundY() - 1 : 3;
            double z = (corner & 4) == 0 ? 0 : 2 + path.reach();
            Vec3 point = SableFlight.toWorld(level, frame.point(x, y, z));
            all = all == null ? new AABB(point, point) : all.minmax(new AABB(point, point));
        }
        return level.getEntitiesOfClass(type, all, Entity::isAlive);
    }

    /** Where an entity's feet are in the door's frame. */
    private Vec3 feet(ServerLevel level, Frame frame, Entity entity) {
        return frame.local(SableFlight.toPlot(level, worldPosition, entity.position(), false));
    }

    /**
     * Carries whoever has stepped onto the slide down it: anyone alive whose middle is out past the hull's face, over
     * the lane, standing on it or just over or under it, not riding anything already. Each sits where they stepped on,
     * as far across the lane as they were.
     */
    private void carry(ServerLevel level) {
        Frame frame = frame();
        if (frame == null || plan == null) {
            return;
        }
        EvacuationSlide.Path path = plan.path();
        for (LivingEntity entity : around(level, frame, path, LivingEntity.class)) {
            if (entity.isPassenger() || entity.isVehicle() || entity.isSpectator() || entity instanceof ArmorStand || entity instanceof Player player && player.getAbilities().flying) {
                continue;
            }
            Vec3 feet = feet(level, frame, entity);
            float u = (float) feet.z - 1;
            if (u < ON_FROM || u > path.rideEnd() - OFF_SHORT || feet.x < -LANE_MARGIN || feet.x > EvacuationSlide.WIDTH + LANE_MARGIN) {
                continue;
            }
            float surface = path.surface(u);
            if (feet.y < surface - 1 || feet.y > surface + 1.2) {
                continue;
            }
            float along = path.along(u);
            EvacuationSlide.Point point = path.at(along);
            Vec3 seat = frame.point(Mth.clamp(feet.x, SEAT_INSET, EvacuationSlide.WIDTH - SEAT_INSET), point.y(), 1 + point.u());
            Vec3 seatInWorld = SableFlight.toWorld(level, seat);
            Vec3 out = SableFlight.toWorld(level, seat.add(Vec3.atLowerCornerOf(frame.out().getNormal()))).subtract(seatInWorld);
            Vec3 up = SableFlight.toWorld(level, seat.add(0, 1, 0)).subtract(seatInWorld);
            SlideRide.carry(level, entity, seatInWorld, out, up, plan, along);
        }
    }

    /**
     * Whether anyone is on the slide, riding it down or up on it, or in the way just over it; not whoever stands on the
     * ground by its toe.
     */
    private boolean anyoneOn(ServerLevel level) {
        Frame frame = frame();
        if (frame == null || plan == null) {
            return false;
        }
        EvacuationSlide.Path path = plan.path();
        if (!around(level, frame, path, SlideRide.class).isEmpty()) {
            return true;
        }
        for (LivingEntity entity : around(level, frame, path, LivingEntity.class)) {
            Vec3 feet = feet(level, frame, entity);
            float u = (float) feet.z - 1;
            if (u >= ON_FROM && u <= path.reach() && feet.x > -1 && feet.x < EvacuationSlide.WIDTH + 1) {
                float surface = path.surface(u);
                if (feet.y > Math.max(surface - 1.5, path.groundY() + OFF_GROUND) && feet.y < surface + 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private void shutDoor() {
        PlugDoorBlockEntity served = servedDoor();
        if (served != null) {
            served.set(false);
        }
    }

    /** The lever and the lamp beside it show the slide out or packed. */
    private void lever() {
        if (level != null && getBlockState().getValue(SlideControlBlock.DEPLOYED) != deployed) {
            level.setBlock(worldPosition, getBlockState().setValue(SlideControlBlock.DEPLOYED, deployed), Block.UPDATE_CLIENTS);
        }
    }

    /** The gas rushing in or out, heard from the doorway's foot. */
    private void gas(ServerLevel level, SoundEvent sound) {
        BlockPos at = served();
        level.playSound(null, at == null ? worldPosition : at, sound, SoundSource.BLOCKS, GAS_VOLUME, 1.0F);
    }

    /** Takes the slide away at once, steps and all, as when the control itself is broken. */
    public void stowAway() {
        if (level instanceof ServerLevel server && laid) {
            takeUp(server);
        }
        deployed = false;
        held = false;
        ticks = 0;
        plan = null;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (door != null) {
            tag.put("Door", NbtUtils.writeBlockPos(door));
        }
        tag.putBoolean("Deployed", deployed);
        tag.putInt("Ticks", ticks);
        tag.putBoolean("Laid", laid);
        tag.putBoolean("Held", held);
        if (plan != null) {
            tag.putFloat("Angle", plan.angle());
            tag.putFloat("ToeY", plan.toeY());
            tag.putFloat("Toe", plan.toe());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        door = NbtUtils.readBlockPos(tag, "Door").orElse(null);
        deployed = tag.getBoolean("Deployed");
        ticks = Mth.clamp(tag.getInt("Ticks"), 0, EvacuationSlide.INFLATE);
        lastTicks = ticks;
        laid = tag.getBoolean("Laid");
        held = tag.getBoolean("Held");
        plan = tag.contains("Angle") ? new EvacuationSlide.Plan(tag.getFloat("Angle"), tag.getFloat("ToeY"), tag.getFloat("Toe")) : null;
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
