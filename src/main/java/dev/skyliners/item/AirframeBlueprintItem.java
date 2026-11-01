package dev.skyliners.item;

import dev.skyliners.airframe.AirframeBuild;
import dev.skyliners.airframe.Layout;
import dev.skyliners.block.LaunchPadBlockEntity;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lays down a whole airframe, an airliner or a zeppelin. Used on the ground, it builds it with its nose just past the
 * block clicked and its tail stretching away in the direction the player looks, wheels on the ground. It refuses when
 * the space is not clear, unless the player sneaks, which builds straight through whatever is there. Held, it shows a
 * miniature of what it builds where it would build it (see {@code BlueprintPreview}): the airframe's souvenir model.
 */
public class AirframeBlueprintItem extends Item {
    /** How far past the block clicked an airframe's nose is, so it does not come down on the player. */
    private static final int AHEAD = 2;

    /** Where an airframe goes: its nose tip, and the way its tail stretches from there. */
    protected record Spot(BlockPos nose, Direction along) {
    }

    private final Supplier<Layout> layout;
    private final Supplier<BlockState> model;
    private final float modelMiddle;
    private final String tooltip;

    /**
     * A blueprint of the layout made when it is used, shown by a souvenir model whose body's middle is so many
     * sixteenths up its block, described by its tooltip's lang key.
     */
    public AirframeBlueprintItem(Supplier<Layout> layout, Supplier<BlockState> model, float modelMiddle, String tooltip, Properties properties) {
        super(properties);
        this.layout = layout;
        this.model = model;
        this.modelMiddle = modelMiddle;
        this.tooltip = tooltip;
    }

    /** The souvenir model its preview shows, nose north. */
    public BlockState model() {
        return model.get();
    }

    /** How far up its block the model's body's middle is, in sixteenths: what the preview turns it about. */
    public float modelMiddle() {
        return modelMiddle;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        Spot spot = spot(level, context.getClickedPos(), player.getDirection());
        return build(level, player, layout, spot.nose(), spot.along());
    }

    /**
     * Where a layout goes when its blueprint is used on a block by a player looking a way: its nose just past the
     * block, its wheels on top of it, its tail stretching away from the player.
     */
    protected Spot spot(Level level, BlockPos clicked, Direction looking) {
        return new Spot(clicked.above().relative(looking, AHEAD), looking);
    }

    /** The launch pad this blueprint would stand its airframe on, used on a block, on either side; null for none. */
    @Nullable
    public LaunchPadBlockEntity padUnder(Level level, BlockPos aimed) {
        return null;
    }

    /**
     * Starts building a layout for a player, its nose at a spot and its tail stretching one way; refuses while another
     * is going up, before laying this one out, past the build height, or with something in the way unless they sneak.
     */
    static InteractionResult build(ServerLevel level, ServerPlayer player, Supplier<? extends Layout> layout, BlockPos nose, Direction along) {
        if (AirframeBuild.isRunning(level)) {
            player.displayClientMessage(Component.translatable("message.skyliners.busy"), true);
            return InteractionResult.FAIL;
        }
        Layout laid = layout.get();
        AirframeBuild.Placement placement = AirframeBuild.Placement.of(level, laid, nose, along);
        if (placement.top() >= level.getMaxBuildHeight()) {
            player.displayClientMessage(Component.translatable(laid.message("too_high")), false);
            return InteractionResult.FAIL;
        }
        if (placement.blocked() > 0 && !player.isShiftKeyDown()) {
            BlockPos at = placement.firstBlocked();
            player.displayClientMessage(Component.translatable(
                "message.skyliners.blocked", placement.blocked(), at.getX(), at.getY(), at.getZ()
            ).withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        AirframeBuild.start(level, placement, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.tooltip).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.skyliners.airliner_blueprint.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
