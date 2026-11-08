package dev.skyliners.floorwindow;

import dev.skyliners.block.Mount;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The round floor window as an item (see {@link FloorWindowBlock}): used on the face of a block, it sets the whole
 * window into the face it was used on, a floor, a wall or a ceiling, its glass flush with that face, every block of the
 * disc taking the place of the block there and blending into it. It takes the largest size that fits (see {@link
 * WindowDisc}): an odd size round the block it was used on, an even one round that block's corner nearest where it was
 * used, from nine blocks across on a broad floor down to two on a ledge two blocks wide. It fits only where every block
 * of the disc is plain floor, a whole block with nothing kept in it that can be broken, its face seen, not covered by a
 * block in front of it, loaded, and where the player may build, out of the spawn's protection; anywhere else it only
 * thuds and puffs, and is kept. It lays itself as a builder's tool does, past the events by which claim mods guard each
 * block placed.
 */
public class FloorWindowItem extends BlockItem {
    /** How loud its thud is where it does not fit, and its pitch. */
    private static final float THUD_VOLUME = 0.8F;
    private static final float THUD_PITCH = 0.5F;
    /** How many puffs of smoke show where it does not fit, and how far out from the face they show, in blocks. */
    private static final int PUFFS = 8;
    private static final double PUFF_OUT = 0.6;

    /**
     * Where a window goes: round the block it was used on, its middle so many half blocks from that block's middle, so
     * many blocks across, looking out the way given.
     */
    private record Laying(BlockPos from, BlockPos toCentre, int size, Direction out) {
        List<BlockPos> blocks() {
            return WindowDisc.blocks(from, toCentre, size, out);
        }
    }

    public FloorWindowItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Direction out = context.getClickedFace();
        Laying laying = largest(level, clicked, out, context.getClickLocation(), context.getPlayer(), context.getItemInHand());
        if (laying == null) {
            if (level instanceof ServerLevel server) {
                refuse(server, clicked, out);
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel server) {
            Direction facing = out.getAxis().isHorizontal() ? out : context.getHorizontalDirection();
            lay(server, laying, facing);
            server.playSound(null, clicked, SoundEvents.GLASS_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
            Player player = context.getPlayer();
            if (player == null || !player.getAbilities().instabuild) {
                context.getItemInHand().shrink(1);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The largest window that fits round a block used on a face of it, where it was used; null for none. */
    @Nullable
    private static Laying largest(Level level, BlockPos clicked, Direction out, Vec3 hit, @Nullable Player player, ItemStack stack) {
        for (int size = WindowDisc.FULL; size >= WindowDisc.SMALLEST; size--) {
            Laying laying = fitting(level, clicked, out, hit, size, player, stack);
            if (laying != null) {
                return laying;
            }
        }
        return null;
    }

    /** A window of a size that fits round a block used on a face of it, the nearest where it was used; null for none. */
    @Nullable
    private static Laying fitting(Level level, BlockPos clicked, Direction out, Vec3 hit, int size, @Nullable Player player, ItemStack stack) {
        return middles(size, clicked, out, hit).stream().map(toCentre -> new Laying(clicked, toCentre, size, out))
            .filter(laying -> fits(level, laying, player, stack)).findFirst().orElse(null);
    }

    /**
     * Where the middle of a window of a size may lie from the middle of the block it was used on, in half blocks: in
     * it for an odd size, and for an even one at its corners, the nearest where it was used first.
     */
    private static List<BlockPos> middles(int size, BlockPos clicked, Direction out, Vec3 hit) {
        if (size % 2 == 1) {
            return List.of(BlockPos.ZERO);
        }
        Direction[] across = WindowDisc.across(out);
        List<BlockPos> corners = new ArrayList<>();
        for (int u = -1; u <= 1; u += 2) {
            for (int v = -1; v <= 1; v += 2) {
                corners.add(BlockPos.ZERO.relative(across[0], u).relative(across[1], v));
            }
        }
        Vec3 middle = Vec3.atCenterOf(clicked);
        corners.sort(Comparator.comparingDouble(corner -> hit.distanceToSqr(middle.add(Vec3.atLowerCornerOf(corner).scale(0.5)))));
        return corners;
    }

    /** Whether every block of a window is plain floor with its face seen, where the player may build. */
    private static boolean fits(Level level, Laying laying, @Nullable Player player, ItemStack stack) {
        for (BlockPos at : laying.blocks()) {
            if (!level.isLoaded(at) || !allowed(level, at, laying.out(), player, stack) || !fitsAt(level, at, laying.out())) {
                return false;
            }
        }
        return true;
    }

    /** Whether a player may build at a block: out of the spawn's protection and where they may use what they hold. */
    private static boolean allowed(Level level, BlockPos at, Direction out, @Nullable Player player, ItemStack stack) {
        return player == null || level.mayInteract(player, at) && player.mayUseItemAt(at, out, stack);
    }

    /**
     * Whether a block of a window may take the place of a block: a whole block to break, keeping nothing, not another
     * window, whose face the window looks out of is seen, so the window never reaches in under a wall or past a corner.
     */
    private static boolean fitsAt(Level level, BlockPos at, Direction out) {
        BlockState state = level.getBlockState(at);
        boolean plain = !state.canBeReplaced() && state.isCollisionShapeFullBlock(level, at) && !state.hasBlockEntity()
            && state.getDestroySpeed(level, at) >= 0 && !(state.getBlock() instanceof FloorWindowBlock);
        return plain && Block.shouldRenderFace(state, level, at, out, at.relative(out));
    }

    /** Lays a window, each block of it keeping the block it took the place of, which it blends into. */
    private void lay(ServerLevel level, Laying laying, Direction facing) {
        BlockState window = getBlock().defaultBlockState().setValue(FloorWindowBlock.FACING, facing)
            .setValue(FloorWindowBlock.MOUNT, Mount.against(laying.out().getOpposite()));
        for (BlockPos at : laying.blocks()) {
            BlockState was = level.getBlockState(at);
            BlockPos toMiddle = laying.from().subtract(at);
            level.setBlock(at, window, Block.UPDATE_ALL);
            if (level.getBlockEntity(at) instanceof FloorWindowBlockEntity laid) {
                laid.configure(toMiddle, was, was, 0, laying.size(), laying.toCentre().offset(toMiddle.multiply(2)));
            }
        }
    }

    /** Where a window does not fit: a thud and a puff of smoke off the face it was used on, and the window kept. */
    private static void refuse(ServerLevel level, BlockPos clicked, Direction out) {
        Vec3 puff = Vec3.atCenterOf(clicked).add(Vec3.atLowerCornerOf(out.getNormal()).scale(PUFF_OUT));
        level.playSound(null, clicked, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, THUD_VOLUME, THUD_PITCH);
        level.sendParticles(ParticleTypes.SMOKE, puff.x, puff.y, puff.z, PUFFS, 0.3, 0.05, 0.3, 0.01);
    }
}
