package dev.aerolarge.entity;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * The rocket's tardigrade: a water bear the size of an elephant, kept in a glass tank in her lab (see
 * {@link dev.aerolarge.block.HabitatHatchBlockEntity}, its keeper, in the tank's feeding hatch). It lives among her
 * blocks, in her plot (its type is in {@code #sable:retain_in_sub_level}), so Sable carries it as she flies, and goes
 * with her when she goes.
 *
 * <p>Its keeper hands it, every tick, the line it paces along the tank, the point on it where it can turn round (if
 * the tank is deep enough anywhere) and where the hatch is. It lumbers slowly along the line, rests, turns round there
 * and looks at whoever is near, and nothing moves it: it cannot be hurt, pushed or led. Fed moss through the hatch, or
 * straight from the hand, it comes to the hatch and munches it; touched with an empty hand, it wriggles and purrs. High
 * over the top of the world, where she flies through space, it curls up into a tun, the dry barrel real tardigrades
 * survive space in, and uncurls once she is back down (its keeper says when).
 *
 * <p>Left without its keeper a while, as when its tank is broken, it goes; one never kept, as one summoned, stays.
 */
public class Tardigrade extends PathfinderMob {
    /** How far the creature turns its head from its body at most, in degrees, across and up or down. */
    private static final int MAX_HEAD_YAW = 50;
    private static final int MAX_HEAD_PITCH = 20;
    /**
     * How fast it lumbers forward and shuffles back, in blocks a tick, and how many ticks it takes to come up to it.
     */
    private static final double WALK_SPEED = 0.035;
    private static final double BACK_SPEED = 0.02;
    private static final int EASE = 12;
    /** How close to where it is going it has come once there, in blocks. */
    private static final double THERE = 0.05;
    /** How far it turns each tick it turns round, in degrees: half a turn in four and a half seconds. */
    private static final float TURN_RATE = 2.0F;
    /** How long it rests between walks, from and to, in ticks, and how often it turns round where it can after one. */
    private static final int REST_FROM = 80;
    private static final int REST_TO = 260;
    private static final float TURN_CHANCE = 0.35F;
    /** How long it stays at the hatch after coming to it, in ticks. */
    private static final int AT_HATCH = 100;
    /** How long it munches, and how often it takes a bite, in ticks; how long it wriggles when touched. */
    public static final int MUNCH_TICKS = 60;
    private static final int BITE_EVERY = 15;
    /**
     * How often bits of moss fall from its mouth as it munches, in ticks, how many, and how fast they fly off, in
     * blocks a tick.
     */
    private static final int CRUMBS_EVERY = 4;
    private static final int CRUMBS = 3;
    private static final double CRUMB_SPREAD = 0.12;
    public static final int WIGGLE_TICKS = 30;
    /** How long it takes to curl up into a tun, or out of one, in ticks. */
    public static final int CURL_TICKS = 40;
    /** How long it may go without its keeper before it goes, in ticks. */
    private static final int UNKEPT = 200;
    /** How often it purrs, at least, in ticks, and how loud. */
    private static final int PURR_EVERY = 400;
    private static final float VOLUME = 0.7F;
    /**
     * How far from its middle its mouth is, ahead and up, in blocks, and how near the hatch it has come to be touched
     * there.
     */
    public static final double MOUTH_AHEAD = 2.9;
    public static final double MOUTH_UP = 1.3;
    public static final double AT_REACH = 1.6;
    /** How far it looks for whoever is near to look at, in blocks. */
    private static final float LOOK_RANGE = 10.0F;
    /** What the client is told as it munches and as it wriggles. */
    private static final byte MUNCHING = 80;
    private static final byte WIGGLING = 81;

    private static final EntityDataAccessor<Boolean> CURLED = SynchedEntityData.defineId(Tardigrade.class, EntityDataSerializers.BOOLEAN);

    /**
     * Its line's ends, the point where it turns round and the hatch's inside, among her blocks; null before it is kept.
     */
    @Nullable
    private Vec3 from;
    @Nullable
    private Vec3 to;
    @Nullable
    private Vec3 turnAt;
    @Nullable
    private Vec3 hatch;
    /** Whether it has ever had a keeper, and the game tick it last heard from it. */
    private boolean kept;
    private long keptAt;
    /** What it was called to the hatch for, if it was: true to be fed, false to be touched. */
    @Nullable
    private Boolean called;
    /**
     * How many ticks of munching it has left; how far it has still to turn round, in degrees; how long it has walked.
     */
    private int munching;
    private float turning;
    private int walked;
    /**
     * On the client: how far it has curled up, now and a tick ago, from 0 to 1, and how long it still munches and
     * wriggles.
     */
    private float curl;
    private float curlBefore;
    private int munchShown;
    private int wiggleShown;

    public Tardigrade(EntityType<? extends Tardigrade> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, WALK_SPEED).add(Attributes.KNOCKBACK_RESISTANCE, 1)
            .add(Attributes.FOLLOW_RANGE, LOOK_RANGE);
    }

    /** Gives the creature its attributes as the game asks for every living thing's. */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(AerolargeRegistry.TARDIGRADE.get(), createAttributes().build());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CURLED, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new TankLife(this));
        goalSelector.addGoal(2, new WatchVisitors(this));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    /** Its body stays along its line, turned only as it turns round; its head turns from it no further than it can. */
    @Override
    protected BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
                setYBodyRot(getYRot());
                yHeadRot = Mth.rotateIfNecessary(yHeadRot, yBodyRot, MAX_HEAD_YAW);
            }
        };
    }

    @Override
    public int getMaxHeadYRot() {
        return MAX_HEAD_YAW;
    }

    @Override
    public int getMaxHeadXRot() {
        return MAX_HEAD_PITCH;
    }

    /**
     * Kept by its keeper this tick, among her blocks: its line's ends, the point where it can turn round (null where it
     * cannot, when it faces the line's end at the hatch), and the hatch's inside.
     */
    public void keep(long tick, Vec3 lineFrom, Vec3 lineTo, @Nullable Vec3 turnPoint, Vec3 hatchInside) {
        from = lineFrom;
        to = lineTo;
        turnAt = turnPoint;
        hatch = hatchInside;
        kept = true;
        keptAt = tick;
        restrictTo(BlockPos.containing(lineFrom.add(lineTo).scale(0.5)), (int) Math.ceil(lineFrom.distanceTo(lineTo) / 2 + MOUTH_AHEAD));
        if (turnAt == null && turning == 0) {
            setYRot(lineYaw(true));
        }
    }

    /** Whether it is at the hatch with its mouth: near enough to be touched or fed there. */
    public boolean atHatch() {
        return hatch != null && mouth().distanceTo(hatch) < AT_REACH;
    }

    /**
     * Called to the hatch, to be fed or to be touched: it comes, and once there munches or wriggles; touched where it
     * is, at the hatch already, it wriggles at once. Whether it heeds it: not curled up in its tun.
     */
    public boolean call(boolean feed) {
        if (isCurled()) {
            return false;
        }
        if (!feed && atHatch()) {
            wiggle();
            return true;
        }
        called = feed;
        return true;
    }

    /** Whether it is curled up in its tun, on either side. */
    public boolean isCurled() {
        return entityData.get(CURLED);
    }

    /** Curls it up into its tun, with a bloop, or out of it, with a purr. */
    public void setCurled(boolean curled) {
        if (curled == isCurled()) {
            return;
        }
        entityData.set(CURLED, curled);
        SoundEvent sound = curled ? AerolargeRegistry.TARDIGRADE_BLOOP.get() : AerolargeRegistry.TARDIGRADE_MRRP.get();
        playSound(sound, VOLUME, curled ? 1.0F : 0.85F);
        munching = 0;
        called = null;
    }

    /** How far it has curled up into its tun this frame, from 0 to 1, on the client. */
    public float curl(float partialTick) {
        return Mth.lerp(partialTick, curlBefore, curl);
    }

    /** How much of its munching it has still to do this frame, from 1 to 0, on the client. */
    public float munchShown(float partialTick) {
        return Math.max(0, munchShown - partialTick) / MUNCH_TICKS;
    }

    /** How much of its wriggle it has still to do this frame, from 1 to 0, on the client. */
    public float wiggleShown(float partialTick) {
        return Math.max(0, wiggleShown - partialTick) / WIGGLE_TICKS;
    }

    /** Where its mouth is, in front of its head, among her blocks. */
    public Vec3 mouth() {
        return position().add(Vec3.directionFromRotation(0, yBodyRot).scale(MOUTH_AHEAD)).add(0, MOUTH_UP, 0);
    }

    /**
     * A point in the world as the creature sees it, in her frame while it lives among her blocks (where its own place
     * is), the point itself otherwise.
     */
    private Vec3 seen(Vec3 point) {
        return SableFlight.toPlot(level(), blockPosition(), point, false);
    }

    /** The nearest player near enough to look at, as it sees them; null for none. */
    @Nullable
    private Player nearestVisitor() {
        Player nearest = null;
        double nearestDistance = LOOK_RANGE;
        for (Player player : level().players()) {
            double distance = seen(player.getEyePosition()).distanceTo(getEyePosition());
            if (!player.isSpectator() && distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** Whether something held is what it eats: moss. */
    public static boolean eats(ItemStack stack) {
        return stack.is(Items.MOSS_BLOCK) || stack.is(Items.MOSS_CARPET);
    }

    /**
     * Fed moss straight from the hand, as through the open hatch, or touched with an empty one: it munches the moss, or
     * wriggles, where it is. In its tun it heeds neither.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean empty = held.isEmpty() && hand == InteractionHand.MAIN_HAND;
        if (isCurled() || !eats(held) && !empty) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            if (empty) {
                wiggle();
            } else {
                munch();
                held.consume(1, player);
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Starts munching, on the server: a bite at once and every so often, the client shown it. */
    private void munch() {
        munching = MUNCH_TICKS;
        level().broadcastEntityEvent(this, MUNCHING);
    }

    /** Wriggles with a purr, on the server, the client shown it. */
    private void wiggle() {
        level().broadcastEntityEvent(this, WIGGLING);
        playSound(AerolargeRegistry.TARDIGRADE_MRRP.get(), VOLUME, 1.2F);
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == MUNCHING) {
            munchShown = MUNCH_TICKS;
        } else if (id == WIGGLING) {
            wiggleShown = WIGGLE_TICKS;
        } else {
            super.handleEntityEvent(id);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            animate();
            return;
        }
        if (kept && level().getGameTime() - keptAt > UNKEPT) {
            discard();
            return;
        }
        if (munching > 0) {
            if (munching % BITE_EVERY == 0) {
                Vec3 mouth = mouth();
                level().playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.SNIFFER_EAT, SoundSource.NEUTRAL, VOLUME, 0.75F + random.nextFloat() * 0.15F);
            }
            munching--;
        }
    }

    /** The client's side of it: its curling, munching and wriggling played out, bits of moss falling as it munches. */
    private void animate() {
        curlBefore = curl;
        curl = Mth.approach(curl, isCurled() ? 1 : 0, 1.0F / CURL_TICKS);
        wiggleShown = Math.max(0, wiggleShown - 1);
        if (munchShown > 0) {
            munchShown--;
            if (munchShown % CRUMBS_EVERY == 0) {
                crumbs();
            }
        }
    }

    /** A few bits of moss falling from its mouth as it munches, on the client. */
    private void crumbs() {
        Vec3 mouth = mouth();
        ItemParticleOption moss = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.MOSS_BLOCK));
        for (int bit = 0; bit < CRUMBS; bit++) {
            level().addParticle(moss, mouth.x, mouth.y, mouth.z, (random.nextDouble() - 0.5) * CRUMB_SPREAD, CRUMB_SPREAD / 2 + random.nextDouble() * CRUMB_SPREAD,
                (random.nextDouble() - 0.5) * CRUMB_SPREAD);
        }
    }

    /** The way along its line it faces when facing the hatch's end, or the other end, in degrees. */
    private float lineYaw(boolean towardHatch) {
        if (from == null || to == null) {
            return getYRot();
        }
        Vec3 along = towardHatch ? to.subtract(from) : from.subtract(to);
        return (float) (Mth.atan2(along.z, along.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** Whether it faces the end of its line at the hatch. */
    private boolean facesHatch() {
        return Math.abs(Mth.wrapDegrees(getYRot() - lineYaw(true))) < 90;
    }

    /** Stands still where it is. */
    private void halt() {
        walked = 0;
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }

    /** One tick of walking to a point without turning, forward or back; whether it is there. */
    private boolean walkTo(Vec3 point) {
        Vec3 off = point.subtract(position()).multiply(1, 0, 1);
        double distance = off.length();
        if (distance < THERE) {
            halt();
            return true;
        }
        boolean ahead = off.dot(Vec3.directionFromRotation(0, getYRot())) >= 0;
        walked++;
        double speed = (ahead ? WALK_SPEED : BACK_SPEED) * Math.min(1, walked / (double) EASE);
        Vec3 step = off.scale(Math.min(speed, distance) / distance);
        setDeltaMovement(step.x, getDeltaMovement().y, step.z);
        return false;
    }

    /** One tick of turning round where it stands; whether it has turned all the way. */
    private boolean turnRound() {
        if (turning == 0) {
            turning = 180;
        }
        float step = Math.min(TURN_RATE, turning);
        setYRot(getYRot() + step);
        turning -= step;
        if (turning > 0) {
            return false;
        }
        setYRot(lineYaw(facesHatch()));
        return true;
    }

    /** At the hatch it was called to: it munches what it was fed or wriggles at being touched. */
    private void arrive(boolean fed) {
        if (fed) {
            munch();
        } else {
            wiggle();
        }
    }

    /** It cannot be hurt, but by what passes every protection: an operator's kill or falling out of the world. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, amount);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity entity) {
    }

    @Override
    protected void doPush(Entity entity) {
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public boolean requiresCustomPersistence() {
        return true;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return isCurled() ? null : AerolargeRegistry.TARDIGRADE_MRRP.get();
    }

    @Override
    public int getAmbientSoundInterval() {
        return PURR_EVERY;
    }

    @Override
    protected float getSoundVolume() {
        return VOLUME;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Nullable
    @Override
    protected SoundEvent getDeathSound() {
        return null;
    }

    /** Its body reaches well past the box it stands in, its head and its tail out ahead and behind. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(MOUTH_AHEAD, 0, MOUTH_AHEAD);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Curled", isCurled());
        tag.putBoolean("Kept", kept);
    }

    /** Loaded again, it waits for its keeper as long as it would have from now. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CURLED, tag.getBoolean("Curled"));
        kept = tag.getBoolean("Kept");
        keptAt = level().getGameTime();
    }

    /**
     * Looking a while at whoever comes near, now and then, as vanilla's creatures look at players; but seeing them as
     * it sees the world, in her frame while it lives among her blocks, so aboard her in flight it still finds them.
     */
    private static final class WatchVisitors extends Goal {
        /** How likely it is to look for someone each tick, and how long it looks, from and to, in ticks. */
        private static final float CHANCE = 0.02F;
        private static final int LOOK_FROM = 40;
        private static final int LOOK_TO = 80;

        private final Tardigrade creature;
        @Nullable
        private Player visitor;
        private int looking;

        WatchVisitors(Tardigrade creature) {
            this.creature = creature;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (creature.isCurled() || creature.random.nextFloat() >= CHANCE) {
                return false;
            }
            visitor = creature.nearestVisitor();
            return visitor != null;
        }

        @Override
        public boolean canContinueToUse() {
            return visitor != null && visitor.isAlive() && !creature.isCurled() && looking > 0
                && creature.seen(visitor.getEyePosition()).distanceTo(creature.getEyePosition()) < LOOK_RANGE;
        }

        @Override
        public void start() {
            looking = adjustedTickDelay(LOOK_FROM + creature.random.nextInt(LOOK_TO - LOOK_FROM));
        }

        @Override
        public void stop() {
            visitor = null;
        }

        @Override
        public void tick() {
            looking--;
            if (visitor != null) {
                creature.getLookControl().setLookAt(creature.seen(visitor.getEyePosition()));
            }
        }
    }

    /**
     * Its life in the tank: resting, lumbering to somewhere along its line and back, turning round where it can, and
     * coming to the hatch when called there, turning round first if it faces away. A walk is a few steps, each a point
     * to walk to or a turn; nothing at all while it is curled up.
     */
    private static final class TankLife extends Goal {
        /** A step of a walk: to a point, or turning round where it stands. */
        private record Step(@Nullable Vec3 to) {
        }

        /** How long a step may take before it is given up, in ticks, as when something stands in the way. */
        private static final int STEP_LONGEST = 600;

        private final Tardigrade creature;
        private final Deque<Step> steps = new ArrayDeque<>();
        private int resting;
        private int stepTicks;
        @Nullable
        private Boolean arriving;

        TankLife(Tardigrade creature) {
            this.creature = creature;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return creature.from != null && !creature.isCurled();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        /** Stopped, as when it curls up: standing still, and squared to its line if it was turning round. */
        @Override
        public void stop() {
            steps.clear();
            stepTicks = 0;
            arriving = null;
            if (creature.turning > 0) {
                creature.turning = 0;
                creature.setYRot(creature.lineYaw(creature.facesHatch()));
            }
            creature.halt();
        }

        @Override
        public void tick() {
            if (creature.called != null && creature.turning == 0) {
                comeToHatch(creature.called);
                creature.called = null;
            }
            if (creature.munching > 0) {
                creature.halt();
                lookAtHatch();
                return;
            }
            if (steps.isEmpty()) {
                rest();
                return;
            }
            Step step = steps.peek();
            boolean done = step.to() != null ? creature.walkTo(step.to()) : creature.turnRound();
            if (done || ++stepTicks > STEP_LONGEST) {
                steps.poll();
                stepTicks = 0;
                arrived();
            }
        }

        /** Resting between walks, and once rested, off on another. */
        private void rest() {
            creature.halt();
            if (resting > 0) {
                resting--;
            } else {
                wander();
            }
        }

        /** Once the last step of a walk is done: at the hatch if it was called there, then a rest. */
        private void arrived() {
            if (!steps.isEmpty()) {
                return;
            }
            if (arriving != null) {
                creature.arrive(arriving);
                arriving = null;
                resting = AT_HATCH;
                return;
            }
            resting = REST_FROM + creature.random.nextInt(REST_TO - REST_FROM);
        }

        /** While munching or resting by the hatch, it looks at the hatch. */
        private void lookAtHatch() {
            if (creature.munching > 0 && creature.hatch != null) {
                creature.getLookControl().setLookAt(creature.hatch);
            }
        }

        /** A walk to the hatch, turning round on the way if it faces away and can, to be fed or touched there. */
        private void comeToHatch(boolean feed) {
            steps.clear();
            stepTicks = 0;
            creature.turning = 0;
            if (!creature.facesHatch() && creature.turnAt != null) {
                steps.add(new Step(creature.turnAt));
                steps.add(new Step(null));
            }
            steps.add(new Step(creature.to));
            arriving = feed;
        }

        /** A walk of its own: now and then round about where it can turn, else to somewhere along its line. */
        private void wander() {
            if (creature.turnAt != null && creature.random.nextFloat() < TURN_CHANCE) {
                steps.add(new Step(creature.turnAt));
                steps.add(new Step(null));
                return;
            }
            steps.add(new Step(creature.from.lerp(creature.to, creature.random.nextDouble())));
        }
    }
}
