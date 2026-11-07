package dev.skyliners.console;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.autopilot.FreighterClimbBoost;
import dev.skyliners.autopilot.FreighterFlight;
import dev.skyliners.autopilot.FreighterJump;
import dev.skyliners.block.Mount;
import dev.skyliners.block.MountedModule;
import dev.skyliners.block.TurnedShapes;
import java.util.EnumMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A console module of the freighter's cockpit, of our own design: a worn sci-fi desk in dark gunmetal, its top crowded
 * with chunky controls and small lamps, a screen leaning back over it, what it shows and works by its kind (see {@link
 * ConsoleKind}). Laid round her pilots' seats, each is a slice of one smooth half-round console curving round them (see
 * {@link ConsoleShape}); put up by hand, it is a module on its own, on a floor as a desk, on a wall as a plate or under
 * a ceiling as a wedge leaning down, each drawn its own way, facing whoever put it up, reading the nearest autopilot.
 * Its renderer draws it all, the client's {@code ConsoleRenderer}, its pictures live, or a standby picture with no
 * ship to read. Used, it answers with a sound and a moment its client draws: a toggle flipped under the hand, its lamp
 * lit; the scope's ping going out; a lamp test; or a big button pressed in, which asks her autopilot to boost, to climb
 * hard or to jump through hyperspace to her pin.
 * FACING is the way toward whoever works it, MOUNT how it is put up.
 */
public class ConsoleBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final EnumProperty<Mount> MOUNT = MountedModule.MOUNT;
    /**
     * Its shapes drawn facing north: a desk across its block as high as the desk's top is drawn at its back, so the one
     * beyond it round her ring is pressed where it is seen over it rather than this one's empty room over its desk
     * taking the click; a plate against a wall; a wedge under a ceiling.
     */
    private static final VoxelShape DESK = Block.box(0, 0, 0, 16, ConsoleShape.DESK_BACK * 16, 16);
    private static final VoxelShape PLATE = Block.box(0, 0, 13, 16, 16, 16);
    private static final VoxelShape WEDGE = Block.box(0, 8, 0, 16, 16, 16);
    private static final Map<Mount, Map<Direction, VoxelShape>> SHAPES = new EnumMap<>(Map.of(
        Mount.FLOOR, TurnedShapes.of(DESK), Mount.WALL, TurnedShapes.of(PLATE), Mount.CEILING, TurnedShapes.of(WEDGE)));
    /** How loud and how high its sounds are. */
    private static final float CLICK_VOLUME = 0.6F;
    private static final float TOGGLE_UP_PITCH = 0.7F;
    private static final float TOGGLE_DOWN_PITCH = 0.55F;
    private static final float PING_PITCH = 1.9F;
    private static final float BEEP_PITCH = 1.6F;
    private static final float REFUSED_PITCH = 0.6F;
    private static final float ROAR_VOLUME = 2.5F;

    private final ConsoleKind kind;

    public ConsoleBlock(Properties properties, ConsoleKind kind) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH).setValue(MOUNT, Mount.FLOOR));
    }

    /** What it shows and works. */
    public ConsoleKind kind() {
        return kind;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return simpleCodec(properties -> new ConsoleBlock(properties, kind));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MOUNT);
    }

    /** Put up as a button goes, against the block clicked, facing whoever puts it up (see {@link MountedModule}). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return MountedModule.place(context, defaultBlockState());
    }

    /** Drawn by its renderer alone, desk and pictures, as it bends round a ship's seats. */
    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(MountedModule.of(state)).get(state.getValue(FACING));
    }

    /** No shadow on the floor round it: what is drawn of it is smaller than its block. */
    @Override
    protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server && !player.isSpectator() && level.getBlockEntity(pos) instanceof ConsoleBlockEntity console) {
            Vec3 inBlock = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            use(server, pos, console, player, inBlock);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Used by a player, on the server, touched at a point in its block: what it does by its kind. */
    private void use(ServerLevel level, BlockPos pos, ConsoleBlockEntity console, Player player, Vec3 at) {
        switch (kind) {
            case SWITCHES -> {
                boolean up = console.flip(console.shape().control(at, kind.controls()));
                sound(level, pos, SoundEvents.LEVER_CLICK, up ? TOGGLE_UP_PITCH : TOGGLE_DOWN_PITCH);
            }
            case SCANNER -> {
                console.touch(level);
                sound(level, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), PING_PITCH);
            }
            case BOOST -> boost(level, pos, console, player);
            case JUMP -> jump(level, pos, console, player);
            case CLIMB -> climb(level, pos, console, player);
            default -> {
                console.touch(level);
                sound(level, pos, SoundEvents.NOTE_BLOCK_BIT.value(), BEEP_PITCH);
            }
        }
    }

    /**
     * The boost button pressed in: her autopilot asked to boost, which roars into it, or says why not with a low note,
     * for those who cannot read its word in the hotbar.
     */
    private static void boost(ServerLevel level, BlockPos pos, ConsoleBlockEntity console, Player player) {
        console.touch(level);
        AutopilotBlockEntity autopilot = console.autopilot();
        FreighterFlight.BoostAnswer answer = FreighterFlight.boost(level, autopilot);
        player.displayClientMessage(Component.translatable("message.skyliners.console.boost_" + answer.getSerializedName()), true);
        if (answer == FreighterFlight.BoostAnswer.BOOSTING) {
            level.playSound(null, pos, SkylinersRegistry.SPEED_BOOST.get(), SoundSource.BLOCKS, ROAR_VOLUME, 1);
        } else {
            sound(level, pos, SoundEvents.NOTE_BLOCK_BASS.value(), REFUSED_PITCH);
        }
    }

    /**
     * The jump button pressed in: her autopilot asked to jump to her pin, her computer answering as her drive spools
     * up, or, with a low note, saying why not: no pin, not flying along, or the pin too near to bother; only a click
     * while she is jumping already.
     */
    private static void jump(ServerLevel level, BlockPos pos, ConsoleBlockEntity console, Player player) {
        console.touch(level);
        FreighterJump.Answer answer = FreighterFlight.jump(level, console.autopilot());
        String said = answer == null ? "no_ship" : answer.getSerializedName();
        player.displayClientMessage(Component.translatable("message.skyliners.console.jump_" + said), true);
        if (answer == FreighterJump.Answer.JUMPING) {
            return;
        }
        if (answer == FreighterJump.Answer.BUSY) {
            sound(level, pos, SoundEvents.STONE_BUTTON_CLICK_ON, BEEP_PITCH);
            return;
        }
        sound(level, pos, SoundEvents.NOTE_BLOCK_BASS.value(), REFUSED_PITCH);
        SoundEvent why = answer == null ? null : switch (answer) {
            case NO_PIN -> SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_NO_PIN.get();
            case NOT_FLYING -> SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_GROUNDED.get();
            case TOO_NEAR -> SkylinersRegistry.FREIGHTER_COMPUTER_JUMP_TOO_NEAR.get();
            default -> null;
        };
        if (why != null) {
            level.playSound(null, pos, why, SoundSource.BLOCKS, 1, 1);
        }
    }

    /**
     * The climb button pressed in: her autopilot asked to climb hard, which roars into it as her computer says so, or
     * says why not with a low note; only a click while it fires already.
     */
    private static void climb(ServerLevel level, BlockPos pos, ConsoleBlockEntity console, Player player) {
        console.touch(level);
        FreighterClimbBoost.Answer answer = FreighterFlight.climbBoost(level, console.autopilot());
        player.displayClientMessage(Component.translatable("message.skyliners.console.climb_" + answer.getSerializedName()), true);
        switch (answer) {
            case CLIMBING -> {
                level.playSound(null, pos, SkylinersRegistry.SPEED_BOOST.get(), SoundSource.BLOCKS, ROAR_VOLUME, 1);
                level.playSound(null, pos, SkylinersRegistry.FREIGHTER_COMPUTER_CLIMB_BOOST.get(), SoundSource.BLOCKS, 1, 1);
            }
            case BUSY -> sound(level, pos, SoundEvents.STONE_BUTTON_CLICK_ON, BEEP_PITCH);
            default -> sound(level, pos, SoundEvents.NOTE_BLOCK_BASS.value(), REFUSED_PITCH);
        }
    }

    private static void sound(ServerLevel level, BlockPos pos, SoundEvent sound, float pitch) {
        level.playSound(null, pos, sound, SoundSource.BLOCKS, CLICK_VOLUME, pitch);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ConsoleBlockEntity(pos, state);
    }
}
