package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A neon sign for the club's walls: tubes of glowing glass bent into a picture, with no words in it, standing off a
 * black backing board fixed to the wall. Its picture, DESIGN, is in neon colours of its own: a martini glass with an
 * olive on a pick, a pair of music notes, a ringed planet, a rocket, a pair of lips or a lightning bolt. Click it to
 * switch it off and on (LIT), with the clink of its transformer and the buzz of its tubes striking or dying away; off,
 * its tubes are dark glass and it gives no light. Sneak and click it with an empty hand and it strikes up the next
 * picture. Nothing collides with it, so it is walked past as a picture is. FACING is the way it faces, out from the
 * wall behind it.
 */
public class NeonSignBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<NeonSignBlock> CODEC = simpleCodec(NeonSignBlock::new);
    public static final EnumProperty<Design> DESIGN = EnumProperty.create("design", Design.class);
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** How brightly a lit sign lights the wall and the room round it, in light levels. */
    public static final int GLOW = 8;
    /** How loud its switch is; and the pitch its tubes strike a new picture at, a touch higher than switching on. */
    private static final float SWITCH_VOLUME = 0.7F;
    private static final float NEXT_PITCH = 1.15F;
    /** The sign facing north: its board against the south side of the block, its tubes standing off it. */
    private static final Map<Direction, VoxelShape> SHAPES = TurnedShapes.of(Block.box(0, 0, 13.5, 16, 16, 16));

    /** The pictures a neon sign shows, kept in its state by name; its item's looks follow this order. */
    public enum Design implements StringRepresentable {
        COCKTAIL,
        NOTE,
        PLANET,
        ROCKET,
        LIPS,
        BOLT;

        /** The picture after this one, round to the first again after the last. */
        public Design next() {
            return values()[(ordinal() + 1) % values().length];
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public NeonSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(DESIGN, Design.COCKTAIL).setValue(LIT, true));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, DESIGN, LIT);
    }

    /** How brightly a sign lights what is round it: GLOW lit, nothing off. */
    public static int glow(BlockState state) {
        return state.getValue(LIT) ? GLOW : 0;
    }

    /**
     * Fixed to the side of the block clicked, facing out from it; put on a floor or a ceiling, it faces whoever puts it
     * up.
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        Direction facing = clicked.getAxis().isHorizontal() ? clicked : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    /** Switched off or on; sneaking, it strikes up its next picture. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            switchOver(server, state, pos, player.isSecondaryUseActive());
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * Strikes up the next picture, lit, or switches the sign off or on, with its transformer's clink and its tubes'
     * buzz.
     */
    private static void switchOver(ServerLevel level, BlockState state, BlockPos pos, boolean nextPicture) {
        if (nextPicture) {
            level.setBlock(pos, state.setValue(DESIGN, state.getValue(DESIGN).next()).setValue(LIT, true), Block.UPDATE_ALL);
            level.playSound(null, pos, SkylinersRegistry.NEON_ON.get(), SoundSource.BLOCKS, SWITCH_VOLUME, NEXT_PITCH);
            return;
        }
        boolean lit = !state.getValue(LIT);
        level.setBlock(pos, state.setValue(LIT, lit), Block.UPDATE_ALL);
        level.playSound(null, pos, (lit ? SkylinersRegistry.NEON_ON : SkylinersRegistry.NEON_OFF).get(), SoundSource.BLOCKS, SWITCH_VOLUME, 1.0F);
    }

    /** A sign showing its picture, the way one broken comes back; the martini glass is left off, so they stack. */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        ItemStack stack = new ItemStack(this);
        if (state.getValue(DESIGN) != Design.COCKTAIL) {
            stack.set(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY.with(DESIGN, state.getValue(DESIGN)));
        }
        return stack;
    }
}
