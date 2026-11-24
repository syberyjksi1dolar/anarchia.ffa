package com.example.mymod;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * Rozwój botów: przy swoim warsztacie (piec + stół, budowane przez BotBuilder) przetapiają rudy i gotują jedzenie,
 * craftują lepsze narzędzia i zbroję (kamień, żelazo, diament) i zakładają je na siebie.
 * Drewno na deski i kije bot przerabia w ręku, bez warsztatu. Wszystko tylko z materiałów z jego ekwipunku.
 */
final class BotCraft {
	private BotCraft() {}

	private static final Map<String, Integer> CNT = new HashMap<>();

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	/** Zbroja wg slotu: skórzana, kolcza, żelazna, diamentowa, netherytowa (indeks = ranga). */
	private static final Item[][] ARMOR = {
			{Items.LEATHER_HELMET, Items.CHAINMAIL_HELMET, Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET},
			{Items.LEATHER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE, Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE},
			{Items.LEATHER_LEGGINGS, Items.CHAINMAIL_LEGGINGS, Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS},
			{Items.LEATHER_BOOTS, Items.CHAINMAIL_BOOTS, Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS}};
	private static final Item[] SWORDS = {Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};
	private static final Item[] PICKS = {Items.WOODEN_PICKAXE, Items.STONE_PICKAXE, Items.IRON_PICKAXE, Items.DIAMOND_PICKAXE, Items.NETHERITE_PICKAXE};

	/** kind: 0 miecz, 1 kilof, 2-5 hełm, napierśnik, spodnie, buty. */
	private record Craft(Item out, Item mat, int matCount, int sticks, int rank, int kind) {}

	private static final Craft[] CRAFTS = {
			new Craft(Items.DIAMOND_CHESTPLATE, Items.DIAMOND, 8, 0, 3, 3), new Craft(Items.DIAMOND_LEGGINGS, Items.DIAMOND, 7, 0, 3, 4),
			new Craft(Items.DIAMOND_HELMET, Items.DIAMOND, 5, 0, 3, 2), new Craft(Items.DIAMOND_BOOTS, Items.DIAMOND, 4, 0, 3, 5),
			new Craft(Items.DIAMOND_SWORD, Items.DIAMOND, 2, 1, 3, 0), new Craft(Items.DIAMOND_PICKAXE, Items.DIAMOND, 3, 2, 3, 1),
			new Craft(Items.IRON_CHESTPLATE, Items.IRON_INGOT, 8, 0, 2, 3), new Craft(Items.IRON_LEGGINGS, Items.IRON_INGOT, 7, 0, 2, 4),
			new Craft(Items.IRON_HELMET, Items.IRON_INGOT, 5, 0, 2, 2), new Craft(Items.IRON_BOOTS, Items.IRON_INGOT, 4, 0, 2, 5),
			new Craft(Items.IRON_SWORD, Items.IRON_INGOT, 2, 1, 2, 0), new Craft(Items.IRON_PICKAXE, Items.IRON_INGOT, 3, 2, 2, 1),
			new Craft(Items.STONE_SWORD, Items.COBBLESTONE, 2, 1, 1, 0), new Craft(Items.STONE_PICKAXE, Items.COBBLESTONE, 3, 2, 1, 1)};

	/** Przetapianie: surowiec -> produkt (jeden węgiel/węgiel drzewny na 8 sztuk). */
	private static final Item[][] SMELT = {
			{Items.RAW_IRON, Items.IRON_INGOT}, {Items.RAW_COPPER, Items.COPPER_INGOT}, {Items.RAW_GOLD, Items.GOLD_INGOT},
			{Items.BEEF, Items.COOKED_BEEF}, {Items.PORKCHOP, Items.COOKED_PORKCHOP}, {Items.CHICKEN, Items.COOKED_CHICKEN},
			{Items.MUTTON, Items.COOKED_MUTTON}, {Items.RABBIT, Items.COOKED_RABBIT}, {Items.COD, Items.COOKED_COD},
			{Items.SALMON, Items.COOKED_SALMON}, {Items.POTATO, Items.BAKED_POTATO}};

	private static int count(SimpleContainer c, Item it) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(it)) n += c.getItem(i).getCount();
		return n;
	}

	private static void remove(SimpleContainer c, Item it, int n) {
		for (int i = 0; i < c.getContainerSize() && n > 0; i++) {
			ItemStack s = c.getItem(i);
			if (!s.is(it)) continue;
			int t = Math.min(n, s.getCount());
			s.shrink(t);
			n -= t;
			if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
		}
		c.setChanged();
	}

	private static int rankIn(Item[] arr, ItemStack s) {
		if (s.isEmpty()) return -1;
		for (int i = 0; i < arr.length; i++) if (s.is(arr[i])) return i;
		return -1;
	}

	private static int bestInv(SimpleContainer c, Item[] arr) {
		int best = -1;
		for (int i = 0; i < c.getContainerSize(); i++) best = Math.max(best, rankIn(arr, c.getItem(i)));
		return best;
	}

	private static int bestRank(Mannequin m, SimpleContainer c, int kind) {
		if (kind == 0) return Math.max(rankIn(SWORDS, m.getMainHandItem()), bestInv(c, SWORDS));
		if (kind == 1) return bestInv(c, PICKS);
		Item[] arr = ARMOR[kind - 2];
		return Math.max(rankIn(arr, m.getItemBySlot(SLOTS[kind - 2])), bestInv(c, arr));
	}

	private static int fuelCount(SimpleContainer c) {
		return count(c, Items.COAL) + count(c, Items.CHARCOAL);
	}

	private static int smeltIndex(SimpleContainer c) {
		if (fuelCount(c) < 1) return -1;
		for (int i = 0; i < SMELT.length; i++) if (count(c, SMELT[i][0]) > 0) return i;
		return -1;
	}

	private static int craftIndex(Mannequin m, SimpleContainer c) {
		for (int i = 0; i < CRAFTS.length; i++) {
			Craft cr = CRAFTS[i];
			if (cr.rank() <= bestRank(m, c, cr.kind())) continue;
			if (count(c, cr.mat()) < cr.matCount()) continue;
			if (cr.sticks() > 0 && count(c, Items.STICK) < cr.sticks()) continue;
			return i;
		}
		return -1;
	}

	/** Co 0,5 s dla bota, który nie walczy, nie kopie i nie buduje. */
	static void tick(ServerLevel level, String nick, Mannequin m) {
		int c = CNT.merge(nick, 1, Integer::sum);
		SimpleContainer inv = BotManager.inv(nick);
		try {
			boolean slow = c % 10 == 0; // co ok. 5 s: obróbka w ręku i zakładanie sprzętu
			if (slow) {
				handCraft(inv);
				refit(m, inv);
			}
			int smelt = smeltIndex(inv);
			int craft = craftIndex(m, inv);
			if (smelt < 0 && craft < 0) return;
			int[] ws = BotManager.workshop(nick);
			if (ws[3] == 0) return; // bez warsztatu (jeszcze) nic nie zrobi
			BlockPos fp = new BlockPos(ws[0], ws[1], ws[2]);
			BlockPos tp = fp.east();
			if (!level.hasChunkAt(fp)) return;
			if (!level.getBlockState(fp).is(Blocks.FURNACE) || !level.getBlockState(tp).is(Blocks.CRAFTING_TABLE)) {
				BotManager.setWorkshop(nick, 0, 0, 0, false); // zniszczony: zbuduje nowy
				return;
			}
			double d2 = m.distanceToSqr(fp.getX() + 1.0, fp.getY(), fp.getZ() + 1.5);
			if (d2 > 9.0) { // idzie do warsztatu
				BotManager.goTo(nick, fp.getX() + 1.0, fp.getZ() + 2.0);
				return;
			}
			if (c % 10 != 5) return; // pracuje co ok. 5 s
			double dx = fp.getX() + 1.0 - m.getX(), dz = fp.getZ() + 0.5 - m.getZ();
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			m.setYRot(yaw);
			m.setYHeadRot(yaw);
			m.setYBodyRot(yaw);
			m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			if (smelt >= 0) doSmelt(inv, smelt);
			if (craft >= 0) doCraft(inv, craft);
			refit(m, inv);
		} catch (Exception e) {
			MyMod.LOGGER.warn("Crafting bota {}: {}", nick, e.toString());
		}
	}

	/** W ręku: kłody na deski (gdy jest ich dużo) i deski na kije. */
	private static void handCraft(SimpleContainer inv) {
		if (count(inv, Items.OAK_LOG) >= 40 && count(inv, Items.OAK_PLANKS) < 300) {
			remove(inv, Items.OAK_LOG, 4);
			inv.addItem(new ItemStack(Items.OAK_PLANKS, 16));
		}
		if (count(inv, Items.STICK) < 8 && count(inv, Items.OAK_PLANKS) >= 40) {
			remove(inv, Items.OAK_PLANKS, 2);
			inv.addItem(new ItemStack(Items.STICK, 4));
		}
	}

	private static void doSmelt(SimpleContainer inv, int idx) {
		Item in = SMELT[idx][0], out = SMELT[idx][1];
		int n = Math.min(8, count(inv, in));
		if (n <= 0) return;
		if (count(inv, Items.COAL) > 0) remove(inv, Items.COAL, 1);
		else remove(inv, Items.CHARCOAL, 1);
		remove(inv, in, n);
		inv.addItem(new ItemStack(out, n));
	}

	private static void doCraft(SimpleContainer inv, int idx) {
		Craft cr = CRAFTS[idx];
		if (count(inv, cr.mat()) < cr.matCount()) return;
		if (cr.sticks() > 0 && count(inv, Items.STICK) < cr.sticks()) return;
		remove(inv, cr.mat(), cr.matCount());
		if (cr.sticks() > 0) remove(inv, Items.STICK, cr.sticks());
		inv.addItem(new ItemStack(cr.out()));
	}

	/** Zakłada na siebie lepszą zbroję i lepszy miecz z ekwipunku; stary sprzęt wraca do plecaka. */
	private static void refit(Mannequin m, SimpleContainer inv) {
		for (int k = 0; k < 4; k++) {
			int cur = rankIn(ARMOR[k], m.getItemBySlot(SLOTS[k]));
			int best = bestInv(inv, ARMOR[k]);
			if (best > cur) {
				remove(inv, ARMOR[k][best], 1);
				ItemStack old = m.getItemBySlot(SLOTS[k]);
				if (!old.isEmpty()) inv.addItem(old.copy());
				m.setItemSlot(SLOTS[k], new ItemStack(ARMOR[k][best]));
			}
		}
		ItemStack main = m.getMainHandItem();
		int mr = rankIn(SWORDS, main);
		if (main.isEmpty() || mr >= 0) { // nie rusza eventówkowej broni w ręku
			int best = bestInv(inv, SWORDS);
			if (best > mr) {
				remove(inv, SWORDS[best], 1);
				if (!main.isEmpty()) inv.addItem(main.copy());
				m.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(SWORDS[best]));
			}
		}
	}
}
