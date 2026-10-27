package dev.aerolarge.autopilot;

import dev.aerolarge.Aerolarge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to a player who used the autopilot or its screen: open the autopilot's settings screen, filled in with what it
 * flies now (or would fly if engaged now), whether engaging it would take off, whether the airliner or zeppelin is not
 * assembled but engaging assembles it first, the heights and speeds it can be set to hold there, and how hard gravity
 * pulls there, in blocks a second each second, which the size of a circle follows from (see
 * {@link AutopilotBlockEntity.CircleSize#radius}).
 */
public record OpenAutopilotScreen(BlockPos pos, AutopilotBlockEntity.Settings settings, boolean takeoff, boolean assembles, Limits limits,
                                  float gravity)
    implements CustomPacketPayload {
    public static final Type<OpenAutopilotScreen> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "open_autopilot_screen"));
    public static final StreamCodec<FriendlyByteBuf, OpenAutopilotScreen> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, OpenAutopilotScreen::pos,
        AutopilotBlockEntity.Settings.STREAM_CODEC, OpenAutopilotScreen::settings,
        ByteBufCodecs.BOOL, OpenAutopilotScreen::takeoff,
        ByteBufCodecs.BOOL, OpenAutopilotScreen::assembles,
        Limits.STREAM_CODEC, OpenAutopilotScreen::limits,
        ByteBufCodecs.FLOAT, OpenAutopilotScreen::gravity,
        OpenAutopilotScreen::new
    );

    /**
     * The lowest and highest heights it can be set to hold, in world blocks, the slowest and fastest it flies and where
     * its speed dial turns amber and red, in blocks a second: an airliner's or a zeppelin's (see {@link Craft}).
     */
    public record Limits(int minAltitude, int maxAltitude, int minSpeed, int maxSpeed, int amberFrom, int redFrom) {
        public static final StreamCodec<FriendlyByteBuf, Limits> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Limits::minAltitude,
            ByteBufCodecs.VAR_INT, Limits::maxAltitude,
            ByteBufCodecs.VAR_INT, Limits::minSpeed,
            ByteBufCodecs.VAR_INT, Limits::maxSpeed,
            ByteBufCodecs.VAR_INT, Limits::amberFrom,
            ByteBufCodecs.VAR_INT, Limits::redFrom,
            Limits::new
        );
    }

    @Override
    public Type<OpenAutopilotScreen> type() {
        return TYPE;
    }
}
