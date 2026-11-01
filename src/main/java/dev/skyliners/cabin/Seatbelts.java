package dev.skyliners.cabin;

import dev.skyliners.Aboard;
import dev.skyliners.FirstFlight;
import dev.skyliners.block.SeatbeltSwitchBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.EntityMountEvent;

/**
 * Buckled in: while an airliner's seatbelt signs are on, a player seated aboard it stays in their seat when they
 * sneak to get up, and is told why. Getting off any other way (a teleport, dying, the seat breaking) still works.
 */
public final class Seatbelts {
    private Seatbelts() {
    }

    /** Whether the signs are on in the airliner the entity is aboard. */
    public static boolean areOn(Entity entity) {
        for (SeatbeltSwitchBlockEntity seatbelts : SeatbeltSwitchBlockEntity.all(entity.level())) {
            if (seatbelts.isOn() && Aboard.isAboard(entity, seatbelts)) {
                return true;
            }
        }
        return false;
    }

    public static void onMount(EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityMounting() instanceof Player player && !player.level().isClientSide
            && player.isAlive() && player.isShiftKeyDown() && areOn(player)) {
            event.setCanceled(true);
            player.displayClientMessage(Component.translatable("message.skyliners.seatbelts.fastened"), true);
            if (player instanceof ServerPlayer buckled) {
                FirstFlight.BUCKLE_UP.award(buckled);
            }
        }
    }
}
