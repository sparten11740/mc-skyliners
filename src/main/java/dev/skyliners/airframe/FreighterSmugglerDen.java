package dev.skyliners.airframe;

import dev.skyliners.Skyliners;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.BunkBlock;
import dev.skyliners.block.CargoCrateBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The freighter's smugglers' hold, in the tip of her starboard prong. The tunnel down the prong seems to end short of
 * the port prong's, at a wall of riveted hull plate across it, a few blocks before her nose jet's casing (see
 * {@link FreighterEngineBays}). One panel of the wall, beside the casing, is a secret panel (see
 * {@link dev.skyliners.block.SecretPanelBlock}), a shade darker than the rest and scuffed, wisps of draught puffing out
 * of its seams; and in the wall beside it, at eye height, one rivet is brass, glinting: the loose rivet that opens it
 * (see {@link dev.skyliners.block.LooseRivetBlock}), which it touches through the plate it is set in. Behind it a
 * narrow way runs past the casing to a cramped den against the engine bulkhead across the tip: a cot along her hull,
 * contraband crates, barrels of smuggled goods, one with a lantern on it, a mail sack, and a chest of loot.
 *
 * <p>Found from her nose jet's relay, as her layout lays it, and from where her tunnel is open, so it follows her
 * shape. Nothing that a redstone pulse sets off, as her care packages are sent off, stands beside one of her
 * thrusters' relays, whose signal it would take for one. Laid out nose north, before her portholes, which keep clear
 * of it; none with the server config's {@code freighter.smugglerHold} off.
 */
final class FreighterSmugglerDen {
    /** What the chest holds and what the barrel with the lantern on it holds, from loot tables of the mod's own. */
    private static final ResourceKey<LootTable> STASH =
        ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "chests/smuggler_stash"));
    private static final ResourceKey<LootTable> CONTRABAND =
        ResourceKey.create(Registries.LOOT_TABLE, ResourceLocation.fromNamespaceAndPath(Skyliners.MOD_ID, "chests/smuggler_contraband"));
    /** How far astern of her nose jet's casing the wall across her tunnel stands, in blocks: a block of way between. */
    private static final int WALL_ASTERN = FreighterEngineBays.CASING_ALONG + 1;
    /** How far toward her middle from the jet's relay the secret panel stands, in blocks: just past its casing. */
    private static final int PANEL_IN = 2;
    /** How far across her tunnel, and how many rows over her deck, it is looked along, in blocks. */
    private static final int TUNNEL_REACH = 24;
    private static final int ROWS = 3;
    /** How far toward her tip the den is looked along at most, in blocks. */
    private static final int DEN_REACH = 12;
    /** The cot's blanket. */
    private static final DyeColor BLANKET = DyeColor.BROWN;

    private final HullLayout layout;
    private final int deck;

    private FreighterSmugglerDen(HullLayout layout, int deck) {
        this.layout = layout;
        this.deck = deck;
    }

    /**
     * Lays the hold in a freighter's layout whose deck's floor is the row given, round her starboard nose jet, whose
     * relay is given, pushing toward her middle line; none where she has no such jet.
     */
    static void build(HullLayout layout, int deck, @Nullable BlockPos jetRelay) {
        if (SkylinersConfig.FREIGHTER_SMUGGLER_HOLD.get() && jetRelay != null) {
            new FreighterSmugglerDen(layout, deck).lay(jetRelay);
        }
    }

    private void lay(BlockPos jetRelay) {
        int wallZ = jetRelay.getZ() + WALL_ASTERN;
        int panelX = jetRelay.getX() - PANEL_IN;
        int inner = tunnelEdge(wallZ);
        int denZ = jetRelay.getZ() - FreighterEngineBays.CASING_ALONG - 1;
        if (inner < 0 || inner > panelX || !openInside(panelX, deck + 2, wallZ)) {
            return;
        }
        wall(wallZ);
        panel(panelX, wallZ);
        rivet(panelX + 1, wallZ);
        furnish(tunnelEdge(denZ), denZ);
    }

    /** The wall across her tunnel: riveted hull plate in every open block of it. */
    private void wall(int z) {
        BlockState plate = SkylinersRegistry.HULL_PLATE_RIVETED.get().defaultBlockState();
        for (int x = 0; x <= TUNNEL_REACH; x++) {
            for (int y = deck + 1; y <= deck + ROWS; y++) {
                piece(x, y, z, plate);
            }
        }
    }

    /** The secret panel in the wall, both its halves, its front to her tunnel, swinging open toward her tip. */
    private void panel(int x, int z) {
        BlockState panel = SkylinersRegistry.SECRET_PANEL.get().defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
            .setValue(DoorBlock.HINGE, DoorHingeSide.LEFT);
        layout.set(x, deck + 1, z, panel.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        layout.set(x, deck + 2, z, panel.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /** The loose rivet on the wall's face toward her tunnel, at eye height, set in the plate beside the panel's top. */
    private void rivet(int x, int z) {
        if (layout.stateAt(x, deck + 2, z).is(SkylinersRegistry.HULL_PLATE_RIVETED.get()) && openInside(x, deck + 2, z + 1)) {
            layout.set(x, deck + 2, z + 1, SkylinersRegistry.LOOSE_RIVET.get().defaultBlockState()
                .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, Direction.SOUTH));
        }
    }

    /**
     * The den against the bulkhead across her tip, from the row before her nose jet's casing toward it: along the
     * bulkhead, from her hull in, the cot's head, two crates, one stacked on the other, a crate, the chest, a barrel of
     * contraband with the lantern on it and two barrels stacked; the cot runs back along her hull, a mail sack past its
     * foot.
     */
    private void furnish(int inner, int fromZ) {
        int tip = fromZ;
        while (tip > fromZ - DEN_REACH && openInside(inner, deck + 1, tip - 1)) {
            tip--;
        }
        int outer = inner;
        while (openInside(outer + 1, deck + 1, tip)) {
            outer++;
        }
        cot(outer, tip);
        crate(outer - 1, tip, 2);
        crate(outer - 2, tip, 1);
        chest(outer - 3, tip);
        barrel(outer - 4, tip, CONTRABAND);
        piece(outer - 4, deck + 2, tip, Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, false));
        barrel(outer - 5, tip, null);
        barrel(outer - 5, tip, null);
        piece(outer, deck + 1, tip + 2, SkylinersRegistry.MAIL_SACK.get().defaultBlockState());
    }

    /** The cot along her hull, its head against the bulkhead, its foot toward her tail, open toward her middle. */
    private void cot(int x, int z) {
        BlockState head = SkylinersRegistry.BUNK.get().defaultBlockState().setValue(BunkBlock.FACING, Direction.WEST)
            .setValue(BunkBlock.COLOR, BLANKET).setValue(BunkBlock.TIER, BunkBlock.Tier.SINGLE).setValue(BunkBlock.LIT, false);
        BlockPos foot = new BlockPos(x, deck + 1, z).relative(BunkBlock.footward(Direction.WEST));
        if (openInside(x, deck + 1, z) && openInside(foot.getX(), foot.getY(), foot.getZ())) {
            layout.set(x, deck + 1, z, head.setValue(BunkBlock.PART, BedPart.HEAD));
            layout.set(foot.getX(), foot.getY(), foot.getZ(), head.setValue(BunkBlock.PART, BedPart.FOOT));
        }
    }

    /**
     * Care-package crates stacked so many high on the floor at a column, facing into the den; a barrel in place of any
     * beside one of her thrusters' relays, whose signal would send a crate off.
     */
    private void crate(int x, int z, int high) {
        for (int y = deck + 1; y < deck + 1 + high; y++) {
            BlockState crate = SkylinersRegistry.CARGO_CRATE.get().defaultBlockState()
                .setValue(CargoCrateBlock.FACING, y == deck + 1 ? Direction.SOUTH : Direction.EAST);
            piece(x, y, z, besideRelay(x, y, z) ? Blocks.BARREL.defaultBlockState() : crate);
        }
    }

    /** The chest of loot, facing into the den, filled from the stash's loot table as she is built. */
    private void chest(int x, int z) {
        if (piece(x, deck + 1, z, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.SOUTH))) {
            fill(new BlockPos(x, deck + 1, z), STASH);
        }
    }

    /** A barrel on the floor at a column, or on the one there, lid up, filled from a loot table if one is given. */
    private void barrel(int x, int z, @Nullable ResourceKey<LootTable> loot) {
        int y = openInside(x, deck + 1, z) ? deck + 1 : deck + 2;
        if (piece(x, y, z, Blocks.BARREL.defaultBlockState().setValue(BarrelBlock.FACING, Direction.UP)) && loot != null) {
            fill(new BlockPos(x, y, z), loot);
        }
    }

    private void fill(BlockPos at, ResourceKey<LootTable> loot) {
        layout.fit(at, (blockEntity, rotation) -> {
            if (blockEntity instanceof RandomizableContainerBlockEntity container) {
                container.setLootTable(loot, 0);
            }
        });
    }

    /** Sets a block where her hull is open inside, and nowhere else; whether it went in. */
    private boolean piece(int x, int y, int z, BlockState state) {
        if (!openInside(x, y, z)) {
            return false;
        }
        layout.set(x, y, z, state);
        return true;
    }

    /** Whether one of her thrusters' relays stands beside a block, face to face. */
    private boolean besideRelay(int x, int y, int z) {
        for (Direction side : Direction.values()) {
            if (layout.stateAt(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ()).is(SkylinersRegistry.THRUST_RELAY.get())) {
                return true;
            }
        }
        return false;
    }

    /** The column of her starboard tunnel nearest her middle line on a row across her; -1 for none. */
    private int tunnelEdge(int z) {
        for (int x = 0; x <= TUNNEL_REACH; x++) {
            if (openInside(x, deck + 1, z)) {
                return x;
            }
        }
        return -1;
    }

    private boolean openInside(int x, int y, int z) {
        return layout.isOpen(x, y, z) && layout.surface().depth(x, y + 0.5, z) < 0;
    }
}
