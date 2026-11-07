package dev.skyliners.block;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Sent to everyone near as a laser bolt is fired, by the laser path clearer or a laser crossbow: where it leaves the
 * muzzle and where it strikes, in the world, and the colour of its glow, alpha first.
 */
public record LaserBolt(Vec3 from, Vec3 to, int glow) implements CustomPacketPayload {
    /** The path clearer's bolts' red glow. */
    public static final int RED = 0xA0FF2020;
    /** The laser crossbow's bolts' amber glow. */
    public static final int AMBER = 0xA0FFA020;
    public static final Type<LaserBolt> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "laser_bolt"));
    public static final StreamCodec<ByteBuf, LaserBolt> STREAM_CODEC = StreamCodec.of(LaserBolt::write, LaserBolt::read);

    private static void write(ByteBuf buffer, LaserBolt bolt) {
        for (Vec3 end : new Vec3[]{bolt.from(), bolt.to()}) {
            buffer.writeDouble(end.x);
            buffer.writeDouble(end.y);
            buffer.writeDouble(end.z);
        }
        buffer.writeInt(bolt.glow());
    }

    private static LaserBolt read(ByteBuf buffer) {
        Vec3 from = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        Vec3 to = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        return new LaserBolt(from, to, buffer.readInt());
    }

    @Override
    public Type<LaserBolt> type() {
        return TYPE;
    }
}
