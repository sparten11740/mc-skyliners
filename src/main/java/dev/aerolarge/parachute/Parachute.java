package dev.aerolarge.parachute;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.block.LandingTargetBlockEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * What a parachute does. Worn and packed, it opens when its wearer jumps while falling (the client asks, see
 * {@link OpenParachute}), or by itself once the ground is close and the wearer is still falling free. Open, it caps
 * the fall to a gentle sink, lets the wearer drift the way they walk, and saves them the fall; it packs itself away
 * again on landing, in water or on anything that carries them, and a landing near a landing target is scored.
 * The movement runs on both sides, since each client moves its own player.
 */
public final class Parachute {
    /** The sink under an open canopy, in blocks a tick, and how quickly a fall slows to it, as a share a tick. */
    private static final double SINK = 0.12;
    private static final double BRAKING = 0.3;
    /** What the air leaves of a falling player's speed each tick, as the game moves them. */
    private static final double AIR_DRAG = 0.98;
    /** Drift added per tick toward where the wearer walks, the fastest drift, and how it dies down without input. */
    private static final double STEER = 0.02;
    private static final double MAX_DRIFT = 0.35;
    private static final double DRIFT_DAMPING = 0.96;
    /** Opens by itself once the wearer has fallen this far and the ground is this close below. */
    private static final double AUTO_OPEN_FALL = 8;
    private static final int AUTO_OPEN_HEIGHT = 30;
    /** Falling this far counts as falling, for opening it by jumping. */
    private static final double FALLING = 1.5;

    private Parachute() {
    }

    /** The parachute the player wears, or an empty stack. */
    public static ItemStack worn(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return chest.is(AerolargeRegistry.PARACHUTE.get()) ? chest : ItemStack.EMPTY;
    }

    public static boolean isOpen(Player player) {
        return worn(player).getOrDefault(AerolargeRegistry.PARACHUTE_OPEN.get(), false);
    }

    /** Whether the player wears a packed parachute and is falling free, so jumping would open it. */
    public static boolean canOpen(Player player) {
        return !worn(player).isEmpty() && !isOpen(player) && !player.onGround() && !player.isInWater() && !player.isFallFlying()
            && !player.getAbilities().flying && !player.isPassenger() && player.fallDistance > FALLING;
    }

    /** Opens the player's parachute, with the snap of the canopy filling. */
    public static void open(Player player) {
        worn(player).set(AerolargeRegistry.PARACHUTE_OPEN.get(), true);
        player.level().playSound(null, player.blockPosition(), SoundEvents.ARMOR_EQUIP_ELYTRA.value(), SoundSource.PLAYERS, 1.0F, 0.7F);
    }

    /**
     * On the server: a client's player jumped while falling. Its client has opened the parachute already; if it cannot
     * open here (the server's fall lags the client's), the client is sent the packed one back.
     */
    public static void handle(OpenParachute payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (canOpen(player)) {
            open(player);
        } else {
            player.inventoryMenu.sendAllDataToRemote();
        }
    }

    /** Every tick, on both sides, before the player moves. */
    public static void tick(PlayerTickEvent.Pre event) {
        Player player = event.getEntity();
        ItemStack parachute = worn(player);
        if (parachute.isEmpty()) {
            return;
        }
        boolean server = !player.level().isClientSide;
        if (!isOpen(player)) {
            if (server && canOpen(player) && player.fallDistance > AUTO_OPEN_FALL && nearGround(player)) {
                open(player);
            }
            return;
        }
        if (player.onGround() || player.isInWater() || player.isInLava() || player.isFallFlying() || player.getAbilities().flying || player.isPassenger()) {
            if (server) {
                parachute.set(AerolargeRegistry.PARACHUTE_OPEN.get(), false);
                if (player.onGround() && player instanceof ServerPlayer landed) {
                    LandingTargetBlockEntity.scoreLanding(landed);
                }
            }
            return;
        }
        player.fallDistance = 0;
        Vec3 motion = player.getDeltaMovement();
        // Gravity and air still act this tick; braked toward the speed they leave at just the canopy's sink.
        double floor = -SINK / AIR_DRAG + player.getGravity();
        double down = motion.y < floor ? motion.y + (floor - motion.y) * BRAKING : motion.y;
        double x = motion.x * DRIFT_DAMPING;
        double z = motion.z * DRIFT_DAMPING;
        if (player.level().isClientSide) {
            // Steered the way the player walks: forward and back, and sideways.
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            double sin = Mth.sin(yaw);
            double cos = Mth.cos(yaw);
            x += (player.xxa * cos - player.zza * sin) * STEER;
            z += (player.zza * cos + player.xxa * sin) * STEER;
        }
        double drift = Math.hypot(x, z);
        if (drift > MAX_DRIFT) {
            x *= MAX_DRIFT / drift;
            z *= MAX_DRIFT / drift;
        }
        player.setDeltaMovement(x, down, z);
    }

    private static boolean nearGround(Player player) {
        int ground = player.level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(player.getX()), Mth.floor(player.getZ()));
        return player.getY() - ground < AUTO_OPEN_HEIGHT;
    }
}
