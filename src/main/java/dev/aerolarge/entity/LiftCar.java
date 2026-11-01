package dev.aerolarge.entity;

import dev.aerolarge.block.LiftFloorBlock;
import dev.aerolarge.block.LiftMachineBlockEntity;
import dev.aerolarge.compat.SableFlight;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.fml.util.thread.EffectiveSide;

/**
 * The car of a ship's lift (see {@link LiftMachineBlockEntity}): a platform that rides up and down the glass shaft
 * under its machine. It lives among the ship's blocks, in her plot (its type is in {@code #sable:retain_in_sub_level}),
 * so Sable carries it as she flies and tilts, and it moves only along the shaft. Where it is on the shaft follows from
 * a ride's start and end and the tick it set off, which both sides know, so the server sends no moves for it: it eases
 * off, runs at the lift's speed and eases in (see {@link #travelled}); a client times the ride by its own count of the
 * level's ticks (see {@link #clientTicks}).
 *
 * <p>Whoever is in it stands on its floor and walks about freely, at a floor and on a ride, as in any lift: the shaft
 * is filled with blocks of its floor (see {@link LiftFloorBlock}), each solid as far as the car's floor lies in it now
 * (see {@link #floorIn}), so the floor rides along the shaft with the car. Each client carries its own player along on
 * it (see {@code client.LiftRide}); on the server, which leaves a player's moves to their game, the car carries
 * everything else standing on it (see {@link #carry}). Its machine holds it every tick; left without its machine, as
 * when the ship is taken away, it goes.
 */
public class LiftCar extends Entity {
    /**
     * The lift's top speed, in blocks a tick: twelve a second, an express lift's, so it comes the whole height of a tall
     * ship in about ten seconds.
     */
    public static final double SPEED = 0.6;
    /** How long it takes to come up to its top speed from standing, and down again, in ticks. */
    public static final int EASE = 25;
    /** How far out from the shaft's middle its floor reaches each way, in blocks: the shaft's opening, three wide. */
    public static final double FLOOR_HALF = 1.5;
    /** How far over its floor something's feet may be and still stand on it, carried along, in blocks. */
    public static final double STANDING = 0.1;
    /**
     * How far under its floor's top something's feet may be and still be lifted back onto it, in blocks: the floor may
     * rise past a foot between two ticks, as when someone jumps or flies in a car going up, and no one drops down the
     * shaft.
     */
    public static final double SUNK = 1.5;
    /**
     * How far over and under its floor the car looks for what is in it as it rides, in blocks: anything falling after a
     * jump in a car going down stays within this of the floor.
     */
    private static final double REACH = 8;
    /** How long it may go without its machine before it goes, in ticks. */
    private static final int MACHINE_LOST = 40;
    /** What a client does with a car each tick, set by the client (its motor's hum): nothing on a server. */
    public static Consumer<LiftCar> clientTicker = car -> {
    };
    /**
     * The client's count of its level's ticks, one each time the level ticks before anything in it does (see {@code
     * client.LiftRide}), which every car times its ride by there. The game's clock would not do: the server sets it
     * back every second wherever the client has run ahead of it, and timed by it the car, and its floor under whoever
     * stands in it, would jump back a little each time, a story at its full speed.
     */
    private static long clientTicks;
    /**
     * The cars in loaded levels on each side, for the shaft's floor blocks and the client's player to find theirs; held
     * weakly, so a level left behind is not kept by its cars, and read under their lock, as a block's shape may be
     * asked for off the side's own thread.
     */
    private static final Set<LiftCar> SERVER_CARS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));
    private static final Set<LiftCar> CLIENT_CARS = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

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
    /** On a client: when the ride now under way set off, by {@link #clientTicks}. */
    private long rideStartedAt;

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

    /** One more tick of the client's level, before anything in it ticks. */
    public static void clientTick() {
        clientTicks++;
    }

    /** The cars loaded in a level, in no order. */
    public static List<LiftCar> cars(Level level) {
        Set<LiftCar> side = level.isClientSide ? CLIENT_CARS : SERVER_CARS;
        synchronized (side) {
            return side.stream().filter(car -> car.level() == level && !car.isRemoved()).toList();
        }
    }

    /**
     * The car whose shaft a block among a ship's blocks is in, under its machine and within its floor; or none. Asked
     * for by every floor block whose shape is wanted, so it looks through the cars where they are kept. The blocks may
     * be read through the level or through something else reading them, as Sable's collision with a ship's blocks reads
     * them through its own reader of the level: then the side is the thread's.
     */
    @Nullable
    public static LiftCar inShaft(BlockGetter blocks, BlockPos cell) {
        Level level = blocks instanceof Level found ? found : null;
        boolean client = level != null ? level.isClientSide : EffectiveSide.get().isClient();
        Set<LiftCar> side = client ? CLIENT_CARS : SERVER_CARS;
        synchronized (side) {
            for (LiftCar car : side) {
                if ((level == null || car.level() == level) && !car.isRemoved() && car.shaftHolds(cell)) {
                    return car;
                }
            }
        }
        return null;
    }

    /** Whether a block among the ship's blocks is in its shaft: under its machine, within its floor. */
    private boolean shaftHolds(BlockPos cell) {
        BlockPos machine = machine();
        return cell.getY() < machine.getY() && Math.abs(cell.getX() - machine.getX()) <= 1 && Math.abs(cell.getZ() - machine.getZ()) <= 1;
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        (level().isClientSide ? CLIENT_CARS : SERVER_CARS).add(this);
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        (level().isClientSide ? CLIENT_CARS : SERVER_CARS).remove(this);
    }

    /** Its machine, among the ship's blocks. */
    public BlockPos machine() {
        return entityData.get(MACHINE);
    }

    /** Held by its machine this tick. */
    public void hold(ServerLevel level, BlockPos machine) {
        entityData.set(MACHINE, machine);
        heldAt = level.getGameTime();
        follow();
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
        return riding(0) >= duration(distance);
    }

    /** How far under its machine's bottom its floor stands this tick and partial tick, in blocks. */
    public double depth(float partialTick) {
        return depthAfter(riding(partialTick));
    }

    /** How far under its machine's bottom its floor stands so many ticks into the ride, in blocks. */
    private double depthAfter(double ticks) {
        double from = entityData.get(FROM);
        double to = entityData.get(TO);
        return from + Math.signum(to - from) * travelled(Math.abs(to - from), Math.max(0, ticks));
    }

    /** How high its floor's top is among the ship's blocks this tick, where whoever is in it stands. */
    public double floorTop() {
        return machine().getY() - depth(0);
    }

    /** How far its floor rose this tick, in blocks: less than nothing going down. */
    public double rose() {
        double ticks = riding(0);
        return depthAfter(ticks - 1) - depthAfter(ticks);
    }

    /**
     * How long the ride has been under way at this partial tick, in ticks: by the game's clock on the server, and on a
     * client by {@link #clientTicks}, which runs on smoothly whatever the server sets the clock to.
     */
    private double riding(float partialTick) {
        long ticks = level().isClientSide ? clientTicks - rideStartedAt : level().getGameTime() - entityData.get(START);
        return ticks + partialTick;
    }

    /**
     * A ride's start from the server, on a client, timed from there on by {@link #clientTicks}. A ride that starts
     * while the client has the car sets off there as it hears of it, whatever the game's clock says: the client's clock
     * runs on ahead of a lagging server's until the server sets it back, by seconds after a stall, and reckoned by it
     * the car's floor would leap blocks up the shaft at once, out from under whoever stands on it. The ride the car
     * brings along as the client first has it is reckoned by the clock, as it may long since have ended.
     */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key == START && level().isClientSide) {
            boolean firstHeard = tickCount == 0;
            rideStartedAt = clientTicks - (firstHeard ? Math.max(0, level().getGameTime() - entityData.get(START)) : 0);
        }
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

    /**
     * Its floor as far as it lies in a cell of its shaft, for the floor block there (see {@link LiftFloorBlock}): from
     * the cell's foot up to the floor's top, where the top is in it. On the server it is there only once the car stands
     * at a floor, and for a player only from over it: whoever is in the car is carried as it rides by their own game,
     * or by the car (see {@link #carry}), and a player's game times the ride by its own clock, which the server's,
     * lagging or catching up, runs behind or ahead of, so the server's floor would pull them back down or bar their way
     * up.
     */
    public VoxelShape floorIn(BlockPos cell, CollisionContext context) {
        double top = floorTop() - cell.getY();
        if (top <= 0 || top > 1) {
            return Shapes.empty();
        }
        VoxelShape floor = Shapes.box(0, 0, 0, 1, top, 1);
        if (level().isClientSide) {
            return floor;
        }
        boolean player = context instanceof EntityCollisionContext entity && entity.getEntity() instanceof Player;
        return arrived() && (!player || context.isAbove(floor, cell, false)) ? floor : Shapes.empty();
    }

    /**
     * How high to stand something whose feet are at a point among the ship's blocks, on its floor; or NaN where it is
     * not on it: off the floor's edge, in the air over it, or too far under it. Standing on it as it rode this tick, it
     * goes along; sunk under its top, it is lifted back onto it.
     */
    public double standing(Vec3 feet) {
        BlockPos machine = machine();
        double floor = floorTop();
        double rose = rose();
        double before = floor - rose;
        boolean over = Math.abs(feet.x - machine.getX() - 0.5) < FLOOR_HALF && Math.abs(feet.z - machine.getZ() - 0.5) < FLOOR_HALF;
        if (!over || feet.y > Math.max(before, floor) + STANDING || feet.y < floor - SUNK) {
            return Double.NaN;
        }
        double carried = Math.abs(feet.y - before) <= STANDING ? feet.y + rose : feet.y;
        return Math.max(carried, floor);
    }

    @Override
    public void tick() {
        super.tick();
        follow();
        if (level().isClientSide) {
            clientTicker.accept(this);
            return;
        }
        if (level().getGameTime() - heldAt > MACHINE_LOST) {
            discard();
            return;
        }
        if (rose() != 0) {
            carry((ServerLevel) level());
        }
    }

    /**
     * On the server, as it rides: nothing in it is falling, or a jump in a car going down, which drops away under it,
     * would hurt as a fall down the shaft, where it falls no more than a few blocks in the car; and everything but
     * players standing on its floor goes along with it, lifted back onto it where the floor rose past it (see
     * {@link #standing}). Players are carried by their own game.
     */
    private void carry(ServerLevel level) {
        Vec3 middle = SableFlight.toWorld(level, position());
        AABB around = new AABB(middle, middle).inflate(FLOOR_HALF + 1, REACH, FLOOR_HALF + 1);
        for (Entity inside : level.getEntities(this, around, entity -> !entity.isPassenger() && !entity.noPhysics)) {
            carry(level, inside, SableFlight.toPlot(level, machine(), inside.position(), false));
        }
    }

    /**
     * One thing near the car, its feet where given among the ship's blocks, carried as it rides if it is in the shaft
     * over the car's floor: there, as a car going down drops away from a jump faster than anything falls at first, it
     * may be well over the floor and still in the car.
     */
    private void carry(ServerLevel level, Entity inside, Vec3 feet) {
        BlockPos machine = machine();
        double floor = floorTop();
        boolean over = Math.abs(feet.x - machine.getX() - 0.5) < FLOOR_HALF && Math.abs(feet.z - machine.getZ() - 0.5) < FLOOR_HALF;
        if (!over || feet.y < floor - SUNK || feet.y > machine.getY()) {
            return;
        }
        inside.resetFallDistance();
        double stand = standing(feet);
        if (inside instanceof Player || Double.isNaN(stand) || stand == feet.y) {
            return;
        }
        inside.setPos(SableFlight.toWorld(level, new Vec3(feet.x, stand, feet.z)));
        inside.setDeltaMovement(inside.getDeltaMovement().multiply(1, 0, 1));
    }

    /** Stands it where its ride has it now, under its machine, on either side. */
    private void follow() {
        BlockPos machine = machine();
        setPos(machine.getX() + 0.5, machine.getY() - depth(0), machine.getZ() + 0.5);
    }

    /** No one rides it: whoever is in it stands on its floor. */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return false;
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
