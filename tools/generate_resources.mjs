import { mkdirSync, writeFileSync, readFileSync, readdirSync, existsSync } from "node:fs";
import { resolve, dirname } from "node:path";
import { fileURLToPath } from "node:url";

// Run from anywhere: node tools/generate_resources.mjs [vanilla-assets-dir]
// Writes the blockstates, models, loot tables, tags, recipes and the English lang file. The airliner door and the
// call button are vanilla's iron door and stone button retextured, so their files are derived from vanilla's: pass
// the folder the Minecraft client resources jar (build/moddev/artifacts/*-client-extra-*.jar) is extracted to.
// Without it those files are left as they are.
const ROOT = fileURLToPath(new URL("../src/main/resources", import.meta.url));
const VANILLA = process.argv[2];
const withVanilla = VANILLA !== undefined && existsSync(VANILLA);
if (!withVanilla) console.log("No vanilla assets given: leaving the door and call button files as they are");
const ID = "aerolarge";
// How far a model drawn facing north is turned for each horizontal facing.
const yRot = { north: 0, east: 90, south: 180, west: 270 };
// How tall a module of the flight deck's centre console is, in pixels: ConsoleModule.HEIGHT.
const CONSOLE_HEIGHT = 6;

function write(path, value) {
  const file = resolve(ROOT, path);
  mkdirSync(dirname(file), { recursive: true });
  writeFileSync(file, (typeof value === "string" ? value : JSON.stringify(value, null, 2)) + "\n");
}

const cubes = {
  airframe_panel: null,
  livery_panel: null,
  cabin_floor: null,
  engine_intake: null,
  landing_gear: null,
  landing_gear_tire: null,
  cabin_window: "minecraft:translucent",
  cockpit_window: "minecraft:translucent",
  cabin_light: null,
  zeppelin_fabric: null,
  zeppelin_girder: "minecraft:cutout",
  festive_garland: "minecraft:cutout",
  festive_bow: null,
  zeppelin_catwalk: "minecraft:cutout",
  flight_deck: null,
  flight_deck_stripe: null,
  dance_floor: null,
  club_wall: null,
};
// Cubes whose blockstate is written with their other models below, and cubes with faces of their own, written below.
const OWN_BLOCKSTATES = new Set(["cabin_light"]);
const OWN_MODELS = new Set(["cabin_floor", "airframe_panel", "zeppelin_fabric", "flight_deck_stripe"]);
for (const [name, renderType] of Object.entries(cubes)) {
  if (!OWN_BLOCKSTATES.has(name)) {
    write(`assets/${ID}/blockstates/${name}.json`, { variants: { "": { model: `${ID}:block/${name}` } } });
  }
  if (!OWN_MODELS.has(name)) {
    const model = { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/${name}` } };
    if (renderType) model.render_type = renderType;
    write(`assets/${ID}/models/block/${name}.json`, model);
  }
  write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}` });
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:${name}` }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/${name}`,
  });
}

// The cabin floor: carpet on top, and finished where a floor is seen from elsewhere, as an airliner's is: underneath,
// where the upper deck's floor is the main deck's ceiling and the main deck's the hold's, white ceiling panels; round
// its sides, where a gallery or a stairwell shows its edge, the carpet's edge over a steel trim and a white fascia.
write(`assets/${ID}/models/block/cabin_floor.json`, {
  parent: "minecraft:block/cube_bottom_top",
  textures: {
    top: `${ID}:block/cabin_floor`, bottom: `${ID}:block/cabin_floor_under`, side: `${ID}:block/cabin_floor_edge`, particle: `${ID}:block/cabin_floor`,
  },
});

// The cabin light: the cube above as the dimmer has it bright or dimmed, and at night, unless kept on (as the
// washrooms' and the crew room's are), a soft blue diffuser glowing whatever the light around, as the strips and the
// pendants glow at night.
{
  const faces = Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, {
    texture: "#all", cullface: face, neoforge_data: { block_light: 15, sky_light: 15 },
  }]));
  write(`assets/${ID}/models/block/cabin_light_night.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    textures: { all: `${ID}:block/cabin_light_night`, particle: `${ID}:block/cabin_light` },
    elements: [{ from: [0, 0, 0], to: [16, 16, 16], shade: false, faces }],
  });
  // Red for a door open in flight (see Brightness.ALARM), whatever the dimmer has it at, glowing as at night.
  write(`assets/${ID}/models/block/cabin_light_alarm.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    textures: { all: `${ID}:block/cabin_light_alarm`, particle: `${ID}:block/cabin_light` },
    elements: [{ from: [0, 0, 0], to: [16, 16, 16], shade: false, faces }],
  });
  write(`assets/${ID}/blockstates/cabin_light.json`, {
    variants: {
      ...Object.fromEntries(["bright", "dim", "night"].flatMap((level) => [false, true].map((keptOn) =>
        [`alarm=false,brightness=${level},kept_on=${keptOn}`, { model: `${ID}:block/cabin_light${level === "night" && !keptOn ? "_night" : ""}` }]))),
      "alarm=true": { model: `${ID}:block/cabin_light_alarm` },
    },
  });
}

// The door: vanilla's iron door blockstate and models, retextured, with a cutout for the porthole.
if (withVanilla) {
  const doorState = readFileSync(`${VANILLA}/assets/minecraft/blockstates/iron_door.json`, "utf8")
    .replaceAll("minecraft:block/iron_door", `${ID}:block/airliner_door`);
  write(`assets/${ID}/blockstates/airliner_door.json`, doorState.trimEnd());
  for (const file of readdirSync(`${VANILLA}/assets/minecraft/models/block`).filter((f) => f.startsWith("iron_door"))) {
    const model = JSON.parse(readFileSync(`${VANILLA}/assets/minecraft/models/block/${file}`, "utf8"));
    model.textures = { bottom: `${ID}:block/airliner_door_bottom`, top: `${ID}:block/airliner_door_top` };
    model.render_type = "minecraft:cutout";
    write(`assets/${ID}/models/block/${file.replace("iron_door", "airliner_door")}`, model);
  }
  const doorLoot = readFileSync(`${VANILLA}/data/minecraft/loot_table/blocks/iron_door.json`, "utf8")
    .replaceAll("minecraft:iron_door", `${ID}:airliner_door`)
    .replace("minecraft:blocks/iron_door", `${ID}:blocks/airliner_door`);
  write(`data/${ID}/loot_table/blocks/airliner_door.json`, doorLoot.trimEnd());
}
write(`assets/${ID}/models/item/airliner_door.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/airliner_door` } });

// The plug doors in the hull's sides: drawn whole by their renderer, panel and all (see PlugDoorRenderer), so the
// model only gives the particles; placed only by the blueprints, so no item and no loot. Part of the airframe, in
// its tags (see cubes), but no vanilla door: it opens for villagers itself.
write(`assets/${ID}/blockstates/plug_door.json`, { variants: { "": { model: `${ID}:block/plug_door` } } });
write(`assets/${ID}/models/block/plug_door.json`, { textures: { particle: `${ID}:block/airframe_panel` } });
cubes.plug_door = null;
// The plug door's pneumatic hiss, the clunk of its latches, and the alarm while one stands open in flight (see
// tools/generate_sounds.mjs).
function plugDoorSounds() {
  const sounds = Object.fromEntries(["plug_door_hiss", "plug_door_latch"].map((name) =>
    [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
  return { ...sounds, door_alarm: heardAboard("door_alarm") };
}
function plugDoorLang() {
  return {
    "block.aerolarge.plug_door": "Plug Door",
    "subtitles.aerolarge.plug_door_hiss": "Door hisses",
    "subtitles.aerolarge.plug_door_latch": "Door latches",
    "subtitles.aerolarge.door_alarm": "Door alarm wails",
  };
}

// The cockpit door: drawn whole by its renderer (see CockpitDoorRenderer), so the model only gives the particles;
// placed only by the blueprints, so no item and no loot. Part of the airframe, in its tags (see cubes); no vanilla
// door, so villagers do not open it.
write(`assets/${ID}/blockstates/cockpit_door.json`, { variants: { "": { model: `${ID}:block/cockpit_door` } } });
write(`assets/${ID}/models/block/cockpit_door.json`, { textures: { particle: `${ID}:block/cockpit_door_edge` } });
cubes.cockpit_door = null;
// Locked from the flight deck (see CockpitDoorBlockEntity), it says so to whoever locks it or tries it from the aisle.
function cockpitDoorLang() {
  return {
    "block.aerolarge.cockpit_door": "Cockpit Door",
    "message.aerolarge.cockpit_door.locked": "Cockpit door locked: it opens only from the flight deck",
    "message.aerolarge.cockpit_door.unlocked": "Cockpit door unlocked",
    "message.aerolarge.cockpit_door.locked_out": "Locked from the flight deck",
  };
}

// The airstair controller is a plain cube; the call button is vanilla's stone button, retextured.
{
  const name = "airstair_controller";
  write(`assets/${ID}/blockstates/${name}.json`, { variants: { "powered=false": { model: `${ID}:block/${name}` }, "powered=true": { model: `${ID}:block/${name}` } } });
  write(`assets/${ID}/models/block/${name}.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/${name}` } });
  write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}` });
  cubes[name] = null;
}
{
  const name = "airstair_call_button";
  if (withVanilla) {
    write(`assets/${ID}/blockstates/${name}.json`, readFileSync(`${VANILLA}/assets/minecraft/blockstates/stone_button.json`, "utf8")
      .replaceAll("minecraft:block/stone_button", `${ID}:block/${name}`).trimEnd());
    for (const suffix of ["", "_pressed", "_inventory"]) {
      const model = JSON.parse(readFileSync(`${VANILLA}/assets/minecraft/models/block/stone_button${suffix}.json`, "utf8"));
      model.textures = { texture: `${ID}:block/${name}` };
      write(`assets/${ID}/models/block/${name}${suffix}.json`, model);
    }
  }
  write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}_inventory` });
  cubes[name] = null;
}
// The lit push-buttons (see LitButtonBlock), drawn as vanilla draws a button, lying on the floor and turned by the
// blockstate as vanilla's buttons are: a dark steel bezel a pixel thick, the airstair button's, and on it the square
// cap, glowing whatever the light around, a pixel and a half proud, or half a pixel pressed in. The cap's face shows the
// middle six pixels of its texture, its picture, the right way up for someone standing at the button's foot; on a
// wall, turned up off the floor, it would stand on its head and back to front, so a wall button's face is turned half
// round to read the right way up. In the hand, the button upright, its cap to the north. A button may wear another's
// cap, its picture and all.
function litButton(name, capName = name) {
  const block = (texture) => `${ID}:block/${texture}`;
  const bezel = block("airstair_button_bezel");
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  const cap = (from, to, turned) => ({
    from, to, shade: false,
    faces: Object.fromEntries(["north", "south", "east", "west", "up"].map((face) =>
      [face, { texture: "#cap", ...(face === "up" && turned ? { rotation: 180 } : {}), ...lit }])),
  });
  for (const wall of [false, true]) {
    for (const pressed of [false, true]) {
      write(`assets/${ID}/models/block/${name}${wall ? "_wall" : ""}${pressed ? "_pressed" : ""}.json`, {
        parent: "minecraft:block/block",
        textures: { bezel, cap: block(`${capName}${pressed ? "_pressed" : ""}`), particle: bezel },
        elements: [solid([4, 0, 4], [12, 1, 12], "#bezel"), cap([5, 1, 5], [11, pressed ? 1.5 : 2.5, 11], wall)],
      });
    }
  }
  write(`assets/${ID}/models/block/${name}_inventory.json`, {
    parent: "minecraft:block/block",
    textures: { bezel, cap: block(capName), particle: bezel },
    elements: [
      solid([4, 4, 8], [12, 12, 9], "#bezel"),
      { from: [5, 5, 6.5], to: [11, 11, 8], shade: false, faces: Object.fromEntries(["north", "east", "west", "up", "down"].map((face) => [face, { texture: "#cap", ...lit }])) },
    ],
  });
  // Turned as vanilla's: on the floor and the wall to face the way it looks, on the ceiling upside down and the other
  // way round.
  const ceiling = { north: 180, east: 270, south: 0, west: 90 };
  write(`assets/${ID}/blockstates/${name}.json`, {
    variants: Object.fromEntries(["floor", "wall", "ceiling"].flatMap((face) => Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((powered) => {
      const turn = face === "ceiling" ? ceiling[facing] : y;
      return [`face=${face},facing=${facing},powered=${powered}`, {
        model: `${ID}:block/${name}${face === "wall" ? "_wall" : ""}${powered ? "_pressed" : ""}`,
        ...(face === "wall" ? { x: 90 } : face === "ceiling" ? { x: 180 } : {}),
        ...(turn ? { y: turn } : {}),
      }];
    })))),
  });
  write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}_inventory` });
  dropsItself(name);
  cubes[name] = null;
}
// The airstair button beside the controller (see AirstairButtonBlock), its cap showing the controller's staircase.
litButton("airstair_button");
// A zeppelin's boarding stairs' button (see BoardingStairsButtonBlock), in the airstair button's cap: the same stairs.
litButton("boarding_stairs_button", "airstair_button");
// A zeppelin's speed boost button (see BoostButtonBlock): its red cap shows a fast-forward arrow.
litButton("boost_button");
// The illuminated button: the call button with glowstone dust over it to light its cap.
function airstairButtonRecipes() {
  shaped("airstair_button", ["G", "B"], { G: "minecraft:glowstone_dust", B: `${ID}:airstair_call_button` }, 1);
}
function airstairButtonLang() {
  return {
    "block.aerolarge.airstair_button": "Airstair Button",
  };
}
// The door button beside each hull door (see DoorButtonBlock), its cap showing a door.
litButton("door_button");
// An airstair button with its cap dyed light blue.
function doorButtonRecipes() {
  shaped("door_button", ["D", "B"], { D: "minecraft:light_blue_dye", B: `${ID}:airstair_button` }, 1);
}
function doorButtonLang() {
  return {
    "block.aerolarge.door_button": "Door Button",
  };
}
// The integral airstairs are drawn by their controller's renderer (see AirstairRenderer); the steps it lays under them
// are invisible, so their model only gives the particles feet kick up off the treads. Laid only by the controller, so
// no item and no loot; light like the rest of the airliner, but no part of its hull (see airstairFittings).
write(`assets/${ID}/blockstates/airstair_step.json`, { variants: { "": { model: `${ID}:block/airstair_step` } } });
write(`assets/${ID}/models/block/airstair_step.json`, { textures: { particle: `${ID}:block/airstair_tread` } });
cubes.airstair_step = null;
function airstairFittings() {
  return ["airstair_step"];
}
// The airstairs' hydraulic motor and the clunk of their latches (see tools/generate_sounds.mjs).
function airstairSounds() {
  return Object.fromEntries(["airstair_motor", "airstair_latch"].map((name) =>
    [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function airstairLang() {
  return {
    "block.aerolarge.airstair_step": "Airstair Step",
    "subtitles.aerolarge.airstair_motor": "Airstairs whirr",
    "subtitles.aerolarge.airstair_latch": "Airstairs latch",
  };
}
// What the control surfaces' panels are walked on, aft of the wings' trailing edges: the panels are drawn by the
// autopilot's renderer (see ControlSurfaceRenderer), so these are invisible, and their model only gives the particles
// feet kick up. Laid only with the wings, so no item and no loot; light like the rest of the airliner, but no part of its
// hull.
write(`assets/${ID}/blockstates/surface_panel.json`, { variants: { "": { model: `${ID}:block/surface_panel` } } });
write(`assets/${ID}/models/block/surface_panel.json`, { textures: { particle: `${ID}:block/airframe_panel` } });
cubes.surface_panel = null;
function surfacePanelFittings() {
  return ["surface_panel"];
}
function surfacePanelLang() {
  return {
    "block.aerolarge.surface_panel": "Control Surface Panel",
  };
}
// The evacuation slides. The control, set into the cabin wall beside a door, drawn facing north: the hull's skin all
// round but on its north face, which is its red panel, a red handle in the slot down its side, up while the slide is
// packed and down once it is out, and a lamp beside it, dark or glowing green. The slides themselves are drawn by the
// control's renderer (see SlideRenderer), and the steps under them are invisible, so their model only gives the
// particles; laid only by the control, so no item and no loot, light like the rest of the airliner but no part of its
// hull (see slideFittings).
{
  const skin = { texture: "#skin" };
  for (const out of [false, true]) {
    const name = `slide_control${out ? "_out" : ""}`;
    // The handle's stem from its pivot to its grip, and the grip across it, up or down the slot.
    const [stemFrom, stemTo, gripFrom, gripTo] = out ? [3, 7.5, 1.5, 3] : [7.5, 12.5, 12, 13.5];
    const lamp = { texture: "#lamp", ...(out ? { neoforge_data: { block_light: 15, sky_light: 15 } } : {}) };
    write(`assets/${ID}/models/block/${name}.json`, {
      parent: "minecraft:block/block",
      textures: {
        face: `${ID}:block/slide_control`, skin: `${ID}:block/airframe_panel`, stem: `${ID}:block/slide_control_stem`,
        grip: `${ID}:block/slide_control_grip`, lamp: `${ID}:block/slide_control_lamp${out ? "_lit" : ""}`, particle: `${ID}:block/slide_control`,
      },
      elements: [
        {
          from: [0, 0, 0], to: [16, 16, 16],
          faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) =>
            [face, { ...(face === "north" ? { texture: "#face" } : skin), cullface: face }])),
        },
        solid([2.5, 6.5, -1], [4.5, 8.5, 0], "#stem"),
        solid([3, stemFrom, -1.25], [4, stemTo, -0.25], "#stem"),
        solid([1.5, gripFrom, -2.5], [5.5, gripTo, -0.5], "#grip"),
        { from: [6, 12, -0.5], to: [8, 14, 0], ...(out ? { shade: false } : {}), faces: Object.fromEntries(["north", "east", "west", "up", "down"].map((face) => [face, lamp])) },
      ],
    });
  }
  write(`assets/${ID}/blockstates/slide_control.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((out) =>
      [`deployed=${out},facing=${facing}`, { model: `${ID}:block/slide_control${out ? "_out" : ""}`, y }]))),
  });
  write(`assets/${ID}/models/item/slide_control.json`, { parent: `${ID}:block/slide_control` });
  dropsItself("slide_control");
  cubes.slide_control = null;
  write(`assets/${ID}/blockstates/slide_step.json`, { variants: { "": { model: `${ID}:block/slide_step` } } });
  write(`assets/${ID}/models/block/slide_step.json`, { textures: { particle: `${ID}:block/slide_lane` } });
  cubes.slide_step = null;
}
function slideFittings() {
  return ["slide_step"];
}
// The gas rushing in as a slide inflates and out as it deflates, and someone setting off down it (see
// tools/generate_sounds.mjs).
function slideSounds() {
  return Object.fromEntries(["slide_inflate", "slide_deflate", "slide_whoosh"].map((name) =>
    [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
// A slide of yellow wool on a lever in a panel.
function slideRecipes() {
  shaped("slide_control", ["Y", "L", "P"], { Y: "minecraft:yellow_wool", L: "minecraft:lever", P: `${ID}:airframe_panel` }, 1);
}
function slideLang() {
  return {
    "block.aerolarge.slide_control": "Evacuation Slide Control",
    "block.aerolarge.slide_step": "Evacuation Slide",
    "entity.aerolarge.slide_ride": "Evacuation Slide",
    "subtitles.aerolarge.slide_inflate": "Slide inflates",
    "subtitles.aerolarge.slide_deflate": "Slide deflates",
    "subtitles.aerolarge.slide_whoosh": "Someone slides down",
    "message.aerolarge.slide.moving": "The slide only comes out while the airliner stands still",
    "message.aerolarge.slide.no_ground": "No ground within the slide's reach below this door",
    "message.aerolarge.slide.blocked": "Something is in the slide's way",
    "message.aerolarge.slide.too_low": "The ground is right below the door: no slide needed",
    "message.aerolarge.slide.no_door": "This slide control is not next to a door",
  };
}
// The skin, the windscreen and the moon pool's clear glass cut to the hull's curve: one model each for every shape,
// baked by the mod's own "shell" loader from the block's corners, the windscreen's drawn as a single pane however
// thick its glass runs (see ShellModel). They give back the part they are cut from (the clear glass, plain glass). The
// relay hidden in the nacelles drops nothing.
for (const [name, skin, renderType, part, singlePane, tinted] of [
  ["airframe_shell", "airframe_panel", "minecraft:solid", `${ID}:airframe_panel`, false, false],
  ["cockpit_glass", "cockpit_glass", "minecraft:translucent", `${ID}:cockpit_window`, true, false],
  ["hull_glass", "hull_glass", "minecraft:translucent", "minecraft:glass", false, false],
  ["smoked_glass", "smoked_glass", "minecraft:translucent", "minecraft:glass", false, false],
  ["zeppelin_cover", "zeppelin_skin", "minecraft:solid", `${ID}:zeppelin_fabric`, false, true],
]) {
  write(`assets/${ID}/blockstates/${name}.json`, { variants: { "": { model: `${ID}:block/${name}` } } });
  write(`assets/${ID}/models/block/${name}.json`, {
    loader: `${ID}:shell`,
    render_type: renderType,
    ...(singlePane ? { single_pane: true } : {}),
    ...(tinted ? { tint_index: 0 } : {}),
    textures: { skin: `${ID}:block/${skin}`, particle: `${ID}:block/${skin}` },
  });
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: part }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/${name}`,
  });
  cubes[name] = null;
}

// The zeppelin's fabric, on its fins, its cars and its neck: the undoped weave all round, tinted by the zeppelin's
// livery as its cover is (see Livery), the way vanilla's leaves are tinted.
write(`assets/${ID}/models/block/zeppelin_fabric.json`, { parent: "minecraft:block/leaves", textures: { all: `${ID}:block/zeppelin_skin` } });

// The hull's lining (see Lining): the plain skin (the whole panel, and the relay in the cabin wall that passes for
// one) and the skin cut to the curve where the airliner's inside sees it, drawn by the "shell" loader with the
// lining's panels on the faces the block says are lined and the plating on the rest. The panel's item is the same
// model, plated all round, shown as a block. The lined skin cut to the curve gives back a panel, as the plain does.
{
  const lining = Object.fromEntries(["", "_ceiling", "_floor", "_dado", "_reveal", "_reveal_pair", "_reveal_head", "_reveal_sill", "_dado_sill"]
    .map((look) => [`lining${look}`, `${ID}:block/hull_lining${look}`]));
  const skin = { skin: `${ID}:block/airframe_panel`, particle: `${ID}:block/airframe_panel` };
  write(`assets/${ID}/models/block/airframe_panel.json`, {
    parent: "minecraft:block/block",
    loader: `${ID}:shell`,
    render_type: "minecraft:solid",
    textures: { ...skin, ...lining },
  });
  write(`assets/${ID}/blockstates/airframe_lined_shell.json`, { variants: { "": { model: `${ID}:block/airframe_lined_shell` } } });
  write(`assets/${ID}/models/block/airframe_lined_shell.json`, { loader: `${ID}:shell`, render_type: "minecraft:solid", textures: { ...skin, ...lining } });
  write(`data/${ID}/loot_table/blocks/airframe_lined_shell.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:airframe_panel` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/airframe_lined_shell`,
  });
  cubes.airframe_lined_shell = null;
}
function hullLiningLang() {
  return { "block.aerolarge.airframe_lined_shell": "Lined Airframe Shell" };
}

write(`assets/${ID}/blockstates/thrust_relay.json`, { variants: { "": { model: `${ID}:block/thrust_relay` } } });
write(`assets/${ID}/models/block/thrust_relay.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/thrust_relay` } });
cubes.thrust_relay = null;
// The zeppelin's propeller hub: a polished steel block, its blades drawn behind it by its renderer (see
// PropellerRenderer), the same model whichever way it faces and whatever its signal.
write(`assets/${ID}/blockstates/propeller.json`, { variants: { "": { model: `${ID}:block/propeller` } } });
write(`assets/${ID}/models/block/propeller.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/propeller_hub` } });
write(`assets/${ID}/models/item/propeller.json`, { parent: `${ID}:block/propeller` });
write(`data/${ID}/loot_table/blocks/propeller.json`, {
  type: "minecraft:block",
  pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:propeller` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
  random_sequence: `${ID}:blocks/propeller`,
});
cubes.propeller = null;
// The zeppelin's hand controls in its control car (see HelmBlock, TelegraphBlock and BurnerLeverBlock), drawn with
// whoever works them standing at the model's south side, looking north. The ship's wheels stand on a turned brass
// pedestal, the wheel itself drawn round by its renderer (see HelmRenderer). The engine telegraph is a brass column
// with a round head, its dial facing whoever rings it and its handle over the order rung, the five orders on the
// dial as pictures in colours (see the texture); its base plate is red for the port engines' and green for the
// starboard engines', as a ship's sides are. The burner lever stands on a box beside a flame painted from a small
// blue one at the bottom to a big red one at the top, its red knob thrown forward to burn harder.
{
  const brass = `${ID}:block/zeppelin_brass`;
  const wood = `${ID}:block/zeppelin_wood`;
  write(`assets/${ID}/models/block/helm_pedestal.json`, {
    parent: "minecraft:block/block",
    textures: { brass, wood, particle: brass },
    elements: [
      solid([4, 0, 4], [12, 2, 12], "#wood"),
      solid([6.5, 2, 6.5], [9.5, 13, 9.5], "#brass"),
      solid([5.5, 13, 6], [10.5, 15, 10], "#wood"),
    ],
  });
  for (const name of ["rudder_wheel", "elevator_wheel", "trim_wheel"]) {
    write(`assets/${ID}/blockstates/${name}.json`, {
      variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
        [...Array(9).keys()].map((turn) => [`facing=${facing},turn=${turn}`, { model: `${ID}:block/helm_pedestal`, y }]))),
    });
    write(`assets/${ID}/models/item/${name}.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/ships_wheel` } });
    dropsItself(name);
    cubes[name] = null;
  }
  const orders = ["astern", "stop", "slow", "half", "full"];
  for (const side of ["port", "starboard"]) {
    orders.forEach((order, i) => {
      const rotation = { origin: [8, 11.25, 11], axis: "z", angle: 45 - 22.5 * i };
      write(`assets/${ID}/models/block/engine_telegraph_${side}_${order}.json`, {
        parent: "minecraft:block/block",
        render_type: "minecraft:cutout",
        textures: { brass, plate: `${ID}:block/telegraph_${side}`, dial: `${ID}:block/telegraph_dial`, particle: brass },
        elements: [
          solid([3, 0, 3], [13, 1, 13], "#plate"),
          solid([6.5, 1, 6.5], [9.5, 9, 9.5], "#brass"),
          solid([4, 9, 6], [12, 16, 10.5], "#brass"),
          // The dial's half disc, which the texture draws across its middle rows, across the head's face.
          { from: [4, 10, 10.5], to: [12, 15, 10.6], faces: { south: { texture: "#dial", uv: [0, 5, 16, 15] } } },
          solid([7.5, 11.25, 10.6], [8.5, 14.75, 11.2], "#brass", rotation),
        ],
      });
    });
  }
  write(`assets/${ID}/blockstates/engine_telegraph.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["port", "starboard"].flatMap((side) =>
      orders.map((order) => [`engines=${side},facing=${facing},order=${order}`, { model: `${ID}:block/engine_telegraph_${side}_${order}`, y }])))),
  });
  write(`assets/${ID}/models/item/engine_telegraph.json`, { parent: `${ID}:block/engine_telegraph_port_stop` });
  // The engine repeater in each engine car (see EngineRepeaterBlock): the telegraph's pedestal, dial and handle on a
  // brass plate, and a lamp on the side of its head, red while its order waits to be answered and green once it is.
  for (const answered of ["false", "true"]) {
    orders.forEach((order, i) => {
      const rotation = { origin: [8, 11.25, 11], axis: "z", angle: 45 - 22.5 * i };
      write(`assets/${ID}/models/block/engine_repeater_${order}${answered === "true" ? "_answered" : ""}.json`, {
        parent: "minecraft:block/block",
        render_type: "minecraft:cutout",
        textures: { brass, dial: `${ID}:block/telegraph_dial`, lamp: `${ID}:block/jump_light_${answered === "true" ? "green" : "red"}`, particle: brass },
        elements: [
          solid([3, 0, 3], [13, 1, 13], "#brass"),
          solid([6.5, 1, 6.5], [9.5, 9, 9.5], "#brass"),
          solid([4, 9, 6], [12, 16, 10.5], "#brass"),
          { from: [4, 10, 10.5], to: [12, 15, 10.6], faces: { south: { texture: "#dial", uv: [0, 5, 16, 15] } } },
          solid([7.5, 11.25, 10.6], [8.5, 14.75, 11.2], "#brass", rotation),
          solid([12, 13, 7], [13.5, 15, 9], "#lamp"),
        ],
      });
    });
  }
  write(`assets/${ID}/blockstates/engine_repeater.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["false", "true"].flatMap((answered) =>
      orders.map((order) => [`answered=${answered},facing=${facing},order=${order}`,
        { model: `${ID}:block/engine_repeater_${order}${answered === "true" ? "_answered" : ""}`, y }])))),
  });
  write(`assets/${ID}/models/item/engine_repeater.json`, { parent: `${ID}:block/engine_repeater_stop_answered` });
  // The trim car: a heavy iron ballast car hung by its brass wheels from the rail over it; the winch hauling it, an
  // iron drum wound with chain; and the bubble level, a wooden stand under its glass tube, the spirit and the bubble
  // drawn by its renderer (see BubbleLevelRenderer).
  const iron = "minecraft:block/anvil";
  const chain = "minecraft:block/chain";
  // The secret techno room: its hatch, closed just like the gangway's grating and open only a frame round its edge;
  // its speakers and its DJ deck, each turned to face the way it looks.
  const catwalk = `${ID}:block/zeppelin_catwalk`;
  write(`assets/${ID}/models/block/secret_hatch.json`, { parent: "minecraft:block/cube_all", render_type: "minecraft:cutout", textures: { all: catwalk } });
  write(`assets/${ID}/models/block/secret_hatch_open.json`, {
    parent: "minecraft:block/block",
    render_type: "minecraft:cutout",
    textures: { grating: catwalk, particle: catwalk },
    elements: [solid([0, 14, 0], [16, 16, 1], "#grating"), solid([0, 14, 15], [16, 16, 16], "#grating"), solid([0, 14, 1], [1, 16, 15], "#grating"),
      solid([15, 14, 1], [16, 16, 15], "#grating")],
  });
  write(`assets/${ID}/blockstates/secret_hatch.json`, {
    variants: { "open=false": { model: `${ID}:block/secret_hatch` }, "open=true": { model: `${ID}:block/secret_hatch_open` } },
  });
  write(`assets/${ID}/models/item/secret_hatch.json`, { parent: `${ID}:block/secret_hatch` });
  dropsItself("secret_hatch");
  cubes.secret_hatch = null;
  write(`assets/${ID}/models/block/speaker.json`, {
    parent: "minecraft:block/orientable",
    textures: { front: `${ID}:block/speaker_front`, side: `${ID}:block/speaker_side`, top: `${ID}:block/speaker_side` },
  });
  write(`assets/${ID}/blockstates/speaker.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/speaker`, y }])),
  });
  write(`assets/${ID}/models/item/speaker.json`, { parent: `${ID}:block/speaker` });
  dropsItself("speaker");
  cubes.speaker = null;
  write(`assets/${ID}/models/block/techno_deck.json`, {
    parent: "minecraft:block/block",
    textures: { top: `${ID}:block/techno_deck_top`, side: `${ID}:block/techno_deck_side`, particle: `${ID}:block/techno_deck_side` },
    elements: [{
      from: [0, 0, 0], to: [16, 13, 16],
      faces: {
        up: { texture: "#top" }, down: { texture: "#side" }, north: { texture: "#side", uv: [0, 3, 16, 16] }, south: { texture: "#side", uv: [0, 3, 16, 16] },
        east: { texture: "#side", uv: [0, 3, 16, 16] }, west: { texture: "#side", uv: [0, 3, 16, 16] },
      },
    }],
  });
  write(`assets/${ID}/blockstates/techno_deck.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/techno_deck`, y }])),
  });
  write(`assets/${ID}/models/item/techno_deck.json`, { parent: `${ID}:block/techno_deck` });
  dropsItself("techno_deck");
  cubes.techno_deck = null;
  write(`assets/${ID}/models/block/trim_car.json`, {
    parent: "minecraft:block/block",
    textures: { iron, brass, particle: iron },
    elements: [solid([2, 0, 2], [14, 11, 14], "#iron"), solid([4, 11, 3], [12, 16, 5], "#brass"), solid([4, 11, 11], [12, 16, 13], "#brass")],
  });
  write(`assets/${ID}/blockstates/trim_car.json`, { variants: { "": { model: `${ID}:block/trim_car` } } });
  write(`assets/${ID}/models/item/trim_car.json`, { parent: `${ID}:block/trim_car` });
  dropsItself("trim_car");
  cubes.trim_car = null;
  write(`assets/${ID}/models/block/trim_winch.json`, {
    parent: "minecraft:block/block",
    render_type: "minecraft:cutout",
    textures: { iron, chain, particle: iron },
    elements: [solid([3, 4, 1], [13, 14, 15], "#iron"), solid([6.5, 0, 6.5], [9.5, 4, 9.5], "#chain")],
  });
  write(`assets/${ID}/blockstates/trim_winch.json`, { variants: { "": { model: `${ID}:block/trim_winch` } } });
  write(`assets/${ID}/models/item/trim_winch.json`, { parent: `${ID}:block/trim_winch` });
  dropsItself("trim_winch");
  cubes.trim_winch = null;
  write(`assets/${ID}/models/block/bubble_level.json`, {
    parent: "minecraft:block/block",
    textures: { wood, brass, particle: wood },
    elements: [solid([2, 0, 5], [14, 4, 11], "#wood"), solid([1, 4, 6.5], [2.5, 8, 9.5], "#brass"), solid([13.5, 4, 6.5], [15, 8, 9.5], "#brass")],
  });
  write(`assets/${ID}/blockstates/bubble_level.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/bubble_level`, y }])),
  });
  write(`assets/${ID}/models/item/bubble_level.json`, { parent: `${ID}:block/bubble_level` });
  dropsItself("bubble_level");
  cubes.bubble_level = null;
  dropsItself("engine_repeater");
  cubes.engine_repeater = null;
  // The livery board: a board three sixteenths thick standing at the back of its block, its swatches on its front.
  write(`assets/${ID}/models/block/livery_board.json`, {
    parent: "minecraft:block/block",
    textures: { front: `${ID}:block/livery_board_front`, side: `${ID}:block/livery_board_side`, particle: `${ID}:block/livery_board_side` },
    elements: [{
      from: [0, 0, 13], to: [16, 16, 16],
      faces: {
        north: { texture: "#front" }, south: { texture: "#side" }, up: { texture: "#side", uv: [0, 13, 16, 16] },
        down: { texture: "#side", uv: [0, 13, 16, 16] }, east: { texture: "#side", uv: [13, 0, 16, 16] }, west: { texture: "#side", uv: [0, 0, 3, 16] },
      },
    }],
  });
  write(`assets/${ID}/blockstates/livery_board.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/livery_board`, y }])),
  });
  write(`assets/${ID}/models/item/livery_board.json`, { parent: `${ID}:block/livery_board` });
  dropsItself("livery_board");
  cubes.livery_board = null;
  // The horn cord: a rope hanging from the ceiling with a wooden handle across its end, drawn lower while pulled.
  for (const [pulled, drop] of [["false", 0], ["true", 2]]) {
    write(`assets/${ID}/models/block/horn_cord${pulled === "true" ? "_pulled" : ""}.json`, {
      parent: "minecraft:block/block",
      render_type: "minecraft:cutout",
      textures: { rope: `${ID}:block/climbing_rope`, handle: "minecraft:block/stripped_dark_oak_log", particle: `${ID}:block/climbing_rope` },
      elements: [
        { from: [7.5, 5 - drop, 7.5], to: [8.5, 16, 8.5], faces: Object.fromEntries(["north", "south", "east", "west"].map((f) => [f, { texture: "#rope" }])) },
        { from: [5, 2 - drop, 7], to: [11, 5 - drop, 9], faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((f) => [f, { texture: "#handle" }])) },
      ],
    });
  }
  write(`assets/${ID}/blockstates/horn_cord.json`, {
    variants: { "powered=false": { model: `${ID}:block/horn_cord` }, "powered=true": { model: `${ID}:block/horn_cord_pulled` } },
  });
  write(`assets/${ID}/models/item/horn_cord.json`, { parent: `${ID}:block/horn_cord` });
  dropsItself("horn_cord");
  cubes.horn_cord = null;
  dropsItself("engine_telegraph");
  cubes.engine_telegraph = null;
  for (let heat = 0; heat <= 4; heat++) {
    const rotation = { origin: [8, 6, 8], axis: "x", angle: 45 - 22.5 * heat };
    write(`assets/${ID}/models/block/burner_lever_${heat}.json`, {
      parent: "minecraft:block/block",
      textures: { brass, wood, flame: `${ID}:block/burner_flame`, knob: `${ID}:block/jump_light_red`, particle: brass },
      elements: [
        solid([4, 0, 5], [12, 6, 11], "#wood"),
        // The painted flame, a board standing on the box beside the lever, the whole picture on either face.
        { from: [10, 6, 8.5], to: [13.5, 15, 9], faces: { south: { texture: "#flame", uv: [0, 0, 16, 16] }, north: { texture: "#flame", uv: [16, 0, 0, 16] } } },
        solid([7.5, 6, 7.5], [8.5, 12, 8.5], "#brass", rotation),
        solid([6.5, 12, 6.5], [9.5, 14, 9.5], "#knob", rotation),
      ],
    });
  }
  write(`assets/${ID}/blockstates/burner_lever.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
      [0, 1, 2, 3, 4].map((heat) => [`facing=${facing},heat=${heat}`, { model: `${ID}:block/burner_lever_${heat}`, y }]))),
  });
  write(`assets/${ID}/models/item/burner_lever.json`, { parent: `${ID}:block/burner_lever_2` });
  dropsItself("burner_lever");
  cubes.burner_lever = null;
}
// The ship's colours on the Sky Deck (see FlagHalyardBlock): a white flagstaff, its topmost length carrying a gilded
// truck, and its foot, a brass plate with the staff going on up from it and a horn cleat on the side the flag flies,
// a coil of the halyard hung under it. The flag and the halyard's line up the staff are drawn by its renderer (see
// ShipsColoursRenderer).
{
  const pole = "minecraft:block/white_concrete";
  const brass = `${ID}:block/zeppelin_brass`;
  for (const top of [false, true]) {
    write(`assets/${ID}/models/block/flagstaff${top ? "_top" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: { pole, truck: "minecraft:block/gold_block", particle: pole },
      elements: top ? [solid([6.5, 0, 6.5], [9.5, 13, 9.5], "#pole"), solid([5.5, 13, 5.5], [10.5, 16, 10.5], "#truck")] : [solid([6.5, 0, 6.5], [9.5, 16, 9.5], "#pole")],
    });
  }
  write(`assets/${ID}/blockstates/flagstaff.json`, { variants: { "up=false": { model: `${ID}:block/flagstaff` }, "up=true": { model: `${ID}:block/flagstaff_top` } } });
  write(`assets/${ID}/models/item/flagstaff.json`, { parent: `${ID}:block/flagstaff_top` });
  dropsItself("flagstaff");
  cubes.flagstaff = null;
  write(`assets/${ID}/models/block/flag_halyard.json`, {
    parent: "minecraft:block/block",
    textures: { pole, brass, rope: `${ID}:block/climbing_rope`, particle: brass },
    elements: [
      solid([3, 0, 3], [13, 2, 13], "#brass"),
      solid([6.5, 2, 6.5], [9.5, 16, 9.5], "#pole"),
      solid([7.5, 5, 5.5], [8.5, 6, 6.5], "#brass"),
      solid([7.5, 8, 5.5], [8.5, 9, 6.5], "#brass"),
      solid([7.5, 4, 4.5], [8.5, 10, 5.5], "#brass"),
      solid([6, 2, 4], [10, 5, 5], "#rope"),
    ],
  });
  write(`assets/${ID}/blockstates/flag_halyard.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/flag_halyard`, y }])),
  });
  write(`assets/${ID}/models/item/flag_halyard.json`, { parent: `${ID}:block/flag_halyard` });
  dropsItself("flag_halyard");
  cubes.flag_halyard = null;
  // The Lookout's Glass in the control car's lounge (see LookoutGlassBlock): a teak post on a foot, a brass yoke on its
  // top and in it a long brass glass, its sunshade forward and its eyepiece aft, tilted up a step toward the horizon
  // ahead. It stands above its block, at a lookout's chest, so that it points out of the car's windows over their sill
  // rather than into the wall under them; its renderer leaves it out for whoever looks through it.
  const wood = `${ID}:block/zeppelin_wood`;
  const tilt = { origin: [8, 20, 8], axis: "x", angle: 22.5 };
  write(`assets/${ID}/models/block/lookout_glass.json`, {
    parent: "minecraft:block/block",
    textures: { wood, brass, eyepiece: "minecraft:block/black_concrete", particle: brass },
    elements: [
      patched([4, 0, 4], [12, 1, 12], "#wood"),
      patched([7, 1, 7], [9, 18, 9], "#wood"),
      patched([5.5, 18, 7], [10.5, 19, 9], "#brass"),
      patched([5.5, 19, 7], [6.5, 21, 9], "#brass"),
      patched([9.5, 19, 7], [10.5, 21, 9], "#brass"),
      patched([6.5, 18.5, 2], [9.5, 21.5, 15], "#brass", tilt),
      patched([6, 18, -1], [10, 22, 2], "#brass", tilt),
      patched([7, 19, 15], [9, 21, 17.5], "#eyepiece", tilt),
    ],
  });
  write(`assets/${ID}/blockstates/lookout_glass.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/lookout_glass`, y }])),
  });
  write(`assets/${ID}/models/item/lookout_glass.json`, { parent: `${ID}:block/lookout_glass` });
  dropsItself("lookout_glass");
  cubes.lookout_glass = null;
  // The water ballast valve under the gangway at the bow (see BallastValveBlock): a copper pipe down through its block
  // from the bow's ballast bags, and on it a brass hand wheel, its rim, two spokes and its hub.
  write(`assets/${ID}/models/block/ballast_valve.json`, {
    parent: "minecraft:block/block",
    textures: { pipe: "minecraft:block/cut_copper", brass, particle: brass },
    elements: [
      solid([6.5, 0, 6.5], [9.5, 16, 9.5], "#pipe"),
      solid([3, 7, 3], [13, 8, 4], "#brass"),
      solid([3, 7, 12], [13, 8, 13], "#brass"),
      solid([3, 7, 4], [4, 8, 12], "#brass"),
      solid([12, 7, 4], [13, 8, 12], "#brass"),
      solid([4, 7, 7.5], [12, 8, 8.5], "#brass"),
      solid([7.5, 7, 4], [8.5, 8, 12], "#brass"),
      solid([6, 6.5, 6], [10, 8.5, 10], "#brass"),
    ],
  });
  write(`assets/${ID}/blockstates/ballast_valve.json`, { variants: { "": { model: `${ID}:block/ballast_valve` } } });
  // The searchlights under the control car (see SearchlightBlock): a black drum on a brass yoke hung from the top of
  // its block, its big lens on the drum's underside, dark glass or, lit, glowing; and the handle in the lounge it is
  // swung from (see SearchlightHandleBlock): a brass column with a cross grip, black at its ends.
  for (const lit of [false, true]) {
    write(`assets/${ID}/models/block/searchlight${lit ? "_lit" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: { drum: "minecraft:block/black_concrete", brass, lens: `${ID}:block/searchlight_lens${lit ? "" : "_off"}`, particle: brass },
      elements: [
        solid([7, 12, 7], [9, 16, 9], "#brass"),
        solid([3, 11, 7], [13, 12, 9], "#brass"),
        solid([3, 5, 7], [4, 11, 9], "#brass"),
        solid([12, 5, 7], [13, 11, 9], "#brass"),
        { from: [4, 3, 4], to: [12, 10, 12], faces: {
          north: { texture: "#drum" }, south: { texture: "#drum" }, east: { texture: "#drum" }, west: { texture: "#drum" },
          up: { texture: "#drum" }, down: { texture: "#lens" },
        } },
      ],
    });
  }
  write(`assets/${ID}/blockstates/searchlight.json`, {
    variants: { "lit=false": { model: `${ID}:block/searchlight` }, "lit=true": { model: `${ID}:block/searchlight_lit` } },
  });
  write(`assets/${ID}/models/item/searchlight.json`, { parent: `${ID}:block/searchlight` });
  dropsItself("searchlight");
  cubes.searchlight = null;
  // Zeppelin Post (see FrankingMachineBlock): the franking machine, a red post office machine on the control car's
  // floor, drawn facing north: its body, an iron top with the stamp's black handle on it, a dark slot in its front the
  // postcards come out of, and a little lamp on top, green while it is ready to drop a mail sack and dark while it
  // rests. And the mail sack (see MailSackBlock): canvas, tied at the neck, a red band of the post round it.
  for (const resting of [false, true]) {
    write(`assets/${ID}/models/block/franking_machine${resting ? "_resting" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: {
        body: "minecraft:block/red_concrete", top: "minecraft:block/iron_block", dark: "minecraft:block/black_concrete",
        lamp: resting ? "minecraft:block/gray_concrete" : "minecraft:block/lime_concrete", particle: "minecraft:block/red_concrete",
      },
      elements: [
        solid([2, 0, 3], [14, 8, 13], "#body"),
        solid([3, 8, 4], [13, 9, 12], "#top"),
        solid([5, 4, 2.9], [11, 5, 3], "#dark"),
        solid([4, 9, 6], [6, 12, 8], "#dark"),
        solid([10, 9, 9], [12, 11, 11], "#lamp"),
      ],
    });
  }
  write(`assets/${ID}/blockstates/franking_machine.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((resting) => [
      `facing=${facing},resting=${resting}`, { model: `${ID}:block/franking_machine${resting ? "_resting" : ""}`, y },
    ]))),
  });
  write(`assets/${ID}/models/item/franking_machine.json`, { parent: `${ID}:block/franking_machine` });
  dropsItself("franking_machine");
  cubes.franking_machine = null;
  write(`assets/${ID}/models/block/mail_sack.json`, {
    parent: "minecraft:block/block",
    textures: { canvas: "minecraft:block/brown_wool", band: "minecraft:block/red_wool", particle: "minecraft:block/brown_wool" },
    elements: [solid([3, 0, 3], [13, 10, 13], "#canvas"), solid([2.9, 4, 2.9], [13.1, 6, 13.1], "#band"), solid([6, 10, 6], [10, 12, 10], "#canvas")],
  });
  write(`assets/${ID}/blockstates/mail_sack.json`, { variants: { "": { model: `${ID}:block/mail_sack` } } });
  write(`assets/${ID}/models/item/mail_sack.json`, { parent: `${ID}:block/mail_sack` });
  dropsItself("mail_sack");
  cubes.mail_sack = null;
  // The towed banner's cleat (see BannerCleatBlock): a brass horn cleat on a plate against the wall behind it, drawn
  // facing north; while the banner is hauled in its tow rope is made fast round the horns and coiled under them, and
  // while it streams the rope runs taut from the horns up out of the car.
  for (const streaming of [false, true]) {
    write(`assets/${ID}/models/block/banner_cleat${streaming ? "_streaming" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: { brass, rope: `${ID}:block/climbing_rope`, particle: brass },
      elements: [
        solid([5, 4, 15], [11, 12, 16], "#brass"),
        solid([7, 7, 12], [9, 9, 15], "#brass"),
        solid([3, 7, 10], [13, 9, 12], "#brass"),
        solid([6.5, 6.5, 9.5], [9.5, 9.5, 12.5], "#rope"),
        ...streaming ? [solid([7.5, 9.5, 10.5], [8.5, 16, 11.5], "#rope")] : [solid([5, 1, 10], [11, 6.5, 14], "#rope")],
      ],
    });
  }
  write(`assets/${ID}/blockstates/banner_cleat.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((streaming) => [
      `facing=${facing},streaming=${streaming}`, { model: `${ID}:block/banner_cleat${streaming ? "_streaming" : ""}`, y },
    ]))),
  });
  write(`assets/${ID}/models/item/banner_cleat.json`, { parent: `${ID}:block/banner_cleat` });
  dropsItself("banner_cleat");
  cubes.banner_cleat = null;
  // The sky crane's winch (see CraneWinchBlock): an iron frame on a dark base, the drum of rope between its sides drawn
  // turning by CraneWinchRenderer, the load dial's cream plate over it, and its lever at its right side, a brass arm
  // with a red knob, tilted up to hoist, level to hold and down to lower; drawn facing north, worked from the south.
  for (const [notch, angle] of [["lower", 45], ["hold", 0], ["hoist", -45]]) {
    const lever = (from, to, texture) => ({ ...solid(from, to, texture), rotation: { origin: [15.5, 6.5, 8], axis: "x", angle } });
    write(`assets/${ID}/models/block/crane_winch_${notch}.json`, {
      parent: "minecraft:block/block",
      textures: { base: "minecraft:block/black_concrete", iron: "minecraft:block/iron_block", dial: "minecraft:block/white_concrete",
        brass, knob: "minecraft:block/red_concrete", particle: "minecraft:block/iron_block" },
      elements: [
        solid([1, 0, 2], [15, 2, 14], "#base"),
        solid([1, 2, 5], [3, 12, 11], "#iron"),
        solid([13, 2, 5], [15, 12, 11], "#iron"),
        solid([3.5, 10, 11], [12.5, 16, 12], "#dial"),
        lever([15, 6, 8], [16, 7, 14], "#brass"),
        lever([14.75, 5.75, 13.5], [16.25, 7.25, 15], "#knob"),
      ],
    });
  }
  write(`assets/${ID}/blockstates/crane_winch.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["lower", "hold", "hoist"].map((notch, index) => [
      `facing=${facing},notch=${index}`, { model: `${ID}:block/crane_winch_${notch}`, y },
    ]))),
  });
  write(`assets/${ID}/models/item/crane_winch.json`, { parent: `${ID}:block/crane_winch_hold` });
  dropsItself("crane_winch");
  cubes.crane_winch = null;
  // The flight deck's lines are painted on its top only: its sides are the plain deck's.
  write(`assets/${ID}/models/block/flight_deck_stripe.json`, {
    parent: "minecraft:block/cube_bottom_top",
    textures: { top: `${ID}:block/flight_deck_stripe`, bottom: `${ID}:block/flight_deck`, side: `${ID}:block/flight_deck` },
  });
  // The flying carrier's docking clamp (see DeckClampBlock): a block of the flight deck with a pair of yellow jaws lying
  // flat in it, or raised round a plane's wheels when closed, and a lamp between them, red open and green closed; and
  // its release lever (see CarrierLeverBlock), a yellow lever on an iron stand, forward when the clamps lock and back
  // when they are released, drawn facing north.
  for (const closed of [false, true]) {
    const jaw = closed ? 18 : 16.5;
    write(`assets/${ID}/models/block/deck_clamp${closed ? "_closed" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: { deck: `${ID}:block/flight_deck`, jaw: "minecraft:block/yellow_concrete", lamp: closed ? "minecraft:block/lime_concrete" : "minecraft:block/red_concrete",
        particle: `${ID}:block/flight_deck` },
      elements: [
        solid([0, 0, 0], [16, 16, 16], "#deck"),
        solid([2, 16, 4], [14, jaw, 6], "#jaw"),
        solid([2, 16, 10], [14, jaw, 12], "#jaw"),
        solid([7, 16, 7], [9, 16.6, 9], "#lamp"),
      ],
    });
  }
  write(`assets/${ID}/blockstates/deck_clamp.json`, {
    variants: { "closed=false": { model: `${ID}:block/deck_clamp` }, "closed=true": { model: `${ID}:block/deck_clamp_closed` } },
  });
  write(`assets/${ID}/models/item/deck_clamp.json`, { parent: `${ID}:block/deck_clamp` });
  dropsItself("deck_clamp");
  cubes.deck_clamp = null;
  for (const released of [false, true]) {
    write(`assets/${ID}/models/block/carrier_lever${released ? "_released" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: { iron: "minecraft:block/iron_block", lever: "minecraft:block/yellow_concrete", tip: "minecraft:block/black_concrete",
        particle: "minecraft:block/iron_block" },
      elements: [
        solid([5, 0, 5], [11, 3, 11], "#iron"),
        { ...solid([7.25, 3, 7.25], [8.75, 12, 8.75], "#lever"), rotation: { origin: [8, 3, 8], axis: "x", angle: released ? 45 : -45 } },
        { ...solid([6.75, 10.5, 6.75], [9.25, 13, 9.25], "#tip"), rotation: { origin: [8, 3, 8], axis: "x", angle: released ? 45 : -45 } },
      ],
    });
  }
  write(`assets/${ID}/blockstates/carrier_lever.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((released) => [
      `facing=${facing},released=${released}`, { model: `${ID}:block/carrier_lever${released ? "_released" : ""}`, y },
    ]))),
  });
  write(`assets/${ID}/models/item/carrier_lever.json`, { parent: `${ID}:block/carrier_lever` });
  dropsItself("carrier_lever");
  cubes.carrier_lever = null;
  // The floodlights under the zeppelin (see FloodlightBlock): a flat black lamp on a brass plate hung from the top of
  // its block, its wide lens underneath, dark glass or, lit, glowing, and a hood down its back edge, so it reads which
  // way, drawn facing north, it throws its light.
  for (const lit of [false, true]) {
    write(`assets/${ID}/models/block/floodlight${lit ? "_lit" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: { drum: "minecraft:block/black_concrete", brass, lens: `${ID}:block/searchlight_lens${lit ? "" : "_off"}`, particle: brass },
      elements: [
        solid([5, 15, 5], [11, 16, 11], "#brass"),
        solid([2, 12, 2], [14, 15, 14], "#drum"),
        { from: [3, 11, 3], to: [13, 12, 13], faces: {
          north: { texture: "#drum" }, south: { texture: "#drum" }, east: { texture: "#drum" }, west: { texture: "#drum" },
          up: { texture: "#drum" }, down: { texture: "#lens" },
        } },
        solid([2, 9, 13], [14, 12, 14], "#drum"),
      ],
    });
  }
  write(`assets/${ID}/blockstates/floodlight.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((lit) => [
      `facing=${facing},lit=${lit}`, { model: `${ID}:block/floodlight${lit ? "_lit" : ""}`, y },
    ]))),
  });
  write(`assets/${ID}/models/item/floodlight.json`, { parent: `${ID}:block/floodlight` });
  dropsItself("floodlight");
  cubes.floodlight = null;
  write(`assets/${ID}/models/block/searchlight_handle.json`, {
    parent: "minecraft:block/block",
    textures: { brass, grip: "minecraft:block/black_concrete", particle: brass },
    elements: [
      solid([5, 0, 5], [11, 1, 11], "#brass"),
      solid([7, 1, 7], [9, 12, 9], "#brass"),
      solid([5, 12, 7], [11, 13, 9], "#brass"),
      solid([3, 12, 7], [5, 13, 9], "#grip"),
      solid([11, 12, 7], [13, 13, 9], "#grip"),
    ],
  });
  write(`assets/${ID}/blockstates/searchlight_handle.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/searchlight_handle`, y }])),
  });
  write(`assets/${ID}/models/item/searchlight_handle.json`, { parent: `${ID}:block/searchlight_handle` });
  dropsItself("searchlight_handle");
  cubes.searchlight_handle = null;
  // The skywriting board (see SkywritingBoardBlock): a board two sixteenths thick at the back of its block, its
  // picture buttons on its face; the lit one's frame is drawn by its renderer (see SkywritingBoardRenderer).
  write(`assets/${ID}/models/block/skywriting_board.json`, {
    parent: "minecraft:block/block",
    textures: { front: `${ID}:block/skywriting_board_front`, side: `${ID}:block/skywriting_board_side`, particle: `${ID}:block/skywriting_board_side` },
    elements: [{
      from: [0, 0, 14], to: [16, 16, 16],
      faces: {
        north: { texture: "#front" }, south: { texture: "#side" }, up: { texture: "#side", uv: [0, 14, 16, 16] },
        down: { texture: "#side", uv: [0, 14, 16, 16] }, east: { texture: "#side", uv: [14, 0, 16, 16] }, west: { texture: "#side", uv: [0, 0, 2, 16] },
      },
    }],
  });
  write(`assets/${ID}/blockstates/skywriting_board.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/skywriting_board`, y }])),
  });
  write(`assets/${ID}/models/item/skywriting_board.json`, { parent: `${ID}:block/skywriting_board` });
  dropsItself("skywriting_board");
  cubes.skywriting_board = null;
  write(`assets/${ID}/models/item/ballast_valve.json`, { parent: `${ID}:block/ballast_valve` });
  dropsItself("ballast_valve");
  cubes.ballast_valve = null;
}
// A ship's plaque (see ShipsPlaqueBlock): a brass plate on the wall, a sixteenth thick, its engraving drawn by its
// renderer once its zeppelin is christened (see ShipsPlaqueRenderer); and the Christening Bottle broken on her nose.
{
  const brass = `${ID}:block/zeppelin_brass`;
  write(`assets/${ID}/models/block/ships_plaque.json`, {
    parent: "minecraft:block/block",
    textures: { brass, particle: brass },
    elements: [solid([1, 3, 15], [15, 13, 16], "#brass")],
  });
  write(`assets/${ID}/blockstates/ships_plaque.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/ships_plaque`, y }])),
  });
  write(`assets/${ID}/models/item/ships_plaque.json`, { parent: `${ID}:block/ships_plaque` });
  dropsItself("ships_plaque");
  cubes.ships_plaque = null;
  write(`assets/${ID}/models/item/christening_bottle.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/christening_bottle` } });
  write(`assets/${ID}/particles/dove.json`, { textures: [`${ID}:dove_up`, `${ID}:dove_down`] });
}
// The picture board choosing what a zeppelin's flank screens show (see PictureBoardBlock): a board three sixteenths
// thick standing at the back of its block, the little screen and its buttons on its front, the screens and the
// little screen's picture drawn by its renderer (see FlankScreenRenderer).
{
  write(`assets/${ID}/models/block/picture_board.json`, {
    parent: "minecraft:block/block",
    textures: { front: `${ID}:block/picture_board_front`, side: `${ID}:block/picture_board_side`, particle: `${ID}:block/picture_board_side` },
    elements: [{
      from: [0, 0, 13], to: [16, 16, 16],
      faces: {
        north: { texture: "#front" }, south: { texture: "#side" }, up: { texture: "#side", uv: [0, 13, 16, 16] },
        down: { texture: "#side", uv: [0, 13, 16, 16] }, east: { texture: "#side", uv: [13, 0, 16, 16] }, west: { texture: "#side", uv: [0, 0, 3, 16] },
      },
    }],
  });
  write(`assets/${ID}/blockstates/picture_board.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/picture_board`, y }])),
  });
  write(`assets/${ID}/models/item/picture_board.json`, { parent: `${ID}:block/picture_board` });
  dropsItself("picture_board");
  cubes.picture_board = null;
}
function flankScreenLang() {
  return {
    "block.aerolarge.picture_board": "Picture Board",
    "message.aerolarge.picture_board.no_map": "Hand the board a filled map to show it on the screens",
    "message.aerolarge.picture_board.no_colours": "Hoist a banner at the flagstaff to show her colours",
    "poster.aerolarge.nether": "See the Nether",
    "poster.aerolarge.end": "The End: Last Stop",
    "poster.aerolarge.jungle": "Fly to the Jungle",
    "poster.aerolarge.peaks": "Ski the Frozen Peaks",
    "poster.aerolarge.creeper_insurance": "Creeper Insurance",
    "poster.aerolarge.villager_bank": "Villager Bank",
    "poster.aerolarge.golem_guards": "Golem Guards",
    "poster.aerolarge.cake_by_air": "Cake by Air",
  };
}
function christeningLang() {
  return {
    "block.aerolarge.ships_plaque": "Ship's Plaque",
    "block.aerolarge.ships_plaque.christened_by": "Christened by %s",
    "item.aerolarge.christening_bottle": "Christening Bottle",
    "item.aerolarge.christening_bottle.tooltip": "Name it at an anvil, then swing it at a zeppelin's nose to christen her",
    "message.aerolarge.christening.named": "I name this ship %s. God bless her, and all who sail in her!",
    "message.aerolarge.christening.unnamed": "God bless her, and all who sail in her! Name the bottle at an anvil to give her a name",
    "message.aerolarge.christening.nose": "Swing the bottle at her nose",
  };
}
function shipsColoursLang() {
  return {
    "block.aerolarge.flag_halyard": "Flag Halyard",
    "block.aerolarge.flagstaff": "Flagstaff",
    "subtitles.aerolarge.flag_halyard": "Halyard pulley squeaks",
  };
}
function zeppelinControlsLang() {
  return {
    "block.aerolarge.rudder_wheel": "Rudder Wheel",
    "block.aerolarge.elevator_wheel": "Elevator Wheel",
    "block.aerolarge.engine_telegraph": "Engine Telegraph",
    "block.aerolarge.engine_repeater": "Engine Repeater",
    "block.aerolarge.trim_wheel": "Trim Wheel",
    "block.aerolarge.trim_winch": "Trim Winch",
    "block.aerolarge.trim_car": "Trim Car",
    "block.aerolarge.bubble_level": "Bubble Level",
    "block.aerolarge.techno_deck": "DJ Deck",
    "block.aerolarge.secret_hatch": "Secret Hatch",
    "block.aerolarge.speaker": "Speaker",
    "block.aerolarge.dance_floor": "Dance Floor",
    "block.aerolarge.club_wall": "Club Wall",
    "block.aerolarge.lookout_glass": "Lookout's Glass",
    "block.aerolarge.screen_stand": "Screen Stand",
    "subtitles.aerolarge.lookout_bell": "Ship's bell strikes twice",
    "block.aerolarge.ballast_valve": "Water Ballast Valve",
    "block.aerolarge.searchlight": "Searchlight",
    "block.aerolarge.floodlight": "Floodlight",
    "block.aerolarge.franking_machine": "Franking Machine",
    "block.aerolarge.mail_sack": "Mail Sack",
    "block.aerolarge.banner_cleat": "Banner Cleat",
    "block.aerolarge.crane_winch": "Crane Winch",
    "block.aerolarge.flight_deck": "Flight Deck",
    "block.aerolarge.flight_deck_stripe": "Flight Deck Line",
    "block.aerolarge.deck_clamp": "Docking Clamp",
    "block.aerolarge.carrier_lever": "Clamp Release Lever",
    "entity.aerolarge.crane_hook": "Crane Hook",
    "item.aerolarge.postcard": "Postcard from %s",
    "item.aerolarge.postcard.franked": "Franked over %s, %s",
    "item.aerolarge.postcard.the_zeppelin": "the Zeppelin",
    "block.aerolarge.searchlight_handle": "Searchlight Handle",
    "block.aerolarge.skywriting_board": "Skywriting Board",
    "block.aerolarge.boarding_stairs_button": "Boarding Stairs Button",
    "block.aerolarge.boost_button": "Speed Boost Button",
    "subtitles.aerolarge.speed_boost": "Boosters roar",
    "subtitles.aerolarge.zeppelin_engine": "Engine car drones",
    "subtitles.aerolarge.telegraph_ring": "Engine telegraph rings",
    "subtitles.aerolarge.telegraph_answer": "Engine car answers",
    "subtitles.aerolarge.helm_notch": "Ship's wheel turns",
    "subtitles.aerolarge.control_stop": "Control at its stop",
    "subtitles.aerolarge.crane_ratchet": "Winch ratchets",
    "subtitles.aerolarge.crane_latch": "Crane hook clunks",
    "subtitles.aerolarge.clamp_lock": "Docking clamps lock",
    "subtitles.aerolarge.clamp_release": "Docking clamps open",
    "subtitles.aerolarge.canopy_open": "Parachute opens",
    "subtitles.aerolarge.touchdown": "Wheels touch down",
    "subtitles.aerolarge.trim_car_roll": "Trim car rolls",
    "subtitles.aerolarge.bin_open": "Overhead bin opens",
    "subtitles.aerolarge.bin_close": "Overhead bin closes",
    "subtitles.aerolarge.searchlight_on": "Searchlight strikes up",
    "subtitles.aerolarge.booster_cutout": "Boosters cut out",
    "subtitles.aerolarge.tow_rope": "Tow rope runs",
    "subtitles.aerolarge.glass_squeak": "Finger squeaks on glass",
    "subtitles.aerolarge.autopilot_off": "Autopilot disconnects",
    "subtitles.aerolarge.gear_lever": "Gear lever clunks",
    "subtitles.aerolarge.handler_whistle_short": "Whistle: hold fast",
    "subtitles.aerolarge.handler_whistle_long": "Whistle: let go",
    "subtitles.aerolarge.water_ballast": "Water ballast pours",
    "subtitles.aerolarge.up_ship_call": "Up ship!",
    "subtitles.aerolarge.techno_track": "Techno pounds",
    "block.aerolarge.horn_cord": "Horn Cord",
    "block.aerolarge.livery_board": "Livery Board",
    "block.aerolarge.livery_selector": "Livery Selector",
    "block.aerolarge.festive_garland": "Festive Garland",
    "block.aerolarge.festive_bow": "Festive Bow",
    "livery.aerolarge.silver": "Hindenburg silver",
    "livery.aerolarge.cream": "Graf Zeppelin cream",
    "livery.aerolarge.navy": "navy grey",
    "livery.aerolarge.airline": "the airliner's colours",
    "livery.aerolarge.polar": "polar red",
    "livery.aerolarge.midnight": "midnight blue",
    "livery.aerolarge.checker": "moon-rocket checks",
    "livery.aerolarge.roll": "black and white roll stripes",
    "livery.aerolarge.red_trim": "silver with red trim",
    "message.aerolarge.livery_board.repainting": "Repainting in %s: watch the painters' cradle sweep along",
    "message.aerolarge.livery_board.repainting_rocket": "Repainting in %s: watch the new paint run down her from her nose",
    "message.aerolarge.livery_board.no_zeppelin": "This board paints the zeppelin whose control car it stands in",
    "subtitles.aerolarge.zeppelin_horn": "Zeppelin's horn sounds",
    "subtitles.aerolarge.zeppelin_hull": "Wind booms through the hull",
    "subtitles.aerolarge.zeppelin_creak": "Girder groans",
    "subtitles.aerolarge.zeppelin_horn_echo": "Horn echoes off the ground",
    "subtitles.aerolarge.sleigh_bells": "Sleigh bells jingle",
    "block.aerolarge.burner_lever": "Burner Lever",
  };
}
// The zeppelin's fabric is far lighter than the airliner's aluminium: its cover and fins weigh a tenth of a block,
// against the airframe's quarter (sable:super_light), which it overrides, so the hot air carries a hull of its size.
write(`data/${ID}/tags/block/zeppelin_fabric.json`, { replace: false, values: [`${ID}:zeppelin_fabric`, `${ID}:zeppelin_cover`] });
write(`data/${ID}/physics_block_properties/zeppelin_fabric.json`, {
  priority: 1002,
  selector: `#${ID}:zeppelin_fabric`,
  properties: { "sable:mass": 0.1 },
});
// The trim car is heavy, some four parts in a hundred of the zeppelin, so moving it fore and aft pitches her.
write(`data/${ID}/physics_block_properties/trim_car.json`, {
  priority: 1003,
  selector: `${ID}:trim_car`,
  properties: { "sable:mass": 1200 },
});
function zeppelinLang() {
  return {
    "itemGroup.aerolarge.zeppelin": "Zeppelin",
    "block.aerolarge.zeppelin_fabric": "Zeppelin Fabric",
    "block.aerolarge.zeppelin_cover": "Zeppelin Cover",
    "block.aerolarge.zeppelin_girder": "Zeppelin Girder",
    "block.aerolarge.zeppelin_catwalk": "Zeppelin Catwalk",
    "block.aerolarge.propeller": "Propeller",
    "message.aerolarge.zeppelin_building": "Building the zeppelin… %s%%",
    "message.aerolarge.zeppelin_built": "The zeppelin is ready. Use the autopilot in its control car, under the bow, to fly it.",
    "message.aerolarge.zeppelin_too_high": "The zeppelin would reach past the build height here; build it lower down.",
  };
}

// The relay behind a lamp in the cabin wall shows the cabin plain skin.
write(`assets/${ID}/blockstates/hull_relay.json`, { variants: { "": { model: `${ID}:block/airframe_panel` } } });
cubes.hull_relay = null;

// The fairing, drawn cut at its top east edge: a skin plate along the diagonal from the top west corner to the
// bottom east one (a thin slab through the middle turned 45 degrees and stretched to reach both corners), over eight
// steps that fill the solid half and close its ends. The other cuts are the same model turned.
{
  const all = (texture) => ({ north: { texture }, south: { texture }, east: { texture }, west: { texture }, up: { texture }, down: { texture } });
  const steps = [...Array(8).keys()].map((k) => ({ from: [0, 2 * k, 0], to: [14 - 2 * k, 2 * k + 2, 16], faces: all("#skin") }));
  write(`assets/${ID}/models/block/airframe_fairing.json`, {
    parent: "minecraft:block/block",
    textures: { skin: `${ID}:block/airframe_panel`, particle: `${ID}:block/airframe_panel` },
    elements: [
      { from: [0, 7.75, 0], to: [16, 8.25, 16], rotation: { origin: [8, 8, 8], axis: "z", angle: -45, rescale: true }, faces: all("#skin") },
      ...steps,
    ],
  });
  write(`assets/${ID}/models/item/airframe_fairing.json`, { parent: `${ID}:block/airframe_fairing` });
  const turn = { east: 0, south: 90, west: 180, north: 270 };
  write(`assets/${ID}/blockstates/airframe_fairing.json`, {
    variants: Object.fromEntries(Object.entries(turn).flatMap(([facing, y]) => [
      [`facing=${facing},half=top`, { model: `${ID}:block/airframe_fairing`, y }],
      [`facing=${facing},half=bottom`, { model: `${ID}:block/airframe_fairing`, x: 180, y }],
    ])),
  });
  write(`data/${ID}/loot_table/blocks/airframe_fairing.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:airframe_fairing` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/airframe_fairing`,
  });
  cubes.airframe_fairing = null;
}

// The engine core looks like the other relays: it is hidden in the nacelle.
write(`assets/${ID}/blockstates/engine_core.json`, { variants: { "": { model: `${ID}:block/thrust_relay` } } });
cubes.engine_core = null;

// A sound the server plays to each listener aboard, following them about (the callouts, the announcements, air traffic
// control, the door alarm): never heard from where it is set off, so how far it carries changes nothing of who hears
// it. The subtitles, though, keep a sound only while the listener is within that reach of where it started, and in
// flight the listener is soon hundreds of blocks from there; carrying far keeps its subtitle up while it plays.
const HEARD_ABOARD = 1024;
function heardAboard(name) {
  return { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}`, attenuation_distance: HEARD_ABOARD }] };
}

// The flight deck's voice (see tools/generate_sounds.mjs): each callout and cabin announcement, subtitled with what it
// says, the announcements' gist.
const VOICE_SUBTITLES = {
  callout_v1: "Callout: V one",
  callout_rotate: "Callout: Rotate",
  callout_positive_rate: "Callout: Positive rate",
  callout_100: "Callout: One hundred",
  callout_50: "Callout: Fifty",
  callout_40: "Callout: Forty",
  callout_30: "Callout: Thirty",
  callout_20: "Callout: Twenty",
  callout_10: "Callout: Ten",
  callout_retard: "Callout: Retard",
  callout_bank_angle: "Callout: Bank angle!",
  callout_pull_up: "Callout: Pull up!",
  callout_touchdown: "Callout: Touchdown",
  announcement_welcome: "Announcement: Welcome aboard",
  announcement_takeoff: "Announcement: Crew seated for take-off",
  announcement_turbulence: "Announcement: Bumpy air, seatbelts on",
  announcement_snacks: "Announcement: Drinks and snacks",
  announcement_window: "Announcement: Look out of the window",
  announcement_playtime: "Announcement: Pool and gym open",
  announcement_sleep: "Announcement: Lights dimmed, sleep time",
  announcement_landing: "Announcement: Crew seated for landing",
  announcement_arrival: "Announcement: Welcome to your destination",
  announcement_space: "Announcement: Welcome to space",
};
const VOICE_LANG = Object.fromEntries(Object.entries(VOICE_SUBTITLES).map(([name, subtitle]) => [`subtitles.${ID}.${name}`, subtitle]));
// Air traffic control on the flight deck's radio (see tools/generate_sounds.mjs and AtcRadio): each transmission
// subtitled with its gist, other aircraft's as the radio's, and the carrier clips as the crackle they are.
const ATC_SUBTITLES = {
  atc_cleared_for_takeoff: "ATC: Cleared for take-off",
  atc_contact_departure: "ATC: Contact departure",
  atc_radar_contact: "ATC: Radar contact, climb",
  atc_cleared_to_land: "ATC: Cleared to land",
  atc_go_around: "ATC: Go around",
  atc_taxi_to_gate: "ATC: Welcome, taxi to the gate",
  atc_contact_center: "ATC: Contact center, good day",
  atc_center_check_in: "ATC: Center, radar contact",
  atc_traffic_two_oclock: "ATC: Traffic, two o'clock",
  atc_traffic_ten_oclock: "ATC: Traffic, ten o'clock",
  atc_light_turbulence: "ATC: Light turbulence ahead",
  atc_smooth_ride: "ATC: Smooth ride ahead",
  atc_geese: "ATC: Geese reported!",
  atc_balloon: "ATC: Hot air balloon ahead",
  atc_skyhopper_hello: "Radio: Another plane says hello",
  atc_cargo_climb: "Radio: A cargo jet climbs",
  atc_static_open: "Radio crackles",
  atc_static_clicks: "Radio clicks",
};
function atcSounds() {
  return Object.fromEntries(Object.keys(ATC_SUBTITLES).map((name) => [name, heardAboard(name)]));
}
function atcLang() {
  return Object.fromEntries(Object.entries(ATC_SUBTITLES).map(([name, subtitle]) => [`subtitles.${ID}.${name}`, subtitle]));
}

// The rocket's sound and voice (see tools/generate_sounds.mjs, the client's RocketRoar and the autopilot's
// RocketSounds). Her engines' roar, their ignition and cut-off and her touchdown on her fins, heard as far as
// ROCKET_HEARD blocks off, out across the ground round her and up her whole height; the same heard aboard, round the
// listener, and her side jets' puffs, near them; and launch control's count and its calls of her milestones (see the
// autopilot's LaunchCallouts), played to each listener as the flight deck's voice is (see heardAboard), subtitled with
// what is said.
const ROCKET_HEARD = 160;
const ROCKET_SUBTITLES = {
  rocket_roar: "Rocket engines roar",
  rocket_roar_aboard: "Rocket engines rumble",
  rocket_ignition: "Rocket engines ignite",
  rocket_ignition_aboard: "Rocket engines ignite",
  rocket_cutoff: "Rocket engines cut off",
  rocket_cutoff_aboard: "Rocket engines cut off",
  rocket_jet_puff: "Side jet puffs",
  rocket_touchdown: "Rocket lands on her fins",
};
const LAUNCH_SUBTITLES = {
  launch_10: "Launch control: Ten",
  launch_9: "Launch control: Nine",
  launch_8: "Launch control: Eight",
  launch_7: "Launch control: Seven",
  launch_6: "Launch control: Six",
  launch_5: "Launch control: Five",
  launch_4: "Launch control: Four",
  launch_3: "Launch control: Three",
  launch_2: "Launch control: Two",
  launch_1: "Launch control: One",
  launch_liftoff: "Launch control: Lift-off!",
  launch_tower_cleared: "Launch control: Tower cleared",
  launch_max_q: "Launch control: Max Q",
  launch_main_engine_cutoff: "Launch control: Main engine cut-off",
  launch_landing_burn: "Launch control: Landing burn",
};
function rocketSounds() {
  const far = ["rocket_roar", "rocket_ignition", "rocket_cutoff", "rocket_touchdown"];
  const sounds = Object.fromEntries(Object.keys(ROCKET_SUBTITLES).map((name) => [name, {
    subtitle: `subtitles.${ID}.${name}`,
    sounds: [far.includes(name) ? { name: `${ID}:${name}`, attenuation_distance: ROCKET_HEARD } : { name: `${ID}:${name}` }],
  }]));
  return { ...sounds, ...Object.fromEntries(Object.keys(LAUNCH_SUBTITLES).map((name) => [name, heardAboard(name)])) };
}
function rocketSoundsLang() {
  return Object.fromEntries(Object.entries({ ...ROCKET_SUBTITLES, ...LAUNCH_SUBTITLES }).map(([name, subtitle]) => [`subtitles.${ID}.${name}`, subtitle]));
}

// The engines' sound and the flight deck's voice (see tools/generate_sounds.mjs), and the contrail particle.
{
  // How far an engine is heard, in blocks: from the flight deck and the far end of the cabin, and out on the ground
  // round the runway. Minecraft fixes how far a sound carries as it starts, from how loud it is then, and an engine's
  // sound starts as it begins to spool up, all but silent; so its reach is set here, not by turning it up.
  const ENGINE_HEARD = 160;
  const sounds = {
    engine: {
      subtitle: `subtitles.${ID}.engine`,
      sounds: [{ name: `${ID}:engine`, attenuation_distance: ENGINE_HEARD }],
    },
  };
  for (const name of Object.keys(VOICE_SUBTITLES)) {
    sounds[name] = heardAboard(name);
  }
  Object.assign(sounds, atcSounds());
  Object.assign(sounds, rocketSounds());
  sounds.pa_chime = { subtitle: `subtitles.${ID}.pa_chime`, sounds: [{ name: `${ID}:pa_chime` }] };
  sounds.radio_crackle = { subtitle: `subtitles.${ID}.radio_crackle`, sounds: [{ name: `${ID}:radio_crackle` }] };
  sounds.seatbelt_ding = { subtitle: `subtitles.${ID}.seatbelt_ding`, sounds: [{ name: `${ID}:seatbelt_ding` }] };
  sounds.shade_motor = { subtitle: `subtitles.${ID}.shade_motor`, sounds: [{ name: `${ID}:shade_motor` }] };
  sounds.trolley_squeak = { subtitle: `subtitles.${ID}.trolley_squeak`, sounds: [{ name: `${ID}:trolley_squeak` }] };
  for (const name of ["ball_bounce", "basket_swish", "scoreboard_buzzer", "springboard_boing"]) {
    sounds[name] = { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] };
  }
  // Duty free and the paper plane.
  for (const name of ["paper_plane_whoosh", "shop_bell", "cash_register"]) {
    sounds[name] = { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] };
  }
  Object.assign(sounds, holdAndTailSounds());
  // The pilot's head-up display coming on as the pilot helmet goes on.
  sounds.hud_on = { subtitle: `subtitles.${ID}.hud_on`, sounds: [{ name: `${ID}:hud_on` }] };
  // The space helmet's air, breathed in and out, looped in the helmet aboard a rocket in space (see HelmetBreathing).
  sounds.space_helmet_breath = { subtitle: `subtitles.${ID}.space_helmet_breath`, sounds: [{ name: `${ID}:space_helmet_breath` }] };
  // The vacuum toilets' flush, and the blue ice they drop cracking as it lands, or bonking whoever it lands on.
  for (const name of ["toilet_flush", "blue_ice_crack", "blue_ice_bonk"]) {
    sounds[name] = { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] };
  }
  Object.assign(sounds, plugDoorSounds());
  Object.assign(sounds, airstairSounds());
  Object.assign(sounds, slideSounds());
  Object.assign(sounds, rubberDuckSounds());
  Object.assign(sounds, coffeeMachineSounds());
  Object.assign(sounds, crewLockerSounds());
  Object.assign(sounds, gearSounds());
  Object.assign(sounds, airPocketSounds());
  Object.assign(sounds, laserSounds());
  Object.assign(sounds, selfDestructSounds());
  Object.assign(sounds, roverSounds());
  Object.assign(sounds, labSounds());
  // The zeppelin's horn and its echo off the ground, played loud enough to carry some 320 blocks, its sleigh bells, the
  // pulley of its flag halyard, the ship's bell a lookout's spot is struck with, and her take-off on her handlers'
  // lines: the officer's whistles, her bow's water ballast pouring and his call "Up ship!".
  const zeppelinSounds = ["zeppelin_horn", "zeppelin_horn_echo", "sleigh_bells", "flag_halyard", "lookout_bell", "handler_whistle_short",
    "handler_whistle_long", "water_ballast", "up_ship_call"];
  for (const name of zeppelinSounds) {
    sounds[name] = { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] };
  }
  // Inside a zeppelin's hull: its loop, streamed as it is long; and the girders' groans, one of three, heard from well
  // off through the hull, as far as GROAN_HEARD blocks.
  const GROAN_HEARD = 48;
  sounds.zeppelin_hull = { subtitle: `subtitles.${ID}.zeppelin_hull`, sounds: [{ name: `${ID}:zeppelin_hull`, stream: true }] };
  sounds.zeppelin_creak = {
    subtitle: `subtitles.${ID}.zeppelin_creak`,
    sounds: [1, 2, 3].map((variant) => ({ name: `${ID}:zeppelin_creak_${variant}`, attenuation_distance: GROAN_HEARD })),
  };
  // A zeppelin's speed boost, her engines roaring up to full ahead, heard as far as BOOST_HEARD blocks off.
  const BOOST_HEARD = 96;
  sounds.speed_boost = { subtitle: `subtitles.${ID}.speed_boost`, sounds: [{ name: `${ID}:speed_boost`, attenuation_distance: BOOST_HEARD }] };
  // The sound pass's sounds: a zeppelin's engine cars, heard as far as ENGINE_CAR_HEARD blocks off, from the ground
  // under her at cruise; her telegraphs and wheels, her controls' stops, the sky crane, the flight deck's clamps, a
  // parachute opening, the airliner's touchdown, the trim car, the overhead bins, a searchlight striking up, her
  // boosters cutting out; and the towed banner's rope, the halyard's pulley under a subtitle of its own.
  const ENGINE_CAR_HEARD = 96;
  sounds.zeppelin_engine = { subtitle: `subtitles.${ID}.zeppelin_engine`, sounds: [{ name: `${ID}:zeppelin_engine`, attenuation_distance: ENGINE_CAR_HEARD }] };
  for (const name of ["telegraph_ring", "telegraph_answer", "helm_notch", "control_stop", "crane_ratchet", "crane_latch", "clamp_lock", "clamp_release",
    "canopy_open", "touchdown", "trim_car_roll", "bin_open", "bin_close", "searchlight_on", "booster_cutout", "glass_squeak", "autopilot_off", "gear_lever"]) {
    sounds[name] = { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] };
  }
  sounds.tow_rope = { subtitle: `subtitles.${ID}.tow_rope`, sounds: [{ name: `${ID}:flag_halyard` }] };
  // The rocket's lift: its doors sliding, its motor setting off, its hum while the car rides the shaft, and its chimes.
  for (const name of ["lift_door", "lift_start", "lift_hum", "lift_chime_up", "lift_chime_down"]) {
    sounds[name] = { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] };
  }
  // Low gravity aboard a rocket in space coming on and going (see SpaceGravity).
  for (const name of ["space_light", "space_heavy"]) {
    sounds[name] = { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] };
  }
  // Its music, played in the car as it rides: at LIFT_MUSIC_VOLUME of the level it is written at, a little softer than
  // the lift's other sounds, and heard only in the car and right by it, falling away to nothing LIFT_MUSIC_HEARD blocks
  // off.
  const LIFT_MUSIC_VOLUME = 0.35;
  const LIFT_MUSIC_HEARD = 4;
  sounds.lift_music = {
    subtitle: `subtitles.${ID}.lift_music`,
    sounds: [{ name: `${ID}:lift_music`, volume: LIFT_MUSIC_VOLUME, attenuation_distance: LIFT_MUSIC_HEARD }],
  };
  // The secret techno room's track: a minute long, so streamed as music is rather than loaded whole.
  sounds.techno_track = { subtitle: `subtitles.${ID}.techno_track`, sounds: [{ name: `${ID}:techno_track`, stream: true }] };
  write(`assets/${ID}/sounds.json`, sounds);
  write(`assets/${ID}/particles/contrail.json`, { textures: [`${ID}:contrail`] });
  // A flare's smoke is the same soft puff, tinted.
  write(`assets/${ID}/particles/flare_smoke.json`, { textures: [`${ID}:contrail`] });
  selfDestructParticles();
}

// The tiller and the parking brake, modules of the centre console drawn nose north. The tiller: a small handwheel on a
// post, its grip ahead when it steers straight and turned left or right through its five notches. The parking brake:
// a T-handle with a red grip, standing up when set and folded down toward the nose when released.
{
  const textures = { plate: `${ID}:block/panel_side`, handle: `${ID}:block/thrust_lever_handle`, knob: `${ID}:block/thrust_lever_knob`, grip: `${ID}:block/jump_light_red` };
  for (let steer = 0; steer <= 4; steer++) {
    const rotation = { origin: [8, 8, 8], axis: "y", angle: 45 - 22.5 * steer };
    consoleModule(`tiller_${steer}`, textures, "#plate", false, [
      solid([7.25, CONSOLE_HEIGHT, 7.25], [8.75, 7.5, 8.75], "#handle"),
      solid([5, 7.5, 5], [11, 8.5, 11], "#handle", rotation),
      solid([7.25, 8.5, 5.5], [8.75, 10.5, 7], "#knob", rotation),
    ]);
  }
  for (const [set, angle] of [[true, 0], [false, -45]]) {
    const rotation = { origin: [8, CONSOLE_HEIGHT, 8], axis: "x", angle };
    consoleModule(`parking_brake_${set ? "set" : "released"}`, textures, "#plate", false, [
      solid([7.5, CONSOLE_HEIGHT, 7.5], [8.5, 11, 8.5], "#handle", rotation),
      solid([5.5, 11, 7], [10.5, 12.5, 9], "#grip", rotation),
    ]);
  }
  write(`assets/${ID}/blockstates/tiller.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
      [0, 1, 2, 3, 4].map((steer) => [`facing=${facing},steer=${steer}`, { model: `${ID}:block/tiller_${steer}`, y }]))),
  });
  write(`assets/${ID}/blockstates/parking_brake.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
      [true, false].map((set) => [`facing=${facing},powered=${set}`, { model: `${ID}:block/parking_brake_${set ? "set" : "released"}`, y }]))),
  });
  write(`assets/${ID}/models/item/tiller.json`, { parent: `${ID}:block/tiller_2` });
  write(`assets/${ID}/models/item/parking_brake.json`, { parent: `${ID}:block/parking_brake_set` });
  for (const name of ["tiller", "parking_brake"]) {
    write(`data/${ID}/loot_table/blocks/${name}.json`, {
      type: "minecraft:block",
      pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:${name}` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
      random_sequence: `${ID}:blocks/${name}`,
    });
    cubes[name] = null;
  }
}

// The retractable landing gear (see GearBayBlock and GearLeverBlock). The bay at the top of each leg: the belly's skin
// with the gear doors shut underneath, turned with the way the leg swings so the doors' seam runs along it. The lever,
// a module of the flight deck drawn with the pilots at its north side: an arm with a little wheel for a knob, thrown
// back toward the pilots for the gear down and forward for it up, and three lamps in front of it, on the pilots' side
// where the knob never hides them, dark, red or green.
{
  write(`assets/${ID}/models/block/gear_bay.json`, {
    parent: "minecraft:block/cube",
    textures: {
      down: `${ID}:block/gear_bay_bottom`, up: `${ID}:block/landing_gear`, side: `${ID}:block/airframe_panel`,
      particle: `${ID}:block/airframe_panel`,
      north: `${ID}:block/airframe_panel`, south: `${ID}:block/airframe_panel`, east: `${ID}:block/airframe_panel`, west: `${ID}:block/airframe_panel`,
    },
  });
  facing("gear_bay");
  write(`assets/${ID}/models/item/gear_bay.json`, { parent: `${ID}:block/gear_bay` });
  dropsItself("gear_bay");
  cubes.gear_bay = null;

  const lamps = ["off", "red", "green"];
  for (const [down, angle] of [[true, -22.5], [false, 22.5]]) {
    const rotation = { origin: [8, CONSOLE_HEIGHT, 8], axis: "x", angle };
    for (const lamp of lamps) {
      const textures = { plate: `${ID}:block/panel_side`, handle: `${ID}:block/thrust_lever_handle`, knob: `${ID}:block/landing_gear_tire`, lamp: `${ID}:block/jump_light_${lamp}` };
      consoleModule(`gear_lever_${down ? "down" : "up"}_${lamp}`, textures, "#plate", true, [
        solid([6.5, CONSOLE_HEIGHT, 4], [9.5, CONSOLE_HEIGHT + 0.5, 12], "#handle"),
        solid([7.5, CONSOLE_HEIGHT, 7.5], [8.5, 12, 8.5], "#handle", rotation),
        solid([7, 11, 6], [9, 15, 10], "#knob", rotation),
        ...[2, 6.5, 11].map((x) => solid([x, CONSOLE_HEIGHT, 1], [x + 3, CONSOLE_HEIGHT + 0.5, 4], "#lamp")),
      ]);
    }
  }
  write(`assets/${ID}/blockstates/gear_lever.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
      [true, false].flatMap((down) => lamps.map((lamp) =>
        [`down=${down},facing=${facing},lamps=${lamp}`, { model: `${ID}:block/gear_lever_${down ? "down" : "up"}_${lamp}`, y }])))),
  });
  write(`assets/${ID}/models/item/gear_lever.json`, { parent: `${ID}:block/gear_lever_down_green` });
  dropsItself("gear_lever");
  cubes.gear_lever = null;
}
// The gear going up: the hydraulics whining under the floor, the leg thumping into its bay, the doors shutting; coming
// down: the uplocks letting go, the air roaring in, the leg locking down. And the gear horn on the flight deck. Heard
// from the legs and the lever, where they are; the legs play theirs loud enough to carry through the cabin.
function gearSounds() {
  return Object.fromEntries(["gear_retract", "gear_extend", "gear_horn"].map((name) =>
    [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
// Dropping into an air pocket: played to each listener aboard, following them about.
function airPocketSounds() {
  return { air_pocket: heardAboard("air_pocket") };
}
function airPocketLang() {
  return { "subtitles.aerolarge.air_pocket": "Airliner drops into an air pocket" };
}
function gearRecipes() {
  shaped("gear_lever", ["T", "S", "P"], { T: `${ID}:landing_gear_tire`, S: "minecraft:lever", P: `${ID}:airframe_panel` }, 1);
  shaped("gear_bay", ["P", "G", "I"], { P: `${ID}:airframe_panel`, G: `${ID}:landing_gear`, I: "minecraft:piston" }, 1);
}
function gearLang() {
  return {
    "block.aerolarge.gear_bay": "Landing Gear Bay",
    "block.aerolarge.gear_lever": "Landing Gear Lever",
    "subtitles.aerolarge.gear_retract": "Landing gear retracts",
    "subtitles.aerolarge.gear_extend": "Landing gear extends",
    "subtitles.aerolarge.gear_horn": "Gear horn sounds",
    "message.aerolarge.gear.up": "Gear up",
    "message.aerolarge.gear.down": "Gear down",
    "message.aerolarge.gear.on_ground": "The gear stays down: the airliner is standing on its wheels",
    "message.aerolarge.gear.fixed": "The gear stays down: retractable gear is switched off on this server",
    "message.aerolarge.gear.no_room": "The gear stays up: its wheels have no room to come down",
  };
}

// The laser path clearer (see LaserTurretBlock and LaserSwitchBlock). The turret's block: the belly's skin with the
// round hatch it drops out of underneath. The turret itself, out of its hatch, drawn by its renderer a block lower in
// two parts that turn toward where it fires: its head (part=head), a dark housing hanging from a stalk, which swings
// round, and its barrel (part=barrel), steel pointing ahead (north in the model) with the glowing red emitter at its
// tip, which swings with the head and tilts up and down about the housing's middle. The barrel reaches back to that
// middle, so tilted any way it still comes out of the housing; as barrel and emitter reach out of the block, their
// faces take their texture from inside its 16 pixels, the emitter's front the bright middle of its glow. The item is
// the whole turret, both parts together. The switch, a module of the flight deck drawn with the pilots at its north
// side: its top pictured with a turret firing a red beam, a big red button, dark until armed and glowing once it is,
// and the button's guard, shut over it while safe and flipped up behind it when armed.
{
  write(`assets/${ID}/models/block/laser_turret.json`, {
    parent: "minecraft:block/cube",
    textures: {
      down: `${ID}:block/laser_hatch`, up: `${ID}:block/airframe_panel`, particle: `${ID}:block/airframe_panel`,
      north: `${ID}:block/airframe_panel`, south: `${ID}:block/airframe_panel`, east: `${ID}:block/airframe_panel`, west: `${ID}:block/airframe_panel`,
    },
  });
  const glowing = (element) => ({ ...element, faces: Object.fromEntries(Object.entries(element.faces).map(([face, value]) =>
    [face, { ...value, neoforge_data: { block_light: 15, sky_light: 15 } }])) });
  const turretTextures = { body: `${ID}:block/flight_console`, barrel: `${ID}:block/landing_gear`, emitter: `${ID}:block/laser_emitter`, particle: `${ID}:block/flight_console` };
  const head = [solid([7, 14, 7], [9, 16, 9], "#barrel"), solid([4, 8, 4], [12, 14, 12], "#body")];
  const faced = (texture, ends, sides, tops) => ({ north: { texture, uv: ends }, south: { texture, uv: ends }, east: { texture, uv: sides },
    west: { texture, uv: sides }, up: { texture, uv: tops }, down: { texture, uv: tops } });
  const barrel = [
    { from: [7, 10, -4], to: [9, 12, 8], faces: faced("#barrel", [7, 4, 9, 6], [2, 4, 14, 6], [7, 2, 9, 14]) },
    glowing({ from: [6.5, 9.5, -5], to: [9.5, 12.5, -4], faces: faced("#emitter", [6.5, 6.5, 9.5, 9.5], [7.5, 6.5, 8.5, 9.5], [6.5, 7.5, 9.5, 8.5]) }),
  ];
  const turretModel = (name, elements) => write(`assets/${ID}/models/block/${name}.json`, { parent: "minecraft:block/block", textures: turretTextures, elements });
  turretModel("laser_turret_head", head);
  turretModel("laser_turret_barrel", barrel);
  turretModel("laser_turret_gun", [...head, ...barrel]);
  const partModels = { hatch: "laser_turret", head: "laser_turret_head", barrel: "laser_turret_barrel" };
  write(`assets/${ID}/blockstates/laser_turret.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
      Object.entries(partModels).map(([part, model]) => [`facing=${facing},part=${part}`, { model: `${ID}:block/${model}`, y }]))),
  });
  write(`assets/${ID}/models/item/laser_turret.json`, { parent: `${ID}:block/laser_turret_gun` });
  dropsItself("laser_turret");
  cubes.laser_turret = null;

  // Safe, clearing the way (glowing red) and holding its fire at blocks low down (glowing amber).
  const looks = { safe: `${ID}:block/jump_light_off`, armed: `${ID}:block/jump_light_red`, holding: `${ID}:block/laser_amber` };
  for (const [look, lens] of Object.entries(looks)) {
    const textures = { plate: `${ID}:block/laser_switch_top`, button: lens, guard: `${ID}:block/thrust_lever_handle` };
    const button = solid([5, CONSOLE_HEIGHT, 5], [11, CONSOLE_HEIGHT + 2, 11], "#button");
    const up = solid([4.5, CONSOLE_HEIGHT, 11.5], [11.5, CONSOLE_HEIGHT + 7, 12.5], "#guard");
    const shut = solid([4.5, CONSOLE_HEIGHT + 2, 4.5], [11.5, CONSOLE_HEIGHT + 2.5, 11.5], "#guard");
    consoleModule(`laser_switch_${look}`, textures, "#plate", true, look === "safe" ? [button, shut] : [glowing(button), up]);
  }
  const switchLook = (armed, holding) => (!armed ? "safe" : holding ? "holding" : "armed");
  write(`assets/${ID}/blockstates/laser_switch.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
      [false, true].flatMap((armed) => [false, true].map((holding) =>
        [`armed=${armed},facing=${facing},holding=${holding}`, { model: `${ID}:block/laser_switch_${switchLook(armed, holding)}`, y }])))),
  });
  write(`assets/${ID}/models/item/laser_switch.json`, { parent: `${ID}:block/laser_switch_safe` });
  dropsItself("laser_switch");
  cubes.laser_switch = null;

  // What a bolt hurts with: no armour scaling, the death message its own.
  write(`data/${ID}/damage_type/laser.json`, { exhaustion: 0.1, message_id: `${ID}.laser`, scaling: "never" });
}
// A bolt's hiss and the turret's charging whine, from the turret under the nose.
function laserSounds() {
  return Object.fromEntries(["laser_hiss", "laser_charge"].map((name) => [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function laserRecipes() {
  shaped("laser_switch", ["R", "B", "P"], { R: "minecraft:redstone", B: "minecraft:stone_button", P: `${ID}:airframe_panel` }, 1);
  shaped("laser_turret", ["P", "O", "G"], { P: `${ID}:airframe_panel`, O: "minecraft:observer", G: "minecraft:glass" }, 1);
}
function laserLang() {
  return {
    "block.aerolarge.laser_switch": "Laser Switch",
    "block.aerolarge.laser_turret": "Laser Turret",
    "subtitles.aerolarge.laser_hiss": "Laser fires",
    "subtitles.aerolarge.laser_charge": "Laser charges",
    "message.aerolarge.laser.armed": "Laser path clearer armed",
    "message.aerolarge.laser.disarmed": "Laser path clearer safe",
    "message.aerolarge.laser.off": "The laser path clearer is switched off on this server",
    "death.attack.aerolarge.laser": "%1$s was vaporised by an airliner's laser",
  };
}

// The self-destruct button (see SelfDestructButtonBlock), drawn facing north as its viewer does, against the north side
// of its block: a plate in a yellow-and-black hazard border, the red button standing out of its middle, and over it a
// clear lid in a striped frame, so the button shows through it. Shut, the lid covers the button; open, it is flipped up
// to stand out over it; armed, the button glows red-hot whatever the light round it, as its renderer flashes it. The
// lid is see-through, so the whole model is drawn translucent.
{
  const face = (texture, uv, lit) => ({ texture, ...(uv ? { uv } : {}), ...(lit ? { neoforge_data: { block_light: 15, sky_light: 15 } } : {}) });
  const box = (from, to, front, edge, lit, shade = true) => ({
    from, to, ...(shade ? {} : { shade: false }),
    faces: { south: front, north: edge, east: edge, west: edge, up: edge, down: edge },
  });
  const plate = box([2, 2, 0], [14, 14, 1.5], face("#plate"), face("#plate", [0, 0, 16, 2]));
  const button = (lit) => box([5.5, 5.5, 1.5], [10.5, 10.5, 4], face("#button", null, lit), face("#button", [0, 0, 16, 4], lit), lit, !lit);
  const lidEdge = face("#cover", [0, 0, 16, 3]);
  // Shut over the button, and flipped up about its top edge to stand out from the wall over it.
  const shut = { from: [4.5, 4.5, 1.5], to: [11.5, 11.5, 5.5], faces: { south: face("#cover"), east: lidEdge, west: lidEdge, up: lidEdge, down: lidEdge } };
  const up = { from: [4.5, 11.5, 1.5], to: [11.5, 12.5, 8.5], faces: { up: face("#cover"), down: face("#cover"), south: lidEdge, east: lidEdge, west: lidEdge } };
  const looks = { shut: [button(false), shut], open: [button(false), up], armed: [button(true), up] };
  for (const [look, parts] of Object.entries(looks)) {
    write(`assets/${ID}/models/block/self_destruct_button_${look}.json`, {
      parent: "minecraft:block/block",
      render_type: "minecraft:translucent",
      textures: {
        plate: `${ID}:block/self_destruct_plate`, cover: `${ID}:block/self_destruct_cover`, particle: `${ID}:block/self_destruct_plate`,
        button: `${ID}:block/self_destruct_button${look === "armed" ? "_lit" : ""}`,
      },
      elements: [plate, ...parts],
    });
  }
  const buttonLook = (open, armed) => (armed ? "armed" : open ? "open" : "shut");
  write(`assets/${ID}/blockstates/self_destruct_button.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
      [false, true].flatMap((armed) => [false, true].map((open) =>
        [`armed=${armed},facing=${facing},open=${open}`, { model: `${ID}:block/self_destruct_button_${buttonLook(open, armed)}`, y }])))),
  });
  write(`assets/${ID}/models/item/self_destruct_button.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/self_destruct_button` } });
  dropsItself("self_destruct_button");
  cubes.self_destruct_button = null;
}
// The self-destruct's sounds (see tools/generate_sounds.mjs): the alarm's siren, heard by everyone aboard wherever they
// are, and the airliner blowing up, its bang heard far and wide and the rumble after it, played at each listener's ear.
function selfDestructSounds() {
  const BANG_HEARD = 640;
  return {
    self_destruct_siren: heardAboard("self_destruct_siren"),
    self_destruct_blast: { subtitle: `subtitles.${ID}.self_destruct_blast`, sounds: [{ name: `${ID}:self_destruct_blast`, stream: true, attenuation_distance: BANG_HEARD }] },
    self_destruct_rumble: { subtitle: `subtitles.${ID}.self_destruct_rumble`, sounds: [{ name: `${ID}:self_destruct_rumble`, stream: true }] },
  };
}
// The blast's puffs share one soft cloud picture, each kind tinting it.
function selfDestructParticles() {
  for (const kind of ["fire", "smoke", "wave", "ember"]) {
    write(`assets/${ID}/particles/blast_${kind}.json`, { textures: [`${ID}:blast_puff`] });
  }
}
// A stone button and a block of TNT, between yellow and black dye, on a plate of the airframe.
function selfDestructRecipes() {
  shaped("self_destruct_button", ["YBK", "RTR", " P "], {
    Y: "minecraft:yellow_dye", B: "minecraft:stone_button", K: "minecraft:black_dye", R: "minecraft:redstone", T: "minecraft:tnt", P: `${ID}:airframe_panel`,
  }, 1);
}
function selfDestructLang() {
  return {
    "block.aerolarge.self_destruct_button": "Self-Destruct Button",
    "subtitles.aerolarge.self_destruct_siren": "Self-destruct alarm wails",
    "subtitles.aerolarge.self_destruct_blast": "Airliner explodes",
    "subtitles.aerolarge.self_destruct_rumble": "Explosion rumbles",
    "message.aerolarge.self_destruct.open": "Cover up: press the red button to arm the self-destruct",
    "message.aerolarge.self_destruct.shut": "Cover shut",
    "message.aerolarge.self_destruct.armed": "Self-destruct armed! The airliner blows up in %s seconds: press again to blow it up now, or sneak to cancel",
    "message.aerolarge.self_destruct.alarm": "Self-destruct armed! Everyone will get a parachute",
    "message.aerolarge.self_destruct.cancelled": "Self-destruct cancelled",
    "message.aerolarge.self_destruct.assembling": "Getting the airliner ready: the self-destruct arms in a moment",
    "message.aerolarge.self_destruct.not_assembled": "Nothing happens: the self-destruct only works aboard an airliner built from its blueprint",
    "message.aerolarge.self_destruct.off": "The self-destruct is switched off on this server",
    "message.aerolarge.self_destruct.parachute": "Boom! Your parachute opens by itself: steer it the way you walk",
  };
}

// The exterior lights: a small lens on the face it is fixed to, pointing the way it shines (up in the model), one
// model per kind; turned like an end rod for the other directions.
{
  const kinds = ["red", "green", "white", "strobe", "beacon", "bulb", "fairy_red", "fairy_green", "fairy_gold", "fairy_blue", "star"];
  for (const kind of kinds) {
    const face = { texture: "#lens" };
    write(`assets/${ID}/models/block/nav_light_${kind}.json`, {
      parent: "minecraft:block/block",
      render_type: "minecraft:cutout",
      textures: { lens: `${ID}:block/nav_light_${kind}`, particle: `${ID}:block/nav_light_${kind}` },
      elements: [{ from: [5, 0, 5], to: [11, 5, 11], faces: { north: face, south: face, east: face, west: face, up: face, down: face } }],
    });
  }
  write(`assets/${ID}/models/item/nav_light.json`, { parent: `${ID}:block/nav_light_red` });
  const turns = { up: {}, down: { x: 180 }, north: { x: 90 }, south: { x: 90, y: 180 }, east: { x: 90, y: 90 }, west: { x: 90, y: 270 } };
  write(`assets/${ID}/blockstates/nav_light.json`, {
    variants: Object.fromEntries(Object.entries(turns).flatMap(([facing, turn]) =>
      kinds.map((kind) => [`facing=${facing},kind=${kind}`, { model: `${ID}:block/nav_light_${kind}`, ...turn }]))),
  });
  write(`data/${ID}/loot_table/blocks/nav_light.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:nav_light` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/nav_light`,
  });
  cubes.nav_light = null;
}

// The instrument panel: screens on the front, plain panel elsewhere; blank, plain panel all round.
write(`assets/${ID}/models/block/instrument_panel.json`, {
  parent: "minecraft:block/orientable",
  textures: { front: `${ID}:block/instrument_panel`, side: `${ID}:block/panel_side`, top: `${ID}:block/panel_side` },
});
write(`assets/${ID}/models/block/instrument_panel_blank.json`, {
  parent: "minecraft:block/cube_all",
  textures: { all: `${ID}:block/panel_side` },
});
write(`assets/${ID}/models/item/instrument_panel.json`, { parent: `${ID}:block/instrument_panel` });
write(`assets/${ID}/blockstates/instrument_panel.json`, {
  variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [
    [`blank=false,facing=${facing}`, { model: `${ID}:block/instrument_panel`, y }],
    [`blank=true,facing=${facing}`, { model: `${ID}:block/instrument_panel_blank` }],
  ])),
});

// Skydiving: the jump light (a lamp, dark, red or green), the jump switch on the centre console (its lamps lit the
// same way, facing the pilots), the rack of parachutes by the doors, the landing target (a flat bullseye) and the
// parachute.
{
  const loot = (name) => write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:${name}` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/${name}`,
  });
  const signals = ["off", "red", "green"];
  for (const signal of signals) {
    write(`assets/${ID}/models/block/jump_light_${signal}.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/jump_light_${signal}` } });
    // A module of the centre console, its lamps on top and a selector knob pointing at the lamp lit, or between them.
    consoleModule(`jump_switch_${signal}`, { panel: `${ID}:block/jump_switch_${signal}`, knob: `${ID}:block/thrust_lever_knob`, pointer: `${ID}:block/thrust_lever_handle` }, "#panel", true, [
      solid([6.5, CONSOLE_HEIGHT, 3.5], [9.5, 7.5, 6.5], "#knob"),
      solid([7.75, 7.5, 5], [8.25, 8, 8.5], "#pointer", { origin: [8, 7.5, 5], axis: "y", angle: { off: 0, red: 45, green: -45 }[signal] }),
    ]);
  }
  write(`assets/${ID}/blockstates/jump_light.json`, {
    variants: Object.fromEntries(signals.map((signal) => [`signal=${signal}`, { model: `${ID}:block/jump_light_${signal}` }])),
  });
  write(`assets/${ID}/blockstates/jump_switch.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
      signals.map((signal) => [`facing=${facing},signal=${signal}`, { model: `${ID}:block/jump_switch_${signal}`, y }]))),
  });
  write(`assets/${ID}/models/item/jump_light.json`, { parent: `${ID}:block/jump_light_red` });
  write(`assets/${ID}/models/item/jump_switch.json`, { parent: `${ID}:block/jump_switch_green` });
  write(`assets/${ID}/models/block/parachute_rack.json`, {
    parent: "minecraft:block/orientable",
    textures: { front: `${ID}:block/parachute_rack_front`, side: `${ID}:block/parachute_rack_side`, top: `${ID}:block/parachute_rack_top` },
  });
  write(`assets/${ID}/blockstates/parachute_rack.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/parachute_rack`, y }])),
  });
  write(`assets/${ID}/models/item/parachute_rack.json`, { parent: `${ID}:block/parachute_rack` });
  write(`assets/${ID}/models/block/paper_plane_box.json`, {
    parent: "minecraft:block/orientable",
    textures: { front: `${ID}:block/paper_plane_box_front`, side: `${ID}:block/paper_plane_box_side`, top: `${ID}:block/paper_plane_box_top` },
  });
  write(`assets/${ID}/blockstates/paper_plane_box.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/paper_plane_box`, y }])),
  });
  write(`assets/${ID}/models/item/paper_plane_box.json`, { parent: `${ID}:block/paper_plane_box` });
  write(`assets/${ID}/models/block/landing_target.json`, {
    parent: "minecraft:block/block",
    textures: { top: `${ID}:block/landing_target_top`, side: `${ID}:block/landing_target_side`, particle: `${ID}:block/landing_target_top` },
    elements: [{
      from: [0, 0, 0], to: [16, 2, 16],
      faces: {
        up: { texture: "#top" }, down: { texture: "#side" }, north: { texture: "#side", uv: [0, 14, 16, 16] },
        south: { texture: "#side", uv: [0, 14, 16, 16] }, east: { texture: "#side", uv: [0, 14, 16, 16] }, west: { texture: "#side", uv: [0, 14, 16, 16] },
      },
    }],
  });
  write(`assets/${ID}/blockstates/landing_target.json`, { variants: { "": { model: `${ID}:block/landing_target` } } });
  write(`assets/${ID}/models/item/landing_target.json`, { parent: `${ID}:block/landing_target` });
  write(`assets/${ID}/models/item/parachute.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/parachute` } });
  for (const name of ["jump_light", "jump_switch", "parachute_rack", "paper_plane_box", "landing_target"]) {
    loot(name);
    cubes[name] = null;
  }
}

// A model made of boxes, each face of each box showing the given texture over the part of it the box covers; a
// texture given per face as an object, and a box marked lit glowing at full brightness whatever the light around.
function boxes(textures, parts) {
  return {
    parent: "minecraft:block/block",
    textures,
    elements: parts.map(([from, to, texture, lit]) => ({
      from, to, ...(lit ? { shade: false } : {}),
      faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, {
        texture: typeof texture === "string" ? texture : texture[face] ?? texture.all,
        ...(lit ? { neoforge_data: { block_light: 15, sky_light: 15 } } : {}),
      }])),
    })),
  };
}
// A block that faces the way it was placed: one model, turned for each facing.
function facing(name, model = name) {
  write(`assets/${ID}/blockstates/${name}.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/${model}`, y }])),
  });
}
function dropsItself(name) {
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:${name}` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/${name}`,
  });
}
// A part of one texture whose faces each take a patch of it as big as the face from the texture's corner, so a part
// reaching out of its block, over its top, is textured as one inside it would be.
function patched(from, to, texture, rotation) {
  const [width, height, depth] = [0, 1, 2].map((axis) => Math.min(16, Math.abs(to[axis] - from[axis])));
  const patches = {
    north: [0, 0, width, height], south: [0, 0, width, height], east: [0, 0, depth, height], west: [0, 0, depth, height],
    up: [0, 0, width, depth], down: [0, 0, width, depth],
  };
  const element = { from, to, faces: Object.fromEntries(Object.entries(patches).map(([face, uv]) => [face, { texture, uv }])) };
  if (rotation) element.rotation = rotation;
  return element;
}
// A box of a model, every face showing the texture over the part of it the box covers; turned about an axis if a
// rotation is given.
function solid(from, to, texture, rotation) {
  const element = { from, to, faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, { texture }])) };
  if (rotation) element.rotation = rotation;
  return element;
}
// A module of the flight deck's centre console (see ConsoleModule): a slab CONSOLE_HEIGHT pixels tall the size of the
// block in the console's casing, its top the control's panel, and the control's own parts on it. The panel reads the
// right way up for someone at the model's south side, as for the controls that face the nose with the pilots behind
// them; turned round when the pilots are at its north side, as for the controls whose face looks toward them.
function consoleModule(name, textures, top, viewedFromNorth, parts) {
  const side = { texture: "#casing" };
  write(`assets/${ID}/models/block/${name}.json`, {
    parent: "minecraft:block/block",
    textures: { casing: `${ID}:block/flight_console`, particle: `${ID}:block/flight_console`, ...textures },
    elements: [
      {
        from: [0, 0, 0], to: [16, CONSOLE_HEIGHT, 16],
        faces: {
          north: side, south: side, east: side, west: side,
          up: { texture: top, ...(viewedFromNorth ? { rotation: 180 } : {}) },
          down: { texture: "#casing", cullface: "down" },
        },
      },
      ...parts,
    ],
  });
}

// Speaking to the cabin and the tower. The handset: a module of the centre console with a steel cradle on it and the
// receiver lying in it, its earpiece and mouthpiece raised at either end, drawn facing north. The tower radio: a radio
// set with a speaker grille and dials.
{
  consoleModule("handset", { plate: `${ID}:block/panel_side`, cradle: `${ID}:block/pilot_seat_trim`, receiver: `${ID}:block/handset_receiver` }, "#plate", true, [
    solid([3, CONSOLE_HEIGHT, 4], [13, 7.5, 12], "#cradle"),
    solid([5, 7.5, 7], [11, 8.5, 9], "#receiver"),
    solid([3, 7.5, 6], [5, 9.5, 10], "#receiver"),
    solid([11, 7.5, 6], [13, 9.5, 10], "#receiver"),
  ]);
  facing("handset");
  write(`assets/${ID}/models/item/handset.json`, { parent: `${ID}:block/handset` });
  write(`assets/${ID}/models/block/tower_radio.json`, {
    parent: "minecraft:block/orientable",
    textures: { front: `${ID}:block/tower_radio_front`, side: `${ID}:block/tower_radio_side`, top: `${ID}:block/tower_radio_side` },
  });
  facing("tower_radio");
  write(`assets/${ID}/models/item/tower_radio.json`, { parent: `${ID}:block/tower_radio` });
  for (const name of ["handset", "tower_radio"]) {
    dropsItself(name);
    cubes[name] = null;
  }
}

// The control tower. The transponder: a module of the centre console, its panel facing whoever it looks at, with two
// code knobs at the front of it (its picture is drawn on the panel's screen, see TransponderRenderer). The radar
// screen: a monitor on a desk stand, drawn facing north, its screen's face on its north side (the scope is drawn on
// it, see TowerRadarRenderer).
{
  consoleModule("transponder", { panel: `${ID}:block/transponder_top`, knob: `${ID}:block/thrust_lever_knob` }, "#panel", true, [
    solid([3.5, CONSOLE_HEIGHT, 1.5], [5.5, 7.5, 3.5], "#knob"),
    solid([10.5, CONSOLE_HEIGHT, 1.5], [12.5, 7.5, 3.5], "#knob"),
  ]);
  facing("transponder");
  write(`assets/${ID}/models/item/transponder.json`, { parent: `${ID}:block/transponder` });
  const casing = { texture: "#casing" };
  write(`assets/${ID}/models/block/tower_radar.json`, {
    parent: "minecraft:block/block",
    textures: { casing: `${ID}:block/tower_radar_casing`, screen: `${ID}:block/tower_radar_screen`, particle: `${ID}:block/tower_radar_casing` },
    elements: [
      solid([3, 0, 6], [13, 1, 11], "#casing"),
      solid([7, 1, 8], [9, 3, 10], "#casing"),
      { from: [0.5, 3, 6], to: [15.5, 15, 10], faces: { north: { texture: "#screen" }, south: casing, east: casing, west: casing, up: casing, down: casing } },
    ],
  });
  facing("tower_radar");
  write(`assets/${ID}/models/item/tower_radar.json`, { parent: `${ID}:block/tower_radar` });
  write(`assets/${ID}/models/item/control_tower_blueprint.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/control_tower_blueprint` } });
  write(`assets/${ID}/models/item/runway_blueprint.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/runway_blueprint` } });
  // The motion sensor: a slim white housing screwed to the face of the wall behind it (south, facing north), low in its
  // block, so set over a door it sits on the door's head; its lens across its face, the lamp beside the lens lit while
  // it sees someone. Shifted, it sits half a block to the right as seen facing it, over the middle of a pair of doors.
  const sensorModel = (lit, shifted) => `${ID}:block/motion_sensor${shifted ? "_shifted" : ""}${lit ? "_on" : ""}`;
  for (const lit of [false, true]) {
    for (const shifted of [false, true]) {
      const side = { texture: "#side" };
      const left = shifted ? -5 : 3;
      write(`assets/${sensorModel(lit, shifted).replace(`${ID}:`, `${ID}/models/`)}.json`, {
        parent: "minecraft:block/block",
        textures: { particle: `${ID}:block/motion_sensor_side`, front: `${ID}:block/motion_sensor_front${lit ? "_on" : ""}`, side: `${ID}:block/motion_sensor_side` },
        elements: [{
          from: [left, 0, 13], to: [left + 10, 4, 16],
          faces: { north: { texture: "#front", uv: [3, 6, 13, 10] }, south: side, east: side, west: side, up: side, down: side },
        }],
      });
    }
  }
  write(`assets/${ID}/blockstates/motion_sensor.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].flatMap((on) => [false, true].map((shifted) =>
      [`facing=${facing},powered=${on},shifted=${shifted}`, { model: sensorModel(on, shifted), y }])))),
  });
  write(`assets/${ID}/models/item/motion_sensor.json`, { parent: `${ID}:block/motion_sensor` });
  // The lift panel: a slim plate of dark glass in the passenger screen's casing, drawn facing north with its back
  // against the south side of its block (see LiftPanelBlock); its renderer draws the floors over the glass.
  write(`assets/${ID}/models/block/lift_panel.json`, boxes(
    { screen: `${ID}:block/lift_panel`, casing: `${ID}:block/passenger_screen_casing`, particle: `${ID}:block/passenger_screen_casing` },
    [[[3, 1, 15], [13, 15, 16], { north: "#screen", all: "#casing" }]],
  ));
  facing("lift_panel");
  write(`assets/${ID}/models/item/lift_panel.json`, { parent: `${ID}:block/lift_panel` });
  for (const name of ["transponder", "tower_radar", "motion_sensor", "lift_panel"]) {
    dropsItself(name);
    cubes[name] = null;
  }
}

// The control tower's instruments (see ControlTower.instruments). The wind sock: a slim steel pole with a collar round
// its top; the sock flying from the collar is drawn by WindSockRenderer.
{
  write(`assets/${ID}/models/block/wind_sock.json`, {
    parent: "minecraft:block/block",
    textures: { pole: `${ID}:block/wind_sock_pole`, particle: `${ID}:block/wind_sock` },
    elements: [solid([7.5, 0, 7.5], [8.5, 16, 8.5], "#pole"), solid([7, 13, 7], [9, 15, 9], "#pole")],
  });
  write(`assets/${ID}/blockstates/wind_sock.json`, { variants: { "": { model: `${ID}:block/wind_sock` } } });
  write(`assets/${ID}/models/item/wind_sock.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/wind_sock` } });
  // The weather screen: the radar screen's monitor and stand; its report is drawn on it by AtisScreenRenderer.
  write(`assets/${ID}/models/block/atis_screen.json`, { parent: `${ID}:block/tower_radar` });
  facing("atis_screen");
  write(`assets/${ID}/models/item/atis_screen.json`, { parent: `${ID}:block/atis_screen` });
  for (const name of ["wind_sock", "atis_screen"]) {
    dropsItself(name);
    cubes[name] = null;
  }
  // The flight strip board: a wide screen in the tail camera display's dark glass and casing, upright or tilted; its
  // strips are drawn on it by FlightStripBoardRenderer.
  wideScreen("flight_strip_board", "tail_camera_display");
}
// The tower's instruments stand on the ground, never aboard an airliner (see GROUND).
function towerInstrumentsGround() {
  return ["wind_sock", "atis_screen", "flight_strip_board"];
}
// A wind sock: orange and white wool flown from iron bars. A weather screen: a clock behind blue glass, on iron. A
// flight strip board: black glass over paper strips and a compass.
function towerInstrumentsRecipes() {
  shaped("flight_strip_board", ["GGG", "PCP"], { G: "minecraft:black_stained_glass_pane", P: "minecraft:paper", C: "minecraft:compass" }, 1);
  shaped("wind_sock", ["IOW", "I  ", "I  "], { I: "minecraft:iron_bars", O: "minecraft:orange_wool", W: "minecraft:white_wool" }, 1);
  shaped("atis_screen", ["GGG", "GCG", "IRI"], { G: "minecraft:blue_stained_glass_pane", C: "minecraft:clock", I: "minecraft:iron_ingot", R: "minecraft:redstone" }, 1);
}
function towerInstrumentsLang() {
  return {
    "block.aerolarge.wind_sock": "Wind Sock",
    "block.aerolarge.atis_screen": "Weather Screen",
    "screen.aerolarge.atis.title": "ATIS",
    "screen.aerolarge.atis.information": "INFORMATION",
    "screen.aerolarge.atis.wind": "WIND",
    "screen.aerolarge.atis.wind.value": "%s° %sKT",
    "screen.aerolarge.atis.wind.calm": "CALM",
    "screen.aerolarge.atis.visibility": "VISIBILITY",
    "screen.aerolarge.atis.visibility.km": "%sKM",
    "screen.aerolarge.atis.visibility.metres": "%sM",
    "weather.aerolarge.clear": "CLEAR",
    "weather.aerolarge.rain": "RAIN",
    "weather.aerolarge.snow": "SNOW",
    "weather.aerolarge.thunder": "STORM",
    "block.aerolarge.flight_strip_board": "Flight Strip Board",
    "screen.aerolarge.flight_strips.column.callsign": "CALLSIGN",
    "screen.aerolarge.flight_strips.column.height": "ALT",
    "screen.aerolarge.flight_strips.column.distance": "DIST",
    "screen.aerolarge.flight_strips.column.heading": "HDG",
    "screen.aerolarge.flight_strips.column.phase": "STATUS",
    "screen.aerolarge.flight_strips.page": "PAGE %s/%s",
    "screen.aerolarge.flight_strips.none": "NO TRAFFIC",
    "screen.aerolarge.flight_strips.callsign": "%s%s",
    "screen.aerolarge.flight_strips.callsign.tower": "TWR",
    "screen.aerolarge.flight_strips.callsign.airliner": "AER",
    "screen.aerolarge.flight_strips.callsign.house": "HOME",
    "screen.aerolarge.flight_strips.callsign.star": "STAR",
    "screen.aerolarge.flight_strips.callsign.flag": "FLAG",
    "screen.aerolarge.flight_strips.callsign.heart": "HRT",
    "screen.aerolarge.flight_strips.callsign.gem": "GEM",
    "screen.aerolarge.flight_strips.callsign.tree": "TREE",
    "screen.aerolarge.flight_strips.callsign.anchor": "PORT",
    "screen.aerolarge.flight_strips.callsign.pickaxe": "MINE",
    "screen.aerolarge.flight_strips.phase.parked": "PARKED",
    "screen.aerolarge.flight_strips.phase.taxi": "TAXI",
    "screen.aerolarge.flight_strips.phase.climb": "CLIMB",
    "screen.aerolarge.flight_strips.phase.cruise": "CRUISE",
    "screen.aerolarge.flight_strips.phase.descend": "DESCEND",
    "screen.aerolarge.flight_strips.phase.inbound": "INBOUND",
    "screen.aerolarge.flight_strips.phase.fix": "FIX",
  };
}

// Buckling up. The seatbelt sign, dark or lit: lit, it glows at full brightness whatever the light around, a backlit
// sign that shows in a dark cabin without lighting it up. The seatbelt switch, a module of the centre console facing
// the pilots: its lamp dark or lit, and a toggle in front of it, pushed away when on.
{
  write(`assets/${ID}/models/block/seatbelt_sign.json`, {
    parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/seatbelt_sign` },
  });
  write(`assets/${ID}/models/block/seatbelt_sign_on.json`, {
    parent: "minecraft:block/block",
    textures: { all: `${ID}:block/seatbelt_sign_on`, particle: `${ID}:block/seatbelt_sign_on` },
    elements: [{
      from: [0, 0, 0], to: [16, 16, 16], shade: false,
      faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) =>
        [face, { texture: "#all", cullface: face, neoforge_data: { block_light: 15, sky_light: 15 } }])),
    }],
  });
  for (const lit of [false, true]) {
    const name = `seatbelt_switch${lit ? "_on" : ""}`;
    consoleModule(name, { panel: `${ID}:block/${name}`, toggle: `${ID}:block/thrust_lever_knob`, base: `${ID}:block/thrust_lever_handle` }, "#panel", true, [
      solid([7, CONSOLE_HEIGHT, 2.5], [9, 6.5, 4.5], "#base"),
      solid([7.5, 6.5, 3], [8.5, 9, 4], "#toggle", { origin: [8, 6.5, 3.5], axis: "x", angle: lit ? 22.5 : -22.5 }),
    ]);
  }
  // Plated, set into the hull: the sign on its north face only, looking into the cabin, the skin's plating on the
  // others, so it is not seen from outside; lit, only the sign glows.
  for (const lit of [false, true]) {
    const sign = { texture: "#sign", cullface: "north" };
    write(`assets/${ID}/models/block/seatbelt_sign_plated${lit ? "_on" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: { sign: `${ID}:block/seatbelt_sign${lit ? "_on" : ""}`, skin: `${ID}:block/airframe_panel`, particle: `${ID}:block/airframe_panel` },
      elements: [
        {
          from: [0, 0, 0], to: [16, 16, 16],
          faces: Object.fromEntries(["south", "east", "west", "up", "down"].map((face) => [face, { texture: "#skin", cullface: face }])),
        },
        {
          from: [0, 0, 0], to: [16, 16, 16], shade: !lit,
          faces: { north: lit ? { ...sign, neoforge_data: { block_light: 15, sky_light: 15 } } : sign },
        },
      ],
    });
  }
  write(`assets/${ID}/blockstates/seatbelt_sign.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].flatMap((lit) => [false, true].map((plated) =>
      [`facing=${facing},lit=${lit},plated=${plated}`, plated
        ? { model: `${ID}:block/seatbelt_sign_plated${lit ? "_on" : ""}`, y }
        : { model: `${ID}:block/seatbelt_sign${lit ? "_on" : ""}` }])))),
  });
  write(`assets/${ID}/blockstates/seatbelt_switch.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((on) =>
      [`facing=${facing},powered=${on}`, { model: `${ID}:block/seatbelt_switch${on ? "_on" : ""}`, y }]))),
  });
  write(`assets/${ID}/models/item/seatbelt_sign.json`, { parent: `${ID}:block/seatbelt_sign_on` });
  write(`assets/${ID}/models/item/seatbelt_switch.json`, { parent: `${ID}:block/seatbelt_switch_on` });
  for (const name of ["seatbelt_sign", "seatbelt_switch"]) {
    dropsItself(name);
    cubes[name] = null;
  }
}

// The cabin's lining and lights, drawn facing north. The light strip: a glowing bar along the top of the wall it is
// fixed to. The pendant light: a slim black cable down to a flat grey shade, glowing underneath; the cable alone for
// the blocks above it. Both glow at full brightness when bright, are lit only by their own lower light when dimmed,
// and glow a deep blue at night, and red for a door open in flight (see Brightness.ALARM). The overhead locker: a white bin against the wall, its door facing out, the passenger service
// panel underneath; open, its door shows the luggage inside.
{
  const brightness = { bright: ["", "light_strip", true], dim: ["_dim", "light_strip", false], night: ["_night", "light_strip_night", true] };
  for (const [suffix, glow, lit] of [...Object.values(brightness), ["_alarm", "light_strip_alarm", true]]) {
    write(`assets/${ID}/models/block/light_strip${suffix}.json`, boxes(
      { strip: `${ID}:block/${glow}`, particle: `${ID}:block/${glow}` },
      [[[0, 12, 0], [16, 15, 3], "#strip", lit]],
    ));
    write(`assets/${ID}/models/block/pendant_light${suffix}.json`, boxes(
      { cable: `${ID}:block/light_cable`, shade: `${ID}:block/pendant_shade`, glow: `${ID}:block/${glow}`, particle: `${ID}:block/pendant_shade` },
      [
        [[7.5, 8, 7.5], [8.5, 16, 8.5], "#cable"],
        [[3, 4.5, 3], [13, 8, 13], "#shade"],
        [[4, 4, 4], [12, 4.5, 12], "#glow", lit],
      ],
    ));
  }
  write(`assets/${ID}/blockstates/light_strip.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [
      ...Object.entries(brightness).map(([level, [suffix]]) =>
        [`alarm=false,brightness=${level},facing=${facing}`, { model: `${ID}:block/light_strip${suffix}`, y }]),
      [`alarm=true,facing=${facing}`, { model: `${ID}:block/light_strip_alarm`, y }],
    ])),
  });
  write(`assets/${ID}/blockstates/pendant_light.json`, {
    variants: {
      ...Object.fromEntries(Object.entries(brightness).map(([level, [suffix]]) => [`alarm=false,brightness=${level}`, { model: `${ID}:block/pendant_light${suffix}` }])),
      "alarm=true": { model: `${ID}:block/pendant_light_alarm` },
    },
  });
  write(`assets/${ID}/models/block/light_cable.json`, boxes(
    { cable: `${ID}:block/light_cable`, particle: `${ID}:block/light_cable` },
    [[[7.5, 0, 7.5], [8.5, 16, 8.5], "#cable"]],
  ));
  write(`assets/${ID}/blockstates/light_cable.json`, { variants: { "": { model: `${ID}:block/light_cable` } } });
  for (const open of [false, true]) {
    const name = `overhead_bin${open ? "_open" : ""}`;
    write(`assets/${ID}/models/block/${name}.json`, boxes(
      { front: `${ID}:block/${name}`, side: `${ID}:block/overhead_bin_side`, bottom: `${ID}:block/overhead_bin_bottom`, particle: `${ID}:block/overhead_bin_side` },
      [[[0, 3, 3], [16, 16, 16], { north: "#front", down: "#bottom", all: "#side" }]],
    ));
  }
  write(`assets/${ID}/blockstates/overhead_bin.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((open) =>
      [`facing=${facing},open=${open}`, { model: `${ID}:block/overhead_bin${open ? "_open" : ""}`, y }]))),
  });
  for (const name of ["light_strip", "pendant_light", "light_cable", "overhead_bin"]) {
    write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}` });
    dropsItself(name);
    cubes[name] = null;
  }
}

// The passenger screen: a slim seat-back display, drawn facing north with its back against the south side of its
// block, dark glass in a thin grey bezel; its renderer draws the flight information over the glass.
write(`assets/${ID}/models/block/passenger_screen.json`, boxes(
  { screen: `${ID}:block/passenger_screen`, casing: `${ID}:block/passenger_screen_casing`, particle: `${ID}:block/passenger_screen_casing` },
  [[[1, 3, 14], [15, 13, 16], { north: "#screen", all: "#casing" }]],
));
facing("passenger_screen");
write(`assets/${ID}/models/item/passenger_screen.json`, { parent: `${ID}:block/passenger_screen` });
dropsItself("passenger_screen");
cubes.passenger_screen = null;
// The stand a zeppelin's promenade screen stands on (see ScreenStandBlock): a brass foot along the back of its block
// and a teak post up from it, past the block's top into the casing of the screen standing over it, drawn as the
// screen it carries is, facing north with its back to the south side.
write(`assets/${ID}/models/block/screen_stand.json`, {
  parent: "minecraft:block/block",
  textures: { wood: `${ID}:block/zeppelin_wood`, brass: `${ID}:block/zeppelin_brass`, particle: `${ID}:block/zeppelin_wood` },
  elements: [patched([4, 0, 10], [12, 1, 16], "#brass"), patched([7, 1, 14], [9, 19, 16], "#wood")],
});
facing("screen_stand");
write(`assets/${ID}/models/item/screen_stand.json`, { parent: `${ID}:block/screen_stand` });
dropsItself("screen_stand");
cubes.screen_stand = null;

// The signal flare: a red paper tube stuck in the ground, its tip burning bright whatever the light around. Burnt or
// broken, it leaves nothing.
write(`assets/${ID}/models/block/signal_flare.json`, boxes(
  { tube: `${ID}:block/signal_flare`, tip: `${ID}:block/signal_flare_tip`, particle: `${ID}:block/signal_flare` },
  [
    [[7, 0, 7], [9, 6, 9], "#tube"],
    [[6.75, 6, 6.75], [9.25, 7, 9.25], "#tip", true],
  ],
));
write(`assets/${ID}/blockstates/signal_flare.json`, { variants: { "": { model: `${ID}:block/signal_flare` } } });
write(`assets/${ID}/models/item/signal_flare.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/signal_flare` } });

// The drop bay: hatch doors in yellow-and-black hazard stripes, shut, or open on a dark shaft with the loads' rails;
// the drop release, a module of the centre console: its red button under a striped cover, shut when safe and flipped
// up when armed.
for (const open of [false, true]) {
  write(`assets/${ID}/models/block/drop_bay${open ? "_open" : ""}.json`, {
    parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/drop_bay${open ? "_open" : ""}` },
  });
}
write(`assets/${ID}/blockstates/drop_bay.json`, {
  variants: { "open=false": { model: `${ID}:block/drop_bay` }, "open=true": { model: `${ID}:block/drop_bay_open` } },
});
write(`assets/${ID}/models/item/drop_bay.json`, { parent: `${ID}:block/drop_bay` });
for (const armed of [false, true]) {
  const name = `drop_release_${armed ? "armed" : "safe"}`;
  const cover = armed ? { origin: [8, CONSOLE_HEIGHT, 11], axis: "x", angle: 45 } : undefined;
  consoleModule(name, { panel: `${ID}:block/${name}`, button: `${ID}:block/jump_light_red`, cover: `${ID}:block/drop_release_cover` }, "#panel", true, [
    solid([6.25, CONSOLE_HEIGHT, 6.25], [9.75, 6.75, 9.75], "#button"),
    solid([5, CONSOLE_HEIGHT, 5], [11, 7.5, 11], "#cover", cover),
  ]);
}
write(`assets/${ID}/blockstates/drop_release.json`, {
  variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((armed) =>
    [`armed=${armed},facing=${facing}`, { model: `${ID}:block/drop_release_${armed ? "armed" : "safe"}`, y }]))),
});
write(`assets/${ID}/models/item/drop_release.json`, { parent: `${ID}:block/drop_release_safe` });
for (const name of ["drop_bay", "drop_release"]) {
  dropsItself(name);
  cubes[name] = null;
}

// The jet bridge: a steel tread plate set into the terminal floor, a white arrow on it pointing the way the bridge
// reaches out (north, as drawn) inside a hazard border; hazard stripes along the top of its sides.
write(`assets/${ID}/models/block/jet_bridge.json`, {
  parent: "minecraft:block/cube_top",
  textures: { top: `${ID}:block/jet_bridge_top`, side: `${ID}:block/jet_bridge_side` },
});
facing("jet_bridge");
write(`assets/${ID}/models/item/jet_bridge.json`, { parent: `${ID}:block/jet_bridge` });
dropsItself("jet_bridge");
cubes.jet_bridge = null;

// Villager passengers. The check-in desk: a blue counter with a boarding pass on its front, a screen and the pass
// printer's slot on top. The boarding pass: a flat icon.
write(`assets/${ID}/models/block/check_in_desk.json`, {
  parent: "minecraft:block/orientable",
  textures: { front: `${ID}:block/check_in_desk_front`, side: `${ID}:block/check_in_desk_side`, top: `${ID}:block/check_in_desk_top` },
});
facing("check_in_desk");
write(`assets/${ID}/models/item/check_in_desk.json`, { parent: `${ID}:block/check_in_desk` });
dropsItself("check_in_desk");
cubes.check_in_desk = null;
write(`assets/${ID}/models/item/boarding_pass.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/boarding_pass` } });

// Cabin service. The galley is drawn with the galley units below, as their trolley bay. The trolley, drawn as its
// item's model in the world as in the hand: an aluminium cart with drawers at both ends, a red stripe and small dark
// wheels. The meals, pretzels, ice cream and coffee: flat icons.
cubes.galley = null;
write(`assets/${ID}/models/item/trolley.json`, boxes(
  { side: `${ID}:item/trolley_side`, end: `${ID}:item/trolley_end`, top: `${ID}:item/trolley_top`, wheel: `${ID}:block/light_cable`,
    particle: `${ID}:item/trolley_side` },
  [
    [[2.5, 2, 0.5], [13.5, 16, 15.5], { east: "#side", west: "#side", north: "#end", south: "#end", all: "#top" }],
    [[3, 0, 1.5], [5, 2, 3.5], "#wheel"],
    [[11, 0, 1.5], [13, 2, 3.5], "#wheel"],
    [[3, 0, 12.5], [5, 2, 14.5], "#wheel"],
    [[11, 0, 12.5], [13, 2, 14.5], "#wheel"],
  ],
));
for (const food of ["chicken_meal", "pasta_meal", "pretzels", "ice_cream", "airline_coffee"]) {
  write(`assets/${ID}/models/item/${food}.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/${food}` } });
}

// The care package's crate: wooden, an orange parachute stencilled on its sides and a red cross on top, its front
// (the way it is sent off) with a slatted door. Broken, it keeps what is packed in it, as a shulker box does.
write(`assets/${ID}/models/block/cargo_crate.json`, {
  parent: "minecraft:block/orientable",
  textures: { front: `${ID}:block/cargo_crate_front`, side: `${ID}:block/cargo_crate_side`, top: `${ID}:block/cargo_crate_top` },
});
write(`assets/${ID}/blockstates/cargo_crate.json`, {
  variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/cargo_crate`, y }])),
});
write(`assets/${ID}/models/item/cargo_crate.json`, { parent: `${ID}:block/cargo_crate` });
write(`data/${ID}/loot_table/blocks/cargo_crate.json`, {
  type: "minecraft:block",
  pools: [{
    rolls: 1.0, bonus_rolls: 0.0,
    entries: [{
      type: "minecraft:item", name: `${ID}:cargo_crate`,
      functions: [{ function: "minecraft:copy_components", source: "block_entity", include: ["minecraft:custom_name", "minecraft:container"] }],
    }],
    conditions: [{ condition: "minecraft:survives_explosion" }],
  }],
  random_sequence: `${ID}:blocks/cargo_crate`,
});

// The hold and the tail: their fittings, recipes and names are in the functions below, which the shared lists
// further down take in. The floor hatch down to the hold: vanilla's trapdoor shapes over the hatch's own plate,
// turned with the way it faces so its ladder runs along the airliner.
{
  const hatch = "floor_hatch";
  for (const part of ["bottom", "top", "open"]) {
    write(`assets/${ID}/models/block/${hatch}_${part}.json`, { parent: `minecraft:block/template_trapdoor_${part}`, textures: { texture: `${ID}:block/${hatch}` } });
  }
  write(`assets/${ID}/blockstates/${hatch}.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["bottom", "top"].flatMap((half) => [
      [`facing=${facing},half=${half},open=false`, { model: `${ID}:block/${hatch}_${half}`, y }],
      [`facing=${facing},half=${half},open=true`, { model: `${ID}:block/${hatch}_open`, y }],
    ]))),
  });
  write(`assets/${ID}/models/item/${hatch}.json`, { parent: `${ID}:block/${hatch}_bottom` });
  dropsItself(hatch);
  cubes[hatch] = null;
}
// The secret bookshelf door, a door underneath: shut, its lower half is vanilla's bookshelf with the glowing book
// drawn over both faces along the way it faces, at full brightness, and its upper half is vanilla's bookshelf; open,
// vanilla's door leaf shapes with the bookshelf on them, turned as vanilla turns its doors'. Only the lower half drops
// the door, as with doors.
{
  const door = "secret_bookshelf";
  const book = { texture: "#book", neoforge_data: { block_light: 15, sky_light: 15 } };
  write(`assets/${ID}/models/block/${door}_bottom.json`, {
    parent: "minecraft:block/block",
    render_type: "minecraft:cutout",
    textures: { side: "minecraft:block/bookshelf", end: "minecraft:block/oak_planks", book: `${ID}:block/secret_bookshelf_book`, particle: "minecraft:block/bookshelf" },
    elements: [
      {
        from: [0, 0, 0], to: [16, 16, 16],
        faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) =>
          [face, { texture: face === "up" || face === "down" ? "#end" : "#side", cullface: face }])),
      },
      { from: [0, 0, -0.1], to: [16, 16, 16.1], shade: false, faces: { north: book, south: book } },
    ],
  });
  for (const half of ["bottom", "top"]) {
    for (const hinge of ["left", "right"]) {
      write(`assets/${ID}/models/block/${door}_${half}_${hinge}_open.json`, {
        parent: `minecraft:block/door_${half}_${hinge}_open`,
        textures: { bottom: "minecraft:block/bookshelf", top: "minecraft:block/bookshelf" },
      });
    }
  }
  write(`assets/${ID}/blockstates/${door}.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [["lower", "bottom"], ["upper", "top"]].flatMap(([half, model]) => [
      [`facing=${facing},half=${half},hinge=left,open=false`, { model: half === "lower" ? `${ID}:block/${door}_bottom` : "minecraft:block/bookshelf", y }],
      [`facing=${facing},half=${half},hinge=right,open=false`, { model: half === "lower" ? `${ID}:block/${door}_bottom` : "minecraft:block/bookshelf", y }],
      [`facing=${facing},half=${half},hinge=left,open=true`, { model: `${ID}:block/${door}_${model}_left_open`, y }],
      [`facing=${facing},half=${half},hinge=right,open=true`, { model: `${ID}:block/${door}_${model}_right_open`, y: (y + 180) % 360 }],
    ]))),
  });
  write(`assets/${ID}/models/item/${door}.json`, { parent: `${ID}:block/${door}_bottom` });
  write(`data/${ID}/loot_table/blocks/${door}.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0, bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item", name: `${ID}:${door}`,
        conditions: [{ condition: "minecraft:block_state_property", block: `${ID}:${door}`, properties: { half: "lower" } }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/${door}`,
  });
  cubes[door] = null;
}
// The ball pit: a plain cube of balls, its faces shown inside it too, so someone sunk in it sees balls all round. It
// sounds its own steps from inside, as powder snow does.
{
  write(`assets/${ID}/blockstates/ball_pit.json`, { variants: { "": { model: `${ID}:block/ball_pit` } } });
  write(`assets/${ID}/models/block/ball_pit.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/ball_pit` } });
  write(`assets/${ID}/models/item/ball_pit.json`, { parent: `${ID}:block/ball_pit` });
  write("data/minecraft/tags/block/inside_step_sound_blocks.json", { replace: false, values: [`${ID}:ball_pit`] });
  dropsItself("ball_pit");
  cubes.ball_pit = null;
}
// Fairy lights, drawn fixed to the north wall like the light strip they are: a black wire along the top of the wall,
// four bulbs hanging from it. Bright, the bulbs glow at full brightness; dimmed, they are lit only by their own lower
// light; at night they glow dim.
{
  for (const [suffix, bulbs, lit] of [["", "fairy_lights", true], ["_dim", "fairy_lights", false], ["_night", "fairy_lights_night", true]]) {
    write(`assets/${ID}/models/block/fairy_lights${suffix}.json`, boxes(
      { wire: `${ID}:block/light_cable`, bulbs: `${ID}:block/${bulbs}`, particle: `${ID}:block/${bulbs}` },
      [
        [[0, 14, 0.5], [16, 14.5, 1.5], "#wire"],
        ...[1, 5, 9, 13].map((x) => [[x, 12, 1], [x + 2, 14, 3], "#bulbs", lit]),
      ],
    ));
  }
  const levels = { bright: "", dim: "_dim", night: "_night" };
  write(`assets/${ID}/blockstates/fairy_lights.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => Object.entries(levels).map(([level, suffix]) =>
      [`brightness=${level},facing=${facing}`, { model: `${ID}:block/fairy_lights${suffix}`, y }]))),
  });
  write(`assets/${ID}/models/item/fairy_lights.json`, { parent: `${ID}:block/fairy_lights` });
  dropsItself("fairy_lights");
  cubes.fairy_lights = null;
}
// The den's bunk (see BunkBlock), drawn facing north, its open side to the north and its back to the south, in two
// halves, the head's end to the east and the foot's to the west. Both: a white frame, the mattress on it, quilted, and
// the blanket over it, tinted to the bunk's colour (tintindex 0). The head: the blanket folded back under a white
// cuff short of the pillow, the headboard at its end, and along its open side the rail, its padding tinted like the
// blanket under a white top bar on a post; on the headboard's inside a chrome arm holding the reading light over the
// pillow, its shade lit at full brightness while it is on. The foot: the blanket drawn right over it and hanging down
// its front, and the footboard at its end. In the hand both halves side by side, smaller. Only the head drops the bunk,
// in its colour.
{
  const block = (name) => `${ID}:block/${name}`;
  // A box, every face showing the texture over the part of it the box covers, tinted if the blanket's colour; a face
  // given its own texture in faces.
  const part = (from, to, texture, tinted = false, faces = {}) => ({
    from, to,
    faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((side) => [side, {
      texture: faces[side] ?? texture, ...(tinted && !faces[side] ? { tintindex: 0 } : {}),
    }])),
  });
  const textures = (lit) => ({
    frame: block("bunk_frame"), mattress: block("bunk_mattress"), blanket: block("bunk_blanket"), pillow: block("bunk_pillow"),
    rail: block("bunk_rail"), chrome: block("lavatory_basin_chrome"), lamp: block(lit ? "bunk_lamp_lit" : "bunk_lamp"), particle: block("bunk_blanket"),
  });
  // The reading light's head: chrome, its shade's underside and its side toward the foot the light, glowing while lit.
  const lamp = (lit) => {
    const element = part([11.5, 10.5, 11], [14, 12.5, 14], "#chrome", false, { down: "#lamp", west: "#lamp" });
    if (lit) {
      element.shade = false;
      for (const side of ["down", "west"]) element.faces[side].neoforge_data = { block_light: 15, sky_light: 15 };
    }
    return element;
  };
  const head = (lit) => [
    part([0, 0, 1], [15, 3, 16], "#frame"),
    part([0, 3, 1.5], [15, 7, 15.5], "#mattress"),
    part([0, 7, 1.25], [8.5, 7.75, 15.75], "#blanket", true),
    part([8.5, 7, 1.25], [9.5, 8, 15.75], "#pillow"),
    part([10, 7, 3], [14.5, 9.5, 13], "#pillow"),
    part([15, 0, 1], [16, 14, 16], "#frame"),
    part([1.5, 4, 0.5], [15, 11, 1.5], "#rail", true),
    part([0.5, 11, 0.25], [15, 12, 1.75], "#frame"),
    part([0.5, 3, 0.25], [1.5, 11, 1.75], "#frame"),
    part([13, 12.5, 12.5], [15, 13.25, 13.25], "#chrome"),
    lamp(lit),
  ];
  const foot = [
    part([1, 0, 1], [16, 3, 16], "#frame"),
    part([1, 3, 1.5], [16, 7, 15.5], "#mattress"),
    part([1, 7, 1.25], [16, 7.75, 15.75], "#blanket", true),
    part([1, 4, 1], [16, 7.75, 1.5], "#blanket", true),
    part([0, 0, 1], [1, 10, 16], "#frame"),
  ];
  for (const lit of [false, true]) {
    write(`assets/${ID}/models/block/bunk_head${lit ? "_lit" : ""}.json`, { parent: "minecraft:block/block", textures: textures(lit), elements: head(lit) });
  }
  write(`assets/${ID}/models/block/bunk_foot.json`, { parent: "minecraft:block/block", textures: textures(false), elements: foot });
  write(`assets/${ID}/blockstates/bunk.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].flatMap((lit) => ["head", "foot"].map((half) =>
      [`facing=${facing},lit=${lit},part=${half}`, { model: `${ID}:block/bunk_${half === "head" && lit ? "head_lit" : half}`, ...(y ? { y } : {}) }])))),
  });
  // In the hand the halves side by side, the foot to the west of the head, both moved east half a block so the bunk is
  // held by its middle.
  const shifted = (elements, dx) => elements.map((element) => ({ ...element, from: [element.from[0] + dx, element.from[1], element.from[2]], to: [element.to[0] + dx, element.to[1], element.to[2]] }));
  write(`assets/${ID}/models/item/bunk.json`, {
    parent: "minecraft:block/block",
    textures: textures(false),
    elements: [...shifted(head(false), 8), ...shifted(foot, -8)],
    display: {
      gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.45, 0.45, 0.45] },
      ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.2, 0.2, 0.2] },
      fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
      thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.25, 0.25, 0.25] },
      firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.3, 0.3, 0.3] },
      firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.3, 0.3, 0.3] },
    },
  });
  const bunk = `${ID}:bunk`;
  write(`data/${ID}/loot_table/blocks/bunk.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item",
        name: bunk,
        functions: [{
          function: "minecraft:copy_state", block: bunk, properties: ["color"],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: bunk, properties: { color: "light_blue" } } }],
        }],
      }],
      conditions: [
        { condition: "minecraft:block_state_property", block: bunk, properties: { part: "head" } },
        { condition: "minecraft:survives_explosion" },
      ],
    }],
    random_sequence: `${ID}:blocks/bunk`,
  });
  cubes.bunk = null;

  // The bean bag (see BeanBagBlock), drawn facing north, the sitter's back to the south: the bag squashed out over the
  // floor, bulging at its middle, slumped into a seat with its sides bunched up either side and its back heaped up
  // behind, all of it tinted to its colour; a dark carrying strap across the top of the back.
  write(`assets/${ID}/models/block/bean_bag.json`, {
    parent: "minecraft:block/block",
    textures: { fabric: block("bean_bag"), strap: block("bean_bag_strap"), particle: block("bean_bag") },
    elements: [
      part([1.5, 0, 1.5], [14.5, 1.5, 14.5], "#fabric", true),
      part([0.5, 1.5, 0.5], [15.5, 5, 15.5], "#fabric", true),
      part([1.5, 5, 1], [14.5, 6.5, 10.5], "#fabric", true),
      part([0.5, 5, 2], [3, 8, 10.5], "#fabric", true),
      part([13, 5, 2], [15.5, 8, 10.5], "#fabric", true),
      part([1, 5, 10], [15, 10.5, 15.5], "#fabric", true),
      part([2.5, 10.5, 11], [13.5, 12.5, 15], "#fabric", true),
      part([6.5, 12.5, 12.5], [9.5, 13.25, 14.5], "#strap"),
    ],
  });
  facing("bean_bag");
  write(`assets/${ID}/models/item/bean_bag.json`, { parent: `${ID}:block/bean_bag` });
  const bag = `${ID}:bean_bag`;
  write(`data/${ID}/loot_table/blocks/bean_bag.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item",
        name: bag,
        functions: [{
          function: "minecraft:copy_state", block: bag, properties: ["color"],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: bag, properties: { color: "orange" } } }],
        }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/bean_bag`,
  });
  cubes.bean_bag = null;
}
// The den's disco ball (see DiscoBallBlock): the ball itself, turning, is drawn by its renderer over its mirror tile's
// texture (see DiscoBallRenderer), so the block's model is only what it hangs by, on or off: a slim cable from the top
// of the block down to a chrome cap on top of the ball. Its particles are the ball's mirror tiles; in the hand, a
// picture of the ball.
{
  write(`assets/${ID}/models/block/disco_ball.json`, boxes(
    { rod: `${ID}:block/light_cable`, cap: `${ID}:block/lavatory_basin_chrome`, particle: `${ID}:block/disco_ball` },
    [
      [[7.5, 13, 7.5], [8.5, 16, 8.5], "#rod"],
      [[7, 12.5, 7], [9, 13.5, 9], "#cap"],
    ],
  ));
  write(`assets/${ID}/blockstates/disco_ball.json`, {
    variants: Object.fromEntries([false, true].map((lit) => [`lit=${lit}`, { model: `${ID}:block/disco_ball` }])),
  });
  write(`assets/${ID}/models/item/disco_ball.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/disco_ball` } });
  dropsItself("disco_ball");
  cubes.disco_ball = null;
}
// The den's furniture is fitted into it rather than built into the airliner: breakable, and no part of the airframe.
function denFurnitureFittings() {
  return ["bunk", "bean_bag", "disco_ball"];
}
// A bunk: light blue wool on birch planks, with glowstone for the reading light. A bean bag: orange wool round a
// handful of cocoa beans. A disco ball: glass panes round an amethyst shard, on glowstone for its light, hung from an
// iron nugget.
function denFurnitureRecipes() {
  shaped("bunk", ["G  ", "WWW", "PPP"], { G: "minecraft:glowstone_dust", W: "minecraft:light_blue_wool", P: "minecraft:birch_planks" }, 1);
  shaped("bean_bag", ["WCW", "WWW"], { W: "minecraft:orange_wool", C: "minecraft:cocoa_beans" }, 1);
  shaped("disco_ball", [" N ", "PAP", "PGP"], {
    N: "minecraft:iron_nugget", P: "minecraft:glass_pane", A: "minecraft:amethyst_shard", G: "minecraft:glowstone_dust",
  }, 1);
}
function denFurnitureLang() {
  return {
    "block.aerolarge.bunk": "Bunk",
    "block.aerolarge.bean_bag": "Bean Bag",
    "block.aerolarge.disco_ball": "Disco Ball",
  };
}
// The den's treasure chest: cookies, a golden apple, a signal flare, a basketball and three twinkling star rockets.
write(`data/${ID}/loot_table/chests/den_treasure.json`, {
  type: "minecraft:chest",
  pools: [
    ["minecraft:cookie", { type: "minecraft:uniform", min: 4, max: 8 }],
    ["minecraft:golden_apple", 1],
    [`${ID}:signal_flare`, 1],
    [`${ID}:basketball`, 1],
  ].map(([name, count]) => ({
    rolls: 1.0, bonus_rolls: 0.0,
    entries: [{ type: "minecraft:item", name, functions: [{ function: "minecraft:set_count", count }] }],
  })).concat([{
    rolls: 1.0, bonus_rolls: 0.0,
    entries: [{
      type: "minecraft:item", name: "minecraft:firework_rocket",
      functions: [
        { function: "minecraft:set_count", count: 3 },
        {
          function: "minecraft:set_components",
          components: { "minecraft:fireworks": { flight_duration: 1, explosions: [{ shape: "star", colors: [0xff5050, 0xffe050, 0x60a8ff], has_twinkle: true }] } },
        },
      ],
    }],
  }]),
  random_sequence: `${ID}:chests/den_treasure`,
});
// Suitcases, drawn facing north: a hard shell standing on its end, a black handle on top; one model per shell colour,
// and the golden suitcase's, whatever its colour. Broken, a suitcase gives itself back and spills what is in it.
{
  const colours = ["red", "blue", "green", "purple"];
  for (const name of [...colours.map((colour) => `suitcase_${colour}`), "golden_suitcase"]) {
    write(`assets/${ID}/models/block/${name}.json`, boxes(
      { case: `${ID}:block/${name}`, handle: `${ID}:block/light_cable`, particle: `${ID}:block/${name}` },
      [
        [[2, 0, 5], [14, 13, 11], "#case"],
        [[6, 13, 7.5], [7, 15, 8.5], "#handle"],
        [[9, 13, 7.5], [10, 15, 8.5], "#handle"],
        [[6, 15, 7.5], [10, 16, 8.5], "#handle"],
      ],
    ));
  }
  write(`assets/${ID}/blockstates/suitcase.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => colours.map((colour) =>
      [`color=${colour},facing=${facing}`, { model: `${ID}:block/suitcase_${colour}`, y }]))),
  });
  facing("golden_suitcase");
  write(`assets/${ID}/models/item/suitcase.json`, { parent: `${ID}:block/suitcase_red` });
  write(`assets/${ID}/models/item/golden_suitcase.json`, { parent: `${ID}:block/golden_suitcase` });
  for (const name of ["suitcase", "golden_suitcase"]) {
    dropsItself(name);
    cubes[name] = null;
  }
}
// What is packed in the hold. A suitcase: two to four of socks, paper, a book, cookies, string, leather, berries, a
// dye, a clock or a spyglass. A golden suitcase: gold, golden carrots, a diamond and two golden star rockets. A care
// package: bread, apples, torches, steak and a signal flare to mark where it lands.
{
  const entry = (name, min = 1, max = min, weight = 1) => ({
    type: "minecraft:item", name, weight,
    ...(max > 1 ? { functions: [{ function: "minecraft:set_count", count: min === max ? min : { type: "minecraft:uniform", min, max } }] } : {}),
  });
  const chest = (name, pools) => write(`data/${ID}/loot_table/chests/${name}.json`, {
    type: "minecraft:chest", pools, random_sequence: `${ID}:chests/${name}`,
  });
  const each = (entries) => entries.map((one) => ({ rolls: 1.0, bonus_rolls: 0.0, entries: [one] }));
  chest("suitcase", [{
    rolls: { type: "minecraft:uniform", min: 2, max: 4 }, bonus_rolls: 0.0,
    entries: [
      entry("minecraft:white_wool", 1, 2, 3), entry("minecraft:paper", 1, 3, 2), entry("minecraft:book"), entry("minecraft:cookie", 1, 3, 3),
      entry("minecraft:string", 1, 2, 2), entry("minecraft:leather"), entry("minecraft:sweet_berries", 2, 4, 2), entry("minecraft:red_dye"),
      entry("minecraft:yellow_dye"), entry("minecraft:light_blue_dye"), entry("minecraft:clock"), entry("minecraft:spyglass"),
    ],
  }]);
  chest("golden_suitcase", each([
    entry("minecraft:gold_ingot", 2, 4), entry("minecraft:golden_carrot", 3), entry("minecraft:diamond"),
    {
      ...entry("minecraft:firework_rocket", 2),
      functions: [
        { function: "minecraft:set_count", count: 2 },
        {
          function: "minecraft:set_components",
          components: { "minecraft:fireworks": { flight_duration: 1, explosions: [{ shape: "star", colors: [0xffd040], has_twinkle: true }] } },
        },
      ],
    },
  ]));
  chest("care_package", each([
    entry("minecraft:bread", 3, 5), entry("minecraft:apple", 2, 4), entry("minecraft:torch", 8, 16), entry("minecraft:cooked_beef", 2, 3),
    entry(`${ID}:signal_flare`),
  ]));
}
// Lost and Found, in the First Flight tab: one criterion for each of the hold's golden suitcases (GOLDEN_SUITCASES in
// SuitcaseBlockEntity), granted as each is first opened; it is done once all three are found.
write(`data/${ID}/advancement/first_flight/lost_and_found.json`, {
  parent: `${ID}:first_flight/root`,
  display: {
    icon: { id: `${ID}:golden_suitcase` },
    title: { translate: `advancements.${ID}.first_flight.lost_and_found.title` },
    description: { translate: `advancements.${ID}.first_flight.lost_and_found.description` },
    frame: "goal",
  },
  criteria: Object.fromEntries([1, 2, 3].map((index) => [`suitcase_${index}`, { trigger: "minecraft:impossible" }])),
});
// What of the hold's and the tail's is fitted rather than built in: breakable, and no part of the airframe.
function holdAndTailFittings() {
  return ["floor_hatch", "secret_bookshelf", "ball_pit", "fairy_lights", "suitcase", "golden_suitcase"];
}
// The secret door's creak is a wooden door's, deep and slow; the ball pit's plop a chicken's egg or a pop.
function holdAndTailSounds() {
  return {
    secret_bookshelf_open: {
      subtitle: `subtitles.${ID}.secret_bookshelf_open`,
      sounds: ["open1", "open2"].map((file) => ({ name: `minecraft:block/wooden_door/${file}`, pitch: 0.55 })),
    },
    secret_bookshelf_close: {
      subtitle: `subtitles.${ID}.secret_bookshelf_close`,
      sounds: ["close1", "close2", "close3"].map((file) => ({ name: `minecraft:block/wooden_door/${file}`, pitch: 0.6 })),
    },
    ball_pit_plop: {
      subtitle: `subtitles.${ID}.ball_pit_plop`,
      sounds: [{ name: "minecraft:mob/chicken/plop", pitch: 0.8 }, { name: "minecraft:random/pop", volume: 0.6, pitch: 0.7 }],
    },
  };
}
function holdAndTailRecipes() {
  shaped("floor_hatch", ["YTY"], { Y: "minecraft:yellow_dye", T: "minecraft:iron_trapdoor" }, 1);
  shaped("secret_bookshelf", ["BG", "BL"], { B: "minecraft:bookshelf", G: "minecraft:glowstone_dust", L: "minecraft:lever" }, 1);
  shaped("ball_pit", ["RYB", "WWW"], { R: "minecraft:red_dye", Y: "minecraft:yellow_dye", B: "minecraft:blue_dye", W: "minecraft:white_wool" }, 3);
  shaped("fairy_lights", ["RYB", "SGS"], { R: "minecraft:red_dye", Y: "minecraft:yellow_dye", B: "minecraft:blue_dye", S: "minecraft:string", G: "minecraft:glowstone_dust" }, 6);
  // A golden suitcase is found, not made.
  shaped("suitcase", [" N ", "LCL"], { N: "minecraft:iron_nugget", L: "minecraft:leather", C: "minecraft:chest" }, 1);
}
function holdAndTailLang() {
  return {
    "block.aerolarge.floor_hatch": "Floor Hatch",
    "block.aerolarge.secret_bookshelf": "Secret Bookshelf Door",
    "block.aerolarge.ball_pit": "Ball Pit",
    "block.aerolarge.fairy_lights": "Fairy Lights",
    "subtitles.aerolarge.secret_bookshelf_open": "Bookshelf creaks open",
    "subtitles.aerolarge.secret_bookshelf_close": "Bookshelf creaks shut",
    "subtitles.aerolarge.ball_pit_plop": "Ball pit plops",
    "block.aerolarge.suitcase": "Suitcase",
    "block.aerolarge.golden_suitcase": "Golden Suitcase",
    "container.aerolarge.suitcase": "Suitcase",
    "container.aerolarge.golden_suitcase": "Golden Suitcase",
    "message.aerolarge.golden_suitcase.found": "A golden suitcase! %s of %s found",
    "advancements.aerolarge.first_flight.lost_and_found.title": "Lost and Found",
    "advancements.aerolarge.first_flight.lost_and_found.description": "Find the three golden suitcases hidden in an airliner's hold",
  };
}

// The cargo net (see CargoNetBlock), each part drawn for the arm toward the north and turned for the others, as a
// pane's are: the webbing, a sheet half a pixel thick down the middle of the block from its middle to its edge, seen
// through between its straps; along the arm's top the steel track the net hangs from, and along its foot a steel bar
// with a double-stud fitting locked into the floor, each only where no net is joined above or below. Wherever the net
// does not run on through the block (a free end, a corner, a net on its own), an edge strap down the middle with a
// steel fitting at its top and its foot. In the hand, the webbing.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = { net: block("cargo_net"), strap: block("cargo_net_strap"), steel: block("cargo_net_steel"), particle: block("cargo_net") };
  const model = (name, elements) => write(`assets/${ID}/models/block/${name}.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:cutout", textures, elements,
  });
  model("cargo_net_side", [{ from: [7.75, 0, 0], to: [8.25, 16, 8], faces: { east: { texture: "#net" }, west: { texture: "#net" } } }]);
  model("cargo_net_side_top", [solid([7.25, 15, 0], [8.75, 16, 8], "#steel")]);
  model("cargo_net_side_foot", [solid([7, 0, 0], [9, 1, 8], "#steel"), solid([6.5, 0, 2.5], [9.5, 1.5, 5.5], "#steel")]);
  model("cargo_net_strap", [solid([7.25, 0, 7.25], [8.75, 16, 8.75], "#strap")]);
  model("cargo_net_strap_top", [solid([6.75, 14.5, 6.75], [9.25, 16, 9.25], "#steel")]);
  model("cargo_net_strap_foot", [solid([6.75, 0, 6.75], [9.25, 1.5, 9.25], "#steel")]);
  const turned = (side) => (yRot[side] ? { y: yRot[side] } : {});
  // The edge strap: wherever neither the north and south arms nor the east and west ones are both joined.
  const strap = (extra) => ({
    OR: [["north", "east"], ["north", "west"], ["south", "east"], ["south", "west"]].map(([a, b]) => ({ [a]: "false", [b]: "false", ...extra })),
  });
  write(`assets/${ID}/blockstates/cargo_net.json`, {
    multipart: [
      ...Object.keys(yRot).flatMap((side) => [
        { when: { [side]: "true" }, apply: { model: block("cargo_net_side"), ...turned(side) } },
        { when: { [side]: "true", up: "false" }, apply: { model: block("cargo_net_side_top"), ...turned(side) } },
        { when: { [side]: "true", down: "false" }, apply: { model: block("cargo_net_side_foot"), ...turned(side) } },
      ]),
      { when: strap({}), apply: { model: block("cargo_net_strap") } },
      { when: strap({ up: "false" }), apply: { model: block("cargo_net_strap_top") } },
      { when: strap({ down: "false" }), apply: { model: block("cargo_net_strap_foot") } },
    ],
  });
  write(`assets/${ID}/models/item/cargo_net.json`, { parent: "minecraft:item/generated", textures: { layer0: block("cargo_net") } });
  dropsItself("cargo_net");
  cubes.cargo_net = null;
}
// The net is fitted into the hold rather than built into the airliner: breakable, and no part of the airframe.
function cargoNetFittings() {
  return ["cargo_net"];
}
// String webbing on iron nugget fittings.
function cargoNetRecipes() {
  shaped("cargo_net", ["N N", "SSS", "SSS"], { N: "minecraft:iron_nugget", S: "minecraft:string" }, 6);
}
function cargoNetLang() {
  return {
    "block.aerolarge.cargo_net": "Cargo Net",
  };
}

// The hold's floor (see CargoFloorBlock), drawn with its tie-down rail along the north edge: a panel of anti-slip
// plating a pixel thick; on it, along the north edge, the yellow seat track half a pixel proud, its slot and round
// cut-outs along it; and down the middle the steel roller track a pixel proud between two lips, its rollers across it.
// The ball mat (see BallMatBlock): the same plating, and on it sixteen polished steel balls in rows of four, standing
// a pixel proud in their sockets. Both run on into the next panel along them, and are held as a carpet is.
{
  const block = (name) => `${ID}:block/${name}`;
  const plate = (texture) => ({
    from: [0, 0, 0], to: [16, 1, 16],
    faces: {
      up: { texture }, down: { texture, cullface: "down" },
      ...Object.fromEntries(["north", "south", "east", "west"].map((side) => [side, { texture, uv: [0, 15, 16, 16], cullface: side }])),
    },
  });
  // A strip along the panel from its west edge to its east, its ends against the next panel's.
  const strip = (from, to, texture, top, side) => ({
    from, to,
    faces: {
      up: { texture, uv: top }, north: { texture, uv: side }, south: { texture, uv: side },
      east: { texture, uv: side, cullface: "east" }, west: { texture, uv: side, cullface: "west" },
    },
  });
  write(`assets/${ID}/models/block/cargo_floor.json`, {
    parent: "minecraft:block/block",
    textures: {
      plate: block("cargo_floor_plate"), rail: block("cargo_floor_rail"), roller: block("cargo_floor_roller"), steel: block("cargo_net_steel"),
      particle: block("cargo_floor_plate"),
    },
    elements: [
      plate("#plate"),
      strip([0, 1, 0.5], [16, 1.5, 3.5], "#rail", [0, 0, 16, 3], [0, 4, 16, 4.5]),
      strip([0, 1, 7], [16, 1.75, 7.5], "#steel", [0, 0, 16, 0.5], [0, 0, 16, 0.75]),
      strip([0, 1, 11.5], [16, 1.75, 12], "#steel", [0, 0, 16, 0.5], [0, 0, 16, 0.75]),
      strip([0, 1, 7.5], [16, 2, 11.5], "#roller", [0, 0, 16, 4], [0, 0, 16, 1]),
    ],
  });
  facing("cargo_floor");
  const balls = [1, 5, 9, 13].flatMap((x) => [1, 5, 9, 13].map((z) => solid([x, 1, z], [x + 2, 2, z + 2], "#ball")));
  write(`assets/${ID}/models/block/ball_mat.json`, {
    parent: "minecraft:block/block",
    textures: { plate: block("ball_mat"), ball: block("ball_mat_ball"), particle: block("ball_mat") },
    elements: [plate("#plate"), ...balls],
  });
  write(`assets/${ID}/blockstates/ball_mat.json`, { variants: { "": { model: block("ball_mat") } } });
  for (const name of ["cargo_floor", "ball_mat"]) {
    write(`assets/${ID}/models/item/${name}.json`, { parent: block(name) });
    dropsItself(name);
    cubes[name] = null;
  }
}
// The floor panels are laid in the hold rather than built into the airliner: breakable, and no part of the airframe.
function cargoFloorFittings() {
  return ["cargo_floor", "ball_mat"];
}
// Smooth stone plating with iron nuggets for its rollers or its balls, and the rail's yellow paint.
function cargoFloorRecipes() {
  shaped("cargo_floor", ["NYN", "SSS"], { N: "minecraft:iron_nugget", Y: "minecraft:yellow_dye", S: "minecraft:smooth_stone_slab" }, 3);
  shaped("ball_mat", ["NNN", "SSS"], { N: "minecraft:iron_nugget", S: "minecraft:smooth_stone_slab" }, 3);
}
function cargoFloorLang() {
  return {
    "block.aerolarge.cargo_floor": "Cargo Floor",
    "block.aerolarge.ball_mat": "Ball Mat",
  };
}

// The ULD container (see UldContainerBlock), an LD3 of eight blocks, one for each corner, each drawn facing north and
// worked out in the container's own frame, thirty-two pixels each way: its door at the north, its contour along the
// west. Each corner is its part of the container's box, and shows only the container's outside: the door, the back
// and the flat east side each one picture over the whole face (see the textures), the top and the contour's upright
// part the ribbed sheet. At the lower corners on the contour side the box steps in toward the base, under a plate
// turned 22.5 degrees about the container's length, the contour's slant: from 5.8 pixels in at the base out to the
// full width 14 pixels up. The upper corners on the flat side carry the stencilled id, one model for each serial. In
// the hand, the whole container; only the corner at the door's foot on the flat side drops it.
{
  const block = (name) => `${ID}:block/${name}`;
  const SLANT_FOOT = 5.8;
  const SLANT_TOP = 14;
  const STEP = 2;
  const slantIn = (y) => SLANT_FOOT * (1 - y / SLANT_TOP);
  // A face's uv on a picture spread over the container's whole face, as seen from outside, for a box's corners.
  const spread = (face, [x0, y0, z0], [x1, y1, z1]) => ({
    north: [(32 - x1) / 2, (32 - y1) / 2, (32 - x0) / 2, (32 - y0) / 2],
    south: [x0 / 2, (32 - y1) / 2, x1 / 2, (32 - y0) / 2],
    east: [(32 - z1) / 2, (32 - y1) / 2, (32 - z0) / 2, (32 - y0) / 2],
  })[face];
  // A box of the container, in its frame, with the faces of it on the container's outside.
  const box = (from, to) => {
    const faces = {};
    if (from[2] === 0) faces.north = { texture: "#door", uv: spread("north", from, to) };
    if (to[2] === 32) faces.south = { texture: "#back", uv: spread("south", from, to) };
    if (to[0] === 32) faces.east = { texture: "#side", uv: spread("east", from, to) };
    if (from[0] === 0) faces.west = { texture: "#sheet" };
    if (to[1] === 32) faces.up = { texture: "#sheet" };
    if (from[1] === 0) faces.down = { texture: "#sheet" };
    return { from, to, faces };
  };
  // A corner's boxes in the container's frame: its whole block, or at the foot of the contour the steps in to the base
  // and the slant over them, whose ends show the frame at the door and the back.
  const corner = (upper, front, contour) => {
    const [x, y, z] = [contour ? 0 : 16, upper ? 16 : 0, front ? 0 : 16];
    if (upper || !contour) return [box([x, y, z], [x + 16, y + 16, z + 16])];
    const elements = [];
    for (let step = 0; step < SLANT_TOP; step += STEP) elements.push(box([slantIn(step), step, z], [16, step + STEP, z + 16]));
    elements.push(box([0, SLANT_TOP, z], [16, 16, z + 16]));
    const middle = [SLANT_FOOT / 2, SLANT_TOP / 2];
    const half = Math.hypot(SLANT_FOOT, SLANT_TOP) / 2;
    const ends = { texture: "#frame", uv: [0, 0, 1, 15] };
    elements.push({
      from: [middle[0], middle[1] - half, z], to: [middle[0] + 1, middle[1] + half, z + 16],
      rotation: { origin: [middle[0], middle[1], z + 8], axis: "z", angle: 22.5 },
      faces: { west: { texture: "#sheet", uv: [0, 0, 16, 16] }, ...(front ? { north: ends } : { south: ends }) },
    });
    return elements;
  };
  // The elements moved by an offset, from the container's frame into a block's or the hand's.
  const moved = (elements, [dx, dy, dz]) => elements.map((element) => ({
    ...element,
    from: [element.from[0] + dx, element.from[1] + dy, element.from[2] + dz],
    to: [element.to[0] + dx, element.to[1] + dy, element.to[2] + dz],
    ...(element.rotation ? { rotation: { ...element.rotation, origin: element.rotation.origin.map((v, i) => v + [dx, dy, dz][i]) } } : {}),
  }));
  const textures = (side) => ({
    door: block("uld_container_door"), back: block("uld_container_back"), side: block(side), sheet: block("uld_container_skin"),
    frame: block("uld_container_frame"), particle: block("uld_container_skin"),
  });
  const NUMBERS = [0, 1, 2, 3];
  const name = (upper, front, contour, number) => `uld_container_${upper ? "upper" : "lower"}_${front ? "front" : "back"}_${contour ? "contour" : "flat"}`
    + (upper && !contour ? `_${number}` : "");
  for (const upper of [false, true]) {
    for (const front of [true, false]) {
      for (const contour of [false, true]) {
        for (const number of upper && !contour ? NUMBERS : [0]) {
          write(`assets/${ID}/models/block/${name(upper, front, contour, number)}.json`, {
            parent: "minecraft:block/block",
            textures: textures(upper ? `uld_container_side_${number}` : "uld_container_side"),
            elements: moved(corner(upper, front, contour), [contour ? 0 : -16, upper ? -16 : 0, front ? 0 : -16]),
          });
        }
      }
    }
  }
  write(`assets/${ID}/blockstates/uld_container.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].flatMap((contour) => [false, true].flatMap((front) =>
      ["lower", "upper"].flatMap((half) => NUMBERS.map((number) => [
        `contour=${contour},facing=${facing},front=${front},half=${half},number=${number}`,
        { model: block(name(half === "upper", front, contour, number)), ...(y ? { y } : {}) },
      ])))))),
  });
  // In the hand the whole container, its middle at the middle of the block, so it turns about it.
  const whole = [false, true].flatMap((upper) => [true, false].flatMap((front) => [false, true].flatMap((contour) => corner(upper, front, contour))));
  write(`assets/${ID}/models/item/uld_container.json`, {
    parent: "minecraft:block/block",
    textures: textures("uld_container_side_0"),
    elements: moved(whole, [-8, -8, -8]),
    display: {
      gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.32, 0.32, 0.32] },
      ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.15, 0.15, 0.15] },
      fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.3, 0.3, 0.3] },
      thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.2, 0.2, 0.2] },
      firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.25, 0.25, 0.25] },
      firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.25, 0.25, 0.25] },
    },
  });
  write(`data/${ID}/loot_table/blocks/uld_container.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:uld_container` }],
      conditions: [
        { condition: "minecraft:block_state_property", block: `${ID}:uld_container`, properties: { half: "lower", front: "true", contour: "false" } },
        { condition: "minecraft:survives_explosion" },
      ],
    }],
    random_sequence: `${ID}:blocks/uld_container`,
  });
  cubes.uld_container = null;
}
// The container is loaded into the hold rather than built into the airliner: breakable, and no part of the airframe.
function uldContainerFittings() {
  return ["uld_container"];
}
// Aluminium (iron) round a cargo net for its door.
function uldContainerRecipes() {
  shaped("uld_container", ["III", "INI", "III"], { I: "minecraft:iron_ingot", N: `${ID}:cargo_net` }, 1);
}
function uldContainerLang() {
  return {
    "block.aerolarge.uld_container": "ULD Container",
  };
}

// Rubber ducks: their fittings, sound, recipes and names are in the functions below, which the shared lists further
// down take in. The duck, drawn facing north from RubberDuckBlock.BOXES: a yellow body with its head on the front, an
// eye either side of the head, an orange beak and a cocked tail. Its renderer draws it, sitting or afloat, so one model
// serves every state; in the hand and the inventory it is drawn bigger, as it fills only a corner of its block.
{
  const duck = `${ID}:block/rubber_duck`;
  const all = (texture) => Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, { texture }]));
  // The head's sides show the eye in the middle of their three pixels square, the same on both.
  const eye = { texture: "#eye", uv: [0, 0, 3, 3] };
  write(`assets/${ID}/models/block/rubber_duck.json`, {
    parent: "minecraft:block/block",
    textures: { duck, eye: `${ID}:block/rubber_duck_eye`, beak: `${ID}:block/rubber_duck_beak`, particle: duck },
    elements: [
      { from: [5, 0, 5], to: [11, 3, 12], faces: all("#duck") },
      { from: [6, 3, 5], to: [10, 6, 8], faces: { ...all("#duck"), east: eye, west: eye } },
      { from: [7, 4, 3], to: [9, 5, 5], faces: all("#beak") },
      { from: [6, 3, 11], to: [10, 4, 12], faces: all("#duck") },
    ],
    display: {
      gui: { rotation: [30, 225, 0], translation: [0, 4, 0], scale: [1.1, 1.1, 1.1] },
      ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
      fixed: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [1, 1, 1] },
      thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 3, 0], scale: [0.75, 0.75, 0.75] },
      firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 4, 0], scale: [0.8, 0.8, 0.8] },
      firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 4, 0], scale: [0.8, 0.8, 0.8] },
    },
  });
  facing("rubber_duck");
  write(`assets/${ID}/models/item/rubber_duck.json`, { parent: duck });
  // Only a duck put down by hand gives itself back: one hidden aboard an airliner is part of the hunt.
  write(`data/${ID}/loot_table/blocks/rubber_duck.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0, bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:rubber_duck` }],
      conditions: [
        { condition: "minecraft:survives_explosion" },
        { condition: "minecraft:block_state_property", block: `${ID}:rubber_duck`, properties: { duck: "0" } },
      ],
    }],
    random_sequence: `${ID}:blocks/rubber_duck`,
  });
  cubes.rubber_duck = null;
}
// The giant rubber duck, its blocks drawn by the renderer of the one in the middle, from the rubber duck's model: the
// blocks themselves show nothing but the duck's yellow as they break. Only ever raised, it has no item and leaves
// nothing behind.
{
  write(`assets/${ID}/blockstates/giant_rubber_duck.json`, { variants: { "": { model: `${ID}:block/giant_rubber_duck` } } });
  write(`assets/${ID}/models/block/giant_rubber_duck.json`, { textures: { particle: `${ID}:block/rubber_duck` } });
  cubes.giant_rubber_duck = null;
}
// Duck Hunt, in the First Flight tab: one criterion for each of the ducks hidden aboard a furnished airliner
// (HIDDEN_DUCKS in RubberDuckBlock), granted as each is first found; it is done once all five are found.
write(`data/${ID}/advancement/first_flight/duck_hunt.json`, {
  parent: `${ID}:first_flight/root`,
  display: {
    icon: { id: `${ID}:rubber_duck` },
    title: { translate: `advancements.${ID}.first_flight.duck_hunt.title` },
    description: { translate: `advancements.${ID}.first_flight.duck_hunt.description` },
    frame: "goal",
  },
  criteria: Object.fromEntries([1, 2, 3, 4, 5].map((index) => [`duck_${index}`, { trigger: "minecraft:impossible" }])),
});
// What of the ducks is fitted rather than built in: breakable, and no part of the airframe.
function rubberDuckFittings() {
  return ["rubber_duck", "giant_rubber_duck"];
}
// The squeak, two quick squeezes of a rubber duck, and the giant duck's, long and deep (see tools/generate_sounds.mjs).
function rubberDuckSounds() {
  return {
    rubber_duck_squeak: { subtitle: `subtitles.${ID}.rubber_duck_squeak`, sounds: [{ name: `${ID}:rubber_duck_squeak` }] },
    giant_rubber_duck_squeak: { subtitle: `subtitles.${ID}.giant_rubber_duck_squeak`, sounds: [{ name: `${ID}:giant_rubber_duck_squeak` }] },
  };
}
// A duck of yellow wool with a beak of orange dye.
function rubberDuckRecipes() {
  shaped("rubber_duck", ["O", "W"], { O: "minecraft:orange_dye", W: "minecraft:yellow_wool" }, 2);
}
function rubberDuckLang() {
  return {
    "block.aerolarge.rubber_duck": "Rubber Duck",
    "subtitles.aerolarge.rubber_duck_squeak": "Rubber duck squeaks",
    "message.aerolarge.rubber_duck.found": "A rubber duck! %s",
    "message.aerolarge.rubber_duck.all_found": "All five rubber ducks found!",
    "message.aerolarge.rubber_duck.giant": "All five rubber ducks found! Something big is coming up in the moon pool...",
    "block.aerolarge.giant_rubber_duck": "Giant Rubber Duck",
    "subtitles.aerolarge.giant_rubber_duck_squeak": "Giant rubber duck squeaks",
    "advancements.aerolarge.first_flight.duck_hunt.title": "Duck Hunt",
    "advancements.aerolarge.first_flight.duck_hunt.description": "Find the five rubber ducks hidden aboard an airliner",
  };
}

// The coffee machine: its fittings, sounds, recipe and names are in the functions below, which the shared lists
// further down take in. Drawn facing north, its back against the south side of its block: steel sides either side of
// a gloss black body, its head over the cup bay with the display lit on its front, the spout under the head, a drip
// tray sticking out in front, the steam wand beside the bay and the bean hopper on top. From grinding on, the drink's
// cup stands on the tray under the spout: the small espresso cup on its saucer, the wider cappuccino cup with its
// foam, the mocha's tall glass, or the babycino's little glass of frothed milk; while it pours, a stream of coffee runs
// down into it. One model for each step and drink (one for ready, with no cup), turned for each facing. The coffees
// are the drinks Barista asks for; the babycino, with no coffee in it, is not one of them.
const COFFEE_STEPS = ["ready", "grinding", "pouring", "steaming", "done"];
const COFFEE_DRINKS = ["espresso", "cappuccino", "mocha"];
const MACHINE_DRINKS = [...COFFEE_DRINKS, "babycino"];
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = {
    steel: block("coffee_machine_steel"), black: block("coffee_machine_black"), front: block("coffee_machine_front"),
    top: block("coffee_machine_top"), bay: block("coffee_machine_bay"), tray: block("coffee_machine_tray"),
    chrome: block("coffee_machine_chrome"), hopper: block("coffee_machine_hopper"), lid: block("coffee_machine_lid"),
    cup: block("coffee_cup"), crema: block("coffee_crema"), foam: block("coffee_foam"), glass: block("mocha_glass"),
    cream: block("mocha_cream"), stream: block("coffee_stream"), particle: block("coffee_machine_steel"),
    milk: block("babycino_glass"), froth: block("babycino_froth"),
  };
  // Each drink's cup, and the height its top reaches, where the stream from the spout meets it.
  const cups = {
    espresso: [3.5, [
      [[6.5, 1, 3.5], [9.5, 1.5, 6.5], "#cup"],
      [[7, 1.5, 4], [9, 3.5, 6], { up: "#crema", all: "#cup" }],
      [[9, 2, 4.75], [9.75, 3, 5.25], "#cup"],
    ]],
    cappuccino: [4.5, [
      [[6, 1, 3], [10, 1.5, 7], "#cup"],
      [[6.5, 1.5, 3.5], [9.5, 4.5, 6.5], { up: "#foam", all: "#cup" }],
      [[9.5, 2.5, 4.75], [10.25, 4, 5.25], "#cup"],
    ]],
    mocha: [5.5, [
      [[6.5, 1, 3.5], [9.5, 5.5, 6.5], { up: "#cream", down: "#cup", all: "#glass" }],
    ]],
    babycino: [4, [
      [[6.75, 1, 3.75], [9.25, 4, 6.25], { up: "#froth", down: "#cup", all: "#milk" }],
    ]],
  };
  const body = [
    [[2, 0, 3], [4, 14, 15], "#steel"],
    [[12, 0, 3], [14, 14, 15], "#steel"],
    [[4, 0, 7], [12, 7, 15], { north: "#bay", all: "#black" }],
    [[4, 7, 3], [12, 14, 15], { north: "#front", up: "#top", all: "#black" }],
    [[4, 0, 1], [12, 1, 7], { up: "#tray", all: "#steel" }],
    [[6.5, 6, 4], [9.5, 7, 6], "#chrome"],
    [[12.5, 3, 2], [13.5, 7, 3], "#chrome"],
    [[5, 14, 9], [10, 16, 14], { up: "#lid", down: "#black", all: "#hopper" }],
  ];
  for (const step of COFFEE_STEPS) {
    for (const drink of step === "ready" ? [null] : MACHINE_DRINKS) {
      const [top, cup] = drink ? cups[drink] : [0, []];
      const stream = step === "pouring" ? [[[7.75, top, 4.75], [8.25, 6, 5.25], "#stream"]] : [];
      // The display, a hair proud of the head's front, glowing whatever the light around.
      const display = [[[4, 8, 2.9], [12, 13, 3], "#display", true]];
      write(`assets/${ID}/models/block/coffee_machine_${drink ? `${step}_${drink}` : step}.json`, boxes(
        { ...textures, display: block(`coffee_machine_display_${step}`) },
        [...body, ...display, ...cup, ...stream],
      ));
    }
  }
  write(`assets/${ID}/blockstates/coffee_machine.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => COFFEE_STEPS.flatMap((step) => MACHINE_DRINKS.map((drink) =>
      [`brew=${step},drink=${drink},facing=${facing}`, { model: `${ID}:block/coffee_machine_${step === "ready" ? step : `${step}_${drink}`}`, y }])))),
  });
  write(`assets/${ID}/models/item/coffee_machine.json`, { parent: `${ID}:block/coffee_machine_ready` });
  dropsItself("coffee_machine");
  cubes.coffee_machine = null;
  for (const drink of MACHINE_DRINKS) {
    write(`assets/${ID}/models/item/${drink}.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/${drink}` } });
  }
}
// Barista, in the First Flight tab: one criterion for each of the coffee machine's drinks, granted as each is first
// taken from under its spout (see CoffeeMachineBlock); it is done once all three have been.
write(`data/${ID}/advancement/first_flight/barista.json`, {
  parent: `${ID}:first_flight/root`,
  display: {
    icon: { id: `${ID}:cappuccino` },
    title: { translate: `advancements.${ID}.first_flight.barista.title` },
    description: { translate: `advancements.${ID}.first_flight.barista.description` },
    frame: "task",
  },
  criteria: Object.fromEntries(COFFEE_DRINKS.map((drink) => [drink, { trigger: "minecraft:impossible" }])),
});
// What of the coffee machine is fitted rather than built in: breakable, and no part of the airframe.
function coffeeMachineFittings() {
  return ["coffee_machine"];
}
// The grinder, the pump pouring, the steam frothing the milk and the chime of a drink ready (see
// tools/generate_sounds.mjs).
function coffeeMachineSounds() {
  return Object.fromEntries(["coffee_grind", "coffee_pour", "coffee_steam", "coffee_ready"].map((name) =>
    [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
// Steel round a display of black glass, a bucket for the water tank and cocoa beans for the hopper.
function coffeeMachineRecipes() {
  shaped("coffee_machine", [" C ", "IGI", "IBI"], {
    C: "minecraft:cocoa_beans", I: "minecraft:iron_ingot", G: "minecraft:black_stained_glass_pane", B: "minecraft:bucket",
  }, 1);
}
function coffeeMachineLang() {
  return {
    "block.aerolarge.coffee_machine": "Coffee Machine",
    "item.aerolarge.espresso": "Espresso",
    "item.aerolarge.cappuccino": "Cappuccino",
    "item.aerolarge.mocha": "Mocha",
    "item.aerolarge.babycino": "Babycino",
    "screen.aerolarge.coffee_machine.title": "Coffee Machine",
    "screen.aerolarge.coffee_machine.ready": "Ready",
    "subtitles.aerolarge.coffee_grind": "Coffee grinder whirs",
    "subtitles.aerolarge.coffee_pour": "Coffee machine pours",
    "subtitles.aerolarge.coffee_steam": "Milk frother hisses",
    "subtitles.aerolarge.coffee_ready": "Coffee machine chimes",
    "advancements.aerolarge.first_flight.barista.title": "Barista",
    "advancements.aerolarge.first_flight.barista.description": "Brew an espresso, a cappuccino and a mocha at a coffee machine",
  };
}

// Basketball, drawn facing north. The hoop: an orange ring on a dark bracket to the backboard, with its net hanging
// below; the side it counts for does not show. The scoreboard: a dark face, the score drawn over it by its renderer.
// The ball rack: a steel frame of balls. The ball: an item sprite, drawn the same in flight.
{
  const hoop = {
    ...boxes(
      { rim: `${ID}:block/hoop_rim`, bracket: `${ID}:block/handset_cradle`, net: `${ID}:block/hoop_net`, particle: `${ID}:block/hoop_rim` },
      [
        [[2, 10, 2], [14, 11, 3.5], "#rim"],
        [[2, 10, 12.5], [14, 11, 14], "#rim"],
        [[2, 10, 3.5], [3.5, 11, 12.5], "#rim"],
        [[12.5, 10, 3.5], [14, 11, 12.5], "#rim"],
        [[7, 9, 0], [9, 11, 2], "#bracket"],
      ],
    ),
    render_type: "minecraft:cutout",
  };
  // The net: four sides hanging from the ring, seen from inside and out.
  for (const [from, to, faces] of [
    [[3, 4, 3.5], [13, 10, 3.5], ["north", "south"]],
    [[3, 4, 12.5], [13, 10, 12.5], ["north", "south"]],
    [[3.5, 4, 3], [3.5, 10, 13], ["east", "west"]],
    [[12.5, 4, 3], [12.5, 10, 13], ["east", "west"]],
  ]) {
    hoop.elements.push({ from, to, faces: Object.fromEntries(faces.map((face) => [face, { texture: "#net" }])) });
  }
  write(`assets/${ID}/models/block/hoop.json`, hoop);
  write(`assets/${ID}/blockstates/hoop.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/hoop`, y }])),
  });
  for (const name of ["scoreboard", "ball_rack"]) {
    write(`assets/${ID}/models/block/${name}.json`, {
      parent: "minecraft:block/orientable",
      textures: { front: `${ID}:block/${name}_front`, side: `${ID}:block/${name}_side`, top: `${ID}:block/${name}_side` },
    });
    facing(name);
  }
  for (const name of ["hoop", "scoreboard", "ball_rack"]) {
    write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}` });
    dropsItself(name);
    cubes[name] = null;
  }
  write(`assets/${ID}/models/item/basketball.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/basketball` } });
}

// Duty free and the paper plane. The paper plane: a flat icon, folded into shape by its renderer in flight.
write(`assets/${ID}/models/item/paper_plane.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/paper_plane` } });
// The laser pointer (see LaserPointerItem): a flat icon, held like a tool.
write(`assets/${ID}/models/item/laser_pointer.json`, { parent: "minecraft:item/handheld", textures: { layer0: `${ID}:item/laser_pointer` } });
function laserPointerLang() {
  return {
    "item.aerolarge.laser_pointer": "Laser Pointer",
  };
}
// Souvenirs. The pilot's cap, drawn as it sits on the head: worn, it is drawn there as its own model, the way a carved
// pumpkin is, the model's box a little bigger than the head. A black band round the brow, a navy crown over it flaring
// out at the top, a black peak drooping forward over the eyes, a gold cord across the front and a gold badge with
// wings above it. In the inventory it is shown a little lower, as it only fills the top of its box.
{
  const cap = boxes(
    { crown: `${ID}:item/pilot_cap_crown`, band: `${ID}:item/pilot_cap_band`, gold: `${ID}:item/pilot_cap_gold`, particle: `${ID}:item/pilot_cap_crown` },
    [
      [[1.2, 10, 1.2], [14.8, 11.6, 14.8], "#band"],
      [[1, 11.6, 1], [15, 13.2, 15], "#crown"],
      [[0.4, 13.2, 0.4], [15.6, 15.6, 15.6], "#crown"],
      [[3.5, 10.4, 0.9], [12.5, 10.8, 1.2], "#gold"],
      [[6.8, 11.7, 0.6], [9.2, 13.2, 1], "#gold"],
      [[4.6, 12.2, 0.8], [11.4, 12.7, 1], "#gold"],
    ],
  );
  // The peak reaches out past the front of the model's box, so its faces are given a patch of the texture of their own.
  cap.elements.push({
    from: [3.5, 9.6, -2.6], to: [12.5, 10.2, 1.2],
    rotation: { origin: [8, 10, 1.2], axis: "x", angle: -22.5 },
    faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, { texture: "#band", uv: [0, 2, 4, 6] }])),
  });
  cap.display = { gui: { rotation: [30, 225, 0], translation: [0, -3, 0], scale: [0.625, 0.625, 0.625] } };
  write(`assets/${ID}/models/item/pilot_cap.json`, cap);
}
// The pilot's helmet: a flat icon everywhere but on the head, where it is drawn as its own model, as the cap is. The
// model: a white shell round the crown, back and ears, stepped in toward the top, with the stripe over its middle; a
// bulge for each ear cup and the airline's roundel above it; the visor down over the eyes, wrapping back round the
// sides of the face, under the cover it slides up into, with a knob to pull it by; and a microphone on a boom from the
// right ear cup round to the mouth.
{
  const crown = { up: "#stripe", north: "#stripe", south: "#stripe", all: "#shell" };
  const helmet = boxes(
    { shell: `${ID}:item/pilot_helmet_shell`, stripe: `${ID}:item/pilot_helmet_stripe`, visor: `${ID}:item/pilot_helmet_visor`,
      trim: `${ID}:item/pilot_helmet_trim`, logo: `${ID}:item/pilot_helmet_logo`, particle: `${ID}:item/pilot_helmet_shell` },
    [
      [[0.6, 7.4, 0.8], [15.4, 14.2, 15.4], crown],
      [[1.4, 14.2, 1.6], [14.6, 15.4, 14.6], crown],
      [[2.8, 15.4, 3], [13.2, 16, 13.2], crown],
      [[1, 3, 9.4], [15, 7.4, 15.2], { south: "#stripe", all: "#shell" }],
      [[0.6, 3.4, 2.2], [1.8, 7.4, 9.4], "#shell"],
      [[14.2, 3.4, 2.2], [15.4, 7.4, 9.4], "#shell"],
      [[0.1, 3.8, 4.8], [0.6, 8, 9.6], "#shell"],
      [[15.4, 3.8, 4.8], [15.9, 8, 9.6], "#shell"],
      [[1.2, 10.2, 0], [14.8, 12.6, 1], "#shell"],
      [[7, 11, -0.5], [9, 12.8, 0], "#trim"],
      [[1.8, 5.2, 0.2], [14.2, 10.2, 0.9], { north: "#visor", all: "#trim" }],
      [[15.4, 3.8, 2], [16, 4.4, 7], "#trim"],
      [[11, 3.6, 0.4], [15.6, 4.2, 1], "#trim"],
      [[15, 3.7, 0.4], [16, 4.3, 2.4], "#trim"],
      [[9.4, 3.2, 0], [11, 4.6, 1.2], "#trim"],
    ],
  );
  const [, , , , , , , , , knob, visor] = helmet.elements;
  visor.faces.north.uv = [0, 0, 16, 16];
  // The knob stands out past the front of the model's box, so its faces are given a patch of the texture of their own.
  Object.values(knob.faces).forEach((face) => (face.uv = [0, 0, 2, 2]));
  for (const [x0, x1, angle, side] of [[1, 1.8, -22.5, "west"], [14.2, 15, 22.5, "east"]]) {
    helmet.elements.push({
      from: [x0, 5.4, 0.3], to: [x1, 10, 3.3],
      rotation: { origin: [(x0 + x1) / 2, 7.7, 1.8], axis: "y", angle },
      faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face,
        face === side ? { texture: "#visor", uv: [0, 0, 6, 16] } : { texture: "#trim" }])),
    });
  }
  for (const [x0, x1, side] of [[0.4, 0.6, "west"], [15.4, 15.6, "east"]]) {
    helmet.elements.push({ from: [x0, 9, 5.6], to: [x1, 13, 9.6], faces: { [side]: { texture: "#logo", uv: [0, 0, 16, 16] } } });
  }
  write(`assets/${ID}/models/item/pilot_helmet_worn.json`, helmet);
  // A generated model's flat layers are only made when it is baked as an item model of its own, so the icon is made by
  // NeoForge's item layers instead; its parent still gives it an item's transforms in the hand and on the ground.
  write(`assets/${ID}/models/item/pilot_helmet.json`, {
    loader: "neoforge:separate_transforms",
    gui_light: "front",
    base: {
      parent: "minecraft:item/generated", loader: "neoforge:item_layers",
      textures: { layer0: `${ID}:item/pilot_helmet`, particle: `${ID}:item/pilot_helmet` },
    },
    perspectives: { head: { parent: `${ID}:item/pilot_helmet_worn` } },
  });
}
// The rocket crew's space helmet: a flat icon everywhere but on the head, where it is drawn as its own model, as the
// pilot helmet is, a round bubble well bigger than the head. The model: a white shell rounded off in steps toward its
// crown and its foot, the dark ring at its neck under it, the big gold visor over the face in a pale gasket, its
// corners rounded off to show the gasket, and the mission patch on its left side. Most of it reaches out past the
// model's box, so every face takes a patch of its texture from the texture's corner.
{
  const helmet = {
    parent: "minecraft:block/block",
    textures: {
      shell: `${ID}:item/space_helmet_shell`, visor: `${ID}:item/space_helmet_visor`, gasket: `${ID}:item/space_helmet_gasket`,
      ring: `${ID}:item/space_helmet_ring`, patch: `${ID}:item/space_helmet_patch`, particle: `${ID}:item/space_helmet_shell`,
    },
    elements: [
      patched([2, -0.6, 2], [14, 1.4, 14], "#ring"),
      patched([1.4, 1.4, 1.4], [14.6, 2.2, 14.6], "#shell"),
      patched([0.4, 2.2, 0.4], [15.6, 3.2, 15.6], "#shell"),
      patched([0.8, 3.2, -0.8], [15.2, 13.8, 16.8], "#shell"),
      patched([-0.2, 3.2, 0.4], [16.2, 13.8, 15.6], "#shell"),
      patched([-0.8, 4.6, 1.2], [16.8, 12.4, 14.8], "#shell"),
      patched([0, 3.8, -0.2], [16, 13.2, 16.2], "#shell"),
      patched([0.2, 13.8, 0.2], [15.8, 15, 15.8], "#shell"),
      patched([1.4, 15, 1.4], [14.6, 16, 14.6], "#shell"),
      patched([3.4, 16, 3.4], [12.6, 16.8, 12.6], "#shell"),
      patched([1.2, 3.8, -1.1], [14.8, 13.2, -0.8], "#gasket"),
      patched([1.7, 4.3, -1.4], [14.3, 12.7, -1.1], "#gasket"),
    ],
  };
  const visor = helmet.elements[helmet.elements.length - 1];
  visor.faces.north = { texture: "#visor", uv: [0, 0, 16, 16] };
  helmet.elements.push({ from: [-1, 6.2, 5.6], to: [-0.8, 10.6, 10], faces: { west: { texture: "#patch", uv: [0, 0, 16, 16] } } });
  write(`assets/${ID}/models/item/space_helmet_worn.json`, helmet);
  write(`assets/${ID}/models/item/space_helmet.json`, {
    loader: "neoforge:separate_transforms",
    gui_light: "front",
    base: {
      parent: "minecraft:item/generated", loader: "neoforge:item_layers",
      textures: { layer0: `${ID}:item/space_helmet`, particle: `${ID}:item/space_helmet` },
    },
    perspectives: { head: { parent: `${ID}:item/space_helmet_worn` } },
  });
}
// The model airliner on its stand, drawn nose north: a white fuselage with a blue cheat line, swept wings with an
// engine under each, a tailplane and a blue fin, on a black pole and base.
{
  const model = boxes(
    { white: `${ID}:block/airframe_panel`, blue: `${ID}:block/livery_panel`, engine: `${ID}:block/engine_intake`, stand: `${ID}:block/light_cable`,
      particle: `${ID}:block/airframe_panel` },
    [
      [[5, 0, 5], [11, 1, 11], "#stand"],
      [[7.5, 1, 7.5], [8.5, 6, 8.5], "#stand"],
      [[7, 6, 1], [9, 8, 15], "#white"],
      [[7.25, 6.25, 0], [8.75, 7.75, 1], "#white"],
      [[6.9, 6.9, 2], [9.1, 7.2, 13], "#blue"],
      [[1, 6.5, 7], [7, 7, 10], "#white"],
      [[9, 6.5, 7], [15, 7, 10], "#white"],
      [[3.5, 5.6, 7.2], [5, 6.5, 9.7], "#engine"],
      [[11, 5.6, 7.2], [12.5, 6.5, 9.7], "#engine"],
      [[4.5, 7.2, 12.5], [11.5, 7.6, 14.5], "#white"],
      [[7.75, 8, 12], [8.25, 10, 15], "#blue"],
      [[7.75, 10, 13.5], [8.25, 11.5, 15], "#blue"],
    ],
  );
  // The wings sweep back from the fuselage.
  model.elements[5].rotation = { origin: [7, 6.75, 8], axis: "y", angle: 22.5 };
  model.elements[6].rotation = { origin: [9, 6.75, 8], axis: "y", angle: -22.5 };
  write(`assets/${ID}/models/block/model_airliner.json`, model);
  facing("model_airliner");
  write(`assets/${ID}/models/item/model_airliner.json`, { parent: `${ID}:block/model_airliner` });
  dropsItself("model_airliner");
  cubes.model_airliner = null;
}
// The souvenir model zeppelin, nose north: a silver cigar on a slim stand, built up of boxes stepping in toward its
// rounded nose and tapering tail, its four fins in a cross at the tail and its glazed control car under the bow. The
// blueprint's preview draws it too, without its stand (see BlueprintPreview), so the stand alone is light cable.
{
  const fabric = "#fabric";
  const model = {
    parent: "minecraft:block/block",
    textures: { fabric: `${ID}:block/zeppelin_fabric`, car: `${ID}:block/cockpit_glass`, stand: `${ID}:block/light_cable`, particle: `${ID}:block/zeppelin_fabric` },
    elements: [
      solid([6, 6, 1], [10, 10, 2], fabric),
      solid([5, 5, 2], [11, 11, 4], fabric),
      solid([4.5, 4.5, 4], [11.5, 11.5, 10], fabric),
      solid([5, 5, 10], [11, 11, 12], fabric),
      solid([6, 6, 12], [10, 10, 14], fabric),
      solid([7, 7, 14], [9, 9, 15.5], fabric),
      solid([7.75, 10, 12], [8.25, 13, 15], fabric),
      solid([7.75, 3, 12], [8.25, 6, 15], fabric),
      solid([3, 7.75, 12], [6, 8.25, 15], fabric),
      solid([10, 7.75, 12], [13, 8.25, 15], fabric),
      solid([7, 3.5, 3], [9, 4.5, 6], "#car"),
      solid([7.5, 0, 7.5], [8.5, 4.5, 8.5], "#stand"),
      solid([5.5, 0, 5.5], [10.5, 0.5, 10.5], "#stand"),
    ],
  };
  write(`assets/${ID}/models/block/model_zeppelin.json`, model);
  facing("model_zeppelin");
  write(`assets/${ID}/models/item/model_zeppelin.json`, { parent: `${ID}:block/model_zeppelin` });
  dropsItself("model_zeppelin");
  cubes.model_zeppelin = null;
}
// The souvenir model rocket, standing on its fins, its front (where its door is) north: a white body built up of boxes
// stepping in to its pointed nose, a band of cockpit glass under the nose, and four fins out at its foot between the
// four ways, drawn as four plates. It needs no stand: the blueprint's preview draws it whole.
{
  const body = "#body";
  const model = {
    parent: "minecraft:block/block",
    textures: { body: `${ID}:block/zeppelin_fabric`, glass: `${ID}:block/cockpit_glass`, particle: `${ID}:block/zeppelin_fabric` },
    elements: [
      solid([6.5, 2, 6.5], [9.5, 10, 9.5], body),
      solid([6.5, 10, 6.5], [9.5, 11, 9.5], "#glass"),
      solid([7, 11, 7], [9, 13, 9], body),
      solid([7.5, 13, 7.5], [8.5, 15, 8.5], body),
      solid([7.75, 0, 3.5], [8.25, 5, 6.5], body),
      solid([7.75, 0, 9.5], [8.25, 5, 12.5], body),
      solid([3.5, 0, 7.75], [6.5, 5, 8.25], body),
      solid([9.5, 0, 7.75], [12.5, 5, 8.25], body),
    ],
  };
  // Its fins stand between the four ways, as the rocket's do.
  for (const fin of model.elements.slice(4)) {
    fin.rotation = { origin: [8, 2.5, 8], axis: "y", angle: 45 };
  }
  write(`assets/${ID}/models/block/model_rocket.json`, model);
  facing("model_rocket");
  write(`assets/${ID}/models/item/model_rocket.json`, { parent: `${ID}:block/model_rocket` });
  dropsItself("model_rocket");
  cubes.model_rocket = null;
}
// The duty-free counter, drawn facing north: a cabinet ten pixels tall, and on it a glass showcase with the goods on
// show inside, lying on the cabinet's top; the glass is see-through, so the whole counter is drawn translucent.
{
  const counter = boxes(
    { front: `${ID}:block/duty_free_counter_front`, side: `${ID}:block/duty_free_counter_side`, top: `${ID}:block/duty_free_counter_top`,
      glass: `${ID}:block/duty_free_glass`, particle: `${ID}:block/duty_free_counter_side` },
    [
      [[0, 0, 0], [16, 10, 16], { north: "#front", up: "#top", all: "#side" }],
      [[0.5, 10, 0.5], [15.5, 15, 15.5], "#glass"],
    ],
  );
  // The showcase stands on the cabinet: it has no floor of its own.
  delete counter.elements[1].faces.down;
  counter.render_type = "minecraft:translucent";
  write(`assets/${ID}/models/block/duty_free_counter.json`, counter);
  facing("duty_free_counter");
  write(`assets/${ID}/models/item/duty_free_counter.json`, { parent: `${ID}:block/duty_free_counter` });
  dropsItself("duty_free_counter");
  cubes.duty_free_counter = null;
}
const DUTY_FREE_LANG = {
  "block.aerolarge.duty_free_counter": "Duty-Free Counter",
  "container.aerolarge.duty_free": "Duty Free",
  "subtitles.aerolarge.shop_bell": "Shop bell dings",
  "subtitles.aerolarge.cash_register": "Till rings",
  "item.aerolarge.paper_plane": "Paper Plane",
  "entity.aerolarge.paper_plane": "Paper Plane",
  "subtitles.aerolarge.paper_plane_whoosh": "Paper plane whooshes",
  "item.aerolarge.pilot_cap": "Pilot Cap",
  "block.aerolarge.model_airliner": "Model Airliner",
  "block.aerolarge.model_zeppelin": "Model Zeppelin",
  "block.aerolarge.model_rocket": "Model Rocket",
  "item.aerolarge.pilot_helmet": "Pilot Helmet",
  "item.aerolarge.pilot_helmet.tooltip": "On the flight deck, its visor shows the head-up display",
  "subtitles.aerolarge.hud_on": "Pilot HUD beeps on",
  "item.aerolarge.space_helmet": "Space Helmet",
  "item.aerolarge.space_helmet.tooltip": "Aboard a rocket in space, you hear its air as you breathe",
  "subtitles.aerolarge.space_helmet_breath": "Helmet air hisses",
};

// The cabin controls: modules of the centre console, drawn facing north, their top showing the setting. The cabin
// lights' dimmer: bright, dim or night, its knob turned to match. The window shades' control: open or closed, its
// toggle pushed away when they are closed.
{
  const textures = (name) => ({ panel: `${ID}:block/${name}`, knob: `${ID}:block/thrust_lever_knob`, dark: `${ID}:block/thrust_lever_handle` });
  for (const level of ["bright", "dim", "night"]) {
    consoleModule(`cabin_lights_${level}`, textures(`cabin_lights_${level}`), "#panel", true, [
      solid([6.75, CONSOLE_HEIGHT, 1.75], [9.25, 7.5, 4.25], "#knob"),
      solid([7.75, 7.5, 3], [8.25, 8, 4.5], "#dark", { origin: [8, 7.5, 3], axis: "y", angle: { bright: -45, dim: 0, night: 45 }[level] }),
    ]);
  }
  write(`assets/${ID}/blockstates/cabin_lights.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["bright", "dim", "night"].map((level) =>
      [`brightness=${level},facing=${facing}`, { model: `${ID}:block/cabin_lights_${level}`, y }]))),
  });
  write(`assets/${ID}/models/item/cabin_lights.json`, { parent: `${ID}:block/cabin_lights_bright` });
  for (const shaded of [false, true]) {
    consoleModule(`window_shades_${shaded ? "closed" : "open"}`, textures(`window_shades_${shaded ? "closed" : "open"}`), "#panel", true, [
      solid([7, CONSOLE_HEIGHT, 1.5], [9, 6.5, 3.5], "#dark"),
      solid([7.5, 6.5, 2], [8.5, 9, 3], "#knob", { origin: [8, 6.5, 2.5], axis: "x", angle: shaded ? 22.5 : -22.5 }),
    ]);
  }
  write(`assets/${ID}/blockstates/window_shades.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((shaded) =>
      [`facing=${facing},shaded=${shaded}`, { model: `${ID}:block/window_shades_${shaded ? "closed" : "open"}`, y }]))),
  });
  write(`assets/${ID}/models/item/window_shades.json`, { parent: `${ID}:block/window_shades_open` });
  for (const name of ["cabin_lights", "window_shades"]) {
    dropsItself(name);
    cubes[name] = null;
  }
}

// The cabin window with its shade up or down, on its own or joined to the windows above and below it into one tall
// window; its plain blockstate, from the cubes above, gives way to one for them all.
const WINDOW_JOINS = [["", false, false], ["_bottom", true, false], ["_top", false, true], ["_middle", true, true]];
{
  const variants = {};
  for (const [joins, up, down] of WINDOW_JOINS) {
    for (const shaded of [false, true]) {
      const name = `cabin_window${joins}${shaded ? "_shaded" : ""}`;
      if (name !== "cabin_window") {
        write(`assets/${ID}/models/block/${name}.json`, {
          parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/${name}` }, render_type: "minecraft:translucent",
        });
      }
      variants[`down=${down},shaded=${shaded},up=${up}`] = { model: `${ID}:block/${name}` };
    }
  }
  write(`assets/${ID}/blockstates/cabin_window.json`, { variants });
}
// The same window cut to the hull's curve, every pane of it baked for every shape by the "shell" loader, with the
// flat window's textures. It gives back a cabin window.
{
  const variants = {};
  for (const [joins, up, down] of WINDOW_JOINS) {
    for (const shaded of [false, true]) {
      const texture = `${ID}:block/cabin_window${joins}${shaded ? "_shaded" : ""}`;
      const name = `cabin_window_shell${joins}${shaded ? "_shaded" : ""}`;
      write(`assets/${ID}/models/block/${name}.json`, {
        loader: `${ID}:shell`, render_type: "minecraft:translucent", textures: { skin: texture, particle: texture },
      });
      variants[`down=${down},shaded=${shaded},up=${up}`] = { model: `${ID}:block/${name}` };
    }
  }
  write(`assets/${ID}/blockstates/cabin_window_shell.json`, { variants });
  write(`data/${ID}/loot_table/blocks/cabin_window_shell.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:cabin_window` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/cabin_window_shell`,
  });
  cubes.cabin_window_shell = null;
}
// The rocket's oval window (see OvalWindowBlock): each block of it at each step of its dimming, the inner half of its
// block, its glass a dome over the oval in a recess lined with steel, baked by the mod's own "oval_window" loader (see
// OvalWindowModel), its frame and its edges the cover's undoped weave, tinted in the ship's livery as the cover is.
// Modelled looking north, turned for the other ways. It gives back glass.
{
  const variants = {};
  const edge = `${ID}:block/zeppelin_skin`;
  for (let column = 0; column < 3; column++) {
    for (let row = 0; row < 4; row++) {
      for (let dim = 0; dim < 4; dim++) {
        const name = `oval_window_${column}_${row}_dim${dim}`;
        const glass = `${ID}:block/${name}`;
        write(`assets/${ID}/models/block/${name}.json`, {
          loader: `${ID}:oval_window`, render_type: "minecraft:translucent", column, row,
          textures: {
            glass, frame: `${ID}:block/oval_window_${column}_${row}_frame`, inside: `${ID}:block/oval_window_${column}_${row}_inside`,
            lip: `${ID}:block/oval_window_${column}_${row}_lip`, reveal: `${ID}:block/oval_window_reveal`, edge, particle: glass,
          },
        });
        for (const [facing, y] of Object.entries(yRot)) {
          variants[`column=${column},dim=${dim},facing=${facing},row=${row}`] = { model: `${ID}:block/${name}`, y };
        }
      }
    }
  }
  write(`assets/${ID}/blockstates/oval_window.json`, { variants });
  write(`data/${ID}/loot_table/blocks/oval_window.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: "minecraft:glass" }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/oval_window`,
  });
  cubes.oval_window = null;
}
// The rocket's lift (see LiftMachineBlockEntity). Its machine: a steel housing over the shaft. Its landing doors: a
// pane of smoked glass in a steel frame across the middle of the doorway, a block wide and two tall; open, it has slid
// aside into the shaft's wall by the doorway, so it is drawn reaching into the next block. Its picture boards: the slim
// screen of the camera display and the livery selector, a block wide and two tall, its renderer drawing the floors.
// Only the blueprints place them, and they give back nothing.
{
  write(`assets/${ID}/models/block/lift_machine.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/lift_machine` } });
  write(`assets/${ID}/blockstates/lift_machine.json`, { variants: { "": { model: `${ID}:block/lift_machine` } } });
  cubes.lift_machine = null;
  const variants = {};
  for (const half of ["lower", "upper"]) {
    for (const open of [false, true]) {
      const name = `lift_door_${half}${open ? "_open" : ""}`;
      const pane = `${ID}:block/lift_door_${half}`;
      const shift = open ? 14 : 0;
      const edge = { texture: "#frame" };
      write(`assets/${ID}/models/block/${name}.json`, {
        parent: "minecraft:block/block", render_type: "minecraft:translucent", textures: { pane, frame: `${ID}:block/lift_door_frame`, particle: pane },
        elements: [{
          from: [shift, 0, 7], to: [16 + shift, 16, 9],
          faces: { north: { texture: "#pane" }, south: { texture: "#pane" }, east: edge, west: edge, up: edge, down: edge },
        }],
      });
      for (const [facing, y] of Object.entries(yRot)) {
        variants[`facing=${facing},half=${half},open=${open},sliding=false`] = { model: `${ID}:block/${name}`, y };
        variants[`facing=${facing},half=${half},open=${open},sliding=true`] = { model: `${ID}:block/lift_door_sliding` };
      }
    }
  }
  // While a door slides, its block entity's renderer draws the pane on its way (see LiftDoorRenderer), and its block
  // is drawn empty.
  write(`assets/${ID}/models/block/lift_door_sliding.json`, { textures: { particle: `${ID}:block/lift_door_lower` } });
  write(`assets/${ID}/blockstates/lift_door.json`, { variants });
  cubes.lift_door = null;
  slimScreenModels("lift_board", `${ID}:block/lift_board_casing`, `${ID}:block/lift_board_casing`);
  write(`assets/${ID}/blockstates/lift_board.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [0, 1, 2, 3, 4, 5, 6, 7].flatMap((part) =>
      [false, true].map((tilted) => [`facing=${facing},part=${part},tilted=${tilted}`, { model: `${ID}:block/lift_board${tilted ? "_tilted" : ""}`, y }])))),
  });
  cubes.lift_board = null;
}

// The rocket's LED strips (see LedStripBlock): a thin bar along each side of its block that her skin stands on, lying
// on the floor or fixed under the ceiling, its faces glowing whatever the light around and tinted in its story's colour
// (see the client's LedLights); one model for each, drawn along the north side and turned for the others, the bars of
// a strip along two sides meeting in its corner. The panel by each story's lift door and the console in her cockpit
// are slim screens of dark glass that their renderers draw over (see LedPanelRenderer and LedConsoleRenderer). All of
// them are placed only by the blueprint, so they have no items and give nothing back; fitted into her, so breakable,
// and light as the rest of her is (see FITTINGS).
{
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  const face = (uv) => ({ texture: "#strip", uv, tintindex: 0, ...lit });
  for (const [half, from, to, flat] of [["floor", [0, 0, 0], [16, 2, 2], "up"], ["ceiling", [0, 14, 0], [16, 16, 2], "down"]]) {
    write(`assets/${ID}/models/block/led_strip_${half}.json`, {
      parent: "minecraft:block/block",
      ambientocclusion: false,
      textures: { strip: `${ID}:block/led_strip`, particle: `${ID}:block/led_strip` },
      elements: [{
        from, to, shade: false,
        faces: { south: face([0, 7, 16, 9]), [flat]: face([0, 7, 16, 9]), east: face([7, 7, 9, 9]), west: face([7, 7, 9, 9]) },
      }],
    });
  }
  write(`assets/${ID}/blockstates/led_strip.json`, {
    multipart: Object.entries(yRot).flatMap(([wall, y]) => ["false", "true"].map((ceiling) => ({
      when: { [wall]: "true", ceiling },
      apply: { model: `${ID}:block/led_strip_${ceiling === "true" ? "ceiling" : "floor"}`, y },
    }))),
  });
  cubes.led_strip = null;
  // A strip is a thin bar of LEDs, next to nothing beside her skin: it weighs a twentieth of a block, overriding the
  // airframe's quarter (sable:super_light), so her many hundreds of strips hardly add to her weight.
  write(`data/${ID}/physics_block_properties/led_strip.json`, {
    priority: 1004,
    selector: `${ID}:led_strip`,
    properties: { "sable:mass": 0.05 },
  });
  const casing = `${ID}:block/passenger_screen_casing`;
  for (const name of ["led_panel", "led_console"]) {
    slimScreenModels(name, casing, casing);
    write(`assets/${ID}/blockstates/${name}.json`, {
      variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [0, 1, 2, 3, 4, 5, 6, 7].flatMap((part) =>
        [false, true].map((tilted) => [`facing=${facing},part=${part},tilted=${tilted}`, { model: `${ID}:block/${name}${tilted ? "_tilted" : ""}`, y }])))),
    });
    cubes[name] = null;
  }
}
function ledStripFittings() {
  return ["led_strip", "led_panel", "led_console"];
}
function ledStripLang() {
  return {
    "block.aerolarge.led_strip": "LED Strip",
    "block.aerolarge.led_panel": "LED Panel",
    "block.aerolarge.led_console": "LED Console",
  };
}

// The rocket's space posters (see SpacePosterBlock), each so many blocks wide and tall, as SpacePoster has them. Each
// block of a poster is a sheet of board a pixel thick, a quarter pixel clear of the back of its block, its piece of the
// picture on its front and plain board behind it and round its edges, drawn evenly lit whichever way it faces.
// Modelled facing north, turned for the other ways; a state past the edge of its poster, which nothing places, shows
// the poster's nearest piece.
const SPACE_POSTERS = { black_hole: [4, 3], solar_system: [5, 2], quasar: [3, 4], earthrise: [4, 3] };
{
  const back = `${ID}:block/space_poster_back`;
  const mostColumns = Math.max(...Object.values(SPACE_POSTERS).map(([columns]) => columns));
  const mostRows = Math.max(...Object.values(SPACE_POSTERS).map(([, rows]) => rows));
  const pieceName = (poster, column, row) => `space_poster_${poster}_${column}_${row}`;
  const variants = {};
  for (const [poster, [columns, rows]] of Object.entries(SPACE_POSTERS)) {
    for (let piece = 0; piece < columns * rows; piece++) {
      const name = pieceName(poster, piece % columns, Math.floor(piece / columns));
      const front = `${ID}:block/${name}`;
      write(`assets/${ID}/models/block/${name}.json`, {
        parent: "minecraft:block/block",
        ambientocclusion: false,
        textures: { front, back, particle: front },
        elements: [{
          from: [0, 0, 14.75],
          to: [16, 16, 15.75],
          shade: false,
          faces: {
            north: { uv: [0, 0, 16, 16], texture: "#front" },
            south: { uv: [0, 0, 16, 16], texture: "#back" },
            east: { uv: [0, 0, 1, 16], texture: "#back" },
            west: { uv: [15, 0, 16, 16], texture: "#back" },
            up: { uv: [0, 0, 16, 1], texture: "#back" },
            down: { uv: [0, 15, 16, 16], texture: "#back" },
          },
        }],
      });
    }
    for (let state = 0; state < mostColumns * mostRows; state++) {
      const column = state % mostColumns;
      const row = Math.floor(state / mostColumns);
      const model = `${ID}:block/${pieceName(poster, Math.min(column, columns - 1), Math.min(row, rows - 1))}`;
      for (const [facing, y] of Object.entries(yRot)) {
        variants[`column=${column},facing=${facing},poster=${poster},row=${row}`] = { model, y };
      }
    }
  }
  write(`assets/${ID}/blockstates/space_poster.json`, { variants });
  cubes.space_poster = null;
  // Each poster rolled up, to put up by hand (see SpacePosterItem), its icon its own picture; a poster taken down gives
  // it back from its top left piece alone, so once whatever piece is broken.
  for (const poster of Object.keys(SPACE_POSTERS)) {
    write(`assets/${ID}/models/item/${poster}_poster.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/${poster}_poster` } });
  }
  write(`data/${ID}/loot_table/blocks/space_poster.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: Object.keys(SPACE_POSTERS).map((poster) => ({
        type: "minecraft:item",
        name: `${ID}:${poster}_poster`,
        conditions: [{ condition: "minecraft:block_state_property", block: `${ID}:space_poster`, properties: { poster, column: "0", row: "0" } }],
      })),
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/space_poster`,
  });
}
// The posters are hung on the rocket rather than built into her: light like the rest, but no part of her frame, and
// breakable.
function spacePosterFittings() {
  return ["space_poster"];
}
function spacePosterLang() {
  return {
    "block.aerolarge.space_poster": "Space Poster",
    "item.aerolarge.black_hole_poster": "Black Hole Poster",
    "item.aerolarge.solar_system_poster": "Solar System Poster",
    "item.aerolarge.quasar_poster": "Quasar Poster",
    "item.aerolarge.earthrise_poster": "Earthrise Poster",
  };
}
// The rocket's rover bay (see RoverHatchBlock and RoverPanelBlock). Its door's parts are drawn only by the bay's
// renderer (see RoverBayRenderer), door, platform, boom and all, so their model is only a particle texture. Its panel
// is a board two sixteenths thick at the back of its block, as the skywriting board is, its face in the colour of how
// the bay stands. Only the blueprint places them, so neither has an item or drops anything, and both are part of her
// frame, which only a wrench takes apart, as her other controls are. The rover's remote: its antenna's tip lit green
// once it is paired (see RoverClient).
{
  write(`assets/${ID}/blockstates/rover_hatch.json`, { variants: { "": { model: `${ID}:block/rover_hatch` } } });
  write(`assets/${ID}/models/block/rover_hatch.json`, { textures: { particle: `${ID}:block/airframe_panel` } });
  cubes.rover_hatch = null;
  const variants = {};
  for (const status of ["ready", "busy", "out", "refused"]) {
    write(`assets/${ID}/models/block/rover_panel_${status}.json`, {
      parent: "minecraft:block/block",
      textures: { front: `${ID}:block/rover_panel_front_${status}`, side: `${ID}:block/rover_panel_side`, particle: `${ID}:block/rover_panel_side` },
      elements: [{
        from: [0, 0, 14], to: [16, 16, 16],
        faces: {
          north: { texture: "#front" }, south: { texture: "#side" }, up: { texture: "#side", uv: [0, 14, 16, 16] },
          down: { texture: "#side", uv: [0, 14, 16, 16] }, east: { texture: "#side", uv: [14, 0, 16, 16] }, west: { texture: "#side", uv: [0, 0, 2, 16] },
        },
      }],
    });
    for (const [facing, y] of Object.entries(yRot)) {
      variants[`facing=${facing},status=${status}`] = { model: `${ID}:block/rover_panel_${status}`, y };
    }
  }
  write(`assets/${ID}/blockstates/rover_panel.json`, { variants });
  cubes.rover_panel = null;
  write(`assets/${ID}/models/item/rover_remote.json`, {
    parent: "minecraft:item/generated",
    textures: { layer0: `${ID}:item/rover_remote` },
    overrides: [{ predicate: { [`${ID}:paired`]: 1 }, model: `${ID}:item/rover_remote_paired` }],
  });
  write(`assets/${ID}/models/item/rover_remote_paired.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/rover_remote_paired` } });
}
/** The rover's motor, its beeps on and off, its horn and the warning near the edge of its range. */
function roverSounds() {
  return Object.fromEntries(["rover_motor", "rover_start", "rover_stop", "rover_horn", "rover_range"].map((name) =>
    [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function roverLang() {
  return {
    "block.aerolarge.rover_hatch": "Rover Bay Door",
    "block.aerolarge.rover_panel": "Rover Bay Panel",
    "entity.aerolarge.rover": "Rover",
    "item.aerolarge.rover_remote": "Rover Remote",
    "item.aerolarge.rover_remote.paired": "Paired with its rover: use it to drive, sneak and use it to call the rover home",
    "item.aerolarge.rover_remote.unpaired": "Not paired: use it on a rover, or on its bay's panel",
    "message.aerolarge.rover.deploying": "Opening the bay: the rover is on its way down",
    "message.aerolarge.rover.not_still": "The bay only opens while she stands still on her fins, her autopilot off",
    "message.aerolarge.rover.already_out": "The rover is out already",
    "message.aerolarge.rover.busy": "The winch is busy: wait for it",
    "message.aerolarge.rover.coming_home": "The rover is coming home",
    "message.aerolarge.rover.already_home": "The rover is in its bay",
    "message.aerolarge.rover.has_remote": "You carry a remote for this rover already",
    "message.aerolarge.rover.remote_given": "A remote paired with the rover: use it to drive",
    "message.aerolarge.rover.paired": "Remote paired with this rover",
    "message.aerolarge.rover.no_answer": "No answer from the rover: it is out of reach",
    "message.aerolarge.rover.unpaired": "This remote is not paired: use it on a rover, or on its bay's panel",
    "message.aerolarge.rover.countdown_holding": "Countdown holding: the rover is coming home first",
    "screen.aerolarge.rover.camera": "ROVER CAM",
    "screen.aerolarge.rover.hint": "%s %s %s %s: drive    %s: horn    %s: back to yourself",
    "subtitles.aerolarge.rover_motor": "Rover whirs",
    "subtitles.aerolarge.rover_start": "Rover beeps on",
    "subtitles.aerolarge.rover_stop": "Rover beeps off",
    "subtitles.aerolarge.rover_horn": "Rover honks",
    "subtitles.aerolarge.rover_range": "Rover's signal fades",
  };
}

// The rocket's lab (see RocketLab). The glass of her tardigrade's tank (see HabitatGlassBlock): a pane a pixel thick on
// each side of its block that looks out of the tank, drawn along the north side and turned for the others, so a block
// at a corner has two; on its lowest row moss over the floor of its block and a steel kerb along the pane's foot, on
// its highest a soft light along the pane's top, inside. Its feeding hatch: a pane with a steel-framed opening in its
// middle and a steel flap over it, slid aside when open. The instruments, each modelled facing north: a microscope in
// white enamel on a charcoal foot, its eyepieces tilted up toward whoever uses it and its stage lit from under; a rack
// of five vials, their liquids glowing; a scan screen on a stand; and the vitals screen, a panel of the tank's wall,
// beating or, curled up, still. The tank's floor patches lie on the floor as a carpet does: the pool, rippling water
// over gravel; the pebbles, smooth stones in moss. All are placed only by the blueprint, so they have no items and
// give nothing back; the glass and the hatch are built into her, the rest fitted (see labFittings).
{
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  const pane = { texture: "#glass" };
  write(`assets/${ID}/models/block/habitat_glass_pane.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:translucent", ambientocclusion: false,
    textures: { glass: `${ID}:block/habitat_glass`, particle: `${ID}:block/habitat_glass` },
    elements: [{ from: [0, 0, 0], to: [16, 16, 1], faces: { north: pane, south: pane } }],
  });
  write(`assets/${ID}/models/block/habitat_glass_kerb.json`, boxes({ all: `${ID}:block/habitat_kerb`, particle: `${ID}:block/habitat_kerb` },
    [[[0, 0, 0], [16, 2, 1.5], "#all"]]));
  write(`assets/${ID}/models/block/habitat_glass_light.json`, boxes({ all: `${ID}:block/habitat_light`, particle: `${ID}:block/habitat_light` },
    [[[0, 15, 1], [16, 16, 2.5], "#all", true]]));
  write(`assets/${ID}/models/block/habitat_glass_moss.json`, boxes({ all: "minecraft:block/moss_block", particle: "minecraft:block/moss_block" },
    [[[0, 0, 0], [16, 1, 16], "#all"]]));
  const sides = Object.entries(yRot).map(([side, y]) => ({ side, y }));
  write(`assets/${ID}/blockstates/habitat_glass.json`, {
    multipart: [
      ...sides.map(({ side, y }) => ({ when: { [side]: "true" }, apply: { model: `${ID}:block/habitat_glass_pane`, y } })),
      ...sides.map(({ side, y }) => ({ when: { [side]: "true", row: "floor" }, apply: { model: `${ID}:block/habitat_glass_kerb`, y } })),
      ...sides.map(({ side, y }) => ({ when: { [side]: "true", row: "top" }, apply: { model: `${ID}:block/habitat_glass_light`, y } })),
      { when: { row: "floor" }, apply: { model: `${ID}:block/habitat_glass_moss` } },
    ],
  });
  cubes.habitat_glass = null;

  const frame = "#frame";
  for (const open of [false, true]) {
    const flap = open ? [[11.5, 5, -1], [17.5, 11, -0.5]] : [[5, 5, -0.25], [11, 11, 0.75]];
    write(`assets/${ID}/models/block/habitat_hatch${open ? "_open" : ""}.json`, {
      parent: "minecraft:block/block", render_type: "minecraft:translucent",
      textures: { glass: `${ID}:block/habitat_glass`, frame: `${ID}:block/habitat_hatch_frame`, flap: `${ID}:block/habitat_hatch_flap`,
        particle: `${ID}:block/habitat_hatch_frame` },
      elements: [
        ...[[[0, 0], [16, 5]], [[0, 11], [16, 16]], [[0, 5], [5, 11]], [[11, 5], [16, 11]]].map(([[x0, y0], [x1, y1]]) =>
          ({ from: [x0, y0, 0], to: [x1, y1, 1], faces: { north: pane, south: pane } })),
        ...[[[4, 4], [12, 5]], [[4, 11], [12, 12]], [[4, 5], [5, 11]], [[11, 5], [12, 11]]].map(([[x0, y0], [x1, y1]]) =>
          solid([x0, y0, -0.5], [x1, y1, 1.5], frame)),
        solid(flap[0], flap[1], "#flap"),
      ],
    });
  }
  write(`assets/${ID}/blockstates/habitat_hatch.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((open) =>
      [`facing=${facing},open=${open}`, { model: `${ID}:block/habitat_hatch${open ? "_open" : ""}`, y }]))),
  });
  cubes.habitat_hatch = null;

  const instrument = { white: `${ID}:block/lab_white`, dark: `${ID}:block/lab_dark`, chrome: `${ID}:block/lab_chrome`, particle: `${ID}:block/lab_white` };
  // Its foot, its arm and head, its stage, the objective under the head, the focus knobs, the lamp under the stage, and
  // the eyepieces tilted up toward its front.
  const microscope = boxes(instrument, [
    [[4.5, 0, 5], [11.5, 1, 13], "#dark"],
    [[7, 1, 10.5], [9, 10, 12.5], "#white"],
    [[6.5, 9, 5.5], [9.5, 11.5, 12.5], "#white"],
    [[5, 4, 5.5], [11, 5, 10.5], "#dark"],
    [[7.25, 5.5, 7.25], [8.75, 9, 8.75], "#chrome"],
    [[5.5, 6, 9.5], [6.5, 7.5, 11], "#dark"],
    [[9.5, 6, 9.5], [10.5, 7.5, 11], "#dark"],
    [[7.25, 1, 7], [8.75, 1.5, 8.5], "#white", true],
    [[7, 11, 4], [9, 13.5, 7], "#chrome"],
  ]);
  microscope.elements.at(-1).rotation = { angle: 22.5, axis: "x", origin: [8, 11.5, 6] };
  write(`assets/${ID}/models/block/lab_microscope.json`, microscope);
  write(`assets/${ID}/models/block/lab_samples.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:translucent",
    textures: { rack: `${ID}:block/lab_chrome`, vials: `${ID}:block/lab_vials`, particle: `${ID}:block/lab_chrome` },
    elements: [
      solid([2, 0, 5], [14, 2, 11], "#rack"),
      solid([2, 6, 6.5], [14, 6.75, 9.5], "#rack"),
      ...[0, 1, 2, 3, 4].map((vial) => {
        const x = 2.75 + vial * 2.2;
        const uv = [1 + vial * 3, 0, 3 + vial * 3, 16];
        const face = { texture: "#vials", uv };
        return { from: [x, 2, 7.25], to: [x + 1.5, 8.5, 8.75], shade: false,
          faces: { north: { ...face, ...lit }, south: { ...face, ...lit }, east: face, west: face, up: { texture: "#vials", uv: [uv[0], 0, uv[2], 2] } } };
      }),
    ],
  });
  const screen = (name, texture) => ({
    parent: "minecraft:block/block",
    textures: { screen: `${ID}:block/${texture}`, casing: `${ID}:block/lab_dark`, stand: `${ID}:block/lab_chrome`, particle: `${ID}:block/lab_dark` },
    elements: name === "scan"
      ? [solid([5, 0, 7.5], [11, 0.5, 11], "#stand"), solid([7.5, 0.5, 9], [8.5, 4, 10], "#stand"), solid([1, 3, 8], [15, 13, 9.5], "#casing"),
        { from: [2, 4, 7.9], to: [14, 12, 8], shade: false, faces: { north: { texture: "#screen", uv: [1, 1, 15, 15], ...lit } } }]
      : [solid([0, 0, 0.1], [16, 16, 2], "#casing"), { from: [0, 0, 0], to: [16, 16, 0.1], shade: false, faces: { north: { texture: "#screen", ...lit } } }],
  });
  write(`assets/${ID}/models/block/lab_scan.json`, screen("scan", "lab_screen_scan"));
  write(`assets/${ID}/models/block/lab_vitals.json`, screen("vitals", "lab_screen_vitals"));
  write(`assets/${ID}/models/block/lab_vitals_dormant.json`, screen("vitals", "lab_screen_dormant"));
  const instrumentModel = (kind, dormant) => (kind === "vitals" && dormant ? "lab_vitals_dormant" : `lab_${kind}`);
  const variants = {};
  for (const kind of ["microscope", "samples", "scan", "vitals"]) {
    for (const dormant of [false, true]) {
      for (const [facing, y] of Object.entries(yRot)) {
        variants[`dormant=${dormant},facing=${facing},kind=${kind}`] = { model: `${ID}:block/${instrumentModel(kind, dormant)}`, y };
      }
    }
  }
  write(`assets/${ID}/blockstates/lab_instrument.json`, { variants });
  cubes.lab_instrument = null;

  write(`assets/${ID}/models/block/habitat_pool.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:translucent",
    textures: { bed: "minecraft:block/gravel", water: `${ID}:block/habitat_pool`, particle: `${ID}:block/habitat_pool` },
    elements: [
      { from: [0, 0, 0], to: [16, 0.5, 16], faces: { up: { texture: "#bed" }, down: { texture: "#bed", cullface: "down" } } },
      { from: [0, 0, 0], to: [16, 1, 16], faces: Object.fromEntries(["up", "north", "south", "east", "west"].map((face) => [face, { texture: "#water" }])) },
    ],
  });
  const stones = [[[2, 1, 3], [5, 2.5, 6], "#stone"], [[8, 1, 2], [10.5, 2, 4.5], "#tuff"], [[10, 1, 9], [14, 3, 13], "#stone"],
    [[4, 1, 10], [6.5, 2, 12.5], "#tuff"], [[12, 1, 4], [14, 1.75, 6], "#andesite"]];
  write(`assets/${ID}/models/block/habitat_pebbles.json`, boxes(
    { moss: "minecraft:block/moss_block", stone: "minecraft:block/smooth_stone", tuff: "minecraft:block/tuff", andesite: "minecraft:block/andesite",
      particle: "minecraft:block/moss_block" },
    [[[0, 0, 0], [16, 1, 16], "#moss"], ...stones]));
  write(`assets/${ID}/blockstates/habitat_floor.json`, {
    variants: { "kind=pool": { model: `${ID}:block/habitat_pool` }, "kind=pebbles": { model: `${ID}:block/habitat_pebbles` } },
  });
  cubes.habitat_floor = null;
}
// The lab's instruments and its tank's floor patches are fitted into the rocket rather than built into her: light like
// the rest, but no part of her frame, and breakable.
function labFittings() {
  return ["lab_instrument", "habitat_floor"];
}
// Her tardigrade's purr, soft and low now and then, and the bloop as it curls up into its tun.
function labSounds() {
  return Object.fromEntries(["tardigrade_mrrp", "tardigrade_bloop"].map((name) => [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function labLang() {
  return {
    "block.aerolarge.habitat_glass": "Tank Glass",
    "block.aerolarge.habitat_hatch": "Feeding Hatch",
    "block.aerolarge.lab_instrument": "Lab Instrument",
    "block.aerolarge.habitat_floor": "Tank Floor",
    "entity.aerolarge.tardigrade": "Tardigrade",
    "subtitles.aerolarge.tardigrade_mrrp": "Tardigrade purrs",
    "subtitles.aerolarge.tardigrade_bloop": "Tardigrade curls up",
  };
}

// Room dividers, and the exit signs over the doors between the rooms: fitted into the cabin, so breakable, and no
// part of the hull (see FITTINGS).
const ROOM_DIVIDERS = ["cabin_curtain", "cabin_partition", "cabin_arch", "exit_sign"];
const ROOM_DIVIDER_LANG = {
  "block.aerolarge.cabin_curtain": "Cabin Curtain",
  "block.aerolarge.cabin_partition": "Cabin Partition",
  "block.aerolarge.cabin_arch": "Cabin Arch",
  "block.aerolarge.exit_sign": "Exit Sign",
};
// The cabin curtain, drawn facing north (see CurtainBlock), one model for each way it can be drawn and joined; the
// colour is not in it, the client tints the cloth. Shut, four pleats of cloth a pixel and a half thick, standing
// alternately forward and back of the middle so the folds are deep; no faces between pieces one above another, and
// the lowest piece's cloth stops a pixel off the floor on its darker hem. Open, the cloth is gathered at whichever end
// the curtain stops (left is west), a bunch of folds a quarter of the block wide, its lowest piece tied back with a
// brass band; pieces in the middle show nothing. The topmost pieces carry the steel rail with a ring at each pleat.
{
  const all = ["north", "south", "east", "west", "up", "down"];
  // A box whose faces show the parts of their texture over them, as a full block's would; cloth takes the dye's tint.
  const box = (from, to, texture, tinted, skip = []) => {
    const [x0, y0, z0] = from;
    const [x1, y1, z1] = to;
    const uv = {
      north: [16 - x1, 16 - y1, 16 - x0, 16 - y0], south: [x0, 16 - y1, x1, 16 - y0],
      east: [16 - z1, 16 - y1, 16 - z0, 16 - y0], west: [z0, 16 - y1, z1, 16 - y0],
      up: [x0, z0, x1, z1], down: [x0, 16 - z1, x1, 16 - z0],
    };
    const faces = all.filter((face) => !skip.includes(face))
      .map((face) => [face, { texture, uv: uv[face], ...(tinted ? { tintindex: 0 } : {}) }]);
    return { from, to, faces: Object.fromEntries(faces) };
  };
  // Pleats a pixel and a half thick, forward and back of the middle; the cloth's columns lit on the crests and shaded
  // in the folds, so a pleat standing forward shows a crest to the front and a fold to the back, and one set back the
  // other way round.
  const pleat = (k, bottom, top, skip) => {
    const forward = k % 2 === 0;
    const z = forward ? 6.75 : 7.75;
    const base = (k >> 1) * 8;
    const crest = [base, 16 - top, base + 4, 16 - bottom];
    const fold = [base + 4, 16 - top, base + 8, 16 - bottom];
    const element = box([4 * k, bottom, z], [4 * k + 4, top, z + 1.5], "#cloth", true, skip);
    if (element.faces.north) element.faces.north.uv = forward ? crest : fold;
    if (element.faces.south) element.faces.south.uv = forward ? fold : crest;
    return element;
  };
  // An open curtain's bunch of folds at its west end; the east end's is the same mirrored.
  const FOLDS = [[0, 1.5, 6, 8.5], [1, 2.75, 7.5, 10], [2.25, 4, 6.25, 9], [3.5, 4.5, 7.25, 8.75]];
  const mirror = (x0, x1, east) => (east ? [16 - x1, 16 - x0] : [x0, x1]);
  const bunch = (east, bottom, top, skip) => FOLDS.map(([x0, x1, z0, z1]) => {
    const [from, to] = mirror(x0, x1, east);
    return box([from, bottom, z0], [to, top, z1], "#cloth", true, skip);
  });
  const tieBack = (east) => {
    const [from, to] = mirror(0, 4.75, east);
    return box([from, 4, 5.75], [to, 5.5, 10.25], "#tie", false);
  };
  const rail = [
    box([0, 15, 7.25], [16, 16, 8.75], "#rail", false),
    ...[1.5, 5.5, 9.5, 13.5].map((x) => box([x, 14, 7], [x + 1, 15, 9], "#rail", false)),
  ];
  const bits = (value) => (value ? 1 : 0);
  const name = (open, left, right, up, down) => `cabin_curtain_${open ? "open" : "closed"}_${[left, right, up, down].map(bits).join("")}`;
  const variants = {};
  for (const open of [false, true]) {
    for (const left of [false, true]) {
      for (const right of [false, true]) {
        for (const up of [false, true]) {
          for (const down of [false, true]) {
            const bottom = down ? 0 : 1;
            const top = up ? 16 : 14;
            const skip = [...(up ? ["up"] : []), ...(down ? ["down"] : [])];
            const elements = open
              ? [...(left ? [] : bunch(false, bottom, top, skip)), ...(right ? [] : bunch(true, bottom, top, skip)),
                ...(down || left ? [] : [tieBack(false)]), ...(down || right ? [] : [tieBack(true)])]
              : [0, 1, 2, 3].map((k) => pleat(k, bottom, top, skip));
            const cloth = `${ID}:block/cabin_curtain${down ? "" : "_hem"}`;
            write(`assets/${ID}/models/block/${name(open, left, right, up, down)}.json`, {
              parent: "minecraft:block/block",
              render_type: "minecraft:cutout",
              textures: { cloth, rail: `${ID}:block/cabin_curtain_rail`, tie: `${ID}:block/cabin_curtain_tie`, particle: `${ID}:block/cabin_curtain` },
              elements: [...elements, ...(up ? [] : rail)],
            });
            for (const [facing, y] of Object.entries(yRot)) {
              variants[`down=${down},facing=${facing},left=${left},open=${open},right=${right},up=${up}`] = {
                model: `${ID}:block/${name(open, left, right, up, down)}`, ...(y ? { y } : {}),
              };
            }
          }
        }
      }
    }
  }
  write(`assets/${ID}/blockstates/cabin_curtain.json`, { variants });
  write(`assets/${ID}/models/item/cabin_curtain.json`, { parent: `${ID}:block/${name(false, false, false, false, false)}` });
  write(`assets/${ID}/blockstates/cabin_partition.json`, { variants: { "": { model: `${ID}:block/cabin_partition` } } });
  write(`assets/${ID}/models/block/cabin_partition.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/cabin_partition` } });
  write(`assets/${ID}/models/item/cabin_partition.json`, { parent: `${ID}:block/cabin_partition` });
  // A curtain comes down in its colour, kept as the block state to hang it in (as picking one does, see
  // CurtainBlock); white, the cloth's own, is left off, so white curtains stack with new ones.
  const curtain = `${ID}:cabin_curtain`;
  write(`data/${ID}/loot_table/blocks/cabin_curtain.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0, bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item", name: curtain,
        functions: [{
          function: "minecraft:copy_state", block: curtain, properties: ["color"],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: curtain, properties: { color: "white" } } }],
        }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/cabin_curtain`,
  });
  for (const name of ROOM_DIVIDERS) {
    if (name !== "cabin_curtain" && name !== "cabin_arch") {
      dropsItself(name);
    }
    cubes[name] = null;
  }
}
// The cabin arch (see CabinArchBlock): each block of the bulkheads' archways that the curve or its white band passes
// through, drawn facing north, out from the archway's middle to the north and the wall's faces east and west. The
// archway is worked out a pixel at a time across the wall, from how far the pixel's middle is from the edge's middle
// line (round the arch's middle over the opening's sides, straight out to the side below them): inside the radius it
// is open; the first pixel out is the moulding's bead, flush with the wall's faces; the next its groove, set back half
// a pixel into both; then white panelling out to BAND; then the bulkhead's own cream panels. That finds the blocks
// the curve passes through, checked against CabinArchBlock.Part. The jamb, straight, is the pixels of each kind in it
// merged into boxes, showing a face only where a box meets the open archway or something set back further. The arch's
// pieces, and in the hand a narrow archway whole as one goes up put down by hand, are drawn round by the mod's own
// "cabin_arch" loader, from the same rings round the same curve (see CabinArchModel), so the arch's underside and its
// moulding follow the circle instead of stepping round it a pixel at a time.
{
  // How far out from the opening's edge the white reaches, in pixels: CabinArchBlock.BAND.
  const BAND = 6;
  // CabinArchBlock.Part: the jamb (the same for both sizes), then each size's arch blocks, by columns out from the
  // middle and rows over the opening, the opening reaching that many blocks either side of the middle.
  const SPANS = { narrow: 1, wide: 2 };
  const PARTS = ["jamb", "narrow_1_1", "narrow_2_1", "narrow_0_2", "narrow_1_2", "narrow_2_2",
    "wide_2_1", "wide_3_1", "wide_2_2", "wide_3_2", "wide_0_3", "wide_1_3", "wide_2_3"];
  // The arch's crowns, its pieces up the middle: the ones an archway put up whole comes down as.
  const CROWNS = PARTS.filter((part) => part.split("_")[1] === "0");
  const radius = (span) => 16 * span + 8;
  const rows = (span) => Math.ceil((radius(span) + BAND) / 16);
  // What each kind of pixel is drawn as, from the opening out: the bead, the groove, the white panelling and the
  // bulkhead's cream; and how far each is set back into both faces of the wall, in pixels.
  const KINDS = [
    { texture: "#trim", inset: 0 },
    { texture: "#groove", inset: 0.5 },
    { texture: "#arch", inset: 0 },
    { texture: "#wall", inset: 0 },
  ];
  // The kind of the pixel u across from the opening's middle and v up from the top of its sides; null where it is open.
  const kindAt = (span, u, v) => {
    const out = (v >= 0 ? Math.hypot(u + 0.5, v + 0.5) : Math.abs(u + 0.5)) - radius(span);
    return out < 0 ? null : out < 1 ? 0 : out < 2 ? 1 : out < BAND ? 2 : 3;
  };
  // Whether the face of a box of this kind against that pixel shows: against the open archway, or a kind set back
  // further. The faces between kinds flush with each other are inside the wall.
  const shows = (kind, against) => against === null || KINDS[against].inset > KINDS[kind].inset;
  // Boxes of the pixels of each kind in a stretch of the wall, width by height from its corner (u0, v0): each pixel
  // row by row, as far along as the row runs on in the same kind, then as far up as the rows over it do the same.
  const merge = (span, u0, v0, width, height) => {
    const taken = new Set();
    const found = [];
    for (let v = 0; v < height; v++) {
      for (let u = 0; u < width; u++) {
        const kind = kindAt(span, u0 + u, v0 + v);
        if (kind === null || taken.has(`${u},${v}`)) continue;
        let w = 1;
        while (u + w < width && !taken.has(`${u + w},${v}`) && kindAt(span, u0 + u + w, v0 + v) === kind) w++;
        const runsOn = (row) => [...Array(w).keys()].every((k) => !taken.has(`${u + k},${row}`) && kindAt(span, u0 + u + k, v0 + row) === kind);
        let h = 1;
        while (v + h < height && runsOn(v + h)) h++;
        for (let k = 0; k < w; k++) for (let l = 0; l < h; l++) taken.add(`${u + k},${v + l}`);
        found.push({ kind, u, v, w, h });
      }
    }
    return found;
  };
  // Whether any pixel along one side of a box shows its face: the pixels just past it, across or up the wall.
  const exposed = (span, u0, v0, box, side) => {
    const along = side === "out" || side === "in" ? box.h : box.w;
    return [...Array(along).keys()].some((k) => {
      const [u, v] = {
        out: [box.u + box.w, box.v + k], in: [box.u - 1, box.v + k], up: [box.u + k, box.v + box.h], down: [box.u + k, box.v - 1],
      }[side];
      return shows(box.kind, kindAt(span, u0 + u, v0 + v));
    });
  };
  // A face of a box, its texture over the part of it the box covers as a full block's would.
  const face = (texture, uv, cullface) => ({ texture, uv, ...(cullface ? { cullface } : {}) });
  // A block's boxes as model elements, drawn facing north: across the wall (u) runs north, up it (v) up, and its
  // faces are west and east, each set back by its kind's inset.
  const elements = (span, u0, v0) => merge(span, u0, v0, 16, 16).map((box) => {
    const { texture, inset } = KINDS[box.kind];
    const [x0, x1] = [inset, 16 - inset];
    const [y0, y1] = [box.v, box.v + box.h];
    const [z0, z1] = [16 - box.u - box.w, 16 - box.u];
    const faces = {
      west: face(texture, [z0, 16 - y1, z1, 16 - y0], inset === 0 && "west"),
      east: face(texture, [16 - z1, 16 - y1, 16 - z0, 16 - y0], inset === 0 && "east"),
    };
    if (exposed(span, u0, v0, box, "out")) faces.north = face(texture, [16 - x1, 16 - y1, 16 - x0, 16 - y0], z0 === 0 && "north");
    if (exposed(span, u0, v0, box, "in")) faces.south = face(texture, [x0, 16 - y1, x1, 16 - y0], z1 === 16 && "south");
    if (exposed(span, u0, v0, box, "up")) faces.up = face(texture, [x0, z0, x1, z1], y1 === 16 && "up");
    if (exposed(span, u0, v0, box, "down")) faces.down = face(texture, [x0, 16 - z1, x1, 16 - z0], y0 === 0 && "down");
    return { from: [x0, y0, z0], to: [x1, y1, z1], faces };
  });
  const textures = {
    trim: `${ID}:block/cabin_arch_trim`, groove: `${ID}:block/cabin_arch_groove`, arch: `${ID}:block/cabin_arch`,
    wall: `${ID}:block/cabin_partition`, particle: `${ID}:block/cabin_arch`,
  };
  // Every block of each size's archway, from its jambs up, that is neither open nor plain wall, checked against PARTS.
  const found = ["jamb"];
  for (const [size, span] of Object.entries(SPANS)) {
    for (let over = 1; over <= rows(span); over++) {
      for (let out = 0; out <= span + 1; out++) {
        const kinds = new Set();
        for (let u = 0; u < 16; u++) for (let v = 0; v < 16; v++) kinds.add(kindAt(span, 16 * out - 8 + u, 16 * (over - 1) + v));
        if (kinds.size > 1 || !kinds.has(null) && !kinds.has(3)) found.push(`${size}_${out}_${over}`);
      }
    }
  }
  if (found.join() !== PARTS.join()) throw new Error(`cabin_arch: the curve passes through ${found}, CabinArchBlock.Part lists ${PARTS}`);
  // The jamb is the column beside the opening, a row below its top, the same for both sizes.
  write(`assets/${ID}/models/block/cabin_arch_jamb.json`, { parent: "minecraft:block/block", textures, elements: elements(SPANS.narrow, radius(SPANS.narrow), -16) });
  for (const part of PARTS.filter((part) => part !== "jamb")) {
    write(`assets/${ID}/models/block/cabin_arch_${part}.json`, { loader: `${ID}:cabin_arch`, part, textures });
  }
  write(`assets/${ID}/blockstates/cabin_arch.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => PARTS.map((part) =>
      [`facing=${facing},part=${part}`, { model: `${ID}:block/cabin_arch_${part}`, ...(y ? { y } : {}) }]))),
  });
  // In the hand, a narrow archway whole as it goes up put down by hand, seen from the front: the wall shrunk to fit the
  // block, each face in one flat colour of its texture (see CabinArchModel).
  write(`assets/${ID}/models/item/cabin_arch.json`, {
    parent: "minecraft:block/block", loader: `${ID}:cabin_arch`, span: SPANS.narrow, textures,
    display: {
      gui: { rotation: [15, -25, 0], translation: [0, 0, 0], scale: [0.9, 0.9, 0.9] },
      ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
      fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] },
      thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
      firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
      firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
    },
  });
  // A piece standing alone, as the airliner's do, comes down as itself, kept as the block state to put it down as (as
  // picking one does, see CabinArchBlock). An archway put up whole comes down whole, as the one Cabin Arch it went up
  // from, which only its crown gives back, so taking it down never gives back more than went into it.
  const arch = `${ID}:cabin_arch`;
  const whole = (value) => ({ condition: "minecraft:block_state_property", block: arch, properties: { whole: value } });
  write(`data/${ID}/loot_table/blocks/cabin_arch.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0, bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item", name: arch,
        functions: [{ function: "minecraft:copy_state", block: arch, properties: ["part"] }],
      }],
      conditions: [whole("false"), { condition: "minecraft:survives_explosion" }],
    }, {
      rolls: 1.0, bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: arch }],
      conditions: [
        whole("true"),
        { condition: "minecraft:any_of", terms: CROWNS.map((part) => ({ condition: "minecraft:block_state_property", block: arch, properties: { part } })) },
        { condition: "minecraft:survives_explosion" },
      ],
    }],
    random_sequence: `${ID}:blocks/cabin_arch`,
  });
}
// The exit sign: a thin plate with its back to the wall, drawn facing north, glowing whatever the light around.
write(`assets/${ID}/models/block/exit_sign.json`, boxes(
  { face: `${ID}:block/exit_sign`, casing: `${ID}:block/cabin_partition`, particle: `${ID}:block/exit_sign` },
  [[[1, 4, 14.5], [15, 12, 16], { north: "#face", all: "#casing" }, true]],
));
facing("exit_sign");
write(`assets/${ID}/models/item/exit_sign.json`, { parent: `${ID}:block/exit_sign` });

// The bulkhead sign (see BulkheadSignBlock), drawn facing north, one model for each picture: a whole block of the
// partition's cream panelling, and on its north face the lightbox, a brushed steel bezel round the block's edge standing
// three quarters of a pixel proud, and inside it, half a pixel back from the bezel's lip, the printed picture, glowing
// whatever the light around. In the hand, the first picture. Taken down, a sign keeps its picture, the first left off.
const BULKHEAD_SIGN_PICTURES = ["seat", "drop", "ball", "cup", "lavatory"];
// A lightbox set into the bulkhead, its picture the texture given, drawn facing north: the bulkhead signs' and the WC
// sign's.
function bulkheadLightbox(name, picture) {
  const block = (texture) => `${ID}:block/${texture}`;
  const bezel = (from, to) => solid(from, to, "#bezel");
  write(`assets/${ID}/models/block/${name}.json`, {
    parent: "minecraft:block/block",
    textures: { wall: block("cabin_partition"), bezel: block("bulkhead_sign_bezel"), picture: block(picture), particle: block("cabin_partition") },
    elements: [
      {
        from: [0, 0, 0], to: [16, 16, 16],
        faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, { texture: "#wall", cullface: face }])),
      },
      bezel([0, 15, -0.75], [16, 16, 0]),
      bezel([0, 0, -0.75], [16, 1, 0]),
      bezel([0, 1, -0.75], [1, 15, 0]),
      bezel([15, 1, -0.75], [16, 15, 0]),
      {
        from: [1, 1, -0.25], to: [15, 15, 0], shade: false,
        faces: { north: { texture: "#picture", uv: [1, 1, 15, 15], neoforge_data: { block_light: 15, sky_light: 15 } } },
      },
    ],
  });
}
{
  for (const picture of BULKHEAD_SIGN_PICTURES) {
    bulkheadLightbox(`bulkhead_sign_${picture}`, `bulkhead_sign_${picture}`);
  }
  write(`assets/${ID}/blockstates/bulkhead_sign.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => BULKHEAD_SIGN_PICTURES.map((picture) =>
      [`facing=${facing},picture=${picture}`, { model: `${ID}:block/bulkhead_sign_${picture}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/models/item/bulkhead_sign.json`, { parent: `${ID}:block/bulkhead_sign_${BULKHEAD_SIGN_PICTURES[0]}` });
  const sign = `${ID}:bulkhead_sign`;
  write(`data/${ID}/loot_table/blocks/bulkhead_sign.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0, bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item", name: sign,
        functions: [{
          function: "minecraft:copy_state", block: sign, properties: ["picture"],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: sign, properties: { picture: BULKHEAD_SIGN_PICTURES[0] } } }],
        }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/bulkhead_sign`,
  });
  cubes.bulkhead_sign = null;
}
// The sign is fitted into the bulkheads as they are: breakable, and no part of the airframe.
function bulkheadSignFittings() {
  return ["bulkhead_sign"];
}
// A partition with a pane of glass over glowstone for its lightbox.
function bulkheadSignRecipes() {
  shaped("bulkhead_sign", ["G", "L", "P"], { G: "minecraft:glass_pane", L: "minecraft:glowstone_dust", P: `${ID}:cabin_partition` }, 1);
}
function bulkheadSignLang() {
  return {
    "block.aerolarge.bulkhead_sign": "Bulkhead Sign",
  };
}

// The WC signs (see WcSignBlock and OverheadWcSignBlock). The one over a WC's door: the bulkhead's lightbox, its toilet
// lit green while the WC is free and red while it is taken. The overhead one, drawn facing north: a slim off-white
// lightbox three pixels deep, its back against the south side of the block, or hung in the block's middle on two
// brushed steel rods from the block over it; on its face, inside a pixel of the housing all round, the arrow and the
// toilet glowing whatever the light around, a quarter of a pixel proud. In the hand, each as it is when its WC is free,
// the overhead one pointing straight on. Taken down, the overhead sign keeps its arrow, the first left off.
const WC_SIGN_ARROWS = ["ahead", "left", "right", "down", "none"];
{
  const block = (name) => `${ID}:block/${name}`;
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  for (const taken of [false, true]) {
    const state = taken ? "_occupied" : "";
    bulkheadLightbox(`wc_sign${state}`, `wc_sign${state}`);
    for (const arrow of WC_SIGN_ARROWS) {
      for (const hanging of [false, true]) {
        // The lightbox's front face, and its depth either side of it.
        const front = hanging ? 6.5 : 13;
        const back = front + 3;
        const housing = {
          from: [0, 2, front], to: [16, 14, back],
          faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) =>
            [face, { texture: "#housing", ...(face === "south" && !hanging ? { cullface: "south" } : {}) }])),
        };
        const face = {
          from: [1, 3, front - 0.25], to: [15, 13, front], shade: false,
          faces: { north: { texture: "#face", uv: [1, 3, 15, 13], ...lit } },
        };
        const rods = hanging ? [solid([2.5, 14, 7.5], [3.5, 16, 8.5], "#rod"), solid([12.5, 14, 7.5], [13.5, 16, 8.5], "#rod")] : [];
        write(`assets/${ID}/models/block/overhead_wc_sign_${arrow}${state}${hanging ? "_hanging" : ""}.json`, {
          parent: "minecraft:block/block",
          textures: {
            housing: block("overhead_wc_sign"), face: block(`overhead_wc_sign_${arrow}${state}`), rod: block("bulkhead_sign_bezel"),
            particle: block("overhead_wc_sign"),
          },
          elements: [housing, face, ...rods],
        });
      }
    }
  }
  write(`assets/${ID}/blockstates/wc_sign.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((taken) =>
      [`facing=${facing},occupied=${taken}`, { model: `${ID}:block/wc_sign${taken ? "_occupied" : ""}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/blockstates/overhead_wc_sign.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => WC_SIGN_ARROWS.flatMap((arrow) =>
      [false, true].flatMap((hanging) => [false, true].map((taken) => [
        `arrow=${arrow},facing=${facing},hanging=${hanging},occupied=${taken}`,
        { model: `${ID}:block/overhead_wc_sign_${arrow}${taken ? "_occupied" : ""}${hanging ? "_hanging" : ""}`, ...(y ? { y } : {}) },
      ]))))),
  });
  write(`assets/${ID}/models/item/wc_sign.json`, { parent: `${ID}:block/wc_sign` });
  write(`assets/${ID}/models/item/overhead_wc_sign.json`, { parent: `${ID}:block/overhead_wc_sign_${WC_SIGN_ARROWS[0]}` });
  dropsItself("wc_sign");
  const sign = `${ID}:overhead_wc_sign`;
  write(`data/${ID}/loot_table/blocks/overhead_wc_sign.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0, bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item", name: sign,
        functions: [{
          function: "minecraft:copy_state", block: sign, properties: ["arrow"],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: sign, properties: { arrow: WC_SIGN_ARROWS[0] } } }],
        }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/overhead_wc_sign`,
  });
  cubes.wc_sign = null;
  cubes.overhead_wc_sign = null;
}
// The WC signs are fitted into the cabin as the bulkhead signs are: breakable, and no part of the airframe.
function wcSignFittings() {
  return ["wc_sign", "overhead_wc_sign"];
}
// The bulkhead sign's lightbox with a green and a red lamp in it; the overhead one a smaller lightbox on iron.
function wcSignRecipes() {
  shaped("wc_sign", ["LGR", " D ", " P "], {
    L: "minecraft:lime_dye", G: "minecraft:glass_pane", R: "minecraft:red_dye", D: "minecraft:glowstone_dust", P: `${ID}:cabin_partition`,
  }, 1);
  shaped("overhead_wc_sign", ["LGR", "IDI"], {
    L: "minecraft:lime_dye", G: "minecraft:glass_pane", R: "minecraft:red_dye", I: "minecraft:iron_nugget", D: "minecraft:glowstone_dust",
  }, 2);
}
function wcSignLang() {
  return {
    "block.aerolarge.wc_sign": "WC Sign",
    "block.aerolarge.overhead_wc_sign": "Overhead WC Sign",
  };
}

// The staircases between the decks: their fittings, recipes and names are in the functions below, which the shared
// lists further down take in. The stair (see CabinStairsBlock), drawn climbing north: its two steps as its outline is,
// each a carpeted tread over a white riser and white sides, and along each tread's front edge a brushed steel nosing
// standing a quarter of a pixel proud of the tread and the riser. Turned over it is all white, a slab over the north
// half. The landing: carpet on top, white panelling round it and under it. In the hand, the stair as vanilla's is held.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = {
    tread: block("cabin_stairs_tread"), riser: block("cabin_stairs_riser"), panel: block("cabin_stairs_panel"),
    nosing: block("cabin_stairs_nosing"), particle: block("cabin_stairs_tread"),
  };
  const face = (texture, cullface, uv) => ({ texture, ...(uv ? { uv } : {}), ...(cullface ? { cullface } : {}) });
  // A tread's nosing, along the front edge of a tread this high over a riser this far forward.
  const nosing = (top, front) => ({
    from: [1, top - 1, front - 1.75], to: [15, top + 0.25, front + 0.25],
    faces: {
      up: face("#nosing", null, [1, 0, 15, 2]), down: face("#nosing", null, [1, 0, 15, 2]),
      north: face("#nosing", null, [1, 0, 15, 1.25]), south: face("#nosing", null, [1, 0, 15, 1.25]),
      east: face("#nosing", null, [0, 0, 2, 1.25]), west: face("#nosing", null, [0, 0, 2, 1.25]),
    },
  });
  write(`assets/${ID}/models/block/cabin_stairs.json`, {
    parent: "minecraft:block/block", textures,
    elements: [
      { from: [0, 0, 8], to: [16, 8, 16], faces: {
        up: face("#tread"), south: face("#riser", "south"), east: face("#panel", "east"), west: face("#panel", "west"), down: face("#panel", "down"),
      } },
      { from: [0, 0, 0], to: [16, 8, 8], faces: {
        north: face("#panel", "north"), east: face("#panel", "east"), west: face("#panel", "west"), down: face("#panel", "down"),
      } },
      { from: [0, 8, 0], to: [16, 16, 8], faces: {
        up: face("#tread", "up"), south: face("#riser"), north: face("#panel", "north"), east: face("#panel", "east"), west: face("#panel", "west"),
      } },
      nosing(8, 16),
      nosing(16, 8),
    ],
  });
  // White all over: the faces on the block's sides culled against whatever covers them, those inside it left out.
  const white = (cull, inside) => Object.fromEntries(["north", "south", "east", "west", "up", "down"].filter((side) => side !== inside)
    .map((side) => [side, face("#panel", cull.includes(side) && side)]));
  write(`assets/${ID}/models/block/cabin_stairs_soffit.json`, {
    parent: "minecraft:block/block", textures: { panel: block("cabin_stairs_panel"), particle: block("cabin_stairs_panel") },
    elements: [
      { from: [0, 0, 0], to: [16, 16, 8], faces: white(["north", "east", "west", "up", "down"]) },
      { from: [0, 8, 8], to: [16, 16, 16], faces: white(["south", "east", "west", "up"], "north") },
    ],
  });
  write(`assets/${ID}/blockstates/cabin_stairs.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [["bottom", "cabin_stairs"], ["top", "cabin_stairs_soffit"]]
      .map(([half, model]) => [`facing=${facing},half=${half}`, { model: `${ID}:block/${model}`, ...(y ? { y } : {}) }]))),
  });
  // Held as vanilla's stairs are, whose model climbs east where this one climbs north: every view turned a quarter back.
  write(`assets/${ID}/models/item/cabin_stairs.json`, {
    parent: `${ID}:block/cabin_stairs`,
    display: {
      gui: { rotation: [30, 45, 0], translation: [0, 0, 0], scale: [0.625, 0.625, 0.625] },
      head: { rotation: [0, -180, 0], translation: [0, 0, 0], scale: [1, 1, 1] },
      ground: { rotation: [0, -90, 0], translation: [0, 3, 0], scale: [0.25, 0.25, 0.25] },
      fixed: { rotation: [0, -90, 0], translation: [0, 0, 0], scale: [0.5, 0.5, 0.5] },
      thirdperson_righthand: { rotation: [75, -45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
      thirdperson_lefthand: { rotation: [75, -225, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
      firstperson_righthand: { rotation: [0, -45, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
      firstperson_lefthand: { rotation: [0, 135, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
    },
  });
  write(`assets/${ID}/blockstates/cabin_landing.json`, { variants: { "": { model: `${ID}:block/cabin_landing` } } });
  write(`assets/${ID}/models/block/cabin_landing.json`, {
    parent: "minecraft:block/cube_bottom_top",
    textures: { top: block("cabin_landing"), bottom: block("cabin_stairs_panel"), side: block("cabin_stairs_panel") },
  });
  write(`assets/${ID}/models/item/cabin_landing.json`, { parent: `${ID}:block/cabin_landing` });
  dropsItself("cabin_stairs");
  dropsItself("cabin_landing");
  cubes.cabin_stairs = null;
  cubes.cabin_landing = null;
}
// The glass rail (see CabinRailBlock), each edge drawn along the north side of its block, seen from inside (from the
// south) with its left end west: clear glass half a pixel thick from the floor up to a brushed steel handrail two
// pixels wide whose top is a block and a quarter up, and a post in the middle of the edge. Level, the handrail stops
// two pixels short of either corner, where a corner piece joins it to the next edge's, so rails meeting at a corner
// share one corner and none of them overlap; raked, over a stair climbing along the edge to the right, the handrail
// is one bar sloping with the flight, from the height of the level rail on the landing below at its left end to that
// of the one on the landing above at its right, and the glass, in strips two pixels wide, reaches from each tread up
// into the handrail, where its stepped top is hidden. Raked to the left it is the same mirrored.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = { glass: block("cabin_rail_glass"), steel: block("cabin_rail_steel"), particle: block("cabin_rail_steel") };
  // The handrail's top (CabinRailBlock.HANDRAIL), how thick it is (straight up when level, across the bar when raked)
  // and where the glass is.
  const TOP = 20;
  const BAR = 1.5;
  const GLASS = [0.75, 1.25];
  // Steel, its brushed streaks along each face's length.
  const steel = (from, to) => ({
    from, to, faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((side) => [side, { texture: "#steel", uv: [0, 4, 16, 6] }])),
  });
  const pane = (x0, x1, bottom, top) => ({
    from: [x0, bottom, GLASS[0]], to: [x1, top, GLASS[1]],
    faces: { north: { texture: "#glass", uv: [16 - x1, 0, 16 - x0, 16] }, south: { texture: "#glass", uv: [x0, 0, x1, 16] } },
  });
  const post = (bottom, top) => steel([7.25, bottom, 0.25], [8.75, top, 1.75]);
  const model = (name, elements) => write(`assets/${ID}/models/block/${name}.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:translucent", textures, elements,
  });
  model("cabin_rail_level", [pane(0, 16, 0, TOP - BAR), steel([2, TOP - BAR, 0], [14, TOP, 2]), post(0, TOP - BAR)]);
  model("cabin_rail_corner", [steel([0, TOP - BAR, 0], [2, TOP, 2])]);
  // Raked: the handrail's top runs from TOP - 16 at the left end to TOP at the right, the treads under it from half a
  // block down on the left half to the rail's own floor on the right.
  // How deep the sloping handrail is, straight down.
  const depth = BAR * Math.SQRT2;
  const raked = (mirror) => {
    const x = (from, to) => (mirror ? [16 - to, 16 - from] : [from, to]);
    const top = (at) => at + TOP - 16;
    const elements = [];
    for (let u = 0; u < 16; u += 2) {
      const [x0, x1] = x(u, u + 2);
      elements.push(pane(x0, x1, u < 8 ? -8 : 0, top(u + 2) - depth));
    }
    const [p0, p1] = x(7.25, 8.75);
    elements.push(steel([p0, -8, 0.25], [p1, top(8.75) - depth, 1.75]));
    const middle = top(8) - depth / 2;
    const half = 8 * Math.SQRT2;
    const bar = steel([8 - half, middle - BAR / 2, 0], [8 + half, middle + BAR / 2, 2]);
    bar.rotation = { origin: [8, middle, 1], axis: "z", angle: mirror ? -45 : 45 };
    elements.push(bar);
    return elements;
  };
  model("cabin_rail_raked_right", raked(false));
  model("cabin_rail_raked_left", raked(true));
  // Which way the stair under it climbs (its rake) leaves each edge level, or raked to its right or left.
  const turns = ["north", "east", "south", "west"];
  const right = (edge) => turns[(turns.indexOf(edge) + 1) % 4];
  const left = (edge) => turns[(turns.indexOf(edge) + 3) % 4];
  const level = (edge) => ["level", edge, turns[(turns.indexOf(edge) + 2) % 4]].join("|");
  write(`assets/${ID}/blockstates/cabin_rail.json`, {
    multipart: [
      ...turns.flatMap((edge) => [
        { when: { [edge]: "true", rake: level(edge) }, apply: { model: `${ID}:block/cabin_rail_level`, y: yRot[edge] } },
        { when: { [edge]: "true", rake: right(edge) }, apply: { model: `${ID}:block/cabin_rail_raked_right`, y: yRot[edge] } },
        { when: { [edge]: "true", rake: left(edge) }, apply: { model: `${ID}:block/cabin_rail_raked_left`, y: yRot[edge] } },
      ]),
      // A corner, between an edge and the one to its left, wherever either has a level rail.
      ...turns.map((edge) => ({
        when: { OR: [{ [edge]: "true", rake: level(edge) }, { [left(edge)]: "true", rake: level(left(edge)) }] },
        apply: { model: `${ID}:block/cabin_rail_corner`, y: yRot[edge] },
      })),
    ].map((part) => (part.apply.y ? part : { ...part, apply: { model: part.apply.model } })),
  });
  // In the hand, one level stretch across the middle of the block, a block high.
  write(`assets/${ID}/models/item/cabin_rail.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:translucent", textures,
    elements: [
      { ...pane(0, 16, 0, 14.5), from: [0, 0, 7.75], to: [16, 14.5, 8.25] },
      steel([0, 14.5, 7], [16, 16, 9]),
      steel([7.25, 0, 7.25], [8.75, 14.5, 8.75]),
    ],
  });
  // One rail back for each edge it stands along.
  write(`data/${ID}/loot_table/blocks/cabin_rail.json`, {
    type: "minecraft:block",
    pools: turns.map((edge) => ({
      rolls: 1.0, bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:cabin_rail` }],
      conditions: [
        { condition: "minecraft:block_state_property", block: `${ID}:cabin_rail`, properties: { [edge]: "true" } },
        { condition: "minecraft:survives_explosion" },
      ],
    })),
    random_sequence: `${ID}:blocks/cabin_rail`,
  });
  cubes.cabin_rail = null;
}
// What of the staircases is fitted rather than built in: breakable, and no part of the airframe.
function cabinStairsFittings() {
  return ["cabin_stairs", "cabin_landing", "cabin_rail"];
}
// Carpet on white concrete; the rail, glass panes under a row of iron nuggets for its handrail.
function cabinStairsRecipes() {
  shaped("cabin_stairs", ["C  ", "WC ", "WWC"], { C: "minecraft:blue_carpet", W: "minecraft:white_concrete" }, 4);
  shaped("cabin_landing", ["C", "W"], { C: "minecraft:blue_carpet", W: "minecraft:white_concrete" }, 1);
  shaped("cabin_rail", ["NNN", "PPP"], { N: "minecraft:iron_nugget", P: "minecraft:glass_pane" }, 3);
}
function cabinStairsLang() {
  return {
    "block.aerolarge.cabin_stairs": "Cabin Stairs",
    "block.aerolarge.cabin_landing": "Cabin Landing",
    "block.aerolarge.cabin_rail": "Cabin Rail",
  };
}

// The passengers' seats (see CabinSeatBlock): their models here, their recipes and names in the functions after them,
// which the shared lists further down take in. Each is drawn facing north, the sitter's back to the south, in two
// halves, the upholstery tinted to the seat's colour (tintindex 0) and the rest in its own colours; the back's shell
// is flush with the south side of the block, where a passenger screen goes on the upper half. The economy seat: the
// cushion on a pair of aluminium legs on floor rails with a cross tube and the life vest's pouch under it, slim
// graphite armrests along the block's sides on a post at their front, so a row of seats shares them, and the back's
// shell with the tray table folded up over the seat pocket on it; over that the top of the back and the headrest,
// under its white cover. The business seat: the cushion on a pedestal, its leg rest folded down at its front, in a
// white shell that wraps its sides up to wide walnut armrest consoles and rises behind the back up over a padded,
// winged headrest under its cover. In the hand both halves together, smaller. Only the lower half drops the seat, in
// its colour.
{
  const block = (name) => `${ID}:block/${name}`;
  // A box, every face showing the texture over the part of it the box covers, tinted if upholstered; a face given its
  // own texture in faces, or left out where faces gives it null.
  const part = (from, to, texture, tinted = false, faces = {}) => ({
    from, to,
    faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].filter((side) => faces[side] !== null).map((side) => [side, {
      texture: faces[side] ?? texture, ...(tinted && !faces[side] ? { tintindex: 0 } : {}),
    }])),
  });
  const seats = {
    economy_seat: {
      textures: {
        fabric: block("cabin_seat_fabric"), cover: block("cabin_seat_cover"), shell: block("economy_seat_shell"), tray: block("economy_seat_tray"),
        pocket: block("economy_seat_pocket"), frame: block("cabin_seat_frame"), particle: block("economy_seat_shell"),
      },
      lower: [
        part([2, 0, 1.5], [3.5, 1, 14.5], "#frame"),
        part([12.5, 0, 1.5], [14, 1, 14.5], "#frame"),
        part([2, 1, 3], [3.5, 4.5, 4.5], "#frame"),
        part([12.5, 1, 3], [14, 4.5, 4.5], "#frame"),
        part([2, 1, 11], [3.5, 4.5, 12.5], "#frame"),
        part([12.5, 1, 11], [14, 4.5, 12.5], "#frame"),
        part([3.5, 3, 6.5], [12.5, 4, 7.5], "#frame"),
        part([5, 2, 8], [11, 4, 11], "#pocket"),
        part([1.5, 4.5, 1.5], [14.5, 5.5, 13.5], "#shell"),
        part([1.5, 5.5, 1], [14.5, 8, 11.5], "#fabric", true),
        part([1.5, 5.5, 11], [14.5, 16, 13.5], "#fabric", true),
        part([1, 4.5, 13.5], [15, 16, 15.5], "#shell"),
        part([2.5, 9.5, 15.5], [13.5, 15.5, 16], "#shell", false, { south: "#tray" }),
        part([2.5, 5, 15.5], [13.5, 9, 16], "#shell", false, { south: "#pocket" }),
        part([0, 10, 2], [1.75, 11.5, 13.5], "#shell"),
        part([14.25, 10, 2], [16, 11.5, 13.5], "#shell"),
        part([0.25, 4.5, 2.5], [1.5, 10, 3.75], "#frame"),
        part([14.5, 4.5, 2.5], [15.75, 10, 3.75], "#frame"),
      ],
      upper: [
        part([1.5, 0, 11], [14.5, 9, 13.5], "#fabric", true, { down: null }),
        part([1, 0, 13.5], [15, 10, 16], "#shell", false, { down: null }),
        part([2.5, 9, 11.5], [13.5, 14.5, 14], "#fabric", true),
        part([2, 10, 14], [14, 15, 16], "#shell"),
        part([3.5, 10, 11.25], [12.5, 14, 11.5], "#cover"),
      ],
    },
    business_seat: {
      textures: {
        leather: block("cabin_seat_leather"), cover: block("cabin_seat_cover"), shell: block("business_seat_shell"),
        veneer: block("business_seat_veneer"), frame: block("cabin_seat_frame"), particle: block("business_seat_shell"),
      },
      lower: [
        part([4, 0, 3], [12, 1, 13], "#frame"),
        part([6, 1, 6], [10, 3, 10], "#frame"),
        part([2, 3, 2], [14, 4, 12], "#shell"),
        part([2, 4, 1.5], [14, 8, 11], "#leather", true),
        part([3, 1, 1], [13, 4, 2], "#leather", true),
        part([2, 4, 10.5], [14, 16, 13.5], "#leather", true),
        part([1, 1, 13.5], [15, 16, 16], "#shell"),
        part([0, 1, 1.5], [2, 10.5, 16], "#shell"),
        part([14, 1, 1.5], [16, 10.5, 16], "#shell"),
        part([0, 10.5, 1.5], [2.5, 11.5, 13.5], "#veneer"),
        part([13.5, 10.5, 1.5], [16, 11.5, 13.5], "#veneer"),
      ],
      upper: [
        part([2, 0, 10.5], [14, 9, 13.5], "#leather", true, { down: null }),
        part([1, 0, 13.5], [15, 15.5, 16], "#shell", false, { down: null }),
        part([1, 0, 12], [2, 13, 13.5], "#shell", false, { down: null }),
        part([14, 0, 12], [15, 13, 13.5], "#shell", false, { down: null }),
        part([3, 9, 11], [13, 14.5, 13.5], "#leather", true),
        part([2, 9.5, 10.5], [3.5, 14, 12], "#leather", true),
        part([12.5, 9.5, 10.5], [14, 14, 12], "#leather", true),
        part([4, 10, 10.75], [12, 14, 11], "#cover"),
      ],
    },
  };
  for (const [name, { textures, lower, upper }] of Object.entries(seats)) {
    for (const [half, elements] of [["lower", lower], ["upper", upper]]) {
      write(`assets/${ID}/models/block/${name}_${half}.json`, { parent: "minecraft:block/block", textures, elements });
    }
    write(`assets/${ID}/blockstates/${name}.json`, {
      variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["lower", "upper"].map((half) =>
        [`facing=${facing},half=${half}`, { model: `${ID}:block/${name}_${half}`, ...(y ? { y } : {}) }]))),
    });
    const raised = upper.map((element) => ({ ...element, from: [element.from[0], element.from[1] + 16, element.from[2]], to: [element.to[0], element.to[1] + 16, element.to[2]] }));
    write(`assets/${ID}/models/item/${name}.json`, {
      parent: "minecraft:block/block",
      textures,
      elements: [...lower, ...raised],
      display: {
        gui: { rotation: [30, 225, 0], translation: [0, -3, 0], scale: [0.4, 0.4, 0.4] },
        ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.15, 0.15, 0.15] },
        fixed: { rotation: [0, 0, 0], translation: [0, -3, 0], scale: [0.3, 0.3, 0.3] },
        thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: [0.22, 0.22, 0.22] },
        firstperson_righthand: { rotation: [0, 45, 0], translation: [0, -1, 0], scale: [0.25, 0.25, 0.25] },
        firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, -1, 0], scale: [0.25, 0.25, 0.25] },
      },
    });
    const seat = `${ID}:${name}`;
    const own = name === "economy_seat" ? "blue" : "gray";
    write(`data/${ID}/loot_table/blocks/${name}.json`, {
      type: "minecraft:block",
      pools: [{
        rolls: 1.0,
        bonus_rolls: 0.0,
        entries: [{
          type: "minecraft:item",
          name: seat,
          functions: [{
            function: "minecraft:copy_state", block: seat, properties: ["color"],
            conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: seat, properties: { color: own } } }],
          }],
        }],
        conditions: [
          { condition: "minecraft:block_state_property", block: seat, properties: { half: "lower" } },
          { condition: "minecraft:survives_explosion" },
        ],
      }],
      random_sequence: `${ID}:blocks/${name}`,
    });
    cubes[name] = null;
  }
}
// The seats are fitted into the cabin rather than built into the airliner: breakable, and no part of the airframe.
function cabinSeatFittings() {
  return ["economy_seat", "business_seat"];
}
// Wool padding on an iron frame, as the pilot seat is: economy's in blue, business's in grey in a white concrete shell.
function cabinSeatRecipes() {
  shaped("economy_seat", ["W  ", "WWW", "I I"], { W: "minecraft:blue_wool", I: "minecraft:iron_ingot" }, 1);
  shaped("business_seat", ["W  ", "WWW", "CIC"], { W: "minecraft:gray_wool", C: "minecraft:white_concrete", I: "minecraft:iron_ingot" }, 1);
}
function cabinSeatLang() {
  return {
    "block.aerolarge.economy_seat": "Economy Seat",
    "block.aerolarge.business_seat": "Business Seat",
  };
}

// The cabin carpet (see CabinCarpetBlock): its pile a pixel thick, as vanilla's carpet's is, every face of it tinted
// to the carpet's colour. The aisle carpet (see AisleCarpetBlock), drawn with its lights along the north edge: the
// same pile, and on it, a quarter of a pixel proud, the floor path lights in their channel from one side of the block
// to the other, so a run of them is one strip; the lights glow at full brightness whatever the light around, and are
// not tinted. In the hand, as vanilla's carpet is held. Taken up, each keeps its colour.
{
  const block = (name) => `${ID}:block/${name}`;
  const pile = {
    from: [0, 0, 0], to: [16, 1, 16],
    faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((side) => [side, {
      texture: "#pile", tintindex: 0,
      ...(side === "up" || side === "down" ? {} : { uv: [0, 15, 16, 16] }),
      ...(side === "up" ? {} : { cullface: side }),
    }])),
  };
  write(`assets/${ID}/models/block/cabin_carpet.json`, {
    parent: "minecraft:block/thin_block", textures: { pile: block("cabin_carpet"), particle: block("cabin_carpet") }, elements: [pile],
  });
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  write(`assets/${ID}/models/block/aisle_carpet.json`, {
    parent: "minecraft:block/thin_block",
    textures: { pile: block("aisle_carpet"), light: block("aisle_light"), channel: block("aisle_light_channel"), particle: block("aisle_carpet") },
    elements: [
      pile,
      {
        from: [0, 1, 0.5], to: [16, 1.25, 1.5], shade: false,
        faces: {
          up: { texture: "#light", uv: [0, 7, 16, 8], ...lit },
          north: { texture: "#channel", uv: [0, 0, 16, 1] },
          south: { texture: "#channel", uv: [0, 0, 16, 1] },
        },
      },
    ],
  });
  write(`assets/${ID}/blockstates/cabin_carpet.json`, { variants: { "": { model: `${ID}:block/cabin_carpet` } } });
  facing("aisle_carpet");
  for (const name of ["cabin_carpet", "aisle_carpet"]) {
    write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}` });
    const carpet = `${ID}:${name}`;
    write(`data/${ID}/loot_table/blocks/${name}.json`, {
      type: "minecraft:block",
      pools: [{
        rolls: 1.0,
        bonus_rolls: 0.0,
        entries: [{
          type: "minecraft:item",
          name: carpet,
          functions: [{
            function: "minecraft:copy_state", block: carpet, properties: ["color"],
            conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: carpet, properties: { color: "blue" } } }],
          }],
        }],
        conditions: [{ condition: "minecraft:survives_explosion" }],
      }],
      random_sequence: `${ID}:blocks/${name}`,
    });
    cubes[name] = null;
  }
}
// The carpets are laid in the cabin rather than built into the airliner: breakable, and no part of the airframe.
function cabinCarpetFittings() {
  return ["cabin_carpet", "aisle_carpet"];
}
// Blue wool woven on string; the aisle's, carpet with glowstone for its lights.
function cabinCarpetRecipes() {
  shaped("cabin_carpet", ["WW", "SS"], { W: "minecraft:blue_wool", S: "minecraft:string" }, 4);
  shaped("aisle_carpet", ["CCC", " G "], { C: `${ID}:cabin_carpet`, G: "minecraft:glowstone_dust" }, 3);
}
function cabinCarpetLang() {
  return {
    "block.aerolarge.cabin_carpet": "Cabin Carpet",
    "block.aerolarge.aisle_carpet": "Aisle Carpet",
  };
}

// The gym hall's sprung floor (see GymFloorBlock): maple boards on top, their strips running north and south, turned
// a quarter for strips along the x axis; a court line painted white over them; the boards' edges round the sides and
// white panelling underneath, the ceiling of the hold below. Taken up, a board comes back plain.
{
  const block = (name) => `${ID}:block/${name}`;
  for (const [name, top] of [["gym_floor", "gym_floor"], ["gym_floor_line", "gym_floor_line"]]) {
    write(`assets/${ID}/models/block/${name}.json`, {
      parent: "minecraft:block/cube_bottom_top",
      textures: { top: block(top), bottom: block("cabin_stairs_panel"), side: block("gym_floor") },
    });
  }
  write(`assets/${ID}/blockstates/gym_floor.json`, {
    variants: Object.fromEntries([["z", 0], ["x", 90]].flatMap(([axis, y]) => [false, true].map((line) =>
      [`axis=${axis},line=${line}`, { model: `${ID}:block/gym_floor${line ? "_line" : ""}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/models/item/gym_floor.json`, { parent: `${ID}:block/gym_floor` });
  dropsItself("gym_floor");
  cubes.gym_floor = null;
}
// The gym floor is laid in the cabin rather than built into the airliner: breakable, and no part of the airframe.
function gymFloorFittings() {
  return ["gym_floor"];
}
// Birch boards laid on slime, for the spring.
function gymFloorRecipes() {
  shaped("gym_floor", ["PPP", " S "], { P: "minecraft:birch_planks", S: "minecraft:slime_ball" }, 3);
}
function gymFloorLang() {
  return {
    "block.aerolarge.gym_floor": "Gym Floor",
  };
}

// The lavatory basin (see LavatoryBasinBlock), drawn facing north, its back against the south side of its block, in
// two halves. The lower one: the vanity in the toilet's wall panel (its stainless kick strip along the foot) with a
// chrome waste flap in its front, and the counter on it in four pieces round the basin set into it, its floor the
// basin's bowl with the drain; the chrome tap rising at the back and arching out over the basin, its lever beside it,
// and the soap dispenser in the back corner. The upper one: the mirror against the wall in a chrome frame, and along
// its top a strip of warm light, glowing whatever the light around. In the hand both halves together, smaller. Only
// the lower half drops the basin.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = {
    cabinet: block("vacuum_toilet_panel"), counter: block("lavatory_basin_counter"), bowl: block("lavatory_basin_bowl"),
    chrome: block("lavatory_basin_chrome"), mirror: block("lavatory_basin_mirror"), light: block("lavatory_basin_light"),
    particle: block("lavatory_basin_counter"),
  };
  const lower = [
    solid([1, 0, 4], [15, 10, 16], "#cabinet"),
    solid([5, 5, 3.75], [11, 7.5, 4], "#chrome"),
    solid([0, 10, 2], [16, 13, 4], "#counter"),
    solid([0, 10, 12], [16, 13, 16], "#counter"),
    solid([0, 10, 4], [4, 13, 12], "#counter"),
    solid([12, 10, 4], [16, 13, 12], "#counter"),
    { ...solid([4, 10, 4], [12, 10.5, 12], "#counter"), faces: { up: { texture: "#bowl" } } },
    solid([7.5, 13, 12.5], [8.5, 16, 13.5], "#chrome"),
    solid([7.5, 15, 9.5], [8.5, 16, 12.5], "#chrome"),
    solid([7.5, 14.25, 9.5], [8.5, 15, 10.5], "#chrome"),
    solid([8.5, 14.5, 12.75], [10.5, 15, 13.25], "#chrome"),
    solid([2, 13, 13], [4, 15.5, 15], "#counter"),
    solid([2.5, 14.5, 12], [3.5, 15, 13], "#chrome"),
  ];
  const upper = [
    solid([0.5, 0.5, 15.5], [15.5, 15.5, 16], "#chrome"),
    solid([1.5, 1.5, 15.25], [14.5, 13, 15.5], "#mirror"),
    {
      from: [1.5, 13.5, 15.25], to: [14.5, 14.5, 15.5], shade: false,
      faces: { north: { texture: "#light", neoforge_data: { block_light: 15, sky_light: 15 } } },
    },
  ];
  for (const [half, elements] of [["lower", lower], ["upper", upper]]) {
    write(`assets/${ID}/models/block/lavatory_basin_${half}.json`, { parent: "minecraft:block/block", textures, elements });
  }
  write(`assets/${ID}/blockstates/lavatory_basin.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["lower", "upper"].map((half) =>
      [`facing=${facing},half=${half}`, { model: `${ID}:block/lavatory_basin_${half}`, ...(y ? { y } : {}) }]))),
  });
  const raised = upper.map((element) => ({ ...element, from: [element.from[0], element.from[1] + 16, element.from[2]], to: [element.to[0], element.to[1] + 16, element.to[2]] }));
  write(`assets/${ID}/models/item/lavatory_basin.json`, {
    parent: "minecraft:block/block",
    textures,
    elements: [...lower, ...raised],
    display: {
      gui: { rotation: [30, 225, 0], translation: [0, -3, 0], scale: [0.4, 0.4, 0.4] },
      ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.15, 0.15, 0.15] },
      fixed: { rotation: [0, 0, 0], translation: [0, -3, 0], scale: [0.3, 0.3, 0.3] },
      thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: [0.22, 0.22, 0.22] },
      firstperson_righthand: { rotation: [0, 45, 0], translation: [0, -1, 0], scale: [0.25, 0.25, 0.25] },
      firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, -1, 0], scale: [0.25, 0.25, 0.25] },
    },
  });
  write(`data/${ID}/loot_table/blocks/lavatory_basin.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:lavatory_basin` }],
      conditions: [
        { condition: "minecraft:block_state_property", block: `${ID}:lavatory_basin`, properties: { half: "lower" } },
        { condition: "minecraft:survives_explosion" },
      ],
    }],
    random_sequence: `${ID}:blocks/lavatory_basin`,
  });
  cubes.lavatory_basin = null;
}
// The basin is fitted into the cabin rather than built into the airliner: breakable, and no part of the airframe.
function lavatoryBasinFittings() {
  return ["lavatory_basin"];
}
// A cauldron for the basin, set into smooth quartz, under a pane of glass for the mirror.
function lavatoryBasinRecipes() {
  shaped("lavatory_basin", ["G", "C", "Q"], { G: "minecraft:glass_pane", C: "minecraft:cauldron", Q: "minecraft:smooth_quartz" }, 1);
}
function lavatoryBasinLang() {
  return {
    "block.aerolarge.lavatory_basin": "Lavatory Basin",
  };
}

// The autopilot screen, drawn over live by its renderer from the block holding it: drawn facing north, a slim screen
// with its back against the south side of its block, as deep as the tail camera display's upright one (see
// AutopilotScreenBlock.DEPTH) and in the same dark casing, so the camera display stood on it makes one sheet of
// glass with it; facing the pilots, one state per part. It drops nothing: only the blueprints place it.
write(`assets/${ID}/models/block/autopilot_screen.json`, boxes(
  { screen: `${ID}:block/autopilot_screen`, casing: `${ID}:block/passenger_screen_casing`, particle: `${ID}:block/passenger_screen_casing` },
  [[[0, 0, 13], [16, 16, 16], { north: "#screen", all: "#casing" }]],
));
write(`assets/${ID}/blockstates/autopilot_screen.json`, {
  variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
    [0, 1, 2, 3, 4, 5].map((part) => [`facing=${facing},part=${part}`, { model: `${ID}:block/autopilot_screen`, y }]))),
});
cubes.autopilot_screen = null;
// The flight deck's curved row of screens either side of the autopilot screen, each panel drawn whole by its
// renderer from the block holding it (see CockpitDisplayRenderer), so the model gives only the particles of breaking
// one. Built only into the airliner: no item and no loot.
write(`assets/${ID}/blockstates/cockpit_display.json`, { variants: { "": { model: `${ID}:block/cockpit_display` } } });
write(`assets/${ID}/models/block/cockpit_display.json`, { textures: { particle: `${ID}:block/passenger_screen_casing` } });
cubes.cockpit_display = null;

// The flight display: a plain dark panel, its screen drawn live by its renderer; facing the pilot.
write(`assets/${ID}/models/block/flight_display.json`, {
  parent: "minecraft:block/orientable",
  textures: { front: `${ID}:block/panel_side`, side: `${ID}:block/panel_side`, top: `${ID}:block/panel_side` },
});
write(`assets/${ID}/models/item/flight_display.json`, { parent: `${ID}:block/instrument_panel` });
write(`assets/${ID}/blockstates/flight_display.json`, {
  variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/flight_display`, y }])),
});
cubes.flight_display = null;

// The tail camera display: a wide screen of three parts side by side, each drawn facing north; its renderer draws the
// picture over all of it from part 0. Upright, a slim screen with its back against the south side of its block (as
// deep as TailCameraDisplayBlock.DEPTH), dark glass on a dark casing. Tilted, as it hangs from the crown: the screen a
// plate across the block's diagonal, its top edge along the top of the side it faces (north, as drawn) and its bottom
// edge along the bottom of its back (a thin slab through the middle turned 45 degrees and stretched to reach both
// edges), its glass looking down and out the way it faces, over steps of the dark casing that fill the half behind it
// out to the top and back. Only part 0 drops the display, so the whole screen gives back one.
// The livery selector is the same slim board in the livery board's dark wood, its pictures drawn by its renderer.
function slimScreenModels(name, screen, casing) {
  const depth = 3;
  write(`assets/${ID}/models/block/${name}.json`, boxes(
    { screen, casing, particle: casing },
    [[[0, 0, 16 - depth], [16, 16, 16], { north: "#screen", all: "#casing" }]],
  ));
  const all = (texture) => ({ north: { texture }, south: { texture }, east: { texture }, west: { texture }, up: { texture }, down: { texture } });
  const steps = [1, 2, 3, 4, 5, 6, 7].map((k) => ({ from: [0, 2 * k, 16 - 2 * k], to: [16, 2 * k + 2, 16], faces: all("#casing") }));
  write(`assets/${ID}/models/block/${name}_tilted.json`, {
    parent: "minecraft:block/block",
    textures: { screen, casing, particle: casing },
    elements: [
      {
        from: [0, 7.75, 0], to: [16, 8.25, 16], rotation: { origin: [8, 8, 8], axis: "x", angle: 45, rescale: true },
        faces: { ...all("#casing"), down: { texture: "#screen" } },
      },
      ...steps,
    ],
  });
}
slimScreenModels("tail_camera_display", `${ID}:block/tail_camera_display`, `${ID}:block/passenger_screen_casing`);
// The large display a zeppelin's control car has is the same slim screen, block for block, four wide and two tall.
wideScreen("tail_camera_display", "tail_camera_display", ["large=false", "large=true"]);
slimScreenModels("livery_selector", `${ID}:block/livery_board_side`, `${ID}:block/livery_board_side`);
wideScreen("livery_selector", "livery_selector");
// A wide screen (see WideScreenBlock) drawn with the models given, upright or tilted, and its item a picture of its
// own; only part 0 drops it, so the whole screen gives back one. Its parts run as far as the largest wide screen's
// (WideScreenBlock.MOST_PARTS); a block with a size of its own lists the values of the property that picks it.
function wideScreen(name, model, sizes = [""]) {
  const parts = [0, 1, 2, 3, 4, 5, 6, 7];
  write(`assets/${ID}/blockstates/${name}.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => sizes.flatMap((size) => parts.flatMap((part) =>
      [false, true].map((tilted) => [[`facing=${facing}`, size, `part=${part}`, `tilted=${tilted}`].filter(Boolean).join(","),
        { model: `${ID}:block/${model}${tilted ? "_tilted" : ""}`, y }]))))),
  });
  write(`assets/${ID}/models/item/${name}.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/${name}` } });
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:${name}` }],
      conditions: [
        { condition: "minecraft:block_state_property", block: `${ID}:${name}`, properties: { part: "0" } },
        { condition: "minecraft:survives_explosion" },
      ],
    }],
    random_sequence: `${ID}:blocks/${name}`,
  });
  cubes[name] = null;
}

// The announcement panel and the flight map (see TiledPanelBlock): nine parts each, a slab of the dark casing as deep
// as TiledPanelBlock.DEPTH against what they are put up on, hung from a ceiling (the announcement panel only) or on a
// wall. Their renderers draw over all of the face from part 0: the panel's tiles, the map's page inside a thin bezel,
// so the panel's face is a tile's dark glass and the map's the casing. One model for each way it can be put up, for
// every part, turned with the facing. Only part 0 drops it, so a whole one gives back one.
function tiledPanel(name, face, ceilings) {
  const depth = 4;
  const textures = { screen: face, casing: `${ID}:block/passenger_screen_casing`, particle: `${ID}:block/passenger_screen_casing` };
  if (ceilings) {
    write(`assets/${ID}/models/block/${name}.json`, boxes(textures, [[[0, 16 - depth, 0], [16, 16, 16], { down: "#screen", all: "#casing" }]]));
  }
  // On a wall, facing north as its viewer does: against the north side of its block, its face to the south.
  const wallModel = ceilings ? `${name}_wall` : name;
  write(`assets/${ID}/models/block/${wallModel}.json`, boxes(textures, [[[0, 0, 0], [16, 16, depth], { south: "#screen", all: "#casing" }]]));
  const parts = [0, 1, 2, 3, 4, 5, 6, 7, 8];
  write(`assets/${ID}/blockstates/${name}.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => parts.flatMap((part) => [
      ...(ceilings ? [[`facing=${facing},part=${part},wall=false`, { model: `${ID}:block/${name}`, y }]] : []),
      [`facing=${facing},part=${part}${ceilings ? ",wall=true" : ""}`, { model: `${ID}:block/${wallModel}`, y }],
    ]))),
  });
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:${name}` }],
      conditions: [
        { condition: "minecraft:block_state_property", block: `${ID}:${name}`, properties: { part: "0" } },
        { condition: "minecraft:survives_explosion" },
      ],
    }],
    random_sequence: `${ID}:blocks/${name}`,
  });
  cubes[name] = null;
}
tiledPanel("announcement_panel", `${ID}:block/announcement_panel`, true);
write(`assets/${ID}/models/item/announcement_panel.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/announcement_panel` } });
tiledPanel("flight_map", `${ID}:block/passenger_screen_casing`, false);
write(`assets/${ID}/models/item/flight_map.json`, { parent: `${ID}:block/flight_map` });

// The autopilot's control head: a module of the centre console with a raised head on it, its top the panel (the AP
// badge and a row of mode lamps), which in the world its renderer covers with the display of what the autopilot is
// set to (see AutopilotRenderer); as wide as AutopilotBlock.HEAD_HALF_WIDTH says and HEAD_TOP high.
consoleModule("autopilot", {
  plate: `${ID}:block/panel_side`, panel: `${ID}:block/autopilot`, side: `${ID}:block/panel_side`,
}, "#plate", true, [
  {
    from: [1, CONSOLE_HEIGHT, 1], to: [15, 7.5, 15],
    faces: {
      north: { texture: "#side" }, south: { texture: "#side" }, east: { texture: "#side" }, west: { texture: "#side" },
      up: { texture: "#panel", uv: [0, 0, 16, 16], rotation: 180 }, down: { texture: "#side" },
    },
  },
]);
write(`assets/${ID}/models/item/autopilot.json`, { parent: `${ID}:block/autopilot` });
write(`assets/${ID}/blockstates/autopilot.json`, {
  variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/autopilot`, y: (y + 180) % 360 }])),
});
cubes.autopilot = null;

// The thrust lever: a module of the throttle quadrant, side by side with the others along the centre console, the
// lever's slot down its plate and the lever a slim black handle with a pale grip, swung from fully back (idle) to fully
// forward (full thrust) over its five notches; the nose to the north.
for (let thrust = 0; thrust <= 4; thrust++) {
  const rotation = { origin: [8, CONSOLE_HEIGHT, 8], axis: "x", angle: 45 - 22.5 * thrust };
  consoleModule(`thrust_lever_${thrust}`, {
    plate: `${ID}:block/thrust_lever_base`, handle: `${ID}:block/thrust_lever_handle`, knob: `${ID}:block/thrust_lever_knob`,
  }, "#plate", false, [
    solid([7.25, CONSOLE_HEIGHT, 7.25], [8.75, 13, 8.75], "#handle", rotation),
    solid([6, 12, 6.75], [10, 14.5, 9.25], "#knob", rotation),
  ]);
}
// The whole throttle in one block, for the single-block centre console: the four levers side by side, moving as one.
for (let thrust = 0; thrust <= 4; thrust++) {
  const rotation = { origin: [8, CONSOLE_HEIGHT, 8], axis: "x", angle: 45 - 22.5 * thrust };
  consoleModule(`thrust_lever_all_engines_${thrust}`, {
    plate: `${ID}:block/thrust_lever_base`, handle: `${ID}:block/thrust_lever_handle`, knob: `${ID}:block/thrust_lever_knob`,
  }, "#plate", false, [3.5, 6.5, 9.5, 12.5].flatMap((x) => [
    solid([x - 0.5, CONSOLE_HEIGHT, 7.5], [x + 0.5, 13, 8.5], "#handle", rotation),
    solid([x - 1.25, 12, 6.75], [x + 1.25, 14.5, 9.25], "#knob", rotation),
  ]));
}
write(`assets/${ID}/models/item/thrust_lever.json`, { parent: `${ID}:block/thrust_lever_2` });
write(`assets/${ID}/blockstates/thrust_lever.json`, {
  variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [0, 1, 2, 3, 4].flatMap((thrust) => [false, true].map((all) =>
    [`all_engines=${all},facing=${facing},thrust=${thrust}`, { model: `${ID}:block/thrust_lever_${all ? "all_engines_" : ""}${thrust}`, y }])))),
});
cubes.thrust_lever = null;
cubes.instrument_panel = null;

for (const name of ["airstair_controller", "airstair_call_button", "thrust_lever", "instrument_panel", "autopilot", "flight_display"]) {
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:${name}` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/${name}`,
  });
}

// The flight deck's pilot seats: their models here, their recipe and name in the functions after them, which the
// shared lists further down take in. The seat is drawn facing north, the sitter's back to the south, in two halves,
// standing six pixels higher than a Create seat (PilotSeatBlock.RAISE) so a pilot looks out over the screens.
// The lower one: the seat pan on a tall column, on a plate across two floor rails, chrome piping along its front edge,
// the back down to the pan with the harness's shoulder straps down it, and a padded armrest on a post either side,
// standing up into the upper half; on the flight deck one armrest carries a sidestick at its front end, as an A380
// pilot's outboard hand has, a dark grip on its base with a chrome cap. The upper one: the top of the back narrowing at
// the shoulders, the straps over them, and the headrest with a trim band along its top, right behind a sitter's head.
// In the hand both halves together, smaller, without a stick. Only the lower half drops the seat, as with doors.
{
  const textures = {
    leather: `${ID}:block/pilot_seat_leather`, trim: `${ID}:block/pilot_seat_trim`, frame: `${ID}:block/pilot_seat_frame`,
    harness: `${ID}:block/pilot_seat_harness`, particle: `${ID}:block/pilot_seat_leather`,
  };
  // The texture a box's face would show by default, moved back inside the texture where the box reaches up past
  // the block, so the faces over the top of the block do not show the textures next to it on the atlas.
  const inTexture = (element) => {
    const [x0, y0, z0] = element.from;
    const [x1, y1, z1] = element.to;
    const rects = {
      north: [16 - x1, 16 - y1, 16 - x0, 16 - y0], south: [x0, 16 - y1, x1, 16 - y0], west: [z0, 16 - y1, z1, 16 - y0],
      east: [16 - z1, 16 - y1, 16 - z0, 16 - y0], up: [x0, z0, x1, z1], down: [x0, 16 - z1, x1, 16 - z0],
    };
    const fit = ([u0, v0, u1, v1]) => {
      const du = Math.max(0, -u0) - Math.max(0, u1 - 16);
      const dv = Math.max(0, -v0) - Math.max(0, v1 - 16);
      return [u0 + du, v0 + dv, u1 + du, v1 + dv];
    };
    return { ...element, faces: Object.fromEntries(Object.entries(element.faces).map(([face, value]) => [face, { ...value, uv: fit(rects[face]) }])) };
  };
  const lower = [
    solid([3, 0, 0.5], [4.5, 1, 15.5], "#frame"),
    solid([11.5, 0, 0.5], [13, 1, 15.5], "#frame"),
    solid([3, 1, 4.5], [13, 2, 11.5], "#frame"),
    solid([5.5, 2, 5], [10.5, 10.5, 11], "#frame"),
    solid([2, 10.5, 1], [14, 14, 12], "#leather"),
    solid([2, 13.5, 0.5], [14, 14.25, 1], "#trim"),
    solid([2, 10.5, 11], [14, 16, 15], "#leather"),
    solid([4.5, 14.5, 10.6], [6, 16, 11], "#harness"),
    solid([10, 14.5, 10.6], [11.5, 16, 11], "#harness"),
    solid([0.5, 16.5, 3.5], [2, 18, 12], "#leather"),
    solid([14, 16.5, 3.5], [15.5, 18, 12], "#leather"),
    solid([1, 10.5, 9.5], [2, 16.5, 11], "#frame"),
    solid([14, 10.5, 9.5], [15, 16.5, 11], "#frame"),
  ].map(inTexture);
  const upper = [
    solid([2, 0, 11], [14, 9, 15], "#leather"),
    solid([3, 9, 11.5], [13, 10.5, 15], "#leather"),
    solid([4.5, 0, 10.6], [6, 9.5, 11], "#harness"),
    solid([10, 0, 10.6], [11.5, 9.5, 11], "#harness"),
    solid([4.5, 9.5, 10.6], [6, 10.75, 13], "#harness"),
    solid([10, 9.5, 10.6], [11.5, 10.75, 13], "#harness"),
    solid([4, 10.5, 12], [12, 18.5, 15], "#leather"),
    solid([5, 12, 11.5], [11, 17.5, 12], "#leather"),
    solid([4, 18.5, 12], [12, 19, 15], "#trim"),
  ].map(inTexture);
  // The sidestick on the left armrest's front end, the sitter facing north; the right one mirrored across the seat.
  const leftStick = [
    solid([0.25, 18, 4], [2.25, 18.5, 6.5], "#frame"),
    solid([0.75, 18.5, 4.5], [1.75, 22, 5.75], "#frame"),
    solid([0.6, 22, 4.35], [1.9, 22.75, 5.9], "#trim"),
  ].map(inTexture);
  const rightStick = leftStick.map((element) => ({ ...element, from: [16 - element.to[0], element.from[1], element.from[2]],
    to: [16 - element.from[0], element.to[1], element.to[2]] })).map(inTexture);
  const models = { lower, upper, lower_stick_left: [...lower, ...leftStick], lower_stick_right: [...lower, ...rightStick] };
  for (const [name, elements] of Object.entries(models)) {
    write(`assets/${ID}/models/block/pilot_seat_${name}.json`, { parent: "minecraft:block/block", textures, elements });
  }
  const seatModel = (half, stick) => (half === "upper" || stick === "none" ? `pilot_seat_${half}` : `pilot_seat_lower_stick_${stick}`);
  write(`assets/${ID}/blockstates/pilot_seat.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["lower", "upper"].flatMap((half) => ["none", "left", "right"].flatMap((stick) =>
      [false, true].map((water) => [`facing=${facing},half=${half},stick=${stick},waterlogged=${water}`, { model: `${ID}:block/${seatModel(half, stick)}`, y }]))))),
  });
  // In the hand the whole seat stands three pixels lower, so its headrest stays inside what a model may hold.
  const shifted = (elements, up) => elements.map((element) => ({ ...element, from: [element.from[0], element.from[1] + up, element.from[2]],
    to: [element.to[0], element.to[1] + up, element.to[2]] }));
  write(`assets/${ID}/models/item/pilot_seat.json`, {
    parent: "minecraft:block/block",
    textures,
    elements: [...shifted(lower, -3), ...shifted(upper, 13)],
    display: {
      gui: { rotation: [30, 225, 0], translation: [0, -3, 0], scale: [0.4, 0.4, 0.4] },
      ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.15, 0.15, 0.15] },
      fixed: { rotation: [0, 0, 0], translation: [0, -3, 0], scale: [0.3, 0.3, 0.3] },
      thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: [0.22, 0.22, 0.22] },
      firstperson_righthand: { rotation: [0, 45, 0], translation: [0, -1, 0], scale: [0.25, 0.25, 0.25] },
      firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, -1, 0], scale: [0.25, 0.25, 0.25] },
    },
  });
  write(`data/${ID}/loot_table/blocks/pilot_seat.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:pilot_seat` }],
      conditions: [
        { condition: "minecraft:block_state_property", block: `${ID}:pilot_seat`, properties: { half: "lower" } },
        { condition: "minecraft:survives_explosion" },
      ],
    }],
    random_sequence: `${ID}:blocks/pilot_seat`,
  });
  cubes.pilot_seat = null;
}
// A pilot seat: black wool padding on an iron frame.
function flightDeckRecipes() {
  shaped("pilot_seat", ["  W", "WWW", "I I"], { W: "minecraft:black_wool", I: "minecraft:iron_ingot" }, 1);
}
function flightDeckLang() {
  return {
    "block.aerolarge.pilot_seat": "Pilot Seat",
  };
}

// The flight crew's locker: its models here, its fittings, sounds, recipe and names in the functions after them, which
// the shared lists further down take in. Drawn facing north, its back against the south side of its block: a slim
// cabinet of brushed grey panels, its white door on the front with the helmet on it, a toe kick under the door and a
// chrome handle by the door's west edge, hinged along its east edge. Open, the cabinet's inside shows, dark, with a
// shelf across it, and the door stands swung out on its hinge half way round, as far as a model turns a part.
{
  const textures = {
    side: `${ID}:block/crew_locker_side`, door: `${ID}:block/crew_locker_door`, inside: `${ID}:block/crew_locker_inside`,
    handle: `${ID}:block/crew_locker_handle`, particle: `${ID}:block/crew_locker_side`,
  };
  const hinge = { angle: -45, axis: "y", origin: [15, 8, 9] };
  const door = [
    [[1, 1, 8], [15, 16, 9], { north: "#door", all: "#side" }],
    [[2, 7, 7], [3, 11, 8], "#handle"],
  ];
  write(`assets/${ID}/models/block/crew_locker.json`, boxes(textures, [
    [[1, 0, 9], [15, 16, 16], { north: "#inside", all: "#side" }],
    ...door,
  ]));
  const open = boxes(textures, [
    [[1, 0, 15], [15, 16, 16], { north: "#inside", all: "#side" }],
    [[1, 0, 9], [2, 16, 15], { east: "#inside", all: "#side" }],
    [[14, 0, 9], [15, 16, 15], { west: "#inside", all: "#side" }],
    [[2, 15, 9], [14, 16, 15], { down: "#inside", all: "#side" }],
    [[2, 0, 9], [14, 1, 15], { up: "#inside", all: "#side" }],
    [[2, 8, 10], [14, 9, 15], "#side"],
    ...door,
  ]);
  for (const element of open.elements.slice(-door.length)) element.rotation = hinge;
  write(`assets/${ID}/models/block/crew_locker_open.json`, open);
  write(`assets/${ID}/blockstates/crew_locker.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((isOpen) =>
      [`facing=${facing},open=${isOpen}`, { model: `${ID}:block/crew_locker${isOpen ? "_open" : ""}`, y }]))),
  });
  write(`assets/${ID}/models/item/crew_locker.json`, { parent: `${ID}:block/crew_locker` });
  dropsItself("crew_locker");
  cubes.crew_locker = null;
}
// What of the crew locker is fitted rather than built in: breakable, and no part of the airframe.
function crewLockerFittings() {
  return ["crew_locker"];
}
// The door of a light metal cabinet: an iron trapdoor's, higher and softer.
function crewLockerSounds() {
  return {
    crew_locker_open: {
      subtitle: `subtitles.${ID}.crew_locker_open`,
      sounds: [1, 2, 3, 4].map((n) => ({ name: `minecraft:block/iron_trapdoor/open${n}`, volume: 0.7, pitch: 1.4 })),
    },
    crew_locker_close: {
      subtitle: `subtitles.${ID}.crew_locker_close`,
      sounds: [1, 2, 3, 4].map((n) => ({ name: `minecraft:block/iron_trapdoor/close${n}`, volume: 0.7, pitch: 1.5 })),
    },
  };
}
// Iron round a chest, an iron trapdoor for its door.
function crewLockerRecipes() {
  shaped("crew_locker", ["ITI", "ICI"], { I: "minecraft:iron_ingot", T: "minecraft:iron_trapdoor", C: "minecraft:chest" }, 1);
}
function crewLockerLang() {
  return {
    "block.aerolarge.crew_locker": "Flight Crew Locker",
    "container.aerolarge.crew_locker": "Flight Crew Locker",
    "subtitles.aerolarge.crew_locker_open": "Crew locker opens",
    "subtitles.aerolarge.crew_locker_close": "Crew locker shuts",
  };
}

for (const name of ["airliner_blueprint", "furnished_airliner_blueprint", "zeppelin_blueprint", "furnished_zeppelin_blueprint", "festive_zeppelin_blueprint",
  "half_zeppelin_blueprint", "half_furnished_zeppelin_blueprint", "half_festive_zeppelin_blueprint", "rocket_blueprint"]) {
  write(`assets/${ID}/models/item/${name}.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/${name}` } });
}

// The furnished cabin's finishing touches (see CabinDecor), and what they add to the lang file.
const cabinDecorLang = {};
// The springboard, drawn with its tip to the north (as SpringboardBlock's shape is): a slim board 4 px thick the
// length of the block, its top flush with the block's and gripped in blue, white below; under its back end a steel
// coil spring, three turns round a post.
{
  write(`assets/${ID}/models/block/springboard.json`, boxes(
    { grip: `${ID}:block/springboard_grip`, board: `${ID}:block/springboard_board`, spring: `${ID}:block/springboard_spring`,
      particle: `${ID}:block/springboard_board` },
    [
      [[2, 12, 0], [14, 16, 16], { up: "#grip", all: "#board" }],
      [[5, 10.5, 12], [11, 11.5, 16], "#spring"],
      [[5, 8.5, 12], [11, 9.5, 16], "#spring"],
      [[5, 6.5, 12], [11, 7.5, 16], "#spring"],
      [[7, 6, 13], [9, 12, 15], "#spring"],
    ],
  ));
  facing("springboard");
  write(`assets/${ID}/models/item/springboard.json`, { parent: `${ID}:block/springboard` });
  dropsItself("springboard");
  cubes.springboard = null;
  Object.assign(cabinDecorLang, {
    "block.aerolarge.springboard": "Springboard",
    "subtitles.aerolarge.springboard_boing": "Springboard boings",
  });
}
// The starlight panel: a navy cube, its twinkling stars (an animated texture with see-through gaps) laid over it
// glowing at full brightness whatever the light around, so they still show when the dimmer has the panel itself
// dark. One model for every brightness: the dimmer only sets how much light the panel gives.
{
  const faces = (texture, lit) => Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) =>
    [face, { texture, cullface: face, ...(lit ? { neoforge_data: { block_light: 15, sky_light: 15 } } : {}) }]));
  write(`assets/${ID}/models/block/starlight_panel.json`, {
    parent: "minecraft:block/block",
    render_type: "minecraft:cutout",
    textures: { panel: `${ID}:block/starlight_panel`, stars: `${ID}:block/starlight_panel_stars`, particle: `${ID}:block/starlight_panel` },
    elements: [
      { from: [0, 0, 0], to: [16, 16, 16], faces: faces("#panel", false) },
      { from: [0, 0, 0], to: [16, 16, 16], shade: false, faces: faces("#stars", true) },
    ],
  });
  write(`assets/${ID}/blockstates/starlight_panel.json`, {
    variants: Object.fromEntries(["bright", "dim", "night"].map((level) => [`brightness=${level}`, { model: `${ID}:block/starlight_panel` }])),
  });
  write(`assets/${ID}/models/item/starlight_panel.json`, { parent: `${ID}:block/starlight_panel` });
  dropsItself("starlight_panel");
  cubes.starlight_panel = null;
  cabinDecorLang["block.aerolarge.starlight_panel"] = "Starlight Panel";
}
// The gymnastic rings, drawn facing north: two wooden rings side by side, each a square loop on a leather strap up to
// the top of the block, where the chain they hang from carries on.
{
  const parts = [];
  for (const cx of [4.5, 11.5]) {
    parts.push(
      [[cx - 2, 1, 7.5], [cx + 2, 1.75, 8.5], "#wood"],
      [[cx - 2, 4.25, 7.5], [cx + 2, 5, 8.5], "#wood"],
      [[cx - 2, 1.75, 7.5], [cx - 1.25, 4.25, 8.5], "#wood"],
      [[cx + 1.25, 1.75, 7.5], [cx + 2, 4.25, 8.5], "#wood"],
      [[cx - 0.5, 5, 7.75], [cx + 0.5, 16, 8.25], "#strap"],
    );
  }
  write(`assets/${ID}/models/block/gym_rings.json`, boxes(
    { wood: `${ID}:block/gym_rings_wood`, strap: `${ID}:block/gym_rings_strap`, particle: `${ID}:block/gym_rings_wood` }, parts,
  ));
  facing("gym_rings");
  write(`assets/${ID}/models/item/gym_rings.json`, { parent: `${ID}:block/gym_rings` });
  dropsItself("gym_rings");
  cubes.gym_rings = null;
  cabinDecorLang["block.aerolarge.gym_rings"] = "Gymnastic Rings";
}
// The hide and seek button, drawn facing north: a low stand with a hand on its front, and on top a big round button
// with an open eye; pressed, the button sits lower, its eye shut and glowing whatever the light around.
{
  const textures = {
    stand: `${ID}:block/hide_and_seek_button_side`, front: `${ID}:block/hide_and_seek_button_front`,
    cap: `${ID}:block/hide_and_seek_button_cap`, closed: `${ID}:block/hide_and_seek_button_cap_closed`,
    particle: `${ID}:block/hide_and_seek_button_side`,
  };
  for (const pressed of [false, true]) {
    const name = `hide_and_seek_button${pressed ? "_pressed" : ""}`;
    write(`assets/${ID}/models/block/${name}.json`, boxes(textures, [
      [[2, 0, 2], [14, 8, 14], { north: "#front", all: "#stand" }],
      [[4, 8, 4], [12, pressed ? 9 : 10, 12], { up: pressed ? "#closed" : "#cap", all: "#stand" }, pressed],
    ]));
  }
  write(`assets/${ID}/blockstates/hide_and_seek_button.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((pressed) =>
      [`facing=${facing},pressed=${pressed}`, { model: `${ID}:block/hide_and_seek_button${pressed ? "_pressed" : ""}`, y }]))),
  });
  write(`assets/${ID}/models/item/hide_and_seek_button.json`, { parent: `${ID}:block/hide_and_seek_button` });
  dropsItself("hide_and_seek_button");
  cubes.hide_and_seek_button = null;
  cabinDecorLang["block.aerolarge.hide_and_seek_button"] = "Hide and Seek Button";
}

// The moon pool's tiling and ladder: their models here, their fittings, recipes and names in the functions after them,
// which the shared lists further down take in. The deck tile: the dotted tiles on top, plain tiles round its sides and white
// ceiling panels underneath, as the hold sees the cabin floor. The mosaic, plain or with its lane line. The coping
// (see PoolCopingBlock), drawn facing north: a kerb whose top steps down a pixel along its front edge, so the edge
// reads as rounded over, its front the coping's band over the mosaic; a corner piece the kerb's plain stone all over.
const POOL = ["pool_deck_tile", "pool_mosaic", "pool_coping", "pool_steps", "pool_ladder"];
{
  const block = (name) => `${ID}:block/${name}`;
  write(`assets/${ID}/models/block/pool_deck_tile.json`, {
    parent: "minecraft:block/cube_bottom_top",
    textures: { top: block("pool_deck_tile"), bottom: block("cabin_floor_under"), side: block("pool_deck_tile_side") },
  });
  write(`assets/${ID}/blockstates/pool_deck_tile.json`, { variants: { "": { model: block("pool_deck_tile") } } });
  for (const name of ["pool_mosaic", "pool_mosaic_line"]) {
    write(`assets/${ID}/models/block/${name}.json`, { parent: "minecraft:block/cube_all", textures: { all: block(name) } });
  }
  write(`assets/${ID}/blockstates/pool_mosaic.json`, {
    variants: { "line=false": { model: block("pool_mosaic") }, "line=true": { model: block("pool_mosaic_line") } },
  });
  const face = (texture, cullface, uv) => ({ texture, ...(uv ? { uv } : {}), ...(cullface ? { cullface } : {}) });
  write(`assets/${ID}/models/block/pool_coping.json`, {
    parent: "minecraft:block/block",
    textures: { top: block("pool_coping_top"), front: block("pool_coping_front"), side: block("pool_coping"), particle: block("pool_coping") },
    elements: [
      { from: [0, 0, 1], to: [16, 16, 16], faces: {
        up: face("#top", "up", [0, 1, 16, 16]), north: face("#front"), south: face("#side", "south"),
        east: face("#side", "east", [0, 0, 15, 16]), west: face("#side", "west", [1, 0, 16, 16]), down: face("#side", "down", [0, 1, 16, 16]),
      } },
      { from: [0, 0, 0], to: [16, 15, 1], faces: {
        up: face("#top", null, [0, 0, 16, 1]), north: face("#front", "north", [0, 1, 16, 16]),
        east: face("#side", "east", [15, 1, 16, 16]), west: face("#side", "west", [0, 1, 1, 16]), down: face("#side", "down", [0, 0, 16, 1]),
      } },
    ],
  });
  write(`assets/${ID}/models/block/pool_coping_corner.json`, { parent: "minecraft:block/cube_all", textures: { all: block("pool_coping") } });
  write(`assets/${ID}/blockstates/pool_coping.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [
      [`corner=false,facing=${facing}`, { model: block("pool_coping"), ...(y ? { y } : {}) }],
      [`corner=true,facing=${facing}`, { model: block("pool_coping_corner") }],
    ])),
  });
  for (const name of ["pool_deck_tile", "pool_mosaic", "pool_coping"]) {
    write(`assets/${ID}/models/item/${name}.json`, { parent: block(name) });
  }
}
// The pool steps (see PoolStepsBlock), drawn climbing east as vanilla's stairs are, so they take vanilla's turns for
// every way they face and corner: the dotted deck tile on the treads, plain tiles on the risers and sides and
// underneath, and along each tread's front edge a dark blue nosing a quarter of a pixel proud of the tread and the
// riser; a straight flight's grab rail at its left (north) or right (south) end, a chrome tube up from the lower tread
// and down from over the upper one, joined by a handrail sloping up with the flight. In the hand, a step with both.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = {
    tread: block("pool_deck_tile"), side: block("pool_deck_tile_side"), nosing: block("pool_steps_nosing"),
    chrome: block("lavatory_basin_chrome"), particle: block("pool_deck_tile_side"),
  };
  const sides = (from, to) => ({
    from, to, faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((side) => [side, { texture: side === "up" ? "#tread" : "#side" }])),
  });
  // A nosing along a tread's front edge, from one end to the other of the edge, at the tread's height.
  const nosing = (top, [x0, z0], [x1, z1]) => x0 === x1
    ? solid([x0 - 0.25, top - 1, Math.min(z0, z1)], [x0 + 1.75, top + 0.25, Math.max(z0, z1)], "#nosing")
    : solid([Math.min(x0, x1), top - 1, z0 - 0.25], [Math.max(x0, x1), top + 0.25, z0 + 1.75], "#nosing");
  const steps = {
    pool_steps: [sides([0, 0, 0], [16, 8, 16]), sides([8, 8, 0], [16, 16, 16]),
      nosing(8, [0, 0], [0, 16]), nosing(16, [8, 0], [8, 16])],
    pool_steps_inner: [sides([0, 0, 0], [16, 8, 16]), sides([8, 8, 0], [16, 16, 16]), sides([0, 8, 8], [8, 16, 16]),
      nosing(8, [0, 0], [0, 8]), nosing(8, [0, 0], [8, 0]), nosing(16, [8, 0], [8, 8]), nosing(16, [0, 8], [8, 8])],
    pool_steps_outer: [sides([0, 0, 0], [16, 8, 16]), sides([8, 8, 8], [16, 16, 16]),
      nosing(8, [0, 0], [0, 16]), nosing(8, [0, 0], [16, 0]), nosing(16, [8, 8], [8, 16]), nosing(16, [8, 8], [16, 8])],
  };
  for (const [name, elements] of Object.entries(steps)) {
    write(`assets/${ID}/models/block/${name}.json`, { parent: "minecraft:block/stairs", textures, elements });
  }
  // A grab rail at one end of the step, its tubes this far in from the north side: the lower post on the lower
  // tread's front, the upper one on the upper tread's back, and the handrail between their tops at 45 degrees.
  const rail = (z) => [
    solid([2, 8, z], [3.5, 20, z + 1.5], "#chrome"),
    solid([12.5, 16, z], [14, 31, z + 1.5], "#chrome"),
    solid([2.75, 19.25, z], [2.75 + 10.5 * Math.SQRT2, 20.75, z + 1.5], "#chrome", { origin: [2.75, 20, z + 0.75], axis: "z", angle: 45 }),
  ];
  write(`assets/${ID}/models/block/pool_steps_rail_left.json`, { parent: "minecraft:block/block", textures, elements: rail(1) });
  write(`assets/${ID}/models/block/pool_steps_rail_right.json`, { parent: "minecraft:block/block", textures, elements: rail(13.5) });
  // Vanilla's turns of its stair models, for each way a stair faces, which half it is and the shape its neighbours give it.
  const turns = {
    bottom: {
      east: { straight: 0, outer_right: 0, outer_left: 270, inner_right: 0, inner_left: 270 },
      west: { straight: 180, outer_right: 180, outer_left: 90, inner_right: 180, inner_left: 90 },
      south: { straight: 90, outer_right: 90, outer_left: 0, inner_right: 90, inner_left: 0 },
      north: { straight: 270, outer_right: 270, outer_left: 180, inner_right: 270, inner_left: 180 },
    },
    top: {
      east: { straight: 0, outer_right: 90, outer_left: 0, inner_right: 90, inner_left: 0 },
      west: { straight: 180, outer_right: 270, outer_left: 180, inner_right: 270, inner_left: 180 },
      south: { straight: 90, outer_right: 180, outer_left: 90, inner_right: 180, inner_left: 90 },
      north: { straight: 270, outer_right: 0, outer_left: 270, inner_right: 0, inner_left: 270 },
    },
  };
  const multipart = [];
  for (const [half, facings] of Object.entries(turns)) {
    for (const [facing, shapes] of Object.entries(facings)) {
      for (const [shape, y] of Object.entries(shapes)) {
        const model = shape === "straight" ? "pool_steps" : shape.startsWith("inner") ? "pool_steps_inner" : "pool_steps_outer";
        multipart.push({
          when: { facing, half, shape },
          apply: { model: block(model), ...(half === "top" ? { x: 180 } : {}), ...(y ? { y } : {}), uvlock: true },
        });
      }
    }
  }
  for (const [facing, y] of Object.entries(turns.bottom).map(([facing, shapes]) => [facing, shapes.straight])) {
    for (const side of ["left", "right"]) {
      multipart.push({
        when: { facing, half: "bottom", shape: "straight", [`rail_${side}`]: "true" },
        apply: { model: block(`pool_steps_rail_${side}`), ...(y ? { y } : {}) },
      });
    }
  }
  write(`assets/${ID}/blockstates/pool_steps.json`, { multipart });
  write(`assets/${ID}/models/item/pool_steps.json`, {
    parent: "minecraft:block/stairs", textures, elements: [...steps.pool_steps, ...rail(1), ...rail(13.5)],
  });
}
// The pool ladder (see RungLadderBlock), drawn facing north, its wall to the south: two chrome rails standing off the
// wall on a bracket each, a ribbed steel tread between them every half block. Its top piece's rails carry on up past
// its top and curl over into grab handles, down onto the floor beyond the wall's top, as a pool ladder's over the
// coping. In the hand, the top piece.
{
  const textures = { chrome: `${ID}:block/lavatory_basin_chrome`, tread: `${ID}:block/pool_ladder_tread`, particle: `${ID}:block/lavatory_basin_chrome` };
  const piece = [];
  const top = [];
  for (const [x0, x1] of [[1.5, 3], [13, 14.5]]) {
    piece.push(
      [[x0, 0, 10.5], [x1, 16, 12], "#chrome"],
      [[x0 + 0.25, 6, 12], [x1 - 0.25, 7, 16], "#chrome"],
    );
    top.push(
      [[x0, 16, 10.5], [x1, 28, 12], "#chrome"],
      [[x0, 26.5, 12], [x1, 28, 20], "#chrome"],
      [[x0, 16, 18.5], [x1, 26.5, 20], "#chrome"],
    );
  }
  for (const y of [3.5, 11.5]) {
    piece.push([[3, y, 8], [13, y + 1, 13], { up: "#tread", down: "#tread", all: "#chrome" }]);
  }
  write(`assets/${ID}/models/block/pool_ladder.json`, boxes(textures, piece));
  write(`assets/${ID}/models/block/pool_ladder_top.json`, boxes(textures, [...piece, ...top]));
  write(`assets/${ID}/blockstates/pool_ladder.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((isTop) =>
      [`facing=${facing},top=${isTop}`, { model: `${ID}:block/pool_ladder${isTop ? "_top" : ""}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/models/item/pool_ladder.json`, { parent: `${ID}:block/pool_ladder_top` });
}
// The pool's tiles and ladder are fitted into the cabin rather than built into the airliner: breakable, and no part of
// the airframe.
function poolFittings() {
  return POOL;
}
// What of the pool climbs as a ladder does.
function poolClimbable() {
  return ["pool_ladder"];
}
// Smooth sandstone round gravel for the deck tile's grit; light blue glass round white concrete for the mosaic; a
// kerb of smooth quartz over mosaic for the coping; steps of deck tiles as any stairs are made; a ladder of iron.
function poolRecipes() {
  shaped("pool_deck_tile", ["SSS", "SGS", "SSS"], { S: "minecraft:smooth_sandstone", G: "minecraft:gravel" }, 8);
  shaped("pool_mosaic", ["GGG", "GCG", "GGG"], { G: "minecraft:light_blue_stained_glass", C: "minecraft:white_concrete" }, 8);
  shaped("pool_coping", ["QQQ", "MMM"], { Q: "minecraft:smooth_quartz", M: `${ID}:pool_mosaic` }, 6);
  shaped("pool_steps", ["T  ", "TT ", "TTT"], { T: `${ID}:pool_deck_tile` }, 4);
  shaped("pool_ladder", ["I I", "INI", "I I"], { I: "minecraft:iron_ingot", N: "minecraft:iron_nugget" }, 3);
}
function poolLang() {
  return {
    "block.aerolarge.pool_deck_tile": "Pool Deck Tile",
    "block.aerolarge.pool_mosaic": "Pool Mosaic",
    "block.aerolarge.pool_coping": "Pool Coping",
    "block.aerolarge.pool_steps": "Pool Steps",
    "block.aerolarge.pool_ladder": "Pool Ladder",
  };
}
for (const name of POOL) {
  dropsItself(name);
  cubes[name] = null;
}

// The gym hall's fittings: their models here, their fittings, recipes and names in the functions after them, which
// the shared lists further down take in. The wall bars (see RungLadderBlock), drawn facing north, their wall to the
// south: an ash upright down either side against the wall, so bars side by side share a pair of uprights between
// them, and four round rungs across; the top piece's top rung hangs out from the wall on a bracket at either end.
const GYM_HALL = ["wall_bars", "gym_mat", "climbing_rope", "glass_backboard", "gym_bench"];
{
  const textures = { wood: `${ID}:block/wall_bars_wood`, particle: `${ID}:block/wall_bars_wood` };
  const uprights = [[[0, 0, 11], [1.5, 16, 16], "#wood"], [[14.5, 0, 11], [16, 16, 16], "#wood"]];
  const rung = (y) => [[1.5, y, 12.25], [14.5, y + 1.5, 13.75], "#wood"];
  write(`assets/${ID}/models/block/wall_bars.json`, boxes(textures, [...uprights, ...[1.5, 5.5, 9.5, 13.5].map(rung)]));
  write(`assets/${ID}/models/block/wall_bars_top.json`, boxes(textures, [
    ...uprights, ...[1.5, 5.5, 9.5].map(rung),
    [[0, 13.5, 7], [1.5, 15, 11], "#wood"],
    [[14.5, 13.5, 7], [16, 15, 11], "#wood"],
    [[1.5, 13.5, 7], [14.5, 15, 8.5], "#wood"],
  ]));
  write(`assets/${ID}/blockstates/wall_bars.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((isTop) =>
      [`facing=${facing},top=${isTop}`, { model: `${ID}:block/wall_bars${isTop ? "_top" : ""}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/models/item/wall_bars.json`, { parent: `${ID}:block/wall_bars_top` });
}
// The gym mat (see GymMatBlock): a pad a quarter of a block high, blue vinyl on top and seamed round its sides, and
// along each edge that does not run on into another mat a grey cord of piping round its top and its foot, drawn along
// the north edge and turned for the others. In the hand, a mat piped all round.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = { top: block("gym_mat"), side: block("gym_mat_side"), piping: block("gym_mat_piping"), particle: block("gym_mat") };
  const pad = [[0, 0, 0], [16, 4, 16], { up: "#top", all: "#side" }];
  const piping = ([x0, z0], [x1, z1]) => [[[x0, 3, z0], [x1, 4.5, z1], "#piping"], [[x0, 0, z0], [x1, 1, z1], "#piping"]];
  write(`assets/${ID}/models/block/gym_mat.json`, boxes(textures, [pad]));
  write(`assets/${ID}/models/block/gym_mat_piping.json`, boxes(textures, piping([0, 0], [16, 1])));
  write(`assets/${ID}/blockstates/gym_mat.json`, {
    multipart: [
      { apply: { model: block("gym_mat") } },
      ...Object.entries(yRot).map(([side, y]) => ({ when: { [side]: "false" }, apply: { model: block("gym_mat_piping"), ...(y ? { y } : {}) } })),
    ],
  });
  write(`assets/${ID}/models/item/gym_mat.json`, boxes(textures, [
    pad, ...piping([0, 0], [16, 1]), ...piping([0, 15], [16, 16]), ...piping([0, 0], [1, 16]), ...piping([15, 0], [16, 16]),
  ]));
}
// The climbing rope (see ClimbingRopeBlock): braided hemp down the middle of its block; where nothing of it is above, it
// hangs on a steel eye from a plate against the ceiling, and where nothing of it is below it ends in a knot over its
// whipped end. In the hand, a short rope with both.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = { rope: block("climbing_rope"), steel: block("cabin_rail_steel"), particle: block("climbing_rope") };
  const strand = (y0, y1) => [[6.75, y0, 6.75], [9.25, y1, 9.25], "#rope"];
  const eye = [[[7.25, 13.5, 7.25], [8.75, 15, 8.75], "#steel"], [[5.5, 15, 5.5], [10.5, 16, 10.5], "#steel"]];
  const knot = [[[6, 2, 6], [10, 5, 10], "#rope"], [[7, 0.5, 7], [9, 2, 9], "#rope"]];
  const pieces = {
    climbing_rope: [strand(0, 16)],
    climbing_rope_top: [strand(0, 13.5), ...eye],
    climbing_rope_bottom: [strand(5, 16), ...knot],
    climbing_rope_single: [strand(5, 13.5), ...eye, ...knot],
  };
  for (const [name, parts] of Object.entries(pieces)) {
    write(`assets/${ID}/models/block/${name}.json`, boxes(textures, parts));
  }
  write(`assets/${ID}/blockstates/climbing_rope.json`, {
    variants: {
      "down=true,up=true": { model: block("climbing_rope") },
      "down=true,up=false": { model: block("climbing_rope_top") },
      "down=false,up=true": { model: block("climbing_rope_bottom") },
      "down=false,up=false": { model: block("climbing_rope_single") },
    },
  });
  write(`assets/${ID}/models/item/climbing_rope.json`, { parent: block("climbing_rope_single") });
}
// The glass backboard (see GlassBackboardBlock), drawn facing north, its front to the north: a pane of faintly tinted
// glass along the north side of its block, seen through; along each edge that does not run on into the board a
// brushed aluminium frame standing a little proud of the glass; and on a piece with the board running on either side
// and over it the lower half of the red shooter's square, its bottom edge just over the hoop's ring, or with the
// board either side and under it the upper half. In the hand, a pane framed all round with the whole square on it.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = { glass: block("glass_backboard"), frame: block("cabin_rail_steel"), red: block("backboard_red"), particle: block("glass_backboard") };
  const glass = { from: [0, 0, 0], to: [16, 16, 1.5], faces: { north: { texture: "#glass" }, south: { texture: "#glass" } } };
  const frames = {
    top: [[0, 14.5, -0.5], [16, 16, 2], "#frame"],
    bottom: [[0, 0, -0.5], [16, 1.5, 2], "#frame"],
    left: [[14.5, 0, -0.5], [16, 16, 2], "#frame"],
    right: [[0, 0, -0.5], [1.5, 16, 2], "#frame"],
  };
  const square = {
    bottom: [[[3, 11, -0.25], [13, 12, 0], "#red"], [[3, 12, -0.25], [4, 16, 0], "#red"], [[12, 12, -0.25], [13, 16, 0], "#red"]],
    top: [[[3, 6, -0.25], [13, 7, 0], "#red"], [[3, 0, -0.25], [4, 6, 0], "#red"], [[12, 0, -0.25], [13, 6, 0], "#red"]],
  };
  write(`assets/${ID}/models/block/glass_backboard.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:translucent", textures, elements: [glass],
  });
  for (const [edge, part] of Object.entries(frames)) {
    write(`assets/${ID}/models/block/glass_backboard_frame_${edge}.json`, boxes(textures, [part]));
  }
  for (const [half, parts] of Object.entries(square)) {
    write(`assets/${ID}/models/block/glass_backboard_square_${half}.json`, boxes(textures, parts));
  }
  const multipart = [];
  for (const [facing, y] of Object.entries(yRot)) {
    const turned = (model) => ({ model: block(model), ...(y ? { y } : {}) });
    multipart.push({ when: { facing }, apply: turned("glass_backboard") });
    for (const [edge, property] of [["top", "up"], ["bottom", "down"], ["left", "left"], ["right", "right"]]) {
      multipart.push({ when: { facing, [property]: "false" }, apply: turned(`glass_backboard_frame_${edge}`) });
    }
    multipart.push({ when: { facing, left: "true", right: "true", up: "true", down: "false" }, apply: turned("glass_backboard_square_bottom") });
    multipart.push({ when: { facing, left: "true", right: "true", up: "false", down: "true" }, apply: turned("glass_backboard_square_top") });
  }
  write(`assets/${ID}/blockstates/glass_backboard.json`, { multipart });
  const item = boxes(textures, [
    ...Object.values(frames),
    [[4, 4, -0.25], [12, 5, 0], "#red"], [[4, 11, -0.25], [12, 12, 0], "#red"], [[4, 5, -0.25], [5, 11, 0], "#red"], [[11, 5, -0.25], [12, 11, 0], "#red"],
  ]);
  write(`assets/${ID}/models/item/glass_backboard.json`, { ...item, render_type: "minecraft:translucent", elements: [...item.elements, glass] });
}
// The gym bench (see GymBenchBlock), drawn facing north, whoever sits on it facing north: an ash board at a seat's
// height across the block, the narrow balance beam along its underside, and at each end that does not run on into
// another bench a steel frame of two legs on a foot rail, its left end to the west. In the hand, a bench on both.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = { wood: block("wall_bars_wood"), steel: block("cabin_rail_steel"), particle: block("wall_bars_wood") };
  const board = [[[0, 6, 4], [16, 8, 12], "#wood"], [[0, 4, 6.75], [16, 6, 9.25], "#wood"]];
  const legs = (x0, x1) => [
    [[x0, 1, 4.5], [x1, 6, 6], "#steel"],
    [[x0, 1, 10], [x1, 6, 11.5], "#steel"],
    [[x0, 0, 4], [x1, 1, 12], "#steel"],
  ];
  write(`assets/${ID}/models/block/gym_bench.json`, boxes(textures, board));
  write(`assets/${ID}/models/block/gym_bench_legs_left.json`, boxes(textures, legs(1, 2.5)));
  write(`assets/${ID}/models/block/gym_bench_legs_right.json`, boxes(textures, legs(13.5, 15)));
  const multipart = [];
  for (const [facing, y] of Object.entries(yRot)) {
    const turned = (model) => ({ model: block(model), ...(y ? { y } : {}) });
    multipart.push({ when: { facing }, apply: turned("gym_bench") });
    multipart.push({ when: { facing, left: "false" }, apply: turned("gym_bench_legs_left") });
    multipart.push({ when: { facing, right: "false" }, apply: turned("gym_bench_legs_right") });
  }
  write(`assets/${ID}/blockstates/gym_bench.json`, { multipart });
  write(`assets/${ID}/models/item/gym_bench.json`, boxes(textures, [...board, ...legs(1, 2.5), ...legs(13.5, 15)]));
}
// The gym hall's fittings are fitted into the cabin rather than built into the airliner: breakable, and no part of
// the airframe.
function gymHallFittings() {
  return GYM_HALL;
}
// What of the gym hall climbs as a ladder does.
function gymHallClimbable() {
  return ["wall_bars", "climbing_rope"];
}
// Wall bars of birch, its uprights planks and its rungs sticks; a mat of blue wool padded with feathers; a rope of
// string; a backboard of glass panes in an iron frame round a red square; a bench of birch on iron legs.
function gymHallRecipes() {
  shaped("wall_bars", ["PSP", "PSP", "PSP"], { P: "minecraft:birch_planks", S: "minecraft:stick" }, 3);
  shaped("gym_mat", ["WWW", "FFF"], { W: "minecraft:blue_wool", F: "minecraft:feather" }, 3);
  shaped("climbing_rope", ["SS", "SS", "SS"], { S: "minecraft:string" }, 3);
  shaped("glass_backboard", ["GGG", "IRI"], { G: "minecraft:glass_pane", I: "minecraft:iron_ingot", R: "minecraft:red_dye" }, 3);
  shaped("gym_bench", ["PPP", "I I"], { P: "minecraft:birch_planks", I: "minecraft:iron_ingot" }, 2);
}
function gymHallLang() {
  return {
    "block.aerolarge.wall_bars": "Wall Bars",
    "block.aerolarge.gym_mat": "Gym Mat",
    "block.aerolarge.climbing_rope": "Climbing Rope",
    "block.aerolarge.glass_backboard": "Glass Backboard",
    "block.aerolarge.gym_bench": "Gym Bench",
  };
}
for (const name of GYM_HALL) {
  dropsItself(name);
  cubes[name] = null;
}

// Vacuum toilets (see VacuumToiletBlock) and the blue ice they drop (see BlueIceChunk and LavIceBlock), and what they
// add to the lang file.
const VACUUM_TOILET_LANG = {
  "block.aerolarge.vacuum_toilet": "Vacuum Toilet",
  "block.aerolarge.lav_ice": "Blue Lav Ice",
  "entity.aerolarge.blue_ice_chunk": "Blue Ice",
  "subtitles.aerolarge.toilet_flush": "Vacuum toilet flushes",
  "subtitles.aerolarge.blue_ice_crack": "Blue ice shatters",
  "subtitles.aerolarge.blue_ice_bonk": "Bonk!",
  "message.aerolarge.blue_ice.bonk": "Bonk! Blue ice from above",
};
// The vacuum toilet, drawn facing north against a wall panel along the south side of its block: a white shroud on a
// narrower foot, the stainless bowl let into its top, a white seat on the rim, and the lid down over it or up against
// the panel; on the panel beside the lid, clear of it, the square flush button, lit while the vacuum builds up again
// after a flush.
{
  const textures = {
    panel: `${ID}:block/vacuum_toilet_panel`, shroud: `${ID}:block/vacuum_toilet_shroud`, steel: `${ID}:block/vacuum_toilet_steel`,
    bowl: `${ID}:block/vacuum_toilet_bowl`, seat: `${ID}:block/vacuum_toilet_seat`, lid: `${ID}:block/vacuum_toilet_lid`,
    button: `${ID}:block/vacuum_toilet_button`, lit: `${ID}:block/vacuum_toilet_button_lit`, particle: `${ID}:block/vacuum_toilet_steel`,
  };
  for (const open of [false, true]) {
    for (const flushing of [false, true]) {
      write(`assets/${ID}/models/block/vacuum_toilet_${open ? "open" : "closed"}${flushing ? "_flushing" : ""}.json`, boxes(textures, [
        [[0, 0, 14], [16, 16, 16], "#panel"],
        [[4, 0, 6], [12, 5, 14], { up: "#bowl", all: "#shroud" }],
        // The shroud round the bowl, its rim and the bowl's sides stainless.
        [[3, 5, 4], [13, 7, 6], { up: "#steel", south: "#steel", all: "#shroud" }],
        [[3, 5, 12], [13, 7, 14], { up: "#steel", north: "#steel", all: "#shroud" }],
        [[3, 5, 6], [5, 7, 12], { up: "#steel", east: "#steel", all: "#shroud" }],
        [[11, 5, 6], [13, 7, 12], { up: "#steel", west: "#steel", all: "#shroud" }],
        [[4, 7, 4.5], [12, 7.75, 6], "#seat"],
        [[4, 7, 11.5], [12, 7.75, 13], "#seat"],
        [[4, 7, 6], [5.5, 7.75, 11.5], "#seat"],
        [[10.5, 7, 6], [12, 7.75, 11.5], "#seat"],
        open ? [[4, 7, 13], [12, 15, 14], "#lid"] : [[4, 7.75, 4.5], [12, 8.5, 13], "#lid"],
        [[12.5, 11, 13.25], [15.5, 14, 14], flushing ? "#lit" : "#button", flushing],
      ]));
    }
  }
  write(`assets/${ID}/blockstates/vacuum_toilet.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].flatMap((flushing) => [false, true].map((open) =>
      [`facing=${facing},flushing=${flushing},open=${open}`, { model: `${ID}:block/vacuum_toilet_${open ? "open" : "closed"}${flushing ? "_flushing" : ""}`, y }])))),
  });
  write(`assets/${ID}/models/item/vacuum_toilet.json`, { parent: `${ID}:block/vacuum_toilet_closed` });
  dropsItself("vacuum_toilet");
  cubes.vacuum_toilet = null;
}
// The lump of blue lav ice a chunk lands as: a slab of see-through blue ice with a smaller one frozen on top, turned
// any way. Only a falling chunk leaves one: no item, and it drops nothing.
{
  const lump = boxes({ ice: `${ID}:block/lav_ice`, particle: `${ID}:block/lav_ice` }, [
    [[3, 0, 3], [13, 5, 13], "#ice"],
    [[4, 5, 5], [11, 8, 11], "#ice"],
  ]);
  // The smaller lump stands on the slab: it has no floor of its own.
  delete lump.elements[1].faces.down;
  lump.render_type = "minecraft:translucent";
  write(`assets/${ID}/models/block/lav_ice.json`, lump);
  write(`assets/${ID}/blockstates/lav_ice.json`, { variants: { "": Object.values(yRot).map((y) => ({ model: `${ID}:block/lav_ice`, y })) } });
}

// A box of a model for the lounge's, business class's and the galleys' furniture below: every face showing the texture
// over the part of it the box covers, a face given its own texture (or its own face, as an object) in faces, or left
// out where faces gives it null; glowing at full brightness whatever the light around where lit.
function furniture(from, to, texture, faces = {}, lit = false) {
  return {
    from, to, ...(lit ? { shade: false } : {}),
    faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].filter((side) => faces[side] !== null).map((side) => {
      const face = typeof faces[side] === "object" ? faces[side] : { texture: faces[side] ?? texture };
      return [side, { ...face, ...(lit ? { neoforge_data: { block_light: 15, sky_light: 15 } } : {}) }];
    })),
  };
}

// The seat console and the lounge table (see SeatConsoleBlock). The console, drawn facing north, the way the seats
// beside it face: an aluminium plinth, the moulded white unit on it round its cubby (three pixels deep into its front),
// the walnut cocktail tray as high as a business seat's armrests with an aluminium tray on it, and at its back corner
// the reading lamp, a chrome stalk under a shade glowing underneath. The table: an aluminium foot rounded off, a chrome
// pedestal up from it and the walnut top, round, a frame under its edge.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = {
    shell: block("business_seat_shell"), veneer: block("business_seat_veneer"), frame: block("cabin_seat_frame"),
    chrome: block("lavatory_basin_chrome"), cubby: block("seat_console_cubby"), lamp: block("seat_console_lamp"),
    particle: block("business_seat_shell"),
  };
  write(`assets/${ID}/models/block/seat_console.json`, {
    parent: "minecraft:block/block",
    textures,
    elements: [
      furniture([1.5, 0, 2], [14.5, 1, 14], "#frame"),
      furniture([0.5, 1, 1], [15.5, 3, 15], "#shell"),
      furniture([0.5, 8, 1], [15.5, 10.5, 15], "#shell"),
      furniture([0.5, 3, 1], [3, 8, 15], "#shell"),
      furniture([13, 3, 1], [15.5, 8, 15], "#shell"),
      furniture([3, 3, 4], [13, 8, 15], "#shell", { north: "#cubby" }),
      furniture([0.5, 10.5, 1], [15.5, 11.5, 15], "#veneer"),
      furniture([2, 11.5, 2.5], [8.5, 11.75, 8.5], "#frame"),
      furniture([11, 11.5, 11], [13, 12, 13], "#chrome"),
      furniture([11.75, 12, 11.75], [12.25, 14.5, 12.25], "#chrome"),
      furniture([10.5, 14.5, 10.5], [13.5, 15.5, 13.5], "#chrome", { down: null }),
      furniture([10.5, 14.5, 10.5], [13.5, 14.5, 13.5], "#lamp", { north: null, south: null, east: null, west: null, up: null }, true),
    ],
  });
  write(`assets/${ID}/models/block/lounge_table.json`, {
    parent: "minecraft:block/block",
    textures: { ...textures, particle: block("business_seat_veneer") },
    elements: [
      furniture([4, 0, 3], [12, 1, 13], "#frame"),
      furniture([3, 0, 4], [13, 1, 12], "#frame"),
      furniture([7, 1, 7], [9, 9, 9], "#chrome"),
      furniture([2.5, 8.5, 2.5], [13.5, 9, 13.5], "#frame"),
      furniture([3, 9, 1], [13, 10, 15], "#veneer"),
      furniture([1, 9, 3], [15, 10, 13], "#veneer"),
      furniture([2, 9, 2], [14, 10, 14], "#veneer"),
    ],
  });
  for (const name of ["seat_console", "lounge_table"]) {
    facing(name);
    write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}` });
    dropsItself(name);
    cubes[name] = null;
  }
}
// The console and the table are fitted into the cabin rather than built into the airliner: breakable, and no part of
// the airframe.
function seatConsoleFittings() {
  return ["seat_console", "lounge_table"];
}
// A walnut top (dark oak) on a white moulding, the console's with a light for its lamp; the table's on an iron pedestal.
function seatConsoleRecipes() {
  shaped("seat_console", ["DG", "WW"], { D: "minecraft:dark_oak_slab", G: "minecraft:glowstone_dust", W: "minecraft:white_concrete" }, 2);
  shaped("lounge_table", ["DDD", " I "], { D: "minecraft:dark_oak_slab", I: "minecraft:iron_ingot" }, 2);
}
function seatConsoleLang() {
  return {
    "block.aerolarge.seat_console": "Seat Console",
    "block.aerolarge.lounge_table": "Lounge Table",
  };
}

// The galley units (see GalleyOvenBlock and GalleyUnitBlock) and the galley itself, their trolley bay (see
// GalleyBlock), each drawn facing north, the front a pixel back from the block's edge so the handles and latches on it
// stay inside the block, and topped with the galley's brushed steel worktop. The oven: its door's chrome handle over
// the window on two standoffs; cooking, its window lit over. The stowage: a red latch proud of each container. The
// worktop: a chrome handle on each drawer, under a steel top over the front's edge. The trolley bay: steel posts
// either side and the control panel over the bay, its back wall dark, a trolley parked in it end on with its wheels
// down on a steel threshold plate and a red latch across the bay holding it in.
{
  const block = (name) => `${ID}:block/${name}`;
  const steel = { side: block("galley_side"), top: block("galley_worktop_top"), chrome: block("lavatory_basin_chrome"), particle: block("galley_side") };
  const body = (front, height = 16) => furniture([0, 0, 1], [16, height, 16], "#side", { north: front, up: "#top" });
  const ovenParts = [
    body("#front"),
    furniture([3, 9.25, 0], [13, 10, 0.5], "#chrome"),
    furniture([3.5, 9.25, 0.5], [4.5, 10, 1], "#chrome"),
    furniture([11.5, 9.25, 0.5], [12.5, 10, 1], "#chrome"),
  ];
  const oven = { ...steel, front: block("galley_oven_front"), glow: block("galley_oven_glow") };
  write(`assets/${ID}/models/block/galley_oven.json`, { parent: "minecraft:block/block", textures: oven, elements: ovenParts });
  write(`assets/${ID}/models/block/galley_oven_on.json`, {
    parent: "minecraft:block/block",
    textures: oven,
    elements: [...ovenParts, furniture([3, 2, 0.9], [13, 9, 0.9], "#glow", { south: null, east: null, west: null, up: null, down: null }, true)],
  });
  write(`assets/${ID}/blockstates/galley_oven.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((lit) =>
      [`facing=${facing},lit=${lit}`, { model: `${ID}:block/galley_oven${lit ? "_on" : ""}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/models/block/galley_stowage.json`, {
    parent: "minecraft:block/block",
    textures: { ...steel, front: block("galley_stowage_front"), latch: block("galley_latch") },
    elements: [
      body("#front"),
      ...[[3, 14], [11, 14], [3, 6], [11, 6]].map(([x, y]) => furniture([x, y, 0.25], [x + 2, y + 1, 1], "#latch")),
    ],
  });
  write(`assets/${ID}/models/block/galley_worktop.json`, {
    parent: "minecraft:block/block",
    textures: { ...steel, front: block("galley_worktop_front") },
    elements: [
      body("#front", 15),
      furniture([0, 15, 0], [16, 16, 16], "#top"),
      ...[13, 8, 3].map((y) => furniture([5, y, 0.25], [11, y + 0.75, 1], "#chrome")),
    ],
  });
  write(`assets/${ID}/models/block/galley.json`, {
    parent: "minecraft:block/block",
    textures: {
      ...steel, panel: block("galley_front"), bay: block("galley_bay"), latch: block("galley_latch"), wheel: block("light_cable"),
      end: `${ID}:item/trolley_end`, trolley: `${ID}:item/trolley_side`, tray: `${ID}:item/trolley_top`,
    },
    elements: [
      furniture([0, 0, 0], [1.5, 16, 16], "#side", { up: "#top" }),
      furniture([14.5, 0, 0], [16, 16, 16], "#side", { up: "#top" }),
      furniture([1.5, 13, 0], [14.5, 16, 16], "#side", { north: "#panel", up: "#top" }),
      furniture([1.5, 0, 14], [14.5, 13, 16], "#side", { north: "#bay" }),
      furniture([1.5, 0, 0], [14.5, 0.25, 14], "#top"),
      furniture([2.5, 1.5, 1.5], [13.5, 12.5, 13.5], "#trolley", { north: "#end", south: "#end", up: "#tray", down: "#tray" }),
      ...[[3, 2], [11, 2], [3, 11], [11, 11]].map(([x, z]) => furniture([x, 0.25, z], [x + 2, 1.5, z + 2], "#wheel")),
      furniture([12.5, 6, 0.25], [14.5, 7, 1], "#latch"),
    ],
  });
  for (const name of ["galley_oven", "galley_stowage", "galley_worktop", "galley"]) {
    if (name !== "galley_oven") {
      facing(name);
    }
    write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}` });
    dropsItself(name);
    cubes[name] = null;
  }
}
// The units are fitted into the cabin rather than built into the airliner: breakable, and no part of the airframe.
function galleyUnitFittings() {
  return ["galley_oven", "galley_stowage", "galley_worktop"];
}
// In steel round what each stands in for: a smoker, and a barrel with a red latch, or under a steel top.
function galleyUnitRecipes() {
  shaped("galley_oven", ["III", "ISI"], { I: "minecraft:iron_ingot", S: "minecraft:smoker" }, 1);
  shaped("galley_stowage", ["IRI", "IBI"], { I: "minecraft:iron_ingot", R: "minecraft:red_dye", B: "minecraft:barrel" }, 1);
  shaped("galley_worktop", ["III", "IBI"], { I: "minecraft:iron_ingot", B: "minecraft:barrel" }, 1);
}
function galleyUnitLang() {
  return {
    "block.aerolarge.galley_oven": "Galley Oven",
    "block.aerolarge.galley_stowage": "Galley Stowage",
    "block.aerolarge.galley_worktop": "Galley Worktop",
  };
}

// The planter (see PlanterBlock), drawn facing north, its back against the south side of the block: a slim white box
// with a lip round its top and the soil half a pixel under it, and planted in it, clipped greenery in a few mounds,
// the same in flower, or bamboo, its canes up to the top of the block between a little ground cover and tufts of
// leaves. In the hand, the greenery. Taken up, it keeps its plant.
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = {
    box: block("planter_box"), soil: block("planter_soil"), leaves: block("planter_leaves"), flowers: block("planter_flowers"),
    cane: block("planter_cane"), particle: block("planter_box"),
  };
  const planter = [
    furniture([1, 0, 7], [15, 6.5, 15], "#box"),
    furniture([0.5, 6.5, 6.5], [15.5, 7.5, 7.5], "#box"),
    furniture([0.5, 6.5, 14.5], [15.5, 7.5, 15.5], "#box"),
    furniture([0.5, 6.5, 7.5], [1.5, 7.5, 14.5], "#box"),
    furniture([14.5, 6.5, 7.5], [15.5, 7.5, 14.5], "#box"),
    furniture([1.5, 6.5, 7.5], [14.5, 7, 14.5], "#soil"),
  ];
  const mounds = (texture) => [
    furniture([2, 7, 8], [14, 10.5, 14], texture),
    furniture([3, 10.5, 8.5], [8, 12.5, 13.5], texture),
    furniture([8, 10.5, 9], [13, 12, 13], texture),
  ];
  const plants = {
    greenery: mounds("#leaves"),
    flowers: mounds("#flowers"),
    bamboo: [
      furniture([2, 7, 8], [14, 8.5, 14], "#leaves"),
      ...[[3, 10, 16], [5.5, 12.5, 14], [8, 9, 15.5], [10.5, 11.5, 16], [12.5, 9.5, 13.5]].map(([x, z, top]) => furniture([x, 7, z], [x + 1, top, z + 1], "#cane")),
      furniture([1.5, 13, 9], [5.5, 14.5, 12], "#leaves"),
      furniture([7, 14, 8], [10.5, 15.5, 11], "#leaves"),
      furniture([9.5, 12, 11], [13.5, 13.5, 14], "#leaves"),
    ],
  };
  for (const [plant, parts] of Object.entries(plants)) {
    write(`assets/${ID}/models/block/planter_${plant}.json`, { parent: "minecraft:block/block", textures, elements: [...planter, ...parts] });
  }
  write(`assets/${ID}/blockstates/planter.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => Object.keys(plants).map((plant) =>
      [`facing=${facing},plant=${plant}`, { model: `${ID}:block/planter_${plant}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/models/item/planter.json`, { parent: `${ID}:block/planter_greenery` });
  const planterId = `${ID}:planter`;
  write(`data/${ID}/loot_table/blocks/planter.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item",
        name: planterId,
        functions: [{
          function: "minecraft:copy_state", block: planterId, properties: ["plant"],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: planterId, properties: { plant: "greenery" } } }],
        }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/planter`,
  });
  cubes.planter = null;
}
// The planter is fitted into the cabin rather than built into the airliner: breakable, and no part of the airframe.
function planterFittings() {
  return ["planter"];
}
// A white box (quartz, as smooth) round a little moss to grow the greenery in.
function planterRecipes() {
  shaped("planter", ["M", "Q"], { M: "minecraft:moss_block", Q: "minecraft:quartz_slab" }, 2);
}
function planterLang() {
  return {
    "block.aerolarge.planter": "Planter",
  };
}

// The lounge's bar (see BarCounterBlock, BarStoolBlock and DisplayShelfBlock), drawn facing north. The counter: a dark
// plinth set back under the front, which curves out over it in steps up to the dark stone top, overhanging, its LED
// strip glowing along the foot of the curve; white at its sides and back too. The stool: a round foot, a chrome
// column with a footrest ring round it, and the round leather seat on a chrome plate. The back bar's cabinet: walnut
// round its glass doors under a stone top, an LED strip under the top's edge; its shelves: walnut sides and cornice
// round a lit smoked mirror at the back, a glass shelf across the middle, and bottles of every colour standing on the
// cabinet's top and on the shelf, tall and short in turn, their necks capped in chrome.
{
  const block = (name) => `${ID}:block/${name}`;
  const counter = {
    front: block("bar_front"), top: block("bar_top"), plinth: block("bar_plinth"), led: block("bar_led"), particle: block("bar_front"),
  };
  write(`assets/${ID}/models/block/bar_counter.json`, {
    parent: "minecraft:block/block",
    textures: counter,
    elements: [
      furniture([0, 0, 3], [16, 2, 16], "#plinth"),
      furniture([0, 2, 2], [16, 4, 16], "#front"),
      furniture([0, 4, 1], [16, 7, 16], "#front"),
      furniture([0, 7, 0.5], [16, 12, 16], "#front"),
      furniture([0, 12, 1], [16, 14, 16], "#front"),
      furniture([0, 14, 0], [16, 16, 16], "#top"),
      furniture([0, 2, 1.75], [16, 2.75, 2], "#led", { south: null, east: null, west: null, up: null }, true),
    ],
  });
  const chrome = block("lavatory_basin_chrome");
  write(`assets/${ID}/models/block/bar_stool.json`, {
    parent: "minecraft:block/block",
    textures: { chrome, foot: block("cabin_seat_frame"), leather: block("bar_stool_leather"), particle: block("bar_stool_leather") },
    elements: [
      furniture([4, 0, 5], [12, 1, 11], "#foot"),
      furniture([5, 0, 4], [11, 1, 12], "#foot"),
      furniture([7, 1, 7], [9, 8.5, 9], "#chrome"),
      furniture([4.5, 4, 4.5], [11.5, 4.75, 5.25], "#chrome"),
      furniture([4.5, 4, 10.75], [11.5, 4.75, 11.5], "#chrome"),
      furniture([4.5, 4, 5.25], [5.25, 4.75, 10.75], "#chrome"),
      furniture([10.75, 4, 5.25], [11.5, 4.75, 10.75], "#chrome"),
      furniture([5, 8.5, 5], [11, 9, 11], "#chrome"),
      furniture([3.5, 9, 4.5], [12.5, 10.5, 11.5], "#leather"),
      furniture([4.5, 9, 3.5], [11.5, 10.5, 12.5], "#leather"),
    ],
  });
  const bottles = ["green", "amber", "clear", "blue"];
  const bottleTextures = Object.fromEntries(bottles.map((colour) => [colour, block(`bar_bottle_${colour}`)]));
  // A row of bottles standing on the row given, tall and short in turn, the colours in turn from the one given.
  const bottleRow = (y, first) => [2, 4.5, 7, 9.5, 12].flatMap((x, i) => {
    const colour = `#${bottles[(first + i) % bottles.length]}`;
    const top = y + (i % 2 === 0 ? 4.5 : 3.5);
    return [
      furniture([x, y, 10], [x + 1.5, top, 11.5], colour),
      furniture([x + 0.5, top, 10.5], [x + 1, top + 1.5, 11], colour),
      furniture([x + 0.4, top + 1.5, 10.4], [x + 1.1, top + 2, 11.1], "#chrome"),
    ];
  });
  const backBar = {
    ...bottleTextures, chrome, veneer: block("business_seat_veneer"), doors: block("back_bar_doors"), top: block("bar_top"),
    mirror: block("back_bar_mirror"), glass: block("back_bar_glass"), led: block("bar_led"), particle: block("business_seat_veneer"),
  };
  write(`assets/${ID}/models/block/back_bar_cabinet.json`, {
    parent: "minecraft:block/block",
    textures: backBar,
    elements: [
      furniture([0, 0, 1], [16, 14, 16], "#veneer", { north: "#doors" }),
      furniture([0, 14, 0], [16, 16, 16], "#top"),
      furniture([0, 13.5, 0.75], [16, 14, 1], "#led", { south: null, east: null, west: null, up: null }, true),
    ],
  });
  write(`assets/${ID}/models/block/back_bar_shelves.json`, {
    parent: "minecraft:block/block",
    textures: backBar,
    elements: [
      furniture([0, 0, 15], [16, 16, 16], "#veneer", { north: null }),
      furniture([1, 0, 14.9], [15, 15, 14.9], "#mirror", { south: null, east: null, west: null, up: null, down: null }, true),
      furniture([0, 0, 7], [1, 16, 15], "#veneer"),
      furniture([15, 0, 7], [16, 16, 15], "#veneer"),
      furniture([1, 15, 7], [15, 16, 15], "#veneer"),
      furniture([1, 7.5, 8], [15, 8, 15], "#glass"),
      ...bottleRow(0, 0),
      ...bottleRow(8, 2),
    ],
  });
  write(`assets/${ID}/blockstates/back_bar.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["cabinet", "shelves"].map((tier) =>
      [`facing=${facing},tier=${tier}`, { model: `${ID}:block/back_bar_${tier}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/blockstates/bar_stool.json`, {
    variants: Object.fromEntries([false, true].map((waterlogged) => [`waterlogged=${waterlogged}`, { model: `${ID}:block/bar_stool` }])),
  });
  facing("bar_counter");
  write(`assets/${ID}/models/item/back_bar.json`, { parent: `${ID}:block/back_bar_cabinet` });
  for (const name of ["bar_counter", "bar_stool", "back_bar"]) {
    if (name !== "back_bar") {
      write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}` });
    }
    dropsItself(name);
    cubes[name] = null;
  }
}
// The bar is fitted into the cabin rather than built into the airliner: breakable, and no part of the airframe.
function barFittings() {
  return ["bar_counter", "bar_stool", "back_bar"];
}
// A white front under a dark stone top; a leather seat on an iron column; and a cabinet under shelves of bottles.
function barRecipes() {
  shaped("bar_counter", ["BBB", "QQQ"], { B: "minecraft:polished_blackstone_slab", Q: "minecraft:quartz_block" }, 3);
  shaped("bar_stool", ["L", "I", "I"], { L: "minecraft:leather", I: "minecraft:iron_ingot" }, 2);
  shaped("back_bar", ["GBG", "PPP"], { G: "minecraft:glass_pane", B: "minecraft:glass_bottle", P: "minecraft:dark_oak_planks" }, 2);
}
function barLang() {
  return {
    "block.aerolarge.bar_counter": "Bar Counter",
    "block.aerolarge.bar_stool": "Bar Stool",
    "block.aerolarge.back_bar": "Back Bar",
  };
}

// The duty-free kiosk (see DisplayShelfBlock and AwningBlock), drawn facing north. The display's cabinet: the
// counters' blue with two drawers. Its showcase, against the south side of the block: a white base, back and top, the
// back lit, glass sides and front, a glass shelf across it, perfumes standing on the base under gold caps and boxes of
// chocolates on the shelf, one stacked on another; seen through glass, so drawn translucent. The awning's canopy: the
// striped fabric over the top of the block on a steel rib along either side; with its valance, a steel tube along its
// front edge and the scalloped valance hanging from it. Its rod: a slim steel tube right through the block.
{
  const block = (name) => `${ID}:block/${name}`;
  const display = {
    drawers: block("duty_free_display_drawers"), side: block("duty_free_counter_side"), white: block("planter_box"),
    back: block("duty_free_display_back"), glass: block("duty_free_glass"), shelf: block("back_bar_glass"), gold: block("perfume_cap"),
    pink: block("perfume_pink"), violet: block("perfume_violet"), amber: block("perfume_amber"),
    brown: block("chocolate_box_brown"), red: block("chocolate_box_red"), particle: block("duty_free_counter_side"),
  };
  write(`assets/${ID}/models/block/duty_free_display_cabinet.json`, {
    parent: "minecraft:block/block",
    textures: display,
    elements: [furniture([0, 0, 0], [16, 16, 16], "#side", { north: "#drawers", up: "#white" })],
  });
  const glassOnly = { south: null, east: null, west: null, up: null, down: null };
  const showcase = {
    parent: "minecraft:block/block",
    render_type: "minecraft:translucent",
    textures: display,
    elements: [
      furniture([0, 0, 7], [16, 1, 16], "#white"),
      furniture([0, 1, 15], [16, 15, 16], "#white", { north: null }),
      furniture([0.5, 1, 14.9], [15.5, 15, 14.9], "#back", glassOnly, true),
      furniture([0, 15, 7], [16, 16, 16], "#white"),
      ...[[2, "#pink"], [5, "#violet"], [8.5, "#amber"], [12, "#pink"]].flatMap(([x, perfume]) => [
        furniture([x, 1, 10], [x + 2, 4, 12], perfume),
        furniture([x + 0.5, 4, 10.5], [x + 1.5, 5, 11.5], "#gold"),
      ]),
      furniture([0.5, 7.5, 8], [15.5, 8, 15], "#shelf"),
      furniture([2, 8, 9], [7, 10, 13], "#brown"),
      furniture([9, 8, 9.5], [14, 9.5, 13.5], "#red"),
      furniture([10, 9.5, 10], [13, 10.5, 13], "#brown"),
      furniture([0, 1, 7], [0.5, 15, 15], "#glass"),
      furniture([15.5, 1, 7], [16, 15, 15], "#glass"),
      furniture([0.5, 1, 7], [15.5, 15, 7.5], "#glass", { east: null, west: null, up: null, down: null }),
    ],
  };
  write(`assets/${ID}/models/block/duty_free_display_shelves.json`, showcase);
  write(`assets/${ID}/blockstates/duty_free_display.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["cabinet", "shelves"].map((tier) =>
      [`facing=${facing},tier=${tier}`, { model: `${ID}:block/duty_free_display_${tier}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/models/item/duty_free_display.json`, { parent: `${ID}:block/duty_free_display_shelves` });
  dropsItself("duty_free_display");
  cubes.duty_free_display = null;

  const awning = { fabric: block("awning_fabric"), valance: block("awning_valance"), steel: block("cabin_seat_frame"), particle: block("awning_fabric") };
  const canopy = [
    furniture([0, 15, 0], [16, 16, 16], "#fabric"),
    furniture([0, 14.5, 0], [0.5, 15, 16], "#steel"),
    furniture([15.5, 14.5, 0], [16, 15, 16], "#steel"),
  ];
  write(`assets/${ID}/models/block/awning_canopy.json`, { parent: "minecraft:block/block", textures: awning, elements: canopy });
  write(`assets/${ID}/models/block/awning_valance.json`, {
    parent: "minecraft:block/block",
    render_type: "minecraft:cutout",
    textures: awning,
    elements: [
      ...canopy,
      furniture([0, 14.5, 0], [16, 15.25, 0.75], "#steel"),
      furniture([0, 9, 0.25], [16, 14.5, 0.75], "#valance", { up: null, down: null }),
    ],
  });
  write(`assets/${ID}/models/block/awning_rod.json`, {
    parent: "minecraft:block/block", textures: awning, elements: [furniture([7.5, 0, 7.5], [8.5, 16, 8.5], "#steel")],
  });
  write(`assets/${ID}/blockstates/awning.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["canopy", "rod"].flatMap((part) => [false, true].map((valance) => {
      const model = part === "rod" ? "awning_rod" : valance ? "awning_valance" : "awning_canopy";
      return [`facing=${facing},part=${part},valance=${valance}`, { model: `${ID}:block/${model}`, ...(y && part !== "rod" ? { y } : {}) }];
    })))),
  });
  write(`assets/${ID}/models/item/awning.json`, { parent: `${ID}:block/awning_valance` });
  dropsItself("awning");
  cubes.awning = null;
}
// The kiosk is fitted into the cabin rather than built into the airliner: breakable, and no part of the airframe.
function dutyFreeKioskFittings() {
  return ["duty_free_display", "awning"];
}
// A blue cabinet under glass, with a bottle and a box of cocoa; lime and white wool on iron.
function dutyFreeKioskRecipes() {
  shaped("duty_free_display", ["GGG", "BCB", "PPP"], { G: "minecraft:glass_pane", B: "minecraft:glass_bottle", C: "minecraft:cocoa_beans", P: "minecraft:blue_concrete" }, 2);
  shaped("awning", ["LWL", "I I"], { L: "minecraft:lime_wool", W: "minecraft:white_wool", I: "minecraft:iron_nugget" }, 4);
}
function dutyFreeKioskLang() {
  return {
    "block.aerolarge.duty_free_display": "Duty-Free Display",
    "block.aerolarge.awning": "Awning",
  };
}

const blocks = [...Object.keys(cubes), "airliner_door"].map((name) => `${ID}:${name}`);
const tag = (values) => ({ replace: false, values });
write("data/minecraft/tags/block/mineable/pickaxe.json", tag(blocks.filter((b) => !b.endsWith("tire") && !b.endsWith("landing_target") && !b.endsWith("cabin_curtain"))));
// The care package is a wooden crate, the gym floor maple, and the wall bars and the gym bench ash.
write("data/minecraft/tags/block/mineable/axe.json", tag([`${ID}:cargo_crate`, `${ID}:paper_plane_box`, `${ID}:gym_floor`, `${ID}:wall_bars`, `${ID}:gym_bench`]));
write("data/minecraft/tags/block/doors.json", tag([`${ID}:airliner_door`]));
write("data/minecraft/tags/item/doors.json", tag([`${ID}:airliner_door`]));
// Villagers find their way through it like a wooden door; this lets them open it on the way to their seats too.
write("data/minecraft/tags/block/mob_interactable_doors.json", tag([`${ID}:airliner_door`]));
// Climbed as a ladder is.
write("data/minecraft/tags/block/climbable.json", tag([...poolClimbable(), ...gymHallClimbable()].map((name) => `${ID}:${name}`)));
// Sable's physics: the whole airframe is aircraft-light, and the tires let it roll down a runway on their own.
const GROUND = ["landing_target", "tower_radio", "tower_radar", "transponder", "motion_sensor", "lift_panel", "jet_bridge", "check_in_desk", ...towerInstrumentsGround()];
const airliner = blocks.filter((b) => !GROUND.some((ground) => b.endsWith(`:${ground}`)));
// What is fitted into the cabin rather than built into the airliner: light like the rest, but not hull, and breakable.
const FITTINGS = ["light_strip", "pendant_light", "light_cable", "overhead_bin", "passenger_screen", "hoop", "scoreboard", "ball_rack", "galley",
  "model_airliner", "model_zeppelin", "model_rocket", "paper_plane_box", "horn_cord", "livery_board", "livery_selector", "flag_halyard", "flagstaff", "lookout_glass", "screen_stand", "ballast_valve", "searchlight", "searchlight_handle", "floodlight", "franking_machine", "mail_sack", "banner_cleat", "crane_winch", "carrier_lever", "skywriting_board", "boarding_stairs_button", "boost_button", "ships_plaque", "picture_board", "engine_repeater", "bubble_level", "duty_free_counter", "springboard", "starlight_panel", "gym_rings", "hide_and_seek_button", ...ROOM_DIVIDERS, "vacuum_toilet"];
FITTINGS.push(...holdAndTailFittings());
FITTINGS.push(...airstairFittings());
FITTINGS.push(...surfacePanelFittings());
FITTINGS.push(...slideFittings());
FITTINGS.push(...rubberDuckFittings());
FITTINGS.push(...coffeeMachineFittings());
FITTINGS.push(...cabinStairsFittings());
FITTINGS.push(...crewLockerFittings());
FITTINGS.push(...cabinSeatFittings());
FITTINGS.push(...cabinCarpetFittings());
FITTINGS.push(...gymFloorFittings());
FITTINGS.push(...lavatoryBasinFittings());
FITTINGS.push(...bulkheadSignFittings());
FITTINGS.push(...wcSignFittings());
FITTINGS.push(...denFurnitureFittings());
FITTINGS.push(...cargoNetFittings());
FITTINGS.push(...cargoFloorFittings());
FITTINGS.push(...uldContainerFittings());
FITTINGS.push(...poolFittings());
FITTINGS.push(...gymHallFittings());
FITTINGS.push(...seatConsoleFittings());
FITTINGS.push(...galleyUnitFittings());
FITTINGS.push(...planterFittings());
FITTINGS.push(...barFittings());
FITTINGS.push(...dutyFreeKioskFittings());
FITTINGS.push(...ledStripFittings());
FITTINGS.push(...spacePosterFittings());
FITTINGS.push(...labFittings());
const structure = airliner.filter((b) => !FITTINGS.some((fitting) => b.endsWith(`:${fitting}`)));
write("data/sable/tags/block/super_light.json", tag(airliner));
// What counts as the inside of an airliner for the spawn guard: everything but the gear, which stands on open ground,
// and the buttons, the airstairs' (the call button on the gear and the one on the skin outside by the top of the stairs
// among them) and the doors'; but the self-destruct button, on the flight deck's wall like its other controls, is in.
const outsideButton = (b) => b.endsWith("_button") && !b.endsWith(":self_destruct_button");
write(`data/${ID}/tags/block/airframe.json`, tag(structure.filter((b) => !b.includes("landing_gear") && !outsideButton(b))));
// What cannot be broken, except by a player sneaking with a wrench or in creative mode: the airliner's frame and
// controls, its own blocks and the other mods' it is built with, but not the cabin floor, where the building goes.
write(`data/${ID}/tags/block/unbreakable.json`, tag([
  ...structure.filter((b) => !b.endsWith("cabin_floor")),
  ...["createpropulsion:wing", "createpropulsion:creative_thruster", "createpropulsion:thruster", "offroad:wheel_mount",
    "simulated:physics_assembler"].map((id) => ({ id, required: false })),
]));
// Other mods' blocks held the same way only within an airframe's frame (see FrameProtection): a zeppelin's gas cells,
// in any of the dyes' colours, and its burners, which on anyone else's balloon break as ever.
const DYES = ["white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue", "brown",
  "green", "red", "black"];
write(`data/${ID}/tags/block/unbreakable_in_frame.json`, tag([
  ...DYES.map((dye) => `aeronautics:${dye}_envelope`), "aeronautics:adjustable_burner",
].map((id) => ({ id, required: false }))));
write("data/sable/tags/block/slippery.json", tag([`${ID}:landing_gear_tire`]));
// The cabin's loose things: inside an assembled airliner's hull they live among its blocks, and Sable leaves them there
// to carry them along, as it does Create's seats, instead of putting them out into the world (see SableFlight); the
// rocket's rover, stowed in its cradle (see Rover); and her lab's tardigrade (see Tardigrade).
write("data/sable/tags/entity_type/retain_in_sub_level.json", tag([`${ID}:trolley`, `${ID}:basketball`, `${ID}:paper_plane`, `${ID}:lift_car`,
  `${ID}:rover`, `${ID}:tardigrade`]));
// A lift's car lives among its ship's blocks and goes with them: taken away, it goes too.
write("data/sable/tags/entity_type/destroy_with_sub_level.json", tag([`${ID}:lift_car`, `${ID}:tardigrade`]));
// What the sky crane's hook lifts (see CraneHook): animals and vehicles, never players, villagers or monsters; and in its
// net, chests, barrels and care packages with all they hold. A camel it catches but cannot lift: too heavy.
write(`data/${ID}/tags/entity_type/crane_loads.json`, tag(["horse", "donkey", "mule", "llama", "trader_llama", "camel", "skeleton_horse",
  "zombie_horse", "cow", "mooshroom", "pig", "sheep", "goat", "boat", "chest_boat", "minecart", "chest_minecart", "furnace_minecart",
  "hopper_minecart"].map((id) => `minecraft:${id}`)));
// What the flying carrier's clamps top a plane's tanks up with: whatever the modpack counts as fuel, and water for steam.
write(`data/${ID}/tags/fluid/carrier_fuel.json`, tag(["#c:fuel", "minecraft:water"]));
write(`data/${ID}/tags/block/crane_cargo.json`, tag(["minecraft:chest", "minecraft:trapped_chest", "minecraft:barrel", `${ID}:cargo_crate`]));

// Recipes, for repairing and extending an airliner in survival; the blueprints themselves are creative only.
const item = (id) => ({ item: id });
function shaped(name, pattern, key, count) {
  write(`data/${ID}/recipe/${name}.json`, {
    type: "minecraft:crafting_shaped",
    category: "building",
    pattern,
    key: Object.fromEntries(Object.entries(key).map(([k, v]) => [k, item(v)])),
    result: { id: `${ID}:${name}`, count },
  });
}
shaped("airframe_panel", ["CCC", "CIC", "CCC"], { C: "minecraft:white_concrete", I: "minecraft:iron_ingot" }, 8);
shaped("livery_panel", ["PPP", "PDP", "PPP"], { P: `${ID}:airframe_panel`, D: "minecraft:blue_dye" }, 8);
shaped("cabin_window", [" G ", "GPG", " G "], { G: "minecraft:glass", P: `${ID}:airframe_panel` }, 4);
shaped("parachute", ["WWW", "S S", " L "], { W: "minecraft:white_wool", S: "minecraft:string", L: "minecraft:leather" }, 1);
shaped("jump_light", ["R", "L", "G"], { R: "minecraft:red_dye", L: "minecraft:redstone_lamp", G: "minecraft:green_dye" }, 1);
shaped("jump_switch", ["L", "P"], { L: "minecraft:lever", P: `${ID}:instrument_panel` }, 1);
shaped("parachute_rack", ["PPP", "P P", "PPP"], { P: `${ID}:parachute` }, 1);
shaped("paper_plane_box", ["PPP", "PCP", "PPP"], { P: `${ID}:paper_plane`, C: "minecraft:chest" }, 1);
shaped("horn_cord", ["S", "S", "P"], { S: "minecraft:string", P: "minecraft:dark_oak_planks" }, 1);
shaped("livery_board", ["RYB", "PPP"], { R: "minecraft:red_dye", Y: "minecraft:yellow_dye", B: "minecraft:blue_dye", P: "minecraft:dark_oak_planks" }, 1);
// The livery selector: a livery board between two more dark oak planks, its swatches painted out as pictures.
shaped("livery_selector", ["PLP"], { L: `${ID}:livery_board`, P: "minecraft:dark_oak_planks" }, 1);
shaped("flagstaff", ["W", "S", "S"], { W: "minecraft:white_dye", S: "minecraft:stick" }, 2);
shaped("flag_halyard", ["S", "F", "C"], { S: "minecraft:string", F: `${ID}:flagstaff`, C: "minecraft:copper_ingot" }, 1);
shaped("lookout_glass", ["S", "C", "F"], { S: "minecraft:spyglass", C: "minecraft:copper_ingot", F: "minecraft:dark_oak_fence" }, 1);
shaped("screen_stand", ["F", "C"], { F: "minecraft:dark_oak_fence", C: "minecraft:copper_ingot" }, 2);
shaped("ballast_valve", ["N", "C", "B"], { N: "minecraft:gold_nugget", C: "minecraft:copper_ingot", B: "minecraft:bucket" }, 1);
shaped("searchlight", ["I", "L", "G"], { I: "minecraft:iron_ingot", L: "minecraft:redstone_lamp", G: "minecraft:glass" }, 1);
shaped("floodlight", ["I", "L", "D"], { I: "minecraft:iron_ingot", L: "minecraft:redstone_lamp", D: "minecraft:daylight_detector" }, 1);
shaped("franking_machine", ["P", "C"], { P: "minecraft:paper", C: "minecraft:cartography_table" }, 1);
shaped("mail_sack", ["S", "L", "P"], { S: "minecraft:string", L: "minecraft:leather", P: "minecraft:paper" }, 1);
shaped("banner_cleat", ["L", "C", "W"], { L: "minecraft:lead", C: "minecraft:copper_ingot", W: "minecraft:white_wool" }, 1);
shaped("crane_winch", ["CLC", "IRI"], { C: "minecraft:chain", L: "minecraft:lever", I: "minecraft:iron_ingot", R: "minecraft:lead" }, 1);
shaped("flight_deck", ["SS", "SS"], { S: "minecraft:smooth_stone_slab" }, 4);
shaped("flight_deck_stripe", ["W", "D"], { W: "minecraft:white_dye", D: `${ID}:flight_deck` }, 1);
shaped("deck_clamp", ["Y", "P", "D"], { Y: "minecraft:yellow_dye", P: "minecraft:piston", D: `${ID}:flight_deck` }, 1);
shaped("carrier_lever", ["Y", "L"], { Y: "minecraft:yellow_dye", L: "minecraft:lever" }, 1);
shaped("searchlight_handle", ["L", "C"], { L: "minecraft:lever", C: "minecraft:copper_ingot" }, 1);
shaped("skywriting_board", ["WBW", "PPP"], { W: "minecraft:white_dye", B: "minecraft:blue_dye", P: "minecraft:dark_oak_planks" }, 1);
shaped("boarding_stairs_button", ["G", "B"], { G: "minecraft:glowstone_dust", B: "minecraft:polished_blackstone_button" }, 1);
shaped("boost_button", ["F", "B"], { F: "minecraft:fire_charge", B: "minecraft:polished_blackstone_button" }, 1);
shaped("ships_plaque", ["CNC"], { C: "minecraft:copper_ingot", N: "minecraft:gold_nugget" }, 1);
shaped("speaker", ["PNP", "PNP"], { P: "minecraft:black_concrete", N: "minecraft:note_block" }, 2);
shaped("dance_floor", ["GLG"], { G: "minecraft:black_stained_glass", L: "minecraft:glowstone_dust" }, 2);
shaped("club_wall", ["WW", "WW"], { W: "minecraft:black_wool" }, 4);
shaped("techno_deck", ["NJN", "CCC"], { N: "minecraft:note_block", J: "minecraft:jukebox", C: "minecraft:black_concrete" }, 1);
shaped("secret_hatch", ["T", "G"], { T: "minecraft:iron_trapdoor", G: `${ID}:zeppelin_catwalk` }, 1);
shaped("trim_wheel", ["SSS", "S S", "SSS"], { S: "minecraft:stick" }, 1);
shaped("bubble_level", ["GLG", "PPP"], { G: "minecraft:gold_nugget", L: "minecraft:lime_stained_glass_pane", P: "minecraft:dark_oak_slab" }, 1);
shaped("trim_winch", ["ICI", "I I"], { I: "minecraft:iron_ingot", C: "minecraft:chain" }, 1);
shaped("trim_car", ["III", "IBI", "III"], { I: "minecraft:iron_ingot", B: "minecraft:iron_block" }, 1);
shaped("engine_repeater", ["B", "G", "C"], { B: "minecraft:bell", G: "minecraft:gold_ingot", C: "minecraft:copper_ingot" }, 1);
shaped("picture_board", ["GGG", "RDB", "PPP"], { G: "minecraft:glass_pane", R: "minecraft:redstone", D: "minecraft:glowstone_dust", B: "minecraft:stone_button", P: "minecraft:dark_oak_planks" }, 1);
shaped("christening_bottle", ["N", "S", "B"], { N: "minecraft:gold_nugget", S: "minecraft:sweet_berries", B: "minecraft:glass_bottle" }, 1);
shaped("landing_target", ["WRW", "RWR", "WRW"], { W: "minecraft:white_carpet", R: "minecraft:red_carpet" }, 1);
shaped("seatbelt_sign", ["GLG"], { G: "minecraft:glass_pane", L: "minecraft:redstone_lamp" }, 2);
shaped("seatbelt_switch", ["L", "P"], { L: "minecraft:stone_button", P: `${ID}:instrument_panel` }, 1);
shaped("light_strip", ["GGG", "RLR"], { G: "minecraft:glass_pane", R: "minecraft:redstone", L: "minecraft:glowstone_dust" }, 6);
shaped("pendant_light", ["C", "I", "G"], { C: `${ID}:light_cable`, I: "minecraft:iron_ingot", G: "minecraft:glowstone" }, 1);
shaped("light_cable", ["S", "S", "S"], { S: "minecraft:string" }, 3);
shaped("overhead_bin", ["PPP", "P P", "PPP"], { P: `${ID}:airframe_panel` }, 2);
shaped("passenger_screen", ["GGG", "PRP"], { G: "minecraft:black_stained_glass_pane", P: `${ID}:airframe_panel`, R: "minecraft:redstone" }, 3);
shaped("signal_flare", ["P", "G", "S"], { P: "minecraft:paper", G: "minecraft:gunpowder", S: "minecraft:stick" }, 4);
shaped("drop_bay", ["PTP", "P P", "PTP"], { P: `${ID}:airframe_panel`, T: "minecraft:iron_trapdoor" }, 1);
shaped("drop_release", ["R", "P"], { R: "minecraft:redstone_torch", P: `${ID}:instrument_panel` }, 1);
shaped("galley", ["III", "FCF", "III"], { I: "minecraft:iron_ingot", F: "minecraft:furnace", C: "minecraft:cauldron" }, 1);
shaped("trolley", ["III", "ICI", "N N"], { I: "minecraft:iron_ingot", C: "minecraft:chest", N: "minecraft:iron_nugget" }, 1);
shaped("chicken_meal", ["CPB"], { C: "minecraft:cooked_chicken", P: "minecraft:baked_potato", B: "minecraft:bowl" }, 1);
shaped("pasta_meal", ["WMB"], { W: "minecraft:wheat", M: "minecraft:brown_mushroom", B: "minecraft:bowl" }, 1);
shaped("pretzels", ["WSW"], { W: "minecraft:wheat", S: "minecraft:sugar" }, 4);
// Snow and sugar churned into milk, on a wheat cone.
shaped("ice_cream", ["SNS", " M ", " W "], { S: "minecraft:sugar", N: "minecraft:snowball", M: "minecraft:milk_bucket", W: "minecraft:wheat" }, 2);
shaped("airline_coffee", ["C", "M", "P"], { C: "minecraft:cocoa_beans", M: "minecraft:milk_bucket", P: "minecraft:paper" }, 2);
shaped("cargo_crate", ["PSP", "PCP", "PPP"], { P: "minecraft:oak_planks", S: `${ID}:parachute`, C: "minecraft:chest" }, 1);
shaped("basketball", [" L ", "LDL", " L "], { L: "minecraft:leather", D: "minecraft:orange_dye" }, 1);
shaped("hoop", ["III", "S S", " S "], { I: "minecraft:iron_ingot", S: "minecraft:string" }, 1);
shaped("scoreboard", ["BBB", "LGL", "BBB"], { B: "minecraft:black_concrete", L: "minecraft:redstone_lamp", G: "minecraft:glowstone" }, 1);
shaped("ball_rack", ["IBI", "IBI"], { I: "minecraft:iron_bars", B: `${ID}:basketball` }, 1);
shaped("window_shades", ["W", "P"], { W: "minecraft:white_wool", P: `${ID}:instrument_panel` }, 1);
shaped("cabin_lights", ["G", "P"], { G: "minecraft:glowstone_dust", P: `${ID}:instrument_panel` }, 1);
shaped("handset", ["S", "P"], { S: "minecraft:string", P: `${ID}:instrument_panel` }, 1);
shaped("tower_radio", ["IRI", "INI", "ICI"], { I: "minecraft:iron_ingot", R: "minecraft:lightning_rod", N: "minecraft:note_block", C: "minecraft:comparator" }, 1);
shaped("tower_radar", ["GGG", "GRG", "ICI"], { G: "minecraft:green_stained_glass_pane", R: "minecraft:redstone", I: "minecraft:iron_ingot", C: "minecraft:compass" }, 1);
shaped("transponder", ["IGI", "RCR"], { I: "minecraft:iron_ingot", G: "minecraft:glass_pane", R: "minecraft:redstone", C: "minecraft:compass" }, 1);
shaped("motion_sensor", ["QOQ", " R "], { Q: "minecraft:quartz", O: "minecraft:observer", R: "minecraft:redstone" }, 1);
shaped("lift_panel", ["IGI", " R "], { I: "minecraft:iron_ingot", G: "minecraft:black_stained_glass_pane", R: "minecraft:redstone" }, 1);
shaped("jet_bridge", ["GGG", "IPI", "IRI"], { G: "minecraft:glass", I: "minecraft:iron_ingot", P: "minecraft:piston", R: "minecraft:redstone" }, 1);
shaped("check_in_desk", ["PGP", "IRI", "III"], { P: "minecraft:paper", G: "minecraft:glass_pane", I: "minecraft:iron_ingot", R: "minecraft:redstone" }, 1);
shaped("cockpit_window", ["GGG", "GDG", "GGG"], { G: `${ID}:cabin_window`, D: "minecraft:black_dye" }, 8);
shaped("cabin_floor", ["CC", "PP"], { C: "minecraft:gray_carpet", P: `${ID}:airframe_panel` }, 4);
shaped("airliner_door", ["PP", "PG", "PP"], { P: `${ID}:airframe_panel`, G: "minecraft:glass" }, 3);
shaped("engine_intake", ["PPP", "PFP", "PPP"], { P: `${ID}:airframe_panel`, F: "minecraft:iron_bars" }, 8);
shaped("landing_gear", ["I", "P", "I"], { I: "minecraft:iron_ingot", P: "minecraft:piston" }, 4);
shaped("airstair_controller", ["PRP", "RXR", "PRP"], { P: `${ID}:airframe_panel`, R: "minecraft:redstone", X: "minecraft:piston" }, 1);
shaped("airstair_call_button", ["B", "P"], { B: "minecraft:stone_button", P: `${ID}:airframe_panel` }, 1);
shaped("cabin_light", ["PGP"], { P: `${ID}:airframe_panel`, G: "minecraft:glowstone" }, 2);
shaped("thrust_lever", ["K", "S", "P"], { K: "minecraft:quartz", S: "minecraft:stick", P: `${ID}:airframe_panel` }, 1);
shaped("instrument_panel", ["GGG", "RPR"], { G: "minecraft:black_stained_glass_pane", R: "minecraft:redstone", P: `${ID}:airframe_panel` }, 3);
shaped("airframe_fairing", ["P ", "PP"], { P: `${ID}:airframe_panel` }, 4);
shaped("autopilot", ["CRC", "RPR"], { C: "minecraft:comparator", R: "minecraft:redstone", P: `${ID}:instrument_panel` }, 1);
shaped("nav_light", [" G ", "GTG"], { G: "minecraft:glass_pane", T: "minecraft:redstone_torch" }, 2);
shaped("tiller", [" S ", "RPR"], { S: "minecraft:stick", R: "minecraft:redstone", P: `${ID}:airframe_panel` }, 1);
shaped("parking_brake", ["S", "R", "P"], { S: "minecraft:lever", R: "minecraft:redstone", P: `${ID}:airframe_panel` }, 1);
shaped("flight_display", ["G", "P"], { G: "minecraft:black_stained_glass_pane", P: `${ID}:instrument_panel` }, 1);
shaped("tail_camera_display", ["GGG", "PSP"], { G: "minecraft:black_stained_glass_pane", P: `${ID}:airframe_panel`, S: "minecraft:spyglass" }, 1);
shaped("announcement_panel", ["GGG", "PNP"], { G: "minecraft:black_stained_glass_pane", P: `${ID}:airframe_panel`, N: "minecraft:note_block" }, 1);
shaped("flight_map", ["GGG", "GMG", "PRP"], { G: "minecraft:black_stained_glass_pane", M: "minecraft:map", P: `${ID}:airframe_panel`, R: "minecraft:redstone" }, 1);
shaped("landing_gear_tire", ["KKK", "KSK", "KKK"], { K: "minecraft:black_wool", S: "minecraft:iron_ingot" }, 4);
// Duty free and the paper plane: a counter of glass, emeralds and a chest on white concrete; a sheet of paper folds
// into a plane; a cap in the shape of a helmet, with a gold badge; a model airliner, its fuselage and wings of airframe
// panel on a stick.
shaped("duty_free_counter", ["GGG", "ECE", "WWW"], { G: "minecraft:glass", E: "minecraft:emerald", C: "minecraft:chest", W: "minecraft:white_concrete" }, 1);
shaped("paper_plane", ["P"], { P: "minecraft:paper" }, 1);
// The laser pointer: red glass over a stick of redstone in an iron tube.
shaped("laser_pointer", ["G", "R", "I"], { G: "minecraft:red_stained_glass_pane", R: "minecraft:redstone", I: "minecraft:iron_ingot" }, 1);
shaped("pilot_cap", ["WGW", "W W"], { W: "minecraft:black_wool", G: "minecraft:gold_nugget" }, 1);
shaped("model_airliner", [" P ", "PPP", " S "], { P: `${ID}:airframe_panel`, S: "minecraft:stick" }, 1);
// The pilot's helmet: an iron shell, a dark glass visor across its middle and black wool ear cups under it.
shaped("pilot_helmet", ["III", "IGI", "W W"], { I: "minecraft:iron_ingot", G: "minecraft:black_stained_glass_pane", W: "minecraft:black_wool" }, 1);
// The space helmet: a white shell, a gold visor across its middle and an iron ring at its neck under it.
shaped("space_helmet", ["CCC", "CGC", "III"], { C: "minecraft:white_concrete", G: "minecraft:gold_ingot", I: "minecraft:iron_ingot" }, 1);
shaped("springboard", ["WWW", "PSP"], { W: "minecraft:white_wool", P: "minecraft:birch_planks", S: "minecraft:slime_ball" }, 1);
shaped("starlight_panel", ["WWW", "WGW", "WWW"], { W: "minecraft:blue_wool", G: "minecraft:glowstone_dust" }, 8);
shaped("gym_rings", ["C C", "L L", "P P"], { C: "minecraft:chain", L: "minecraft:leather", P: "minecraft:birch_planks" }, 1);
shaped("hide_and_seek_button", ["B", "N", "I"], { B: "minecraft:stone_button", N: "minecraft:note_block", I: "minecraft:iron_ingot" }, 1);
// Room dividers: cloth hung from a rail on rings, a cream panel of pale wood, an arch of cream panels whitened, and
// the exit sign's glowing green glass.
shaped("cabin_curtain", ["NSN", "WWW", "WWW"], { N: "minecraft:iron_nugget", S: "minecraft:stick", W: "minecraft:white_wool" }, 6);
shaped("cabin_partition", ["PPP", "PDP", "PPP"], { P: "minecraft:birch_planks", D: "minecraft:white_dye" }, 8);
shaped("cabin_arch", ["PDP", "P P"], { P: `${ID}:cabin_partition`, D: "minecraft:white_dye" }, 4);
shaped("exit_sign", ["GLG"], { G: "minecraft:green_stained_glass_pane", L: "minecraft:glowstone_dust" }, 2);
holdAndTailRecipes();
// The vacuum toilet: a bucket for its bowl in iron.
shaped("vacuum_toilet", ["IBI", " I "], { I: "minecraft:iron_ingot", B: "minecraft:bucket" }, 1);
flightDeckRecipes();
rubberDuckRecipes();
coffeeMachineRecipes();
cabinStairsRecipes();
crewLockerRecipes();
gearRecipes();
laserRecipes();
selfDestructRecipes();
slideRecipes();
cabinSeatRecipes();
cabinCarpetRecipes();
gymFloorRecipes();
lavatoryBasinRecipes();
bulkheadSignRecipes();
wcSignRecipes();
denFurnitureRecipes();
airstairButtonRecipes();
doorButtonRecipes();
cargoNetRecipes();
cargoFloorRecipes();
uldContainerRecipes();
poolRecipes();
gymHallRecipes();
seatConsoleRecipes();
galleyUnitRecipes();
planterRecipes();
barRecipes();
dutyFreeKioskRecipes();
towerInstrumentsRecipes();

// The First Flight advancements. The first opens the tab on joining a world, quietly, as vanilla's tabs open: no toast
// and no line in chat for something nobody did. Each of the rest has one criterion, "done", that only the mod grants,
// when it happens (see FirstFlight).
const FIRST_FLIGHT = [
  // name, parent, icon, frame, title, description
  ["root", null, "airliner_blueprint", "task", "First Flight", "Airliners, from the runway to the jet stream"],
  ["cleared_for_takeoff", "root", "thrust_lever", "task", "Cleared for Takeoff", "Be aboard an airliner as it climbs away from the runway"],
  ["buckle_up", "root", "seatbelt_sign", "task", "Buckle Up", "Try to get up from your seat while the seatbelt signs are on"],
  ["butter_landing", "cleared_for_takeoff", "landing_gear_tire", "challenge", "Butter Landing", "Be aboard an airliner touching down so gently you hardly feel it"],
  ["on_the_numbers", "cleared_for_takeoff", "runway_blueprint", "goal", "On the Numbers", "Be aboard an airliner as its autopilot lands it on a runway you laid down, flying to a pin by it"],
  ["swish", "cleared_for_takeoff", "basketball", "challenge", "Swish at 30,000 Feet", "Score a basket aboard an airliner in flight"],
  ["perfect_jump", "cleared_for_takeoff", "parachute", "goal", "Perfect Jump", "Land your parachute within a block of a landing target"],
  ["special_delivery", "cleared_for_takeoff", "cargo_crate", "goal", "Special Delivery", "Drop a care package within 10 blocks of a burning signal flare"],
  ["frequent_flyer", "cleared_for_takeoff", "boarding_pass", "task", "Frequent Flyer", "Collect a villager passenger's fare"],
  ["air_mail", "cleared_for_takeoff", "paper_plane", "goal", "Air Mail", "Throw a paper plane out of an airliner in flight and see it come down 100 blocks away"],
  ["duty_free", "frequent_flyer", "duty_free_counter", "task", "Duty Free", "Spend emeralds at a duty-free counter"],
  ["cannonball", "cleared_for_takeoff", "springboard", "goal", "Cannonball", "Dive into the moon pool off its springboard or its tower while the airliner flies"],
  ["blue_ice", "cleared_for_takeoff", "vacuum_toilet", "goal", "Blue Ice", "Flush an airliner's toilet in flight and drop blue ice on the world below"],
  ["edge_of_space", "root", "model_rocket", "goal", "Edge of Space", "Be aboard a rocket as she climbs to the edge of space"],
];
for (const [name, parent, icon, frame] of FIRST_FLIGHT) {
  write(`data/${ID}/advancement/first_flight/${name}.json`, {
    ...(parent ? { parent: `${ID}:first_flight/${parent}` } : {}),
    display: {
      icon: { id: `${ID}:${icon}` },
      title: { translate: `advancements.${ID}.first_flight.${name}.title` },
      description: { translate: `advancements.${ID}.first_flight.${name}.description` },
      frame,
      ...(parent ? {} : { background: `${ID}:textures/block/airframe_panel.png`, show_toast: false, announce_to_chat: false }),
    },
    criteria: { done: { trigger: parent ? "minecraft:impossible" : "minecraft:tick" } },
  });
}

write(`assets/${ID}/lang/en_us.json`, {
  "itemGroup.aerolarge": "Aerolarge",
  "itemGroup.aerolarge.blueprints": "Blueprints",
  "itemGroup.aerolarge.airframe": "Airframe",
  "itemGroup.aerolarge.flight_deck": "Flight Deck",
  "itemGroup.aerolarge.cabin": "Cabin",
  "itemGroup.aerolarge.gym": "Gym Hall",
  "itemGroup.aerolarge.drops": "Skydiving and Drops",
  "itemGroup.aerolarge.airport": "Airport",
  "block.aerolarge.airframe_panel": "Airframe Panel",
  "block.aerolarge.airframe_fairing": "Airframe Fairing",
  "block.aerolarge.livery_panel": "Livery Panel",
  "block.aerolarge.cabin_window": "Cabin Window",
  "block.aerolarge.cockpit_window": "Cockpit Window",
  "block.aerolarge.cabin_floor": "Cabin Floor",
  "block.aerolarge.cabin_light": "Cabin Light",
  "block.aerolarge.airliner_door": "Airliner Door",
  "block.aerolarge.engine_intake": "Engine Intake",
  "block.aerolarge.landing_gear": "Landing Gear",
  "block.aerolarge.landing_gear_tire": "Landing Gear Tire",
  "block.aerolarge.airstair_controller": "Airstair Controller",
  "block.aerolarge.airstair_call_button": "Airstair Call Button",
  "block.aerolarge.airframe_shell": "Airframe Shell",
  "block.aerolarge.cockpit_glass": "Cockpit Glass",
  "block.aerolarge.hull_glass": "Hull Glass",
  "block.aerolarge.smoked_glass": "Smoked Glass",
  "block.aerolarge.cabin_window_shell": "Curved Cabin Window",
  "block.aerolarge.oval_window": "Oval Window",
  "block.aerolarge.lift_machine": "Lift Machine",
  "block.aerolarge.lift_door": "Lift Door",
  "block.aerolarge.lift_board": "Lift Board",
  "entity.aerolarge.lift_car": "Lift Car",
  "block.aerolarge.thrust_lever": "Thrust Lever",
  "block.aerolarge.thrust_relay": "Thrust Relay",
  "block.aerolarge.hull_relay": "Hull Relay",
  "block.aerolarge.instrument_panel": "Instrument Panel",
  "message.aerolarge.thrust": "Thrust: %s%%",
  "block.aerolarge.autopilot": "Autopilot",
  "block.aerolarge.nav_light": "Navigation Light",
  "block.aerolarge.engine_core": "Engine Core",
  "block.aerolarge.tiller": "Nose Wheel Tiller",
  "block.aerolarge.flight_display": "Flight Display",
  "block.aerolarge.parking_brake": "Parking Brake",
  "message.aerolarge.tiller.left": "Nose wheel: steering left",
  "message.aerolarge.tiller.right": "Nose wheel: steering right",
  "message.aerolarge.tiller.centre": "Nose wheel: straight ahead",
  "message.aerolarge.brake.set": "Parking brake set",
  "message.aerolarge.brake.released": "Parking brake released",
  "subtitles.aerolarge.engine": "Jet engine roars",
  ...VOICE_LANG,
  ...atcLang(),
  ...rocketSoundsLang(),
  "message.aerolarge.autopilot.engaged": "Autopilot engaged: take-off, then climbing to altitude %s at %s b/s, %s.",
  "message.aerolarge.autopilot.off": "Autopilot off: the thrust levers, tiller and parking brake have the airliner again.",
  "message.aerolarge.autopilot.off_zeppelin": "Autopilot off: the wheels, telegraphs and burner lever in the control car have the zeppelin now.",
  "message.aerolarge.autopilot.off_rocket": "Autopilot off: the rocket rests on her fins, her engines quiet, ready to count down again.",
  "message.aerolarge.autopilot.not_assembled": "The autopilot only flies an assembled airliner, and there is no Physics Assembler aboard to assemble it.",
  "message.aerolarge.autopilot.assembling": "Assembling the airliner: the autopilot takes it from here.",
  "message.aerolarge.autopilot.assembly_failed": "The Physics Assembler could not assemble the airliner: %s",
  "message.aerolarge.autopilot.still_assembling": "The airliner is still being assembled.",
  "message.aerolarge.autopilot.assembly_unknown": "it did not say why",
  "message.aerolarge.autopilot.phase.takeoff": "TAKE-OFF",
  "message.aerolarge.autopilot.phase.climb": "CLIMB",
  "message.aerolarge.autopilot.phase.cruise": "CRUISE",
  "message.aerolarge.autopilot.phase.descent": "DESCENT",
  "message.aerolarge.autopilot.phase.land": "LAND",
  "message.aerolarge.autopilot.phase.rollout": "ROLL-OUT",
  "message.aerolarge.autopilot.holding": "Autopilot engaged: altitude %s at %s b/s, %s.",
  "message.aerolarge.autopilot.set": "Autopilot set: altitude %s at %s b/s, %s.",
  "message.aerolarge.autopilot.mode.heading": "heading %s",
  "message.aerolarge.autopilot.mode.orbit_left": "circling left, radius %s blocks",
  "message.aerolarge.autopilot.mode.orbit_right": "circling right, radius %s blocks",
  "message.aerolarge.autopilot.mode.destination": "flying to the pin, then circling it, radius %s blocks",
  "message.aerolarge.autopilot.mode.destination_land": "flying to the pin to land near it, on clear, level ground",
  "message.aerolarge.autopilot.pin.found": "Landing site found %s blocks from the pin: flying the approach, to land heading %s.",
  "message.aerolarge.autopilot.pin.nowhere": "Nowhere clear to land near the pin yet: flying over it again and again, looking as more of the land comes into view.",
  "message.aerolarge.autopilot.pin.go_around": "Not lined up with the landing site: going around to try again.",
  "message.aerolarge.autopilot.coming_in": "The autopilot is landing; disengage it to take over.",
  "message.aerolarge.autopilot.landing": "Autopilot landing: down ahead on heading %s, slowing until the nose sits level; once down it brakes to a stop and parks.",
  "message.aerolarge.autopilot.on_ground": "The autopilot lands from the air: the airliner is on the ground already.",
  "message.aerolarge.autopilot.rolling_out": "The autopilot is braking to a stop after landing; disengage it to take over.",
  "message.aerolarge.autopilot.landed": "Landed and stopped: thrust levers at idle, parking brake set, autopilot off.",
  "message.aerolarge.autopilot.unstuck": "Control jets firing: hold Unstuck to lift the airliner higher.",
  "message.aerolarge.autopilot.unstuck.firing": "The control jets are firing already.",
  "message.aerolarge.autopilot.unstuck.resting": "The control jets are resting: try again in a few seconds.",
  "message.aerolarge.autopilot.unstuck.engines_off": "Push the thrust levers forward first: the jets only help engines that cannot move the airliner.",
  "message.aerolarge.autopilot.unstuck.braked": "The parking brake is holding the airliner: release it to roll.",
  "message.aerolarge.autopilot.unstuck.moving": "The airliner is not stuck: it is moving, or has only just stopped.",
  "item.aerolarge.parachute": "Parachute",
  "block.aerolarge.jump_light": "Jump Light",
  "block.aerolarge.jump_switch": "Jump Light Switch",
  "block.aerolarge.parachute_rack": "Parachute Rack",
  "block.aerolarge.landing_target": "Landing Target",
  "message.aerolarge.jump_switch.off": "Jump lights off",
  "message.aerolarge.jump_switch.red": "Jump lights red: stand by",
  "message.aerolarge.jump_switch.green": "Jump lights green: GO!",
  "message.aerolarge.parachute_rack.put_on": "You put on a parachute. Jump while falling to open it.",
  "message.aerolarge.parachute_rack.handed": "You took a parachute: wear it in your chest slot.",
  "block.aerolarge.paper_plane_box": "Paper Plane Box",
  "message.aerolarge.paper_plane_box.handed": "A paper plane: throw it from up here and watch it glide",
  "message.aerolarge.parachute_rack.wearing": "You are already wearing a parachute.",
  "message.aerolarge.decompression": "Door open! Explosive decompression: keep clear of the door, or stay in your seat.",
  "message.aerolarge.landing_target.landed": "%s landed %s blocks from the target",
  "message.aerolarge.landing_target.record": "%s landed %s blocks from the target: a new best!",
  "message.aerolarge.landing_target.best": "Best landing: %s, %s blocks from the middle",
  "message.aerolarge.landing_target.none": "No landings on this target yet",
  "message.aerolarge.landing_target.plane_landed": "%s's paper plane came down %s blocks from the target",
  "message.aerolarge.landing_target.plane_record": "%s's paper plane came down %s blocks from the target: a new best!",
  "message.aerolarge.landing_target.best_plane": "Best paper plane: %s, %s blocks from the middle",
  "block.aerolarge.handset": "Captain's Handset",
  "block.aerolarge.tower_radio": "Tower Radio",
  "message.aerolarge.radio.keyed_announcement": "Speak now: your next message is announced to the cabin.",
  "message.aerolarge.radio.keyed_radio": "Speak now: your next message goes out over the radio.",
  "message.aerolarge.radio.released": "Handset back in its cradle.",
  "title.aerolarge.captain": "Captain %s",
  "chat.aerolarge.announcement": "[Cabin] Captain %s: %s",
  "chat.aerolarge.radio": "[Radio] %s: %s",
  "block.aerolarge.signal_flare": "Signal Flare",
  "block.aerolarge.drop_bay": "Drop Bay",
  "block.aerolarge.drop_release": "Drop Release",
  "container.aerolarge.drop_bay": "Drop Bay",
  "message.aerolarge.drop_bay.empty": "The drop bay is empty",
  "message.aerolarge.drop_bay.away": "%s away! %s left in the bay",
  "message.aerolarge.drop_bay.no_bomb": "This version of Ultrasplosive has no bomb to drop from the air",
  "message.aerolarge.drop_bay.too_low": "Too low to drop a nuke: climb above %s blocks over the ground first",
  "message.aerolarge.drop_release.armed": "Bay doors open: use again to drop, sneak to close",
  "message.aerolarge.drop_release.safe": "Bay doors closed",
  "message.aerolarge.drop_release.no_bay": "This release is not wired to a drop bay",
  "block.aerolarge.galley": "Galley",
  "item.aerolarge.trolley": "Trolley",
  "entity.aerolarge.trolley": "Trolley",
  "item.aerolarge.chicken_meal": "Chicken Meal",
  "item.aerolarge.pasta_meal": "Pasta Meal",
  "item.aerolarge.pretzels": "Pretzels",
  "item.aerolarge.ice_cream": "Ice Cream",
  "item.aerolarge.airline_coffee": "Airline Coffee",
  "message.aerolarge.trolley.served": "Chicken or pasta? %s for you (%s left on the trolley)",
  "message.aerolarge.trolley.empty": "The trolley is empty: restock it at the galley",
  "message.aerolarge.galley.rolled_out": "A full trolley, ready to go",
  "message.aerolarge.galley.blocked": "No room in front of the galley for a trolley",
  "message.aerolarge.galley.restocked": "Trolley restocked",
  "message.aerolarge.galley.full": "The trolley is already full",
  "subtitles.aerolarge.trolley_squeak": "Trolley squeaks",
  "block.aerolarge.jet_bridge": "Jet Bridge",
  "message.aerolarge.jet_bridge.docking": "Docking at the airliner door %s blocks out",
  "message.aerolarge.jet_bridge.no_door": "No airliner door within %s blocks ahead of the bridge",
  "message.aerolarge.jet_bridge.misaligned": "Not lined up with the airliner door %s blocks out: move the bridge %s",
  "message.aerolarge.jet_bridge.left": "%s left",
  "message.aerolarge.jet_bridge.right": "%s right",
  "message.aerolarge.jet_bridge.up": "%s up",
  "message.aerolarge.jet_bridge.down": "%s down",
  "message.aerolarge.jet_bridge.moving": "Wait for the airliner to stop",
  "message.aerolarge.jet_bridge.blocked": "Something stands in the way %s blocks out, short of the airliner door %s blocks out",
  "block.aerolarge.check_in_desk": "Check-in Desk",
  "item.aerolarge.boarding_pass": "Boarding Pass",
  "item.aerolarge.boarding_pass.tooltip": "Hand it to a villager to fly it as a passenger",
  "message.aerolarge.check_in.printed": "Boarding pass printed: hand it to a villager",
  "message.aerolarge.passengers.off": "Villager passengers are turned off: turn on Villager Passengers in the mod settings",
  "message.aerolarge.boarding_pass.boarding": "%s takes the boarding pass and looks for a seat",
  "message.aerolarge.boarding_pass.already": "%s already has a boarding pass",
  "message.aerolarge.passengers.fare": "%s paid %s emeralds for a flight of %s blocks",
  "block.aerolarge.cargo_crate": "Care Package",
  "container.aerolarge.cargo_crate": "Care Package",
  "entity.aerolarge.falling_crate": "Care Package",
  "message.aerolarge.cargo_crate.landed": "A care package has landed",
  "message.aerolarge.cargo_crate.landed_near": "A care package has landed %s blocks from the flare",
  "item.aerolarge.basketball": "Basketball",
  "entity.aerolarge.basketball": "Basketball",
  "block.aerolarge.hoop": "Basketball Hoop",
  "block.aerolarge.scoreboard": "Scoreboard",
  "block.aerolarge.ball_rack": "Ball Rack",
  "team.aerolarge.home": "HOME",
  "team.aerolarge.guest": "GUEST",
  "message.aerolarge.hoop.team": "This hoop now counts for %s",
  "message.aerolarge.hoop.basket": "Swish! %s points for %s",
  "message.aerolarge.hoop.basket_score": "Swish! %s points for %s: %s",
  "message.aerolarge.scoreboard.score": "%s %s : %s %s",
  "message.aerolarge.scoreboard.reset": "New game: the scoreboard is back to nil all",
  "subtitles.aerolarge.ball_bounce": "Basketball bounces",
  "subtitles.aerolarge.basket_swish": "Basket swishes",
  "subtitles.aerolarge.scoreboard_buzzer": "Scoreboard buzzes",
  "block.aerolarge.window_shades": "Window Shades Control",
  "message.aerolarge.window_shades.open": "Window shades going up",
  "message.aerolarge.window_shades.closed": "Window shades coming down",
  "subtitles.aerolarge.shade_motor": "Window shade whirrs",
  "subtitles.aerolarge.lift_door": "Lift doors slide",
  "subtitles.aerolarge.lift_start": "Lift sets off",
  "subtitles.aerolarge.lift_hum": "Lift hums",
  "subtitles.aerolarge.lift_chime_up": "Lift chimes, going up",
  "subtitles.aerolarge.lift_chime_down": "Lift chimes, going down",
  "subtitles.aerolarge.space_light": "You feel light",
  "subtitles.aerolarge.space_heavy": "Your weight comes back",
  "subtitles.aerolarge.lift_music": "Lift music",
  "block.aerolarge.cabin_lights": "Cabin Lights Dimmer",
  "message.aerolarge.cabin_lights.bright": "Cabin lights bright",
  "message.aerolarge.cabin_lights.dim": "Cabin lights dimmed for take-off and landing",
  "message.aerolarge.cabin_lights.night": "Cabin lights set for the night",
  ...ROOM_DIVIDER_LANG,
  "block.aerolarge.light_strip": "LED Light Strip",
  "block.aerolarge.pendant_light": "Pendant Light",
  "block.aerolarge.light_cable": "Light Cable",
  "block.aerolarge.overhead_bin": "Overhead Locker",
  "container.aerolarge.overhead_bin": "Overhead Locker",
  "block.aerolarge.passenger_screen": "Passenger Screen",
  "screen.aerolarge.passenger_screen.welcome": "WELCOME ABOARD",
  "screen.aerolarge.passenger_screen.standby": "ASSEMBLE TO FLY",
  "screen.aerolarge.passenger_screen.title": "FLIGHT INFO",
  "screen.aerolarge.passenger_screen.map_title": "ROUTE MAP",
  "screen.aerolarge.passenger_screen.status.ground": "ON THE GROUND",
  "screen.aerolarge.passenger_screen.status.climbing": "CLIMBING",
  "screen.aerolarge.passenger_screen.status.cruising": "CRUISING",
  "screen.aerolarge.passenger_screen.status.descending": "DESCENDING",
  "screen.aerolarge.passenger_screen.altitude": "ALTITUDE",
  "screen.aerolarge.passenger_screen.ground_speed": "GROUND SPEED",
  "screen.aerolarge.passenger_screen.vertical_speed": "VERTICAL SPEED",
  "screen.aerolarge.passenger_screen.heading": "HEADING",
  "screen.aerolarge.passenger_screen.height": "ABOVE GROUND",
  "screen.aerolarge.passenger_screen.distance": "DISTANCE FLOWN",
  "screen.aerolarge.passenger_screen.flight_time": "FLIGHT TIME %s",
  "screen.aerolarge.passenger_screen.unit.metres": "m",
  "screen.aerolarge.passenger_screen.unit.kilometres": "km",
  "screen.aerolarge.passenger_screen.unit.kilometres_per_hour": "km/h",
  "screen.aerolarge.passenger_screen.unit.metres_per_second": "m/s",
  ...Object.fromEntries(["N", "NE", "E", "SE", "S", "SW", "W", "NW"].map((point, i) => [`screen.aerolarge.passenger_screen.compass.${i}`, point])),
  "screen.aerolarge.passenger_screen.cameras": "USE FOR OUTSIDE CAMERAS",
  "screen.aerolarge.passenger_screen.camera": "CAM %s  %s",
  "screen.aerolarge.passenger_screen.camera.tail": "TAIL",
  "screen.aerolarge.passenger_screen.camera.wing": "RIGHT WING",
  "screen.aerolarge.passenger_screen.camera.nose": "NOSE GEAR",
  "screen.aerolarge.passenger_screen.camera.front": "FRONT",
  "screen.aerolarge.passenger_screen.camera.bow": "BOW",
  "screen.aerolarge.passenger_screen.camera.below": "BELOW",
  "screen.aerolarge.passenger_screen.camera.flyby": "FLYBY",
  "screen.aerolarge.passenger_screen.camera.side": "SIDE",
  "screen.aerolarge.passenger_screen.camera.leg": "LANDING LEG",
  "screen.aerolarge.passenger_screen.camera.orbit": "ORBIT",
  "screen.aerolarge.passenger_screen.camera_hint": "%s: next camera    %s: back to the cabin",
  "screen.aerolarge.lookout.hint": "%s: drop your flag there    Mouse wheel: zoom    %s: step back",
  "message.aerolarge.passenger_screen.not_assembled": "The outside cameras come on once the airliner is assembled",
  "message.aerolarge.passenger_screen.no_cameras": "This airliner has no outside cameras: it has no autopilot to fix them to",
  "block.aerolarge.seatbelt_sign": "Seatbelt Sign",
  "block.aerolarge.seatbelt_switch": "Seatbelt Switch",
  "message.aerolarge.seatbelts.on": "Seatbelt signs on",
  "message.aerolarge.seatbelts.off": "Seatbelt signs off",
  "message.aerolarge.seatbelts.fastened": "Your seatbelt is fastened: stay seated while the signs are on.",
  "subtitles.aerolarge.seatbelt_ding": "Seatbelt sign dings",
  "subtitles.aerolarge.pa_chime": "Cabin chime",
  "subtitles.aerolarge.radio_crackle": "Radio crackles",
  "message.aerolarge.unbreakable": "The frame and controls cannot be broken. Sneak holding a wrench to take them apart.",
  "block.aerolarge.tail_camera_display": "Tail Camera Display",
  "screen.aerolarge.tail_camera_display.no_signal": "NO SIGNAL",
  "screen.aerolarge.tail_camera_display.standby": "ASSEMBLE TO FLY",
  "screen.aerolarge.tail_camera_display.camera": "CAM %s",
  "block.aerolarge.announcement_panel": "Announcement Panel",
  "block.aerolarge.flight_map": "Flight Map",
  "message.aerolarge.announcement_panel.unlinked": "This panel is not near an airliner's autopilot.",
  "screen.aerolarge.announcement_panel.welcome": "Welcome",
  "screen.aerolarge.announcement_panel.takeoff": "Take-off",
  "screen.aerolarge.announcement_panel.turbulence": "Seatbelts",
  "screen.aerolarge.announcement_panel.snacks": "Snacks",
  "screen.aerolarge.announcement_panel.window": "Look out",
  "screen.aerolarge.announcement_panel.playtime": "Pool & gym",
  "screen.aerolarge.announcement_panel.sleep": "Sleep",
  "screen.aerolarge.announcement_panel.landing": "Landing",
  "screen.aerolarge.announcement_panel.arrival": "Arrival",
  "block.aerolarge.autopilot_screen": "Autopilot Screen",
  "block.aerolarge.cockpit_display": "Cockpit Display",
  "message.aerolarge.autopilot_screen.unlinked": "This screen is not linked to an autopilot.",
  "screen.aerolarge.autopilot.engage": "AP %s",
  "screen.aerolarge.autopilot.on": "ON",
  "screen.aerolarge.autopilot.off": "OFF",
  "screen.aerolarge.autopilot.heading_left": "◄ HDG",
  "screen.aerolarge.autopilot.heading_right": "HDG ►",
  "screen.aerolarge.autopilot.orbit_left": "◄ CIRCLE",
  "screen.aerolarge.autopilot.orbit_right": "CIRCLE ►",
  "screen.aerolarge.autopilot.altitude_down": "ALT ▼",
  "screen.aerolarge.autopilot.altitude_up": "ALT ▲",
  "screen.aerolarge.autopilot.speed_down": "SPD ▼",
  "screen.aerolarge.autopilot.speed_up": "SPD ▲",
  "screen.aerolarge.autopilot.radius_down": "SIZE ▼",
  "screen.aerolarge.autopilot.radius_up": "SIZE ▲",
  "screen.aerolarge.autopilot.standby": "ASSEMBLE TO FLY",
  "screen.aerolarge.autopilot.disengaged": "AUTOPILOT OFF",
  "screen.aerolarge.autopilot.mode": "%s  %s",
  "screen.aerolarge.autopilot.mode.heading": "HDG %s",
  "screen.aerolarge.autopilot.mode.orbit_left": "CIRCLE L %s",
  "screen.aerolarge.autopilot.mode.orbit_right": "CIRCLE R %s",
  "screen.aerolarge.autopilot.mode.destination": "PIN %s",
  "screen.aerolarge.autopilot.targets": "ALT %s  SPD %s",
  "screen.aerolarge.autopilot.targets.land": "TOUCHDOWN  SPD %s",
  "screen.aerolarge.autopilot.targets.rollout": "BRAKING",
  "screen.aerolarge.autopilot.now": "SPD %s  ALT %s  HDG %s",
  "screen.aerolarge.autopilot.range": "%s blocks",
  "screen.aerolarge.pilot_hud.off": "AP OFF",
  "screen.aerolarge.autopilot_settings.title": "Autopilot",
  "screen.aerolarge.autopilot_settings.now": "Flying",
  "screen.aerolarge.autopilot_settings.take_off_hint": "Takes off, then climbs to fly the settings below.",
  "screen.aerolarge.autopilot_settings.engage_hint": "Engaging flies the settings below.",
  "screen.aerolarge.autopilot_settings.assemble_hint": "Takes off once it has been assembled into one flying body.",
  "screen.aerolarge.autopilot_settings.assembling": "Assembling...",
  "screen.aerolarge.autopilot_settings.mode.destination": "Fly to Pin",
  "screen.aerolarge.autopilot_settings.turn.tight_left": "Tight Circle Left",
  "screen.aerolarge.autopilot_settings.turn.gentle_left": "Gentle Circle Left",
  "screen.aerolarge.autopilot_settings.turn.straight": "Straight On",
  "screen.aerolarge.autopilot_settings.turn.gentle_right": "Gentle Circle Right",
  "screen.aerolarge.autopilot_settings.turn.tight_right": "Tight Circle Right",
  "screen.aerolarge.autopilot_settings.land_at_pin.on": "Land at the Pin: on, landing near it clear of buildings",
  "screen.aerolarge.autopilot_settings.land_at_pin.off": "Land at the Pin: off, circling it",
  "screen.aerolarge.autopilot_settings.map": "Map: drag to look about, scroll to zoom, click to drop the pin",
  "screen.aerolarge.autopilot_settings.map.middle": "X %s  Z %s",
  "screen.aerolarge.autopilot_settings.map.pin": "Pin X %s  Z %s",
  "screen.aerolarge.autopilot_settings.heading": "Heading",
  "screen.aerolarge.autopilot_settings.altitude": "Altitude",
  "screen.aerolarge.autopilot_settings.speed": "Speed",
  "screen.aerolarge.autopilot_settings.range.heading": "%s–%s°",
  "screen.aerolarge.autopilot_settings.range.altitude": "Y %s–%s",
  "screen.aerolarge.autopilot_settings.range.speed": "%s–%s b/s",
  "screen.aerolarge.autopilot_settings.take_off": "Take Off",
  "screen.aerolarge.autopilot_settings.engage": "Engage",
  "screen.aerolarge.autopilot_settings.apply": "Apply",
  "screen.aerolarge.autopilot_settings.disengage": "Disengage",
  "screen.aerolarge.autopilot_settings.land": "Land",
  "screen.aerolarge.autopilot_settings.unstuck": "Unstuck",
  "aerolarge.configuration.autopilot": "Autopilot",
  "aerolarge.configuration.climbHeight": "Climb Height",
  "aerolarge.configuration.rotateSpeed": "Rotate Speed",
  "aerolarge.configuration.cruiseSpeed": "Cruise Speed",
  "aerolarge.configuration.controlSurfaces": "Control Surfaces (Experimental)",
  "aerolarge.configuration.radio": "Radio",
  "aerolarge.configuration.atcChatter": "Air Traffic Control",
  "aerolarge.configuration.atcVolume": "Air Traffic Control Volume",
  "aerolarge.configuration.cameraShake": "Camera Shake",
  "aerolarge.configuration.tailCameraFeed": "Tail Camera Picture",
  "aerolarge.configuration.tailCameraFps": "Tail Camera Pictures a Second",
  "aerolarge.configuration.tailCameraRenderDistance": "Tail Camera Render Distance",
  "aerolarge.configuration.tailCameraResolution": "Tail Camera Picture Height",
  "aerolarge.configuration.pilotHud": "Pilot HUD",
  "aerolarge.configuration.pilotHudScale": "Pilot HUD Size",
  "aerolarge.configuration.nightLights": "Blue Night Lights",
  "aerolarge.configuration.discoLights": "Disco Ball Spots",
  "aerolarge.configuration.cloudlessCabin": "Cloudless Cabin",
  "aerolarge.configuration.spaceSky": "Space Sky",
  "aerolarge.configuration.shootingStars": "Shooting Stars",
  "aerolarge.configuration.heightGauge": "Height Gauge",
  "aerolarge.configuration.helmetBreathing": "Helmet Breathing",
  "aerolarge.configuration.aurora": "Aurora",
  "aerolarge.configuration.burnerSmoke": "Burner Smoke",
  "commands.aerolarge.build": "Building an airliner of %s blocks, %s of them over blocks that were in the way",
  "message.aerolarge.autopilot.coming_down": "Coming down here, straight down to the ground",
  "message.aerolarge.autopilot.assembling_zeppelin": "Assembling the zeppelin: the autopilot takes it from here.",
  "message.aerolarge.autopilot.assembling_rocket": "Assembling the rocket: the countdown begins as soon as it is ready.",
  "message.aerolarge.autopilot.no_engines": "This rocket has no engines to fly it with: build it again with Create Propulsion installed.",
  "message.aerolarge.autopilot.coming_down_rocket": "Coming down here, catching ourselves on the engines",
  "message.aerolarge.autopilot.no_burners": "This zeppelin has no burners to lift it: build it again with Create Aeronautics installed.",
  "message.aerolarge.autopilot.too_heavy": "The zeppelin is too heavy for its burners to lift, and stays on the ground.",
  "commands.aerolarge.build_zeppelin": "Building a zeppelin of %s blocks, %s of them over blocks that were in the way",
  "commands.aerolarge.build_rocket": "Building a rocket of %s blocks, %s of them over blocks that were in the way",
  "item.aerolarge.airliner_blueprint": "Airliner Blueprint",
  "item.aerolarge.furnished_airliner_blueprint": "Furnished Airliner Blueprint",
  "item.aerolarge.airliner_blueprint.tooltip": "A double-deck airliner with an empty cabin, ready to build in",
  "item.aerolarge.furnished_airliner_blueprint.tooltip": "A double-deck airliner fitted with a pool, a gym hall and a lounge",
  "item.aerolarge.zeppelin_blueprint": "Zeppelin Blueprint",
  "item.aerolarge.rocket_blueprint": "Rocket Blueprint",
  "item.aerolarge.rocket_blueprint.tooltip": "A rocket as tall as the airliner is long, flown to a pin on the map and landed on its fins",
  "message.aerolarge.rocket_building": "Building the rocket… %s%%",
  "message.aerolarge.rocket_built": "The rocket is ready. Use the autopilot in its cockpit, at the top, to fly it.",
  "message.aerolarge.rocket_too_high": "The rocket would reach past the build height here; build it lower down.",
  "item.aerolarge.zeppelin_blueprint.tooltip": "A Hindenburg-sized zeppelin, 490 blocks long, lifted by sixteen gas cells of hot air",
  "item.aerolarge.furnished_zeppelin_blueprint": "Furnished Zeppelin Blueprint",
  "item.aerolarge.furnished_zeppelin_blueprint.tooltip": "The same zeppelin with cabins, a galley, a dining room, a lounge and its promenades furnished",
  "item.aerolarge.festive_zeppelin_blueprint": "Festive Zeppelin Blueprint",
  "item.aerolarge.festive_zeppelin_blueprint.tooltip": "The furnished zeppelin dressed for Christmas: snow, red bands, green fins, fairy lights, a wreath, a star and sleigh bells",
  "item.aerolarge.half_zeppelin_blueprint": "Half-Size Zeppelin Blueprint",
  "item.aerolarge.half_zeppelin_blueprint.tooltip": "A zeppelin half the Hindenburg's size, 245 blocks long, its control car and decks as big as ever",
  "item.aerolarge.half_furnished_zeppelin_blueprint": "Half-Size Furnished Zeppelin Blueprint",
  "item.aerolarge.half_furnished_zeppelin_blueprint.tooltip": "The half-size zeppelin with its passenger decks furnished",
  "item.aerolarge.half_festive_zeppelin_blueprint": "Half-Size Festive Zeppelin Blueprint",
  "item.aerolarge.half_festive_zeppelin_blueprint.tooltip": "The half-size furnished zeppelin dressed for Christmas",
  "item.aerolarge.airliner_blueprint.usage": "Use on the ground: the tail stretches away the way you face. Sneak to build through anything in the way.",
  "item.aerolarge.control_tower_blueprint": "Control Tower Blueprint",
  "item.aerolarge.control_tower_blueprint.tooltip": "An airfield's control tower: a glass cab with a radio, a radar and a transponder",
  "item.aerolarge.control_tower_blueprint.usage": "Use on the ground: the tower goes up ahead of you, its door toward you. Sneak to build through anything in the way.",
  "message.aerolarge.control_tower.built": "The control tower is up. Take the lift or the stairs up to the cab: its transponder shows it on every autopilot's map.",
  "item.aerolarge.runway_blueprint": "Runway Blueprint",
  "item.aerolarge.runway_blueprint.tooltip": "A lit runway for the airliner, twice as long as it needs to take off",
  "item.aerolarge.runway_blueprint.usage": "Use on the ground: the runway runs away the way you face, replacing whatever is there.",
  "message.aerolarge.runway.building": "Laying the runway: %s%%",
  "message.aerolarge.runway.busy": "A runway is already being laid; wait for it to finish.",
  "message.aerolarge.runway.built": "The runway is down: %s blocks long and %s wide. To line an airliner up on it, use its blueprint at the far end, looking back down the runway.",
  "block.aerolarge.transponder": "Transponder",
  "block.aerolarge.tower_radar": "Tower Radar",
  "block.aerolarge.motion_sensor": "Motion Sensor",
  "block.aerolarge.lift_panel": "Lift Panel",
  "screen.aerolarge.lift_panel.none": "NO LIFT",
  "screen.aerolarge.radar.title": "Tower Radar",
  "screen.aerolarge.radar.map": "Radar map",
  "screen.aerolarge.map.contact.where": "X %s, Z %s",
  "screen.aerolarge.map.lookout_flag": "Spotted by %s",
  "screen.aerolarge.map.contact.distance": "%s blocks away",
  "screen.aerolarge.map.contact.flight": "Height %s, speed %s blocks a second",
  "marker.aerolarge.icon.tower": "Control tower",
  "marker.aerolarge.icon.airliner": "Airliner",
  "marker.aerolarge.icon.house": "Home",
  "marker.aerolarge.icon.star": "Star",
  "marker.aerolarge.icon.flag": "Flag",
  "marker.aerolarge.icon.heart": "Heart",
  "marker.aerolarge.icon.gem": "Treasure",
  "marker.aerolarge.icon.tree": "Forest",
  "marker.aerolarge.icon.anchor": "Harbour",
  "marker.aerolarge.icon.pickaxe": "Mine",
  "marker.aerolarge.icon.airship": "Airship",
  "marker.aerolarge.colour.white": "White",
  "marker.aerolarge.colour.red": "Red",
  "marker.aerolarge.colour.orange": "Orange",
  "marker.aerolarge.colour.yellow": "Yellow",
  "marker.aerolarge.colour.lime": "Lime",
  "marker.aerolarge.colour.green": "Green",
  "marker.aerolarge.colour.cyan": "Cyan",
  "marker.aerolarge.colour.blue": "Blue",
  "marker.aerolarge.colour.purple": "Purple",
  "marker.aerolarge.colour.magenta": "Magenta",
  "marker.aerolarge.colour.pink": "Pink",
  "marker.aerolarge.colour.brown": "Brown",
  "message.aerolarge.building": "Building the airliner… %s%%",
  "message.aerolarge.built": "The airliner is ready and glued together. Fold the airstairs away, then use the Physics Assembler in the cockpit to fly it.",
  "message.aerolarge.busy": "An airliner or a zeppelin is already being built here; wait for it to finish.",
  "message.aerolarge.too_high": "The airliner would reach past the build height here; build it lower down.",
  "message.aerolarge.blocked": "Not enough room: %s blocks are in the way, the first at %s %s %s. Sneak to build anyway.",
  "aerolarge.configuration.airframe": "Airframe",
  "aerolarge.configuration.scale": "Scale",
  "aerolarge.configuration.blocksPerTick": "Blocks per Tick",
  "aerolarge.configuration.shellStyle": "Shell Style",
  "aerolarge.configuration.flight": "Flight",
  "aerolarge.configuration.wingBlock": "Wing Block",
  "aerolarge.configuration.engineBlock": "Engine Block",
  "aerolarge.configuration.thrustSetting": "Thrust Setting",
  "aerolarge.configuration.assemblerBlock": "Assembler Block",
  "aerolarge.configuration.wheelMountBlock": "Wheel Mount Block",
  "aerolarge.configuration.tireItem": "Tire Item",
  "aerolarge.configuration.landAhead": "Land Ahead",
  "aerolarge.configuration.landAheadChunks": "Land Ahead Distance",
  "aerolarge.configuration.cabin": "Cabin",
  "aerolarge.configuration.turbulence": "Turbulence",
  "aerolarge.configuration.decompression": "Decompression",
  "aerolarge.configuration.villagerPassengers": "Villager Passengers",
  ...DUTY_FREE_LANG,
  ...cabinDecorLang,
  ...holdAndTailLang(),
  ...VACUUM_TOILET_LANG,
  ...plugDoorLang(),
  ...cockpitDoorLang(),
  ...airstairLang(),
  ...surfacePanelLang(),
  ...slideLang(),
  ...flightDeckLang(),
  ...rubberDuckLang(),
  ...coffeeMachineLang(),
  ...cabinStairsLang(),
  ...crewLockerLang(),
  ...gearLang(),
  ...airPocketLang(),
  ...laserLang(),
  ...selfDestructLang(),
  ...hullLiningLang(),
  ...cabinSeatLang(),
  ...cabinCarpetLang(),
  ...gymFloorLang(),
  ...lavatoryBasinLang(),
  ...bulkheadSignLang(),
  ...wcSignLang(),
  ...denFurnitureLang(),
  ...airstairButtonLang(),
  ...doorButtonLang(),
  ...cargoNetLang(),
  ...cargoFloorLang(),
  ...uldContainerLang(),
  ...poolLang(),
  ...gymHallLang(),
  ...seatConsoleLang(),
  ...galleyUnitLang(),
  ...planterLang(),
  ...barLang(),
  ...dutyFreeKioskLang(),
  ...laserPointerLang(),
  ...towerInstrumentsLang(),
  ...zeppelinLang(),
  ...zeppelinControlsLang(),
  ...ledStripLang(),
  ...shipsColoursLang(),
  ...christeningLang(),
  ...flankScreenLang(),
  ...spacePosterLang(),
  ...roverLang(),
  ...labLang(),
  ...Object.fromEntries(FIRST_FLIGHT.flatMap(([name, , , , title, description]) => [
    [`advancements.${ID}.first_flight.${name}.title`, title],
    [`advancements.${ID}.first_flight.${name}.description`, description],
  ])),
});
console.log("ok");
