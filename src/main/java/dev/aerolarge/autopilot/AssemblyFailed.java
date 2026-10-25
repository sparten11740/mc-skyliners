package dev.aerolarge.autopilot;

import dev.aerolarge.Aerolarge;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to a player who asked the autopilot at a position to fly an airliner not yet assembled, when it could not
 * have it assembled (no Physics Assembler, or one that could not assemble it): its settings screen, which greys its
 * take-off button out from the moment it is pressed, lets it be pressed again.
 */
public record AssemblyFailed(BlockPos pos) implements CustomPacketPayload {
    public static final Type<AssemblyFailed> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "assembly_failed"));
    public static final StreamCodec<FriendlyByteBuf, AssemblyFailed> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, AssemblyFailed::pos,
        AssemblyFailed::new
    );

    @Override
    public Type<AssemblyFailed> type() {
        return TYPE;
    }
}
