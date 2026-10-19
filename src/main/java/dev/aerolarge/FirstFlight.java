package dev.aerolarge;

import java.util.Locale;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The First Flight advancements: milestones of flying the airliner, from climbing away from the runway to a gentle
 * touchdown. Each has one criterion, "done", that nothing in the game triggers; the mod grants it where the moment
 * happens. The advancements themselves are data, under {@code first_flight/}.
 */
public enum FirstFlight {
    /** Tried to get up while the seatbelt signs were on. */
    BUCKLE_UP,
    /** Aboard an airliner as it climbed away from the runway. */
    CLEARED_FOR_TAKEOFF,
    /** Aboard an airliner touching down hardly sinking at all. */
    BUTTER_LANDING,
    /** Scored a basket aboard an airliner in flight. */
    SWISH,
    /** Landed a parachute within a block of a landing target. */
    PERFECT_JUMP,
    /** A care package dropped landed near a burning signal flare. */
    SPECIAL_DELIVERY,
    /** Collected a villager passenger's fare. */
    FREQUENT_FLYER,
    /** A paper plane thrown aboard an airliner in flight came down outside it, far from where it was thrown. */
    AIR_MAIL,
    /** Bought something at a duty-free counter. */
    DUTY_FREE;

    /** Grants the player this milestone, if they have not got it already. */
    public void award(ServerPlayer player) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "first_flight/" + name().toLowerCase(Locale.ROOT));
        AdvancementHolder advancement = player.server.getAdvancements().get(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, "done");
        }
    }
}
