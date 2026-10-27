package dev.aerolarge.item;

import dev.aerolarge.airframe.AirframeBuild;
import dev.aerolarge.airframe.Layout;
import java.util.List;
import java.util.function.Supplier;
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
import net.minecraft.world.level.block.state.BlockState;

/**
 * Lays down a whole airframe, an airliner or a zeppelin. Used on the ground, it builds it with its nose just past the
 * block clicked and its tail stretching away in the direction the player looks, wheels on the ground. It refuses when
 * the space is not clear, unless the player sneaks, which builds straight through whatever is there. Held, it shows a
 * miniature of what it builds where it would build it (see {@code BlueprintPreview}): the airframe's souvenir model.
 */
public class AirframeBlueprintItem extends Item {
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
        if (AirframeBuild.isRunning(level)) {
            player.displayClientMessage(Component.translatable("message.aerolarge.busy"), true);
            return InteractionResult.FAIL;
        }

        Direction along = player.getDirection();
        BlockPos nose = context.getClickedPos().above().relative(along, 2);
        Layout laid = layout.get();
        AirframeBuild.Placement placement = AirframeBuild.Placement.of(level, laid, nose, along);

        if (placement.top() >= level.getMaxBuildHeight()) {
            player.displayClientMessage(Component.translatable(laid.message("too_high")), false);
            return InteractionResult.FAIL;
        }
        if (placement.blocked() > 0 && !player.isShiftKeyDown()) {
            BlockPos at = placement.firstBlocked();
            player.displayClientMessage(Component.translatable(
                "message.aerolarge.blocked", placement.blocked(), at.getX(), at.getY(), at.getZ()
            ).withStyle(ChatFormatting.RED), false);
            return InteractionResult.FAIL;
        }
        AirframeBuild.start(level, placement, player);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(this.tooltip).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.aerolarge.airliner_blueprint.usage").withStyle(ChatFormatting.DARK_GRAY));
    }
}
