package dev.skyliners.airframe;

import dev.skyliners.SkylinersRegistry;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The freighter's engine bays in her prongs' tips, so nobody walking down the tunnel in either prong comes face to face
 * with a thruster, nor reaches one: across the very tip a bulkhead of glowing engine grating, her retro's relay set
 * into it and the retro behind it in her skin; and round her nose jet's relay a housing two blocks high, engine grating
 * below and a hazard-striped top, along her a block either side of it, from the relay out through the jet and its
 * exhaust to her hull. Her nose jets stand in her prongs' outer walls (see {@code FreighterLayout.jet}), so there the
 * housing is a frame round the relay against the wall, its face showing; a jet out in a passage would be boxed in
 * whole. Inside the housing the jet's exhaust is left open straight out from its nozzle, as it was, so the jet pushes
 * as hard as ever (a thruster's push falls with what stands in that line, and only that line). Every block of both is
 * held as her frame is (see {@code FrameProtection}), so the thrusters behind them are out of sight and out of reach.
 * Laid out nose north, as the rest of her, straight after her engines; always, with her engines.
 */
final class FreighterEngineBays {
    /** How many rows over her deck the tip's bulkhead and a jet's housing reach up: her prongs' headroom there. */
    private static final int ROWS = 2;
    /** How far across her the tip's bulkhead is looked for either side of the relay set in it, in blocks. */
    private static final int BULKHEAD_REACH = 6;
    /** How far along her a jet's housing reaches either side of the jet, in blocks. */
    static final int HOUSING_ALONG = 1;
    /** How far out from its jet a housing follows its exhaust at most, in blocks, should her hull never stop it. */
    private static final int EXHAUST_REACH = 6;

    private final HullLayout layout;
    private final int deck;

    FreighterEngineBays(HullLayout layout, int deck) {
        this.layout = layout;
        this.deck = deck;
    }

    /** The bulkhead across a prong's tip in the row of the retro's relay given, every open block of it. */
    void bulkhead(BlockPos relay) {
        BlockState grating = SkylinersRegistry.ENGINE_GRATING.get().defaultBlockState();
        for (int x = relay.getX() - BULKHEAD_REACH; x <= relay.getX() + BULKHEAD_REACH; x++) {
            for (int y = deck + 1; y <= deck + ROWS; y++) {
                fill(x, y, relay.getZ(), grating);
            }
        }
    }

    /**
     * The housing round a nose jet whose relay is given, pushing the way given: from the relay's column out through the
     * jet and its exhaust to her hull, which are left as they are, the relay's face toward her middle line showing.
     */
    void housing(@Nullable BlockPos relay, Direction pushes) {
        if (relay == null) {
            return;
        }
        List<BlockPos> run = run(relay, pushes.getOpposite());
        BlockPos to = run.getLast();
        BlockState grating = SkylinersRegistry.ENGINE_GRATING.get().defaultBlockState();
        BlockState top = SkylinersRegistry.HAZARD_PLATE.get().defaultBlockState();
        for (int x = Math.min(relay.getX(), to.getX()); x <= Math.max(relay.getX(), to.getX()); x++) {
            for (int z = relay.getZ() - HOUSING_ALONG; z <= relay.getZ() + HOUSING_ALONG; z++) {
                housingColumn(run, x, z, grating, top);
            }
        }
    }

    /** One column of a housing: grating in its lowest row, hazard plate over it, but where the jet's run is. */
    private void housingColumn(List<BlockPos> run, int x, int z, BlockState grating, BlockState top) {
        for (int y = deck + 1; y <= deck + ROWS; y++) {
            if (!run.contains(new BlockPos(x, y, z))) {
                fill(x, y, z, y == deck + 1 ? grating : top);
            }
        }
    }

    /**
     * A jet's run, from its relay through the jet and on along its exhaust, the way given, as far as her hull's open
     * air goes.
     */
    private List<BlockPos> run(BlockPos relay, Direction exhaust) {
        List<BlockPos> run = new ArrayList<>(List.of(relay, relay.relative(exhaust)));
        for (int out = 0; out < EXHAUST_REACH; out++) {
            BlockPos next = run.getLast().relative(exhaust);
            if (!openInside(next.getX(), next.getY(), next.getZ())) {
                break;
            }
            run.add(next);
        }
        return run;
    }

    /** Sets a block where her hull is open inside, and nowhere else. */
    private void fill(int x, int y, int z, BlockState state) {
        if (openInside(x, y, z)) {
            layout.set(x, y, z, state);
        }
    }

    private boolean openInside(int x, int y, int z) {
        return layout.isOpen(x, y, z) && layout.surface().depth(x, y + 0.5, z) < 0;
    }
}
