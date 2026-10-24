package dev.aerolarge.radar;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * A transponder as the maps show it: where it is in the world, its picture and colour, and whether it flies, aboard
 * an assembled airliner. One aboard an airliner also says which way it goes (a compass heading, 0 north and 90 east,
 * NaN while it stands still) and how fast, in blocks a second across the ground. {@code block} is where the
 * transponder's block is: in the world for one on the ground, among the airliner's blocks (its plot) for one aboard,
 * which tells the airliner's own transponder apart from the others on its autopilot's map.
 */
public record Contact(BlockPos block, double x, double y, double z, MarkerIcon icon, MarkerColour colour, boolean airborne, float heading, float speed) implements OnTheMap {
    public static final StreamCodec<ByteBuf, Contact> STREAM_CODEC = StreamCodec.of(Contact::write, Contact::read);

    private static void write(ByteBuf buffer, Contact contact) {
        BlockPos.STREAM_CODEC.encode(buffer, contact.block);
        buffer.writeDouble(contact.x);
        buffer.writeFloat((float) contact.y);
        buffer.writeDouble(contact.z);
        ByteBufCodecs.VAR_INT.encode(buffer, contact.icon.ordinal());
        ByteBufCodecs.VAR_INT.encode(buffer, contact.colour.ordinal());
        buffer.writeBoolean(contact.airborne);
        buffer.writeFloat(contact.heading);
        buffer.writeFloat(contact.speed);
    }

    private static Contact read(ByteBuf buffer) {
        BlockPos block = BlockPos.STREAM_CODEC.decode(buffer);
        double x = buffer.readDouble();
        double y = buffer.readFloat();
        double z = buffer.readDouble();
        MarkerIcon icon = MarkerIcon.of(ByteBufCodecs.VAR_INT.decode(buffer));
        MarkerColour colour = MarkerColour.of(ByteBufCodecs.VAR_INT.decode(buffer));
        return new Contact(block, x, y, z, icon, colour, buffer.readBoolean(), buffer.readFloat(), buffer.readFloat());
    }
}
