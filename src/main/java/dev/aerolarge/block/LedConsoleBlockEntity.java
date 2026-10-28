package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.ArrayList;
import java.util.List;
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
 * silhouette of her lays its bands by; and which story it sets now, or all of them. A change made for all of them runs
 * up her from the entry story, a story every few ticks, so the new colour climbs her. Set as the rocket is built; the
 * clients draw what it holds (see the client's {@code LedConsoleRenderer}).
 */
public class LedConsoleBlockEntity extends BlockEntity {
    /** What {@link #selected} is while the console sets every story at once. */
    public static final int WHOLE_ROCKET = -1;
    /** How many ticks a change for all of her takes to climb from one story to the next. */
    private static final int CLIMB_TICKS = 3;

    private List<BlockPos> panels = List.of();
    /** Each story's floor and ceiling in turn, rows up from her foot. */
    private int[] spans = new int[0];
    private int selected = WHOLE_ROCKET;

    public LedConsoleBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.LED_CONSOLE_ENTITY.get(), pos, state);
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

    /** Each story's floor and ceiling in turn, rows up from her foot; none for a console set by hand. */
    public int[] spans() {
        return spans.clone();
    }

    /** How many stories it sets. */
    public int stories() {
        return Math.min(panels.size(), spans.length / 2);
    }

    /** The story it sets now, from the entry story up, or {@link #WHOLE_ROCKET}. */
    public int selected() {
        return selected;
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
     * Works a touched control, on the server: a story picks it, the whole-rocket button picks all of them, and a
     * swatch, brightness or mode changes the story picked, or every story in turn up her.
     */
    void touch(LedControls.Control control) {
        switch (control) {
            case LedControls.Story story -> pick(story.story());
            case LedControls.WholeRocket whole -> pick(WHOLE_ROCKET);
            default -> set(control);
        }
    }

    private void pick(int story) {
        selected = story < stories() ? story : WHOLE_ROCKET;
        changed();
    }

    /** Changes the story picked, or each in turn from the entry story up. */
    private void set(LedControls.Control control) {
        List<Integer> stories = new ArrayList<>();
        for (int story = 0; story < stories(); story++) {
            if (selected == WHOLE_ROCKET || selected == story) {
                stories.add(story);
            }
        }
        for (int k = 0; k < stories.size(); k++) {
            LedPanelBlockEntity panel = panel(stories.get(k));
            if (panel != null) {
                panel.change(setting -> LedControls.changed(setting, control), k * CLIMB_TICKS);
            }
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
        tag.putInt("Selected", selected);
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
        selected = tag.contains("Selected", Tag.TAG_INT) ? tag.getInt("Selected") : WHOLE_ROCKET;
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
