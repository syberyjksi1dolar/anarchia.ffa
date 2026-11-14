package com.example.mymod;

import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * Zbroje: Zbroja Dolara (Ochrona 10, Niezniszczalność 10) i Anarchiczna Zbroja 2 (Ochrona 7, Niezniszczalność 7).
 * Zaklęcia nakładamy na każdy element zbroi, który gracz ma gdziekolwiek w ekwipunku (także po wzięciu z zakładki).
 */
public final class ZbrojeHandler {
	private record Gear(List<Item> pieces, int protection, int unbreaking) {
	}

	private static final List<Gear> GEARS = List.of(
			new Gear(List.of(ModItems.ZBROJA_DOLARA_HELM, ModItems.ZBROJA_DOLARA_KLATA, ModItems.ZBROJA_DOLARA_SPODNIE,
					ModItems.ZBROJA_DOLARA_BUTY), 10, 10),
			new Gear(List.of(ModItems.ANARCHICZNA_ZBROJA_2_HELM, ModItems.ANARCHICZNA_ZBROJA_2_KLATA,
					ModItems.ANARCHICZNA_ZBROJA_2_SPODNIE, ModItems.ANARCHICZNA_ZBROJA_2_BUTY), 7, 7));

	private ZbrojeHandler() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 10 != 0) {
				return;
			}
			var enchants = server.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
			var protection = enchants.getOrThrow(Enchantments.PROTECTION);
			var unbreaking = enchants.getOrThrow(Enchantments.UNBREAKING);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
					ItemStack s = p.getInventory().getItem(i);
					if (s.isEmpty()) {
						continue;
					}
					for (Gear g : GEARS) {
						for (Item piece : g.pieces()) {
							if (s.is(piece)) {
								if (EnchantmentHelper.getItemEnchantmentLevel(protection, s) < g.protection()) {
									s.enchant(protection, g.protection());
								}
								if (EnchantmentHelper.getItemEnchantmentLevel(unbreaking, s) < g.unbreaking()) {
									s.enchant(unbreaking, g.unbreaking());
								}
							}
						}
					}
				}
			}
		});
	}
}
