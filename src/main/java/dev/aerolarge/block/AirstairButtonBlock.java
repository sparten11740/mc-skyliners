package dev.aerolarge.block;

import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * The airstairs' push-button, set beside their controller just aft of the forward door, inside the cabin and outside
 * by the top of the stairs: a lit push-button (see {@link LitButtonBlock}), its cap glowing amber with the panel's own
 * staircase on it. It is a stone button in all it does: it stays down a second and powers what it is on, which, on the
 * controller, folds the stairs away or brings them out on the signal (see {@link AirstairControllerBlock}). Unlike the
 * call button on the nose gear it works only what it is on.
 */
public class AirstairButtonBlock extends LitButtonBlock {
    public AirstairButtonBlock(BlockSetType type, Properties properties) {
        super(type, properties);
    }
}
