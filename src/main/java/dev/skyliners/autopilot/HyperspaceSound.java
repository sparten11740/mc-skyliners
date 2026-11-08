package dev.skyliners.autopilot;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Sent to everyone within earshot of a freighter's hyperspace jump but not aboard her, for their client to hear it from
 * outside (see {@link FreighterJump}): one of its sounds, and where in the world it is made. As she lets go, her charge
 * where she is and the rush where she is about to come out; as she goes, her whump where she was and the thunderclap
 * where she comes out. Each client hears it as late as sound takes to reach it from there, and duller from far off
 * (see the client's {@code HyperspaceSounds}), well beyond where the jump is seen (see {@link HyperspaceWake}). Those
 * aboard hear the jump from inside instead (see {@link HyperspaceJump}).
 */
public record HyperspaceSound(Part part, Vec3 at) implements CustomPacketPayload {
    public static final Type<HyperspaceSound> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "hyperspace_sound"));
    public static final StreamCodec<ByteBuf, HyperspaceSound> STREAM_CODEC = StreamCodec.of(HyperspaceSound::write, HyperspaceSound::read);
    /**
     * How far from where it is made a jump's sound is sent, in blocks: as far as its whump and thunderclap carry, the
     * volume falling off evenly to nothing there (their {@code heard} in tools/generate_resources.mjs), some three
     * times as far as the jump is seen.
     */
    public static final double HEARD_WITHIN = 640;

    /** The sounds of a jump as heard from outside: her charge and her whump going, the rush and the thunderclap out. */
    public enum Part {
        CHARGE, WHUMP, RUSH, THUNDER;

        /** A sound by its place in the list, as sent; the thunderclap for one this build does not know. */
        static Part of(int index) {
            Part[] parts = values();
            return parts[Mth.clamp(index, 0, parts.length - 1)];
        }

        /** Whether this is a bang, heard as she goes or comes out, rather than the lead up to one. */
        public boolean bang() {
            return this == WHUMP || this == THUNDER;
        }
    }

    private static void write(ByteBuf buffer, HyperspaceSound sound) {
        buffer.writeByte(sound.part().ordinal());
        buffer.writeDouble(sound.at().x);
        buffer.writeDouble(sound.at().y);
        buffer.writeDouble(sound.at().z);
    }

    private static HyperspaceSound read(ByteBuf buffer) {
        Part part = Part.of(buffer.readByte());
        return new HyperspaceSound(part, new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble()));
    }

    @Override
    public Type<HyperspaceSound> type() {
        return TYPE;
    }
}
