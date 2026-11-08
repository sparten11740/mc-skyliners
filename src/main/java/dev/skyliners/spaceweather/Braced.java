package dev.skyliners.spaceweather;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.parachute.SafetyCanopy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Everyone aboard a rocket or a freighter as a rock of the space weather strikes her (see {@link SpaceWeather}), kept a
 * while from coming to harm by it, however hard it throws her about: a giant asteroid can spin her round and round, and
 * someone flung across her cabin, into a wall, through a floor or clear of her altogether must still come out of it
 * unhurt. Nothing they fall from, no wall they are thrown into or through, hurts them while they are kept, and should
 * they be thrown clear of her, their safety canopy opens over them once they fall a few blocks (see
 * {@link SafetyCanopy#thrown}), or they float down slowly where the server keeps none. Only what the strike itself does
 * is kept from them: anything else that hurts them still does.
 */
public final class Braced {
    /**
     * How long someone thrown clear floats down slowly, in ticks, where the server keeps no safety canopy to open over
     * them instead; given again each tick they are kept.
     */
    private static final int FLOAT_DOWN_TICKS = 40;
    /** Who is kept from harm, and until what game time, on the server. */
    private static final Map<UUID, Long> UNTIL = new HashMap<>();

    private Braced() {
    }

    /** A player kept from harm by a strike until a game time, or later if they were already kept longer. */
    static void brace(ServerPlayer player, long until) {
        UNTIL.merge(player.getUUID(), until, Math::max);
    }

    /**
     * A tick of someone kept from harm: while they are still aboard her, their fall is forgotten, so a tumble across her
     * cabin never counts as one; and their safety canopy is kept ready to open should they be thrown clear, or, where
     * the server keeps none, they float down slowly.
     */
    static void keep(ServerPlayer player, boolean aboard) {
        if (aboard) {
            player.resetFallDistance();
        }
        if (SkylinersConfig.SAFETY_CANOPY.get()) {
            SafetyCanopy.thrown(player);
        } else if (!aboard) {
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, FLOAT_DOWN_TICKS, 0, false, false, true));
        }
    }

    /** Whether a player is kept from harm by a strike now; one whose time is up is let go. */
    public static boolean kept(LivingEntity entity) {
        Long until = UNTIL.get(entity.getUUID());
        if (until != null && entity.level().getGameTime() > until) {
            UNTIL.remove(entity.getUUID());
            return false;
        }
        return until != null;
    }

    /**
     * Harm about to come to someone: a fall, a wall they were thrown into or into which she was thrown round them,
     * kept from them while they are kept from harm by a strike.
     */
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity hurt = event.getEntity();
        if (hurt.level().isClientSide || !(hurt instanceof ServerPlayer) || !kept(hurt)) {
            return;
        }
        if (thrownAbout(event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** Whether harm is what being thrown about does: a fall, a wall flown into, or being squeezed into blocks. */
    private static boolean thrownAbout(DamageSource source) {
        return source.is(DamageTypeTags.IS_FALL) || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.FLY_INTO_WALL)
            || source.is(DamageTypes.CRAMMING);
    }

    /** As the server stops: nobody is kept in the next world opened. */
    public static void forget(ServerStoppedEvent event) {
        UNTIL.clear();
    }
}
