package dev.aerolarge;

import dev.aerolarge.block.AirstairCallButtonBlock;
import dev.aerolarge.block.AirstairControllerBlock;
import dev.aerolarge.block.AirstairControllerBlockEntity;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.AutopilotBlockEntity;
import dev.aerolarge.block.CockpitWindscreenBlock;
import dev.aerolarge.block.CockpitWindscreenBlockEntity;
import dev.aerolarge.block.FairingBlock;
import dev.aerolarge.block.InstrumentPanelBlock;
import dev.aerolarge.block.ThrustLeverBlock;
import dev.aerolarge.block.ThrustLeverBlockEntity;
import dev.aerolarge.block.ThrustRelayBlock;
import dev.aerolarge.block.WindscreenBlock;
import dev.aerolarge.item.AirlinerBlueprintItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The airframe's blocks and the two blueprints. Every block is also in {@code #sable:super_light} (see the data
 * pack), so a hull this size still weighs little enough to fly once Create Aeronautics assembles it.
 */
public final class AerolargeRegistry {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Aerolarge.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Aerolarge.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Aerolarge.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Aerolarge.MOD_ID);

    /** Opens by hand like a wooden door but sounds like the metal one it is. */
    public static final BlockSetType AIRLINER = BlockSetType.register(new BlockSetType(
        "aerolarge_airliner",
        true,
        false,
        false,
        BlockSetType.PressurePlateSensitivity.EVERYTHING,
        SoundType.METAL,
        SoundEvents.IRON_DOOR_CLOSE,
        SoundEvents.IRON_DOOR_OPEN,
        SoundEvents.IRON_TRAPDOOR_CLOSE,
        SoundEvents.IRON_TRAPDOOR_OPEN,
        SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF,
        SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
        SoundEvents.STONE_BUTTON_CLICK_OFF,
        SoundEvents.STONE_BUTTON_CLICK_ON
    ));

    public static final DeferredBlock<Block> AIRFRAME_PANEL = BLOCKS.registerSimpleBlock("airframe_panel", metal(MapColor.SNOW));
    /** Airframe panel with its outer edge cut at 45 degrees, smoothing the steps of the hull's curves. */
    public static final DeferredBlock<FairingBlock> AIRFRAME_FAIRING = BLOCKS.registerBlock(
        "airframe_fairing", FairingBlock::new, metal(MapColor.SNOW).noOcclusion()
    );
    public static final DeferredBlock<Block> LIVERY_PANEL = BLOCKS.registerSimpleBlock("livery_panel", metal(MapColor.COLOR_BLUE));
    public static final DeferredBlock<Block> CABIN_FLOOR = BLOCKS.registerSimpleBlock("cabin_floor", metal(MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> ENGINE_INTAKE = BLOCKS.registerSimpleBlock("engine_intake", metal(MapColor.COLOR_BLACK));
    /** Built into the crown, the walls and the hold's belly so no corner of the cabin is dark enough for mobs. */
    public static final DeferredBlock<Block> CABIN_LIGHT = BLOCKS.registerSimpleBlock(
        "cabin_light", metal(MapColor.SNOW).lightLevel(state -> 15).sound(SoundType.GLASS)
    );
    public static final DeferredBlock<Block> LANDING_GEAR = BLOCKS.registerSimpleBlock("landing_gear", metal(MapColor.METAL));
    public static final DeferredBlock<Block> LANDING_GEAR_TIRE = BLOCKS.registerSimpleBlock(
        "landing_gear_tire",
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F, 3.0F).sound(SoundType.WOOL).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredBlock<TransparentBlock> CABIN_WINDOW = BLOCKS.registerBlock("cabin_window", TransparentBlock::new, glass());
    public static final DeferredBlock<TransparentBlock> COCKPIT_WINDOW = BLOCKS.registerBlock("cockpit_window", TransparentBlock::new, glass());
    public static final DeferredBlock<DoorBlock> AIRLINER_DOOR = BLOCKS.registerBlock(
        "airliner_door",
        properties -> new DoorBlock(AIRLINER, properties),
        metal(MapColor.SNOW).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    /** The airstairs' panel beside the forward door, and the ground crew's button on the nose gear that calls it. */
    public static final DeferredBlock<AirstairControllerBlock> AIRSTAIR_CONTROLLER = BLOCKS.registerBlock(
        "airstair_controller", AirstairControllerBlock::new, metal(MapColor.COLOR_YELLOW)
    );
    public static final DeferredBlock<AirstairCallButtonBlock> AIRSTAIR_CALL_BUTTON = BLOCKS.registerBlock(
        "airstair_call_button",
        properties -> new AirstairCallButtonBlock(AIRLINER, properties),
        BlockBehaviour.Properties.of().noCollission().strength(0.5F).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirstairControllerBlockEntity>> AIRSTAIR_CONTROLLER_ENTITY =
        BLOCK_ENTITIES.register("airstair_controller", () -> BlockEntityType.Builder.of(AirstairControllerBlockEntity::new, AIRSTAIR_CONTROLLER.get()).build(null));

    /** The hull behind the curved windscreen, invisible and solid; one of them carries the windscreen's renderer. */
    public static final DeferredBlock<WindscreenBlock> WINDSCREEN = BLOCKS.registerBlock("windscreen", WindscreenBlock::new, glass());
    public static final DeferredBlock<CockpitWindscreenBlock> COCKPIT_WINDSCREEN = BLOCKS.registerBlock(
        "cockpit_windscreen", CockpitWindscreenBlock::new, glass()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CockpitWindscreenBlockEntity>> COCKPIT_WINDSCREEN_ENTITY =
        BLOCK_ENTITIES.register("cockpit_windscreen", () -> BlockEntityType.Builder.of(CockpitWindscreenBlockEntity::new, COCKPIT_WINDSCREEN.get()).build(null));
    /** The flight deck: four thrust levers on the pedestal, the relays they drive in the nacelles, the instrument panel. */
    public static final DeferredBlock<ThrustLeverBlock> THRUST_LEVER = BLOCKS.registerBlock(
        "thrust_lever", ThrustLeverBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThrustLeverBlockEntity>> THRUST_LEVER_ENTITY =
        BLOCK_ENTITIES.register("thrust_lever", () -> BlockEntityType.Builder.of(ThrustLeverBlockEntity::new, THRUST_LEVER.get()).build(null));
    public static final DeferredBlock<AutopilotBlock> AUTOPILOT = BLOCKS.registerBlock(
        "autopilot", AutopilotBlock::new, metal(MapColor.COLOR_BLACK).lightLevel(state -> 7)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutopilotBlockEntity>> AUTOPILOT_ENTITY =
        BLOCK_ENTITIES.register("autopilot", () -> BlockEntityType.Builder.of(AutopilotBlockEntity::new, AUTOPILOT.get()).build(null));
    public static final DeferredBlock<ThrustRelayBlock> THRUST_RELAY = BLOCKS.registerBlock("thrust_relay", ThrustRelayBlock::new, metal(MapColor.SNOW));
    public static final DeferredBlock<InstrumentPanelBlock> INSTRUMENT_PANEL = BLOCKS.registerBlock(
        "instrument_panel", InstrumentPanelBlock::new, metal(MapColor.COLOR_BLACK).lightLevel(state -> 7)
    );

    public static final DeferredItem<BlockItem> AIRFRAME_PANEL_ITEM = ITEMS.registerSimpleBlockItem(AIRFRAME_PANEL);
    public static final DeferredItem<BlockItem> AIRFRAME_FAIRING_ITEM = ITEMS.registerSimpleBlockItem(AIRFRAME_FAIRING);
    public static final DeferredItem<BlockItem> LIVERY_PANEL_ITEM = ITEMS.registerSimpleBlockItem(LIVERY_PANEL);
    public static final DeferredItem<BlockItem> CABIN_WINDOW_ITEM = ITEMS.registerSimpleBlockItem(CABIN_WINDOW);
    public static final DeferredItem<BlockItem> COCKPIT_WINDOW_ITEM = ITEMS.registerSimpleBlockItem(COCKPIT_WINDOW);
    public static final DeferredItem<BlockItem> CABIN_FLOOR_ITEM = ITEMS.registerSimpleBlockItem(CABIN_FLOOR);
    public static final DeferredItem<DoubleHighBlockItem> AIRLINER_DOOR_ITEM = ITEMS.register(
        "airliner_door", () -> new DoubleHighBlockItem(AIRLINER_DOOR.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> ENGINE_INTAKE_ITEM = ITEMS.registerSimpleBlockItem(ENGINE_INTAKE);
    public static final DeferredItem<BlockItem> CABIN_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(CABIN_LIGHT);
    public static final DeferredItem<BlockItem> LANDING_GEAR_ITEM = ITEMS.registerSimpleBlockItem(LANDING_GEAR);
    public static final DeferredItem<BlockItem> LANDING_GEAR_TIRE_ITEM = ITEMS.registerSimpleBlockItem(LANDING_GEAR_TIRE);
    public static final DeferredItem<BlockItem> THRUST_LEVER_ITEM = ITEMS.registerSimpleBlockItem(THRUST_LEVER);
    public static final DeferredItem<BlockItem> INSTRUMENT_PANEL_ITEM = ITEMS.registerSimpleBlockItem(INSTRUMENT_PANEL);
    public static final DeferredItem<BlockItem> AUTOPILOT_ITEM = ITEMS.registerSimpleBlockItem(AUTOPILOT);
    public static final DeferredItem<BlockItem> AIRSTAIR_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(AIRSTAIR_CONTROLLER);
    public static final DeferredItem<BlockItem> AIRSTAIR_CALL_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(AIRSTAIR_CALL_BUTTON);

    /** Creative only: there is no recipe for laying down a few tens of thousands of blocks at once. */
    public static final DeferredItem<AirlinerBlueprintItem> AIRLINER_BLUEPRINT = ITEMS.register(
        "airliner_blueprint",
        () -> new AirlinerBlueprintItem(false, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );
    /** The same airliner with a pool, a gym hall and a lounge already fitted into the cabin. */
    public static final DeferredItem<AirlinerBlueprintItem> FURNISHED_AIRLINER_BLUEPRINT = ITEMS.register(
        "furnished_airliner_blueprint",
        () -> new AirlinerBlueprintItem(true, new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("aerolarge", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.aerolarge"))
        .icon(() -> AIRLINER_BLUEPRINT.get().getDefaultInstance())
        .displayItems((parameters, output) -> List.of(
            AIRLINER_BLUEPRINT, FURNISHED_AIRLINER_BLUEPRINT, AIRFRAME_PANEL_ITEM, AIRFRAME_FAIRING_ITEM, LIVERY_PANEL_ITEM, CABIN_WINDOW_ITEM,
            COCKPIT_WINDOW_ITEM, CABIN_FLOOR_ITEM, CABIN_LIGHT_ITEM, AIRLINER_DOOR_ITEM, ENGINE_INTAKE_ITEM, LANDING_GEAR_ITEM, LANDING_GEAR_TIRE_ITEM,
            AIRSTAIR_CONTROLLER_ITEM, AIRSTAIR_CALL_BUTTON_ITEM, THRUST_LEVER_ITEM, INSTRUMENT_PANEL_ITEM, AUTOPILOT_ITEM
        ).forEach(item -> output.accept(item.get())))
        .build());

    private AerolargeRegistry() {
    }

    /**
     * Aircraft aluminium: about as tough as copper, quick to cut away when rebuilding the cabin. Nothing spawns on
     * it, so a dark corner of the hold or a wing at night stays empty.
     */
    private static BlockBehaviour.Properties metal(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(2.0F, 6.0F).sound(SoundType.COPPER).isValidSpawn(AerolargeRegistry::never);
    }

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos, EntityType<?> type) {
        return false;
    }

    private static BlockBehaviour.Properties glass() {
        return BlockBehaviour.Properties.of()
            .strength(1.0F, 6.0F)
            .sound(SoundType.GLASS)
            .noOcclusion()
            .isValidSpawn(AerolargeRegistry::never)
            .isRedstoneConductor((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false);
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
    }
}
