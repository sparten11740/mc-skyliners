package dev.aerolarge.block;

import com.mojang.serialization.MapCodec;
import dev.aerolarge.compat.SableFlight;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A screen of the flight deck's curved row, either side of the autopilot screen: a panel turned toward the pilots'
 * seats, showing a pilot's flight display, the moving map or an outside camera (see {@link CockpitDisplayBlockEntity}).
 * Turned, it runs across blocks at a slant; every block it passes through is this block, standing where the panel
 * does, and the one holding the panel (HOLDER) draws all of it and keeps what it knows. FACING is the way the row
 * faces, toward the seats; SHOWS what is on it. Using any part touches the panel where the look meets it. Broken by a
 * player, the whole panel goes.
 */
public class CockpitDisplayBlock extends HorizontalDirectionalBlock implements EntityBlock {
    private static final MapCodec<CockpitDisplayBlock> CODEC = simpleCodec(CockpitDisplayBlock::new);
    public static final BooleanProperty HOLDER = BooleanProperty.create("holder");
    public static final EnumProperty<Shows> SHOWS = EnumProperty.create("shows", Shows.class);
    /** How far round a part the block holding its panel is looked for, in blocks: further than a panel ever runs. */
    static final int HOLDER_REACH = 4;
    /** How deep a panel's slab is behind its face, in blocks. */
    public static final float DEPTH = 0.1F;

    /** What a panel shows. */
    public enum Shows implements StringRepresentable {
        FLIGHT, MAP, CAMERA;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public CockpitDisplayBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(HOLDER, false).setValue(SHOWS, Shows.FLIGHT));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HOLDER, SHOWS);
    }

    /** Drawn by the block holding the panel, all of it at once, not block by block. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    /** The whole block, to aim at and stand clear of: the panel runs through it at a slant. */
    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel server) {
            Vec3 eye = SableFlight.toPlot(server, pos, player.getEyePosition(), false);
            CockpitDisplayBlockEntity panel = CockpitDisplayBlockEntity.touched(server, pos, eye, hit.getLocation());
            if (panel != null && panel.touch(server, eye, hit.getLocation(), serverPlayer)) {
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.4F, 1.6F);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Broken by a player, the panel goes with every block it stands in, none of them dropping anything. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            CockpitDisplayBlockEntity panel = CockpitDisplayBlockEntity.holding(level, pos);
            if (panel != null) {
                panel.dismantle(pos);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Whether a panel's line across the floor plan, from one point to another, passes through a block's square there,
     * not only along its edge (Liang-Barsky).
     */
    public static boolean crossesSquare(double x0, double z0, double x1, double z1, int squareX, int squareZ) {
        double from = 0;
        double to = 1;
        double[] p = {-(x1 - x0), x1 - x0, -(z1 - z0), z1 - z0};
        double[] q = {x0 - squareX, squareX + 1 - x0, z0 - squareZ, squareZ + 1 - z0};
        for (int side = 0; side < 4; side++) {
            if (p[side] == 0 && q[side] < 0) {
                return false;
            }
            if (p[side] == 0) {
                continue;
            }
            double t = q[side] / p[side];
            if (p[side] < 0) {
                from = Math.max(from, t);
            } else {
                to = Math.min(to, t);
            }
        }
        return from < to;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HOLDER) ? new CockpitDisplayBlockEntity(pos, state) : null;
    }
}
