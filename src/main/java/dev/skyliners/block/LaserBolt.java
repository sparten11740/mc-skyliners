package dev.skyliners.block;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/**
 * Sent to everyone near as a laser bolt is fired, by the laser path clearer, the freighter's laser cannon or a laser
 * crossbow: where it leaves the muzzle, in the world, and where it strikes, in the world or, where it strikes a ship's
 * block, among her blocks, so that it lands on her as she is drawn; the colour of its glow, alpha first; and, for a
 * bolt fired from a weapon held in hand, the entity id of whoever holds it, from whose weapon as drawn it is drawn
 * leaving, or {@link #NO_HOLDER} for a gun mounted on a ship or the ground.
 */
public record LaserBolt(Vec3 from, Vec3 to, int glow, int holder) implements CustomPacketPayload {
    /** The path clearer's bolts' red glow. */
    public static final int RED = 0xA0FF2020;
    /** The laser crossbow's bolts' amber glow. */
    public static final int AMBER = 0xA0FFA020;
    /** The holder of a bolt fired from a mounted gun: nobody. */
    public static final int NO_HOLDER = -1;
    public static final Type<LaserBolt> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "laser_bolt"));
    public static final StreamCodec<ByteBuf, LaserBolt> STREAM_CODEC = StreamCodec.of(LaserBolt::write, LaserBolt::read);
    /**
     * What this client last made of the newest bolt it was sent, none before it was sent one; the client sets it, for a
     * play-test to read (see {@code BlockpilotCommands}).
     */
    public static Supplier<Seen> clientSeen = () -> null;

    /**
     * What a client made of the newest bolt it was sent: how many bolts it had been sent by then, whoever held the
     * weapon it left, how many frames it has been drawn in and for how many ticks after it came, whether it was drawn
     * leaving that weapon's muzzle as drawn, and where it was drawn leaving from, from the camera, in blocks to the
     * right, up and ahead as the camera looks, none before it is drawn.
     */
    public record Seen(int received, int holder, int frames, int shown, boolean fromMuzzle, @Nullable Vec3 fromCamera) {
    }

    /** A bolt from a mounted gun, held by nobody. */
    public LaserBolt(Vec3 from, Vec3 to, int glow) {
        this(from, to, glow, NO_HOLDER);
    }

    private static void write(ByteBuf buffer, LaserBolt bolt) {
        for (Vec3 end : new Vec3[]{bolt.from(), bolt.to()}) {
            buffer.writeDouble(end.x);
            buffer.writeDouble(end.y);
            buffer.writeDouble(end.z);
        }
        buffer.writeInt(bolt.glow());
        buffer.writeInt(bolt.holder());
    }

    private static LaserBolt read(ByteBuf buffer) {
        Vec3 from = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        Vec3 to = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        return new LaserBolt(from, to, buffer.readInt(), buffer.readInt());
    }

    @Override
    public Type<LaserBolt> type() {
        return TYPE;
    }
}
