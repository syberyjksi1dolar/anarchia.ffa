package com.example.mymod;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Pomocnik do nakładania efektów po ich nazwie (np. "slowness", "blindness"). */
public final class EffectUtil {
	private EffectUtil() {
	}

	public static Holder<MobEffect> get(String id) {
		ResourceKey<MobEffect> key = ResourceKey.create(Registries.MOB_EFFECT,
				Identifier.fromNamespaceAndPath("minecraft", id));
		return BuiltInRegistries.MOB_EFFECT.getOrThrow(key);
	}

	/** amplifier = poziom - 1 (poziom I = 0). */
	public static void give(LivingEntity target, String id, int ticks, int amplifier) {
		target.addEffect(new MobEffectInstance(get(id), ticks, amplifier, false, true, true));
	}
}
