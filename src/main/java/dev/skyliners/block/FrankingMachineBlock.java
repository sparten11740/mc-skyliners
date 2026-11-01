package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.SkylinersConfig;
import dev.skyliners.SkylinersRegistry;
import dev.skyliners.autopilot.AutopilotBlockEntity;
import dev.skyliners.compat.SableFlight;
import dev.skyliners.entity.FallingCrate;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Zeppelin Post: the franking machine in a zeppelin's control car, a red post office machine with a little green lamp.
 * Used with a sheet of paper, it prints a postcard: a map of the land she flies over, named for her, franked with
 * where she was, which fills in as it is held. Used with an empty hand while she flies over a village, it drops a
 * mail sack from under the car, which parachutes down to it (see {@link dev.skyliners.item.MailSackItem}); its lamp
 * goes dark while it rests before the next, and away from a village, or resting, it only sounds a low note. RESTING is
 * whether it rests.
 */
public class FrankingMachineBlock extends HorizontalDirectionalBlock {
    private static final MapCodec<FrankingMachineBlock> CODEC = simpleCodec(FrankingMachineBlock::new);
    public static final BooleanProperty RESTING = BooleanProperty.create("resting");
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 12, 14);
    /** How long it rests after dropping a sack, in ticks: half a minute. */
    private static final int REST_TICKS = 600;
    /** How far from the land under her a village's bell may be for a sack to be dropped to it, in blocks. */
    private static final int VILLAGE_REACH = 96;
    /** How far under the machine the sack is dropped from, clear of the car's floor, in blocks. */
    private static final double DROP_BELOW = 2.5;
    /** How much of the land round her a postcard shows: a map's own scale, a block to a pixel. */
    private static final byte POSTCARD_SCALE = 0;
    private static final float LOW_NOTE_VOLUME = 0.6F;

    public FrankingMachineBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(RESTING, false));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RESTING);
    }

    /** Put down by hand, it faces whoever puts it down. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** With a sheet of paper in hand: a postcard printed, the paper taken. */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
                                              BlockHitResult hit) {
        if (!stack.is(Items.PAPER)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level instanceof ServerLevel server && SkylinersConfig.ZEPPELIN_POST.get()) {
            stack.consume(1, player);
            player.getInventory().placeItemBackInInventory(postcard(server, pos));
            level.playSound(null, pos, SoundEvents.UI_CARTOGRAPHY_TABLE_TAKE_RESULT, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    /** With an empty hand: a mail sack dropped to the village under her, if there is one and it is not resting. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level instanceof ServerLevel server) {
            drop(server, state, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void drop(ServerLevel level, BlockState state, BlockPos pos, Player player) {
        Vec3 below = SableFlight.toWorld(level, Vec3.atCenterOf(pos).subtract(0, DROP_BELOW, 0));
        if (!SkylinersConfig.ZEPPELIN_POST.get() || state.getValue(RESTING) || !SableFlight.aboard(level, Vec3.atCenterOf(pos)) || !overVillage(level, below)) {
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, LOW_NOTE_VOLUME, 0.6F);
            return;
        }
        Vec3 moving = SableFlight.velocity(level, Vec3.atCenterOf(pos)).scale(1.0 / 20);
        level.addFreshEntity(FallingCrate.of(level, below, moving, new ItemStack(SkylinersRegistry.MAIL_SACK_ITEM.get()), player.getUUID()));
        level.playSound(null, pos, SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.BLOCKS, 1.0F, 0.8F);
        level.setBlock(pos, state.setValue(RESTING, true), Block.UPDATE_ALL);
        level.scheduleTick(pos, state.getBlock(), REST_TICKS);
    }

    /** Rested: its lamp lit again for the next sack. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (state.getValue(RESTING)) {
            level.setBlock(pos, state.setValue(RESTING, false), Block.UPDATE_ALL);
            level.playSound(null, pos, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, LOW_NOTE_VOLUME, 1.4F);
        }
    }

    /** Whether a village's bell stands within reach of the land under a point. */
    private static boolean overVillage(ServerLevel level, Vec3 point) {
        int x = (int) Math.floor(point.x);
        int z = (int) Math.floor(point.z);
        BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
        return level.getPoiManager().findClosest(FrankingMachineBlock::meeting, ground, VILLAGE_REACH, PoiManager.Occupancy.ANY).isPresent();
    }

    private static boolean meeting(Holder<PoiType> type) {
        return type.is(PoiTypes.MEETING);
    }

    /** A postcard of the land under her: a map round where she is, named for her, franked with where that was. */
    private static ItemStack postcard(ServerLevel level, BlockPos pos) {
        Vec3 at = SableFlight.toWorld(level, Vec3.atCenterOf(pos));
        ItemStack card = MapItem.create(level, (int) Math.floor(at.x), (int) Math.floor(at.z), POSTCARD_SCALE, true, false);
        card.set(DataComponents.CUSTOM_NAME, Component.translatable("item.skyliners.postcard", name(level, pos)));
        card.set(DataComponents.LORE, new ItemLore(List.of(Component.translatable("item.skyliners.postcard.franked", (int) Math.floor(at.x),
            (int) Math.floor(at.z)))));
        return card;
    }

    /** Her name, once she is christened, from her plaque; "the zeppelin" before. */
    private static Component name(ServerLevel level, BlockPos pos) {
        AutopilotBlockEntity zeppelin = AutopilotBlockEntity.zeppelinFraming(level, pos);
        BoundingBox frame = zeppelin == null ? null : zeppelin.frame();
        for (ShipsPlaqueBlockEntity plaque : ShipsPlaqueBlockEntity.loaded(level)) {
            if (frame != null && frame.isInside(plaque.getBlockPos()) && plaque.christened()) {
                return Component.literal(plaque.name());
            }
        }
        return Component.translatable("item.skyliners.postcard.the_zeppelin");
    }
}
