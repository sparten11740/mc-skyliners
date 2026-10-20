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
};
// Cubes whose blockstate is written with their other models below, and cubes with faces of their own, written below.
const OWN_BLOCKSTATES = new Set(["cabin_light"]);
const OWN_MODELS = new Set(["cabin_floor"]);
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
  write(`assets/${ID}/blockstates/cabin_light.json`, {
    variants: Object.fromEntries(["bright", "dim", "night"].flatMap((level) => [false, true].map((keptOn) =>
      [`brightness=${level},kept_on=${keptOn}`, { model: `${ID}:block/cabin_light${level === "night" && !keptOn ? "_night" : ""}` }]))),
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
// The plug door's pneumatic hiss and the clunk of its latches (see tools/generate_sounds.mjs).
function plugDoorSounds() {
  return Object.fromEntries(["plug_door_hiss", "plug_door_latch"].map((name) =>
    [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function plugDoorLang() {
  return {
    "block.aerolarge.plug_door": "Plug Door",
    "subtitles.aerolarge.plug_door_hiss": "Door hisses",
    "subtitles.aerolarge.plug_door_latch": "Door latches",
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
// baked by the mod's own "shell" loader from the block's corners. They give back the part they are cut from (the
// clear glass, plain glass). The relay hidden in the
// nacelles drops nothing.
for (const [name, skin, renderType, part] of [
  ["airframe_shell", "airframe_panel", "minecraft:solid", `${ID}:airframe_panel`],
  ["cockpit_glass", "cockpit_glass", "minecraft:translucent", `${ID}:cockpit_window`],
  ["hull_glass", "hull_glass", "minecraft:translucent", "minecraft:glass"],
]) {
  write(`assets/${ID}/blockstates/${name}.json`, { variants: { "": { model: `${ID}:block/${name}` } } });
  write(`assets/${ID}/models/block/${name}.json`, {
    loader: `${ID}:shell`,
    render_type: renderType,
    textures: { skin: `${ID}:block/${skin}`, particle: `${ID}:block/${skin}` },
  });
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: part }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/${name}`,
  });
  cubes[name] = null;
}
write(`assets/${ID}/blockstates/thrust_relay.json`, { variants: { "": { model: `${ID}:block/thrust_relay` } } });
write(`assets/${ID}/models/block/thrust_relay.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/thrust_relay` } });
cubes.thrust_relay = null;
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
  announcement_takeoff: "Announcement: Crew seated for take-off",
  announcement_landing: "Announcement: Crew seated for landing",
  announcement_arrival: "Announcement: Welcome to your destination",
};
const VOICE_LANG = Object.fromEntries(Object.entries(VOICE_SUBTITLES).map(([name, subtitle]) => [`subtitles.${ID}.${name}`, subtitle]));

// The engines' sound and the flight deck's voice (see tools/generate_sounds.mjs), and the contrail particle.
{
  const sounds = { engine: { subtitle: `subtitles.${ID}.engine`, sounds: [{ name: `${ID}:engine` }] } };
  for (const name of Object.keys(VOICE_SUBTITLES)) {
    sounds[name] = { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] };
  }
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
  write(`assets/${ID}/sounds.json`, sounds);
  write(`assets/${ID}/particles/contrail.json`, { textures: [`${ID}:contrail`] });
  // A flare's smoke is the same soft puff, tinted.
  write(`assets/${ID}/particles/flare_smoke.json`, { textures: [`${ID}:contrail`] });
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

// The exterior lights: a small lens on the face it is fixed to, pointing the way it shines (up in the model), one
// model per kind; turned like an end rod for the other directions.
{
  const kinds = ["red", "green", "white", "strobe", "beacon"];
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

// The instrument panel: screens on the front, plain panel elsewhere.
write(`assets/${ID}/models/block/instrument_panel.json`, {
  parent: "minecraft:block/orientable",
  textures: { front: `${ID}:block/instrument_panel`, side: `${ID}:block/panel_side`, top: `${ID}:block/panel_side` },
});
write(`assets/${ID}/models/item/instrument_panel.json`, { parent: `${ID}:block/instrument_panel` });
write(`assets/${ID}/blockstates/instrument_panel.json`, {
  variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/instrument_panel`, y }])),
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
    textures: { front: `${ID}:block/parachute_rack_front`, side: `${ID}:block/parachute_rack_side`, top: `${ID}:block/parachute_rack_side` },
  });
  write(`assets/${ID}/blockstates/parachute_rack.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/parachute_rack`, y }])),
  });
  write(`assets/${ID}/models/item/parachute_rack.json`, { parent: `${ID}:block/parachute_rack` });
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
  for (const name of ["jump_light", "jump_switch", "parachute_rack", "landing_target"]) {
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
  write(`assets/${ID}/blockstates/seatbelt_sign.json`, {
    variants: { "lit=false": { model: `${ID}:block/seatbelt_sign` }, "lit=true": { model: `${ID}:block/seatbelt_sign_on` } },
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
// and glow a deep blue at night. The overhead locker: a white bin against the wall, its door facing out, the passenger service
// panel underneath; open, its door shows the luggage inside.
{
  const brightness = { bright: ["", "light_strip", true], dim: ["_dim", "light_strip", false], night: ["_night", "light_strip_night", true] };
  for (const [suffix, glow, lit] of Object.values(brightness)) {
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
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => Object.entries(brightness).map(([level, [suffix]]) =>
      [`brightness=${level},facing=${facing}`, { model: `${ID}:block/light_strip${suffix}`, y }]))),
  });
  write(`assets/${ID}/blockstates/pendant_light.json`, {
    variants: Object.fromEntries(Object.entries(brightness).map(([level, [suffix]]) => [`brightness=${level}`, { model: `${ID}:block/pendant_light${suffix}` }])),
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

// Cabin service. The galley: stainless cupboards with an oven and a coffee machine. The trolley, drawn as its item's
// model in the world as in the hand: an aluminium cart with drawers at both ends, a red stripe and small dark wheels.
// The meals, pretzels and coffee: flat icons.
write(`assets/${ID}/models/block/galley.json`, {
  parent: "minecraft:block/orientable",
  textures: { front: `${ID}:block/galley_front`, side: `${ID}:block/galley_side`, top: `${ID}:block/galley_side` },
});
facing("galley");
write(`assets/${ID}/models/item/galley.json`, { parent: `${ID}:block/galley` });
dropsItself("galley");
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
for (const food of ["chicken_meal", "pasta_meal", "pretzels", "airline_coffee"]) {
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
// The den's furniture is fitted into it rather than built into the airliner: breakable, and no part of the airframe.
function denFurnitureFittings() {
  return ["bunk", "bean_bag"];
}
// A bunk: light blue wool on birch planks, with glowstone for the reading light. A bean bag: orange wool round a
// handful of cocoa beans.
function denFurnitureRecipes() {
  shaped("bunk", ["G  ", "WWW", "PPP"], { G: "minecraft:glowstone_dust", W: "minecraft:light_blue_wool", P: "minecraft:birch_planks" }, 1);
  shaped("bean_bag", ["WCW", "WWW"], { W: "minecraft:orange_wool", C: "minecraft:cocoa_beans" }, 1);
}
function denFurnitureLang() {
  return {
    "block.aerolarge.bunk": "Bunk",
    "block.aerolarge.bean_bag": "Bean Bag",
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
// foam, or the mocha's tall glass; while it pours, a stream of coffee runs down into it. One model for each step and
// drink (one for ready, with no cup), turned for each facing.
const COFFEE_STEPS = ["ready", "grinding", "pouring", "steaming", "done"];
const COFFEE_DRINKS = ["espresso", "cappuccino", "mocha"];
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = {
    steel: block("coffee_machine_steel"), black: block("coffee_machine_black"), front: block("coffee_machine_front"),
    top: block("coffee_machine_top"), bay: block("coffee_machine_bay"), tray: block("coffee_machine_tray"),
    chrome: block("coffee_machine_chrome"), hopper: block("coffee_machine_hopper"), lid: block("coffee_machine_lid"),
    cup: block("coffee_cup"), crema: block("coffee_crema"), foam: block("coffee_foam"), glass: block("mocha_glass"),
    cream: block("mocha_cream"), stream: block("coffee_stream"), particle: block("coffee_machine_steel"),
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
    for (const drink of step === "ready" ? [null] : COFFEE_DRINKS) {
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
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => COFFEE_STEPS.flatMap((step) => COFFEE_DRINKS.map((drink) =>
      [`brew=${step},drink=${drink},facing=${facing}`, { model: `${ID}:block/coffee_machine_${step === "ready" ? step : `${step}_${drink}`}`, y }])))),
  });
  write(`assets/${ID}/models/item/coffee_machine.json`, { parent: `${ID}:block/coffee_machine_ready` });
  dropsItself("coffee_machine");
  cubes.coffee_machine = null;
  for (const drink of COFFEE_DRINKS) {
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
  "item.aerolarge.pilot_helmet": "Pilot Helmet",
  "item.aerolarge.pilot_helmet.tooltip": "On the flight deck, its visor shows the head-up display",
  "subtitles.aerolarge.hud_on": "Pilot HUD beeps on",
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
// a pixel into both; then white panelling out to BAND; then the bulkhead's own cream panels. Each block is the pixels
// of each kind in it merged into boxes, showing a face only where a box meets the open archway or something set back
// further, so the arch's underside steps round its curve a pixel at a time. In the hand, a narrow archway whole.
{
  // How far out from the opening's edge the white reaches, in pixels: CabinArchBlock.BAND.
  const BAND = 6;
  // CabinArchBlock.Part: the jamb (the same for both sizes), then each size's arch blocks, by columns out from the
  // middle and rows over the opening, the opening reaching that many blocks either side of the middle.
  const SPANS = { narrow: 1, wide: 2 };
  const PARTS = ["jamb", "narrow_1_1", "narrow_2_1", "narrow_0_2", "narrow_1_2", "narrow_2_2",
    "wide_2_1", "wide_3_1", "wide_2_2", "wide_3_2", "wide_0_3", "wide_1_3", "wide_2_3"];
  const DEFAULT = "narrow_0_2";
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
  for (const part of PARTS) {
    const [size, out, over] = part.split("_");
    const span = SPANS[size] ?? 1;
    // The jamb is the column beside the opening, a row below its top.
    const [u0, v0] = part === "jamb" ? [radius(span), -16] : [16 * Number(out) - 8, 16 * (Number(over) - 1)];
    write(`assets/${ID}/models/block/cabin_arch_${part}.json`, { parent: "minecraft:block/block", textures, elements: elements(span, u0, v0) });
  }
  write(`assets/${ID}/blockstates/cabin_arch.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => PARTS.map((part) =>
      [`facing=${facing},part=${part}`, { model: `${ID}:block/cabin_arch_${part}`, ...(y ? { y } : {}) }]))),
  });
  // In the hand, a narrow archway whole, its jambs two rows tall, seen from the front: the wall a fifth of its size,
  // each kind's boxes over all of it in one flat colour of its texture.
  {
    const span = SPANS.narrow;
    const width = 16 * (2 * span + 3);
    const height = 32 + 16 * rows(span);
    const scale = 16 / width;
    const left = -width / 2;
    const bottom = -32;
    const lift = (16 - height * scale) / 2;
    const flat = [4, 4, 5, 5];
    const elements = merge(span, left, bottom, width, height).map((box) => {
      const { texture, inset } = KINDS[box.kind];
      const faces = { north: { texture, uv: flat }, south: { texture, uv: flat } };
      for (const side of ["out", "in", "up", "down"]) {
        if (exposed(span, left, bottom, box, side)) faces[{ out: "east", in: "west", up: "up", down: "down" }[side]] = { texture, uv: flat };
      }
      return {
        from: [box.u * scale, lift + box.v * scale, 8 - (8 - inset) * scale],
        to: [(box.u + box.w) * scale, lift + (box.v + box.h) * scale, 8 + (8 - inset) * scale],
        faces,
      };
    });
    write(`assets/${ID}/models/item/cabin_arch.json`, {
      parent: "minecraft:block/block", textures, elements,
      display: {
        gui: { rotation: [15, -25, 0], translation: [0, 0, 0], scale: [0.9, 0.9, 0.9] },
        ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.5, 0.5, 0.5] },
        fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [1, 1, 1] },
        thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.375, 0.375, 0.375] },
        firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
        firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, 0, 0], scale: [0.4, 0.4, 0.4] },
      },
    });
  }
  // A piece comes down as itself, kept as the block state to put it down as (as picking one does, see CabinArchBlock);
  // the piece put down when nothing says which is left off, so those stack with new ones.
  const arch = `${ID}:cabin_arch`;
  write(`data/${ID}/loot_table/blocks/cabin_arch.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0, bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item", name: arch,
        functions: [{
          function: "minecraft:copy_state", block: arch, properties: ["part"],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: arch, properties: { part: DEFAULT } } }],
        }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
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
{
  const block = (name) => `${ID}:block/${name}`;
  const bezel = (from, to) => solid(from, to, "#bezel");
  for (const picture of BULKHEAD_SIGN_PICTURES) {
    write(`assets/${ID}/models/block/bulkhead_sign_${picture}.json`, {
      parent: "minecraft:block/block",
      textures: { wall: block("cabin_partition"), bezel: block("bulkhead_sign_bezel"), picture: block(`bulkhead_sign_${picture}`), particle: block("cabin_partition") },
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
{
  const depth = 3;
  write(`assets/${ID}/models/block/tail_camera_display.json`, boxes(
    { screen: `${ID}:block/tail_camera_display`, casing: `${ID}:block/passenger_screen_casing`, particle: `${ID}:block/passenger_screen_casing` },
    [[[0, 0, 16 - depth], [16, 16, 16], { north: "#screen", all: "#casing" }]],
  ));
  const all = (texture) => ({ north: { texture }, south: { texture }, east: { texture }, west: { texture }, up: { texture }, down: { texture } });
  const steps = [1, 2, 3, 4, 5, 6, 7].map((k) => ({ from: [0, 2 * k, 16 - 2 * k], to: [16, 2 * k + 2, 16], faces: all("#casing") }));
  write(`assets/${ID}/models/block/tail_camera_display_tilted.json`, {
    parent: "minecraft:block/block",
    textures: { screen: `${ID}:block/tail_camera_display`, casing: `${ID}:block/passenger_screen_casing`, particle: `${ID}:block/passenger_screen_casing` },
    elements: [
      {
        from: [0, 7.75, 0], to: [16, 8.25, 16], rotation: { origin: [8, 8, 8], axis: "x", angle: 45, rescale: true },
        faces: { ...all("#casing"), down: { texture: "#screen" } },
      },
      ...steps,
    ],
  });
  write(`assets/${ID}/blockstates/tail_camera_display.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [0, 1, 2].flatMap((part) => [false, true].map((tilted) =>
      [`facing=${facing},part=${part},tilted=${tilted}`, { model: `${ID}:block/tail_camera_display${tilted ? "_tilted" : ""}`, y }])))),
  });
  write(`assets/${ID}/models/item/tail_camera_display.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/tail_camera_display` } });
  write(`data/${ID}/loot_table/blocks/tail_camera_display.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:tail_camera_display` }],
      conditions: [
        { condition: "minecraft:block_state_property", block: `${ID}:tail_camera_display`, properties: { part: "0" } },
        { condition: "minecraft:survives_explosion" },
      ],
    }],
    random_sequence: `${ID}:blocks/tail_camera_display`,
  });
  cubes.tail_camera_display = null;
}

// The autopilot's control head: a module of the centre console with a raised head on it, its panel (the AP badge
// and a row of mode lamps) facing the pilots, away from the nose it points with, and four knobs along its front edge.
consoleModule("autopilot", {
  plate: `${ID}:block/panel_side`, panel: `${ID}:block/autopilot`, side: `${ID}:block/panel_side`, knob: `${ID}:block/thrust_lever_knob`,
}, "#plate", true, [
  {
    from: [2, CONSOLE_HEIGHT, 3], to: [14, 7.5, 13],
    faces: {
      north: { texture: "#side" }, south: { texture: "#side" }, east: { texture: "#side" }, west: { texture: "#side" },
      up: { texture: "#panel", uv: [0, 0, 16, 16], rotation: 180 }, down: { texture: "#side" },
    },
  },
  ...[3.25, 6, 8.75, 11.5].map((x) => solid([x, 7.5, 3.75], [x + 1.25, 8.5, 5], "#knob")),
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
write(`assets/${ID}/models/item/thrust_lever.json`, { parent: `${ID}:block/thrust_lever_2` });
write(`assets/${ID}/blockstates/thrust_lever.json`, {
  variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
    [0, 1, 2, 3, 4].map((thrust) => [`facing=${facing},thrust=${thrust}`, { model: `${ID}:block/thrust_lever_${thrust}`, y }]))),
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
// shared lists further down take in. The seat is drawn facing north, the sitter's back to the south, in two halves.
// The lower one: the seat pan on a column, on a plate across two floor rails, chrome piping along its front edge,
// the back up to the shoulder blades with the harness's shoulder straps down it, and a padded armrest on a post either
// side. The upper one: the top of the back narrowing at the shoulders, the straps over them, and the headrest with a
// trim band along its top, right behind a sitter's head. In the hand both halves together, smaller. Only the lower
// half drops the seat, as with doors.
{
  const textures = {
    leather: `${ID}:block/pilot_seat_leather`, trim: `${ID}:block/pilot_seat_trim`, frame: `${ID}:block/pilot_seat_frame`,
    harness: `${ID}:block/pilot_seat_harness`, particle: `${ID}:block/pilot_seat_leather`,
  };
  const lower = [
    solid([3, 0, 0.5], [4.5, 1, 15.5], "#frame"),
    solid([11.5, 0, 0.5], [13, 1, 15.5], "#frame"),
    solid([3, 1, 4.5], [13, 2, 11.5], "#frame"),
    solid([5.5, 2, 5], [10.5, 4.5, 11], "#frame"),
    solid([2, 4.5, 1], [14, 8, 12], "#leather"),
    solid([2, 7.5, 0.5], [14, 8.25, 1], "#trim"),
    solid([2, 4.5, 11], [14, 16, 15], "#leather"),
    solid([4.5, 8.5, 10.6], [6, 16, 11], "#harness"),
    solid([10, 8.5, 10.6], [11.5, 16, 11], "#harness"),
    solid([0.5, 10.5, 3.5], [2, 12, 12], "#leather"),
    solid([14, 10.5, 3.5], [15.5, 12, 12], "#leather"),
    solid([1, 4.5, 9.5], [2, 10.5, 11], "#frame"),
    solid([14, 4.5, 9.5], [15, 10.5, 11], "#frame"),
  ];
  const upper = [
    solid([2, 0, 11], [14, 3, 15], "#leather"),
    solid([3, 3, 11.5], [13, 4.5, 15], "#leather"),
    solid([4.5, 0, 10.6], [6, 3.5, 11], "#harness"),
    solid([10, 0, 10.6], [11.5, 3.5, 11], "#harness"),
    solid([4.5, 3.5, 10.6], [6, 4.75, 13], "#harness"),
    solid([10, 3.5, 10.6], [11.5, 4.75, 13], "#harness"),
    solid([4, 4.5, 12], [12, 12.5, 15], "#leather"),
    solid([5, 6, 11.5], [11, 11.5, 12], "#leather"),
    solid([4, 12.5, 12], [12, 13, 15], "#trim"),
  ];
  for (const [half, elements] of [["lower", lower], ["upper", upper]]) {
    write(`assets/${ID}/models/block/pilot_seat_${half}.json`, { parent: "minecraft:block/block", textures, elements });
  }
  write(`assets/${ID}/blockstates/pilot_seat.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["lower", "upper"].flatMap((half) => [false, true].map((water) =>
      [`facing=${facing},half=${half},waterlogged=${water}`, { model: `${ID}:block/pilot_seat_${half}`, y }])))),
  });
  const raised = upper.map((element) => ({ ...element, from: [element.from[0], element.from[1] + 16, element.from[2]], to: [element.to[0], element.to[1] + 16, element.to[2]] }));
  write(`assets/${ID}/models/item/pilot_seat.json`, {
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

for (const name of ["airliner_blueprint", "furnished_airliner_blueprint"]) {
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

const blocks = [...Object.keys(cubes), "airliner_door"].map((name) => `${ID}:${name}`);
const tag = (values) => ({ replace: false, values });
write("data/minecraft/tags/block/mineable/pickaxe.json", tag(blocks.filter((b) => !b.endsWith("tire") && !b.endsWith("landing_target") && !b.endsWith("cabin_curtain"))));
// The care package is a wooden crate, and the gym floor is maple.
write("data/minecraft/tags/block/mineable/axe.json", tag([`${ID}:cargo_crate`, `${ID}:gym_floor`]));
write("data/minecraft/tags/block/doors.json", tag([`${ID}:airliner_door`]));
write("data/minecraft/tags/item/doors.json", tag([`${ID}:airliner_door`]));
// Villagers find their way through it like a wooden door; this lets them open it on the way to their seats too.
write("data/minecraft/tags/block/mob_interactable_doors.json", tag([`${ID}:airliner_door`]));
// Sable's physics: the whole airframe is aircraft-light, and the tires let it roll down a runway on their own.
const GROUND = ["landing_target", "tower_radio", "jet_bridge", "check_in_desk"];
const airliner = blocks.filter((b) => !GROUND.some((ground) => b.endsWith(`:${ground}`)));
// What is fitted into the cabin rather than built into the airliner: light like the rest, but not hull, and breakable.
const FITTINGS = ["light_strip", "pendant_light", "light_cable", "overhead_bin", "passenger_screen", "hoop", "scoreboard", "ball_rack", "galley",
  "model_airliner", "duty_free_counter", "springboard", "starlight_panel", "gym_rings", "hide_and_seek_button", ...ROOM_DIVIDERS, "vacuum_toilet"];
FITTINGS.push(...holdAndTailFittings());
FITTINGS.push(...airstairFittings());
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
FITTINGS.push(...denFurnitureFittings());
const structure = airliner.filter((b) => !FITTINGS.some((fitting) => b.endsWith(`:${fitting}`)));
write("data/sable/tags/block/super_light.json", tag(airliner));
// What counts as the inside of an airliner for the spawn guard: everything but the gear, which stands on open ground.
write(`data/${ID}/tags/block/airframe.json`, tag(structure.filter((b) => !b.includes("landing_gear") && !b.includes("call_button"))));
// What cannot be broken, except by a player sneaking with a wrench or in creative mode: the airliner's frame and
// controls, its own blocks and the other mods' it is built with, but not the cabin floor, where the building goes.
write(`data/${ID}/tags/block/unbreakable.json`, tag([
  ...structure.filter((b) => !b.endsWith("cabin_floor")),
  ...["createpropulsion:wing", "createpropulsion:creative_thruster", "createpropulsion:thruster", "offroad:wheel_mount",
    "simulated:physics_assembler"].map((id) => ({ id, required: false })),
]));
write("data/sable/tags/block/slippery.json", tag([`${ID}:landing_gear_tire`]));

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
shaped("landing_gear_tire", ["KKK", "KSK", "KKK"], { K: "minecraft:black_wool", S: "minecraft:iron_ingot" }, 4);
// Duty free and the paper plane: a counter of glass, emeralds and a chest on white concrete; a sheet of paper folds
// into a plane; a cap in the shape of a helmet, with a gold badge; a model airliner, its fuselage and wings of airframe
// panel on a stick.
shaped("duty_free_counter", ["GGG", "ECE", "WWW"], { G: "minecraft:glass", E: "minecraft:emerald", C: "minecraft:chest", W: "minecraft:white_concrete" }, 1);
shaped("paper_plane", ["P"], { P: "minecraft:paper" }, 1);
shaped("pilot_cap", ["WGW", "W W"], { W: "minecraft:black_wool", G: "minecraft:gold_nugget" }, 1);
shaped("model_airliner", [" P ", "PPP", " S "], { P: `${ID}:airframe_panel`, S: "minecraft:stick" }, 1);
// The pilot's helmet: an iron shell, a dark glass visor across its middle and black wool ear cups under it.
shaped("pilot_helmet", ["III", "IGI", "W W"], { I: "minecraft:iron_ingot", G: "minecraft:black_stained_glass_pane", W: "minecraft:black_wool" }, 1);
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
slideRecipes();
cabinSeatRecipes();
cabinCarpetRecipes();
gymFloorRecipes();
lavatoryBasinRecipes();
bulkheadSignRecipes();
denFurnitureRecipes();

// The First Flight advancements. The first opens the tab on joining a world; each of the rest has one criterion,
// "done", that only the mod grants, when it happens (see FirstFlight).
const FIRST_FLIGHT = [
  // name, parent, icon, frame, title, description
  ["root", null, "airliner_blueprint", "task", "First Flight", "Airliners, from the runway to the jet stream"],
  ["cleared_for_takeoff", "root", "thrust_lever", "task", "Cleared for Takeoff", "Be aboard an airliner as it climbs away from the runway"],
  ["buckle_up", "root", "seatbelt_sign", "task", "Buckle Up", "Try to get up from your seat while the seatbelt signs are on"],
  ["butter_landing", "cleared_for_takeoff", "landing_gear_tire", "challenge", "Butter Landing", "Be aboard an airliner touching down so gently you hardly feel it"],
  ["swish", "cleared_for_takeoff", "basketball", "challenge", "Swish at 30,000 Feet", "Score a basket aboard an airliner in flight"],
  ["perfect_jump", "cleared_for_takeoff", "parachute", "goal", "Perfect Jump", "Land your parachute within a block of a landing target"],
  ["special_delivery", "cleared_for_takeoff", "cargo_crate", "goal", "Special Delivery", "Drop a care package within 10 blocks of a burning signal flare"],
  ["frequent_flyer", "cleared_for_takeoff", "boarding_pass", "task", "Frequent Flyer", "Collect a villager passenger's fare"],
  ["air_mail", "cleared_for_takeoff", "paper_plane", "goal", "Air Mail", "Throw a paper plane out of an airliner in flight and see it come down 100 blocks away"],
  ["duty_free", "frequent_flyer", "duty_free_counter", "task", "Duty Free", "Spend emeralds at a duty-free counter"],
  ["cannonball", "cleared_for_takeoff", "springboard", "goal", "Cannonball", "Dive into the moon pool off its springboard or its tower while the airliner flies"],
  ["blue_ice", "cleared_for_takeoff", "vacuum_toilet", "goal", "Blue Ice", "Flush an airliner's toilet in flight and drop blue ice on the world below"],
];
for (const [name, parent, icon, frame] of FIRST_FLIGHT) {
  write(`data/${ID}/advancement/first_flight/${name}.json`, {
    ...(parent ? { parent: `${ID}:first_flight/${parent}` } : {}),
    display: {
      icon: { id: `${ID}:${icon}` },
      title: { translate: `advancements.${ID}.first_flight.${name}.title` },
      description: { translate: `advancements.${ID}.first_flight.${name}.description` },
      frame,
      ...(parent ? {} : { background: `${ID}:textures/block/airframe_panel.png` }),
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
  "block.aerolarge.cabin_window_shell": "Curved Cabin Window",
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
  "message.aerolarge.autopilot.engaged": "Autopilot engaged: take-off, then climbing to altitude %s at %s b/s, %s.",
  "message.aerolarge.autopilot.off": "Autopilot off: the thrust levers, tiller and parking brake have the airliner again.",
  "message.aerolarge.autopilot.not_assembled": "The autopilot only flies an assembled airliner: fold the airstairs away and use the Physics Assembler first.",
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
  "message.aerolarge.autopilot.landing": "Autopilot landing: down ahead on heading %s, slowing until the nose sits level; once down it brakes to a stop and parks.",
  "message.aerolarge.autopilot.on_ground": "The autopilot lands from the air: the airliner is on the ground already.",
  "message.aerolarge.autopilot.rolling_out": "The autopilot is braking to a stop after landing; disengage it to take over.",
  "message.aerolarge.autopilot.landed": "Landed and stopped: thrust levers at idle, parking brake set, autopilot off.",
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
  "message.aerolarge.parachute_rack.wearing": "You are already wearing a parachute.",
  "message.aerolarge.decompression": "Door open! Explosive decompression: keep clear of the door, or stay in your seat.",
  "message.aerolarge.landing_target.landed": "%s landed %s blocks from the target",
  "message.aerolarge.landing_target.record": "%s landed %s blocks from the target: a new best!",
  "message.aerolarge.landing_target.best": "Best landing: %s, %s blocks from the middle",
  "message.aerolarge.landing_target.none": "No landings on this target yet",
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
  "message.aerolarge.drop_bay.loaded": "Drop bay: %s of %s loaded",
  "message.aerolarge.drop_bay.full": "The drop bay is full",
  "message.aerolarge.drop_bay.empty": "The drop bay is empty",
  "message.aerolarge.drop_bay.package_away": "Package away! %s left in the bay",
  "message.aerolarge.drop_bay.bomb_away": "Bomb away! %s left in the bay",
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
  "screen.aerolarge.passenger_screen.camera_hint": "%s: next camera    %s: back to the cabin",
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
  "message.aerolarge.unbreakable": "The airliner's frame and controls cannot be broken. Sneak holding a wrench to take them apart.",
  "block.aerolarge.tail_camera_display": "Tail Camera Display",
  "screen.aerolarge.tail_camera_display.no_signal": "NO SIGNAL",
  "screen.aerolarge.tail_camera_display.standby": "ASSEMBLE TO FLY",
  "screen.aerolarge.tail_camera_display.camera": "CAM %s",
  "block.aerolarge.autopilot_screen": "Autopilot Screen",
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
  "screen.aerolarge.autopilot.targets": "ALT %s  SPD %s",
  "screen.aerolarge.autopilot.targets.land": "TOUCHDOWN  SPD %s",
  "screen.aerolarge.autopilot.targets.rollout": "BRAKING",
  "screen.aerolarge.autopilot.now": "SPD %s  ALT %s  HDG %s",
  "screen.aerolarge.autopilot.range": "%s blocks",
  "screen.aerolarge.pilot_hud.off": "AP OFF",
  "screen.aerolarge.autopilot_settings.title": "Autopilot",
  "screen.aerolarge.autopilot_settings.now": "Flying",
  "screen.aerolarge.autopilot_settings.set": "Set to",
  "screen.aerolarge.autopilot_settings.take_off_hint": "Takes off, then climbs to fly the settings below.",
  "screen.aerolarge.autopilot_settings.engage_hint": "Engaging flies the settings below.",
  "screen.aerolarge.autopilot_settings.mode": "Mode",
  "screen.aerolarge.autopilot_settings.mode.heading": "Hold Heading",
  "screen.aerolarge.autopilot_settings.mode.orbit_left": "Circle Left",
  "screen.aerolarge.autopilot_settings.mode.orbit_right": "Circle Right",
  "screen.aerolarge.autopilot_settings.heading": "Heading",
  "screen.aerolarge.autopilot_settings.altitude": "Altitude",
  "screen.aerolarge.autopilot_settings.speed": "Speed",
  "screen.aerolarge.autopilot_settings.radius": "Circle Radius",
  "screen.aerolarge.autopilot_settings.range.heading": "%s–%s°",
  "screen.aerolarge.autopilot_settings.range.altitude": "Y %s–%s",
  "screen.aerolarge.autopilot_settings.range.speed": "%s–%s b/s",
  "screen.aerolarge.autopilot_settings.range.radius": "%s–%s",
  "screen.aerolarge.autopilot_settings.down": "-%s",
  "screen.aerolarge.autopilot_settings.up": "+%s",
  "screen.aerolarge.autopilot_settings.take_off": "Take Off",
  "screen.aerolarge.autopilot_settings.engage": "Engage",
  "screen.aerolarge.autopilot_settings.apply": "Apply",
  "screen.aerolarge.autopilot_settings.disengage": "Disengage",
  "screen.aerolarge.autopilot_settings.land": "Land",
  "aerolarge.configuration.autopilot": "Autopilot",
  "aerolarge.configuration.climbHeight": "Climb Height",
  "aerolarge.configuration.rotateSpeed": "Rotate Speed",
  "aerolarge.configuration.cruiseSpeed": "Cruise Speed",
  "aerolarge.configuration.cameraShake": "Camera Shake",
  "aerolarge.configuration.tailCameraFeed": "Tail Camera Picture",
  "aerolarge.configuration.tailCameraFps": "Tail Camera Pictures a Second",
  "aerolarge.configuration.tailCameraResolution": "Tail Camera Picture Height",
  "aerolarge.configuration.pilotHud": "Pilot HUD",
  "aerolarge.configuration.pilotHudScale": "Pilot HUD Size",
  "aerolarge.configuration.nightLights": "Blue Night Lights",
  "aerolarge.configuration.cloudlessCabin": "Cloudless Cabin",
  "commands.aerolarge.build": "Building an airliner of %s blocks, %s of them over blocks that were in the way",
  "item.aerolarge.airliner_blueprint": "Airliner Blueprint",
  "item.aerolarge.furnished_airliner_blueprint": "Furnished Airliner Blueprint",
  "item.aerolarge.airliner_blueprint.tooltip": "A double-deck airliner with an empty cabin, ready to build in",
  "item.aerolarge.furnished_airliner_blueprint.tooltip": "A double-deck airliner fitted with a pool, a gym hall and a lounge",
  "item.aerolarge.airliner_blueprint.usage": "Use on the ground: the tail stretches away the way you face. Sneak to build through anything in the way.",
  "message.aerolarge.building": "Building the airliner… %s%%",
  "message.aerolarge.built": "The airliner is ready and glued together. Fold the airstairs away, then use the Physics Assembler in the cockpit to fly it.",
  "message.aerolarge.busy": "An airliner is already being built here; wait for it to finish.",
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
  "aerolarge.configuration.cabin": "Cabin",
  "aerolarge.configuration.turbulence": "Turbulence",
  "aerolarge.configuration.decompression": "Decompression",
  "aerolarge.configuration.villagerPassengers": "Villager Passengers",
  ...DUTY_FREE_LANG,
  ...cabinDecorLang,
  ...holdAndTailLang(),
  ...VACUUM_TOILET_LANG,
  ...plugDoorLang(),
  ...airstairLang(),
  ...slideLang(),
  ...flightDeckLang(),
  ...rubberDuckLang(),
  ...coffeeMachineLang(),
  ...cabinStairsLang(),
  ...crewLockerLang(),
  ...cabinSeatLang(),
  ...cabinCarpetLang(),
  ...gymFloorLang(),
  ...lavatoryBasinLang(),
  ...bulkheadSignLang(),
  ...denFurnitureLang(),
  ...Object.fromEntries(FIRST_FLIGHT.flatMap(([name, , , , title, description]) => [
    [`advancements.${ID}.first_flight.${name}.title`, title],
    [`advancements.${ID}.first_flight.${name}.description`, description],
  ])),
});
console.log("ok");
