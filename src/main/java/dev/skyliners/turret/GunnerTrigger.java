package dev.skyliners.turret;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by a gunner's client while they sit at a laser cannon (see {@link LaserCannonBlockEntity}): whether they hold
 * the attack key down, the cannon's trigger, and the use key, its rockets' trigger, sent as either changes and again
 * every few ticks while either is held, so a trigger whose word stops coming lapses. Nothing in it is taken on trust:
 * the server only fires a cannon for a player it finds seated at it, and the cannon keeps its own pace, heat and
 * rockets.
 */
public record GunnerTrigger(boolean held, boolean rockets) implements CustomPacketPayload {
    public static final Type<GunnerTrigger> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "gunner_trigger"));
    public static final StreamCodec<ByteBuf, GunnerTrigger> STREAM_CODEC =
        StreamCodec.composite(ByteBufCodecs.BOOL, GunnerTrigger::held, ByteBufCodecs.BOOL, GunnerTrigger::rockets, GunnerTrigger::new);

    @Override
    public Type<GunnerTrigger> type() {
        return TYPE;
    }

    /** On the server: the triggers of the cannon the player sits at, if any, held or let go. */
    public static void handle(GunnerTrigger payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            LaserCannonBlockEntity.trigger(player, payload.held(), payload.rockets());
        }
    }
}
