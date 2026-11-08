package dev.skyliners.autopilot;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import java.util.function.Supplier;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Sent to everyone near a freighter's hyperspace jump but not aboard her, for their client to show it from outside
 * (see {@link FreighterJump}), one message a step: light gathering on her as she lets go, the flash and the streaks
 * shooting off ahead where she was as she goes, streaks rushing in to where she is about to come out, and the flash
 * and the shock ring there as she does. Those aboard see the jump from inside instead (see {@link HyperspaceJump}).
 *
 * <p>Where in the world it happens, her middle; where among her blocks that is, as she lets go and as she comes out,
 * so the light gathering on her keeps up with her as she flies on and she is drawn stretching into the jump and out of
 * it; the way she points; how far her hull reaches from her middle, in blocks; and how many ticks the step lasts before
 * the next, for those that lead up to one.
 */
public record HyperspaceWake(Stage stage, Vec3 at, Vec3 aboard, Vec3 ahead, float reach, int ticks) implements CustomPacketPayload {
    public static final Type<HyperspaceWake> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "hyperspace_wake"));
    public static final StreamCodec<ByteBuf, HyperspaceWake> STREAM_CODEC = StreamCodec.of(HyperspaceWake::write, HyperspaceWake::read);
    /**
     * How far from where it happens a jump is seen, in blocks: well past where her hull fills the view. It is heard much
     * further (see {@link HyperspaceSound#HEARD_WITHIN}).
     */
    public static final double SEEN_WITHIN = 192;
    /**
     * What this client last drew of a freighter stretching into a jump and out of it, none before it was shown one; the
     * client sets it, for a play-test to read (see {@code BlockpilotCommands}).
     */
    public static Supplier<Stretched> clientStretched = () -> null;

    /**
     * What a client drew of the last jump it was shown from outside: how many jumps it has been shown so far, how many
     * times her own length she was drawn at most as she went and in how many frames she was drawn stretched, and the
     * same as she came out.
     */
    public record Stretched(int jumps, double going, int goingFrames, double coming, int comingFrames) {
    }

    /** The steps of a jump as seen from outside: gathering, gone, on her way in, and out. */
    public enum Stage {
        GATHER, DEPART, INBOUND, ARRIVE;

        /** A step by its place in the list, as sent; out for one this build does not know. */
        static Stage of(int index) {
            Stage[] stages = values();
            return stages[Mth.clamp(index, 0, stages.length - 1)];
        }
    }

    private static void write(ByteBuf buffer, HyperspaceWake wake) {
        buffer.writeByte(wake.stage().ordinal());
        writeVec(buffer, wake.at());
        writeVec(buffer, wake.aboard());
        buffer.writeFloat((float) wake.ahead().x);
        buffer.writeFloat((float) wake.ahead().y);
        buffer.writeFloat((float) wake.ahead().z);
        buffer.writeFloat(wake.reach());
        buffer.writeShort(wake.ticks());
    }

    private static HyperspaceWake read(ByteBuf buffer) {
        Stage stage = Stage.of(buffer.readByte());
        Vec3 at = readVec(buffer);
        Vec3 aboard = readVec(buffer);
        Vec3 ahead = new Vec3(buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
        return new HyperspaceWake(stage, at, aboard, ahead, buffer.readFloat(), buffer.readShort());
    }

    private static void writeVec(ByteBuf buffer, Vec3 vec) {
        buffer.writeDouble(vec.x);
        buffer.writeDouble(vec.y);
        buffer.writeDouble(vec.z);
    }

    private static Vec3 readVec(ByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    public Type<HyperspaceWake> type() {
        return TYPE;
    }
}
