package dev.skyliners.zerog;

import dev.skyliners.Skyliners;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Weightlessness, on the server, for whatever is in a room whose gravity switch is off (see
 * {@link GravitySwitchBlockEntity}). A player there weighs nothing: their own game moves them, so they keep a
 * modifier that takes all of their gravity away, which their game is told of, and it does the rest (the client's
 * {@code Weightless} gives them a push up or down as they jump or sneak and bounces them off walls); a jump from the
 * floor is a gentle push off it, and a landing never hurts. A mob that wanders in weighs nothing the same way, and the
 * server moves it: it rises gently off the floor, drifts, is nudged now and then and bounces slowly off whatever it
 * runs into, never faster than a slow drift. A dropped item does the same, made to fall no more, and marked so that
 * one saved while floating has its weight back as it is loaded again. Nothing is ever pushed into a block: whatever
 * moves, moves by the game's own collisions.
 *
 * <p>Let go of, as gravity comes back on, as it leaves the room or as its switch goes, everything has its weight back,
 * and whoever was floating falls slowly for a few seconds, unhurt. A player holds a lease on their weightlessness,
 * renewed every tick by the switch, so one whose switch is gone without a word (unloaded with them in another
 * dimension, or the world left) has their weight back all the same. None of it is saved.
 */
public final class ZeroGravity {
    /** The modifier that takes a floating player's or mob's gravity away, by which their own game knows they float. */
    public static final ResourceLocation WEIGHTLESS = id("zero_gravity");
    /** The modifier that makes a floating player's jump a gentle push off the floor, and its share of a jump kept. */
    private static final ResourceLocation SOFT_JUMP = id("zero_gravity_jump");
    private static final double JUMP_KEPT = 0.3;
    /** The modifier that lets a floating player or mob come down on anything unhurt, and how much further, in blocks. */
    private static final ResourceLocation SOFT_LANDING = id("zero_gravity_landing");
    private static final double SOFTER_LANDING = 16;
    /** How long a player's lease lasts unrenewed, in ticks: a switch renews it every tick. */
    private static final int LEASE = 10;
    /** How long whoever was floating falls slowly once let go of, in ticks: six seconds, down from any room's ceiling. */
    private static final int SLOW_FALL = 6 * 20;
    /** How fast a mob or an item rises off the floor as it starts to float, in blocks a tick. */
    private static final double LIFT = 0.06;
    /** The fastest a floating mob or item drifts, in blocks a tick. */
    private static final double DRIFT_MOST = 0.12;
    /** How often each floating mob or item is nudged, in ticks, and how hard at most, in blocks a tick. */
    private static final int NUDGE_EVERY = 30;
    private static final double NUDGE = 0.02;
    /**
     * A bounce off a wall, floor or ceiling: the share of its speed a mob or an item keeps, the way back, and the
     * slowest it must have been going to bounce, in blocks a tick; slower, it stops against it.
     */
    private static final double BOUNCE = 0.6;
    private static final double BOUNCE_FROM = 0.02;
    /** How near still a way it moved in must be to have been stopped there by something, in blocks a tick. */
    private static final double STOPPED = 1.0E-3;
    /** How many sparkles puff out round a player as they start to float. */
    private static final int SPARKLES = 16;
    /** The mark on a floating item, so one saved while floating has its weight back as it is loaded again. */
    private static final String FLOATING_MARK = Skyliners.MOD_ID + ":weightless";
    /** Each floating player's lease: the game time it runs to. */
    private static final Map<UUID, Long> LEASES = new HashMap<>();

    private ZeroGravity() {
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, path);
    }

    /**
     * Whether a thing floats in a weightless room: a player, not a spectator nor flying, gliding or seated; a mob, never
     * a boss, one riding or ridden, or one of the mod's own creatures, which keep to their rounds and their tanks; or a
     * dropped item.
     */
    static boolean floats(Entity entity) {
        boolean free = entity.isAlive() && !entity.isSpectator() && !entity.isPassenger() && !entity.isVehicle();
        if (!free) {
            return false;
        }
        if (entity instanceof Player player) {
            return !player.getAbilities().flying && !player.isFallFlying();
        }
        if (entity instanceof Mob mob) {
            boolean ours = EntityType.getKey(mob.getType()).getNamespace().equals(Skyliners.MOD_ID);
            return !ours && !mob.getType().is(Tags.EntityTypes.BOSSES) && !mob.getType().is(EntityTypeTags.AQUATIC);
        }
        return entity instanceof ItemEntity;
    }

    /** Whether a player floats now: whether they keep the modifier that takes their gravity away, on either side. */
    public static boolean weightless(Player player) {
        AttributeInstance gravity = player.getAttribute(Attributes.GRAVITY);
        return gravity != null && gravity.hasModifier(WEIGHTLESS);
    }

    /**
     * One tick of a thing in a weightless room, after it has moved: kept floating, as it only now starts to when it was
     * not before (the velocity it was left with last tick null). Gives the velocity it is left with now.
     */
    static Vec3 keepFloating(ServerLevel level, Entity entity, @Nullable Vec3 left, long now) {
        entity.resetFallDistance();
        if (entity instanceof ServerPlayer player) {
            LEASES.put(player.getUUID(), now + LEASE);
            if (lighten(player) && left == null) {
                level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(), SPARKLES, 0.3, 0.1, 0.3, 0.02);
            }
            return Vec3.ZERO;
        }
        if (entity instanceof LivingEntity living) {
            lighten(living);
        } else if (!entity.isNoGravity()) {
            entity.setNoGravity(true);
            entity.getPersistentData().putBoolean(FLOATING_MARK, true);
        }
        return drift(level.getRandom(), entity, left, now);
    }

    /**
     * A floating mob or item one tick on: lifted off the floor as it starts, bounced off what it ran into, nudged now
     * and then, and never let go faster than a slow drift. Its game is told only of a change.
     */
    private static Vec3 drift(RandomSource random, Entity entity, @Nullable Vec3 left, long now) {
        Vec3 was = entity.getDeltaMovement();
        Vec3 moving = left == null ? was.add(0, LIFT, 0) : bounced(entity, left, was);
        if ((now + entity.getId()) % NUDGE_EVERY == 0) {
            moving = moving.add(nudge(random), nudge(random), nudge(random));
        }
        if (moving.length() > DRIFT_MOST) {
            moving = moving.normalize().scale(DRIFT_MOST);
        }
        if (!moving.equals(was)) {
            entity.setDeltaMovement(moving);
            entity.hurtMarked = true;
        }
        return moving;
    }

    private static double nudge(RandomSource random) {
        return (random.nextDouble() * 2 - 1) * NUDGE;
    }

    /**
     * How a thing moves once it has bounced off whatever stopped it this tick: each way it was moving in, fast enough,
     * and now stopped, turned back and slowed.
     */
    private static Vec3 bounced(Entity entity, Vec3 left, Vec3 now) {
        double x = entity.horizontalCollision ? bounce(left.x, now.x) : now.x;
        double y = entity.verticalCollision ? bounce(left.y, now.y) : now.y;
        double z = entity.horizontalCollision ? bounce(left.z, now.z) : now.z;
        return new Vec3(x, y, z);
    }

    private static double bounce(double before, double after) {
        return Math.abs(before) > BOUNCE_FROM && Math.abs(after) < STOPPED ? -before * BOUNCE : after;
    }

    /**
     * Takes a living thing's gravity away, if it was not already, with a gentle jump and soft landings; whether it only
     * now came.
     */
    private static boolean lighten(LivingEntity living) {
        boolean came = modify(living, Attributes.GRAVITY, WEIGHTLESS, -1);
        modify(living, Attributes.JUMP_STRENGTH, SOFT_JUMP, JUMP_KEPT - 1);
        AttributeInstance landing = living.getAttribute(Attributes.SAFE_FALL_DISTANCE);
        if (landing != null && !landing.hasModifier(SOFT_LANDING)) {
            landing.addTransientModifier(new AttributeModifier(SOFT_LANDING, SOFTER_LANDING, AttributeModifier.Operation.ADD_VALUE));
        }
        return came;
    }

    /** Adds a share of a living thing's attribute, as a modifier, unless it is there already; whether it was added. */
    private static boolean modify(LivingEntity living, Holder<Attribute> attribute, ResourceLocation modifier, double share) {
        AttributeInstance instance = living.getAttribute(attribute);
        if (instance == null || instance.hasModifier(modifier)) {
            return false;
        }
        instance.addTransientModifier(new AttributeModifier(modifier, share, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        return true;
    }

    /**
     * Lets go of a thing that was floating: its weight back, and, alive, a few seconds of falling slowly, unhurt.
     * Nothing for a thing no longer about.
     */
    static void letGo(@Nullable Entity entity) {
        if (entity == null) {
            return;
        }
        entity.resetFallDistance();
        if (entity instanceof LivingEntity living) {
            LEASES.remove(living.getUUID());
            restore(living);
        } else if (entity.getPersistentData().getBoolean(FLOATING_MARK)) {
            entity.getPersistentData().remove(FLOATING_MARK);
            entity.setNoGravity(false);
        }
    }

    /** A living thing's weight back, falling slowly a while if it was floating. */
    private static void restore(LivingEntity living) {
        boolean was = unmodify(living, Attributes.GRAVITY, WEIGHTLESS);
        unmodify(living, Attributes.JUMP_STRENGTH, SOFT_JUMP);
        unmodify(living, Attributes.SAFE_FALL_DISTANCE, SOFT_LANDING);
        if (was) {
            living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL, 0, true, false, true));
        }
    }

    private static boolean unmodify(LivingEntity living, Holder<Attribute> attribute, ResourceLocation modifier) {
        AttributeInstance instance = living.getAttribute(attribute);
        return instance != null && instance.removeModifier(modifier);
    }

    /** Each tick of a floating player's, on the server: their weight back once their lease has run out. */
    public static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Long until = LEASES.get(player.getUUID());
        if (until != null && player.level().getGameTime() > until) {
            letGo(player);
        }
    }

    /** A player leaving the game leaves their lease too: its modifiers are never saved. */
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LEASES.remove(event.getEntity().getUUID());
    }

    /** An item saved while floating has its weight back as it is loaded again. */
    public static void onJoinLevel(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ItemEntity item && item.getPersistentData().getBoolean(FLOATING_MARK)) {
            letGo(item);
        }
    }
}
