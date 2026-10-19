package dev.aerolarge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.aerolarge.block.AirstairCallButtonBlock;
import dev.aerolarge.block.AirstairControllerBlock;
import dev.aerolarge.block.AirstairControllerBlockEntity;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.block.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlock;
import dev.aerolarge.block.AutopilotScreenBlockEntity;
import dev.aerolarge.block.BallRackBlock;
import dev.aerolarge.block.Brightness;
import dev.aerolarge.block.CabinControlBlockEntity;
import dev.aerolarge.block.CabinLightBlock;
import dev.aerolarge.block.CabinLightsBlock;
import dev.aerolarge.block.CabinWindowBlock;
import dev.aerolarge.block.CockpitGlassBlock;
import dev.aerolarge.block.EngineCoreBlock;
import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.block.FairingBlock;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.FlightDisplayBlockEntity;
import dev.aerolarge.block.HandsetBlock;
import dev.aerolarge.block.HoopBlock;
import dev.aerolarge.block.InstrumentPanelBlock;
import dev.aerolarge.block.JumpLightBlock;
import dev.aerolarge.block.JumpSwitchBlock;
import dev.aerolarge.block.LandingTargetBlock;
import dev.aerolarge.block.LandingTargetBlockEntity;
import dev.aerolarge.block.LightCableBlock;
import dev.aerolarge.block.LightStripBlock;
import dev.aerolarge.block.LinkedControlBlockEntity;
import dev.aerolarge.block.NavLightBlock;
import dev.aerolarge.block.NavLightBlockEntity;
import dev.aerolarge.block.OverheadBinBlock;
import dev.aerolarge.block.OverheadBinBlockEntity;
import dev.aerolarge.block.ParachuteRackBlock;
import dev.aerolarge.block.ParkingBrakeBlock;
import dev.aerolarge.block.PendantLightBlock;
import dev.aerolarge.block.RadioBlockEntity;
import dev.aerolarge.block.ScoreboardBlock;
import dev.aerolarge.block.ScoreboardBlockEntity;
import dev.aerolarge.block.SeatbeltSignBlock;
import dev.aerolarge.block.SeatbeltSwitchBlock;
import dev.aerolarge.block.SeatbeltSwitchBlockEntity;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.SignalFlareBlock;
import dev.aerolarge.block.SignalFlareBlockEntity;
import dev.aerolarge.block.ThrustLeverBlock;
import dev.aerolarge.block.ThrustLeverBlockEntity;
import dev.aerolarge.block.ThrustRelayBlock;
import dev.aerolarge.block.TillerBlock;
import dev.aerolarge.block.TowerRadioBlock;
import dev.aerolarge.block.WindowShadesBlock;
import dev.aerolarge.entity.Basketball;
import dev.aerolarge.item.AirlinerBlueprintItem;
import dev.aerolarge.item.BasketballItem;
import dev.aerolarge.item.ParachuteItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
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
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Aerolarge.MOD_ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Aerolarge.MOD_ID);
    private static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Aerolarge.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Aerolarge.MOD_ID);

    /** A turbofan running, looped; the engines pitch and swell it with their thrust. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_SOUND = sound("engine");
    /** The flight deck's synthetic voice: take-off and landing callouts and warnings. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_V1 = sound("callout_v1");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_ROTATE = sound("callout_rotate");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_POSITIVE_RATE = sound("callout_positive_rate");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_100 = sound("callout_100");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_50 = sound("callout_50");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_40 = sound("callout_40");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_30 = sound("callout_30");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_20 = sound("callout_20");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_10 = sound("callout_10");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_RETARD = sound("callout_retard");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_BANK_ANGLE = sound("callout_bank_angle");
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_PULL_UP = sound("callout_pull_up");
    /** The two-tone chime before a cabin announcement, and the crackle of a radio call. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PA_CHIME = sound("pa_chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_CRACKLE = sound("radio_crackle");
    /** The ding of the seatbelt signs coming on. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SEATBELT_DING = sound("seatbelt_ding");
    /** The whirr of the window shades' motors. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_MOTOR = sound("shade_motor");
    /** Basketball: the ball bouncing, the swish of a basket and the scoreboard's buzzer. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BALL_BOUNCE = sound("ball_bounce");
    public static final DeferredHolder<SoundEvent, SoundEvent> BASKET_SWISH = sound("basket_swish");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCOREBOARD_BUZZER = sound("scoreboard_buzzer");
    /** A puff of a signal flare's smoke, in the flare's colour, rising high enough to be seen from the air. */
    public static final DeferredHolder<ParticleType<?>, ParticleType<ColorParticleOption>> FLARE_SMOKE = PARTICLES.register(
        "flare_smoke", () -> new ParticleType<ColorParticleOption>(true) {
            @Override
            public MapCodec<ColorParticleOption> codec() {
                return ColorParticleOption.codec(this);
            }

            @Override
            public StreamCodec<? super RegistryFriendlyByteBuf, ColorParticleOption> streamCodec() {
                return ColorParticleOption.streamCodec(this);
            }
        }
    );
    /** A puff of an engine's contrail, laid high up and left hanging in the sky. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CONTRAIL = PARTICLES.register("contrail", () -> new SimpleParticleType(true));

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
    public static final DeferredBlock<CabinLightBlock> CABIN_LIGHT = BLOCKS.registerBlock(
        "cabin_light", CabinLightBlock::new, metal(MapColor.SNOW).lightLevel(state -> state.getValue(Brightness.PROPERTY).light(15)).sound(SoundType.GLASS)
    );
    public static final DeferredBlock<Block> LANDING_GEAR = BLOCKS.registerSimpleBlock("landing_gear", metal(MapColor.METAL));
    public static final DeferredBlock<Block> LANDING_GEAR_TIRE = BLOCKS.registerSimpleBlock(
        "landing_gear_tire",
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F, 3.0F).sound(SoundType.WOOL).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredBlock<CabinWindowBlock> CABIN_WINDOW = BLOCKS.registerBlock("cabin_window", CabinWindowBlock::new, glass());
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

    /** The skin and the windscreen cut to the curve of the hull; placed only by the blueprints, so they have no items. */
    public static final DeferredBlock<ShellBlock> AIRFRAME_SHELL = BLOCKS.registerBlock(
        "airframe_shell", properties -> new ShellBlock(properties, AerolargeRegistry.AIRFRAME_PANEL), metal(MapColor.SNOW).noOcclusion()
    );
    public static final DeferredBlock<CockpitGlassBlock> COCKPIT_GLASS = BLOCKS.registerBlock(
        "cockpit_glass", properties -> new CockpitGlassBlock(properties, AerolargeRegistry.COCKPIT_WINDOW), glass()
    );
    /** The flight deck: four thrust levers on the pedestal, the relays they drive in the nacelles, the instrument panel. */
    public static final DeferredBlock<ThrustLeverBlock> THRUST_LEVER = BLOCKS.registerBlock(
        "thrust_lever", ThrustLeverBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThrustLeverBlockEntity>> THRUST_LEVER_ENTITY =
        BLOCK_ENTITIES.register("thrust_lever", () -> BlockEntityType.Builder.of(ThrustLeverBlockEntity::new, THRUST_LEVER.get()).build(null));
    /** The nose wheel tiller and the parking brake, beside the pilots' seats, sharing one kind of linked block entity. */
    public static final DeferredBlock<TillerBlock> TILLER = BLOCKS.registerBlock("tiller", TillerBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion());
    /** The switch in the instrument panel that turns the jump lights by the doors red, then green. */
    public static final DeferredBlock<JumpSwitchBlock> JUMP_SWITCH = BLOCKS.registerBlock(
        "jump_switch", JumpSwitchBlock::new, metal(MapColor.COLOR_BLACK).lightLevel(state -> 7)
    );
    public static final DeferredBlock<ParkingBrakeBlock> PARKING_BRAKE = BLOCKS.registerBlock(
        "parking_brake", ParkingBrakeBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LinkedControlBlockEntity>> LINKED_CONTROL_ENTITY =
        BLOCK_ENTITIES.register("linked_control", () -> BlockEntityType.Builder.of(LinkedControlBlockEntity::new, TILLER.get(), PARKING_BRAKE.get(), JUMP_SWITCH.get()).build(null));
    public static final DeferredBlock<AutopilotBlock> AUTOPILOT = BLOCKS.registerBlock(
        "autopilot", AutopilotBlock::new, metal(MapColor.COLOR_BLACK).lightLevel(state -> 7)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutopilotBlockEntity>> AUTOPILOT_ENTITY =
        BLOCK_ENTITIES.register("autopilot", () -> BlockEntityType.Builder.of(AutopilotBlockEntity::new, AUTOPILOT.get()).build(null));
    /** The exterior lights: navigation lights, strobes and beacons on the wingtips, tail and fuselage. */
    public static final DeferredBlock<NavLightBlock> NAV_LIGHT = BLOCKS.registerBlock(
        "nav_light", NavLightBlock::new, glass().lightLevel(state -> 9).noCollission()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NavLightBlockEntity>> NAV_LIGHT_ENTITY =
        BLOCK_ENTITIES.register("nav_light", () -> BlockEntityType.Builder.of(NavLightBlockEntity::new, NAV_LIGHT.get()).build(null));
    public static final DeferredBlock<ThrustRelayBlock> THRUST_RELAY = BLOCKS.registerBlock("thrust_relay", ThrustRelayBlock::new, metal(MapColor.SNOW));
    /** The same relay set into the cabin wall beside a lamp it lights, plated like the hull around it. */
    public static final DeferredBlock<ThrustRelayBlock> HULL_RELAY = BLOCKS.registerBlock("hull_relay", ThrustRelayBlock::new, metal(MapColor.SNOW));
    /** The primary flight displays in the panel, one in front of each pilot. */
    public static final DeferredBlock<FlightDisplayBlock> FLIGHT_DISPLAY = BLOCKS.registerBlock(
        "flight_display", FlightDisplayBlock::new, metal(MapColor.COLOR_BLACK).lightLevel(state -> 7)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlightDisplayBlockEntity>> FLIGHT_DISPLAY_ENTITY =
        BLOCK_ENTITIES.register("flight_display", () -> BlockEntityType.Builder.of(FlightDisplayBlockEntity::new, FLIGHT_DISPLAY.get()).build(null));
    /** The big touch screen in the middle of the instrument panel that works the autopilot; placed only by the blueprints. */
    public static final DeferredBlock<AutopilotScreenBlock> AUTOPILOT_SCREEN = BLOCKS.registerBlock(
        "autopilot_screen", AutopilotScreenBlock::new, metal(MapColor.COLOR_BLACK).lightLevel(state -> 9)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutopilotScreenBlockEntity>> AUTOPILOT_SCREEN_ENTITY =
        BLOCK_ENTITIES.register("autopilot_screen", () -> BlockEntityType.Builder.of(AutopilotScreenBlockEntity::new, AUTOPILOT_SCREEN.get()).build(null));
    public static final DeferredBlock<EngineCoreBlock> ENGINE_CORE = BLOCKS.registerBlock("engine_core", EngineCoreBlock::new, metal(MapColor.SNOW));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineCoreBlockEntity>> ENGINE_CORE_ENTITY =
        BLOCK_ENTITIES.register("engine_core", () -> BlockEntityType.Builder.of(EngineCoreBlockEntity::new, ENGINE_CORE.get()).build(null));
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
    public static final DeferredItem<BlockItem> FLIGHT_DISPLAY_ITEM = ITEMS.registerSimpleBlockItem(FLIGHT_DISPLAY);
    public static final DeferredItem<BlockItem> TILLER_ITEM = ITEMS.registerSimpleBlockItem(TILLER);
    public static final DeferredItem<BlockItem> PARKING_BRAKE_ITEM = ITEMS.registerSimpleBlockItem(PARKING_BRAKE);
    public static final DeferredItem<BlockItem> NAV_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(NAV_LIGHT);
    public static final DeferredItem<BlockItem> AIRSTAIR_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(AIRSTAIR_CONTROLLER);
    public static final DeferredItem<BlockItem> AIRSTAIR_CALL_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(AIRSTAIR_CALL_BUTTON);

    /** Skydiving out of the airliner: the jump light by the doors, the rack of parachutes and the target to land on. */
    public static final DeferredBlock<JumpLightBlock> JUMP_LIGHT = BLOCKS.registerBlock(
        "jump_light", JumpLightBlock::new, glass().lightLevel(state -> state.getValue(JumpLightBlock.SIGNAL).light())
    );
    public static final DeferredBlock<ParachuteRackBlock> PARACHUTE_RACK = BLOCKS.registerBlock(
        "parachute_rack", ParachuteRackBlock::new, metal(MapColor.COLOR_GREEN)
    );
    public static final DeferredBlock<LandingTargetBlock> LANDING_TARGET = BLOCKS.registerBlock(
        "landing_target", LandingTargetBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.5F).sound(SoundType.WOOL)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LandingTargetBlockEntity>> LANDING_TARGET_ENTITY =
        BLOCK_ENTITIES.register("landing_target", () -> BlockEntityType.Builder.of(LandingTargetBlockEntity::new, LANDING_TARGET.get()).build(null));
    public static final DeferredItem<ParachuteItem> PARACHUTE = ITEMS.register("parachute", () -> new ParachuteItem(new Item.Properties()));
    /** Whether a worn parachute is open, kept on its stack so every client sees the canopy. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> PARACHUTE_OPEN = DATA_COMPONENTS.registerComponentType(
        "parachute_open", builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL)
    );
    public static final DeferredItem<BlockItem> JUMP_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(JUMP_SWITCH);
    public static final DeferredItem<BlockItem> JUMP_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(JUMP_LIGHT);
    public static final DeferredItem<BlockItem> PARACHUTE_RACK_ITEM = ITEMS.registerSimpleBlockItem(PARACHUTE_RACK);
    public static final DeferredItem<BlockItem> LANDING_TARGET_ITEM = ITEMS.registerSimpleBlockItem(LANDING_TARGET);

    /** Speaking to the cabin and the tower: the captain's handset on the flight deck and a radio for the airfield. */
    public static final DeferredBlock<HandsetBlock> HANDSET = BLOCKS.registerBlock("handset", HandsetBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion());
    public static final DeferredBlock<TowerRadioBlock> TOWER_RADIO = BLOCKS.registerBlock("tower_radio", TowerRadioBlock::new, metal(MapColor.COLOR_GRAY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioBlockEntity>> RADIO_ENTITY =
        BLOCK_ENTITIES.register("radio", () -> BlockEntityType.Builder.of(RadioBlockEntity::new, HANDSET.get(), TOWER_RADIO.get()).build(null));
    public static final DeferredItem<BlockItem> HANDSET_ITEM = ITEMS.registerSimpleBlockItem(HANDSET);
    public static final DeferredItem<BlockItem> TOWER_RADIO_ITEM = ITEMS.registerSimpleBlockItem(TOWER_RADIO);

    /** Buckling up: the seatbelt switch on the flight deck and the signs it lights in the cabin walls. */
    public static final DeferredBlock<SeatbeltSwitchBlock> SEATBELT_SWITCH = BLOCKS.registerBlock(
        "seatbelt_switch", SeatbeltSwitchBlock::new, metal(MapColor.COLOR_BLACK).lightLevel(state -> 7)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeatbeltSwitchBlockEntity>> SEATBELT_SWITCH_ENTITY =
        BLOCK_ENTITIES.register("seatbelt_switch", () -> BlockEntityType.Builder.of(SeatbeltSwitchBlockEntity::new, SEATBELT_SWITCH.get()).build(null));
    public static final DeferredBlock<SeatbeltSignBlock> SEATBELT_SIGN = BLOCKS.registerBlock(
        "seatbelt_sign", SeatbeltSignBlock::new, glass().lightLevel(state -> state.getValue(SeatbeltSignBlock.LIT) ? 10 : 0)
    );
    public static final DeferredItem<BlockItem> SEATBELT_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(SEATBELT_SWITCH);
    public static final DeferredItem<BlockItem> SEATBELT_SIGN_ITEM = ITEMS.registerSimpleBlockItem(SEATBELT_SIGN);

    /** Modern cabin lighting: LED strips along the tops of the walls, and pendant lights hung on slim cables. */
    public static final DeferredBlock<LightStripBlock> LIGHT_STRIP = BLOCKS.registerBlock(
        "light_strip", LightStripBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.3F).sound(SoundType.GLASS).noCollission().noOcclusion()
            .lightLevel(state -> state.getValue(Brightness.PROPERTY).light(13))
    );
    public static final DeferredBlock<PendantLightBlock> PENDANT_LIGHT = BLOCKS.registerBlock(
        "pendant_light", PendantLightBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.3F).sound(SoundType.LANTERN).noOcclusion()
            .lightLevel(state -> state.getValue(Brightness.PROPERTY).light(15))
    );
    public static final DeferredBlock<LightCableBlock> LIGHT_CABLE = BLOCKS.registerBlock(
        "light_cable", LightCableBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.2F).sound(SoundType.CHAIN).noCollission().noOcclusion()
    );
    /** The overhead lockers along the cabin walls. */
    public static final DeferredBlock<OverheadBinBlock> OVERHEAD_BIN = BLOCKS.registerBlock(
        "overhead_bin", OverheadBinBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.5F).sound(SoundType.WOOD).noOcclusion().isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverheadBinBlockEntity>> OVERHEAD_BIN_ENTITY =
        BLOCK_ENTITIES.register("overhead_bin", () -> BlockEntityType.Builder.of(OverheadBinBlockEntity::new, OVERHEAD_BIN.get()).build(null));
    public static final DeferredItem<BlockItem> LIGHT_STRIP_ITEM = ITEMS.registerSimpleBlockItem(LIGHT_STRIP);
    public static final DeferredItem<BlockItem> PENDANT_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(PENDANT_LIGHT);
    public static final DeferredItem<BlockItem> LIGHT_CABLE_ITEM = ITEMS.registerSimpleBlockItem(LIGHT_CABLE);
    public static final DeferredItem<BlockItem> OVERHEAD_BIN_ITEM = ITEMS.registerSimpleBlockItem(OVERHEAD_BIN);
    /** The cabin lights' dimmer on the flight deck, and the block entity any cabin control keeps what it works in. */
    public static final DeferredBlock<CabinLightsBlock> CABIN_LIGHTS = BLOCKS.registerBlock(
        "cabin_lights", CabinLightsBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 5)
    );
    /** The window shades' control on the flight deck. */
    public static final DeferredBlock<WindowShadesBlock> WINDOW_SHADES = BLOCKS.registerBlock(
        "window_shades", WindowShadesBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 5)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CabinControlBlockEntity>> CABIN_CONTROL_ENTITY =
        BLOCK_ENTITIES.register("cabin_control", () -> BlockEntityType.Builder.of(CabinControlBlockEntity::new, CABIN_LIGHTS.get(), WINDOW_SHADES.get()).build(null));
    public static final DeferredItem<BlockItem> CABIN_LIGHTS_ITEM = ITEMS.registerSimpleBlockItem(CABIN_LIGHTS);
    public static final DeferredItem<BlockItem> WINDOW_SHADES_ITEM = ITEMS.registerSimpleBlockItem(WINDOW_SHADES);

    /** A signal flare to mark a drop zone with a column of coloured smoke. */
    public static final DeferredBlock<SignalFlareBlock> SIGNAL_FLARE = BLOCKS.registerBlock(
        "signal_flare", SignalFlareBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollission().instabreak().sound(SoundType.WOOD).lightLevel(state -> 12)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SignalFlareBlockEntity>> SIGNAL_FLARE_ENTITY =
        BLOCK_ENTITIES.register("signal_flare", () -> BlockEntityType.Builder.of(SignalFlareBlockEntity::new, SIGNAL_FLARE.get()).build(null));
    public static final DeferredItem<BlockItem> SIGNAL_FLARE_ITEM = ITEMS.registerSimpleBlockItem(SIGNAL_FLARE);

    /** Basketball in the gym hall: the ball, the hoops, the scoreboard and a rack of balls by the court. */
    public static final DeferredHolder<EntityType<?>, EntityType<Basketball>> BASKETBALL = ENTITY_TYPES.register(
        "basketball", () -> EntityType.Builder.<Basketball>of(Basketball::new, MobCategory.MISC).sized(0.4F, 0.4F)
            .clientTrackingRange(8).updateInterval(2).build("basketball")
    );
    public static final DeferredItem<BasketballItem> BASKETBALL_ITEM = ITEMS.register("basketball", () -> new BasketballItem(new Item.Properties().stacksTo(16)));
    public static final DeferredBlock<HoopBlock> HOOP = BLOCKS.registerBlock(
        "hoop", HoopBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(1.0F).sound(SoundType.CHAIN).noOcclusion()
    );
    public static final DeferredBlock<ScoreboardBlock> SCOREBOARD = BLOCKS.registerBlock(
        "scoreboard", ScoreboardBlock::new, metal(MapColor.COLOR_BLACK).lightLevel(state -> 5)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScoreboardBlockEntity>> SCOREBOARD_ENTITY =
        BLOCK_ENTITIES.register("scoreboard", () -> BlockEntityType.Builder.of(ScoreboardBlockEntity::new, SCOREBOARD.get()).build(null));
    public static final DeferredBlock<BallRackBlock> BALL_RACK = BLOCKS.registerBlock("ball_rack", BallRackBlock::new, metal(MapColor.METAL).noOcclusion());
    public static final DeferredItem<BlockItem> HOOP_ITEM = ITEMS.registerSimpleBlockItem(HOOP);
    public static final DeferredItem<BlockItem> SCOREBOARD_ITEM = ITEMS.registerSimpleBlockItem(SCOREBOARD);
    public static final DeferredItem<BlockItem> BALL_RACK_ITEM = ITEMS.registerSimpleBlockItem(BALL_RACK);

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
            AIRSTAIR_CONTROLLER_ITEM, AIRSTAIR_CALL_BUTTON_ITEM, THRUST_LEVER_ITEM, INSTRUMENT_PANEL_ITEM, AUTOPILOT_ITEM, TILLER_ITEM, PARKING_BRAKE_ITEM, NAV_LIGHT_ITEM, FLIGHT_DISPLAY_ITEM,
            JUMP_SWITCH_ITEM, JUMP_LIGHT_ITEM, PARACHUTE_RACK_ITEM, LANDING_TARGET_ITEM, PARACHUTE, HANDSET_ITEM, TOWER_RADIO_ITEM,
            SEATBELT_SWITCH_ITEM, SEATBELT_SIGN_ITEM, LIGHT_STRIP_ITEM, PENDANT_LIGHT_ITEM, LIGHT_CABLE_ITEM, OVERHEAD_BIN_ITEM, CABIN_LIGHTS_ITEM, WINDOW_SHADES_ITEM,
            BASKETBALL_ITEM, HOOP_ITEM, SCOREBOARD_ITEM, BALL_RACK_ITEM, SIGNAL_FLARE_ITEM
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

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, name)));
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
        // The invisible backing the windscreen used to be drawn over, from before the nose was built of blocks: glass now.
        for (String old : new String[]{"windscreen", "cockpit_windscreen"}) {
            BLOCKS.addAlias(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, old), COCKPIT_GLASS.getId());
        }
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
        SOUNDS.register(modEventBus);
        PARTICLES.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        ENTITY_TYPES.register(modEventBus);
    }
}
