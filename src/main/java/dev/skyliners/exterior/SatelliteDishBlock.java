package dev.skyliners.exterior;

import dev.skyliners.block.Mount;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;

/**
 * A satellite dish, a white bowl with its feed horn held out over it on two struts, a tiny amber light at its tip,
 * turning slowly as if tracking something far off (see the client's {@code SatelliteDishRenderer}); the big one's
 * dish three times as wide, nearly four blocks across. Its mount is its block's own model, a shape of its own for each
 * way it is put up (see {@link HullFittingBlock}): on a floor a post on a foot plate, the dish turning round on top of
 * it, tipped up at the sky; on a wall a plate and an arm out from it, the dish at its end looking out from the wall and
 * sweeping from side to side; and under a ceiling a hanger, the dish hung from it, tipped down at the ground, turning
 * round. PART is what the state looks like: only its mount is ever placed, its dish drawn over it.
 *
 * <p>Its dish is solid as drawn, out past its block, turning with it (see {@link DishShape}): it goes by the game's
 * time, which the server and every client keep alike, so every player sees each dish turned the same way and bumps into
 * it where it is drawn, give or take the tick or two a client's drawing clock eases its time by.
 */
public class SatelliteDishBlock extends HullFittingBlock implements EntityBlock {
    /** Which of its parts the state looks like: its mount, placed, or its dish, drawn over the mount. */
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    /** How long a dish on a floor or under a ceiling takes to turn round once, in ticks: slow, tracking. */
    private static final double TURN_TICKS = 600;
    /** How long a dish on a wall takes to sweep to one side and back, in ticks. */
    private static final double SWEEP_TICKS = 360;
    /** How far a dish on a wall is tipped up from looking straight out from it, in degrees. */
    private static final float WALL_TILT = 20;
    /** Where in the dish's own model it is held: the back of its hub, in pixels (see tools/generate_resources.mjs). */
    private static final float[] HELD_AT = {8, 8, 12};
    /** A large odd number spreading the dishes' places in their turn, so neighbours are out of step. */
    private static final long SPREAD = 0x9E3779B97F4A7C15L;
    /**
     * How many degrees apart the turns are that its shape is worked out for, each kept once worked out: its bowl's rim
     * is never further than a few hundredths of a block from where it is drawn.
     */
    private static final int SHAPE_STEP = 2;
    /** Where its mounts hold its dish, and how big it is drawn. */
    private final Dish dish;
    /** Its mount and its dish to collide with, kept by how it is put up, the way it faces and its turn in steps. */
    private final Map<Integer, VoxelShape> shapesByTurn = new ConcurrentHashMap<>();

    /**
     * Where a dish's mounts hold it, on a floor, on a wall and under a ceiling, in pixels from the block's corner as
     * drawn facing north (see tools/generate_resources.mjs); how many times its model's size the dish is drawn; how far
     * it is tipped up at the sky on a floor, and down at the ground under a ceiling, in degrees; and how far either way
     * it sweeps on a wall, in degrees: a big dish less, so it never swings into its wall.
     */
    public record Dish(float[] floor, float[] wall, float[] ceiling, float scale, float tilt, float sweep) {
    }

    /** Which part of the dish a state draws. */
    public enum Part implements StringRepresentable {
        MOUNT,
        DISH;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** A dish on mounts of the outline given, held and drawn as given. */
    public SatelliteDishBlock(Properties properties, Outline outline, Dish dish) {
        super(properties, outline);
        this.dish = dish;
        registerDefaultState(defaultBlockState().setValue(PART, Part.MOUNT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(PART);
    }

    /** Where its mounts hold its dish and how it is drawn. */
    public Dish dish() {
        return dish;
    }

    /** Where its mount holds its dish, put up as given, in pixels from the block's corner as drawn facing north. */
    public float[] pivot(Mount mount) {
        return switch (mount) {
            case FLOOR -> dish.floor();
            case WALL -> dish.wall();
            case CEILING -> dish.ceiling();
        };
    }

    /**
     * The time a dish at a block keeps, in ticks: the game's time, each dish a little out of step with any other by
     * where it is, so a ship's dishes never turn as one.
     */
    public static double time(BlockPos pos, double gameTime) {
        return gameTime + Math.floorMod(pos.asLong() * SPREAD, (long) TURN_TICKS);
    }

    /**
     * How far round a dish put up as given is turned at its time, in degrees: on a floor or under a ceiling round and
     * round, on a wall sweeping either way.
     */
    public double turn(Mount mount, double time) {
        if (mount == Mount.WALL) {
            return dish.sweep() * Math.sin(2 * Math.PI * (time % SWEEP_TICKS) / SWEEP_TICKS);
        }
        return 360 * (time % TURN_TICKS) / TURN_TICKS;
    }

    /**
     * Where its dish is held, put up and facing as given and turned so far, in degrees: from its model's pixels over
     * sixteen to its block's own blocks, turned about where its mount holds it, tipped up at the sky on a floor, up a
     * little out from a wall and down at the ground under a ceiling, and drawn as big as it is. Its renderer draws it
     * so, and it is solid so.
     */
    public Matrix4f pose(Mount mount, Direction facing, double turn) {
        float[] pivot = pivot(mount);
        float tilt = mount == Mount.FLOOR ? dish.tilt() : mount == Mount.WALL ? WALL_TILT : -dish.tilt();
        return new Matrix4f()
            .translate(0.5F, 0, 0.5F).rotateY((float) Math.toRadians(180 - facing.toYRot())).translate(-0.5F, 0, -0.5F)
            .translate(pivot[0] / 16, pivot[1] / 16, pivot[2] / 16)
            .rotateY((float) Math.toRadians(turn)).rotateX((float) Math.toRadians(tilt))
            .scale(dish.scale())
            .translate(-HELD_AT[0] / 16, -HELD_AT[1] / 16, -HELD_AT[2] / 16);
    }

    /**
     * Its mount, and its dish where it is turned now, so nobody walks through it; its shape changing as it turns, it is
     * never kept by its state (see {@code dynamicShape}). Its mount alone where the game's time cannot be read, and at
     * the world's origin, where Sable asks for a ship's blocks' shapes by their state alone, as it weighs them.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape mount = getShape(state, level, pos, context);
        if (state.getValue(PART) != Part.MOUNT || pos.equals(BlockPos.ZERO) || !(level instanceof Level world)) {
            return mount;
        }
        Mount put = state.getValue(MOUNT);
        Direction facing = state.getValue(FACING);
        int steps = 360 / SHAPE_STEP;
        int step = Math.floorMod((int) Math.round(turn(put, time(pos, world.getGameTime())) / SHAPE_STEP), steps);
        int key = (put.ordinal() * 4 + facing.get2DDataValue()) * steps + step;
        return shapesByTurn.computeIfAbsent(key, ignored -> Shapes.or(mount, DishShape.of(pose(put, facing, step * SHAPE_STEP), dish.scale())));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SatelliteDishBlockEntity(pos, state);
    }
}
