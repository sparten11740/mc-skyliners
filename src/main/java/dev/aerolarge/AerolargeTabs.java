package dev.aerolarge;

import static dev.aerolarge.AerolargeRegistry.*;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Aerolarge's creative tabs, side by side after vanilla's, each holding one kind of thing a player goes looking for
 * and showing a picture of it for those who cannot read its name: the craft and what they are built of (a blueprint),
 * the controls they are flown and worked by (a ship's wheel), the gear and gadgets aboard (the big red button), the
 * cabin's and the rooms' furniture (a seat), food and drink (an ice cream), the leisure rooms (a basketball), lights
 * and decorations (a pendant light) and the airport (a wind sock). Inside a tab the items stand in sections, each under
 * a banner of its own (see {@link dev.aerolarge.client.TabBanners}), related things side by side and variants next to
 * each other. Every item the mod registers stands in exactly one section; the blocks only a blueprint places, or only
 * another block lays, have no item, so they are in none.
 */
public final class AerolargeTabs {
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Aerolarge.MOD_ID);
    /**
     * What each tab's registry name starts with. Where nothing else orders the tabs that come after vanilla's, NeoForge
     * stands them in the order of their registry names, path first, so another mod's tab whose path sorted between two
     * of these, each ordered only after the one before it, would stand between them; starting every path with the
     * mod's id sorts them ahead of nearly every other mod's tab, and so keeps them together.
     */
    private static final String REGISTRY_PREFIX = "aerolarge_";

    /**
     * A section of a tab, under a banner of its own: its name, which gives its banner's lang key
     * ("itemGroup.aerolarge.<name>", so no two sections of any tabs share one), and its items, in order.
     */
    public record Section(String name, List<? extends Supplier<? extends Item>> items) {
        /** The name on the section's banner. */
        public Component bannerName() {
            return Component.translatable("itemGroup.aerolarge." + name);
        }
    }

    /**
     * A tab: its name, which gives its registry name (after {@link #REGISTRY_PREFIX}) and its title's lang key
     * ("itemGroup.aerolarge.<name>"), the item its icon shows, and its sections in order.
     */
    public record Tab(String name, Supplier<? extends Item> icon, List<Section> sections) {
        /** The tab's title, over its items and beside its icon when pointed at. */
        public Component title() {
            return Component.translatable("itemGroup.aerolarge." + name);
        }
    }

    /** The tabs, in the order they stand. */
    public static final List<Tab> ALL = List.of(
        new Tab("aircraft", AIRLINER_BLUEPRINT, List.of(
            new Section("blueprints", List.of(AIRLINER_BLUEPRINT, FURNISHED_AIRLINER_BLUEPRINT, ZEPPELIN_BLUEPRINT, FURNISHED_ZEPPELIN_BLUEPRINT,
                FESTIVE_ZEPPELIN_BLUEPRINT, HALF_ZEPPELIN_BLUEPRINT, HALF_FURNISHED_ZEPPELIN_BLUEPRINT, HALF_FESTIVE_ZEPPELIN_BLUEPRINT, ROCKET_BLUEPRINT)),
            new Section("airframe", List.of(AIRFRAME_PANEL_ITEM, AIRFRAME_FAIRING_ITEM, LIVERY_PANEL_ITEM, CABIN_WINDOW_ITEM, COCKPIT_WINDOW_ITEM,
                CABIN_FLOOR_ITEM, AIRLINER_DOOR_ITEM, FLOOR_HATCH_ITEM, ENGINE_INTAKE_ITEM, LANDING_GEAR_ITEM, LANDING_GEAR_TIRE_ITEM, GEAR_BAY_ITEM)),
            new Section("zeppelin", List.of(ZEPPELIN_FABRIC_ITEM, ZEPPELIN_GIRDER_ITEM, ZEPPELIN_CATWALK_ITEM, PROPELLER_ITEM)),
            new Section("rocket", List.of(STEEL_GIRDER_ITEM, LIFT_GLASS_ITEM, OVAL_WINDOW_ITEM, LIFT_MACHINE_ITEM, LIFT_DOOR_ITEM, LIFT_BOARD_ITEM,
                LIFT_CALL_BUTTON_ITEM))
        )),
        new Tab("controls", RUDDER_WHEEL_ITEM, List.of(
            new Section("flight_deck", List.of(AUTOPILOT_ITEM, PILOT_SEAT_ITEM, THRUST_LEVER_ITEM, INSTRUMENT_PANEL_ITEM, FLIGHT_DISPLAY_ITEM,
                TAIL_CAMERA_DISPLAY_ITEM, FLIGHT_MAP_ITEM, TILLER_ITEM, PARKING_BRAKE_ITEM, GEAR_LEVER_ITEM, HANDSET_ITEM, ANNOUNCEMENT_PANEL_ITEM,
                SEATBELT_SWITCH_ITEM, CABIN_LIGHTS_ITEM, WINDOW_SHADES_ITEM)),
            new Section("rocket_cockpit", List.of(ROCKET_BOOST_BUTTON_ITEM, ROCKET_STABILITY_SWITCH_ITEM, ROCKET_MODE_KNOB_ITEM, ROCKET_ABORT_HANDLE_ITEM,
                ROCKET_LAUNCH_KEY_ITEM)),
            new Section("doors", List.of(DOOR_BUTTON_ITEM, AIRSTAIR_CONTROLLER_ITEM, AIRSTAIR_BUTTON_ITEM, AIRSTAIR_CALL_BUTTON_ITEM, SLIDE_CONTROL_ITEM,
                BOARDING_STAIRS_BUTTON_ITEM, ROCKET_STAIRS_BUTTON_ITEM, AIRLOCK_PANEL_ITEM)),
            new Section("control_car", List.of(RUDDER_WHEEL_ITEM, ELEVATOR_WHEEL_ITEM, TRIM_WHEEL_ITEM, ENGINE_TELEGRAPH_ITEM, ENGINE_REPEATER_ITEM,
                BURNER_LEVER_ITEM, BOOST_BUTTON_ITEM, HORN_CORD_ITEM, BALLAST_VALVE_ITEM, TRIM_WINCH_ITEM, TRIM_CAR_ITEM, BUBBLE_LEVEL_ITEM,
                LOOKOUT_GLASS_ITEM, SEARCHLIGHT_HANDLE_ITEM, SEARCHLIGHT_ITEM))
        )),
        new Tab("gear", SELF_DESTRUCT_BUTTON_ITEM, List.of(
            new Section("crew_kit", List.of(PILOT_HELMET, PILOT_CAP, SPACE_HELMET, CREW_LOCKER_ITEM)),
            new Section("drops", List.of(PARACHUTE, PARACHUTE_RACK_ITEM, JUMP_LIGHT_ITEM, JUMP_SWITCH_ITEM, LANDING_TARGET_ITEM, SIGNAL_FLARE_ITEM,
                PAPER_PLANE_ITEM, PAPER_PLANE_BOX_ITEM, CARGO_CRATE_ITEM, DROP_BAY_ITEM, DROP_RELEASE_ITEM)),
            new Section("gadgets", List.of(SELF_DESTRUCT_BUTTON_ITEM, LASER_SWITCH_ITEM, LASER_TURRET_ITEM, LASER_POINTER, ROVER_REMOTE)),
            new Section("zeppelin_extras", List.of(CHRISTENING_BOTTLE, SHIPS_PLAQUE_ITEM, FLAG_HALYARD_ITEM, FLAGSTAFF_ITEM, LIVERY_BOARD_ITEM,
                LIVERY_SELECTOR_ITEM, PICTURE_BOARD_ITEM, SKYWRITING_BOARD_ITEM, BANNER_CLEAT_ITEM, FRANKING_MACHINE_ITEM, MAIL_SACK_ITEM, CRANE_WINCH_ITEM,
                FLIGHT_DECK_ITEM, FLIGHT_DECK_STRIPE_ITEM, DECK_CLAMP_ITEM, CARRIER_LEVER_ITEM))
        )),
        new Tab("cabin", ECONOMY_SEAT_ITEM, List.of(
            new Section("seats", List.of(ECONOMY_SEAT_ITEM, BUSINESS_SEAT_ITEM, SEAT_CONSOLE_ITEM, PASSENGER_SCREEN_ITEM, SCREEN_STAND_ITEM,
                LOUNGE_TABLE_ITEM, OVERHEAD_BIN_ITEM)),
            new Section("floors_walls", List.of(CABIN_CARPET_ITEM, AISLE_CARPET_ITEM, CABIN_CURTAIN_ITEM, CABIN_PARTITION_ITEM, CABIN_ARCH_ITEM,
                CABIN_STAIRS_ITEM, CABIN_LANDING_ITEM, CABIN_RAIL_ITEM, SHELF_UNIT_ITEM)),
            new Section("signs", List.of(SEATBELT_SIGN_ITEM, EXIT_SIGN_ITEM, BULKHEAD_SIGN_ITEM, WC_SIGN_ITEM, OVERHEAD_WC_SIGN_ITEM, VACUUM_TOILET_ITEM,
                LAVATORY_BASIN_ITEM, LAV_ICE_ITEM)),
            new Section("duty_free", List.of(DUTY_FREE_COUNTER_ITEM, DUTY_FREE_DISPLAY_ITEM, AWNING_ITEM)),
            new Section("hold", List.of(CARGO_FLOOR_ITEM, CARGO_NET_ITEM, BALL_MAT_ITEM, ULD_CONTAINER_ITEM, SUITCASE_ITEM, GOLDEN_SUITCASE_ITEM,
                PALLET_RACK_ITEM, PARCEL_SCANNER_ITEM)),
            new Section("garden", List.of(PLANTER_ITEM, GARDEN_BED_ITEM, SPRINKLER_ITEM, IRRIGATION_VALVE_ITEM, GROW_LIGHT_ITEM)),
            new Section("lab", List.of(BIOREACTOR_ITEM, HYDROPONIC_RACK_ITEM, ALGAE_COLUMN_ITEM, SAMPLE_CENTRIFUGE_ITEM, LAB_INSTRUMENT_ITEM,
                LAB_PARTITION_ITEM, LAB_PIPE_ITEM, HABITAT_GLASS_ITEM, HABITAT_HATCH_ITEM, HABITAT_FLOOR_ITEM))
        )),
        new Tab("food", ICE_CREAM, List.of(
            new Section("galley", List.of(GALLEY_ITEM, GALLEY_OVEN_ITEM, GALLEY_STOWAGE_ITEM, GALLEY_WORKTOP_ITEM, GALLEY_FRIDGE_ITEM, SNACK_MACHINE_ITEM,
                TROLLEY_ITEM, CHICKEN_MEAL, PASTA_MEAL, PRETZELS, ICE_CREAM, LAB_GROWN_STEAK, COFFEE_MACHINE_ITEM, AIRLINE_COFFEE, ESPRESSO, CAPPUCCINO,
                MOCHA, BABYCINO)),
            new Section("bar", List.of(BAR_COUNTER_ITEM, BACK_BAR_ITEM, BAR_STOOL_ITEM, COCKTAIL_TABLE_ITEM, DRINK_TAP_ITEM, STAR_FIZZ, ROBOT_BARTENDER_ITEM,
                NEBULA_FIZZ, ROCKET_FUEL, ZERO_G_FLOAT, SUPERNOVA_SUNRISE, MOON_MILK))
        )),
        new Tab("leisure", BASKETBALL_ITEM, List.of(
            new Section("gym", List.of(BASKETBALL_ITEM, HOOP_ITEM, GLASS_BACKBOARD_ITEM, BALL_RACK_ITEM, SCOREBOARD_ITEM, HIDE_AND_SEEK_BUTTON_ITEM,
                GYM_FLOOR_ITEM, GYM_MAT_ITEM, WALL_BARS_ITEM, CLIMBING_ROPE_ITEM, GYM_RINGS_ITEM, GYM_BENCH_ITEM, SPRINGBOARD_ITEM)),
            new Section("pool", List.of(POOL_MOSAIC_ITEM, POOL_DECK_TILE_ITEM, POOL_COPING_ITEM, POOL_STEPS_ITEM, POOL_LADDER_ITEM, WAVE_MACHINE_ITEM,
                RUBBER_DUCK_ITEM, GIANT_RUBBER_DUCK_ITEM)),
            new Section("club", List.of(TECHNO_DECK_ITEM, SPEAKER_ITEM, DANCE_FLOOR_ITEM, CLUB_WALL_ITEM, NEON_SIGN_ITEM, DISCO_BALL_ITEM, FOG_MACHINE_ITEM,
                SECRET_HATCH_ITEM)),
            new Section("den", List.of(BALL_PIT_ITEM, BEAN_BAG_ITEM, BUNK_ITEM, BUNK_LADDER_ITEM, SECRET_BOOKSHELF_ITEM))
        )),
        new Tab("decor", PENDANT_LIGHT_ITEM, List.of(
            new Section("lights", List.of(PENDANT_LIGHT_ITEM, HALO_LIGHT_ITEM, LIGHT_CABLE_ITEM, LIGHT_STRIP_ITEM, CABIN_LIGHT_ITEM, STARLIGHT_PANEL_ITEM,
                FAIRY_LIGHTS_ITEM, FLOODLIGHT_ITEM, NAV_LIGHT_ITEM, LED_STRIP_ITEM, LED_PANEL_ITEM, LED_CONSOLE_ITEM)),
            new Section("decorations", List.of(MODEL_AIRLINER_ITEM, MODEL_ZEPPELIN_ITEM, MODEL_ROCKET_ITEM, ORRERY_ITEM, BLACK_HOLE_POSTER,
                SOLAR_SYSTEM_POSTER, QUASAR_POSTER, EARTHRISE_POSTER, SHAFT_DECOR_ITEM, GIRDER_DECOR_ITEM, FESTIVE_GARLAND_ITEM, FESTIVE_BOW_ITEM))
        )),
        new Tab("airport", WIND_SOCK_ITEM, List.of(
            new Section("airfield_blueprints", List.of(LAUNCH_PAD_BLUEPRINT, CONTROL_TOWER_BLUEPRINT, RUNWAY_BLUEPRINT)),
            new Section("control_tower", List.of(TOWER_RADIO_ITEM, TOWER_RADAR_ITEM, TRANSPONDER_ITEM, FLIGHT_STRIP_BOARD_ITEM, ATIS_SCREEN_ITEM,
                WIND_SOCK_ITEM, MOTION_SENSOR_ITEM, LIFT_PANEL_ITEM)),
            new Section("terminal", List.of(CHECK_IN_DESK_ITEM, BOARDING_PASS, JET_BRIDGE_ITEM))
        ))
    );

    /** The tabs as registered, in the order of {@link #ALL}. */
    private static final List<DeferredHolder<CreativeModeTab, CreativeModeTab>> REGISTERED = registerAll();

    private AerolargeTabs() {
    }

    static void register(IEventBus modEventBus) {
        TABS.register(modEventBus);
    }

    /** The Aerolarge tab this is, or null if it is vanilla's or another mod's. */
    @Nullable
    public static Tab of(CreativeModeTab tab) {
        for (int i = 0; i < ALL.size(); i++) {
            if (REGISTERED.get(i).get() == tab) {
                return ALL.get(i);
            }
        }
        return null;
    }

    /** Registers each tab after the one before it, so they stand together and in order, the first after vanilla's. */
    private static List<DeferredHolder<CreativeModeTab, CreativeModeTab>> registerAll() {
        List<DeferredHolder<CreativeModeTab, CreativeModeTab>> registered = new ArrayList<>();
        for (Tab tab : ALL) {
            ResourceLocation[] previous = registered.isEmpty() ? new ResourceLocation[0] : new ResourceLocation[]{registered.getLast().getId()};
            registered.add(TABS.register(REGISTRY_PREFIX + tab.name(), () -> build(tab, previous)));
        }
        return List.copyOf(registered);
    }

    private static CreativeModeTab build(Tab tab, ResourceLocation[] previous) {
        return CreativeModeTab.builder()
            .title(tab.title())
            .icon(() -> tab.icon().get().getDefaultInstance())
            .displayItems((parameters, output) -> tab.sections().forEach(section -> section.items().forEach(item -> output.accept(item.get()))))
            .withTabsBefore(previous)
            .build();
    }
}
