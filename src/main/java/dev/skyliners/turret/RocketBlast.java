package dev.skyliners.turret;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Sent to everyone near as a laser cannon's rocket goes off (see {@link CannonRocket}): where, in the world, so that
 * each client draws its fireball, its soot and its sparks there, with a flash, and jolts the view of whoever is near.
 * One message for the whole blast; the game's own explosion only reaches those within sixty-four blocks.
 */
public record RocketBlast(Vec3 at) implements CustomPacketPayload {
    public static final Type<RocketBlast> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "rocket_blast"));
    public static final StreamCodec<ByteBuf, RocketBlast> STREAM_CODEC = StreamCodec.of(RocketBlast::write, RocketBlast::read);

    private static void write(ByteBuf buffer, RocketBlast blast) {
        buffer.writeDouble(blast.at().x);
        buffer.writeDouble(blast.at().y);
        buffer.writeDouble(blast.at().z);
    }

    private static RocketBlast read(ByteBuf buffer) {
        return new RocketBlast(new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    @Override
    public Type<RocketBlast> type() {
        return TYPE;
    }
}
