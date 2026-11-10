package com.example.mymod;

import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ToolMaterial;

/**
 * Siekiera Grincha. Normalna siekiera w walce (zasięg jak miecz, bo to zwykła broń biała),
 * a efekty po uderzeniu obsługuje GrinchAxeHandler.
 */
public class GrinchAxeItem extends AxeItem {
	public GrinchAxeItem(Properties properties) {
		super(ToolMaterial.DIAMOND, 5.0F, -3.0F, properties);
	}
}
