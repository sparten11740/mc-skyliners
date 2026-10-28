package dev.aerolarge;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The First Flight advancements: milestones of flying the airliner, from climbing away from the runway to a gentle
 * touchdown, and of riding the rocket to the edge of space. Most have one criterion, "done"; Lost and Found has one for each golden suitcase in the hold, Duck
 * Hunt one for each rubber duck hidden aboard, and Barista one for each of the coffee machine's drinks. Nothing in
 * the game triggers them: the mod grants them where the moment happens. The advancements themselves are data, under
 * {@code first_flight/}.
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
    DUTY_FREE,
    /** Dived into the moon pool off its springboard or its tower in flight. */
    CANNONBALL,
    /** Found the golden suitcases hidden in an airliner's hold: "suitcase_1" and on, one criterion each. */
    LOST_AND_FOUND,
    /** Flushed a vacuum toilet aboard an airliner in flight, dropping its blue ice from high over the ground. */
    BLUE_ICE,
    /** Found the rubber ducks hidden aboard a furnished airliner: "duck_1" and on, one criterion each. */
    DUCK_HUNT,
    /** Took each of a coffee machine's drinks from under its spout: one criterion each, named as the drink. */
    BARISTA,
    /** Aboard an airliner as its autopilot landed it on a runway laid down from a blueprint, and stopped. */
    ON_THE_NUMBERS,
    /** Aboard a rocket as she climbed to the edge of space. */
    EDGE_OF_SPACE;

    /** Grants the player this milestone, if they have not got it already. */
    public void award(ServerPlayer player) {
        award(player, "done");
    }

    /** Grants the player one of this milestone's criteria; whether they did not have it yet. */
    public boolean award(ServerPlayer player, String criterion) {
        AdvancementHolder advancement = advancement(player);
        return advancement != null && player.getAdvancements().award(advancement, criterion);
    }

    /** How many of this milestone's criteria the player has. */
    public int progress(ServerPlayer player) {
        AdvancementHolder advancement = advancement(player);
        int done = 0;
        if (advancement != null) {
            for (String ignored : player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria()) {
                done++;
            }
        }
        return done;
    }

    @Nullable
    private AdvancementHolder advancement(ServerPlayer player) {
        return player.server.getAdvancements().get(id());
    }

    /** The milestone's advancement, as the server and the client both know it. */
    public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "first_flight/" + name().toLowerCase(Locale.ROOT));
    }
}
