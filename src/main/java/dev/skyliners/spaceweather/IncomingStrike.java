package dev.skyliners.spaceweather;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Sent to everyone near a rocket or a freighter as a meteor or an asteroid of the space weather blowing is seen flying
 * in to strike her (see {@link SpaceWeather}), one message a strike, for their client to draw it coming, and again
 * each time her laser cannon strikes the rock on its way in, knocking it off its course: what kind of weather it is of;
 * its number among the weather's rocks; where on her it lands, among her blocks, so it keeps coming at that spot as she
 * moves; the way it travels, in the world's directions, of length 1; when it lands, in game time; its own seed, for its
 * tumble and its shape; how big it is, in blocks across, none for a meteor; how fast it comes, in blocks a second; how
 * far it had been knocked off its course, in blocks, as of a game time, and how fast it drifts on off it since, in
 * blocks a second; and whether it is knocked far enough off to miss her, flying on past.
 */
public record IncomingStrike(SpaceWeather.Kind kind, int id, Vec3 onHer, Vec3 way, long at, long seed, float size, float speed, Vec3 aside,
                             Vec3 drift, long changed, boolean misses) implements CustomPacketPayload {
    public static final Type<IncomingStrike> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "incoming_strike"));
    public static final StreamCodec<ByteBuf, IncomingStrike> STREAM_CODEC = StreamCodec.of(IncomingStrike::write, IncomingStrike::read);
    /** Ticks a second. */
    private static final double TICKS = 20;

    private static void write(ByteBuf buffer, IncomingStrike strike) {
        buffer.writeByte(strike.kind().ordinal());
        buffer.writeInt(strike.id());
        SpaceWeatherShown.writeVec(buffer, strike.onHer());
        writeFloats(buffer, strike.way());
        buffer.writeLong(strike.at());
        buffer.writeLong(strike.seed());
        buffer.writeFloat(strike.size());
        buffer.writeFloat(strike.speed());
        writeFloats(buffer, strike.aside());
        writeFloats(buffer, strike.drift());
        buffer.writeLong(strike.changed());
        buffer.writeBoolean(strike.misses());
    }

    private static IncomingStrike read(ByteBuf buffer) {
        SpaceWeather.Kind kind = SpaceWeather.Kind.of(buffer.readByte());
        int id = buffer.readInt();
        Vec3 onHer = SpaceWeatherShown.readVec(buffer);
        Vec3 way = readFloats(buffer);
        long at = buffer.readLong();
        long seed = buffer.readLong();
        float size = buffer.readFloat();
        float speed = buffer.readFloat();
        Vec3 aside = readFloats(buffer);
        Vec3 drift = readFloats(buffer);
        return new IncomingStrike(kind, id, onHer, way, at, seed, size, speed, aside, drift, buffer.readLong(), buffer.readBoolean());
    }

    private static void writeFloats(ByteBuf buffer, Vec3 vec) {
        buffer.writeFloat((float) vec.x);
        buffer.writeFloat((float) vec.y);
        buffer.writeFloat((float) vec.z);
    }

    private static Vec3 readFloats(ByteBuf buffer) {
        return new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    /**
     * Where it is at a game time, from where the spot on her it comes at is then, in the world: back along its way as
     * far as it has still to come, and as far off its course as it has been knocked.
     */
    public Vec3 position(Vec3 target, double now) {
        return target.subtract(way.scale((at - now) / TICKS * speed)).add(aside).add(drift.scale((now - changed) / TICKS));
    }

    /** Whether it is a rock, which her cannon can shoot at, rather than a meteor. */
    public boolean rock() {
        return size > 0;
    }

    @Override
    public Type<IncomingStrike> type() {
        return TYPE;
    }
}
