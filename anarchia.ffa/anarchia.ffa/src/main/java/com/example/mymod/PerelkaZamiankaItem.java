package com.example.mymod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Perełka Zamianka: po trafieniu byta zamienia miejscami rzucającego i trafionego. Cooldown 20 s. */
public class PerelkaZamiankaItem extends Item {
	public static final int COOLDOWN_TICKS = 20 * 20;

	public PerelkaZamiankaItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		ThrowTracker.throwItem(sp, new ItemStack(Items.ENDER_PEARL), 1.5F, (owner, target) -> {
			Vec3 a = owner.position();
			Vec3 b = target.position();
			Util13.moveTo(owner, b);
			Util13.moveTo(target, a);
		});
		player.getCooldowns().addCooldown(stack, COOLDOWN_TICKS);
		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
