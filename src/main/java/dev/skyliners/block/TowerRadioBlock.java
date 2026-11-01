package dev.skyliners.block;

import com.mojang.serialization.MapCodec;
import dev.skyliners.radio.Broadcasts;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

/** A radio set for the airfield's tower: every line spoken into it is a radio call to the airliners in range. */
public class TowerRadioBlock extends RadioBlock {
    private static final MapCodec<TowerRadioBlock> CODEC = simpleCodec(TowerRadioBlock::new);

    public TowerRadioBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected Broadcasts.Kind kind(Player player) {
        return Broadcasts.Kind.RADIO;
    }
}
