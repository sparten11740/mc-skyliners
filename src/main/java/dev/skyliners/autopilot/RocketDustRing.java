package dev.skyliners.autopilot;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent each tick that a rocket's lift-off shockwave races out to everyone near enough to see its ring of dust (see
 * {@link RocketLaunchBlast}), which the client rolls along the ground: where it began, how far its front has come, and
 * how far its front came this tick, in blocks.
 */
public record RocketDustRing(double x, double y, double z, float front, float speed) implements CustomPacketPayload {
    /**
     * How far above where the ring began it still counts ground as ground, in blocks: past it stands something like a
     * launch pad's tower, which it shoves nothing on and rolls its dust past, not over.
     */
    public static final double HIGHEST_GROUND = 12;
    public static final Type<RocketDustRing> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "rocket_dust_ring"));
    public static final StreamCodec<ByteBuf, RocketDustRing> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, RocketDustRing::x,
        ByteBufCodecs.DOUBLE, RocketDustRing::y,
        ByteBufCodecs.DOUBLE, RocketDustRing::z,
        ByteBufCodecs.FLOAT, RocketDustRing::front,
        ByteBufCodecs.FLOAT, RocketDustRing::speed,
        RocketDustRing::new
    );

    @Override
    public Type<RocketDustRing> type() {
        return TYPE;
    }
}
