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
import dev.aerolarge.block.BallPitBlock;
import dev.aerolarge.block.BallRackBlock;
import dev.aerolarge.block.Brightness;
import dev.aerolarge.block.CabinControlBlockEntity;
import dev.aerolarge.block.CabinLightBlock;
import dev.aerolarge.block.CabinLightsBlock;
import dev.aerolarge.block.CabinWindowBlock;
import dev.aerolarge.block.CabinWindowShellBlock;
import dev.aerolarge.block.CargoCrateBlock;
import dev.aerolarge.block.CargoCrateBlockEntity;
import dev.aerolarge.block.CheckInDeskBlock;
import dev.aerolarge.block.CockpitGlassBlock;
import dev.aerolarge.block.CurtainBlock;
import dev.aerolarge.block.DropBayBlock;
import dev.aerolarge.block.DropBayBlockEntity;
import dev.aerolarge.block.DropReleaseBlock;
import dev.aerolarge.block.DropReleaseBlockEntity;
import dev.aerolarge.block.DutyFreeCounterBlock;
import dev.aerolarge.block.EngineCoreBlock;
import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.block.ExitSignBlock;
import dev.aerolarge.block.FairingBlock;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.FlightDisplayBlockEntity;
import dev.aerolarge.block.FloorHatchBlock;
import dev.aerolarge.block.GalleyBlock;
import dev.aerolarge.block.GymRingsBlock;
import dev.aerolarge.block.HandsetBlock;
import dev.aerolarge.block.HideAndSeekButtonBlock;
import dev.aerolarge.block.HoopBlock;
import dev.aerolarge.block.InstrumentPanelBlock;
import dev.aerolarge.block.JetBridgeBlock;
import dev.aerolarge.block.JetBridgeBlockEntity;
import dev.aerolarge.block.JumpLightBlock;
import dev.aerolarge.block.JumpSwitchBlock;
import dev.aerolarge.block.LandingTargetBlock;
import dev.aerolarge.block.LandingTargetBlockEntity;
import dev.aerolarge.block.LavIceBlock;
import dev.aerolarge.block.LightCableBlock;
import dev.aerolarge.block.LightStripBlock;
import dev.aerolarge.block.LinkedControlBlockEntity;
import dev.aerolarge.block.ModelAirlinerBlock;
import dev.aerolarge.block.NavLightBlock;
import dev.aerolarge.block.NavLightBlockEntity;
import dev.aerolarge.block.OverheadBinBlock;
import dev.aerolarge.block.OverheadBinBlockEntity;
import dev.aerolarge.block.ParachuteRackBlock;
import dev.aerolarge.block.ParkingBrakeBlock;
import dev.aerolarge.block.PassengerScreenBlock;
import dev.aerolarge.block.PassengerScreenBlockEntity;
import dev.aerolarge.block.PendantLightBlock;
import dev.aerolarge.block.PilotSeatBlock;
import dev.aerolarge.block.PlugDoorBlock;
import dev.aerolarge.block.PlugDoorBlockEntity;
import dev.aerolarge.block.RadioBlockEntity;
import dev.aerolarge.block.ScoreboardBlock;
import dev.aerolarge.block.ScoreboardBlockEntity;
import dev.aerolarge.block.SeatbeltSignBlock;
import dev.aerolarge.block.SeatbeltSwitchBlock;
import dev.aerolarge.block.SeatbeltSwitchBlockEntity;
import dev.aerolarge.block.SecretBookshelfBlock;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.SignalFlareBlock;
import dev.aerolarge.block.SignalFlareBlockEntity;
import dev.aerolarge.block.SpringboardBlock;
import dev.aerolarge.block.SuitcaseBlock;
import dev.aerolarge.block.SuitcaseBlockEntity;
import dev.aerolarge.block.TailCameraDisplayBlock;
import dev.aerolarge.block.TailCameraDisplayBlockEntity;
import dev.aerolarge.block.ThrustLeverBlock;
import dev.aerolarge.block.ThrustLeverBlockEntity;
import dev.aerolarge.block.ThrustRelayBlock;
import dev.aerolarge.block.TillerBlock;
import dev.aerolarge.block.TowerRadioBlock;
import dev.aerolarge.block.VacuumToiletBlock;
import dev.aerolarge.block.WindowShadesBlock;
import dev.aerolarge.cabin.Passengers;
import dev.aerolarge.entity.Basketball;
import dev.aerolarge.entity.BlueIceChunk;
import dev.aerolarge.entity.FallingCrate;
import dev.aerolarge.entity.PaperPlane;
import dev.aerolarge.entity.Trolley;
import dev.aerolarge.item.AirlinerBlueprintItem;
import dev.aerolarge.item.BasketballItem;
import dev.aerolarge.item.BoardingPassItem;
import dev.aerolarge.item.CoffeeItem;
import dev.aerolarge.item.PaperPlaneItem;
import dev.aerolarge.item.ParachuteItem;
import dev.aerolarge.item.PilotCapItem;
import dev.aerolarge.item.PilotHelmetItem;
import dev.aerolarge.item.TrolleyItem;
import java.util.List;
import java.util.function.Supplier;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

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
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Aerolarge.MOD_ID);
    /** How brightly a starlight panel glows full on, in light levels: softly, the lounge's pendants doing the rest. */
    private static final int STARLIGHT_GLOW = 10;
    /** How brightly the hide and seek button glows while pressed, in light levels. */
    private static final int PRESSED_GLOW = 7;

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
    /** The same voice over the cabin PA, after the chime: crew seated for take-off, for landing, and arrival. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_TAKEOFF = sound("announcement_takeoff");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_LANDING = sound("announcement_landing");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_ARRIVAL = sound("announcement_arrival");
    /** The two-tone chime before a cabin announcement, and the crackle of a radio call. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PA_CHIME = sound("pa_chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_CRACKLE = sound("radio_crackle");
    /** The ding of the seatbelt signs coming on. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SEATBELT_DING = sound("seatbelt_ding");
    /** The whirr of the window shades' motors. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SHADE_MOTOR = sound("shade_motor");
    /** The trolley's wheels squeaking. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TROLLEY_SQUEAK = sound("trolley_squeak");
    /** Basketball: the ball bouncing, the swish of a basket and the scoreboard's buzzer. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BALL_BOUNCE = sound("ball_bounce");
    public static final DeferredHolder<SoundEvent, SoundEvent> BASKET_SWISH = sound("basket_swish");
    public static final DeferredHolder<SoundEvent, SoundEvent> SCOREBOARD_BUZZER = sound("scoreboard_buzzer");
    /** A springboard throwing someone up in the air. */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPRINGBOARD_BOING = sound("springboard_boing");
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
    /**
     * Built into the crown, the walls, the hold's belly and the walls of the pool, as bright as the flight deck's
     * dimmer has it; the spawn guard, not their light, keeps mobs out of the cabin, so they can be turned down to a
     * night glow.
     */
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
    /**
     * The doors inside the cabin (the cockpit door, the WC's, the crew room's), and the hull's doors in airliners built
     * before the plug doors.
     */
    public static final DeferredBlock<DoorBlock> AIRLINER_DOOR = BLOCKS.registerBlock(
        "airliner_door",
        properties -> new DoorBlock(AIRLINER, properties),
        metal(MapColor.SNOW).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    /**
     * The doors in the hull's sides: one panel over each doorway, drawn by its renderer as it moves out of the frame
     * and swings away. Placed only by the blueprints, so it has no item.
     */
    public static final DeferredBlock<PlugDoorBlock> PLUG_DOOR = BLOCKS.registerBlock("plug_door", PlugDoorBlock::new, metal(MapColor.SNOW).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlugDoorBlockEntity>> PLUG_DOOR_ENTITY =
        BLOCK_ENTITIES.register("plug_door", () -> BlockEntityType.Builder.of(PlugDoorBlockEntity::new, PLUG_DOOR.get()).build(null));
    /** A plug door's pneumatic hiss as it starts to move, and the clunk of its latches. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PLUG_DOOR_HISS = sound("plug_door_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLUG_DOOR_LATCH = sound("plug_door_latch");
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

    /**
     * The skin cut to the curve of the hull, and below it the glass cut alike; placed only by the blueprints, so they
     * have no items. The skin occludes, by the sides its cut leaves whole (see {@link ShellBlock}), to keep the
     * daylight out of the cabin; the glass (the curved cabin windows with their shades up, the windscreen, the moon
     * pool's belly) lets it through.
     */
    public static final DeferredBlock<ShellBlock> AIRFRAME_SHELL = BLOCKS.registerBlock(
        "airframe_shell", properties -> new ShellBlock(properties, AerolargeRegistry.AIRFRAME_PANEL), metal(MapColor.SNOW)
    );
    /** A cabin window cut to the hull's curve, where the fuselage's side curves; placed only by the blueprints too. */
    public static final DeferredBlock<CabinWindowShellBlock> CABIN_WINDOW_SHELL = BLOCKS.registerBlock(
        "cabin_window_shell", properties -> new CabinWindowShellBlock(properties, AerolargeRegistry.CABIN_WINDOW), glass()
    );
    public static final DeferredBlock<CockpitGlassBlock> COCKPIT_GLASS = BLOCKS.registerBlock(
        "cockpit_glass", properties -> new CockpitGlassBlock(properties, AerolargeRegistry.COCKPIT_WINDOW), glass()
    );
    /** Clear glass cut to the hull's curve, for the moon pool's view down through the belly; it gives back glass. */
    public static final DeferredBlock<CockpitGlassBlock> HULL_GLASS = BLOCKS.registerBlock(
        "hull_glass", properties -> new CockpitGlassBlock(properties, () -> Blocks.GLASS), glass()
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
    /** The pilots' seats: two blocks tall, sat in like Create's seats. */
    public static final DeferredBlock<PilotSeatBlock> PILOT_SEAT = BLOCKS.registerBlock(
        "pilot_seat", PilotSeatBlock::new, metal(MapColor.COLOR_BLACK).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
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
    /** The wide screen over the autopilot screen showing the picture from the camera on top of the fin. */
    public static final DeferredBlock<TailCameraDisplayBlock> TAIL_CAMERA_DISPLAY = BLOCKS.registerBlock(
        "tail_camera_display", TailCameraDisplayBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 5)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TailCameraDisplayBlockEntity>> TAIL_CAMERA_DISPLAY_ENTITY =
        BLOCK_ENTITIES.register("tail_camera_display", () -> BlockEntityType.Builder.of(TailCameraDisplayBlockEntity::new, TAIL_CAMERA_DISPLAY.get()).build(null));
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
    public static final DeferredItem<BlockItem> TAIL_CAMERA_DISPLAY_ITEM = ITEMS.registerSimpleBlockItem(TAIL_CAMERA_DISPLAY);
    public static final DeferredItem<BlockItem> TILLER_ITEM = ITEMS.registerSimpleBlockItem(TILLER);
    public static final DeferredItem<BlockItem> PARKING_BRAKE_ITEM = ITEMS.registerSimpleBlockItem(PARKING_BRAKE);
    public static final DeferredItem<BlockItem> PILOT_SEAT_ITEM = ITEMS.registerSimpleBlockItem(PILOT_SEAT);
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

    /** The jet bridge at the terminal, reaching out to an airliner's door. */
    public static final DeferredBlock<JetBridgeBlock> JET_BRIDGE = BLOCKS.registerBlock("jet_bridge", JetBridgeBlock::new, metal(MapColor.COLOR_GRAY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JetBridgeBlockEntity>> JET_BRIDGE_ENTITY =
        BLOCK_ENTITIES.register("jet_bridge", () -> BlockEntityType.Builder.of(JetBridgeBlockEntity::new, JET_BRIDGE.get()).build(null));
    public static final DeferredItem<BlockItem> JET_BRIDGE_ITEM = ITEMS.registerSimpleBlockItem(JET_BRIDGE);

    /** Villager passengers: the check-in desk at the terminal, the boarding passes it prints and a passenger's trip. */
    public static final DeferredBlock<CheckInDeskBlock> CHECK_IN_DESK = BLOCKS.registerBlock("check_in_desk", CheckInDeskBlock::new, metal(MapColor.COLOR_BLUE));
    public static final DeferredItem<BlockItem> CHECK_IN_DESK_ITEM = ITEMS.registerSimpleBlockItem(CHECK_IN_DESK);
    public static final DeferredItem<BoardingPassItem> BOARDING_PASS = ITEMS.register("boarding_pass", () -> new BoardingPassItem(new Item.Properties()));
    // The default trip is never read: a villager without one has no data at all.
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<Passengers.Trip>> TRIP = ATTACHMENT_TYPES.register(
        "trip", () -> AttachmentType.builder(() -> Passengers.Trip.boarding(Vec3.ZERO, null)).serialize(Passengers.Trip.CODEC).build()
    );

    /** Buckling up: the seatbelt switch on the flight deck and the signs it lights in the cabin walls. */
    public static final DeferredBlock<SeatbeltSwitchBlock> SEATBELT_SWITCH = BLOCKS.registerBlock(
        "seatbelt_switch", SeatbeltSwitchBlock::new, metal(MapColor.COLOR_BLACK).lightLevel(state -> 7)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SeatbeltSwitchBlockEntity>> SEATBELT_SWITCH_ENTITY =
        BLOCK_ENTITIES.register("seatbelt_switch", () -> BlockEntityType.Builder.of(SeatbeltSwitchBlockEntity::new, SEATBELT_SWITCH.get()).build(null));
    /** Lit, the sign glows as a backlit sign does (its face is drawn at full brightness) but gives the cabin little light. */
    public static final DeferredBlock<SeatbeltSignBlock> SEATBELT_SIGN = BLOCKS.registerBlock(
        "seatbelt_sign", SeatbeltSignBlock::new, glass().lightLevel(state -> state.getValue(SeatbeltSignBlock.LIT) ? 4 : 0)
    );
    public static final DeferredItem<BlockItem> SEATBELT_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(SEATBELT_SWITCH);
    public static final DeferredItem<BlockItem> SEATBELT_SIGN_ITEM = ITEMS.registerSimpleBlockItem(SEATBELT_SIGN);

    /**
     * Modern cabin lighting: LED strips along the tops of the walls, and pendant lights hung on slim cables. The strips
     * give all a lamp can: no daylight comes in through the skin, and over most of the main deck they are what lights
     * it.
     */
    public static final DeferredBlock<LightStripBlock> LIGHT_STRIP = BLOCKS.registerBlock(
        "light_strip", LightStripBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.3F).sound(SoundType.GLASS).noCollission().noOcclusion()
            .lightLevel(state -> state.getValue(Brightness.PROPERTY).light(15))
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
    /**
     * The lounge's starlit ceiling: navy panels scattered with twinkling stars that glow whatever the light around, so
     * with the cabin lights set for the night only the stars show. Full on, the panels glow softly themselves.
     */
    public static final DeferredBlock<CabinLightBlock> STARLIGHT_PANEL = BLOCKS.registerBlock(
        "starlight_panel", CabinLightBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.5F).sound(SoundType.WOOD).isValidSpawn(AerolargeRegistry::never)
            .lightLevel(state -> state.getValue(Brightness.PROPERTY).light(STARLIGHT_GLOW))
    );
    public static final DeferredItem<BlockItem> LIGHT_STRIP_ITEM = ITEMS.registerSimpleBlockItem(LIGHT_STRIP);
    public static final DeferredItem<BlockItem> STARLIGHT_PANEL_ITEM = ITEMS.registerSimpleBlockItem(STARLIGHT_PANEL);
    public static final DeferredItem<BlockItem> PENDANT_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(PENDANT_LIGHT);
    public static final DeferredItem<BlockItem> LIGHT_CABLE_ITEM = ITEMS.registerSimpleBlockItem(LIGHT_CABLE);
    public static final DeferredItem<BlockItem> OVERHEAD_BIN_ITEM = ITEMS.registerSimpleBlockItem(OVERHEAD_BIN);
    /** The passengers' screens in the seat backs and bulkheads, showing how the airliner flies. */
    public static final DeferredBlock<PassengerScreenBlock> PASSENGER_SCREEN = BLOCKS.registerBlock(
        "passenger_screen", PassengerScreenBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 5)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PassengerScreenBlockEntity>> PASSENGER_SCREEN_ENTITY =
        BLOCK_ENTITIES.register("passenger_screen", () -> BlockEntityType.Builder.of(PassengerScreenBlockEntity::new, PASSENGER_SCREEN.get()).build(null));
    public static final DeferredItem<BlockItem> PASSENGER_SCREEN_ITEM = ITEMS.registerSimpleBlockItem(PASSENGER_SCREEN);
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

    /**
     * Dividing the cabin into rooms: curtains to draw across it, cloth that nothing bumps into, and the bulkheads they
     * hang in, a smooth cream wall that is the cabin's own and no part of the hull.
     */
    public static final DeferredBlock<CurtainBlock> CABIN_CURTAIN = BLOCKS.registerBlock(
        "cabin_curtain", CurtainBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.8F).sound(SoundType.WOOL).noCollission().noOcclusion()
            .pushReaction(PushReaction.DESTROY).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredBlock<Block> CABIN_PARTITION = BLOCKS.registerSimpleBlock(
        "cabin_partition", BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5F).sound(SoundType.WOOD).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredItem<BlockItem> CABIN_CURTAIN_ITEM = ITEMS.registerSimpleBlockItem(CABIN_CURTAIN);
    public static final DeferredItem<BlockItem> CABIN_PARTITION_ITEM = ITEMS.registerSimpleBlockItem(CABIN_PARTITION);
    /**
     * The green running man over the doors, lit as an exit sign is: enough to find a door by with the cabin lights set
     * for the night, and no more than the passenger screens give, so the cabin stays dark.
     */
    public static final DeferredBlock<ExitSignBlock> EXIT_SIGN = BLOCKS.registerBlock(
        "exit_sign", ExitSignBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN).strength(0.5F).sound(SoundType.METAL).noCollission().noOcclusion()
            .isValidSpawn(AerolargeRegistry::never).lightLevel(state -> 4)
    );
    public static final DeferredItem<BlockItem> EXIT_SIGN_ITEM = ITEMS.registerSimpleBlockItem(EXIT_SIGN);
    /**
     * The lavatories' vacuum toilets and their flush, and the blue ice each flush drops out of the belly: the chunk
     * falling, the crack as it lands or the bonk on whoever it lands on, and the lump of it on the ground that melts
     * away. Only a falling chunk leaves a lump, so it has no item and gives nothing back; nothing bumps into it, so it
     * never stands in the way of a wheel.
     */
    public static final DeferredBlock<VacuumToiletBlock> VACUUM_TOILET = BLOCKS.registerBlock(
        "vacuum_toilet", VacuumToiletBlock::new, metal(MapColor.SNOW).noOcclusion()
    );
    public static final DeferredItem<BlockItem> VACUUM_TOILET_ITEM = ITEMS.registerSimpleBlockItem(VACUUM_TOILET);
    public static final DeferredHolder<SoundEvent, SoundEvent> TOILET_FLUSH = sound("toilet_flush");
    public static final DeferredHolder<EntityType<?>, EntityType<BlueIceChunk>> BLUE_ICE_CHUNK = ENTITY_TYPES.register(
        "blue_ice_chunk", () -> EntityType.Builder.<BlueIceChunk>of(BlueIceChunk::new, MobCategory.MISC).sized(0.6F, 0.5F)
            .clientTrackingRange(16).updateInterval(2).build("blue_ice_chunk")
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> BLUE_ICE_CRACK = sound("blue_ice_crack");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLUE_ICE_BONK = sound("blue_ice_bonk");
    public static final DeferredBlock<LavIceBlock> LAV_ICE = BLOCKS.registerBlock(
        "lav_ice", LavIceBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.ICE).strength(0.2F).sound(SoundType.GLASS).noCollission().noOcclusion().noLootTable()
            .pushReaction(PushReaction.DESTROY)
    );

    /** A signal flare to mark a drop zone with a column of coloured smoke. */
    public static final DeferredBlock<SignalFlareBlock> SIGNAL_FLARE = BLOCKS.registerBlock(
        "signal_flare", SignalFlareBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).noCollission().instabreak().sound(SoundType.WOOD).lightLevel(state -> 12)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SignalFlareBlockEntity>> SIGNAL_FLARE_ENTITY =
        BLOCK_ENTITIES.register("signal_flare", () -> BlockEntityType.Builder.of(SignalFlareBlockEntity::new, SIGNAL_FLARE.get()).build(null));
    public static final DeferredItem<BlockItem> SIGNAL_FLARE_ITEM = ITEMS.registerSimpleBlockItem(SIGNAL_FLARE);
    /** Care packages: the crate to pack and drop, and the crate on its way down under its parachute. */
    public static final DeferredBlock<CargoCrateBlock> CARGO_CRATE = BLOCKS.registerBlock(
        "cargo_crate", CargoCrateBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CargoCrateBlockEntity>> CARGO_CRATE_ENTITY =
        BLOCK_ENTITIES.register("cargo_crate", () -> BlockEntityType.Builder.of(CargoCrateBlockEntity::new, CARGO_CRATE.get()).build(null));
    public static final DeferredItem<BlockItem> CARGO_CRATE_ITEM = ITEMS.registerSimpleBlockItem(CARGO_CRATE, new Item.Properties().stacksTo(1));
    /** The drop bay in the belly and its release on the flight deck. */
    public static final DeferredBlock<DropBayBlock> DROP_BAY = BLOCKS.registerBlock("drop_bay", DropBayBlock::new, metal(MapColor.COLOR_YELLOW));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DropBayBlockEntity>> DROP_BAY_ENTITY =
        BLOCK_ENTITIES.register("drop_bay", () -> BlockEntityType.Builder.of(DropBayBlockEntity::new, DROP_BAY.get()).build(null));
    public static final DeferredBlock<DropReleaseBlock> DROP_RELEASE = BLOCKS.registerBlock(
        "drop_release", DropReleaseBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 5)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DropReleaseBlockEntity>> DROP_RELEASE_ENTITY =
        BLOCK_ENTITIES.register("drop_release", () -> BlockEntityType.Builder.of(DropReleaseBlockEntity::new, DROP_RELEASE.get()).build(null));
    public static final DeferredItem<BlockItem> DROP_BAY_ITEM = ITEMS.registerSimpleBlockItem(DROP_BAY);
    public static final DeferredItem<BlockItem> DROP_RELEASE_ITEM = ITEMS.registerSimpleBlockItem(DROP_RELEASE);
    public static final DeferredHolder<EntityType<?>, EntityType<FallingCrate>> FALLING_CRATE = ENTITY_TYPES.register(
        "falling_crate", () -> EntityType.Builder.<FallingCrate>of(FallingCrate::new, MobCategory.MISC).sized(0.9F, 0.9F)
            .clientTrackingRange(16).updateInterval(2).build("falling_crate")
    );
    /** The hatch in the cabin floor over the ladder down to the hold, so the drop bay can be reloaded in flight. */
    public static final DeferredBlock<FloorHatchBlock> FLOOR_HATCH = BLOCKS.registerBlock(
        "floor_hatch", FloorHatchBlock::new, metal(MapColor.COLOR_YELLOW).noOcclusion()
    );
    public static final DeferredItem<BlockItem> FLOOR_HATCH_ITEM = ITEMS.registerSimpleBlockItem(FLOOR_HATCH);

    /**
     * The secret bookshelf door's creak, opening and shutting: a wooden door's, played deep and slow (see the sounds
     * file). Made here, not by the registry, because the door's block set takes them before the registry has run.
     */
    private static final SoundEvent SECRET_BOOKSHELF_OPEN_SOUND = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "secret_bookshelf_open"));
    private static final SoundEvent SECRET_BOOKSHELF_CLOSE_SOUND = SoundEvent.createVariableRangeEvent(
        ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "secret_bookshelf_close"));
    public static final DeferredHolder<SoundEvent, SoundEvent> SECRET_BOOKSHELF_OPEN = SOUNDS.register("secret_bookshelf_open", () -> SECRET_BOOKSHELF_OPEN_SOUND);
    public static final DeferredHolder<SoundEvent, SoundEvent> SECRET_BOOKSHELF_CLOSE = SOUNDS.register("secret_bookshelf_close", () -> SECRET_BOOKSHELF_CLOSE_SOUND);
    /** The secret bookshelf door: opened by hand like a wooden door, with its own creak. */
    public static final BlockSetType SECRET_BOOKSHELF_SET = BlockSetType.register(new BlockSetType(
        "aerolarge_secret_bookshelf",
        true,
        true,
        false,
        BlockSetType.PressurePlateSensitivity.EVERYTHING,
        SoundType.WOOD,
        SECRET_BOOKSHELF_CLOSE_SOUND,
        SECRET_BOOKSHELF_OPEN_SOUND,
        SoundEvents.WOODEN_TRAPDOOR_CLOSE,
        SoundEvents.WOODEN_TRAPDOOR_OPEN,
        SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF,
        SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON,
        SoundEvents.WOODEN_BUTTON_CLICK_OFF,
        SoundEvents.WOODEN_BUTTON_CLICK_ON
    ));
    /** The soft plop of sinking into the ball pit. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BALL_PIT_PLOP = sound("ball_pit_plop");
    /** The light the secret door's glowing book gives, and the fairy lights full on, in light levels. */
    private static final int HINT_GLOW = 3;
    private static final int FAIRY_GLOW = 12;
    /** The light a golden suitcase gives, in light levels: enough to catch the eye in the hold's dark corners. */
    private static final int GOLDEN_GLOW = 4;
    /**
     * The kids' den in the tail: the secret bookshelf door into it, its glowing book faintly lighting the shelf, the
     * ball pit, and fairy lights, a string of coloured bulbs as bright as the dimmer has them.
     */
    public static final DeferredBlock<SecretBookshelfBlock> SECRET_BOOKSHELF = BLOCKS.registerBlock(
        "secret_bookshelf", SecretBookshelfBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.5F).noOcclusion().pushReaction(PushReaction.DESTROY)
            .isValidSpawn(AerolargeRegistry::never).lightLevel(state -> state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? HINT_GLOW : 0)
    );
    public static final DeferredBlock<BallPitBlock> BALL_PIT = BLOCKS.registerBlock(
        "ball_pit", BallPitBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.5F).sound(SoundType.WOOL).dynamicShape().noOcclusion()
            .isValidSpawn(AerolargeRegistry::never).isRedstoneConductor((state, level, pos) -> false)
            .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
    );
    public static final DeferredBlock<LightStripBlock> FAIRY_LIGHTS = BLOCKS.registerBlock(
        "fairy_lights", LightStripBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.3F).sound(SoundType.GLASS).noCollission().noOcclusion()
            .lightLevel(state -> state.getValue(Brightness.PROPERTY).light(FAIRY_GLOW))
    );
    public static final DeferredItem<DoubleHighBlockItem> SECRET_BOOKSHELF_ITEM = ITEMS.register(
        "secret_bookshelf", () -> new DoubleHighBlockItem(SECRET_BOOKSHELF.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> BALL_PIT_ITEM = ITEMS.registerSimpleBlockItem(BALL_PIT);
    public static final DeferredItem<BlockItem> FAIRY_LIGHTS_ITEM = ITEMS.registerSimpleBlockItem(FAIRY_LIGHTS);
    /**
     * Luggage in the hold: suitcases in four colours, and the golden suitcases hidden among them, giving a faint glow
     * to be found by in the dark; each a small container.
     */
    public static final DeferredBlock<SuitcaseBlock> SUITCASE = BLOCKS.registerBlock(
        "suitcase", properties -> new SuitcaseBlock(properties, false), luggage(MapColor.COLOR_RED)
    );
    public static final DeferredBlock<SuitcaseBlock> GOLDEN_SUITCASE = BLOCKS.registerBlock(
        "golden_suitcase", properties -> new SuitcaseBlock(properties, true), luggage(MapColor.GOLD).lightLevel(state -> GOLDEN_GLOW)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SuitcaseBlockEntity>> SUITCASE_ENTITY =
        BLOCK_ENTITIES.register("suitcase", () -> BlockEntityType.Builder.of(SuitcaseBlockEntity::new, SUITCASE.get(), GOLDEN_SUITCASE.get()).build(null));
    public static final DeferredItem<BlockItem> SUITCASE_ITEM = ITEMS.registerSimpleBlockItem(SUITCASE);
    public static final DeferredItem<BlockItem> GOLDEN_SUITCASE_ITEM = ITEMS.registerSimpleBlockItem(GOLDEN_SUITCASE);

    /** Cabin service: the galley, the crew's trolley, and the meals, pretzels and coffee it serves. */
    public static final DeferredBlock<GalleyBlock> GALLEY = BLOCKS.registerBlock("galley", GalleyBlock::new, metal(MapColor.METAL));
    public static final DeferredItem<BlockItem> GALLEY_ITEM = ITEMS.registerSimpleBlockItem(GALLEY);
    public static final DeferredHolder<EntityType<?>, EntityType<Trolley>> TROLLEY = ENTITY_TYPES.register(
        "trolley", () -> EntityType.Builder.<Trolley>of(Trolley::new, MobCategory.MISC).sized(0.8F, 1.1F)
            .clientTrackingRange(8).updateInterval(3).build("trolley")
    );
    public static final DeferredItem<TrolleyItem> TROLLEY_ITEM = ITEMS.register("trolley", () -> new TrolleyItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<Item> CHICKEN_MEAL = ITEMS.registerSimpleItem(
        "chicken_meal", new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build())
    );
    public static final DeferredItem<Item> PASTA_MEAL = ITEMS.registerSimpleItem(
        "pasta_meal", new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.7F).build())
    );
    public static final DeferredItem<Item> PRETZELS = ITEMS.registerSimpleItem(
        "pretzels", new Item.Properties().food(new FoodProperties.Builder().nutrition(3).saturationModifier(0.4F).fast().build())
    );
    /** A minute of Speed in a paper cup. */
    public static final DeferredItem<CoffeeItem> AIRLINE_COFFEE = ITEMS.register("airline_coffee", () -> new CoffeeItem(new Item.Properties().food(
        new FoodProperties.Builder().nutrition(1).saturationModifier(0.2F).alwaysEdible()
            .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60 * 20, 0), 1.0F).build()
    )));

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
    /** The moon pool's high dive: the springboard off its tower's platform, and a trampoline wherever else it is put. */
    public static final DeferredBlock<SpringboardBlock> SPRINGBOARD = BLOCKS.registerBlock(
        "springboard", SpringboardBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.8F).sound(SoundType.WOOL).noOcclusion()
    );
    public static final DeferredItem<BlockItem> SPRINGBOARD_ITEM = ITEMS.registerSimpleBlockItem(SPRINGBOARD);
    /** Gymnastic rings, hung on chains from the gym hall's roof truss. */
    public static final DeferredBlock<GymRingsBlock> GYM_RINGS = BLOCKS.registerBlock(
        "gym_rings", GymRingsBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5F).sound(SoundType.WOOD).noCollission().noOcclusion()
    );
    public static final DeferredItem<BlockItem> GYM_RINGS_ITEM = ITEMS.registerSimpleBlockItem(GYM_RINGS);
    /** The button by each hoop that starts hide and seek's countdown on the scoreboards; it glows while pressed. */
    public static final DeferredBlock<HideAndSeekButtonBlock> HIDE_AND_SEEK_BUTTON = BLOCKS.registerBlock(
        "hide_and_seek_button", HideAndSeekButtonBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.8F).sound(SoundType.METAL).noOcclusion()
            .lightLevel(state -> state.getValue(HideAndSeekButtonBlock.PRESSED) ? PRESSED_GLOW : 0)
    );
    public static final DeferredItem<BlockItem> HIDE_AND_SEEK_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(HIDE_AND_SEEK_BUTTON);

    /** A paper plane to throw and watch glide, down the cabin or out of a door in flight, and its whoosh off the hand. */
    public static final DeferredHolder<EntityType<?>, EntityType<PaperPlane>> PAPER_PLANE = ENTITY_TYPES.register(
        "paper_plane", () -> EntityType.Builder.<PaperPlane>of(PaperPlane::new, MobCategory.MISC).sized(0.4F, 0.15F)
            .clientTrackingRange(8).updateInterval(2).build("paper_plane")
    );
    public static final DeferredItem<PaperPlaneItem> PAPER_PLANE_ITEM = ITEMS.register("paper_plane", () -> new PaperPlaneItem(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<SoundEvent, SoundEvent> PAPER_PLANE_WHOOSH = sound("paper_plane_whoosh");
    /** Souvenirs: a pilot's cap to wear and a model of the airliner on its stand. */
    public static final DeferredItem<PilotCapItem> PILOT_CAP = ITEMS.register("pilot_cap", () -> new PilotCapItem(new Item.Properties().stacksTo(1)));
    public static final DeferredBlock<ModelAirlinerBlock> MODEL_AIRLINER = BLOCKS.registerBlock(
        "model_airliner", ModelAirlinerBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredItem<BlockItem> MODEL_AIRLINER_ITEM = ITEMS.registerSimpleBlockItem(MODEL_AIRLINER);
    /**
     * The duty-free counter, where the emeralds passengers pay as fares buy toys, snacks and souvenirs, its showcase
     * softly lit; the ding of its shop bell as it opens, and its till ringing up a sale.
     */
    public static final DeferredBlock<DutyFreeCounterBlock> DUTY_FREE_COUNTER = BLOCKS.registerBlock(
        "duty_free_counter", DutyFreeCounterBlock::new, metal(MapColor.COLOR_BLUE).noOcclusion().lightLevel(state -> 5)
    );
    public static final DeferredItem<BlockItem> DUTY_FREE_COUNTER_ITEM = ITEMS.registerSimpleBlockItem(DUTY_FREE_COUNTER);
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOP_BELL = sound("shop_bell");
    public static final DeferredHolder<SoundEvent, SoundEvent> CASH_REGISTER = sound("cash_register");
    /** The pilot's flight helmet, its visor down over the eyes, and the beep of the head-up display it shows coming on. */
    public static final DeferredItem<PilotHelmetItem> PILOT_HELMET = ITEMS.register("pilot_helmet", () -> new PilotHelmetItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<SoundEvent, SoundEvent> HUD_ON = sound("hud_on");

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

    /** A section of the creative tab, under a banner of its own: its name ("itemGroup.aerolarge.<name>") and items. */
    public record TabSection(String name, List<? extends Supplier<? extends Item>> items) {
    }

    /** The creative tab's sections, in order (see {@link dev.aerolarge.client.TabBanners}). */
    public static final List<TabSection> TAB_SECTIONS = List.of(
        new TabSection("blueprints", List.of(AIRLINER_BLUEPRINT, FURNISHED_AIRLINER_BLUEPRINT)),
        new TabSection("airframe", List.of(
            AIRFRAME_PANEL_ITEM, AIRFRAME_FAIRING_ITEM, LIVERY_PANEL_ITEM, CABIN_WINDOW_ITEM, COCKPIT_WINDOW_ITEM, CABIN_FLOOR_ITEM, CABIN_LIGHT_ITEM,
            AIRLINER_DOOR_ITEM, ENGINE_INTAKE_ITEM, LANDING_GEAR_ITEM, LANDING_GEAR_TIRE_ITEM, NAV_LIGHT_ITEM, AIRSTAIR_CONTROLLER_ITEM,
            AIRSTAIR_CALL_BUTTON_ITEM, FLOOR_HATCH_ITEM
        )),
        new TabSection("flight_deck", List.of(
            PILOT_HELMET, PILOT_SEAT_ITEM,
            THRUST_LEVER_ITEM, INSTRUMENT_PANEL_ITEM, AUTOPILOT_ITEM, TILLER_ITEM, PARKING_BRAKE_ITEM, FLIGHT_DISPLAY_ITEM,
            TAIL_CAMERA_DISPLAY_ITEM, HANDSET_ITEM, SEATBELT_SWITCH_ITEM, CABIN_LIGHTS_ITEM, WINDOW_SHADES_ITEM, JUMP_SWITCH_ITEM,
            DROP_RELEASE_ITEM
        )),
        new TabSection("cabin", List.of(
            SEATBELT_SIGN_ITEM, LIGHT_STRIP_ITEM, PENDANT_LIGHT_ITEM, LIGHT_CABLE_ITEM, STARLIGHT_PANEL_ITEM, OVERHEAD_BIN_ITEM, PASSENGER_SCREEN_ITEM,
            GALLEY_ITEM, TROLLEY_ITEM, CHICKEN_MEAL, PASTA_MEAL, PRETZELS, AIRLINE_COFFEE, DUTY_FREE_COUNTER_ITEM, PAPER_PLANE_ITEM, PILOT_CAP,
            MODEL_AIRLINER_ITEM, CABIN_CURTAIN_ITEM, CABIN_PARTITION_ITEM, EXIT_SIGN_ITEM, VACUUM_TOILET_ITEM,
            SECRET_BOOKSHELF_ITEM, BALL_PIT_ITEM, FAIRY_LIGHTS_ITEM, SUITCASE_ITEM, GOLDEN_SUITCASE_ITEM
        )),
        new TabSection("gym", List.of(
            BASKETBALL_ITEM, HOOP_ITEM, SCOREBOARD_ITEM, BALL_RACK_ITEM, HIDE_AND_SEEK_BUTTON_ITEM, GYM_RINGS_ITEM, SPRINGBOARD_ITEM
        )),
        new TabSection("drops", List.of(
            JUMP_LIGHT_ITEM, PARACHUTE_RACK_ITEM, PARACHUTE, LANDING_TARGET_ITEM, SIGNAL_FLARE_ITEM, CARGO_CRATE_ITEM, DROP_BAY_ITEM
        )),
        new TabSection("airport", List.of(JET_BRIDGE_ITEM, CHECK_IN_DESK_ITEM, BOARDING_PASS, TOWER_RADIO_ITEM))
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("aerolarge", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.aerolarge"))
        .icon(() -> AIRLINER_BLUEPRINT.get().getDefaultInstance())
        .displayItems((parameters, output) -> TAB_SECTIONS.forEach(section -> section.items().forEach(item -> output.accept(item.get()))))
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

    /** A suitcase's hard shell: quick to pick up, small enough to see past, and nothing spawns on it. */
    private static BlockBehaviour.Properties luggage(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(0.8F).sound(SoundType.WOOL).noOcclusion()
            .pushReaction(PushReaction.DESTROY).isValidSpawn(AerolargeRegistry::never);
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
        ATTACHMENT_TYPES.register(modEventBus);
    }
}
