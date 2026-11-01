package dev.aerolarge.entity;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.EvacuationSlide;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import org.joml.Vector3f;

/**
 * A ride down an evacuation slide: what carries whoever steps onto a slide (see
 * {@code SlideControlBlockEntity}) down its lane and off its toe onto the ground, seated, quickly and smoothly, with no
 * fall to hurt them. It runs down the lane's middle line from where they stepped on, picking up speed down the slope and
 * slowing along the toe, and sets them down on the ground just past its end with a little push on. Sneaking does not
 * get anyone off halfway. It knows the slide as it lay in the world when they stepped on: the seat there, and the ways
 * out and up of the slide's door. Both sides run the same clock from the same start, so the ride is worked out alike on
 * the server and on every client, and nobody's view of it waits on the network.
 */
public class SlideRide extends Entity {
    /** How fast a ride sets off, picks up speed down the slope, how fast it may go, and how it slows along the toe. */
    private static final float SET_OFF = 0.15F;
    private static final float SPEED_UP = 0.03F;
    private static final float TOP_SPEED = 0.6F;
    private static final float SLOW_DOWN = 0.06F;
    private static final float LEAST_SPEED = 0.15F;
    /** How far past the toe's end a rider is set down, and how hard they are pushed on, in blocks a tick. */
    private static final double SET_DOWN = 0.4;
    private static final double PUSH_ON = 0.2;
    /** How far round a rider may look from straight down the slide, in degrees. */
    private static final float LOOK_ROUND = 105;
    /**
     * Where the seat was at the start, as a block and the offset into it, the ways out and up of the slide's door, the
     * slide (its angle, the height of its toe and the toe's length), how far down the lane the ride started, and the game
     * tick it did.
     */
    private static final EntityDataAccessor<BlockPos> ANCHOR = SynchedEntityData.defineId(SlideRide.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> OFFSET = SynchedEntityData.defineId(SlideRide.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> OUT = SynchedEntityData.defineId(SlideRide.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> UP = SynchedEntityData.defineId(SlideRide.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> ANGLE = SynchedEntityData.defineId(SlideRide.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TOE_Y = SynchedEntityData.defineId(SlideRide.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> TOE = SynchedEntityData.defineId(SlideRide.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FROM = SynchedEntityData.defineId(SlideRide.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Long> START = SynchedEntityData.defineId(SlideRide.class, EntityDataSerializers.LONG);

    /** How far down the lane the ride is after each tick, worked out once. */
    @Nullable
    private float[] course;
    @Nullable
    private EvacuationSlide.Path path;
    /** Set down at the end: the rider may get off. */
    private boolean arrived;
    /**
     * On a client: its own ticks, counted one a tick as it ticks, and, by them, when the ride set off. The client's clock
     * is set back to the server's every second, where it has run ahead; timed by it, the rider would jump back up the
     * lane each time.
     */
    private long ownTicks;
    private long rideStartedAt;

    public SlideRide(EntityType<? extends SlideRide> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    /**
     * Seats the entity on the slide and sets it off: from the seat, a point on the lane's middle line this far down it,
     * the ways out and up of the slide's door, all as they lie in the world.
     */
    public static void carry(ServerLevel level, LivingEntity rider, Vec3 seat, Vec3 out, Vec3 up, EvacuationSlide.Plan plan, float from) {
        SlideRide ride = new SlideRide(AerolargeRegistry.SLIDE_RIDE.get(), level);
        BlockPos anchor = BlockPos.containing(seat);
        ride.entityData.set(ANCHOR, anchor);
        ride.entityData.set(OFFSET, seat.subtract(Vec3.atLowerCornerOf(anchor)).toVector3f());
        ride.entityData.set(OUT, out.toVector3f());
        ride.entityData.set(UP, up.toVector3f());
        ride.entityData.set(ANGLE, plan.angle());
        ride.entityData.set(TOE_Y, plan.toeY());
        ride.entityData.set(TOE, plan.toe());
        ride.entityData.set(FROM, from);
        ride.entityData.set(START, level.getGameTime());
        ride.moveTo(seat.x, seat.y, seat.z, (float) Math.toDegrees(Math.atan2(-out.x, out.z)), 0);
        level.addFreshEntity(ride);
        rider.startRiding(ride, true);
        rider.resetFallDistance();
        level.playSound(null, seat.x, seat.y, seat.z, AerolargeRegistry.SLIDE_WHOOSH.get(), SoundSource.NEUTRAL, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ANCHOR, BlockPos.ZERO);
        builder.define(OFFSET, new Vector3f());
        builder.define(OUT, new Vector3f(0, 0, 1));
        builder.define(UP, new Vector3f(0, 1, 0));
        builder.define(ANGLE, 35F);
        builder.define(TOE_Y, -2F);
        builder.define(TOE, 1F);
        builder.define(FROM, 0F);
        builder.define(START, 0L);
    }

    /** A client hears of the ride after it is made: what it knew of the slide is worked out again from what it heard. */
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        course = null;
        path = null;
        if (key == START && level().isClientSide) {
            rideStartedAt = ownTicks - (level().getGameTime() - entityData.get(START));
        }
    }

    private EvacuationSlide.Path path() {
        if (path == null) {
            path = new EvacuationSlide.Plan(entityData.get(ANGLE), entityData.get(TOE_Y), entityData.get(TOE)).path();
        }
        return path;
    }

    /**
     * How far down the lane the ride is after each tick: setting off a little way, picking up speed down the slope and
     * round onto the ground, slowing along the toe, until it is at the end.
     */
    private float[] course() {
        if (course == null) {
            EvacuationSlide.Path slide = path();
            List<Float> along = new ArrayList<>();
            float s = entityData.get(FROM);
            float speed = SET_OFF;
            along.add(s);
            while (s < slide.rideLength()) {
                s = Math.min(slide.rideLength(), s + speed);
                speed = s < slide.toeAlong() ? Math.min(TOP_SPEED, speed + SPEED_UP) : Math.max(LEAST_SPEED, speed - SLOW_DOWN);
                along.add(s);
            }
            course = new float[along.size()];
            for (int i = 0; i < course.length; i++) {
                course[i] = along.get(i);
            }
        }
        return course;
    }

    /** Where the seat is in the world this far down the lane. */
    private Vec3 seatAt(float s) {
        EvacuationSlide.Path slide = path();
        EvacuationSlide.Point start = slide.at(entityData.get(FROM));
        EvacuationSlide.Point at = slide.at(s);
        return seatAt(at.u() - start.u(), at.y() - start.y());
    }

    /** Where a point this far out and up from the seat at the start is in the world. */
    private Vec3 seatAt(float out, float up) {
        Vector3f offset = entityData.get(OFFSET);
        Vec3 origin = Vec3.atLowerCornerOf(entityData.get(ANCHOR)).add(offset.x, offset.y, offset.z);
        return origin.add(new Vec3(entityData.get(OUT)).scale(out)).add(new Vec3(entityData.get(UP)).scale(up));
    }

    @Override
    public void tick() {
        super.tick();
        float[] along = course();
        if (level().isClientSide) {
            ownTicks++;
        }
        long ticks = Math.max(0, level().isClientSide ? ownTicks - rideStartedAt : level().getGameTime() - entityData.get(START));
        int step = (int) Math.min(ticks, along.length - 1);
        Vec3 seat = seatAt(along[step]);
        setPos(seat.x, seat.y, seat.z);
        if (level().isClientSide) {
            return;
        }
        if (!isVehicle() && ticks > 1) {
            discard();
        } else if (ticks >= along.length - 1) {
            setDown();
        }
    }

    /** At the end: the riders are set down on the ground just past the toe, pushed on a little, and the ride is over. */
    private void setDown() {
        arrived = true;
        Vec3 out = new Vec3(entityData.get(OUT)).multiply(1, 0, 1).normalize();
        for (Entity rider : List.copyOf(getPassengers())) {
            rider.stopRiding();
            rider.resetFallDistance();
            rider.setDeltaMovement(out.scale(PUSH_ON));
            rider.hurtMarked = true;
        }
        discard();
    }

    /** Just past the toe's end, on the ground. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        EvacuationSlide.Path slide = path();
        EvacuationSlide.Point start = slide.at(entityData.get(FROM));
        return seatAt(slide.rideEnd() + (float) SET_DOWN - start.u(), slide.groundY() - start.y());
    }

    /** Seated right on the lane. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return Vec3.ZERO;
    }

    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        super.positionRider(passenger, callback);
        passenger.resetFallDistance();
        faceDown(passenger);
    }

    @Override
    public void onPassengerTurned(Entity passenger) {
        faceDown(passenger);
    }

    /** Feet first down the slide: the rider's body faces down it, and they may look round only so far. */
    private void faceDown(Entity passenger) {
        passenger.setYBodyRot(getYRot());
        float turned = Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float kept = Mth.clamp(turned, -LOOK_ROUND, LOOK_ROUND);
        passenger.yRotO += kept - turned;
        passenger.setYRot(passenger.getYRot() + kept - turned);
        passenger.setYHeadRot(passenger.getYRot());
    }

    /**
     * Sneaking does not get anyone off a slide halfway down: they would only fall. Getting off any other way (a
     * teleport, dying) still works, and so does the set-down at the end.
     */
    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityBeingMounted() instanceof SlideRide ride && !ride.arrived && ride.isAlive()
            && !event.getLevel().isClientSide && event.getEntityMounting().isAlive() && event.getEntityMounting().isShiftKeyDown()) {
            event.setCanceled(true);
        }
    }

    /** The ride's clock and course are its own: the server's moves would only make it stutter. */
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
    protected boolean canAddPassenger(Entity passenger) {
        return !isVehicle();
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(ANCHOR, NbtUtils.readBlockPos(tag, "Anchor").orElse(blockPosition()));
        entityData.set(OFFSET, vector(tag, "Offset"));
        entityData.set(OUT, vector(tag, "Out"));
        entityData.set(UP, vector(tag, "Up"));
        entityData.set(ANGLE, tag.getFloat("Angle"));
        entityData.set(TOE_Y, tag.getFloat("ToeY"));
        entityData.set(TOE, tag.getFloat("Toe"));
        entityData.set(FROM, tag.getFloat("From"));
        entityData.set(START, tag.getLong("Start"));
        course = null;
        path = null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.put("Anchor", NbtUtils.writeBlockPos(entityData.get(ANCHOR)));
        tag.put("Offset", vector(entityData.get(OFFSET)));
        tag.put("Out", vector(entityData.get(OUT)));
        tag.put("Up", vector(entityData.get(UP)));
        tag.putFloat("Angle", entityData.get(ANGLE));
        tag.putFloat("ToeY", entityData.get(TOE_Y));
        tag.putFloat("Toe", entityData.get(TOE));
        tag.putFloat("From", entityData.get(FROM));
        tag.putLong("Start", entityData.get(START));
    }

    private static CompoundTag vector(Vector3f vector) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("X", vector.x);
        tag.putFloat("Y", vector.y);
        tag.putFloat("Z", vector.z);
        return tag;
    }

    private static Vector3f vector(CompoundTag tag, String key) {
        CompoundTag vector = tag.getCompound(key);
        return new Vector3f(vector.getFloat("X"), vector.getFloat("Y"), vector.getFloat("Z"));
    }
}
