package com.example.mymod;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;

/**
 * Zmianki: 5% szans na 1-5 sztuk po zabiciu gracza. Prawy klik otwiera listę mieczy z ekwipunku;
 * kliknięcie miecza losuje mu dodatkowe procenty obrażeń od -10% do 60% (im wyższe, tym mniej szans).
 * Procenty są zapisane w opisie (lore) miecza w kolorze zależnym od wartości.
 */
public class ZmiankiItem extends Item {
	public static final String PREFIX = "Dodatkowe obrażenia: ";
	private static final Pattern PCT = Pattern.compile("Dodatkowe obrażenia: ([+-]?\\d+)%");
	public static final float DROP_CHANCE = 0.05F;
	private static boolean inside = false;

	public ZmiankiItem(Properties properties) {
		super(properties);
	}

	/** Czy przedmiot jest mieczem (wanilijne *_sword oraz miecze z moda). */
	public static boolean isSword(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		return path.endsWith("_sword") || path.contains("miecz") || path.equals("excalibur");
	}

	/** Procenty zapisane na mieczu (0, gdy brak). */
	public static int percentOf(ItemStack stack) {
		ItemLore lore = stack.get(DataComponents.LORE);
		if (lore == null) {
			return 0;
		}
		for (Component line : lore.lines()) {
			Matcher m = PCT.matcher(line.getString());
			if (m.find()) {
				try {
					return Integer.parseInt(m.group(1).replace("+", ""));
				} catch (NumberFormatException e) {
					return 0;
				}
			}
		}
		return 0;
	}

	private static ChatFormatting colorFor(int v) {
		if (v <= 0) {
			return ChatFormatting.RED;
		}
		if (v <= 10) {
			return ChatFormatting.WHITE;
		}
		if (v <= 25) {
			return ChatFormatting.YELLOW;
		}
		if (v <= 49) {
			return ChatFormatting.GOLD;
		}
		return ChatFormatting.RED;
	}

	/** Zapisuje procenty w lore miecza (zastępuje poprzednie). */
	public static void setPercent(ItemStack stack, int v) {
		List<Component> lines = new ArrayList<>();
		ItemLore old = stack.get(DataComponents.LORE);
		if (old != null) {
			for (Component line : old.lines()) {
				if (!line.getString().startsWith(PREFIX)) {
					lines.add(line);
				}
			}
		}
		String text = PREFIX + (v > 0 ? "+" : "") + v + "%";
		lines.add(Component.literal(text).withStyle(colorFor(v)));
		stack.set(DataComponents.LORE, new ItemLore(lines));
	}

	/** Losuje -10..60; im wyższa wartość, tym rzadsza (rozkład wykładniczy). */
	public static int roll(net.minecraft.util.RandomSource random) {
		for (int i = 0; i < 100; i++) {
			int v = -10 + (int) Math.floor(-Math.log(1.0 - random.nextDouble()) * 18.0);
			if (v <= 60) {
				return v;
			}
		}
		return 0;
	}

	public static void initialize() {
		// 5% szans na 1-5 zmianek po zabiciu gracza.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer victim && source.getEntity() instanceof ServerPlayer killer && killer != victim
					&& victim.level() instanceof ServerLevel level && level.getRandom().nextFloat() < DROP_CHANCE) {
				int count = 1 + level.getRandom().nextInt(5);
				ItemStack stack = new ItemStack(ModItems.ZMIANKI, count);
				if (!killer.getInventory().add(stack)) {
					level.addFreshEntity(new ItemEntity(level, killer.getX(), killer.getY(), killer.getZ(), stack));
				}
				killer.sendSystemMessage(Component.literal("Zdobyłeś Zmianki x" + count + "!"), true);
			}
		});

		// Dodatkowe procenty obrażeń z miecza (ujemne = osłabienie).
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (inside || blocked || damageTaken <= 0.0F || !(entity.level() instanceof ServerLevel level)) {
				return;
			}
			if (source.getDirectEntity() instanceof Player attacker && entity != attacker && entity.isAlive()) {
				int pct = percentOf(attacker.getMainHandItem());
				if (pct == 0 || !isSword(attacker.getMainHandItem())) {
					return;
				}
				inside = true;
				try {
					if (pct > 0) {
						entity.invulnerableTime = 0;
						entity.hurtServer(level, source, damageTaken * pct / 100.0F);
					} else {
						entity.heal(damageTaken * (-pct) / 100.0F);
					}
				} finally {
					inside = false;
				}
			}
		});
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		var container = new net.minecraft.world.SimpleContainer(27);
		int[] map = new int[27];
		int n = 0;
		for (int i = 0; i < 36 && n < 27; i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (isSword(s)) {
				container.setItem(n, s.copy());
				map[n] = i;
				n++;
			}
		}
		if (n == 0) {
			sp.sendSystemMessage(Component.literal("Nie masz żadnego miecza w ekwipunku!"), true);
			return InteractionResult.FAIL;
		}
		sp.sendSystemMessage(Component.literal("Wybierz broń, aby zmienić procenty"), true);
		sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ZmiankiMenu(id, inv, container, map),
				Component.literal("Wybierz broń, aby zmienić procenty")));
		return InteractionResult.SUCCESS;
	}
}
