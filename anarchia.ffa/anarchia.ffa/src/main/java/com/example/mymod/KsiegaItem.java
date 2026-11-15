package com.example.mymod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Księgi z własnymi zaklęciami. Książka w jednej ręce, przedmiot w drugiej, prawy klik = zaklęcie trafia na
 * przedmiot (zapisane w jego opisie). Księga Regeneracji: zbroja na klatę, 2x szybsza regeneracja serc,
 * 2% szans na wypadnięcie z każdego zabitego bytu.
 */
public class KsiegaItem extends Item {
	/** Opis zaklęcia i warunek, na jakie przedmioty można je nałożyć. */
	public record Ench(String name, java.util.function.Predicate<ItemStack> target, String targetText) {
	}

	public static final Ench REGENERACJA = new Ench("Regeneracja", s -> {
		Equippable eq = s.get(DataComponents.EQUIPPABLE);
		return eq != null && eq.slot() == EquipmentSlot.CHEST;
	}, "klatę (zbroję na tors)");

	public static final Ench KRYTYCZNE = new Ench("Obrażenia Krytyczne", s -> ZmiankiItem.isSword(s), "miecz");
	public static final Ench ULASKAWIENIE = new Ench("Ułaskawienie", s -> !s.isEmpty(), "dowolny przedmiot");
	public static final Ench WAMPIRYZM = new Ench("Wampiryzm", s -> ZmiankiItem.isSword(s), "miecz");
	public static final Ench WYTRZYMALOSC = new Ench("Wytrzymałość", s -> s.isDamageableItem(), "zbroję albo narzędzie");
	public static final Ench SZCZESCIE = new Ench("Szczęście", s -> ZmiankiItem.isSword(s) || s.has(DataComponents.TOOL), "miecz albo narzędzie");
	public static final Ench SZYBKOSC = new Ench("Szybkość", s -> slot(s) == EquipmentSlot.FEET, "buty");
	public static final Ench WOLNOSC = new Ench("Wolność", s -> isArmor(s), "część zbroi (hełm, klata, spodnie, buty)");
	public static final Ench SPADANIE = new Ench("Spadanie", s -> slot(s) == EquipmentSlot.LEGS, "spodnie");
	public static final Ench NIEPODPALENIE = new Ench("Niepodpalenie", s -> slot(s) == EquipmentSlot.HEAD, "hełm");
	public static final Ench MIM = new Ench("Moc Mima", s -> isArmor(s), "część zbroi");

	/** Slot, w którym zakłada się przedmiot (null, jeśli to nie zbroja). */
	public static EquipmentSlot slot(ItemStack s) {
		Equippable eq = s.get(DataComponents.EQUIPPABLE);
		return eq == null ? null : eq.slot();
	}

	public static boolean isArmor(ItemStack s) {
		EquipmentSlot sl = slot(s);
		return sl == EquipmentSlot.HEAD || sl == EquipmentSlot.CHEST || sl == EquipmentSlot.LEGS || sl == EquipmentSlot.FEET;
	}

	public static final Ench ZAMROZENIE = new Ench("Zamrożenie", s -> ZmiankiItem.isSword(s), "miecz");
	public static final Ench KZM = new Ench("Kradzież Życia Maksymalnego", s -> ZmiankiItem.isSword(s), "miecz");
	public static final Ench PIORUNY = new Ench("Pioruny", s -> ZmiankiItem.isSword(s), "miecz");

	public static final Ench CIERNIE = new Ench("Ciernie", s -> isArmor(s), "część zbroi");
	public static final Ench SERCA = new Ench("Serca", s -> isArmor(s), "część zbroi");
	public static final Ench ODBICIE = new Ench("Odbicie", s -> isArmor(s), "część zbroi");
	public static final Ench NIEWIDZIALNOSC = new Ench("Niewidzialność", s -> isArmor(s), "część zbroi");
	public static final Ench ODDYCHANIE = new Ench("Oddychanie", s -> slot(s) == EquipmentSlot.HEAD, "hełm");
	public static final Ench AUTOWYTAPIANIE = new Ench("Auto-Wytapianie", s -> s.has(DataComponents.TOOL), "narzędzie (kilof, siekierę, łopatę)");
	public static final Ench MAGNES = new Ench("Magnes", s -> s.has(DataComponents.TOOL), "narzędzie (kilof, siekierę, łopatę)");
	public static final Ench DOSWIADCZENIE = new Ench("Doświadczenie", s -> s.has(DataComponents.TOOL), "narzędzie (kilof, siekierę, łopatę)");

	public static final float DROP_CHANCE = 0.02F;

	private static final Map<UUID, Float> LAST_HEALTH = new HashMap<>();

	private final Ench ench;

	public KsiegaItem(Properties properties, Ench ench) {
		super(properties);
		this.ench = ench;
	}

	public static boolean has(ItemStack stack, Ench ench) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return false;
		}
		for (Component line : lore.lines()) {
			if (line.getString().equals(ench.name())) {
				return true;
			}
		}
		return false;
	}

	public static void initialize() {
		// 2% szans na księgę z każdego bytu zabitego przez gracza.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (source.getEntity() instanceof ServerPlayer killer && entity != killer
					&& entity.level() instanceof ServerLevel level && level.getRandom().nextFloat() < DROP_CHANCE) {
				Block.popResource(level, entity.blockPosition(), new ItemStack(ModItems.KSIEGA_REGENERACJI));
				killer.sendSystemMessage(Component.literal("Wypadła Księga Regeneracji!"), true);
			}
		});

		// Regeneracja: podwajamy każde niewielkie leczenie gracza z zaklętą klatą (naturalna regeneracja).
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
				float h = p.getHealth();
				Float last = LAST_HEALTH.get(id);
				if (last != null && p.isAlive() && h > last && h - last <= 1.01F
						&& has(p.getItemBySlot(EquipmentSlot.CHEST), REGENERACJA)) {
					p.setHealth(Math.min(p.getMaxHealth(), h + (h - last)));
					h = p.getHealth();
				}
				LAST_HEALTH.put(id, h);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST_HEALTH.clear());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack target = player.getItemInHand(other);
		if (target.isEmpty() || !ench.target().test(target)) {
			sp.sendSystemMessage(Component.literal("Weź przedmiot do drugiej ręki: " + ench.name() + " nałożysz na " + ench.targetText() + "."), true);
			return InteractionResult.FAIL;
		}
		if (has(target, ench)) {
			sp.sendSystemMessage(Component.literal("Ten przedmiot ma już zaklęcie " + ench.name() + "!"), true);
			return InteractionResult.FAIL;
		}
		List<Component> lines = new ArrayList<>();
		ItemLore old = target.get(DataComponents.LORE);
		if (old != null) {
			lines.addAll(old.lines());
		}
		lines.add(Component.literal(ench.name()).withStyle(ChatFormatting.LIGHT_PURPLE));
		target.set(DataComponents.LORE, new ItemLore(lines));
		player.getItemInHand(hand).shrink(1);
		sp.sendSystemMessage(Component.literal("Nałożono zaklęcie: " + ench.name()), true);
		return InteractionResult.SUCCESS;
	}
}
