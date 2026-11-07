package dev.skyliners.floorwindow;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The round floor window as an item (see {@link FloorWindowBlock}): used on the top or underside of a floor, it lays
 * the whole window into that floor, its middle where it was used, every block of the disc taking the place of the floor
 * block there and blending into it, and over open air into the floor most common round it. It fits only where every
 * block of the disc is open or plain floor, a whole block with nothing kept in it that can be broken, loaded, and the
 * player may build there, out of the spawn's protection; anywhere else it only thuds and puffs, and is kept. It lays
 * itself as a builder's tool does, past the events by which claim mods guard each block placed.
 */
public class FloorWindowItem extends BlockItem {
    /** How far past the window's own blocks the floor it blends into is looked for, in blocks. */
    private static final int FLOOR_LOOK = 2;
    /** The floor a window laid over nothing but open air blends into. */
    private static final BlockState PLAIN_FLOOR = Blocks.SMOOTH_STONE.defaultBlockState();
    /** How loud its thud is where it does not fit, and its pitch. */
    private static final float THUD_VOLUME = 0.8F;
    private static final float THUD_PITCH = 0.5F;
    /** How many puffs of smoke show where it does not fit. */
    private static final int PUFFS = 8;

    public FloorWindowItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos middle = context.getClickedPos();
        boolean flat = context.getClickedFace().getAxis() == Direction.Axis.Y;
        if (!flat || !fits(level, middle, context.getPlayer(), context.getItemInHand())) {
            if (level instanceof ServerLevel server) {
                refuse(server, middle);
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            lay(server, middle, context.getHorizontalDirection());
            server.playSound(null, middle, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Whether a window fits round a middle block: every block of it open or plain floor, where one may build. */
    private static boolean fits(Level level, BlockPos middle, Player player, ItemStack stack) {
        for (BlockPos offset : FloorWindowBlock.footprint()) {
            BlockPos at = middle.offset(offset);
            if (!level.isLoaded(at) || !allowed(level, at, player, stack) || !fitsAt(level, at)) {
                return false;
            }
        }
        return true;
    }

    /** Whether a player may build at a block: out of the spawn's protection and where they may use what they hold. */
    private static boolean allowed(Level level, BlockPos at, Player player, ItemStack stack) {
        return player == null || level.mayInteract(player, at) && player.mayUseItemAt(at, Direction.UP, stack);
    }

    /** Whether a block of a window may take the place of what is at a block: open, or a whole block to break. */
    private static boolean fitsAt(Level level, BlockPos at) {
        BlockState state = level.getBlockState(at);
        if (state.canBeReplaced()) {
            return true;
        }
        boolean plain = state.isCollisionShapeFullBlock(level, at) && !state.hasBlockEntity();
        return plain && state.getDestroySpeed(level, at) >= 0 && !(state.getBlock() instanceof FloorWindowBlock);
    }

    /** Lays a window round a middle block, facing the way given, each block keeping the floor it took the place of. */
    private void lay(ServerLevel level, BlockPos middle, Direction facing) {
        BlockState around = floorAround(level, middle);
        BlockState window = getBlock().defaultBlockState().setValue(FloorWindowBlock.FACING, facing);
        for (BlockPos offset : FloorWindowBlock.footprint()) {
            BlockPos at = middle.offset(offset);
            BlockState was = level.getBlockState(at);
            BlockState replaced = was.canBeReplaced() ? Blocks.AIR.defaultBlockState() : was;
            level.setBlock(at, window, Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof FloorWindowBlockEntity laid) {
                laid.configure(BlockPos.ZERO.subtract(offset), replaced.isAir() ? around : replaced, replaced);
            }
        }
    }

    /** The floor most common in and round a window's blocks: whole blocks keeping nothing; plain stone for none. */
    private static BlockState floorAround(Level level, BlockPos middle) {
        int reach = (int) Math.ceil(FloorWindowBlock.RADIUS) + FLOOR_LOOK;
        Map<BlockState, Integer> counted = new HashMap<>();
        for (BlockPos at : BlockPos.betweenClosed(middle.offset(-reach, 0, -reach), middle.offset(reach, 0, reach))) {
            BlockState state = level.getBlockState(at);
            if (state.isCollisionShapeFullBlock(level, at) && !state.hasBlockEntity() && !(state.getBlock() instanceof FloorWindowBlock)) {
                counted.merge(state, 1, Integer::sum);
            }
        }
        return counted.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(PLAIN_FLOOR);
    }

    /** Where a window does not fit: a thud and a puff of smoke, and the window kept. */
    private static void refuse(ServerLevel level, BlockPos middle) {
        level.playSound(null, middle, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, THUD_VOLUME, THUD_PITCH);
        level.sendParticles(ParticleTypes.SMOKE, middle.getX() + 0.5, middle.getY() + 1.1, middle.getZ() + 0.5, PUFFS, 0.3, 0.05, 0.3, 0.01);
    }
}
