package com.example.mymod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Działanie ksiąg z zaklęciami (poza Regeneracją, która jest w KsiegaItem). */
public final class KsiegiHandler {
	private static final EquipmentSlot[] ARMOR = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
	/** 40% większa wytrzymałość = zwracamy punkt wytrzymałości z szansą 1 - 1/1.4. */
	private static final float DURABILITY_REFUND = 1.0F - 1.0F / 1.4F;
	public static final int MIM_ACTIVE_TICKS = 5 * 20;
	public static final int MIM_COOLDOWN_TICKS = 3 * 60 * 20;
	public static final float MIM_HP_LIMIT = 8.0F; // 4 serca

	private static final Map<UUID, List<ItemStack>> SAVED = new HashMap<>();
	private static final Map<UUID, Long> MIM_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> MIM_COOLDOWN = new HashMap<>();
	private static boolean inside = false;
	public static final int NIEW_TICKS = 5 * 20;
	public static final int NIEW_COOLDOWN = 4 * 60 * 20;
	private static final Map<UUID, Long> NIEW_UNTIL = new HashMap<>();
	private static final Map<UUID, Long> NIEW_COOLDOWN_MAP = new HashMap<>();
	private static final Map<Item, Item> SMELT = new HashMap<>();
	static {
		SMELT.put(Items.RAW_IRON, Items.IRON_INGOT);
		SMELT.put(Items.RAW_GOLD, Items.GOLD_INGOT);
		SMELT.put(Items.RAW_COPPER, Items.COPPER_INGOT);
		SMELT.put(Items.ANCIENT_DEBRIS, Items.NETHERITE_SCRAP);
		SMELT.put(Items.COBBLESTONE, Items.STONE);
		SMELT.put(Items.SAND, Items.GLASS);
	}

	private KsiegiHandler() {
	}

	private static boolean has(ItemStack s, KsiegaItem.Ench e) {
		return !s.isEmpty() && KsiegaItem.has(s, e);
	}

	private static boolean anyArmor(Player p, KsiegaItem.Ench e) {
		for (EquipmentSlot slot : ARMOR) {
			if (has(p.getItemBySlot(slot), e)) {
				return true;
			}
		}
		return false;
	}

	public static void initialize() {
		// Odbicie: 5% szans na zablokowanie całego ciosu.
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer victim && anyArmor(victim, KsiegaItem.ODBICIE)
					&& victim.getRandom().nextFloat() < 0.05F) {
				victim.sendSystemMessage(Component.literal("Odbiłeś cios!"), true);
				return false;
			}
			return true;
		});

		// Ułaskawienie: przedmioty z zaklęciem nie wypadają po śmierci i wracają po odrodzeniu.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof ServerPlayer p && !SAVED.containsKey(p.getUUID())) {
				List<ItemStack> kept = new ArrayList<>();
				var inv = p.getInventory();
				for (int i = 0; i < inv.getContainerSize(); i++) {
					ItemStack s = inv.getItem(i);
					if (has(s, KsiegaItem.ULASKAWIENIE)) {
						kept.add(s.copy());
						inv.setItem(i, ItemStack.EMPTY);
					}
				}
				if (!kept.isEmpty()) {
					SAVED.put(p.getUUID(), kept);
				}
			}
			return true;
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			List<ItemStack> kept = SAVED.remove(oldPlayer.getUUID());
			if (kept != null) {
				for (ItemStack s : kept) {
					if (!newPlayer.getInventory().add(s)) {
						newPlayer.drop(s, false);
					}
				}
			}
		});

		// Szczęście: podwójny drop z bytu (nie z gracza) zabitego mieczem lub narzędziem z zaklęciem.
		// Kradzież Życia Maksymalnego: po zabiciu bytu leczysz 1 serce.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && killer != entity && killer.isAlive()
					&& has(killer.getMainHandItem(), KsiegaItem.KZM)) {
				killer.heal(2.0F);
			}
			// Doświadczenie: +5 XP za byt zabity narzędziem z zaklęciem.
			if (source.getEntity() instanceof ServerPlayer xpKiller && xpKiller != entity
					&& has(xpKiller.getMainHandItem(), KsiegaItem.DOSWIADCZENIE)) {
				xpKiller.giveExperiencePoints(5);
			}
		});

		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(entity instanceof Player) && source.getEntity() instanceof ServerPlayer killer
					&& has(killer.getMainHandItem(), KsiegaItem.SZCZESCIE) && entity.level() instanceof ServerLevel level) {
				List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, entity.getBoundingBox().inflate(3.0),
						e -> e.tickCount < 5);
				for (ItemEntity d : drops) {
					level.addFreshEntity(new ItemEntity(level, d.getX(), d.getY(), d.getZ(), d.getItem().copy()));
				}
			}
		});

		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (blocked || damageTaken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			// --- strona atakującego ---
			if (!inside && source.getDirectEntity() instanceof Player attacker && attacker != entity) {
				ItemStack weapon = attacker.getMainHandItem();
				// Obrażenia krytyczne: 25% szans, że krytyk zadaje podwójnie.
				if (has(weapon, KsiegaItem.KRYTYCZNE) && entity.isAlive() && !attacker.onGround()
						&& attacker.getDeltaMovement().y < 0.0 && !attacker.isInWater() && !attacker.isSprinting()
						&& !attacker.isPassenger() && level.getRandom().nextFloat() < 0.25F) {
					inside = true;
					try {
						entity.invulnerableTime = 0;
						entity.hurtServer(level, source, damageTaken);
					} finally {
						inside = false;
					}
				}
				// Wampiryzm: leczenie 20% zadanych obrażeń.
				if (has(weapon, KsiegaItem.WAMPIRYZM) && attacker.isAlive()) {
					attacker.heal(damageTaken * 0.20F);
				}
				// Wytrzymałość broni.
				if (has(weapon, KsiegaItem.WYTRZYMALOSC) && weapon.getDamageValue() > 0
						&& level.getRandom().nextFloat() < DURABILITY_REFUND) {
					weapon.setDamageValue(weapon.getDamageValue() - 1);
				}
			}
			// --- strona obrywającego ---
			if (entity instanceof ServerPlayer victim && victim.isAlive()) {
				// Wytrzymałość zbroi.
				for (EquipmentSlot slot : ARMOR) {
					ItemStack a = victim.getItemBySlot(slot);
					if (has(a, KsiegaItem.WYTRZYMALOSC) && a.getDamageValue() > 0
							&& level.getRandom().nextFloat() < DURABILITY_REFUND) {
						a.setDamageValue(a.getDamageValue() - 1);
					}
				}
				// Wolność: 10% mniej obrażeń za każdą część zbroi z zaklęciem (max 40%).
				int pieces = 0;
				for (EquipmentSlot slot : ARMOR) {
					if (has(victim.getItemBySlot(slot), KsiegaItem.WOLNOSC)) {
						pieces++;
					}
				}
				if (pieces > 0) {
					victim.heal(damageTaken * 0.10F * pieces);
				}
				// Spadanie: 70% mniej obrażeń od upadku (także od stalagmitu).
				String msg = source.getMsgId();
				if (has(victim.getItemBySlot(EquipmentSlot.LEGS), KsiegaItem.SPADANIE)
						&& (msg.equals("fall") || msg.equals("stalagmite"))) {
					victim.heal(damageTaken * 0.70F);
				}
				// Zamrożenie i Pioruny (miecz w ręce): reakcja na cios wroga.
				if (source.getDirectEntity() instanceof LivingEntity hitter && hitter != victim && hitter.isAlive()) {
					ItemStack held = victim.getMainHandItem();
					if (has(held, KsiegaItem.ZAMROZENIE) && level.getRandom().nextFloat() < 0.05F) {
						EffectUtil.give(hitter, "slowness", 4 * 20, 6);
						EffectUtil.give(hitter, "jump_boost", 4 * 20, 128);
						victim.sendSystemMessage(Component.literal("Zamroziłeś przeciwnika na 4 sekundy!"), true);
					}
					if (has(held, KsiegaItem.PIORUNY) && level.getRandom().nextFloat() < 0.03F) {
						strike(level, hitter, victim);
					}
				}
				// Ciernie: 15% otrzymanych obrażeń wraca do atakującego.
				if (!inside && anyArmor(victim, KsiegaItem.CIERNIE) && source.getDirectEntity() instanceof LivingEntity thorn
						&& thorn != victim && thorn.isAlive()) {
					inside = true;
					try {
						thorn.hurtServer(level, level.damageSources().indirectMagic(victim, victim), damageTaken * 0.15F);
					} finally {
						inside = false;
					}
				}
				// Niewidzialność: poniżej 4 serc niewidzialność, Szybkość V, Regeneracja III, moby tracą cel; cooldown 4 min.
				long nowT = level.getServer().getTickCount();
				if (victim.getHealth() < MIM_HP_LIMIT && anyArmor(victim, KsiegaItem.NIEWIDZIALNOSC)
						&& nowT >= NIEW_COOLDOWN_MAP.getOrDefault(victim.getUUID(), 0L)) {
					NIEW_UNTIL.put(victim.getUUID(), nowT + NIEW_TICKS);
					NIEW_COOLDOWN_MAP.put(victim.getUUID(), nowT + NIEW_COOLDOWN);
					EffectUtil.give(victim, "invisibility", NIEW_TICKS, 0);
					EffectUtil.give(victim, "speed", NIEW_TICKS, 4);
					EffectUtil.give(victim, "regeneration", NIEW_TICKS, 2);
					victim.sendSystemMessage(Component.literal("Niewidzialność! Moby cię nie widzą przez 5 sekund!"), true);
				}
				// Moc Mima: poniżej 4 serc nieśmiertelność na 5 s, cooldown 3 min.
				long now = level.getServer().getTickCount();
				if (victim.getHealth() < MIM_HP_LIMIT && anyArmor(victim, KsiegaItem.MIM)
						&& now >= MIM_COOLDOWN.getOrDefault(victim.getUUID(), 0L)) {
					MIM_UNTIL.put(victim.getUUID(), now + MIM_ACTIVE_TICKS);
					MIM_COOLDOWN.put(victim.getUUID(), now + MIM_COOLDOWN_TICKS);
					victim.setInvulnerable(true);
					victim.sendSystemMessage(Component.literal("Moc Mima! Jesteś odporny na wszystko przez 5 sekund!"), true);
				}
			}
		});

		// Wytrzymałość narzędzi: zwrot punktu po zniszczeniu bloku.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, be) -> {
			ItemStack tool = player.getMainHandItem();
			if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
				boolean smelt = has(tool, KsiegaItem.AUTOWYTAPIANIE);
				boolean magnet = has(tool, KsiegaItem.MAGNES);
				if (smelt || magnet) {
					List<ItemEntity> drops = sl.getEntitiesOfClass(ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(1.5),
							e -> e.tickCount < 5);
					for (ItemEntity e : drops) {
						if (smelt) {
							Item result = SMELT.get(e.getItem().getItem());
							if (result != null) {
								e.setItem(new ItemStack(result, e.getItem().getCount()));
							}
						}
						if (magnet) {
							ItemStack stack = e.getItem().copy();
							sp.getInventory().add(stack);
							if (stack.isEmpty()) {
								e.discard();
							} else {
								e.setItem(stack);
							}
						}
					}
				}
				// Doświadczenie: +2 XP za każdy wykopany blok.
				if (has(tool, KsiegaItem.DOSWIADCZENIE)) {
					sp.giveExperiencePoints(2);
				}
			}
			if (has(tool, KsiegaItem.WYTRZYMALOSC) && tool.getDamageValue() > 0
					&& level.getRandom().nextFloat() < DURABILITY_REFUND) {
				tool.setDamageValue(tool.getDamageValue() - 1);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long now = server.getTickCount();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
				// Koniec Mocy Mima.
				Long until = MIM_UNTIL.get(id);
				if (until != null && now >= until) {
					MIM_UNTIL.remove(id);
					p.setInvulnerable(false);
				}
				if (!p.isAlive()) {
					continue;
				}
				// Serca: +4 serca łącznie, jeśli którakolwiek część zbroi ma zaklęcie (nie sumuje się).
				var maxHp = p.getAttribute(Attributes.MAX_HEALTH);
				if (maxHp != null) {
					Identifier mid = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "ksiega_serca");
					boolean on = anyArmor(p, KsiegaItem.SERCA);
					boolean hasMod = maxHp.getModifier(mid) != null;
					if (on && !hasMod) {
						maxHp.addTransientModifier(new AttributeModifier(mid, 8.0, AttributeModifier.Operation.ADD_VALUE));
					} else if (!on && hasMod) {
						maxHp.removeModifier(mid);
						if (p.getHealth() > p.getMaxHealth()) {
							p.setHealth(p.getMaxHealth());
						}
					}
				}
				// Niewidzialność: moby zapominają cel.
				Long niewUntil = NIEW_UNTIL.get(id);
				if (niewUntil != null) {
					if (now >= niewUntil) {
						NIEW_UNTIL.remove(id);
					} else if (p.level() instanceof ServerLevel pl) {
						for (Mob m : pl.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(32.0), mob -> mob.getTarget() == p)) {
							m.setTarget(null);
						}
					}
				}
				if (now % 10 == 0) {
					// Szybkość II na butach.
					if (has(p.getItemBySlot(EquipmentSlot.FEET), KsiegaItem.SZYBKOSC)) {
						p.addEffect(new MobEffectInstance(EffectUtil.get("speed"), 40, 1, true, false, true));
					}
					// Oddychanie na hełmie.
					if (has(p.getItemBySlot(EquipmentSlot.HEAD), KsiegaItem.ODDYCHANIE)) {
						p.addEffect(new MobEffectInstance(EffectUtil.get("water_breathing"), 40, 0, true, false, true));
					}
					// Niepodpalenie na hełmie.
					if (has(p.getItemBySlot(EquipmentSlot.HEAD), KsiegaItem.NIEPODPALENIE)) {
						p.addEffect(new MobEffectInstance(EffectUtil.get("fire_resistance"), 40, 0, true, false, true));
						p.clearFire();
					}
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				if (MIM_UNTIL.containsKey(p.getUUID())) {
					p.setInvulnerable(false);
				}
			}
			SAVED.clear();
			MIM_UNTIL.clear();
			MIM_COOLDOWN.clear();
			NIEW_UNTIL.clear();
			NIEW_COOLDOWN_MAP.clear();
		});
	}

	/** Piorun w atakującego: 90% jego HP, ogień w promieniu 5 bloków i zmutowany (naładowany) creeper. */
	private static void strike(ServerLevel level, LivingEntity target, ServerPlayer owner) {
		Vec3 at = target.position();
		LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level, EntitySpawnReason.TRIGGERED);
		if (bolt != null) {
			bolt.setPos(at.x, at.y, at.z);
			bolt.setVisualOnly(true);
			level.addFreshEntity(bolt);
		}
		DamageSource source = level.damageSources().indirectMagic(owner, owner);
		target.hurtServer(level, source, target.getHealth() * 0.9F);

		double r = 5.0;
		AABB area = new AABB(at.x - r, at.y - r, at.z - r, at.x + r, at.y + r, at.z + r);
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, area,
				x -> x.isAlive() && !x.isSpectator() && x != owner && x.position().distanceTo(at) <= r)) {
			e.igniteForSeconds(5.0F);
		}

		BlockPos center = BlockPos.containing(at);
		int ri = (int) Math.ceil(r);
		for (int dx = -ri; dx <= ri; dx++) {
			for (int dy = -ri; dy <= ri; dy++) {
				for (int dz = -ri; dz <= ri; dz++) {
					BlockPos pos = center.offset(dx, dy, dz);
					if (Vec3.atCenterOf(pos).distanceTo(at) > r || !level.isInWorldBounds(pos)
							|| !level.getBlockState(pos).isAir()) {
						continue;
					}
					BlockPos below = pos.below();
					if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
						level.setBlockAndUpdate(pos, Blocks.FIRE.defaultBlockState());
					}
				}
			}
		}

		// Zmutowany (naładowany) creeper z Minecrafta, nie z naszego moda.
		Creeper creeper = EntityType.CREEPER.create(level, EntitySpawnReason.TRIGGERED);
		if (creeper != null) {
			creeper.setPos(at.x + 2.0, at.y, at.z);
			level.addFreshEntity(creeper);
			if (bolt != null) {
				creeper.thunderHit(level, bolt);
			}
			creeper.setTarget(target);
		}
	}
}
