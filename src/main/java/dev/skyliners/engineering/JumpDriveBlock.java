package dev.skyliners.engineering;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.compat.SableFlight;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The freighter's jump drive in engineering (see {@link JumpDriveBlockEntity}): a dark steel plinth with a glowing core
 * over it in three gyroscope rings, which the client's {@code JumpDriveRenderer} draws turning. Use it and it spools up
 * with a rising whine, its rings spinning faster and glowing brighter blue; then, mostly, it fires, with a deep whump
 * and a burst of light, and runs a while, rings racing, before it winds down. But now and then it sputters instead:
 * its rings slow and turn red, smoke puffs from it with a falling cough, and it stays so until someone thumps it, using
 * it again, when it roars back to life with a clang and fires; coughing every few seconds and shuddering until then,
 * asking for it. Every step shows in its rings' colour and speed as well as in its sound. Used while busy, it only
 * clicks. FACING is the way its plinth's panel faces.
 */
public class JumpDriveBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<JumpDriveBlock> CODEC = simpleCodec(JumpDriveBlock::new);
    public static final EnumProperty<Drive> DRIVE = EnumProperty.create("drive", Drive.class);
    /** How long it spools up before it fires or sputters, and how long it runs once fired, in ticks. */
    private static final int SPOOL_TICKS = 60;
    private static final int RUN_TICKS = 120;
    /** How likely it is to sputter once spooled up, rather than fire. */
    private static final float SPUTTER_CHANCE = 0.35F;
    /** How often a sputtering drive coughs, in ticks, asking to be thumped, and how loud. */
    private static final int COUGH_TICKS = 50;
    private static final float COUGH_VOLUME = 0.5F;
    /** How loud its click is when it is used while busy, and its other sounds. */
    private static final float BUSY_VOLUME = 0.4F;
    private static final float VOLUME = 1.0F;
    /** How brightly it glows at each step, in light levels: dim at rest, brighter spooling, brightest running. */
    private static final int REST_GLOW = 4;
    private static final int SPOOL_GLOW = 9;
    private static final int RUN_GLOW = 13;
    private static final int SPUTTER_GLOW = 6;
    /**
     * Where its particles come from: so high over its block's foot, round its core, so far out at most, in blocks; and
     * how fast its motes and smoke rise, in blocks a tick.
     */
    private static final double CORE_UP = 1.9;
    private static final double PARTICLE_SPREAD = 0.4;
    private static final double MOTE_RISE = 0.04;
    /** Its plinth and the column its core turns over, to bump into. */
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 6, 15), Block.box(5, 6, 5, 11, 16, 11));

    /** Where the drive stands: at rest, spooling up, running, or sputtering until it is thumped. */
    public enum Drive implements StringRepresentable {
        REST, SPOOLING, RUNNING, SPUTTERING;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public JumpDriveBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DRIVE, Drive.REST));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DRIVE);
    }

    /** How brightly it glows at the step it stands at. */
    public static int glow(BlockState state) {
        return switch (state.getValue(DRIVE)) {
            case REST -> REST_GLOW;
            case SPOOLING -> SPOOL_GLOW;
            case RUNNING -> RUN_GLOW;
            case SPUTTERING -> SPUTTER_GLOW;
        };
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** At rest it spools up; sputtering it is thumped back to life; busy otherwise, it only clicks. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel server) || player.isSpectator()) {
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        switch (state.getValue(DRIVE)) {
            case REST -> step(server, pos, state, Drive.SPOOLING, SkylinersRegistry.JUMP_DRIVE_SPOOL.get(), SPOOL_TICKS);
            case SPUTTERING -> thump(server, pos, state);
            default -> busy(server, pos);
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Used while busy: only a click, and its next step at once where that was lost, as when she was assembled in the
     * middle of it.
     */
    private void busy(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_OFF, SoundSource.BLOCKS, BUSY_VOLUME, 1.0F);
        if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /**
     * Thumped while it sputters: a clang, sparks, and it roars back to life and fires. Its next cough is struck off
     * first, as the level keeps only one waiting tick a block, so it would otherwise end its run early.
     */
    private void thump(ServerLevel level, BlockPos pos, BlockState state) {
        level.getBlockTicks().clearArea(new BoundingBox(pos));
        level.playSound(null, pos, SkylinersRegistry.JUMP_DRIVE_THUMP.get(), SoundSource.BLOCKS, VOLUME, 1.0F);
        step(level, pos, state, Drive.RUNNING, SkylinersRegistry.JUMP_DRIVE_JUMP.get(), RUN_TICKS);
    }

    /** On to a step, with its sound, and on again after so many ticks, if any. */
    private void step(ServerLevel level, BlockPos pos, BlockState state, Drive drive, SoundEvent sound, int ticks) {
        level.setBlock(pos, state.setValue(DRIVE, drive), Block.UPDATE_ALL);
        level.playSound(null, pos, sound, SoundSource.BLOCKS, VOLUME, 1.0F);
        if (ticks > 0) {
            level.scheduleTick(pos, this, ticks);
        }
    }

    /**
     * Her hyperspace jump begun from her cockpit (see {@code FreighterJump}): a drive at rest at a position, if one is
     * there, spools up with its whine and holds there until she jumps, with no tick of its own to fire or sputter by.
     */
    public static void spoolForJump(ServerLevel level, @Nullable BlockPos pos) {
        BlockState state = pos == null ? null : level.getBlockState(pos);
        if (state != null && state.getBlock() instanceof JumpDriveBlock && state.getValue(DRIVE) == Drive.REST) {
            level.setBlock(pos, state.setValue(DRIVE, Drive.SPOOLING), Block.UPDATE_ALL);
            level.playSound(null, pos, SkylinersRegistry.JUMP_DRIVE_SPOOL.get(), SoundSource.BLOCKS, VOLUME, 1.0F);
        }
    }

    /** She jumps: the drive, if one is there, fires with its whump and runs a while before it winds down. */
    public static void fireForJump(ServerLevel level, @Nullable BlockPos pos) {
        BlockState state = pos == null ? null : level.getBlockState(pos);
        if (state != null && state.getBlock() instanceof JumpDriveBlock drive) {
            level.getBlockTicks().clearArea(new BoundingBox(pos));
            drive.step(level, pos, state, Drive.RUNNING, SkylinersRegistry.JUMP_DRIVE_JUMP.get(), RUN_TICKS);
        }
    }

    /** Her jump called off: the drive, if one is spooling for it, falls back to rest. */
    public static void spinDown(ServerLevel level, @Nullable BlockPos pos) {
        BlockState state = pos == null ? null : level.getBlockState(pos);
        if (state != null && state.getBlock() instanceof JumpDriveBlock && state.getValue(DRIVE) == Drive.SPOOLING) {
            level.setBlock(pos, state.setValue(DRIVE, Drive.REST), Block.UPDATE_ALL);
        }
    }

    /** Spooled up, it fires or now and then sputters; run a while, it winds down to rest. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Drive drive = state.getValue(DRIVE);
        if (drive == Drive.SPOOLING) {
            spooled(level, pos, state, random);
        } else if (drive == Drive.RUNNING) {
            level.setBlock(pos, state.setValue(DRIVE, Drive.REST), Block.UPDATE_ALL);
        } else if (drive == Drive.SPUTTERING) {
            cough(level, pos);
        }
    }

    /** Sputtering, a cough now and then, its smoke puffing, asking to be thumped, until it is. */
    private void cough(ServerLevel level, BlockPos pos) {
        level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, COUGH_VOLUME, 0.6F);
        level.scheduleTick(pos, this, COUGH_TICKS);
    }

    /** Spooled up: it fires and runs a while, or now and then sputters until it is thumped. */
    private void spooled(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (random.nextFloat() < SPUTTER_CHANCE) {
            step(level, pos, state, Drive.SPUTTERING, SkylinersRegistry.JUMP_DRIVE_SPUTTER.get(), COUGH_TICKS);
        } else {
            step(level, pos, state, Drive.RUNNING, SkylinersRegistry.JUMP_DRIVE_JUMP.get(), RUN_TICKS);
        }
    }

    /**
     * On the client, what shows it working or failing round its core, where its block is in the world as she flies:
     * sparks crackling while it runs, a few motes rising as it spools, and smoke puffing while it sputters.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Drive drive = state.getValue(DRIVE);
        if (drive == Drive.REST) {
            return;
        }
        Vec3 near = new Vec3(pos.getX() + 0.5 + random.nextGaussian() * PARTICLE_SPREAD, pos.getY() + CORE_UP,
            pos.getZ() + 0.5 + random.nextGaussian() * PARTICLE_SPREAD);
        Vec3 at = SableFlight.toWorld(level, near);
        switch (drive) {
            case RUNNING -> level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + random.nextGaussian() * PARTICLE_SPREAD, at.z, 0, 0, 0);
            case SPOOLING -> level.addParticle(ParticleTypes.END_ROD, at.x, at.y, at.z, 0, MOTE_RISE, 0);
            default -> level.addParticle(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 0, MOTE_RISE, 0);
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JumpDriveBlockEntity(pos, state);
    }
}
