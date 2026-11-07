package dev.skyliners.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.LaserBolt;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The laser crossbow, the freighter's own weapon, our own design: a gunmetal receiver on a dark-wood stock, two swept
 * arms tipped with emitters and an amber string of light between them, and a glowing coil round its prod. Hold use to
 * charge it, a ratchet and a rising whine, its coil glowing brighter, until it clicks, and let go: it is charged, its
 * string drawn back and glowing near white, its lamps green; use it again to fire an amber bolt the way its holder
 * looks (see {@link LaserFire}), which strikes hostile mobs hard and throws them back, in a shower of sparks wherever
 * it lands. It never breaks, burns or moves a block, and never hurts players, villagers, pets or animals. Three quick
 * shots and it vents steam a few seconds, its lamps red, before it charges again. The server config's
 * {@code freighter.laserCrossbow} switches it off.
 *
 * <p>Its charge and its heat are kept on its stack. Its holder's client works both out at once, as the server does, as
 * it is let go charged and as it fires, so that it is drawn charged without waiting for the server to say so, and is
 * told through {@link #clientFired}, which the client sets, the moment it fires, to kick it back then (see
 * {@code client/LaserCrossbowHands}): the game's time, which the server keeps, runs unevenly on a client.
 */
public class LaserCrossbowItem extends Item {
    /**
     * How long it takes to charge, in ticks, held; and how long it may be held, in ticks, longer than anyone holds it,
     * so it charges only once let go, as the game's crossbow does, and is not fired again by the use key held down.
     */
    private static final int CHARGE_TICKS = 20;
    private static final int HELD_AT_MOST = 72000;
    /** How far its bolt reaches, in blocks. */
    private static final double RANGE = 48;
    /** How hard its bolt strikes a hostile mob, in half hearts, and how hard it throws it back. */
    private static final float DAMAGE = 7;
    private static final double THROW = 1.2;
    /**
     * How many shots in a row heat it up to venting, how soon after the last a shot counts as in a row, and how long it
     * vents, in ticks.
     */
    public static final int MOST_HEAT = 3;
    private static final int IN_A_ROW = 80;
    private static final int VENT_TICKS = 80;
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
    /** How loud its shot, its charging and its venting are, as shares of full. */
    private static final float SHOT_VOLUME = 1.0F;
    private static final float CHARGE_VOLUME = 0.9F;
    private static final float VENT_VOLUME = 0.6F;
    /**
     * How high its venting steam hisses, as a share of the steam's own pitch: higher than the ramp's, a smaller valve.
     */
    private static final float VENT_PITCH = 1.4F;

    /**
     * How hot it is: how many shots it has fired in a row, as far as {@link #MOST_HEAT}, at which it vents, and the
     * game tick of the last, from which it cools.
     */
    public record Heat(int shots, long at) {
        public static final Heat COLD = new Heat(0, 0);
        public static final Codec<Heat> CODEC = RecordCodecBuilder.create(heat -> heat.group(
            Codec.INT.fieldOf("shots").forGetter(Heat::shots),
            Codec.LONG.fieldOf("at").forGetter(Heat::at)
        ).apply(heat, Heat::new));
        public static final StreamCodec<ByteBuf, Heat> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Heat::shots, ByteBufCodecs.VAR_LONG, Heat::at, Heat::new);
    }

    /**
     * Set by the client, on its own thread: its own player has fired a laser crossbow from a hand. Nothing anywhere
     * else, as on a dedicated server.
     */
    public static Consumer<InteractionHand> clientFired = hand -> { };

    public LaserCrossbowItem(Properties properties) {
        super(properties);
    }

    /** Whether it is charged, ready to fire. */
    public static boolean charged(ItemStack stack) {
        return stack.getOrDefault(SkylinersRegistry.LASER_CROSSBOW_CHARGED.get(), false);
    }

    /** How many shots it has fired in a row, as far as {@link #MOST_HEAT}, at which it vents. */
    public static int heat(ItemStack stack) {
        return stack.getOrDefault(SkylinersRegistry.LASER_CROSSBOW_HEAT.get(), Heat.COLD).shots();
    }

    /** The game tick of its last shot, while it has not cooled since; 0 once cool. */
    public static long lastShot(ItemStack stack) {
        return stack.getOrDefault(SkylinersRegistry.LASER_CROSSBOW_HEAT.get(), Heat.COLD).at();
    }

    /**
     * How far it is through charging, so far through this tick, from 0 to 1 once it may be let go, while someone is
     * holding use with it; 0 otherwise.
     */
    public static float charging(ItemStack stack, @Nullable LivingEntity user, float partialTick) {
        if (user == null || !user.isUsingItem() || user.getUseItem() != stack) {
            return 0;
        }
        float held = user.getTicksUsingItem() + partialTick;
        return Math.min(1, held / CHARGE_TICKS);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return HELD_AT_MOST;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.CROSSBOW;
    }

    /**
     * Used: charged, it fires, and is spent on its holder's client too at once, which kicks it back, the server's own
     * stack then sent to put the client right should it not have been charged there; venting or switched off, it only
     * hisses or clicks; otherwise it starts charging, as long as use is held. It is never swung as it fires, as the
     * game's own crossbow is not.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SkylinersConfig.FREIGHTER_LASER_CROSSBOW.get() || heat(stack) >= MOST_HEAT) {
            refuse(level, player, heat(stack) >= MOST_HEAT);
            return InteractionResultHolder.fail(stack);
        }
        if (charged(stack)) {
            if (level instanceof ServerLevel server) {
                fire(server, player);
            } else {
                clientFired.accept(hand);
            }
            spend(level, player, stack);
            return InteractionResultHolder.consume(stack);
        }
        if (!level.isClientSide) {
            sound(level, player, SkylinersRegistry.LASER_CROSSBOW_RELOAD.get(), CHARGE_VOLUME, 1.0F);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    /** Held long enough to charge, it clicks, to say it may be let go. */
    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        if (!level.isClientSide && HELD_AT_MOST - remaining == CHARGE_TICKS) {
            sound(level, user, SoundEvents.CROSSBOW_LOADING_END.value(), CHARGE_VOLUME, 1.0F);
        }
    }

    /**
     * Let go after it clicked: it is charged. Let go too soon for the server, which may count a tick or two fewer than
     * its holder's client did, the client, which charges it itself, is told how it stands.
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        if (HELD_AT_MOST - remaining >= CHARGE_TICKS) {
            stack.set(SkylinersRegistry.LASER_CROSSBOW_CHARGED.get(), true);
        } else if (user instanceof ServerPlayer player) {
            player.inventoryMenu.sendAllDataToRemote();
        }
    }

    /** The hiss of a crossbow venting, or the click of one switched off. */
    private static void refuse(Level level, Player player, boolean venting) {
        if (!level.isClientSide) {
            sound(level, player, venting ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.DISPENSER_FAIL, VENT_VOLUME, 1.0F);
        }
    }

    /**
     * Fires its bolt: whatever it lands on struck, hostile mobs only hurt and thrown back; the bolt drawn for everyone
     * near, leaving the crossbow in its holder's hands, sparks where it lands, its shot heard.
     */
    private static void fire(ServerLevel level, Player player) {
        Vec3 eye = player.getEyePosition();
        HitResult hit = LaserFire.trace(level, player, eye, RANGE);
        Vec3 look = player.getLookAngle();
        Vec3 right = look.cross(new Vec3(0, 1, 0)).normalize();
        Vec3 muzzle = eye.add(look.scale(MUZZLE_AHEAD)).add(right.scale(MUZZLE_RIGHT)).add(0, -MUZZLE_DOWN, 0);
        LivingEntity hurt = LaserFire.strike(level, player, muzzle, hit, DAMAGE, LaserBolt.AMBER, BOLTS_SEEN, player);
        if (hurt != null) {
            Vec3 away = hit.getLocation().subtract(eye).normalize();
            hurt.knockback(THROW, -away.x, -away.z);
        }
        sound(level, player, SkylinersRegistry.LASER_CROSSBOW_SHOT.get(), SHOT_VOLUME, 0.95F + level.getRandom().nextFloat() * 0.1F);
    }

    /**
     * Fired, on the server and its holder's client alike: it is spent, and a shot hotter, venting after the last of
     * them, with a hiss of steam.
     */
    private static void spend(Level level, Player player, ItemStack stack) {
        stack.set(SkylinersRegistry.LASER_CROSSBOW_CHARGED.get(), false);
        long now = level.getGameTime();
        Heat before = stack.getOrDefault(SkylinersRegistry.LASER_CROSSBOW_HEAT.get(), Heat.COLD);
        int shots = now - before.at() <= IN_A_ROW ? before.shots() + 1 : 1;
        stack.set(SkylinersRegistry.LASER_CROSSBOW_HEAT.get(), new Heat(shots, now));
        if (shots >= MOST_HEAT && !level.isClientSide) {
            sound(level, player, SkylinersRegistry.RAMP_STEAM.get(), VENT_VOLUME, VENT_PITCH);
        }
    }

    /** A sound from where its holder's eyes are, heard by everyone near. */
    private static void sound(Level level, Entity holder, SoundEvent sound, float volume, float pitch) {
        level.playSound(null, holder.getX(), holder.getEyeY(), holder.getZ(), sound, SoundSource.PLAYERS, volume, pitch);
    }

    /**
     * Each tick it is carried, on the server: it cools, its heat gone once it has rested long enough since its last
     * shot, longer after venting, steam puffing from it as it vents.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity holder, int slot, boolean selected) {
        Heat heat = stack.getOrDefault(SkylinersRegistry.LASER_CROSSBOW_HEAT.get(), Heat.COLD);
        if (heat.shots() == 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        long since = server.getGameTime() - heat.at();
        if (since >= (heat.shots() >= MOST_HEAT ? VENT_TICKS : IN_A_ROW)) {
            stack.set(SkylinersRegistry.LASER_CROSSBOW_HEAT.get(), Heat.COLD);
        } else if (heat.shots() >= MOST_HEAT && since % STEAM_EVERY == 0) {
            server.sendParticles(ParticleTypes.CLOUD, holder.getX(), holder.getEyeY() - MUZZLE_DOWN, holder.getZ(), STEAM_PUFFS, 0.2, 0.1, 0.2, 0.02);
        }
    }

    /** Swapped about in hand as it charges or cools, it is not drawn swinging up again each time. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack before, ItemStack after, boolean slotChanged) {
        return slotChanged || !before.is(after.getItem());
    }
}
