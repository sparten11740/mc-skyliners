package dev.aerolarge.client;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;

/**
 * What a view through one of the airliner's outside cameras is drawn from: an entity of no size that is never added to
 * the world, so nothing is drawn of it and nothing it could suffer (blindness, darkness, a hurt shake) colours the
 * view, its eyes as high as asked.
 */
final class Lens extends Marker {
    private final float eyes;

    Lens(Level level, float eyes) {
        super(EntityType.MARKER, level);
        this.eyes = eyes;
        refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0, 0).withEyeHeight(eyes);
    }
}
