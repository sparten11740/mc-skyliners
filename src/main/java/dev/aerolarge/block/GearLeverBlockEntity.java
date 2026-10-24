package dev.aerolarge.block;

import dev.aerolarge.AerolargeConfig;
import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.compat.SableFlight;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the landing gear lever works: every leg of the retractable gear (see {@link GearBayBlockEntity}), as offsets
 * from the lever. The legs follow the lever: thrown up, each goes up as soon as it may (never with the airliner standing
 * on its wheels: then the lever bumps back down with a buzz and its lamps flash red); thrown down, each comes down. The
 * lamps show where the legs are, and coming down below {@link #HORN_HEIGHT} with the gear not down the gear horn
 * sounds on the flight deck and the lamps flash red, as on an airliner, though nothing stops a pilot landing on the
 * belly. The autopilot throws it too, up once it climbs away and down to land (see {@code AutopilotBlockEntity}).
 */
public class GearLeverBlockEntity extends BlockEntity {
    /** How often the lever looks at the legs, to light its lamps and sound the horn, in ticks: the horn's beat. */
    private static final int LOOK_TICKS = 10;
    /** Below this height of the wheels over the ground, in blocks, coming down with the gear not down sounds the horn. */
    private static final double HORN_HEIGHT = 30;
    /** Sinking faster than this, in blocks a second, is coming down. */
    private static final double HORN_SINK = 1.0;
    /** How long the lamps flash red after the lever was refused, in ticks. */
    private static final int REFUSED_TICKS = 30;
    private static final float LEVER_VOLUME = 0.8F;
    private static final float HORN_VOLUME = 1.0F;

    private List<BlockPos> bays = List.of();
    /** Whether its links have been looked at since it was loaded, to mend them (see {@link Turning#mend}). */
    private boolean linksChecked;
    /** How much longer the lamps flash red after the lever was refused, in ticks. */
    private int refused;
    /** Which half of a flash the lamps are in: lit or dark. */
    private boolean flashLit;

    public GearLeverBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.GEAR_LEVER_ENTITY.get(), pos, state);
    }

    /** Wired when the airliner is built: the legs it works, as offsets from it. */
    public void configure(List<BlockPos> bays) {
        this.bays = List.copyOf(bays);
        linksChecked = true;
        setChanged();
    }

    /** Whether the lever is thrown for the gear down. */
    public boolean down() {
        return getBlockState().getValue(GearLeverBlock.DOWN);
    }

    /** Whether every leg is down and locked. */
    public boolean locked() {
        return level != null && legs(level).stream().allMatch(bay -> bay.motion() == GearBayBlockEntity.Motion.DOWN);
    }

    /**
     * How high the lowest of the legs' wheels is above the ground, in blocks, each leg's measured where it stands, down
     * or not; with no legs, as high as can be.
     */
    public double lowestWheels(Level level) {
        return legs(level).stream().mapToDouble(bay -> bay.wheelsAboveGround(level)).min().orElse(Double.MAX_VALUE);
    }

    /** Throws the lever down, if it is not, and the legs follow. */
    public void lower(ServerLevel level) {
        throwTo(level, true);
    }

    /**
     * Throws the lever up, if it is not, and the legs follow. False, the lever left down, while the airliner stands on
     * its wheels or retractable gear is switched off in the server config.
     */
    public boolean raise(ServerLevel level) {
        if (!AerolargeConfig.RETRACTABLE_GEAR.get() || legs(level).stream().anyMatch(bay -> bay.bearsWeight(level))) {
            return false;
        }
        throwTo(level, false);
        return true;
    }

    /**
     * Used by a pilot: thrown the other way; or, held down by the airliner's weight or with retractable gear switched off,
     * a buzz and a red flash, and why.
     */
    void pull(ServerLevel level, Player player) {
        if (!down()) {
            lower(level);
            player.displayClientMessage(Component.translatable("message.aerolarge.gear.down"), true);
        } else if (raise(level)) {
            player.displayClientMessage(Component.translatable("message.aerolarge.gear.up"), true);
        } else {
            refused = REFUSED_TICKS;
            level.playSound(null, worldPosition, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.5F);
            light(level, GearLeverBlock.Lamps.RED);
            String why = AerolargeConfig.RETRACTABLE_GEAR.get() ? "on_ground" : "fixed";
            player.displayClientMessage(Component.translatable("message.aerolarge.gear." + why), true);
        }
    }

    private void throwTo(ServerLevel level, boolean down) {
        BlockState state = getBlockState();
        if (state.getValue(GearLeverBlock.DOWN) != down) {
            level.setBlock(worldPosition, state.setValue(GearLeverBlock.DOWN, down), Block.UPDATE_ALL);
            level.playSound(null, worldPosition, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, LEVER_VOLUME, down ? 1.3F : 1.6F);
        }
        follow(level, legs(level), down);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, GearLeverBlockEntity lever) {
        if (level instanceof ServerLevel serverLevel && Math.floorMod(level.getGameTime() + pos.asLong(), LOOK_TICKS) == 0) {
            lever.look(serverLevel);
        }
    }

    /**
     * Every {@link #LOOK_TICKS}: each leg sent after the lever (one that was held down by the airliner's weight goes
     * once it is lifted off), the horn sounded if it should be, and the lamps lit for where the legs are.
     */
    private void look(ServerLevel level) {
        mendLinks(level);
        List<GearBayBlockEntity> legs = legs(level);
        follow(level, legs, down());
        boolean horn = horn(level, legs);
        if (horn) {
            level.playSound(null, worldPosition, AerolargeRegistry.GEAR_HORN.get(), SoundSource.BLOCKS, HORN_VOLUME, 1.0F);
        }
        refused = Math.max(0, refused - LOOK_TICKS);
        flashLit = !flashLit;
        light(level, lamps(legs, horn || refused > 0));
    }

    private static void follow(ServerLevel level, List<GearBayBlockEntity> legs, boolean down) {
        for (GearBayBlockEntity bay : legs) {
            if (down) {
                bay.lower(level);
            } else {
                bay.raise(level);
            }
        }
    }

    /** Whether the gear horn should sound: the airliner coming down near the ground with its gear not all down. */
    private boolean horn(ServerLevel level, List<GearBayBlockEntity> legs) {
        if (legs.stream().allMatch(bay -> bay.motion() == GearBayBlockEntity.Motion.DOWN)) {
            return false;
        }
        SableFlight.State state = SableFlight.read(this, nose());
        if (state == null || state.velocity().y > -HORN_SINK) {
            return false;
        }
        return legs.stream().anyMatch(bay -> bay.wheelsAboveGround(level) < HORN_HEIGHT);
    }

    /** What the lamps show: flashing red as asked, else green with every leg down, dark with every leg up, red between. */
    private GearLeverBlock.Lamps lamps(List<GearBayBlockEntity> legs, boolean flashing) {
        if (flashing) {
            return flashLit ? GearLeverBlock.Lamps.RED : GearLeverBlock.Lamps.OFF;
        }
        if (legs.stream().allMatch(bay -> bay.motion() == GearBayBlockEntity.Motion.DOWN)) {
            return GearLeverBlock.Lamps.GREEN;
        }
        boolean up = legs.stream().allMatch(bay -> bay.motion() == GearBayBlockEntity.Motion.UP);
        return up ? GearLeverBlock.Lamps.OFF : GearLeverBlock.Lamps.RED;
    }

    private void light(ServerLevel level, GearLeverBlock.Lamps lamps) {
        BlockState state = getBlockState();
        if (state.getValue(GearLeverBlock.LAMPS) != lamps) {
            level.setBlock(worldPosition, state.setValue(GearLeverBlock.LAMPS, lamps), Block.UPDATE_CLIENTS);
        }
    }

    /** The legs it works that are there, loaded. */
    private List<GearBayBlockEntity> legs(Level level) {
        List<GearBayBlockEntity> legs = new ArrayList<>();
        for (BlockPos offset : bays) {
            if (level.getBlockEntity(worldPosition.offset(offset)) instanceof GearBayBlockEntity bay) {
                legs.add(bay);
            }
        }
        return legs;
    }

    /** The way the airliner's nose points: away from the pilots the lever faces. */
    private Direction nose() {
        return getBlockState().getValue(GearLeverBlock.FACING).getOpposite();
    }

    /** Once after loading, mends its links to the legs if they lead nowhere. */
    private void mendLinks(ServerLevel level) {
        if (!linksChecked) {
            linksChecked = true;
            Turning.mend(level, worldPosition, bays, state -> state.getBlock() instanceof GearBayBlock, this::turnLinks);
        }
    }

    private void turnLinks(Rotation turn) {
        bays = Turning.turn(bays, turn);
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Bays", Relays.write(bays));
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bays = Turning.turn(Relays.read(tag.getList("Bays", Tag.TAG_INT_ARRAY)), Turning.since(tag, getBlockState()));
    }
}
