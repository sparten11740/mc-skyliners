package dev.skyliners.beam;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.Loaded;
import dev.skyliners.block.Mount;
import dev.skyliners.compat.SableFlight;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * A tractor beam's block entity (see {@link TractorBeamBlock}): while it is lit, it carries what stands in its beam up
 * along it, gently, never hurting anything. Hung under a ceiling its beam shines down to the ground and lifts whatever
 * stands under it up to its lens; standing on a floor its beam shines up a dozen blocks, a lift of light. Whatever
 * reaches the top is brought up onto the nearest {@link BeamPadBlock} in reach, if there is one with room over it, as
 * the freighter's beam brings it up into her cargo hold; with none, it floats there. Whatever then steps onto the pad,
 * rather than arriving on it, is let down the beam to its foot, coming down slowly until it lands; but only onto solid
 * ground or water, never onto lava or where the beam meets no ground within its reach, when the pad only buzzes. Only
 * players, animals, villagers, golems, allays and loose items and experience ride it: never a hostile mob, a creature
 * of the water, which would dry out, a boss, anyone gliding on wings, or anyone riding or ridden. Anyone it lets go of,
 * by stepping out of it or as it goes dark, falls slowly for long enough to come down from the whole of its reach.
 *
 * <p>What it is doing with each thing it holds is kept only while it is lit, never saved: a beam loaded again starts
 * afresh, lifting whatever stands in it. Kept track of on the clients too, which draw its beam (see the client's
 * {@code TractorBeams}) and play its hum.
 */
public class TractorBeamBlockEntity extends BlockEntity {
    private static final Loaded<TractorBeamBlockEntity> LOADED = new Loaded<>(TractorBeamBlockEntity.class, true);
    /** How wide the beam catches things, as the most a thing's feet may stand off its middle line, in blocks. */
    public static final double RADIUS = 1.3;
    /**
     * How far off its middle line a thing it already carries may drift and still be held, in blocks: wider than it
     * catches things, so a ship hovering a little to and fro carries on lifting whoever it has hold of.
     */
    private static final double HOLD_RADIUS = 2.5;
    /**
     * How far a beam hung under a ceiling reaches down to the ground at most, in blocks: from the freighter's belly as
     * she hovers where her autopilot holds her, high enough to be clear of everything round her.
     */
    public static final double DOWN_REACH = 64;
    /** How high a beam standing on a floor shines up, in blocks. */
    public static final double UP_REACH = 12;
    /** How far below the top of its beam a lifted thing's head comes before it arrives there, in blocks. */
    private static final double ARRIVE_GAP = 0.3;
    /** How far past its top a thing it lifts may float and still be in the beam, in blocks, so a bobbing one stays. */
    private static final double TOP_SLACK = 1;
    /** How near the beam's foot a thing let down it must stand to have landed, in blocks. */
    private static final double LANDED_WITHIN = 1;
    /** How far a pad may be from the beam for things to arrive on it, in blocks. */
    public static final double PAD_REACH = 16;
    /** How far round a pad's middle a thing's feet must stand for it to be on the pad, in blocks. */
    private static final double ON_PAD = 0.55;
    /** How high over a pad's top a thing's feet may be and still stand on it, in blocks. */
    private static final double PAD_HEADROOM = 0.6;
    /** How fast it lifts things, in blocks a tick: six blocks a second. */
    private static final double RISE_SPEED = 0.3;
    /**
     * How fast it lets things other than players sink, in blocks a tick: three blocks a second. A player let down falls
     * slowly instead, as the game's slow falling has them fall, faster once under way but never hurt.
     */
    private static final double SINK_SPEED = 0.15;
    /** How much of its way off the beam's middle line a thing it carries is drawn back each tick, a share. */
    private static final double CENTRE_PULL = 0.12;
    /**
     * The levitation a player it lifts is given, as the effect's amplifier: the game lifts a levitating player at a
     * twentieth of a block a tick for each step, so five lifts them at its {@link #RISE_SPEED}.
     */
    private static final int RISE_LEVITATION = 5;
    /** The levitation a player floating at its top bobs on, as the effect's amplifier: the slowest rise. */
    private static final int FLOAT_LEVITATION = 0;
    /** How long each effect it gives lasts, in ticks. */
    private static final int EFFECT_TICKS = 10;
    /** How little may be left of an effect it gives before it is given again, in ticks. */
    private static final int EFFECT_RENEWED_BELOW = 4;
    /**
     * How long anyone it lets go of falls slowly, in ticks: ten seconds, enough to come down from the whole of its
     * reach as the game's slow falling has them fall.
     */
    private static final int SAFE_FALL_TICKS = 200;
    /** How often it looks again for its pad and for the ground under it, in ticks. */
    private static final int LOOK_EVERY = 10;
    /** How often a lit beam holding nothing looks for something come into it or onto its pad, in ticks. */
    private static final int IDLE_LOOK_EVERY = 4;
    /** How far apart the ground is looked for down its beam, in blocks. */
    private static final double GROUND_STEP = 0.5;
    /** How many things it carries at once at most: those it carries already first, then the nearest its middle line. */
    private static final int MOST_CARRIED = 16;
    /** How loud it is switched on and off, and how loud it refuses. */
    private static final float SWITCH_VOLUME = 1.0F;
    /** How loud a thing is lifted off, arrives and is let down. */
    private static final float RIDE_VOLUME = 0.9F;
    /** How many sparkles burst round a thing arriving on its pad or let down from it. */
    private static final int SPARKLES = 24;
    /** Played on the client while it is lit: its hum, set by the client so a dedicated server never loads it. */
    public static Consumer<TractorBeamBlockEntity> clientTicker = beam -> { };

    /** What the beam does with a thing it holds. */
    private enum Ride {
        /** Lifted up the beam. */
        RISING,
        /** Floating at the top, with no pad to arrive on. */
        HOLDING,
        /** Let down the beam from its pad. */
        SINKING,
        /** Down at the foot of the beam after sinking, left be until it steps out of the beam. */
        LANDED,
        /** On its pad, brought up onto it or not let down from it, left be until it steps off. */
        ON_PAD
    }

    /** The beam as it lies in the world: its foot and its top, the way up along it, and how long it is, in blocks. */
    private record Shape(Vec3 foot, Vec3 top, Vec3 up, double length) {
    }

    private final Map<UUID, Ride> rides = new HashMap<>();
    /** The pad things arrive on, among the same blocks as the beam; null for none. */
    @Nullable
    private BlockPos pad;
    /** How long the beam is, in blocks, as last looked; negative before it has looked. */
    private double length = -1;
    /**
     * Whether its foot, as last looked, is somewhere to let things down onto: solid ground or water within its reach.
     */
    private boolean safeFoot;

    public TractorBeamBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.TRACTOR_BEAM_ENTITY.get(), pos, state);
    }

    /** The tractor beams loaded in a level, on the server or on this client. */
    public static List<TractorBeamBlockEntity> loaded(Level level) {
        return LOADED.in(level);
    }

    /** The tractor beam nearest a block within a pad's reach of it; null for none. */
    @Nullable
    public static TractorBeamBlockEntity nearest(Level level, BlockPos from) {
        return loaded(level).stream().filter(beam -> beam.getBlockPos().closerThan(from, PAD_REACH))
            .min(Comparator.comparingDouble(beam -> beam.getBlockPos().distSqr(from))).orElse(null);
    }

    /** Whether it shines. */
    public boolean lit() {
        return getBlockState().getValue(TractorBeamBlock.LIT);
    }

    /** Whether it hangs under a ceiling and shines down, rather than standing on a floor and shining up. */
    public boolean shinesDown() {
        return getBlockState().getValue(TractorBeamBlock.MOUNT) == Mount.CEILING;
    }

    /** Where its beam leaves its lens, among its own blocks: under its housing hung, over it standing. */
    public Vec3 lens() {
        double out = shinesDown() ? -TractorBeamBlock.LENS_OUT : TractorBeamBlock.LENS_OUT;
        return Vec3.atCenterOf(worldPosition).add(0, out, 0);
    }

    /** The way its beam shines, among its own blocks: straight down or straight up. */
    public Vec3 way() {
        return new Vec3(0, shinesDown() ? -1 : 1, 0);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, TractorBeamBlockEntity beam) {
        if (level instanceof ServerLevel server && beam.lit()) {
            beam.carry(server);
        }
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, TractorBeamBlockEntity beam) {
        clientTicker.accept(beam);
    }

    /**
     * Switches it on or off, on the server, with its sound; a pad given is the one things then arrive on. Switched on
     * with the server config's {@code freighter.tractorBeam} off, it stays dark with a low buzz. Its pad lights with
     * it.
     */
    public void light(ServerLevel level, boolean on, @Nullable BlockPos padUsed) {
        if (on && !SkylinersConfig.FREIGHTER_TRACTOR_BEAM.get()) {
            refuse(level, worldPosition);
            return;
        }
        if (lit() == on) {
            return;
        }
        level.setBlock(worldPosition, getBlockState().setValue(TractorBeamBlock.LIT, on), Block.UPDATE_ALL);
        SoundEvent sound = on ? SkylinersRegistry.TRACTOR_BEAM_ON.get() : SkylinersRegistry.TRACTOR_BEAM_OFF.get();
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, SWITCH_VOLUME, 1.0F);
        if (on) {
            usePad(level, padUsed != null ? padUsed : nearestPad(level));
            length = -1;
        } else {
            letGoOfAll(level);
            usePad(level, null);
        }
    }

    /** Its low buzz, at a block among its own: it will not shine, or will not let something down. */
    private static void refuse(ServerLevel level, BlockPos at) {
        level.playSound(null, at, SkylinersRegistry.TRACTOR_BEAM_REFUSED.get(), SoundSource.BLOCKS, SWITCH_VOLUME, 1.0F);
    }

    /**
     * Takes a pad for things to arrive on, lighting it, and putting out the one it had unless another lit beam still
     * uses that; none for null.
     */
    private void usePad(ServerLevel level, @Nullable BlockPos next) {
        if (pad != null && !pad.equals(next) && !usedByAnother(level, pad)) {
            BeamPadBlock.show(level, pad, false);
        }
        pad = next;
        if (pad != null) {
            BeamPadBlock.show(level, pad, true);
        }
    }

    /** Whether another lit beam brings things up onto a pad. */
    private boolean usedByAnother(Level level, BlockPos at) {
        return loaded(level).stream().anyMatch(beam -> beam != this && beam.lit() && at.equals(beam.pad));
    }

    /** The pad nearest the beam within reach, among the same blocks; null for none. */
    @Nullable
    private BlockPos nearestPad(Level level) {
        return BeamPadBlockEntity.loaded(level).stream().map(BlockEntity::getBlockPos)
            .filter(at -> at.closerThan(worldPosition, PAD_REACH)).min(Comparator.comparingDouble(at -> at.distSqr(worldPosition)))
            .orElse(null);
    }

    /**
     * One tick of the lit beam: everything in it carried, everything stepping onto its pad let down, everything gone
     * from both let go of. Holding nothing, it looks only every few ticks.
     */
    private void carry(ServerLevel level) {
        if (!SkylinersConfig.FREIGHTER_TRACTOR_BEAM.get()) {
            light(level, false, null);
            return;
        }
        long now = level.getGameTime();
        if (length < 0 || now % LOOK_EVERY == 0) {
            look(level);
        }
        if (rides.isEmpty() && now % IDLE_LOOK_EVERY != 0) {
            return;
        }
        Shape beam = shape(level);
        Set<UUID> seen = new HashSet<>();
        for (Entity entity : riders(level, beam)) {
            seen.add(entity.getUUID());
            ride(level, entity, beam);
        }
        Vec3 padTop = padTop(level);
        if (padTop != null) {
            for (Entity entity : onPad(level, padTop)) {
                seen.add(entity.getUUID());
                stepOntoPad(level, entity, beam, padTop);
            }
        }
        forgetAllBut(level, seen);
    }

    /** Looks again for its pad, which may have gone, and for how far the beam reaches. */
    private void look(ServerLevel level) {
        if (pad != null && !(level.getBlockEntity(pad) instanceof BeamPadBlockEntity)) {
            pad = null;
        }
        if (pad == null) {
            usePad(level, nearestPad(level));
        }
        if (shinesDown()) {
            measureDown(level);
        } else {
            length = UP_REACH;
            safeFoot = true;
        }
    }

    /**
     * How far down the beam it is to the ground in the world, or to water or lava, up to its reach; and whether that
     * foot is somewhere to let things down onto: solid ground or water, not lava, land not loaded or open air past its
     * reach.
     */
    private void measureDown(ServerLevel level) {
        Vec3 from = SableFlight.toWorld(level, lens());
        Vec3 down = SableFlight.toWorld(level, lens().add(way())).subtract(from).normalize();
        for (double along = GROUND_STEP; along < DOWN_REACH; along += GROUND_STEP) {
            BlockPos at = BlockPos.containing(from.add(down.scale(along)));
            boolean loaded = level.isLoaded(at);
            FluidState fluid = level.getFluidState(at);
            if (!loaded || !fluid.isEmpty() || !level.getBlockState(at).getCollisionShape(level, at).isEmpty()) {
                length = along - GROUND_STEP / 2;
                safeFoot = loaded && !fluid.is(FluidTags.LAVA);
                return;
            }
        }
        length = DOWN_REACH;
        safeFoot = false;
    }

    /** The beam as it lies in the world now: aboard a ship in flight, where she holds it. */
    private Shape shape(ServerLevel level) {
        Vec3 from = SableFlight.toWorld(level, lens());
        Vec3 out = SableFlight.toWorld(level, lens().add(way())).subtract(from).normalize();
        Vec3 end = from.add(out.scale(length));
        return shinesDown() ? new Shape(end, from, out.reverse(), length) : new Shape(from, end, out, length);
    }

    /**
     * What rides the beam: whatever it carries standing in it, those it carries already first, then the nearest its
     * middle line, so a crowd under it never knocks off one on its way up.
     */
    private List<Entity> riders(ServerLevel level, Shape beam) {
        AABB round = new AABB(beam.foot(), beam.top()).inflate(HOLD_RADIUS, TOP_SLACK, HOLD_RADIUS);
        Comparator<Entity> order = Comparator.comparing((Entity entity) -> !rides.containsKey(entity.getUUID()))
            .thenComparingDouble(entity -> offMiddle(beam, entity.position()).lengthSqr());
        return level.getEntities((Entity) null, round, entity -> carries(entity) && standsIn(beam, entity)).stream()
            .sorted(order).limit(MOST_CARRIED).toList();
    }

    /**
     * Whether a thing's feet stand in the beam: within its width of its middle line, or a little more for a thing it
     * carries already, from its foot to just past its top.
     */
    private boolean standsIn(Shape beam, Entity entity) {
        Vec3 feet = entity.position();
        double along = alongBeam(beam, feet);
        double reach = rides.containsKey(entity.getUUID()) ? HOLD_RADIUS : RADIUS;
        boolean alongIt = along >= -TOP_SLACK && along <= beam.length() + TOP_SLACK;
        return alongIt && offMiddle(beam, feet).lengthSqr() <= reach * reach;
    }

    private static double alongBeam(Shape beam, Vec3 at) {
        return at.subtract(beam.foot()).dot(beam.up());
    }

    /** The way from the beam's middle line out to a point, square across it. */
    private static Vec3 offMiddle(Shape beam, Vec3 at) {
        Vec3 from = at.subtract(beam.foot());
        return from.subtract(beam.up().scale(from.dot(beam.up())));
    }

    /**
     * Whether the beam carries a thing: a player neither flying nor gliding on wings, an animal, a villager, a golem or
     * an allay, or a loose item or experience; never a hostile mob, a creature of the water, a boss, anything riding or
     * ridden, or anything living among a ship's blocks.
     */
    static boolean carries(Entity entity) {
        boolean free = entity.isAlive() && !entity.isPassenger() && !entity.isVehicle() && !entity.isSpectator();
        if (!free || SableFlight.inPlot(entity)) {
            return false;
        }
        if (entity instanceof ItemEntity || entity instanceof ExperienceOrb) {
            return true;
        }
        if (entity instanceof Player player) {
            return !player.getAbilities().flying && !player.isFallFlying();
        }
        return entity instanceof Mob mob && friendly(mob);
    }

    /**
     * Whether a mob is one the beam carries: one of the friendly kinds, and none that would come to harm out of water.
     */
    private static boolean friendly(Mob mob) {
        boolean harmed = mob instanceof Enemy || mob instanceof WaterAnimal || mob instanceof Axolotl
            || mob.getType().is(EntityTypeTags.AQUATIC);
        boolean kind = mob instanceof Animal || mob instanceof AbstractVillager || mob instanceof AbstractGolem || mob instanceof Allay;
        return kind && !harmed && !mob.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Carries a thing in the beam the way it rides it, lifting it if it has only now stepped in. */
    private void ride(ServerLevel level, Entity entity, Shape beam) {
        Ride ride = rides.get(entity.getUUID());
        if (ride == Ride.ON_PAD || ride == Ride.LANDED) {
            return;
        }
        if (ride == null) {
            ride = Ride.RISING;
            rides.put(entity.getUUID(), ride);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SkylinersRegistry.TRACTOR_BEAM_LIFT.get(),
                SoundSource.NEUTRAL, RIDE_VOLUME, pitchFor(entity));
        }
        double along = alongBeam(beam, entity.position());
        Vec3 off = offMiddle(beam, entity.position());
        entity.resetFallDistance();
        switch (ride) {
            case RISING -> rise(level, entity, along, off, beam);
            case HOLDING -> hold(entity, along, off, beam);
            case SINKING -> sink(entity, along, off);
            default -> { }
        }
    }

    /** A higher whoop for something small, a lower one for something big, as a thing is lifted off. */
    private static float pitchFor(Entity entity) {
        return (float) Math.max(0.7, Math.min(1.4, 1.3 - 0.25 * entity.getBbHeight()));
    }

    /** Lifts a thing toward the top, where it arrives once its head is there. */
    private void rise(ServerLevel level, Entity entity, double along, Vec3 off, Shape beam) {
        if (along + entity.getBbHeight() < beam.length() - ARRIVE_GAP) {
            carryAt(entity, off, RISE_SPEED, RISE_LEVITATION);
            return;
        }
        Vec3 padTop = padTop(level);
        if (padTop != null && roomOnPad(level)) {
            arrive(level, entity, padTop);
        } else {
            rides.put(entity.getUUID(), Ride.HOLDING);
            hold(entity, along, off, beam);
        }
    }

    /** Floats a thing at the top of the beam: a player bobbing there, rising slowly and falling slowly in turn. */
    private static void hold(Entity entity, double along, Vec3 off, Shape beam) {
        if (!(entity instanceof Player player)) {
            carryAt(entity, off, 0, FLOAT_LEVITATION);
        } else if (along + entity.getBbHeight() < beam.length() - ARRIVE_GAP) {
            renew(player, MobEffects.LEVITATION, FLOAT_LEVITATION);
        } else {
            player.removeEffect(MobEffects.LEVITATION);
            renew(player, MobEffects.SLOW_FALLING, 0);
        }
    }

    /**
     * Lets a thing down the beam until it lands at its foot: standing on something there, not on the pad it was just
     * let down from, which a player's own game may still say they stand on for a tick or two.
     */
    private void sink(Entity entity, double along, Vec3 off) {
        if (entity.onGround() && along < LANDED_WITHIN) {
            rides.put(entity.getUUID(), Ride.LANDED);
            return;
        }
        if (entity instanceof Player player) {
            renew(player, MobEffects.SLOW_FALLING, 0);
            return;
        }
        carryAt(entity, off, -SINK_SPEED, 0);
    }

    /**
     * Carries a thing along the beam at a speed, drawing it in toward its middle line: a player by levitation, which
     * their own game moves them by smoothly, given the amplifier given; anything else by its motion, made up for the
     * gravity it falls by each tick.
     */
    private static void carryAt(Entity entity, Vec3 off, double speed, int levitation) {
        if (entity instanceof Player player) {
            renew(player, MobEffects.LEVITATION, levitation);
            return;
        }
        double fall = entity instanceof LivingEntity ? 0 : entity.getGravity();
        entity.setDeltaMovement(-off.x * CENTRE_PULL, speed + fall, -off.z * CENTRE_PULL);
        entity.hurtMarked = true;
    }

    /** Gives a player an effect for a little while, unseen, again only once it has nearly run out. */
    private static void renew(Player player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance had = player.getEffect(effect);
        if (had == null || had.getAmplifier() != amplifier || had.getDuration() < EFFECT_RENEWED_BELOW) {
            player.addEffect(new MobEffectInstance(effect, EFFECT_TICKS, amplifier, true, false, false));
        }
    }

    /** Where a thing stands on the beam's pad, in the world: the middle of its top; null with no pad. */
    @Nullable
    private Vec3 padTop(ServerLevel level) {
        return pad == null ? null : SableFlight.toWorld(level, Vec3.atBottomCenterOf(pad.above()));
    }

    /** Whether the pad has room for someone to stand on it: nothing solid in the two blocks over it. */
    private boolean roomOnPad(ServerLevel level) {
        return pad != null && level.getBlockState(pad.above()).getCollisionShape(level, pad.above()).isEmpty()
            && level.getBlockState(pad.above(2)).getCollisionShape(level, pad.above(2)).isEmpty();
    }

    /** Brings a thing up onto the pad, with a sparkle and a chime. */
    private void arrive(ServerLevel level, Entity entity, Vec3 padTop) {
        rides.put(entity.getUUID(), Ride.ON_PAD);
        moveTo(entity, padTop);
        level.playSound(null, pad, SkylinersRegistry.TRACTOR_BEAM_ARRIVE.get(), SoundSource.NEUTRAL, RIDE_VOLUME, pitchFor(entity));
        sparkle(level, padTop, entity.getBbHeight());
    }

    /** Whatever stands on the pad: things the beam carries, in the world, their feet on its top round its middle. */
    private List<Entity> onPad(ServerLevel level, Vec3 padTop) {
        AABB over = new AABB(padTop, padTop).inflate(ON_PAD, 0, ON_PAD).expandTowards(0, PAD_HEADROOM, 0).move(0, -0.2, 0);
        return level.getEntities((Entity) null, over,
            entity -> carries(entity) && entity.position().subtract(padTop).horizontalDistance() <= ON_PAD);
    }

    /**
     * Something on the pad that has stepped onto it, rather than arrived on it or come back down the beam onto it, is
     * let down: put at the top of the beam, coming down from there, with a falling whoop and a sparkle. Where the
     * beam's foot is nowhere to let it down onto, the pad buzzes instead and leaves it be until it steps off.
     */
    private void stepOntoPad(ServerLevel level, Entity entity, Shape beam, Vec3 padTop) {
        if (rides.containsKey(entity.getUUID())) {
            return;
        }
        if (!safeFoot) {
            rides.put(entity.getUUID(), Ride.ON_PAD);
            refuse(level, pad);
            return;
        }
        double from = Math.max(0, beam.length() - ARRIVE_GAP - entity.getBbHeight());
        rides.put(entity.getUUID(), Ride.SINKING);
        level.playSound(null, pad, SkylinersRegistry.TRACTOR_BEAM_DROP.get(), SoundSource.NEUTRAL, RIDE_VOLUME, pitchFor(entity));
        sparkle(level, padTop, entity.getBbHeight());
        moveTo(entity, beam.foot().add(beam.up().scale(from)));
        if (entity instanceof LivingEntity living) {
            SableFlight.letGo(living);
        }
    }

    /** Puts a thing somewhere in the world, still and unhurt, its lift let go of. */
    private static void moveTo(Entity entity, Vec3 at) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.resetFallDistance();
        if (entity instanceof Player player) {
            player.removeEffect(MobEffects.LEVITATION);
        }
        entity.teleportTo(at.x, at.y, at.z);
        entity.hurtMarked = true;
    }

    /** A burst of sparkles round a thing as tall as given standing at a point in the world, seen by whoever is near. */
    private static void sparkle(ServerLevel level, Vec3 at, double tall) {
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + tall / 2, at.z, SPARKLES, 0.35, tall / 2, 0.35, 0.02);
    }

    /** Lets go of everything it holds that was not seen in the beam nor on its pad this tick, and forgets it. */
    private void forgetAllBut(ServerLevel level, Set<UUID> seen) {
        List<UUID> gone = rides.keySet().stream().filter(id -> !seen.contains(id)).toList();
        for (UUID id : gone) {
            letGo(level, id, rides.remove(id));
        }
    }

    /** Lets go of a thing it held, falling slowly a while if it was being carried. */
    private static void letGo(ServerLevel level, UUID id, Ride ride) {
        boolean carried = ride == Ride.RISING || ride == Ride.HOLDING || ride == Ride.SINKING;
        if (carried && level.getEntity(id) instanceof LivingEntity living) {
            living.removeEffect(MobEffects.LEVITATION);
            living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SAFE_FALL_TICKS, 0, true, false, true));
            living.resetFallDistance();
        }
    }

    /** As it goes dark: lets go of everything it holds, gently. */
    private void letGoOfAll(ServerLevel level) {
        rides.forEach((id, ride) -> letGo(level, id, ride));
        rides.clear();
    }

    /** Taken away while lit: lets go of everything it holds and puts its pad out. */
    void goneWhileLit(ServerLevel level) {
        letGoOfAll(level);
        usePad(level, null);
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
}
