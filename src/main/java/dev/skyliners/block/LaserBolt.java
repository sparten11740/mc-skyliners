package dev.skyliners.block;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Sent to everyone near as the laser path clearer fires a bolt: where it leaves the muzzle and where it strikes, in the world. */
public record LaserBolt(Vec3 from, Vec3 to) implements CustomPacketPayload {
    public static final Type<LaserBolt> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "laser_bolt"));
    public static final StreamCodec<ByteBuf, LaserBolt> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.DOUBLE, bolt -> bolt.from().x,
        ByteBufCodecs.DOUBLE, bolt -> bolt.from().y,
        ByteBufCodecs.DOUBLE, bolt -> bolt.from().z,
        ByteBufCodecs.DOUBLE, bolt -> bolt.to().x,
        ByteBufCodecs.DOUBLE, bolt -> bolt.to().y,
        ByteBufCodecs.DOUBLE, bolt -> bolt.to().z,
        (fromX, fromY, fromZ, toX, toY, toZ) -> new LaserBolt(new Vec3(fromX, fromY, fromZ), new Vec3(toX, toY, toZ))
    );

    @Override
    public Type<LaserBolt> type() {
        return TYPE;
    }
}
