package dev.aerolarge.mixin;

import com.simibubi.create.content.decoration.girder.GirderBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Turns Create's metal girder a quarter turn without losing its beam. A girder runs along X, Z or both as its X and Z
 * say; turned a quarter turn, Create's own turn sets X to what Z was but leaves Z as it was, so a beam along X comes
 * out running nowhere and each girder of it is drawn as a stub on its own. An airliner built facing east or west had
 * the gym hall's roof trusses broken into a row of loose boxes so, and so would anything Sable turns as it assembles or
 * sets down an airliner. Here X and Z trade places, as the beam does.
 */
@Mixin(GirderBlock.class)
abstract class GirderBlockMixin {
    @Inject(method = "rotate", at = @At("HEAD"), cancellable = true)
    private void aerolarge$turnBeams(BlockState state, Rotation rotation, CallbackInfoReturnable<BlockState> info) {
        Direction.Axis axis = state.getValue(GirderBlock.AXIS);
        BlockState turned = state.setValue(GirderBlock.AXIS, rotation.rotate(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)).getAxis());
        if (rotation.rotate(Direction.EAST).getAxis() == Direction.Axis.X) {
            info.setReturnValue(turned);
            return;
        }
        info.setReturnValue(turned.setValue(GirderBlock.X, state.getValue(GirderBlock.Z)).setValue(GirderBlock.Z, state.getValue(GirderBlock.X)));
    }
}
