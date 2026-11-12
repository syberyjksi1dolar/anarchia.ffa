package com.example.mymod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Eventówki 30-46 działające po uderzeniu, trafieniu strzałą lub użyciu na bycie:
 * Piekielny Miecz, Króliczy Miecz, Łopata Grincha, Zatruty Ołówek, Marchewkowy Miecz,
 * Marchewkowa Kusza, Łuk Kupidyna oraz Spleśniała Kanapka.
 */
public final class Eventowki46Handler {
	private static final float SHARPNESS_5 = 3.0F; // 0.5 * 5 + 0.5
	private static boolean inside = false;

	/** Podpalenie odpornych na ogień bytów: własne obrażenia co sekundę. */
	private static final Map<LivingEntity, long[]> BURNING = new HashMap<>();
	/** Spleśniała Kanapka: obrażenia co 2 sekundy. */
	private static final Map<LivingEntity, long[]> ROT = new HashMap<>();
	/** Ile razy gracz użył kanapki w bieżącej serii. */
	private static final Map<java.util.UUID, Integer> SANDWICH_USES = new HashMap<>();

	private Eventowki46Handler() {
	}

	/** Blokada skoku: modyfikator atrybutu JUMP_STRENGTH (-1) zdejmowany po czasie. */
	private static final Map<LivingEntity, Long> NO_JUMP = new HashMap<>();
	private static final net.minecraft.resources.Identifier NO_JUMP_ID =
			net.minecraft.resources.Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "no_jump");

	private static void noJump(LivingEntity target, int ticks) {
		var attr = target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
		if (attr == null) {
			return;
		}
		attr.removeModifier(NO_JUMP_ID);
		attr.addTransientModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(NO_JUMP_ID, -1.0,
				net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
		NO_JUMP.put(target, target.level().getGameTime() + ticks);
	}

	private static void freeze(LivingEntity target, int ticks) {
		EffectUtil.give(target, "slowness", ticks, 6); // Spowolnienie VII: stoi w miejscu
		noJump(target, ticks);
	}

	/** Podpala nawet byty odporne na ogień (Wither Szkielety, Blaze'y). */
	private static void ignite(LivingEntity target, int seconds, long now) {
		if (target.fireImmune()) {
			target.setRemainingFireTicks(seconds * 20);
			BURNING.put(target, new long[] { now + seconds * 20L, now + 20 });
		} else {
			target.igniteForSeconds((float) seconds);
		}
	}

	public static void initialize() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(level instanceof ServerLevel)) {
				return InteractionResult.PASS;
			}
			if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isSpectator()) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (stack.is(ModItems.ZATRUTY_OLOWEK) && !player.getCooldowns().isOnCooldown(stack)) {
				EffectUtil.give(target, "poison", 20 * 20, 0);
				EffectUtil.give(target, "weakness", 20 * 20, 1); // Słabość II
				EffectUtil.give(target, "nausea", 20 * 20, 0);
				player.getCooldowns().addCooldown(stack, 120 * 20);
			} else if (stack.is(ModItems.MARCHEWKOWY_MIECZ) && !player.getCooldowns().isOnCooldown(stack)) {
				freeze(target, 3 * 20);
				player.getCooldowns().addCooldown(stack, 30 * 20);
			} else if (stack.is(ModItems.LOPATA_GRINCHA) && !player.getCooldowns().isOnCooldown(stack)) {
				freeze(target, 5 * 20);
				if (target instanceof ServerPlayer victim) {
					float yaw = victim.getRandom().nextFloat() * 360.0F - 180.0F;
					float pitch = victim.getRandom().nextFloat() * 120.0F - 60.0F;
					victim.connection.teleport(victim.getX(), victim.getY(), victim.getZ(), yaw, pitch);
				}
				player.getCooldowns().addCooldown(stack, 30 * 20);
			} else if (stack.is(ModItems.KROLICZY_MIECZ) && !player.getCooldowns().isOnCooldown(stack)) {
				noJump(target, 6 * 20); // nie może skakać
				player.getCooldowns().addCooldown(stack, 60 * 20);
			}
			return InteractionResult.PASS;
		});

		// Spleśniała Kanapka: prawy klik na bycie.
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			if (!(level instanceof ServerLevel serverLevel) || !(entity instanceof LivingEntity target)) {
				return InteractionResult.PASS;
			}
			ItemStack stack = player.getItemInHand(hand);
			if (!stack.is(ModItems.SPLESNIALA_KANAPKA)) {
				return InteractionResult.PASS;
			}
			if (player.getCooldowns().isOnCooldown(stack)) {
				return InteractionResult.FAIL;
			}
			int dur = 30 * 20;
			EffectUtil.give(target, "nausea", dur, 0);
			EffectUtil.give(target, "weakness", dur, 0);
			EffectUtil.give(target, "slowness", dur, 0);
			long now = serverLevel.getGameTime();
			ROT.put(target, new long[] { now + dur, now + 40 });
			int uses = SANDWICH_USES.merge(player.getUUID(), 1, Integer::sum);
			if (uses >= 3) {
				SANDWICH_USES.remove(player.getUUID());
				player.getCooldowns().addCooldown(stack, 120 * 20); // pełny cooldown kanapki
			} else {
				player.getCooldowns().addCooldown(stack, 10 * 20);  // cooldown pojedynczego użycia
			}
			return InteractionResult.SUCCESS;
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			long now = level.getGameTime();

			// Miecze: Ostrość V + podpalenie.
			if (source.getDirectEntity() instanceof Player player) {
				ItemStack held = player.getMainHandItem();
				boolean hell = held.is(ModItems.PIEKIELNY_MIECZ);
				boolean bunny = held.is(ModItems.KROLICZY_MIECZ);
				if (hell || bunny) {
					inside = true;
					try {
						ignite(entity, hell ? 8 : 4, now); // Piekielny: mocno, Króliczy: Aspekt Ognia I
						entity.invulnerableTime = 0;
						entity.hurtServer(level, source, SHARPNESS_5);
					} finally {
						inside = false;
					}
				}
				return;
			}

			// Strzały: Marchewkowa Kusza przyciąga, Łuk Kupidyna może oślepić.
			if (source.getDirectEntity() != null && source.getDirectEntity().getType() == EntityType.ARROW
					&& source.getEntity() instanceof Player shooter) {
				ItemStack main = shooter.getMainHandItem();
				ItemStack off = shooter.getOffhandItem();
				if (main.is(ModItems.MARCHEWKOWA_KUSZA) || off.is(ModItems.MARCHEWKOWA_KUSZA)) {
					net.minecraft.world.phys.Vec3 pull = shooter.position().subtract(entity.position());
					if (pull.lengthSqr() > 1.0E-4) {
						net.minecraft.world.phys.Vec3 dir = pull.normalize();
						double strength = Math.min(1.4, 0.35 + pull.length() * 0.08); // płynnie, bez wyrzucania
						entity.setDeltaMovement(dir.x * strength, 0.25 + dir.y * 0.2, dir.z * strength);
						entity.hurtMarked = true;
					}
				}
				if ((main.is(ModItems.LUK_KUPIDYNA) || off.is(ModItems.LUK_KUPIDYNA))
						&& level.getRandom().nextFloat() < 0.25F) {
					EffectUtil.give(entity, "blindness", 10 * 20, 0);
				}
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			// Niezniszczalność mieczy, łopaty i kilofa.
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				for (ItemStack s : new ItemStack[] { p.getMainHandItem(), p.getOffhandItem() }) {
					if (s.isDamaged() && (s.is(ModItems.PIEKIELNY_MIECZ) || s.is(ModItems.KROLICZY_MIECZ)
							|| s.is(ModItems.MARCHEWKOWY_MIECZ) || s.is(ModItems.LOPATA_GRINCHA)
							|| s.is(ModItems.ANARCHICZNY_KILOF) || s.is(ModItems.MARCHEWKOWA_KUSZA)
							|| s.is(ModItems.LUK_KUPIDYNA))) {
						s.setDamageValue(0);
					}
				}
			}
			// Zdejmowanie blokady skoku i nakładanie enchantów na Anarchiczny Kilof.
			Iterator<Map.Entry<LivingEntity, Long>> nj = NO_JUMP.entrySet().iterator();
			while (nj.hasNext()) {
				Map.Entry<LivingEntity, Long> e = nj.next();
				LivingEntity t = e.getKey();
				if (t.isRemoved() || t.level().getGameTime() >= e.getValue()) {
					var attr = t.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
					if (attr != null) {
						attr.removeModifier(NO_JUMP_ID);
					}
					nj.remove();
				}
			}
			var enchants = server.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
			var eff = enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.EFFICIENCY);
			var fort = enchants.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.FORTUNE);
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				for (ItemStack s : new ItemStack[] { p.getMainHandItem(), p.getOffhandItem() }) {
					if (s.is(ModItems.ANARCHICZNY_KILOF)) {
						if (net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(eff, s) < 13) {
							s.enchant(eff, 13);
						}
						if (net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(fort, s) < 4) {
							s.enchant(fort, 4);
						}
					}
				}
			}
			tickDamage(BURNING, 20, 1.0F, true);
			tickDamage(ROT, 40, 1.0F, false);
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			BURNING.clear();
			ROT.clear();
			SANDWICH_USES.clear();
			NO_JUMP.clear();
		});
	}

	private static void tickDamage(Map<LivingEntity, long[]> map, int interval, float amount, boolean keepFire) {
		Iterator<Map.Entry<LivingEntity, long[]>> it = map.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<LivingEntity, long[]> e = it.next();
			LivingEntity target = e.getKey();
			if (target.isRemoved() || !target.isAlive() || !(target.level() instanceof ServerLevel level)) {
				it.remove();
				continue;
			}
			long now = level.getGameTime();
			long[] st = e.getValue();
			if (now >= st[0]) {
				it.remove();
				continue;
			}
			if (now >= st[1]) {
				st[1] = now + interval;
				target.hurtServer(level, level.damageSources().magic(), amount);
				if (keepFire) {
					target.setRemainingFireTicks(40);
				}
			}
		}
	}
}
