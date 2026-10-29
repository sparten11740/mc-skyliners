package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import dev.aerolarge.autopilot.AutopilotBlockEntity;
import dev.aerolarge.compat.SableFlight;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * A zeppelin's boarding stairs (see {@link BoardingStairsButtonBlock}): the button at the head of a flight keeps it.
 * A zeppelin built now has the airliner's airstairs there, two blocks wide, hung in her belly (see
 * {@link BoardingAirstairs}). One built before keeps its stairs of blocks: the button keeps the blocks of the flight that
 * hang below her hull, its steps, their rails and the patch of her cover they cut through, each as it stands with the
 * stairs down and with them up. Going up, that flight folds away a row at a time from its foot, and the cover closes
 * over it last; coming down, the cover opens and the steps run down a row at a time to the ground, the first row that
 * would stand in the ground ending them there. Either kind folds away of its own accord as soon as she moves, and
 * comes down again of its own accord once she is down on the ground after flying, or first stands there built; any of
 * her stairs' buttons works them all by hand while she stands still. The button low on the control car, calling them
 * from the ground, keeps no flight of its own.
 */
public class BoardingStairsBlockEntity extends BlockEntity implements Airstairs, BoardingAirstairs.Keeper {
    private static final Loaded<BoardingStairsBlockEntity> LOADED = new Loaded<>(BoardingStairsBlockEntity.class);
    /** How many ticks a row of the flight takes to fold away or come down. */
    private static final int ROW_TICKS = 3;
    /** How often the stairs look whether she has started moving or come to rest, in ticks. */
    private static final int WATCH_TICKS = 10;
    /** Faster than this, in blocks a second, she is moving: the stairs fold away, and will not come down. */
    private static final double MOVING = 1.0;
    /** How many looks in a row she has to be still on the ground, after flying, before the stairs come down. */
    private static final int STILL_LOOKS = 4;
    private static final float MOTOR_VOLUME = 0.9F;
    private static final float LATCH_VOLUME = 1.0F;
    /** What stands in the stairs' way in the world under her: anything solid. */
    private static final Predicate<BlockState> IN_THE_WAY = state -> !state.isAir() && !state.canBeReplaced();

    /**
     * One block of a flight that folds away: where it is, from the button, and what stands there with the stairs down
     * and with them up.
     */
    private record Fold(BlockPos at, BlockState down, BlockState up) {
    }

    /** The flight's folding blocks, and its rows of them by height, the highest first. */
    private final List<Fold> folds = new ArrayList<>();
    private final List<Integer> rows = new ArrayList<>();
    /** Whether the stairs are down, or on their way down; how many of the rows, from the top, stand as they do down. */
    private boolean down = true;
    private int laid;
    /** Whether they folded away as she moved off, to come down again of their own accord once she is down. */
    private boolean flown;
    private int stillLooks;
    /** The airliner's airstairs, in a zeppelin built with them; none in one with stairs of blocks. */
    @Nullable
    private BoardingAirstairs belly;

    public BoardingStairsBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.BOARDING_STAIRS_ENTITY.get(), pos, state);
    }

    /**
     * Hands the button the airstairs it keeps, as the zeppelin is built with them away (see {@link BellyAirstairs}):
     * where their head is from it, the ways out and to the right, how far they come down and in how many sections,
     * and their hatch, each block of it from the button. They come down of their own accord once she is found
     * standing on the ground.
     */
    public void rigAirstairs(BlockPos head, Direction out, Direction right, int rise, int sections, List<AirstairControllerBlockEntity.Step> hatch) {
        folds.clear();
        rows.clear();
        belly = new BoardingAirstairs(this, head, out, right, rise, sections, AirstairFlight.WIDTH, hatch, null);
        flown = true;
        setChanged();
    }

    private void sortRows() {
        rows.clear();
        folds.stream().map(fold -> fold.at().getY()).distinct().sorted(Comparator.reverseOrder()).forEach(rows::add);
    }

    /**
     * A stairs button pressed, on the server: every flight of its zeppelin's boarding stairs folds away if any is down,
     * or comes down if all are away; nothing but a low note while she moves.
     */
    static void press(ServerLevel level, BlockPos button) {
        List<BoardingStairsBlockEntity> flights = flights(level, button);
        if (flights.isEmpty()) {
            return;
        }
        if (moving(level, button)) {
            level.playSound(null, button, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 1.0F, 0.6F);
            return;
        }
        boolean anyDown = flights.stream().anyMatch(BoardingStairsBlockEntity::isDown);
        for (BoardingStairsBlockEntity flight : flights) {
            flight.flown = false;
            flight.go(level, !anyDown);
        }
    }

    /** Every flight of the zeppelin a block is part of, by the buttons at their heads; only its own off a zeppelin. */
    private static List<BoardingStairsBlockEntity> flights(ServerLevel level, BlockPos button) {
        AutopilotBlockEntity zeppelin = AutopilotBlockEntity.zeppelinFraming(level, button);
        BoundingBox frame = zeppelin == null ? null : zeppelin.frame();
        if (frame == null) {
            return level.getBlockEntity(button) instanceof BoardingStairsBlockEntity own ? List.of(own) : List.of();
        }
        return LOADED.in(level).stream().filter(flight -> frame.isInside(flight.getBlockPos())).toList();
    }

    private static boolean moving(Level level, BlockPos pos) {
        return SableFlight.velocity(level, Vec3.atCenterOf(pos)).length() > MOVING;
    }

    /** Whether the flight is down, or on its way down. */
    private boolean isDown() {
        return belly != null ? belly.down() : down && !folds.isEmpty();
    }

    /** Whether the flight is all away and staying that way. */
    private boolean isAway() {
        return belly != null ? belly.stowed() : laid == 0;
    }

    /** Sets the stairs off down or up, the motor heard, if they are not already where they are going. */
    private void go(ServerLevel level, boolean toDown) {
        if (belly != null) {
            belly.go(level, toDown);
            sync();
            return;
        }
        if (folds.isEmpty() || toDown == down) {
            return;
        }
        down = toDown;
        sound(level, AerolargeRegistry.AIRSTAIR_MOTOR.get(), MOTOR_VOLUME);
        setChanged();
    }

    /**
     * Once a tick, on the server: the airstairs a tick further, or a row of the stairs of blocks moved toward where
     * they are going; and a look at how she is doing.
     */
    void serverTick(ServerLevel level) {
        if (belly == null && folds.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (now % WATCH_TICKS == 0) {
            watch(level);
        }
        if (belly != null) {
            belly.tick(level);
        } else if (now % ROW_TICKS == 0) {
            step(level);
        }
    }

    /** Once a tick, on a client: the airstairs' clock, so they are drawn on their way between the server's word. */
    void clientTick() {
        if (belly != null) {
            belly.clientTick();
        }
    }

    /** Folds the stairs away as she moves off; brings them down again once she is still on the ground after flying. */
    private void watch(ServerLevel level) {
        if (moving(level, worldPosition)) {
            stillLooks = 0;
            if (isDown()) {
                flown = true;
                go(level, false);
            }
            return;
        }
        stillLooks = flown && !isDown() && isAway() ? stillLooks + 1 : 0;
        if (stillLooks >= STILL_LOOKS && onGround(level)) {
            flown = false;
            go(level, true);
        }
    }

    /** A row of the flight moved: the next one down laid, or the lowest one laid folded away; a latch once done. */
    private void step(ServerLevel level) {
        if (down && laid < rows.size()) {
            if (blocked(level, rows.get(laid))) {
                finish(level);
                return;
            }
            lay(level, rows.get(laid), true);
            laid++;
            if (laid == rows.size()) {
                finish(level);
            }
        } else if (!down && laid > 0) {
            laid--;
            lay(level, rows.get(laid), false);
            if (laid == 0) {
                finish(level);
            }
        }
    }

    /** Done moving: the latch heard, and the rows still to come down given up, where the ground stopped them. */
    private void finish(ServerLevel level) {
        sound(level, AerolargeRegistry.AIRSTAIR_LATCH.get(), LATCH_VOLUME);
        if (down) {
            laid = rows.size();
        }
        setChanged();
    }

    /** Sets every block of a row of the flight as it stands with the stairs down, or up. */
    private void lay(ServerLevel level, int row, boolean toDown) {
        for (Fold fold : folds) {
            if (fold.at().getY() == row) {
                level.setBlock(worldPosition.offset(fold.at()), toDown ? fold.down() : fold.up(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * Whether a row of the flight would stand in the ground under her: any step of it outside her hull, where the
     * world has something solid.
     */
    private boolean blocked(ServerLevel level, int row) {
        for (Fold fold : folds) {
            if (fold.at().getY() == row && fold.up().isAir() && !fold.down().isAir() && solidInWorld(level, worldPosition.offset(fold.at()))) {
                return true;
            }
        }
        return false;
    }

    /** Whether she stands on the ground: something solid in the world under the foot of the flight. */
    private boolean onGround(ServerLevel level) {
        if (belly != null) {
            return belly.onGround(level);
        }
        int lowest = rows.get(rows.size() - 1);
        for (Fold fold : folds) {
            if (fold.at().getY() == lowest && solidInWorld(level, worldPosition.offset(fold.at()).below())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the world has something solid where a block of hers is, under her rather than among her blocks. */
    private static boolean solidInWorld(ServerLevel level, BlockPos pos) {
        return IN_THE_WAY.test(level.getBlockState(BlockPos.containing(SableFlight.toWorld(level, Vec3.atCenterOf(pos)))));
    }

    private void sound(ServerLevel level, SoundEvent sound, float volume) {
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, 1.0F);
    }

    @Nullable
    @Override
    public Frame frame() {
        return belly == null ? null : belly.frame();
    }

    @Nullable
    @Override
    public AirstairFlight.Motion motion(float partialTick) {
        return belly == null ? null : belly.motion(partialTick);
    }

    @Override
    public boolean holds(Level level, BlockPos pos) {
        return belly != null && belly.holds(level, pos);
    }

    /** Tells the clients where the airstairs are going, which draw them. */
    @Override
    public void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        LOADED.add(this);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        LOADED.remove(this);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (Fold fold : folds) {
            CompoundTag saved = new CompoundTag();
            saved.put("At", NbtUtils.writeBlockPos(fold.at()));
            saved.put("Down", NbtUtils.writeBlockState(fold.down()));
            saved.put("Up", NbtUtils.writeBlockState(fold.up()));
            list.add(saved);
        }
        tag.put("Folds", list);
        tag.putBoolean("StairsDown", down);
        tag.putInt("Laid", laid);
        tag.putBoolean("Flown", flown);
        if (belly != null) {
            CompoundTag airstairs = new CompoundTag();
            belly.save(airstairs);
            tag.put("Airstairs", airstairs);
        }
        Turning.save(tag, getBlockState());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Rotation turn = Turning.since(tag, getBlockState());
        folds.clear();
        for (Tag entry : tag.getList("Folds", Tag.TAG_COMPOUND)) {
            CompoundTag saved = (CompoundTag) entry;
            BlockPos at = Turning.turn(NbtUtils.readBlockPos(saved, "At").orElse(BlockPos.ZERO), turn);
            BlockState toDown = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), saved.getCompound("Down")).rotate(turn);
            BlockState toUp = NbtUtils.readBlockState(registries.lookupOrThrow(Registries.BLOCK), saved.getCompound("Up")).rotate(turn);
            folds.add(new Fold(at, toDown, toUp));
        }
        sortRows();
        down = !tag.contains("StairsDown") || tag.getBoolean("StairsDown");
        laid = tag.contains("Laid") ? Math.min(tag.getInt("Laid"), rows.size()) : rows.size();
        flown = tag.getBoolean("Flown");
        belly = tag.contains("Airstairs") ? BoardingAirstairs.load(this, tag.getCompound("Airstairs"), turn, registries) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
