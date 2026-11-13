package dev.skyliners.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.ryanhcode.sable.sublevel.entity_collision.SubLevelEntityCollision;
import dev.skyliners.exterior.SatelliteDishBlockEntity;
import dev.skyliners.turret.LaserCannonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Lets a ship's blocks stop a player where their shapes reach out past their blocks, as a laser cannon's barrels and
 * rocket pods do, and a satellite dish's bowl. Sable collides an entity aboard a ship only with the blocks it stands in
 * and the row under it, so a player walked straight through a freighter's cannon barrels, which reach a block and a
 * half past its block, and her big dish's bowl; on the ground Minecraft looks a block further all round, which stops
 * them at all but the barrels' tips and the bowl's rim. With a cannon or a dish near, Sable looks as far round the
 * entity as they reach; everywhere else it looks as before, so no other ship's collisions cost more.
 */
@Mixin(value = SubLevelEntityCollision.class, remap = false)
abstract class SubLevelEntityCollisionMixin {
    @WrapOperation(method = "collide", require = 1, at = @At(value = "INVOKE",
        target = "Lnet/minecraft/core/BlockPos;betweenClosed(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;)Ljava/lang/Iterable;"))
    private static Iterable<BlockPos> skyliners$reachPastBlocks(BlockPos low, BlockPos high,
        Operation<Iterable<BlockPos>> original, @Local(argsOnly = true) Entity entity) {
        int reach = Math.max(LaserCannonBlockEntity.reaches(entity.level(), low, high) ? LaserCannonBlockEntity.REACH_BLOCKS : 0,
            SatelliteDishBlockEntity.reaches(entity.level(), low, high) ? SatelliteDishBlockEntity.REACH_BLOCKS : 0);
        return original.call(low.offset(-reach, -reach, -reach), high.offset(reach, reach, reach));
    }
}
