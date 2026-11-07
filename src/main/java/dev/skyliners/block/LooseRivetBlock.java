package dev.skyliners.block;

import dev.skyliners.compat.SableFlight;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A loose rivet, a hidden switch: one brass rivet head on a wall, a floor or a ceiling, no bigger than a fingertip.
 * Pressed, it sinks in with a click and gives off a redstone pulse as a stone button does, so whatever stands beside
 * it opens, as the secret panel does (see {@link SecretPanelBlock}). Its brass gleams a little in the dark, the hint,
 * and while a player is near it glints now and then, with a faint chime.
 */
public class LooseRivetBlock extends ButtonBlock {
    /** How long it stays in once pressed, in ticks: a stone button's. */
    private static final int PRESS_TICKS = 20;
    /** Half its head's width as it is clicked, in pixels, a little wider than drawn, and how far it stands out. */
    private static final double HALF = 2.5;
    private static final double OUT = 1.5;
    private static final double PRESSED_OUT = 0.5;
    private static final Map<Direction, VoxelShape> OUT_SHAPES = shapes(OUT);
    private static final Map<Direction, VoxelShape> PRESSED_SHAPES = shapes(PRESSED_OUT);
    /** How near a player has to be for it to glint, in blocks. */
    private static final double HINT_RANGE = 6;
    /** Of the client's random visits, the share that also chimes, and how loud. */
    private static final float CHIME_CHANCE = 0.15F;
    private static final float CHIME_VOLUME = 0.35F;
    /** How far out of what it is on its glint shows, in blocks. */
    private static final double GLINT_OUT = 0.12;

    public LooseRivetBlock(Properties properties) {
        super(BlockSetType.IRON, PRESS_TICKS, properties);
    }

    /** Its head standing so far out of what it is on, for each way it can look out from it. */
    private static Map<Direction, VoxelShape> shapes(double out) {
        double from = 8 - HALF;
        double to = 8 + HALF;
        Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);
        shapes.put(Direction.UP, Block.box(from, 0, from, to, out, to));
        shapes.put(Direction.DOWN, Block.box(from, 16 - out, from, to, 16, to));
        shapes.put(Direction.NORTH, Block.box(from, from, 16 - out, to, to, 16));
        shapes.put(Direction.SOUTH, Block.box(from, from, 0, to, to, out));
        shapes.put(Direction.WEST, Block.box(16 - out, from, from, 16, to, to));
        shapes.put(Direction.EAST, Block.box(0, from, from, out, to, to));
        return shapes;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return (state.getValue(POWERED) ? PRESSED_SHAPES : OUT_SHAPES).get(getConnectedDirection(state));
    }

    /**
     * The hint: while a player is near and it is out, a glint of gold on its head, and now and then a faint chime.
     * Aboard a ship it stands in her plot, far from the players round her, so how near they are, and where the chime
     * is heard, is where it is in the world.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Vec3 there = SableFlight.toWorld(level, Vec3.atCenterOf(pos));
        if (state.getValue(POWERED) || level.getNearestPlayer(there.x, there.y, there.z, HINT_RANGE, false) == null) {
            return;
        }
        Direction out = getConnectedDirection(state);
        double fromMiddle = 0.5 - GLINT_OUT;
        level.addParticle(ParticleTypes.WAX_ON, pos.getX() + 0.5 - out.getStepX() * fromMiddle, pos.getY() + 0.5 - out.getStepY() * fromMiddle,
            pos.getZ() + 0.5 - out.getStepZ() * fromMiddle, 0, 0, 0);
        if (random.nextFloat() < CHIME_CHANCE) {
            level.playLocalSound(there.x, there.y, there.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, CHIME_VOLUME, 1.6F + random.nextFloat() * 0.3F, false);
        }
    }
}
