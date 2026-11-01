package dev.skyliners.block;

import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.radar.MarkerColour;
import dev.skyliners.radar.MarkerIcon;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Sent by a transponder's panel: show this picture in this colour. The server only acts for a player who can reach a
 * transponder standing there already loaded.
 */
public record SetTransponder(BlockPos pos, MarkerIcon icon, MarkerColour colour) implements CustomPacketPayload {
    public static final Type<SetTransponder> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "set_transponder"));
    public static final StreamCodec<FriendlyByteBuf, SetTransponder> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SetTransponder::pos,
        NeoForgeStreamCodecs.enumCodec(MarkerIcon.class), SetTransponder::icon,
        NeoForgeStreamCodecs.enumCodec(MarkerColour.class), SetTransponder::colour,
        SetTransponder::new
    );

    @Override
    public Type<SetTransponder> type() {
        return TYPE;
    }

    /** On the server: sets the transponder, for a player who can reach it. Checked loaded first, so a client never loads a chunk. */
    public static void handle(SetTransponder payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        Level level = player.level();
        BlockPos pos = payload.pos();
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(SkylinersRegistry.TRANSPONDER.get())) {
            return;
        }
        if (level.getBlockEntity(pos) instanceof TransponderBlockEntity transponder && Container.stillValidBlockEntity(transponder, player)) {
            transponder.show(payload.icon(), payload.colour());
        }
    }
}
