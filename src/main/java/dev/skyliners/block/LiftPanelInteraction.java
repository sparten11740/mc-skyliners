package dev.skyliners.block;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A touch on a lift panel carried in a cabin. Create hands it over by the block touched only, so the server traces the
 * player's look again, into the cabin's own frame, to find where on the panel's face it landed; from there it is a
 * touch as on a panel beside a landing (see {@link LiftPanelBlock#touch}).
 */
public class LiftPanelInteraction extends MovingInteractionBehaviour {
    @Override
    public boolean handlePlayerInteraction(Player player, InteractionHand hand, BlockPos localPos, AbstractContraptionEntity cabin) {
        StructureTemplate.StructureBlockInfo info = cabin.getContraption().getBlocks().get(localPos);
        if (player.level().isClientSide || info == null) {
            // On the client, the touch goes on to the server.
            return true;
        }
        BlockState state = info.state();
        Vec3 eye = player.getEyePosition();
        Vec3 reach = eye.add(player.getViewVector(1).scale(player.blockInteractionRange() + 1));
        BlockHitResult hit = state.getShape(cabin.level(), localPos).clip(cabin.toLocalVector(eye, 1), cabin.toLocalVector(reach, 1), localPos);
        if (hit != null && hit.getDirection() == state.getValue(LiftPanelBlock.FACING)) {
            LiftPanelBlock.touch(player.level(), cabin, hit.getLocation().y - localPos.getY(), cabin.toGlobalVector(Vec3.atCenterOf(localPos), 1));
        }
        return true;
    }
}
