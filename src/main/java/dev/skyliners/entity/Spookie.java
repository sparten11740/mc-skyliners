package dev.skyliners.entity;

import dev.skyliners.SkylinersRegistry;
import java.util.EnumSet;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * The freighter's Spookie: a shaggy giant of her crew, two and a half blocks tall, slate blue-grey fur with a cream
 * mane, a charcoal face with two small tusks and big amber eyes, long tufted ears that show how it feels, and a hi-vis
 * tool harness. It lives among her blocks, in her plot (its type is in {@code #sable:retain_in_sub_level}), so Sable
 * carries it as she flies, kept by its hammock in her crew quarters (see
 * {@link dev.skyliners.spookie.SpookieHammockBlockEntity}), which hands it every tick the rounds it walks: round her
 * ring corridor, looking into her rooms on the way, and home to its hammock, resting a while at each stop. It never
 * finds its own way, as a way off her deck would take it out of her blocks.
 *
 * <p>It watches whoever comes near. Someone coming up to it after a while alone it greets, its arms thrown up and its
 * ears pricked, with a rising "hoo-HOO" and a chuff. Touched with an empty hand, it purrs and hoots, its ears up; fed a
 * cookie, sweet berries or honey, it gulps it down and purrs. Struck, it is never hurt: it honks in alarm, its ears flat
 * back, and turns its back. Nothing pushes or leads it, and it never despawns; left without its keeper a while, as when
 * its hammock is broken, it goes.
 */
public class Spookie extends PathfinderMob {
    /** How fast it strolls between stops, in blocks a tick. */
    private static final double WALK_SPEED = 0.08;
    /** How long it takes to come up to that speed from standing, in ticks. */
    private static final int EASE = 10;
    /**
     * How far it must have got in so long walking, in blocks and ticks, not to be stuck, as behind something set in its
     * way: stuck, it goes on to its next stop.
     */
    private static final double STUCK_MOVE = 0.5;
    private static final int STUCK_TICKS = 100;
    /** How close to a stop it has come once there, in blocks. */
    private static final double THERE = 0.2;
    /** How far it turns toward where it walks each tick, in degrees. */
    private static final float TURN_RATE = 10.0F;
    /** How far it looks for whoever is near to watch, in blocks. */
    private static final float LOOK_RANGE = 12.0F;
    /** How near someone has come for it to greet them, in blocks, if it can see them. */
    private static final double GREET_REACH = 6;
    /** How long after its last greeting it greets anyone again, in ticks. */
    private static final int GREET_AGAIN = 1200;
    /** How often it looks whether someone has come to greet, in ticks. */
    private static final int GREET_LOOK_EVERY = 10;
    /** How long it stands still greeting, touched or fed, or startled, in ticks. */
    private static final int STANDS_STILL = 50;
    /** How long its mood shows after it is greeted, touched, fed or struck, in ticks. */
    private static final int MOOD_TICKS = 60;
    /** How loud its voice is. */
    private static final float VOLUME = 1.0F;
    /** How long it may go without its keeper before it goes, in ticks. */
    private static final int UNKEPT = 200;
    /** What the client is told as it greets someone, its arms thrown up. */
    private static final byte GREETING = 90;
    /** How long its arms stay up greeting, in ticks, on the client. */
    public static final int GREET_TICKS = 30;

    /** How it feels, as its ears show: as it was, happy, or alarmed. */
    public enum Mood { CALM, HAPPY, ALARMED }

    /** A stop on its rounds: where, among her blocks, its feet on her deck, and how long it rests there, in ticks. */
    public record Stop(Vec3 at, int rest) {
    }

    private static final EntityDataAccessor<Integer> MOOD =
        SynchedEntityData.defineId(Spookie.class, EntityDataSerializers.INT);

    /** Its rounds as its keeper hands them, the stop it walks to next, and how long it rests there yet, in ticks. */
    private List<Stop> rounds = List.of();
    private int next;
    private int resting;
    private int walked;
    /** Where it last got on from walking, and how long it has walked since without getting on, in ticks. */
    @Nullable
    private Vec3 gotOnAt;
    private int stuckFor;
    /** How long it stands still yet, greeting, being touched or fed, or startled, in ticks. */
    private int standing;
    private int moodTicks;
    private long greetedAt = -GREET_AGAIN;
    private boolean kept;
    private long keptAt;
    /** On the client: how long its arms stay up yet, greeting, in ticks. */
    private int greetShown;

    public Spookie(EntityType<? extends Spookie> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 60).add(Attributes.MOVEMENT_SPEED, WALK_SPEED)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1).add(Attributes.FOLLOW_RANGE, LOOK_RANGE);
    }

    /** Gives the Spookie its attributes as the game asks for every living thing's. */
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(SkylinersRegistry.SPOOKIE.get(), createAttributes().build());
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(MOOD, Mood.CALM.ordinal());
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Rounds(this));
        goalSelector.addGoal(2, new WatchVisitors(this, LOOK_RANGE, () -> mood() != Mood.ALARMED));
        goalSelector.addGoal(3, new RandomLookAroundGoal(this));
    }

    /**
     * Kept by its keeper this tick, among her blocks: the rounds it walks, the same list every tick until its keeper
     * lays them out again, when it walks on from the stop nearest it.
     */
    public void keep(long tick, List<Stop> handed) {
        if (handed != rounds) {
            rounds = handed;
            next = nearestStop();
            resting = 0;
        }
        kept = true;
        keptAt = tick;
    }

    /** The stop of its rounds nearest it. */
    private int nearestStop() {
        int nearest = 0;
        for (int stop = 1; stop < rounds.size(); stop++) {
            if (rounds.get(stop).at().distanceToSqr(position()) < rounds.get(nearest).at().distanceToSqr(position())) {
                nearest = stop;
            }
        }
        return nearest;
    }

    /** How it feels now, as its ears show. */
    public Mood mood() {
        int shown = entityData.get(MOOD);
        return shown >= 0 && shown < Mood.values().length ? Mood.values()[shown] : Mood.CALM;
    }

    private void feel(Mood mood) {
        entityData.set(MOOD, mood.ordinal());
        moodTicks = MOOD_TICKS;
    }

    /** How far its arms are up greeting this frame, from 0 to 1, on the client. */
    public float greeting(float partialTick) {
        float left = Math.max(0, greetShown - partialTick) / GREET_TICKS;
        return Mth.sin(Mth.PI * left);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            greetShown = Math.max(0, greetShown - 1);
            return;
        }
        if (kept && level().getGameTime() - keptAt > UNKEPT) {
            discard();
            return;
        }
        if (moodTicks > 0 && --moodTicks == 0) {
            entityData.set(MOOD, Mood.CALM.ordinal());
        }
        standing = Math.max(0, standing - 1);
        greetWhoeverComes();
    }

    /**
     * Greets the nearest player come near it, if it has greeted nobody for a while: it stops, turns to them, throws its
     * arms up with its ears pricked and hoots.
     */
    private void greetWhoeverComes() {
        long now = level().getGameTime();
        if (now - greetedAt < GREET_AGAIN || now % GREET_LOOK_EVERY != 0 || mood() == Mood.ALARMED) {
            return;
        }
        Player visitor = WatchVisitors.nearest(this, GREET_REACH);
        if (visitor == null || !sees(WatchVisitors.seen(this, visitor.getEyePosition()))) {
            return;
        }
        greetedAt = now;
        faceTo(WatchVisitors.seen(this, visitor.getEyePosition()));
        standStill();
        feel(Mood.HAPPY);
        level().broadcastEntityEvent(this, GREETING);
        playSound(SkylinersRegistry.SPOOKIE_GREET.get(), VOLUME, 1.0F);
    }

    /** Whether nothing solid stands between its eyes and a point among her blocks, as a wall or a floor. */
    private boolean sees(Vec3 point) {
        ClipContext look = new ClipContext(getEyePosition(), point, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this);
        return level().clip(look).getType() == HitResult.Type.MISS;
    }

    /** Turns its body and head to face a point among her blocks. */
    private void faceTo(Vec3 point) {
        Vec3 off = point.subtract(position());
        float yaw = (float) (Mth.atan2(off.z, off.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(yaw);
        setYBodyRot(yaw);
        setYHeadRot(yaw);
    }

    private void standStill() {
        standing = STANDS_STILL;
        halt();
    }

    /** Stops where it is, to start off slowly again. */
    private void halt() {
        walked = 0;
        gotOnAt = null;
        stuckFor = 0;
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }

    /**
     * Whether it has walked a while without getting on, as against something set in its way; counted each tick it
     * walks, and starting again once it has.
     */
    private boolean stuck() {
        if (gotOnAt == null || gotOnAt.distanceTo(position()) > STUCK_MOVE) {
            gotOnAt = position();
            stuckFor = 0;
            return false;
        }
        if (++stuckFor < STUCK_TICKS) {
            return false;
        }
        stuckFor = 0;
        return true;
    }

    @Override
    public void handleEntityEvent(byte id) {
        if (id == GREETING) {
            greetShown = GREET_TICKS;
            return;
        }
        super.handleEntityEvent(id);
    }

    /**
     * Touched with an empty hand, it purrs and hoots, its ears up; fed something it eats, it gulps it down first. Either
     * way it stops and turns to whoever it is.
     */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        boolean empty = held.isEmpty() && hand == InteractionHand.MAIN_HAND;
        if (!empty && !eats(held)) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            faceTo(WatchVisitors.seen(this, player.getEyePosition()));
            standStill();
            feel(Mood.HAPPY);
            if (!empty) {
                eat(player, hand, held);
            }
            playSound(SkylinersRegistry.SPOOKIE_HAPPY.get(), VOLUME, 1.0F);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** Eats one of what it was fed with a gulp, the bottle honey came in handed back. */
    private void eat(Player player, InteractionHand hand, ItemStack held) {
        if (held.is(Items.HONEY_BOTTLE)) {
            player.setItemInHand(hand, ItemUtils.createFilledResult(held, player, new ItemStack(Items.GLASS_BOTTLE)));
        } else {
            held.consume(1, player);
        }
        playSound(SoundEvents.GENERIC_EAT, VOLUME, 0.7F);
    }

    /** Whether something held is what it eats: a cookie, sweet berries or honey. */
    public static boolean eats(ItemStack stack) {
        return stack.is(Items.COOKIE) || stack.is(Items.SWEET_BERRIES) || stack.is(Items.HONEY_BOTTLE);
    }

    /**
     * Never hurt, but by what nothing withstands, as falling out of the world: struck by a player, it honks in alarm
     * with its ears flat back and turns its back on them, once for each fright, however many blows come while it lasts.
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (!level().isClientSide && mood() != Mood.ALARMED && source.getEntity() instanceof Player attacker) {
            Vec3 away = position().scale(2).subtract(WatchVisitors.seen(this, attacker.position()));
            faceTo(away);
            standStill();
            feel(Mood.ALARMED);
            playSound(SkylinersRegistry.SPOOKIE_ALARM.get(), VOLUME, 1.0F);
        }
        return false;
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

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Kept", kept);
    }

    /** Loaded again, it waits for its keeper as long as it would have from now. */
    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        kept = tag.getBoolean("Kept");
        keptAt = level().getGameTime();
    }

    /**
     * Its rounds: walking to each stop in turn, turning toward where it goes, and resting there as long as the stop
     * says; standing still while it greets, is touched or fed, or is startled.
     */
    private static final class Rounds extends Goal {
        private final Spookie spookie;

        Rounds(Spookie spookie) {
            this.spookie = spookie;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !spookie.rounds.isEmpty();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (spookie.standing > 0) {
                return;
            }
            if (spookie.resting > 0) {
                spookie.resting--;
                return;
            }
            Stop stop = spookie.rounds.get(Math.floorMod(spookie.next, spookie.rounds.size()));
            boolean there = spookie.walkTo(stop.at());
            if (there && stop.rest() > 0) {
                spookie.halt();
                spookie.resting = stop.rest();
            }
            if (there || spookie.stuck()) {
                spookie.next = (spookie.next + 1) % spookie.rounds.size();
            }
        }
    }

    /**
     * One tick of walking toward a point, turning toward it as it goes; whether it is there, when it walks straight on
     * to its next stop unless it rests.
     */
    private boolean walkTo(Vec3 point) {
        Vec3 off = point.subtract(position()).multiply(1, 0, 1);
        double distance = off.length();
        if (distance < THERE) {
            return true;
        }
        float heading = (float) (Mth.atan2(off.z, off.x) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(Mth.approachDegrees(getYRot(), heading, TURN_RATE));
        walked++;
        double speed = WALK_SPEED * Math.min(1, walked / (double) EASE);
        Vec3 step = off.scale(Math.min(speed, distance) / distance);
        setDeltaMovement(step.x, getDeltaMovement().y, step.z);
        return false;
    }
}
