package dev.aerolarge.block;

import dev.aerolarge.AerolargeRegistry;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An engine's state on the client: how far it has spooled up toward the thrust its lever asks for. The engine's sound
 * and contrail live in the client package and are driven through {@link #clientTicker}, which the client sets, so
 * this class stays safe to load on a dedicated server. It also knows where the engine's intake is, set when the
 * airliner is built, so the client can draw the intake round over the nacelle's face.
 */
public class EngineCoreBlockEntity extends BlockEntity {
    /** Set by the client: plays the engine and lays its contrail each tick. */
    public static Consumer<EngineCoreBlockEntity> clientTicker = engine -> { };
    /** Share of full thrust gained or lost per tick: a turbofan takes a few seconds to spool up. */
    private static final float SPOOL_RATE = 0.012F;

    private float spool;
    /** Where the intake is; null in an engine built before the intakes were drawn, which keeps its plain face. */
    @Nullable
    private Intake intake;

    /**
     * Where an engine's intake is, from the middle of its core, in blocks: ahead along the nose to the intake's face,
     * then to starboard and up to the middle of it; and the radii of the nacelle and of the mouth inside the cowl's
     * lip. Measured from the nose, so they hold however the airliner is turned.
     */
    public record Intake(float ahead, float starboard, float up, float radius, float mouth) {
        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putFloat("Ahead", ahead);
            tag.putFloat("Starboard", starboard);
            tag.putFloat("Up", up);
            tag.putFloat("Radius", radius);
            tag.putFloat("Mouth", mouth);
            return tag;
        }

        static Intake load(CompoundTag tag) {
            return new Intake(
                tag.getFloat("Ahead"), tag.getFloat("Starboard"), tag.getFloat("Up"), tag.getFloat("Radius"), tag.getFloat("Mouth")
            );
        }
    }

    public EngineCoreBlockEntity(BlockPos pos, BlockState state) {
        super(AerolargeRegistry.ENGINE_CORE_ENTITY.get(), pos, state);
    }

    /** How far the engine has spooled up, 0 at rest to 1 at full thrust. */
    public float spool() {
        return spool;
    }

    @Nullable
    public Intake intake() {
        return intake;
    }

    /** Tells the engine where its intake is, when the airliner is built. */
    public void configure(Intake intake) {
        this.intake = intake;
        setChanged();
    }

    static void clientTick(EngineCoreBlockEntity engine) {
        float target = engine.getBlockState().getValue(ThrustRelayBlock.POWER) / 15F;
        engine.spool += Math.clamp(target - engine.spool, -SPOOL_RATE, SPOOL_RATE);
        clientTicker.accept(engine);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (intake != null) {
            tag.put("Intake", intake.save());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        intake = tag.contains("Intake", Tag.TAG_COMPOUND) ? Intake.load(tag.getCompound("Intake")) : null;
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
