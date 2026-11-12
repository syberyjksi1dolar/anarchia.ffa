package com.example.mymod;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Wata Cukrowa: naprawia losowy zniszczony element noszonej zbroi. Bez cooldownu, zużywalna. */
public class WataCukrowaItem extends Item {
	public WataCukrowaItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		List<EquipmentSlot> damaged = new ArrayList<>();
		for (EquipmentSlot slot : new EquipmentSlot[] { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
			ItemStack armor = player.getItemBySlot(slot);
			if (!armor.isEmpty() && armor.isDamaged()) {
				damaged.add(slot);
			}
		}
		if (damaged.isEmpty()) {
			sp.sendSystemMessage(Component.literal("Twoja zbroja nie wymaga naprawy"), true);
			return InteractionResult.FAIL;
		}
		EquipmentSlot slot = damaged.get(level.getRandom().nextInt(damaged.size()));
		player.getItemBySlot(slot).setDamageValue(0);
		String part = switch (slot) {
			case FEET -> "buty";
			case CHEST -> "klatę";
			case HEAD -> "hełm";
			default -> "spodnie";
		};
		sp.sendSystemMessage(Component.literal("Wata Cukrowa naprawiła ci " + part + "!"), true);
		if (!player.getAbilities().instabuild) {
			player.getItemInHand(hand).shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
