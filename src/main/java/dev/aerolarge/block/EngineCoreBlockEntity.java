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
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * An engine's state on the client: how far it has spooled up toward the thrust its lever asks for, how far its
 * intake's iris is open and how far round its fan has turned. The engine's sound and contrail live in the client
 * package and are driven through {@link #clientTicker}, which the client sets, so this class stays safe to load on a
 * dedicated server. It also knows where the engine's intake is, set when the airliner is built, so the client can
 * draw the intake round over the nacelle's face.
 */
public class EngineCoreBlockEntity extends BlockEntity {
    /** Set by the client: plays the engine and lays its contrail each tick. */
    public static Consumer<EngineCoreBlockEntity> clientTicker = engine -> { };
    /** Share of full thrust gained or lost per tick: a turbofan takes a few seconds to spool up. */
    private static final float SPOOL_RATE = 0.012F;
    /** Share of the way the intake's iris opens or shuts per tick: a second from shut to wide open. */
    private static final float IRIS_RATE = 0.05F;
    /** Below this spool the fan has stopped turning, and the iris may shut over it. */
    private static final float STOPPED = 0.01F;
    /** How far round the fan turns per tick at full thrust, in radians: quick, but slow enough to see the blades go. */
    private static final float FAN_SPEED = 0.2F;
    /** A whole turn of the fan, in radians. */
    private static final float FULL_TURN = (float) (2 * Math.PI);

    private float spool;
    private float iris;
    private float lastIris;
    private float fan;
    private float lastFan;
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

    /** How far the intake's iris is open, 0 shut to 1 wide open, between the last tick and this one. */
    public float iris(float partialTick) {
        return Mth.lerp(partialTick, lastIris, iris);
    }

    /** How far round the fan has turned, in radians, between the last tick and this one. */
    public float fan(float partialTick) {
        return Mth.lerp(partialTick, lastFan, fan);
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

    /**
     * Spools the engine toward its relay's signal and turns its fan with the spool. The intake's iris opens as soon
     * as the engine is asked for thrust, the thrust lever off idle or the autopilot flying it, and shuts again only
     * once the fan has run down, so a moment back at idle does not close it on a turning fan.
     */
    static void clientTick(EngineCoreBlockEntity engine) {
        int power = engine.getBlockState().getValue(ThrustRelayBlock.POWER);
        engine.spool += Math.clamp(power / 15F - engine.spool, -SPOOL_RATE, SPOOL_RATE);
        boolean running = power > 0 || engine.spool > STOPPED;
        engine.lastIris = engine.iris;
        engine.iris = Math.clamp(engine.iris + (running ? IRIS_RATE : -IRIS_RATE), 0F, 1F);
        engine.lastFan = engine.fan;
        engine.fan += engine.spool * FAN_SPEED;
        // Kept to one turn, both together, so it never grows past what a float holds finely.
        if (engine.fan > FULL_TURN) {
            engine.fan -= FULL_TURN;
            engine.lastFan -= FULL_TURN;
        }
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
