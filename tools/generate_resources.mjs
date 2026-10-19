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
// The windscreen backing is invisible: its models only give the breaking particles. It drops nothing, and neither
// does the relay hidden in the nacelles.
for (const name of ["windscreen", "cockpit_windscreen"]) {
  write(`assets/${ID}/models/block/${name}.json`, { textures: { particle: `${ID}:block/cockpit_window` } });
  cubes[name] = null;
}
write(`assets/${ID}/blockstates/windscreen.json`, { variants: { "": { model: `${ID}:block/windscreen` } } });
write(`assets/${ID}/blockstates/cockpit_windscreen.json`, { variants: { "": { model: `${ID}:block/cockpit_windscreen` } } });
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

for (const name of ["airstair_controller", "airstair_call_button", "thrust_lever", "instrument_panel", "autopilot"]) {
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
write("data/minecraft/tags/block/mineable/pickaxe.json", tag(blocks.filter((b) => !b.endsWith("tire"))));
write("data/minecraft/tags/block/doors.json", tag([`${ID}:airliner_door`]));
write("data/minecraft/tags/item/doors.json", tag([`${ID}:airliner_door`]));
// Sable's physics: the whole airframe is aircraft-light, and the tires let it roll down a runway on their own.
write("data/sable/tags/block/super_light.json", tag(blocks));
// What counts as the inside of an airliner for the spawn guard: everything but the gear, which stands on open ground.
write(`data/${ID}/tags/block/airframe.json`, tag(blocks.filter((b) => !b.includes("landing_gear") && !b.includes("call_button"))));
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
  "block.aerolarge.windscreen": "Windscreen",
  "block.aerolarge.cockpit_windscreen": "Cockpit Windscreen",
  "block.aerolarge.thrust_lever": "Thrust Lever",
  "block.aerolarge.thrust_relay": "Thrust Relay",
  "block.aerolarge.instrument_panel": "Instrument Panel",
  "message.aerolarge.thrust": "Engine %s thrust: %s%%",
  "block.aerolarge.autopilot": "Autopilot",
  "block.aerolarge.nav_light": "Navigation Light",
  "block.aerolarge.engine_core": "Engine Core",
  "block.aerolarge.tiller": "Nose Wheel Tiller",
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
  "message.aerolarge.autopilot.status": "AP %s  |  %s b/s  |  %s blocks  |  pitch %s°  bank %s°",
  "message.aerolarge.autopilot.phase.takeoff": "TAKE-OFF",
  "message.aerolarge.autopilot.phase.climb": "CLIMB",
  "message.aerolarge.autopilot.phase.cruise": "CRUISE",
  "aerolarge.configuration.autopilot": "Autopilot",
  "aerolarge.configuration.climbHeight": "Climb Height",
  "aerolarge.configuration.rotateSpeed": "Rotate Speed",
  "aerolarge.configuration.cruiseSpeed": "Cruise Speed",
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
