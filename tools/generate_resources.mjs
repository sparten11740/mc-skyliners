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
for (const [name, renderType] of Object.entries(cubes)) {
  write(`assets/${ID}/blockstates/${name}.json`, { variants: { "": { model: `${ID}:block/${name}` } } });
  const model = { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/${name}` } };
  if (renderType) model.render_type = renderType;
  write(`assets/${ID}/models/block/${name}.json`, model);
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

// The engines' sound and the callouts (see tools/generate_sounds.mjs), and the contrail particle.
{
  const callouts = ["v1", "rotate", "positive_rate", "100", "50", "40", "30", "20", "10", "retard", "bank_angle", "pull_up"];
  const sounds = { engine: { subtitle: `subtitles.${ID}.engine`, sounds: [{ name: `${ID}:engine` }] } };
  for (const callout of callouts) {
    sounds[`callout_${callout}`] = { subtitle: `subtitles.${ID}.callout`, sounds: [{ name: `${ID}:callout_${callout}` }] };
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
  write(`assets/${ID}/sounds.json`, sounds);
  write(`assets/${ID}/particles/contrail.json`, { textures: [`${ID}:contrail`] });
  // A flare's smoke is the same soft puff, tinted.
  write(`assets/${ID}/particles/flare_smoke.json`, { textures: [`${ID}:contrail`] });
}

// The tiller (a handle on a pedestal, swung left or right through its five notches) and the parking brake (a lever
// up when set, down when released); both drawn nose north and turned for the other ways.
{
  const all = (texture) => ({ north: { texture }, south: { texture }, east: { texture }, west: { texture }, up: { texture }, down: { texture } });
  const textures = { base: `${ID}:block/thrust_lever_base`, handle: `${ID}:block/thrust_lever_handle`, knob: `${ID}:block/thrust_lever_knob`, particle: `${ID}:block/thrust_lever_base` };
  for (let steer = 0; steer <= 4; steer++) {
    const rotation = { origin: [8, 8, 8], axis: "y", angle: 45 - 22.5 * steer };
    write(`assets/${ID}/models/block/tiller_${steer}.json`, {
      parent: "minecraft:block/block",
      textures,
      elements: [
        { from: [4, 0, 4], to: [12, 7, 12], faces: all("#base") },
        { from: [7, 7, 3], to: [9, 9, 9], rotation, faces: all("#handle") },
        { from: [6.5, 7, 2], to: [9.5, 10, 4], rotation, faces: all("#knob") },
      ],
    });
  }
  for (const [set, angle] of [[true, -22.5], [false, 45]]) {
    const rotation = { origin: [8, 6, 8], axis: "x", angle };
    write(`assets/${ID}/models/block/parking_brake_${set ? "set" : "released"}.json`, {
      parent: "minecraft:block/block",
      textures,
      elements: [
        { from: [5, 0, 5], to: [11, 6, 11], faces: all("#base") },
        { from: [7, 6, 7], to: [9, 14, 9], rotation, faces: all("#handle") },
        { from: [6, 13, 6], to: [10, 15, 10], rotation, faces: all("#knob") },
      ],
    });
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

// Skydiving: the jump light (a lamp, dark, red or green), the jump switch in the panel (its face lit the same way,
// facing the pilots), the rack of parachutes by the doors, the landing target (a flat bullseye) and the parachute.
{
  const loot = (name) => write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:${name}` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/${name}`,
  });
  const signals = ["off", "red", "green"];
  for (const signal of signals) {
    write(`assets/${ID}/models/block/jump_light_${signal}.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/jump_light_${signal}` } });
    write(`assets/${ID}/models/block/jump_switch_${signal}.json`, {
      parent: "minecraft:block/orientable",
      textures: { front: `${ID}:block/jump_switch_${signal}`, side: `${ID}:block/panel_side`, top: `${ID}:block/panel_side` },
    });
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

// Speaking to the cabin and the tower. The handset: a dark cradle with the receiver lying in it, its earpiece and
// mouthpiece raised at either end, drawn facing north. The tower radio: a radio set with a speaker grille and dials.
{
  write(`assets/${ID}/models/block/handset.json`, boxes(
    { cradle: `${ID}:block/handset_cradle`, receiver: `${ID}:block/handset_receiver`, particle: `${ID}:block/handset_cradle` },
    [
      [[3, 0, 4], [13, 2, 12], "#cradle"],
      [[5, 2, 7], [11, 3, 9], "#receiver"],
      [[3, 2, 6], [5, 5, 10], "#receiver"],
      [[11, 2, 6], [13, 5, 10], "#receiver"],
    ],
  ));
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
// sign that shows in a dark cabin without lighting it up. The seatbelt switch in the panel, its lamp dark or lit,
// facing the pilots.
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
    write(`assets/${ID}/models/block/seatbelt_switch${lit ? "_on" : ""}.json`, {
      parent: "minecraft:block/orientable",
      textures: { front: `${ID}:block/seatbelt_switch${lit ? "_on" : ""}`, side: `${ID}:block/panel_side`, top: `${ID}:block/panel_side` },
    });
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
// the drop release, a small module like the cabin controls, its guarded switch safe or armed.
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
  write(`assets/${ID}/models/block/${name}.json`, boxes(
    { face: `${ID}:block/${name}`, side: `${ID}:block/handset_cradle`, particle: `${ID}:block/handset_cradle` },
    [[[3, 0, 4], [13, 4, 12], { north: "#face", up: "#face", all: "#side" }]],
  ));
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
};

// The cabin controls: small modules on the panel, drawn facing north, their face and top showing the setting. The
// cabin lights' dimmer: bright, dim or night. The window shades' control: open or closed.
{
  const module = (name) => write(`assets/${ID}/models/block/${name}.json`, boxes(
    { face: `${ID}:block/${name}`, side: `${ID}:block/handset_cradle`, particle: `${ID}:block/handset_cradle` },
    [[[3, 0, 4], [13, 4, 12], { north: "#face", up: "#face", all: "#side" }]],
  ));
  for (const level of ["bright", "dim", "night"]) {
    module(`cabin_lights_${level}`);
  }
  write(`assets/${ID}/blockstates/cabin_lights.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["bright", "dim", "night"].map((level) =>
      [`brightness=${level},facing=${facing}`, { model: `${ID}:block/cabin_lights_${level}`, y }]))),
  });
  write(`assets/${ID}/models/item/cabin_lights.json`, { parent: `${ID}:block/cabin_lights_bright` });
  for (const shaded of [false, true]) {
    module(`window_shades_${shaded ? "closed" : "open"}`);
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
const ROOM_DIVIDERS = ["cabin_curtain", "cabin_partition", "exit_sign"];
const ROOM_DIVIDER_LANG = {
  "block.aerolarge.cabin_curtain": "Cabin Curtain",
  "block.aerolarge.cabin_partition": "Cabin Partition",
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
    if (name !== "cabin_curtain") {
      dropsItself(name);
    }
    cubes[name] = null;
  }
}
// The exit sign: a thin plate with its back to the wall, drawn facing north, glowing whatever the light around.
write(`assets/${ID}/models/block/exit_sign.json`, boxes(
  { face: `${ID}:block/exit_sign`, casing: `${ID}:block/cabin_partition`, particle: `${ID}:block/exit_sign` },
  [[[1, 4, 14.5], [15, 12, 16], { north: "#face", all: "#casing" }, true]],
));
facing("exit_sign");
write(`assets/${ID}/models/item/exit_sign.json`, { parent: `${ID}:block/exit_sign` });

// The autopilot screen: dark panels, drawn over live by its renderer from the block holding it; facing the pilots,
// one state per part. It drops nothing: only the blueprints place it.
write(`assets/${ID}/models/block/autopilot_screen.json`, {
  parent: "minecraft:block/orientable",
  textures: { front: `${ID}:block/panel_side`, side: `${ID}:block/panel_side`, top: `${ID}:block/panel_side` },
});
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

// The autopilot: a panel block whose screen faces the pilots, away from the nose it points with.
write(`assets/${ID}/models/block/autopilot.json`, {
  parent: "minecraft:block/orientable",
  textures: { front: `${ID}:block/autopilot`, side: `${ID}:block/panel_side`, top: `${ID}:block/panel_side` },
});
write(`assets/${ID}/models/item/autopilot.json`, { parent: `${ID}:block/autopilot` });
write(`assets/${ID}/blockstates/autopilot.json`, {
  variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/autopilot`, y: (y + 180) % 360 }])),
});
cubes.autopilot = null;

// The thrust lever: a pedestal and a handle swung from fully back (idle) to fully forward (full thrust) over its
// five notches, the nose to the north.
for (let thrust = 0; thrust <= 4; thrust++) {
  const rotation = { origin: [8, 8, 8], axis: "x", angle: 45 - 22.5 * thrust };
  const box = (from, to, texture, rotated) => {
    const face = { texture };
    const element = { from, to, faces: { north: face, south: face, east: face, west: face, up: face, down: face } };
    if (rotated) element.rotation = rotation;
    return element;
  };
  write(`assets/${ID}/models/block/thrust_lever_${thrust}.json`, {
    parent: "minecraft:block/block",
    textures: { base: `${ID}:block/thrust_lever_base`, handle: `${ID}:block/thrust_lever_handle`, knob: `${ID}:block/thrust_lever_knob`, particle: `${ID}:block/thrust_lever_base` },
    elements: [
      box([3, 0, 2], [13, 8, 14], "#base", false),
      box([7.25, 8, 7.25], [8.75, 17, 8.75], "#handle", true),
      box([6, 15, 6.5], [10, 18, 9.5], "#knob", true),
    ],
  });
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

const blocks = [...Object.keys(cubes), "airliner_door"].map((name) => `${ID}:${name}`);
const tag = (values) => ({ replace: false, values });
write("data/minecraft/tags/block/mineable/pickaxe.json", tag(blocks.filter((b) => !b.endsWith("tire") && !b.endsWith("landing_target") && !b.endsWith("cabin_curtain"))));
// The care package is a wooden crate.
write("data/minecraft/tags/block/mineable/axe.json", tag([`${ID}:cargo_crate`]));
write("data/minecraft/tags/block/doors.json", tag([`${ID}:airliner_door`]));
write("data/minecraft/tags/item/doors.json", tag([`${ID}:airliner_door`]));
// Villagers find their way through it like a wooden door; this lets them open it on the way to their seats too.
write("data/minecraft/tags/block/mob_interactable_doors.json", tag([`${ID}:airliner_door`]));
// Sable's physics: the whole airframe is aircraft-light, and the tires let it roll down a runway on their own.
const GROUND = ["landing_target", "tower_radio", "jet_bridge", "check_in_desk"];
const airliner = blocks.filter((b) => !GROUND.some((ground) => b.endsWith(`:${ground}`)));
// What is fitted into the cabin rather than built into the airliner: light like the rest, but not hull, and breakable.
const FITTINGS = ["light_strip", "pendant_light", "light_cable", "overhead_bin", "passenger_screen", "hoop", "scoreboard", "ball_rack", "galley",
  "model_airliner", "duty_free_counter", "springboard", "starlight_panel", "gym_rings", "hide_and_seek_button", ...ROOM_DIVIDERS];
FITTINGS.push(...holdAndTailFittings());
const structure = airliner.filter((b) => !FITTINGS.some((fitting) => b.endsWith(`:${fitting}`)));
write("data/sable/tags/block/super_light.json", tag(airliner));
// What counts as the inside of an airliner for the spawn guard: everything but the gear, which stands on open ground.
write(`data/${ID}/tags/block/airframe.json`, tag(structure.filter((b) => !b.includes("landing_gear") && !b.includes("call_button"))));
// What cannot be broken, except by a player sneaking with a wrench or in creative mode: the airliner's frame and
// controls, its own blocks and the other mods' it is built with, but not the cabin floor, where the building goes.
write(`data/${ID}/tags/block/unbreakable.json`, tag([
  ...structure.filter((b) => !b.endsWith("cabin_floor")),
  ...["createpropulsion:wing", "createpropulsion:creative_thruster", "createpropulsion:thruster", "offroad:wheel_mount",
    "simulated:physics_assembler", "create:black_seat"].map((id) => ({ id, required: false })),
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
shaped("springboard", ["WWW", "PSP"], { W: "minecraft:white_wool", P: "minecraft:birch_planks", S: "minecraft:slime_ball" }, 1);
shaped("starlight_panel", ["WWW", "WGW", "WWW"], { W: "minecraft:blue_wool", G: "minecraft:glowstone_dust" }, 8);
shaped("gym_rings", ["C C", "L L", "P P"], { C: "minecraft:chain", L: "minecraft:leather", P: "minecraft:birch_planks" }, 1);
shaped("hide_and_seek_button", ["B", "N", "I"], { B: "minecraft:stone_button", N: "minecraft:note_block", I: "minecraft:iron_ingot" }, 1);
// Room dividers: cloth hung from a rail on rings, a cream panel of pale wood, and the exit sign's glowing green glass.
shaped("cabin_curtain", ["NSN", "WWW", "WWW"], { N: "minecraft:iron_nugget", S: "minecraft:stick", W: "minecraft:white_wool" }, 6);
shaped("cabin_partition", ["PPP", "PDP", "PPP"], { P: "minecraft:birch_planks", D: "minecraft:white_dye" }, 8);
shaped("exit_sign", ["GLG"], { G: "minecraft:green_stained_glass_pane", L: "minecraft:glowstone_dust" }, 2);
holdAndTailRecipes();

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
  "subtitles.aerolarge.callout": "Cockpit callout",
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
  ...Object.fromEntries(FIRST_FLIGHT.flatMap(([name, , , , title, description]) => [
    [`advancements.${ID}.first_flight.${name}.title`, title],
    [`advancements.${ID}.first_flight.${name}.description`, description],
  ])),
});
console.log("ok");
