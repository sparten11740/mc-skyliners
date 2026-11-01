package dev.skyliners.block;

import dev.skyliners.Skyliners;
import java.util.Locale;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.codec.NeoForgeStreamCodecs;

/**
 * Sent to a player who looked into one of the lab's microscopes (see {@link LabInstrumentBlock}): show the slide under
 * its lens, magnified, as its eyepiece sees it.
 */
public record LookIntoMicroscope(Slide slide) implements CustomPacketPayload {
    public static final Type<LookIntoMicroscope> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "look_into_microscope"));
    public static final StreamCodec<FriendlyByteBuf, LookIntoMicroscope> STREAM_CODEC = StreamCodec.composite(
        NeoForgeStreamCodecs.enumCodec(Slide.class), LookIntoMicroscope::slide,
        LookIntoMicroscope::new
    );

    /**
     * The microscope's slides: a water bear, the tank's creature's tiny cousin; the algae column's cells, green beads
     * full of chloroplasts; the bioreactor's steak, its muscle fibres striped; and diatoms, glassy shells in the shapes
     * of stars, boats and wheels.
     */
    public enum Slide {
        TARDIGRADE,
        ALGAE,
        MUSCLE,
        DIATOMS;

        /** The name its picture is painted under, in tools/generate_textures.mjs. */
        public String picture() {
            return "microscope_" + name().toLowerCase(Locale.ROOT);
        }
    }

    @Override
    public Type<LookIntoMicroscope> type() {
        return TYPE;
    }
}
