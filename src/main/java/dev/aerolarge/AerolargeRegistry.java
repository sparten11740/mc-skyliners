package dev.aerolarge;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.aerolarge.airframe.AirframeLayout;
import dev.aerolarge.airframe.RocketLayout;
import dev.aerolarge.airframe.ZeppelinLayout;
import dev.aerolarge.airframe.ZeppelinOptions;
import dev.aerolarge.airframe.ZeppelinSize;
import dev.aerolarge.block.AirstairBlockEntity;
import dev.aerolarge.block.AirstairButtonBlock;
import dev.aerolarge.block.AirstairCallButtonBlock;
import dev.aerolarge.block.AirstairControllerBlock;
import dev.aerolarge.block.AirstairStepBlock;
import dev.aerolarge.block.AisleCarpetBlock;
import dev.aerolarge.block.AnnouncementPanelBlock;
import dev.aerolarge.block.AnnouncementPanelBlockEntity;
import dev.aerolarge.block.AtisScreenBlock;
import dev.aerolarge.block.AtisScreenBlockEntity;
import dev.aerolarge.block.AutopilotBlock;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.block.AutopilotScreenBlock;
import dev.aerolarge.block.AutopilotScreenBlockEntity;
import dev.aerolarge.block.AwningBlock;
import dev.aerolarge.block.BallMatBlock;
import dev.aerolarge.block.BallPitBlock;
import dev.aerolarge.block.BallRackBlock;
import dev.aerolarge.block.BallastValveBlock;
import dev.aerolarge.block.BallastValveBlockEntity;
import dev.aerolarge.block.BannerCleatBlock;
import dev.aerolarge.block.BannerCleatBlockEntity;
import dev.aerolarge.block.BarCounterBlock;
import dev.aerolarge.block.BarStoolBlock;
import dev.aerolarge.block.BeanBagBlock;
import dev.aerolarge.block.BoardingStairsBlockEntity;
import dev.aerolarge.block.BoardingStairsButtonBlock;
import dev.aerolarge.block.BoostButtonBlock;
import dev.aerolarge.block.BoostButtonBlockEntity;
import dev.aerolarge.block.Brightness;
import dev.aerolarge.block.BubbleLevelBlock;
import dev.aerolarge.block.BubbleLevelBlockEntity;
import dev.aerolarge.block.BulkheadSignBlock;
import dev.aerolarge.block.BunkBlock;
import dev.aerolarge.block.BurnerLeverBlock;
import dev.aerolarge.block.CabinArchBlock;
import dev.aerolarge.block.CabinCarpetBlock;
import dev.aerolarge.block.CabinControlBlockEntity;
import dev.aerolarge.block.CabinLightBlock;
import dev.aerolarge.block.CabinLightsBlock;
import dev.aerolarge.block.CabinRailBlock;
import dev.aerolarge.block.CabinSeatBlock;
import dev.aerolarge.block.CabinStairsBlock;
import dev.aerolarge.block.CabinWindowBlock;
import dev.aerolarge.block.CabinWindowShellBlock;
import dev.aerolarge.block.CargoCrateBlock;
import dev.aerolarge.block.CargoCrateBlockEntity;
import dev.aerolarge.block.CargoFloorBlock;
import dev.aerolarge.block.CargoNetBlock;
import dev.aerolarge.block.CarrierLeverBlock;
import dev.aerolarge.block.CheckInDeskBlock;
import dev.aerolarge.block.ClimbingRopeBlock;
import dev.aerolarge.block.CoffeeMachineBlock;
import dev.aerolarge.block.CoffeeMachineBlockEntity;
import dev.aerolarge.block.CraneWinchBlock;
import dev.aerolarge.block.CraneWinchBlockEntity;
import dev.aerolarge.block.CockpitDoorBlock;
import dev.aerolarge.block.CockpitDoorBlockEntity;
import dev.aerolarge.block.CockpitGlassBlock;
import dev.aerolarge.block.CrewLockerBlock;
import dev.aerolarge.block.CrewLockerBlockEntity;
import dev.aerolarge.block.CurtainBlock;
import dev.aerolarge.block.DeckClampBlock;
import dev.aerolarge.block.DeckClampBlockEntity;
import dev.aerolarge.block.DiscoBallBlock;
import dev.aerolarge.block.DiscoBallBlockEntity;
import dev.aerolarge.block.DisplayShelfBlock;
import dev.aerolarge.block.DoorButtonBlock;
import dev.aerolarge.block.DropBayBlock;
import dev.aerolarge.block.DropBayBlockEntity;
import dev.aerolarge.block.DropBayMenu;
import dev.aerolarge.block.DropReleaseBlock;
import dev.aerolarge.block.DropReleaseBlockEntity;
import dev.aerolarge.block.DutyFreeCounterBlock;
import dev.aerolarge.block.EngineCoreBlock;
import dev.aerolarge.block.EngineCoreBlockEntity;
import dev.aerolarge.block.ExitSignBlock;
import dev.aerolarge.block.FairingBlock;
import dev.aerolarge.block.FlagHalyardBlock;
import dev.aerolarge.block.FlagHalyardBlockEntity;
import dev.aerolarge.block.FlagstaffBlock;
import dev.aerolarge.block.FloodlightBlock;
import dev.aerolarge.block.FrankingMachineBlock;
import dev.aerolarge.block.FloodlightBlockEntity;
import dev.aerolarge.block.FlightDisplayBlock;
import dev.aerolarge.block.FlightDisplayBlockEntity;
import dev.aerolarge.block.FlightMapBlock;
import dev.aerolarge.block.FlightMapBlockEntity;
import dev.aerolarge.block.FlightStripBoardBlock;
import dev.aerolarge.block.FlightStripBoardBlockEntity;
import dev.aerolarge.block.FloorHatchBlock;
import dev.aerolarge.block.GalleyBlock;
import dev.aerolarge.block.GalleyOvenBlock;
import dev.aerolarge.block.GalleyOvenBlockEntity;
import dev.aerolarge.block.GalleyUnitBlock;
import dev.aerolarge.block.GalleyUnitBlockEntity;
import dev.aerolarge.block.GearBayBlock;
import dev.aerolarge.block.GearBayBlockEntity;
import dev.aerolarge.block.GearLeverBlock;
import dev.aerolarge.block.GearLeverBlockEntity;
import dev.aerolarge.block.LaserSwitchBlock;
import dev.aerolarge.block.LaserSwitchBlockEntity;
import dev.aerolarge.block.LaserTurretBlock;
import dev.aerolarge.block.LaserTurretBlockEntity;
import dev.aerolarge.block.GiantRubberDuckBlock;
import dev.aerolarge.block.GiantRubberDuckBlockEntity;
import dev.aerolarge.block.GlassBackboardBlock;
import dev.aerolarge.block.GymBenchBlock;
import dev.aerolarge.block.GymFloorBlock;
import dev.aerolarge.block.GymMatBlock;
import dev.aerolarge.block.GymRingsBlock;
import dev.aerolarge.block.HaloLightBlock;
import dev.aerolarge.block.HandsetBlock;
import dev.aerolarge.block.HideAndSeekButtonBlock;
import dev.aerolarge.block.HoopBlock;
import dev.aerolarge.block.HullPanelBlock;
import dev.aerolarge.block.HullRelayBlock;
import dev.aerolarge.block.HabitatFloorBlock;
import dev.aerolarge.block.HabitatGlassBlock;
import dev.aerolarge.block.HabitatHatchBlock;
import dev.aerolarge.block.HabitatHatchBlockEntity;
import dev.aerolarge.block.LabInstrumentBlock;
import dev.aerolarge.block.BioreactorBlock;
import dev.aerolarge.block.LabGrowerBlock;
import dev.aerolarge.block.LabPartitionBlock;
import dev.aerolarge.block.LabPipeBlock;
import dev.aerolarge.block.SampleCentrifugeBlock;
import dev.aerolarge.block.InstrumentPanelBlock;
import dev.aerolarge.block.JetBridgeBlock;
import dev.aerolarge.block.JetBridgeBlockEntity;
import dev.aerolarge.block.JumpLightBlock;
import dev.aerolarge.block.JumpSwitchBlock;
import dev.aerolarge.block.LandingTargetBlock;
import dev.aerolarge.block.LandingTargetBlockEntity;
import dev.aerolarge.block.LiveryBoardBlock;
import dev.aerolarge.block.LiveryBoardBlockEntity;
import dev.aerolarge.block.LiverySelectorBlock;
import dev.aerolarge.block.LiverySelectorBlockEntity;
import dev.aerolarge.block.LookoutGlassBlock;
import dev.aerolarge.block.MailSackBlock;
import dev.aerolarge.block.LookoutGlassBlockEntity;
import dev.aerolarge.block.LavIceBlock;
import dev.aerolarge.block.LavatoryBasinBlock;
import dev.aerolarge.block.LiftBoardBlock;
import dev.aerolarge.block.LiftBoardBlockEntity;
import dev.aerolarge.block.LiftDoorBlock;
import dev.aerolarge.block.LiftDoorBlockEntity;
import dev.aerolarge.block.LiftMachineBlock;
import dev.aerolarge.block.LiftMachineBlockEntity;
import dev.aerolarge.block.LedConsoleBlock;
import dev.aerolarge.block.LedConsoleBlockEntity;
import dev.aerolarge.block.LedPanelBlock;
import dev.aerolarge.block.LedPanelBlockEntity;
import dev.aerolarge.block.LedStripBlock;
import dev.aerolarge.block.LiftPanelBlock;
import dev.aerolarge.block.LiftPanelBlockEntity;
import dev.aerolarge.block.LightCableBlock;
import dev.aerolarge.block.LightStripBlock;
import dev.aerolarge.block.LinedShellBlock;
import dev.aerolarge.block.LinkedControlBlockEntity;
import dev.aerolarge.block.MotionSensorBlock;
import dev.aerolarge.block.NavLightBlock;
import dev.aerolarge.block.NavLightBlockEntity;
import dev.aerolarge.block.OvalWindowBlock;
import dev.aerolarge.block.OverheadBinBlock;
import dev.aerolarge.block.OverheadBinBlockEntity;
import dev.aerolarge.block.OverheadWcSignBlock;
import dev.aerolarge.block.OverheadWcSignBlockEntity;
import dev.aerolarge.block.PaperPlaneBoxBlock;
import dev.aerolarge.block.ParachuteRackBlock;
import dev.aerolarge.block.ParkingBrakeBlock;
import dev.aerolarge.block.PassengerScreenBlock;
import dev.aerolarge.block.PassengerScreenBlockEntity;
import dev.aerolarge.block.PendantLightBlock;
import dev.aerolarge.block.PilotSeatBlock;
import dev.aerolarge.block.PlanterBlock;
import dev.aerolarge.block.PlugDoorBlock;
import dev.aerolarge.block.PlugDoorBlockEntity;
import dev.aerolarge.block.PoolCopingBlock;
import dev.aerolarge.block.PoolMosaicBlock;
import dev.aerolarge.block.PoolStepsBlock;
import dev.aerolarge.block.PropellerBlock;
import dev.aerolarge.block.PropellerBlockEntity;
import dev.aerolarge.block.RadioBlockEntity;
import dev.aerolarge.block.RocketFinish;
import dev.aerolarge.block.RocketFloorBlock;
import dev.aerolarge.block.RocketLinedCoverBlock;
import dev.aerolarge.block.RocketLiningPanelBlock;
import dev.aerolarge.block.RubberDuckBlock;
import dev.aerolarge.block.RubberDuckBlockEntity;
import dev.aerolarge.block.RoverBayBlockEntity;
import dev.aerolarge.block.RoverHatchBlock;
import dev.aerolarge.block.RoverPanelBlock;
import dev.aerolarge.block.RungLadderBlock;
import dev.aerolarge.block.ScoreboardBlock;
import dev.aerolarge.block.ScoreboardBlockEntity;
import dev.aerolarge.block.ScreenStandBlock;
import dev.aerolarge.block.SearchlightBlock;
import dev.aerolarge.block.SearchlightBlockEntity;
import dev.aerolarge.block.SearchlightHandleBlock;
import dev.aerolarge.block.SearchlightHandleBlockEntity;
import dev.aerolarge.block.SeatConsoleBlock;
import dev.aerolarge.block.SeatbeltSignBlock;
import dev.aerolarge.block.SeatbeltSwitchBlock;
import dev.aerolarge.block.SeatbeltSwitchBlockEntity;
import dev.aerolarge.block.SecretBookshelfBlock;
import dev.aerolarge.block.SelfDestructButtonBlock;
import dev.aerolarge.block.SelfDestructButtonBlockEntity;
import dev.aerolarge.block.ShellBlock;
import dev.aerolarge.block.SignalFlareBlock;
import dev.aerolarge.block.SignalFlareBlockEntity;
import dev.aerolarge.block.SlideControlBlock;
import dev.aerolarge.block.SlideControlBlockEntity;
import dev.aerolarge.block.SlideStepBlock;
import dev.aerolarge.block.SpacePoster;
import dev.aerolarge.block.SpacePosterBlock;
import dev.aerolarge.block.SpringboardBlock;
import dev.aerolarge.block.SuitcaseBlock;
import dev.aerolarge.block.SuitcaseBlockEntity;
import dev.aerolarge.block.SurfacePanelBlock;
import dev.aerolarge.block.TailCameraDisplayBlock;
import dev.aerolarge.block.TailCameraDisplayBlockEntity;
import dev.aerolarge.block.ThrustLeverBlock;
import dev.aerolarge.block.ThrustLeverBlockEntity;
import dev.aerolarge.block.ThrustRelayBlock;
import dev.aerolarge.block.TillerBlock;
import dev.aerolarge.block.TowerRadarBlock;
import dev.aerolarge.block.TowerRadarBlockEntity;
import dev.aerolarge.block.TowerRadioBlock;
import dev.aerolarge.block.TransponderBlock;
import dev.aerolarge.block.TransponderBlockEntity;
import dev.aerolarge.block.UldContainerBlock;
import dev.aerolarge.block.VacuumToiletBlock;
import dev.aerolarge.block.WallLightBlock;
import dev.aerolarge.block.WcSignBlock;
import dev.aerolarge.block.WcSignBlockEntity;
import dev.aerolarge.block.WindSockBlock;
import dev.aerolarge.block.WindSockBlockEntity;
import dev.aerolarge.block.WindowShadesBlock;
import dev.aerolarge.block.CockpitDisplayBlock;
import dev.aerolarge.block.CockpitDisplayBlockEntity;
import dev.aerolarge.block.EngineRepeaterBlock;
import dev.aerolarge.block.EngineRepeaterBlockEntity;
import dev.aerolarge.block.HelmBlock;
import dev.aerolarge.block.HelmBlockEntity;
import dev.aerolarge.block.HornCordBlock;
import dev.aerolarge.block.PictureBoardBlock;
import dev.aerolarge.block.PictureBoardBlockEntity;
import dev.aerolarge.block.SecretHatchBlock;
import dev.aerolarge.block.ShipsPlaqueBlock;
import dev.aerolarge.block.ShipsPlaqueBlockEntity;
import dev.aerolarge.block.SkywritingBoardBlock;
import dev.aerolarge.block.SkywritingBoardBlockEntity;
import dev.aerolarge.block.SouvenirModelBlock;
import dev.aerolarge.block.SpeakerBlock;
import dev.aerolarge.block.TechnoDeckBlock;
import dev.aerolarge.block.TechnoDeckBlockEntity;
import dev.aerolarge.block.TelegraphBlock;
import dev.aerolarge.block.TrimWinchBlock;
import dev.aerolarge.block.TrimWinchBlockEntity;
import dev.aerolarge.cabin.Passengers;
import dev.aerolarge.christening.ChristeningBottleItem;
import dev.aerolarge.entity.Basketball;
import dev.aerolarge.entity.BlueIceChunk;
import dev.aerolarge.entity.CraneHook;
import dev.aerolarge.entity.FallingCrate;
import dev.aerolarge.entity.LiftCar;
import dev.aerolarge.entity.PaperPlane;
import dev.aerolarge.entity.Rover;
import dev.aerolarge.entity.SlideRide;
import dev.aerolarge.entity.Tardigrade;
import dev.aerolarge.entity.Trolley;
import dev.aerolarge.item.AirframeBlueprintItem;
import dev.aerolarge.item.BasketballItem;
import dev.aerolarge.item.BoardingPassItem;
import dev.aerolarge.item.CoffeeItem;
import dev.aerolarge.item.ControlTowerBlueprintItem;
import dev.aerolarge.item.RunwayBlueprintItem;
import dev.aerolarge.item.LaserPointerItem;
import dev.aerolarge.item.MailSackItem;
import dev.aerolarge.item.PaperPlaneItem;
import dev.aerolarge.item.ParachuteItem;
import dev.aerolarge.item.PilotCapItem;
import dev.aerolarge.item.PilotHelmetItem;
import dev.aerolarge.item.RoverRemoteItem;
import dev.aerolarge.item.SpaceHelmetItem;
import dev.aerolarge.item.SpacePosterItem;
import dev.aerolarge.item.TrolleyItem;
import dev.aerolarge.compat.SableFlight;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageType;
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
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
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
import net.minecraft.world.level.block.state.properties.BedPart;
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
    /**
     * How hard to mine the blocks only the airliner itself places and takes away (the airstairs' and slides' steps
     * and the control surfaces' plates, none of which can be aimed at either): as hard as a block can be, so never
     * mined, but not unbreakable (-1), which Create Aeronautics' Physics Assembler leaves behind when it assembles
     * the airliner, in its way as it takes off.
     */
    private static final float ONLY_EVER_PLACED = Float.MAX_VALUE;
    /** How far up their blocks the souvenir models' bodies' middles are, in sixteenths (see AirframeBlueprintItem). */
    private static final float AIRLINER_MODEL_MIDDLE = 7;
    private static final float ZEPPELIN_MODEL_MIDDLE = 8;
    private static final float ROCKET_MODEL_MIDDLE = 8;

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Aerolarge.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Aerolarge.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Aerolarge.MOD_ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Aerolarge.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Aerolarge.MOD_ID);
    private static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(Registries.PARTICLE_TYPE, Aerolarge.MOD_ID);
    private static final DeferredRegister.DataComponents DATA_COMPONENTS = DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Aerolarge.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, Aerolarge.MOD_ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Aerolarge.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, Aerolarge.MOD_ID);
    /** How brightly a starlight panel glows full on, in light levels: softly, the lounge's pendants doing the rest. */
    private static final int STARLIGHT_GLOW = 10;
    /** How brightly the hide and seek button glows while pressed, in light levels. */
    private static final int PRESSED_GLOW = 7;
    /** How brightly the self-destruct button glows while armed, in light levels: a red glow round it on the wall. */
    private static final int ARMED_GLOW = 9;

    /** A turbofan running, looped; the engines pitch and swell it with their thrust. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_SOUND = sound("engine");
    /** The flight deck's voice, a calm British woman's: take-off and landing callouts and warnings. */
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
    /**
     * The same voice over the cabin PA, after the chime: crew seated for take-off, for landing, and arrival; and the
     * ones only the announcement panel makes: welcome, bumpy air, snacks, the view, the pool and gym open, and sleep.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_WELCOME = sound("announcement_welcome");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_TAKEOFF = sound("announcement_takeoff");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_TURBULENCE = sound("announcement_turbulence");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_SNACKS = sound("announcement_snacks");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_WINDOW = sound("announcement_window");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_PLAYTIME = sound("announcement_playtime");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_SLEEP = sound("announcement_sleep");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_LANDING = sound("announcement_landing");
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_ARRIVAL = sound("announcement_arrival");
    /** The rocket's own arrival announcement, which says "spacecraft" where the airliner's says "aircraft". */
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_ARRIVAL_ROCKET = sound("announcement_arrival_rocket");
    /** The rocket's welcome to space, after the chime, as she climbs past its edge (see {@code EdgeOfSpace}). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_SPACE = sound("announcement_space");
    /** The rocket's artificial gravity announced, after the chime, once all aboard have been light a while in space. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ANNOUNCEMENT_GRAVITY = sound("announcement_gravity");
    /** Low gravity aboard a rocket in space coming on, and going (see {@code autopilot.SpaceGravity}). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_LIGHT = sound("space_light");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_HEAVY = sound("space_heavy");
    /** The two-tone chime before a cabin announcement, and the crackle of a radio call. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PA_CHIME = sound("pa_chime");
    public static final DeferredHolder<SoundEvent, SoundEvent> RADIO_CRACKLE = sound("radio_crackle");
    /**
     * Air traffic control over the flight deck's radio: clearing the take-off, handing over after it, clearing the
     * landing or a go-around, and welcoming the airliner in; chatter in flight, other aircraft on the frequency among it;
     * and the carrier's crackle and clicks with nobody speaking.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_CLEARED_FOR_TAKEOFF = sound("atc_cleared_for_takeoff");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_CONTACT_DEPARTURE = sound("atc_contact_departure");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_RADAR_CONTACT = sound("atc_radar_contact");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_CLEARED_TO_LAND = sound("atc_cleared_to_land");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_GO_AROUND = sound("atc_go_around");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_TAXI_TO_GATE = sound("atc_taxi_to_gate");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_CONTACT_CENTER = sound("atc_contact_center");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_CENTER_CHECK_IN = sound("atc_center_check_in");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_TRAFFIC_TWO_OCLOCK = sound("atc_traffic_two_oclock");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_TRAFFIC_TEN_OCLOCK = sound("atc_traffic_ten_oclock");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_LIGHT_TURBULENCE = sound("atc_light_turbulence");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_SMOOTH_RIDE = sound("atc_smooth_ride");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_GEESE = sound("atc_geese");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_BALLOON = sound("atc_balloon");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_SKYHOPPER_HELLO = sound("atc_skyhopper_hello");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_CARGO_CLIMB = sound("atc_cargo_climb");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_STATIC_OPEN = sound("atc_static_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> ATC_STATIC_CLICKS = sound("atc_static_clicks");
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
    /** The landing gear going up and coming down, heard under the floor, and the gear horn on the flight deck. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_RETRACT = sound("gear_retract");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_EXTEND = sound("gear_extend");
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_HORN = sound("gear_horn");
    /** The cabin dropping into an air pocket: the rush of air, the rattle of the cabin and the thud as the air catches it. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AIR_POCKET = sound("air_pocket");
    /** The laser path clearer: a bolt's hiss, and the whine of its turret charging as it is armed. */
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_HISS = sound("laser_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> LASER_CHARGE = sound("laser_charge");
    /** What the laser path clearer hurts hostile mobs with (data/aerolarge/damage_type/laser.json). */
    public static final ResourceKey<DamageType> LASER_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, "laser"));
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
    /** A white dove released at a zeppelin's christening, seen from far off. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> DOVE = PARTICLES.register("dove", () -> new SimpleParticleType(true));
    /** A puff of an engine's contrail, laid high up and left hanging in the sky. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> CONTRAIL = PARTICLES.register("contrail", () -> new SimpleParticleType(true));
    /**
     * The self-destruct: the alarm's siren wailing through the cabin while it is armed, and the airliner blowing up,
     * its bang and the long rumble after it.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> SELF_DESTRUCT_SIREN = sound("self_destruct_siren");
    public static final DeferredHolder<SoundEvent, SoundEvent> SELF_DESTRUCT_BLAST = sound("self_destruct_blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> SELF_DESTRUCT_RUMBLE = sound("self_destruct_rumble");
    /**
     * The airliner blowing up (see {@link dev.aerolarge.selfdestruct.Blast}): huge, slow puffs of its fireball, of the
     * smoke rising from it, of the ring of its blast wave, and the burning embers thrown out of it.
     */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_FIRE = PARTICLES.register("blast_fire", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_SMOKE = PARTICLES.register("blast_smoke", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_WAVE = PARTICLES.register("blast_wave", () -> new SimpleParticleType(false));
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> BLAST_EMBER = PARTICLES.register("blast_ember", () -> new SimpleParticleType(false));

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

    /** The plain skin: plated outside, lined on each face the blueprints find looking in (see {@link HullPanelBlock}). */
    public static final DeferredBlock<HullPanelBlock> AIRFRAME_PANEL = BLOCKS.registerBlock("airframe_panel", HullPanelBlock::new, metal(MapColor.SNOW));
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
     * night glow. The washrooms' and the crew room's are kept on at night (see {@link CabinLightBlock}).
     */
    public static final DeferredBlock<CabinLightBlock> CABIN_LIGHT = BLOCKS.registerBlock(
        "cabin_light", CabinLightBlock::new, metal(MapColor.SNOW).lightLevel(state -> CabinLightBlock.light(state, 15)).sound(SoundType.GLASS)
    );
    public static final DeferredBlock<Block> LANDING_GEAR = BLOCKS.registerSimpleBlock("landing_gear", metal(MapColor.METAL));
    /** The top of a retractable gear leg, with its doors and the hydraulics that raise and lower it. */
    public static final DeferredBlock<GearBayBlock> GEAR_BAY = BLOCKS.registerBlock("gear_bay", GearBayBlock::new, metal(MapColor.METAL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GearBayBlockEntity>> GEAR_BAY_ENTITY =
        BLOCK_ENTITIES.register("gear_bay", () -> BlockEntityType.Builder.of(GearBayBlockEntity::new, GEAR_BAY.get()).build(null));
    public static final DeferredBlock<Block> LANDING_GEAR_TIRE = BLOCKS.registerSimpleBlock(
        "landing_gear_tire",
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F, 3.0F).sound(SoundType.WOOL).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredBlock<CabinWindowBlock> CABIN_WINDOW = BLOCKS.registerBlock("cabin_window", CabinWindowBlock::new, glass());
    public static final DeferredBlock<TransparentBlock> COCKPIT_WINDOW = BLOCKS.registerBlock("cockpit_window", TransparentBlock::new, glass());
    /**
     * The doors inside the cabin (the WC's, the crew room's, and the cockpit door where the big one does not fit), and
     * the hull's doors in airliners built before the plug doors.
     */
    public static final DeferredBlock<DoorBlock> AIRLINER_DOOR = BLOCKS.registerBlock(
        "airliner_door",
        properties -> new DoorBlock(AIRLINER, properties),
        metal(MapColor.SNOW).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    /**
     * The doors in the hull's sides: one panel over each doorway, drawn by its renderer as it moves out of the frame
     * and swings away. Placed only by the blueprints, so it has no item. It occludes by its panel, so a shut door keeps
     * the daylight out of the cabin (see {@link PlugDoorBlock}).
     */
    public static final DeferredBlock<PlugDoorBlock> PLUG_DOOR = BLOCKS.registerBlock("plug_door", PlugDoorBlock::new, metal(MapColor.SNOW));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PlugDoorBlockEntity>> PLUG_DOOR_ENTITY =
        BLOCK_ENTITIES.register("plug_door", () -> BlockEntityType.Builder.of(PlugDoorBlockEntity::new, PLUG_DOOR.get()).build(null));
    /**
     * The cockpit door in the bulkhead behind the flight deck: one big panel swinging out into the aisle, drawn by its
     * renderer. Placed only by the blueprints, so it has no item (see {@link CockpitDoorBlock}).
     */
    public static final DeferredBlock<CockpitDoorBlock> COCKPIT_DOOR = BLOCKS.registerBlock(
        "cockpit_door", CockpitDoorBlock::new, metal(MapColor.SNOW).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CockpitDoorBlockEntity>> COCKPIT_DOOR_ENTITY =
        BLOCK_ENTITIES.register("cockpit_door", () -> BlockEntityType.Builder.of(CockpitDoorBlockEntity::new, COCKPIT_DOOR.get()).build(null));
    /** A plug door's pneumatic hiss as it starts to move, and the clunk of its latches. */
    public static final DeferredHolder<SoundEvent, SoundEvent> PLUG_DOOR_HISS = sound("plug_door_hiss");
    public static final DeferredHolder<SoundEvent, SoundEvent> PLUG_DOOR_LATCH = sound("plug_door_latch");
    /** The siren wailing through the cabin while a door stands open in flight (see {@code Decompression}). */
    public static final DeferredHolder<SoundEvent, SoundEvent> DOOR_ALARM = sound("door_alarm");
    /** The airstairs' panel beside the forward door, and the ground crew's button on the nose gear that calls it. */
    public static final DeferredBlock<AirstairControllerBlock> AIRSTAIR_CONTROLLER = BLOCKS.registerBlock(
        "airstair_controller", AirstairControllerBlock::new, metal(MapColor.COLOR_YELLOW)
    );
    public static final DeferredBlock<AirstairCallButtonBlock> AIRSTAIR_CALL_BUTTON = BLOCKS.registerBlock(
        "airstair_call_button",
        properties -> new AirstairCallButtonBlock(AIRLINER, properties),
        BlockBehaviour.Properties.of().noCollission().strength(0.5F).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY)
    );
    /** The lit push-button beside the airstairs' controller, inside the cabin and out, a stone button in all it does. */
    public static final DeferredBlock<AirstairButtonBlock> AIRSTAIR_BUTTON = BLOCKS.registerBlock(
        "airstair_button",
        properties -> new AirstairButtonBlock(AIRLINER, properties),
        BlockBehaviour.Properties.of().noCollission().strength(0.5F).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY)
    );
    /** The lit push-button inside beside every hull door, which opens the door or shuts it but powers nothing. */
    public static final DeferredBlock<DoorButtonBlock> DOOR_BUTTON = BLOCKS.registerBlock(
        "door_button",
        properties -> new DoorButtonBlock(AIRLINER, properties),
        BlockBehaviour.Properties.of().noCollission().strength(0.5F).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AirstairBlockEntity>> AIRSTAIR_CONTROLLER_ENTITY =
        BLOCK_ENTITIES.register("airstair_controller", () -> BlockEntityType.Builder.of(AirstairBlockEntity::new, AIRSTAIR_CONTROLLER.get()).build(null));
    /**
     * What the airstairs stand on once down: invisible steps and the handrails' guards over them, laid and taken up
     * by the controller only, so they cannot be mined, blown up or moved, and drop nothing.
     */
    public static final DeferredBlock<AirstairStepBlock> AIRSTAIR_STEP = BLOCKS.registerBlock(
        "airstair_step", SableFlight::airstairStep,
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(ONLY_EVER_PLACED, 3600000.0F).noLootTable().noOcclusion()
            .sound(SoundType.METAL).isValidSpawn(AerolargeRegistry::never).isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false).pushReaction(PushReaction.BLOCK)
    );
    /** The airstairs' hydraulic motor running, and the clunk of their latches and their hatch's. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRSTAIR_MOTOR = sound("airstair_motor");
    public static final DeferredHolder<SoundEvent, SoundEvent> AIRSTAIR_LATCH = sound("airstair_latch");

    /**
     * The evacuation slides packed in plug doors: each one's control in the wall beside its door, which keeps and works
     * the slide, the invisible steps it lays under the slide once it is out, laid and taken up by the control only, and
     * the ride that carries whoever steps onto it down to the ground. The gas rushing in as it inflates and out as it
     * deflates, and the whoosh of someone setting off down it.
     */
    public static final DeferredBlock<SlideControlBlock> SLIDE_CONTROL = BLOCKS.registerBlock(
        "slide_control", SlideControlBlock::new, metal(MapColor.COLOR_RED)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SlideControlBlockEntity>> SLIDE_CONTROL_ENTITY =
        BLOCK_ENTITIES.register("slide_control", () -> BlockEntityType.Builder.of(SlideControlBlockEntity::new, SLIDE_CONTROL.get()).build(null));
    public static final DeferredItem<BlockItem> SLIDE_CONTROL_ITEM = ITEMS.registerSimpleBlockItem(SLIDE_CONTROL);
    public static final DeferredBlock<SlideStepBlock> SLIDE_STEP = BLOCKS.registerBlock(
        "slide_step", SableFlight::slideStep,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(ONLY_EVER_PLACED, 3600000.0F).noLootTable().noOcclusion()
            .sound(SoundType.WOOL).isValidSpawn(AerolargeRegistry::never).isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false).pushReaction(PushReaction.BLOCK)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<SlideRide>> SLIDE_RIDE = ENTITY_TYPES.register(
        "slide_ride", () -> EntityType.Builder.<SlideRide>of(SlideRide::new, MobCategory.MISC).sized(0.5F, 0.25F)
            .clientTrackingRange(10).updateInterval(20).noSummon().fireImmune().build("slide_ride")
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE_INFLATE = sound("slide_inflate");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE_DEFLATE = sound("slide_deflate");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLIDE_WHOOSH = sound("slide_whoosh");

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
    /** The rocket's big oval windows, each a few blocks of it, their glass dimming in steps. */
    public static final DeferredBlock<OvalWindowBlock> OVAL_WINDOW = BLOCKS.registerBlock("oval_window", OvalWindowBlock::new, glass());
    /**
     * The rocket's lift: its machine at the top of the glass shaft, its landing doors, the picture boards by the doors
     * and on the car's back wall, the car that rides the shaft, and its sounds (see {@link LiftMachineBlockEntity}).
     */
    public static final DeferredBlock<LiftMachineBlock> LIFT_MACHINE = BLOCKS.registerBlock("lift_machine", LiftMachineBlock::new, metal(MapColor.METAL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LiftMachineBlockEntity>> LIFT_MACHINE_ENTITY =
        BLOCK_ENTITIES.register("lift_machine", () -> BlockEntityType.Builder.of(LiftMachineBlockEntity::new, LIFT_MACHINE.get()).build(null));
    public static final DeferredBlock<LiftDoorBlock> LIFT_DOOR = BLOCKS.registerBlock("lift_door", LiftDoorBlock::new, glass());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LiftDoorBlockEntity>> LIFT_DOOR_ENTITY =
        BLOCK_ENTITIES.register("lift_door", () -> BlockEntityType.Builder.of(LiftDoorBlockEntity::new, LIFT_DOOR.get()).build(null));
    public static final DeferredBlock<LiftBoardBlock> LIFT_BOARD = BLOCKS.registerBlock("lift_board", LiftBoardBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LiftBoardBlockEntity>> LIFT_BOARD_ENTITY =
        BLOCK_ENTITIES.register("lift_board", () -> BlockEntityType.Builder.of(LiftBoardBlockEntity::new, LIFT_BOARD.get()).build(null));
    public static final DeferredHolder<EntityType<?>, EntityType<LiftCar>> LIFT_CAR = ENTITY_TYPES.register(
        "lift_car", () -> EntityType.Builder.<LiftCar>of(LiftCar::new, MobCategory.MISC).sized(2.9F, 0.2F)
            .clientTrackingRange(10).updateInterval(20).noSummon().fireImmune().build("lift_car")
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_DOOR_SOUND = sound("lift_door");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_START = sound("lift_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_HUM = sound("lift_hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_CHIME_UP = sound("lift_chime_up");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_CHIME_DOWN = sound("lift_chime_down");
    public static final DeferredHolder<SoundEvent, SoundEvent> LIFT_MUSIC = sound("lift_music");

    // The rocket's sound and voice (see dev.aerolarge.autopilot.RocketSounds and the client's RocketRoar).
    /** Her engines' roar, looped while they fire, swelled and pitched with their thrust; and as it is heard aboard. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_ROAR = sound("rocket_roar");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_ROAR_ABOARD = sound("rocket_roar_aboard");
    /** Her engines lighting at lift-off, and cutting off; and each as it is heard aboard. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_IGNITION = sound("rocket_ignition");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_IGNITION_ABOARD = sound("rocket_ignition_aboard");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_CUTOFF = sound("rocket_cutoff");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_CUTOFF_ABOARD = sound("rocket_cutoff_aboard");
    /** One of her side jets firing from rest. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_JET_PUFF = sound("rocket_jet_puff");
    /** Her fins taking her weight as she lands. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ROCKET_TOUCHDOWN = sound("rocket_touchdown");
    /** Launch control counting her down over the radio: "one" to "ten", each at its number less one, and "lift-off". */
    public static final List<DeferredHolder<SoundEvent, SoundEvent>> LAUNCH_COUNT = IntStream.rangeClosed(1, 10).mapToObj(n -> sound("launch_" + n)).toList();
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_LIFTOFF = sound("launch_liftoff");
    /** Launch control calling her flight's milestones over the radio (see dev.aerolarge.autopilot.LaunchCallouts). */
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_TOWER_CLEARED = sound("launch_tower_cleared");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_MAX_Q = sound("launch_max_q");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_MAIN_ENGINE_CUTOFF = sound("launch_main_engine_cutoff");
    public static final DeferredHolder<SoundEvent, SoundEvent> LAUNCH_LANDING_BURN = sound("launch_landing_burn");
    /** The flight deck's voice as she settles on her fins. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CALLOUT_TOUCHDOWN = sound("callout_touchdown");
    public static final DeferredBlock<CockpitGlassBlock> COCKPIT_GLASS = BLOCKS.registerBlock(
        "cockpit_glass", properties -> new CockpitGlassBlock(properties, AerolargeRegistry.COCKPIT_WINDOW), glass()
    );
    /** Clear glass cut to the hull's curve, for the moon pool's view down through the belly; it gives back glass. */
    public static final DeferredBlock<CockpitGlassBlock> HULL_GLASS = BLOCKS.registerBlock(
        "hull_glass", properties -> new CockpitGlassBlock(properties, () -> Blocks.GLASS), glass()
    );
    /**
     * Smoked glass cut to the curve, nearly black, for the rocket's cockpit round her nose, drawn as any glass is, every
     * face of it, so it joins without a gap all round her stepped curve; it gives back glass.
     */
    public static final DeferredBlock<CockpitGlassBlock> SMOKED_GLASS = BLOCKS.registerBlock(
        "smoked_glass", properties -> new CockpitGlassBlock(properties, () -> Blocks.GLASS), glass()
    );

    /**
     * The hull's lining (see {@link dev.aerolarge.block.Lining}): the skin cut to the hull's curve where the airliner's
     * inside sees it, lined on that side. Placed only by the blueprints, so no item; it occludes like the plain skin
     * and gives back an airframe panel.
     */
    public static final DeferredBlock<LinedShellBlock> AIRFRAME_LINED_SHELL = BLOCKS.registerBlock(
        "airframe_lined_shell", properties -> new LinedShellBlock(properties, AerolargeRegistry.AIRFRAME_PANEL), metal(MapColor.SNOW)
    );
    /** The flight deck: the thrust levers on the console, relays they drive in the nacelles, the instrument panel. */
    public static final DeferredBlock<ThrustLeverBlock> THRUST_LEVER = BLOCKS.registerBlock(
        "thrust_lever", ThrustLeverBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ThrustLeverBlockEntity>> THRUST_LEVER_ENTITY =
        BLOCK_ENTITIES.register("thrust_lever", () -> BlockEntityType.Builder.of(ThrustLeverBlockEntity::new, THRUST_LEVER.get()).build(null));
    /** The nose wheel tiller and the parking brake, on the centre console, sharing one kind of linked block entity. */
    public static final DeferredBlock<TillerBlock> TILLER = BLOCKS.registerBlock("tiller", TillerBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion());
    /** The switch on the centre console that turns the jump lights by the doors red, then green. */
    public static final DeferredBlock<JumpSwitchBlock> JUMP_SWITCH = BLOCKS.registerBlock(
        "jump_switch", JumpSwitchBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 7)
    );
    public static final DeferredBlock<ParkingBrakeBlock> PARKING_BRAKE = BLOCKS.registerBlock(
        "parking_brake", ParkingBrakeBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion()
    );
    /** The landing gear lever, with its lamps, which raises and lowers the retractable gear. */
    public static final DeferredBlock<GearLeverBlock> GEAR_LEVER = BLOCKS.registerBlock(
        "gear_lever", GearLeverBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> state.getValue(GearLeverBlock.LAMPS) == GearLeverBlock.Lamps.OFF ? 0 : 5)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GearLeverBlockEntity>> GEAR_LEVER_ENTITY =
        BLOCK_ENTITIES.register("gear_lever", () -> BlockEntityType.Builder.of(GearLeverBlockEntity::new, GEAR_LEVER.get()).build(null));
    /** The laser path clearer's switch on the console, lit while armed, and its turret in the nose's underside. */
    public static final DeferredBlock<LaserSwitchBlock> LASER_SWITCH = BLOCKS.registerBlock(
        "laser_switch", LaserSwitchBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> state.getValue(LaserSwitchBlock.ARMED) ? 7 : 0)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserSwitchBlockEntity>> LASER_SWITCH_ENTITY =
        BLOCK_ENTITIES.register("laser_switch", () -> BlockEntityType.Builder.of(LaserSwitchBlockEntity::new, LASER_SWITCH.get()).build(null));
    public static final DeferredBlock<LaserTurretBlock> LASER_TURRET = BLOCKS.registerBlock("laser_turret", LaserTurretBlock::new, metal(MapColor.METAL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LaserTurretBlockEntity>> LASER_TURRET_ENTITY =
        BLOCK_ENTITIES.register("laser_turret", () -> BlockEntityType.Builder.of(LaserTurretBlockEntity::new, LASER_TURRET.get()).build(null));
    /** The self-destruct button on the flight deck's back wall, under its striped cover, glowing red while armed. */
    public static final DeferredBlock<SelfDestructButtonBlock> SELF_DESTRUCT_BUTTON = BLOCKS.registerBlock(
        "self_destruct_button", SelfDestructButtonBlock::new,
        metal(MapColor.COLOR_RED).noOcclusion().lightLevel(state -> state.getValue(SelfDestructButtonBlock.ARMED) ? ARMED_GLOW : 0)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SelfDestructButtonBlockEntity>> SELF_DESTRUCT_BUTTON_ENTITY =
        BLOCK_ENTITIES.register("self_destruct_button", () -> BlockEntityType.Builder.of(SelfDestructButtonBlockEntity::new, SELF_DESTRUCT_BUTTON.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LinkedControlBlockEntity>> LINKED_CONTROL_ENTITY =
        BLOCK_ENTITIES.register("linked_control", () -> BlockEntityType.Builder.of(LinkedControlBlockEntity::new, TILLER.get(), PARKING_BRAKE.get(), JUMP_SWITCH.get()).build(null));
    public static final DeferredBlock<AutopilotBlock> AUTOPILOT = BLOCKS.registerBlock(
        "autopilot", AutopilotBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 7)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutopilotBlockEntity>> AUTOPILOT_ENTITY =
        BLOCK_ENTITIES.register("autopilot", () -> BlockEntityType.Builder.of(AutopilotBlockEntity::new, AUTOPILOT.get()).build(null));
    /**
     * What the control surfaces' panels, drawn by the autopilot, are walked on: invisible plates laid with the wings, so
     * they cannot be mined, blown up or pushed, and drop nothing; they move with the airliner when it is assembled.
     */
    public static final DeferredBlock<SurfacePanelBlock> SURFACE_PANEL = BLOCKS.registerBlock(
        "surface_panel", SurfacePanelBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(ONLY_EVER_PLACED, 3600000.0F).noLootTable().noOcclusion()
            .sound(SoundType.METAL).isValidSpawn(AerolargeRegistry::never).isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false).pushReaction(PushReaction.BLOCK)
    );
    /** The pilots' seats: two blocks tall, sat in like Create's seats. */
    public static final DeferredBlock<PilotSeatBlock> PILOT_SEAT = BLOCKS.registerBlock(
        "pilot_seat", PilotSeatBlock::new, metal(MapColor.COLOR_BLACK).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    /** The flight crew's locker, where the pilots' helmets and caps are stowed, and its door's sounds. */
    public static final DeferredBlock<CrewLockerBlock> CREW_LOCKER = BLOCKS.registerBlock(
        "crew_locker", CrewLockerBlock::new, metal(MapColor.METAL).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CrewLockerBlockEntity>> CREW_LOCKER_ENTITY =
        BLOCK_ENTITIES.register("crew_locker", () -> BlockEntityType.Builder.of(CrewLockerBlockEntity::new, CREW_LOCKER.get()).build(null));
    public static final DeferredItem<BlockItem> CREW_LOCKER_ITEM = ITEMS.registerSimpleBlockItem(CREW_LOCKER);
    public static final DeferredHolder<SoundEvent, SoundEvent> CREW_LOCKER_OPEN = sound("crew_locker_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> CREW_LOCKER_CLOSE = sound("crew_locker_close");
    /** The exterior lights: navigation lights, strobes and beacons on the wingtips, tail and fuselage. */
    public static final DeferredBlock<NavLightBlock> NAV_LIGHT = BLOCKS.registerBlock(
        "nav_light", NavLightBlock::new, glass().lightLevel(state -> 9).noCollission()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<NavLightBlockEntity>> NAV_LIGHT_ENTITY =
        BLOCK_ENTITIES.register("nav_light", () -> BlockEntityType.Builder.of(NavLightBlockEntity::new, NAV_LIGHT.get()).build(null));
    public static final DeferredBlock<ThrustRelayBlock> THRUST_RELAY = BLOCKS.registerBlock("thrust_relay", ThrustRelayBlock::new, metal(MapColor.SNOW));
    /** The same relay set into the cabin wall beside a lamp it lights, plated like the hull around it. */
    public static final DeferredBlock<HullRelayBlock> HULL_RELAY = BLOCKS.registerBlock("hull_relay", HullRelayBlock::new, metal(MapColor.SNOW));
    /** The primary flight displays in the panel, one in front of each pilot. */
    public static final DeferredBlock<FlightDisplayBlock> FLIGHT_DISPLAY = BLOCKS.registerBlock(
        "flight_display", FlightDisplayBlock::new, metal(MapColor.COLOR_BLACK).lightLevel(state -> 7)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlightDisplayBlockEntity>> FLIGHT_DISPLAY_ENTITY =
        BLOCK_ENTITIES.register("flight_display", () -> BlockEntityType.Builder.of(FlightDisplayBlockEntity::new, FLIGHT_DISPLAY.get()).build(null));
    /** The big touch screen in the middle of the instrument panel that works the autopilot; placed only by the blueprints. */
    public static final DeferredBlock<AutopilotScreenBlock> AUTOPILOT_SCREEN = BLOCKS.registerBlock(
        "autopilot_screen", AutopilotScreenBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 9)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutopilotScreenBlockEntity>> AUTOPILOT_SCREEN_ENTITY =
        BLOCK_ENTITIES.register("autopilot_screen", () -> BlockEntityType.Builder.of(AutopilotScreenBlockEntity::new, AUTOPILOT_SCREEN.get()).build(null));
    /** The screens of the flight deck's curved row either side of the autopilot screen, turned toward the seats. */
    public static final DeferredBlock<CockpitDisplayBlock> COCKPIT_DISPLAY = BLOCKS.registerBlock(
        "cockpit_display", CockpitDisplayBlock::new,
        metal(MapColor.COLOR_BLACK).noOcclusion().noLootTable().lightLevel(state -> state.getValue(CockpitDisplayBlock.HOLDER) ? 7 : 0)
            .isSuffocating((state, level, pos) -> false).isViewBlocking((state, level, pos) -> false)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CockpitDisplayBlockEntity>> COCKPIT_DISPLAY_ENTITY =
        BLOCK_ENTITIES.register("cockpit_display", () -> BlockEntityType.Builder.of(CockpitDisplayBlockEntity::new, COCKPIT_DISPLAY.get()).build(null));
    /** The wide screen over the autopilot screen showing the picture from the camera on top of the fin. */
    public static final DeferredBlock<TailCameraDisplayBlock> TAIL_CAMERA_DISPLAY = BLOCKS.registerBlock(
        "tail_camera_display", TailCameraDisplayBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 5)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TailCameraDisplayBlockEntity>> TAIL_CAMERA_DISPLAY_ENTITY =
        BLOCK_ENTITIES.register("tail_camera_display", () -> BlockEntityType.Builder.of(TailCameraDisplayBlockEntity::new, TAIL_CAMERA_DISPLAY.get()).build(null));
    /** The panel of picture tiles on the flight deck, each making one cabin announcement. */
    public static final DeferredBlock<AnnouncementPanelBlock> ANNOUNCEMENT_PANEL = BLOCKS.registerBlock(
        "announcement_panel", AnnouncementPanelBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 7)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AnnouncementPanelBlockEntity>> ANNOUNCEMENT_PANEL_ENTITY =
        BLOCK_ENTITIES.register("announcement_panel", () -> BlockEntityType.Builder.of(AnnouncementPanelBlockEntity::new, ANNOUNCEMENT_PANEL.get()).build(null));
    /** The big route map on the flight deck's back wall, as big as the announcement panel. */
    public static final DeferredBlock<FlightMapBlock> FLIGHT_MAP = BLOCKS.registerBlock(
        "flight_map", FlightMapBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 7)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlightMapBlockEntity>> FLIGHT_MAP_ENTITY =
        BLOCK_ENTITIES.register("flight_map", () -> BlockEntityType.Builder.of(FlightMapBlockEntity::new, FLIGHT_MAP.get()).build(null));
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
    public static final DeferredItem<BlockItem> GEAR_BAY_ITEM = ITEMS.registerSimpleBlockItem(GEAR_BAY);
    public static final DeferredItem<BlockItem> THRUST_LEVER_ITEM = ITEMS.registerSimpleBlockItem(THRUST_LEVER);
    public static final DeferredItem<BlockItem> INSTRUMENT_PANEL_ITEM = ITEMS.registerSimpleBlockItem(INSTRUMENT_PANEL);
    public static final DeferredItem<BlockItem> AUTOPILOT_ITEM = ITEMS.registerSimpleBlockItem(AUTOPILOT);
    public static final DeferredItem<BlockItem> FLIGHT_DISPLAY_ITEM = ITEMS.registerSimpleBlockItem(FLIGHT_DISPLAY);
    public static final DeferredItem<BlockItem> TAIL_CAMERA_DISPLAY_ITEM = ITEMS.registerSimpleBlockItem(TAIL_CAMERA_DISPLAY);
    public static final DeferredItem<BlockItem> ANNOUNCEMENT_PANEL_ITEM = ITEMS.registerSimpleBlockItem(ANNOUNCEMENT_PANEL);
    public static final DeferredItem<BlockItem> FLIGHT_MAP_ITEM = ITEMS.registerSimpleBlockItem(FLIGHT_MAP);
    public static final DeferredItem<BlockItem> TILLER_ITEM = ITEMS.registerSimpleBlockItem(TILLER);
    public static final DeferredItem<BlockItem> PARKING_BRAKE_ITEM = ITEMS.registerSimpleBlockItem(PARKING_BRAKE);
    public static final DeferredItem<BlockItem> GEAR_LEVER_ITEM = ITEMS.registerSimpleBlockItem(GEAR_LEVER);
    public static final DeferredItem<BlockItem> LASER_SWITCH_ITEM = ITEMS.registerSimpleBlockItem(LASER_SWITCH);
    public static final DeferredItem<BlockItem> LASER_TURRET_ITEM = ITEMS.registerSimpleBlockItem(LASER_TURRET);
    public static final DeferredItem<BlockItem> SELF_DESTRUCT_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(SELF_DESTRUCT_BUTTON);
    public static final DeferredItem<BlockItem> PILOT_SEAT_ITEM = ITEMS.registerSimpleBlockItem(PILOT_SEAT);
    public static final DeferredItem<BlockItem> NAV_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(NAV_LIGHT);
    public static final DeferredItem<BlockItem> AIRSTAIR_CONTROLLER_ITEM = ITEMS.registerSimpleBlockItem(AIRSTAIR_CONTROLLER);
    public static final DeferredItem<BlockItem> AIRSTAIR_CALL_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(AIRSTAIR_CALL_BUTTON);
    public static final DeferredItem<BlockItem> AIRSTAIR_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(AIRSTAIR_BUTTON);
    public static final DeferredItem<BlockItem> DOOR_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(DOOR_BUTTON);

    /** Skydiving out of the airliner: the jump light by the doors, the rack of parachutes and the target to land on. */
    public static final DeferredBlock<JumpLightBlock> JUMP_LIGHT = BLOCKS.registerBlock(
        "jump_light", JumpLightBlock::new, glass().lightLevel(state -> state.getValue(JumpLightBlock.SIGNAL).light())
    );
    public static final DeferredBlock<ParachuteRackBlock> PARACHUTE_RACK = BLOCKS.registerBlock(
        "parachute_rack", ParachuteRackBlock::new, metal(MapColor.COLOR_GREEN)
    );
    public static final DeferredBlock<PaperPlaneBoxBlock> PAPER_PLANE_BOX = BLOCKS.registerBlock(
        "paper_plane_box", PaperPlaneBoxBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD)
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
    public static final DeferredItem<BlockItem> PAPER_PLANE_BOX_ITEM = ITEMS.registerSimpleBlockItem(PAPER_PLANE_BOX);
    public static final DeferredItem<BlockItem> LANDING_TARGET_ITEM = ITEMS.registerSimpleBlockItem(LANDING_TARGET);

    /** Speaking to the cabin and the tower: the captain's handset on the flight deck and a radio for the airfield. */
    public static final DeferredBlock<HandsetBlock> HANDSET = BLOCKS.registerBlock("handset", HandsetBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion());
    public static final DeferredBlock<TowerRadioBlock> TOWER_RADIO = BLOCKS.registerBlock("tower_radio", TowerRadioBlock::new, metal(MapColor.COLOR_GRAY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RadioBlockEntity>> RADIO_ENTITY =
        BLOCK_ENTITIES.register("radio", () -> BlockEntityType.Builder.of(RadioBlockEntity::new, HANDSET.get(), TOWER_RADIO.get()).build(null));
    public static final DeferredItem<BlockItem> HANDSET_ITEM = ITEMS.registerSimpleBlockItem(HANDSET);
    public static final DeferredItem<BlockItem> TOWER_RADIO_ITEM = ITEMS.registerSimpleBlockItem(TOWER_RADIO);

    /** The control tower: its radar screen, and the transponders that show on the radar and on the autopilots' maps. */
    public static final DeferredBlock<TowerRadarBlock> TOWER_RADAR = BLOCKS.registerBlock(
        "tower_radar", TowerRadarBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TowerRadarBlockEntity>> TOWER_RADAR_ENTITY =
        BLOCK_ENTITIES.register("tower_radar", () -> BlockEntityType.Builder.of(TowerRadarBlockEntity::new, TOWER_RADAR.get()).build(null));
    public static final DeferredBlock<TransponderBlock> TRANSPONDER = BLOCKS.registerBlock(
        "transponder", TransponderBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TransponderBlockEntity>> TRANSPONDER_ENTITY =
        BLOCK_ENTITIES.register("transponder", () -> BlockEntityType.Builder.of(TransponderBlockEntity::new, TRANSPONDER.get()).build(null));
    public static final DeferredItem<BlockItem> TOWER_RADAR_ITEM = ITEMS.registerSimpleBlockItem(TOWER_RADAR);
    public static final DeferredItem<BlockItem> TRANSPONDER_ITEM = ITEMS.registerSimpleBlockItem(TRANSPONDER);
    /** A motion sensor over an automatic door, as at the control tower's entrance. */
    public static final DeferredBlock<MotionSensorBlock> MOTION_SENSOR = BLOCKS.registerBlock(
        "motion_sensor", MotionSensorBlock::new, metal(MapColor.SNOW).lightLevel(state -> state.getValue(MotionSensorBlock.POWERED) ? 3 : 0)
    );
    public static final DeferredItem<BlockItem> MOTION_SENSOR_ITEM = ITEMS.registerSimpleBlockItem(MOTION_SENSOR);
    /** A lift panel by the door of one of Create's elevators, or in its cabin, as in the control tower. */
    public static final DeferredBlock<LiftPanelBlock> LIFT_PANEL = BLOCKS.registerBlock(
        "lift_panel", LiftPanelBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SoundType.METAL).noOcclusion().lightLevel(state -> 5)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LiftPanelBlockEntity>> LIFT_PANEL_ENTITY =
        BLOCK_ENTITIES.register("lift_panel", () -> BlockEntityType.Builder.of(LiftPanelBlockEntity::new, LIFT_PANEL.get()).build(null));
    public static final DeferredItem<BlockItem> LIFT_PANEL_ITEM = ITEMS.registerSimpleBlockItem(LIFT_PANEL);
    /** The wind sock on the control tower's roof, flying with the wind its weather screen reports. */
    public static final DeferredBlock<WindSockBlock> WIND_SOCK = BLOCKS.registerBlock(
        "wind_sock", WindSockBlock::new, metal(MapColor.COLOR_ORANGE).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WindSockBlockEntity>> WIND_SOCK_ENTITY =
        BLOCK_ENTITIES.register("wind_sock", () -> BlockEntityType.Builder.of(WindSockBlockEntity::new, WIND_SOCK.get()).build(null));
    public static final DeferredItem<BlockItem> WIND_SOCK_ITEM = ITEMS.registerSimpleBlockItem(WIND_SOCK);
    /** The tower's weather screen, reporting the airfield's weather under its information letter. */
    public static final DeferredBlock<AtisScreenBlock> ATIS_SCREEN = BLOCKS.registerBlock(
        "atis_screen", AtisScreenBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 5)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AtisScreenBlockEntity>> ATIS_SCREEN_ENTITY =
        BLOCK_ENTITIES.register("atis_screen", () -> BlockEntityType.Builder.of(AtisScreenBlockEntity::new, ATIS_SCREEN.get()).build(null));
    public static final DeferredItem<BlockItem> ATIS_SCREEN_ITEM = ITEMS.registerSimpleBlockItem(ATIS_SCREEN);
    /** The tower's flight strip board, listing every transponder in the radar's range. */
    public static final DeferredBlock<FlightStripBoardBlock> FLIGHT_STRIP_BOARD = BLOCKS.registerBlock(
        "flight_strip_board", FlightStripBoardBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 5)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlightStripBoardBlockEntity>> FLIGHT_STRIP_BOARD_ENTITY =
        BLOCK_ENTITIES.register("flight_strip_board", () -> BlockEntityType.Builder.of(FlightStripBoardBlockEntity::new, FLIGHT_STRIP_BOARD.get()).build(null));
    public static final DeferredItem<BlockItem> FLIGHT_STRIP_BOARD_ITEM = ITEMS.registerSimpleBlockItem(FLIGHT_STRIP_BOARD);

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
        "seatbelt_switch", SeatbeltSwitchBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> 7)
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
            .lightLevel(state -> Brightness.light(state, 15))
    );
    public static final DeferredBlock<PendantLightBlock> PENDANT_LIGHT = BLOCKS.registerBlock(
        "pendant_light", PendantLightBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.3F).sound(SoundType.LANTERN).noOcclusion()
            .lightLevel(state -> Brightness.light(state, 15))
    );
    public static final DeferredBlock<LightCableBlock> LIGHT_CABLE = BLOCKS.registerBlock(
        "light_cable", LightCableBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.2F).sound(SoundType.CHAIN).noCollission().noOcclusion()
    );
    /** The rocket's wall washer, throwing a fan of light up her wall, warm, blue or pink. */
    public static final DeferredBlock<WallLightBlock> WALL_LIGHT = BLOCKS.registerBlock(
        "wall_light", WallLightBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.3F).sound(SoundType.LANTERN).noCollission().noOcclusion()
            .lightLevel(WallLightBlock::light)
    );
    /** The rocket's halo light, hung on the cable above, warm white or a cool blue night light. */
    public static final DeferredBlock<HaloLightBlock> HALO_LIGHT = BLOCKS.registerBlock(
        "halo_light", HaloLightBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.3F).sound(SoundType.GLASS).noOcclusion().lightLevel(HaloLightBlock::light)
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
            .lightLevel(state -> CabinLightBlock.light(state, STARLIGHT_GLOW))
    );
    public static final DeferredItem<BlockItem> LIGHT_STRIP_ITEM = ITEMS.registerSimpleBlockItem(LIGHT_STRIP);
    public static final DeferredItem<BlockItem> STARLIGHT_PANEL_ITEM = ITEMS.registerSimpleBlockItem(STARLIGHT_PANEL);
    public static final DeferredItem<BlockItem> PENDANT_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(PENDANT_LIGHT);
    public static final DeferredItem<BlockItem> LIGHT_CABLE_ITEM = ITEMS.registerSimpleBlockItem(LIGHT_CABLE);
    public static final DeferredItem<BlockItem> HALO_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(HALO_LIGHT);
    public static final DeferredItem<BlockItem> WALL_LIGHT_ITEM = ITEMS.registerSimpleBlockItem(WALL_LIGHT);
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
    /** The white archways in the bulkheads, put up whole by hand: as tough as the partition, and nothing spawns on it. */
    public static final DeferredBlock<CabinArchBlock> CABIN_ARCH = BLOCKS.registerBlock(
        "cabin_arch", CabinArchBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.5F).sound(SoundType.WOOD).noOcclusion().isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredItem<BlockItem> CABIN_ARCH_ITEM = ITEMS.registerSimpleBlockItem(CABIN_ARCH);
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
     * The backlit signs set into the bulkheads over the archways, a picture of what lies through each: a block of the
     * bulkhead, as tough as the partition, and nothing spawns on it. Its picture glows but lights nothing.
     */
    public static final DeferredBlock<BulkheadSignBlock> BULKHEAD_SIGN = BLOCKS.registerBlock(
        "bulkhead_sign", BulkheadSignBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5F).sound(SoundType.WOOD).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredItem<BlockItem> BULKHEAD_SIGN_ITEM = ITEMS.registerSimpleBlockItem(BULKHEAD_SIGN);
    /**
     * The WC signs, lit green while a WC is free and red while it is taken: the one over a WC's door, a block of the
     * bulkhead as tough as the partition, and the slim lightboxes high up in the cabin that point the way to the WCs,
     * which nothing bumps into. Their faces glow but light nothing, and nothing spawns on either.
     */
    public static final DeferredBlock<WcSignBlock> WC_SIGN = BLOCKS.registerBlock(
        "wc_sign", WcSignBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.5F).sound(SoundType.WOOD).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<WcSignBlockEntity>> WC_SIGN_ENTITY =
        BLOCK_ENTITIES.register("wc_sign", () -> BlockEntityType.Builder.of(WcSignBlockEntity::new, WC_SIGN.get()).build(null));
    public static final DeferredItem<BlockItem> WC_SIGN_ITEM = ITEMS.registerSimpleBlockItem(WC_SIGN);
    public static final DeferredBlock<OverheadWcSignBlock> OVERHEAD_WC_SIGN = BLOCKS.registerBlock(
        "overhead_wc_sign", OverheadWcSignBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F).sound(SoundType.METAL).noCollission().noOcclusion()
            .isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<OverheadWcSignBlockEntity>> OVERHEAD_WC_SIGN_ENTITY =
        BLOCK_ENTITIES.register("overhead_wc_sign", () -> BlockEntityType.Builder.of(OverheadWcSignBlockEntity::new, OVERHEAD_WC_SIGN.get()).build(null));
    public static final DeferredItem<BlockItem> OVERHEAD_WC_SIGN_ITEM = ITEMS.registerSimpleBlockItem(OVERHEAD_WC_SIGN);
    /**
     * The staircases between the decks: carpeted stairs and landings, which sound like the carpet they are, and the glass
     * rails along their open sides and round the upper deck's wells. Nothing spawns on any of them.
     */
    public static final DeferredBlock<CabinStairsBlock> CABIN_STAIRS = BLOCKS.registerBlock(
        "cabin_stairs", CabinStairsBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.5F).sound(SoundType.WOOL).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredBlock<Block> CABIN_LANDING = BLOCKS.registerSimpleBlock(
        "cabin_landing", BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.5F).sound(SoundType.WOOL).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredBlock<CabinRailBlock> CABIN_RAIL = BLOCKS.registerBlock("cabin_rail", CabinRailBlock::new, glass());
    public static final DeferredItem<BlockItem> CABIN_STAIRS_ITEM = ITEMS.registerSimpleBlockItem(CABIN_STAIRS);
    public static final DeferredItem<BlockItem> CABIN_LANDING_ITEM = ITEMS.registerSimpleBlockItem(CABIN_LANDING);
    public static final DeferredItem<BlockItem> CABIN_RAIL_ITEM = ITEMS.registerSimpleBlockItem(CABIN_RAIL);
    /**
     * The passengers' seats, economy and business: two blocks tall like the pilots', one of Create's seats underneath,
     * upholstered in any dye's colour, and as soft to sit on as they sound. Nothing spawns on them.
     */
    public static final DeferredBlock<CabinSeatBlock> ECONOMY_SEAT = BLOCKS.registerBlock(
        "economy_seat", properties -> new CabinSeatBlock(properties, CabinSeatBlock.Cut.ECONOMY),
        metal(MapColor.COLOR_BLUE).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredBlock<CabinSeatBlock> BUSINESS_SEAT = BLOCKS.registerBlock(
        "business_seat", properties -> new CabinSeatBlock(properties, CabinSeatBlock.Cut.BUSINESS),
        metal(MapColor.SNOW).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredItem<BlockItem> ECONOMY_SEAT_ITEM = ITEMS.registerSimpleBlockItem(ECONOMY_SEAT);
    public static final DeferredItem<BlockItem> BUSINESS_SEAT_ITEM = ITEMS.registerSimpleBlockItem(BUSINESS_SEAT);
    /** The cabin's carpet, and the aisle's runner with its floor path lights: a woven pile, as soft as vanilla's. */
    public static final DeferredBlock<CabinCarpetBlock> CABIN_CARPET = BLOCKS.registerBlock("cabin_carpet", CabinCarpetBlock::new, carpet());
    public static final DeferredBlock<AisleCarpetBlock> AISLE_CARPET = BLOCKS.registerBlock("aisle_carpet", AisleCarpetBlock::new, carpet());
    public static final DeferredItem<BlockItem> CABIN_CARPET_ITEM = ITEMS.registerSimpleBlockItem(CABIN_CARPET);
    public static final DeferredItem<BlockItem> AISLE_CARPET_ITEM = ITEMS.registerSimpleBlockItem(AISLE_CARPET);
    /** The gym hall's sprung maple floor, its court lines painted on: as tough as planks, and nothing spawns on it. */
    public static final DeferredBlock<GymFloorBlock> GYM_FLOOR = BLOCKS.registerBlock(
        "gym_floor", GymFloorBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.0F, 3.0F).sound(SoundType.WOOD).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredItem<BlockItem> GYM_FLOOR_ITEM = ITEMS.registerSimpleBlockItem(GYM_FLOOR);
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
    /** The lavatories' washbasin, with its mirror over it. */
    public static final DeferredBlock<LavatoryBasinBlock> LAVATORY_BASIN = BLOCKS.registerBlock(
        "lavatory_basin", LavatoryBasinBlock::new, metal(MapColor.SNOW).noOcclusion()
    );
    public static final DeferredItem<BlockItem> LAVATORY_BASIN_ITEM = ITEMS.registerSimpleBlockItem(LAVATORY_BASIN);
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
    public static final DeferredHolder<MenuType<?>, MenuType<DropBayMenu>> DROP_BAY_MENU =
        MENU_TYPES.register("drop_bay", () -> new MenuType<>(DropBayMenu::new, FeatureFlags.VANILLA_SET));
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
    /** The sky crane's hook, swinging on its winch's rope under a zeppelin with whatever it has caught. */
    public static final DeferredHolder<EntityType<?>, EntityType<CraneHook>> CRANE_HOOK = ENTITY_TYPES.register(
        "crane_hook", () -> EntityType.Builder.<CraneHook>of(CraneHook::new, MobCategory.MISC).sized(0.3F, 0.3F)
            .clientTrackingRange(10).updateInterval(1).build("crane_hook")
    );
    /** The hatch in the cabin floor over the ladder down to the hold, so the drop bay can be reloaded in flight. */
    public static final DeferredBlock<FloorHatchBlock> FLOOR_HATCH = BLOCKS.registerBlock(
        "floor_hatch", FloorHatchBlock::new, metal(MapColor.COLOR_YELLOW).noOcclusion()
    );
    public static final DeferredItem<BlockItem> FLOOR_HATCH_ITEM = ITEMS.registerSimpleBlockItem(FLOOR_HATCH);

    /** The hold's fit-out. Cargo nets: webbing on steel fittings, to see and reach through; nothing spawns on them. */
    public static final DeferredBlock<CargoNetBlock> CARGO_NET = BLOCKS.registerBlock(
        "cargo_net", CargoNetBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(0.8F).sound(SoundType.WOOL).noOcclusion()
            .isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredItem<BlockItem> CARGO_NET_ITEM = ITEMS.registerSimpleBlockItem(CARGO_NET);
    /** The hold's floor panels, laid like carpet: roller tracks with a tie-down rail, and ball mats. */
    public static final DeferredBlock<CargoFloorBlock> CARGO_FLOOR = BLOCKS.registerBlock("cargo_floor", CargoFloorBlock::new, metal(MapColor.COLOR_GRAY));
    public static final DeferredBlock<BallMatBlock> BALL_MAT = BLOCKS.registerBlock("ball_mat", BallMatBlock::new, metal(MapColor.COLOR_GRAY));
    public static final DeferredItem<BlockItem> CARGO_FLOOR_ITEM = ITEMS.registerSimpleBlockItem(CARGO_FLOOR);
    public static final DeferredItem<BlockItem> BALL_MAT_ITEM = ITEMS.registerSimpleBlockItem(BALL_MAT);
    /** The hold's ULD containers, eight blocks to an LD3, of aluminium. */
    public static final DeferredBlock<UldContainerBlock> ULD_CONTAINER = BLOCKS.registerBlock(
        "uld_container", UldContainerBlock::new, metal(MapColor.METAL).noOcclusion()
    );
    public static final DeferredItem<BlockItem> ULD_CONTAINER_ITEM = ITEMS.registerSimpleBlockItem(ULD_CONTAINER);

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
            .lightLevel(state -> Brightness.light(state, FAIRY_GLOW))
    );
    public static final DeferredItem<DoubleHighBlockItem> SECRET_BOOKSHELF_ITEM = ITEMS.register(
        "secret_bookshelf", () -> new DoubleHighBlockItem(SECRET_BOOKSHELF.get(), new Item.Properties())
    );
    public static final DeferredItem<BlockItem> BALL_PIT_ITEM = ITEMS.registerSimpleBlockItem(BALL_PIT);
    public static final DeferredItem<BlockItem> FAIRY_LIGHTS_ITEM = ITEMS.registerSimpleBlockItem(FAIRY_LIGHTS);
    /** The light a bunk's reading light gives while it is on, in light levels: enough to read by on the pillow. */
    private static final int READING_GLOW = 7;
    /**
     * The den's bunks: padded, a reading light at the head, soft to fall on as a bed and as quick to take down; nothing
     * spawns on them.
     */
    public static final DeferredBlock<BunkBlock> BUNK = BLOCKS.registerBlock(
        "bunk", BunkBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.8F).sound(SoundType.WOOL).noOcclusion()
            .pushReaction(PushReaction.DESTROY).isValidSpawn(AerolargeRegistry::never)
            .lightLevel(state -> state.getValue(BunkBlock.LIT) && state.getValue(BunkBlock.PART) == BedPart.HEAD ? READING_GLOW : 0)
    );
    public static final DeferredItem<BlockItem> BUNK_ITEM = ITEMS.registerSimpleBlockItem(BUNK);
    /**
     * The den's bean bags: one of Create's seats underneath, as soft and as quick to take up as wool; nothing spawns
     * on them.
     */
    public static final DeferredBlock<BeanBagBlock> BEAN_BAG = BLOCKS.registerBlock(
        "bean_bag", BeanBagBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_ORANGE).strength(0.5F).sound(SoundType.WOOL).noOcclusion()
            .pushReaction(PushReaction.DESTROY).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredItem<BlockItem> BEAN_BAG_ITEM = ITEMS.registerSimpleBlockItem(BEAN_BAG);
    /** The light a disco ball gives while it is on, in light levels: the den stays lit wherever its spots are not drawn. */
    private static final int DISCO_GLOW = 10;
    /**
     * The den's disco ball: mirror glass, hung overhead with nothing to bump into, quick to take down; it lights the
     * room round it while on.
     */
    public static final DeferredBlock<DiscoBallBlock> DISCO_BALL = BLOCKS.registerBlock(
        "disco_ball", DiscoBallBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.GLASS).noCollission().noOcclusion()
            .pushReaction(PushReaction.DESTROY).isValidSpawn(AerolargeRegistry::never)
            .lightLevel(state -> state.getValue(DiscoBallBlock.LIT) ? DISCO_GLOW : 0)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DiscoBallBlockEntity>> DISCO_BALL_ENTITY =
        BLOCK_ENTITIES.register("disco_ball", () -> BlockEntityType.Builder.of(DiscoBallBlockEntity::new, DISCO_BALL.get()).build(null));
    public static final DeferredItem<BlockItem> DISCO_BALL_ITEM = ITEMS.registerSimpleBlockItem(DISCO_BALL);
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
    /** A little rubber duck to put down anywhere, or float in water, and its squeak. */
    public static final DeferredBlock<RubberDuckBlock> RUBBER_DUCK = BLOCKS.registerBlock(
        "rubber_duck", RubberDuckBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.WOOL).noOcclusion()
            .pushReaction(PushReaction.DESTROY).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RubberDuckBlockEntity>> RUBBER_DUCK_ENTITY =
        BLOCK_ENTITIES.register("rubber_duck", () -> BlockEntityType.Builder.of(RubberDuckBlockEntity::new, RUBBER_DUCK.get()).build(null));
    public static final DeferredItem<BlockItem> RUBBER_DUCK_ITEM = ITEMS.registerSimpleBlockItem(RUBBER_DUCK);
    public static final DeferredHolder<SoundEvent, SoundEvent> RUBBER_DUCK_SQUEAK = sound("rubber_duck_squeak");
    /**
     * The giant rubber duck that rises out of the moon pool once all the hidden ducks are found, and its huge squeak:
     * only ever raised, so it has no item, and it leaves nothing behind. Its blocks are as light as the rest of the
     * fittings, so it does not tip the airliner.
     */
    public static final DeferredBlock<GiantRubberDuckBlock> GIANT_RUBBER_DUCK = BLOCKS.registerBlock(
        "giant_rubber_duck", GiantRubberDuckBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_YELLOW).strength(0.5F).sound(SoundType.WOOL).noOcclusion().noLootTable()
            .pushReaction(PushReaction.BLOCK).isValidSpawn(AerolargeRegistry::never).isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false).isRedstoneConductor((state, level, pos) -> false)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GiantRubberDuckBlockEntity>> GIANT_RUBBER_DUCK_ENTITY =
        BLOCK_ENTITIES.register("giant_rubber_duck", () -> BlockEntityType.Builder.of(GiantRubberDuckBlockEntity::new, GIANT_RUBBER_DUCK.get()).build(null));
    public static final DeferredHolder<SoundEvent, SoundEvent> GIANT_RUBBER_DUCK_SQUEAK = sound("giant_rubber_duck_squeak");

    /** Cabin service: the galley, the crew's trolley, and the meals, pretzels and coffee it serves. */
    public static final DeferredBlock<GalleyBlock> GALLEY = BLOCKS.registerBlock("galley", GalleyBlock::new, metal(MapColor.METAL).noOcclusion());
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
    /** An ice cream cone, sold at the duty-free counter: quick to eat, and cooling, for a little Fire Resistance. */
    public static final DeferredItem<Item> ICE_CREAM = ITEMS.registerSimpleItem(
        "ice_cream", new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationModifier(0.3F).fast().alwaysEdible()
            .effect(() -> new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 20, 0), 1.0F).build())
    );
    /** A minute of Speed in a paper cup. */
    public static final DeferredItem<CoffeeItem> AIRLINE_COFFEE = ITEMS.register("airline_coffee", () -> new CoffeeItem(new Item.Properties().food(
        new FoodProperties.Builder().nutrition(1).saturationModifier(0.2F).alwaysEdible()
            .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60 * 20, 0), 1.0F).build()
    )));
    /**
     * The coffee machine, a bean-to-cup machine for the galley, the sounds of its brew (the grinder, the pump, the
     * steam frothing the milk and the chime when the drink is ready), and what it brews: an espresso for a short burst
     * of Haste, a cappuccino for a little Speed, a mocha, sweet with chocolate, for Speed and a moment's Regeneration,
     * and for children a babycino, frothed milk with no coffee, for a little Regeneration. Each is drunk from its cup, which is gone with the drink.
     */
    public static final DeferredBlock<CoffeeMachineBlock> COFFEE_MACHINE = BLOCKS.registerBlock(
        "coffee_machine", CoffeeMachineBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CoffeeMachineBlockEntity>> COFFEE_MACHINE_ENTITY =
        BLOCK_ENTITIES.register("coffee_machine", () -> BlockEntityType.Builder.of(CoffeeMachineBlockEntity::new, COFFEE_MACHINE.get()).build(null));
    public static final DeferredItem<BlockItem> COFFEE_MACHINE_ITEM = ITEMS.registerSimpleBlockItem(COFFEE_MACHINE);
    public static final DeferredHolder<SoundEvent, SoundEvent> COFFEE_GRIND = sound("coffee_grind");
    public static final DeferredHolder<SoundEvent, SoundEvent> COFFEE_POUR = sound("coffee_pour");
    public static final DeferredHolder<SoundEvent, SoundEvent> COFFEE_STEAM = sound("coffee_steam");
    public static final DeferredHolder<SoundEvent, SoundEvent> COFFEE_READY = sound("coffee_ready");
    public static final DeferredItem<CoffeeItem> ESPRESSO = ITEMS.register("espresso", () -> new CoffeeItem(new Item.Properties().stacksTo(16).food(
        new FoodProperties.Builder().nutrition(1).saturationModifier(0.2F).alwaysEdible()
            .effect(() -> new MobEffectInstance(MobEffects.DIG_SPEED, 45 * 20, 0), 1.0F).build()
    )));
    public static final DeferredItem<CoffeeItem> CAPPUCCINO = ITEMS.register("cappuccino", () -> new CoffeeItem(new Item.Properties().stacksTo(16).food(
        new FoodProperties.Builder().nutrition(3).saturationModifier(0.4F).alwaysEdible()
            .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 45 * 20, 0), 1.0F).build()
    )));
    public static final DeferredItem<CoffeeItem> MOCHA = ITEMS.register("mocha", () -> new CoffeeItem(new Item.Properties().stacksTo(16).food(
        new FoodProperties.Builder().nutrition(4).saturationModifier(0.5F).alwaysEdible()
            .effect(() -> new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30 * 20, 0), 1.0F)
            .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 5 * 20, 0), 1.0F).build()
    )));
    public static final DeferredItem<CoffeeItem> BABYCINO = ITEMS.register("babycino", () -> new CoffeeItem(new Item.Properties().stacksTo(16).food(
        new FoodProperties.Builder().nutrition(3).saturationModifier(0.4F).alwaysEdible()
            .effect(() -> new MobEffectInstance(MobEffects.REGENERATION, 10 * 20, 0), 1.0F).build()
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

    /**
     * The moon pool's tiling: the non-slip deck tile round it, the glass mosaic lining its basin, the coping along its
     * edge and the tiled steps out of it. As tough as stone, and nothing spawns on any of them.
     */
    public static final DeferredBlock<Block> POOL_DECK_TILE = BLOCKS.registerSimpleBlock("pool_deck_tile", poolTile(MapColor.SAND));
    public static final DeferredBlock<PoolMosaicBlock> POOL_MOSAIC = BLOCKS.registerBlock(
        "pool_mosaic", PoolMosaicBlock::new, poolTile(MapColor.COLOR_LIGHT_BLUE).sound(SoundType.GLASS)
    );
    public static final DeferredBlock<PoolCopingBlock> POOL_COPING = BLOCKS.registerBlock("pool_coping", PoolCopingBlock::new, poolTile(MapColor.SNOW));
    public static final DeferredBlock<PoolStepsBlock> POOL_STEPS = BLOCKS.registerBlock(
        "pool_steps", properties -> new PoolStepsBlock(POOL_DECK_TILE.get().defaultBlockState(), properties), poolTile(MapColor.SAND)
    );
    public static final DeferredItem<BlockItem> POOL_DECK_TILE_ITEM = ITEMS.registerSimpleBlockItem(POOL_DECK_TILE);
    public static final DeferredItem<BlockItem> POOL_MOSAIC_ITEM = ITEMS.registerSimpleBlockItem(POOL_MOSAIC);
    public static final DeferredItem<BlockItem> POOL_COPING_ITEM = ITEMS.registerSimpleBlockItem(POOL_COPING);
    public static final DeferredItem<BlockItem> POOL_STEPS_ITEM = ITEMS.registerSimpleBlockItem(POOL_STEPS);
    /** The diving tower's chrome ladder, climbed as any ladder is. */
    public static final DeferredBlock<RungLadderBlock> POOL_LADDER = BLOCKS.registerBlock(
        "pool_ladder", properties -> new RungLadderBlock(properties, RungLadderBlock.Kind.POOL_LADDER),
        metal(MapColor.METAL).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredItem<BlockItem> POOL_LADDER_ITEM = ITEMS.registerSimpleBlockItem(POOL_LADDER);

    /**
     * The gym hall's fittings: the wall bars and the climbing ropes, climbed as a ladder is, the mats in front of the
     * bars, the hoops' glass backboards and the benches along the walls. Nothing spawns on any of them.
     */
    public static final DeferredBlock<RungLadderBlock> WALL_BARS = BLOCKS.registerBlock(
        "wall_bars", properties -> new RungLadderBlock(properties, RungLadderBlock.Kind.WALL_BARS), gymFitting(MapColor.WOOD).sound(SoundType.LADDER)
    );
    public static final DeferredBlock<GymMatBlock> GYM_MAT = BLOCKS.registerBlock(
        "gym_mat", GymMatBlock::new, gymFitting(MapColor.COLOR_BLUE).strength(0.5F).sound(SoundType.WOOL).ignitedByLava()
    );
    public static final DeferredBlock<ClimbingRopeBlock> CLIMBING_ROPE = BLOCKS.registerBlock(
        "climbing_rope", ClimbingRopeBlock::new, gymFitting(MapColor.WOOD).strength(0.5F).sound(SoundType.WOOL).noCollission().ignitedByLava()
    );
    public static final DeferredBlock<GlassBackboardBlock> GLASS_BACKBOARD = BLOCKS.registerBlock("glass_backboard", GlassBackboardBlock::new, glass());
    public static final DeferredBlock<GymBenchBlock> GYM_BENCH = BLOCKS.registerBlock(
        "gym_bench", GymBenchBlock::new, gymFitting(MapColor.WOOD).strength(1.5F).sound(SoundType.WOOD)
    );
    public static final DeferredItem<BlockItem> WALL_BARS_ITEM = ITEMS.registerSimpleBlockItem(WALL_BARS);
    public static final DeferredItem<BlockItem> GYM_MAT_ITEM = ITEMS.registerSimpleBlockItem(GYM_MAT);
    public static final DeferredItem<BlockItem> CLIMBING_ROPE_ITEM = ITEMS.registerSimpleBlockItem(CLIMBING_ROPE);
    public static final DeferredItem<BlockItem> GLASS_BACKBOARD_ITEM = ITEMS.registerSimpleBlockItem(GLASS_BACKBOARD);
    public static final DeferredItem<BlockItem> GYM_BENCH_ITEM = ITEMS.registerSimpleBlockItem(GYM_BENCH);

    /** A paper plane to throw and watch glide, down the cabin or out of a door in flight, and its whoosh off the hand. */
    public static final DeferredHolder<EntityType<?>, EntityType<PaperPlane>> PAPER_PLANE = ENTITY_TYPES.register(
        "paper_plane", () -> EntityType.Builder.<PaperPlane>of(PaperPlane::new, MobCategory.MISC).sized(0.4F, 0.15F)
            .clientTrackingRange(8).updateInterval(2).build("paper_plane")
    );
    public static final DeferredItem<PaperPlaneItem> PAPER_PLANE_ITEM = ITEMS.register("paper_plane", () -> new PaperPlaneItem(new Item.Properties().stacksTo(16)));
    public static final DeferredHolder<SoundEvent, SoundEvent> PAPER_PLANE_WHOOSH = sound("paper_plane_whoosh");
    /** A laser pointer to shine about the cabin, which dazzles whoever it catches in the face. */
    public static final DeferredItem<LaserPointerItem> LASER_POINTER = ITEMS.register("laser_pointer", () -> new LaserPointerItem(new Item.Properties().stacksTo(1)));
    /** Souvenirs: a pilot's cap to wear, and models of the airliner, the zeppelin and the rocket on their stands. */
    public static final DeferredItem<PilotCapItem> PILOT_CAP = ITEMS.register("pilot_cap", () -> new PilotCapItem(new Item.Properties().stacksTo(1)));
    public static final DeferredBlock<SouvenirModelBlock> MODEL_AIRLINER = BLOCKS.registerBlock(
        "model_airliner", SouvenirModelBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredItem<BlockItem> MODEL_AIRLINER_ITEM = ITEMS.registerSimpleBlockItem(MODEL_AIRLINER);
    public static final DeferredBlock<SouvenirModelBlock> MODEL_ZEPPELIN = BLOCKS.registerBlock(
        "model_zeppelin", SouvenirModelBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredItem<BlockItem> MODEL_ZEPPELIN_ITEM = ITEMS.registerSimpleBlockItem(MODEL_ZEPPELIN);
    public static final DeferredBlock<SouvenirModelBlock> MODEL_ROCKET = BLOCKS.registerBlock(
        "model_rocket", SouvenirModelBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.5F).sound(SoundType.METAL).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredItem<BlockItem> MODEL_ROCKET_ITEM = ITEMS.registerSimpleBlockItem(MODEL_ROCKET);

    /**
     * The space posters on the rocket's lift shaft (see {@link SpacePosterBlock}), each a few blocks of thin board that
     * nothing collides with, quick to tear down; and each of them rolled up, to put up by hand (see
     * {@link SpacePosterItem}).
     */
    public static final DeferredBlock<SpacePosterBlock> SPACE_POSTER = BLOCKS.registerBlock(
        "space_poster", SpacePosterBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(0.5F).sound(SpacePosterBlock.PAPER).noCollission().noOcclusion()
            .pushReaction(PushReaction.DESTROY).isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredItem<SpacePosterItem> BLACK_HOLE_POSTER = ITEMS.register(
        "black_hole_poster", () -> new SpacePosterItem(SpacePoster.BLACK_HOLE, new Item.Properties()));
    public static final DeferredItem<SpacePosterItem> SOLAR_SYSTEM_POSTER = ITEMS.register(
        "solar_system_poster", () -> new SpacePosterItem(SpacePoster.SOLAR_SYSTEM, new Item.Properties()));
    public static final DeferredItem<SpacePosterItem> QUASAR_POSTER = ITEMS.register(
        "quasar_poster", () -> new SpacePosterItem(SpacePoster.QUASAR, new Item.Properties()));
    public static final DeferredItem<SpacePosterItem> EARTHRISE_POSTER = ITEMS.register(
        "earthrise_poster", () -> new SpacePosterItem(SpacePoster.EARTHRISE, new Item.Properties()));
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
    /**
     * The rocket crew's space helmet, its gold visor down over the face, and its air breathed in and out, heard in it
     * aboard a rocket in space.
     */
    public static final DeferredItem<SpaceHelmetItem> SPACE_HELMET = ITEMS.register("space_helmet", () -> new SpaceHelmetItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<SoundEvent, SoundEvent> SPACE_HELMET_BREATH = sound("space_helmet_breath");

    /**
     * Business class's side consoles beside its seats, each with its cocktail tray and reading lamp, and the lounge's
     * low round tables: moulded and veneered, as quick to take out as the seats, and nothing spawns on them.
     */
    public static final DeferredBlock<SeatConsoleBlock> SEAT_CONSOLE = BLOCKS.registerBlock(
        "seat_console", properties -> new SeatConsoleBlock(properties, SeatConsoleBlock.Kind.CONSOLE), metal(MapColor.SNOW).noOcclusion()
    );
    public static final DeferredBlock<SeatConsoleBlock> LOUNGE_TABLE = BLOCKS.registerBlock(
        "lounge_table", properties -> new SeatConsoleBlock(properties, SeatConsoleBlock.Kind.TABLE), metal(MapColor.WOOD).noOcclusion()
    );
    public static final DeferredItem<BlockItem> SEAT_CONSOLE_ITEM = ITEMS.registerSimpleBlockItem(SEAT_CONSOLE);
    public static final DeferredItem<BlockItem> LOUNGE_TABLE_ITEM = ITEMS.registerSimpleBlockItem(LOUNGE_TABLE);
    /** How brightly a galley oven's window glows while it cooks, in light levels: a dim warm glow, not a fire's. */
    private static final int OVEN_GLOW = 7;
    /**
     * The galleys' stainless units round the galley's trolley bay: the oven, which cooks as a smoker does and glows
     * while it does, and the cupboards, stowage behind red latches and a worktop over drawers, a chest's worth each.
     */
    public static final DeferredBlock<GalleyOvenBlock> GALLEY_OVEN = BLOCKS.registerBlock(
        "galley_oven", GalleyOvenBlock::new, metal(MapColor.METAL).noOcclusion().lightLevel(state -> state.getValue(GalleyOvenBlock.LIT) ? OVEN_GLOW : 0)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GalleyOvenBlockEntity>> GALLEY_OVEN_ENTITY =
        BLOCK_ENTITIES.register("galley_oven", () -> BlockEntityType.Builder.of(GalleyOvenBlockEntity::new, GALLEY_OVEN.get()).build(null));
    public static final DeferredBlock<GalleyUnitBlock> GALLEY_STOWAGE = BLOCKS.registerBlock(
        "galley_stowage", properties -> new GalleyUnitBlock(properties, GalleyUnitBlock.Kind.STOWAGE), metal(MapColor.METAL).noOcclusion()
    );
    public static final DeferredBlock<GalleyUnitBlock> GALLEY_WORKTOP = BLOCKS.registerBlock(
        "galley_worktop", properties -> new GalleyUnitBlock(properties, GalleyUnitBlock.Kind.WORKTOP), metal(MapColor.METAL).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GalleyUnitBlockEntity>> GALLEY_UNIT_ENTITY =
        BLOCK_ENTITIES.register("galley_unit", () -> BlockEntityType.Builder.of(GalleyUnitBlockEntity::new, GALLEY_STOWAGE.get(), GALLEY_WORKTOP.get()).build(null));
    public static final DeferredItem<BlockItem> GALLEY_OVEN_ITEM = ITEMS.registerSimpleBlockItem(GALLEY_OVEN);
    public static final DeferredItem<BlockItem> GALLEY_STOWAGE_ITEM = ITEMS.registerSimpleBlockItem(GALLEY_STOWAGE);
    public static final DeferredItem<BlockItem> GALLEY_WORKTOP_ITEM = ITEMS.registerSimpleBlockItem(GALLEY_WORKTOP);
    /** How long a planter takes to break: a suitcase's while, not an aluminium panel's. */
    private static final float PLANTER_STRENGTH = 0.8F;
    /**
     * The cabin's slim white planters, greenery, flowers or bamboo in each: a light box, quick to pick up, that sounds
     * like the plants in it, and nothing spawns on it.
     */
    public static final DeferredBlock<PlanterBlock> PLANTER = BLOCKS.registerBlock(
        "planter", PlanterBlock::new, metal(MapColor.PLANT).strength(PLANTER_STRENGTH).sound(SoundType.AZALEA).noOcclusion()
    );
    public static final DeferredItem<BlockItem> PLANTER_ITEM = ITEMS.registerSimpleBlockItem(PLANTER);
    /** How brightly a display's lit shelves glow, in light levels: enough to show off what is on them. */
    private static final int SHELF_GLOW = 6;
    /**
     * The lounge's bar: its counter, its stools, Create's seats underneath and as soft as they sound, and its back bar,
     * a cabinet with lit shelves of bottles on it.
     */
    public static final DeferredBlock<BarCounterBlock> BAR_COUNTER = BLOCKS.registerBlock(
        "bar_counter", BarCounterBlock::new, metal(MapColor.SNOW).noOcclusion()
    );
    public static final DeferredBlock<BarStoolBlock> BAR_STOOL = BLOCKS.registerBlock(
        "bar_stool", BarStoolBlock::new, metal(MapColor.COLOR_RED).sound(SoundType.WOOL).noOcclusion().pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredBlock<DisplayShelfBlock> BACK_BAR = BLOCKS.registerBlock(
        "back_bar", DisplayShelfBlock::new,
        metal(MapColor.COLOR_BROWN).noOcclusion().lightLevel(state -> state.getValue(DisplayShelfBlock.TIER) == DisplayShelfBlock.Tier.SHELVES ? SHELF_GLOW : 0)
    );
    public static final DeferredItem<BlockItem> BAR_COUNTER_ITEM = ITEMS.registerSimpleBlockItem(BAR_COUNTER);
    public static final DeferredItem<BlockItem> BAR_STOOL_ITEM = ITEMS.registerSimpleBlockItem(BAR_STOOL);
    public static final DeferredItem<BlockItem> BACK_BAR_ITEM = ITEMS.registerSimpleBlockItem(BACK_BAR);
    /**
     * The duty-free kiosk's display, a cabinet with a lit glass showcase of perfumes and chocolates on it, and its
     * striped awning on a steel frame, hung on steel rods.
     */
    public static final DeferredBlock<DisplayShelfBlock> DUTY_FREE_DISPLAY = BLOCKS.registerBlock(
        "duty_free_display", DisplayShelfBlock::new,
        metal(MapColor.COLOR_BLUE).noOcclusion().lightLevel(state -> state.getValue(DisplayShelfBlock.TIER) == DisplayShelfBlock.Tier.SHELVES ? SHELF_GLOW : 0)
    );
    public static final DeferredBlock<AwningBlock> AWNING = BLOCKS.registerBlock(
        "awning", AwningBlock::new, metal(MapColor.COLOR_LIGHT_GREEN).sound(SoundType.WOOL).noOcclusion()
    );
    public static final DeferredItem<BlockItem> DUTY_FREE_DISPLAY_ITEM = ITEMS.registerSimpleBlockItem(DUTY_FREE_DISPLAY);
    public static final DeferredItem<BlockItem> AWNING_ITEM = ITEMS.registerSimpleBlockItem(AWNING);

    /**
     * The zeppelin's airframe: the silver fabric its cover and fins are made of, as the Hindenburg's cotton was doped
     * with aluminium; the cover cut to the hull's curve, placed only when a zeppelin is built, which gives back fabric;
     * the blue duralumin girders of its rings and struts; and the open grating of its catwalks, seen through as a real
     * one is. Nothing spawns on any of them, so the hull's great dark inside stays empty.
     */
    public static final DeferredBlock<Block> ZEPPELIN_FABRIC = BLOCKS.registerSimpleBlock("zeppelin_fabric", fabric());
    public static final DeferredBlock<ShellBlock> ZEPPELIN_COVER = BLOCKS.registerBlock(
        "zeppelin_cover", properties -> new ShellBlock(properties, AerolargeRegistry.ZEPPELIN_FABRIC), fabric()
    );
    /**
     * The rocket's rooms finished inside (see {@link RocketFinish}): her cover where a room sees it, cut to her curve
     * or whole, lined on the faces it sees in that room's finish and painted by her livery outside; and her floors,
     * each finished on top as its room and underneath as the ceiling of the room under it. Placed only when a rocket
     * is built, so no items; the cover gives back fabric, the floor a cabin floor.
     */
    public static final DeferredBlock<RocketLinedCoverBlock> ROCKET_LINED_COVER = BLOCKS.registerBlock(
        "rocket_lined_cover", properties -> new RocketLinedCoverBlock(properties, AerolargeRegistry.ZEPPELIN_FABRIC), fabric()
    );
    public static final DeferredBlock<RocketLiningPanelBlock> ROCKET_LINING_PANEL = BLOCKS.registerBlock(
        "rocket_lining_panel", RocketLiningPanelBlock::new, fabric()
    );
    public static final DeferredBlock<RocketFloorBlock> ROCKET_FLOOR = BLOCKS.registerBlock("rocket_floor", RocketFloorBlock::new, metal(MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> ZEPPELIN_GIRDER = BLOCKS.registerSimpleBlock("zeppelin_girder", metal(MapColor.COLOR_BLUE).noOcclusion());
    /**
     * A festive zeppelin's wreath on its nose (see {@link ZeppelinOptions#FESTIVE}): evergreen garland dotted with red
     * berries and gold tinsel, and the red satin ribbon of its bow. Aerolarge's own, so the zeppelin's assembly takes
     * them along, as it would not vanilla leaves.
     */
    public static final DeferredBlock<Block> FESTIVE_GARLAND = BLOCKS.registerSimpleBlock("festive_garland",
        BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.2F).sound(SoundType.GRASS).noOcclusion());
    public static final DeferredBlock<Block> FESTIVE_BOW = BLOCKS.registerSimpleBlock("festive_bow",
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_RED).strength(0.2F).sound(SoundType.WOOL));
    public static final DeferredBlock<Block> ZEPPELIN_CATWALK = BLOCKS.registerSimpleBlock(
        "zeppelin_catwalk", metal(MapColor.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false)
    );
    /** The hub of an engine car's propeller, a relay that spins the propeller drawn behind it with its engine's thrust. */
    public static final DeferredBlock<PropellerBlock> PROPELLER = BLOCKS.registerBlock("propeller", PropellerBlock::new, metal(MapColor.METAL));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PropellerBlockEntity>> PROPELLER_ENTITY =
        BLOCK_ENTITIES.register("propeller", () -> BlockEntityType.Builder.of(PropellerBlockEntity::new, PROPELLER.get()).build(null));
    /**
     * The control car's hand controls: the rudder wheel and the elevator wheel, ship's wheels on brass pedestals; the
     * engine telegraphs, one for each side's engines; and the burner lever. The autopilot flies by them while it is
     * switched off, and moves them itself while it flies (see {@link dev.aerolarge.autopilot.ZeppelinPilot}).
     */
    public static final DeferredBlock<HelmBlock> RUDDER_WHEEL = BLOCKS.registerBlock(
        "rudder_wheel", HelmBlock::new, metal(MapColor.COLOR_BROWN).sound(SoundType.WOOD).noOcclusion()
    );
    public static final DeferredBlock<HelmBlock> ELEVATOR_WHEEL = BLOCKS.registerBlock(
        "elevator_wheel", HelmBlock::new, metal(MapColor.COLOR_BROWN).sound(SoundType.WOOD).noOcclusion()
    );
    /** The trim wheel, saying where the trim car under the gangway goes (see {@link TrimWinchBlockEntity}). */
    public static final DeferredBlock<HelmBlock> TRIM_WHEEL = BLOCKS.registerBlock(
        "trim_wheel", HelmBlock::new, metal(MapColor.COLOR_BROWN).sound(SoundType.WOOD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HelmBlockEntity>> HELM_ENTITY = BLOCK_ENTITIES.register(
        "helm", () -> BlockEntityType.Builder.of(HelmBlockEntity::new, RUDDER_WHEEL.get(), ELEVATOR_WHEEL.get(), TRIM_WHEEL.get()).build(null)
    );
    public static final DeferredBlock<TelegraphBlock> ENGINE_TELEGRAPH = BLOCKS.registerBlock(
        "engine_telegraph", TelegraphBlock::new, metal(MapColor.GOLD).noOcclusion()
    );
    public static final DeferredBlock<BurnerLeverBlock> BURNER_LEVER = BLOCKS.registerBlock(
        "burner_lever", BurnerLeverBlock::new, metal(MapColor.GOLD).noOcclusion()
    );
    public static final DeferredBlock<LiveryBoardBlock> LIVERY_BOARD = BLOCKS.registerBlock(
        "livery_board", LiveryBoardBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LiveryBoardBlockEntity>> LIVERY_BOARD_ENTITY =
        BLOCK_ENTITIES.register("livery_board", () -> BlockEntityType.Builder.of(LiveryBoardBlockEntity::new, LIVERY_BOARD.get()).build(null));
    public static final DeferredBlock<LiverySelectorBlock> LIVERY_SELECTOR = BLOCKS.registerBlock(
        "livery_selector", LiverySelectorBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LiverySelectorBlockEntity>> LIVERY_SELECTOR_ENTITY =
        BLOCK_ENTITIES.register("livery_selector", () -> BlockEntityType.Builder.of(LiverySelectorBlockEntity::new, LIVERY_SELECTOR.get()).build(null));
    /**
     * The rocket's LED strips round each of her stories, the panel by each story's lift door that sets them, and the
     * console in her cockpit that sets any story or all of them (see {@link dev.aerolarge.airframe.RocketLedStrips}).
     * Placed only by the blueprint, so no items, and they give nothing back.
     */
    public static final DeferredBlock<LedStripBlock> LED_STRIP = BLOCKS.registerBlock(
        "led_strip", LedStripBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(0.3F).sound(SoundType.GLASS).noCollission().noOcclusion().noLootTable()
            .lightLevel(LedStripBlock::light)
    );
    public static final DeferredBlock<LedPanelBlock> LED_PANEL = BLOCKS.registerBlock(
        "led_panel", LedPanelBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().noLootTable()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LedPanelBlockEntity>> LED_PANEL_ENTITY =
        BLOCK_ENTITIES.register("led_panel", () -> BlockEntityType.Builder.of(LedPanelBlockEntity::new, LED_PANEL.get()).build(null));
    public static final DeferredBlock<LedConsoleBlock> LED_CONSOLE = BLOCKS.registerBlock(
        "led_console", LedConsoleBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().noLootTable()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LedConsoleBlockEntity>> LED_CONSOLE_ENTITY =
        BLOCK_ENTITIES.register("led_console", () -> BlockEntityType.Builder.of(LedConsoleBlockEntity::new, LED_CONSOLE.get()).build(null));
    /**
     * The rocket's lab (see {@link dev.aerolarge.airframe.RocketLab}): the glass of her tardigrade's tank, its lowest
     * row mossy and its highest lit; its feeding hatch, whose block entity keeps the creature; the lab's instruments,
     * the vitals screen among them; the pool and pebble beds of the tank's floor; the creature itself, and its purr and
     * the bloop as it curls up. Placed only by the blueprint, so no items, and they give nothing back.
     */
    public static final DeferredBlock<HabitatGlassBlock> HABITAT_GLASS = BLOCKS.registerBlock(
        "habitat_glass", HabitatGlassBlock::new,
        glass().noLootTable().lightLevel(state -> state.getValue(HabitatGlassBlock.ROW) == HabitatGlassBlock.Row.TOP ? HabitatGlassBlock.GLOW : 0)
    );
    public static final DeferredBlock<HabitatHatchBlock> HABITAT_HATCH = BLOCKS.registerBlock("habitat_hatch", HabitatHatchBlock::new, glass().noLootTable());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HabitatHatchBlockEntity>> HABITAT_HATCH_ENTITY =
        BLOCK_ENTITIES.register("habitat_hatch", () -> BlockEntityType.Builder.of(HabitatHatchBlockEntity::new, HABITAT_HATCH.get()).build(null));
    public static final DeferredBlock<LabInstrumentBlock> LAB_INSTRUMENT = BLOCKS.registerBlock(
        "lab_instrument", LabInstrumentBlock::new, metal(MapColor.SNOW).noOcclusion().noLootTable().lightLevel(LabInstrumentBlock::light)
    );
    public static final DeferredBlock<HabitatFloorBlock> HABITAT_FLOOR = BLOCKS.registerBlock(
        "habitat_floor", HabitatFloorBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.PLANT).strength(0.1F).sound(SoundType.MOSS_CARPET).noOcclusion().noLootTable()
            .isValidSpawn(AerolargeRegistry::never)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<Tardigrade>> TARDIGRADE = ENTITY_TYPES.register(
        "tardigrade", () -> EntityType.Builder.<Tardigrade>of(Tardigrade::new, MobCategory.MISC).sized(2.6F, 3.5F).eyeHeight(2.2F)
            .clientTrackingRange(10).fireImmune().build("tardigrade")
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> TARDIGRADE_MRRP = sound("tardigrade_mrrp");
    public static final DeferredHolder<SoundEvent, SoundEvent> TARDIGRADE_BLOOP = sound("tardigrade_bloop");
    /**
     * The lab's food lab (see {@link dev.aerolarge.airframe.LabPlan}): the cultured-meat bioreactor, which grows a
     * Lab-Grown Steak over and over; the hydroponic rack and the algae column glowing beside it; the sample centrifuge
     * on the counters; the glazed partition screening the food lab off; the pipes feeding the machines from the
     * ceiling; the steak; and the sounds of a harvest and of a spin. Placed only by the blueprint, so the blocks have no
     * items, and they give nothing back.
     */
    public static final DeferredBlock<BioreactorBlock> BIOREACTOR = BLOCKS.registerBlock(
        "bioreactor", BioreactorBlock::new, glass().noLootTable().lightLevel(state -> BioreactorBlock.GLOW)
    );
    public static final DeferredBlock<LabGrowerBlock> HYDROPONIC_RACK = BLOCKS.registerBlock(
        "hydroponic_rack", properties -> new LabGrowerBlock(properties, LabGrowerBlock.Kind.HYDROPONIC_RACK),
        metal(MapColor.PLANT).noOcclusion().noLootTable().lightLevel(state -> LabGrowerBlock.RACK_GLOW)
    );
    public static final DeferredBlock<LabGrowerBlock> ALGAE_COLUMN = BLOCKS.registerBlock(
        "algae_column", properties -> new LabGrowerBlock(properties, LabGrowerBlock.Kind.ALGAE_COLUMN),
        glass().noLootTable().lightLevel(state -> LabGrowerBlock.COLUMN_GLOW)
    );
    public static final DeferredBlock<SampleCentrifugeBlock> SAMPLE_CENTRIFUGE = BLOCKS.registerBlock(
        "sample_centrifuge", SampleCentrifugeBlock::new,
        metal(MapColor.SNOW).noOcclusion().noLootTable()
            .lightLevel(state -> state.getValue(SampleCentrifugeBlock.SPINNING) ? SampleCentrifugeBlock.SPIN_GLOW : 0)
    );
    public static final DeferredBlock<LabPartitionBlock> LAB_PARTITION = BLOCKS.registerBlock("lab_partition", LabPartitionBlock::new, glass().noLootTable());
    public static final DeferredBlock<LabPipeBlock> LAB_PIPE = BLOCKS.registerBlock(
        "lab_pipe", LabPipeBlock::new, metal(MapColor.METAL).noOcclusion().noLootTable()
    );
    /** A steak grown in the lab's bioreactor: as filling as a cooked steak, and no cow the worse for it. */
    public static final DeferredItem<Item> LAB_GROWN_STEAK = ITEMS.registerSimpleItem(
        "lab_grown_steak", new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationModifier(0.8F).build())
    );
    public static final DeferredHolder<SoundEvent, SoundEvent> BIOREACTOR_HARVEST = sound("bioreactor_harvest");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_SPIN = sound("centrifuge_spin");
    public static final DeferredHolder<SoundEvent, SoundEvent> CENTRIFUGE_DING = sound("centrifuge_ding");
    /** The rocket's greenhouse and pool as one stands in them: their loops and what is heard now and then over them. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GREENHOUSE_AIR = sound("greenhouse_air");
    public static final DeferredHolder<SoundEvent, SoundEvent> GREENHOUSE_BEE = sound("greenhouse_bee");
    public static final DeferredHolder<SoundEvent, SoundEvent> POOL_WATER = sound("pool_water");
    public static final DeferredHolder<SoundEvent, SoundEvent> POOL_SLAP = sound("pool_slap");
    public static final DeferredBlock<HornCordBlock> HORN_CORD = BLOCKS.registerBlock(
        "horn_cord", HornCordBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.5F).sound(SoundType.WOOL).noOcclusion()
    );
    /** The ship's colours on the Sky Deck: the halyard they are hoisted at and the flagstaff they fly from. */
    public static final DeferredBlock<FlagHalyardBlock> FLAG_HALYARD = BLOCKS.registerBlock(
        "flag_halyard", FlagHalyardBlock::new, metal(MapColor.GOLD).sound(SoundType.WOOD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FlagHalyardBlockEntity>> FLAG_HALYARD_ENTITY =
        BLOCK_ENTITIES.register("flag_halyard", () -> BlockEntityType.Builder.of(FlagHalyardBlockEntity::new, FLAG_HALYARD.get()).build(null));
    public static final DeferredBlock<FlagstaffBlock> FLAGSTAFF = BLOCKS.registerBlock(
        "flagstaff", FlagstaffBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.0F).sound(SoundType.WOOD).noOcclusion()
    );
    /** The halyard's pulley squeaking as the colours run up the staff, and down. */
    public static final DeferredHolder<SoundEvent, SoundEvent> FLAG_HALYARD_SOUND = sound("flag_halyard");
    /** A ship's plaque, engraved with a zeppelin's name and crest when she is christened. */
    public static final DeferredBlock<ShipsPlaqueBlock> SHIPS_PLAQUE = BLOCKS.registerBlock(
        "ships_plaque", ShipsPlaqueBlock::new, metal(MapColor.GOLD).noOcclusion().noCollission()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShipsPlaqueBlockEntity>> SHIPS_PLAQUE_ENTITY =
        BLOCK_ENTITIES.register("ships_plaque", () -> BlockEntityType.Builder.of(ShipsPlaqueBlockEntity::new, SHIPS_PLAQUE.get()).build(null));
    /** The trim car under a zeppelin's gangway, the winch hauling it, and the bubble level showing how she pitches. */
    public static final DeferredBlock<TrimWinchBlock> TRIM_WINCH = BLOCKS.registerBlock(
        "trim_winch", TrimWinchBlock::new, metal(MapColor.METAL).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TrimWinchBlockEntity>> TRIM_WINCH_ENTITY =
        BLOCK_ENTITIES.register("trim_winch", () -> BlockEntityType.Builder.of(TrimWinchBlockEntity::new, TRIM_WINCH.get()).build(null));
    public static final DeferredBlock<Block> TRIM_CAR = BLOCKS.registerSimpleBlock("trim_car", metal(MapColor.COLOR_GRAY).noOcclusion());
    public static final DeferredBlock<BubbleLevelBlock> BUBBLE_LEVEL = BLOCKS.registerBlock(
        "bubble_level", BubbleLevelBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BubbleLevelBlockEntity>> BUBBLE_LEVEL_ENTITY =
        BLOCK_ENTITIES.register("bubble_level", () -> BlockEntityType.Builder.of(BubbleLevelBlockEntity::new, BUBBLE_LEVEL.get()).build(null));
    /**
     * The secret techno room under a zeppelin's gangway: its DJ deck, its hidden hatch, its speakers, floor and walls.
     */
    public static final DeferredBlock<TechnoDeckBlock> TECHNO_DECK = BLOCKS.registerBlock(
        "techno_deck", TechnoDeckBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F).lightLevel(state -> 7).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TechnoDeckBlockEntity>> TECHNO_DECK_ENTITY =
        BLOCK_ENTITIES.register("techno_deck", () -> BlockEntityType.Builder.of(TechnoDeckBlockEntity::new, TECHNO_DECK.get()).build(null));
    public static final DeferredBlock<SecretHatchBlock> SECRET_HATCH = BLOCKS.registerBlock(
        "secret_hatch", SecretHatchBlock::new, metal(MapColor.METAL).noOcclusion().isViewBlocking((state, level, pos) -> false)
    );
    public static final DeferredBlock<SpeakerBlock> SPEAKER = BLOCKS.registerBlock(
        "speaker", SpeakerBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F).sound(SoundType.WOOD)
    );
    public static final DeferredBlock<Block> DANCE_FLOOR = BLOCKS.registerSimpleBlock("dance_floor",
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F).sound(SoundType.GLASS));
    public static final DeferredBlock<Block> CLUB_WALL = BLOCKS.registerSimpleBlock("club_wall",
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.5F).sound(SoundType.WOOL));
    /** The secret techno room's track, a minute of build and drop, looped while anyone is in the room. */
    public static final DeferredHolder<SoundEvent, SoundEvent> TECHNO_TRACK = sound("techno_track");
    /** The Lookout's Glass in a zeppelin's control car, and the ship's bell struck twice as a lookout marks a spot. */
    public static final DeferredBlock<LookoutGlassBlock> LOOKOUT_GLASS = BLOCKS.registerBlock(
        "lookout_glass", LookoutGlassBlock::new, metal(MapColor.GOLD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LookoutGlassBlockEntity>> LOOKOUT_GLASS_ENTITY =
        BLOCK_ENTITIES.register("lookout_glass", () -> BlockEntityType.Builder.of(LookoutGlassBlockEntity::new, LOOKOUT_GLASS.get()).build(null));
    public static final DeferredHolder<SoundEvent, SoundEvent> LOOKOUT_BELL = sound("lookout_bell");
    /** The stand a passenger screen stands on before each armchair on a zeppelin's promenades. */
    public static final DeferredBlock<ScreenStandBlock> SCREEN_STAND = BLOCKS.registerBlock(
        "screen_stand", ScreenStandBlock::new,
        BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F).sound(SoundType.WOOD).noOcclusion()
    );
    /**
     * The water ballast valve at a zeppelin's bow, and the sounds of her take-off on her handlers' lines: the ground
     * officer's whistle, short to hold fast and long to let go, her bow's water pouring out, and his call "Up ship!".
     */
    public static final DeferredBlock<BallastValveBlock> BALLAST_VALVE = BLOCKS.registerBlock(
        "ballast_valve", BallastValveBlock::new, metal(MapColor.GOLD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BallastValveBlockEntity>> BALLAST_VALVE_ENTITY =
        BLOCK_ENTITIES.register("ballast_valve", () -> BlockEntityType.Builder.of(BallastValveBlockEntity::new, BALLAST_VALVE.get()).build(null));
    public static final DeferredHolder<SoundEvent, SoundEvent> HANDLER_WHISTLE_SHORT = sound("handler_whistle_short");
    public static final DeferredHolder<SoundEvent, SoundEvent> HANDLER_WHISTLE_LONG = sound("handler_whistle_long");
    public static final DeferredHolder<SoundEvent, SoundEvent> WATER_BALLAST = sound("water_ballast");
    public static final DeferredHolder<SoundEvent, SoundEvent> UP_SHIP_CALL = sound("up_ship_call");
    /** The searchlights under a zeppelin's control car, lit when switched on, and the handles they are swung from. */
    public static final DeferredBlock<SearchlightBlock> SEARCHLIGHT = BLOCKS.registerBlock(
        "searchlight", SearchlightBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> state.getValue(SearchlightBlock.LIT) ? 15 : 0)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SearchlightBlockEntity>> SEARCHLIGHT_ENTITY =
        BLOCK_ENTITIES.register("searchlight", () -> BlockEntityType.Builder.of(SearchlightBlockEntity::new, SEARCHLIGHT.get()).build(null));
    public static final DeferredBlock<SearchlightHandleBlock> SEARCHLIGHT_HANDLE = BLOCKS.registerBlock(
        "searchlight_handle", SearchlightHandleBlock::new, metal(MapColor.GOLD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SearchlightHandleBlockEntity>> SEARCHLIGHT_HANDLE_ENTITY =
        BLOCK_ENTITIES.register("searchlight_handle", () -> BlockEntityType.Builder.of(SearchlightHandleBlockEntity::new, SEARCHLIGHT_HANDLE.get()).build(null));
    /** A zeppelin's speed boost button, which runs her engines full ahead a while, and the roar as it does. */
    public static final DeferredBlock<BoostButtonBlock> BOOST_BUTTON = BLOCKS.registerBlock(
        "boost_button",
        properties -> new BoostButtonBlock(AIRLINER, properties),
        BlockBehaviour.Properties.of().noCollission().strength(0.5F).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoostButtonBlockEntity>> BOOST_BUTTON_ENTITY =
        BLOCK_ENTITIES.register("boost_button", () -> BlockEntityType.Builder.of(BoostButtonBlockEntity::new, BOOST_BUTTON.get()).build(null));
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEED_BOOST = sound("speed_boost");
    /**
     * The sound pass's sounds: a zeppelin's engine cars droning, her telegraphs' gong and their answer, a ship's wheel
     * turned, a control at its stop, the sky crane's ratchet and latch, the flight deck's clamps, a parachute opening,
     * the airliner's wheels touching down, the trim car rolling, the overhead bins, a searchlight striking up, the
     * boosters cutting out, and the towed banner's rope.
     */
    public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELIN_ENGINE = sound("zeppelin_engine");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH_RING = sound("telegraph_ring");
    public static final DeferredHolder<SoundEvent, SoundEvent> TELEGRAPH_ANSWER = sound("telegraph_answer");
    public static final DeferredHolder<SoundEvent, SoundEvent> HELM_NOTCH = sound("helm_notch");
    public static final DeferredHolder<SoundEvent, SoundEvent> CONTROL_STOP = sound("control_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRANE_RATCHET = sound("crane_ratchet");
    public static final DeferredHolder<SoundEvent, SoundEvent> CRANE_LATCH = sound("crane_latch");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAMP_LOCK = sound("clamp_lock");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAMP_RELEASE = sound("clamp_release");
    public static final DeferredHolder<SoundEvent, SoundEvent> CANOPY_OPEN = sound("canopy_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOUCHDOWN = sound("touchdown");
    public static final DeferredHolder<SoundEvent, SoundEvent> TRIM_CAR_ROLL = sound("trim_car_roll");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIN_OPEN = sound("bin_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIN_CLOSE = sound("bin_close");
    public static final DeferredHolder<SoundEvent, SoundEvent> SEARCHLIGHT_ON = sound("searchlight_on");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOOSTER_CUTOUT = sound("booster_cutout");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOW_ROPE = sound("tow_rope");
    /** A finger squeaking across a misted window, and the autopilot's disconnect warning as it lets go. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GLASS_SQUEAK = sound("glass_squeak");
    public static final DeferredHolder<SoundEvent, SoundEvent> AUTOPILOT_OFF = sound("autopilot_off");
    /** The airliner's landing gear lever thrown, out of its detent and home with a clunk. */
    public static final DeferredHolder<SoundEvent, SoundEvent> GEAR_LEVER_SOUND = sound("gear_lever");
    /** A floodlight under a zeppelin, lit from dusk to dawn and lighting the land under her. */
    public static final DeferredBlock<FloodlightBlock> FLOODLIGHT = BLOCKS.registerBlock(
        "floodlight", FloodlightBlock::new, metal(MapColor.COLOR_BLACK).noOcclusion().lightLevel(state -> state.getValue(FloodlightBlock.LIT) ? 15 : 0)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FloodlightBlockEntity>> FLOODLIGHT_ENTITY =
        BLOCK_ENTITIES.register("floodlight", () -> BlockEntityType.Builder.of(FloodlightBlockEntity::new, FLOODLIGHT.get()).build(null));
    /** A zeppelin's boarding stairs' lit push-button, which folds her boarding stairs away or brings them down. */
    public static final DeferredBlock<BoardingStairsButtonBlock> BOARDING_STAIRS_BUTTON = BLOCKS.registerBlock(
        "boarding_stairs_button",
        properties -> new BoardingStairsButtonBlock(AIRLINER, properties),
        BlockBehaviour.Properties.of().noCollission().strength(0.5F).sound(SoundType.METAL).pushReaction(PushReaction.DESTROY)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BoardingStairsBlockEntity>> BOARDING_STAIRS_ENTITY =
        BLOCK_ENTITIES.register("boarding_stairs_button", () -> BlockEntityType.Builder.of(BoardingStairsBlockEntity::new, BOARDING_STAIRS_BUTTON.get()).build(null));
    /** Zeppelin Post: the franking machine in a zeppelin's control car, and the mail sack it drops to a village. */
    public static final DeferredBlock<FrankingMachineBlock> FRANKING_MACHINE = BLOCKS.registerBlock(
        "franking_machine", FrankingMachineBlock::new, metal(MapColor.COLOR_RED).noOcclusion()
    );
    public static final DeferredBlock<MailSackBlock> MAIL_SACK = BLOCKS.registerBlock(
        "mail_sack", MailSackBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(0.4F).sound(SoundType.WOOL).noOcclusion()
    );
    /** The towed banner's cleat on a zeppelin's control car's back wall, which streams her banner from her tail. */
    public static final DeferredBlock<BannerCleatBlock> BANNER_CLEAT = BLOCKS.registerBlock(
        "banner_cleat", BannerCleatBlock::new, metal(MapColor.GOLD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BannerCleatBlockEntity>> BANNER_CLEAT_ENTITY =
        BLOCK_ENTITIES.register("banner_cleat", () -> BlockEntityType.Builder.of(BannerCleatBlockEntity::new, BANNER_CLEAT.get()).build(null));
    /** The sky crane's winch on the glass floor of a zeppelin's observation lounge, lowering and hoisting its hook. */
    public static final DeferredBlock<CraneWinchBlock> CRANE_WINCH = BLOCKS.registerBlock(
        "crane_winch", CraneWinchBlock::new, metal(MapColor.METAL).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraneWinchBlockEntity>> CRANE_WINCH_ENTITY =
        BLOCK_ENTITIES.register("crane_winch", () -> BlockEntityType.Builder.of(CraneWinchBlockEntity::new, CRANE_WINCH.get()).build(null));
    /** The flying carrier's flight deck on a zeppelin's crown: its plain deck, its white lines, its clamps and lever. */
    public static final DeferredBlock<Block> FLIGHT_DECK = BLOCKS.registerSimpleBlock("flight_deck", metal(MapColor.COLOR_GRAY));
    public static final DeferredBlock<Block> FLIGHT_DECK_STRIPE = BLOCKS.registerSimpleBlock("flight_deck_stripe", metal(MapColor.SNOW));
    public static final DeferredBlock<DeckClampBlock> DECK_CLAMP = BLOCKS.registerBlock("deck_clamp", DeckClampBlock::new, metal(MapColor.COLOR_GRAY));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<DeckClampBlockEntity>> DECK_CLAMP_ENTITY =
        BLOCK_ENTITIES.register("deck_clamp", () -> BlockEntityType.Builder.of(DeckClampBlockEntity::new, DECK_CLAMP.get()).build(null));
    public static final DeferredBlock<CarrierLeverBlock> CARRIER_LEVER = BLOCKS.registerBlock(
        "carrier_lever", CarrierLeverBlock::new, metal(MapColor.COLOR_YELLOW).noOcclusion()
    );
    /** The skywriting board in a zeppelin's control car, whose picture buttons have her fly a shape in smoke. */
    public static final DeferredBlock<SkywritingBoardBlock> SKYWRITING_BOARD = BLOCKS.registerBlock(
        "skywriting_board", SkywritingBoardBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(1.0F).sound(SoundType.WOOD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SkywritingBoardBlockEntity>> SKYWRITING_BOARD_ENTITY =
        BLOCK_ENTITIES.register("skywriting_board", () -> BlockEntityType.Builder.of(SkywritingBoardBlockEntity::new, SKYWRITING_BOARD.get()).build(null));
    /** An engine repeater in a zeppelin's engine car, answering the control car's orders for its engine. */
    public static final DeferredBlock<EngineRepeaterBlock> ENGINE_REPEATER = BLOCKS.registerBlock(
        "engine_repeater", EngineRepeaterBlock::new, metal(MapColor.GOLD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EngineRepeaterBlockEntity>> ENGINE_REPEATER_ENTITY =
        BLOCK_ENTITIES.register("engine_repeater", () -> BlockEntityType.Builder.of(EngineRepeaterBlockEntity::new, ENGINE_REPEATER.get()).build(null));
    /** The picture board choosing what a zeppelin's flank screens show. */
    public static final DeferredBlock<PictureBoardBlock> PICTURE_BOARD = BLOCKS.registerBlock(
        "picture_board", PictureBoardBlock::new, BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(1.0F).sound(SoundType.WOOD).noOcclusion()
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PictureBoardBlockEntity>> PICTURE_BOARD_ENTITY =
        BLOCK_ENTITIES.register("picture_board", () -> BlockEntityType.Builder.of(PictureBoardBlockEntity::new, PICTURE_BOARD.get()).build(null));
    /** The zeppelin's horn and its echo off the ground (see {@link dev.aerolarge.horn.ShipHorn}). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELIN_HORN = sound("zeppelin_horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELIN_HORN_ECHO = sound("zeppelin_horn_echo");
    /** The sound of a zeppelin's hull heard aboard her, and a girder groaning in it (see the client's HullAmbience). */
    public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELIN_HULL = sound("zeppelin_hull");
    public static final DeferredHolder<SoundEvent, SoundEvent> ZEPPELIN_CREAK = sound("zeppelin_creak");
    /** Sleigh bells, rung from a festive zeppelin's engines as they turn (see the client's SleighBells). */
    public static final DeferredHolder<SoundEvent, SoundEvent> SLEIGH_BELLS = sound("sleigh_bells");
    public static final DeferredItem<BlockItem> ZEPPELIN_FABRIC_ITEM = ITEMS.registerSimpleBlockItem(ZEPPELIN_FABRIC);
    public static final DeferredItem<BlockItem> ZEPPELIN_GIRDER_ITEM = ITEMS.registerSimpleBlockItem(ZEPPELIN_GIRDER);
    public static final DeferredItem<BlockItem> ZEPPELIN_CATWALK_ITEM = ITEMS.registerSimpleBlockItem(ZEPPELIN_CATWALK);
    public static final DeferredItem<BlockItem> PROPELLER_ITEM = ITEMS.registerSimpleBlockItem(PROPELLER);
    public static final DeferredItem<BlockItem> RUDDER_WHEEL_ITEM = ITEMS.registerSimpleBlockItem(RUDDER_WHEEL);
    public static final DeferredItem<BlockItem> ELEVATOR_WHEEL_ITEM = ITEMS.registerSimpleBlockItem(ELEVATOR_WHEEL);
    public static final DeferredItem<BlockItem> ENGINE_TELEGRAPH_ITEM = ITEMS.registerSimpleBlockItem(ENGINE_TELEGRAPH);
    public static final DeferredItem<BlockItem> BURNER_LEVER_ITEM = ITEMS.registerSimpleBlockItem(BURNER_LEVER);
    public static final DeferredItem<BlockItem> HORN_CORD_ITEM = ITEMS.registerSimpleBlockItem(HORN_CORD);
    public static final DeferredItem<BlockItem> LIVERY_BOARD_ITEM = ITEMS.registerSimpleBlockItem(LIVERY_BOARD);
    public static final DeferredItem<BlockItem> LIVERY_SELECTOR_ITEM = ITEMS.registerSimpleBlockItem(LIVERY_SELECTOR);
    public static final DeferredItem<BlockItem> FLAG_HALYARD_ITEM = ITEMS.registerSimpleBlockItem(FLAG_HALYARD);
    public static final DeferredItem<BlockItem> FLAGSTAFF_ITEM = ITEMS.registerSimpleBlockItem(FLAGSTAFF);
    public static final DeferredItem<BlockItem> SHIPS_PLAQUE_ITEM = ITEMS.registerSimpleBlockItem(SHIPS_PLAQUE);
    public static final DeferredItem<BlockItem> PICTURE_BOARD_ITEM = ITEMS.registerSimpleBlockItem(PICTURE_BOARD);
    public static final DeferredItem<BlockItem> ENGINE_REPEATER_ITEM = ITEMS.registerSimpleBlockItem(ENGINE_REPEATER);
    public static final DeferredItem<BlockItem> TRIM_WHEEL_ITEM = ITEMS.registerSimpleBlockItem(TRIM_WHEEL);
    public static final DeferredItem<BlockItem> TECHNO_DECK_ITEM = ITEMS.registerSimpleBlockItem(TECHNO_DECK);
    public static final DeferredItem<BlockItem> SECRET_HATCH_ITEM = ITEMS.registerSimpleBlockItem(SECRET_HATCH);
    public static final DeferredItem<BlockItem> SPEAKER_ITEM = ITEMS.registerSimpleBlockItem(SPEAKER);
    public static final DeferredItem<BlockItem> DANCE_FLOOR_ITEM = ITEMS.registerSimpleBlockItem(DANCE_FLOOR);
    public static final DeferredItem<BlockItem> CLUB_WALL_ITEM = ITEMS.registerSimpleBlockItem(CLUB_WALL);
    public static final DeferredItem<BlockItem> LOOKOUT_GLASS_ITEM = ITEMS.registerSimpleBlockItem(LOOKOUT_GLASS);
    public static final DeferredItem<BlockItem> SCREEN_STAND_ITEM = ITEMS.registerSimpleBlockItem(SCREEN_STAND);
    public static final DeferredItem<BlockItem> BALLAST_VALVE_ITEM = ITEMS.registerSimpleBlockItem(BALLAST_VALVE);
    public static final DeferredItem<BlockItem> SEARCHLIGHT_ITEM = ITEMS.registerSimpleBlockItem(SEARCHLIGHT);
    public static final DeferredItem<BlockItem> FLOODLIGHT_ITEM = ITEMS.registerSimpleBlockItem(FLOODLIGHT);
    public static final DeferredItem<BlockItem> SEARCHLIGHT_HANDLE_ITEM = ITEMS.registerSimpleBlockItem(SEARCHLIGHT_HANDLE);
    public static final DeferredItem<BlockItem> SKYWRITING_BOARD_ITEM = ITEMS.registerSimpleBlockItem(SKYWRITING_BOARD);
    public static final DeferredItem<BlockItem> FRANKING_MACHINE_ITEM = ITEMS.registerSimpleBlockItem(FRANKING_MACHINE);
    public static final DeferredItem<MailSackItem> MAIL_SACK_ITEM = ITEMS.register("mail_sack", () -> new MailSackItem(MAIL_SACK.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> BANNER_CLEAT_ITEM = ITEMS.registerSimpleBlockItem(BANNER_CLEAT);
    public static final DeferredItem<BlockItem> CRANE_WINCH_ITEM = ITEMS.registerSimpleBlockItem(CRANE_WINCH);
    public static final DeferredItem<BlockItem> FLIGHT_DECK_ITEM = ITEMS.registerSimpleBlockItem(FLIGHT_DECK);
    public static final DeferredItem<BlockItem> FLIGHT_DECK_STRIPE_ITEM = ITEMS.registerSimpleBlockItem(FLIGHT_DECK_STRIPE);
    public static final DeferredItem<BlockItem> DECK_CLAMP_ITEM = ITEMS.registerSimpleBlockItem(DECK_CLAMP);
    public static final DeferredItem<BlockItem> CARRIER_LEVER_ITEM = ITEMS.registerSimpleBlockItem(CARRIER_LEVER);
    public static final DeferredItem<BlockItem> BOARDING_STAIRS_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(BOARDING_STAIRS_BUTTON);
    public static final DeferredItem<BlockItem> BOOST_BUTTON_ITEM = ITEMS.registerSimpleBlockItem(BOOST_BUTTON);
    public static final DeferredItem<BlockItem> TRIM_WINCH_ITEM = ITEMS.registerSimpleBlockItem(TRIM_WINCH);
    public static final DeferredItem<BlockItem> TRIM_CAR_ITEM = ITEMS.registerSimpleBlockItem(TRIM_CAR);
    public static final DeferredItem<BlockItem> BUBBLE_LEVEL_ITEM = ITEMS.registerSimpleBlockItem(BUBBLE_LEVEL);
    /** Broken on a zeppelin's nose, christens her with the name it was given at an anvil. */
    public static final DeferredItem<ChristeningBottleItem> CHRISTENING_BOTTLE = ITEMS.register(
        "christening_bottle", () -> new ChristeningBottleItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON))
    );
    public static final DeferredItem<BlockItem> FESTIVE_GARLAND_ITEM = ITEMS.registerSimpleBlockItem(FESTIVE_GARLAND);
    public static final DeferredItem<BlockItem> FESTIVE_BOW_ITEM = ITEMS.registerSimpleBlockItem(FESTIVE_BOW);

    /**
     * Creative only: there is no recipe for laying down a few tens of thousands of blocks at once. Each shows a
     * miniature of what it builds by the souvenir model's body's middle, so many sixteenths up its block.
     */
    public static final DeferredItem<AirframeBlueprintItem> AIRLINER_BLUEPRINT = ITEMS.register(
        "airliner_blueprint", () -> new AirframeBlueprintItem(() -> AirframeLayout.create(AerolargeConfig.SCALE.get(), false),
            () -> MODEL_AIRLINER.get().defaultBlockState(), AIRLINER_MODEL_MIDDLE, "item.aerolarge.airliner_blueprint.tooltip",
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );
    /** The same airliner with a pool, a gym hall and a lounge already fitted into the cabin. */
    public static final DeferredItem<AirframeBlueprintItem> FURNISHED_AIRLINER_BLUEPRINT = ITEMS.register(
        "furnished_airliner_blueprint", () -> new AirframeBlueprintItem(() -> AirframeLayout.create(AerolargeConfig.SCALE.get(), true),
            () -> MODEL_AIRLINER.get().defaultBlockState(), AIRLINER_MODEL_MIDDLE, "item.aerolarge.furnished_airliner_blueprint.tooltip",
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );
    /** The Hindenburg-sized zeppelin, always at its own size. */
    public static final DeferredItem<AirframeBlueprintItem> ZEPPELIN_BLUEPRINT = ITEMS.register(
        "zeppelin_blueprint", () -> new AirframeBlueprintItem(() -> ZeppelinLayout.create(ZeppelinOptions.PLAIN), () -> MODEL_ZEPPELIN.get().defaultBlockState(),
            ZEPPELIN_MODEL_MIDDLE, "item.aerolarge.zeppelin_blueprint.tooltip", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );
    /** The same zeppelin with its decks furnished: cabins, a galley, a dining room, a lounge and promenades. */
    public static final DeferredItem<AirframeBlueprintItem> FURNISHED_ZEPPELIN_BLUEPRINT = ITEMS.register(
        "furnished_zeppelin_blueprint", () -> new AirframeBlueprintItem(() -> ZeppelinLayout.create(ZeppelinOptions.FURNISHED),
            () -> MODEL_ZEPPELIN.get().defaultBlockState(), ZEPPELIN_MODEL_MIDDLE, "item.aerolarge.furnished_zeppelin_blueprint.tooltip",
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );
    /**
     * The furnished zeppelin dressed for Christmas: snow on its crown, red bands at its rings, green fins, red and
     * green gas cells, fairy lights, a wreath on its nose, a star on its mast and sleigh bells with its engines.
     */
    public static final DeferredItem<AirframeBlueprintItem> FESTIVE_ZEPPELIN_BLUEPRINT = ITEMS.register(
        "festive_zeppelin_blueprint", () -> new AirframeBlueprintItem(() -> ZeppelinLayout.create(ZeppelinOptions.FESTIVE),
            () -> MODEL_ZEPPELIN.get().defaultBlockState(), ZEPPELIN_MODEL_MIDDLE, "item.aerolarge.festive_zeppelin_blueprint.tooltip",
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );
    /** The zeppelin at half the Hindenburg's size, plain, furnished and dressed for Christmas. */
    public static final DeferredItem<AirframeBlueprintItem> HALF_ZEPPELIN_BLUEPRINT = halfZeppelinBlueprint("half_zeppelin_blueprint", ZeppelinOptions.PLAIN);
    public static final DeferredItem<AirframeBlueprintItem> HALF_FURNISHED_ZEPPELIN_BLUEPRINT =
        halfZeppelinBlueprint("half_furnished_zeppelin_blueprint", ZeppelinOptions.FURNISHED);
    public static final DeferredItem<AirframeBlueprintItem> HALF_FESTIVE_ZEPPELIN_BLUEPRINT =
        halfZeppelinBlueprint("half_festive_zeppelin_blueprint", ZeppelinOptions.FESTIVE);
    /** The rocket, as tall as the airliner is long, standing on its fins. */
    public static final DeferredItem<AirframeBlueprintItem> ROCKET_BLUEPRINT = ITEMS.register(
        "rocket_blueprint", () -> new AirframeBlueprintItem(RocketLayout::create, () -> MODEL_ROCKET.get().defaultBlockState(), ROCKET_MODEL_MIDDLE,
            "item.aerolarge.rocket_blueprint.tooltip", new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );
    /** An airfield's control tower, its radio, radar and transponder in the glass cab on top. */
    public static final DeferredItem<ControlTowerBlueprintItem> CONTROL_TOWER_BLUEPRINT = ITEMS.register(
        "control_tower_blueprint", () -> new ControlTowerBlueprintItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );
    /** A runway for the airliner, twice as long as it needs to take off. */
    public static final DeferredItem<RunwayBlueprintItem> RUNWAY_BLUEPRINT = ITEMS.register(
        "runway_blueprint", () -> new RunwayBlueprintItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    /** A blueprint for a half-size zeppelin built with these options, its tooltip under its own name's. */
    private static DeferredItem<AirframeBlueprintItem> halfZeppelinBlueprint(String name, ZeppelinOptions options) {
        return ITEMS.register(name, () -> new AirframeBlueprintItem(() -> ZeppelinLayout.create(options.sized(ZeppelinSize.HALF)),
            () -> MODEL_ZEPPELIN.get().defaultBlockState(), ZEPPELIN_MODEL_MIDDLE, "item.aerolarge." + name + ".tooltip",
            new Item.Properties().stacksTo(1).rarity(Rarity.EPIC)));
    }

    // The rocket's rover (see Rover): the door of her bay in her skin, which keeps the bay's block entity working the door,
    // its boom and its winch, the panel that works them, the rover itself, its remote and the rover a remote is paired
    // with, and its sounds: its motor's whine, its beeps on and off, its horn and the warning near the edge of its range.
    public static final DeferredBlock<RoverHatchBlock> ROVER_HATCH = BLOCKS.registerBlock(
        "rover_hatch", RoverHatchBlock::new, metal(MapColor.SNOW).noOcclusion().isSuffocating((state, level, pos) -> false)
            .isViewBlocking((state, level, pos) -> false)
    );
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RoverBayBlockEntity>> ROVER_BAY_ENTITY =
        BLOCK_ENTITIES.register("rover_bay", () -> BlockEntityType.Builder.of(RoverBayBlockEntity::new, ROVER_HATCH.get()).build(null));
    public static final DeferredBlock<RoverPanelBlock> ROVER_PANEL = BLOCKS.registerBlock(
        "rover_panel", RoverPanelBlock::new, metal(MapColor.COLOR_GRAY).noOcclusion().lightLevel(RoverPanelBlock::glow)
    );
    public static final DeferredHolder<EntityType<?>, EntityType<Rover>> ROVER = ENTITY_TYPES.register(
        "rover", () -> EntityType.Builder.<Rover>of(Rover::new, MobCategory.MISC).sized(2.5F, 1.5F).fireImmune()
            .clientTrackingRange(10).updateInterval(1).build("rover")
    );
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> ROVER_PAIRING = DATA_COMPONENTS.registerComponentType(
        "rover_pairing", builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC)
    );
    public static final DeferredItem<RoverRemoteItem> ROVER_REMOTE = ITEMS.register("rover_remote", () -> new RoverRemoteItem(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<SoundEvent, SoundEvent> ROVER_MOTOR = sound("rover_motor");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROVER_START = sound("rover_start");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROVER_STOP = sound("rover_stop");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROVER_HORN = sound("rover_horn");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROVER_RANGE = sound("rover_range");

    /** A section of the creative tab, under a banner of its own: its name ("itemGroup.aerolarge.<name>") and items. */
    public record TabSection(String name, List<? extends Supplier<? extends Item>> items) {
    }

    /** The creative tab's sections, in order (see {@link dev.aerolarge.client.TabBanners}). */
    public static final List<TabSection> TAB_SECTIONS = List.of(
        new TabSection("blueprints", List.of(AIRLINER_BLUEPRINT, FURNISHED_AIRLINER_BLUEPRINT, ZEPPELIN_BLUEPRINT, FURNISHED_ZEPPELIN_BLUEPRINT, FESTIVE_ZEPPELIN_BLUEPRINT,
            HALF_ZEPPELIN_BLUEPRINT, HALF_FURNISHED_ZEPPELIN_BLUEPRINT, HALF_FESTIVE_ZEPPELIN_BLUEPRINT, ROCKET_BLUEPRINT, CONTROL_TOWER_BLUEPRINT,
            RUNWAY_BLUEPRINT)),
        new TabSection("airframe", List.of(
            AIRFRAME_PANEL_ITEM, AIRFRAME_FAIRING_ITEM, LIVERY_PANEL_ITEM, CABIN_WINDOW_ITEM, COCKPIT_WINDOW_ITEM, CABIN_FLOOR_ITEM, CABIN_LIGHT_ITEM,
            AIRLINER_DOOR_ITEM, ENGINE_INTAKE_ITEM, LANDING_GEAR_ITEM, LANDING_GEAR_TIRE_ITEM, GEAR_BAY_ITEM, NAV_LIGHT_ITEM, AIRSTAIR_CONTROLLER_ITEM,
            AIRSTAIR_CALL_BUTTON_ITEM, AIRSTAIR_BUTTON_ITEM, DOOR_BUTTON_ITEM, SLIDE_CONTROL_ITEM, FLOOR_HATCH_ITEM
        )),
        new TabSection("flight_deck", List.of(
            PILOT_HELMET, SPACE_HELMET, PILOT_SEAT_ITEM, CREW_LOCKER_ITEM,
            THRUST_LEVER_ITEM, INSTRUMENT_PANEL_ITEM, AUTOPILOT_ITEM, TILLER_ITEM, PARKING_BRAKE_ITEM, GEAR_LEVER_ITEM, LASER_SWITCH_ITEM, LASER_TURRET_ITEM, FLIGHT_DISPLAY_ITEM,
            TAIL_CAMERA_DISPLAY_ITEM, ANNOUNCEMENT_PANEL_ITEM, FLIGHT_MAP_ITEM, HANDSET_ITEM, SEATBELT_SWITCH_ITEM, CABIN_LIGHTS_ITEM, WINDOW_SHADES_ITEM, JUMP_SWITCH_ITEM,
            DROP_RELEASE_ITEM, SELF_DESTRUCT_BUTTON_ITEM
        )),
        new TabSection("cabin", List.of(
            SEATBELT_SIGN_ITEM, LIGHT_STRIP_ITEM, PENDANT_LIGHT_ITEM, LIGHT_CABLE_ITEM, HALO_LIGHT_ITEM, WALL_LIGHT_ITEM, STARLIGHT_PANEL_ITEM, OVERHEAD_BIN_ITEM,
            ECONOMY_SEAT_ITEM, BUSINESS_SEAT_ITEM, SEAT_CONSOLE_ITEM, LOUNGE_TABLE_ITEM, PASSENGER_SCREEN_ITEM, CABIN_CARPET_ITEM, AISLE_CARPET_ITEM,
            GALLEY_ITEM, GALLEY_OVEN_ITEM, GALLEY_STOWAGE_ITEM, GALLEY_WORKTOP_ITEM, BAR_COUNTER_ITEM, BAR_STOOL_ITEM, BACK_BAR_ITEM,
            TROLLEY_ITEM, CHICKEN_MEAL, PASTA_MEAL, PRETZELS, ICE_CREAM, LAB_GROWN_STEAK, AIRLINE_COFFEE, COFFEE_MACHINE_ITEM, ESPRESSO, CAPPUCCINO, MOCHA, BABYCINO,
            PLANTER_ITEM, DUTY_FREE_COUNTER_ITEM, DUTY_FREE_DISPLAY_ITEM, AWNING_ITEM,
            PAPER_PLANE_ITEM, LASER_POINTER, PILOT_CAP, MODEL_AIRLINER_ITEM, MODEL_ZEPPELIN_ITEM, MODEL_ROCKET_ITEM, CABIN_CURTAIN_ITEM, CABIN_PARTITION_ITEM, CABIN_ARCH_ITEM,
            BULKHEAD_SIGN_ITEM, WC_SIGN_ITEM, OVERHEAD_WC_SIGN_ITEM,
            EXIT_SIGN_ITEM, CABIN_STAIRS_ITEM, CABIN_LANDING_ITEM, CABIN_RAIL_ITEM, VACUUM_TOILET_ITEM, LAVATORY_BASIN_ITEM,
            SECRET_BOOKSHELF_ITEM, BALL_PIT_ITEM, FAIRY_LIGHTS_ITEM, BUNK_ITEM, BEAN_BAG_ITEM, DISCO_BALL_ITEM,
            SUITCASE_ITEM, GOLDEN_SUITCASE_ITEM, RUBBER_DUCK_ITEM,
            BLACK_HOLE_POSTER, SOLAR_SYSTEM_POSTER, QUASAR_POSTER, EARTHRISE_POSTER,
            CARGO_NET_ITEM, CARGO_FLOOR_ITEM, BALL_MAT_ITEM, ULD_CONTAINER_ITEM
        )),
        new TabSection("gym", List.of(
            GYM_FLOOR_ITEM, GYM_MAT_ITEM, BASKETBALL_ITEM, HOOP_ITEM, GLASS_BACKBOARD_ITEM, SCOREBOARD_ITEM, BALL_RACK_ITEM,
            HIDE_AND_SEEK_BUTTON_ITEM, WALL_BARS_ITEM, CLIMBING_ROPE_ITEM, GYM_RINGS_ITEM, GYM_BENCH_ITEM, SPRINGBOARD_ITEM,
            POOL_DECK_TILE_ITEM, POOL_MOSAIC_ITEM, POOL_COPING_ITEM, POOL_STEPS_ITEM, POOL_LADDER_ITEM
        )),
        new TabSection("zeppelin", List.of(ZEPPELIN_FABRIC_ITEM, ZEPPELIN_GIRDER_ITEM, ZEPPELIN_CATWALK_ITEM, PROPELLER_ITEM, RUDDER_WHEEL_ITEM,
            ELEVATOR_WHEEL_ITEM, ENGINE_TELEGRAPH_ITEM, ENGINE_REPEATER_ITEM, BURNER_LEVER_ITEM, TRIM_WHEEL_ITEM, BUBBLE_LEVEL_ITEM, TRIM_WINCH_ITEM, TRIM_CAR_ITEM,
            TECHNO_DECK_ITEM, SECRET_HATCH_ITEM, SPEAKER_ITEM, DANCE_FLOOR_ITEM, CLUB_WALL_ITEM, LOOKOUT_GLASS_ITEM, SCREEN_STAND_ITEM, BALLAST_VALVE_ITEM, SEARCHLIGHT_ITEM, SEARCHLIGHT_HANDLE_ITEM, FLOODLIGHT_ITEM, SKYWRITING_BOARD_ITEM, FRANKING_MACHINE_ITEM, MAIL_SACK_ITEM, BANNER_CLEAT_ITEM, CRANE_WINCH_ITEM, FLIGHT_DECK_ITEM, FLIGHT_DECK_STRIPE_ITEM, DECK_CLAMP_ITEM, CARRIER_LEVER_ITEM, BOARDING_STAIRS_BUTTON_ITEM, BOOST_BUTTON_ITEM, HORN_CORD_ITEM, LIVERY_BOARD_ITEM, LIVERY_SELECTOR_ITEM, FLAG_HALYARD_ITEM, FLAGSTAFF_ITEM,
            SHIPS_PLAQUE_ITEM, CHRISTENING_BOTTLE, PICTURE_BOARD_ITEM, FESTIVE_GARLAND_ITEM, FESTIVE_BOW_ITEM)),
        new TabSection("drops", List.of(
            JUMP_LIGHT_ITEM, PARACHUTE_RACK_ITEM, PARACHUTE, PAPER_PLANE_BOX_ITEM, LANDING_TARGET_ITEM, SIGNAL_FLARE_ITEM, CARGO_CRATE_ITEM, DROP_BAY_ITEM, ROVER_REMOTE
        )),
        new TabSection("airport", List.of(
            JET_BRIDGE_ITEM, CHECK_IN_DESK_ITEM, BOARDING_PASS, TOWER_RADIO_ITEM, TOWER_RADAR_ITEM, TRANSPONDER_ITEM, MOTION_SENSOR_ITEM, LIFT_PANEL_ITEM,
            FLIGHT_STRIP_BOARD_ITEM, ATIS_SCREEN_ITEM, WIND_SOCK_ITEM
        ))
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

    /** A pool's tile: as tough as stone and as hard underfoot, and nothing spawns on it. */
    private static BlockBehaviour.Properties poolTile(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.5F, 6.0F).sound(SoundType.STONE).isValidSpawn(AerolargeRegistry::never);
    }

    /** A fitting of the gym hall's: quick to take down, small enough to see past, and nothing spawns on it. */
    private static BlockBehaviour.Properties gymFitting(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(1.0F).noOcclusion().pushReaction(PushReaction.DESTROY)
            .isValidSpawn(AerolargeRegistry::never);
    }

    private static boolean never(BlockState state, BlockGetter level, BlockPos pos, EntityType<?> type) {
        return false;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Aerolarge.MOD_ID, name)));
    }

    /** A woven carpet: as quick to take up as vanilla's, and nothing spawns on it. */
    private static BlockBehaviour.Properties carpet() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLUE).strength(0.1F).sound(SoundType.WOOL).ignitedByLava()
            .isValidSpawn(AerolargeRegistry::never);
    }

    /** Doped fabric over a light frame: as quick to cut as wool, and nothing spawns on it. */
    private static BlockBehaviour.Properties fabric() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.8F, 6.0F).sound(SoundType.WOOL).isValidSpawn(AerolargeRegistry::never);
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
        MENU_TYPES.register(modEventBus);
    }
}
