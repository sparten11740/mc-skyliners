package dev.skyliners.spaceweather;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Sent to everyone near a freighter as a meteor or an asteroid of the space weather blowing is seen flying in to strike
 * her (see {@link SpaceWeather}), one message a strike, for their client to draw it coming: what kind of weather it is
 * of; where on her it lands, among her blocks, so it keeps coming at that spot as she moves; the way it travels, in the
 * world's directions, of length 1; when it lands, in game time; and its own seed, for its tumble and its size.
 */
public record IncomingStrike(SpaceWeather.Kind kind, Vec3 onHer, Vec3 way, long at, long seed) implements CustomPacketPayload {
    public static final Type<IncomingStrike> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "incoming_strike"));
    public static final StreamCodec<ByteBuf, IncomingStrike> STREAM_CODEC = StreamCodec.of(IncomingStrike::write, IncomingStrike::read);

    private static void write(ByteBuf buffer, IncomingStrike strike) {
        buffer.writeByte(strike.kind().ordinal());
        SpaceWeatherShown.writeVec(buffer, strike.onHer());
        buffer.writeFloat((float) strike.way().x);
        buffer.writeFloat((float) strike.way().y);
        buffer.writeFloat((float) strike.way().z);
        buffer.writeLong(strike.at());
        buffer.writeLong(strike.seed());
    }

    private static IncomingStrike read(ByteBuf buffer) {
        SpaceWeather.Kind kind = SpaceWeather.Kind.of(buffer.readByte());
        Vec3 onHer = SpaceWeatherShown.readVec(buffer);
        Vec3 way = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        return new IncomingStrike(kind, onHer, way, buffer.readLong(), buffer.readLong());
    }

    @Override
    public Type<IncomingStrike> type() {
        return TYPE;
    }
}
