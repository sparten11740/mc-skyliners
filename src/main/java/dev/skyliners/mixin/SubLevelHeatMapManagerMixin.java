package dev.skyliners.mixin;

import dev.ryanhcode.sable.sublevel.plot.heat.SubLevelHeatMapManager;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Keeps a ship in one piece where blocks are put back while Sable works out whether she has come apart. Sable's heat
 * map floods a ship over many ticks after blocks go: what the flood cuts off is listed to be shed as a ship of its own,
 * and taken off the list again as the flood reaches it from the rest of her. A block put back meanwhile, as a hatch
 * shuts or a ramp's plate moves, takes its heat from the neighbours it lands beside but stays on the list, and the
 * flood, finding it warm already, never takes it off: at the end it is shed with whatever else is left listed. Loaded
 * from a save, a ship's heat is laid out so that a few blocks going starts a flood that outlasts the fold of the
 * freighter's ramp: its hatch shut in the middle of one, and pieces of her belly fell under her and in the way of her
 * ramp. Just before anything is shed, a listed block that has heat, so reaches the rest of her, or that is no longer
 * there at all, comes off the list; what is really cut off, solid and cold, is still shed.
 */
@Mixin(value = SubLevelHeatMapManager.class, remap = false)
abstract class SubLevelHeatMapManagerMixin {
    @Shadow
    @Final
    private Long2IntOpenHashMap subLevelSplits;

    @Shadow
    private boolean heatMapContains(BlockPos pos) {
        throw new AssertionError();
    }

    @Shadow
    private boolean isSolidAt(BlockPos pos) {
        throw new AssertionError();
    }

    @Inject(method = "split", at = @At("HEAD"), require = 1)
    private void skyliners$keepWhatIsAttached(CallbackInfo info) {
        subLevelSplits.keySet().removeIf((long packed) -> skyliners$stillAttachedOrGone(BlockPos.of(packed)));
    }

    /** Whether a listed block is not cut off after all: it has heat, had only through the rest of her, or has gone. */
    @Unique
    private boolean skyliners$stillAttachedOrGone(BlockPos pos) {
        return heatMapContains(pos) || !isSolidAt(pos);
    }
}
