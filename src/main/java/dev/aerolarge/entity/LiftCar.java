package dev.aerolarge.entity;

import dev.aerolarge.block.LiftMachineBlockEntity;
import dev.aerolarge.compat.SableFlight;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * The car of a ship's lift (see {@link LiftMachineBlockEntity}): a platform that rides up and down the glass shaft
 * under its machine, its riders standing on it, up to four. It lives among the ship's blocks, in her plot (its type is
 * in {@code #sable:retain_in_sub_level}), so Sable carries it and its riders as she flies and tilts, and it moves only
 * along the shaft. Where it is on the shaft follows from a ride's start and end and the tick it set off, which both
 * sides know, so the server sends no moves for it: it eases off, runs at the lift's speed and eases in (see
 * {@link #travelled}).
 *
 * <p>Its machine holds it every tick; left without its machine, as when the ship is taken away, it lets its riders
 * off where they are and goes. No one gets off it between floors: only its machine sets riders down, at an open door.
 */
public class LiftCar extends Entity {
    /** The lift's top speed, in blocks a tick: six a second. */
    public static final double SPEED = 0.3;
    /** How long it takes to come up to its top speed from standing, and down again, in ticks. */
    public static final int EASE = 20;
    /** How many riders it carries at most. */
    public static final int RIDERS = 4;
    /** How far out from the shaft's middle its riders stand, in blocks, each in a quarter of the car. */
    private static final double RIDER_OUT = 0.55;
    /** How long it may go without its machine before it lets its riders off and goes, in ticks. */
    private static final int MACHINE_LOST = 40;
    /** What a client does with a car each tick, set by the client (its motor's hum): nothing on a server. */
    public static Consumer<LiftCar> clientTicker = car -> {
    };

    /**
     * Its machine, as a block among the ship's blocks; how far under the machine's bottom its floor stood as the ride
     * set off and will stand at its end, in blocks; and the game tick it set off.
     */
    private static final EntityDataAccessor<BlockPos> MACHINE = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Float> FROM = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TO = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(LiftCar.class, EntityDataSerializers.LONG);

    /** The game tick its machine last held it. */
    private long heldAt;
    /** Where its riders get off, among the ship's blocks, while its machine lets them; null while it may not. */
    @Nullable
    private Vec3 letOffAt;
    /** Whether its machine is setting a rider down now, which no sneaking stops. */
    private boolean releasing;
    /**
     * The riders who sneaked to get off since its machine last held it, for the machine to set down outside the door if
     * it stands open: the game forgets a rider's sneak in the tick it is refused.
     */
    private final Set<UUID> askedOff = new HashSet<>();

    public LiftCar(EntityType<? extends LiftCar> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MACHINE, BlockPos.ZERO);
        builder.define(FROM, 0F);
        builder.define(TO, 0F);
        builder.define(START, 0L);
    }

    /** Its machine, among the ship's blocks. */
    public BlockPos machine() {
        return entityData.get(MACHINE);
    }

    /**
     * Held by its machine this tick: where the machine is, and, when a new ride sets off, how far under it the floor
     * stands now and will at its end.
     */
    public void hold(ServerLevel level, BlockPos machine, @Nullable Vec3 letOff) {
        entityData.set(MACHINE, machine);
        heldAt = level.getGameTime();
        letOffAt = letOff;
        askedOff.clear();
        follow();
    }

    /** Whether a rider sneaked to get off since its machine last held it. */
    public boolean askedOff(Entity rider) {
        return askedOff.contains(rider.getUUID());
    }

    /** Sets a ride off from where the car stands now to so far under its machine, in blocks, from this tick on. */
    public void ride(ServerLevel level, double to) {
        entityData.set(FROM, (float) depth(0));
        entityData.set(TO, (float) to);
        entityData.set(START, level.getGameTime());
    }

    /** Stands the car still so far under its machine, in blocks, as it is placed or snapped to a floor. */
    public void standAt(double depth) {
        entityData.set(FROM, (float) depth);
        entityData.set(TO, (float) depth);
        entityData.set(START, 0L);
        follow();
    }

    /** Whether the ride has come to its end: the car stands still at a floor. */
    public boolean arrived() {
        double distance = Math.abs(entityData.get(TO) - entityData.get(FROM));
        return level().getGameTime() - entityData.get(START) >= duration(distance);
    }

    /** How far under its machine's bottom its floor stands this tick and partial tick, in blocks. */
    public double depth(float partialTick) {
        double from = entityData.get(FROM);
        double to = entityData.get(TO);
        double ticks = level().getGameTime() - entityData.get(START) + partialTick;
        return from + Math.signum(to - from) * travelled(Math.abs(to - from), ticks);
    }

    /** Whether it is going up or down this tick: 1 down, -1 up, 0 standing. */
    public int heading() {
        return arrived() ? 0 : (int) Math.signum(entityData.get(TO) - entityData.get(FROM));
    }

    /**
     * How far a ride of a length, in blocks, has gone after so many ticks: easing off over {@link #EASE} ticks to its
     * top speed, running at it, and easing in to stand at its end, or, on a ride too short to come up to it, easing off
     * halfway and in again.
     */
    public static double travelled(double length, double ticks) {
        double rate = SPEED / EASE;
        double total = duration(length);
        if (ticks >= total) {
            return length;
        }
        double ease = Math.min(EASE, total / 2);
        if (ticks < ease) {
            return rate * ticks * ticks / 2;
        }
        double left = total - ticks;
        if (left < ease) {
            return length - rate * left * left / 2;
        }
        return rate * ease * ease / 2 + SPEED * (ticks - ease);
    }

    /** How long a ride of a length, in blocks, takes, in ticks. */
    public static double duration(double length) {
        if (length >= SPEED * EASE) {
            return length / SPEED + EASE;
        }
        return 2 * Math.sqrt(length * EASE / SPEED);
    }

    @Override
    public void tick() {
        super.tick();
        follow();
        if (level().isClientSide) {
            clientTicker.accept(this);
        } else if (level().getGameTime() - heldAt > MACHINE_LOST) {
            ejectPassengers();
            discard();
        }
    }

    /** Stands it where its ride has it now, under its machine, on either side. */
    private void follow() {
        BlockPos machine = machine();
        double depth = depth(0);
        setPos(machine.getX() + 0.5, machine.getY() - depth, machine.getZ() + 0.5);
    }

    /**
     * Its riders stand each in a quarter of the car, facing as they will, their feet on its floor: the first two in the
     * quarters by its doors, a step back from the board on the far wall that they pick their floor on, and the next two
     * behind them. The game lowers a rider by where it sits on a vehicle, which a standing rider does not, so that is
     * put back.
     */
    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        int place = Math.max(0, getPassengers().indexOf(passenger));
        Direction doors = doorsSide();
        Direction side = doors.getClockWise();
        double across = (place & 1) == 0 ? -RIDER_OUT : RIDER_OUT;
        double along = (place & 2) == 0 ? RIDER_OUT : -RIDER_OUT;
        Vec3 spot = Vec3.atLowerCornerOf(side.getNormal()).scale(across).add(Vec3.atLowerCornerOf(doors.getNormal()).scale(along));
        return position().add(spot).add(passenger.getVehicleAttachmentPoint(this));
    }

    /** The side of the shaft its doors are on, as its machine's landings have them; north without them. */
    private Direction doorsSide() {
        if (!(level().getBlockEntity(machine()) instanceof LiftMachineBlockEntity lift) || lift.landings().isEmpty()) {
            return Direction.NORTH;
        }
        BlockPos door = lift.landings().getFirst().door();
        return Direction.getNearest(door.getX(), 0, door.getZ());
    }

    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return letOffAt != null ? letOffAt : position();
    }

    @Override
    public boolean shouldRiderSit() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().size() < RIDERS;
    }

    /** Whether its riders may get off now: its machine has its door open at a floor. */
    public boolean lettingOff() {
        return letOffAt != null;
    }

    /**
     * A rider set down by its machine, which then puts them outside the door: the only way off but for the rider
     * going, dying or being teleported.
     */
    public void release(Entity rider) {
        releasing = true;
        rider.stopRiding();
        releasing = false;
    }

    /**
     * No one gets off by sneaking, between floors or at one: at a floor whose door is open, its machine sets a sneaking
     * rider down outside the door itself (see {@link LiftMachineBlockEntity}), as it does its riders as the car comes
     * in. Left to the game, a rider would be set down in the car, where the doors would shut on them.
     */
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof LiftCar car && car.isAlive() && !car.releasing
            && !event.getLevel().isClientSide && event.getEntityMounting().isAlive() && event.getEntityMounting().isShiftKeyDown()) {
            event.setCanceled(true);
            car.askedOff.add(event.getEntityMounting().getUUID());
        }
    }

    /**
     * A rider leaving the game is set down first, where the car's machine lets riders off or else where the car is:
     * the game would otherwise save the car with them and bring a copy of it back when they come again.
     */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getVehicle() instanceof LiftCar car) {
            Vec3 off = car.letOffAt != null ? car.letOffAt : SableFlight.toWorld(car.level(), car.position());
            event.getEntity().stopRiding();
            event.getEntity().teleportTo(off.x, off.y, off.z);
        }
    }

    /** Its place comes from its ride's clock, as this side has it, never from the server's last word. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps) {
    }

    @Override
    public void lerpMotion(double x, double y, double z) {
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        NbtUtils.readBlockPos(tag, "Machine").ifPresent(pos -> entityData.set(MACHINE, pos));
        float depth = tag.getFloat("Depth");
        entityData.set(FROM, depth);
        entityData.set(TO, depth);
        entityData.set(START, 0L);
        heldAt = level().getGameTime();
    }

    /** Only where it stands is kept: loaded again it stands still there, and its machine sends it on to a floor. */
    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Machine", NbtUtils.writeBlockPos(machine()));
        tag.putFloat("Depth", (float) depth(0));
    }
}
