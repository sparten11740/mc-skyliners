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
const ID = "skyliners";
// How far a model drawn facing north is turned for each horizontal facing.
const yRot = { north: 0, east: 90, south: 180, west: 270 };
// How tall a module of the flight deck's centre console is, in pixels: ConsoleModule.HEIGHT.
const CONSOLE_HEIGHT = 6;

// The columns and rows of its texture the game gives a face with none of its own: taken from where it lies in its block.
function defaultUv(side, [fromX, fromY, fromZ], [toX, toY, toZ]) {
  return {
    down: [fromX, 16 - toZ, toX, 16 - fromZ],
    up: [fromX, fromZ, toX, toZ],
    north: [16 - toX, 16 - toY, 16 - fromX, 16 - fromY],
    south: [fromX, 16 - toY, toX, 16 - fromY],
    west: [fromZ, 16 - toY, toZ, 16 - fromY],
    east: [16 - toZ, 16 - toY, 16 - fromZ, 16 - fromY],
  }[side];
}
function offItsTexture(uv) {
  return Math.min(...uv) < 0 || Math.max(...uv) > 16;
}
// One way along a texture brought back onto it: by whole textures where that will do, as the same texture would carry
// on in the block beyond; a span as long as the texture takes all of it, a little squeezed.
function ontoTexture(from, to) {
  const span = to - from;
  if (span >= 16) return [0, 16];
  const start = Math.min(((from % 16) + 16) % 16, 16 - span);
  return [start, start + span].map((at) => Math.round(at * 1000) / 1000);
}
// A model with every face past its block given columns and rows on its texture. Going by where it lies, such a face
// runs off its texture into whatever lies beside that in the atlas: a part above its block shows another block's colours.
function facesOnTheirTextures(model) {
  const faceOnTexture = (element, side, face) => {
    const uv = defaultUv(side, element.from, element.to);
    if (face.uv || !offItsTexture(uv)) return face;
    const [fromU, toU] = ontoTexture(uv[0], uv[2]);
    const [fromV, toV] = ontoTexture(uv[1], uv[3]);
    return { ...face, uv: [fromU, fromV, toU, toV] };
  };
  const elementOnTexture = (element) => ({
    ...element,
    faces: Object.fromEntries(Object.entries(element.faces).map(([side, face]) => [side, faceOnTexture(element, side, face)])),
  });
  return { ...model, elements: model.elements.map(elementOnTexture) };
}
function write(path, value) {
  const file = resolve(ROOT, path);
  mkdirSync(dirname(file), { recursive: true });
  if (path.includes("/models/") && value && Array.isArray(value.elements)) {
    unfight(value.elements);
    value = facesOnTheirTextures(value);
  }
  writeFileSync(file, (typeof value === "string" ? value : JSON.stringify(value, null, 2)) + "\n");
}

// Two faces of a model's boxes that lie in one plane, look the same way and overlap flicker through each other where
// nothing covers them ("z-fighting"): the later box, as a rule the detail laid over the other, is stood out on that
// side by PROUD, a twentieth of a pixel, so its face is drawn in front. Turned boxes are left as they are.
const PROUD = 0.05;
const FACE_AXES = { west: [0, -1], east: [0, 1], down: [1, -1], up: [1, 1], north: [2, -1], south: [2, 1] };
function unfight(elements) {
  const square = elements.filter((element) => !(element.rotation && element.rotation.angle) && element.faces);
  for (let later = 1; later < square.length; later++) {
    for (let earlier = 0; earlier < later; earlier++) {
      standOut(square[earlier], square[later], square);
    }
  }
}
// Stands a later box's faces out where they lie in the plane of an earlier box's like faces and overlap them, seen.
function standOut(earlier, later, all) {
  for (const [face, [axis, sign]] of Object.entries(FACE_AXES)) {
    if (!earlier.faces[face] || !later.faces[face]) continue;
    const plane = sign > 0 ? later.to[axis] : later.from[axis];
    const theirs = sign > 0 ? earlier.to[axis] : earlier.from[axis];
    if (Math.abs(plane - theirs) > 1e-6) continue;
    const across = [0, 1, 2].filter((other) => other !== axis);
    const overlap = across.map((other) => [Math.max(earlier.from[other], later.from[other]), Math.min(earlier.to[other], later.to[other])]);
    if (overlap.some(([from, to]) => to - from < 0.01) || hidden(all, axis, sign, plane, across, overlap)) continue;
    if (sign > 0) later.to[axis] = plane + PROUD;
    else later.from[axis] = plane - PROUD;
  }
}
// Whether a box fills the space just outside a face over all of a stretch of it, hiding it there.
function hidden(all, axis, sign, plane, across, overlap) {
  return all.some((box) => {
    const outside = sign > 0 ? box.from[axis] <= plane + 1e-6 && box.to[axis] > plane + 0.01 : box.to[axis] >= plane - 1e-6 && box.from[axis] < plane - 0.01;
    return outside && across.every((other, i) => box.from[other] <= overlap[i][0] + 1e-6 && box.to[other] >= overlap[i][1] - 1e-6);
  });
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
  lift_glass: "minecraft:translucent",
  festive_garland: "minecraft:cutout",
  festive_bow: null,
  zeppelin_catwalk: "minecraft:cutout",
  flight_deck: null,
  flight_deck_stripe: null,
  dance_floor: null,
  club_wall: null,
  padded_bulkhead: null,
  engine_grating: null,
  beach_sand: null,
};
// Cubes whose blockstate is written with their other models below, and cubes with faces of their own, written below.
const OWN_BLOCKSTATES = new Set(["cabin_light", "flight_deck_stripe"]);
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

// The freighter's dimming glass (see DimmingGlassBlock): her canopy, cut to her hull's curve as the smoked glass is by the
// mod's "shell" loader, its model for each darkness drawn with that darkness's glass, clear being the smoked glass.
{
  const DIMS = [0, 1, 2, 3];
  for (const dim of DIMS) {
    const skin = dim === 0 ? "smoked_glass" : `dimming_glass_${dim}`;
    write(`assets/${ID}/models/block/dimming_glass_${dim}.json`, {
      loader: `${ID}:shell`, render_type: "minecraft:translucent",
      textures: { skin: `${ID}:block/${skin}`, particle: `${ID}:block/${skin}` },
    });
  }
  write(`assets/${ID}/blockstates/dimming_glass.json`, {
    variants: Object.fromEntries(DIMS.map((dim) => [`dim=${dim}`, { model: `${ID}:block/dimming_glass_${dim}` }])),
  });
  write(`data/${ID}/loot_table/blocks/dimming_glass.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: "minecraft:glass" }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/dimming_glass`,
  });
  cubes.dimming_glass = null;
}

// The freighter's deck robot (see DeckRobotBlock), our own design, drawn facing north: its body on three small wheels,
// one ahead and two behind, a dark chassis plate and a white body with a teal band; and its head, a state the client's
// DeckRobotRenderer draws turning about its neck at (8, 9, 8): a short neck, a teal and white head, a cyan visor across
// its front lit full whatever the light round it, and a thin antenna.
{
  const body = [
    solid([7, 0, 2.5], [9, 2, 4.5], "#dark"),
    solid([3, 0, 10], [5, 2, 12], "#dark"),
    solid([11, 0, 10], [13, 2, 12], "#dark"),
    solid([4, 2, 4], [12, 3, 12], "#dark"),
    solid([4.5, 3, 4.5], [11.5, 9, 11.5], "#body"),
  ];
  const visor = { texture: "#visor", neoforge_data: { block_light: 15, sky_light: 15 } };
  const head = [
    solid([7, 9, 7], [9, 10, 9], "#dark"),
    solid([5, 10, 5], [11, 14, 11], "#head"),
    { from: [5.5, 11.5, 4.8], to: [10.5, 12.5, 5], faces: { north: visor, east: visor, west: visor, up: visor, down: visor } },
    solid([9.5, 14, 9], [10, 16, 9.5], "#dark"),
  ];
  const textures = {
    body: `${ID}:block/deck_robot_body`, head: `${ID}:block/deck_robot_head`, dark: `${ID}:block/deck_robot_dark`,
    visor: `${ID}:block/deck_robot_visor`, particle: `${ID}:block/deck_robot_body`,
  };
  write(`assets/${ID}/models/block/deck_robot.json`, { parent: "minecraft:block/block", textures, elements: body });
  write(`assets/${ID}/models/block/deck_robot_head.json`, { parent: "minecraft:block/block", textures, elements: head });
  write(`assets/${ID}/models/item/deck_robot.json`, { parent: "minecraft:block/block", textures, elements: [...body, ...head],
    display: { gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.8, 0.8, 0.8] } } });
  write(`assets/${ID}/blockstates/deck_robot.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [
      [`facing=${facing},part=body`, { model: `${ID}:block/deck_robot`, y }],
      [`facing=${facing},part=head`, { model: `${ID}:block/deck_robot_head`, y }],
    ])),
  });
  dropsItself("deck_robot");
}

// The freighter's engine grille (see EngineGrilleBlock): a cube of its own texture for each of its glows, dark, idling and
// at full, the glowing ones lit full whatever the light round them, as the cabin light glows at night; its item shows it
// at full.
{
  const GLOWS = ["", "_idle", "_full"];
  const glowing = Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, {
    texture: "#all", cullface: face, neoforge_data: { block_light: 15, sky_light: 15 },
  }]));
  write(`assets/${ID}/models/block/engine_grille.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/engine_grille` } });
  for (const suffix of GLOWS.slice(1)) {
    write(`assets/${ID}/models/block/engine_grille${suffix}.json`, {
      parent: "minecraft:block/block",
      ambientocclusion: false,
      textures: { all: `${ID}:block/engine_grille${suffix}`, particle: `${ID}:block/engine_grille${suffix}` },
      elements: [{ from: [0, 0, 0], to: [16, 16, 16], shade: false, faces: glowing }],
    });
  }
  write(`assets/${ID}/blockstates/engine_grille.json`, {
    variants: Object.fromEntries(GLOWS.map((suffix, glow) => [`glow=${glow}`, { model: `${ID}:block/engine_grille${suffix}` }])),
  });
  write(`assets/${ID}/models/item/engine_grille.json`, { parent: `${ID}:block/engine_grille_full` });
  dropsItself("engine_grille");
  cubes.engine_grille = null;
}

// The freighter's boarding ramp (see BoardingRamp): the invisible slopes its deck is walked on (see RampDeckBlock) only
// give the particles feet kick up off its plate; the ramp lays and takes them up, so they have no item and drop nothing,
// light as the rest of her but no part of her frame (see rampFittings). The panel at its head (see RampPanelBlock) is a
// board two sixteenths thick at the back of its block, as the rover bay's panel is, its face in the colour of how the
// ramp stands, painted as the rover panel's is; only the blueprint places it, so it has no item and drops nothing.
{
  write(`assets/${ID}/blockstates/ramp_deck.json`, { variants: { "": { model: `${ID}:block/ramp_deck` } } });
  write(`assets/${ID}/models/block/ramp_deck.json`, { textures: { particle: `${ID}:block/airstair_tread` } });
  cubes.ramp_deck = null;
  const variants = {};
  const FRONTS = { ready: "ready", busy: "busy", down: "out", refused: "refused" };
  for (const [status, front] of Object.entries(FRONTS)) {
    write(`assets/${ID}/models/block/ramp_panel_${status}.json`, {
      parent: "minecraft:block/block",
      textures: { front: `${ID}:block/rover_panel_front_${front}`, side: `${ID}:block/rover_panel_side`, particle: `${ID}:block/rover_panel_side` },
      elements: [{
        from: [0, 0, 14], to: [16, 16, 16],
        faces: {
          north: { texture: "#front" }, south: { texture: "#side" }, up: { texture: "#side", uv: [0, 14, 16, 16] },
          down: { texture: "#side", uv: [0, 14, 16, 16] }, east: { texture: "#side", uv: [14, 0, 16, 16] }, west: { texture: "#side", uv: [0, 0, 2, 16] },
        },
      }],
    });
    for (const [facing, y] of Object.entries(yRot)) {
      variants[`facing=${facing},status=${status}`] = { model: `${ID}:block/ramp_panel_${status}`, y };
    }
  }
  write(`assets/${ID}/blockstates/ramp_panel.json`, { variants });
  cubes.ramp_panel = null;
}
// Her ramp's call button, low on her starboard leg: a lit push-button, its amber cap showing her ramp let down.
litButton("ramp_button");
function rampFittings() {
  return ["ramp_deck"];
}

// The freighter's laser cannon (see LaserCannonBlock): its pedestal, a gunmetal column on a flange up the middle of its
// block with a drum on top, its swivel and barrels drawn on the drum by LaserCannonRenderer; round, so the same whichever
// way it faces; its item shows the pedestal.
{
  const bezel = `${ID}:block/airstair_button_bezel`;
  write(`assets/${ID}/models/block/laser_cannon.json`, {
    parent: "minecraft:block/block",
    textures: { mount: bezel, particle: bezel },
    elements: [solid([2, 0, 2], [14, 2, 14], "#mount"), solid([5, 2, 5], [11, 13, 11], "#mount"), solid([3, 13, 3], [13, 16, 13], "#mount")],
  });
  write(`assets/${ID}/blockstates/laser_cannon.json`, {
    variants: Object.fromEntries(["north", "east", "south", "west"].map((facing) => [`facing=${facing}`, { model: `${ID}:block/laser_cannon` }])),
  });
  write(`assets/${ID}/models/item/laser_cannon.json`, { parent: `${ID}:block/laser_cannon` });
  dropsItself("laser_cannon");
  cubes.laser_cannon = null;
}

// The fleet's outside fittings (see HullFittingBlock), the freighter's dishes, masts, domes, fins, vents, machinery,
// floodlights and light strips: each a model of its own for each way it is put up, drawn facing north, a wall's
// against the south side of its block (see hullFittingModels), turned for each facing, unlit by the corner darkening
// so a part past its block never draws black aboard. A satellite dish's mount is its block's model and its dish a model
// of its own, drawn over it by SatelliteDishRenderer about the back of its hub, the big dish's three times as big on
// heavier mounts; their items show the dish on its post. Fitted onto her hull, so breakable, and light as the rest of
// her is (see FITTINGS); each drops itself.
{
  const textures = {
    steel: `${ID}:block/hull_fitting_steel`, dark: `${ID}:block/hull_fitting_dark`, pipe: `${ID}:block/hull_pipe`,
    lamp: `${ID}:block/hull_status_lamp`, particle: `${ID}:block/hull_fitting_steel`,
  };
  for (const [name, own, mounts] of hullFittingModels()) {
    for (const [mount, elements] of Object.entries(mounts)) {
      write(`assets/${ID}/models/block/${name}_${mount}.json`, {
        parent: "minecraft:block/block", ambientocclusion: false, textures: { ...textures, ...own }, elements,
      });
    }
    const part = name.endsWith("satellite_dish") ? ",part=mount" : "";
    const variants = Object.fromEntries(Object.keys(mounts).filter((mount) => mount !== "dish").flatMap((mount) => Object.entries(yRot).map(([facing, y]) =>
      [`facing=${facing},mount=${mount}${part}`, { model: `${ID}:block/${name}_${mount}`, ...(y ? { y } : {}) }])));
    if (part) {
      ["floor", "wall", "ceiling"].forEach((mount) => Object.keys(yRot).forEach((facing) => {
        variants[`facing=${facing},mount=${mount},part=dish`] = { model: `${ID}:block/satellite_dish_dish` };
      }));
    }
    write(`assets/${ID}/blockstates/${name}.json`, { variants });
    if (!part) {
      write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}_floor` });
    }
    dropsItself(name);
    cubes[name] = null;
  }
  const models = Object.fromEntries(hullFittingModels().map(([name, own, mounts]) => [name, { own, mounts }]));
  const bowl = models.satellite_dish.mounts.dish.filter((element) => !element.rotation);
  for (const [name, up] of [["satellite_dish", 6], ["big_satellite_dish", 11]]) {
    write(`assets/${ID}/models/item/${name}.json`, {
      parent: "minecraft:block/block", ambientocclusion: false, textures: { ...textures, ...models.satellite_dish.own },
      elements: [...models[name].mounts.floor, ...shifted(bowl, 0, up, -4)],
      display: { gui: { rotation: [25, 200, 0], translation: [0, -1.5, 0], scale: [0.55, 0.55, 0.55] } },
    });
  }
}
// The outside fittings, each [its name, its own textures, its models by mount]: on a floor, on a wall, under a ceiling,
// and the satellite dish's dish.
function hullFittingModels() {
  const glow = (from, to, texture, rotation) => {
    const element = solid(from, to, texture, rotation);
    element.shade = false;
    Object.values(element.faces).forEach((face) => (face.neoforge_data = { block_light: 15, sky_light: 15 }));
    return element;
  };
  const faced = (from, to, faces, rotation) => ({
    from, to, ...(rotation ? { rotation } : {}),
    faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((side) => [side, { texture: faces[side] ?? faces.all }])),
  });
  const tilt = (origin, angle) => ({ origin, axis: "x", angle });
  // A disc of a radius about the middle of a face looking north, between two depths, as three crossed boxes.
  const disc = (radius, z0, z1, faces) => [[1, 0.41], [0.41, 1], [0.71, 0.71]].map(([across, up]) =>
    faced([8 - radius * across, 8 - radius * up, z0], [8 + radius * across, 8 + radius * up, z1], faces));
  // The bowl's raised rim, a ring of boxes round its edge.
  const rim = (radius, z0, z1, texture) => {
    const [side, flat, thick] = [radius * 0.41, radius * 0.71, 1.5];
    return [
      solid([8 - side, 8 + radius - thick, z0], [8 + side, 8 + radius, z1], texture), solid([8 - side, 8 - radius, z0], [8 + side, 8 - radius + thick, z1], texture),
      solid([8 - radius, 8 - side, z0], [8 - radius + thick, 8 + side, z1], texture), solid([8 + radius - thick, 8 - side, z0], [8 + radius, 8 + side, z1], texture),
      ...[[-1, -1], [-1, 1], [1, -1], [1, 1]].map(([sx, sy]) => solid(
        [Math.min(8 + sx * flat, 8 + sx * side), Math.min(8 + sy * flat, 8 + sy * side), z0],
        [Math.max(8 + sx * flat, 8 + sx * side), Math.max(8 + sy * flat, 8 + sy * side), z1], texture)),
    ];
  };
  const fins = (count, place) => Array.from({ length: count }, (_, i) => place(2 + i * 2.3));
  return [
    ["satellite_dish", { face: `${ID}:block/satellite_dish_face`, back: `${ID}:block/satellite_dish_back` }, {
      floor: [solid([4, 0, 4], [12, 1.5, 12], "#dark"), solid([5, 1.5, 5], [11, 2.5, 11], "#steel"), solid([6.5, 2.5, 6.5], [9.5, 9, 9.5], "#steel"),
        solid([9.5, 1.5, 7.5], [10.5, 8, 8.5], "#pipe"), solid([5, 9, 5], [11, 10.5, 11], "#dark")],
      wall: [solid([5, 3, 14], [11, 13, 16], "#dark"), solid([7, 7, 6], [9, 9, 14], "#steel"),
        solid([7.25, 3.5, 9.3], [8.75, 5, 15], "#steel", tilt([8, 4.25, 15], 45)), solid([5.5, 9, 3.5], [10.5, 10, 7.5], "#dark")],
      ceiling: [solid([4, 14.5, 4], [12, 16, 12], "#dark"), solid([7, 10.5, 7], [9, 14.5, 9], "#steel"), solid([9, 11, 7.5], [10, 14.5, 8.5], "#pipe"),
        solid([5.5, 9, 5.5], [10.5, 10.5, 10.5], "#dark")],
      dish: [solid([6.5, 6.5, 10], [9.5, 9.5, 12], "#dark"), ...disc(9, 9, 10, { north: "#face", all: "#back" }), ...rim(9, 7.5, 9, "#back"),
        solid([7.4, 7.4, 1], [8.6, 8.6, 9], "#steel"), solid([6.5, 6.5, -1.5], [9.5, 9.5, 1], "#dark"), glow([7.25, 7.25, -1.6], [8.75, 8.75, -1.5], "#lamp"),
        solid([7.5, 9, 0.5], [8.5, 10, 11.1], "#steel", tilt([8, 9.5, 0.5], -45)), solid([7.5, 6, 0.5], [8.5, 7, 11.1], "#steel", tilt([8, 6.5, 0.5], 45))],
    }],
    ["big_satellite_dish", {}, {
      floor: [solid([1, 0, 1], [15, 2, 15], "#dark"), solid([3, 2, 3], [13, 6, 13], "#steel"), solid([5, 6, 5], [11, 13, 11], "#steel"),
        solid([11, 2, 7.5], [12.5, 13, 8.5], "#pipe"), solid([3.5, 13, 3.5], [12.5, 16, 12.5], "#dark"), glow([5, 3.5, 2.9], [7, 4.5, 3], "#lamp")],
      wall: [solid([3, 2, 14], [13, 14, 16], "#dark"), solid([5, 9, 4], [7, 11, 14], "#steel"), solid([9, 9, 4], [11, 11, 14], "#steel"),
        solid([6, 4.5, 9.4], [10, 6, 15], "#steel", tilt([8, 5.25, 15], 45)), solid([4.5, 11, 1], [11.5, 12, 5], "#dark")],
      ceiling: [solid([2, 14, 2], [14, 16, 14], "#dark"), solid([5, 4, 5], [11, 14, 11], "#steel"), solid([10.5, 5, 7.5], [12, 14, 8.5], "#pipe"),
        solid([3.5, 1, 3.5], [12.5, 4, 12.5], "#dark")],
    }],
    ["antenna_mast", { beacon: `${ID}:block/antenna_lamp` }, {
      floor: [solid([5, 0, 5], [11, 1, 11], "#dark"), solid([6, 1, 6], [10, 3, 10], "#steel"), solid([7.25, 3, 7.25], [8.75, 28, 8.75], "#steel"),
        solid([6.5, 6, 6.5], [9.5, 10, 9.5], "#dark"), glow([9.5, 8, 7.5], [9.6, 9, 8.5], "#lamp"), solid([7.5, 13, 4], [8.5, 14, 12], "#steel"),
        solid([3, 18, 7.5], [13, 19, 8.5], "#steel"), solid([4.5, 23, 7.5], [11.5, 24, 8.5], "#steel"), glow([6.75, 28, 6.75], [9.25, 30.5, 9.25], "#beacon")],
      wall: [solid([5.5, 2, 14], [10.5, 8, 16], "#dark"), solid([6.5, 3, 11], [9.5, 7, 14], "#steel"), glow([9.5, 4.5, 12], [9.6, 5.5, 13], "#lamp"),
        solid([7.4, 4.4, -9], [8.6, 5.6, 11], "#steel", tilt([8, 5, 11], 45)), glow([6.9, 3.9, -11.5], [9.1, 6.1, -9], "#beacon", tilt([8, 5, 11], 45)),
        solid([9.5, 8, 12.5], [10.5, 13, 13.5], "#steel")],
      ceiling: [solid([5, 15, 5], [11, 16, 11], "#dark"), solid([7.5, 9, 3], [8.5, 15, 12], "#steel"), solid([7.5, 4, 6], [8.5, 9, 12], "#steel"),
        solid([7.5, 0.5, 12.5], [8.5, 15, 13.5], "#steel"), glow([7, 2, 6.5], [9, 4, 8.5], "#beacon"), glow([8.5, 13, 9], [8.6, 14, 10], "#lamp")],
    }],
    ["sensor_dome", { dome: `${ID}:block/sensor_dome`, glow: `${ID}:block/sensor_glow` }, {
      floor: [solid([3, 0, 3], [13, 2.5, 13], "#dark"), glow([2.9, 1, 2.9], [13.1, 1.6, 13.1], "#glow"),
        solid([3.5, 2.5, 3.5], [12.5, 7, 12.5], "#dome"), solid([2.5, 2.5, 4.5], [13.5, 6, 11.5], "#dome"), solid([4.5, 2.5, 2.5], [11.5, 6, 13.5], "#dome"),
        solid([4.5, 7, 4.5], [11.5, 9.5, 11.5], "#dome"), solid([5.5, 9.5, 5.5], [10.5, 11, 10.5], "#dome"), solid([6.5, 11, 6.5], [9.5, 12, 9.5], "#dome"),
        glow([6.5, 4, 2.4], [9.5, 5.5, 2.5], "#glow")],
      wall: [solid([3, 3, 14.5], [13, 13, 16], "#dark"), glow([2.9, 2.9, 14.6], [13.1, 13.1, 15.2], "#glow"),
        solid([3.5, 3.5, 10], [12.5, 12.5, 14.5], "#dome"), solid([4.5, 2.5, 11], [11.5, 13.5, 14.5], "#dome"), solid([2.5, 4.5, 11], [13.5, 11.5, 14.5], "#dome"),
        solid([4.5, 4.5, 7.5], [11.5, 11.5, 10], "#dome"), solid([5.5, 5.5, 6], [10.5, 10.5, 7.5], "#dome"), solid([6.5, 6.5, 5], [9.5, 9.5, 6], "#dome"),
        glow([6.5, 4, 9.9], [9.5, 5.5, 10], "#glow")],
      ceiling: [solid([3, 13.5, 3], [13, 16, 13], "#dark"), glow([2.9, 14.4, 2.9], [13.1, 15, 13.1], "#glow"),
        solid([3.5, 9, 3.5], [12.5, 13.5, 12.5], "#dome"), solid([2.5, 10, 4.5], [13.5, 13.5, 11.5], "#dome"), solid([4.5, 10, 2.5], [11.5, 13.5, 13.5], "#dome"),
        solid([4.5, 6.5, 4.5], [11.5, 9, 11.5], "#dome"), solid([5.5, 5, 5.5], [10.5, 6.5, 10.5], "#dome"), solid([6.5, 4, 6.5], [9.5, 5, 9.5], "#dome"),
        glow([6.5, 10.5, 2.4], [9.5, 12, 2.5], "#glow")],
    }],
    ["heat_sink", { fin: `${ID}:block/heat_sink_fin`, heat: `${ID}:block/heat_glow` }, {
      floor: [solid([1, 0, 2], [15, 2, 14], "#dark"), glow([1.5, 2, 3.5], [14.5, 2.4, 12.5], "#heat"),
        ...fins(6, (x) => solid([x, 2, 3], [x + 1, 13, 13], "#fin")), solid([1.5, 13, 7.25], [14.5, 14, 8.75], "#dark"), solid([0, 0.5, 0.5], [16, 2.5, 2.5], "#pipe")],
      wall: [solid([1, 1, 14], [15, 15, 16], "#dark"), glow([2, 1.5, 13.6], [14, 14.5, 14], "#heat"),
        ...fins(6, (y) => solid([2, y, 4], [14, y + 1, 14], "#fin")), solid([1, 1, 3.5], [2, 15, 14], "#dark"), solid([14, 1, 3.5], [15, 15, 14], "#dark"),
        solid([2, 0, 12], [14, 1.5, 13.5], "#pipe")],
      ceiling: [solid([1, 14, 2], [15, 16, 14], "#dark"), glow([1.5, 13.6, 3.5], [14.5, 14, 12.5], "#heat"),
        ...fins(6, (x) => solid([x, 3, 3], [x + 1, 14, 13], "#fin")), solid([1.5, 2, 7.25], [14.5, 3, 8.75], "#dark"), solid([0, 13.5, 0.5], [16, 15.5, 2.5], "#pipe")],
    }],
    ["exhaust_vent", { grate: `${ID}:block/exhaust_grate` }, {
      floor: [solid([3, 0, 3], [13, 2, 13], "#dark"), faced([4.5, 2, 4.5], [11.5, 11, 11.5], { up: "#grate", all: "#steel" }),
        solid([4.2, 5, 4.2], [11.8, 6, 11.8], "#dark"), faced([3.8, 11, 3.8], [12.2, 12.5, 12.2], { up: "#grate", all: "#dark" }),
        ...[[4.5, 4.5], [10.5, 4.5], [4.5, 10.5], [10.5, 10.5]].map(([x, z]) => solid([x, 12.5, z], [x + 1, 14.5, z + 1], "#dark")),
        solid([4, 14.5, 4], [12, 15.5, 12], "#dark"), solid([5.5, 15.5, 5.5], [10.5, 16, 10.5], "#dark")],
      wall: [faced([2, 2, 13], [14, 14, 16], { north: "#grate", all: "#dark" }), solid([1.5, 2, 9], [2.5, 14, 16], "#dark"), solid([13.5, 2, 9], [14.5, 14, 16], "#dark"),
        solid([1.5, 14, 8.5], [14.5, 15, 16], "#dark"), ...[3.5, 6, 8.5, 11].map((y) => solid([2.5, y, 9.5], [13.5, y + 1, 13], "#steel", tilt([8, y + 0.5, 13], -22.5)))],
      ceiling: [solid([3, 14, 3], [13, 16, 13], "#dark"), solid([4.5, 7, 4.5], [11.5, 14, 11.5], "#steel"), solid([4.2, 10, 4.2], [11.8, 11, 11.8], "#dark"),
        faced([3.5, 4.5, 3.5], [12.5, 7, 12.5], { down: "#grate", all: "#dark" })],
    }],
    ["hull_machinery", { valve: "minecraft:block/red_concrete" }, {
      floor: [solid([1.5, 0, 6], [9.5, 6, 14.5], "#steel"), solid([2.5, 6, 7], [8.5, 6.4, 13.5], "#dark"), glow([4, 3, 5.9], [6, 4.5, 6], "#lamp"),
        solid([10.5, 0, 7], [14.5, 4.5, 11], "#dark"), solid([0, 0.5, 2], [16, 2.5, 4], "#pipe"), solid([11.5, 2.5, 2], [13.5, 7, 4], "#pipe"),
        solid([11.5, 5, 4], [13.5, 7, 8], "#pipe"), solid([10.5, 7, 1.8], [14.5, 7.6, 4.2], "#valve"), solid([0, 0.5, 14.8], [16, 1.5, 15.8], "#dark")],
      wall: [solid([0, 2, 13.5], [16, 3.5, 15], "#pipe"), solid([0, 12.5, 14], [16, 13.5, 15], "#dark"), solid([4, 4.5, 11], [12, 11.5, 16], "#steel"),
        solid([5, 5.5, 10.6], [11, 10.5, 11], "#dark"), glow([9.5, 9, 10.5], [10.5, 10, 10.6], "#lamp"), solid([13, 0, 13], [14.5, 16, 14.5], "#pipe"),
        solid([12.6, 3.5, 12.5], [14.9, 4.5, 15], "#dark"), solid([12.6, 11, 12.5], [14.9, 12, 15], "#dark"), solid([1.5, 6.5, 10.5], [3.5, 8.5, 13.5], "#valve")],
      ceiling: [solid([2, 15, 6.5], [14, 16, 9.5], "#dark"), solid([4, 10, 6], [5, 15, 10], "#dark"), solid([11, 10, 6], [12, 15, 10], "#dark"),
        solid([2.5, 6, 5], [13.5, 11, 11], "#steel"), solid([1.8, 6.5, 5.5], [2.5, 10.5, 10.5], "#dark"), solid([13.5, 6.5, 5.5], [14.2, 10.5, 10.5], "#dark"),
        solid([0, 12.5, 12], [16, 14, 13.5], "#pipe"), solid([7.25, 11, 9.5], [8.75, 12.5, 12.5], "#pipe"), glow([7.5, 5.9, 7.5], [8.5, 6, 8.5], "#lamp")],
    }],
    ["hull_light_strip", { strip: `${ID}:block/hull_light_strip` }, {
      floor: [solid([0, 0, 6], [16, 1, 10], "#dark"), glow([0, 1, 6.75], [16, 1.75, 9.25], "#strip")],
      wall: [solid([0, 6.5, 15], [16, 9.5, 16], "#dark"), glow([0, 7, 13.5], [16, 9, 15], "#strip"), solid([0, 9, 12.5], [16, 10, 16], "#dark")],
      ceiling: [solid([0, 15, 6], [16, 16, 10], "#dark"), glow([0, 14.25, 6.75], [16, 15, 9.25], "#strip")],
    }],
    ["hull_floodlight", { lens: `${ID}:block/floodlight_lens` }, {
      floor: [solid([4, 0, 4], [12, 1.5, 12], "#dark"), solid([3, 1.5, 7], [4, 9, 9], "#dark"), solid([12, 1.5, 7], [13, 9, 9], "#dark"),
        solid([4, 1.5, 7], [12, 2.5, 9], "#dark"), solid([4, 4, 5], [12, 10, 12], "#steel", tilt([8, 7, 8.5], 22.5)),
        glow([4.5, 4.5, 4.9], [11.5, 9.5, 5], "#lens", tilt([8, 7, 8.5], 22.5))],
      wall: [solid([5, 4, 15], [11, 11, 16], "#dark"), solid([6.5, 6, 11], [9.5, 9, 15], "#dark"), solid([4, 4, 4], [12, 10, 11], "#steel", tilt([8, 7, 7.5], -22.5)),
        glow([4.5, 4.5, 3.9], [11.5, 9.5, 4], "#lens", tilt([8, 7, 7.5], -22.5))],
      ceiling: [solid([3, 15, 3], [13, 16, 13], "#dark"), solid([4, 9, 4], [12, 15, 12], "#steel"), solid([3.5, 8, 3.5], [12.5, 9, 12.5], "#dark"),
        glow([4.5, 7.9, 4.5], [11.5, 8, 11.5], "#lens")],
    }],
  ];
}
// A model's boxes moved by so many pixels each way.
function shifted(elements, dx, dy, dz) {
  const by = (at) => [at[0] + dx, at[1] + dy, at[2] + dz];
  return elements.map((element) => ({ ...element, from: by(element.from), to: by(element.to) }));
}
function hullFittingNames() {
  return ["satellite_dish", "big_satellite_dish", "antenna_mast", "sensor_dome", "heat_sink", "exhaust_vent", "hull_machinery", "hull_floodlight", "hull_light_strip"];
}
function hullFittingLang() {
  return {
    "block.skyliners.satellite_dish": "Satellite Dish",
    "block.skyliners.big_satellite_dish": "Big Satellite Dish",
    "block.skyliners.antenna_mast": "Antenna Mast",
    "block.skyliners.sensor_dome": "Sensor Dome",
    "block.skyliners.heat_sink": "Heat Sink",
    "block.skyliners.exhaust_vent": "Exhaust Vent",
    "block.skyliners.hull_machinery": "Hull Machinery",
    "block.skyliners.hull_floodlight": "Hull Floodlight",
  };
}

// The freighter's hull plates (see FreighterPlating): skin cut to her curve as her cover is, baked by the "shell"
// loader, each of its own texture; set by hand, a whole plate, its item shown as one. Part of her hull as her cover is,
// so held fast aboard her; each gives itself back.
for (const name of ["hull_plate_dark", "hull_plate_riveted", "hull_plate_patched", "hazard_plate"]) {
  hullPlate(name);
}
function hullPlate(name) {
  write(`assets/${ID}/blockstates/${name}.json`, { variants: { "": { model: `${ID}:block/${name}` } } });
  write(`assets/${ID}/models/block/${name}.json`, {
    loader: `${ID}:shell`, render_type: "minecraft:solid", textures: { skin: `${ID}:block/${name}`, particle: `${ID}:block/${name}` },
  });
  write(`assets/${ID}/models/item/${name}.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/${name}` } });
  dropsItself(name);
  cubes[name] = null;
}
function hullPlateLang() {
  return {
    "block.skyliners.hull_plate_dark": "Dark Hull Plate",
    "block.skyliners.hull_plate_riveted": "Riveted Hull Plate",
    "block.skyliners.hull_plate_patched": "Patched Hull Plate",
    "block.skyliners.hazard_plate": "Hazard Plate",
  };
}

// The freighter's light band (see FreighterLights), a hull plate of its own, lit by the block's own light; and her
// light strips, among the outside fittings (see hullFittingModels).
hullPlate("hull_light_band");
function hullLightLang() {
  return {
    "block.skyliners.hull_light_band": "Hull Light Band",
    "block.skyliners.hull_light_strip": "Hull Light Strip",
  };
}

// The freighter's club bandstand (see BandstandBlock): its stage, a low dark-oak platform with a brass edge along its
// front; its band is drawn over it by BandstandRenderer. Its item shows the stage.
{
  write(`assets/${ID}/models/block/bandstand.json`, {
    parent: "minecraft:block/block",
    textures: { stage: "minecraft:block/dark_oak_planks", edge: "minecraft:block/gold_block", particle: "minecraft:block/dark_oak_planks" },
    elements: [solid([0, 0, 0], [16, 3, 16], "#stage"), solid([0, 2.5, 0], [16, 3.2, 0.5], "#edge")],
  });
  write(`assets/${ID}/blockstates/bandstand.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/bandstand`, y }])),
  });
  write(`assets/${ID}/models/item/bandstand.json`, { parent: `${ID}:block/bandstand` });
  dropsItself("bandstand");
  cubes.bandstand = null;
}

// The Spookie's hammock in the freighter's crew quarters (see SpookieHammockBlock): a striped orange and cream canvas
// sling three blocks long, sagging deep in the middle of its own block, hung from a wooden spreader bar at each end,
// each bar on a rope slanting down from the top of a steel post standing a block out from the hammock's block. Its
// parts past its block, near two blocks tall, are drawn without ambient occlusion so they do not go dark aboard. Its
// item shows it small.
{
  // A stretch of the sling, from one end to the other along it and from its underside to its top, each stretch's top
  // the underside of the one nearer the bar, so the canvas runs on unbroken as it sags.
  const sling = (from, to, low, high) => solid([from, low, 1.5], [to, high, 14.5], "#cloth");
  const rope = (x) => solid([x - 4.24, 20.5, 7.5], [x + 4.24, 21.5, 8.5], "#rope", { angle: x < 8 ? -45 : 45, axis: "z", origin: [x, 21, 8] });
  write(`assets/${ID}/models/block/spookie_hammock.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    textures: { cloth: `${ID}:block/spookie_hammock`, post: `${ID}:block/habitat_kerb`, rope: "minecraft:block/brown_wool", particle: `${ID}:block/spookie_hammock` },
    elements: [
      solid([-15, 0, 6], [-12, 30, 10], "#post"), solid([28, 0, 6], [31, 30, 10], "#post"),
      rope(-9), rope(25),
      solid([-6, 17, 1], [-5, 19, 15], "#post"), solid([21, 17, 1], [22, 19, 15], "#post"),
      sling(-5, -1, 15, 17), sling(-1, 3, 12, 15), sling(3, 6, 10, 12), sling(6, 10, 8, 10), sling(10, 13, 10, 12), sling(13, 17, 12, 15),
      sling(17, 21, 15, 17),
    ],
    display: {
      gui: { rotation: [30, 225, 0], translation: [0, 0, 0], scale: [0.3, 0.3, 0.3] },
      ground: { rotation: [0, 0, 0], translation: [0, 3, 0], scale: [0.15, 0.15, 0.15] },
      fixed: { rotation: [0, 0, 0], translation: [0, 0, 0], scale: [0.3, 0.3, 0.3] },
      thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 2.5, 0], scale: [0.2, 0.2, 0.2] },
      firstperson_righthand: { rotation: [0, 45, 0], translation: [0, 0, 0], scale: [0.25, 0.25, 0.25] },
    },
  });
  write(`assets/${ID}/blockstates/spookie_hammock.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/spookie_hammock`, y }])),
  });
  write(`assets/${ID}/models/item/spookie_hammock.json`, { parent: `${ID}:block/spookie_hammock` });
  dropsItself("spookie_hammock");
  cubes.spookie_hammock = null;
}

// The freighter's jump drive in engineering (see JumpDriveBlock): a dark steel plinth with a glowing sea-blue band round
// it and a chrome column rising from its middle half a block past its own, drawn without ambient occlusion so it does
// not go dark aboard, its core and rings drawn over it by JumpDriveRenderer; its item shows the plinth.
{
  write(`assets/${ID}/models/block/jump_drive.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    textures: { plinth: `${ID}:block/habitat_kerb`, band: "minecraft:block/sea_lantern", column: `${ID}:block/lab_chrome`,
      particle: `${ID}:block/habitat_kerb` },
    elements: [solid([1, 0, 1], [15, 6, 15], "#plinth"), solid([0.5, 3.5, 0.5], [15.5, 4.5, 15.5], "#band"), solid([5, 6, 5], [11, 22, 11], "#column")],
  });
  write(`assets/${ID}/blockstates/jump_drive.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["rest", "spooling", "running", "sputtering"]
      .map((drive) => [`drive=${drive},facing=${facing}`, { model: `${ID}:block/jump_drive`, y }]))),
  });
  write(`assets/${ID}/models/item/jump_drive.json`, { parent: `${ID}:block/jump_drive` });
  dropsItself("jump_drive");
  cubes.jump_drive = null;
}

// The freighter's breaker board in engineering (see BreakerBoardBlock): a steel panel two pixels deep against the wall
// behind it, a darker frame round its face, its six switches drawn over it by BreakerBoardRenderer; drawn facing north.
{
  write(`assets/${ID}/models/block/breaker_board.json`, {
    parent: "minecraft:block/block",
    textures: { panel: `${ID}:block/habitat_kerb`, frame: `${ID}:block/lab_dark`, particle: `${ID}:block/habitat_kerb` },
    elements: [solid([0, 0, 14], [16, 16, 16], "#panel"), solid([0, 0, 13.6], [16, 0.8, 14], "#frame"), solid([0, 15.2, 13.6], [16, 16, 14], "#frame"),
      solid([0, 0.8, 13.6], [0.8, 15.2, 14], "#frame"), solid([15.2, 0.8, 13.6], [16, 15.2, 14], "#frame")],
  });
  write(`assets/${ID}/blockstates/breaker_board.json`, {
    variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/breaker_board`, y }])),
  });
  write(`assets/${ID}/models/item/breaker_board.json`, { parent: `${ID}:block/breaker_board` });
  dropsItself("breaker_board");
  cubes.breaker_board = null;
}

// The freighter's coolant valve in engineering (see CoolantValveBlock): a steel plate against the wall behind it, a
// chrome pipe out of it to where the red wheel turns, and a chrome vent pipe up its right side, its mouth at the top of
// the block, the wheel and the gauge drawn over it by CoolantValveRenderer; drawn facing north.
{
  write(`assets/${ID}/models/block/coolant_valve.json`, {
    parent: "minecraft:block/block",
    textures: { plate: `${ID}:block/habitat_kerb`, pipe: `${ID}:block/lab_chrome`, particle: `${ID}:block/habitat_kerb` },
    elements: [solid([2, 0, 14], [14, 16, 16], "#plate"), solid([6.5, 5, 9], [9.5, 8, 14], "#pipe"), solid([3, 9, 11], [5, 16, 13], "#pipe"),
      solid([3, 9, 13], [5, 11, 14], "#pipe")],
  });
  write(`assets/${ID}/blockstates/coolant_valve.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [0, 1, 2, 3, 4].flatMap((pressure) => [false, true]
      .map((venting) => [`facing=${facing},pressure=${pressure},venting=${venting}`, { model: `${ID}:block/coolant_valve`, y }])))),
  });
  write(`assets/${ID}/models/item/coolant_valve.json`, { parent: `${ID}:block/coolant_valve` });
  dropsItself("coolant_valve");
  cubes.coolant_valve = null;
}

// The laser crossbow's rack in the freighter's entry hall (see CrossbowRackBlock): a dark wooden board against the wall
// behind it with two brass pegs standing out, the crossbow drawn across them by CrossbowRackRenderer; drawn facing north.
{
  write(`assets/${ID}/models/block/crossbow_rack.json`, {
    parent: "minecraft:block/block",
    textures: { board: "minecraft:block/dark_oak_planks", peg: "minecraft:block/gold_block", particle: "minecraft:block/dark_oak_planks" },
    elements: [solid([1, 3, 14], [15, 13, 16], "#board"), solid([3, 6, 12], [4.5, 7.5, 14], "#peg"), solid([11.5, 6, 12], [13, 7.5, 14], "#peg")],
  });
  write(`assets/${ID}/blockstates/crossbow_rack.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true]
      .map((loaded) => [`facing=${facing},loaded=${loaded}`, { model: `${ID}:block/crossbow_rack`, y }]))),
  });
  write(`assets/${ID}/models/item/crossbow_rack.json`, { parent: `${ID}:block/crossbow_rack` });
  dropsItself("crossbow_rack");
  cubes.crossbow_rack = null;
}

// The round floor window (see FloorWindowBlock): no model of its own, FloorWindowRenderer drawing it all, but glass
// when it is broken; its item a flat picture of it. It drops nothing by itself, as the window gives itself back once
// whichever of its blocks is broken. Its panel (see FloorWindowPanelBlock): a steel foot, post and head, and on top a
// big round button painted with the window, glowing whatever the light round it, its lamp green or amber; fixed to a
// wall, the same button facing out from it in a steel rim, flat to the wall, drawn facing north, its wall to the south.
{
  write(`assets/${ID}/models/block/floor_window.json`, { textures: { particle: "minecraft:block/glass" } });
  write(`assets/${ID}/blockstates/floor_window.json`, {
    variants: Object.fromEntries(Object.keys(yRot).flatMap((facing) => [false, true]
      .map((open) => [`facing=${facing},open=${open}`, { model: `${ID}:block/floor_window` }]))),
  });
  write(`assets/${ID}/models/item/floor_window.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/floor_window` } });
  cubes.floor_window = null;
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  for (const open of [false, true]) {
    const face = `${ID}:block/floor_window_panel_${open ? "open" : "shut"}`;
    write(`assets/${ID}/models/block/floor_window_panel_${open ? "open" : "shut"}.json`, {
      parent: "minecraft:block/block",
      textures: { post: `${ID}:block/floor_window_panel_post`, foot: `${ID}:block/lab_chrome`, face, particle: `${ID}:block/floor_window_panel_post` },
      elements: [solid([4, 0, 4], [12, 1, 12], "#foot"), solid([6.5, 1, 6.5], [9.5, 10, 9.5], "#post"), solid([3.5, 10, 3.5], [12.5, 13, 12.5], "#post"),
        { from: [3.5, 13, 3.5], to: [12.5, 14, 12.5], shade: false,
          faces: { up: { texture: "#face", ...lit }, north: { texture: "#post" }, south: { texture: "#post" }, east: { texture: "#post" },
            west: { texture: "#post" }, down: { texture: "#post" } } }],
    });
  }
  for (const open of [false, true]) {
    write(`assets/${ID}/models/block/floor_window_panel_wall_${open ? "open" : "shut"}.json`, {
      parent: "minecraft:block/block",
      textures: { post: `${ID}:block/floor_window_panel_post`, face: `${ID}:block/floor_window_panel_${open ? "open" : "shut"}`,
        particle: `${ID}:block/floor_window_panel_post` },
      elements: [solid([3, 3, 15.5], [13, 13, 16], "#post"),
        { from: [3.5, 3.5, 14.5], to: [12.5, 12.5, 15.5], shade: false,
          faces: { north: { texture: "#face", ...lit }, up: { texture: "#post" }, down: { texture: "#post" }, east: { texture: "#post" },
            west: { texture: "#post" } } }],
    });
  }
  const panelModel = (mount, open) => `${ID}:block/floor_window_panel_${mount === "wall" ? "wall_" : ""}${open ? "open" : "shut"}`;
  write(`assets/${ID}/blockstates/floor_window_panel.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["floor", "wall"].flatMap((mount) => [false, true]
      .flatMap((open) => [false, true].map((powered) => [`facing=${facing},mount=${mount},open=${open},powered=${powered}`,
        { model: panelModel(mount, open), y }]))))),
  });
  write(`assets/${ID}/models/item/floor_window_panel.json`, { parent: `${ID}:block/floor_window_panel_shut` });
  dropsItself("floor_window_panel");
  cubes.floor_window_panel = null;
}

// The freighter's beach in her lounge. A potted palm (see TropicalPieceBlock): a terracotta pot with a lighter rim and
// earth in it, a ringed trunk leaning a little as it rises a block and a half, and fronds spreading from its crown,
// four drooping out to its sides and two more crossing over them, drawn on both faces and clear between their leaflets.
// A parasol: a dark weighted foot, a white pole and a red and white striped canopy two and a half blocks across in
// three tiers over it, as high as a model reaches, over the heads of whoever walks under it, paler underneath. A sun
// lounger (see SunLoungerBlock): a white frame on four short legs, a striped cushion along it, its back raised at its
// south end, facing north. A tiki torch (see TikiTorchBlock): a bamboo pole, a woven head and its flame glowing on top
// whatever the light round it; fixed to a wall, a short pole held out from it on a bamboo bracket, facing north, its
// wall to the south. All reach past their blocks, or nearly, so none is drawn with ambient occlusion, which goes dark
// aboard.
{
  const frond = (from, to, uv, rotation) => ({ from, to, rotation,
    faces: { up: { texture: "#frond", uv }, down: { texture: "#frond", uv } } });
  const crossing = (angle) => ({ from: [-1, 27, 6], to: [17, 27.05, 10], rotation: { origin: [8, 27, 8], axis: "y", angle },
    faces: { up: { texture: "#crossing", uv: [0, 0, 16, 16] }, down: { texture: "#crossing", uv: [0, 0, 16, 16] } } });
  write(`assets/${ID}/models/block/potted_palm.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    render_type: "minecraft:cutout",
    textures: { pot: `${ID}:block/palm_pot`, rim: `${ID}:block/palm_pot_rim`, earth: "minecraft:block/coarse_dirt", trunk: `${ID}:block/palm_trunk`,
      frond: `${ID}:block/palm_frond`, crossing: `${ID}:block/palm_frond_crossing`, particle: `${ID}:block/palm_frond` },
    elements: [solid([5, 0, 5], [11, 4.5, 11], "#pot"), solid([4.5, 4, 4.5], [11.5, 5.5, 11.5], "#rim"), solid([5.5, 5.5, 5.5], [10.5, 5.6, 10.5], "#earth"),
      solid([7, 5.5, 7], [9, 16, 9], "#trunk"), solid([7.5, 16, 7.3], [9.5, 25, 9.3], "#trunk"), solid([7, 25, 6.8], [10, 27, 9.8], "#trunk"),
      frond([10, 26, 5.5], [22, 26.05, 10.5], [0, 0, 16, 16], { origin: [10, 26, 8], axis: "z", angle: -22.5 }),
      frond([-5, 26, 5.5], [7, 26.05, 10.5], [16, 0, 0, 16], { origin: [7, 26, 8], axis: "z", angle: 22.5 }),
      { ...frond([5.5, 26, -5], [10.5, 26.05, 7], [0, 0, 16, 16], { origin: [8, 26, 7], axis: "x", angle: 22.5 }), faces: {
        up: { texture: "#frond", uv: [0, 0, 16, 16], rotation: 270 }, down: { texture: "#frond", uv: [0, 0, 16, 16], rotation: 90 } } },
      { ...frond([5.5, 26, 10], [10.5, 26.05, 22], [0, 0, 16, 16], { origin: [8, 26, 10], axis: "x", angle: -22.5 }), faces: {
        up: { texture: "#frond", uv: [0, 0, 16, 16], rotation: 90 }, down: { texture: "#frond", uv: [0, 0, 16, 16], rotation: 270 } } },
      crossing(45), crossing(-45)],
  });
  write(`assets/${ID}/models/block/parasol.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    textures: { foot: "minecraft:block/polished_deepslate", pole: "minecraft:block/white_concrete", canopy: `${ID}:block/parasol_canopy`,
      under: `${ID}:block/parasol_underside`, particle: `${ID}:block/parasol_canopy` },
    elements: [solid([5, 0, 5], [11, 1, 11], "#foot"), solid([7.5, 1, 7.5], [8.5, 29, 8.5], "#pole"),
      ...[[-12, 29, 28], [-5, 30, 21], [2, 31, 14]].map(([from, y, to]) => ({ from: [from, y, from], to: [to, y + 1, to],
        faces: { up: { texture: "#canopy", uv: [0, 0, 16, 16] }, down: { texture: "#under", uv: [0, 0, 16, 16] },
          north: { texture: "#canopy", uv: [0, 0, 16, 1] }, south: { texture: "#canopy", uv: [0, 0, 16, 1] },
          east: { texture: "#canopy", uv: [0, 0, 16, 1] }, west: { texture: "#canopy", uv: [0, 0, 16, 1] } } }))],
  });
  write(`assets/${ID}/models/block/sun_lounger.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    textures: { frame: "minecraft:block/white_concrete", cushion: `${ID}:block/lounger_cushion`, particle: `${ID}:block/lounger_cushion` },
    elements: [...[[2, 2], [13, 2], [2, 13], [13, 13]].map(([x, z]) => solid([x, 0, z], [x + 1, 3, z + 1], "#frame")),
      solid([1, 3, 0.5], [15, 5, 15.5], "#frame"), solid([1.5, 5, 0.5], [14.5, 6.5, 10], "#cushion"),
      solid([1.5, 5, 10], [14.5, 6.5, 16.5], "#cushion", { origin: [8, 5, 10], axis: "x", angle: -45 })],
  });
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  write(`assets/${ID}/models/block/tiki_torch.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    textures: { bamboo: "minecraft:block/bamboo_block", head: "minecraft:block/hay_block_side", flame: `${ID}:block/tiki_flame`,
      particle: "minecraft:block/bamboo_block" },
    elements: [solid([7, 0, 7], [9, 14, 9], "#bamboo"), solid([6, 14, 6], [10, 18, 10], "#head"),
      { from: [6.5, 18, 6.5], to: [9.5, 19, 9.5], shade: false,
        faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, { texture: "#flame", ...lit }])) }],
  });
  for (const name of ["potted_palm", "parasol", "sun_lounger"]) {
    write(`assets/${ID}/blockstates/${name}.json`, {
      variants: Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing}`, { model: `${ID}:block/${name}`, y }])),
    });
  }
  write(`assets/${ID}/models/block/tiki_torch_wall.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    textures: { bamboo: "minecraft:block/bamboo_block", head: "minecraft:block/hay_block_side", flame: `${ID}:block/tiki_flame`,
      particle: "minecraft:block/bamboo_block" },
    elements: [solid([6.5, 3, 15], [9.5, 9, 16], "#bamboo"), solid([7.25, 5, 9], [8.75, 6.5, 15], "#bamboo"), solid([7, 2, 7], [9, 11, 9], "#bamboo"),
      solid([6, 11, 6], [10, 15, 10], "#head"),
      { from: [6.5, 15, 6.5], to: [9.5, 16, 9.5], shade: false,
        faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, { texture: "#flame", ...lit }])) }],
  });
  write(`assets/${ID}/blockstates/tiki_torch.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [[`facing=${facing},mount=floor`, { model: `${ID}:block/tiki_torch` }],
      [`facing=${facing},mount=wall`, { model: `${ID}:block/tiki_torch_wall`, ...(y ? { y } : {}) }]])),
  });
  for (const name of ["potted_palm", "parasol", "sun_lounger", "tiki_torch"]) {
    write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}` });
    dropsItself(name);
    cubes[name] = null;
  }
}

// The freighter's laser crossbow (see LaserCrossbowItem): a flat picture, held raised and pointing ahead as a tool is
// (the game's own crossbow is laid sideways by the game's own code, not its model), drawn with its coil glowing once
// charged and its lamps red, steam over it, as it vents (see LaserCrossbowLooks).
{
  for (const look of ["charged", "venting"]) {
    write(`assets/${ID}/models/item/laser_crossbow_${look}.json`, {
      parent: "minecraft:item/handheld", textures: { layer0: `${ID}:item/laser_crossbow_${look}` },
    });
  }
  write(`assets/${ID}/models/item/laser_crossbow.json`, {
    parent: "minecraft:item/handheld",
    textures: { layer0: `${ID}:item/laser_crossbow` },
    overrides: [
      { predicate: { [`${ID}:charged`]: 1 }, model: `${ID}:item/laser_crossbow_charged` },
      { predicate: { [`${ID}:venting`]: 1 }, model: `${ID}:item/laser_crossbow_venting` },
    ],
  });
}

// A launch pad's blocks (see LaunchPad): the grate over its flame pit, the zeppelin's aluminium catwalk grating to look
// through, which only the blueprint places, so it has no item and drops nothing (picked, it gives the catwalk), and the
// hub in its middle, which also goes up by hand and gives itself back. They are ground, not airframe (see GROUND).
{
  write(`assets/${ID}/blockstates/launch_pad_grate.json`, { variants: { "": { model: `${ID}:block/launch_pad_grate` } } });
  write(`assets/${ID}/models/block/launch_pad_grate.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/zeppelin_catwalk` }, render_type: "minecraft:cutout" });
  write(`assets/${ID}/blockstates/launch_pad_hub.json`, { variants: { "": { model: `${ID}:block/launch_pad_hub` } } });
  write(`assets/${ID}/models/block/launch_pad_hub.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/launch_pad_hub` } });
  write(`assets/${ID}/models/item/launch_pad_hub.json`, { parent: `${ID}:block/launch_pad_hub` });
  dropsItself("launch_pad_hub");
  cubes.launch_pad_grate = null;
  cubes.launch_pad_hub = null;
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
    "block.skyliners.plug_door": "Plug Door",
    "subtitles.skyliners.plug_door_hiss": "Door hisses",
    "subtitles.skyliners.plug_door_latch": "Door latches",
    "subtitles.skyliners.door_alarm": "Door alarm wails",
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
    "block.skyliners.cockpit_door": "Cockpit Door",
    "message.skyliners.cockpit_door.locked": "Cockpit door locked: it opens only from the flight deck",
    "message.skyliners.cockpit_door.unlocked": "Cockpit door unlocked",
    "message.skyliners.cockpit_door.locked_out": "Locked from the flight deck",
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
  litButtonState(name);
  cubes[name] = null;
  write(`assets/${ID}/models/block/${name}_inventory.json`, {
    parent: "minecraft:block/block",
    textures: { bezel, cap: block(capName), particle: bezel },
    elements: [
      solid([4, 4, 8], [12, 12, 9], "#bezel"),
      { from: [5, 5, 6.5], to: [11, 11, 8], shade: false, faces: Object.fromEntries(["north", "east", "west", "up", "down"].map((face) => [face, { texture: "#cap", ...lit }])) },
    ],
  });
  write(`assets/${ID}/models/item/${name}.json`, { parent: `${ID}:block/${name}_inventory` });
  dropsItself(name);
}
// A lit button's blockstate, turned as vanilla's: on the floor and the wall to face the way it looks, on the ceiling
// upside down and the other way round.
function litButtonState(name) {
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
}
// The airstair button beside the controller (see AirstairButtonBlock), its cap showing the controller's staircase.
litButton("airstair_button");
// A zeppelin's boarding stairs' button (see BoardingStairsButtonBlock), in the airstair button's cap: the same stairs.
litButton("boarding_stairs_button", "airstair_button");
// A rocket's stairs button (see RocketStairsButtonBlock), in the airstair button's cap too: the same stairs, 3 wide.
litButton("rocket_stairs_button", "airstair_button");
// A boarding stairs button's glowstone and blackstone button over an iron ingot, the rocket's steel.
function rocketStairsRecipes() {
  shaped("rocket_stairs_button", ["G", "B", "I"], { G: "minecraft:glowstone_dust", B: "minecraft:polished_blackstone_button", I: "minecraft:iron_ingot" }, 1);
}
function rocketStairsLang() {
  return {
    "block.skyliners.rocket_stairs_button": "Rocket Stairs Button",
    "message.skyliners.rocket.countdown_stairs": "Countdown holding: step clear of the stairs and the door",
  };
}
// A zeppelin's speed boost button (see BoostButtonBlock): its red cap shows a fast-forward arrow.
litButton("boost_button");
// The illuminated button: the call button with glowstone dust over it to light its cap.
function airstairButtonRecipes() {
  shaped("airstair_button", ["G", "B"], { G: "minecraft:glowstone_dust", B: `${ID}:airstair_call_button` }, 1);
}
function airstairButtonLang() {
  return {
    "block.skyliners.airstair_button": "Airstair Button",
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
    "block.skyliners.door_button": "Door Button",
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
    "block.skyliners.airstair_step": "Airstair Step",
    "subtitles.skyliners.airstair_motor": "Airstairs whirr",
    "subtitles.skyliners.airstair_latch": "Airstairs latch",
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
    "block.skyliners.surface_panel": "Control Surface Panel",
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
    "block.skyliners.slide_control": "Evacuation Slide Control",
    "block.skyliners.slide_step": "Evacuation Slide",
    "entity.skyliners.slide_ride": "Evacuation Slide",
    "subtitles.skyliners.slide_inflate": "Slide inflates",
    "subtitles.skyliners.slide_deflate": "Slide deflates",
    "subtitles.skyliners.slide_whoosh": "Someone slides down",
    "message.skyliners.slide.moving": "The slide only comes out while the airliner stands still",
    "message.skyliners.slide.no_ground": "No ground within the slide's reach below this door",
    "message.skyliners.slide.blocked": "Something is in the slide's way",
    "message.skyliners.slide.too_low": "The ground is right below the door: no slide needed",
    "message.skyliners.slide.no_door": "This slide control is not next to a door",
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
  return { "block.skyliners.airframe_lined_shell": "Lined Airframe Shell" };
}

// The rocket's rooms finished inside (see RocketFinish), each its own way. Her cover where a room sees it, cut to her
// curve (the lined cover) or whole (the lining panel), drawn by the "shell" loader with the cover's skin painted by her
// livery and, on the faces the room sees, that room's lining untinted: its wall, the dado along the wall's foot, the
// ledges and the ceiling's edge as the wall turns, the wall again round her windows. One model for each finish, the
// blockstate picking it by the block's finish. Her floors, finished on top as their room and underneath as the
// ceiling of the room under them: one model for each pair. The cover gives back fabric, the floor a cabin floor. The
// dome's night sky lines her nose over the cockpit, where no window is, so her windows take every finish but that.
const ROCKET_FINISHES = ["entry", "cargo", "mess", "bunks", "greenhouse", "pool", "club", "lab", "lounge", "cockpit", "dome"];
const WINDOW_FINISHES = ROCKET_FINISHES.filter((finish) => finish !== "dome");
{
  const skin = { skin: `${ID}:block/zeppelin_skin`, particle: `${ID}:block/zeppelin_skin` };
  const lining = (finish) => {
    const wall = `${ID}:block/rocket_wall_${finish}`;
    const dado = `${ID}:block/rocket_dado_${finish}`;
    return {
      lining: wall, lining_ceiling: `${ID}:block/rocket_ceiling_${finish}`, lining_floor: `${ID}:block/rocket_ledge_${finish}`, lining_dado: dado,
      lining_reveal: wall, lining_reveal_pair: wall, lining_reveal_head: wall, lining_reveal_sill: wall, lining_dado_sill: dado,
    };
  };
  const drops = (name, item) => write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: item }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/${name}`,
  });
  for (const name of ["rocket_lined_cover", "rocket_lining_panel"]) {
    write(`assets/${ID}/blockstates/${name}.json`, {
      variants: Object.fromEntries(ROCKET_FINISHES.map((finish) => [`finish=${finish}`, { model: `${ID}:block/${name}_${finish}` }])),
    });
    for (const finish of ROCKET_FINISHES) {
      write(`assets/${ID}/models/block/${name}_${finish}.json`, {
        loader: `${ID}:shell`, render_type: "minecraft:solid", tint_index: 0, textures: { ...skin, ...lining(finish) },
      });
    }
    drops(name, `${ID}:zeppelin_fabric`);
    cubes[name] = null;
  }
  const floors = {};
  for (const floor of ROCKET_FINISHES) {
    for (const ceiling of ROCKET_FINISHES) {
      floors[`ceiling=${ceiling},floor=${floor}`] = { model: `${ID}:block/rocket_floor_${floor}_${ceiling}` };
      write(`assets/${ID}/models/block/rocket_floor_${floor}_${ceiling}.json`, {
        parent: "minecraft:block/cube_bottom_top",
        textures: { top: `${ID}:block/rocket_floor_${floor}`, bottom: `${ID}:block/rocket_ceiling_${ceiling}`, side: `${ID}:block/rocket_floor_${floor}` },
      });
    }
  }
  write(`assets/${ID}/blockstates/rocket_floor.json`, { variants: floors });
  drops("rocket_floor", `${ID}:cabin_floor`);
  cubes.rocket_floor = null;
}
function rocketFinishLang() {
  return {
    "block.skyliners.rocket_lined_cover": "Lined Rocket Cover",
    "block.skyliners.rocket_lining_panel": "Rocket Lining Panel",
    "block.skyliners.rocket_floor": "Rocket Floor",
  };
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
  // The flight deck's lines are painted on its top only: its sides are the plain deck's. Its line runs north and south,
  // turned a quarter where it runs east and west (see DeckLineBlock).
  write(`assets/${ID}/models/block/flight_deck_stripe.json`, {
    parent: "minecraft:block/cube_bottom_top",
    textures: { top: `${ID}:block/flight_deck_stripe`, bottom: `${ID}:block/flight_deck`, side: `${ID}:block/flight_deck` },
  });
  write(`assets/${ID}/blockstates/flight_deck_stripe.json`, {
    variants: { "axis=z": { model: `${ID}:block/flight_deck_stripe` }, "axis=x": { model: `${ID}:block/flight_deck_stripe`, y: 90 } },
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
  // its block, its wide lens underneath, dark glass or, lit, glowing at full brightness whatever the light round it,
  // and a hood down its back edge, so it reads which way, drawn facing north, it throws its light.
  for (const lit of [false, true]) {
    write(`assets/${ID}/models/block/floodlight${lit ? "_lit" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: { drum: "minecraft:block/black_concrete", brass, lens: `${ID}:block/searchlight_lens${lit ? "" : "_off"}`, particle: brass },
      elements: [
        solid([5, 15, 5], [11, 16, 11], "#brass"),
        solid([2, 12, 2], [14, 15, 14], "#drum"),
        { from: [3, 11, 3], to: [13, 12, 13], faces: {
          north: { texture: "#drum" }, south: { texture: "#drum" }, east: { texture: "#drum" }, west: { texture: "#drum" },
          up: { texture: "#drum" }, down: { texture: "#lens", ...(lit ? { neoforge_data: { block_light: 15, sky_light: 15 } } : {}) },
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
  // picture buttons on its face, which its renderer lights (see SkywritingBoardRenderer).
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
    "block.skyliners.picture_board": "Picture Board",
    "message.skyliners.picture_board.no_map": "Hand the board a filled map to show it on the screens",
    "message.skyliners.picture_board.no_colours": "Hoist a banner at the flagstaff to show her colours",
    "poster.skyliners.nether": "See the Nether",
    "poster.skyliners.end": "The End: Last Stop",
    "poster.skyliners.jungle": "Fly to the Jungle",
    "poster.skyliners.peaks": "Ski the Frozen Peaks",
    "poster.skyliners.creeper_insurance": "Creeper Insurance",
    "poster.skyliners.villager_bank": "Villager Bank",
    "poster.skyliners.golem_guards": "Golem Guards",
    "poster.skyliners.cake_by_air": "Cake by Air",
  };
}
function christeningLang() {
  return {
    "block.skyliners.ships_plaque": "Ship's Plaque",
    "block.skyliners.ships_plaque.christened_by": "Christened by %s",
    "item.skyliners.christening_bottle": "Christening Bottle",
    "item.skyliners.christening_bottle.tooltip": "Name it at an anvil, then swing it at a zeppelin's nose to christen her",
    "message.skyliners.christening.named": "I name this ship %s. God bless her, and all who sail in her!",
    "message.skyliners.christening.unnamed": "God bless her, and all who sail in her! Name the bottle at an anvil to give her a name",
    "message.skyliners.christening.nose": "Swing the bottle at her nose",
  };
}
function shipsColoursLang() {
  return {
    "block.skyliners.flag_halyard": "Flag Halyard",
    "block.skyliners.flagstaff": "Flagstaff",
    "subtitles.skyliners.flag_halyard": "Halyard pulley squeaks",
  };
}
function zeppelinControlsLang() {
  return {
    "block.skyliners.rudder_wheel": "Rudder Wheel",
    "block.skyliners.elevator_wheel": "Elevator Wheel",
    "block.skyliners.engine_telegraph": "Engine Telegraph",
    "block.skyliners.engine_repeater": "Engine Repeater",
    "block.skyliners.trim_wheel": "Trim Wheel",
    "block.skyliners.trim_winch": "Trim Winch",
    "block.skyliners.trim_car": "Trim Car",
    "block.skyliners.bubble_level": "Bubble Level",
    "block.skyliners.techno_deck": "DJ Deck",
    "block.skyliners.secret_hatch": "Secret Hatch",
    "block.skyliners.speaker": "Speaker",
    "block.skyliners.dance_floor": "Dance Floor",
    "block.skyliners.club_wall": "Club Wall",
    "block.skyliners.lookout_glass": "Lookout's Glass",
    "block.skyliners.screen_stand": "Screen Stand",
    "subtitles.skyliners.lookout_bell": "Ship's bell strikes twice",
    "block.skyliners.ballast_valve": "Water Ballast Valve",
    "block.skyliners.searchlight": "Searchlight",
    "block.skyliners.floodlight": "Floodlight",
    "block.skyliners.franking_machine": "Franking Machine",
    "block.skyliners.mail_sack": "Mail Sack",
    "block.skyliners.banner_cleat": "Banner Cleat",
    "block.skyliners.crane_winch": "Crane Winch",
    "block.skyliners.flight_deck": "Flight Deck",
    "block.skyliners.flight_deck_stripe": "Flight Deck Line",
    "block.skyliners.engine_grating": "Engine Grating",
    "block.skyliners.jump_drive": "Jump Drive",
    "block.skyliners.breaker_board": "Breaker Board",
    "block.skyliners.coolant_valve": "Coolant Valve",
    "block.skyliners.crossbow_rack": "Crossbow Rack",
    "block.skyliners.floor_window": "Floor Window",
    "block.skyliners.floor_window_panel": "Floor Window Panel",
    "block.skyliners.beach_sand": "Beach Sand",
    "block.skyliners.potted_palm": "Potted Palm",
    "block.skyliners.parasol": "Parasol",
    "block.skyliners.sun_lounger": "Sun Lounger",
    "block.skyliners.tiki_torch": "Tiki Torch",
    "block.skyliners.crew_ladder": "Crew Ladder",
    "block.skyliners.deck_clamp": "Docking Clamp",
    "block.skyliners.carrier_lever": "Clamp Release Lever",
    "entity.skyliners.crane_hook": "Crane Hook",
    "item.skyliners.postcard": "Postcard from %s",
    "item.skyliners.postcard.franked": "Franked over %s, %s",
    "item.skyliners.postcard.the_zeppelin": "the Zeppelin",
    "block.skyliners.searchlight_handle": "Searchlight Handle",
    "block.skyliners.skywriting_board": "Skywriting Board",
    "block.skyliners.boarding_stairs_button": "Boarding Stairs Button",
    "block.skyliners.boost_button": "Speed Boost Button",
    "subtitles.skyliners.speed_boost": "Boosters roar",
    "subtitles.skyliners.zeppelin_engine": "Engine car drones",
    "subtitles.skyliners.telegraph_ring": "Engine telegraph rings",
    "subtitles.skyliners.telegraph_answer": "Engine car answers",
    "subtitles.skyliners.helm_notch": "Ship's wheel turns",
    "subtitles.skyliners.control_stop": "Control at its stop",
    "subtitles.skyliners.crane_ratchet": "Winch ratchets",
    "subtitles.skyliners.crane_latch": "Crane hook clunks",
    "subtitles.skyliners.clamp_lock": "Docking clamps lock",
    "subtitles.skyliners.clamp_release": "Docking clamps open",
    "subtitles.skyliners.canopy_open": "Parachute opens",
    "subtitles.skyliners.touchdown": "Wheels touch down",
    "subtitles.skyliners.trim_car_roll": "Trim car rolls",
    "subtitles.skyliners.bin_open": "Overhead bin opens",
    "subtitles.skyliners.bin_close": "Overhead bin closes",
    "subtitles.skyliners.searchlight_on": "Searchlight strikes up",
    "subtitles.skyliners.booster_cutout": "Boosters cut out",
    "subtitles.skyliners.tow_rope": "Tow rope runs",
    "subtitles.skyliners.glass_squeak": "Finger squeaks on glass",
    "subtitles.skyliners.autopilot_off": "Autopilot disconnects",
    "subtitles.skyliners.gear_lever": "Gear lever clunks",
    "subtitles.skyliners.handler_whistle_short": "Whistle: hold fast",
    "subtitles.skyliners.handler_whistle_long": "Whistle: let go",
    "subtitles.skyliners.water_ballast": "Water ballast pours",
    "subtitles.skyliners.up_ship_call": "Up ship!",
    "subtitles.skyliners.techno_track": "Techno pounds",
    "subtitles.skyliners.dj_scratch": "DJ scratches a record",
    "block.skyliners.horn_cord": "Horn Cord",
    "block.skyliners.livery_board": "Livery Board",
    "block.skyliners.livery_selector": "Livery Selector",
    "block.skyliners.festive_garland": "Festive Garland",
    "block.skyliners.festive_bow": "Festive Bow",
    "livery.skyliners.silver": "Hindenburg silver",
    "livery.skyliners.cream": "Graf Zeppelin cream",
    "livery.skyliners.navy": "navy grey",
    "livery.skyliners.airline": "the airliner's colours",
    "livery.skyliners.polar": "polar red",
    "livery.skyliners.midnight": "midnight blue",
    "livery.skyliners.checker": "moon-rocket checks",
    "livery.skyliners.roll": "black and white roll stripes",
    "livery.skyliners.red_trim": "silver with red trim",
    "message.skyliners.livery_board.repainting": "Repainting in %s: watch the painters' cradle sweep along",
    "message.skyliners.livery_board.repainting_rocket": "Repainting in %s: watch the new paint run down her from her nose",
    "message.skyliners.livery_board.no_zeppelin": "This board paints the zeppelin whose control car it stands in",
    "subtitles.skyliners.zeppelin_horn": "Zeppelin's horn sounds",
    "subtitles.skyliners.zeppelin_hull": "Wind booms through the hull",
    "subtitles.skyliners.zeppelin_creak": "Girder groans",
    "subtitles.skyliners.zeppelin_horn_echo": "Horn echoes off the ground",
    "subtitles.skyliners.sleigh_bells": "Sleigh bells jingle",
    "block.skyliners.burner_lever": "Burner Lever",
  };
}
// The zeppelin's fabric is far lighter than the airliner's aluminium: its cover and fins weigh a tenth of a block,
// against the airframe's quarter (sable:super_light), which it overrides, so the hot air carries a hull of its size;
// the rocket's cover lined inside her rooms is the same cover, and weighs the same.
write(`data/${ID}/tags/block/zeppelin_fabric.json`, { replace: false, values: [`${ID}:zeppelin_fabric`, `${ID}:zeppelin_cover`,
  `${ID}:rocket_lined_cover`, `${ID}:rocket_lining_panel`] });
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
    "itemGroup.skyliners.zeppelin": "Zeppelin",
    "block.skyliners.zeppelin_fabric": "Zeppelin Fabric",
    "block.skyliners.zeppelin_cover": "Zeppelin Cover",
    "block.skyliners.zeppelin_girder": "Zeppelin Girder",
    "block.skyliners.zeppelin_catwalk": "Zeppelin Catwalk",
    "block.skyliners.propeller": "Propeller",
    "message.skyliners.zeppelin_building": "Building the zeppelin… %s%%",
    "message.skyliners.zeppelin_built": "The zeppelin is ready. Use the autopilot in its control car, under the bow, to fly it.",
    "message.skyliners.zeppelin_too_high": "The zeppelin would reach past the build height here; build it lower down.",
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
  announcement_gravity: "Announcement: Artificial gravity enabled",
  announcement_arrival_rocket: "Announcement: Welcome to your destination",
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
// ROCKET_HEARD blocks off, out across the ground round her and up her whole height; her roar's thunder close by, heard
// only round her feet; her lift-off's shockwave, played to each listener as it reaches them; the same heard aboard,
// round the listener, and her side jets' puffs, near them; and launch control's count and its calls of her milestones
// (see the autopilot's LaunchCallouts), played to each listener as the flight deck's voice is (see heardAboard),
// subtitled with what is said.
const ROCKET_HEARD = 160;
// How far her roar's thunder close by carries, in blocks: round her feet, where those watching her lift off stand.
const ROCKET_NEAR_HEARD = 64;
const ROCKET_SUBTITLES = {
  rocket_roar: "Rocket engines roar",
  rocket_roar_aboard: "Rocket engines rumble",
  rocket_roar_near: "Rocket engines thunder",
  rocket_shockwave: "Shockwave booms",
  rocket_ignition: "Rocket engines ignite",
  rocket_ignition_aboard: "Rocket engines ignite",
  rocket_cutoff: "Rocket engines cut off",
  rocket_cutoff_aboard: "Rocket engines cut off",
  rocket_jet_puff: "Side jet puffs",
  rocket_touchdown: "Rocket lands on her fins",
  rocket_boost: "Rocket engines boost",
  rocket_boost_aboard: "Rocket engines boost",
  rocket_boost_ready: "Boost button beeps ready",
  rocket_scrub: "Launch scrubbed",
  rocket_stability_alarm: "Stability alarm sounds",
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
  launch_boost: "Launch control: Boost!",
};
function rocketSounds() {
  // How far each of her engines' sounds that carries farther than the game's own 16 blocks is heard.
  const heard = {
    rocket_roar: ROCKET_HEARD, rocket_ignition: ROCKET_HEARD, rocket_cutoff: ROCKET_HEARD, rocket_touchdown: ROCKET_HEARD,
    rocket_boost: ROCKET_HEARD, rocket_roar_near: ROCKET_NEAR_HEARD,
  };
  const sounds = Object.fromEntries(Object.keys(ROCKET_SUBTITLES).map((name) => [name, {
    subtitle: `subtitles.${ID}.${name}`,
    sounds: [{ name: `${ID}:${name}`, ...(heard[name] ? { attenuation_distance: heard[name] } : {}) }],
  }]));
  return { ...sounds, ...Object.fromEntries(Object.keys(LAUNCH_SUBTITLES).map((name) => [name, heardAboard(name)])) };
}
function rocketSoundsLang() {
  return Object.fromEntries(Object.entries({ ...ROCKET_SUBTITLES, ...LAUNCH_SUBTITLES }).map(([name, subtitle]) => [`subtitles.${ID}.${name}`, subtitle]));
}

// The freighter's sounds (see tools/generate_sounds.mjs and docs/plans/2026-11-06-freighter.md), each with its
// subtitle and, where it differs from the game's own, how far it is heard (heard, in blocks, the volume falling off
// evenly to nothing there), whether it is streamed as music and long loops are rather than loaded whole (stream), the
// pitches its takes are played at, picked one at random each time (takes), and whether it is played to each listener
// aboard as the flight deck's voice is (aboard, see heardAboard).
// How far her engines' roar carries, in blocks: as far as the rocket's, out across the ground round her.
const FREIGHTER_HEARD = 160;
// The pitches the Spookie's two takes of each sound are played at, so it never says one twice quite alike.
const SPOOKIE_TAKES = [0.95, 1.05];
const FREIGHTER_SOUNDS = {
  // The club's song, 96 seconds long.
  club_band: { subtitle: "Swing band plays", stream: true },
  // Her boarding ramp coming down from her belly, heard by those waiting for it on the ground round her.
  ramp_steam: { subtitle: "Ramp vents steam", heard: 32 },
  ramp_hydraulics: { subtitle: "Ramp hydraulics whine", heard: 32 },
  ramp_thud: { subtitle: "Ramp thuds down", heard: 32 },
  // Her engines: their hum inside her hull, heard all through her; their roar, the show piece, heard up to
  // FREIGHTER_HEARD blocks off with her lift jets lighting, her mains spooling up and her pass at speed; her lift
  // jets themselves, as she lifts off and lands, nearer.
  freighter_engine_idle: { subtitle: "Freighter engines hum", heard: 32 },
  freighter_engine_roar: { subtitle: "Freighter engines roar", heard: FREIGHTER_HEARD },
  freighter_lift_jets: { subtitle: "Lift jets blast", heard: 96 },
  freighter_ignition: { subtitle: "Lift jets ignite", heard: FREIGHTER_HEARD },
  freighter_spool: { subtitle: "Main engines spool up", heard: FREIGHTER_HEARD },
  freighter_flyby: { subtitle: "Freighter thunders past", heard: FREIGHTER_HEARD },
  // Her ball turrets firing, heard well off as guns are, and turning; and the laser crossbow fired and charged.
  ball_turret_shot: { subtitle: "Turret fires", heard: 64 },
  ball_turret_servo: { subtitle: "Turret turns" },
  laser_crossbow_shot: { subtitle: "Laser crossbow fires", heard: 32 },
  laser_crossbow_reload: { subtitle: "Laser crossbow charges" },
  // Her jump drive, heard all through her: spooling up, sputtering as it fails, thumped back to life and jumping.
  jump_drive_spool: { subtitle: "Jump drive spools up", heard: 48 },
  jump_drive_sputter: { subtitle: "Jump drive sputters", heard: 48 },
  jump_drive_thump: { subtitle: "Jump drive kicks back in", heard: 48 },
  jump_drive_jump: { subtitle: "Jump drive jumps", heard: 64 },
  // The Spookie's voice, each in two takes a little higher and lower, its roar carrying through her.
  spookie_greet: { subtitle: "Spookie hoots hello", heard: 24, takes: SPOOKIE_TAKES },
  spookie_happy: { subtitle: "Spookie purrs happily", heard: 24, takes: SPOOKIE_TAKES },
  spookie_alarm: { subtitle: "Spookie honks in alarm", heard: 24, takes: SPOOKIE_TAKES },
  spookie_roar: { subtitle: "Spookie bellows", heard: 48, takes: SPOOKIE_TAKES },
  // Her robots' beeps, heard as far as the game's own sounds are.
  robot_chirp: { subtitle: "Robot chirps" },
  robot_trill: { subtitle: "Robot trills" },
  robot_alarm: { subtitle: "Robot beeps in alarm" },
  robot_happy: { subtitle: "Robot beeps happily" },
  // Her radar dish turning on her top, heard on her hull and round her; and her hidden compartments' hatches.
  radar_dish_whirr: { subtitle: "Radar dish whirrs", heard: 24 },
  hidden_hatch_open: { subtitle: "Hidden hatch slides open" },
  hidden_hatch_close: { subtitle: "Hidden hatch slides shut" },
  // Her computer's voice, each line subtitled with what it says, her own words for the flight deck's announcements
  // with their gist.
  freighter_computer_jump_charged: { subtitle: "Computer: Jump drive charged", aboard: true },
  freighter_computer_jump_fault: { subtitle: "Computer: Jump drive fault", aboard: true },
  freighter_computer_ramp_lowering: { subtitle: "Computer: Ramp coming down", aboard: true },
  freighter_computer_turret_ready: { subtitle: "Computer: Gun turret online", aboard: true },
  freighter_computer_jump_countdown: { subtitle: "Computer: Jump in three, two, one", aboard: true },
  freighter_computer_magpie_ready: { subtitle: "Computer: Magpie's awake", aboard: true },
  freighter_computer_takeoff: { subtitle: "Computer: Lifting off, hold on", aboard: true },
  freighter_computer_landing: { subtitle: "Computer: Coming down to land", aboard: true },
  freighter_computer_arrival: { subtitle: "Computer: We're down", aboard: true },
  freighter_computer_space: { subtitle: "Computer: We're in space", aboard: true },
  freighter_computer_gravity: { subtitle: "Computer: Gravity back on", aboard: true },
};
function freighterSounds() {
  return Object.fromEntries(Object.entries(FREIGHTER_SOUNDS).map(([name, { heard, stream, takes, aboard }]) => {
    if (aboard) {
      return [name, heardAboard(name)];
    }
    const sound = { name: `${ID}:${name}`, ...(stream ? { stream } : {}), ...(heard ? { attenuation_distance: heard } : {}) };
    return [name, { subtitle: `subtitles.${ID}.${name}`, sounds: takes ? takes.map((pitch) => ({ ...sound, pitch })) : [sound] }];
  }));
}
function freighterSoundsLang() {
  return Object.fromEntries(Object.entries(FREIGHTER_SOUNDS).map(([name, { subtitle }]) => [`subtitles.${ID}.${name}`, subtitle]));
}
// A rocket's exhaust plume, the clouds round her lift-off and her shockwave's dust share the blast's soft cloud picture
// (see the client's BlastPuffParticle), each kind tinting it.
for (const kind of ["exhaust", "cloud", "dust"]) {
  write(`assets/${ID}/particles/rocket_${kind}.json`, { textures: [`${ID}:blast_puff`] });
}
// A fog machine's fog has a softer picture of its own.
write(`assets/${ID}/particles/fog_puff.json`, { textures: [`${ID}:fog_puff`] });

// The engines' sound and the flight deck's voice (see tools/generate_sounds.mjs), and the contrail particle.
{
  // How far an engine is heard, in blocks: from the flight deck and the far end of the cabin, and by anyone on the
  // ground a few hundred blocks off as the airliner flies over or past. Minecraft fixes how far a sound carries as it
  // starts, from how loud it is then, and an engine's sound starts as it begins to spool up, all but silent; so its
  // reach is set here, not by turning it up.
  const ENGINE_HEARD = 400;
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
  Object.assign(sounds, freighterSounds());
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
  Object.assign(sounds, robotBartenderSounds());
  Object.assign(sounds, crewLockerSounds());
  Object.assign(sounds, gearSounds());
  Object.assign(sounds, airPocketSounds());
  Object.assign(sounds, laserSounds());
  Object.assign(sounds, selfDestructSounds());
  Object.assign(sounds, roverSounds());
  Object.assign(sounds, labSounds());
  Object.assign(sounds, foodLabSounds());
  Object.assign(sounds, roomSounds());
  Object.assign(sounds, snackMachineSounds());
  Object.assign(sounds, greenhouseSounds());
  Object.assign(sounds, airlockPanelSounds());
  Object.assign(sounds, parcelScannerSounds());
  Object.assign(sounds, galleyFridgeSounds());
  Object.assign(sounds, waveMachineSounds());
  Object.assign(sounds, fogMachineSounds());
  Object.assign(sounds, clubBarSounds());
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
  // The DJ deck's record scratched (see tools/generate_sounds.mjs).
  sounds.dj_scratch = { subtitle: `subtitles.${ID}.dj_scratch`, sounds: [{ name: `${ID}:dj_scratch` }] };
  write(`assets/${ID}/sounds.json`, sounds);
  write(`assets/${ID}/particles/contrail.json`, { textures: [`${ID}:contrail`] });
  // A flare's smoke is the same soft puff, tinted.
  write(`assets/${ID}/particles/flare_smoke.json`, { textures: [`${ID}:contrail`] });
  // The "z" rising from her sleeping tardigrade (see SleepParticle), and the glowing spores of its spore drive.
  write(`assets/${ID}/particles/sleep_z.json`, { textures: [`${ID}:sleep_z`] });
  write(`assets/${ID}/particles/spore.json`, { textures: [`${ID}:spore`] });
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
  return { "subtitles.skyliners.air_pocket": "Airliner drops into an air pocket" };
}
function gearRecipes() {
  shaped("gear_lever", ["T", "S", "P"], { T: `${ID}:landing_gear_tire`, S: "minecraft:lever", P: `${ID}:airframe_panel` }, 1);
  shaped("gear_bay", ["P", "G", "I"], { P: `${ID}:airframe_panel`, G: `${ID}:landing_gear`, I: "minecraft:piston" }, 1);
}
function gearLang() {
  return {
    "block.skyliners.gear_bay": "Landing Gear Bay",
    "block.skyliners.gear_lever": "Landing Gear Lever",
    "subtitles.skyliners.gear_retract": "Landing gear retracts",
    "subtitles.skyliners.gear_extend": "Landing gear extends",
    "subtitles.skyliners.gear_horn": "Gear horn sounds",
    "message.skyliners.gear.up": "Gear up",
    "message.skyliners.gear.down": "Gear down",
    "message.skyliners.gear.on_ground": "The gear stays down: the airliner is standing on its wheels",
    "message.skyliners.gear.fixed": "The gear stays down: retractable gear is switched off on this server",
    "message.skyliners.gear.no_room": "The gear stays up: its wheels have no room to come down",
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
    "block.skyliners.laser_switch": "Laser Switch",
    "block.skyliners.laser_turret": "Laser Turret",
    "subtitles.skyliners.laser_hiss": "Laser fires",
    "subtitles.skyliners.laser_charge": "Laser charges",
    "message.skyliners.laser.armed": "Laser path clearer armed",
    "message.skyliners.laser.disarmed": "Laser path clearer safe",
    "message.skyliners.laser.off": "The laser path clearer is switched off on this server",
    "death.attack.skyliners.laser": "%1$s was vaporised by an airliner's laser",
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
  const textures = (look) => ({
    plate: `${ID}:block/self_destruct_plate`, cover: `${ID}:block/self_destruct_cover`, particle: `${ID}:block/self_destruct_plate`,
    button: `${ID}:block/self_destruct_button${look === "armed" ? "_lit" : ""}`,
  });
  for (const [look, parts] of Object.entries(looks)) {
    write(`assets/${ID}/models/block/self_destruct_button_${look}.json`, {
      parent: "minecraft:block/block",
      render_type: "minecraft:translucent",
      textures: textures(look),
      elements: [plate, ...parts],
    });
  }
  // On the floor: the same plate, button and lid, laid flat to face up on a slab of the flight deck's console casing,
  // the plate's underside UNDER pixels up, and all of it tilted toward whoever faces the button by TILT about the
  // middle of the plate's underside, on top of a pedestal of the casing standing on a hazard-striped plinth; the lid
  // flipped up stands a little over the top of the block. SelfDestructButtonBlock has the button's flare where the
  // tilt takes it, and its outline in steps under the tilted head.
  const TILT = 22.5;
  const UNDER = 9.5;
  const flatBox = (from, to, top, edge, shade = true) => ({
    from, to, ...(shade ? {} : { shade: false }),
    faces: { up: top, north: edge, south: edge, east: edge, west: edge, down: edge },
  });
  const flatButton = (lit) =>
    flatBox([5.5, UNDER + 1.5, 5.5], [10.5, UNDER + 4, 10.5], face("#button", null, lit), face("#button", [0, 0, 16, 4], lit), !lit);
  const flatShut = {
    from: [4.5, UNDER + 1.5, 4.5], to: [11.5, UNDER + 5.5, 11.5],
    faces: { up: face("#cover"), north: lidEdge, south: lidEdge, east: lidEdge, west: lidEdge },
  };
  // Flipped up, its striped edge is at its hinge, as on a wall.
  const openCover = face("#cover", [4.5, 8.5, 11.5, 1.5]);
  const flatUp = {
    from: [4.5, UNDER + 1.5, 3.5], to: [11.5, UNDER + 8.5, 4.5],
    faces: { north: openCover, south: openCover, up: lidEdge, east: lidEdge, west: lidEdge },
  };
  const head = [
    flatBox([1.5, UNDER - 3, 1.5], [14.5, UNDER, 14.5], face("#casing"), face("#casing")),
    flatBox([2, UNDER, 2], [14, UNDER + 1.5, 14], face("#plate"), face("#plate", [0, 0, 16, 2])),
  ];
  const flatLooks = { shut: [flatButton(false), flatShut], open: [flatButton(false), flatUp], armed: [flatButton(true), flatUp] };
  const tilted = (element) => ({ ...element, rotation: { angle: TILT, axis: "x", origin: [8, UNDER, 8] } });
  const pedestal = [solid([4, 1.5, 5], [12, 7.75, 11], "#casing"), solid([2.5, 0, 3], [13.5, 1.5, 13], "#hazard")];
  for (const [look, parts] of Object.entries(flatLooks)) {
    write(`assets/${ID}/models/block/self_destruct_button_floor_${look}.json`, {
      parent: "minecraft:block/block",
      render_type: "minecraft:translucent",
      textures: { ...textures(look), casing: `${ID}:block/flight_console`, hazard: `${ID}:block/shaft_decor_hazard` },
      elements: [...pedestal, ...[...head, ...parts].map(tilted)],
    });
  }
  const buttonLook = (open, armed) => (armed ? "armed" : open ? "open" : "shut");
  write(`assets/${ID}/blockstates/self_destruct_button.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
      [false, true].flatMap((armed) => ["wall", "floor"].flatMap((mount) => [false, true].map((open) => [
        `armed=${armed},facing=${facing},mount=${mount},open=${open}`,
        { model: `${ID}:block/self_destruct_button_${mount === "wall" ? "" : `${mount}_`}${buttonLook(open, armed)}`, y },
      ]))))),
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
    "block.skyliners.self_destruct_button": "Self-Destruct Button",
    "subtitles.skyliners.self_destruct_siren": "Self-destruct alarm wails",
    "subtitles.skyliners.self_destruct_blast": "Ship explodes",
    "subtitles.skyliners.self_destruct_rumble": "Explosion rumbles",
    "message.skyliners.self_destruct.open": "Cover up: press the red button to arm the self-destruct",
    "message.skyliners.self_destruct.shut": "Cover shut",
    "message.skyliners.self_destruct.armed": "Self-destruct armed! The ship blows up in %s seconds: press again to blow it up now, or sneak to cancel",
    "message.skyliners.self_destruct.alarm": "Self-destruct armed! Everyone will get a parachute",
    "message.skyliners.self_destruct.cancelled": "Self-destruct cancelled",
    "message.skyliners.self_destruct.assembling": "Getting the ship ready: the self-destruct arms in a moment",
    "message.skyliners.self_destruct.not_assembled": "Nothing happens: the self-destruct only works aboard a ship built from its blueprint",
    "message.skyliners.self_destruct.off": "The self-destruct is switched off on this server",
    "message.skyliners.self_destruct.parachute": "Boom! Your parachute opens by itself: steer it the way you walk",
  };
}
// The rocket's centre console's controls, by their block names.
const ROCKET_CONTROLS = ["rocket_boost_button", "rocket_stability_switch", "rocket_mode_knob", "rocket_abort_handle", "rocket_launch_key"];
// The hands-on controls on a rocket's centre console (see RocketControlBlock and its kinds), each a module of the
// console drawn with the pilots at its north side, whose panel reads the right way up for them, its viewer's left at
// its east side. Every module is laid out alike, so the console reads as one: its picture plate across the half nearest
// the pilots, saying what it does and glowing in the colour of what it shows (see rocketControlTextures in
// generate_textures.mjs), and its control standing on the far half, behind the plate (ConsoleModule.BEHIND_PLATE). The
// boost button: a short chrome pedestal, a ring of four lamp segments round its foot, and its cap over it, a wide flat
// round lens in two tiers, glowing as it shows (dark, ready, boosting, resting); the ring's segments light green one by
// one as it rests, clockwise from the far side as the pilots see it, all green once it is ready again, and orange while
// it boosts. The stability switch: a guarded toggle leaning back on and forward off, between two guard rails, its
// yellow lid shut over it or flipped up behind it, hazard stripes painted round it. The flight mode knob: a low black
// knob on a chrome skirt, the pointer on its top turned to its mode's picture on the plate below it, glowing in its
// colour (red refused). The abort handle: a red T-handle on a stalk, pulled up a moment as it is pulled. The launch
// key: a key in its lock, turned a quarter on.
{
  const lit = (element) => ({ ...element, faces: Object.fromEntries(Object.entries(element.faces).map(([face, value]) =>
    [face, { ...value, neoforge_data: { block_light: 15, sky_light: 15 } }])) });
  const facingOf = (variants) => Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
    variants.map(([key, model]) => [`facing=${facing}${key ? `,${key}` : ""}`, { model: `${ID}:block/${model}`, y }])));
  const plain = { plate: `${ID}:block/panel_side`, chrome: `${ID}:block/pilot_seat_trim`, black: `${ID}:block/thrust_lever_handle` };
  // Where a control stands, behind its plate: the middle of the module across, this far from its pilots' edge, in
  // pixels (as ConsoleModule.BEHIND_PLATE).
  const BEHIND = 11.25;
  const at = (x0, y0, z0, x1, y1, z1) => [[x0, y0, BEHIND + z0], [x1, y1, BEHIND + z1]];
  // The picture plate, a pane across the half nearest the pilots, its picture in the top half of its texture.
  const PANE = {
    from: [2, CONSOLE_HEIGHT, 0.75], to: [14, CONSOLE_HEIGHT + 0.25, 6.75],
    faces: {
      up: { texture: "#picture", uv: [0, 0, 16, 8], rotation: 180 },
      north: { texture: "#casing" }, south: { texture: "#casing" }, east: { texture: "#casing" }, west: { texture: "#casing" },
    },
  };
  // A control's module: its plate showing the picture named, glowing or not, and the control's own parts behind it.
  const controlModule = (name, textures, picture, glowing, parts) => consoleModule(name,
    { ...plain, picture: `${ID}:block/rocket_plate_${picture}`, ...textures }, "#plate", true, [glowing ? lit(PANE) : PANE, ...parts]);

  // The boost button: its looks, and the cap and the segments lit for each.
  const BOOST_LOOKS = ["dark", "ready", "boosting", "cooling_0", "cooling_1", "cooling_2", "cooling_3", "refused"];
  const capOf = (look) => (look.startsWith("cooling") || look === "refused" ? "resting" : look);
  const segmentsLit = (look) => (look === "ready" || look === "boosting" ? 4 : look.startsWith("cooling") ? Number(look.slice(-1)) : 0);
  const SEGMENTS = [at(5.5, 6, 2.5, 10.5, 7, 4.5), at(3.5, 6, -2.5, 5.5, 7, 2.5), at(5.5, 6, -4.5, 10.5, 7, -2.5), at(10.5, 6, -2.5, 12.5, 7, 2.5)];
  const sides = (texture) => ({ north: { texture }, south: { texture }, east: { texture }, west: { texture }, down: { texture } });
  for (const look of BOOST_LOOKS) {
    const cap = capOf(look);
    const textures = {
      cap_top: `${ID}:block/rocket_boost_cap_${cap}`, cap_side: `${ID}:block/rocket_boost_cap_side_${cap}`,
      segment: `${ID}:block/${look === "boosting" ? "rocket_lamp_orange" : "jump_light_green"}`, segment_off: `${ID}:block/jump_light_off`,
    };
    const [lowFrom, lowTo] = at(4, 9, -4, 12, 10.75, 4);
    const [highFrom, highTo] = at(5, 10.75, -3, 11, 11.5, 3);
    const tiers = [
      { from: lowFrom, to: lowTo, faces: { ...sides("#cap_side"), up: { texture: "#cap_side" } } },
      { from: highFrom, to: highTo, faces: { ...sides("#cap_side"), up: { texture: "#cap_top", rotation: 180 } } },
    ];
    const segments = SEGMENTS.map(([from, to], k) => (k < segmentsLit(look) ? lit(solid(from, to, "#segment")) : solid(from, to, "#segment_off")));
    controlModule(`rocket_boost_button_${look}`, textures, `boost_${cap}`, cap !== "dark", [
      solid(...at(6.5, CONSOLE_HEIGHT, -1.5, 9.5, 9, 1.5), "#chrome"),
      ...segments,
      ...(cap === "dark" ? tiers : tiers.map(lit)),
    ]);
  }
  write(`assets/${ID}/blockstates/rocket_boost_button.json`, { variants: facingOf(BOOST_LOOKS.map((look) => [`look=${look}`, `rocket_boost_button_${look}`])) });
  write(`assets/${ID}/models/item/rocket_boost_button.json`, { parent: `${ID}:block/rocket_boost_button_dark` });

  // The stability switch: a guarded toggle, as on an aircraft's panel. A border of hazard stripes painted on the panel
  // round it, the toggle between two guard rails, and the yellow lid lying on the rails over it, a lip at its front to
  // lift it by, or flipped up on its hinge at the rails' back, standing behind the toggle with its lip toward the pilots.
  const STABILITY_LAMPS = ["holding", "off", "locked", "refused"];
  const border = [at(4, CONSOLE_HEIGHT, -4, 12, 6.25, -3), at(4, CONSOLE_HEIGHT, 3, 12, 6.25, 4), at(4, CONSOLE_HEIGHT, -3, 5, 6.25, 3),
    at(11, CONSOLE_HEIGHT, -3, 12, 6.25, 3)].map(([from, to]) => solid(from, to, "#stripes"));
  const lid = (open) => (open
    ? [solid(...at(5, 10, 2.25, 11, 14.5, 3), "#lid"), solid(...at(5, 13.75, 1.5, 11, 14.5, 2.25), "#lid")]
    : [solid(...at(5, 10, -2.25, 11, 10.75, 2.25), "#lid"), solid(...at(5, 9.25, -2.25, 11, 10, -1.5), "#lid")]);
  for (const open of [false, true]) {
    for (const lamp of STABILITY_LAMPS) {
      const rotation = { origin: [8, 6.5, BEHIND], axis: "x", angle: lamp === "off" ? 22.5 : -22.5 };
      controlModule(`rocket_stability_switch_${open ? "open" : "shut"}_${lamp}`, {
        knob: `${ID}:block/thrust_lever_knob`, stripes: `${ID}:block/rocket_hazard_stripes`, lid: `${ID}:block/rocket_switch_lid`,
      }, `stability_${lamp}`, true, [
        ...border,
        solid(...at(5, CONSOLE_HEIGHT, -2, 5.75, 10, 2.25), "#black"),
        solid(...at(10.25, CONSOLE_HEIGHT, -2, 11, 10, 2.25), "#black"),
        solid(...at(7, CONSOLE_HEIGHT, -1, 9, 7, 1), "#black"),
        solid(...at(7.5, 6.5, -0.5, 8.5, 9, 0.5), "#chrome", rotation),
        solid(...at(7.25, 8.5, -0.75, 8.75, 10, 0.75), "#knob", rotation),
        ...lid(open),
      ]);
    }
  }
  write(`assets/${ID}/blockstates/rocket_stability_switch.json`, {
    variants: facingOf(STABILITY_LAMPS.flatMap((lamp) => [false, true].map((open) =>
      [`lamp=${lamp},open=${open}`, `rocket_stability_switch_${open ? "open" : "shut"}_${lamp}`]))),
  });
  write(`assets/${ID}/models/item/rocket_stability_switch.json`, { parent: `${ID}:block/rocket_stability_switch_shut_holding` });

  // The flight mode knob: the pointer on its top turned from the knob's middle toward each mode's picture on the plate,
  // the arrow up at the viewer's left (east), the arc ahead of them and the snail at their right, in each mode's colour.
  const MODES = { vertical: [-45, "rocket_lamp_blue"], arc: [0, "rocket_lamp_orange"], gentle: [45, "jump_light_green"] };
  for (const [mode, [angle, colour]] of Object.entries(MODES)) {
    for (const refused of [false, true]) {
      const pointer = { origin: [8, 9, BEHIND], axis: "y", angle };
      controlModule(`rocket_mode_knob_${mode}${refused ? "_refused" : ""}`, {
        pointer: `${ID}:block/${refused ? "jump_light_red" : colour}`,
      }, `mode_${mode}${refused ? "_refused" : ""}`, true, [
        solid(...at(5, CONSOLE_HEIGHT, -3, 11, 6.75, 3), "#chrome"),
        solid(...at(6, 6.75, -2, 10, 9, 2), "#black"),
        lit(solid(...at(7.4, 9, -2, 8.6, 9.35, 0), "#pointer", pointer)),
      ]);
    }
  }
  write(`assets/${ID}/blockstates/rocket_mode_knob.json`, {
    variants: facingOf(Object.keys(MODES).flatMap((mode) => [false, true].map((refused) =>
      [`mode=${mode},refused=${refused}`, `rocket_mode_knob_${mode}${refused ? "_refused" : ""}`]))),
  });
  write(`assets/${ID}/models/item/rocket_mode_knob.json`, { parent: `${ID}:block/rocket_mode_knob_vertical` });

  // The abort handle, its plate lit while it is ready.
  for (const ready of [false, true]) {
    for (const pulled of [false, true]) {
      const rise = pulled ? 3 : 0;
      controlModule(`rocket_abort_handle_${ready ? "lit" : "dark"}_${pulled ? "out" : "in"}`, {
        handle: `${ID}:block/rocket_abort_grip`,
      }, `abort_${ready ? "lit" : "dark"}`, ready, [
        solid(...at(7, CONSOLE_HEIGHT, -1, 9, 8 + rise, 1), "#black"),
        solid(...at(3.5, 8 + rise, -1.5, 12.5, 10 + rise, 1.5), "#handle"),
      ]);
    }
  }
  write(`assets/${ID}/blockstates/rocket_abort_handle.json`, {
    variants: mountedVariants([false, true].flatMap((pulled) => [false, true].map((ready) =>
      [`pulled=${pulled},ready=${ready}`, `rocket_abort_handle_${ready ? "lit" : "dark"}_${pulled ? "out" : "in"}`]))),
  });
  write(`assets/${ID}/models/item/rocket_abort_handle.json`, { parent: `${ID}:block/rocket_abort_handle_dark_in` });

  // The launch key, off and turned a quarter on.
  for (const turned of [false, true]) {
    controlModule(`rocket_launch_key_${turned ? "on" : "off"}`, {}, `key_${turned ? "on" : "off"}`, true, [
      solid(...at(5.5, CONSOLE_HEIGHT, -2.5, 10.5, 6.5, 2.5), "#chrome"),
      solid(...at(6.5, 6.5, -1.5, 9.5, 7.5, 1.5), "#black"),
      solid(...at(7.5, 7.5, -0.5, 8.5, 8, 0.5), "#chrome"),
      turned ? solid(...at(7.5, 8, -1.5, 8.5, 11, 1.5), "#chrome") : solid(...at(6.5, 8, -0.5, 9.5, 11, 0.5), "#chrome"),
    ]);
  }
  write(`assets/${ID}/blockstates/rocket_launch_key.json`, {
    variants: mountedVariants([false, true].map((turned) => [`turned=${turned}`, `rocket_launch_key_${turned ? "on" : "off"}`])),
  });
  write(`assets/${ID}/models/item/rocket_launch_key.json`, { parent: `${ID}:block/rocket_launch_key_off` });

  for (const name of ROCKET_CONTROLS) {
    dropsItself(name);
    cubes[name] = null;
  }
}
// The modules of the rocket's centre console, each on a plate of the airframe: the boost button an orange-dyed stone
// button, the stability switch a lever between yellow and black dye, the flight mode knob a comparator, the abort
// handle a tripwire hook between yellow and black dye, and the launch key a tripwire hook with a gold nugget for its
// key.
function rocketControlRecipes() {
  const P = `${ID}:airframe_panel`;
  shaped("rocket_boost_button", ["O", "B", "P"], { O: "minecraft:orange_dye", B: "minecraft:stone_button", P }, 1);
  shaped("rocket_stability_switch", ["YLK", " P "], { Y: "minecraft:yellow_dye", L: "minecraft:lever", K: "minecraft:black_dye", P }, 1);
  shaped("rocket_mode_knob", ["C", "P"], { C: "minecraft:comparator", P }, 1);
  shaped("rocket_abort_handle", ["YTK", " P "], { Y: "minecraft:yellow_dye", T: "minecraft:tripwire_hook", K: "minecraft:black_dye", P }, 1);
  shaped("rocket_launch_key", ["G", "T", "P"], { G: "minecraft:gold_nugget", T: "minecraft:tripwire_hook", P }, 1);
}
function rocketControlLang() {
  return {
    "block.skyliners.rocket_boost_button": "Boost Button",
    "block.skyliners.rocket_stability_switch": "Stability Switch",
    "block.skyliners.rocket_mode_knob": "Flight Mode Knob",
    "block.skyliners.rocket_abort_handle": "Abort Handle",
    "block.skyliners.rocket_launch_key": "Launch Key",
    "message.skyliners.rocket_controls.boost": "Boost! Engines kicked up",
    "message.skyliners.rocket_controls.switched_off": "This control is switched off on this server",
    "message.skyliners.rocket_controls.boost_resting": "The boost is resting: wait for the green light",
    "message.skyliners.rocket_controls.boost_not_now": "No boost now: only climbing or cruising",
    "message.skyliners.rocket_controls.boost_light": "No boost now: everyone aboard is floating",
    "message.skyliners.rocket_controls.stability_cover": "Cover up: flip the switch to turn stability off. Danger!",
    "message.skyliners.rocket_controls.stability_off": "Stability off! She is tipping over: flip the switch back before she tips too far",
    "message.skyliners.rocket_controls.stability_on": "Stability on: you saved her!",
    "message.skyliners.rocket_controls.stability_locked": "Stability is locked on for a moment",
    "message.skyliners.rocket_controls.stability_not_now": "Not now: only cruising, high over the ground",
    "message.skyliners.rocket_controls.stability_unsteady": "Not now: wait until she is not boosting or rushing across",
    "message.skyliners.rocket_controls.arc_switched_off": "The arc is switched off on this server: she flies straight up",
    "message.skyliners.rocket_controls.mode_vertical": "Flight mode: straight up",
    "message.skyliners.rocket_controls.mode_arc": "Flight mode: arc, leaning toward the pin as she climbs",
    "message.skyliners.rocket_controls.mode_gentle": "Flight mode: gentle and slow",
    "message.skyliners.rocket_controls.mode_vertical_later": "Straight up next flight",
    "message.skyliners.rocket_controls.mode_arc_later": "Arc next flight",
    "message.skyliners.rocket_controls.mode_gentle_later": "Gentle next flight",
    "message.skyliners.rocket_controls.not_rocket": "This control only works on a rocket",
    "message.skyliners.rocket_controls.no_autopilot": "Nothing happens: there is no rocket's control head near",
    "message.skyliners.rocket_controls.abort_nothing": "Nothing to abort",
    "message.skyliners.rocket_controls.abort_go_around": "Landing aborted: climbing back to %s and holding",
    "message.skyliners.rocket_controls.abort_hold": "Aborted: holding here at %s",
    "message.skyliners.rocket_controls.key_stuck": "The key is stuck while she flies",
    "message.skyliners.rocket_controls.scrubbed": "Launch scrubbed",
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
  // A jump light looks as what it shows; what a jump switch put up by hand set it to (switched, see JumpLightBlock)
  // changes nothing of its look, so its variants leave it out.
  write(`assets/${ID}/blockstates/jump_light.json`, {
    variants: Object.fromEntries(signals.map((signal) => [`signal=${signal}`, { model: `${ID}:block/jump_light_${signal}` }])),
  });
  write(`assets/${ID}/blockstates/jump_switch.json`, {
    variants: mountedVariants(signals.map((signal) => [`signal=${signal}`, `jump_switch_${signal}`])),
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
// A console module that can be put up on a floor, a wall or a ceiling (see MountedModule): each of its looks, a
// model drawn standing on the floor with its face looking up and its viewer at its north side, tipped over onto the
// wall it looks out from or hung face down under the ceiling, then turned for each facing (TransponderRenderer tips
// its screen the same way). The looks are [the properties choosing it, its model].
function mountedVariants(looks) {
  const tilt = { floor: 0, wall: 90, ceiling: 180 };
  return Object.fromEntries(looks.flatMap(([key, model]) => Object.entries(tilt).flatMap(([mount, x]) => Object.entries(yRot).map(([facing, y]) =>
    [[key, `facing=${facing}`, `mount=${mount}`].filter(Boolean).sort().join(","), { model: `${ID}:block/${model}`, ...(x ? { x } : {}), y }]))));
}
function mountedFacing(name) {
  write(`assets/${ID}/blockstates/${name}.json`, { variants: mountedVariants([["", name]]) });
}
// A monitor on its stand or hung on a wall (see TowerRadarBlock): its stand's model and its wall model, turned for each
// facing.
function monitorFacing(name) {
  write(`assets/${ID}/blockstates/${name}.json`, {
    variants: Object.fromEntries(["floor", "wall"].flatMap((mount) => Object.entries(yRot).map(([facing, y]) =>
      [`facing=${facing},mount=${mount}`, { model: `${ID}:block/${name}${mount === "wall" ? "_wall" : ""}`, y }]))),
  });
}
// The tones the rocket's lights glow in (see LightTone).
const LIGHT_TONES = ["warm", "cool", "pink"];
// The wall washer and the floor light, the halo light put on a wall or the ground: broken, each gives back the Halo
// Light it was put up as (see LightFittingItem).
function dropsHaloLight(name) {
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:halo_light` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
    random_sequence: `${ID}:blocks/${name}`,
  });
}
// An item that keeps what its block showed when broken or picked, drawn as what it keeps (see the client's KeptLooks):
// for each block state property it keeps, by name, its values in the Java enum's order, and an override for each of
// their combinations, chosen by how far through each property's values its value is; the plain look, a stack's from
// the creative tab, is the model's own.
function keptLooks(item, looks, modelOf, plain) {
  const names = Object.keys(looks);
  const combinations = names.reduce((sofar, name) => sofar.flatMap((combination) => looks[name].map((value) => [...combination, value])), [[]]);
  write(`assets/${ID}/models/item/${item}.json`, {
    parent: modelOf(...plain),
    overrides: combinations.map((combination) => ({
      predicate: Object.fromEntries(names.map((name, at) => [`${ID}:${name}`, looks[name].indexOf(combination[at]) / looks[name].length])),
      model: modelOf(...combination),
    })),
  });
}
// Something of several blocks, each a state of one block, that gives itself back from only one of them: the state's
// properties say which, as { half: "lower" } or { part: "0" }.
function dropsFrom(name, properties) {
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:${name}` }],
      conditions: [{ condition: "minecraft:block_state_property", block: `${ID}:${name}`, properties }, { condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/${name}`,
  });
}
// What gives itself back keeping a block state property, as picking it does; the plain value is left off, so they
// stack.
function dropsKeeping(name, property, plain) {
  const block = `${ID}:${name}`;
  write(`data/${ID}/loot_table/blocks/${name}.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item",
        name: block,
        functions: [{
          function: "minecraft:copy_state", block, properties: [property],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block, properties: { [property]: plain } } }],
        }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/${name}`,
  });
}
// How something two blocks tall is held and shown small enough to fit, as the lavatory basin's item is.
const TALL_ITEM_DISPLAY = {
  gui: { rotation: [30, 225, 0], translation: [0, -3, 0], scale: [0.4, 0.4, 0.4] },
  ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.15, 0.15, 0.15] },
  fixed: { rotation: [0, 0, 0], translation: [0, -3, 0], scale: [0.3, 0.3, 0.3] },
  thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: [0.22, 0.22, 0.22] },
  firstperson_righthand: { rotation: [0, 45, 0], translation: [0, -1, 0], scale: [0.25, 0.25, 0.25] },
  firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, -1, 0], scale: [0.25, 0.25, 0.25] },
};
// The item of something two blocks tall, drawn whole: its lower block's model, as already written, with its upper's
// over it, whose textures keep their own names so that two halves with the same name for different pictures stay apart.
function tallItem(name, lower, upper) {
  const written = (model) => JSON.parse(readFileSync(resolve(ROOT, `assets/${ID}/models/block/${model}.json`), "utf8"));
  const under = written(lower);
  const over = written(upper);
  const upperName = (texture) => `#upper_${texture.slice(1)}`;
  const raised = over.elements.map((element) => ({
    ...element,
    from: [element.from[0], element.from[1] + 16, element.from[2]],
    to: [element.to[0], element.to[1] + 16, element.to[2]],
    ...(element.rotation ? { rotation: { ...element.rotation, origin: [element.rotation.origin[0], element.rotation.origin[1] + 16, element.rotation.origin[2]] } } : {}),
    faces: Object.fromEntries(Object.entries(element.faces).map(([side, face]) => [side, { ...face, texture: upperName(face.texture) }])),
  }));
  write(`assets/${ID}/models/item/${name}.json`, {
    parent: "minecraft:block/block",
    ...(under.render_type ? { render_type: under.render_type } : {}),
    textures: { ...under.textures, ...Object.fromEntries(Object.entries(over.textures).map(([key, texture]) => [`upper_${key}`, texture])) },
    elements: [...under.elements, ...raised],
    display: TALL_ITEM_DISPLAY,
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
  mountedFacing("handset");
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
// code knobs at the front of it (its picture is drawn on the panel's screen, see TransponderRenderer), standing on a
// floor or put up on a wall or a ceiling. The radar screen: a monitor on a desk stand, drawn facing north, its screen's
// face on its north side (the scope is drawn on it, see TowerRadarRenderer).
{
  consoleModule("transponder", { panel: `${ID}:block/transponder_top`, knob: `${ID}:block/thrust_lever_knob` }, "#panel", true, [
    solid([3.5, CONSOLE_HEIGHT, 1.5], [5.5, 7.5, 3.5], "#knob"),
    solid([10.5, CONSOLE_HEIGHT, 1.5], [12.5, 7.5, 3.5], "#knob"),
  ]);
  mountedFacing("transponder");
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
  // Hung on a wall, the monitor alone, its back against the wall (TowerRadarBlock.WALL_SCREEN_BACK).
  write(`assets/${ID}/models/block/tower_radar_wall.json`, {
    parent: "minecraft:block/block",
    textures: { casing: `${ID}:block/tower_radar_casing`, screen: `${ID}:block/tower_radar_screen`, particle: `${ID}:block/tower_radar_casing` },
    elements: [
      { from: [0.5, 3, 12], to: [15.5, 15, 16], faces: { north: { texture: "#screen" }, south: casing, east: casing, west: casing, up: casing, down: casing } },
    ],
  });
  monitorFacing("tower_radar");
  write(`assets/${ID}/models/item/tower_radar.json`, { parent: `${ID}:block/tower_radar` });
  write(`assets/${ID}/models/item/launch_pad_blueprint.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/launch_pad_blueprint` } });
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
  write(`assets/${ID}/models/block/atis_screen_wall.json`, { parent: `${ID}:block/tower_radar_wall` });
  monitorFacing("atis_screen");
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
    "block.skyliners.wind_sock": "Wind Sock",
    "block.skyliners.atis_screen": "Weather Screen",
    "screen.skyliners.atis.title": "ATIS",
    "screen.skyliners.atis.information": "INFORMATION",
    "screen.skyliners.atis.wind": "WIND",
    "screen.skyliners.atis.wind.value": "%s° %sKT",
    "screen.skyliners.atis.wind.calm": "CALM",
    "screen.skyliners.atis.visibility": "VISIBILITY",
    "screen.skyliners.atis.visibility.km": "%sKM",
    "screen.skyliners.atis.visibility.metres": "%sM",
    "weather.skyliners.clear": "CLEAR",
    "weather.skyliners.rain": "RAIN",
    "weather.skyliners.snow": "SNOW",
    "weather.skyliners.thunder": "STORM",
    "block.skyliners.flight_strip_board": "Flight Strip Board",
    "screen.skyliners.flight_strips.column.callsign": "CALLSIGN",
    "screen.skyliners.flight_strips.column.height": "ALT",
    "screen.skyliners.flight_strips.column.distance": "DIST",
    "screen.skyliners.flight_strips.column.heading": "HDG",
    "screen.skyliners.flight_strips.column.phase": "STATUS",
    "screen.skyliners.flight_strips.page": "PAGE %s/%s",
    "screen.skyliners.flight_strips.none": "NO TRAFFIC",
    "screen.skyliners.flight_strips.callsign": "%s%s",
    "screen.skyliners.flight_strips.callsign.tower": "TWR",
    "screen.skyliners.flight_strips.callsign.airliner": "AER",
    "screen.skyliners.flight_strips.callsign.house": "HOME",
    "screen.skyliners.flight_strips.callsign.star": "STAR",
    "screen.skyliners.flight_strips.callsign.flag": "FLAG",
    "screen.skyliners.flight_strips.callsign.heart": "HRT",
    "screen.skyliners.flight_strips.callsign.gem": "GEM",
    "screen.skyliners.flight_strips.callsign.tree": "TREE",
    "screen.skyliners.flight_strips.callsign.anchor": "PORT",
    "screen.skyliners.flight_strips.callsign.pickaxe": "MINE",
    "screen.skyliners.flight_strips.phase.parked": "PARKED",
    "screen.skyliners.flight_strips.phase.taxi": "TAXI",
    "screen.skyliners.flight_strips.phase.climb": "CLIMB",
    "screen.skyliners.flight_strips.phase.cruise": "CRUISE",
    "screen.skyliners.flight_strips.phase.descend": "DESCEND",
    "screen.skyliners.flight_strips.phase.inbound": "INBOUND",
    "screen.skyliners.flight_strips.phase.fix": "FIX",
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
    variants: mountedVariants([false, true].map((on) => [`powered=${on}`, `seatbelt_switch${on ? "_on" : ""}`])),
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
  // The pendant light's shade and the glow under it, the glow's underside so many pixels up.
  const pendantShade = (low, lit) => [[[3, low + 0.5, 3], [13, low + 4, 13], "#shade"], [[4, low, 4], [12, low + 0.5, 12], "#glow", lit]];
  // The pendant light for each way it is put up (see PendantLightBlock.MOUNT), by its model's suffix: hung on its cable
  // from the ceiling; on the floor a floor lamp, the shade at the top of its block on a slim black pole over a weighted
  // round base in the shade's grey; and on a wall, its back on the south side of its block, a wall sconce, the shade
  // hung as low as the pendant's on a short drop of cable from the end of a grey arm out from a small wall plate.
  const pendantMounts = (lit) => ({
    "": [[[7.5, 8, 7.5], [8.5, 16, 8.5], "#cable"], ...pendantShade(4, lit)],
    _floor: [
      [[4, 0, 6], [12, 1, 10], "#shade"],
      [[5, 0, 5], [11, 1, 11], "#shade"],
      [[6, 0, 4], [10, 1, 12], "#shade"],
      [[6.5, 1, 6.5], [9.5, 1.5, 9.5], "#shade"],
      [[7.5, 1.5, 7.5], [8.5, 12, 8.5], "#cable"],
      ...pendantShade(12, lit),
    ],
    _wall: [
      [[6.5, 10.5, 15], [9.5, 15.5, 16], "#shade"],
      [[7.5, 12.5, 8.5], [8.5, 13.5, 15], "#shade"],
      [[7.5, 8, 7.5], [8.5, 13.5, 8.5], "#cable"],
      ...pendantShade(4, lit),
    ],
  });
  for (const [suffix, glow, lit] of [...Object.values(brightness), ["_alarm", "light_strip_alarm", true]]) {
    write(`assets/${ID}/models/block/light_strip${suffix}.json`, boxes(
      { strip: `${ID}:block/${glow}`, particle: `${ID}:block/${glow}` },
      [[[0, 12, 0], [16, 15, 3], "#strip", lit]],
    ));
    for (const [mount, parts] of Object.entries(pendantMounts(lit))) {
      write(`assets/${ID}/models/block/pendant_light${mount}${suffix}.json`, boxes(
        { cable: `${ID}:block/light_cable`, shade: `${ID}:block/pendant_shade`, glow: `${ID}:block/${glow}`, particle: `${ID}:block/pendant_shade` },
        parts,
      ));
    }
  }
  write(`assets/${ID}/blockstates/light_strip.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [
      ...Object.entries(brightness).map(([level, [suffix]]) =>
        [`alarm=false,brightness=${level},facing=${facing}`, { model: `${ID}:block/light_strip${suffix}`, y }]),
      [`alarm=true,facing=${facing}`, { model: `${ID}:block/light_strip_alarm`, y }],
    ])),
  });
  // Hung or standing, the pendant light is round and looks the same whichever way it faces; on a wall it faces out.
  const pendantVariants = (mount, keys, turn = {}) => [
    ...Object.entries(brightness).map(([level, [suffix]]) =>
      [`alarm=false,brightness=${level},${keys}`, { model: `${ID}:block/pendant_light${mount}${suffix}`, ...turn }]),
    [`alarm=true,${keys}`, { model: `${ID}:block/pendant_light${mount}_alarm`, ...turn }],
  ];
  write(`assets/${ID}/blockstates/pendant_light.json`, {
    variants: Object.fromEntries([
      ...pendantVariants("", "mount=ceiling"),
      ...pendantVariants("_floor", "mount=floor"),
      ...Object.entries(yRot).flatMap(([facing, y]) => pendantVariants("_wall", `facing=${facing},mount=wall`, { y })),
    ]),
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

// The rocket's halo light (see HaloLightBlock), drawn as the pendant's cable comes down onto it: a slim black cable to
// a small dark hub, four fine spokes out from it, and round them a ring of light a little under the hub, glowing at
// full brightness, warm white, a cool night-light blue or pink. The ring is sixteen short segments round a circle, each
// turned to run along it: a model's elements turn only in steps of 22.5 degrees, so each lies along one of them, a
// segment running across (x) turned by up to 45 degrees either way, or one running along (z) where it would need more.
{
  const HALO = { radius: 5.25, thick: 1.25, low: 6, high: 7.25, length: 2.4, segments: 16 };
  // The floor light's ring, lower and smaller, round the top of its drum.
  const FLOOR = { radius: 4.25, thick: 1, low: 1.5, high: 2.25, length: 1.9, segments: 16 };
  const ring = (glow, HALO) => Array.from({ length: HALO.segments }, (_, k) => {
    const round = (360 / HALO.segments) * k;
    const x = 8 + HALO.radius * Math.cos((round * Math.PI) / 180);
    const z = 8 + HALO.radius * Math.sin((round * Math.PI) / 180);
    // Turned this much a segment running across lies along the circle here; out of reach, one running along does.
    const across = ((((90 - round) % 180) + 270) % 180) - 90;
    const alongX = Math.abs(across) <= 45;
    const angle = alongX ? across : ((((across - 90) % 180) + 270) % 180) - 90;
    const [halfX, halfZ] = alongX ? [HALO.length / 2, HALO.thick / 2] : [HALO.thick / 2, HALO.length / 2];
    return {
      from: [x - halfX, HALO.low, z - halfZ], to: [x + halfX, HALO.high, z + halfZ], shade: false,
      rotation: { angle, axis: "y", origin: [x, (HALO.low + HALO.high) / 2, z] },
      faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, {
        texture: glow, neoforge_data: { block_light: 15, sky_light: 15 },
      }])),
    };
  });
  const spokes = [[[7.75, 7, 3.25], [8.25, 7.5, 7], "#hub"], [[7.75, 7, 9], [8.25, 7.5, 12.75], "#hub"],
    [[3.25, 7, 7.75], [7, 7.5, 8.25], "#hub"], [[9, 7, 7.75], [12.75, 7.5, 8.25], "#hub"]];
  for (const tone of LIGHT_TONES) {
    const model = boxes(
      { cable: `${ID}:block/light_cable`, hub: `${ID}:block/halo_light_hub`, glow: `${ID}:block/halo_light_${tone}`, particle: `${ID}:block/halo_light_hub` },
      [[[7.5, 8, 7.5], [8.5, 16, 8.5], "#cable"], [[7, 7, 7], [9, 8, 9], "#hub"], ...spokes],
    );
    model.elements.push(...ring("#glow", HALO));
    write(`assets/${ID}/models/block/halo_light_${tone}.json`, model);
  }
  write(`assets/${ID}/blockstates/halo_light.json`, {
    variants: Object.fromEntries(LIGHT_TONES.map((tone) => [`tone=${tone}`, { model: `${ID}:block/halo_light_${tone}` }])),
  });
  write(`assets/${ID}/models/item/halo_light.json`, { parent: `${ID}:block/halo_light_warm` });
  dropsItself("halo_light");
  cubes.halo_light = null;
  // The halo light put down on the ground (see FloorLightBlock): a low dark drum, a ring of light round its top and a
  // glowing lens in its middle, shining up. Broken, it gives back the Halo Light it was put down as.
  for (const tone of LIGHT_TONES) {
    const model = boxes(
      { hub: `${ID}:block/halo_light_hub`, glow: `${ID}:block/halo_light_${tone}`, particle: `${ID}:block/halo_light_hub` },
      [[[3, 0, 3], [13, 1.5, 13], "#hub"], [[6.5, 1.5, 6.5], [9.5, 1.75, 9.5], "#glow", true]],
    );
    model.elements.push(...ring("#glow", FLOOR));
    write(`assets/${ID}/models/block/floor_light_${tone}.json`, model);
  }
  write(`assets/${ID}/blockstates/floor_light.json`, {
    variants: Object.fromEntries(LIGHT_TONES.map((tone) => [`tone=${tone}`, { model: `${ID}:block/floor_light_${tone}` }])),
  });
  dropsHaloLight("floor_light");
  cubes.floor_light = null;
}

// The rocket's wall washer (see WallLightBlock), drawn facing north with its back on the south side of its block: a
// small dark lamp over a narrow slit of light along its top, and over it on the wall, nearly two blocks wide and tall,
// its fan of light up the wall, drawn flat just off the wall in the tone's colour, fading as it rises and out to
// its sides; it glows at full brightness, so it shows as light whatever lights the wall. It stands over half a pixel
// off the wall, too little to see and enough that from across a room the two never fight over the same pixels.
{
  for (const tone of LIGHT_TONES) {
    const glow = { block_light: 15, sky_light: 15 };
    write(`assets/${ID}/models/block/wall_light_${tone}.json`, {
      parent: "minecraft:block/block",
      render_type: "minecraft:translucent",
      textures: { lamp: `${ID}:block/halo_light_hub`, slit: `${ID}:block/halo_light_${tone}`, wash: `${ID}:block/wall_light_wash_${tone}`, particle: `${ID}:block/halo_light_hub` },
      elements: [
        { from: [5, 3, 13], to: [11, 6, 16], faces: Object.fromEntries(["north", "east", "west", "up", "down"].map((face) => [face, { texture: "#lamp" }])) },
        { from: [5.5, 6, 13.5], to: [10.5, 6.25, 15.5], shade: false, faces: { up: { texture: "#slit", neoforge_data: glow }, north: { texture: "#slit", neoforge_data: glow } } },
        { from: [-6, 6.25, 15.4], to: [22, 32, 15.4], shade: false, faces: { north: { uv: [0, 0, 16, 16], texture: "#wash", neoforge_data: glow } } },
      ],
    });
  }
  facingWithTones("wall_light", LIGHT_TONES);
  dropsHaloLight("wall_light");
  cubes.wall_light = null;
}
// The rocket's steel girder (see SkylinersRegistry.STEEL_GIRDER): an I-beam, drawn standing up, two broad flanges, riveted
// along their edges, either side of a narrow web between them; laid along the way it runs, its flanges top and bottom.
{
  const model = boxes(
    { steel: `${ID}:block/steel_girder`, flange: `${ID}:block/steel_girder_flange`, end: `${ID}:block/steel_girder_end`, particle: `${ID}:block/steel_girder` },
    [
      [[1, 0, 1], [15, 16, 3], { north: "#flange", up: "#end", down: "#end", all: "#steel" }],
      [[1, 0, 13], [15, 16, 15], { south: "#flange", up: "#end", down: "#end", all: "#steel" }],
      [[7, 0, 3], [9, 16, 13], { up: "#end", down: "#end", all: "#steel" }],
    ],
  );
  // Its ends, flush with its block's top and bottom, are hidden where it stands on another girder or under one.
  for (const element of model.elements) {
    element.faces.up.cullface = "up";
    element.faces.down.cullface = "down";
  }
  write(`assets/${ID}/models/block/steel_girder.json`, model);
  write(`assets/${ID}/blockstates/steel_girder.json`, {
    variants: {
      "axis=y": { model: `${ID}:block/steel_girder` },
      "axis=z": { model: `${ID}:block/steel_girder`, x: 90 },
      "axis=x": { model: `${ID}:block/steel_girder`, x: 90, y: 90 },
    },
  });
  write(`assets/${ID}/models/item/steel_girder.json`, { parent: `${ID}:block/steel_girder` });
  dropsItself("steel_girder");
  cubes.steel_girder = null;
}
// The rocket's girder decorations (see GirderDecorBlock), each room's own, drawn running north and south under a girder
// lying along z and turned a quarter for one along x, so south turns to east; a pair's first block is its northern.
// What reaches up to the girder stops a pixel over the block, under its lower flange, whose top the hoist trolley's
// wheels ride on. A loop of string, the fairy lights' wire or the bunting's cord, is tied under the girder at the north
// edge of its pair, falls across the first block and rises across the second to its next tie: steep by its tie, easing,
// and flat at its foot, each stretch of it a box turned to an angle a model allows. What glows, the bulbs, the
// spotlights' lenses and their beams, glows whatever the light around; the beams, faint cones of coloured light, reach
// down past the block.
// Put up by hand, each can also be fixed to a wall or stand on the floor, each drawn facing north, its wall to the
// south, and turned for the way it faces; what runs along a girder runs along its wall or across the floor, the first
// of a pair on the left as it is faced, drawn in the girder's frame and turned a quarter where that is simpler. Each
// draws in what holds it there, in the iron it hangs by, the girders' own steel or chrome.
function girderDecor() {
  const block = (name) => `${ID}:block/${name}`;
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  const SIDES = ["north", "south", "east", "west", "up", "down"];
  // A box every face of which shows the texture given, or the patch uv of it, glowing if lit, turned if a rotation is
  // given.
  const part = (from, to, texture, { uv, glowing, rotation } = {}) => ({
    from, to, ...(rotation ? { rotation } : {}), ...(glowing ? { shade: false } : {}),
    faces: Object.fromEntries(SIDES.map((face) => [face, { texture, ...(uv ? { uv } : {}), ...(glowing ? lit : {}) }])),
  });
  // A flat picture through the block, seen from the east and the west when it stands across x and from the north and
  // the south when it stands across z, showing the patch uv of its texture on both sides.
  const flat = (from, to, texture, uv, { glowing, rotation } = {}) => ({
    from, to, ...(rotation ? { rotation } : {}), ...(glowing ? { shade: false } : {}),
    faces: Object.fromEntries((from[0] === to[0] ? ["east", "west"] : ["north", "south"]).map((face) => [face, { texture, uv, ...(glowing ? lit : {}) }])),
  });
  // An element mirrored north for south, so the half of a loop falling toward the south becomes the half rising to it.
  const mirrored = (element) => {
    const faces = { ...element.faces };
    delete faces.north;
    delete faces.south;
    if (element.faces.south) faces.north = element.faces.south;
    if (element.faces.north) faces.south = element.faces.north;
    const turned = element.rotation && element.rotation.axis === "x"
      ? { ...element.rotation, angle: -element.rotation.angle, origin: [element.rotation.origin[0], element.rotation.origin[1], 16 - element.rotation.origin[2]] }
      : element.rotation;
    return { ...element, from: [element.from[0], element.from[1], 16 - element.to[2]], to: [element.to[0], element.to[1], 16 - element.from[2]],
      ...(turned ? { rotation: turned } : {}), faces };
  };
  // A point moved so far along each axis, in pixels; and elements moved so, with what they turn about.
  const shift = ([x, y, z], [dx, dy, dz]) => [x + dx, y + dy, z + dz];
  const moved = (elements, by) => elements.map((element) => ({ ...element, from: shift(element.from, by), to: shift(element.to, by),
    ...(element.rotation ? { rotation: { ...element.rotation, origin: shift(element.rotation.origin, by) } } : {}) }));
  // An element turned a quarter clockwise seen from above, about the middle of the block, as a blockstate's y of 90
  // turns a model, north to east: what runs along z in the girder's frame then runs along x, across a wall or the
  // floor, and what stood by the block's east side stands by its south. Its sides' pictures go round with it and its
  // top's and bottom's are turned to match; a turn about x becomes one about z, and one about z one about x the other
  // way.
  const QUARTER_SIDES = { north: "east", east: "south", south: "west", west: "north" };
  const QUARTER_AXES = { x: ["z", 1], y: ["y", 1], z: ["x", -1] };
  const quarterFace = (name, face, [x0, , z0], [x1, , z1]) => {
    if (QUARTER_SIDES[name]) return [QUARTER_SIDES[name], face];
    const own = name === "up" ? [x0, z0, x1, z1] : [x0, 16 - z1, x1, 16 - z0];
    return [name, { ...face, uv: face.uv ?? own, rotation: ((face.rotation ?? 0) + (name === "up" ? 90 : 270)) % 360 }];
  };
  const quarterRotation = ({ angle, axis, origin, ...rest }) => ({ ...rest, angle: angle * QUARTER_AXES[axis][1], axis: QUARTER_AXES[axis][0],
    origin: [16 - origin[2], origin[1], origin[0]] });
  const quarter = (element) => ({ ...element, from: [16 - element.to[2], element.from[1], element.from[0]], to: [16 - element.from[2], element.to[1], element.to[0]],
    ...(element.rotation ? { rotation: quarterRotation(element.rotation) } : {}),
    faces: Object.fromEntries(Object.entries(element.faces).map(([name, face]) => quarterFace(name, face, element.from, element.to))) });
  const turned = (elements) => elements.map(quarter);
  // A bar between two points through the middle of the block across x, each [z, y], so thick, at the angle between
  // them, which must be one a model allows, reaching half its thickness past each into what it joins: a brace.
  const strut = ([za, ya], [zb, yb], thick, texture) => {
    const steep = Math.abs(yb - ya) >= Math.abs(zb - za);
    const [up, out] = steep ? [yb - ya, zb - za] : [zb - za, ya - yb];
    const sign = Math.sign(up);
    const angle = Math.round((Math.atan2(out * sign, up * sign) * 180) / Math.PI / 22.5) * 22.5;
    const [z, y, half, side] = [(za + zb) / 2, (ya + yb) / 2, Math.hypot(zb - za, yb - ya) / 2 + thick / 2, thick / 2];
    const [from, to] = steep ? [[8 - side, y - half, z - side], [8 + side, y + half, z + side]] : [[8 - side, y - side, z - half], [8 + side, y + side, z + half]];
    return part(from, to, texture, angle ? { rotation: { angle, axis: "x", origin: [8, y, z] } } : {});
  };

  // The loop of string: how high its tie is, in pixels over the block's foot, how thick the string is, and its
  // stretches across the first block, each where it starts along the block, how far along it runs and how steeply it
  // falls, in degrees.
  const TIE = 15.5;
  const STRING = 0.5;
  const STRETCHES = [[0, 2.5, 45], [2.5, 6, 22.5], [8.5, 7.5, 0]];
  // How far a stretch has fallen so far along it.
  const fallen = (along, run, angle) => Math.min(Math.max(along, 0), run) * Math.tan((angle * Math.PI) / 180);
  // How high the middle of the string is so far along the first block of its pair, in pixels.
  const stringAt = (z) => STRETCHES.reduce((y, [from, run, angle]) => y - fallen(z - from, run, angle), TIE);
  // The string across the first block, and the clip it is tied to the girder by.
  const strand = (texture) => STRETCHES.map(([from, run, angle]) => {
    const middle = stringAt(from);
    const length = run / Math.cos((angle * Math.PI) / 180) + (angle ? STRING / 2 : 0);
    return part([8 - STRING / 2, middle - STRING / 2, from], [8 + STRING / 2, middle + STRING / 2, from + length], texture,
      angle ? { rotation: { angle, axis: "x", origin: [8, middle, from] } } : {});
  });
  const clip = part([7.5, TIE - 0.5, 0], [8.5, 17, 0.75], "#iron");
  // Along each block, where the bulbs or the pennants hang from the string.
  const ALONG = [2, 6, 10, 14];
  // A loop's two halves: the string and what it is tied by, the girder's clip unless something else is given, and what
  // hangs from it at each place along it, given how high the string's foot is there and which along the block it is.
  const loop = (texture, hung, tie = [clip]) => ({
    first: [...strand(texture), ...tie, ...ALONG.map((z, k) => hung(z, stringAt(z) - STRING / 2, k))],
    second: [...[...strand(texture), ...tie].map(mirrored), ...ALONG.map((z, k) => hung(z, stringAt(16 - z) - STRING / 2, k))],
  });
  // A fairy light's bulb so far across and along the block, warm white and amber by turns; one under a loop, in the
  // middle across it; a pennant, in the pool's four colours in turn.
  const bulbAt = (x, z, y, k) => part([x - 0.6, y - 1.6, z - 0.6], [x + 0.6, y, z + 0.6], "#bulbs", { uv: k % 2 === 0 ? [0, 0, 8, 16] : [8, 0, 16, 16], glowing: true });
  const bulb = (z, y, k) => bulbAt(8, z, y, k);
  const pennant = (z, y, k) => flat([8, y - 5, z - 1.75], [8, y + 0.25, z + 1.75], "#pennants", [8 * (k % 2), 8 * (k >> 1), 8 * (k % 2) + 8, 8 * (k >> 1) + 8]);
  // On a wall a loop hangs along it a pixel and a quarter off it, tied at each end of its pair to a hook in the wall:
  // drawn in the girder's frame with the wall to the east, moved up to it and turned. How far the loop is moved, in
  // pixels from the middle of the block.
  const LOOP_OFF_WALL = 6.75;
  const wallHook = part([7.75, TIE - 0.5, 0], [16 - LOOP_OFF_WALL, TIE + 0.5, 0.75], "#iron");
  const alongWall = ({ first, second }) => ({ first: turned(moved(first, [LOOP_OFF_WALL, 0, 0])), second: turned(moved(second, [LOOP_OFF_WALL, 0, 0])) });
  // On the floor bunting runs between two chrome posts, one at the outer end of each block of its pair, each on a
  // weighted foot, so where two pairs meet end to end their posts stand side by side as one.
  const post = [part([7.5, 1, 0], [8.5, 15.25, 1], "#post"), part([7.25, 15.25, 0], [8.75, 16, 1.25], "#post"), part([6, 0, 0], [10, 1, 2.5], "#iron")];
  // On the floor fairy lights glow coiled in a glass jar under its copper lid, the battery in the lid: three turns of
  // wire up inside it, each turned its own way, a bulb hanging from the middle of each side of each turn, warm white
  // and amber by turns, and the wire up into the lid. How high each turn is, in pixels, and how far it is turned, in
  // degrees; half a turn's width and how thick the wire is, in pixels.
  const JAR_TURNS = [[3, 22.5], [5.75, -22.5], [8.5, 45]];
  const COIL = 2.25;
  const WIRE = 0.35;
  // A point so far across and along from the middle of the block, turned so far about it as a model turns.
  const aroundY = ([x, z], degrees) => {
    const angle = (degrees * Math.PI) / 180;
    return [8 + x * Math.cos(angle) + z * Math.sin(angle), 8 - x * Math.sin(angle) + z * Math.cos(angle)];
  };
  // The middles of the four sides of a turn, and each side as it lies before the turn is turned.
  const COIL_SIDES = [[0, -COIL], [0, COIL], [-COIL, 0], [COIL, 0]];
  const coilSide = ([x, z], y) => (x === 0
    ? [[8 - COIL, y, 8 + z - WIRE / 2], [8 + COIL, y + WIRE, 8 + z + WIRE / 2]]
    : [[8 + x - WIRE / 2, y, 8 - COIL], [8 + x + WIRE / 2, y + WIRE, 8 + COIL]]);
  const coilTurn = ([y, angle], turn) => [
    ...COIL_SIDES.map((side) => part(...coilSide(side, y), "#wire", { rotation: { angle, axis: "y", origin: [8, y, 8] } })),
    ...COIL_SIDES.map((side, k) => bulbAt(...aroundY(side, angle), y, k + turn)),
  ];
  // The jar: its glass, its shoulder and neck, and the lid screwed on over them.
  const jar = [
    part([4.5, 0, 4.5], [11.5, 9.5, 11.5], "#glass"),
    part([5.25, 9.5, 5.25], [10.75, 10.25, 10.75], "#glass"),
    part([6, 10.25, 6], [10, 10.75, 10], "#glass"),
    part([5.5, 10.75, 5.5], [10.5, 12.25, 10.5], "#copper"),
    ...JAR_TURNS.flatMap(coilTurn),
    part([8 - WIRE / 2, 8.5, 8 - WIRE / 2], [8 + WIRE / 2, 10.75, 8 + WIRE / 2], "#wire"),
  ];

  // A banner on its rod, hung from two clips, a mission patch on it.
  const banner = [
    part([7.5, 15, 2], [8.5, 16, 14], "#iron"),
    part([7.75, 16, 3.5], [8.25, 17, 4.5], "#iron"),
    part([7.75, 16, 11.5], [8.25, 17, 12.5], "#iron"),
    flat([8, 1, 2], [8, 15, 14], "#patch", [2, 1, 14, 15]),
  ];
  // A banner's rod or crossbar so far along the block, a knob on each end.
  const rod = (z) => [part([1.5, 15, z - 0.5], [14.5, 16, z + 0.5], "#iron"), part([1, 14.75, z - 0.75], [2, 16, z + 0.75], "#iron"),
    part([14, 14.75, z - 0.75], [15, 16, z + 0.75], "#iron")];
  // On a wall the banner hangs flat from its rod a pixel and a half off the wall, the rod held out on two brackets,
  // each fixed by a plate.
  const wallBanner = [
    ...rod(14.5),
    ...[4, 12].flatMap((x) => [part([x - 0.5, 15.25, 15], [x + 0.5, 15.75, 15.75], "#iron"), part([x - 0.75, 14.5, 15.75], [x + 0.75, 16, 16], "#iron")]),
    flat([2, 1, 14.5], [14, 15, 14.5], "#patch", [2, 1, 14, 15]),
  ];
  // On the floor it hangs from the crossbar of a banner stand, the stand's pole behind it on a collar and a weighted
  // foot.
  const standBanner = [
    ...rod(8),
    part([7.25, 1, 8.5], [8.75, 16, 10], "#iron"),
    part([6.75, 0.75, 8.25], [9.25, 2, 10.5], "#iron"),
    part([5, 0, 6.25], [11, 0.75, 12.25], "#iron"),
    flat([2, 1, 8], [14, 15, 8], "#patch", [2, 1, 14, 15]),
  ];
  // A length of chain hanging at a place across the block, between two heights, drawn as the game's own chain is; so
  // far along the block, or through its middle.
  const chain = (x, low, high, z = 8) => [
    { from: [x - 1.5, low, z], to: [x + 1.5, high, z], rotation: { angle: 45, axis: "y", origin: [x, 8, z] },
      faces: { north: { texture: "#chain", uv: [0, 0, 3, high - low] }, south: { texture: "#chain", uv: [0, 0, 3, high - low] } } },
    { from: [x, low, z - 1.5], to: [x, high, z + 1.5], rotation: { angle: 45, axis: "y", origin: [x, 8, z] },
      faces: { east: { texture: "#chain", uv: [3, 0, 6, high - low] }, west: { texture: "#chain", uv: [3, 0, 6, high - low] } } },
  ];
  // A hoist hanging from the height given, so far along the block: its casing and its end covers, its lifting chain
  // down to the yellow hook, whose foot is so high, and its hand chain.
  const hoistBody = (top, z, foot = 0) => [
    part([5, top - 5.5, z - 2.5], [11, top, z + 2.5], "#casing"),
    part([4, top - 4.5, z - 1.5], [5, top - 1, z + 1.5], "#iron"),
    part([11, top - 4.5, z - 1.5], [12, top - 1, z + 1.5], "#iron"),
    ...chain(8, foot + 4, top - 5.5, z),
    ...chain(11.5, foot + 1, top - 4.5, z),
    part([6.5, foot + 2.5, z - 1.5], [9.5, foot + 4, z + 1.5], "#hook"),
    part([7.5, foot + 1, z], [8.5, foot + 2.5, z + 1], "#hook"),
    part([7.5, foot, z - 2], [8.5, foot + 1, z + 1], "#hook"),
    part([7.5, foot + 1, z - 2], [8.5, foot + 2, z - 1], "#hook"),
  ];
  // The chain hoist: its trolley's side plates either side of the girder's flange and the wheels on it, the plate
  // across under it, and the hoist under that.
  const hoist = [
    part([0.25, 13.5, 5], [1, 19.5, 11], "#iron"),
    part([15, 13.5, 5], [15.75, 19.5, 11], "#iron"),
    ...[[5, 7], [9, 11]].flatMap(([z0, z1]) => [part([1.5, 19, z0], [3.5, 21, z1], "#steel"), part([12.5, 19, z0], [14.5, 21, z1], "#steel")]),
    part([1, 13.5, 6], [15, 15, 10], "#iron"),
    ...hoistBody(13.5, 8),
  ];
  // On a wall the hoist hangs by a shackle from an eye under the end of a jib arm, a short steel beam out from a plate
  // on the wall, braced from under it by a strut at 45 degrees, a stop across its end.
  const jibHoist = [
    part([5.5, 7, 15], [10.5, 16, 16], "#iron"),
    part([6.5, 14, 2], [9.5, 16, 15], "#girder"),
    part([6, 13.5, 1.5], [10, 16, 2], "#iron"),
    strut([15, 8], [9, 14], 1.5, "#girder"),
    part([7.25, 13.25, 4.75], [8.75, 14, 6.25], "#iron"),
    part([7.5, 12.5, 5.25], [8.5, 13.25, 5.75], "#iron"),
    ...hoistBody(12.5, 5.5),
  ];
  // On the floor it hangs by its shackle from an eye under the beam of a little A-frame gantry, each frame two steel
  // legs splayed from its apex to pads on the floor and a tie across them, the hook a pixel clear of the floor. How
  // high the frames' apexes are, in pixels; and how long their legs are, each short of the floor by as much as its
  // splay drops its outer edge, so none reaches through it, and how far out to the side their feet stand.
  const APEX = 15;
  const GANTRY_LEG = APEX / Math.cos(Math.PI / 8) - 0.75 * Math.tan(Math.PI / 8);
  const GANTRY_SPLAY = APEX * Math.tan(Math.PI / 8);
  const aFrame = (z) => [
    ...[22.5, -22.5].map((angle) => part([7.25, APEX - GANTRY_LEG, z - 0.75], [8.75, APEX, z + 0.75], "#girder", { rotation: { angle, axis: "z", origin: [8, APEX, z] } })),
    part([3.5, 4, z - 0.5], [12.5, 4.75, z + 0.5], "#iron"),
    ...[8 - GANTRY_SPLAY, 8 + GANTRY_SPLAY].map((x) => part([x - 1, 0, z - 1], [x + 1, 0.5, z + 1], "#iron")),
  ];
  const gantryHoist = [
    part([7, 14.5, 0.5], [9, 16, 15.5], "#girder"),
    ...aFrame(1.75),
    ...aFrame(14.25),
    part([7.25, 13.75, 7.25], [8.75, 14.5, 8.75], "#iron"),
    part([7.5, 13, 7.75], [8.5, 13.75, 8.25], "#iron"),
    ...hoistBody(13, 8, 1),
  ];
  // The pot rack: its bar, from its knob in the first block to its knob in the second, hung on a rod in each; on hooks,
  // a pot and a frying pan in the first and a little pan and a smaller pot in the second.
  const hook = (z) => part([7.75, 9.5, z - 0.25], [8.25, 11, z + 0.25], "#iron");
  // A pot so far along, so wide either side of its middle and between two heights, its rim's patch of the copper the
  // band along the texture's foot, its handle up to its hook.
  const pot = (z, half, low, high) => [
    part([8 - half, low, z - half], [8 + half, high, z + half], "#copper"),
    part([7.5 - half, high, z - half - 0.5], [8.5 + half, high + 0.5, z + half + 0.5], "#copper", { uv: [0, 14, 16, 16] }),
    part([7.75, high + 0.5, z - 0.25], [8.25, 9.5, z + 0.25], "#iron"),
    hook(z),
  ];
  // A pan so far along, so wide, its round face to either side, hanging by its handle from its hook.
  const pan = (z, size) => [
    flat([8, 6.5 - size, z - size / 2], [8, 6.5, z + size / 2], "#pan", [0, 0, 16, 16]),
    part([7.6, 6.5, z - 0.4], [8.4, 9.5, z + 0.4], "#iron"),
    hook(z),
  ];
  // Each half of the rack: its bar and the knob on its end, how far along the block its rod and its end are, and what
  // hangs from it; and a shape made of each half.
  const RACK = {
    first: { bar: [part([7.5, 11, 2.5], [8.5, 12, 16], "#iron"), part([7.25, 10.75, 2], [8.75, 12.25, 3], "#iron")], rodAt: 5.5, end: 2.5,
      hung: [...pot(7.5, 2.5, 4, 8.5), ...pan(12.5, 6.5)] },
    second: { bar: [part([7.5, 11, 0], [8.5, 12, 13.5], "#iron"), part([7.25, 10.75, 13], [8.75, 12.25, 14], "#iron")], rodAt: 10.5, end: 13.5,
      hung: [...pan(3, 5), ...pot(9.5, 2, 5, 8.5)] },
  };
  const halves = (shape) => Object.fromEntries(Object.entries(RACK).map(([half, rack]) => [half, shape(rack)]));
  const rack = halves(({ bar, rodAt, hung }) => [...bar, part([7.75, 12, rodAt - 0.5], [8.25, 17, rodAt + 0.5], "#iron"), ...hung]);
  // On a wall the rack is a rail along it, three pixels off it on two brackets, each fixed by a plate, the pots against
  // the wall and the pans' faces to the room: drawn in the girder's frame with the wall to the east, moved up to it and
  // turned. How far the rail is moved, in pixels from the middle of the block.
  const RAIL_OFF_WALL = 4.5;
  const wallRack = halves(({ bar, rodAt, hung }) => turned(moved([
    ...bar,
    part([8.5, 11.25, rodAt - 0.5], [16 - RAIL_OFF_WALL, 11.75, rodAt + 0.5], "#iron"),
    part([15.75 - RAIL_OFF_WALL, 10, rodAt - 1], [16 - RAIL_OFF_WALL, 13, rodAt + 1], "#iron"),
    ...hung,
  ], [RAIL_OFF_WALL, 0, 0])));
  // On the floor it is a standing rack, the bar held up at its ends on an upright each, a knob on top and a foot across
  // under it, the pots and pans raised clear of the floor, by so many pixels.
  const RACK_RAISED = 3;
  const standingRack = halves(({ bar, end, hung }) => turned([
    ...moved([...bar, ...hung], [0, RACK_RAISED, 0]),
    part([7.25, 1, end - 0.75], [8.75, 15, end + 0.75], "#iron"),
    part([7, 15, end - 1], [9, 16, end + 1], "#iron"),
    part([3, 0, end - 0.75], [13, 1, end + 0.75], "#iron"),
  ]));
  // The hanging basket: its hook and ring, four cords down to its rim, the basket and its narrower foot, a tuft of
  // leaves over it and trailing stems hanging down its four sides, each side's its own stretch of them. A cord runs
  // from a height so far down to the rim.
  const cord = (axis, angle, top = 16, drop = 7) => part([7.8, top - drop / Math.cos(Math.PI / 8), 7.8], [8.2, top, 8.2], "#cord",
    { rotation: { angle, axis, origin: [8, top, 8] } });
  // Two flat pictures crossed through the middle of the block, as the game draws a flower, between two corners across
  // it and two heights.
  const crossed = (from, to, texture, uv) => [
    flat([from[0], from[1], 8], [to[0], to[1], 8], texture, uv, { rotation: { angle: 45, axis: "y", origin: [8, 8, 8] } }),
    flat([8, from[1], from[0]], [8, to[1], to[0]], texture, uv, { rotation: { angle: 45, axis: "y", origin: [8, 8, 8] } }),
  ];
  // What a basket hangs by, from the height given, its cords so far down to its rim; and the basket itself.
  const basketGear = (top, drop) => [
    part([7.5, top, 7.5], [8.5, top + 1, 8.5], "#iron"),
    part([7, top - 0.5, 7], [9, top, 9], "#iron"),
    cord("x", 22.5, top, drop), cord("x", -22.5, top, drop), cord("z", 22.5, top, drop), cord("z", -22.5, top, drop),
  ];
  const basketBody = [
    part([4.5, 5.5, 4.5], [11.5, 9, 11.5], "#wicker"),
    part([5.5, 4.5, 5.5], [10.5, 5.5, 10.5], "#wicker"),
    ...crossed([4, 8.75], [12, 13], "#foliage", [0, 0, 16, 9]),
    flat([4.5, 0, 4.4], [11.5, 9.25, 4.4], "#trailing", [0, 0, 7, 16]),
    flat([4.5, 0, 11.6], [11.5, 9.25, 11.6], "#trailing", [4, 0, 11, 16]),
    flat([4.4, 0, 4.5], [4.4, 9.25, 11.5], "#trailing", [9, 0, 16, 16]),
    flat([11.6, 0, 4.5], [11.6, 9.25, 11.5], "#trailing", [2, 0, 9, 16]),
  ];
  const basket = [...basketGear(16, 7), ...basketBody];
  // On a wall the basket hangs from the end of a curled wall bracket: an arm out from a plate on the wall, a knob on
  // its end, and under it a brace curving up from the plate to the arm, a quarter circle drawn as two straight lengths.
  // How far along the block the basket hangs, its middle, in pixels; and the brace's circle, the middle of it along and
  // up, and its radius, in pixels, and a point on it so far round, in degrees, from the plate.
  const BASKET_AT = 7;
  const CURL = { z: 9.75, y: 9.5, radius: 5.5 };
  const curlAt = (degrees) => [CURL.z + CURL.radius * Math.cos((degrees * Math.PI) / 180), CURL.y + CURL.radius * Math.sin((degrees * Math.PI) / 180)];
  const wallBasket = [
    part([7, 8, 15.25], [9, 16, 16], "#iron"),
    part([7.5, 15, BASKET_AT - 0.5], [8.5, 16, 15.25], "#iron"),
    part([7.25, 14.75, BASKET_AT - 1.25], [8.75, 16, BASKET_AT - 0.5], "#iron"),
    strut(curlAt(0), curlAt(45), 0.75, "#iron"),
    strut(curlAt(45), curlAt(90), 0.75, "#iron"),
    ...moved([...basketGear(14, 5), ...basketBody], [0, 0, BASKET_AT - 8]),
  ];
  // On the floor the basket sits on a plant stand, raised by so many pixels so its stems trail clear of the floor: a
  // square iron frame under its foot on four legs, and a second frame low between them.
  const BASKET_RAISED = 3;
  const frame = (low, high) => [
    part([5, low, 5], [11, high, 5.75], "#iron"), part([5, low, 10.25], [11, high, 11], "#iron"),
    part([5, low, 5.75], [5.75, high, 10.25], "#iron"), part([10.25, low, 5.75], [11, high, 10.25], "#iron"),
  ];
  const standBasket = [
    ...frame(6.75, 7.5),
    ...frame(2, 2.5),
    ...[[5, 5], [10.25, 5], [5, 10.25], [10.25, 10.25]].map(([x, z]) => part([x, 0, z], [x + 0.75, 6.75, z + 0.75], "#iron")),
    ...moved(basketBody, [0, BASKET_RAISED, 0]),
  ];
  // A stage spotlight's head: its yoke and the can turned in it so far about its pivot, its lens glowing and a cone of
  // light from it.
  const spotHead = (angle) => {
    const tilt = { angle, axis: "x", origin: [8, 10, 8] };
    return [
      part([4.5, 12.5, 7.25], [11.5, 13, 8.75], "#iron"),
      part([4.5, 9, 7.25], [5.25, 12.5, 8.75], "#iron"),
      part([10.75, 9, 7.25], [11.5, 12.5, 8.75], "#iron"),
      part([5.25, 6, 5.5], [10.75, 11.5, 10.5], "#can", { rotation: tilt }),
      part([5.75, 5.6, 6], [10.25, 6, 10], "#lens", { glowing: true, rotation: tilt }),
      flat([8, -12, 3], [8, 5.6, 13], "#beam", [0, 0, 16, 16], { glowing: true, rotation: tilt }),
      flat([3, -12, 8], [13, 5.6, 8], "#beam", [0, 0, 16, 16], { glowing: true, rotation: tilt }),
    ];
  };
  // A stage spotlight: its clamp on the girder's flange, its stem, and its head angled down along the girder: pink
  // angled north in the first, cyan south in the second.
  const spotlight = (angle) => [part([6, 15, 6], [10, 17, 10], "#iron"), part([7.25, 13, 7.25], [8.75, 15, 8.75], "#iron"), ...spotHead(angle)];
  // On a wall it hangs from a clamp on an arm out from a plate on the wall, angled down and out into the room.
  const wallSpot = [
    part([6, 11.5, 15.25], [10, 15.5, 16], "#iron"),
    part([7.25, 13.5, 8], [8.75, 15, 15.25], "#iron"),
    part([6.25, 13, 6.75], [9.75, 15.5, 9.25], "#iron"),
    ...spotHead(45),
  ];
  // On the floor it stands on a short tripod, two legs splayed to its sides and one behind, from a collar up its
  // column, angled up away from whoever stood it there, its cone of light reaching up past the block. How high the legs
  // meet the column, in pixels; and how long they are, each short of the floor by as much as its splay drops its outer
  // edge, so none reaches through it, and how far out their feet stand.
  const HUB = 4.5;
  const TRIPOD_LEG = HUB / Math.cos(Math.PI / 4) - 0.5 * Math.tan(Math.PI / 4);
  const TRIPOD_SPLAY = HUB;
  const tripodLeg = (axis, angle) => part([7.5, HUB - TRIPOD_LEG, 7.5], [8.5, HUB, 8.5], "#iron", { rotation: { angle, axis, origin: [8, HUB, 8] } });
  const upward = { angle: 22.5, axis: "x", origin: [8, 11.5, 8] };
  const standSpot = [
    tripodLeg("z", 45), tripodLeg("z", -45), tripodLeg("x", -45),
    ...[[8 - TRIPOD_SPLAY, 8], [8 + TRIPOD_SPLAY, 8], [8, 8 + TRIPOD_SPLAY]].map(([x, z]) => part([x - 0.75, 0, z - 0.75], [x + 0.75, 0.5, z + 0.75], "#iron")),
    part([7, HUB - 0.75, 7], [9, HUB + 0.75, 9], "#iron"),
    part([7.25, 2.5, 7.25], [8.75, 8.5, 8.75], "#iron"),
    part([4.5, 8.5, 7.25], [11.5, 9, 8.75], "#iron"),
    part([4.5, 9, 7.25], [5.25, 12.5, 8.75], "#iron"),
    part([10.75, 9, 7.25], [11.5, 12.5, 8.75], "#iron"),
    part([5.25, 10, 5.5], [10.75, 15.5, 10.5], "#can", { rotation: upward }),
    part([5.75, 15.5, 6], [10.25, 15.9, 10], "#lens", { glowing: true, rotation: upward }),
    flat([8, 15.9, 3], [8, 32, 13], "#beam", [0, 16, 16, 0], { glowing: true, rotation: upward }),
    flat([3, 15.9, 8], [13, 32, 8], "#beam", [0, 16, 16, 0], { glowing: true, rotation: upward }),
  ];
  // The cable tray, a ladder of it, so its cables show from below between its rungs: a trapeze of two rods from the
  // girder and a bar across under it, its two rails and its rungs, the rungs as far apart across a block's edge as
  // within it, the cables bundled on them, each its colour's patch of the cables' texture, and a white tie round them,
  // the whole way along the block.
  const RUNGS = [1.5, 5.5, 9.5, 13.5];
  const cable = (x0, y0, size, colour) => part([x0, y0, 0], [x0 + size, y0 + size, 16], "#cables", { uv: [2 * colour, 0, 2 * colour + 2, 16] });
  const trayRun = [
    part([4, 8.5, 0], [5, 10.5, 16], "#tray"),
    part([11, 8.5, 0], [12, 10.5, 16], "#tray"),
    ...RUNGS.map((z) => part([5, 8.5, z], [11, 9.25, z + 1], "#tray")),
    cable(5.25, 9.25, 1.25, 0), cable(6.5, 9.25, 1.25, 1), cable(7.75, 9.25, 1.25, 2), cable(9, 9.25, 1.75, 4),
    cable(5.75, 10.5, 1.25, 5), cable(7.25, 10.5, 1.25, 3), cable(8.5, 10.5, 1.25, 6),
    part([5, 9.15, 7.75], [10.85, 11.85, 8.25], "#cables", { uv: [14, 0, 16, 16] }),
  ];
  const tray = [part([3, 7.5, 7], [13, 8.5, 9], "#iron"), part([3, 8.5, 7.5], [3.75, 17, 8.25], "#iron"), part([12.25, 8.5, 7.5], [13, 17, 8.25], "#iron"), ...trayRun];
  // On a wall the tray runs along it half a pixel off it on a bracket, an arm under it out from a plate on the wall,
  // braced from under by a strut at 45 degrees: the tray drawn in the girder's frame with the wall to the east, moved
  // up to it, by so many pixels, and turned.
  const TRAY_OFF_WALL = 3.5;
  const wallTray = [
    ...turned(moved(trayRun, [TRAY_OFF_WALL, 0, 0])),
    part([7, 7.5, 6.5], [9, 8.5, 16], "#iron"),
    part([6.5, 2.5, 15.25], [9.5, 9, 16], "#iron"),
    strut([15.25, 3], [10.75, 7.5], 1, "#iron"),
  ];
  // On the floor the cables cross under a cable cover: a yellow lid over two channels in a black rubber hump, a cable
  // in each, showing at its ends, and a ramp down to the floor along each side, a plate thin enough that tipped down
  // from the hump it meets the floor and goes no further; it runs across the block, so covers laid end to end make one.
  const coverCable = (z, colour) => part([0, 0.1, z], [16, 1.6, z + 1.5], "#cables", { uv: [2 * colour, 0, 2 * colour + 2, 16] });
  const ramp = (z0, z1, angle, edge) => part([0, 1.5, z0], [16, 2.5, z1], "#iron", { rotation: { angle, axis: "x", origin: [8, 2.5, edge] } });
  const cableCover = [
    part([0, 1.75, 5], [16, 2.5, 11], "#lid", { uv: [1, 1, 15, 7] }),
    ...[5, 7.625, 10.25].map((z) => part([0, 0, z], [16, 1.75, z + 0.75], "#iron")),
    coverCable(6, 0),
    coverCable(8.6, 1),
    ramp(1, 5, -22.5, 5),
    ramp(11, 15, 22.5, 11),
  ];

  const fairy = loop("#wire", bulb);
  const bunting = loop("#cord", pennant);
  const wallFairy = alongWall(loop("#wire", bulb, [wallHook]));
  const wallBunting = alongWall(loop("#cord", pennant, [wallHook]));
  const postBunting = loop("#cord", pennant, post);
  const patch = (name) => ({ patch: block(`girder_decor_patch_${name}`), particle: block(`girder_decor_patch_${name}`) });
  const hoisting = { steel: block("girder_decor_steel"), casing: block("girder_decor_hoist"), particle: block("girder_decor_hoist"), hook: block("girder_decor_yellow"), chain: "minecraft:block/chain" };
  const steelwork = { ...hoisting, girder: block("steel_girder") };
  const copper = { copper: block("girder_decor_copper"), pan: block("girder_decor_pan"), particle: block("girder_decor_copper") };
  const lights = { wire: block("girder_decor_wire"), bulbs: block("girder_decor_bulbs"), particle: block("girder_decor_bulbs") };
  const greenery = { cord: block("girder_decor_cord"), wicker: block("girder_decor_wicker"), particle: block("girder_decor_wicker"), foliage: block("girder_decor_foliage"),
    trailing: block("girder_decor_trailing") };
  const pennants = { cord: block("girder_decor_cord"), pennants: block("girder_decor_pennants"), particle: block("girder_decor_pennants") };
  const spot = (colour) => ({ can: block("girder_decor_can"), particle: block("girder_decor_can"), lens: block(`girder_decor_lens_${colour}`), beam: block(`girder_decor_beam_${colour}`) });
  const cabling = { tray: block("girder_decor_tray"), cables: block("girder_decor_cables"), particle: block("girder_decor_cables") };
  const models = {
    mission_patches_first: { textures: patch("a"), elements: banner },
    mission_patches_second: { textures: patch("b"), elements: banner },
    chain_hoist: { textures: hoisting, elements: hoist },
    pot_rack_first: { textures: copper, elements: rack.first },
    pot_rack_second: { textures: copper, elements: rack.second },
    fairy_lights_first: { textures: lights, elements: fairy.first },
    fairy_lights_second: { textures: lights, elements: fairy.second },
    hanging_basket: { textures: greenery, elements: basket },
    bunting_first: { textures: pennants, elements: bunting.first },
    bunting_second: { textures: pennants, elements: bunting.second },
    spotlight_first: { translucent: true, textures: spot("pink"), elements: spotlight(22.5) },
    spotlight_second: { translucent: true, textures: spot("cyan"), elements: spotlight(-22.5) },
    cable_tray: { textures: cabling, elements: tray },
    mission_patches_wall_first: { textures: patch("a"), elements: wallBanner },
    mission_patches_wall_second: { textures: patch("b"), elements: wallBanner },
    chain_hoist_wall: { textures: steelwork, elements: jibHoist },
    pot_rack_wall_first: { textures: copper, elements: wallRack.first },
    pot_rack_wall_second: { textures: copper, elements: wallRack.second },
    fairy_lights_wall_first: { textures: lights, elements: wallFairy.first },
    fairy_lights_wall_second: { textures: lights, elements: wallFairy.second },
    hanging_basket_wall: { textures: greenery, elements: wallBasket },
    bunting_wall_first: { textures: pennants, elements: wallBunting.first },
    bunting_wall_second: { textures: pennants, elements: wallBunting.second },
    spotlight_wall_first: { translucent: true, textures: spot("pink"), elements: wallSpot },
    spotlight_wall_second: { translucent: true, textures: spot("cyan"), elements: wallSpot },
    cable_tray_wall: { textures: cabling, elements: wallTray },
    mission_patches_floor_first: { textures: patch("a"), elements: standBanner },
    mission_patches_floor_second: { textures: patch("b"), elements: standBanner },
    chain_hoist_floor: { textures: steelwork, elements: gantryHoist },
    pot_rack_floor_first: { textures: copper, elements: standingRack.first },
    pot_rack_floor_second: { textures: copper, elements: standingRack.second },
    fairy_lights_floor: { translucent: true, textures: { ...lights, glass: block("cabin_rail_glass"), copper: block("girder_decor_copper") }, elements: jar },
    hanging_basket_floor: { textures: greenery, elements: standBasket },
    bunting_floor_first: { textures: { ...pennants, post: block("lavatory_basin_chrome") }, elements: turned(postBunting.first) },
    bunting_floor_second: { textures: { ...pennants, post: block("lavatory_basin_chrome") }, elements: turned(postBunting.second) },
    spotlight_floor_first: { translucent: true, textures: spot("pink"), elements: standSpot },
    spotlight_floor_second: { translucent: true, textures: spot("cyan"), elements: standSpot },
    cable_tray_floor: { textures: { ...cabling, lid: block("girder_decor_yellow") }, elements: cableCover },
  };
  for (const [name, { translucent, textures, elements }] of Object.entries(models)) {
    write(`assets/${ID}/models/block/girder_decor_${name}.json`, {
      parent: "minecraft:block/block", render_type: translucent ? "minecraft:translucent" : "minecraft:cutout",
      textures: { iron: block("girder_decor_iron"), particle: block("girder_decor_iron"), ...textures }, elements,
    });
  }
  // Each kind's model for each of a pair, hung under a ceiling unless a mount is given, the same for both of a pair
  // where the kind has only one there.
  const modelOf = (kind, half, mount = "ceiling") => {
    const shape = mount === "ceiling" ? kind : `${kind}_${mount}`;
    return `${ID}:block/girder_decor_${models[`${shape}_${half}`] ? `${shape}_${half}` : shape}`;
  };
  const KINDS = ["mission_patches", "chain_hoist", "pot_rack", "fairy_lights", "hanging_basket", "bunting", "spotlight", "cable_tray"];
  const HALVES = ["first", "second"];
  const LOOKS = KINDS.flatMap((kind) => HALVES.map((half) => [kind, half]));
  // Hung under a ceiling a decoration is turned by the way it runs; fixed to a wall or standing on the floor, by the
  // way it faces.
  const hanging = ["x", "z"].flatMap((axis) => LOOKS.map(([kind, half]) =>
    [`axis=${axis},kind=${kind},mount=ceiling,part=${half}`, { model: modelOf(kind, half), ...(axis === "x" ? { y: 270 } : {}) }]));
  const standing = ["wall", "floor"].flatMap((mount) => Object.entries(yRot).flatMap(([facing, y]) => LOOKS.map(([kind, half]) =>
    [`facing=${facing},kind=${kind},mount=${mount},part=${half}`, { model: modelOf(kind, half, mount), ...(y ? { y } : {}) }])));
  write(`assets/${ID}/blockstates/girder_decor.json`, { variants: Object.fromEntries([...hanging, ...standing]) });
  keptLooks("girder_decor", { kind: KINDS, part: HALVES }, modelOf, ["chain_hoist", "first"]);
  // Broken, a decoration keeps its kind and which of a pair it is; a chain hoist's first is left off, so they stack.
  const decorId = `${ID}:girder_decor`;
  write(`data/${ID}/loot_table/blocks/girder_decor.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item",
        name: decorId,
        functions: [{
          function: "minecraft:copy_state", block: decorId, properties: ["kind", "part"],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: decorId,
            properties: { kind: "chain_hoist", part: "first" } } }],
        }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/girder_decor`,
  });
  cubes.girder_decor = null;
}
girderDecor();
// The girder decorations are fitted into the rocket rather than built into her: light like the rest, but no part of
// her frame, and breakable.
function girderDecorFittings() {
  return ["girder_decor"];
}
function girderDecorLang() {
  return {
    "block.skyliners.girder_decor": "Girder Decoration",
  };
}
// A block that faces the way it was placed and comes in tones: a model for each tone, turned for each facing.
function facingWithTones(name, tones) {
  write(`assets/${ID}/blockstates/${name}.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => tones.map((tone) =>
      [`facing=${facing},tone=${tone}`, { model: `${ID}:block/${name}_${tone}`, y }]))),
  });
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
  variants: mountedVariants([false, true].map((armed) => [`armed=${armed}`, `drop_release_${armed ? "armed" : "safe"}`])),
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
// in its colour. In a bunk bed (its tier) the lower bunk's corner posts at its ends run up two blocks, and the upper
// bunk's, three blocks over the lower, carry on from them up past its guards: a tall padded rail along all of its
// open side and its back, under a white top bar, and its headboard raised as high; its foot end is left open, the
// way in from its ladder.
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
  // The foot's half: under an upper bunk's guard its blanket does not hang down the open side, where the guard is.
  const foot = (guarded) => [
    part([1, 0, 1], [16, 3, 16], "#frame"),
    part([1, 3, 1.5], [16, 7, 15.5], "#mattress"),
    part([1, 7, 1.25], [16, 7.75, 15.75], "#blanket", true),
    ...(guarded ? [] : [part([1, 4, 1], [16, 7.75, 1.5], "#blanket", true)]),
    part([0, 0, 1], [1, 10, 16], "#frame"),
  ];
  // A part reaching out of its block, textured as one inside it would be (see patched), tinted if the blanket's colour.
  const tall = (from, to, texture, tinted = false) => {
    const element = patched(from, to, texture);
    if (tinted) Object.values(element.faces).forEach((face) => { face.tintindex = 0; });
    return element;
  };
  // How far the posts and the guards stand proud of the bed's parts they wrap round, in pixels, so no two faces lie
  // in one plane, flickering through each other.
  const PROUD = 0.05;
  // A bunk bed's corner posts at a head's end (to the east) or a foot's (to the west), from so far up to so far.
  const posts = (atHead, from, to) => {
    const [x0, x1] = atHead ? [14.5 - PROUD, 16 + PROUD] : [-PROUD, 1.5 + PROUD];
    return [tall([x0, from, -PROUD], [x1, to, 1.5 + PROUD], "#frame"), tall([x0, from, 14.5 - PROUD], [x1, to, 16 + PROUD], "#frame")];
  };
  // An upper bunk's guards along a half, from so far along to so far: its padded rail up over the head's own, on the
  // open side and the back, each under a top bar; and at the head, the headboard raised as high.
  const GUARD_TOP = 19;
  const guards = (atHead) => {
    const [x0, x1] = atHead ? [1.5, 14.5] : [1.5, 16];
    const along = [];
    for (const [z0, z1, bar0, bar1] of [[0.5, 1.5, 0.25, 1.75], [14.5, 15.5, 14.25, 15.75]]) {
      const from = atHead && z0 < 1 ? 12 : 4;
      along.push(tall([x0, from, z0 - PROUD], [x1, GUARD_TOP - 1, z1 + PROUD], "#rail", true), tall([x0, GUARD_TOP - 1, bar0], [x1, GUARD_TOP, bar1], "#frame"));
    }
    return atHead ? [...along, tall([15, 14, 1.5], [16, GUARD_TOP, 14.5], "#frame")] : along;
  };
  // Each tier's extra parts for a half: the lower's posts up two blocks over its floor; the upper's down the block
  // under it to meet them and up past its guards, and the guards.
  const TIERS = {
    single: () => [],
    lower: (atHead) => posts(atHead, 0, 32),
    upper: (atHead) => [...posts(atHead, -16, GUARD_TOP + 2), ...guards(atHead)],
  };
  const tierSuffix = (tier) => (tier === "single" ? "" : `_${tier}`);
  for (const [tier, extra] of Object.entries(TIERS)) {
    for (const lit of [false, true]) {
      write(`assets/${ID}/models/block/bunk_head${lit ? "_lit" : ""}${tierSuffix(tier)}.json`, {
        parent: "minecraft:block/block", textures: textures(lit), elements: [...head(lit), ...extra(true)],
      });
    }
    write(`assets/${ID}/models/block/bunk_foot${tierSuffix(tier)}.json`, {
      parent: "minecraft:block/block", textures: textures(false), elements: [...foot(tier === "upper"), ...extra(false)],
    });
  }
  write(`assets/${ID}/blockstates/bunk.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].flatMap((lit) => ["head", "foot"].flatMap((half) =>
      Object.keys(TIERS).map((tier) => [`facing=${facing},lit=${lit},part=${half},tier=${tier}`, {
        model: `${ID}:block/bunk_${half === "head" && lit ? "head_lit" : half}${tierSuffix(tier)}`, ...(y ? { y } : {}),
      }]))))),
  });
  // In the hand the halves side by side, the foot to the west of the head, both moved east half a block so the bunk is
  // held by its middle.
  const shifted = (elements, dx) => elements.map((element) => ({ ...element, from: [element.from[0] + dx, element.from[1], element.from[2]], to: [element.to[0] + dx, element.to[1], element.to[2]] }));
  write(`assets/${ID}/models/item/bunk.json`, {
    parent: "minecraft:block/block",
    textures: textures(false),
    elements: [...shifted(head(false), 8), ...shifted(foot(false), -8)],
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
// texture (see DiscoBallRenderer), so the block's model is only what holds it, on or off: hung, a slim cable from the
// top of the block down to a chrome cap on top of the ball. On the floor (its middle 9.5 pixels up, see
// DiscoBallBlock.centre) it turns on a small motor stand: a dark round foot, a squat steel motor on it and a chrome
// spindle up to a cap under the ball. On a wall, drawn facing north with the wall on the south side of its block (its
// middle 9 pixels out from the wall), it hangs from the motor at the end of a dark arm out from a wall plate, the
// motor over a chrome cap on top of the ball. Its particles are the ball's mirror tiles; in the hand, a picture of the
// ball.
{
  const cap = `${ID}:block/lavatory_basin_chrome`;
  const particle = `${ID}:block/disco_ball`;
  // What holds the ball on the floor or a wall: a dark foot or bracket, a steel motor and chrome.
  const holds = { bracket: `${ID}:block/halo_light_hub`, motor: `${ID}:block/shelf_steel`, cap, particle };
  write(`assets/${ID}/models/block/disco_ball.json`, boxes(
    { rod: `${ID}:block/light_cable`, cap, particle },
    [
      [[7.5, 13, 7.5], [8.5, 16, 8.5], "#rod"],
      [[7, 12.5, 7], [9, 13.5, 9], "#cap"],
    ],
  ));
  write(`assets/${ID}/models/block/disco_ball_floor.json`, boxes(holds, [
    [[4, 0, 6], [12, 1, 10], "#bracket"],
    [[5, 0, 5], [11, 1, 11], "#bracket"],
    [[6, 0, 4], [10, 1, 12], "#bracket"],
    [[6, 1, 6], [10, 2.5, 10], "#motor"],
    [[7.5, 2.5, 7.5], [8.5, 3, 8.5], "#cap"],
    [[7, 3, 7], [9, 4, 9], "#cap"],
  ]));
  write(`assets/${ID}/models/block/disco_ball_wall.json`, boxes(holds, [
    [[5.5, 10.5, 15], [10.5, 15.5, 16], "#bracket"],
    [[7, 13.5, 9], [9, 15, 15], "#bracket"],
    [[6, 13.5, 5], [10, 15.5, 9], "#motor"],
    [[7, 12.5, 6], [9, 13.5, 8], "#cap"],
  ]));
  // Hung or standing, the ball looks the same whichever way it faces; on a wall it faces out.
  write(`assets/${ID}/blockstates/disco_ball.json`, {
    variants: {
      "mount=ceiling": { model: `${ID}:block/disco_ball` },
      "mount=floor": { model: `${ID}:block/disco_ball_floor` },
      ...Object.fromEntries(Object.entries(yRot).map(([facing, y]) => [`facing=${facing},mount=wall`, { model: `${ID}:block/disco_ball_wall`, y }])),
    },
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
    "block.skyliners.bunk": "Bunk",
    "block.skyliners.bean_bag": "Bean Bag",
    "block.skyliners.disco_ball": "Disco Ball",
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
    "block.skyliners.floor_hatch": "Floor Hatch",
    "block.skyliners.secret_bookshelf": "Secret Bookshelf Door",
    "block.skyliners.ball_pit": "Ball Pit",
    "block.skyliners.fairy_lights": "Fairy Lights",
    "subtitles.skyliners.secret_bookshelf_open": "Bookshelf creaks open",
    "subtitles.skyliners.secret_bookshelf_close": "Bookshelf creaks shut",
    "subtitles.skyliners.ball_pit_plop": "Ball pit plops",
    "block.skyliners.suitcase": "Suitcase",
    "block.skyliners.golden_suitcase": "Golden Suitcase",
    "container.skyliners.suitcase": "Suitcase",
    "container.skyliners.golden_suitcase": "Golden Suitcase",
    "message.skyliners.golden_suitcase.found": "A golden suitcase! %s of %s found",
    "advancements.skyliners.first_flight.lost_and_found.title": "Lost and Found",
    "advancements.skyliners.first_flight.lost_and_found.description": "Find the three golden suitcases hidden in an airliner's hold",
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
    "block.skyliners.cargo_net": "Cargo Net",
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
    "block.skyliners.cargo_floor": "Cargo Floor",
    "block.skyliners.ball_mat": "Ball Mat",
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
    "block.skyliners.uld_container": "ULD Container",
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
// blocks themselves show nothing but the duck's yellow as they break. In the hand it is the rubber duck; broken, it
// gives itself back by its block's own hand, whichever part was broken (see GiantRubberDuckBlock.playerDestroy).
{
  write(`assets/${ID}/blockstates/giant_rubber_duck.json`, { variants: { "": { model: `${ID}:block/giant_rubber_duck` } } });
  write(`assets/${ID}/models/block/giant_rubber_duck.json`, { textures: { particle: `${ID}:block/rubber_duck` } });
  write(`assets/${ID}/models/item/giant_rubber_duck.json`, { parent: `${ID}:item/rubber_duck` });
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
    "block.skyliners.rubber_duck": "Rubber Duck",
    "subtitles.skyliners.rubber_duck_squeak": "Rubber duck squeaks",
    "message.skyliners.rubber_duck.found": "A rubber duck! %s",
    "message.skyliners.rubber_duck.all_found": "All five rubber ducks found!",
    "message.skyliners.rubber_duck.giant": "All five rubber ducks found! Something big is coming up in the moon pool...",
    "block.skyliners.giant_rubber_duck": "Giant Rubber Duck",
    "subtitles.skyliners.giant_rubber_duck_squeak": "Giant rubber duck squeaks",
    "advancements.skyliners.first_flight.duck_hunt.title": "Duck Hunt",
    "advancements.skyliners.first_flight.duck_hunt.description": "Find the five rubber ducks hidden aboard an airliner",
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
    "block.skyliners.coffee_machine": "Coffee Machine",
    "item.skyliners.espresso": "Espresso",
    "item.skyliners.cappuccino": "Cappuccino",
    "item.skyliners.mocha": "Mocha",
    "item.skyliners.babycino": "Babycino",
    "screen.skyliners.coffee_machine.title": "Coffee Machine",
    "screen.skyliners.coffee_machine.ready": "Ready",
    "subtitles.skyliners.coffee_grind": "Coffee grinder whirs",
    "subtitles.skyliners.coffee_pour": "Coffee machine pours",
    "subtitles.skyliners.coffee_steam": "Milk frother hisses",
    "subtitles.skyliners.coffee_ready": "Coffee machine chimes",
    "advancements.skyliners.first_flight.barista.title": "Barista",
    "advancements.skyliners.first_flight.barista.description": "Brew an espresso, a cappuccino and a mocha at a coffee machine",
  };
}

// Basketball, drawn facing north. The hoop: an orange ring with its net hanging below, as high in its block on a wall
// as on the floor (see HoopBlock); the side it counts for does not show. On a wall, a dark bracket
// to the wall or backboard behind it. On the floor, a little backboard along the north side of the block behind the
// ring, a pane of the glass backboard's glass in its aluminium frame with the red shooter's square over the ring and a
// short dark bracket under the ring to it, on top of a steel pole standing in a dark weighted base. Under a ceiling,
// the same board and ring hung HUNG_DROP pixels lower in the block (HoopBlock's HUNG_DROP, so a ball has room over the
// ring), from the ceiling on two steel rods, each with a plate where it meets it. Those two are drawn translucent, for
// the glass. The scoreboard: a dark face, the score drawn over it by its renderer. The ball
// rack: a steel frame of balls. The ball: an item sprite, drawn the same in flight.
{
  const ring = [
    [[2, 10, 2], [14, 11, 3.5], "#rim"],
    [[2, 10, 12.5], [14, 11, 14], "#rim"],
    [[2, 10, 3.5], [3.5, 11, 12.5], "#rim"],
    [[12.5, 10, 3.5], [14, 11, 12.5], "#rim"],
  ];
  // The net: four sides hanging from the ring, seen from inside and out.
  const net = [
    [[3, 4, 3.5], [13, 10, 3.5], ["north", "south"]],
    [[3, 4, 12.5], [13, 10, 12.5], ["north", "south"]],
    [[3.5, 4, 3], [3.5, 10, 13], ["east", "west"]],
    [[12.5, 4, 3], [12.5, 10, 13], ["east", "west"]],
  ].map(([from, to, faces]) => ({ from, to, faces: Object.fromEntries(faces.map((face) => [face, { texture: "#net" }])) }));
  const hoopTextures = { rim: `${ID}:block/hoop_rim`, bracket: `${ID}:block/handset_cradle`, net: `${ID}:block/hoop_net`, particle: `${ID}:block/hoop_rim` };
  const hoop = { ...boxes(hoopTextures, [...ring, [[7, 9, 0], [9, 11, 2], "#bracket"]]), render_type: "minecraft:cutout" };
  hoop.elements.push(...net);
  write(`assets/${ID}/models/block/hoop.json`, hoop);
  // The little backboard from under the ring up to the top given: its frame round it, the square on its front over the
  // ring and the bracket under the ring to it; its pane goes in on its own, glass seen through from either side.
  const BOARD_BOTTOM = 9;
  const littleBoard = (top) => [
    [[1, top - 1, 0], [15, top, 2], "#frame"],
    [[1, BOARD_BOTTOM, 0], [15, BOARD_BOTTOM + 1, 2], "#frame"],
    [[1, BOARD_BOTTOM + 1, 0], [2, top - 1, 2], "#frame"],
    [[14, BOARD_BOTTOM + 1, 0], [15, top - 1, 2], "#frame"],
    [[5.5, 11, 1.5], [10.5, 11.75, 1.75], "#red"],
    [[5.5, top - 2.75, 1.5], [10.5, top - 2, 1.75], "#red"],
    [[5.5, 11.75, 1.5], [6.25, top - 2.75, 1.75], "#red"],
    [[9.75, 11.75, 1.5], [10.5, top - 2.75, 1.75], "#red"],
    [[7, BOARD_BOTTOM, 2], [9, BOARD_BOTTOM + 1, 3], "#bracket"],
  ];
  const pane = (top) => ({ from: [1, BOARD_BOTTOM, 0.5], to: [15, top, 1.5], faces: { north: { texture: "#glass" }, south: { texture: "#glass" } } });
  const boardTextures = { ...hoopTextures, glass: `${ID}:block/glass_backboard`, frame: `${ID}:block/cabin_rail_steel`, red: `${ID}:block/backboard_red` };
  const HUNG_DROP = 8;
  const lowered = ([from, to, texture]) => [[from[0], from[1] - HUNG_DROP, from[2]], [to[0], to[1] - HUNG_DROP, to[2]], texture];
  const loweredElement = (element) => ({ ...element, from: [element.from[0], element.from[1] - HUNG_DROP, element.from[2]],
    to: [element.to[0], element.to[1] - HUNG_DROP, element.to[2]] });
  const mounted = {
    floor: [16, [
      [[7, 4, 0.25], [9, BOARD_BOTTOM, 1.75], "#frame"],
      [[6.5, 3, 0], [9.5, 4, 2.25], "#bracket"],
      [[3.5, 1.5, 0], [12.5, 3, 5], "#bracket"],
      [[2, 0, 0], [14, 1.5, 7], "#bracket"],
    ]],
  };
  for (const [mount, [top, holder]] of Object.entries(mounted)) {
    const model = { ...boxes(boardTextures, [...ring, ...littleBoard(top), ...holder]), render_type: "minecraft:translucent" };
    model.elements.push(pane(top), ...net);
    write(`assets/${ID}/models/block/hoop_${mount}.json`, model);
  }
  const rodsFrom = 16 - HUNG_DROP;
  const rods = [
    [[2.75, rodsFrom, 0.25], [4.25, 16, 1.75], "#frame"],
    [[11.75, rodsFrom, 0.25], [13.25, 16, 1.75], "#frame"],
    [[2.5, 15.5, 0], [4.5, 16, 2], "#frame"],
    [[11.5, 15.5, 0], [13.5, 16, 2], "#frame"],
  ];
  const hung = { ...boxes(boardTextures, [...[...ring, ...littleBoard(16)].map(lowered), ...rods]), render_type: "minecraft:translucent" };
  hung.elements.push(loweredElement(pane(16)), ...net.map(loweredElement));
  write(`assets/${ID}/models/block/hoop_ceiling.json`, hung);
  const hoopModel = (mount) => `${ID}:block/hoop${mount === "wall" ? "" : `_${mount}`}`;
  write(`assets/${ID}/blockstates/hoop.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) =>
      ["ceiling", "wall", "floor"].map((mount) => [`facing=${facing},mount=${mount}`, { model: hoopModel(mount), y }]))),
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
    "item.skyliners.laser_pointer": "Laser Pointer",
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
// The souvenir model freighter, prongs north: a grey saucer stepping in to her top and her belly, her two prongs
// reaching ahead either side of the loading gap, her cockpit's tube and pod out on her right, her top turret's dome
// and her radar dish, on a slim stand of light cable, as the zeppelin's is (the blueprint's preview leaves it out).
{
  const hull = "#hull";
  const model = {
    parent: "minecraft:block/block",
    textures: { hull: `${ID}:block/zeppelin_fabric`, glass: `${ID}:block/cockpit_glass`, stand: `${ID}:block/light_cable`, particle: `${ID}:block/zeppelin_fabric` },
    elements: [
      solid([3, 6, 4], [13, 8, 12], hull),
      solid([4, 6, 3], [12, 8, 13], hull),
      solid([4.5, 8, 4.5], [11.5, 8.5, 11.5], hull),
      solid([5.5, 5.5, 5.5], [10.5, 6, 10.5], hull),
      solid([4.5, 6.25, 0], [7, 7.75, 4], hull),
      solid([9, 6.25, 0], [11.5, 7.75, 4], hull),
      solid([12, 6.5, 1.5], [13.5, 8, 4.5], hull),
      solid([12.25, 6.75, 0.75], [13.25, 7.75, 1.5], "#glass"),
      solid([7.25, 8.5, 7.25], [8.75, 9.25, 8.75], "#glass"),
      solid([10, 8.5, 5], [11, 9.5, 6], hull),
      solid([7.5, 0, 7.5], [8.5, 5.5, 8.5], "#stand"),
      solid([5.5, 0, 5.5], [10.5, 0.5, 10.5], "#stand"),
    ],
  };
  write(`assets/${ID}/models/block/model_freighter.json`, model);
  facing("model_freighter");
  write(`assets/${ID}/models/item/model_freighter.json`, { parent: `${ID}:block/model_freighter` });
  dropsItself("model_freighter");
  cubes.model_freighter = null;
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
  "block.skyliners.duty_free_counter": "Duty-Free Counter",
  "container.skyliners.duty_free": "Duty Free",
  "subtitles.skyliners.shop_bell": "Shop bell dings",
  "subtitles.skyliners.cash_register": "Till rings",
  "item.skyliners.paper_plane": "Paper Plane",
  "entity.skyliners.paper_plane": "Paper Plane",
  "subtitles.skyliners.paper_plane_whoosh": "Paper plane whooshes",
  "item.skyliners.pilot_cap": "Pilot Cap",
  "block.skyliners.model_airliner": "Model Airliner",
  "block.skyliners.model_zeppelin": "Model Zeppelin",
  "block.skyliners.model_rocket": "Model Rocket",
  "block.skyliners.model_freighter": "Model Freighter",
  "item.skyliners.pilot_helmet": "Pilot Helmet",
  "item.skyliners.pilot_helmet.tooltip": "On the flight deck, its visor shows the head-up display",
  "subtitles.skyliners.hud_on": "Pilot HUD beeps on",
  "item.skyliners.space_helmet": "Space Helmet",
  "item.skyliners.space_helmet.tooltip": "Aboard a rocket in space, you hear its air as you breathe",
  "subtitles.skyliners.space_helmet_breath": "Helmet air hisses",
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
    variants: mountedVariants(["bright", "dim", "night"].map((level) => [`brightness=${level}`, `cabin_lights_${level}`])),
  });
  write(`assets/${ID}/models/item/cabin_lights.json`, { parent: `${ID}:block/cabin_lights_bright` });
  for (const shaded of [false, true]) {
    consoleModule(`window_shades_${shaded ? "closed" : "open"}`, textures(`window_shades_${shaded ? "closed" : "open"}`), "#panel", true, [
      solid([7, CONSOLE_HEIGHT, 1.5], [9, 6.5, 3.5], "#dark"),
      solid([7.5, 6.5, 2], [8.5, 9, 3], "#knob", { origin: [8, 6.5, 2.5], axis: "x", angle: shaded ? 22.5 : -22.5 }),
    ]);
  }
  write(`assets/${ID}/blockstates/window_shades.json`, {
    variants: mountedVariants([false, true].map((shaded) => [`shaded=${shaded}`, `window_shades_${shaded ? "closed" : "open"}`])),
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
// block, its glass a dome over the oval in a steel collar and a recess lined with steel, baked by the mod's own
// "oval_window" loader (see OvalWindowModel), its frame and its edges the cover's undoped weave, tinted in the ship's
// livery as the cover is, and inside the wall round the oval the weave too or, in a room finished its own way, that
// room's wall. Every block takes its share of the glass from one texture over the whole window. Each model is NeoForge's
// composite of two parts: the glass, drawn translucent, and the rest cut out, so the clear hole in the inner face, cut
// out, never hides the steel behind it as a translucent face would wherever it is drawn first. Modelled looking north,
// turned for the other ways. A piece of hers gives back glass. Set into a wall by hand (wall=matched), both its parts
// are marked as set by hand: it fills its whole block, its frame on the block's outer side and its glass standing out
// of it, and its frame looks like the wall beside it (see OvalWindowWall), keeping the weave where no wall is found;
// the whole window, taken away, gives back the window itself from the middle of its bottom row. In the hand: its icon.
// The porthole (see PortholeBlock), a block across and round, is drawn by the same loader at its own size (the
// model's "window"), always as one set by hand, and lying flat as the oval window does; it gives itself back.
{
  const variants = {};
  const edge = `${ID}:block/zeppelin_skin`;
  // A window block's model: its glass and the rest, from its textures, the wall round its oval inside tinted or not,
  // at the size named (the rocket's window's if none).
  const ovalWindowModel = (column, row, { glass, ...rest }, insideTint, setByHand = false, window = undefined) => ({
    loader: "neoforge:composite",
    textures: { particle: glass },
    children: {
      glass: {
        loader: `${ID}:oval_window`, ...(window ? { window } : {}), part: "glass", render_type: "minecraft:translucent", column, row,
        ...(setByHand ? { set_by_hand: true } : {}), textures: { glass, particle: glass },
      },
      frame: {
        loader: `${ID}:oval_window`, ...(window ? { window } : {}), part: "frame", render_type: "minecraft:cutout_mipped", column, row,
        ...(insideTint === undefined ? {} : { inside_tint: insideTint }), ...(setByHand ? { set_by_hand: true } : {}), textures: { ...rest, particle: glass },
      },
    },
  });
  for (let column = 0; column < 3; column++) {
    for (let row = 0; row < 4; row++) {
      for (let dim = 0; dim < 4; dim++) {
        const name = `oval_window_${column}_${row}_dim${dim}`;
        const glass = `${ID}:block/oval_window_glass_dim${dim}`;
        write(`assets/${ID}/models/block/${name}.json`, ovalWindowModel(column, row, {
          glass, frame: `${ID}:block/oval_window_${column}_${row}_frame`, inside: `${ID}:block/oval_window_${column}_${row}_inside`,
          rim: `${ID}:block/oval_window_rim`, lip: `${ID}:block/oval_window_lip`, reveal: `${ID}:block/oval_window_reveal`, edge,
        }));
        for (const [facing, y] of Object.entries(yRot)) {
          variants[`column=${column},dim=${dim},facing=${facing},row=${row},wall=cover`] = { model: `${ID}:block/${name}`, y };
        }
        write(`assets/${ID}/models/block/${name}_matched.json`, ovalWindowModel(column, row, {
          glass, frame: `${ID}:block/oval_window_${column}_${row}_frame`, inside: `${ID}:block/oval_window_${column}_${row}_inside`,
          rim: `${ID}:block/oval_window_rim`, lip: `${ID}:block/oval_window_lip`, reveal: `${ID}:block/oval_window_reveal`, edge,
        }, undefined, true));
        for (const [facing, y] of Object.entries(yRot)) {
          variants[`column=${column},dim=${dim},facing=${facing},row=${row},wall=matched`] = { model: `${ID}:block/${name}_matched`, y };
        }
        // In a room finished its own way, the wall round the oval inside is that room's, untinted.
        for (const finish of WINDOW_FINISHES) {
          write(`assets/${ID}/models/block/${name}_${finish}.json`, ovalWindowModel(column, row, {
            glass, frame: `${ID}:block/oval_window_${column}_${row}_frame`, inside: `${ID}:block/oval_window_${column}_${row}_inside_${finish}`,
            rim: `${ID}:block/oval_window_rim`, lip: `${ID}:block/oval_window_lip`, reveal: `${ID}:block/oval_window_reveal`, edge,
          }, -1));
          for (const [facing, y] of Object.entries(yRot)) {
            variants[`column=${column},dim=${dim},facing=${facing},row=${row},wall=${finish}`] = { model: `${ID}:block/${name}_${finish}`, y };
          }
        }
      }
    }
  }
  write(`assets/${ID}/blockstates/oval_window.json`, { variants });
  // Lying flat in a floor or a roof (see FlatOvalWindowBlock), the same pieces as set into a wall by hand, turned to
  // look up or down, their top row along the way given.
  const flatVariants = {};
  for (let column = 0; column < 3; column++) {
    for (let row = 0; row < 4; row++) {
      for (let dim = 0; dim < 4; dim++) {
        for (const [along, y] of Object.entries(yRot)) {
          const model = `${ID}:block/oval_window_${column}_${row}_dim${dim}_matched`;
          flatVariants[`along=${along},column=${column},dim=${dim},facing=down,row=${row}`] = { model, x: 90, y };
          flatVariants[`along=${along},column=${column},dim=${dim},facing=up,row=${row}`] = { model, x: 270, y: (y + 180) % 360 };
        }
      }
    }
  }
  write(`assets/${ID}/blockstates/flat_oval_window.json`, { variants: flatVariants });
  const setByHand = { condition: "minecraft:block_state_property", block: `${ID}:oval_window`, properties: { wall: "matched" } };
  write(`data/${ID}/loot_table/blocks/oval_window.json`, {
    type: "minecraft:block",
    pools: [
      {
        rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:oval_window` }],
        conditions: [{ ...setByHand, properties: { wall: "matched", column: "1", row: "3" } }, { condition: "minecraft:survives_explosion" }],
      },
      {
        rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: "minecraft:glass" }],
        conditions: [{ condition: "minecraft:inverted", term: setByHand }, { condition: "minecraft:survives_explosion" }],
      },
    ],
    random_sequence: `${ID}:blocks/oval_window`,
  });
  write(`data/${ID}/loot_table/blocks/flat_oval_window.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:oval_window` }],
      conditions: [{ condition: "minecraft:block_state_property", block: `${ID}:flat_oval_window`, properties: { column: "1", row: "3" } },
        { condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/flat_oval_window`,
  });
  write(`assets/${ID}/models/item/oval_window.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/oval_window` } });
  cubes.oval_window = null;
  const portholeVariants = {};
  const flatPortholeVariants = {};
  for (let dim = 0; dim < 4; dim++) {
    const model = `${ID}:block/porthole_dim${dim}`;
    write(`assets/${ID}/models/block/porthole_dim${dim}.json`, ovalWindowModel(0, 0, {
      glass: `${ID}:block/porthole_glass_dim${dim}`, frame: `${ID}:block/porthole_frame`, inside: `${ID}:block/porthole_inside`,
      rim: `${ID}:block/oval_window_rim`, lip: `${ID}:block/oval_window_lip`, reveal: `${ID}:block/oval_window_reveal`, edge,
    }, undefined, true, "porthole"));
    for (const [facing, y] of Object.entries(yRot)) {
      portholeVariants[`dim=${dim},facing=${facing}`] = { model, y };
      flatPortholeVariants[`along=${facing},dim=${dim},facing=down`] = { model, x: 90, y };
      flatPortholeVariants[`along=${facing},dim=${dim},facing=up`] = { model, x: 270, y: (y + 180) % 360 };
    }
  }
  write(`assets/${ID}/blockstates/porthole.json`, { variants: portholeVariants });
  write(`assets/${ID}/blockstates/flat_porthole.json`, { variants: flatPortholeVariants });
  for (const name of ["porthole", "flat_porthole"]) {
    write(`data/${ID}/loot_table/blocks/${name}.json`, {
      type: "minecraft:block",
      pools: [{ rolls: 1.0, bonus_rolls: 0.0, entries: [{ type: "minecraft:item", name: `${ID}:porthole` }], conditions: [{ condition: "minecraft:survives_explosion" }] }],
      random_sequence: `${ID}:blocks/${name}`,
    });
  }
  write(`assets/${ID}/models/item/porthole.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/porthole` } });
  cubes.porthole = null;
}
// The rocket's lift (see LiftMachineBlockEntity). Its machine: a steel housing over the shaft. Its landing doors: a
// pane of smoked glass in a steel frame across the middle of the doorway, a block wide and two tall; open, it has slid
// aside into the shaft's wall by the doorway, so it is drawn reaching into the next block. Its picture boards: the slim
// screen of the camera display and the livery selector, a block wide and two tall, its renderer drawing the floors. The
// floor laid under its car at a landing is not drawn, so its model has nothing in it but the glass's crumbs for its
// particles, and only the lift lays it, so it has no item and gives nothing back; the rest give themselves back.
{
  write(`assets/${ID}/models/block/lift_machine.json`, { parent: "minecraft:block/cube_all", textures: { all: `${ID}:block/lift_machine` } });
  write(`assets/${ID}/blockstates/lift_machine.json`, { variants: { "": { model: `${ID}:block/lift_machine` } } });
  write(`assets/${ID}/models/item/lift_machine.json`, { parent: `${ID}:block/lift_machine` });
  dropsItself("lift_machine");
  cubes.lift_machine = null;
  write(`assets/${ID}/models/block/lift_floor.json`, { textures: { particle: `${ID}:block/lift_glass` } });
  write(`assets/${ID}/blockstates/lift_floor.json`, { variants: { "": { model: `${ID}:block/lift_floor` } } });
  cubes.lift_floor = null;
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
  tallItem("lift_door", "lift_door_lower", "lift_door_upper");
  dropsFrom("lift_door", { half: "lower" });
  cubes.lift_door = null;
  slimScreenModels("lift_board", `${ID}:block/lift_board_casing`, `${ID}:block/rocket_panel_back`);
  write(`assets/${ID}/blockstates/lift_board.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [0, 1, 2, 3, 4, 5, 6, 7].flatMap((part) =>
      [false, true].map((tilted) => [`facing=${facing},part=${part},tilted=${tilted}`, { model: `${ID}:block/lift_board${tilted ? "_tilted" : ""}`, y }])))),
  });
  write(`assets/${ID}/models/item/lift_board.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/lift_board` } });
  dropsFrom("lift_board", { part: "0" });
  cubes.lift_board = null;
  // The call button beside each landing's doors (see LiftCallButtonBlock), its cap showing a lift's arrows up and down.
  litButton("lift_call_button");
}

// The rocket's LED strips (see LedStripBlock): a thin bar along each side of its block that her skin stands on, lying
// on the floor or fixed under the ceiling, its faces glowing whatever the light around and tinted in its story's colour
// (see the client's LedLights); one model for each, drawn along the north side and turned for the others, the bars of
// a strip along two sides meeting in its corner. The panel by each story's lift door and the console in her cockpit
// are slim screens of dark glass that their renderers draw over (see LedPanelRenderer and LedConsoleRenderer), so in
// the hand each is a picture of its own, as the strip is, which is lit only by its tint. All of them give themselves
// back; fitted into her, so breakable, and light as the rest of her is (see FITTINGS).
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
  write(`assets/${ID}/models/item/led_strip.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/led_strip` } });
  dropsItself("led_strip");
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
    slimScreenModels(name, casing, `${ID}:block/rocket_panel_back`);
    write(`assets/${ID}/blockstates/${name}.json`, {
      variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [0, 1, 2, 3, 4, 5, 6, 7].flatMap((part) =>
        [false, true].map((tilted) => [`facing=${facing},part=${part},tilted=${tilted}`, { model: `${ID}:block/${name}${tilted ? "_tilted" : ""}`, y }])))),
    });
    write(`assets/${ID}/models/item/${name}.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/${name}` } });
    dropsFrom(name, { part: "0" });
    cubes[name] = null;
  }
}
function ledStripFittings() {
  return ["led_strip", "led_panel", "led_console"];
}

// The window dimmer by each of the rocket's lift doors (see WindowDimmerBlock): the LED panel's slim screen of dark
// glass, its model and all, its four oval windows drawn by its renderer (see WindowDimmerRenderer), so in the hand it
// is a picture of its own. It gives itself back; fitted into her, so breakable, and light as the rest of her is (see
// FITTINGS).
wideScreen("window_dimmer", "led_panel");
function windowDimmerFittings() {
  return ["window_dimmer"];
}
function windowDimmerLang() {
  return { "block.skyliners.window_dimmer": "Window Dimmer" };
}

// The invisible limit of the rocket's planetarium (see DomeLimitBlock): no model of its own to draw, and next to no
// weight, overriding the airframe's quarter as a strip does.
write(`assets/${ID}/blockstates/dome_limit.json`, { variants: { "": { model: `${ID}:block/dome_limit` } } });
write(`assets/${ID}/models/block/dome_limit.json`, { textures: { particle: `${ID}:block/zeppelin_skin` } });
cubes.dome_limit = null;
write(`data/${ID}/physics_block_properties/dome_limit.json`, {
  priority: 1004,
  selector: `${ID}:dome_limit`,
  properties: { "sable:mass": 0.05 },
});
function ledStripLang() {
  return {
    "block.skyliners.led_strip": "LED Strip",
    "block.skyliners.led_panel": "LED Panel",
    "block.skyliners.led_console": "LED Console",
    "block.skyliners.dome_limit": "Dome Limit",
  };
}

// The rocket's space posters and the fleet's paintings (see SpacePosterBlock), each so many blocks wide and tall, as
// SpacePoster has them. Each block of a poster is a sheet of board a pixel thick, a quarter pixel clear of the back of
// its block, its piece of the picture on its front and plain board behind it and round its edges, drawn evenly lit
// whichever way it faces.
// Modelled facing north, turned for the other ways; a state past the edge of its poster, which nothing places, shows
// the poster's nearest piece.
const SPACE_POSTERS = {
  black_hole: [4, 3], solar_system: [5, 2], quasar: [3, 4], earthrise: [4, 3],
  airliner: [5, 3], zeppelin: [5, 3], rocket: [5, 3], freighter: [5, 3],
};
// Those that are the fleet's paintings, and the item that puts each picture up, named for what it is.
const FLEET_PAINTINGS = new Set(["airliner", "zeppelin", "rocket", "freighter"]);
const spacePosterItem = (poster) => `${poster}_${FLEET_PAINTINGS.has(poster) ? "painting" : "poster"}`;
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
    const item = spacePosterItem(poster);
    write(`assets/${ID}/models/item/${item}.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/${item}` } });
  }
  write(`data/${ID}/loot_table/blocks/space_poster.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: Object.keys(SPACE_POSTERS).map((poster) => ({
        type: "minecraft:item",
        name: `${ID}:${spacePosterItem(poster)}`,
        conditions: [{ condition: "minecraft:block_state_property", block: `${ID}:space_poster`, properties: { poster, column: "0", row: "0" } }],
      })),
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/space_poster`,
  });
}
// The posters and paintings are hung on a ship rather than built into her: light like the rest, but no part of her
// frame, and breakable.
function spacePosterFittings() {
  return ["space_poster"];
}
function spacePosterLang() {
  return {
    "block.skyliners.space_poster": "Space Poster",
    "item.skyliners.black_hole_poster": "Black Hole Poster",
    "item.skyliners.solar_system_poster": "Solar System Poster",
    "item.skyliners.quasar_poster": "Quasar Poster",
    "item.skyliners.earthrise_poster": "Earthrise Poster",
    "item.skyliners.airliner_painting": "Airliner Painting",
    "item.skyliners.zeppelin_painting": "Zeppelin Painting",
    "item.skyliners.rocket_painting": "Rocket Painting",
    "item.skyliners.freighter_painting": "Freighter Painting",
  };
}

// What each of the rocket's rooms hangs on the outside of her lift shaft (see ShaftDecorBlock and RocketShaftDecor),
// each piece drawn facing north, the shaft's glass behind it to the south, and turned for the other ways; nothing of it
// comes nearer the glass than a quarter pixel, so the two are never drawn in one plane. A piece of a pair, as the
// right-hand curtain is, is the left-hand one turned over. The observation deck's curtains: falls of velvet, a shallow
// hollow across their width and the crests of their folds standing out from it, gathered toward the corner girder
// they hang before, narrowing to a gold rope a third of the way up with a tassel hanging from it and flaring again to
// a hem on the floor; over each a box-pleated pelmet with its gold braid and fringe, a gold rosette at its outer end;
// and between them, over the glass, the valance's swags, each dipping across its block, fringed in gold along their
// hems. The boards (the entry's departures, lit; the cargo bay's load chart; the
// mess's chalkboard; the lab's whiteboard): their picture on a panel against the glass and their frame's rim round
// the board's edge, the chalkboard with a ledge of oak and its chalk and eraser, the whiteboard with a tray of
// aluminium and its markers and eraser. The cargo bay's rubber guards, an angle of them round each corner girder, the
// right-hand one reaching round the corner to meet the left-hand one of the next face, a dark cap on top. The fairy
// lights: their wire dipping across each block from hooks on the girders, warm bulbs under it and strands of them
// hanging down into the row under it, each its own length. The ivy: two layers of leaves, a pixel apart. The life
// ring: red and white quarters round its hole, its grab line along the red, on a chrome hook through it. The neon:
// a tube a pixel and a half off the glass, black clips holding it and black caps at its ends where its cable goes into
// the glass. All of it is fitted into the rocket rather than built into her (see shaftDecorFittings); broken, a piece
// gives itself back as it was, but the plain curtain piece its item places, so those stack.
{
  const block = (name) => `${ID}:block/${name}`;
  const ALONGS = ["outer_left", "left", "middle", "right", "outer_right"];
  const ROWS = ["bottom", "lower", "middle", "upper", "top"];
  // How far back toward the glass a piece reaches, in pixels.
  const BACK = 15.75;
  const glow = { neoforge_data: { block_light: 15, sky_light: 15 } };
  const decorModel = (name, textures, elements, cutout = false) => write(`assets/${ID}/models/block/shaft_decor_${name}.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    ...(cutout ? { render_type: "minecraft:cutout" } : {}),
    textures: { particle: Object.values(textures)[0], ...textures },
    elements,
  });
  // A part turned over left to right: the model's east side its west, and a patch given of its texture turned over too.
  const mirrored = (element) => ({
    ...element,
    from: [16 - element.to[0], element.from[1], element.from[2]],
    to: [16 - element.from[0], element.to[1], element.to[2]],
    faces: Object.fromEntries(Object.entries(element.faces).map(([side, face]) => {
      const turned = { east: "west", west: "east" }[side] ?? side;
      const flipped = face.uv && side !== "east" && side !== "west" ? { ...face, uv: [face.uv[2], face.uv[1], face.uv[0], face.uv[3]] } : face;
      return [turned, flipped];
    })),
  });
  // A small part every face of which shows the same patch of its texture, so a bulb or a wire looks the same from all
  // round, and glowing if asked.
  const tiny = (from, to, texture, uv, lit = false) => ({
    from, to, ...(lit ? { shade: false } : {}),
    faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((side) => [side, { texture, uv, ...(lit ? glow : {}) }])),
  });

  // The observation deck's curtains and valance.
  {
    const textures = {
      velvet: block("shaft_decor_velvet"), crest: block("shaft_decor_velvet_crest"), valance: block("shaft_decor_valance"),
      swag: block("shaft_decor_swag"), gold: block("shaft_decor_gold"), fringe: block("shaft_decor_fringe"),
    };
    // How far out from the glass a fall's hollow and its folds' crests stand, in pixels from the block's front.
    const HOLLOW = 14;
    const CREST = 12.5;
    // A fall of curtain between two rows, so many pixels wide from the girder's side (the model's east, the viewer's
    // left), so much further out than its folds stand: its hollow across it, and a crest two pixels wide every four.
    const fall = (y0, y1, width, out = 0) => {
      const parts = [furniture([16 - width, y0, HOLLOW - out], [16, y1, BACK], "#velvet")];
      for (let x = 14; x + 2 > 16 - width; x -= 4) {
        const from = Math.max(x, 16 - width);
        parts.push(furniture([from, y0, CREST - out], [x + 2, y1, HOLLOW - out], "#velvet",
          { north: { texture: "#crest", uv: [0, 16 - y1, x + 2 - from, 16 - y0] }, south: null }));
      }
      return parts;
    };
    // A curtain's bands from the bottom up, each its rows and how wide it hangs, and how much further out it stands.
    const bands = (list) => list.flatMap(([y0, y1, width, out]) => fall(y0, y1, width, out));
    const TIE = 6;
    const curtain = {
      bottom: bands([[0, 2, 14, 1], [2, 5, 12], [5, 8, 11], [8, 12, 10], [12, 16, 9]]),
      lower: fall(0, 16, 9),
      middle: [
        ...bands([[0, 2, 9], [2, 4.5, 8], [4.5, 6.5, 7], [6.5, 8.5, TIE], [8.5, 10, 8], [10, 12, 10], [12, 14, 13], [14, 16, 16]]),
        furniture([16 - TIE - 0.75, 6.75, CREST - 0.75], [16, 8.25, BACK], "#gold"),
        furniture([11.5, 5.25, 11.25], [12.25, 6.75, 12], "#gold"),
        furniture([11, 4.25, 11], [12.75, 5.25, 12.25], "#gold"),
        furniture([10.75, 1.5, 11.1], [13, 4.25, 12.4], "#fringe"),
      ],
      upper: fall(0, 16, 16),
      top: [
        ...fall(0, 11, 16),
        furniture([0, 11, 10.5], [16, 16, BACK], "#valance"),
        furniture([0, 9.5, 10.5], [16, 11, 11], "#fringe"),
        furniture([12.5, 12, 10], [14.5, 14, 10.5], "#gold"),
      ],
    };
    for (const [row, parts] of Object.entries(curtain)) {
      decorModel(`observation_left_${row}`, textures, parts, true);
      decorModel(`observation_right_${row}`, textures, parts.map(mirrored), true);
    }
    // The swag's hem, step by step across its block: from and to, and how low it hangs, at the block's edges level with
    // the bottom of the fringe under the pelmets beside it.
    const SWAG = [[0, 1.5, 11], [1.5, 3.5, 9.5], [3.5, 5.5, 7.5], [5.5, 10.5, 6], [10.5, 12.5, 7.5], [12.5, 14.5, 9.5], [14.5, 16, 11]];
    decorModel("observation_swag", textures, [
      furniture([0, 11, 10.5], [16, 16, BACK], "#valance"),
      ...SWAG.filter(([, , low]) => low < 11).map(([x0, x1, low]) => furniture([x0, low, 11], [x1, 11, 15.5], "#swag")),
      ...SWAG.map(([x0, x1, low]) => furniture([x0, low - 1.5, 11], [x1, low, 11.5], "#fringe")),
    ], true);
  }

  // The boards, each piece of one its picture's block on a panel and its frame's rim along the board's edge.
  const BOARDS = {
    entry: { frame: "shaft_decor_casing", lit: true, ledge: [] },
    cargo: { frame: "shaft_decor_hazard", lit: false, ledge: [] },
    mess: { frame: "shaft_decor_oak", lit: false, ledge: [
      furniture([0, 0, 12], [16, 1.25, 14.25], "#frame"),
      tiny([5, 1.25, 12.5], [9, 2, 13.25], "#ledge", [12, 0, 14, 2]),
      tiny([10, 1.25, 12.25], [13.5, 2.75, 14], "#ledge", [0, 8, 4, 12]),
    ] },
    lab: { frame: "shaft_decor_aluminium", lit: false, ledge: [
      furniture([0, 0, 12], [16, 1, 14.25], "#frame"),
      tiny([2.5, 1, 12.5], [7.5, 1.75, 13.25], "#ledge", [0, 0, 2, 2]),
      tiny([8.5, 1, 12.75], [13.5, 1.75, 13.5], "#ledge", [4, 0, 6, 2]),
      tiny([4, 1, 12.25], [9, 2.5, 14], "#ledge", [8, 8, 12, 12]),
    ] },
  };
  // Which ledge parts go on which piece of the bottom row: the chalk and the eraser, or the markers and the eraser.
  const LEDGES = { mess: { left: [], middle: [1], right: [2] }, lab: { left: [3], middle: [], right: [1, 2] } };
  for (const [kind, { frame, lit, ledge }] of Object.entries(BOARDS)) {
    for (const along of ["left", "middle", "right"]) {
      for (const row of ["bottom", "top"]) {
        const picture = { texture: "#picture", ...(lit ? glow : {}) };
        const rims = [
          ...(row === "top" ? [furniture([0, 15, 13.25], [16, 16, 14.25], "#frame")] : []),
          ...(row === "bottom" ? [furniture([0, 0, 13.25], [16, 1, 14.25], "#frame")] : []),
          ...(along === "left" ? [furniture([15, 0, 13.25], [16, 16, 14.25], "#frame")] : []),
          ...(along === "right" ? [furniture([0, 0, 13.25], [1, 16, 14.25], "#frame")] : []),
        ];
        const ledgeParts = row === "bottom" && ledge.length ? [ledge[0], ...(LEDGES[kind]?.[along] ?? []).map((part) => ledge[part])] : [];
        decorModel(`${kind}_${along}_${row}`, {
          picture: block(`shaft_decor_${kind}_${along}_${row}`), frame: block(frame), ledge: block("shaft_decor_markers"),
        }, [
          { ...furniture([0, 0, 14.25], [16, 16, BACK], "#frame", { north: picture }), ...(lit ? { shade: false } : {}) },
          ...rims,
          ...ledgeParts,
        ]);
      }
    }
  }

  // The cargo bay's corner guards: an angle of striped rubber before the girder's outer half, from the floor up to
  // their top under a dark cap, the right-hand one reaching round the corner; the stripes run on from one row to the
  // next and down the corner.
  for (const [side, x0, x1] of [["left", 9, 16], ["right", -2, 7]]) {
    for (const [row, top] of [["bottom", 16], ["top", 10]]) {
      const v0 = 16 - top;
      const width = x1 - x0;
      const faces = {
        north: { texture: "#rubber", uv: [0, v0, width, 16] }, south: { texture: "#rubber", uv: [0, v0, width, 16] },
        east: { texture: "#rubber", uv: [0, v0, 1.75, 16] }, west: { texture: "#rubber", uv: [0, v0, 1.75, 16] },
        up: { texture: "#rubber", uv: [0, 0, width, 1.75] }, down: { texture: "#rubber", uv: [0, 0, width, 1.75] },
      };
      const cap = row === "top" ? [tiny([x0 + 0.25, top, 14.25], [x1 - 0.25, top + 0.5, BACK], "#cap", [1, 1, 3, 3])] : [];
      decorModel(`cargo_guard_${side}_${row}`, { rubber: block("shaft_decor_hazard"), cap: block("shaft_decor_casing") },
        [{ from: [x0, 0, 14], to: [x1, top, BACK], faces }, ...cap]);
    }
  }

  // The patches of the fairy lights' textures their parts show: the wire's dark green, and a bulb's warm middle.
  const WIRE = [0, 0, 1, 1];
  const LIGHT = [6, 6, 8, 8];
  // The bunk room's fairy lights: the wire's height at each pixel across its block, dipping between the hooks at its
  // edges; where its bulbs hang from it; and the strands hanging from it in each block, where across and how far down
  // they reach in pixels, their bulbs every few pixels and one at the end.
  {
    const wire = (x) => 10.5 + 3.5 * ((x - 8) / 8) ** 2;
    const BULBS = [2, 6, 10, 14];
    const BULB_APART = 5;
    const DROPS = {
      outer_left: [[4, 9], [12, 16]], left: [[4, 14], [12, 7]], middle: [[4, 18], [12, 11]], right: [[4, 8], [12, 15]],
      outer_right: [[4, 13], [12, 6]],
    };
    const bulb = (x, y) => [
      tiny([x - 0.6, y - 0.1, 14.45], [x + 0.6, y + 0.5, 15.35], "#wire", WIRE),
      tiny([x - 0.8, y - 1.7, 14.3], [x + 0.8, y - 0.1, 15.5], "#bulb", LIGHT, true),
    ];
    const textures = { bulb: block("shaft_decor_bulb"), wire: block("shaft_decor_wire") };
    for (const along of ALONGS) {
      const top = [];
      for (let x = 0; x < 16; x += 2) {
        const y = wire(x + 1);
        top.push(tiny([x, y - 0.3, 14.75], [x + 2, y + 0.3, 15.35], "#wire", WIRE));
      }
      for (const x of BULBS) top.push(...bulb(x, wire(x) - 0.3));
      if (along === "outer_left") top.push(tiny([15, 13.4, 14.25], [16, 14.6, BACK], "#wire", WIRE));
      if (along === "outer_right") top.push(tiny([0, 13.4, 14.25], [1, 14.6, BACK], "#wire", WIRE));
      const upper = [];
      for (const [x, length] of DROPS[along]) {
        const from = wire(x);
        const end = from - length;
        top.push(tiny([x - 0.3, Math.max(0, end), 14.75], [x + 0.3, from, 15.35], "#wire", WIRE));
        if (end < 0) upper.push(tiny([x - 0.3, 16 + end, 14.75], [x + 0.3, 16, 15.35], "#wire", WIRE));
        const hung = [];
        for (let down = BULB_APART; down < length - 1; down += BULB_APART) hung.push(from - down);
        hung.push(end);
        for (const y of hung) (y >= 0 ? top : upper).push(...bulb(x, y >= 0 ? y : y + 16));
      }
      decorModel(`bunks_${along}_top`, textures, top);
      decorModel(`bunks_${along}_upper`, textures, upper);
    }
  }

  // The greenhouse's ivy: its leaves and stems on a plane a quarter pixel off the glass and more leaves on another a
  // pixel in front of it, both seen from behind as well; the right-hand column the left one turned over.
  {
    const plane = (z, texture, flipped) => {
      const uv = flipped ? [16, 0, 0, 16] : [0, 0, 16, 16];
      return { from: [0, 0, z], to: [16, 16, z], faces: { north: { texture, uv }, south: { texture, uv } } };
    };
    const ivy = (name, picture, flipped = false) => decorModel(`greenhouse_${name}`, {
      back: block(`shaft_decor_ivy_${picture}`), front: block(`shaft_decor_ivy_${picture}_front`),
    }, [plane(15.5, "#back", flipped), plane(14.5, "#front", flipped)], true);
    for (const row of ["bottom", "middle", "top"]) {
      ivy(`column_left_${row}`, `column_${row}`);
      ivy(`column_right_${row}`, `column_${row}`, true);
    }
    for (const along of ["left", "middle", "right"]) {
      ivy(`garland_${along}`, `garland_${along}`);
      ivy(`trail_${along}`, `trail_${along}`);
    }
  }

  // The pool's life ring, its quarters and grab line the same on its stand (below) as on its hook.
  const RING_TEXTURES = { red: block("shaft_decor_ring_red"), white: block("shaft_decor_ring_white"), hook: block("lavatory_basin_chrome") };
  const RING = [
    furniture([5.5, 11.5, 13.5], [10.5, 14.5, 15.25], "#red"),
    furniture([5.5, 1.5, 13.5], [10.5, 4.5, 15.25], "#red"),
    furniture([1.5, 5.5, 13.5], [4.5, 10.5, 15.25], "#red"),
    furniture([11.5, 5.5, 13.5], [14.5, 10.5, 15.25], "#red"),
    ...[[3, 3], [10.5, 3], [3, 10.5], [10.5, 10.5]].map(([x, y]) => furniture([x, y, 13.5], [x + 2.5, y + 2.5, 15.25], "#white")),
    furniture([5, 14.5, 13.75], [11, 15, 14.75], "#white"),
    furniture([5, 1, 13.75], [11, 1.5, 14.75], "#white"),
    furniture([1, 5, 13.75], [1.5, 11, 14.75], "#white"),
    furniture([14.5, 5, 13.75], [15, 11, 14.75], "#white"),
  ];
  decorModel("pool_ring", RING_TEXTURES, [
    ...RING,
    furniture([6.75, 9.5, 15.25], [9.25, 12, BACK], "#hook"),
    furniture([7.25, 10, 12.5], [8.75, 11, 15.25], "#hook"),
    furniture([7.25, 11, 12.5], [8.75, 12.5, 13.25], "#hook"),
  ]);

  // The club's neon tubes, a pink one and a cyan one: a middle row's tube with a clip round it halfway up, and its
  // ends, where the tube stops at a cap whose cable runs back into the glass.
  for (const colour of ["pink", "cyan"]) {
    const textures = { tube: block(`neon_${colour}`), holder: block("neon_backer") };
    const tube = (y0, y1) => furniture([7, y0, 12], [9, y1, 14], "#tube", {}, true);
    const cap = (y0, y1) => [
      furniture([6.25, y0, 11.25], [9.75, y1, 14.75], "#holder"),
      furniture([7.5, y0 + 0.25, 14.75], [8.5, y1 - 0.25, BACK], "#holder"),
    ];
    decorModel(`club_${colour}_bottom`, textures, [tube(2.5, 16), ...cap(1, 2.5)]);
    decorModel(`club_${colour}_middle`, textures, [tube(0, 16), furniture([6.5, 7.5, 11.5], [9.5, 8.5, BACK], "#holder")]);
    decorModel(`club_${colour}_top`, textures, [tube(0, 13.5), ...cap(13.5, 15)]);
  }

  // Each room's own small decoration, whole in one block, for where a piece of one several blocks wide would make no
  // sense: standing on a floor (its model's name ending in _floor) or hung under a ceiling (_ceiling), drawn facing
  // north, toward whoever put it up, with what holds it there. The departures board: a screen in its graphite casing,
  // its small picture lit behind a bezel, on a chrome pole from a weighted foot; or hung on two chrome rods and lit on
  // both sides, the other two destinations on its back. The mess's menu: an A-frame sandwich board, its two oak frames
  // leaning together and held apart by a steel stay either side, the burger chalked on both; or a board in an oak
  // frame hung on two small chains, the noodles on both its sides. The bunk room's fairy lights: strands of warm bulbs
  // hanging from two wires clipped under the ceiling. The greenhouse's ivy: heaped in a terracotta pot and trailing
  // over its rim down its sides; or a mat of it clinging under the ceiling, its garlands trailing down from it. The
  // pool's life ring, a quarter smaller, on a chrome hook from a chrome post on its foot plate. The club's neon: two
  // tubes standing on black stands, the pink one taller; or hung level on black wires, the pink over the cyan. The
  // lab's whiteboard: a mobile one, between two aluminium uprights on castored feet, its marker tray along its foot,
  // its back wiped clean.
  {
    // A flat picture of a cutout texture, seen from both sides: standing across x or across z, or lying level.
    const sheet = (from, to, texture, uv, rotation) => ({
      from, to, ...(rotation ? { rotation } : {}),
      faces: Object.fromEntries((from[0] === to[0] ? ["east", "west"] : from[2] === to[2] ? ["north", "south"] : ["up", "down"])
        .map((side) => [side, { texture, uv }])),
    });
    // A part turned half round about the block's upright middle, as the back of something is its front seen from
    // behind: its north face its south and its east its west, leaning the other way; its top and underside as before.
    const AROUND = { north: "south", south: "north", east: "west", west: "east", up: "up", down: "down" };
    const turned = (element) => ({
      ...element,
      from: [16 - element.to[0], element.from[1], 16 - element.to[2]],
      to: [16 - element.from[0], element.to[1], 16 - element.from[2]],
      ...(element.rotation ? { rotation: { ...element.rotation, angle: element.rotation.axis === "y" ? element.rotation.angle : -element.rotation.angle,
        origin: [16 - element.rotation.origin[0], element.rotation.origin[1], 16 - element.rotation.origin[2]] } } : {}),
      faces: Object.fromEntries(Object.entries(element.faces).map(([side, face]) => [AROUND[side], face])),
    });
    // A length of the game's chain at half its size, hanging at a place between two heights, its two strips crossed.
    const chain = (x, z, low, high) => [
      { from: [x - 0.75, low, z], to: [x + 0.75, high, z], rotation: { angle: 45, axis: "y", origin: [x, 8, z] },
        faces: Object.fromEntries(["north", "south"].map((side) => [side, { texture: "#chain", uv: [0, 0, 3, 2 * (high - low)] }])) },
      { from: [x, low, z - 0.75], to: [x, high, z + 0.75], rotation: { angle: 45, axis: "y", origin: [x, 8, z] },
        faces: Object.fromEntries(["east", "west"].map((side) => [side, { texture: "#chain", uv: [3, 0, 6, 2 * (high - low)] }])) },
    ];
    const lit = (uv) => ({ texture: "#picture", uv, ...glow });
    // A patch of the neon's black holder clear of the grey screws at its corners, for small black parts.
    const BLACK = [4, 4, 8, 8];

    // The departures board's screen, its foot at a height: its casing, ten pixels tall, and the picture behind the
    // bezel of its front and, if asked, of its back.
    const SCREEN = 10;
    const bezel = (foot, z0, z1) => [
      furniture([1, foot + SCREEN - 1, z0], [15, foot + SCREEN, z1], "#frame"),
      furniture([1, foot, z0], [15, foot + 1, z1], "#frame"),
      furniture([1, foot + 1, z0], [2, foot + SCREEN - 1, z1], "#frame"),
      furniture([14, foot + 1, z0], [15, foot + SCREEN - 1, z1], "#frame"),
    ];
    const screen = (foot, backed) => [
      furniture([1, foot, 7], [15, foot + SCREEN, 9], "#frame"),
      { ...furniture([2, foot + 1, 6.75], [14, foot + SCREEN - 1, 7], "#frame", { north: lit([0, 0, 12, 8]) }), shade: false },
      ...bezel(foot, 6.25, 7),
      ...(backed ? [{ ...furniture([2, foot + 1, 9], [14, foot + SCREEN - 1, 9.25], "#frame", { south: lit([0, 8, 12, 16]) }), shade: false },
        ...bezel(foot, 9, 9.75)] : []),
    ];
    const entry = { picture: block("shaft_decor_entry_small"), frame: block("shaft_decor_casing"), chrome: block("lavatory_basin_chrome") };
    decorModel("entry_floor", entry, [
      furniture([4, 0, 5], [12, 1, 11], "#frame"),
      furniture([5, 1, 6], [11, 1.5, 10], "#frame"),
      furniture([7, 1.5, 7], [9, 6, 9], "#chrome"),
      ...screen(6, false),
    ]);
    decorModel("entry_ceiling", entry, [
      ...screen(3, true),
      ...[4, 12].flatMap((x) => [furniture([x - 0.5, 13, 7.5], [x + 0.5, 15.5, 8.5], "#chrome"), furniture([x - 1, 15.5, 7], [x + 1, 16, 9], "#chrome")]),
    ]);

    // The sandwich board's front frame, leaning out at its foot from the top, where its back frame meets it: its two
    // legs and its rails, the chalkboard set back between them; and the steel stays from one frame to the other.
    const LEAN = { angle: 22.5, axis: "x", origin: [8, 15, 8] };
    const sandwich = [
      furniture([2, -1.2, 6.75], [3, 15, 8], "#frame"),
      furniture([13, -1.2, 6.75], [14, 15, 8], "#frame"),
      furniture([3, 14, 6.75], [13, 15, 8], "#frame"),
      furniture([3, 1, 6.75], [13, 2, 8], "#frame"),
      furniture([3, 2, 7.25], [13, 14, 7.75], "#frame", { north: { texture: "#picture", uv: [0, 0, 10, 12] } }),
    ].map((part) => ({ ...part, rotation: LEAN }));
    decorModel("mess_floor", { picture: block("shaft_decor_mess_stand"), frame: block("shaft_decor_oak"), steel: block("lavatory_basin_chrome") }, [
      ...sandwich,
      ...sandwich.map(turned),
      ...[2.25, 13.25].map((x) => tiny([x, 4.75, 3.75], [x + 0.5, 5.25, 12.25], "#steel", [0, 0, 2, 2])),
    ]);
    decorModel("mess_ceiling", {
      picture: block("shaft_decor_mess_hung"), frame: block("shaft_decor_oak"), steel: block("lavatory_basin_chrome"), chain: "minecraft:block/chain",
    }, [
      furniture([2, 3, 7.5], [14, 12, 8.5], "#frame", { north: { texture: "#picture", uv: [0, 0, 12, 9] }, south: { texture: "#picture", uv: [0, 0, 12, 9] } }),
      furniture([1, 12, 7], [15, 13, 9], "#frame"),
      furniture([1, 2, 7], [15, 3, 9], "#frame"),
      furniture([1, 3, 7], [2, 12, 9], "#frame"),
      furniture([14, 3, 7], [15, 12, 9], "#frame"),
      ...[4, 12].flatMap((x) => [tiny([x - 0.5, 13, 7.75], [x + 0.5, 13.5, 8.25], "#steel", [0, 0, 2, 2]), ...chain(x, 8, 13.5, 16)]),
    ], true);

    // The fairy lights under a ceiling: along each wire, how far back it runs and where across its strands hang and
    // how far down, in pixels; how high the wires' underside is, and how far apart the bulbs hang down a strand.
    const WIRES = [[5.5, [[3, 8], [8, 13], [13, 10]]], [10.5, [[5.5, 11], [10.5, 6]]]];
    const UNDER = 15.25;
    const APART = 4;
    const hungBulb = (x, y, z) => [
      tiny([x - 0.5, y, z - 0.5], [x + 0.5, y + 0.5, z + 0.5], "#wire", WIRE),
      tiny([x - 0.75, y - 1.5, z - 0.75], [x + 0.75, y, z + 0.75], "#bulb", LIGHT, true),
    ];
    const strand = (x, z, length) => {
      const parts = [tiny([x - 0.25, UNDER - length, z - 0.25], [x + 0.25, UNDER, z + 0.25], "#wire", WIRE)];
      for (let down = APART; down < length - 1; down += APART) parts.push(...hungBulb(x, UNDER - down, z));
      return [...parts, ...hungBulb(x, UNDER - length, z)];
    };
    decorModel("bunks_ceiling", { bulb: block("shaft_decor_bulb"), wire: block("shaft_decor_wire") }, WIRES.flatMap(([z, strands]) => [
      tiny([1, UNDER, z - 0.25], [15, UNDER + 0.5, z + 0.25], "#wire", WIRE),
      ...[1.25, 14.25].map((x) => tiny([x, UNDER + 0.5, z - 0.25], [x + 0.5, 16, z + 0.25], "#wire", WIRE)),
      ...strands.flatMap(([x, length]) => strand(x, z, length)),
    ]));

    // The ivy in its pot: the pot, narrowing to its foot, its rim round the soil; the leaves heaped over the soil, two
    // pictures crossed and another a pixel out from one of them; and down each side from the rim a strand trailing
    // over it, leaves along it.
    const heap = block("shaft_decor_ivy_column_middle");
    const CROSSED = { angle: 45, axis: "y", origin: [8, 8, 8] };
    // Down each side of the pot, which strands trail and from how far across their picture the patch shown starts,
    // where a strand runs down it, a quarter pixel out from the rim, eight pixels wide and reaching nearly to the
    // floor.
    const DRAPES = { north: ["left", 0], south: ["middle", 3], west: ["right", 3], east: ["right", 8] };
    const drape = (side, along, u) => {
      const [from, to] = {
        north: [[4, 0.5, 3.75], [12, 7.5, 3.75]],
        south: [[4, 0.5, 12.25], [12, 7.5, 12.25]],
        west: [[3.75, 0.5, 4], [3.75, 7.5, 12]],
        east: [[12.25, 0.5, 4], [12.25, 7.5, 12]],
      }[side];
      return sheet(from, to, `#${along}`, [u, 0, u + 8, 7]);
    };
    decorModel("greenhouse_floor", {
      heap, heapFront: block("shaft_decor_ivy_column_middle_front"), pot: "minecraft:block/terracotta", soil: block("planter_soil"),
      ...Object.fromEntries(["left", "middle", "right"].map((along) => [along, block(`shaft_decor_ivy_trail_${along}`)])),
    }, [
      furniture([5, 0, 5], [11, 2, 11], "#pot"),
      furniture([4.5, 2, 4.5], [11.5, 6, 11.5], "#pot"),
      furniture([4, 6, 4], [12, 7, 5], "#pot"),
      furniture([4, 6, 11], [12, 7, 12], "#pot"),
      furniture([4, 6, 5], [5, 7, 11], "#pot"),
      furniture([11, 6, 5], [12, 7, 11], "#pot"),
      furniture([5, 5.5, 5], [11, 6.5, 11], "#soil"),
      sheet([2, 6.5, 8], [14, 13, 8], "#heap", [2, 5, 14, 11.5], CROSSED),
      sheet([8, 6.5, 2], [8, 13, 14], "#heap", [2, 9.5, 14, 16], CROSSED),
      sheet([2, 6.5, 7], [14, 13, 7], "#heapFront", [2, 5, 14, 11.5], CROSSED),
      ...Object.entries(DRAPES).map(([side, [along, u]]) => drape(side, along, u)),
    ], true);
    // The ivy under a ceiling: a mat of its leaves a quarter pixel under it, and two garlands hanging from it, crossed
    // through the block's middle, their trailing stems along the ceiling and their strands hanging down.
    decorModel("greenhouse_ceiling", {
      heap, middle: block("shaft_decor_ivy_garland_middle"), left: block("shaft_decor_ivy_garland_left"),
    }, [
      sheet([1, 15.75, 1], [15, 15.75, 15], "#heap", [1, 1, 15, 15]),
      sheet([1, 3, 8], [15, 16, 8], "#middle", [1, 3, 15, 16]),
      sheet([8, 3, 1], [8, 16, 15], "#left", [1, 3, 15, 16]),
    ], true);

    // The life ring on its stand: the ring a quarter smaller, its middle nine pixels up, hung in front of the post; the
    // post on its foot plate, capped, and the hook out from it through the ring's hole, turned up in front of it.
    const onStand = ([x, y, z]) => [2 + 0.75 * x, 3 + 0.75 * y, 0.75 * z - 1.6875];
    decorModel("pool_floor", RING_TEXTURES, [
      ...RING.map((part) => ({ ...part, from: onStand(part.from), to: onStand(part.to) })),
      furniture([5, 0, 8], [11, 1, 14], "#hook"),
      furniture([7, 1, 10], [9, 15.5, 12], "#hook"),
      furniture([6.75, 15.5, 9.75], [9.25, 16, 12.25], "#hook"),
      furniture([7.5, 10.625, 7.75], [8.5, 11.625, 10], "#hook"),
      furniture([7.5, 11.625, 7.75], [8.5, 12.625, 8.25], "#hook"),
    ]);

    // The neon standing: a tube on its stand, its middle at a place on the floor, so many pixels tall over the stand's
    // collar, capped. Hung: a tube level along the block at a height and a place across it, in a cap at each end, and a
    // wire up from each cap to a clip on the ceiling.
    const neon = { pink: block("neon_pink"), cyan: block("neon_cyan"), holder: block("neon_backer") };
    const standing = (tube, x, z, tall) => [
      furniture([x - 2.5, 0, z - 2.5], [x + 2.5, 1, z + 2.5], "#holder"),
      furniture([x - 1.75, 1, z - 1.75], [x + 1.75, 3, z + 1.75], "#holder"),
      furniture([x - 1, 3, z - 1], [x + 1, 3 + tall, z + 1], tube, {}, true),
      furniture([x - 1.75, 3 + tall, z - 1.75], [x + 1.75, 4 + tall, z + 1.75], "#holder"),
    ];
    const hanging = (tube, y, z) => [
      furniture([2.5, y - 1, z - 1], [13.5, y + 1, z + 1], tube, {}, true),
      ...[1, 13.5].flatMap((x) => [
        tiny([x, y - 1.25, z - 1.25], [x + 1.5, y + 1.25, z + 1.25], "#holder", BLACK),
        tiny([x + 0.5, y + 1.25, z - 0.25], [x + 1, 15.5, z + 0.25], "#holder", BLACK),
        tiny([x, 15.5, z - 0.75], [x + 1.5, 16, z + 0.75], "#holder", BLACK),
      ]),
    ];
    decorModel("club_floor", neon, [...standing("#pink", 5, 6, 11.5), ...standing("#cyan", 11, 10.5, 8)]);
    decorModel("club_ceiling", neon, [...hanging("#pink", 9, 5), ...hanging("#cyan", 5, 11)]);

    // The mobile whiteboard: the board in its aluminium frame, its picture on its front and its back wiped clean; each
    // upright with its black pivot knob, its foot and the black castors under it; the bar between the uprights, and
    // the tray along the board's foot with a red marker, a blue one and the eraser on it.
    decorModel("lab_floor", {
      picture: block("shaft_decor_lab_small"), frame: block("shaft_decor_aluminium"), ledge: block("shaft_decor_markers"), black: block("neon_backer"),
    }, [
      furniture([2, 6, 7.5], [14, 14, 8.5], "#frame", { north: { texture: "#picture", uv: [0, 0, 12, 8] }, south: { texture: "#picture", uv: [0, 8, 12, 16] } }),
      furniture([1.5, 14, 7], [14.5, 14.75, 9], "#frame"),
      furniture([1.5, 5.25, 7], [14.5, 6, 9], "#frame"),
      furniture([1.5, 6, 7], [2, 14, 9], "#frame"),
      furniture([14, 6, 7], [14.5, 14, 9], "#frame"),
      ...[[0.75, 0.25], [14.5, 15.25]].flatMap(([x, knob]) => [
        furniture([x, 1.75, 7.5], [x + 0.75, 15, 8.5], "#frame"),
        tiny([knob, 9.5, 7.5], [knob + 0.5, 10.5, 8.5], "#black", BLACK),
        furniture([x - 0.25, 0.75, 3], [x + 1, 1.75, 13], "#frame"),
        ...[3.25, 11.5].map((z) => tiny([x - 0.25, 0, z], [x + 1, 0.75, z + 1.25], "#black", BLACK)),
      ]),
      furniture([1.5, 2.5, 7.75], [14.5, 3.25, 8.25], "#frame"),
      furniture([2, 5.25, 6], [14, 5.75, 7], "#frame"),
      tiny([3.5, 5.75, 6.125], [6.5, 6.25, 6.625], "#ledge", [0, 0, 2, 2]),
      tiny([7, 5.75, 6.25], [10, 6.25, 6.75], "#ledge", [4, 0, 6, 2]),
      tiny([10.75, 5.75, 6], [13, 6.75, 6.875], "#ledge", [8, 8, 12, 12]),
    ]);
  }

  // Which model each piece is, by its room, where across the face it is and its row; a state nothing places shows the
  // nearest piece of its room's that is.
  const side = (along) => (along.endsWith("left") ? "left" : "right");
  const inner = (along) => along.replace("outer_", "");
  const outer = (along) => along.startsWith("outer_");
  const high = (row) => (row === "bottom" || row === "lower" ? "bottom" : "top");
  const third = (row) => (row === "bottom" || row === "top" ? row : "middle");
  const MODELS = {
    entry: (along, row) => `entry_${inner(along)}_${high(row)}`,
    cargo: (along, row) => (outer(along) ? `cargo_guard_${side(along)}_${high(row)}` : `cargo_${along}_${high(row)}`),
    mess: (along, row) => `mess_${inner(along)}_${high(row)}`,
    bunks: (along, row) => `bunks_${along}_${row === "top" ? "top" : "upper"}`,
    greenhouse: (along, row) => (outer(along) ? `greenhouse_column_${side(along)}_${third(row)}`
      : `greenhouse_${row === "top" ? "garland" : "trail"}_${along}`),
    pool: () => "pool_ring",
    club: (along, row) => `club_${outer(along) ? "cyan" : "pink"}_${third(row)}`,
    lab: (along, row) => `lab_${inner(along)}_${high(row)}`,
    observation: (along, row) => (outer(along) ? `observation_${side(along)}_${row}` : "observation_swag"),
  };
  // Which rooms have a small decoration of their own standing on a floor and hung under a ceiling (see
  // ShaftDecorBlock's STANDING and HUNG), the same whatever piece it is; a room with none for a mount shows its middle
  // piece there, as nothing puts it up so.
  const SMALL = { floor: ["entry", "mess", "greenhouse", "pool", "club", "lab"], ceiling: ["entry", "mess", "bunks", "greenhouse", "club"] };
  write(`assets/${ID}/blockstates/shaft_decor.json`, {
    variants: Object.fromEntries([
      ...Object.entries(MODELS).flatMap(([kind, model]) => ALONGS.flatMap((along) => ROWS.flatMap((row) =>
        Object.entries(yRot).map(([facing, y]) => [`along=${along},facing=${facing},kind=${kind},mount=wall,row=${row}`,
          { model: `${ID}:block/shaft_decor_${model(along, row)}`, ...(y ? { y } : {}) }])))),
      ...Object.entries(SMALL).flatMap(([mount, kinds]) => Object.entries(MODELS).flatMap(([kind, model]) => Object.entries(yRot).map(([facing, y]) =>
        [`facing=${facing},kind=${kind},mount=${mount}`,
          { model: `${ID}:block/shaft_decor_${kinds.includes(kind) ? `${kind}_${mount}` : model("middle", "middle")}`, ...(y ? { y } : {}) }]))),
    ]),
  });
  write(`assets/${ID}/models/item/shaft_decor.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/shaft_decor` } });
  const decorId = `${ID}:shaft_decor`;
  write(`data/${ID}/loot_table/blocks/shaft_decor.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item",
        name: decorId,
        functions: [{
          function: "minecraft:copy_state", block: decorId, properties: ["kind", "along", "row"],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: decorId,
            properties: { kind: "observation", along: "outer_left", row: "middle" } } }],
        }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/shaft_decor`,
  });
  cubes.shaft_decor = null;
}
// The shaft's decorations are hung on the rocket rather than built into her: light like the rest, but no part of her
// frame, and breakable.
function shaftDecorFittings() {
  return ["shaft_decor"];
}
function shaftDecorLang() {
  return {
    "block.skyliners.shaft_decor": "Shaft Decoration",
  };
}

// The rocket's snack machine in her cafeteria (see SnackMachineBlock), two blocks tall, drawn facing north: a
// midnight-blue cabinet a pixel in from its sides and back, the front of its upper block crowned with its orange badge,
// a chrome kick plate along its foot. Its showcase of snacks, lit, runs down from the upper block into the lower,
// with the control column beside it on the right as one faces it: the lamp and the keypad above, the card reader
// below; under the showcase, the flap a snack drops into, lit green with the packet in it once one has. The lamp's
// colour is the step it is at: cyan ready, blinking amber while the spiral turns, green with a snack in the flap. One
// model for each block and step (the lower block shows only whether a snack waits), turned for each facing. Its lower
// block gives it back.
{
  const block = (name) => `${ID}:block/${name}`;
  const cabinet = { side: block("snack_machine_side"), top: block("snack_machine_top"), trim: block("snack_machine_trim"), particle: block("snack_machine_side") };
  const VENDS = ["ready", "turning", "dropped"];
  for (const vend of VENDS) {
    write(`assets/${ID}/models/block/snack_machine_upper_${vend}.json`, boxes(
      { ...cabinet, front: block("snack_machine_front_upper"), showcase: block("snack_machine_showcase_upper"), lamp: block(`snack_machine_lamp_${vend}`),
        keypad: block("snack_machine_keypad") },
      [
        [[1, 0, 2], [15, 16, 15], { north: "#front", up: "#top", all: "#side" }],
        [[6, 0, 1.5], [14, 12, 2], "#showcase", true],
        [[2, 9, 1.25], [5, 11, 2], "#lamp", true],
        [[2, 2, 1.5], [5, 8, 2], "#keypad", true],
      ],
    ));
  }
  for (const dropped of [false, true]) {
    write(`assets/${ID}/models/block/snack_machine_lower_${dropped ? "dropped" : "ready"}.json`, boxes(
      { ...cabinet, front: block("snack_machine_front_lower"), showcase: block("snack_machine_showcase_lower"), reader: block("snack_machine_reader"),
        flap: block(dropped ? "snack_machine_flap_lit" : "snack_machine_flap") },
      [
        [[1, 0, 2], [15, 16, 15], { north: "#front", all: "#side" }],
        [[1, 0, 1.25], [15, 1, 2], "#trim"],
        [[6, 7, 1.5], [14, 16, 2], "#showcase", true],
        [[6, 2, 1.25], [14, 5, 2], "#flap", dropped],
        [[2, 10, 1.5], [5, 13, 2], "#reader", true],
      ],
    ));
  }
  const model = (half, vend) => (half === "upper" ? `snack_machine_upper_${vend}` : `snack_machine_lower_${vend === "dropped" ? "dropped" : "ready"}`);
  write(`assets/${ID}/blockstates/snack_machine.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["lower", "upper"].flatMap((half) => VENDS.map((vend) =>
      [`facing=${facing},half=${half},vend=${vend}`, { model: `${ID}:block/${model(half, vend)}`, y }])))),
  });
  tallItem("snack_machine", "snack_machine_lower_ready", "snack_machine_upper_ready");
  dropsFrom("snack_machine", { half: "lower" });
  cubes.snack_machine = null;
}
// The snack machine is fitted into the rocket rather than built into her: light like the rest, but no part of her
// frame, and breakable.
function snackMachineFittings() {
  return ["snack_machine"];
}
// The whirr of its spiral turning and the clunk of a snack dropping into its flap (see tools/generate_sounds.mjs).
function snackMachineSounds() {
  return Object.fromEntries(["snack_machine_whirr", "snack_machine_clunk"].map((name) =>
    [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function snackMachineLang() {
  return {
    "block.skyliners.snack_machine": "Snack Machine",
    "subtitles.skyliners.snack_machine_whirr": "Snack machine whirs",
    "subtitles.skyliners.snack_machine_clunk": "Snack drops",
  };
}
// The rocket's rover bay (see RoverHatchBlock and RoverPanelBlock). Its door's parts are drawn only by the bay's
// renderer (see RoverBayRenderer), door, platform, boom and all, so their model is only a particle texture. Its panel
// is a board two sixteenths thick at the back of its block, as the skywriting board is, its face in the colour of how
// the bay stands. Only the blueprint places them, so neither has an item or drops anything, and both are part of her
// frame, which only a wrench takes apart, as her other controls are. The invisible plates the folded-down door is
// walked on (see RoverPlatformBlock) only give the particles feet kick up off its deck; the bay lays and takes them up,
// so they have no item and drop nothing, light as the rest of her but no part of her frame (see roverFittings). The
// rover's remote: its antenna's tip lit green once it is paired (see RoverClient).
{
  write(`assets/${ID}/blockstates/rover_hatch.json`, { variants: { "": { model: `${ID}:block/rover_hatch` } } });
  write(`assets/${ID}/models/block/rover_hatch.json`, { textures: { particle: `${ID}:block/airframe_panel` } });
  cubes.rover_hatch = null;
  write(`assets/${ID}/blockstates/rover_platform.json`, { variants: { "": { model: `${ID}:block/rover_platform` } } });
  write(`assets/${ID}/models/block/rover_platform.json`, { textures: { particle: `${ID}:block/airstair_tread` } });
  cubes.rover_platform = null;
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
function roverFittings() {
  return ["rover_platform"];
}
/** The rover's motor, its beeps on and off, its horn and the warning near the edge of its range. */
function roverSounds() {
  return Object.fromEntries(["rover_motor", "rover_start", "rover_stop", "rover_horn", "rover_range"].map((name) =>
    [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function roverLang() {
  return {
    "block.skyliners.rover_hatch": "Rover Bay Door",
    "block.skyliners.rover_panel": "Rover Bay Panel",
    "block.skyliners.rover_platform": "Rover Bay Platform",
    "entity.skyliners.rover": "Rover",
    "item.skyliners.rover_remote": "Rover Remote",
    "item.skyliners.rover_remote.paired": "Paired with its rover: use it to drive, sneak and use it to call the rover home",
    "item.skyliners.rover_remote.unpaired": "Not paired: use it on a rover, or on its bay's panel",
    "message.skyliners.rover.deploying": "Opening the bay: the rover is on its way down",
    "message.skyliners.rover.not_still": "The bay only opens while she stands still on her fins, her autopilot off",
    "message.skyliners.rover.already_out": "The rover is out already",
    "message.skyliners.rover.busy": "The winch is busy: wait for it",
    "message.skyliners.rover.coming_home": "The rover is coming home",
    "message.skyliners.rover.already_home": "The rover is in its bay",
    "message.skyliners.rover.has_remote": "You carry a remote for this rover already",
    "message.skyliners.rover.remote_given": "A remote paired with the rover: use it to drive",
    "message.skyliners.rover.paired": "Remote paired with this rover",
    "message.skyliners.rover.no_answer": "No answer from the rover: it is out of reach",
    "message.skyliners.rover.unpaired": "This remote is not paired: use it on a rover, or on its bay's panel",
    "message.skyliners.rover.countdown_holding": "Countdown holding: the rover is coming home first",
    "screen.skyliners.rover.camera": "ROVER CAM",
    "screen.skyliners.rover.hint": "%s %s %s %s: drive    %s: horn    %s: back to yourself",
    "subtitles.skyliners.rover_motor": "Rover whirs",
    "subtitles.skyliners.rover_start": "Rover beeps on",
    "subtitles.skyliners.rover_stop": "Rover beeps off",
    "subtitles.skyliners.rover_horn": "Rover honks",
    "subtitles.skyliners.rover_range": "Rover's signal fades",
  };
}

// The rocket's lab (see RocketLab). The glass of her tardigrade's tank (see HabitatGlassBlock): a pane a pixel thick on
// each side of its block that looks out of the tank, drawn along the north side and turned for the others, so a block
// at a corner has two; on its lowest row moss over the floor of its block and a steel kerb along the pane's foot, on
// its highest a soft light along the pane's top, inside. Its feeding hatch: a pane with a steel-framed opening in its
// middle and a steel flap over it, slid aside when open. The instruments, each modelled facing north: a microscope in
// white enamel on a charcoal foot, its eyepieces tilted up toward whoever uses it and its stage lit from under; a rack
// of five vials, their liquids glowing; a scan screen on a stand; and the vitals screen, a panel of the tank's wall,
// beating, asleep beating slowly under a moon, or, curled up, still. The tank's floor patches lie on the floor as a
// carpet does: the pool, rippling water over gravel; the pebbles, smooth stones in moss. All give themselves back, the
// instruments, the patches and the glass keeping their kind or row, as picking them does; the glass and the hatch are
// built into her, the rest fitted (see labFittings).
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
  write(`assets/${ID}/models/item/habitat_glass.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/habitat_glass` } });
  dropsKeeping("habitat_glass", "row", "middle");
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
  write(`assets/${ID}/models/item/habitat_hatch.json`, { parent: `${ID}:block/habitat_hatch` });
  dropsItself("habitat_hatch");
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
  write(`assets/${ID}/models/block/lab_vitals_asleep.json`, screen("vitals", "lab_screen_asleep"));
  write(`assets/${ID}/models/block/lab_vitals_away.json`, screen("vitals", "lab_screen_away"));
  // The vitals screen with stars streaming past while it is away, else curled up still, else asleep slow, else beating;
  // every other instrument as it is.
  const instrumentModel = (kind, dormant, asleep, away = false) => {
    if (kind !== "vitals") return `lab_${kind}`;
    if (away) return "lab_vitals_away";
    if (dormant) return "lab_vitals_dormant";
    return asleep ? "lab_vitals_asleep" : "lab_vitals";
  };
  const variants = {};
  for (const kind of ["microscope", "samples", "scan", "vitals", "specimens"]) {
    for (const [dormant, asleep] of [[false, false], [false, true], [true, false], [true, true]]) {
      for (const [facing, y] of Object.entries(yRot)) {
        variants[`asleep=${asleep},away=false,dormant=${dormant},facing=${facing},kind=${kind}`] = { model: `${ID}:block/${instrumentModel(kind, dormant, asleep)}`, y };
        variants[`asleep=${asleep},away=true,dormant=${dormant},facing=${facing},kind=${kind}`] = { model: `${ID}:block/${instrumentModel(kind, dormant, asleep, true)}`, y };
      }
    }
  }
  write(`assets/${ID}/blockstates/lab_instrument.json`, { variants });
  // In the Java enum's order (see keptLooks).
  keptLooks("lab_instrument", { kind: ["microscope", "samples", "scan", "vitals", "specimens"] },
    (kind) => `${ID}:block/${instrumentModel(kind, false, false)}`, ["microscope"]);
  dropsKeeping("lab_instrument", "kind", "microscope");
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
  keptLooks("habitat_floor", { kind: ["pool", "pebbles"] }, (kind) => `${ID}:block/habitat_${kind}`, ["pool"]);
  dropsKeeping("habitat_floor", "kind", "pool");
  cubes.habitat_floor = null;
}
foodLabModels();
// The lab's instruments and its tank's floor patches are fitted into the rocket rather than built into her: light like
// the rest, but no part of her frame, and breakable.
function labFittings() {
  return ["lab_instrument", "habitat_floor"];
}
// The rocket's dome (see RocketDome): the orrery's mount, a brass rose against her skin at the tip of her nose, a boss
// under it for the rod; the rest of the orrery, its rod, its sun and its planets, is drawn by its block entity from a
// sheet of its own (see OrreryRenderer), so in the hand it is a picture of its own. It gives itself back.
{
  const brass = "#brass";
  write(`assets/${ID}/models/block/orrery.json`, {
    parent: "minecraft:block/block",
    textures: { brass: `${ID}:block/orrery_brass`, particle: `${ID}:block/orrery_brass` },
    elements: [solid([5, 15, 5], [11, 16, 11], brass), solid([6.5, 14, 6.5], [9.5, 15, 9.5], brass)],
  });
  write(`assets/${ID}/blockstates/orrery.json`, { variants: { "": { model: `${ID}:block/orrery` } } });
  write(`assets/${ID}/models/item/orrery.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/orrery` } });
  dropsItself("orrery");
  cubes.orrery = null;
}
// The orrery is fitted into the rocket rather than built into her: light like the rest, but no part of her frame.
function domeFittings() {
  return ["orrery"];
}
function domeLang() {
  return { "block.skyliners.orrery": "Orrery" };
}
// Her tardigrade's purr, soft and low now and then, the bloop as it curls up into its tun, its snore asleep, its slurp
// sucking in what is thrown into its tank and its burp after, and its jump through the spore drive and its return.
function labSounds() {
  return Object.fromEntries(["tardigrade_mrrp", "tardigrade_bloop", "tardigrade_snore", "tardigrade_slurp", "tardigrade_burp", "tardigrade_jump",
    "tardigrade_return"].map((name) => [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
// Her greenhouse's air and the bee buzzing past, her pool's water and its bigger slaps (see RoomAmbience).
function roomSounds() {
  return Object.fromEntries(["greenhouse_air", "greenhouse_bee", "pool_water", "pool_slap"].map((name) => [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function roomLang() {
  return {
    "subtitles.skyliners.greenhouse_air": "Leaves rustle",
    "subtitles.skyliners.greenhouse_bee": "Bee buzzes by",
    "subtitles.skyliners.pool_water": "Pool water laps",
    "subtitles.skyliners.pool_slap": "Water slaps",
  };
}
// Her greenhouse (see RocketGreenhouse): its raised beds, a block of soil in a frame of spruce boards that runs round
// the outside of beds standing together (see GardenBedBlock), the soil dry or wet, a board on each side no bed joins
// and a post at each corner where two such sides meet; the grow lights hung over them, a slim dark bar with a strip of
// LEDs underneath glowing magenta (see GrowLightBlock); the sprinklers in them, a brass head on a copper riser in a box
// of boards over gravel, the head popped up on a taller riser while it sprays, its arm drawn turning with the jet (see
// SprinklerBlock and SprinklerRenderer); and
// the irrigation valve, a copper standpipe on its flange, a tap at its front over a sight glass, dark, or lit blue while
// the water is on, and on top a red hand wheel, turned an eighth while it is (see IrrigationValveBlock). All are fitted,
// so breakable, and each gives itself back.
{
  const planks = "minecraft:block/spruce_planks";
  const brass = `${ID}:block/zeppelin_brass`;
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  const soil = { from: [0, 0, 0], to: [16, 15, 16], faces: { up: { texture: "#top" }, down: { texture: "#dirt", cullface: "down" } } };
  for (const moist of [false, true]) {
    write(`assets/${ID}/models/block/garden_bed_soil${moist ? "_moist" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: { top: `minecraft:block/farmland${moist ? "_moist" : ""}`, dirt: "minecraft:block/dirt", particle: "minecraft:block/dirt" },
      elements: [soil],
    });
  }
  const post = (from, to) => ({ from, to, faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"]
    .map((face) => [face, { texture: face === "up" || face === "down" ? "#end" : "#post" }])) });
  const bedTextures = { board: planks, post: "minecraft:block/stripped_spruce_log", end: "minecraft:block/stripped_spruce_log_top", particle: planks };
  write(`assets/${ID}/models/block/garden_bed_board.json`, { parent: "minecraft:block/block", textures: bedTextures, elements: [solid([0, 0, 0], [16, 16, 2], "#board")] });
  // The corner post stands proud of the boards it meets by a twentieth of a pixel, so its sides and theirs never lie in
  // one plane, flickering through each other.
  const PROUD = 0.05;
  write(`assets/${ID}/models/block/garden_bed_post.json`, {
    parent: "minecraft:block/block", textures: bedTextures, elements: [post([-PROUD, 0, -PROUD], [3 + PROUD, 17, 3 + PROUD])],
  });
  const corners = [["north", "west", 0], ["north", "east", 90], ["south", "east", 180], ["south", "west", 270]];
  write(`assets/${ID}/blockstates/garden_bed.json`, {
    multipart: [
      { when: { moisture: "0|1|2|3|4|5|6" }, apply: { model: `${ID}:block/garden_bed_soil` } },
      { when: { moisture: "7" }, apply: { model: `${ID}:block/garden_bed_soil_moist` } },
      ...Object.entries(yRot).map(([side, y]) => ({ when: { [side]: "false" }, apply: { model: `${ID}:block/garden_bed_board`, y } })),
      ...corners.map(([one, other, y]) => ({ when: { [one]: "false", [other]: "false" }, apply: { model: `${ID}:block/garden_bed_post`, y } })),
    ],
  });
  write(`assets/${ID}/models/block/garden_bed_item.json`, {
    parent: "minecraft:block/block",
    textures: { ...bedTextures, top: "minecraft:block/farmland_moist", dirt: "minecraft:block/dirt" },
    elements: [
      soil,
      solid([0, 0, 0], [16, 16, 2], "#board"), solid([0, 0, 14], [16, 16, 16], "#board"),
      solid([0, 0, 2], [2, 16, 14], "#board"), solid([14, 0, 2], [16, 16, 14], "#board"),
      post([0, 0, 0], [3, 17, 3]), post([13, 0, 0], [16, 17, 3]), post([13, 0, 13], [16, 17, 16]), post([0, 0, 13], [3, 17, 16]),
    ],
  });
  write(`assets/${ID}/models/item/garden_bed.json`, { parent: `${ID}:block/garden_bed_item` });

  write(`assets/${ID}/models/block/grow_light.json`, {
    parent: "minecraft:block/block",
    textures: { housing: `${ID}:block/lab_dark`, leds: `${ID}:block/grow_light`, particle: `${ID}:block/lab_dark` },
    elements: [
      solid([0, 13.5, 5.5], [16, 16, 10.5], "#housing"),
      { from: [0, 13, 6], to: [16, 13.5, 10], shade: false, faces: Object.fromEntries(["north", "south", "east", "west", "down"]
        .map((face) => [face, { texture: "#leds", ...lit }])) },
    ],
  });
  write(`assets/${ID}/blockstates/grow_light.json`, {
    variants: { "axis=x": { model: `${ID}:block/grow_light` }, "axis=z": { model: `${ID}:block/grow_light`, y: 90 } },
  });
  write(`assets/${ID}/models/item/grow_light.json`, { parent: `${ID}:block/grow_light` });

  const box = [
    solid([0, 0, 0], [16, 16, 1], "#board"), solid([0, 0, 15], [16, 16, 16], "#board"),
    solid([0, 0, 1], [1, 16, 15], "#board"), solid([15, 0, 1], [16, 16, 15], "#board"),
    solid([1, 0, 1], [15, 1, 15], "#bed"),
  ];
  for (const spraying of [false, true]) {
    const riser = spraying ? 19 : 12;
    // No smooth lighting: a part above its block, in a block that is solid to the touch, draws black with it aboard
    // anything assembled.
    write(`assets/${ID}/models/block/sprinkler${spraying ? "_spraying" : ""}.json`, {
      parent: "minecraft:block/block",
      ambientocclusion: false,
      textures: { board: planks, bed: "minecraft:block/gravel", riser: "minecraft:block/cut_copper", head: brass, particle: planks },
      elements: [
        ...box,
        solid([7, 1, 7], [9, riser, 9], "#riser"),
        solid([6, riser, 6], [10, riser + 2, 10], "#head"),
      ],
    });
  }
  write(`assets/${ID}/blockstates/sprinkler.json`, {
    variants: Object.fromEntries([false, true].flatMap((spraying) => [false, true].map((waterlogged) =>
      [`spraying=${spraying},waterlogged=${waterlogged}`, { model: `${ID}:block/sprinkler${spraying ? "_spraying" : ""}` }]))),
  });
  write(`assets/${ID}/models/item/sprinkler.json`, { parent: `${ID}:block/sprinkler` });

  for (const open of [false, true]) {
    const turned = open ? { origin: [8, 12, 8], axis: "y", angle: 45 } : undefined;
    write(`assets/${ID}/models/block/irrigation_valve${open ? "_open" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: {
        pipe: "minecraft:block/cut_copper", flange: "minecraft:block/copper_block", wheel: "minecraft:block/red_concrete", brass,
        glass: open ? "minecraft:block/light_blue_concrete" : "minecraft:block/black_concrete", particle: "minecraft:block/cut_copper",
      },
      elements: [
        solid([5, 0, 5], [11, 1, 11], "#flange"),
        solid([6.5, 1, 6.5], [9.5, 12, 9.5], "#pipe"),
        solid([6, 2.5, 5.75], [10, 6.5, 6.5], "#brass"),
        { from: [6.75, 3.25, 5.5], to: [9.25, 5.75, 5.75], ...(open ? { shade: false } : {}), faces: { north: { texture: "#glass", ...(open ? lit : {}) } } },
        solid([7, 8, 2], [9, 10, 6.5], "#pipe"),
        solid([7, 6.5, 2], [9, 8, 4], "#pipe"),
        solid([3, 12, 3], [13, 13, 4], "#wheel", turned), solid([3, 12, 12], [13, 13, 13], "#wheel", turned),
        solid([3, 12, 4], [4, 13, 12], "#wheel", turned), solid([12, 12, 4], [13, 13, 12], "#wheel", turned),
        solid([4, 12, 7.5], [12, 13, 8.5], "#wheel", turned), solid([7.5, 12, 4], [8.5, 13, 12], "#wheel", turned),
        solid([6.5, 12, 6.5], [9.5, 14, 9.5], "#brass"),
      ],
    });
  }
  write(`assets/${ID}/blockstates/irrigation_valve.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((open) =>
      [`facing=${facing},open=${open}`, { model: `${ID}:block/irrigation_valve${open ? "_open" : ""}`, y }]))),
  });
  write(`assets/${ID}/models/item/irrigation_valve.json`, { parent: `${ID}:block/irrigation_valve` });

  for (const name of greenhouseFittings()) {
    dropsItself(name);
    cubes[name] = null;
  }
}
function greenhouseFittings() {
  return ["garden_bed", "grow_light", "sprinkler", "irrigation_valve"];
}
// The irrigation valve's wheel squeaking round and the water rushing up its pipe, and the sprinklers' hiss and tick.
function greenhouseSounds() {
  return Object.fromEntries(["irrigation_valve", "sprinkler_spray"].map((name) => [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
// Spruce planks round dirt; a copper riser under a gold nugget in planks; a copper pipe under a red wheel; and glowstone
// under an iron bar, magenta-tinted.
function greenhouseRecipes() {
  shaped("garden_bed", ["P P", "PDP"], { P: "minecraft:spruce_planks", D: "minecraft:dirt" }, 2);
  shaped("sprinkler", [" N ", "PCP"], { N: "minecraft:gold_nugget", C: "minecraft:copper_ingot", P: "minecraft:spruce_planks" }, 2);
  shaped("irrigation_valve", ["RCR", " C ", " C "], { R: "minecraft:red_dye", C: "minecraft:copper_ingot" }, 1);
  shaped("grow_light", ["III", "GMG"], { I: "minecraft:iron_ingot", G: "minecraft:glowstone_dust", M: "minecraft:magenta_dye" }, 4);
}
function greenhouseLang() {
  return {
    "block.skyliners.garden_bed": "Garden Bed",
    "block.skyliners.grow_light": "Grow Light",
    "block.skyliners.sprinkler": "Sprinkler",
    "block.skyliners.irrigation_valve": "Irrigation Valve",
    "subtitles.skyliners.irrigation_valve": "Irrigation valve squeaks",
    "subtitles.skyliners.sprinkler_spray": "Sprinklers spray",
  };
}
function labLang() {
  return {
    "block.skyliners.habitat_glass": "Tank Glass",
    "block.skyliners.habitat_hatch": "Feeding Hatch",
    "block.skyliners.lab_instrument": "Lab Instrument",
    "block.skyliners.habitat_floor": "Tank Floor",
    "entity.skyliners.tardigrade": "Tardigrade",
    "entity.skyliners.spookie": "Spookie",
    "block.skyliners.spookie_hammock": "Spookie's Hammock",
    "subtitles.skyliners.tardigrade_mrrp": "Tardigrade purrs",
    "subtitles.skyliners.tardigrade_bloop": "Tardigrade curls up",
    "subtitles.skyliners.tardigrade_snore": "Tardigrade snores",
    "subtitles.skyliners.tardigrade_slurp": "Tardigrade slurps",
    "subtitles.skyliners.tardigrade_burp": "Tardigrade burps",
    "subtitles.skyliners.tardigrade_jump": "Tardigrade jumps away",
    "subtitles.skyliners.tardigrade_return": "Tardigrade jumps back",
  };
}

// The lab's food lab (see LabPlan). Its bioreactor, each half drawn facing north at each stage of growth (see
// BioreactorBlock): a dark steel plinth with a panel on its front whose bars light one by one as the steak grows, a
// vessel of pink broth glowing and bubbling over it, a white scaffold rod up its middle with the steak on it, from a
// tuft at its foot to a mass filling the vessel, a steel pipe up its back, and over the broth a little clear headspace
// under a steel cap and hub, a pipe stub on top for the ceiling's pipe to meet. The hydroponic rack: a chrome frame of
// four shelves, open front and back, each a white trough of lettuce heads, basil or strawberry plants under a bar of
// violet grow light, lit. The algae column: a glass tube of glowing green algae on a steel foot, bubbles rising, under a
// chrome cap. The sample centrifuge: a white drum, its lid's round window over the rotor's coloured tubes, or a blur
// while it spins, and on its front a dial and a lamp, green or amber. The partition: glass through the middle of its
// block between steel mullions, its foot a kick plate, its middle row frosted at eye height, its top a head rail. The
// pipe: a steel joint at its middle and an arm out to each way it runs. The lab instrument's specimen jars: three jars
// on a tray, a tentacle, an eye and a sprouting potato, each in its own glowing liquid under a cork. And the steak, an
// item as drawn.
function foodLabModels() {
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  const face = (texture, extra = {}) => ({ texture, ...extra });
  const sidesOnly = (texture, extra = {}) => Object.fromEntries(["north", "south", "east", "west"].map((side) => [side, face(texture, extra)]));
  const litBox = (from, to, texture, faces = ["north", "south", "east", "west", "up", "down"]) => ({
    from, to, shade: false, faces: Object.fromEntries(faces.map((side) => [side, face(texture, lit)])),
  });
  const glassBox = (from, to, texture, faces) => ({ from, to, faces: Object.fromEntries(faces.map((side) => [side, face(texture)])) });
  const steel = { steel: `${ID}:block/lab_dark`, chrome: `${ID}:block/lab_chrome`, white: `${ID}:block/lab_white` };

  // The steak on its rod at each stage: none, a tuft at the rod's foot, half grown, and filling the vessel into the
  // upper half, rounded over its top.
  const lowerMeat = [[], [[6, 3, 6], [10, 6.5, 10]], [[5, 3, 5], [11, 12, 11]], [[4, 3, 4], [12, 16, 12]]];
  const upperMeat = [[], [], [], [[4, 0, 4], [12, 7, 12], [5, 7, 5], [11, 8.5, 11]]];
  const meat = (boxes) => {
    const parts = [];
    for (let i = 0; i < boxes.length; i += 2) parts.push(solid(boxes[i], boxes[i + 1], "#meat"));
    return parts;
  };
  for (let growth = 0; growth <= 3; growth++) {
    write(`assets/${ID}/models/block/bioreactor_lower_${growth}.json`, {
      parent: "minecraft:block/block", render_type: "minecraft:translucent",
      textures: { ...steel, broth: `${ID}:block/bioreactor_broth`, meat: `${ID}:block/bioreactor_meat`, panel: `${ID}:block/bioreactor_panel_${growth}`,
        particle: `${ID}:block/lab_dark` },
      elements: [
        solid([1, 0, 1], [15, 3, 15], "#steel"),
        { from: [5, 0.5, 0.5], to: [11, 2.5, 1], shade: false, faces: { north: face("#panel", { uv: [0, 0, 12, 4], ...lit }) } },
        solid([7.5, 3, 7.5], [8.5, 16, 8.5], "#white"),
        ...meat(lowerMeat[growth]),
        solid([7, 3, 14.25], [9, 16, 15.75], "#chrome"),
        { from: [2, 3, 2], to: [14, 16, 14], shade: false, faces: sidesOnly("#broth", lit) },
      ],
    });
    write(`assets/${ID}/models/block/bioreactor_upper_${growth}.json`, {
      parent: "minecraft:block/block", render_type: "minecraft:translucent",
      textures: { ...steel, broth: `${ID}:block/bioreactor_broth`, glass: `${ID}:block/bioreactor_glass`, meat: `${ID}:block/bioreactor_meat`,
        particle: `${ID}:block/lab_chrome` },
      elements: [
        solid([7.5, 0, 7.5], [8.5, 11, 8.5], "#white"),
        ...meat(upperMeat[growth]),
        solid([7, 0, 14.25], [9, 11, 15.75], "#chrome"),
        solid([1, 11, 1], [15, 13.5, 15], "#chrome"),
        solid([5, 13.5, 5], [11, 15, 11], "#steel"),
        solid([6, 15, 6], [10, 16, 10], "#chrome"),
        { from: [2, 0, 2], to: [14, 9, 14], shade: false, faces: { ...sidesOnly("#broth", lit), up: face("#broth", lit) } },
        glassBox([2, 9, 2], [14, 11, 14], "#glass", ["north", "south", "east", "west"]),
      ],
    });
  }
  write(`assets/${ID}/blockstates/bioreactor.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [0, 1, 2, 3].flatMap((growth) => ["lower", "upper"].map((half) =>
      [`facing=${facing},growth=${growth},half=${half}`, { model: `${ID}:block/bioreactor_${half}_${growth}`, y }])))),
  });
  tallItem("bioreactor", "bioreactor_lower_3", "bioreactor_upper_3");
  dropsFrom("bioreactor", { half: "lower" });
  cubes.bioreactor = null;

  // A shelf of the rack: its plate, its trough, and what grows in it under a bar of grow light so far over it.
  const cross = (x, y, z, width, height, texture) => [45, -45].map((angle) => ({
    from: [x - width / 2, y, z], to: [x + width / 2, y + height, z], shade: false,
    rotation: { angle, axis: "y", origin: [x, y, z] },
    faces: { north: face(texture, { uv: [0, 0, 16, 16] }), south: face(texture, { uv: [0, 0, 16, 16] }) },
  }));
  const lettuces = (y) => [[2.5, 6], [6.25, 9.75], [10, 13.5]].map(([x0, x1]) => solid([x0, y, 5.5], [x1, y + 3, 10.5], "#lettuce"));
  const plants = (y, texture) => [5, 11].flatMap((x) => cross(x, y, 8, 5, 4, texture));
  const shelf = (y, crop, light) => [
    solid([1, y, 3], [15, y + 1, 13], "#frame"),
    solid([2, y + 1, 4], [14, y + 2.5, 12], "#trough"),
    ...(crop === "lettuce" ? lettuces(y + 2.5) : plants(y + 2.5, `#${crop}`)),
    litBox([2, light, 7], [14, light + 0.5, 9], "#light", ["down", "north", "south"]),
  ];
  const posts = (top) => [[1, 3], [14, 3], [1, 12], [14, 12]].map(([x, z]) => solid([x, 0, z], [x + 1, top, z + 1], "#frame"));
  const rackTextures = { frame: `${ID}:block/lab_chrome`, trough: `${ID}:block/lab_white`, lettuce: `${ID}:block/hydroponic_lettuce`,
    herbs: `${ID}:block/hydroponic_herbs`, berries: `${ID}:block/hydroponic_berries`, light: `${ID}:block/hydroponic_light`, particle: `${ID}:block/lab_chrome` };
  write(`assets/${ID}/models/block/hydroponic_rack_lower.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:cutout", textures: rackTextures,
    elements: [...posts(16), ...shelf(0, "lettuce", 7), ...shelf(7.5, "herbs", 15)],
  });
  write(`assets/${ID}/models/block/hydroponic_rack_upper.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:cutout", textures: rackTextures,
    elements: [...posts(15), ...shelf(0, "lettuce", 7), ...shelf(7.5, "berries", 14.5), solid([1, 15, 3], [15, 16, 13], "#frame")],
  });
  const machineStates = (name) => write(`assets/${ID}/blockstates/${name}.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["lower", "upper"].map((half) =>
      [`facing=${facing},half=${half}`, { model: `${ID}:block/${name}_${half}`, y }]))),
  });
  machineStates("hydroponic_rack");
  tallItem("hydroponic_rack", "hydroponic_rack_lower", "hydroponic_rack_upper");
  dropsFrom("hydroponic_rack", { half: "lower" });
  cubes.hydroponic_rack = null;

  const columnTextures = { ...steel, algae: `${ID}:block/algae_tube`, particle: `${ID}:block/algae_tube` };
  write(`assets/${ID}/models/block/algae_column_lower.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:translucent", textures: columnTextures,
    elements: [
      solid([3, 0, 3], [13, 2, 13], "#steel"),
      solid([3.5, 2, 3.5], [12.5, 3, 12.5], "#chrome"),
      litBox([5.5, 0.5, 2.5], [10.5, 1.5, 3], "#algae", ["north"]),
      { from: [4, 3, 4], to: [12, 16, 12], shade: false, faces: sidesOnly("#algae", lit) },
    ],
  });
  write(`assets/${ID}/models/block/algae_column_upper.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:translucent", textures: columnTextures,
    elements: [
      solid([3, 12, 3], [13, 14, 13], "#chrome"),
      solid([6, 14, 6], [10, 16, 10], "#chrome"),
      { from: [4, 0, 4], to: [12, 12, 12], shade: false, faces: sidesOnly("#algae", lit) },
    ],
  });
  machineStates("algae_column");
  tallItem("algae_column", "algae_column_lower", "algae_column_upper");
  dropsFrom("algae_column", { half: "lower" });
  cubes.algae_column = null;

  for (const spinning of [false, true]) {
    const name = `sample_centrifuge${spinning ? "_spinning" : ""}`;
    write(`assets/${ID}/models/block/${name}.json`, {
      parent: "minecraft:block/block",
      textures: { body: `${ID}:block/lab_white`, chrome: `${ID}:block/lab_chrome`, lid: `${ID}:block/centrifuge_lid${spinning ? "_spinning" : ""}`,
        panel: `${ID}:block/centrifuge_panel${spinning ? "_spinning" : ""}`, particle: `${ID}:block/lab_white` },
      elements: [
        solid([2, 0, 2], [14, 6, 14], "#body"),
        { from: [2, 6, 2], to: [14, 7.5, 14], faces: { ...sidesOnly("#body"), up: face("#lid"), down: face("#body") } },
        litBox([4, 1, 1.5], [12, 5, 2], "#panel", ["north"]),
        solid([4, 6, 14], [12, 7, 14.5], "#chrome"),
      ],
    });
  }
  write(`assets/${ID}/blockstates/sample_centrifuge.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((spinning) =>
      [`facing=${facing},spinning=${spinning}`, { model: `${ID}:block/sample_centrifuge${spinning ? "_spinning" : ""}`, y }]))),
  });
  write(`assets/${ID}/models/item/sample_centrifuge.json`, { parent: `${ID}:block/sample_centrifuge` });
  dropsItself("sample_centrifuge");
  cubes.sample_centrifuge = null;

  const pane = (from, to, texture) => ({ from, to, faces: { north: face(texture), south: face(texture) } });
  const mullions = [solid([0, 0, 7], [1, 16, 9], "#steel"), solid([15, 0, 7], [16, 16, 9], "#steel")];
  const partition = {
    bottom: [solid([0, 0, 7], [16, 3, 9], "#steel"), pane([1, 3, 7.75], [15, 16, 8.25], "#glass")],
    middle: [pane([1, 0, 7.75], [15, 16, 8.25], "#frosted")],
    top: [pane([1, 0, 7.75], [15, 15, 8.25], "#glass"), solid([0, 15, 7], [16, 16, 9], "#steel")],
  };
  for (const [row, parts] of Object.entries(partition)) {
    write(`assets/${ID}/models/block/lab_partition_${row}.json`, {
      parent: "minecraft:block/block", render_type: "minecraft:translucent", ambientocclusion: false,
      textures: { steel: `${ID}:block/lab_chrome`, glass: `${ID}:block/lab_partition_glass`, frosted: `${ID}:block/lab_partition_frosted`,
        particle: `${ID}:block/lab_chrome` },
      elements: [...mullions, ...parts],
    });
  }
  write(`assets/${ID}/blockstates/lab_partition.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => Object.keys(partition).map((row) =>
      [`facing=${facing},row=${row}`, { model: `${ID}:block/lab_partition_${row}`, y }]))),
  });
  // In the Java enum's order (see keptLooks).
  keptLooks("lab_partition", { row: ["bottom", "middle", "top"] }, (row) => `${ID}:block/lab_partition_${row}`, ["middle"]);
  dropsKeeping("lab_partition", "row", "middle");
  cubes.lab_partition = null;

  const pipeTextures = { pipe: `${ID}:block/lab_pipe`, joint: `${ID}:block/lab_pipe_joint`, particle: `${ID}:block/lab_pipe` };
  write(`assets/${ID}/models/block/lab_pipe_joint.json`, boxes(pipeTextures, [[[5.5, 5.5, 5.5], [10.5, 10.5, 10.5], "#joint"]]));
  write(`assets/${ID}/models/block/lab_pipe_arm.json`, boxes(pipeTextures, [[[6, 6, 0], [10, 10, 5.5], "#pipe"]]));
  const arms = { north: {}, east: { y: 90 }, south: { y: 180 }, west: { y: 270 }, up: { x: 270 }, down: { x: 90 } };
  write(`assets/${ID}/blockstates/lab_pipe.json`, {
    multipart: [
      { apply: { model: `${ID}:block/lab_pipe_joint` } },
      ...Object.entries(arms).map(([way, turn]) => ({ when: { [way]: "true" }, apply: { model: `${ID}:block/lab_pipe_arm`, ...turn } })),
    ],
  });
  write(`assets/${ID}/models/item/lab_pipe.json`, boxes(pipeTextures, [[[6, 6, 0], [10, 10, 16], "#pipe"], [[5.5, 5.5, 5.5], [10.5, 10.5, 10.5], "#joint"]]));
  dropsItself("lab_pipe");
  cubes.lab_pipe = null;

  // The specimen jars: each its liquid, its cork and what floats in it.
  const jar = (from, to, liquid, contents) => [
    { from, to, shade: false, faces: Object.fromEntries(["north", "south", "east", "west", "up"].map((side) => [side, face(liquid, lit)])) },
    solid([from[0] - 0.25, to[1], from[2] - 0.25], [to[0] + 0.25, to[1] + 1, to[2] + 0.25], "#cork"),
    ...contents,
  ];
  write(`assets/${ID}/models/block/lab_specimens.json`, {
    parent: "minecraft:block/block", render_type: "minecraft:translucent",
    textures: { tray: `${ID}:block/lab_dark`, cork: `${ID}:block/lab_cork`, yellow: `${ID}:block/lab_jar_yellow`, green: `${ID}:block/lab_jar_green`,
      blue: `${ID}:block/lab_jar_blue`, tentacle: `${ID}:block/lab_tentacle`, eye: `${ID}:block/lab_eye`, potato: `${ID}:block/lab_potato`,
      sprout: `${ID}:block/lab_sprout`, particle: `${ID}:block/lab_dark` },
    elements: [
      solid([2, 0, 4], [14, 1, 12], "#tray"),
      ...jar([3, 1, 6], [6.5, 8, 9.5], "#yellow", [
        solid([4, 1, 7.25], [5.25, 4, 8.5], "#tentacle"), solid([4.5, 4, 7.5], [5.5, 6, 8.25], "#tentacle"), solid([5, 5.5, 7.5], [5.75, 7, 8.25], "#tentacle"),
      ]),
      ...jar([7, 1, 5.5], [10, 6, 8.5], "#green", [{
        from: [7.75, 2.5, 6.25], to: [9.25, 4, 7.75],
        faces: { ...Object.fromEntries(["south", "east", "west", "up", "down"].map((side) => [side, face("#eye", { uv: [0, 0, 3, 3] })])), north: face("#eye", { uv: [0, 0, 16, 16] }) },
      }]),
      ...jar([10.5, 1, 7], [13.5, 5, 10], "#blue", [solid([11.25, 1.5, 7.75], [12.75, 3, 9.25], "#potato"), solid([11.75, 3, 8.25], [12.25, 4.5, 8.75], "#sprout")]),
    ],
  });

  write(`assets/${ID}/models/item/lab_grown_steak.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/lab_grown_steak` } });
}
// The food lab's machines and fittings are fitted into the rocket rather than built into her: light like the rest, but
// no part of her frame, and breakable.
function foodLabFittings() {
  return ["bioreactor", "hydroponic_rack", "algae_column", "sample_centrifuge", "lab_partition", "lab_pipe"];
}
// The bioreactor's cap hissing open on a harvest, and the centrifuge's whir and the ding as it stops.
function foodLabSounds() {
  return Object.fromEntries(["bioreactor_harvest", "centrifuge_spin", "centrifuge_ding"].map((name) =>
    [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function foodLabLang() {
  return {
    "block.skyliners.bioreactor": "Bioreactor",
    "block.skyliners.hydroponic_rack": "Hydroponic Rack",
    "block.skyliners.algae_column": "Algae Column",
    "block.skyliners.sample_centrifuge": "Sample Centrifuge",
    "block.skyliners.lab_partition": "Lab Partition",
    "block.skyliners.lab_pipe": "Lab Pipe",
    "item.skyliners.lab_grown_steak": "Lab-Grown Steak",
    "screen.skyliners.microscope": "Microscope",
    "subtitles.skyliners.bioreactor_harvest": "Bioreactor hisses open",
    "subtitles.skyliners.centrifuge_spin": "Centrifuge whirs",
    "subtitles.skyliners.centrifuge_ding": "Centrifuge dings",
  };
}

// The rocket's rooms' furniture (see RocketRoom), each drawn facing north. The shelf unit (see ShelfUnitBlock): a steel
// plinth, birch end panels and two birch shelves under a birch top, open front and back, and what each room keeps on
// it: the entry's boots in pairs under a pair of white helmets, gold visors to the front; the bunk room's books, tall
// and short, one leaning, under a row more and a potted fern; the mess's jars of tomatoes, honey, pickles and plum jam
// with white labels; the pool's fat rolled towels and a pile of folded ones; and the cargo bay's red toolbox and yellow
// case under a coil of cable and spray cans. The pallet rack (see PalletRackBlock): blue uprights at its corners,
// orange beams front and back at its foot under a pine pallet, and its load on the pallet: taped cardboard boxes, a
// blue drum and a yellow one, two wooden crates one on the other, or nothing. Both are placed only by the blueprint, so
// they have no items and give nothing back (see rocketRoomFittings).
function rocketRoomFurniture() {
  const block = (name) => `${ID}:block/${name}`;
  // A box of a model, every face showing the texture given: its front (north) face the patch of it given, if any, and its
  // other faces the patch given for them, or else the front's; turned about an axis if a rotation is given.
  const part = (from, to, texture, front, sides = front, rotation = undefined) => ({
    from, to, ...(rotation ? { rotation } : {}),
    faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => {
      const uv = face === "north" ? front : sides;
      return [face, { texture, ...(uv ? { uv } : {}) }];
    })),
  });
  const shelf = [
    part([0.5, 0, 3.5], [15.5, 1, 12.5], "#steel"),
    part([0, 0, 3], [1, 16, 13], "#wood"),
    part([15, 0, 3], [16, 16, 13], "#wood"),
    part([1, 1, 3.5], [15, 2, 12.5], "#wood"),
    part([1, 8, 3.5], [15, 9, 12.5], "#wood"),
    part([1, 15, 3], [15, 16, 13], "#wood"),
  ];
  // Something kept on a shelf: across from and to, up from and how tall, deep from and to, and the patches of the kind's
  // texture its front and its other faces show (see rocketRoomTextures); the lower shelf's top is at 2 and the upper's
  // at 9.
  const goods = (x0, x1, y0, height, z0, z1, front, sides = front, rotation = undefined) =>
    part([x0, y0, z0], [x1, y0 + height, z1], "#goods", front, sides, rotation);
  const LOW = 2;
  const HIGH = 9;
  // The boots' shafts over their feet, a charcoal pair and a white pair; the helmets' fronts, their sides plain white.
  const boot = (x, z, shaft, foot) => [goods(x, x + 2.5, LOW, 1.5, z, z + 5.5, foot), goods(x, x + 2.5, LOW + 1.5, 3.5, z + 3, z + 5.5, shaft)];
  const helmet = [8, 0, 13.5, 5];
  const helmetSide = [14, 0, 16, 2];
  // A fat rolled towel lying from front to back, its spiral end to the front, the rest of it its colour's terry with a
  // stripe; and a folded one, its fold to the front.
  const ROLL = 4.5;
  const roll = (x, y, z0, z1, colour) => goods(x, x + ROLL, y, ROLL, z0, z1, [5 * colour, 0, 5 * colour + 5, 5], [5 * colour, 10, 5 * colour + 5, 15]);
  const folded = (y, z0, z1, colour) => goods(10, 14.5, y, 1.5, z0, z1, [5 * colour, 6, 5 * colour + 5, 8]);
  const kinds = {
    boots: { texture: "shelf_boots", parts: [
      ...boot(2, 4, [0, 9, 2.5, 12.5], [0, 13, 2.5, 14.5]), ...boot(5, 4, [0, 9, 2.5, 12.5], [0, 13, 2.5, 14.5]),
      ...boot(9, 4.5, [3, 9, 5.5, 12.5], [3, 13, 5.5, 14.5]), ...boot(12, 4.5, [3, 9, 5.5, 12.5], [3, 13, 5.5, 14.5]),
      goods(1.5, 7, HIGH, 5, 5, 10.5, helmet, helmetSide),
      goods(9, 14.5, HIGH, 5, 5.5, 11, helmet, helmetSide),
    ] },
    books: { texture: "shelf_books", parts: [
      goods(1.5, 3, LOW, 5.5, 4.5, 11, [0, 0, 1.5, 5.5]), goods(3, 4.5, LOW, 5, 4.5, 11, [2, 0, 3.5, 5]),
      goods(4.5, 5.5, LOW, 6, 5, 11, [4, 0, 5, 6]), goods(5.5, 7.5, LOW, 4.5, 4.5, 11.5, [5, 0, 7, 4.5]),
      goods(7.5, 8.5, LOW, 5.5, 5, 11, [8, 0, 9, 5.5]), goods(8.5, 10, LOW, 5, 4.5, 11, [9, 0, 10.5, 5]),
      goods(10.5, 12, LOW, 5.5, 4.5, 11, [14, 0, 15.5, 5.5], undefined, { angle: -22.5, axis: "z", origin: [10.5, LOW, 8] }),
      goods(12.5, 14.5, LOW, 3, 4, 11.5, [11, 0, 13, 3]),
      goods(1.5, 3, HIGH, 5, 5, 11, [3, 8, 4.5, 13]), goods(3, 4, HIGH, 5.5, 5, 11, [6, 8, 7, 13.5]),
      goods(4, 5.5, HIGH, 4.5, 5, 11, [12, 8, 13.5, 12.5]),
      goods(10, 13, HIGH, 2.5, 6, 10, [0, 14, 3, 16]), goods(10.5, 12.5, HIGH + 2.5, 3.5, 6.5, 9.5, [4, 12.5, 6, 16]),
    ] },
    jars: { texture: "shelf_jars", parts: [
      goods(1.5, 4.5, LOW, 5, 5, 8, [0, 0, 3, 5]), goods(5, 8, LOW, 4.5, 6, 9, [4, 0, 7, 4.5]),
      goods(8.5, 11.5, LOW, 5, 5, 8, [8, 0, 11, 5]), goods(12, 14.5, LOW, 4, 6.5, 9, [12, 0, 14.5, 4]),
      goods(2, 5, HIGH, 4.5, 6, 9, [8, 0, 11, 4.5]), goods(6, 9, HIGH, 5, 5, 8, [12, 0, 15, 5]),
      goods(10, 12.5, HIGH, 4, 6, 8.5, [4, 0, 6.5, 4]), goods(12.5, 14.5, HIGH, 3, 7, 9, [0, 8, 2, 11]),
    ] },
    towels: { texture: "shelf_towels", parts: [
      roll(1.5, LOW, 4, 12, 0), roll(6, LOW, 4, 12, 1), roll(10.5, LOW, 4, 12, 2),
      roll(1.5, HIGH, 4, 12, 2), roll(6, HIGH, 4.5, 11.5, 0),
      folded(HIGH, 5, 11, 1), folded(HIGH + 1.5, 5.5, 10.5, 0), folded(HIGH + 3, 5, 11, 2),
    ] },
    tools: { texture: "shelf_tools", parts: [
      goods(1.5, 8, LOW, 4, 4.5, 11, [0, 2, 6.5, 6]), goods(3.5, 6, LOW + 4, 0.75, 7, 8.5, [0, 0, 2.5, 0.75]),
      goods(9, 14.5, LOW, 5, 4, 11.5, [8, 1, 13.5, 6]),
      goods(1.5, 6.5, HIGH, 2.5, 5, 10, [0, 8, 5, 10.5]), goods(2.5, 5.5, HIGH + 2.5, 1.5, 6, 9, [0, 11, 3, 12.5]),
      goods(8, 9.5, HIGH, 4, 6, 7.5, [8, 8, 9.5, 12]), goods(10, 11.5, HIGH, 4.5, 6.5, 8, [10, 8, 11.5, 12.5]),
      goods(12.5, 14, HIGH, 3.5, 6, 7.5, [12, 8, 13.5, 11.5]),
    ] },
  };
  const textures = { wood: block("shelf_wood"), steel: block("shelf_steel"), particle: block("shelf_wood") };
  for (const [kind, { texture, parts }] of Object.entries(kinds)) {
    write(`assets/${ID}/models/block/shelf_unit_${kind}.json`, {
      parent: "minecraft:block/block", textures: { ...textures, goods: block(texture) }, elements: [...shelf, ...parts],
    });
  }
  write(`assets/${ID}/blockstates/shelf_unit.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => Object.keys(kinds).map((kind) =>
      [`facing=${facing},kind=${kind}`, { model: `${ID}:block/shelf_unit_${kind}`, y }]))),
  });
  // In the Java enum's order (see keptLooks).
  keptLooks("shelf_unit", { kind: ["boots", "books", "jars", "towels", "tools"] }, (kind) => `${ID}:block/shelf_unit_${kind}`, ["books"]);
  dropsKeeping("shelf_unit", "kind", "books");
  cubes.shelf_unit = null;

  const rack = [
    ...[[0, 1], [14.5, 1], [0, 13.5], [14.5, 13.5]].map(([x, z]) => part([x, 0, z], [x + 1.5, 16, z + 1.5], "#upright")),
    part([1.5, 0.5, 1], [14.5, 2.5, 2.5], "#beam"),
    part([1.5, 0.5, 13.5], [14.5, 2.5, 15], "#beam"),
    part([1.5, 2.5, 2], [14.5, 4, 14], "#pallet"),
  ];
  const loads = {
    boxes: { textures: { load: block("rack_cardboard") }, parts: [
      part([2, 4, 3], [8, 10, 9], "#load"), part([8.5, 4, 3], [14, 9, 13], "#load"), part([2, 4, 9.5], [7.5, 9, 13], "#load"),
      part([3, 10, 4], [8, 14, 8.5], "#load"),
    ] },
    drums: { textures: { load: block("rack_drum_blue"), second: block("rack_drum_yellow") }, parts: [
      part([2.5, 4, 4.5], [7.5, 14, 9.5], "#load"), part([8.5, 4, 6], [13.5, 14, 11], "#second"),
    ] },
    crates: { textures: { load: block("rack_crate") }, parts: [part([2, 4, 3], [14, 10, 13], "#load"), part([4, 10, 5], [10.5, 14.5, 11], "#load")] },
    empty: { textures: {}, parts: [] },
  };
  for (const [load, { textures: own, parts }] of Object.entries(loads)) {
    write(`assets/${ID}/models/block/pallet_rack_${load}.json`, {
      parent: "minecraft:block/block",
      textures: { upright: block("rack_upright"), beam: block("rack_beam"), pallet: block("rack_pallet"), particle: block("rack_upright"), ...own },
      elements: [...rack, ...parts],
    });
  }
  write(`assets/${ID}/blockstates/pallet_rack.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => Object.keys(loads).map((load) =>
      [`facing=${facing},load=${load}`, { model: `${ID}:block/pallet_rack_${load}`, y }]))),
  });
  keptLooks("pallet_rack", { load: ["boxes", "drums", "crates", "empty"] }, (load) => `${ID}:block/pallet_rack_${load}`, ["boxes"]);
  dropsKeeping("pallet_rack", "load", "boxes");
  cubes.pallet_rack = null;
}
rocketRoomFurniture();
// The rocket's rooms' furniture is fitted into her rather than built into her: light like the rest, but no part of
// her frame, and breakable.
function rocketRoomFittings() {
  return ["shelf_unit", "pallet_rack"];
}
function rocketRoomLang() {
  return {
    "block.skyliners.shelf_unit": "Shelf Unit",
    "block.skyliners.pallet_rack": "Pallet Rack",
  };
}

// The rocket's airlock panel (see AirlockPanelBlock), drawn facing north: a steel foot and post under its head, the
// head tilted back so its screen looks up at whoever stands at it, the screen glowing whatever the light around, its
// picture the middle band of its texture, twice as wide as it is tall; one model for each way the airlock stands, its
// screen's picture showing it (see airlockPanelTextures).
function airlockPanel() {
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  const tilt = { angle: 22.5, axis: "x", origin: [8, 9, 10] };
  const screen = { from: [3, 9.5, 5.9], to: [13, 14.5, 6], rotation: tilt, shade: false, faces: { north: { texture: "#screen", uv: [0, 4, 16, 12], ...lit } } };
  for (const cycle of ["sealed", "cycling", "clear"]) {
    write(`assets/${ID}/models/block/airlock_panel_${cycle}.json`, {
      parent: "minecraft:block/block",
      textures: { casing: `${ID}:block/airlock_casing`, post: `${ID}:block/airlock_post`, screen: `${ID}:block/airlock_screen_${cycle}`,
        particle: `${ID}:block/airlock_casing` },
      elements: [solid([4, 0, 4], [12, 1, 12], "#casing"), solid([6.5, 1, 7], [9.5, 10, 10], "#post"), solid([2.5, 9, 6], [13.5, 15, 10], "#casing", tilt),
        screen],
    });
  }
  write(`assets/${ID}/blockstates/airlock_panel.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["sealed", "cycling", "clear"].map((cycle) =>
      [`cycle=${cycle},facing=${facing}`, { model: `${ID}:block/airlock_panel_${cycle}`, y }]))),
  });
  write(`assets/${ID}/models/item/airlock_panel.json`, { parent: `${ID}:block/airlock_panel_sealed` });
  dropsItself("airlock_panel");
  cubes.airlock_panel = null;
}
airlockPanel();
// The panel is fitted into the rocket rather than built into her: light like the rest, but no part of her frame.
function airlockPanelFittings() {
  return ["airlock_panel"];
}
// Its cycle, the latches' clunk and the air's hiss, and the chime as it clears (see tools/generate_sounds.mjs).
function airlockPanelSounds() {
  return Object.fromEntries(["airlock_cycle", "airlock_clear"].map((name) => [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function airlockPanelLang() {
  return {
    "block.skyliners.airlock_panel": "Airlock Panel",
    "subtitles.skyliners.airlock_cycle": "Airlock cycles",
    "subtitles.skyliners.airlock_clear": "Airlock chimes clear",
  };
}
// The blocks of the rocket's front doorway where her sliding airlock doors close (see AirlockDoorBlock): drawn whole
// by her stairs button's renderer (see RocketStairsRenderer), so the model only gives the particles, in her steel.
// Placed only by her blueprint, so no item and no loot; part of her hull, in its tags (see cubes).
write(`assets/${ID}/blockstates/airlock_door.json`, { variants: { "": { model: `${ID}:block/airlock_door` } } });
write(`assets/${ID}/models/block/airlock_door.json`, { textures: { particle: `${ID}:block/airlock_casing` } });
cubes.airlock_door = null;
function airlockDoorLang() {
  return {
    "block.skyliners.airlock_door": "Airlock Door",
  };
}

// The cargo bay's parcel scanner (see ParcelScannerBlock), drawn facing north: a graphite cabinet as high as a table,
// its screen on its front, a conveyor belt for its top and a hazard-striped gantry over the belt, its posts just outside
// the block either side so a crate on the belt stands between them; the screen and the lamps glow whatever the light
// around: one under the gantry's bar and a strip down each post, front, back and outside, which still shows with a crate
// standing on the belt and hiding the bar's. One model for each step of a scan, its screen's picture and its lamps'
// colour showing it; while it scans, a curtain of red laser light hangs from the bar to the belt.
function parcelScanner() {
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  const block = (name) => `${ID}:block/${name}`;
  const glowing = (faces) => Object.fromEntries(faces.map((face) => [face, { texture: "#lamp", ...lit }]));
  // A post's lamp strips, either side of it from across to across: on its front and its back, and on its outside.
  const strips = (x0, x1, outside, outsideX) => [
    { from: [x0, 15, 6.9], to: [x1, 27, 7], shade: false, faces: glowing(["north"]) },
    { from: [x0, 15, 9], to: [x1, 27, 9.1], shade: false, faces: glowing(["south"]) },
    { from: [outsideX, 15, 7.5], to: [outsideX + 0.1, 27, 8.5], shade: false, faces: glowing([outside]) },
  ];
  const belt = solid([0, 13, 0], [16, 16, 16], "#roller");
  belt.faces.up = { texture: "#belt" };
  for (const scan of ["ready", "scanning", "packed", "empty"]) {
    const elements = [
      solid([0, 0, 1], [16, 13, 15], "#casing"),
      { from: [4, 3, 0.9], to: [12, 11, 1], shade: false, faces: { north: { texture: "#screen", ...lit } } },
      belt,
      solid([-1.5, 13, 7], [0, 30, 9], "#gantry"),
      solid([16, 13, 7], [17.5, 30, 9], "#gantry"),
      solid([-1.5, 28, 6.5], [17.5, 30, 9.5], "#gantry"),
      { from: [0, 27.75, 7], to: [16, 28, 9], shade: false, faces: glowing(["down", "north", "south"]) },
      ...strips(-1.25, -0.25, "west", -1.6),
      ...strips(16.25, 17.25, "east", 17.5),
    ];
    if (scan === "scanning") {
      elements.push({ from: [0, 16, 7.9], to: [16, 27.75, 8.1], shade: false, faces: { north: { texture: "#laser", ...lit }, south: { texture: "#laser", ...lit } } });
    }
    write(`assets/${ID}/models/block/parcel_scanner_${scan}.json`, {
      parent: "minecraft:block/block", ...(scan === "scanning" ? { render_type: "minecraft:translucent" } : {}),
      textures: { casing: block("scanner_casing"), screen: block(`scanner_screen_${scan}`), belt: block("scanner_belt"), roller: block("scanner_roller"),
        gantry: block("scanner_gantry"), lamp: block(`scanner_lamp_${scan}`), laser: block("scanner_laser"), particle: block("scanner_casing") },
      elements,
    });
  }
  write(`assets/${ID}/blockstates/parcel_scanner.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["ready", "scanning", "packed", "empty"].map((scan) =>
      [`facing=${facing},scan=${scan}`, { model: `${ID}:block/parcel_scanner_${scan}`, y }]))),
  });
  // Its gantry stands over the top of its block, so it is shown as something two blocks tall is.
  write(`assets/${ID}/models/item/parcel_scanner.json`, { parent: `${ID}:block/parcel_scanner_ready`, display: TALL_ITEM_DISPLAY });
  dropsItself("parcel_scanner");
  cubes.parcel_scanner = null;
}
parcelScanner();
// The scanner is fitted into the rocket rather than built into her: light like the rest, but no part of her frame.
function parcelScannerFittings() {
  return ["parcel_scanner"];
}
// Its whirr as it scans, its bright double beep for a packed crate and its low buzz for anything else.
function parcelScannerSounds() {
  return Object.fromEntries(["scanner_sweep", "scanner_pass", "scanner_fail"].map((name) => [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
// The galley's fridge (see GalleyFridgeBlock), drawn facing north: a stainless body as high as a worktop, its door on
// its front with a steel handle and a blue light by it. Open, the door has swung out a quarter turn on its hinge at the
// left as seen from the front, showing the bins and bottles on its inside, and the body is a lit cavity: white walls,
// two glass shelves and what is kept on them, each thing a patch of the food sheet (see galleyFridgeTextures).
function galleyFridge() {
  const block = (name) => `${ID}:block/${name}`;
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  const textures = { steel: block("fridge_steel"), door: block("fridge_door"), inside: block("fridge_inside"), food: block("fridge_food"),
    bins: block("fridge_door_inside"), particle: block("fridge_steel") };
  // Something kept in the fridge, its box and the patch of the food sheet every face of it shows, lit with the inside.
  const food = (from, to, u, v) => ({ from, to, shade: false,
    faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, { texture: "#food", uv: [u, v, u + 4, v + 4], ...lit }])) });
  const glowing = (from, to, texture, faces) => ({ from, to, shade: false,
    faces: Object.fromEntries(faces.map((face) => [face, { texture, ...lit }])) });
  // A box of steel whose face toward the inside, the way given, is the lit inside's.
  const wall = (from, to, inward) => {
    const box = solid(from, to, "#steel");
    box.faces[inward] = { texture: "#inside", ...lit };
    return box;
  };
  const door = solid([0, 0, 0], [16, 16, 1.5], "#steel");
  door.faces.north = { texture: "#door" };
  const shut = [solid([0, 0, 1.5], [16, 16, 16], "#steel"), door, solid([2.5, 3, -1], [3.5, 13, 0], "#steel")];
  const open = [
    wall([0, 0, 1.5], [1, 16, 16], "east"),
    wall([15, 0, 1.5], [16, 16, 16], "west"),
    wall([1, 15, 1.5], [15, 16, 16], "down"),
    wall([1, 0, 1.5], [15, 1, 16], "up"),
    glowing([1, 1, 14], [15, 15, 15], "#inside", ["north"]),
    glowing([1, 5.5, 2], [15, 6, 14], "#inside", ["up", "down", "north"]),
    glowing([1, 10.5, 2], [15, 11, 14], "#inside", ["up", "down", "north"]),
    food([2.5, 1, 4], [4.5, 5, 6], 0, 0), food([6, 1, 5], [9, 3, 8], 4, 0), food([10, 1, 4], [14, 3.5, 9], 8, 0),
    food([3, 6, 5], [5, 8.5, 7], 12, 0), food([6, 6, 6], [8, 9, 8], 0, 4), food([10, 6, 5], [13, 8, 9], 4, 4),
    food([3, 11, 6], [4.5, 15, 7.5], 8, 4), food([5.5, 11, 6], [7, 14.5, 7.5], 12, 4), food([9, 11, 5], [12, 14.5, 8], 0, 8),
    { from: [16, 0, -14.5], to: [17.5, 16, 1.5], faces: { east: { texture: "#door" }, west: { texture: "#bins" }, north: { texture: "#steel" },
      south: { texture: "#steel" }, up: { texture: "#steel" }, down: { texture: "#steel" } } },
    solid([17.5, 3, -12.5], [18.5, 13, -11.5], "#steel"),
  ];
  for (const [state, elements] of [["shut", shut], ["open", open]]) {
    write(`assets/${ID}/models/block/galley_fridge_${state}.json`, { parent: "minecraft:block/block", textures, elements });
  }
  write(`assets/${ID}/blockstates/galley_fridge.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((isOpen) =>
      [`facing=${facing},open=${isOpen}`, { model: `${ID}:block/galley_fridge_${isOpen ? "open" : "shut"}`, y }]))),
  });
  write(`assets/${ID}/models/item/galley_fridge.json`, { parent: `${ID}:block/galley_fridge_shut` });
  dropsItself("galley_fridge");
  cubes.galley_fridge = null;
}
galleyFridge();
// The fridge is fitted into the rocket rather than built into her: light like the rest, but no part of her frame.
function galleyFridgeFittings() {
  return ["galley_fridge"];
}
// Its door's pop as its seal lets go, with the hum of its cooling swelling behind it, and its soft thud shut.
function galleyFridgeSounds() {
  return Object.fromEntries(["fridge_open", "fridge_close"].map((name) => [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
function galleyFridgeLang() {
  return {
    "block.skyliners.galley_fridge": "Galley Fridge",
    "container.skyliners.galley_fridge": "Fridge",
    "subtitles.skyliners.fridge_open": "Fridge opens and hums",
    "subtitles.skyliners.fridge_close": "Fridge shuts",
  };
}
// The pool's wave machine (see WaveMachineBlock): a chrome foot, a white post and head, a ring round the head's top and a
// big blue button in it, a wave drawn on the button. Idle, the button stands proud and the ring is a soft blue; running,
// the button is pressed down, both glowing whatever the light around, the wave on it rolling.
function waveMachine() {
  const block = (name) => `${ID}:block/${name}`;
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  // A box every face of which shows the texture given, glowing where it runs.
  const part = (from, to, texture, glowing) => ({ from, to, ...(glowing ? { shade: false } : {}),
    faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, { texture, ...(glowing ? lit : {}) }])) });
  for (const running of [false, true]) {
    write(`assets/${ID}/models/block/wave_machine${running ? "_running" : ""}.json`, {
      parent: "minecraft:block/block",
      textures: { chrome: block("wave_machine_chrome"), body: block("wave_machine_body"), ring: block(running ? "wave_ring_running" : "wave_ring"),
        button: block(running ? "wave_button_running" : "wave_button"), particle: block("wave_machine_body") },
      elements: [
        part([4, 0, 4], [12, 1, 12], "#chrome"),
        part([6.5, 1, 6.5], [9.5, 10, 9.5], "#body"),
        part([3.5, 10, 3.5], [12.5, 13, 12.5], "#body"),
        part([4, 13, 4], [12, 13.5, 12], "#ring", running),
        part([5, 13, 5], [11, running ? 13.75 : 14.5, 11], "#button", running),
      ],
    });
  }
  write(`assets/${ID}/blockstates/wave_machine.json`, {
    variants: { "running=false": { model: `${ID}:block/wave_machine` }, "running=true": { model: `${ID}:block/wave_machine_running` } },
  });
  write(`assets/${ID}/models/item/wave_machine.json`, { parent: `${ID}:block/wave_machine` });
  dropsItself("wave_machine");
  cubes.wave_machine = null;
}
waveMachine();
// The wave machine is fitted into the rocket rather than built into her: light like the rest, but no part of her frame.
function waveMachineFittings() {
  return ["wave_machine"];
}
// Its pumps starting with a rising whine and the waves washing through the channel (see tools/generate_sounds.mjs).
function waveMachineSounds() {
  return { wave_surge: { subtitle: `subtitles.${ID}.wave_surge`, sounds: [{ name: `${ID}:wave_surge` }] } };
}
function waveMachineLang() {
  return {
    "block.skyliners.wave_machine": "Wave Machine",
    "subtitles.skyliners.wave_surge": "Waves surge",
  };
}
// The club's fog machine (see FogMachineBlock), drawn facing north: a black flight case on the floor, a handle on its
// top and a little light by it, and a chrome nozzle out of its front. One model for each way it stands: its light green
// ready, white as it pumps, its nozzle's mouth glowing too, and amber as it heats up; the light glows whatever the light
// around.
function fogMachine() {
  const block = (name) => `${ID}:block/${name}`;
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  for (const fog of ["ready", "fogging", "heating"]) {
    const nozzle = solid([6.5, 2, 1], [9.5, 5, 3], "#nozzle");
    nozzle.faces.north = { texture: fog === "fogging" ? "#hot" : "#mouth", ...(fog === "fogging" ? lit : {}) };
    write(`assets/${ID}/models/block/fog_machine_${fog}.json`, {
      parent: "minecraft:block/block",
      textures: { case: block("fog_case"), trim: block("fog_trim"), nozzle: block("fog_nozzle"), mouth: block("fog_mouth"), hot: block("fog_mouth_hot"),
        light: block(`fog_light_${fog}`), particle: block("fog_case") },
      elements: [
        solid([2, 0, 3], [14, 7, 15], "#case"),
        solid([6, 7, 8], [10, 8.5, 9.5], "#trim"),
        nozzle,
        { from: [11, 7, 4], to: [12.5, 7.5, 5.5], shade: false,
          faces: Object.fromEntries(["north", "south", "east", "west", "up"].map((face) => [face, { texture: "#light", ...lit }])) },
      ],
    });
  }
  write(`assets/${ID}/blockstates/fog_machine.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["ready", "fogging", "heating"].map((fog) =>
      [`facing=${facing},fog=${fog}`, { model: `${ID}:block/fog_machine_${fog}`, y }]))),
  });
  write(`assets/${ID}/models/item/fog_machine.json`, { parent: `${ID}:block/fog_machine_ready` });
  dropsItself("fog_machine");
  cubes.fog_machine = null;
}
fogMachine();
// The fog machine is fitted into the rocket rather than built into her: light like the rest, but no part of her frame.
function fogMachineFittings() {
  return ["fog_machine"];
}
// Its hiss as it pumps fog, its pump sputtering under it (see tools/generate_sounds.mjs).
function fogMachineSounds() {
  return { fog_hiss: { subtitle: `subtitles.${ID}.fog_hiss`, sounds: [{ name: `${ID}:fog_hiss` }] } };
}
function fogMachineLang() {
  return {
    "block.skyliners.fog_machine": "Fog Machine",
    "subtitles.skyliners.fog_hiss": "Fog machine hisses",
  };
}

// The club's bar (see DrinkTapBlock, NeonSignBlock and CocktailTableBlock), drawn facing north. The drink tap: a chrome
// drip tray, its grate over it, and a chrome column on a foot plate, a little badge on its front, under a head across
// the top of it, three spouts reaching forward out of the head over the tray; on each spout a chrome ferrule, a tall
// neon handle, pink, cyan or lime, and a white knob, both glowing whatever the light around. One model for each way it
// stands: its handles upright, or one of them pulled, tipped down toward whoever pulls it, and a stream of Star Fizz
// glowing from its spout down to the grate. The neon signs: a black backing board against the back of the block and
// the tubes of the picture standing half a pixel off it, a pixel thick, one box for each run of tube of a colour; lit,
// the tubes glow in their colours, and off they are dark glass. The cocktail table: a round foot, a chrome pole up to
// a plate under the top, a ring of LED light glowing under the rim, and the round top of dark glass.
//
// The neon signs' pictures (see NeonSignBlock.Design), a character to a pixel of the sign as seen from in front, its
// top row first: a dot where there is no tube, a letter the colour of the tube there (see NEON_COLOURS).
const NEON_COLOURS = { c: "cyan", g: "lime", p: "pink", r: "red", v: "violet", a: "amber", y: "yellow", w: "white" };
const NEON_SIGNS = {
  // A martini glass, an olive on a pick in it, the red of its pimento at its middle.
  cocktail: [
    "................",
    ".............p..",
    "............p...",
    "...........p....",
    "ccccccccccpcccc.",
    ".c.......p...c..",
    "..c...ggg...c...",
    "...c..grg..c....",
    "....c.ggg.c.....",
    ".....c...c......",
    "......c.c.......",
    ".......c........",
    ".......c........",
    ".......c........",
    "....ccccccc.....",
    "................",
  ],
  // Two quavers beamed together, pink, their round heads violet.
  note: [
    "................",
    "................",
    ".....pppppppppp.",
    ".....pppppppppp.",
    ".....p........p.",
    ".....p........p.",
    ".....p........p.",
    ".....p........p.",
    ".....p........p.",
    ".....p........p.",
    "...vvp......vvp.",
    "..vvvv.....vvvv.",
    "...vv.......vv..",
    "................",
    "................",
    "................",
  ],
  // A planet, amber, tilted in its cyan ring, the ring passing in front of it and round behind it; a star either side.
  planet: [
    "................",
    "..y.............",
    ".yyy............",
    "..y...aaaa......",
    "....aa....aa....",
    "....a......a....",
    "...a........a...",
    "...a........a...",
    ".cca........acc.",
    "c..a........a..c",
    ".cccccccccccccc.",
    "....aa....aa....",
    "......aaaa...y..",
    "............yyy.",
    ".............y..",
    "................",
  ],
  // A rocket, white, its porthole cyan and its fins red, an amber and yellow flame under it.
  rocket: [
    "................",
    ".......ww.......",
    "......w..w......",
    ".....w....w.....",
    "....w......w....",
    "....w..cc..w....",
    "....w.c..c.w....",
    "....w..cc..w....",
    "....w......w....",
    "...rw......wr...",
    "..r.w......w.r..",
    "..rrwwwwwwwwrr..",
    ".....a.yy.a.....",
    "......ayya......",
    ".......aa.......",
    "................",
  ],
  // A pair of lips, red, a pink line between them.
  lips: [
    "................",
    "................",
    "................",
    "....rr....rr....",
    "...r..rrrr..r...",
    "..r..........r..",
    ".r............r.",
    "rppppppppppppppr",
    ".r............r.",
    "..r..........r..",
    "...rr......rr...",
    ".....rrrrrr.....",
    "................",
    "................",
    "................",
    "................",
  ],
  // A lightning bolt, yellow.
  bolt: [
    "................",
    "........yyyyyy..",
    ".......y....y...",
    "......y....y....",
    ".....y....y.....",
    "....y....yyyyy..",
    "...yyyyy....y...",
    ".......y...y....",
    "......y...y.....",
    "......y..y......",
    ".....y..y.......",
    ".....y.y........",
    "....y.y.........",
    "....yy..........",
    "...yy...........",
    "................",
  ],
};
// The boxes a picture's tubes are drawn with: from each pixel of tube not drawn yet, a run of its colour to the right,
// carried on down the rows under it for as long as they have the same run.
function neonTubes(rows) {
  const drawn = rows.map((row) => [...row].map((pixel) => pixel === "."));
  const tubes = [];
  for (let top = 0; top < rows.length; top++) {
    tubes.push(...neonRuns(rows, drawn, top));
  }
  return tubes;
}
// The runs starting in a row of a picture, each marked drawn as it is found: its colour's letter, and its first and
// last column and row.
function neonRuns(rows, drawn, top) {
  const runs = [];
  for (let left = 0; left < rows[top].length; left++) {
    if (!drawn[top][left]) {
      runs.push(neonRun(rows, drawn, top, left));
    }
  }
  return runs;
}
function neonRun(rows, drawn, top, left) {
  const colour = rows[top][left];
  const free = (row, column) => rows[row][column] === colour && !drawn[row][column];
  let right = left;
  while (right + 1 < rows[top].length && free(top, right + 1)) right++;
  const across = Array.from({ length: right - left + 1 }, (_, i) => left + i);
  let bottom = top;
  while (bottom + 1 < rows.length && across.every((column) => free(bottom + 1, column))) bottom++;
  for (let row = top; row <= bottom; row++) drawn[row].fill(true, left, right + 1);
  return { colour, left, right, top, bottom };
}
function clubBar() {
  const block = (name) => `${ID}:block/${name}`;
  // Where each handle and its spout are across the tower, in pixels (DrinkTapBlock.Pour).
  const HANDLES = { pink: 4, cyan: 8, lime: 12 };
  const tap = {
    chrome: block("lavatory_basin_chrome"), grate: block("drink_tap_grate"), badge: block("drink_tap_badge"), knob: block("neon_white"),
    stream: block("star_fizz_stream"), ...Object.fromEntries(Object.keys(HANDLES).map((colour) => [colour, block(`neon_${colour}`)])),
    particle: block("lavatory_basin_chrome"),
  };
  // A spout reaching forward out of the head, its nozzle pointing down over the tray (DrinkTapBlock.SPOUT_Z).
  const spout = (across) => [
    furniture([across - 0.5, 6.5, 3.5], [across + 0.5, 7.5, 7], "#chrome"),
    furniture([across - 0.5, 5.25, 3.5], [across + 0.5, 6.5, 4.5], "#chrome", { up: null }),
  ];
  // A handle on its spout, upright, or pulled: tipped as far as a model can tip it, toward the front.
  const handle = (colour, across, pulled) => {
    const rotation = { origin: [across, 7.5, 6], axis: "x", angle: -45 };
    return [
      furniture([across - 0.5, 7.5, 5.5], [across + 0.5, 8.5, 6.5], "#chrome"),
      furniture([across - 0.75, 8.5, 5.25], [across + 0.75, 12.5, 6.75], `#${colour}`, {}, true),
      furniture([across - 0.5, 12.5, 5.5], [across + 0.5, 13, 6.5], "#knob", {}, true),
    ].map((part) => (pulled ? { ...part, rotation } : part));
  };
  const tower = [
    furniture([3, 0, 1.5], [13, 0.5, 6], "#chrome"),
    furniture([3.5, 0.5, 2], [12.5, 0.75, 5.5], "#grate", { down: null }),
    furniture([6, 0, 7], [10, 0.5, 11], "#chrome"),
    furniture([6.5, 0.5, 7.5], [9.5, 6, 10.5], "#chrome"),
    furniture([6.5, 2.5, 7.25], [9.5, 5.5, 7.5], "#chrome", { north: { texture: "#badge", uv: [0, 0, 3, 3] } }),
    furniture([2.5, 6, 7], [13.5, 8, 9.5], "#chrome"),
    ...Object.values(HANDLES).flatMap(spout),
  ];
  for (const pour of ["none", ...Object.keys(HANDLES)]) {
    const handles = Object.entries(HANDLES).flatMap(([colour, across]) => handle(colour, across, colour === pour));
    const stream = pour === "none" ? [] : [furniture([HANDLES[pour] - 0.4, 0.75, 3.6], [HANDLES[pour] + 0.4, 5.25, 4.4], "#stream",
      { up: null, down: null }, true)];
    write(`assets/${ID}/models/block/drink_tap${pour === "none" ? "" : `_${pour}`}.json`, {
      parent: "minecraft:block/block",
      ...(pour === "none" ? {} : { render_type: "minecraft:translucent" }),
      textures: tap,
      elements: [...tower, ...handles, ...stream],
    });
  }
  write(`assets/${ID}/blockstates/drink_tap.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["none", ...Object.keys(HANDLES)].map((pour) =>
      [`facing=${facing},pour=${pour}`, { model: `${ID}:block/drink_tap${pour === "none" ? "" : `_${pour}`}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/models/item/drink_tap.json`, { parent: `${ID}:block/drink_tap` });
  write(`assets/${ID}/models/item/star_fizz.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/star_fizz` } });

  for (const [design, rows] of Object.entries(NEON_SIGNS)) {
    const tubes = neonTubes(rows);
    const colours = [...new Set(tubes.map((tube) => NEON_COLOURS[tube.colour]))];
    for (const lit of [true, false]) {
      const glass = Object.fromEntries(colours.map((colour) => [colour, block(`neon_${colour}${lit ? "" : "_off"}`)]));
      write(`assets/${ID}/models/block/neon_sign_${design}${lit ? "" : "_off"}.json`, {
        parent: "minecraft:block/block",
        textures: { backer: block("neon_backer"), particle: block("neon_backer"), ...glass },
        elements: [
          furniture([0, 0, 15], [16, 16, 16], "#backer"),
          // Seen from in front, from the north, the picture's left is the model's east.
          ...tubes.map(({ colour, left, right, top, bottom }) =>
            furniture([15 - right, 15 - bottom, 13.5], [16 - left, 16 - top, 14.5], `#${NEON_COLOURS[colour]}`, { south: null }, lit)),
        ],
      });
    }
  }
  write(`assets/${ID}/blockstates/neon_sign.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => Object.keys(NEON_SIGNS).flatMap((design) => [true, false].map((lit) =>
      [`design=${design},facing=${facing},lit=${lit}`, { model: `${ID}:block/neon_sign_${design}${lit ? "" : "_off"}`, ...(y ? { y } : {}) }])))),
  });
  keptLooks("neon_sign", { design: Object.keys(NEON_SIGNS) }, (design) => `${ID}:block/neon_sign_${design}`, ["cocktail"]);
  // Broken, a sign keeps its picture, as a planter keeps its plant; the martini glass is left off, so they stack.
  const signId = `${ID}:neon_sign`;
  write(`data/${ID}/loot_table/blocks/neon_sign.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{
        type: "minecraft:item",
        name: signId,
        functions: [{
          function: "minecraft:copy_state", block: signId, properties: ["design"],
          conditions: [{ condition: "minecraft:inverted", term: { condition: "minecraft:block_state_property", block: signId, properties: { design: "cocktail" } } }],
        }],
      }],
      conditions: [{ condition: "minecraft:survives_explosion" }],
    }],
    random_sequence: `${ID}:blocks/neon_sign`,
  });

  const table = {
    glass: block("cocktail_table_glass"), led: block("bar_led"), chrome: block("lavatory_basin_chrome"), foot: block("cabin_seat_frame"),
    particle: block("cocktail_table_glass"),
  };
  const ring = { up: null, down: null };
  write(`assets/${ID}/models/block/cocktail_table.json`, {
    parent: "minecraft:block/block",
    textures: table,
    elements: [
      furniture([4, 0, 5], [12, 1, 11], "#foot"),
      furniture([5, 0, 4], [11, 1, 12], "#foot"),
      furniture([7, 1, 7], [9, 14, 9], "#chrome"),
      furniture([5.5, 14, 5.5], [10.5, 14.5, 10.5], "#chrome"),
      furniture([1.25, 14.5, 3.25], [14.75, 15.25, 12.75], "#led", ring, true),
      furniture([3.25, 14.5, 1.25], [12.75, 15.25, 14.75], "#led", ring, true),
      furniture([2.25, 14.5, 2.25], [13.75, 15.25, 13.75], "#led", ring, true),
      furniture([1, 15.25, 3], [15, 16, 13], "#glass"),
      furniture([3, 15.25, 1], [13, 16, 15], "#glass"),
      furniture([2, 15.25, 2], [14, 16, 14], "#glass"),
    ],
  });
  write(`assets/${ID}/blockstates/cocktail_table.json`, { variants: { "": { model: `${ID}:block/cocktail_table` } } });
  write(`assets/${ID}/models/item/cocktail_table.json`, { parent: `${ID}:block/cocktail_table` });
  dropsItself("drink_tap");
  dropsItself("cocktail_table");
  for (const name of clubBarFittings()) {
    cubes[name] = null;
  }
}
clubBar();
// The club's bar is fitted into the rocket rather than built into her: light like the rest, but no part of her frame.
function clubBarFittings() {
  return ["drink_tap", "neon_sign", "cocktail_table"];
}
// The tap's fizzy pour, and a neon sign's transformer clinking and its tubes buzzing on, or dying away (see
// tools/generate_sounds.mjs).
function clubBarSounds() {
  return Object.fromEntries(["tap_pour", "neon_on", "neon_off"].map((name) => [name, { subtitle: `subtitles.${ID}.${name}`, sounds: [{ name: `${ID}:${name}` }] }]));
}
// The three neon colours of the tap's handles over iron; glowstone in glass round a dye; black glass on an iron pole.
function clubBarRecipes() {
  shaped("drink_tap", ["PCL", "III"], { P: "minecraft:pink_dye", C: "minecraft:cyan_dye", L: "minecraft:lime_dye", I: "minecraft:iron_ingot" }, 1);
  shaped("neon_sign", ["GLG", "GDG"], { G: "minecraft:glass_pane", L: "minecraft:glowstone_dust", D: "minecraft:magenta_dye" }, 2);
  shaped("cocktail_table", ["GGG", " I ", " I "], { G: "minecraft:black_stained_glass_pane", I: "minecraft:iron_ingot" }, 2);
}
function clubBarLang() {
  return {
    "block.skyliners.drink_tap": "Drink Tap",
    "item.skyliners.star_fizz": "Star Fizz",
    "block.skyliners.neon_sign": "Neon Sign",
    "block.skyliners.cocktail_table": "Cocktail Table",
    "subtitles.skyliners.tap_pour": "Soda fizzes from the tap",
    "subtitles.skyliners.neon_on": "Neon sign buzzes on",
    "subtitles.skyliners.neon_off": "Neon sign clicks off",
  };
}
function parcelScannerLang() {
  return {
    "block.skyliners.parcel_scanner": "Parcel Scanner",
    "subtitles.skyliners.scanner_sweep": "Scanner whirrs",
    "subtitles.skyliners.scanner_pass": "Scanner beeps: packed",
    "subtitles.skyliners.scanner_fail": "Scanner buzzes: empty",
  };
}

// Room dividers, and the exit signs over the doors between the rooms: fitted into the cabin, so breakable, and no
// part of the hull (see FITTINGS).
const ROOM_DIVIDERS = ["cabin_curtain", "cabin_partition", "cabin_arch", "exit_sign"];
const ROOM_DIVIDER_LANG = {
  "block.skyliners.cabin_curtain": "Cabin Curtain",
  "block.skyliners.cabin_partition": "Cabin Partition",
  "block.skyliners.cabin_arch": "Cabin Arch",
  "block.skyliners.exit_sign": "Exit Sign",
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
// merged into boxes, showing a face where a box meets the open archway or something set back further, and on every
// side of the block it reaches, culled against whatever stands there, so a jamb standing free is closed (the jamb or
// springer over or under it hides that face, see CabinArchBlock.skipRendering). The arch's pieces, and in the hand a
// narrow archway whole as one goes up put down by hand, are drawn round by the mod's own "cabin_arch" loader, from the
// same rings round the same curve (see CabinArchModel), so the arch's underside and its moulding follow the circle
// instead of stepping round it a pixel at a time.
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
    if (z0 === 0 || exposed(span, u0, v0, box, "out")) faces.north = face(texture, [16 - x1, 16 - y1, 16 - x0, 16 - y0], z0 === 0 && "north");
    if (z1 === 16 || exposed(span, u0, v0, box, "in")) faces.south = face(texture, [x0, 16 - y1, x1, 16 - y0], z1 === 16 && "south");
    if (y1 === 16 || exposed(span, u0, v0, box, "up")) faces.up = face(texture, [x0, z0, x1, z1], y1 === 16 && "up");
    if (y0 === 0 || exposed(span, u0, v0, box, "down")) faces.down = face(texture, [x0, 16 - z1, x1, 16 - z0], y0 === 0 && "down");
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
// The exit sign (see ExitSignBlock), drawn facing north, glowing whatever the light around: on a wall a thin plate
// with its back to it. Under a ceiling, the sign lit on both faces across the middle of the block, hung on two short
// brushed steel rods as the overhead WC sign is; on the floor, the same sign on a short steel post standing in a
// steel base, a collar where the post meets each. Off the wall the picture is pinned to its place on the texture.
{
  const sign = { face: `${ID}:block/exit_sign`, casing: `${ID}:block/cabin_partition`, particle: `${ID}:block/exit_sign` };
  write(`assets/${ID}/models/block/exit_sign.json`, boxes(sign, [[[1, 4, 14.5], [15, 12, 16], { north: "#face", all: "#casing" }, true]]));
  const textures = { ...sign, rod: `${ID}:block/bulkhead_sign_bezel`, steel: `${ID}:block/cabin_rail_steel` };
  // The sign lit on both faces, its bottom at the height given, eight pixels tall as on a wall.
  const bothFaces = (bottom) => {
    const face = { texture: "#face", uv: [1, 4, 15, 12] };
    return furniture([1, bottom, 7.25], [15, bottom + 8, 8.75], "#casing", { north: face, south: face }, true);
  };
  const shapes = {
    ceiling: [bothFaces(6), solid([3, 14, 7.5], [4, 16, 8.5], "#rod"), solid([12, 14, 7.5], [13, 16, 8.5], "#rod")],
    floor: [
      bothFaces(7),
      solid([6.5, 6.5, 6.75], [9.5, 7, 9.25], "#steel"),
      solid([7, 1.5, 7], [9, 6.5, 9], "#steel"),
      solid([6.5, 1, 6.5], [9.5, 1.5, 9.5], "#steel"),
      solid([4, 0, 5], [12, 1, 11], "#steel"),
    ],
  };
  for (const [mount, elements] of Object.entries(shapes)) {
    write(`assets/${ID}/models/block/exit_sign_${mount}.json`, { parent: "minecraft:block/block", textures, elements });
  }
  write(`assets/${ID}/blockstates/exit_sign.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["ceiling", "wall", "floor"].map((mount) =>
      [`facing=${facing},mount=${mount}`, { model: `${ID}:block/exit_sign${mount === "wall" ? "" : `_${mount}`}`, y }]))),
  });
  write(`assets/${ID}/models/item/exit_sign.json`, { parent: `${ID}:block/exit_sign` });
}

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
  keptLooks("bulkhead_sign", { picture: BULKHEAD_SIGN_PICTURES }, (picture) => `${ID}:block/bulkhead_sign_${picture}`, [BULKHEAD_SIGN_PICTURES[0]]);
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
    "block.skyliners.bulkhead_sign": "Bulkhead Sign",
  };
}

// The WC signs (see WcSignBlock and OverheadWcSignBlock). The one over a WC's door: the bulkhead's lightbox, its toilet
// lit green while the WC is free and red while it is taken. The overhead one, drawn facing north: a slim off-white
// lightbox three pixels deep, its back against the south side of the block, or hung in the block's middle on two
// brushed steel rods from the block over it, or standing in the block's middle, two pixels higher and its housing
// textured as it is hanging, on two short posts of the rods' steel where its rods would be, each in a steel foot, a
// low bar between the feet; on its face, inside a pixel of the housing all round, the arrow and the toilet glowing
// whatever the light around, a quarter of a pixel proud. In the hand, each as it is when its WC is free, the
// overhead one pointing straight on. Taken down, the overhead sign keeps its arrow, the first left off.
const WC_SIGN_ARROWS = ["ahead", "left", "right", "down", "none"];
{
  const block = (name) => `${ID}:block/${name}`;
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  // How the overhead sign is put up, as its HANGING and STANDING say: its models' names, where its lightbox is and
  // what holds it there.
  const mounts = {
    wall: { suffix: "", front: 13, lift: 0, holder: [] },
    hanging: {
      suffix: "_hanging", front: 6.5, lift: 0,
      holder: [solid([2.5, 14, 7.5], [3.5, 16, 8.5], "#rod"), solid([12.5, 14, 7.5], [13.5, 16, 8.5], "#rod")],
    },
    standing: {
      suffix: "_standing", front: 6.5, lift: 2,
      holder: [
        solid([2.25, 1, 7.25], [3.75, 4, 8.75], "#rod"),
        solid([12.25, 1, 7.25], [13.75, 4, 8.75], "#rod"),
        solid([1, 0, 5.5], [5, 1, 10.5], "#rod"),
        solid([11, 0, 5.5], [15, 1, 10.5], "#rod"),
        solid([5, 0, 7.5], [11, 0.5, 8.5], "#rod"),
      ],
    },
  };
  for (const taken of [false, true]) {
    const state = taken ? "_occupied" : "";
    bulkheadLightbox(`wc_sign${state}`, `wc_sign${state}`);
    for (const arrow of WC_SIGN_ARROWS) {
      for (const [mount, { suffix, front, lift, holder }] of Object.entries(mounts)) {
        // The lightbox's depth behind its front face; raised, its sides show the rows of its housing they show
        // unraised.
        const back = front + 3;
        const sides = { north: [0, 2, 16, 14], south: [0, 2, 16, 14], east: [16 - back, 2, 16 - front, 14], west: [front, 2, back, 14] };
        const housing = {
          from: [0, 2 + lift, front], to: [16, 14 + lift, back],
          faces: Object.fromEntries(["north", "south", "east", "west", "up", "down"].map((face) => [face, {
            texture: "#housing",
            ...(lift && sides[face] ? { uv: sides[face] } : {}),
            ...(face === "south" && mount === "wall" ? { cullface: "south" } : {}),
          }])),
        };
        const face = {
          from: [1, 3 + lift, front - 0.25], to: [15, 13 + lift, front], shade: false,
          faces: { north: { texture: "#face", uv: [1, 3, 15, 13], ...lit } },
        };
        write(`assets/${ID}/models/block/overhead_wc_sign_${arrow}${state}${suffix}.json`, {
          parent: "minecraft:block/block",
          textures: {
            housing: block("overhead_wc_sign"), face: block(`overhead_wc_sign_${arrow}${state}`), rod: block("bulkhead_sign_bezel"),
            particle: block("overhead_wc_sign"),
          },
          elements: [housing, face, ...holder],
        });
      }
    }
  }
  write(`assets/${ID}/blockstates/wc_sign.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((taken) =>
      [`facing=${facing},occupied=${taken}`, { model: `${ID}:block/wc_sign${taken ? "_occupied" : ""}`, ...(y ? { y } : {}) }]))),
  });
  // A sign that hangs hangs, whatever STANDING says.
  const mountOf = (hanging, standing) => mounts[hanging ? "hanging" : standing ? "standing" : "wall"];
  write(`assets/${ID}/blockstates/overhead_wc_sign.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => WC_SIGN_ARROWS.flatMap((arrow) =>
      [false, true].flatMap((hanging) => [false, true].flatMap((taken) => [false, true].map((standing) => [
        `arrow=${arrow},facing=${facing},hanging=${hanging},occupied=${taken},standing=${standing}`,
        { model: `${ID}:block/overhead_wc_sign_${arrow}${taken ? "_occupied" : ""}${mountOf(hanging, standing).suffix}`, ...(y ? { y } : {}) },
      ])))))),
  });
  write(`assets/${ID}/models/item/wc_sign.json`, { parent: `${ID}:block/wc_sign` });
  keptLooks("overhead_wc_sign", { arrow: WC_SIGN_ARROWS }, (arrow) => `${ID}:block/overhead_wc_sign_${arrow}`, [WC_SIGN_ARROWS[0]]);
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
    "block.skyliners.wc_sign": "WC Sign",
    "block.skyliners.overhead_wc_sign": "Overhead WC Sign",
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
    "block.skyliners.cabin_stairs": "Cabin Stairs",
    "block.skyliners.cabin_landing": "Cabin Landing",
    "block.skyliners.cabin_rail": "Cabin Rail",
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
    "block.skyliners.economy_seat": "Economy Seat",
    "block.skyliners.business_seat": "Business Seat",
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
    "block.skyliners.cabin_carpet": "Cabin Carpet",
    "block.skyliners.aisle_carpet": "Aisle Carpet",
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
    "block.skyliners.gym_floor": "Gym Floor",
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
      ["true", "false"].map((mirror) => [`facing=${facing},half=${half},mirror=${mirror}`, { model: `${ID}:block/lavatory_basin_${half}`, ...(y ? { y } : {}) }])).flat())),
  });
  const raised = upper.map((element) => ({ ...element, from: [element.from[0], element.from[1] + 16, element.from[2]], to: [element.to[0], element.to[1] + 16, element.to[2]] }));
  write(`assets/${ID}/models/item/lavatory_basin.json`, {
    parent: "minecraft:block/block",
    textures,
    elements: [...lower, ...raised],
    display: TALL_ITEM_DISPLAY,
  });
  dropsFrom("lavatory_basin", { half: "lower" });
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
    "block.skyliners.lavatory_basin": "Lavatory Basin",
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
  dropsFrom(name, { part: "0" });
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
    "block.skyliners.pilot_seat": "Pilot Seat",
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
    "block.skyliners.crew_locker": "Flight Crew Locker",
    "container.skyliners.crew_locker": "Flight Crew Locker",
    "subtitles.skyliners.crew_locker_open": "Crew locker opens",
    "subtitles.skyliners.crew_locker_close": "Crew locker shuts",
  };
}

for (const name of ["airliner_blueprint", "furnished_airliner_blueprint", "zeppelin_blueprint", "furnished_zeppelin_blueprint", "festive_zeppelin_blueprint",
  "half_zeppelin_blueprint", "half_furnished_zeppelin_blueprint", "half_festive_zeppelin_blueprint", "rocket_blueprint", "freighter_blueprint"]) {
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
    "block.skyliners.springboard": "Springboard",
    "subtitles.skyliners.springboard_boing": "Springboard boings",
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
  cabinDecorLang["block.skyliners.starlight_panel"] = "Starlight Panel";
}
// The gymnastic rings, drawn facing north: two wooden rings side by side, each a square loop on a leather strap up to
// the top of the block, where the chain they hang from carries on. Put up elsewhere (see GymRingsBlock.MOUNT), their
// straps end under a dark steel bar across the top of the block instead: on the floor the crossbar of a free-standing
// frame, on two uprights with long feet on the floor, the rings three pixels higher, clear of the floor; on a wall, its
// back on the south side of its block, a bracket's bar, held out from the wall on two arms from their wall plates.
{
  const textures = { wood: `${ID}:block/gym_rings_wood`, strap: `${ID}:block/gym_rings_strap`, particle: `${ID}:block/gym_rings_wood` };
  // The two rings, their bottoms so many pixels up, on their straps up to a height.
  const rings = (low, top) => [4.5, 11.5].flatMap((cx) => [
    [[cx - 2, low, 7.5], [cx + 2, low + 0.75, 8.5], "#wood"],
    [[cx - 2, low + 3.25, 7.5], [cx + 2, low + 4, 8.5], "#wood"],
    [[cx - 2, low + 0.75, 7.5], [cx - 1.25, low + 3.25, 8.5], "#wood"],
    [[cx + 1.25, low + 0.75, 7.5], [cx + 2, low + 3.25, 8.5], "#wood"],
    [[cx - 0.5, low + 4, 7.75], [cx + 0.5, top, 8.25], "#strap"],
  ]);
  // The bar across the top of the block the straps hang from, on a frame or a bracket.
  const bar = [[0, 14.5, 7], [16, 16, 9], "#frame"];
  const framed = { ...textures, frame: `${ID}:block/shelf_steel` };
  const mounts = {
    gym_rings: [textures, rings(1, 16)],
    gym_rings_floor: [framed, [
      bar,
      [[0.5, 1, 7.25], [2, 14.5, 8.75], "#frame"],
      [[14, 1, 7.25], [15.5, 14.5, 8.75], "#frame"],
      [[0, 0, 3], [2.5, 1, 13], "#frame"],
      [[13.5, 0, 3], [16, 1, 13], "#frame"],
      ...rings(4, 14.5),
    ]],
    gym_rings_wall: [framed, [
      bar,
      [[1, 14.5, 9], [2.5, 16, 15], "#frame"],
      [[13.5, 14.5, 9], [15, 16, 15], "#frame"],
      [[0.5, 12, 15], [3, 16, 16], "#frame"],
      [[13, 12, 15], [15.5, 16, 16], "#frame"],
      ...rings(1, 14.5),
    ]],
  };
  for (const [name, [modelTextures, parts]] of Object.entries(mounts)) {
    write(`assets/${ID}/models/block/${name}.json`, boxes(modelTextures, parts));
  }
  write(`assets/${ID}/blockstates/gym_rings.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["ceiling", "wall", "floor"].map((mount) =>
      [`facing=${facing},mount=${mount}`, { model: `${ID}:block/gym_rings${mount === "ceiling" ? "" : `_${mount}`}`, y }]))),
  });
  write(`assets/${ID}/models/item/gym_rings.json`, { parent: `${ID}:block/gym_rings` });
  dropsItself("gym_rings");
  cubes.gym_rings = null;
  cabinDecorLang["block.skyliners.gym_rings"] = "Gymnastic Rings";
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
  cabinDecorLang["block.skyliners.hide_and_seek_button"] = "Hide and Seek Button";
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
// The freighter's crew ladder up her core (see RungLadderBlock), drawn facing north, its wall to the south: two chrome
// rails close up against the wall, as slim as any ladder, and a ribbed steel rung between them every quarter block.
// Its top piece's rails carry on up past its top into grab handles bent back to the wall.
{
  const textures = { chrome: `${ID}:block/lavatory_basin_chrome`, tread: `${ID}:block/pool_ladder_tread`, particle: `${ID}:block/lavatory_basin_chrome` };
  const piece = [];
  const top = [];
  for (const [x0, x1] of [[1.5, 3], [13, 14.5]]) {
    piece.push([[x0, 0, 13.5], [x1, 16, 15], "#chrome"]);
    top.push([[x0, 16, 13.5], [x1, 23, 15], "#chrome"], [[x0, 21.5, 15], [x1, 23, 16], "#chrome"]);
  }
  for (const y of [1.5, 5.5, 9.5, 13.5]) {
    piece.push([[3, y, 13], [13, y + 1, 15], { up: "#tread", down: "#tread", all: "#chrome" }]);
  }
  write(`assets/${ID}/models/block/crew_ladder.json`, boxes(textures, piece));
  write(`assets/${ID}/models/block/crew_ladder_top.json`, boxes(textures, [...piece, ...top]));
  write(`assets/${ID}/blockstates/crew_ladder.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((isTop) =>
      [`facing=${facing},top=${isTop}`, { model: `${ID}:block/crew_ladder${isTop ? "_top" : ""}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/models/item/crew_ladder.json`, { parent: `${ID}:block/crew_ladder_top` });
  dropsItself("crew_ladder");
  cubes.crew_ladder = null;
}
// The bunk ladder up a bunk bed's foot end (see RungLadderBlock), drawn facing north, the bunk's end to the south: two
// white steel uprights up against it with a round chrome rung between them every quarter block. Its top piece's
// uprights carry on up past its top and hook over onto the upper bunk's footboard beyond it, to hold on to climbing
// off. It has no item: it comes with its bunk bed.
{
  const textures = { frame: `${ID}:block/bunk_frame`, chrome: `${ID}:block/lavatory_basin_chrome`, particle: `${ID}:block/bunk_frame` };
  const piece = [];
  const top = [];
  for (const [x0, x1] of [[1.5, 3], [13, 14.5]]) {
    piece.push([[x0, 0, 13.5], [x1, 16, 15], "#frame"]);
    top.push(
      [[x0, 16, 13.5], [x1, 22, 15], "#frame"],
      [[x0, 20.5, 15], [x1, 22, 19], "#frame"],
    );
  }
  for (const y of [2, 6, 10, 14]) {
    piece.push([[3, y, 13.75], [13, y + 1, 14.75], "#chrome"]);
  }
  write(`assets/${ID}/models/block/bunk_ladder.json`, boxes(textures, piece));
  // Its hooks reach out of the block, textured as they would be inside it (see patched).
  const ladder = boxes(textures, piece);
  write(`assets/${ID}/models/block/bunk_ladder_top.json`, { ...ladder, elements: [...ladder.elements, ...top.map(([from, to, texture]) => patched(from, to, texture))] });
  write(`assets/${ID}/blockstates/bunk_ladder.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => [false, true].map((isTop) =>
      [`facing=${facing},top=${isTop}`, { model: `${ID}:block/bunk_ladder${isTop ? "_top" : ""}`, ...(y ? { y } : {}) }]))),
  });
  write(`assets/${ID}/models/item/bunk_ladder.json`, { parent: `${ID}:block/bunk_ladder` });
  dropsItself("bunk_ladder");
  cubes.bunk_ladder = null;
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
    "block.skyliners.pool_deck_tile": "Pool Deck Tile",
    "block.skyliners.pool_mosaic": "Pool Mosaic",
    "block.skyliners.pool_coping": "Pool Coping",
    "block.skyliners.pool_steps": "Pool Steps",
    "block.skyliners.pool_ladder": "Pool Ladder",
    "block.skyliners.bunk_ladder": "Bunk Ladder",
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
    "block.skyliners.wall_bars": "Wall Bars",
    "block.skyliners.gym_mat": "Gym Mat",
    "block.skyliners.climbing_rope": "Climbing Rope",
    "block.skyliners.glass_backboard": "Glass Backboard",
    "block.skyliners.gym_bench": "Gym Bench",
  };
}
for (const name of GYM_HALL) {
  dropsItself(name);
  cubes[name] = null;
}

// Vacuum toilets (see VacuumToiletBlock) and the blue ice they drop (see BlueIceChunk and LavIceBlock), and what they
// add to the lang file.
const VACUUM_TOILET_LANG = {
  "block.skyliners.vacuum_toilet": "Vacuum Toilet",
  "block.skyliners.lav_ice": "Blue Lav Ice",
  "entity.skyliners.blue_ice_chunk": "Blue Ice",
  "subtitles.skyliners.toilet_flush": "Vacuum toilet flushes",
  "subtitles.skyliners.blue_ice_crack": "Blue ice shatters",
  "subtitles.skyliners.blue_ice_bonk": "Bonk!",
  "message.skyliners.blue_ice.bonk": "Bonk! Blue ice from above",
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
// any way. It melts away wherever it is, so it drops nothing.
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
  write(`assets/${ID}/models/item/lav_ice.json`, { parent: `${ID}:block/lav_ice` });
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
    "block.skyliners.seat_console": "Seat Console",
    "block.skyliners.lounge_table": "Lounge Table",
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
    "block.skyliners.galley_oven": "Galley Oven",
    "block.skyliners.galley_stowage": "Galley Stowage",
    "block.skyliners.galley_worktop": "Galley Worktop",
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
  keptLooks("planter", { plant: Object.keys(plants) }, (plant) => `${ID}:block/planter_${plant}`, ["greenery"]);
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
    "block.skyliners.planter": "Planter",
  };
}

// The lounge's bar (see BarCounterBlock, BarStoolBlock and DisplayShelfBlock), drawn facing north. The counter: a dark
// plinth set back under the front, which curves out over it in steps up to the dark stone top, overhanging, its LED
// strip glowing along the foot of the curve; white at its sides and back too. The stool: a round foot, a chrome
// column with a footrest ring round it, and the round leather seat on a chrome plate. The back bar's cabinet: walnut
// sides round its dark front of smoked glass doors, a chrome rail across them on two brackets, under a stone top, a
// warm LED strip under the top's edge. Its shelves: walnut sides and cornice round a smoked mirror at the back, two
// glass shelves across it, and three rows of bottles, of every colour and five shapes, tall and slim, square, round
// and squat decanters, on the cabinet's top and on each shelf. A warm LED strip runs along the front of each row's
// foot, the mirror behind each row glowing up from it.
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
  const bottles = ["green", "amber", "clear", "blue", "red", "violet"];
  const bottleTextures = Object.fromEntries(bottles.map((colour) => [colour, block(`bar_bottle_${colour}`)]));
  // The bottles' shapes, in pixels: each body's width across the shelf, its depth and its height; its neck's width and
  // height; and its cap's, or its stopper's, width and height and what it is made of.
  const BOTTLE_SHAPES = {
    tall: { width: 1.5, depth: 1.5, height: 3, neck: [0.5, 1.25], cap: [0.75, 0.5, "#chrome"] },
    square: { width: 2.5, depth: 2, height: 2.5, neck: [1, 0.5], cap: [1.5, 0.75, "#cork"] },
    round: { width: 2, depth: 2, height: 2.75, neck: [1, 0.75], cap: [1.25, 0.5, "#chrome"] },
    slim: { width: 1, depth: 1, height: 3.25, neck: [0.5, 0.75], cap: [0.75, 0.5, "#gold"] },
    decanter: { width: 2.5, depth: 2.5, height: 2, neck: [1, 0.5], cap: [1.5, 1, "#clear"] },
  };
  // How far back the bottles stand, in pixels from the front of the block, one half a pixel forward of it and the next
  // half a pixel back in turn; and from where and how far across the rows run, between the shelves' sides.
  const BOTTLES_BACK = 11.5;
  const ROW_FROM = 1;
  const ROW_WIDTH = 14;
  // How brightly the bottles' glass shows whatever the light around, in light levels: lit by the strips under them, so
  // they still glow in a dark club, if less than the strips themselves.
  const BOTTLE_GLOW = 10;
  // A bottle of a shape and colour with its west side at x, its foot at y and its middle z back: its body, its glass
  // drawn from the texture's foot up so that the warm light is at its foot and its label just over that, glowing a
  // little; its neck; and its cap. Its back and its foot, against the mirror and the shelf, are never seen.
  const bottle = (shape, colour, x, y, z) => {
    const { width, depth, height, neck: [neckWidth, neckHeight], cap: [capWidth, capHeight, cap] } = BOTTLE_SHAPES[shape];
    const glass = `#${colour}`;
    const fromFoot = (across) => ({ texture: glass, uv: [0, 16 - height, across, 16], neoforge_data: { block_light: BOTTLE_GLOW } });
    const middle = x + width / 2;
    const shoulder = y + height;
    return [
      furniture([x, y, z - depth / 2], [x + width, shoulder, z + depth / 2], glass,
        { north: fromFoot(width), east: fromFoot(depth), west: fromFoot(depth), south: null, down: null }),
      furniture([middle - neckWidth / 2, shoulder, z - neckWidth / 2], [middle + neckWidth / 2, shoulder + neckHeight, z + neckWidth / 2], glass,
        { south: null, down: null }),
      furniture([middle - capWidth / 2, shoulder + neckHeight, z - capWidth / 2],
        [middle + capWidth / 2, shoulder + neckHeight + capHeight, z + capWidth / 2], cap, { south: null }),
    ];
  };
  // A row of bottles standing at y, spaced evenly across the shelf in the order given.
  const bottleRow = (y, row) => {
    const gap = (ROW_WIDTH - row.reduce((sum, [shape]) => sum + BOTTLE_SHAPES[shape].width, 0)) / (row.length + 1);
    const parts = [];
    let x = ROW_FROM + gap;
    for (const [i, [shape, colour]] of row.entries()) {
      parts.push(...bottle(shape, colour, x, y, BOTTLES_BACK + (i % 2 === 0 ? -0.5 : 0.5)));
      x += BOTTLE_SHAPES[shape].width + gap;
    }
    return parts;
  };
  // Each tier of the shelves: from and to how high its bottles stand, in pixels, on the cabinet's top or on a glass
  // shelf half a pixel thick under them, and its bottles, each short enough to stand under the next shelf.
  const TIERS = [
    [0, 5, [["tall", "green"], ["square", "amber"], ["slim", "red"], ["round", "clear"], ["tall", "blue"], ["decanter", "amber"], ["slim", "violet"]]],
    [5.5, 10.25, [["round", "blue"], ["slim", "amber"], ["tall", "clear"], ["square", "green"], ["slim", "red"], ["decanter", "violet"], ["tall", "amber"]]],
    [10.75, 15, [["square", "violet"], ["round", "red"], ["decanter", "clear"], ["round", "green"], ["square", "blue"]]],
  ];
  // A tier: its glass shelf, but on the cabinet's top; the LED strip along the front of its foot; the mirror behind it,
  // its glow drawn from the texture's foot up so that it glows up from the strip; and its row of bottles.
  const tier = ([from, to, row]) => [
    ...(from > 0 ? [furniture([ROW_FROM, from - 0.5, 8], [ROW_FROM + ROW_WIDTH, from, 15], "#glass")] : []),
    furniture([ROW_FROM, Math.max(0, from - 0.5), 7.75], [ROW_FROM + ROW_WIDTH, Math.max(0.5, from), 8], "#led", { south: null }, true),
    furniture([ROW_FROM, from, 14.9], [ROW_FROM + ROW_WIDTH, to, 14.9], "#mirror", {
      north: { texture: "#mirror", uv: [ROW_FROM, 16 - (to - from), ROW_FROM + ROW_WIDTH, 16] },
      south: null, east: null, west: null, up: null, down: null,
    }, true),
    ...bottleRow(from, row),
  ];
  const backBar = {
    ...bottleTextures, cork: block("bar_bottle_cork"), gold: block("perfume_cap"), chrome, veneer: block("business_seat_veneer"),
    doors: block("back_bar_doors"), top: block("bar_top"), mirror: block("back_bar_mirror"), glass: block("back_bar_glass"),
    led: block("back_bar_led"), particle: block("business_seat_veneer"),
  };
  write(`assets/${ID}/models/block/back_bar_cabinet.json`, {
    parent: "minecraft:block/block",
    textures: backBar,
    elements: [
      furniture([0, 0, 1], [16, 14, 16], "#veneer", { north: "#doors" }),
      furniture([0, 14, 0], [16, 16, 16], "#top"),
      furniture([0, 13.5, 0.75], [16, 14, 1], "#led", { south: null, east: null, west: null, up: null }, true),
      furniture([1.5, 9, 0], [14.5, 10, 0.5], "#chrome"),
      furniture([2.5, 9.25, 0.5], [3.5, 9.75, 1], "#chrome"),
      furniture([12.5, 9.25, 0.5], [13.5, 9.75, 1], "#chrome"),
    ],
  });
  write(`assets/${ID}/models/block/back_bar_shelves.json`, {
    parent: "minecraft:block/block",
    textures: backBar,
    elements: [
      furniture([0, 0, 15], [16, 16, 16], "#veneer", { north: null }),
      furniture([0, 0, 7], [1, 16, 15], "#veneer"),
      furniture([15, 0, 7], [16, 16, 15], "#veneer"),
      furniture([1, 15, 7], [15, 16, 15], "#veneer"),
      ...TIERS.flatMap(tier),
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
    "block.skyliners.bar_counter": "Bar Counter",
    "block.skyliners.bar_stool": "Bar Stool",
    "block.skyliners.back_bar": "Back Bar",
  };
}

// The robot bartender (see RobotBartenderBlock), drawn facing north, its right hand to the east, two blocks tall. Its
// lower block's model is its body: a chrome pedestal on a round foot ringed by a cyan glow, the column up to a graphite
// collar, white hips, a graphite waist, the white torso with a cyan light on its chest under a shoulder yoke, and the
// chrome neck. The rest its renderer draws over it, moving, each part a state of the lower block, drawn where it
// stands unturned and turned about its own joint (see RobotBartenderRenderer, whose joints are where these parts meet):
// the head, rounded white over a dark visor band, chrome ears and a nub on top, turning about the neck; the visor's
// glow, a cyan strip a hair in front of the band, left out as it blinks; each arm's upper arm, a white shoulder over a
// chrome arm hanging to the elbow, swinging from the shoulder, and its forearm, a graphite elbow, a white forearm
// reaching forward and a graphite hand, bending at the elbow; and what it holds: the glass it polishes in its left
// hand, thin walls round a hollow on a base, the drink it pours into it, Nebula Fizz under a striped straw, the
// cocktail shaker in its right, a chrome tin under its cap, and the polishing cloth draped over its right hand. Parts
// reaching into the block above take their faces' texture from inside its sixteen pixels, a patch of it as big as the
// face. Its upper block is only room for them, with nothing of its own. The item is the whole bartender at rest, its
// forearms forward, the glass and the cloth in its hands. Its mocktails are item sprites.
const BARTENDER_PARTS = ["body", "head", "visor", "left_upper_arm", "left_forearm", "right_upper_arm", "right_forearm", "glass", "drink", "shaker", "cloth"];
{
  const block = (name) => `${ID}:block/${name}`;
  const textures = {
    white: block("robot_bartender_white"), chrome: block("robot_bartender_chrome"), dark: block("robot_bartender_dark"),
    visor: block("robot_bartender_visor"), eyes: block("robot_bartender_eyes"), glow: block("robot_bartender_glow"),
    glass: block("robot_bartender_glass"), drink: block("robot_bartender_drink"), cloth: block("robot_bartender_cloth"),
    particle: block("robot_bartender_white"),
  };
  const lit = { neoforge_data: { block_light: 15, sky_light: 15 } };
  // A box of it, each face a patch of the texture from its corner as big as the face; glowing, unshaded, where lit.
  const part = (from, to, texture, glowing = false) => {
    const [width, height, depth] = [0, 1, 2].map((axis) => Math.min(16, to[axis] - from[axis]));
    const patches = {
      north: [0, 0, width, height], south: [0, 0, width, height], east: [0, 0, depth, height], west: [0, 0, depth, height],
      up: [0, 0, width, depth], down: [0, 0, width, depth],
    };
    return {
      from, to, ...(glowing ? { shade: false } : {}),
      faces: Object.fromEntries(Object.entries(patches).map(([face, uv]) => [face, { texture, uv, ...(glowing ? lit : {}) }])),
    };
  };
  // A part of its right side mirrored onto its left, across the model's middle.
  const mirrored = (element) => ({ ...element, from: [16 - element.to[0], element.from[1], element.from[2]], to: [16 - element.from[0], element.to[1], element.to[2]] });
  const body = [
    part([3.5, 0, 2.5], [12.5, 1.5, 11.5], "#chrome"),
    part([2.5, 0, 3.5], [13.5, 1.5, 10.5], "#chrome"),
    part([4, 1.5, 3.5], [12, 2, 10.5], "#glow", true),
    part([6.5, 2, 5.5], [9.5, 10, 8.5], "#chrome"),
    part([6, 10, 5], [10, 11.5, 9], "#dark"),
    part([5, 11.5, 4.5], [11, 13.5, 9.5], "#white"),
    part([6, 13.5, 5], [10, 15, 9], "#dark"),
    part([4.5, 15, 4], [11.5, 21, 10], "#white"),
    part([5, 15.5, 3.5], [11, 20.5, 4], "#white"),
    part([7, 18, 3.3], [9, 19, 3.5], "#glow", true),
    part([4, 20.5, 4.5], [12, 22.5, 9.5], "#white"),
    part([7, 22.5, 6], [9, 24.5, 8], "#chrome"),
  ];
  const head = [
    part([5, 24.5, 4], [11, 30.5, 10], "#white"),
    part([5.5, 30.5, 4.5], [10.5, 31.5, 9.5], "#white"),
    part([4.6, 25.5, 4.6], [11.4, 29.5, 9.4], "#white"),
    part([5.2, 26.5, 3.6], [10.8, 29, 4], "#visor"),
    part([4.2, 26.5, 6], [4.6, 28.5, 8], "#chrome"),
    part([11.4, 26.5, 6], [11.8, 28.5, 8], "#chrome"),
    part([7.5, 31.5, 6.5], [8.5, 32, 7.5], "#chrome"),
  ];
  // The eyes' strip, the whole of its texture squeezed onto its face.
  const visor = [{ from: [5.7, 27.2, 3.45], to: [10.3, 28.3, 3.6], shade: false, faces: { north: { texture: "#eyes", uv: [0, 0, 16, 16], ...lit } } }];
  const rightUpperArm = [
    part([11.4, 20.4, 5.9], [13.6, 22.8, 8.1], "#white"),
    part([11.8, 16.5, 6.3], [13.2, 20.5, 7.7], "#chrome"),
  ];
  const rightForearm = [
    part([11.6, 15.6, 6.1], [13.4, 17.4, 7.9], "#dark"),
    part([11.7, 15.8, 1.8], [13.3, 17.2, 6.2], "#white"),
    part([11.8, 15.7, 0.2], [13.2, 17.3, 1.8], "#dark"),
  ];
  // The glass, inside the left hand: a base and four thin walls round its hollow.
  const glass = [
    part([4.3, 15.6, 0], [6.3, 15.9, 2], "#glass"),
    part([4.3, 15.9, 0], [6.3, 18.6, 0.25], "#glass"),
    part([4.3, 15.9, 1.75], [6.3, 18.6, 2], "#glass"),
    part([4.3, 15.9, 0.25], [4.55, 18.6, 1.75], "#glass"),
    part([6.05, 15.9, 0.25], [6.3, 18.6, 1.75], "#glass"),
  ];
  const drink = [...glass, part([4.55, 15.9, 0.25], [6.05, 18.1, 1.75], "#drink"), part([5.6, 17.6, 0.9], [5.9, 19.8, 1.2], "#cloth")];
  const shaker = [
    part([9.9, 15.2, 0.2], [11.7, 19.2, 2], "#chrome"),
    part([10.15, 19.2, 0.45], [11.45, 20.3, 1.75], "#chrome"),
    part([10.5, 20.3, 0.8], [11.1, 20.8, 1.4], "#chrome"),
  ];
  const cloth = [part([9.9, 15.4, -0.1], [11.8, 17.7, 2.1], "#cloth"), part([10.2, 14.4, 0.3], [11.5, 15.4, 1.7], "#cloth")];
  const parts = {
    body, head, visor, glass, drink, shaker, cloth,
    left_upper_arm: rightUpperArm.map(mirrored), left_forearm: rightForearm.map(mirrored), right_upper_arm: rightUpperArm, right_forearm: rightForearm,
  };
  const model = (name, elements) => write(`assets/${ID}/models/block/${name}.json`, { parent: "minecraft:block/block", ambientocclusion: false, textures, elements });
  for (const name of BARTENDER_PARTS) {
    model(`robot_bartender_${name}`, parts[name]);
  }
  write(`assets/${ID}/models/block/robot_bartender_upper.json`, { parent: "minecraft:block/block", textures: { particle: textures.particle }, elements: [] });
  write(`assets/${ID}/blockstates/robot_bartender.json`, {
    variants: Object.fromEntries(Object.entries(yRot).flatMap(([facing, y]) => ["lower", "upper"].flatMap((half) => BARTENDER_PARTS.map((name) =>
      [`facing=${facing},half=${half},part=${name}`, { model: `${ID}:block/robot_bartender_${half === "upper" ? "upper" : name}`, ...(y ? { y } : {}) }])))),
  });
  write(`assets/${ID}/models/item/robot_bartender.json`, {
    parent: "minecraft:block/block",
    ambientocclusion: false,
    textures,
    elements: [...body, ...head, ...visor, ...parts.left_upper_arm, ...parts.left_forearm, ...rightUpperArm, ...rightForearm, ...glass, ...cloth],
    display: {
      gui: { rotation: [30, 225, 0], translation: [0, -3, 0], scale: [0.4, 0.4, 0.4] },
      ground: { rotation: [0, 0, 0], translation: [0, 2, 0], scale: [0.15, 0.15, 0.15] },
      fixed: { rotation: [0, 0, 0], translation: [0, -3, 0], scale: [0.3, 0.3, 0.3] },
      thirdperson_righthand: { rotation: [75, 45, 0], translation: [0, 1.5, 0], scale: [0.22, 0.22, 0.22] },
      firstperson_righthand: { rotation: [0, 45, 0], translation: [0, -1, 0], scale: [0.25, 0.25, 0.25] },
      firstperson_lefthand: { rotation: [0, 225, 0], translation: [0, -1, 0], scale: [0.25, 0.25, 0.25] },
    },
  });
  write(`data/${ID}/loot_table/blocks/robot_bartender.json`, {
    type: "minecraft:block",
    pools: [{
      rolls: 1.0,
      bonus_rolls: 0.0,
      entries: [{ type: "minecraft:item", name: `${ID}:robot_bartender` }],
      conditions: [
        { condition: "minecraft:block_state_property", block: `${ID}:robot_bartender`, properties: { half: "lower" } },
        { condition: "minecraft:survives_explosion" },
      ],
    }],
    random_sequence: `${ID}:blocks/robot_bartender`,
  });
  cubes.robot_bartender = null;
  for (const drinkName of ["nebula_fizz", "rocket_fuel", "zero_g_float", "supernova_sunrise", "moon_milk"]) {
    write(`assets/${ID}/models/item/${drinkName}.json`, { parent: "minecraft:item/generated", textures: { layer0: `${ID}:item/${drinkName}` } });
  }
}
// What of the robot bartender is fitted rather than built in: breakable, and no part of the airframe.
function robotBartenderFittings() {
  return ["robot_bartender"];
}
// The servo whirr of its head turning to a customer, its shaker's rattle, the pour and the glass set down, and its
// robot voice, greeting a customer and answering a sale, each time one of three lines (see tools/generate_sounds.mjs).
function robotBartenderSounds() {
  const LINES = 3;
  const voiced = (name) => ({ subtitle: `subtitles.${ID}.${name}`, sounds: Array.from({ length: LINES }, (_, i) => ({ name: `${ID}:${name}_${i + 1}` })) });
  return {
    bartender_servo: { subtitle: `subtitles.${ID}.bartender_servo`, sounds: [{ name: `${ID}:bartender_servo` }] },
    bartender_shake: { subtitle: `subtitles.${ID}.bartender_shake`, sounds: [{ name: `${ID}:bartender_shake` }] },
    bartender_greet: voiced("bartender_greet"),
    bartender_serve: voiced("bartender_serve"),
  };
}
// Iron round an observer for its eyes and a redstone block for its works, under glass bottles for its trade.
function robotBartenderRecipes() {
  shaped("robot_bartender", ["BOB", "IRI", " I "], {
    B: "minecraft:glass_bottle", O: "minecraft:observer", I: "minecraft:iron_ingot", R: "minecraft:redstone_block",
  }, 1);
}
function robotBartenderLang() {
  return {
    "block.skyliners.robot_bartender": "Robot Bartender",
    "container.skyliners.mocktail_bar": "Mocktail Bar",
    "item.skyliners.nebula_fizz": "Nebula Fizz",
    "item.skyliners.rocket_fuel": "Rocket Fuel",
    "item.skyliners.zero_g_float": "Zero-G Float",
    "item.skyliners.supernova_sunrise": "Supernova Sunrise",
    "item.skyliners.moon_milk": "Moon Milk",
    "subtitles.skyliners.bartender_servo": "Robot bartender whirrs",
    "subtitles.skyliners.bartender_shake": "Cocktail shaker rattles",
    "subtitles.skyliners.bartender_greet": "Robot bartender greets you",
    "subtitles.skyliners.bartender_serve": "Robot bartender takes the order",
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
    "block.skyliners.duty_free_display": "Duty-Free Display",
    "block.skyliners.awning": "Awning",
  };
}

const blocks = [...Object.keys(cubes), "airliner_door"].map((name) => `${ID}:${name}`);
const tag = (values) => ({ replace: false, values });
write("data/minecraft/tags/block/mineable/pickaxe.json", tag(blocks.filter((b) => !b.endsWith("tire") && !b.endsWith("landing_target") && !b.endsWith("cabin_curtain"))));
// The care package is a wooden crate, the gym floor maple, the wall bars and the gym bench ash, the garden beds and the
// sprinklers' boxes spruce, and the shelf units' end panels birch.
write("data/minecraft/tags/block/mineable/axe.json", tag([`${ID}:cargo_crate`, `${ID}:paper_plane_box`, `${ID}:gym_floor`, `${ID}:wall_bars`, `${ID}:gym_bench`,
  `${ID}:garden_bed`, `${ID}:sprinkler`, `${ID}:shelf_unit`]));
write("data/minecraft/tags/block/doors.json", tag([`${ID}:airliner_door`]));
write("data/minecraft/tags/item/doors.json", tag([`${ID}:airliner_door`]));
// Villagers find their way through it like a wooden door; this lets them open it on the way to their seats too.
write("data/minecraft/tags/block/mob_interactable_doors.json", tag([`${ID}:airliner_door`]));
// Climbed as a ladder is.
write("data/minecraft/tags/block/climbable.json", tag([...poolClimbable(), ...gymHallClimbable(), "bunk_ladder", "crew_ladder"].map((name) => `${ID}:${name}`)));
// Sable's physics: the whole airframe is aircraft-light, and the tires let it roll down a runway on their own.
const GROUND = ["launch_pad_grate", "launch_pad_hub", "landing_target", "tower_radio", "tower_radar", "transponder", "motion_sensor", "lift_panel", "jet_bridge", "check_in_desk", ...towerInstrumentsGround()];
const airliner = blocks.filter((b) => !GROUND.some((ground) => b.endsWith(`:${ground}`)));
// What is fitted into the cabin rather than built into the airliner: light like the rest, but not hull, and breakable.
const FITTINGS = ["light_strip", "pendant_light", "light_cable", "halo_light", "wall_light", "floor_light", "overhead_bin", "passenger_screen", "hoop", "scoreboard", "ball_rack", "galley",
  "model_airliner", "model_zeppelin", "model_rocket", "model_freighter", "paper_plane_box", "horn_cord", "livery_board", "livery_selector", "flag_halyard", "flagstaff", "lookout_glass", "screen_stand", "ballast_valve", "searchlight", "searchlight_handle", "floodlight", "franking_machine", "mail_sack", "banner_cleat", "crane_winch", "carrier_lever", "skywriting_board", "boarding_stairs_button", "boost_button", "ships_plaque", "picture_board", "engine_repeater", "bubble_level", "duty_free_counter", "springboard", "starlight_panel", "gym_rings", "hide_and_seek_button", ...ROOM_DIVIDERS, "vacuum_toilet"];
FITTINGS.push(...holdAndTailFittings());
FITTINGS.push(...airstairFittings());
FITTINGS.push(...roverFittings());
FITTINGS.push(...rampFittings());
// The club's bandstand is fitted into her club, set down and taken up by hand.
FITTINGS.push("bandstand");
// The Spookie's hammock is fitted into her crew quarters, and her engineering's machines into it.
FITTINGS.push("spookie_hammock", "jump_drive", "breaker_board", "coolant_valve", "crossbow_rack");
// The round floor window and its panel are fitted into her lounge, and laid and taken out by hand anywhere.
FITTINGS.push("floor_window", "floor_window_panel");
// Her beach's palms, parasol, loungers and torches are fitted into her lounge too, set down and taken up by hand.
FITTINGS.push("potted_palm", "parasol", "sun_lounger", "tiki_torch");
// Her crew ladder is fitted up her core, and set up by hand anywhere.
FITTINGS.push("crew_ladder");
// The freighter's outside fittings, fitted onto her hull.
FITTINGS.push(...hullFittingNames());
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
FITTINGS.push(...robotBartenderFittings());
FITTINGS.push(...dutyFreeKioskFittings());
FITTINGS.push(...ledStripFittings());
FITTINGS.push(...windowDimmerFittings());
FITTINGS.push("dome_limit");
FITTINGS.push(...spacePosterFittings());
FITTINGS.push(...shaftDecorFittings());
FITTINGS.push(...snackMachineFittings());
FITTINGS.push(...labFittings());
FITTINGS.push(...domeFittings());
// The floor a lift lays under its car is fitted into the shaft, not part of her frame.
FITTINGS.push("lift_floor");
FITTINGS.push(...foodLabFittings());
FITTINGS.push(...greenhouseFittings());
FITTINGS.push(...rocketRoomFittings());
FITTINGS.push(...airlockPanelFittings());
FITTINGS.push(...parcelScannerFittings());
FITTINGS.push(...galleyFridgeFittings());
FITTINGS.push(...waveMachineFittings());
FITTINGS.push(...fogMachineFittings());
FITTINGS.push(...clubBarFittings());
FITTINGS.push(...girderDecorFittings());
const structure = airliner.filter((b) => !FITTINGS.some((fitting) => b.endsWith(`:${fitting}`)));
write("data/sable/tags/block/super_light.json", tag(airliner));
// What counts as the inside of an airliner for the spawn guard: everything but the gear, which stands on open ground,
// and the buttons, the airstairs' (the call button on the gear and the one on the skin outside by the top of the stairs
// among them) and the doors'; but the self-destruct button, on the flight deck's wall like its other controls, and the
// rocket's boost button, on her centre console, are in.
const outsideButton = (b) => b.endsWith("_button") && !b.endsWith(":self_destruct_button") && !b.endsWith(":rocket_boost_button");
write(`data/${ID}/tags/block/airframe.json`, tag(structure.filter((b) => !b.includes("landing_gear") && !outsideButton(b))));
// What cannot be broken, except by a player sneaking with a wrench or in creative mode: the airliner's frame and
// controls, its own blocks and the other mods' it is built with, but not the cabin floor, where the building goes, nor
// the rocket's floors finished as her rooms', which stand in its place.
write(`data/${ID}/tags/block/unbreakable.json`, tag([
  ...structure.filter((b) => !b.endsWith("cabin_floor") && !b.endsWith(":rocket_floor")),
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
  `${ID}:rover`, `${ID}:tardigrade`, `${ID}:spookie`]));
// A lift's car lives among its ship's blocks and goes with them: taken away, it goes too.
write("data/sable/tags/entity_type/destroy_with_sub_level.json", tag([`${ID}:lift_car`, `${ID}:tardigrade`, `${ID}:spookie`]));
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
shaped("engine_grating", ["BBB", "BGB", "BBB"], { B: "minecraft:iron_bars", G: "minecraft:glowstone_dust" }, 8);
shaped("crossbow_rack", ["PGP", "PPP"], { P: "minecraft:dark_oak_planks", G: "minecraft:gold_nugget" }, 1);
shaped("floor_window", ["IGI", "GGG", "IGI"], { I: "minecraft:iron_ingot", G: "minecraft:glass" }, 1);
shaped("beach_sand", ["SS", "SS"], { S: "minecraft:sand" }, 4);
shaped("potted_palm", [" J ", " J ", " P "], { J: "minecraft:jungle_sapling", P: "minecraft:flower_pot" }, 1);
shaped("parasol", ["WWW", " S ", " S "], { W: "minecraft:red_wool", S: "minecraft:stick" }, 1);
shaped("sun_lounger", ["  W", "SSS"], { W: "minecraft:cyan_wool", S: "minecraft:white_concrete" }, 1);
shaped("tiki_torch", ["T", "B", "B"], { T: "minecraft:torch", B: "minecraft:bamboo" }, 2);
shaped("crew_ladder", ["I I", "III", "I I"], { I: "minecraft:iron_ingot" }, 4);
shaped("floor_window_panel", [" B ", "IRI", " I "], { B: "minecraft:stone_button", I: "minecraft:iron_ingot", R: "minecraft:redstone" }, 1);
shaped("coolant_valve", [" R ", "IPI", " I "], { R: "minecraft:red_dye", I: "minecraft:iron_ingot", P: "minecraft:piston" }, 1);
shaped("breaker_board", ["ILI", "LRL", "ILI"], { I: "minecraft:iron_ingot", L: "minecraft:lever", R: "minecraft:redstone" }, 1);
shaped("jump_drive", ["ICI", "BEB", "III"], { I: "minecraft:iron_ingot", C: "minecraft:end_crystal", B: "minecraft:blue_ice", E: "minecraft:ender_eye" }, 1);
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
shaped("halo_light", ["C", "Q", "G"], { C: `${ID}:light_cable`, Q: "minecraft:quartz", G: "minecraft:glowstone_dust" }, 1);
shaped("steel_girder", ["III", " I ", "III"], { I: "minecraft:iron_ingot" }, 4);
shaped("lift_glass", [" G ", "GTG", " G "], { G: "minecraft:glass", T: "minecraft:tinted_glass" }, 4);
shaped("padded_bulkhead", ["WWW", "WIW", "WWW"], { W: "minecraft:white_wool", I: "minecraft:iron_ingot" }, 8);
shaped("engine_grille", ["BBB", "GGG", "BBB"], { B: "minecraft:iron_bars", G: "minecraft:glowstone_dust" }, 6);
shaped("deck_robot", [" G ", "IRI", "I I"], { G: "minecraft:glass_pane", I: "minecraft:iron_ingot", R: "minecraft:redstone" }, 1);
shaped("ramp_button", ["G", "B", "I"], { G: "minecraft:glowstone_dust", B: "minecraft:polished_blackstone_button", I: "minecraft:iron_ingot" }, 1);
shaped("laser_crossbow", [" A ", "RCR", " O "], { A: "minecraft:amethyst_shard", R: "minecraft:redstone", C: "minecraft:crossbow", O: "minecraft:copper_ingot" }, 1);
shaped("laser_cannon", ["ARA", "IDI", "III"], { A: "minecraft:amethyst_shard", R: "minecraft:redstone", I: "minecraft:iron_ingot", D: "minecraft:dispenser" }, 1);
shaped("satellite_dish", [" Q ", "QIQ", " B "], { Q: "minecraft:quartz", I: "minecraft:iron_ingot", B: "minecraft:iron_bars" }, 1);
shaped("big_satellite_dish", ["DD", "DD"], { D: `${ID}:satellite_dish` }, 1);
shaped("antenna_mast", ["R", "B", "I"], { R: "minecraft:redstone_torch", B: "minecraft:iron_bars", I: "minecraft:iron_ingot" }, 2);
shaped("sensor_dome", [" Q ", "QPQ", "III"], { Q: "minecraft:quartz", P: "minecraft:ender_pearl", I: "minecraft:iron_ingot" }, 1);
shaped("heat_sink", ["BBB", "BBB", "ICI"], { B: "minecraft:iron_bars", I: "minecraft:iron_ingot", C: "minecraft:copper_ingot" }, 2);
shaped("exhaust_vent", ["B", "H", "I"], { B: "minecraft:iron_bars", H: "minecraft:hopper", I: "minecraft:iron_ingot" }, 2);
shaped("hull_machinery", ["CRC", "III"], { C: "minecraft:copper_ingot", R: "minecraft:redstone", I: "minecraft:iron_ingot" }, 2);
shaped("hull_floodlight", ["IGI", " I "], { I: "minecraft:iron_ingot", G: "minecraft:glowstone" }, 2);
shaped("hull_light_strip", ["GGG", "III"], { G: "minecraft:glowstone_dust", I: "minecraft:iron_ingot" }, 6);
shaped("hull_plate_dark", ["PPP", "PDP", "PPP"], { P: `${ID}:airframe_panel`, D: "minecraft:gray_dye" }, 8);
shaped("hull_plate_riveted", ["PPP", "PNP", "PPP"], { P: `${ID}:airframe_panel`, N: "minecraft:iron_nugget" }, 8);
shaped("hull_plate_patched", ["PPP", "PCP", "PPP"], { P: `${ID}:airframe_panel`, C: "minecraft:copper_ingot" }, 8);
shaped("hazard_plate", ["PPP", "YPB", "PPP"], { P: `${ID}:airframe_panel`, Y: "minecraft:yellow_dye", B: "minecraft:black_dye" }, 7);
shaped("hull_light_band", ["PPP", "GGG", "PPP"], { P: `${ID}:airframe_panel`, G: "minecraft:glowstone_dust" }, 6);
shaped("bandstand", ["NNN", "PPP"], { N: "minecraft:note_block", P: "minecraft:dark_oak_slab" }, 1);
shaped("spookie_hammock", ["I I", "WWW", "I I"], { I: "minecraft:iron_bars", W: "minecraft:orange_wool" }, 1);
shaped("oval_window", ["NGN", "GGG", "NGN"], { G: "minecraft:glass", N: "minecraft:iron_nugget" }, 1);
// Two portholes from a pane of glass in a ring of iron.
shaped("porthole", [" N ", "NGN", " N "], { G: "minecraft:glass", N: "minecraft:iron_nugget" }, 2);
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
rocketControlRecipes();
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
rocketStairsRecipes();
cargoNetRecipes();
cargoFloorRecipes();
uldContainerRecipes();
poolRecipes();
gymHallRecipes();
seatConsoleRecipes();
galleyUnitRecipes();
planterRecipes();
barRecipes();
robotBartenderRecipes();
dutyFreeKioskRecipes();
towerInstrumentsRecipes();
greenhouseRecipes();
clubBarRecipes();

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
  "itemGroup.skyliners": "Create: Skyliners",
  "itemGroup.skyliners.blueprints": "Blueprints",
  "itemGroup.skyliners.airframe": "Airliner Airframe",
  "itemGroup.skyliners.rocket": "Rocket",
  "itemGroup.skyliners.freighter": "Freighter",
  "itemGroup.skyliners.flight_deck": "Flight Deck",
  "itemGroup.skyliners.rocket_cockpit": "Rocket Cockpit",
  "itemGroup.skyliners.doors": "Doors and Stairs",
  "itemGroup.skyliners.control_car": "Zeppelin Control Car",
  "itemGroup.skyliners.crew_kit": "Crew Kit",
  "itemGroup.skyliners.drops": "Skydiving and Drops",
  "itemGroup.skyliners.gadgets": "Gadgets",
  "itemGroup.skyliners.zeppelin_extras": "Zeppelin Extras",
  "itemGroup.skyliners.seats": "Seats",
  "itemGroup.skyliners.floors_walls": "Floors and Walls",
  "itemGroup.skyliners.signs": "Signs and Washrooms",
  "itemGroup.skyliners.duty_free": "Duty Free",
  "itemGroup.skyliners.hold": "The Hold",
  "itemGroup.skyliners.garden": "Garden",
  "itemGroup.skyliners.lab": "Lab",
  "itemGroup.skyliners.galley": "Galley and Coffee",
  "itemGroup.skyliners.bar": "Bar",
  "itemGroup.skyliners.gym": "Gym Hall",
  "itemGroup.skyliners.pool": "Pool",
  "itemGroup.skyliners.club": "Club",
  "itemGroup.skyliners.den": "The Den",
  "itemGroup.skyliners.lights": "Lights",
  "itemGroup.skyliners.decorations": "Decorations",
  "itemGroup.skyliners.control_tower": "Control Tower",
  "itemGroup.skyliners.terminal": "Terminal",
  "block.skyliners.airframe_panel": "Airframe Panel",
  "block.skyliners.airframe_fairing": "Airframe Fairing",
  "block.skyliners.livery_panel": "Livery Panel",
  "block.skyliners.cabin_window": "Cabin Window",
  "block.skyliners.cockpit_window": "Cockpit Window",
  "block.skyliners.cabin_floor": "Cabin Floor",
  "block.skyliners.cabin_light": "Cabin Light",
  "block.skyliners.airliner_door": "Airliner Door",
  "block.skyliners.engine_intake": "Engine Intake",
  "block.skyliners.landing_gear": "Landing Gear",
  "block.skyliners.landing_gear_tire": "Landing Gear Tire",
  "block.skyliners.airstair_controller": "Airstair Controller",
  "block.skyliners.airstair_call_button": "Airstair Call Button",
  "block.skyliners.airframe_shell": "Airframe Shell",
  "block.skyliners.cockpit_glass": "Cockpit Glass",
  "block.skyliners.hull_glass": "Hull Glass",
  "block.skyliners.smoked_glass": "Smoked Glass",
  "block.skyliners.dimming_glass": "Dimming Glass",
  "block.skyliners.cabin_window_shell": "Curved Cabin Window",
  "block.skyliners.oval_window": "Oval Window",
  "block.skyliners.flat_oval_window": "Oval Window",
  "block.skyliners.porthole": "Porthole",
  "block.skyliners.flat_porthole": "Porthole",
  "block.skyliners.lift_machine": "Lift Machine",
  "block.skyliners.lift_floor": "Lift Car Floor",
  "block.skyliners.lift_call_button": "Lift Call Button",
  "block.skyliners.lift_door": "Lift Door",
  "block.skyliners.lift_board": "Lift Board",
  "entity.skyliners.lift_car": "Lift Car",
  "block.skyliners.thrust_lever": "Thrust Lever",
  "block.skyliners.thrust_relay": "Thrust Relay",
  "block.skyliners.hull_relay": "Hull Relay",
  "block.skyliners.instrument_panel": "Instrument Panel",
  "message.skyliners.thrust": "Thrust: %s%%",
  "block.skyliners.autopilot": "Autopilot",
  "block.skyliners.nav_light": "Navigation Light",
  "block.skyliners.engine_core": "Engine Core",
  "block.skyliners.tiller": "Nose Wheel Tiller",
  "block.skyliners.flight_display": "Flight Display",
  "block.skyliners.parking_brake": "Parking Brake",
  "message.skyliners.tiller.left": "Nose wheel: steering left",
  "message.skyliners.tiller.right": "Nose wheel: steering right",
  "message.skyliners.tiller.centre": "Nose wheel: straight ahead",
  "message.skyliners.brake.set": "Parking brake set",
  "message.skyliners.brake.released": "Parking brake released",
  "subtitles.skyliners.engine": "Jet engine roars",
  ...VOICE_LANG,
  ...atcLang(),
  ...rocketSoundsLang(),
  ...freighterSoundsLang(),
  "message.skyliners.autopilot.engaged": "Autopilot engaged: take-off, then climbing to altitude %s at %s b/s, %s.",
  "message.skyliners.autopilot.off": "Autopilot off: the thrust levers, tiller and parking brake have the airliner again.",
  "message.skyliners.autopilot.off_zeppelin": "Autopilot off: the wheels, telegraphs and burner lever in the control car have the zeppelin now.",
  "message.skyliners.autopilot.off_rocket": "Autopilot off: the rocket rests on her fins, her engines quiet, ready to count down again.",
  "message.skyliners.autopilot.off_freighter": "Autopilot off: the freighter rests on her legs, her thrusters quiet, ready to lift off again.",
  "message.skyliners.autopilot.not_assembled": "The autopilot only flies an assembled airliner, and there is no Physics Assembler aboard to assemble it.",
  "message.skyliners.autopilot.assembling": "Assembling the airliner: the autopilot takes it from here.",
  "message.skyliners.autopilot.assembly_failed": "The Physics Assembler could not assemble the airliner: %s",
  "message.skyliners.autopilot.still_assembling": "The airliner is still being assembled.",
  "message.skyliners.autopilot.assembly_unknown": "it did not say why",
  "message.skyliners.autopilot.phase.takeoff": "TAKE-OFF",
  "message.skyliners.autopilot.phase.climb": "CLIMB",
  "message.skyliners.autopilot.phase.cruise": "CRUISE",
  "message.skyliners.autopilot.phase.descent": "DESCENT",
  "message.skyliners.autopilot.phase.land": "LAND",
  "message.skyliners.autopilot.phase.rollout": "ROLL-OUT",
  "message.skyliners.autopilot.holding": "Autopilot engaged: altitude %s at %s b/s, %s.",
  "message.skyliners.autopilot.set": "Autopilot set: altitude %s at %s b/s, %s.",
  "message.skyliners.autopilot.go_around": "Landing called off: climbing back to altitude %s at %s b/s, %s.",
  "message.skyliners.autopilot.mode.heading": "heading %s",
  "message.skyliners.autopilot.mode.orbit_left": "circling left, radius %s blocks",
  "message.skyliners.autopilot.mode.orbit_right": "circling right, radius %s blocks",
  "message.skyliners.autopilot.mode.destination": "flying to the pin, then circling it, radius %s blocks",
  "message.skyliners.autopilot.mode.destination_land": "flying to the pin to land near it, on clear, level ground",
  "message.skyliners.autopilot.mode.rocket_pin": "over to the pin and down on it",
  "message.skyliners.autopilot.mode.rocket_back": "and back down where she rose",
  "message.skyliners.autopilot.mode.rocket_hold": "holding there until told to land or given a pin",
  "message.skyliners.autopilot.pin.found": "Landing site found %s blocks from the pin: flying the approach, to land heading %s.",
  "message.skyliners.autopilot.pin.nowhere": "Nowhere clear to land near the pin yet: flying over it again and again, looking as more of the land comes into view.",
  "message.skyliners.autopilot.pin.go_around": "Not lined up with the landing site: going around to try again.",
  "message.skyliners.autopilot.coming_in": "The autopilot is landing already; set a new height to call the landing off.",
  "message.skyliners.autopilot.landing": "Autopilot landing: down ahead on heading %s, slowing until the nose sits level; once down it brakes to a stop and parks.",
  "message.skyliners.autopilot.on_ground": "The autopilot lands from the air: the airliner is on the ground already.",
  "message.skyliners.autopilot.rolling_out": "The autopilot is braking to a stop after landing; disengage it to take over.",
  "message.skyliners.autopilot.landed": "Landed and stopped: thrust levers at idle, parking brake set, autopilot off.",
  "message.skyliners.autopilot.unstuck": "Control jets firing: hold Unstuck to lift the airliner higher.",
  "message.skyliners.autopilot.unstuck.firing": "The control jets are firing already.",
  "message.skyliners.autopilot.unstuck.resting": "The control jets are resting: try again in a few seconds.",
  "message.skyliners.autopilot.unstuck.engines_off": "Push the thrust levers forward first: the jets only help engines that cannot move the airliner.",
  "message.skyliners.autopilot.unstuck.braked": "The parking brake is holding the airliner: release it to roll.",
  "message.skyliners.autopilot.unstuck.moving": "The airliner is not stuck: it is moving, or has only just stopped.",
  "item.skyliners.parachute": "Parachute",
  "block.skyliners.jump_light": "Jump Light",
  "block.skyliners.jump_switch": "Jump Light Switch",
  "block.skyliners.parachute_rack": "Parachute Rack",
  "block.skyliners.landing_target": "Landing Target",
  "message.skyliners.jump_switch.off": "Jump lights off",
  "message.skyliners.jump_switch.red": "Jump lights red: stand by",
  "message.skyliners.jump_switch.green": "Jump lights green: GO!",
  "message.skyliners.parachute_rack.put_on": "You put on a parachute. Jump while falling to open it.",
  "message.skyliners.parachute_rack.handed": "You took a parachute: wear it in your chest slot.",
  "block.skyliners.paper_plane_box": "Paper Plane Box",
  "message.skyliners.paper_plane_box.handed": "A paper plane: throw it from up here and watch it glide",
  "message.skyliners.parachute_rack.wearing": "You are already wearing a parachute.",
  "message.skyliners.decompression": "Door open! Explosive decompression: keep clear of the door, or stay in your seat.",
  "message.skyliners.landing_target.landed": "%s landed %s blocks from the target",
  "message.skyliners.landing_target.record": "%s landed %s blocks from the target: a new best!",
  "message.skyliners.landing_target.best": "Best landing: %s, %s blocks from the middle",
  "message.skyliners.landing_target.none": "No landings on this target yet",
  "message.skyliners.landing_target.plane_landed": "%s's paper plane came down %s blocks from the target",
  "message.skyliners.landing_target.plane_record": "%s's paper plane came down %s blocks from the target: a new best!",
  "message.skyliners.landing_target.best_plane": "Best paper plane: %s, %s blocks from the middle",
  "block.skyliners.handset": "Captain's Handset",
  "block.skyliners.tower_radio": "Tower Radio",
  "message.skyliners.radio.keyed_announcement": "Speak now: your next message is announced to the cabin.",
  "message.skyliners.radio.keyed_radio": "Speak now: your next message goes out over the radio.",
  "message.skyliners.radio.released": "Handset back in its cradle.",
  "title.skyliners.captain": "Captain %s",
  "chat.skyliners.announcement": "[Cabin] Captain %s: %s",
  "chat.skyliners.radio": "[Radio] %s: %s",
  "block.skyliners.signal_flare": "Signal Flare",
  "block.skyliners.drop_bay": "Drop Bay",
  "block.skyliners.drop_release": "Drop Release",
  "container.skyliners.drop_bay": "Drop Bay",
  "message.skyliners.drop_bay.empty": "The drop bay is empty",
  "message.skyliners.drop_bay.away": "%s away! %s left in the bay",
  "message.skyliners.drop_bay.no_bomb": "This version of Ultrasplosive has no bomb to drop from the air",
  "message.skyliners.drop_bay.too_low": "Too low to drop a nuke: climb above %s blocks over the ground first",
  "message.skyliners.drop_release.armed": "Bay doors open: use again to drop, sneak to close",
  "message.skyliners.drop_release.safe": "Bay doors closed",
  "message.skyliners.drop_release.no_bay": "This release is not wired to a drop bay",
  "block.skyliners.galley": "Galley",
  "item.skyliners.trolley": "Trolley",
  "entity.skyliners.trolley": "Trolley",
  "item.skyliners.chicken_meal": "Chicken Meal",
  "item.skyliners.pasta_meal": "Pasta Meal",
  "item.skyliners.pretzels": "Pretzels",
  "item.skyliners.ice_cream": "Ice Cream",
  "item.skyliners.airline_coffee": "Airline Coffee",
  "message.skyliners.trolley.served": "Chicken or pasta? %s for you (%s left on the trolley)",
  "message.skyliners.trolley.empty": "The trolley is empty: restock it at the galley",
  "message.skyliners.galley.rolled_out": "A full trolley, ready to go",
  "message.skyliners.galley.blocked": "No room in front of the galley for a trolley",
  "message.skyliners.galley.restocked": "Trolley restocked",
  "message.skyliners.galley.full": "The trolley is already full",
  "subtitles.skyliners.trolley_squeak": "Trolley squeaks",
  "block.skyliners.jet_bridge": "Jet Bridge",
  "message.skyliners.jet_bridge.docking": "Docking at the airliner door %s blocks out",
  "message.skyliners.jet_bridge.no_door": "No airliner door within %s blocks ahead of the bridge",
  "message.skyliners.jet_bridge.misaligned": "Not lined up with the airliner door %s blocks out: move the bridge %s",
  "message.skyliners.jet_bridge.left": "%s left",
  "message.skyliners.jet_bridge.right": "%s right",
  "message.skyliners.jet_bridge.up": "%s up",
  "message.skyliners.jet_bridge.down": "%s down",
  "message.skyliners.jet_bridge.moving": "Wait for the airliner to stop",
  "message.skyliners.jet_bridge.blocked": "Something stands in the way %s blocks out, short of the airliner door %s blocks out",
  "block.skyliners.check_in_desk": "Check-in Desk",
  "item.skyliners.boarding_pass": "Boarding Pass",
  "item.skyliners.boarding_pass.tooltip": "Hand it to a villager to fly it as a passenger",
  "message.skyliners.check_in.printed": "Boarding pass printed: hand it to a villager",
  "message.skyliners.passengers.off": "Villager passengers are turned off: turn on Villager Passengers in the mod settings",
  "message.skyliners.boarding_pass.boarding": "%s takes the boarding pass and looks for a seat",
  "message.skyliners.boarding_pass.already": "%s already has a boarding pass",
  "message.skyliners.passengers.fare": "%s paid %s emeralds for a flight of %s blocks",
  "block.skyliners.cargo_crate": "Care Package",
  "container.skyliners.cargo_crate": "Care Package",
  "entity.skyliners.falling_crate": "Care Package",
  "message.skyliners.cargo_crate.landed": "A care package has landed",
  "message.skyliners.cargo_crate.landed_near": "A care package has landed %s blocks from the flare",
  "item.skyliners.basketball": "Basketball",
  "entity.skyliners.basketball": "Basketball",
  "block.skyliners.hoop": "Basketball Hoop",
  "block.skyliners.scoreboard": "Scoreboard",
  "block.skyliners.ball_rack": "Ball Rack",
  "team.skyliners.home": "HOME",
  "team.skyliners.guest": "GUEST",
  "message.skyliners.hoop.team": "This hoop now counts for %s",
  "message.skyliners.hoop.basket": "Swish! %s points for %s",
  "message.skyliners.hoop.basket_score": "Swish! %s points for %s: %s",
  "message.skyliners.scoreboard.score": "%s %s : %s %s",
  "message.skyliners.scoreboard.reset": "New game: the scoreboard is back to nil all",
  "subtitles.skyliners.ball_bounce": "Basketball bounces",
  "subtitles.skyliners.basket_swish": "Basket swishes",
  "subtitles.skyliners.scoreboard_buzzer": "Scoreboard buzzes",
  "block.skyliners.window_shades": "Window Shades Control",
  "message.skyliners.window_shades.open": "Window shades going up",
  "message.skyliners.window_shades.closed": "Window shades coming down",
  "subtitles.skyliners.shade_motor": "Window shade whirrs",
  "subtitles.skyliners.lift_door": "Lift doors slide",
  "subtitles.skyliners.lift_start": "Lift sets off",
  "subtitles.skyliners.lift_hum": "Lift hums",
  "subtitles.skyliners.lift_chime_up": "Lift chimes, going up",
  "subtitles.skyliners.lift_chime_down": "Lift chimes, going down",
  "subtitles.skyliners.space_light": "You feel light",
  "subtitles.skyliners.space_heavy": "Your weight comes back",
  "subtitles.skyliners.lift_music": "Lift music",
  "block.skyliners.cabin_lights": "Cabin Lights Dimmer",
  "message.skyliners.cabin_lights.bright": "Cabin lights bright",
  "message.skyliners.cabin_lights.dim": "Cabin lights dimmed for take-off and landing",
  "message.skyliners.cabin_lights.night": "Cabin lights set for the night",
  ...ROOM_DIVIDER_LANG,
  "block.skyliners.light_strip": "LED Light Strip",
  "block.skyliners.pendant_light": "Pendant Light",
  "block.skyliners.halo_light": "Halo Light",
  "block.skyliners.steel_girder": "Steel Girder",
  "block.skyliners.lift_glass": "Lift Glass",
  "block.skyliners.wall_light": "Wall Washer",
  "block.skyliners.floor_light": "Floor Light",
  "block.skyliners.light_cable": "Light Cable",
  "block.skyliners.overhead_bin": "Overhead Locker",
  "container.skyliners.overhead_bin": "Overhead Locker",
  "block.skyliners.passenger_screen": "Passenger Screen",
  "screen.skyliners.passenger_screen.welcome": "WELCOME ABOARD",
  "screen.skyliners.passenger_screen.standby": "ASSEMBLE TO FLY",
  "screen.skyliners.passenger_screen.title": "FLIGHT INFO",
  "screen.skyliners.passenger_screen.map_title": "ROUTE MAP",
  "screen.skyliners.passenger_screen.status.ground": "ON THE GROUND",
  "screen.skyliners.passenger_screen.status.climbing": "CLIMBING",
  "screen.skyliners.passenger_screen.status.cruising": "CRUISING",
  "screen.skyliners.passenger_screen.status.descending": "DESCENDING",
  "screen.skyliners.passenger_screen.altitude": "ALTITUDE",
  "screen.skyliners.passenger_screen.ground_speed": "GROUND SPEED",
  "screen.skyliners.passenger_screen.vertical_speed": "VERTICAL SPEED",
  "screen.skyliners.passenger_screen.heading": "HEADING",
  "screen.skyliners.passenger_screen.height": "ABOVE GROUND",
  "screen.skyliners.passenger_screen.distance": "DISTANCE FLOWN",
  "screen.skyliners.passenger_screen.flight_time": "FLIGHT TIME %s",
  "screen.skyliners.passenger_screen.unit.metres": "m",
  "screen.skyliners.passenger_screen.unit.kilometres": "km",
  "screen.skyliners.passenger_screen.unit.kilometres_per_hour": "km/h",
  "screen.skyliners.passenger_screen.unit.metres_per_second": "m/s",
  ...Object.fromEntries(["N", "NE", "E", "SE", "S", "SW", "W", "NW"].map((point, i) => [`screen.skyliners.passenger_screen.compass.${i}`, point])),
  "screen.skyliners.passenger_screen.cameras": "USE FOR OUTSIDE CAMERAS",
  "screen.skyliners.passenger_screen.camera": "CAM %s  %s",
  "screen.skyliners.passenger_screen.camera.tail": "TAIL",
  "screen.skyliners.passenger_screen.camera.wing": "RIGHT WING",
  "screen.skyliners.passenger_screen.camera.nose": "NOSE GEAR",
  "screen.skyliners.passenger_screen.camera.front": "FRONT",
  "screen.skyliners.passenger_screen.camera.bow": "BOW",
  "screen.skyliners.passenger_screen.camera.below": "BELOW",
  "screen.skyliners.passenger_screen.camera.flyby": "FLYBY",
  "screen.skyliners.passenger_screen.camera.side": "SIDE",
  "screen.skyliners.passenger_screen.camera.leg": "LANDING LEG",
  "screen.skyliners.passenger_screen.camera.orbit": "ORBIT",
  "screen.skyliners.passenger_screen.camera.underside": "UNDERSIDE",
  "screen.skyliners.passenger_screen.camera.prongs": "BETWEEN PRONGS",
  "screen.skyliners.passenger_screen.camera.astern": "ASTERN",
  "screen.skyliners.passenger_screen.camera.circling": "CIRCLING",
  "screen.skyliners.passenger_screen.camera_hint": "%s: next camera    %s: back to the cabin",
  "screen.skyliners.lookout.hint": "%s: drop your flag there    Mouse wheel: zoom    %s: step back",
  "message.skyliners.passenger_screen.not_assembled": "The outside cameras come on once the airliner is assembled",
  "message.skyliners.passenger_screen.no_cameras": "This airliner has no outside cameras: it has no autopilot to fix them to",
  "message.skyliners.passenger_screen.no_camera_here": "There is no outside camera to show here",
  "block.skyliners.seatbelt_sign": "Seatbelt Sign",
  "block.skyliners.seatbelt_switch": "Seatbelt Switch",
  "message.skyliners.seatbelts.on": "Seatbelt signs on",
  "message.skyliners.seatbelts.off": "Seatbelt signs off",
  "message.skyliners.seatbelts.fastened": "Your seatbelt is fastened: stay seated while the signs are on.",
  "subtitles.skyliners.seatbelt_ding": "Seatbelt sign dings",
  "subtitles.skyliners.pa_chime": "Cabin chime",
  "subtitles.skyliners.radio_crackle": "Radio crackles",
  "message.skyliners.unbreakable": "The frame and controls cannot be broken. Sneak holding a wrench to take them apart.",
  "block.skyliners.tail_camera_display": "Tail Camera Display",
  "screen.skyliners.tail_camera_display.no_signal": "NO SIGNAL",
  "screen.skyliners.tail_camera_display.standby": "ASSEMBLE TO FLY",
  "screen.skyliners.tail_camera_display.camera": "CAM %s",
  "block.skyliners.announcement_panel": "Announcement Panel",
  "block.skyliners.flight_map": "Flight Map",
  "message.skyliners.announcement_panel.unlinked": "This panel is not near an airliner's autopilot.",
  "screen.skyliners.announcement_panel.welcome": "Welcome",
  "screen.skyliners.announcement_panel.takeoff": "Take-off",
  "screen.skyliners.announcement_panel.turbulence": "Seatbelts",
  "screen.skyliners.announcement_panel.snacks": "Snacks",
  "screen.skyliners.announcement_panel.window": "Look out",
  "screen.skyliners.announcement_panel.playtime": "Pool & gym",
  "screen.skyliners.announcement_panel.sleep": "Sleep",
  "screen.skyliners.announcement_panel.landing": "Landing",
  "screen.skyliners.announcement_panel.arrival": "Arrival",
  "block.skyliners.autopilot_screen": "Autopilot Screen",
  "block.skyliners.cockpit_display": "Cockpit Display",
  "message.skyliners.autopilot_screen.unlinked": "This screen is not linked to an autopilot.",
  "screen.skyliners.autopilot.engage": "AP %s",
  "screen.skyliners.autopilot.on": "ON",
  "screen.skyliners.autopilot.off": "OFF",
  "screen.skyliners.autopilot.heading_left": "◄ HDG",
  "screen.skyliners.autopilot.heading_right": "HDG ►",
  "screen.skyliners.autopilot.orbit_left": "◄ CIRCLE",
  "screen.skyliners.autopilot.orbit_right": "CIRCLE ►",
  "screen.skyliners.autopilot.altitude_down": "ALT ▼",
  "screen.skyliners.autopilot.altitude_up": "ALT ▲",
  "screen.skyliners.autopilot.speed_down": "SPD ▼",
  "screen.skyliners.autopilot.speed_up": "SPD ▲",
  "screen.skyliners.autopilot.radius_down": "SIZE ▼",
  "screen.skyliners.autopilot.radius_up": "SIZE ▲",
  "screen.skyliners.autopilot.standby": "ASSEMBLE TO FLY",
  "screen.skyliners.autopilot.disengaged": "AUTOPILOT OFF",
  "screen.skyliners.autopilot.mode": "%s  %s",
  "screen.skyliners.autopilot.mode.heading": "HDG %s",
  "screen.skyliners.autopilot.mode.orbit_left": "CIRCLE L %s",
  "screen.skyliners.autopilot.mode.orbit_right": "CIRCLE R %s",
  "screen.skyliners.autopilot.mode.destination": "PIN %s",
  "screen.skyliners.autopilot.mode.rocket_pin": "TO PIN",
  "screen.skyliners.autopilot.mode.rocket_back": "RETURN",
  "screen.skyliners.autopilot.targets": "ALT %s  SPD %s",
  "screen.skyliners.autopilot.targets.land": "TOUCHDOWN  SPD %s",
  "screen.skyliners.autopilot.targets.rollout": "BRAKING",
  "screen.skyliners.autopilot.now": "SPD %s  ALT %s  HDG %s",
  "screen.skyliners.autopilot.range": "%s blocks",
  "screen.skyliners.pilot_hud.off": "AP OFF",
  "screen.skyliners.autopilot_settings.title": "Autopilot",
  "screen.skyliners.autopilot_settings.now": "Flying",
  "screen.skyliners.autopilot_settings.take_off_hint": "Takes off, then climbs to fly the settings below.",
  "screen.skyliners.autopilot_settings.engage_hint": "Engaging flies the settings below.",
  "screen.skyliners.autopilot_settings.assemble_hint": "Takes off once it has been assembled into one flying body.",
  "screen.skyliners.autopilot_settings.assembling": "Assembling...",
  "screen.skyliners.autopilot_settings.mode.destination": "Fly to Pin",
  "screen.skyliners.autopilot_settings.turn.tight_left": "Tight Circle Left",
  "screen.skyliners.autopilot_settings.turn.gentle_left": "Gentle Circle Left",
  "screen.skyliners.autopilot_settings.turn.straight": "Straight On",
  "screen.skyliners.autopilot_settings.turn.gentle_right": "Gentle Circle Right",
  "screen.skyliners.autopilot_settings.turn.tight_right": "Tight Circle Right",
  "screen.skyliners.autopilot_settings.land_at_pin.on": "Land at the Pin: on, landing near it clear of buildings",
  "screen.skyliners.autopilot_settings.land_at_pin.off": "Land at the Pin: off, circling it",
  "screen.skyliners.autopilot_settings.map": "Map: drag to look about, scroll to zoom, click to drop the pin",
  "screen.skyliners.autopilot_settings.map.middle": "X %s  Z %s",
  "screen.skyliners.autopilot_settings.map.pin": "Pin X %s  Z %s",
  "screen.skyliners.autopilot_settings.heading": "Heading",
  "screen.skyliners.autopilot_settings.altitude": "Altitude",
  "screen.skyliners.autopilot_settings.speed": "Speed",
  "screen.skyliners.autopilot_settings.range.heading": "%s–%s°",
  "screen.skyliners.autopilot_settings.range.altitude": "Y %s–%s",
  "screen.skyliners.autopilot_settings.range.speed": "%s–%s b/s",
  "screen.skyliners.autopilot_settings.take_off": "Take Off",
  "screen.skyliners.autopilot_settings.engage": "Engage",
  "screen.skyliners.autopilot_settings.apply": "Apply",
  "screen.skyliners.autopilot_settings.disengage": "Disengage",
  "screen.skyliners.autopilot_settings.land": "Land",
  "screen.skyliners.autopilot_settings.unstuck": "Unstuck",
  "skyliners.configuration.autopilot": "Autopilot",
  "skyliners.configuration.climbHeight": "Climb Height",
  "skyliners.configuration.rotateSpeed": "Rotate Speed",
  "skyliners.configuration.cruiseSpeed": "Cruise Speed",
  "skyliners.configuration.controlSurfaces": "Control Surfaces",
  "skyliners.configuration.radio": "Radio",
  "skyliners.configuration.atcChatter": "Air Traffic Control",
  "skyliners.configuration.atcVolume": "Air Traffic Control Volume",
  "skyliners.configuration.cameraShake": "Camera Shake",
  "skyliners.configuration.tailCameraFeed": "Tail Camera Picture",
  "skyliners.configuration.tailCameraFps": "Tail Camera Pictures a Second",
  "skyliners.configuration.tailCameraRenderDistance": "Tail Camera Render Distance",
  "skyliners.configuration.tailCameraResolution": "Tail Camera Picture Height",
  "skyliners.configuration.pilotHud": "Pilot HUD",
  "skyliners.configuration.pilotHudScale": "Pilot HUD Size",
  "skyliners.configuration.nightLights": "Blue Night Lights",
  "skyliners.configuration.discoLights": "Disco Ball Spots",
  "skyliners.configuration.cloudlessCabin": "Cloudless Cabin",
  "skyliners.configuration.fairOverClouds": "Fair Weather Over the Clouds",
  "skyliners.configuration.spaceSky": "Space Sky",
  "skyliners.configuration.shootingStars": "Shooting Stars",
  "skyliners.configuration.heightGauge": "Height Gauge",
  "skyliners.configuration.helmetBreathing": "Helmet Breathing",
  "skyliners.configuration.roomAmbience": "Room Ambience",
  "skyliners.configuration.aurora": "Aurora",
  "skyliners.configuration.burnerSmoke": "Burner Smoke",
  "commands.skyliners.build": "Building an airliner of %s blocks, %s of them over blocks that were in the way",
  "message.skyliners.autopilot.coming_down": "Coming down here, straight down to the ground",
  "message.skyliners.autopilot.assembling_zeppelin": "Assembling the zeppelin: the autopilot takes it from here.",
  "message.skyliners.autopilot.assembling_rocket": "Assembling the rocket: the countdown begins as soon as it is ready.",
  "message.skyliners.autopilot.no_engines": "This rocket has no engines to fly it with: build it again with Create Propulsion installed.",
  "message.skyliners.autopilot.coming_down_rocket": "Coming down here, catching ourselves on the engines",
  "message.skyliners.autopilot.coming_down_freighter": "Coming straight down here on her thrusters",
  "message.skyliners.autopilot.no_ground_freighter": "No clear, level ground within 64 blocks of the pin: holding over it. Set another pin, or tell her to land where she is.",
  "message.skyliners.autopilot.assembling_freighter": "Assembling the freighter: she lifts off as soon as she is ready.",
  "message.skyliners.autopilot.no_thrusters": "This freighter has no thrusters to fly her with: build her again with Create Propulsion installed.",
  "message.skyliners.autopilot.off_once_down_freighter": "Autopilot off once she is down: only her thrusters hold her up, so she comes straight down here first and switches off on her legs.",
  "message.skyliners.autopilot.off_once_down_rocket": "Autopilot off once the rocket is down: only her engines hold her up, so she comes down here first and switches off on her fins.",
  "message.skyliners.autopilot.no_burners": "This zeppelin has no burners to lift it: build it again with Create Aeronautics installed.",
  "message.skyliners.autopilot.too_heavy": "The zeppelin is too heavy for its burners to lift, and stays on the ground.",
  "commands.skyliners.build_zeppelin": "Building a zeppelin of %s blocks, %s of them over blocks that were in the way",
  "commands.skyliners.build_rocket": "Building a rocket of %s blocks, %s of them over blocks that were in the way",
  "item.skyliners.airliner_blueprint": "Airliner Blueprint",
  "item.skyliners.furnished_airliner_blueprint": "Furnished Airliner Blueprint",
  "item.skyliners.airliner_blueprint.tooltip": "A double-deck airliner with an empty cabin, ready to build in",
  "item.skyliners.furnished_airliner_blueprint.tooltip": "A double-deck airliner fitted with a pool, a gym hall and a lounge",
  "item.skyliners.zeppelin_blueprint": "Zeppelin Blueprint",
  "item.skyliners.rocket_blueprint": "Rocket Blueprint",
  "item.skyliners.rocket_blueprint.tooltip": "A rocket as tall as the airliner is long, flown to a pin on the map and landed on its fins",
  "message.skyliners.rocket_building": "Building the rocket… %s%%",
  "message.skyliners.rocket_built": "The rocket is ready. Use the autopilot in its cockpit, at the top, to fly it.",
  "message.skyliners.rocket_too_high": "The rocket would reach past the build height here; build it lower down.",
  "item.skyliners.freighter_blueprint": "Freighter Blueprint",
  "item.skyliners.freighter_blueprint.tooltip": "A battered saucer freighter on her legs, one deck round a core, flown on her thrusters",
  "message.skyliners.freighter_building": "Building the freighter… %s%%",
  "message.skyliners.freighter_built": "The freighter is ready. Use the autopilot in her cockpit, out at her front right, to fly her.",
  "message.skyliners.freighter_too_high": "The freighter would reach past the build height here; build her lower down.",
  "commands.skyliners.build_freighter": "Building a freighter of %s blocks, %s of them over blocks that were in the way",
  "block.skyliners.padded_bulkhead": "Padded Bulkhead",
  "block.skyliners.engine_grille": "Engine Grille",
  "block.skyliners.deck_robot": "Deck Robot",
  "block.skyliners.ramp_deck": "Boarding Ramp",
  "block.skyliners.ramp_panel": "Boarding Ramp Panel",
  "block.skyliners.ramp_button": "Ramp Call Button",
  "item.skyliners.laser_crossbow": "Laser Crossbow",
  "block.skyliners.laser_cannon": "Laser Cannon",
  "block.skyliners.bandstand": "Bandstand",
  "message.skyliners.freighter.takeoff_ramp": "Her take-off waits for everyone to step off the ramp",
  "message.skyliners.freighter.takeoff_called_off": "Take-off called off: she was held on the ground too long",
  "item.skyliners.zeppelin_blueprint.tooltip": "A Hindenburg-sized zeppelin, 490 blocks long, lifted by sixteen gas cells of hot air",
  "item.skyliners.furnished_zeppelin_blueprint": "Furnished Zeppelin Blueprint",
  "item.skyliners.furnished_zeppelin_blueprint.tooltip": "The same zeppelin with cabins, a galley, a dining room, a lounge and its promenades furnished",
  "item.skyliners.festive_zeppelin_blueprint": "Festive Zeppelin Blueprint",
  "item.skyliners.festive_zeppelin_blueprint.tooltip": "The furnished zeppelin dressed for Christmas: snow, red bands, green fins, fairy lights, a wreath, a star and sleigh bells",
  "item.skyliners.half_zeppelin_blueprint": "Half-Size Zeppelin Blueprint",
  "item.skyliners.half_zeppelin_blueprint.tooltip": "A zeppelin half the Hindenburg's size, 245 blocks long, its control car and decks as big as ever",
  "item.skyliners.half_furnished_zeppelin_blueprint": "Half-Size Furnished Zeppelin Blueprint",
  "item.skyliners.half_furnished_zeppelin_blueprint.tooltip": "The half-size zeppelin with its passenger decks furnished",
  "item.skyliners.half_festive_zeppelin_blueprint": "Half-Size Festive Zeppelin Blueprint",
  "item.skyliners.half_festive_zeppelin_blueprint.tooltip": "The half-size furnished zeppelin dressed for Christmas",
  "item.skyliners.airliner_blueprint.usage": "Use on the ground: the tail stretches away the way you face. Sneak to build through anything in the way.",
  "item.skyliners.rocket_blueprint.on_pad": "Use on a launch pad: she stands in its very middle, her door to its front.",
  "item.skyliners.launch_pad_blueprint": "Launch Pad Blueprint",
  "item.skyliners.launch_pad_blueprint.tooltip": "A concrete pad for the rocket, with a flame pit, a service tower and floodlights",
  "item.skyliners.launch_pad_blueprint.usage": "Use on the ground: the pad's front edge goes just past where you click, its walkway toward you. Sneak to build through anything in the way.",
  "message.skyliners.launch_pad_building": "Building the launch pad… %s%%",
  "message.skyliners.launch_pad_built": "The launch pad is ready. Use the Rocket Blueprint anywhere on it: she stands in its very middle.",
  "message.skyliners.launch_pad_too_high": "The launch pad's tower would reach past the build height here; build it lower down.",
  "commands.skyliners.build_launch_pad": "Building a launch pad of %s blocks, %s of them over blocks that were in the way",
  "block.skyliners.launch_pad_grate": "Launch Pad Grate",
  "block.skyliners.launch_pad_hub": "Launch Pad Hub",
  "item.skyliners.control_tower_blueprint": "Control Tower Blueprint",
  "item.skyliners.control_tower_blueprint.tooltip": "An airfield's control tower: a glass cab with a radio, a radar and a transponder",
  "item.skyliners.control_tower_blueprint.usage": "Use on the ground: the tower goes up ahead of you, its door toward you. Sneak to build through anything in the way.",
  "message.skyliners.control_tower.built": "The control tower is up. Take the lift or the stairs up to the cab: its transponder shows it on every autopilot's map.",
  "item.skyliners.runway_blueprint": "Runway Blueprint",
  "item.skyliners.runway_blueprint.tooltip": "A lit runway for the airliner, twice as long as it needs to take off",
  "item.skyliners.runway_blueprint.usage": "Use on the ground: the runway runs away the way you face, replacing whatever is there.",
  "message.skyliners.runway.building": "Laying the runway: %s%%",
  "message.skyliners.runway.busy": "A runway is already being laid; wait for it to finish.",
  "message.skyliners.runway.built": "The runway is down: %s blocks long and %s wide. To line an airliner up on it, use its blueprint at the far end, looking back down the runway.",
  "block.skyliners.transponder": "Transponder",
  "block.skyliners.tower_radar": "Tower Radar",
  "block.skyliners.motion_sensor": "Motion Sensor",
  "block.skyliners.lift_panel": "Lift Panel",
  "screen.skyliners.lift_panel.none": "NO LIFT",
  "screen.skyliners.radar.title": "Tower Radar",
  "screen.skyliners.radar.map": "Radar map",
  "screen.skyliners.map.contact.where": "X %s, Z %s",
  "screen.skyliners.map.lookout_flag": "Spotted by %s",
  "screen.skyliners.map.contact.distance": "%s blocks away",
  "screen.skyliners.map.contact.flight": "Height %s, speed %s blocks a second",
  "marker.skyliners.icon.tower": "Control tower",
  "marker.skyliners.icon.airliner": "Airliner",
  "marker.skyliners.icon.house": "Home",
  "marker.skyliners.icon.star": "Star",
  "marker.skyliners.icon.flag": "Flag",
  "marker.skyliners.icon.heart": "Heart",
  "marker.skyliners.icon.gem": "Treasure",
  "marker.skyliners.icon.tree": "Forest",
  "marker.skyliners.icon.anchor": "Harbour",
  "marker.skyliners.icon.pickaxe": "Mine",
  "marker.skyliners.icon.airship": "Airship",
  "marker.skyliners.colour.white": "White",
  "marker.skyliners.colour.red": "Red",
  "marker.skyliners.colour.orange": "Orange",
  "marker.skyliners.colour.yellow": "Yellow",
  "marker.skyliners.colour.lime": "Lime",
  "marker.skyliners.colour.green": "Green",
  "marker.skyliners.colour.cyan": "Cyan",
  "marker.skyliners.colour.blue": "Blue",
  "marker.skyliners.colour.purple": "Purple",
  "marker.skyliners.colour.magenta": "Magenta",
  "marker.skyliners.colour.pink": "Pink",
  "marker.skyliners.colour.brown": "Brown",
  "message.skyliners.building": "Building the airliner… %s%%",
  "message.skyliners.built": "The airliner is ready and glued together. Fold the airstairs away, then use the Physics Assembler in the cockpit to fly it.",
  "message.skyliners.busy": "An airliner or a zeppelin is already being built here; wait for it to finish.",
  "message.skyliners.too_high": "The airliner would reach past the build height here; build it lower down.",
  "message.skyliners.blocked": "Not enough room: %s blocks are in the way, the first at %s %s %s. Sneak to build anyway.",
  "skyliners.configuration.airframe": "Airframe",
  "skyliners.configuration.scale": "Scale",
  "skyliners.configuration.blocksPerTick": "Blocks per Tick",
  "skyliners.configuration.shellStyle": "Shell Style",
  "skyliners.configuration.flight": "Flight",
  "skyliners.configuration.wingBlock": "Wing Block",
  "skyliners.configuration.engineBlock": "Engine Block",
  "skyliners.configuration.thrustSetting": "Thrust Setting",
  "skyliners.configuration.assemblerBlock": "Assembler Block",
  "skyliners.configuration.wheelMountBlock": "Wheel Mount Block",
  "skyliners.configuration.tireItem": "Tire Item",
  "skyliners.configuration.landAhead": "Land Ahead",
  "skyliners.configuration.landAheadChunks": "Land Ahead Distance",
  "skyliners.configuration.cabin": "Cabin",
  "skyliners.configuration.curtainsPart": "Curtains Part",
  "skyliners.configuration.turbulence": "Turbulence",
  "skyliners.configuration.decompression": "Decompression",
  "skyliners.configuration.villagerPassengers": "Villager Passengers",
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
  ...rocketControlLang(),
  ...hullLiningLang(),
  ...rocketFinishLang(),
  ...cabinSeatLang(),
  ...cabinCarpetLang(),
  ...gymFloorLang(),
  ...lavatoryBasinLang(),
  ...bulkheadSignLang(),
  ...wcSignLang(),
  ...denFurnitureLang(),
  ...airstairButtonLang(),
  ...doorButtonLang(),
  ...rocketStairsLang(),
  ...cargoNetLang(),
  ...cargoFloorLang(),
  ...uldContainerLang(),
  ...poolLang(),
  ...gymHallLang(),
  ...seatConsoleLang(),
  ...galleyUnitLang(),
  ...planterLang(),
  ...barLang(),
  ...robotBartenderLang(),
  ...dutyFreeKioskLang(),
  ...laserPointerLang(),
  ...towerInstrumentsLang(),
  ...zeppelinLang(),
  ...zeppelinControlsLang(),
  ...ledStripLang(),
  ...windowDimmerLang(),
  ...shipsColoursLang(),
  ...christeningLang(),
  ...flankScreenLang(),
  ...spacePosterLang(),
  ...shaftDecorLang(),
  ...snackMachineLang(),
  ...roverLang(),
  ...labLang(),
  ...domeLang(),
  ...foodLabLang(),
  ...roomLang(),
  ...greenhouseLang(),
  ...rocketRoomLang(),
  ...airlockPanelLang(),
  ...airlockDoorLang(),
  ...parcelScannerLang(),
  ...galleyFridgeLang(),
  ...waveMachineLang(),
  ...fogMachineLang(),
  ...clubBarLang(),
  ...girderDecorLang(),
  ...hullFittingLang(),
  ...hullPlateLang(),
  ...hullLightLang(),
  ...Object.fromEntries(FIRST_FLIGHT.flatMap(([name, , , , title, description]) => [
    [`advancements.${ID}.first_flight.${name}.title`, title],
    [`advancements.${ID}.first_flight.${name}.description`, description],
  ])),
});
console.log("ok");
