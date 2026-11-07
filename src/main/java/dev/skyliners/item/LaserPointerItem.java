package dev.skyliners.item;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A laser pointer: held down, it shines a thin red beam the way its holder looks, with a bright red dot wherever it
 * lands. Caught in the face, someone looking back down the beam is dazzled: blinded for a few seconds after the beam
 * leaves them, a player with a flash of darkness as it first catches their eye, a mob losing whoever it was after and
 * stumbling about slowed. On the body, or from behind, it is only a dot. The server traces the beam and sends its
 * particles, so everyone nearby sees it.
 */
public class LaserPointerItem extends Item {
    /** How far the beam reaches, in blocks. */
    private static final double RANGE = 64.0;
    /** How far above or below the eyes the beam still counts as in the face, in blocks. */
    private static final double FACE = 0.35;
    /** How long the blindness lasts once the beam leaves the face, in ticks. */
    private static final int BLINDNESS_TICKS = 60;
    /** The flash of darkness a player sees as the beam first catches their eye, in ticks. */
    private static final int FLASH_TICKS = 40;
    /** How long a dazzled mob stumbles about slowed, in ticks. */
    private static final int DAZZLE_TICKS = 40;
    /** The gap between the specks along the beam, in blocks, kept wide so a long beam stays cheap. */
    private static final double BEAM_SPACING = 2.0;
    private static final Vector3f RED = new Vector3f(1.0F, 0.05F, 0.05F);
    private static final DustParticleOptions DOT = new DustParticleOptions(RED, 0.6F);
    private static final DustParticleOptions BEAM = new DustParticleOptions(RED, 0.25F);

    public LaserPointerItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.3F, 2.0F);
        }
        return ItemUtils.startUsingInstantly(level, player, hand);
    }

    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remainingUseDuration) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Vec3 eye = user.getEyePosition();
        HitResult hit = LaserFire.trace(server, user, eye, RANGE);
        Vec3 spot = hit.getLocation();
        shine(server, eye, spot, remainingUseDuration);
        if (hit instanceof EntityHitResult struck && struck.getEntity() instanceof LivingEntity target && inFace(target, spot, user.getLookAngle())) {
            dazzle(target, user);
        }
    }

    /**
     * Whether the beam lands in someone's face: near the height of their eyes, coming at them from in front, so a beam
     * on the back of the head does no harm.
     */
    private static boolean inFace(LivingEntity target, Vec3 spot, Vec3 beam) {
        if (Math.abs(spot.y - target.getEyeY()) > FACE) {
            return false;
        }
        return target.getViewVector(1.0F).dot(beam) < 0.0;
    }

    /**
     * Blinds whoever the beam is in the face of, renewed every tick it stays there. A player also sees a flash of
     * darkness as it first catches their eye; a mob, which blindness hardly slows, forgets whoever it was after and is
     * slowed as if it cannot see where it is going.
     */
    private static void dazzle(LivingEntity target, LivingEntity user) {
        if (target instanceof Player && !target.hasEffect(MobEffects.BLINDNESS)) {
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, FLASH_TICKS, 0, false, false), user);
        }
        target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, BLINDNESS_TICKS, 0, false, false), user);
        if (!(target instanceof Mob mob)) {
            return;
        }
        mob.setTarget(null);
        mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, DAZZLE_TICKS, 2, false, false), user);
    }

    /**
     * Draws the beam: the dot where it lands every tick, pulled a hair back towards the eyes so it is not lost inside a
     * block, and sparse specks along the way, shifted along a little each tick so the beam seems to shimmer.
     */
    private static void shine(ServerLevel level, Vec3 eye, Vec3 spot, int tick) {
        Vec3 path = spot.subtract(eye);
        double length = path.length();
        if (length < 1.0E-3) {
            return;
        }
        Vec3 along = path.scale(1.0 / length);
        Vec3 dot = spot.subtract(along.scale(0.05));
        level.sendParticles(DOT, dot.x, dot.y, dot.z, 1, 0.0, 0.0, 0.0, 0.0);
        double start = 1.0 + (tick % 4) * BEAM_SPACING / 4.0;
        for (double d = start; d < length - 0.5; d += BEAM_SPACING) {
            Vec3 speck = eye.add(along.scale(d));
            level.sendParticles(BEAM, speck.x, speck.y, speck.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }
}
