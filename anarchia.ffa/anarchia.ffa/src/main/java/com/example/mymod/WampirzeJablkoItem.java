package com.example.mymod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Wampirze Jabłko: po zjedzeniu Siła II na 2 sekundy i 5 dodatkowych serc (żółte serca) do 1 minuty. */
public class WampirzeJablkoItem extends Item {
	public static final int STRENGTH_TICKS = 2 * 20;
	public static final float ABSORPTION_HP = 10.0F; // 5 serc

	public static final int ABSORPTION_TICKS = 60 * 20; // żółte serca znikają po minucie

	private static final Map<LivingEntity, Integer> EXPIRES = new HashMap<>();

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			int now = server.getTickCount();
			Iterator<Map.Entry<LivingEntity, Integer>> it = EXPIRES.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<LivingEntity, Integer> e = it.next();
				if (e.getKey().isRemoved() || now >= e.getValue()) {
					if (!e.getKey().isRemoved()) {
						e.getKey().setAbsorptionAmount(0.0F);
					}
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> EXPIRES.clear());
	}

	public WampirzeJablkoItem(Properties properties) {
		super(properties);
	}

	@Override
	public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
		ItemStack result = super.finishUsingItem(stack, level, entity);
		if (!level.isClientSide()) {
			EffectUtil.give(entity, "strength", STRENGTH_TICKS, 1); // Siła II
			entity.setAbsorptionAmount(Math.max(entity.getAbsorptionAmount(), ABSORPTION_HP));
			if (level.getServer() != null) {
				EXPIRES.put(entity, level.getServer().getTickCount() + ABSORPTION_TICKS);
			}
		}
		return result;
	}
}
