package dev.skyliners.compat;

import javax.annotation.Nullable;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Bombs from Ultrasplosive, when it is installed, for the drop bay: its nuke (the Fat Man, not Sundial) loads like a
 * care package and leaves the bay as Ultrasplosive's flying nuke, which goes off where it first hits. Found by
 * registry id, so nothing here links against Ultrasplosive.
 */
public final class Ultrasplosive {
    private static final boolean LOADED = ModList.get().isLoaded("ultrasplosive");
    private static final ResourceLocation NUKE = ResourceLocation.fromNamespaceAndPath("ultrasplosive", "nuke");
    private static final ResourceLocation FLYING_NUKE = ResourceLocation.fromNamespaceAndPath("ultrasplosive", "flying_nuke");

    private Ultrasplosive() {
    }

    /** Whether the stack is Ultrasplosive's nuke. */
    public static boolean isNuke(ItemStack stack) {
        return LOADED && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(NUKE);
    }

    /** Drops a nuke from the point given, moving as given (blocks a tick); whether there was one to drop. */
    public static boolean drop(ServerLevel level, Vec3 at, Vec3 velocity, @Nullable Entity owner) {
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(FLYING_NUKE).orElse(null);
        Entity bomb = type == null ? null : type.create(level);
        if (bomb == null) {
            return false;
        }
        bomb.setPos(at);
        bomb.setDeltaMovement(velocity);
        if (bomb instanceof Projectile projectile && owner != null) {
            projectile.setOwner(owner);
        }
        level.addFreshEntity(bomb);
        return true;
    }
}
