package dev.skyliners.entity;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.cabin.TurbulencePayload;
import dev.skyliners.compat.SableFlight;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The rocket's tardigrade: a water bear the size of an elephant, kept in a glass tank in her lab (see
 * {@link dev.skyliners.block.HabitatHatchBlockEntity}, its keeper, in the tank's feeding hatch). It lives among her
 * blocks, in her plot (its type is in {@code #sable:retain_in_sub_level}), so Sable carries it as she flies, and goes
 * with her when she goes.
 *
 * <p>Its keeper hands it, every tick, the line it paces along the tank, the point on it where it can turn round (if
 * the tank is deep enough anywhere) and where the hatch is. It lumbers slowly along the line, rests, turns round there
 * and looks at whoever is near, and nothing moves it: it cannot be hurt, pushed or led. Fed moss through the hatch, or
 * straight from the hand, it comes to the hatch and munches it; touched with an empty hand, it wriggles and purrs. At
 * night it settles down to sleep with a drowsy purr, its eyes shut and its breath slow, snoring softly, a "z" rising
 * from it now and then; at daybreak it wakes with a stretch and a purr. Fed or touched in the night it wakes to come to the
 * hatch, and drifts off again a while after. High over the top of the world, where she flies through space, it curls up
 * into a tun, the dry barrel real tardigrades survive space in, and uncurls once she is back down (its keeper says
 * when); curled up it neither sleeps nor wakes. Whatever is thrown into its tank, it goes for along its line and
 * sucks in with a slurp, the thing flying to its mouth, chews it with bits of it falling, and burps; it burps after
 * every meal. And now and then, resting awake, it jumps away through the spore drive, as its cousin on the starship
 * does: glowing as spores swirl in round it with a rising hum, gone in a burst of them with a whump, and back where it
 * was a few seconds later in another, whooshing in. A babycino sets the drive off at once. An ice cream does more: it
 * goes down in a gulp, the creature swells up and lets out a massive burp that shakes the ship for everyone on her, and
 * then it stretches out long and thin and is gone to hyperspace in a streak of light. Nothing hurts it, but a player
 * attacking it startles it into a jump straight away, and then it stays away a minute or two.
 *
 * <p>The freighter keeps a small cousin of it, a quarter its size and mint green, in a tank in her lounge: the same
 * creature made small (see {@link #setSize}), which does all it does, its voice higher and its burp a hiccup that
 * hardly rocks her.
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
    /**
     * How much higher its voice is and how much softer the smaller it is, each as a power of its size: a quarter the
     * size, half again as high and half as loud.
     */
    private static final double VOICE_RISE = 0.3;
    private static final double VOICE_FALL = 0.5;
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
     * there, each at its full size (see {@link #setSize}).
     */
    private static final double MOUTH_AHEAD = 2.9;
    private static final double MOUTH_UP = 1.3;
    private static final double AT_REACH = 1.6;
    /** How far it looks for whoever is near to look at, in blocks. */
    private static final float LOOK_RANGE = 10.0F;
    /** What the client is told as it munches and as it wriggles. */
    private static final byte MUNCHING = 80;
    private static final byte WIGGLING = 81;
    /** When night falls in its tank and when day breaks, in the ticks of a day: as the sky darkens and lightens. */
    private static final int NIGHT_FROM = 13000;
    private static final int NIGHT_TO = 23000;
    private static final int DAY = 24000;
    /** How long it stays awake after being fed or touched in the night, in ticks, before it drifts off again. */
    private static final int ROUSED_TICKS = 600;
    /** How long it takes to settle down asleep, or to get up again, in ticks. */
    public static final int SETTLE_TICKS = 40;
    /** How often it snores asleep, in ticks, and how loud; the pitch of its drowsy purr settling down. */
    private static final int SNORE_EVERY = 90;
    private static final float SNORE_VOLUME = 0.35F;
    private static final float DROWSY_PITCH = 0.6F;
    /** How often a "z" rises from it asleep, in ticks, how high over its middle and how fast, in blocks a tick. */
    private static final int Z_EVERY = 35;
    private static final double Z_UP = 2.6;
    private static final double Z_RISE = 0.025;
    /**
     * How far it looks for what is thrown into its tank, in blocks: out from where it stands as far as the tank of the
     * biggest rocket reaches from its line, and up as high as a tank is; and how often, in ticks. How near its mouth
     * a thing is when it starts sucking it in, in blocks; how fast it flies in, in blocks a tick; how near its mouth it
     * is swallowed; and how long it is sucked at most before it is swallowed anyway, in ticks, as when something holds
     * it.
     */
    private static final double MEAL_RANGE = 24;
    private static final double MEAL_UP = 12;
    private static final int MEAL_LOOK_EVERY = 10;
    private static final double SUCK_REACH = 3.5;
    private static final double SUCK_SPEED = 0.2;
    private static final double SWALLOWED = 0.5;
    private static final int SUCK_LONGEST = 60;
    /** How far over a thing lying in the tank its look at it ends, in blocks, past the floor's carpet of moss. */
    private static final double LOOKED_OVER = 0.25;
    /** How long after it has done chewing it burps, in ticks. */
    private static final int BURP_AFTER = 10;
    /**
     * Its spore drive: how likely it is to jump each tick it rests awake (about once in four minutes of it), how long
     * it charges up before it goes, how long it stays away, from and to, and how long before it comes back its return
     * is heard, in ticks.
     */
    private static final float JUMP_CHANCE = 1 / 4800F;
    private static final int CHARGE_TICKS = 22;
    private static final int AWAY_FROM = 60;
    private static final int AWAY_TO = 120;
    private static final int RETURN_HEARD = 10;
    /** How long it stays away when a player has attacked it, at least and at most, in ticks; and its yelp's pitch. */
    private static final int FLED_FROM = 1200;
    private static final int FLED_TO = 2400;
    private static final float YELP_PITCH = 1.7F;
    /**
     * How many spores swirl in round it each tick it charges, from how far off, in blocks, how much of the way in they
     * fly each tick, and how fast they swirl round it, in blocks a tick; how many burst from it as it goes and as it
     * comes back, how fast, in blocks a tick, and how far out from its middle they burst, in ticks of their flight; and
     * how much of their spread lies under its middle, the rest over it, round its back.
     */
    private static final int SWIRL = 3;
    private static final double SWIRL_FROM = 3.5;
    private static final double SWIRL_IN = 0.08;
    private static final double SWIRL_ROUND = 0.06;
    private static final int BURST = 40;
    private static final double BURST_SPEED = 0.25;
    private static final double BURST_OUT = 8;
    private static final double UNDER_MIDDLE = 0.3;
    /** How many puffs leave its mouth as it burps, and how fast. */
    private static final int BURP_PUFFS = 6;
    private static final double BURP_SPEED = 0.06;
    /** What the client is told as it burps, and as it jumps away or comes back. */
    private static final byte BURPED = 82;
    private static final byte JUMPED = 83;
    /**
     * An ice cream: how long it swells after the gulp before its massive burp, how long after the burp it starts to
     * stretch out, and how long it takes to stretch out and be gone, as long as the drive's hum takes to its whump, all
     * in ticks.
     */
    private static final int SWELL_TICKS = 30;
    private static final int STRETCH_AFTER = 30;
    private static final int STRETCH_TICKS = CHARGE_TICKS;
    /**
     * Its massive burp: how far off it is heard, as a sound's volume, and the pitches of its two voices; the rumble of
     * the ship shaking under it, its volume and pitch.
     */
    private static final float BIG_BURP_VOLUME = 4.0F;
    private static final float BIG_BURP_PITCH = 0.5F;
    private static final float BIG_BURP_SECOND_PITCH = 0.62F;
    private static final float RUMBLE_VOLUME = 1.5F;
    private static final float RUMBLE_PITCH = 0.5F;
    /** How many puffs leave its mouth as it lets it out, how fast, in blocks a tick, and how many bits of ice cream. */
    private static final int BIG_BURP_PUFFS = 70;
    private static final double BIG_BURP_SPEED = 0.3;
    private static final int BIG_BURP_BITS = 14;
    /**
     * The shake it sends through the ship: how far from it it is felt, in blocks, about the whole of a default rocket;
     * how hard, as rough air's strength (1 being the roughest of clear skies); and how long, in ticks.
     */
    private static final double SHAKE_REACH = 128;
    private static final float SHAKE_STRENGTH = 2.0F;
    private static final int SHAKE_TICKS = 40;
    /** How many streaks of light it leaves going to hyperspace, how fast they fly ahead, in blocks a tick. */
    private static final int STREAKS = 60;
    private static final double STREAK_SPEED = 1.5;
    /** What the client is told as it gulps an ice cream, lets out its massive burp and goes to hyperspace. */
    private static final byte GULPED = 84;
    private static final byte BIG_BURPED = 85;
    private static final byte STREAKED = 86;

    private static final EntityDataAccessor<Boolean> CURLED = SynchedEntityData.defineId(Tardigrade.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> ASLEEP = SynchedEntityData.defineId(Tardigrade.class, EntityDataSerializers.BOOLEAN);
    /** What it last ate, for the bits falling from its mouth as it chews; and where it is in a jump. */
    private static final EntityDataAccessor<ItemStack> EATEN = SynchedEntityData.defineId(Tardigrade.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<Integer> JUMP = SynchedEntityData.defineId(Tardigrade.class, EntityDataSerializers.INT);

    /** Where it is in a jump: here, charging up to go, away, or stretching out to go to hyperspace. */
    public enum Jump {
        HERE, CHARGING, AWAY, STRETCHING
    }

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
    /**
     * What it was called to the hatch for, if it was, and what it is on its way there for, once it comes: true to be
     * fed, false to be touched. Put off on the way (by a meal, a jump or curling up), the call is made again.
     */
    @Nullable
    private Boolean called;
    @Nullable
    private Boolean coming;
    /** What it was called to the hatch to be fed, which it gets once it is there. */
    private ItemStack calledWith = new ItemStack(Items.MOSS_CARPET);
    /** Whether it is to burp when it is back from this jump, having jumped straight after a babycino. */
    private boolean burpOnReturn;
    /** Whether it is charging up to flee a player who attacked it, to stay away the longer. */
    private boolean fleeing;
    /**
     * How many ticks of munching it has left; how far it has still to turn round, in degrees; how long it has walked.
     */
    private int munching;
    private float turning;
    private int walked;
    /** How long it stays awake yet, roused in the night, in ticks. */
    private int roused;
    /**
     * What it has found thrown into its tank and goes for; how long it has sucked it in, in ticks; whether it stands
     * where it goes for it to suck it in; and how long until it burps after a meal.
     */
    @Nullable
    private ItemEntity meal;
    private int sucked;
    private boolean atMeal;
    private int burpIn;
    /** How long is left of its charging up or of its time away, in ticks. */
    private int jumpTicks;
    /** After an ice cream: how long until its massive burp, and from then until it stretches out, in ticks. */
    private int bigBurpIn;
    private int stretchIn;
    /**
     * On the client: how far it has curled up, now and a tick ago, from 0 to 1, and how long it still munches and
     * wriggles.
     */
    private float curl;
    private float curlBefore;
    private int munchShown;
    private int wiggleShown;
    /** On the client: how far it has settled down asleep, now and a tick ago, from 0 to 1. */
    private float sleep;
    private float sleepBefore;
    /** On the client: how far it has charged up to jump, now and a tick ago, from 0 to 1. */
    private float charge;
    private float chargeBefore;
    /** On the client: how far it has stretched out for hyperspace, now and a tick ago, from 0 to 1. */
    private float stretch;
    private float stretchBefore;
    /** On the client: how long it swells yet before its massive burp, in ticks. */
    private int swellShown;

    public Tardigrade(EntityType<? extends Tardigrade> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 40).add(Attributes.MOVEMENT_SPEED, WALK_SPEED).add(Attributes.KNOCKBACK_RESISTANCE, 1)
            .add(Attributes.FOLLOW_RANGE, LOOK_RANGE);
    }

    /**
     * Makes it so many times its full size, every part of it with it: its body and the box it stands in, its mouth and
     * its reach, its stride and the clouds it lets out, its voice higher and softer. The game's scale attribute holds
     * it, so it is saved and told to clients with the rest of its attributes; 1, the rocket's elephant, unless set.
     */
    public void setSize(double size) {
        AttributeInstance scale = getAttribute(Attributes.SCALE);
        if (scale != null) {
            scale.setBaseValue(size);
        }
    }

    /** Its voice, higher and softer the smaller it is (see {@link #setSize}). */
    @Override
    public void playSound(SoundEvent sound, float volume, float pitch) {
        super.playSound(sound, softer(volume), higher(pitch));
    }

    /** How loud a sound of its is, softer the smaller it is. */
    private float softer(float volume) {
        return (float) (volume * Math.pow(getScale(), VOICE_FALL));
    }

    /** How high a sound of its is, higher the smaller it is. */
    private float higher(float pitch) {
        return (float) (pitch * Math.pow(getScale(), -VOICE_RISE));
    }

    /** Gives the creature its attributes as the game asks for every living thing's. */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SkylinersRegistry.TARDIGRADE.get(), createAttributes().build());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CURLED, false);
        builder.define(ASLEEP, false);
        builder.define(EATEN, ItemStack.EMPTY);
        builder.define(JUMP, Jump.HERE.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new TankLife(this));
        goalSelector.addGoal(2, new WatchVisitors(this, LOOK_RANGE, () -> !isCurled() && !isAsleep() && jump() == Jump.HERE));
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
        restrictTo(BlockPos.containing(lineFrom.add(lineTo).scale(0.5)), (int) Math.ceil(lineFrom.distanceTo(lineTo) / 2 + mouthAhead()));
        if (turnAt == null && turning == 0) {
            setYRot(lineYaw(true));
        }
    }

    /** Whether it is at the hatch with its mouth: near enough to be touched or fed there. */
    public boolean atHatch() {
        return hatch != null && mouth().distanceTo(hatch) < AT_REACH * getScale();
    }

    /**
     * Called to the hatch, to be fed or to be touched: it comes, and once there munches or wriggles; touched where it
     * is, at the hatch already, it wriggles at once, or wakes, asleep. Whether it heeds it: not curled up in its tun,
     * nor away through the spore drive.
     */
    public boolean call(boolean feed) {
        if (isCurled() || jump() != Jump.HERE) {
            return false;
        }
        boolean woke = rouse();
        if (!feed && atHatch()) {
            if (!woke) {
                wiggle();
            }
            return true;
        }
        called = feed;
        return true;
    }

    /** Called to the hatch to be fed this, as {@link #call} calls it; whether it heeds it. */
    public boolean feed(ItemStack food) {
        boolean heeded = call(true);
        if (heeded) {
            calledWith = food.copyWithCount(1);
        }
        return heeded;
    }

    /** Where it is in a jump through the spore drive, on either side. */
    public Jump jump() {
        return Jump.values()[Math.floorMod(entityData.get(JUMP), Jump.values().length)];
    }

    private void setJump(Jump jump) {
        entityData.set(JUMP, jump.ordinal());
    }

    /** How far it has charged up to jump this frame, from 0 to 1, on the client. */
    public float charge(float partialTick) {
        return Mth.lerp(partialTick, chargeBefore, charge);
    }

    /** How far it has stretched out for hyperspace this frame, from 0 to 1, on the client. */
    public float stretch(float partialTick) {
        return Mth.lerp(partialTick, stretchBefore, stretch);
    }

    /** How far it has swollen toward its massive burp this frame, from 0 to 1, on the client. */
    public float swell(float partialTick) {
        return swellShown == 0 ? 0 : 1 - Math.max(0, swellShown - partialTick) / SWELL_TICKS;
    }

    /** Whether it is asleep, on either side. */
    public boolean isAsleep() {
        return entityData.get(ASLEEP);
    }

    /** How far it has settled down asleep this frame, from 0 to 1, on the client. */
    public float sleep(float partialTick) {
        return Mth.lerp(partialTick, sleepBefore, sleep);
    }

    /** Woken, if asleep, to stay awake a while: fed or touched. Whether it woke. */
    private boolean rouse() {
        roused = ROUSED_TICKS;
        if (!isAsleep()) {
            return false;
        }
        wake();
        return true;
    }

    /** Wakes with a stretch and a purr, on the server. */
    private void wake() {
        entityData.set(ASLEEP, false);
        wiggle();
    }

    /**
     * One tick of its sleep, on the server: asleep at night in its tank, but roused, called or on its way to the hatch,
     * munching, going for a meal, curled up in its tun or away through the spore drive; settling down with a drowsy
     * purr, snoring now and then, and waking at daybreak.
     */
    private void sleepOrWake() {
        roused = Math.max(0, roused - 1);
        boolean drowsy = night() && roused == 0 && munching == 0 && called == null && coming == null && meal == null && !isCurled()
            && jump() == Jump.HERE;
        if (drowsy && !isAsleep()) {
            entityData.set(ASLEEP, true);
            playSound(SkylinersRegistry.TARDIGRADE_MRRP.get(), VOLUME, DROWSY_PITCH);
        } else if (!drowsy && isAsleep()) {
            wake();
        } else if (drowsy && tickCount % SNORE_EVERY == 0) {
            playSound(SkylinersRegistry.TARDIGRADE_SNORE.get(), SNORE_VOLUME, 0.9F + random.nextFloat() * 0.2F);
        }
    }

    /** Whether it is night in its tank: from dusk to dawn, in a world whose sky turns. */
    private boolean night() {
        if (level().dimensionType().hasFixedTime()) {
            return false;
        }
        long time = level().getDayTime() % DAY;
        return time >= NIGHT_FROM && time < NIGHT_TO;
    }

    /** Whether it is curled up in its tun, on either side. */
    public boolean isCurled() {
        return entityData.get(CURLED);
    }

    /**
     * Curls it up into its tun, with a bloop, or out of it, with a purr; curling up, it leaves off its meal and its
     * call to the hatch.
     */
    public void setCurled(boolean curled) {
        if (curled == isCurled()) {
            return;
        }
        entityData.set(CURLED, curled);
        entityData.set(ASLEEP, false);
        SoundEvent sound = curled ? SkylinersRegistry.TARDIGRADE_BLOOP.get() : SkylinersRegistry.TARDIGRADE_MRRP.get();
        playSound(sound, VOLUME, curled ? 1.0F : 0.85F);
        munching = 0;
        burpIn = 0;
        settleIceCream();
        called = null;
        coming = null;
        forgetMeal();
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

    /** How far ahead of its middle its mouth is, as big as it is, in blocks. */
    private double mouthAhead() {
        return MOUTH_AHEAD * getScale();
    }

    /** How far over its feet its mouth is, as big as it is, in blocks. */
    private double mouthUp() {
        return MOUTH_UP * getScale();
    }

    /** Where its mouth is, in front of its head, among her blocks. */
    public Vec3 mouth() {
        return position().add(Vec3.directionFromRotation(0, yBodyRot).scale(mouthAhead())).add(0, mouthUp(), 0);
    }

    /**
     * A point in the world as the creature sees it, in her frame while it lives among her blocks (where its own place
     * is), the point itself otherwise.
     */
    private Vec3 seen(Vec3 point) {
        return WatchVisitors.seen(this, point);
    }

    /** Whether something held is what it eats: moss, a babycino or an ice cream, never refused (see {@link #chew}). */
    public static boolean eats(ItemStack stack) {
        return stack.is(Items.MOSS_BLOCK) || stack.is(Items.MOSS_CARPET) || stack.is(SkylinersRegistry.BABYCINO.get())
            || stack.is(SkylinersRegistry.ICE_CREAM.get());
    }

    /**
     * Fed straight from the hand, as through the open hatch, or touched with an empty one: it munches the moss or
     * gulps the babycino or the ice cream, or wriggles (or wakes, asleep), where it is. In its tun, or away through the
     * spore drive, it heeds neither.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean empty = held.isEmpty() && hand == InteractionHand.MAIN_HAND;
        if (isCurled() || jump() != Jump.HERE || !eats(held) && !empty) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            boolean woke = rouse();
            if (!empty) {
                chew(held.copyWithCount(1));
                held.consume(1, player);
            } else if (!woke) {
                wiggle();
            }
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /**
     * Chews what it was fed or has swallowed, on the server: a bite at once and every so often, the client shown it and
     * bits of it falling from its mouth, and a burp once it is done. A babycino it does not chew: it goes straight to
     * its spore drive (see {@link #gulpAndJump}). Nor an ice cream, which goes straight to its belly (see
     * {@link #gulpAndSwell}).
     */
    private void chew(ItemStack eaten) {
        entityData.set(EATEN, eaten);
        if (eaten.is(SkylinersRegistry.BABYCINO.get())) {
            gulpAndJump();
            return;
        }
        if (eaten.is(SkylinersRegistry.ICE_CREAM.get())) {
            gulpAndSwell();
            return;
        }
        munching = MUNCH_TICKS;
        burpIn = MUNCH_TICKS + BURP_AFTER;
        level().broadcastEntityEvent(this, MUNCHING);
    }

    /**
     * A little secret of its keepers: a babycino is gone in one gulp, and the frothed milk sets off its spore drive,
     * however it was busy. It charges up at once and jumps, and burps when it is back.
     */
    private void gulpAndJump() {
        munching = 0;
        burpIn = 0;
        burpOnReturn = true;
        settleIceCream();
        playSound(SoundEvents.GENERIC_DRINK, VOLUME, 0.8F);
        chargeUp();
    }

    /**
     * Its keepers' bigger secret: an ice cream goes down in one gulp, however it was busy, and it swells up with it,
     * toward a massive burp (see {@link #bigBurp}).
     */
    private void gulpAndSwell() {
        munching = 0;
        burpIn = 0;
        burpOnReturn = false;
        stretchIn = 0;
        bigBurpIn = SWELL_TICKS;
        playSound(SoundEvents.GENERIC_EAT, VOLUME, 0.8F);
        level().broadcastEntityEvent(this, GULPED);
    }

    /** Whether an ice cream is still going down: from the gulp until it stretches out for hyperspace. */
    private boolean iceCreamGoingDown() {
        return bigBurpIn > 0 || stretchIn > 0;
    }

    /** Leaves off an ice cream going down, as when it curls up or is startled away. */
    private void settleIceCream() {
        bigBurpIn = 0;
        stretchIn = 0;
    }

    /** One tick of an ice cream going down, on the server: on to its massive burp, and from that on to hyperspace. */
    private void iceCreamDown() {
        if (bigBurpIn > 0 && --bigBurpIn == 0) {
            bigBurp();
            stretchIn = STRETCH_AFTER;
        } else if (stretchIn > 0 && --stretchIn == 0) {
            stretchOut();
        }
    }

    /**
     * Its massive burp, on the server: its burp's voice an octave down with another over it, the ship's rumble under
     * them, the client shown a great cloud from its mouth, and the ship shaken for everyone on her.
     */
    private void bigBurp() {
        playSound(SkylinersRegistry.TARDIGRADE_BURP.get(), BIG_BURP_VOLUME, BIG_BURP_PITCH);
        playSound(SkylinersRegistry.TARDIGRADE_BURP.get(), BIG_BURP_VOLUME, BIG_BURP_SECOND_PITCH);
        playSound(SoundEvents.GENERIC_EXPLODE.value(), RUMBLE_VOLUME, RUMBLE_PITCH);
        level().broadcastEntityEvent(this, BIG_BURPED);
        shakeShip();
    }

    /**
     * Shakes the view of everyone near it, as rough air does, as far and as hard as it is big: players are in the world,
     * it among her blocks.
     */
    private void shakeShip() {
        Vec3 here = SableFlight.toWorld(level(), position());
        float size = getScale();
        for (Player player : level().players()) {
            if (player instanceof ServerPlayer near && near.position().distanceTo(here) < SHAKE_REACH * size) {
                PacketDistributor.sendToPlayer(near, new TurbulencePayload(SHAKE_STRENGTH * size, SHAKE_TICKS));
            }
        }
    }

    /** Starts stretching out for hyperspace, on the server, with the drive's rising hum. */
    private void stretchOut() {
        munching = 0;
        burpIn = 0;
        setJump(Jump.STRETCHING);
        jumpTicks = STRETCH_TICKS;
        playSound(SkylinersRegistry.TARDIGRADE_JUMP.get(), VOLUME, 1.0F);
    }

    /** Burps after its meal, on the server, the client shown a puff from its mouth. */
    private void burp() {
        playSound(SkylinersRegistry.TARDIGRADE_BURP.get(), VOLUME, 0.9F + random.nextFloat() * 0.2F);
        level().broadcastEntityEvent(this, BURPED);
    }

    /** Wriggles with a purr, on the server, the client shown it. */
    private void wiggle() {
        level().broadcastEntityEvent(this, WIGGLING);
        playSound(SkylinersRegistry.TARDIGRADE_MRRP.get(), VOLUME, 1.2F);
    }

    @Override
    public void handleEntityEvent(byte id) {
        switch (id) {
            case MUNCHING -> munchShown = MUNCH_TICKS;
            case WIGGLING -> wiggleShown = WIGGLE_TICKS;
            case BURPED -> burpPuff();
            case JUMPED -> sporeBurst();
            case GULPED -> swellShown = SWELL_TICKS;
            case BIG_BURPED -> bigBurpCloud();
            case STREAKED -> hyperspaceStreaks();
            default -> super.handleEntityEvent(id);
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
        sleepOrWake();
        sporeDrive();
        lookForMeal();
        suckIn();
        if (burpIn > 0 && --burpIn == 0) {
            burp();
        }
        iceCreamDown();
        if (munching > 0) {
            if (munching % BITE_EVERY == 0) {
                Vec3 mouth = mouth();
                level().playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.SNIFFER_EAT, SoundSource.NEUTRAL, softer(VOLUME),
                    higher(0.75F + random.nextFloat() * 0.15F));
            }
            munching--;
        }
    }

    /**
     * The client's side of it: its curling, settling down asleep, munching and wriggling played out, bits of moss
     * falling as it munches and a "z" rising from it now and then asleep.
     */
    private void animate() {
        curlBefore = curl;
        curl = Mth.approach(curl, isCurled() ? 1 : 0, 1.0F / CURL_TICKS);
        sleepBefore = sleep;
        sleep = Mth.approach(sleep, isAsleep() ? 1 : 0, 1.0F / SETTLE_TICKS);
        if (isAsleep() && tickCount % Z_EVERY == 0) {
            Vec3 head = overHead(Z_UP * getScale());
            ShipParticles.give(this, SkylinersRegistry.SLEEP_Z.get(), head, new Vec3(0, Z_RISE, 0));
        }
        chargeBefore = charge;
        charge = Mth.approach(charge, jump() == Jump.CHARGING ? 1 : 0, 1.0F / CHARGE_TICKS);
        if (jump() == Jump.CHARGING) {
            sporeSwirl();
        }
        stretchBefore = stretch;
        stretch = Mth.approach(stretch, jump() == Jump.STRETCHING ? 1 : 0, 1.0F / STRETCH_TICKS);
        swellShown = Math.max(0, swellShown - 1);
        wiggleShown = Math.max(0, wiggleShown - 1);
        if (munchShown > 0) {
            munchShown--;
            if (munchShown % CRUMBS_EVERY == 0) {
                crumbs();
            }
        }
    }

    /**
     * A few bits of what it ate falling from its mouth as it munches, on the client: moss, unless it ate something
     * else.
     */
    private void crumbs() {
        Vec3 mouth = mouth();
        ItemStack eaten = entityData.get(EATEN);
        ItemParticleOption moss = new ItemParticleOption(ParticleTypes.ITEM, eaten.isEmpty() ? new ItemStack(Items.MOSS_BLOCK) : eaten);
        for (int bit = 0; bit < CRUMBS; bit++) {
            ShipParticles.give(this, moss, mouth, new Vec3((random.nextDouble() - 0.5) * CRUMB_SPREAD, CRUMB_SPREAD / 2 + random.nextDouble() * CRUMB_SPREAD,
                (random.nextDouble() - 0.5) * CRUMB_SPREAD));
        }
    }

    /** A puff of air from its mouth as it burps, on the client, and a little shake. */
    private void burpPuff() {
        Vec3 mouth = mouth();
        Vec3 ahead = Vec3.directionFromRotation(0, yBodyRot);
        double speed = BURP_SPEED * getScale();
        for (int puff = 0; puff < BURP_PUFFS; puff++) {
            ShipParticles.give(this, ParticleTypes.POOF, mouth, new Vec3(ahead.x * speed + (random.nextDouble() - 0.5) * speed,
                (random.nextDouble() - 0.3) * speed, ahead.z * speed + (random.nextDouble() - 0.5) * speed));
        }
        wiggleShown = WIGGLE_TICKS / 2;
    }

    /** A great cloud from its mouth as it lets out its massive burp, on the client, bits of the ice cream in it. */
    private void bigBurpCloud() {
        swellShown = 0;
        wiggleShown = WIGGLE_TICKS;
        Vec3 mouth = mouth();
        Vec3 ahead = Vec3.directionFromRotation(0, yBodyRot);
        double speed = BIG_BURP_SPEED * getScale();
        for (int puff = 0; puff < BIG_BURP_PUFFS; puff++) {
            ShipParticles.give(this, ParticleTypes.POOF, mouth, new Vec3(ahead.x * speed + (random.nextDouble() - 0.5) * speed,
                (random.nextDouble() - 0.3) * speed, ahead.z * speed + (random.nextDouble() - 0.5) * speed));
        }
        ItemParticleOption eaten = new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(SkylinersRegistry.ICE_CREAM.get()));
        for (int bit = 0; bit < BIG_BURP_BITS; bit++) {
            ShipParticles.give(this, eaten, mouth, new Vec3(ahead.x * speed + (random.nextDouble() - 0.5) * speed,
                random.nextDouble() * speed, ahead.z * speed + (random.nextDouble() - 0.5) * speed));
        }
    }

    /** Streaks of light flying on ahead from all along it as it goes to hyperspace, on the client. */
    private void hyperspaceStreaks() {
        Vec3 ahead = Vec3.directionFromRotation(0, yBodyRot);
        double reach = mouthAhead();
        for (int streak = 0; streak < STREAKS; streak++) {
            Vec3 at = position().add(ahead.scale((random.nextDouble() - 0.5) * reach * 2))
                .add((random.nextDouble() - 0.5) * reach, random.nextDouble() * reach, (random.nextDouble() - 0.5) * reach);
            ShipParticles.give(this, ParticleTypes.END_ROD, at, new Vec3(ahead.x * STREAK_SPEED, ahead.y * STREAK_SPEED, ahead.z * STREAK_SPEED));
        }
    }

    /** Spores swirling in round it as it charges up to jump, on the client, from all round toward its middle. */
    private void sporeSwirl() {
        double size = getScale();
        Vec3 middle = position().add(0, mouthUp(), 0);
        double from = SWIRL_FROM * size;
        for (int spore = 0; spore < SWIRL; spore++) {
            double round = random.nextDouble() * Mth.TWO_PI;
            Vec3 start = middle.add(Math.cos(round) * from, (random.nextDouble() - UNDER_MIDDLE) * from, Math.sin(round) * from);
            Vec3 in = middle.subtract(start).scale(SWIRL_IN).add(-Math.sin(round) * SWIRL_ROUND * size, 0, Math.cos(round) * SWIRL_ROUND * size);
            ShipParticles.give(this, SkylinersRegistry.SPORE.get(), start, in);
        }
    }

    /** A burst of spores from all over it as it jumps away or comes back, on the client. */
    private void sporeBurst() {
        double size = getScale();
        for (int spore = 0; spore < BURST; spore++) {
            Vec3 out = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - UNDER_MIDDLE, random.nextDouble() - 0.5).normalize()
                .scale(BURST_SPEED * size * random.nextDouble());
            Vec3 at = position().add(out.scale(BURST_OUT)).add(0, mouthUp(), 0);
            ShipParticles.give(this, SkylinersRegistry.SPORE.get(), at, out);
        }
    }

    /**
     * One tick of its spore drive, on the server: now and then, resting awake in its tank, it charges up and jumps
     * away, and a few seconds later comes back where it was, its return heard as it comes; away, it is not to be seen.
     * It burps as it comes back from a jump a babycino set off.
     */
    private void sporeDrive() {
        switch (jump()) {
            case HERE -> {
                if (mayJump() && random.nextFloat() < JUMP_CHANCE) {
                    chargeUp();
                }
            }
            case CHARGING -> {
                if (--jumpTicks <= 0) {
                    goAway(JUMPED);
                }
            }
            case STRETCHING -> {
                if (--jumpTicks <= 0) {
                    goAway(STREAKED);
                }
            }
            case AWAY -> {
                if (--jumpTicks == RETURN_HEARD) {
                    playSound(SkylinersRegistry.TARDIGRADE_RETURN.get(), VOLUME, 1.0F);
                }
                if (jumpTicks <= 0) {
                    back();
                }
            }
        }
    }

    /** Gone, on the server, for as long as it stays away, the client shown it go as this says. */
    private void goAway(byte shown) {
        setJump(Jump.AWAY);
        jumpTicks = fleeing ? FLED_FROM + random.nextInt(FLED_TO - FLED_FROM) : AWAY_FROM + random.nextInt(AWAY_TO - AWAY_FROM);
        fleeing = false;
        setInvisible(true);
        level().broadcastEntityEvent(this, shown);
    }

    /**
     * Attacked by a player, on the server: nothing happens to it, but it yelps, woken if it slept, and jumps away at
     * once for a long while. Curled up in its tun it does not notice, and away there is nothing to hit.
     */
    private void flee() {
        if (level().isClientSide || isCurled() || jump() == Jump.AWAY) {
            return;
        }
        rouse();
        fleeing = true;
        if (jump() == Jump.HERE) {
            munching = 0;
            burpIn = 0;
            settleIceCream();
            playSound(SkylinersRegistry.TARDIGRADE_MRRP.get(), VOLUME, YELP_PITCH);
            chargeUp();
        }
    }

    /** Starts charging up to jump, with the spore drive's rising hum. */
    private void chargeUp() {
        setJump(Jump.CHARGING);
        jumpTicks = CHARGE_TICKS;
        playSound(SkylinersRegistry.TARDIGRADE_JUMP.get(), VOLUME, 1.0F);
    }

    /** Back from a jump, where it was. */
    private void back() {
        setJump(Jump.HERE);
        setInvisible(false);
        level().broadcastEntityEvent(this, JUMPED);
        if (burpOnReturn) {
            burpOnReturn = false;
            burpIn = BURP_AFTER;
        }
    }

    /** Whether it may jump: kept in its tank, resting awake, with nothing to eat or do and standing still. */
    private boolean mayJump() {
        return from != null && !isCurled() && !isAsleep() && munching == 0 && burpIn == 0 && !iceCreamGoingDown() && meal == null && called == null
            && coming == null && turning == 0 && walked == 0;
    }

    /**
     * Now and then a look for what has been thrown into its tank, on the server, the nearest it sees to go for; what it
     * went for, gone or out of its tank, it forgets, letting it fall again.
     */
    private void lookForMeal() {
        if (meal != null && (!meal.isAlive() || jump() != Jump.HERE || !inTank(itemPoint(meal)))) {
            forgetMeal();
        }
        if (meal != null || from == null || isCurled() || jump() != Jump.HERE || tickCount % MEAL_LOOK_EVERY != 0) {
            return;
        }
        ItemEntity nearest = null;
        double nearestDistance = MEAL_RANGE;
        for (ItemEntity item : itemsNear()) {
            Vec3 at = itemPoint(item);
            double distance = at.distanceTo(position());
            if (item.isAlive() && distance < nearestDistance && inTank(at)) {
                nearest = item;
                nearestDistance = distance;
            }
        }
        if (nearest != null) {
            meal = nearest;
            rouse();
        }
    }

    private void forgetMeal() {
        if (meal != null && meal.isAlive()) {
            meal.setNoGravity(false);
        }
        meal = null;
        sucked = 0;
        atMeal = false;
    }

    /** The things lying round it: among her blocks, where it lives, and in the world round her. */
    private List<ItemEntity> itemsNear() {
        Set<ItemEntity> near = new HashSet<>(level().getEntitiesOfClass(ItemEntity.class, getBoundingBox().inflate(MEAL_RANGE, MEAL_UP, MEAL_RANGE)));
        Vec3 world = SableFlight.toWorld(level(), position());
        if (!world.equals(position())) {
            near.addAll(level().getEntitiesOfClass(ItemEntity.class, new AABB(world, world).inflate(MEAL_RANGE, MEAL_UP, MEAL_RANGE)));
        }
        return List.copyOf(near);
    }

    /** Where a thing is as it sees it, among her blocks, where it lives itself. */
    private Vec3 itemPoint(ItemEntity item) {
        return SableFlight.inPlot(item) ? item.position() : seen(item.position());
    }

    /**
     * Whether a point is in its tank: nothing solid between it and its head, so not beyond its glass, her skin or
     * the hatch.
     */
    private boolean inTank(Vec3 at) {
        ClipContext look = new ClipContext(overHead(mouthUp()), at.add(0, LOOKED_OVER, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
        return level().clip(look).getType() == HitResult.Type.MISS;
    }

    /** Over its head, halfway out to its mouth, so high over its middle, among her blocks. */
    private Vec3 overHead(double up) {
        return position().add(Vec3.directionFromRotation(0, yBodyRot).scale(mouthAhead() / 2)).add(0, up, 0);
    }

    /**
     * One tick of going for its meal, on the server: along its line to where its mouth is level with it, looking at
     * it.
     */
    private void goToMeal() {
        Vec3 at = itemPoint(meal);
        getLookControl().setLookAt(at);
        Vec3 line = to.subtract(from);
        double length = line.length();
        if (length == 0) {
            atMeal = true;
            return;
        }
        Vec3 along = line.scale(1 / length);
        double mouthAhead = Vec3.directionFromRotation(0, getYRot()).dot(along) > 0 ? mouthAhead() : -mouthAhead();
        double stand = Mth.clamp(at.subtract(from).dot(along) - mouthAhead, 0, length);
        atMeal = walkTo(from.add(along.scale(stand)));
    }

    /**
     * Sucks its meal in once its mouth is near it, or it stands as near as it comes, on the server: a slurp, and the
     * thing flies to its mouth, out of anyone's reach, and is swallowed there.
     */
    private void suckIn() {
        if (meal == null || munching > 0) {
            return;
        }
        Vec3 at = itemPoint(meal);
        Vec3 mouth = mouth();
        double distance = at.distanceTo(mouth);
        if (sucked == 0 && distance > SUCK_REACH * getScale() && !atMeal) {
            return;
        }
        if (sucked++ == 0) {
            playSound(SkylinersRegistry.TARDIGRADE_SLURP.get(), VOLUME, 0.9F + random.nextFloat() * 0.2F);
        }
        if (distance < SWALLOWED * getScale() || sucked > SUCK_LONGEST) {
            swallow();
            return;
        }
        Vec3 target = SableFlight.inPlot(meal) ? mouth : SableFlight.toWorld(level(), mouth);
        Vec3 pull = target.subtract(meal.position());
        meal.setNoGravity(true);
        meal.setPickUpDelay(SUCK_LONGEST);
        meal.setDeltaMovement(pull.normalize().scale(Math.min(SUCK_SPEED, pull.length())));
        meal.hasImpulse = true;
    }

    /** Swallows its meal, the whole of it, and chews it. */
    private void swallow() {
        ItemStack eaten = meal.getItem().copyWithCount(1);
        meal.discard();
        meal = null;
        sucked = 0;
        atMeal = false;
        chew(eaten);
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
        double speed = (ahead ? WALK_SPEED : BACK_SPEED) * Math.sqrt(getScale()) * Math.min(1, walked / (double) EASE);
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

    /** At the hatch it was called to: it eats what it was fed or wriggles at being touched. */
    private void arrive(boolean fed) {
        if (fed) {
            chew(calledWith);
        } else {
            wiggle();
        }
    }

    /**
     * It cannot be hurt, but by what passes every protection: an operator's kill or falling out of the world. A
     * player's attack, by hand, by blade or by arrow, only sends it fleeing (see {@link #flee}).
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (source.getEntity() instanceof Player) {
            flee();
        }
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /** Away through the spore drive, there is nothing of it to touch. */
    @Override
    public boolean isPickable() {
        return jump() != Jump.AWAY && super.isPickable();
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
        return isCurled() || isAsleep() || jump() != Jump.HERE ? null : SkylinersRegistry.TARDIGRADE_MRRP.get();
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
        return getBoundingBox().inflate(mouthAhead(), 0, mouthAhead());
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Curled", isCurled());
        tag.putBoolean("Asleep", isAsleep());
        tag.putBoolean("Kept", kept);
    }

    /**
     * Loaded again, it waits for its keeper as long as it would have from now, sleeps on if it was asleep, and is back
     * from any jump.
     */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        entityData.set(CURLED, tag.getBoolean("Curled"));
        entityData.set(ASLEEP, tag.getBoolean("Asleep"));
        kept = tag.getBoolean("Kept");
        keptAt = level().getGameTime();
        setInvisible(false);
    }

    /** Gone, as when its tank is broken, it lets go of a meal it was sucking in, to fall again. */
    @Override
    public void remove(RemovalReason reason) {
        forgetMeal();
        super.remove(reason);
    }

    /**
     * Its life in the tank: resting, lumbering to somewhere along its line and back, turning round where it can, and
     * coming to the hatch when called there, turning round first if it faces away. A walk is a few steps, each a point
     * to walk to or a turn; nothing at all while it is curled up, asleep or away through the spore drive.
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

        TankLife(Tardigrade creature) {
            this.creature = creature;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return creature.from != null && !creature.isCurled() && !creature.isAsleep() && creature.jump() == Jump.HERE;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        /**
         * Stopped, as when it curls up or jumps away: standing still, squared to its line if it was turning round, and
         * called to the hatch again if it was on its way there.
         */
        @Override
        public void stop() {
            putOff();
            if (creature.turning > 0) {
                creature.turning = 0;
                creature.setYRot(creature.lineYaw(creature.facesHatch()));
            }
            creature.halt();
        }

        @Override
        public void tick() {
            if (creature.munching > 0 || creature.iceCreamGoingDown()) {
                creature.halt();
                lookAtHatch();
                return;
            }
            if (creature.meal != null) {
                putOff();
                creature.goToMeal();
                return;
            }
            if (creature.called != null && creature.turning == 0) {
                comeToHatch(creature.called);
                creature.called = null;
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

        /** Leaves off its walk, to come to the hatch again afterwards if it was on its way there. */
        private void putOff() {
            steps.clear();
            stepTicks = 0;
            if (creature.coming != null) {
                creature.called = creature.coming;
                creature.coming = null;
            }
        }

        /** Once the last step of a walk is done: at the hatch if it was called there, then a rest. */
        private void arrived() {
            if (!steps.isEmpty()) {
                return;
            }
            if (creature.coming != null) {
                creature.arrive(creature.coming);
                creature.coming = null;
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
            creature.coming = feed;
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
