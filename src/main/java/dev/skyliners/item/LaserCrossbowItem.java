package dev.skyliners.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LaserBolt;
import dev.skyliners.compat.SableFlight;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The laser crossbow, the freighter's own weapon, our own design: a gunmetal receiver on a dark-wood stock, two swept
 * arms tipped with emitters and an amber string of light drawn back between them, and a glowing coil round its prod.
 * Each press of use fires at once, and held down it keeps firing, five shots a second: a short amber bolt the way its
 * holder looks, flying fast enough to follow by eye (see {@link LaserFire#fly}), which strikes hostile mobs and throws
 * them back, bursting into sparks with a sizzle wherever it lands, or fading out at the end of its range. It never
 * breaks, burns or moves a block, and never hurts players, villagers, pets or animals. Each shot heats it and it cools
 * as it rests, its coil glowing hotter and its lamps turning amber; fired too long without a rest it overheats and vents
 * steam a couple of seconds, its lamps red and its string slack, before it fires again. The server config's
 * {@code freighter.laserCrossbow} switches it off.
 *
 * <p>Its heat is kept on its stack. Its holder's client works it out at once, as the server does, as it fires, and is
 * told through {@link #clientFired}, which the client sets, the moment it fires, to kick it back then (see
 * {@code client/LaserCrossbowHands}): the game's time, which the server keeps, runs unevenly on a client.
 */
public class LaserCrossbowItem extends Item {
    /** How far its bolt reaches, in blocks. */
    private static final double RANGE = 40;
    /** How hard its bolt strikes a hostile mob, in half hearts, and how hard it throws it back. */
    private static final float DAMAGE = 4;
    private static final double THROW = 0.5;
    /**
     * The least time between two shots, in ticks: under the four ticks the game repeats use at while it is held, so
     * held down it fires at that pace, a tick of the server's and the client's clocks apart allowed for.
     */
    private static final int FIRE_GAP = 3;
    /**
     * How hot it may get before it vents, in shots' worth of heat, how many ticks each shot's worth takes to cool, and
     * how long it vents, in ticks: held down it cools half as fast as it heats, so it vents after some four seconds of
     * firing, and fired at a steady two shots a second it never does.
     */
    public static final int MOST_HEAT = 10;
    private static final double COOL_TICKS = 8;
    private static final int VENT_TICKS = 50;
    /**
     * Where its bolt leaves from, from its holder's eyes: so far ahead along their look, so far to their right and so
     * far down, in blocks, near the crossbow's muzzle as it is held.
     */
    private static final double MUZZLE_AHEAD = 0.6;
    private static final double MUZZLE_RIGHT = 0.25;
    private static final double MUZZLE_DOWN = 0.2;
    /** How far off its bolts are seen, in blocks. */
    private static final double BOLTS_SEEN = 96;
    /** How often it puffs steam as it vents, in ticks, and how many puffs. */
    private static final int STEAM_EVERY = 5;
    private static final int STEAM_PUFFS = 3;
    /** How loud its shot and its venting are, as shares of full. */
    private static final float SHOT_VOLUME = 0.9F;
    private static final float VENT_VOLUME = 0.6F;
    /**
     * How high its shot is pitched at the lowest and how much higher it may be, at random, as shares of its own pitch,
     * so a quick run of shots chirps rather than drones.
     */
    private static final float SHOT_PITCH = 0.88F;
    private static final float SHOT_PITCH_SPREAD = 0.24F;
    /**
     * How high its venting steam hisses, as a share of the steam's own pitch: higher than the ramp's, a smaller valve.
     */
    private static final float VENT_PITCH = 1.4F;

    /**
     * How hot it is: how many shots' worth of heat it held at its last shot, {@link #MOST_HEAT} once it vents, and the
     * game tick of that shot, from which it cools. A crossbow saved before it fired without charging kept a count of
     * shots instead, which is left unread, so it loads cool.
     */
    public record Heat(float level, long at) {
        public static final Heat COLD = new Heat(0, 0);
        public static final Codec<Heat> CODEC = RecordCodecBuilder.create(heat -> heat.group(
            Codec.FLOAT.optionalFieldOf("level", 0F).forGetter(Heat::level),
            Codec.LONG.fieldOf("at").forGetter(Heat::at)
        ).apply(heat, Heat::new));
        public static final StreamCodec<ByteBuf, Heat> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Heat::level, ByteBufCodecs.VAR_LONG, Heat::at, Heat::new);
    }

    /**
     * Set by the client, on its own thread: its own player has fired a laser crossbow from a hand. Nothing anywhere
     * else, as on a dedicated server.
     */
    public static Consumer<InteractionHand> clientFired = hand -> { };

    public LaserCrossbowItem(Properties properties) {
        super(properties);
    }

    private static Heat heatOf(ItemStack stack) {
        return stack.getOrDefault(SkylinersRegistry.LASER_CROSSBOW_HEAT.get(), Heat.COLD);
    }

    /**
     * How hot it is at a game tick, in shots' worth of heat: cooling steadily since its last shot, held at
     * {@link #MOST_HEAT} while it vents and cool once it has vented.
     */
    public static double heat(ItemStack stack, long now) {
        Heat heat = heatOf(stack);
        long since = Math.max(0, now - heat.at());
        if (heat.level() >= MOST_HEAT) {
            return since < VENT_TICKS ? MOST_HEAT : 0;
        }
        return Math.max(0, heat.level() - since / COOL_TICKS);
    }

    /** Whether it vents at a game tick, holding its fire. */
    public static boolean venting(ItemStack stack, long now) {
        Heat heat = heatOf(stack);
        return heat.level() >= MOST_HEAT && now - heat.at() < VENT_TICKS;
    }

    /** How much of its venting is still to come at a game tick, from 1 as it starts to 0 once it is done. */
    public static double ventLeft(ItemStack stack, long now) {
        if (!venting(stack, now)) {
            return 0;
        }
        return 1 - Math.max(0, now - heatOf(stack).at()) / (double) VENT_TICKS;
    }

    /** The game tick of its last shot; 0 before it has fired, or once it has cooled. */
    public static long lastShot(ItemStack stack) {
        return heatOf(stack).at();
    }

    /**
     * Used: it fires, at once, on the server and on its holder's client alike, which kicks it back, the server's own
     * stack then sent to put the client right; venting, or fired again too soon, it holds its fire; switched off, it only
     * clicks. It is never swung as it fires, as the game's own crossbow is not.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long now = level.getGameTime();
        if (!SkylinersConfig.FREIGHTER_LASER_CROSSBOW.get()) {
            if (!level.isClientSide) {
                sound(level, player, SoundEvents.DISPENSER_FAIL, VENT_VOLUME, 1.0F);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (venting(stack, now) || now - lastShot(stack) < FIRE_GAP) {
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel server) {
            fire(server, player);
        } else {
            clientFired.accept(hand);
        }
        spend(level, player, stack, now);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * Fires its bolt from its muzzle the way its holder looks, drawn for everyone near flying out of the crossbow in
     * its holder's hands; a hostile mob it is to strike hurt and thrown back once it gets there, and a ship whose block
     * it is to strike rocked a little; its shot heard, a little higher or lower each time.
     */
    private static void fire(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        HitResult hit = LaserFire.trace(level, player, eye, look, RANGE, entity -> true);
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 muzzle = eye.add(look.scale(MUZZLE_AHEAD)).add(right.scale(MUZZLE_RIGHT)).add(0, -MUZZLE_DOWN, 0);
        int ticks = LaserFire.fly(level, player, muzzle, hit, LaserBolt.AMBER, BOLTS_SEEN);
        LivingEntity target = LaserFire.struck(hit);
        Vec3 way = hit.getLocation().subtract(eye).normalize();
        if (target != null) {
            BoltsInFlight.launch(level, player, target, ticks, DAMAGE, THROW, way);
        }
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            BoltsInFlight.launchAt(level, player, block, ticks, way);
        }
        float pitch = SHOT_PITCH + level.getRandom().nextFloat() * SHOT_PITCH_SPREAD;
        sound(level, player, SkylinersRegistry.LASER_CROSSBOW_SHOT.get(), SHOT_VOLUME, pitch);
    }

    /**
     * Fired, on the server and its holder's client alike: a shot's worth hotter, and once too hot it vents, its holder
     * then kept from using it until it has (the game's own cooldown, drawn over it in the hotbar), with a hiss of steam.
     */
    private void spend(Level level, Player player, ItemStack stack, long now) {
        double hotter = heat(stack, now) + 1;
        if (hotter < MOST_HEAT) {
            stack.set(SkylinersRegistry.LASER_CROSSBOW_HEAT.get(), new Heat((float) hotter, now));
            return;
        }
        stack.set(SkylinersRegistry.LASER_CROSSBOW_HEAT.get(), new Heat(MOST_HEAT, now));
        player.getCooldowns().addCooldown(this, VENT_TICKS);
        if (!level.isClientSide) {
            sound(level, player, SkylinersRegistry.RAMP_STEAM.get(), VENT_VOLUME, VENT_PITCH);
        }
    }

    /** A sound from where its holder's eyes are, heard by everyone near. */
    private static void sound(Level level, Entity holder, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, holder.getX(), holder.getEyeY(), holder.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * Each tick it is carried, on the server: steam puffing from it as it vents, carried along with any ship its holder
     * rides; its heat forgotten once it has cooled; and the charge a crossbow saved before it fired without charging may
     * still hold let go.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        if (stack.has(SkylinersRegistry.LASER_CROSSBOW_CHARGED.get())) {
            stack.remove(SkylinersRegistry.LASER_CROSSBOW_CHARGED.get());
        }
        long now = server.getGameTime();
        if (heatOf(stack).level() == 0) {
            return;
        }
        if (venting(stack, now) && now % STEAM_EVERY == 0) {
            Vec3 puff = SableFlight.toRidden(holder, new Vec3(holder.getX(), holder.getEyeY() - MUZZLE_DOWN, holder.getZ()));
            server.sendParticles(ParticleTypes.CLOUD, puff.x, puff.y, puff.z, STEAM_PUFFS, 0.2, 0.1, 0.2, 0.02);
        } else if (heat(stack, now) == 0) {
            stack.set(SkylinersRegistry.LASER_CROSSBOW_HEAT.get(), Heat.COLD);
        }
    }

    /** Swapped about in hand as it heats or cools, it is not drawn swinging up again each time. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack before, ItemStack after, boolean slotChanged) {
        return slotChanged || !before.is(after.getItem());
    }
}
