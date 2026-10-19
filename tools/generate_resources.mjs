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
// The skin and the windscreen cut to the hull's curve: one model each for every shape, baked by the mod's own
// "shell" loader from the block's corners. They give back the part they are cut from. The relay hidden in the
// nacelles drops nothing.
for (const [name, skin, renderType, part] of [
  ["airframe_shell", "airframe_panel", "minecraft:solid", "airframe_panel"],
  ["cockpit_glass", "cockpit_glass", "minecraft:translucent", "cockpit_window"],
]) {
  write(`assets/${ID}/blockstates/${name}.json`, { variants: { "": { model: `${ID}:block/${name}` } } });
  write(`assets/${ID}/models/block/${name}.json`, {
    loader: `${ID}:shell`,
    render_type: renderType,
    textures: { skin: `${ID}:block/${skin}`, particle: `${ID}:block/${skin}` },
  });
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:${part}` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/${name}`,
  });
  cubes[name] = null;
}
write(`assets/${ID}/blockstates/thrust_relay.json`, { variants: { "": { model: `${ID}:block/thrust_relay` } } });
write(`assets/${ID}/models/block/thrust_relay.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/thrust_relay` } });
cubes.thrust_relay = null;

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
  write(`assets/${ID}/sounds.json`, sounds);
  write(`assets/${ID}/particles/contrail.json`, { textures: [`${ID}:contrail`] });
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

// A model made of boxes, each face of each box showing the given texture over the part of it the box covers.
function boxes(textures, parts) {
  return {
    parent: "minecraft:block/block",
    textures,
    elements: parts.map(([from, to, texture]) => ({
      from, to,
      faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, { texture }])),
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

const blocks = [...Object.keys(cubes), "airliner_door"].map((name) => `${ID}:${name}`);
const tag = (values) => ({ replace: false, values });
write("data/minecraft/tags/block/mineable/pickaxe.json", tag(blocks.filter((b) => !b.endsWith("tire") && !b.endsWith("landing_target"))));
write("data/minecraft/tags/block/doors.json", tag([`${ID}:airliner_door`]));
write("data/minecraft/tags/item/doors.json", tag([`${ID}:airliner_door`]));
// Sable's physics: the whole airframe is aircraft-light, and the tires let it roll down a runway on their own.
const GROUND = ["landing_target", "tower_radio"];
const airliner = blocks.filter((b) => !GROUND.some((ground) => b.endsWith(`:${ground}`)));
write("data/sable/tags/block/super_light.json", tag(airliner));
// What counts as the inside of an airliner for the spawn guard: everything but the gear, which stands on open ground.
write(`data/${ID}/tags/block/airframe.json`, tag(airliner.filter((b) => !b.includes("landing_gear") && !b.includes("call_button"))));
// What cannot be broken, except by a player sneaking with a wrench or in creative mode: the airliner's frame and
// controls, its own blocks and the other mods' it is built with, but not the cabin floor, where the building goes.
write(`data/${ID}/tags/block/unbreakable.json`, tag([
  ...airliner.filter((b) => !b.endsWith("cabin_floor")),
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
shaped("handset", ["S", "P"], { S: "minecraft:string", P: `${ID}:instrument_panel` }, 1);
shaped("tower_radio", ["IRI", "INI", "ICI"], { I: "minecraft:iron_ingot", R: "minecraft:lightning_rod", N: "minecraft:note_block", C: "minecraft:comparator" }, 1);
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
shaped("landing_gear_tire", ["KKK", "KSK", "KKK"], { K: "minecraft:black_wool", S: "minecraft:iron_ingot" }, 4);

write(`assets/${ID}/lang/en_us.json`, {
  "itemGroup.aerolarge": "Aerolarge",
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
  "block.aerolarge.thrust_lever": "Thrust Lever",
  "block.aerolarge.thrust_relay": "Thrust Relay",
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
  "message.aerolarge.autopilot.engaged": "Autopilot engaged: take-off, then climbing to %s blocks. Use it again to hand back to the controls.",
  "message.aerolarge.autopilot.off": "Autopilot off: the thrust levers, tiller and parking brake have the airliner again.",
  "message.aerolarge.autopilot.not_assembled": "The autopilot only flies an assembled airliner: fold the airstairs away and use the Physics Assembler first.",
  "message.aerolarge.autopilot.status": "AP %s  |  %s b/s  |  %s blocks  |  pitch %s°  bank %s°  |  hdg %s",
  "message.aerolarge.autopilot.phase.takeoff": "TAKE-OFF",
  "message.aerolarge.autopilot.phase.climb": "CLIMB",
  "message.aerolarge.autopilot.phase.cruise": "CRUISE",
  "message.aerolarge.autopilot.phase.descent": "DESCENT",
  "message.aerolarge.autopilot.holding": "Autopilot engaged: holding %s blocks, heading %s.",
  "message.aerolarge.autopilot.engage_first": "Engage the autopilot first.",
  "message.aerolarge.autopilot.heading": "Heading %s",
  "message.aerolarge.autopilot.orbit_left": "Circling left, radius %s blocks",
  "message.aerolarge.autopilot.orbit_right": "Circling right, radius %s blocks",
  "message.aerolarge.autopilot.altitude": "Altitude %s",
  "message.aerolarge.autopilot.speed": "Speed %s b/s",
  "message.aerolarge.autopilot.radius": "Circle radius %s blocks",
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
  "subtitles.aerolarge.pa_chime": "Cabin chime",
  "subtitles.aerolarge.radio_crackle": "Radio crackles",
  "message.aerolarge.unbreakable": "The airliner's frame and controls cannot be broken. Sneak holding a wrench to take them apart.",
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
  "screen.aerolarge.autopilot.now": "SPD %s  ALT %s  HDG %s",
  "screen.aerolarge.autopilot.range": "%s blocks",
  "aerolarge.configuration.autopilot": "Autopilot",
  "aerolarge.configuration.climbHeight": "Climb Height",
  "aerolarge.configuration.rotateSpeed": "Rotate Speed",
  "aerolarge.configuration.cruiseSpeed": "Cruise Speed",
  "aerolarge.configuration.cameraShake": "Camera Shake",
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
  "aerolarge.configuration.assemblerBlock": "Assembler Block",
  "aerolarge.configuration.wheelMountBlock": "Wheel Mount Block",
  "aerolarge.configuration.tireItem": "Tire Item",
});
console.log("ok");
