package dev.skyliners.block;

import dev.skyliners.SkylinersRegistry;
import java.util.List;
import java.util.function.BiConsumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The cockpit's LED console's block entity, in the part holding the console: the panel of each of her stories, as
 * offsets from it, the entry story's first; each story's floor and ceiling, rows up from her foot, which the console's
 * silhouette of her lays its bands by; and which stories it sets now, any of them or all, their strips and their
 * windows' glass. A change made for several runs up her from the lowest of them, a story every few ticks, so the new
 * colour climbs her. Set as the rocket is built; the clients draw what it holds (see the client's
 * {@code LedConsoleRenderer}).
 */
public class LedConsoleBlockEntity extends BlockEntity {
    /** What {@link #picked} is once the whole-rocket button has picked every story, however many she has. */
    private static final long EVERY_STORY = -1L;
    /** How many ticks a change for several stories takes to climb from one story to the next. */
    private static final int CLIMB_TICKS = 3;

    private List<BlockPos> panels = List.of();
    /** Each story's floor and ceiling in turn, rows up from her foot. */
    private int[] spans = new int[0];
    /** The stories it sets now, a bit for each, counted from the entry story up. */
    private long picked = EVERY_STORY;
    /**
     * Whether a touch on a story's band adds to what is picked, as it does once a band has been touched since anything
     * was set. Only the server's, and not saved: after a reload the next band simply starts a new pick.
     */
    private boolean gathering;

    public LedConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(SkylinersRegistry.LED_CONSOLE_ENTITY.get(), pos, state);
    }

    /**
     * Tells it her stories, as she is built: each one's panel, from the console (turned as she was placed), and each
     * one's floor and ceiling in turn, the entry story's first.
     */
    public void wire(List<BlockPos> panels, int[] spans) {
        this.panels = List.copyOf(panels);
        this.spans = spans.clone();
        setChanged();
    }

    /**
     * Its face's tiles (see {@link LedControls#console}), the same for a touch on the server and for the tile lit under
     * a client's crosshair: its stories' bands laid along her silhouette by their floors and ceilings, none for a
     * console set by hand.
     */
    public TouchPanel.Layout layout() {
        return LedControls.console(spans, stories());
    }

    /** How many stories it sets. */
    public int stories() {
        return Math.min(panels.size(), spans.length / 2);
    }

    /** Whether it sets a story now, counted from the entry story up. */
    public boolean picks(int story) {
        return (picked >> story & 1) != 0;
    }

    /** Whether it sets every one of her stories now. */
    public boolean picksAll() {
        return (~picked & ((1L << stories()) - 1)) == 0;
    }

    /** A story's panel, where it stands loaded; null where it does not. */
    @Nullable
    public LedPanelBlockEntity panel(int story) {
        if (level == null || story < 0 || story >= stories()) {
            return null;
        }
        return level.getBlockEntity(worldPosition.offset(panels.get(story))) instanceof LedPanelBlockEntity panel ? panel : null;
    }

    /**
     * Works a touched control, on the server: a story's band picks it (see {@link #pick}), the whole-rocket button
     * picks all of them, and a swatch, brightness, mode or window's tile changes what is picked, each story in turn up
     * her.
     */
    void touch(LedControls.Control control) {
        switch (control) {
            case LedControls.Story story -> pick(story.story());
            case LedControls.WholeRocket whole -> pickAll();
            case LedControls.Windows windows -> climb((panel, delay) -> panel.dimWindows(windows.dim(), delay));
            default -> climb((panel, delay) -> panel.change(setting -> LedControls.changed(setting, control), delay));
        }
    }

    /**
     * A touch on a story's band. The first since anything was set picks it alone, so that setting one story after
     * another takes a touch each, as on a lighting desk; those after it add more stories, or leave out one already
     * picked, but for the last, as something is always picked for the controls to change.
     */
    private void pick(int story) {
        long band = 1L << story;
        if (!gathering) {
            picked = band;
        } else if (picked != band) {
            picked ^= band;
        }
        gathering = true;
        changed();
    }

    private void pickAll() {
        picked = EVERY_STORY;
        gathering = false;
        changed();
    }

    /** Changes the stories picked, each in turn from the lowest up, its panel told how many ticks later it is. */
    private void climb(BiConsumer<LedPanelBlockEntity, Integer> change) {
        gathering = false;
        int climbed = 0;
        for (int story = 0; story < stories(); story++) {
            if (!picks(story)) {
                continue;
            }
            LedPanelBlockEntity panel = panel(story);
            if (panel != null) {
                change.accept(panel, climbed * CLIMB_TICKS);
            }
            climbed++;
        }
    }

    private void changed() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Panels", Relays.write(panels));
        Turning.save(tag, getBlockState());
        tag.putIntArray("Spans", spans);
        tag.putLong("Picked", picked);
    }

    /**
     * Its panels' offsets turned with the rocket, if it faces another way than it was saved facing (see
     * {@link Turning}).
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        panels = Turning.turn(Relays.read(tag.getList("Panels", Tag.TAG_INT_ARRAY)), Turning.since(tag, getBlockState()));
        spans = tag.getIntArray("Spans");
        picked = tag.contains("Picked", Tag.TAG_LONG) ? tag.getLong("Picked") : pickedBefore(tag);
    }

    /** What a console saved when it set one story or all of them has picked. */
    private long pickedBefore(CompoundTag tag) {
        int story = tag.contains("Selected", Tag.TAG_INT) ? tag.getInt("Selected") : -1;
        return story < 0 || story >= stories() ? EVERY_STORY : 1L << story;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
