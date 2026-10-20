package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The airstairs' memory and motor. It knows the flight as a list of stages, from the landing at the door down to the
 * bottom step, each a few blocks placed relative to itself, so the mechanism keeps working wherever the airliner
 * stands or flies. Folding takes the stages away from the bottom up; unfolding lays them out again from the door
 * down, one every few ticks, and stops early where a step meets the ground. The airliner's airstairs also work the
 * door they serve, opening it as they unfold and shutting it as they fold. The jet bridge runs on it too, laying out
 * a tunnel it plans afresh each time (see {@link JetBridgeBlockEntity}).
 */
public class AirstairControllerBlockEntity extends BlockEntity {
    /** Ticks between stages: quick enough to watch, slow enough to see each step fold. */
    private static final int STAGE_TICKS = 3;
    /** While someone stands on the stairs, how often folding looks again whether they have stepped off. */
    private static final int CLEAR_CHECK_TICKS = 10;

    /** One block of the stairs: where it goes relative to the controller, and what it is. */
    public record Step(BlockPos offset, BlockState state) {
    }

    private List<List<Step>> stages = List.of();
    /** How many stages are out right now, counted from the door. */
    private int out;
    private boolean extending;
    /** Unfolding met the ground (or something else) before the last stage, and stopped there. */
    private boolean grounded;
    /** Folding is held up by someone on the stairs; the chime has sounded. */
    private boolean waiting;
    private int cooldown;
    /** Where the door the stairs serve is, from the controller: its bottom left part; null for none. */
    @Nullable
    private BlockPos door;

    public AirstairControllerBlockEntity(BlockPos pos, BlockState state) {
        this(AerolargeRegistry.AIRSTAIR_CONTROLLER_ENTITY.get(), pos, state);
    }

    protected AirstairControllerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /** Hands the controller the stairs it was built with, already standing, and where the door they serve is from it. */
    public void configure(List<List<Step>> stages, BlockPos door) {
        this.stages = stages;
        this.out = stages.size();
        this.extending = true;
        this.door = door;
        setChanged();
    }

    /** Lays out a new flight to unfold, in place of the old one, which must be all folded away. */
    protected void replan(List<List<Step>> stages) {
        this.stages = stages;
        setChanged();
    }

    /** Whether the stairs are out or coming out. */
    protected boolean unfolding() {
        return extending;
    }

    /** Whether the stairs are all out, down to the ground or as far as they go. */
    protected boolean extended() {
        return extending && (grounded || out == stages.size());
    }

    /** Whether the stairs are all folded away and staying that way. */
    protected boolean folded() {
        return !extending && out == 0;
    }

    /** Folds the stairs away at once, as when the controller itself is broken. */
    protected void foldAway() {
        if (level != null) {
            while (out > 0) {
                takeAway(level, stages.get(--out));
            }
        }
        extending = false;
        setChanged();
    }

    /** Folds the stairs away if they are out or coming out, otherwise unfolds them; the door they serve goes with them. */
    public void toggle() {
        extending = !extending;
        grounded = false;
        waiting = false;
        cooldown = 0;
        setChanged();
        if (level != null) {
            level.playSound(null, worldPosition, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, extending ? 0.8F : 1.1F);
            PlugDoorBlockEntity served = door == null ? null : PlugDoorBlockEntity.of(level, worldPosition.offset(door));
            if (served != null) {
                served.set(extending);
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AirstairControllerBlockEntity controller) {
        if (level instanceof ServerLevel serverLevel) {
            controller.tick(serverLevel);
        }
    }

    protected void tick(ServerLevel level) {
        boolean moving = extending ? !grounded && out < stages.size() : out > 0;
        if (!moving || --cooldown > 0) {
            return;
        }
        cooldown = STAGE_TICKS;
        if (extending) {
            List<Step> stage = stages.get(out);
            for (Step step : stage) {
                BlockState there = level.getBlockState(worldPosition.offset(step.offset()));
                if (!there.isAir() && !there.canBeReplaced()) {
                    // Down on the ground (or against something else): the stairs end here.
                    grounded = true;
                    setChanged();
                    return;
                }
            }
            for (Step step : stage) {
                level.setBlock(worldPosition.offset(step.offset()), step.state(), Block.UPDATE_ALL);
            }
            out++;
            effects(level, stage, true);
        } else {
            if (occupied(level)) {
                // Nobody gets folded up with the stairs: wait for them to step off, chiming once to say so.
                if (!waiting) {
                    waiting = true;
                    level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1.0F, 0.7F);
                }
                cooldown = CLEAR_CHECK_TICKS;
                return;
            }
            waiting = false;
            out--;
            List<Step> stage = stages.get(out);
            takeAway(level, stage);
            effects(level, stage, false);
        }
        setChanged();
    }

    private void takeAway(Level level, List<Step> stage) {
        for (Step step : stage) {
            BlockPos at = worldPosition.offset(step.offset());
            // Only what the stairs put there: a block someone built in their place stays.
            if (level.getBlockState(at).is(step.state().getBlock())) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /** Whether anyone stands on, or is passing through, any part of the stairs that is out. */
    private boolean occupied(ServerLevel level) {
        List<AABB> spots = new ArrayList<>();
        for (int i = 0; i < out; i++) {
            for (Step step : stages.get(i)) {
                spots.add(new AABB(worldPosition.offset(step.offset())).expandTowards(0, 2, 0));
            }
        }
        return anyoneIn(level, spots);
    }

    /**
     * Whether anyone alive is in any of the boxes, given among the controller's own blocks: where it stands in the
     * world, or aboard an assembled airliner, where everyone is seen in the airliner's frame.
     */
    protected boolean anyoneIn(ServerLevel level, List<AABB> spots) {
        if (spots.isEmpty()) {
            return false;
        }
        AABB all = spots.getFirst();
        for (AABB spot : spots) {
            all = all.minmax(spot);
        }
        // However the airliner is turned, the boxes lie within half their diagonal of their middle.
        Vec3 middle = SableFlight.toWorld(level, all.getCenter());
        double reach = Math.sqrt(all.getXsize() * all.getXsize() + all.getYsize() * all.getYsize() + all.getZsize() * all.getZsize()) / 2 + 2;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(middle, middle).inflate(reach), LivingEntity::isAlive)) {
            Vec3 centre = SableFlight.toPlot(level, worldPosition, entity.getBoundingBox().getCenter(), false);
            AABB box = AABB.ofSize(centre, entity.getBbWidth(), entity.getBbHeight(), entity.getBbWidth());
            for (AABB spot : spots) {
                if (spot.intersects(box)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void effects(ServerLevel level, List<Step> stage, boolean unfolding) {
        BlockPos at = worldPosition.offset(stage.getFirst().offset());
        level.playSound(null, at, unfolding ? SoundEvents.PISTON_EXTEND : SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, 1.2F + level.random.nextFloat() * 0.2F);
        for (Step step : stage) {
            BlockPos cell = worldPosition.offset(step.offset());
            level.sendParticles(ParticleTypes.SMOKE, cell.getX() + 0.5, cell.getY() + 0.6, cell.getZ() + 0.5, 3, 0.25, 0.1, 0.25, 0.01);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag stageList = new ListTag();
        for (List<Step> stage : stages) {
            ListTag steps = new ListTag();
            for (Step step : stage) {
                CompoundTag entry = new CompoundTag();
                entry.put("Offset", NbtUtils.writeBlockPos(step.offset()));
                entry.put("State", NbtUtils.writeBlockState(step.state()));
                steps.add(entry);
            }
            stageList.add(steps);
        }
        tag.put("Stages", stageList);
        tag.putInt("Out", out);
        tag.putBoolean("Extending", extending);
        tag.putBoolean("Grounded", grounded);
        if (door != null) {
            tag.put("Door", NbtUtils.writeBlockPos(door));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        List<List<Step>> loaded = new ArrayList<>();
        for (Tag stageTag : tag.getList("Stages", Tag.TAG_LIST)) {
            List<Step> stage = new ArrayList<>();
            for (Tag stepTag : (ListTag) stageTag) {
                CompoundTag entry = (CompoundTag) stepTag;
                BlockPos offset = NbtUtils.readBlockPos(entry, "Offset").orElse(BlockPos.ZERO);
                BlockState state = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), entry.getCompound("State"));
                stage.add(new Step(offset, state));
            }
            loaded.add(List.copyOf(stage));
        }
        stages = List.copyOf(loaded);
        out = Math.min(tag.getInt("Out"), stages.size());
        extending = tag.getBoolean("Extending");
        grounded = tag.getBoolean("Grounded");
        door = NbtUtils.readBlockPos(tag, "Door").orElse(null);
    }
}
