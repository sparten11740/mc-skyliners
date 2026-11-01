package dev.skyliners.airframe;

import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.CrewLockerBlock;
import dev.skyliners.block.CrewLockerBlockEntity;
import dev.skyliners.block.JumpSwitchBlock;
import dev.skyliners.block.LinkedControlBlockEntity;
import dev.skyliners.block.PaperPlaneBoxBlock;
import dev.skyliners.block.ParachuteRackBlock;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Jumping and throwing from the top of the zeppelin, on the Sky Deck (see {@link ZeppelinSkyDeck}). Along its port
 * side, facing in: a rack of parachutes, the jump switch and the jump light it works, red to stand by and green to go,
 * with a bell, by a gap in the deck's rail, the way out onto the crown to jump from, down its curve and off the flank
 * (whoever jumps without a parachute is caught by the safety canopy); along its starboard side, facing in: a box of
 * paper planes to throw off the top of the airship, and a
 * locker holding a landing target and signal flares, to lay out and light on the ground below as a drop zone, where
 * every landing and every paper plane that comes down near the target is called out. Everything stands on the deck's
 * grating, the jump light over the relay that lights it, so it is assembled with the zeppelin; nothing stands on it.
 * Without the Sky Deck, there is nothing to stand it on, and it is left out.
 */
final class ZeppelinSkyDeckLaunch implements ZeppelinPiece {
    /** How far out from the middle, to port and to starboard, the things on the deck stand, in blocks. */
    private static final int OUT = 2;
    /** How far fore or aft of the deck's middle each stands, in rows: aft is positive. */
    private static final int RACK_AFT = 2;
    private static final int SWITCH_AFT = 1;
    private static final int LIGHT_AFT = 0;
    private static final int BOX_AFT = 2;
    private static final int LOCKER_AFT = -2;
    /** How many signal flares the locker holds. */
    private static final int FLARES = 4;

    private final ZeppelinLayout layout;

    ZeppelinSkyDeckLaunch(ZeppelinLayout layout) {
        this.layout = layout;
    }

    @Override
    public boolean wanted(ZeppelinOptions options) {
        return SkylinersConfig.SKY_DECK_LAUNCH.get();
    }

    /** On the Sky Deck, the same at every size. */
    @Override
    public boolean fits(ZeppelinSize size) {
        return true;
    }

    @Override
    public void build() {
        int z = ZeppelinSkyDeck.station(layout);
        int deck = ZeppelinSkyDeck.floor(layout);
        if (!layout.stateAt(0, deck, z).is(SkylinersRegistry.ZEPPELIN_CATWALK.get())) {
            return;
        }
        int y = deck + 1;
        place(-OUT, y, z + RACK_AFT, SkylinersRegistry.PARACHUTE_RACK.get().defaultBlockState().setValue(ParachuteRackBlock.FACING, Direction.EAST));
        jumpLight(y, z);
        place(OUT, y, z + BOX_AFT, SkylinersRegistry.PAPER_PLANE_BOX.get().defaultBlockState().setValue(PaperPlaneBoxBlock.FACING, Direction.WEST));
        locker(y, z + LOCKER_AFT);
    }

    /**
     * The jump light, the relay under it in the grating that lights it, the gap in the rail beside it, and the switch
     * by it wired to the relay.
     */
    private void jumpLight(int y, int z) {
        BlockPos light = new BlockPos(-OUT, y, z + LIGHT_AFT);
        BlockPos at = new BlockPos(-OUT, y, z + SWITCH_AFT);
        if (!place(light.getX(), light.getY(), light.getZ(), SkylinersRegistry.JUMP_LIGHT.get().defaultBlockState())) {
            return;
        }
        BlockPos relay = light.below();
        layout.set(relay.getX(), relay.getY(), relay.getZ(), SkylinersRegistry.HULL_RELAY.get().defaultBlockState());
        if (layout.stateAt(light.getX() - 1, y, light.getZ()).is(SkylinersRegistry.CABIN_RAIL.get())) {
            layout.clear(light.getX() - 1, y, light.getZ());
        }
        if (!place(at.getX(), at.getY(), at.getZ(), SkylinersRegistry.JUMP_SWITCH.get().defaultBlockState().setValue(JumpSwitchBlock.FACING, Direction.EAST))) {
            return;
        }
        layout.fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof LinkedControlBlockEntity control) {
                control.configure(Map.of(JumpSwitchBlock.LIGHTS, List.of(relay.subtract(at).rotate(rotation))));
            }
        });
    }

    /** The locker, its door to the deck, holding a landing target and flares. */
    private void locker(int y, int z) {
        BlockState locker = SkylinersRegistry.CREW_LOCKER.get().defaultBlockState().setValue(CrewLockerBlock.FACING, Direction.WEST);
        if (!place(OUT, y, z, locker)) {
            return;
        }
        layout.fit(new BlockPos(OUT, y, z), (blockEntity, rotation) -> {
            if (blockEntity instanceof CrewLockerBlockEntity stock) {
                stock.setItem(0, new ItemStack(SkylinersRegistry.LANDING_TARGET_ITEM.get()));
                stock.setItem(1, new ItemStack(SkylinersRegistry.SIGNAL_FLARE_ITEM.get(), FLARES));
            }
        });
    }

    /** Stands something on the deck's grating where it is free; whether it did. */
    private boolean place(int x, int y, int z, BlockState state) {
        if (!layout.isOpen(x, y, z) || !layout.stateAt(x, y - 1, z).is(SkylinersRegistry.ZEPPELIN_CATWALK.get())) {
            return false;
        }
        layout.set(x, y, z, state);
        return true;
    }
}
