package dev.skyliners.autopilot;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent each tick to everyone near enough to see a rocket's clouds or falling flames (see {@link RocketPlumes}): one
 * small message that the client draws a tick's worth from, where a packet for each cloud would cost every player
 * dozens of kilobytes a second. Where they start, how far out round it, in blocks, and whether they are flames
 * streaming from her base as she falls back from space, or clouds rolling out over the ground.
 */
public record RocketPlume(double x, double y, double z, float round, boolean falling) implements CustomPacketPayload {
    public static final Type<RocketPlume> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "rocket_plume"));
    public static final StreamCodec<ByteBuf, RocketPlume> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, RocketPlume::x,
        ByteBufCodecs.DOUBLE, RocketPlume::y,
        ByteBufCodecs.DOUBLE, RocketPlume::z,
        ByteBufCodecs.FLOAT, RocketPlume::round,
        ByteBufCodecs.BOOL, RocketPlume::falling,
        RocketPlume::new
    );

    @Override
    public Type<RocketPlume> type() {
        return TYPE;
    }
}
