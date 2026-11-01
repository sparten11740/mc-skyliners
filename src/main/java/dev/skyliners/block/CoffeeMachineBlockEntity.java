package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import dev.skyliners.block.CoffeeMachineBlock.Brew;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The coffee machine's clock: how far into the step of a brew it is, so each step (its block state) moves on to the
 * next once it has lasted its time. On the client it lets off steam: a wisp off the coffee as it pours and off the
 * drink waiting under the spout, and a cloud from the steam wand while it froths the milk.
 */
public class CoffeeMachineBlockEntity extends BlockEntity {
    /** Where the steam comes from, in pixels in the model drawn facing north: the wand's tip, and the cup's middle. */
    private static final double WAND_X = 13;
    private static final double WAND_Y = 3;
    private static final double WAND_Z = 2.5;
    private static final double CUP_X = 8;
    private static final double CUP_Z = 5;
    /**
     * The top of each drink's cup, in pixels: the small espresso cup, the cappuccino's, the mocha's tall glass and the
     * babycino's little glass.
     */
    private static final double ESPRESSO_TOP = 3.5;
    private static final double CAPPUCCINO_TOP = 4.5;
    private static final double MOCHA_TOP = 5.5;
    private static final double BABYCINO_TOP = 4;
    /** How likely a wisp off the cup is each tick, as it pours and once done; puffs from the wand each tick. */
    private static final float POURING_WISP = 0.3F;
    private static final float DONE_WISP = 0.08F;
    private static final int WAND_PUFFS = 2;
    /** How fast steam rises, and how far a puff from the wand spreads, in blocks a tick. */
    private static final double RISE = 0.03;
    private static final double SPREAD = 0.02;

    private int ticks;

    public CoffeeMachineBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.COFFEE_MACHINE_ENTITY.get(), pos, state);
    }

    /** Whether a player can work the machine from where they are; with Sable, reach is measured aboard an airliner. */
    public boolean canBeUsedBy(Player player) {
        return !player.isSpectator() && Container.stillValidBlockEntity(this, player);
    }

    static void serverTick(Level level, BlockPos pos, BlockState state, CoffeeMachineBlockEntity machine) {
        Brew brew = state.getValue(CoffeeMachineBlock.BREW);
        if (!brew.busy()) {
            machine.ticks = 0;
            return;
        }
        machine.setChanged();
        if (++machine.ticks >= brew.ticks()) {
            machine.ticks = 0;
            CoffeeMachineBlock.advance((ServerLevel) level, pos, state);
        }
    }

    static void clientTick(Level level, BlockPos pos, BlockState state, CoffeeMachineBlockEntity machine) {
        RandomSource random = level.getRandom();
        Direction facing = state.getValue(CoffeeMachineBlock.FACING);
        double top = switch (state.getValue(CoffeeMachineBlock.DRINK)) {
            case ESPRESSO -> ESPRESSO_TOP;
            case CAPPUCCINO -> CAPPUCCINO_TOP;
            case MOCHA -> MOCHA_TOP;
            case BABYCINO -> BABYCINO_TOP;
        };
        switch (state.getValue(CoffeeMachineBlock.BREW)) {
            case POURING -> wisp(level, CoffeeMachineBlock.at(pos, facing, CUP_X, top, CUP_Z), random, POURING_WISP);
            case DONE -> wisp(level, CoffeeMachineBlock.at(pos, facing, CUP_X, top, CUP_Z), random, DONE_WISP);
            case STEAMING -> {
                Vec3 wand = CoffeeMachineBlock.at(pos, facing, WAND_X, WAND_Y, WAND_Z);
                for (int i = 0; i < WAND_PUFFS; i++) {
                    level.addParticle(ParticleTypes.WHITE_SMOKE, wand.x, wand.y, wand.z,
                        random.triangle(0, SPREAD), RISE, random.triangle(0, SPREAD));
                }
                wisp(level, CoffeeMachineBlock.at(pos, facing, CUP_X, top, CUP_Z), random, POURING_WISP);
            }
            case READY, GRINDING -> {
            }
        }
    }

    /** Now and then, a wisp of steam rising off a hot drink. */
    private static void wisp(Level level, Vec3 at, RandomSource random, float chance) {
        if (random.nextFloat() < chance) {
            level.addParticle(ParticleTypes.WHITE_SMOKE, at.x + random.triangle(0, 0.03), at.y, at.z + random.triangle(0, 0.03), 0, RISE, 0);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ticks", ticks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ticks = tag.getInt("ticks");
    }
}
