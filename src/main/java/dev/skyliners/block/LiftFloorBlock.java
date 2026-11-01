package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.entity.LiftCar;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.EntityCollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The floor of a lift's car (see {@link LiftCar}), filling the shaft's opening from the lowest landing's floor to the
 * highest's (see {@link LiftMachineBlockEntity}): each block is solid only as far as the car's floor lies in it now, so
 * the floor rides up and down the shaft with the car, and whoever is in the car stands and walks about on it, at a
 * floor and on a ride, and walks out when the doors open. It is not drawn, the car drawing its own floor, and cannot be
 * picked out or broken; only the lift lays it, so it has no item and gives nothing back.
 */
public class LiftFloorBlock extends Block {
    private static final MapCodec<LiftFloorBlock> CODEC = simpleCodec(LiftFloorBlock::new);

    public LiftFloorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** Nothing to pick out: what is aimed at through it is what lies past it. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    /**
     * The car's floor as far as it lies in this block now (see {@link LiftCar#floorIn}); nothing without a car, but for
     * a player on a client that has no car yet (see {@link #awaitingCar}).
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        LiftCar car = LiftCar.inShaft(level, pos);
        if (car != null) {
            return car.floorIn(pos, context);
        }
        return awaitingCar(level, context) ? Shapes.block() : Shapes.empty();
    }

    /**
     * Whether this is a player moving on a client that has no car yet, for whom the block is solid: the car comes some
     * ticks after a player starts to move in a world just joined, and whoever was saved standing in it would by then
     * have dropped out of reach of its floor, down the whole shaft. It must be the player and nothing else: Sable asks
     * once what every block of a kind collides with, with no one and nowhere in particular, and keeps the answer for
     * the physics of every ship, on the server too, to which this block must stay nothing.
     */
    private static boolean awaitingCar(BlockGetter level, CollisionContext context) {
        boolean onClient = level instanceof Level found && found.isClientSide;
        return onClient && context instanceof EntityCollisionContext entity && entity.getEntity() instanceof Player;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }
}
