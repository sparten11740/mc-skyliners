package dev.skyliners.radar;

import dev.skyliners.Skyliners;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Sent to every player once a second: every transponder in their dimension, for the autopilot's map and the tower's
 * radar (see {@link Transponders}), and every player there, for their faces on them. Each list stands in for the one
 * before.
 */
public record RadarContacts(List<Contact> contacts, List<PlayerContact> players) implements CustomPacketPayload {
    public static final Type<RadarContacts> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "radar_contacts"));
    public static final StreamCodec<FriendlyByteBuf, RadarContacts> STREAM_CODEC = StreamCodec.composite(
        Contact.STREAM_CODEC.apply(ByteBufCodecs.list()), RadarContacts::contacts,
        PlayerContact.STREAM_CODEC.apply(ByteBufCodecs.list()), RadarContacts::players,
        RadarContacts::new
    );

    @Override
    public Type<RadarContacts> type() {
        return TYPE;
    }
}
