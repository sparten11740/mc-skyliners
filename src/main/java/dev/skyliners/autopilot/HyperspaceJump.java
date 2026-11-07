package dev.skyliners.autopilot;

import dev.skyliners.Skyliners;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Sent to everyone aboard a freighter as her hyperspace jump goes (see {@link FreighterJump}), for their client to
 * show it: the stars stretching into streaks as her drive spools up, racing as she lets go, the flash as she jumps and
 * the streaks falling back as she comes out at the pin; or fading as a jump is called off. Each step with how long it
 * lasts at the least, in ticks.
 */
public record HyperspaceJump(Stage stage, int ticks) implements CustomPacketPayload {
    public static final Type<HyperspaceJump> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "hyperspace_jump"));
    public static final StreamCodec<ByteBuf, HyperspaceJump> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT.map(Stage::of, Stage::ordinal), HyperspaceJump::stage,
        ByteBufCodecs.VAR_INT, HyperspaceJump::ticks,
        HyperspaceJump::new
    );

    /** The steps of a jump: spooling up, letting go, out at the pin, and called off. */
    public enum Stage {
        SPOOL, GO, ARRIVE, CANCEL;

        /** A step by its place in the list, as sent; called off for one this build does not know. */
        static Stage of(int index) {
            Stage[] stages = values();
            return stages[Mth.clamp(index, 0, stages.length - 1)];
        }
    }

    @Override
    public Type<HyperspaceJump> type() {
        return TYPE;
    }
}
