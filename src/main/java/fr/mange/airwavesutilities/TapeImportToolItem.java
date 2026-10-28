package fr.mange.airwavesutilities;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class TapeImportToolItem extends Item {
    public TapeImportToolItem(Properties properties) { super(properties); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            TapeImportClient.open();
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        return InteractionResultHolder.success(stack);
    }
}
