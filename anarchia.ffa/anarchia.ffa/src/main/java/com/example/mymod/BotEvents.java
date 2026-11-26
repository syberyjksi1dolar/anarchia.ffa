package com.example.mymod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

/**
 * Boty używają eventówek według opisów przedmiotów (bot nie jest graczem, więc to odtworzone efekty, a nie wywołanie prawdziwego prawego kliku).
 * Obsługiwane: leczenie i wzmocnienia (krew wampira, róża kupidyna, wampirze jabłko, cudowna latarnia, wata cukrowa, ciepłe mleko, korona),
 * ataki obszarowe i osłabiające w walce (zatopiony statek, płatek śniegu, parasol, róg Koozy, boski topór, blok widmo, jajko creepera,
 * kulka bałwana, trunki, kanapka, serce) oraz bronie trzymane w ręku z efektem przy trafieniu (Excalibur, smoczy/piekielny/króliczy/marchewkowy miecz,
 * siekiera i łopata Grincha, zatruty ołówek, rózga). Pozostałych eventówek bot nie umie użyć i je tylko nosi.
 */
final class BotEvents {
	private BotEvents() {}

	private static final Random RNG = new Random();
	private static final Map<String, Long> CD = new HashMap<>();
	private static final Map<String, Long> INVULN = new HashMap<>();

	private static final List<String> WEAPONS = List.of("excalibur", "smoczy_miecz", "piekielny_miecz", "siekiera_grincha", "kroliczy_miecz",
			"marchewkowy_miecz", "lopata_grincha", "zatruty_olowek", "rozga");
	private static final List<String> ATTACK = List.of("zatopiony_statek", "platek_sniegu", "parasol", "rog_koozy", "boski_topor", "blok_widmo",
			"jajko_zmutowanego_creepera", "kulka_balwana", "zatruty_trunek", "rozgotowana_kukurydza", "splesniala_kanapka", "serce");

	/** Stałe eventówki (nie znikają po użyciu). */
	private static final Set<String> PERMANENT = Set.of("rog_koozy", "boski_topor");

	private static String pathOf(ItemStack st) {
		if (st.isEmpty()) return "";
		net.minecraft.resources.Identifier id = BuiltInRegistries.ITEM.getKey(st.getItem());
		return "mymod".equals(id.getNamespace()) ? id.getPath() : "";
	}

	private static int find(SimpleContainer c, String path) {
		for (int i = 0; i < c.getContainerSize(); i++) if (pathOf(c.getItem(i)).equals(path)) return i;
		return -1;
	}

	private static boolean ready(String nick, String path, long now) {
		return CD.getOrDefault(nick + "|" + path, 0L) <= now;
	}

	private static void cooldown(String nick, String path, long now, int ticks) {
		CD.put(nick + "|" + path, now + ticks);
	}

	/** Zużywa jedną sztukę (eventówki zużywalne); stałe zostają w ekwipunku. */
	private static void consume(SimpleContainer c, int idx) {
		ItemStack s = c.getItem(idx);
		s.shrink(1);
		if (s.isEmpty()) c.setItem(idx, ItemStack.EMPTY);
		c.setChanged();
	}

	private static List<LivingEntity> around(ServerLevel level, Mannequin m, double radius) {
		AABB box = m.getBoundingBox().inflate(radius);
		List<LivingEntity> l = new ArrayList<>();
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x != m && x.isAlive() && !x.isSpectator())) {
			if (e.distanceTo(m) > radius || BotManager.friendly(m, e)) continue;
			if (e instanceof net.minecraft.server.level.ServerPlayer p && p.isCreative()) continue;
			l.add(e);
		}
		return l;
	}

	/** Wywoływane co 0,5 s dla każdego bota; foe = aktualny przeciwnik albo null. */
	static void tick(ServerLevel level, String nick, Mannequin m, LivingEntity foe) {
		long now = level.getServer().getTickCount();
		Long inv = INVULN.get(nick);
		if (inv != null && now >= inv) {
			m.setInvulnerable(false);
			INVULN.remove(nick);
		}
		SimpleContainer c = BotManager.inv(nick);
		float hp = m.getHealth(), max = m.getMaxHealth();
		try {
			// naprawa zbroi
			int w = find(c, "wata_cukrowa");
			if (w >= 0 && ready(nick, "wata_cukrowa", now)) {
				for (EquipmentSlot sl : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
					ItemStack a = m.getItemBySlot(sl);
					if (!a.isEmpty() && a.isDamaged()) {
						a.setDamageValue(0);
						consume(c, w);
						cooldown(nick, "wata_cukrowa", now, 200);
						break;
					}
				}
			}
			// zdejmuje negatywne efekty
			int mk = find(c, "cieple_mleko");
			if (mk >= 0 && ready(nick, "cieple_mleko", now)) {
				boolean bad = false;
				for (net.minecraft.world.effect.MobEffectInstance ef : new ArrayList<>(m.getActiveEffects())) {
					if (ef.getEffect().value().getCategory() == net.minecraft.world.effect.MobEffectCategory.HARMFUL) {
						m.removeEffect(ef.getEffect());
						bad = true;
					}
				}
				if (bad) cooldown(nick, "cieple_mleko", now, 100);
			}
			// zwykłe złote jabłko, gdy życie spada poniżej 40%
			if (hp < max * 0.4f && ready(nick, "zlote_jablko_bot", now)) {
				for (int gi = 0; gi < c.getContainerSize(); gi++) {
					ItemStack ga = c.getItem(gi);
					if (ga.is(net.minecraft.world.item.Items.GOLDEN_APPLE)) {
						ga.shrink(1);
						m.setHealth(Math.min(max, hp + 8f));
						m.setAbsorptionAmount(Math.max(m.getAbsorptionAmount(), 4f));
						EffectUtil.give(m, "regeneration", 100, 1);
						cooldown(nick, "zlote_jablko_bot", now, 100);
						break;
					}
				}
			}
			// leczenie, gdy mało życia
			if (hp < max * 0.55f) {
				int i;
				if ((i = find(c, "krew_wampira")) >= 0) {
					m.setHealth(max);
					consume(c, i);
					return;
				}
				if ((i = find(c, "roza_kupidyna")) >= 0 && ready(nick, "roza_kupidyna", now)) {
					EffectUtil.give(m, "regeneration", 200, 1);
					EffectUtil.give(m, "resistance", 200, 0);
					m.setAbsorptionAmount(Math.max(m.getAbsorptionAmount(), 6f));
					cooldown(nick, "roza_kupidyna", now, 1200);
					return;
				}
				if ((i = find(c, "wampirze_jablko")) >= 0) {
					EffectUtil.give(m, "strength", 40, 1);
					m.setAbsorptionAmount(Math.max(m.getAbsorptionAmount(), 10f));
					consume(c, i);
					return;
				}
				if ((i = find(c, "cudowna_latarnia")) >= 0) {
					EffectUtil.give(m, "strength", 200, 1);
					EffectUtil.give(m, "resistance", 400, 1);
					EffectUtil.give(m, "regeneration", 400, 0);
					consume(c, i);
					return;
				}
			}
			if (foe == null) return;
			ensureWeapon(nick, m, c);
			if (find(c, "anarchia_korona") >= 0 && ready(nick, "anarchia_korona", now)) { // korona działa po założeniu: w walce wzmacnia
				EffectUtil.give(m, "strength", 160, 2);
				EffectUtil.give(m, "resistance", 160, 3);
				EffectUtil.give(m, "speed", 160, 3);
				cooldown(nick, "anarchia_korona", now, 120);
			}
			if (RNG.nextInt(3) != 0) return;
			double d = Math.sqrt(foe.distanceToSqr(m));
			List<String> order = new ArrayList<>(ATTACK);
			Collections.shuffle(order, RNG);
			for (String path : order) {
				int i = find(c, path);
				if (i < 0 || !ready(nick, path, now)) continue;
				if (attack(level, nick, m, foe, d, path, now)) {
					if (!PERMANENT.contains(path)) consume(c, i);
					cooldown(nick, path, now, 400 + RNG.nextInt(400));
					return;
				}
			}
		} catch (Exception e) {
			MyMod.LOGGER.warn("Eventówka bota {}: {}", nick, e.toString());
		}
	}

	/** Bot trzyma w ręku eventówkową broń, gdy walczy (dotychczasowy miecz wraca do ekwipunku). */
	private static void ensureWeapon(String nick, Mannequin m, SimpleContainer c) {
		if (WEAPONS.contains(pathOf(m.getMainHandItem()))) return;
		for (String path : WEAPONS) {
			int i = find(c, path);
			if (i < 0) continue;
			ItemStack weapon = c.removeItemNoUpdate(i);
			ItemStack old = m.getMainHandItem();
			if (!old.isEmpty()) c.addItem(old.copy());
			m.setItemSlot(EquipmentSlot.MAINHAND, weapon);
			return;
		}
	}

	private static boolean attack(ServerLevel level, String nick, Mannequin m, LivingEntity foe, double d, String path, long now) {
		switch (path) {
			case "zatopiony_statek" -> {
				if (d > 14) return false;
				for (LivingEntity e : around(level, m, 14)) {
					EffectUtil.give(e, "slow_falling", 200, 1);
					EffectUtil.give(e, "poison", 200, 1);
					EffectUtil.give(e, "weakness", 200, 1);
				}
				EffectUtil.give(m, "strength", 200, 0);
			}
			case "platek_sniegu" -> {
				if (d > 4) return false;
				BotManager.stun(foe, 60);
			}
			case "parasol" -> {
				if (d > 6) return false;
				for (LivingEntity e : around(level, m, 6)) BotManager.pushAway(e, m.getX(), m.getZ(), 6);
			}
			case "rog_koozy" -> {
				if (d > 10) return false;
				for (LivingEntity e : around(level, m, 10)) {
					e.hurtServer(level, level.damageSources().mobAttack(m), 6f);
					BotManager.pushAway(e, m.getX(), m.getZ(), 10);
				}
			}
			case "boski_topor" -> {
				if (d > 15) return false;
				for (LivingEntity e : around(level, m, 15)) BotManager.pushAway(e, m.getX(), m.getZ(), 8);
				m.setInvulnerable(true); // 10 s niewrażliwości
				INVULN.put(nick, now + 200);
			}
			case "blok_widmo" -> {
				if (d > 5) return false;
				for (LivingEntity e : around(level, m, 5)) e.hurtServer(level, level.damageSources().mobAttack(m), 10f);
			}
			case "jajko_zmutowanego_creepera" -> {
				if (d > 5) return false;
				for (LivingEntity e : around(level, m, 5)) e.hurtServer(level, level.damageSources().mobAttack(m), e.getMaxHealth() * 0.5f);
			}
			case "kulka_balwana" -> {
				if (d > 12) return false;
				EffectUtil.give(foe, "slowness", 60, 0);
				EffectUtil.give(foe, "weakness", 60, 0);
			}
			case "zatruty_trunek", "rozgotowana_kukurydza" -> {
				if (d > 10) return false;
				for (LivingEntity e : around(level, m, 10)) {
					if (e.distanceTo(foe) > 4) continue;
					EffectUtil.give(e, "poison", 300, 1);
					EffectUtil.give(e, "nausea", 400, 0);
				}
			}
			case "splesniala_kanapka" -> {
				if (d > 10) return false;
				EffectUtil.give(foe, "nausea", 600, 0);
				EffectUtil.give(foe, "weakness", 600, 0);
				EffectUtil.give(foe, "slowness", 600, 0);
			}
			case "serce" -> {
				if (d > 3.5) return false;
				EffectUtil.give(foe, "slowness", 120, 0);
				EffectUtil.give(foe, "blindness", 120, 0);
			}
			default -> {
				return false;
			}
		}
		return true;
	}

	/** Efekt trzymanej eventówkowej broni przy trafieniu; zwraca nowe obrażenia. */
	static float onHit(ServerLevel level, String nick, Mannequin m, LivingEntity f, float dmg) {
		String path = pathOf(m.getMainHandItem());
		if (!WEAPONS.contains(path)) return dmg;
		long now = level.getServer().getTickCount();
		switch (path) {
			case "excalibur" -> dmg = dmg * 1.58f + 4f;
			case "smoczy_miecz" -> {
				dmg += 3f;
				f.setRemainingFireTicks(160);
			}
			case "piekielny_miecz" -> {
				dmg += 3f;
				f.setRemainingFireTicks(160);
			}
			case "kroliczy_miecz" -> {
				dmg += 3f;
				f.setRemainingFireTicks(80);
			}
			case "marchewkowy_miecz" -> {
				if (ready(nick, path, now)) {
					BotManager.stun(f, 60);
					cooldown(nick, path, now, 200);
				}
			}
			case "lopata_grincha" -> {
				if (ready(nick, path, now)) {
					BotManager.stun(f, 100);
					cooldown(nick, path, now, 300);
				}
			}
			case "siekiera_grincha" -> {
				if (ready(nick, path, now)) {
					dmg += f.getMaxHealth() * 0.30f;
					LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
					if (bolt != null) {
						bolt.setPos(f.getX(), f.getY(), f.getZ());
						bolt.setVisualOnly(true);
						level.addFreshEntity(bolt);
					}
					cooldown(nick, path, now, 400);
				}
			}
			case "zatruty_olowek" -> {
				if (ready(nick, path, now)) {
					EffectUtil.give(f, "poison", 400, 0);
					EffectUtil.give(f, "weakness", 400, 1);
					EffectUtil.give(f, "nausea", 400, 0);
					cooldown(nick, path, now, 200);
				}
			}
			case "rozga" -> {
				if (ready(nick, path, now)) {
					BotManager.pushAway(f, m.getX(), m.getZ(), 6);
					cooldown(nick, path, now, 160);
				}
			}
			default -> { }
		}
		return dmg;
	}
}
