package dev.skyliners.rover;

import dev.skyliners.Skyliners;
import dev.skyliners.entity.Rover;
import dev.skyliners.item.RoverRemoteItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by a player driving the rocket's rover from its remote (see the client's {@code RoverDriving}): how hard they
 * drive it ahead (1) or back (-1), how hard they turn it right (1) or left (-1), and whether its horn is held. Sent
 * whenever it changes and a few times a second besides, so the rover stops once it has heard nothing for half a second.
 * Nothing in it is taken on trust: the server only drives a rover loaded there, for a player alive and holding a remote
 * paired with it, within reach of it, each input held to its range.
 */
public record DriveRover(int rover, float forward, float turn, boolean honk) implements CustomPacketPayload {
    public static final Type<DriveRover> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "drive_rover"));
    public static final StreamCodec<FriendlyByteBuf, DriveRover> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, DriveRover::rover,
        ByteBufCodecs.FLOAT, DriveRover::forward,
        ByteBufCodecs.FLOAT, DriveRover::turn,
        ByteBufCodecs.BOOL, DriveRover::honk,
        DriveRover::new
    );

    @Override
    public Type<DriveRover> type() {
        return TYPE;
    }

    /** On the server: drives the rover, for a player who may. */
    public static void handle(DriveRover payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator() || !player.isAlive()) {
            return;
        }
        if (!(player.serverLevel().getEntity(payload.rover()) instanceof Rover rover) || rover.isRemoved() || !RoverRemoteItem.inHand(player, rover.getUUID())
            || !rover.withinReach(player)) {
            return;
        }
        rover.drive(player, held(payload.forward()), held(payload.turn()), payload.honk());
        // Only a rover on its way to the ground or on it is driven: word for one in its bay or hoisted covers no fall.
        if (rover.state() == Rover.State.OUT || rover.state() == Rover.State.LOWERING) {
            RoverDrivers.heard(player);
        }
    }

    /** An input held to its range, from -1 to 1; nothing for one that is not a number. */
    private static float held(float input) {
        return Float.isFinite(input) ? Mth.clamp(input, -1, 1) : 0;
    }
}
