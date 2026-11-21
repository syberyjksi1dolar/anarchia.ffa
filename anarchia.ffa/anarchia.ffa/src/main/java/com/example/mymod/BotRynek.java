package com.example.mymod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Handel botów-graczy na istniejącym rynku: wystawiają własne przedmioty i kupują od siebie za własne pieniądze. */
final class BotRynek {
	private BotRynek() {}

	private static final Random RNG = new Random();

	/** Wywoływane co 5 sekund. */
	static void cycle(MinecraftServer server) {
		for (Rynek.Offer o : Rynek.removeExpiredBotOffers()) {
			BotManager.addItem(o.seller.substring(4), o.item, o.count);
		}
		List<String> nicks = BotManager.aliveNicks();
		if (nicks.isEmpty()) return;
		sell(nicks.get(RNG.nextInt(nicks.size())));
		for (int k = 0; k < 2; k++) {
			buy(server, nicks.get(RNG.nextInt(nicks.size())));
		}
	}

	private static void sell(String nick) {
		if (RNG.nextDouble() > 0.35) return; // wystawiają rzadziej
		SimpleContainer inv = BotManager.inv(nick);
		List<Integer> ok = new ArrayList<>();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && s.getComponentsPatch().isEmpty() && Ceny.price(s) >= 0) ok.add(i);
		}
		if (ok.isEmpty()) return;
		List<Integer> kit = new ArrayList<>();
		for (int i : ok) {
			if (BotManager.KIT_PATHS.contains(BuiltInRegistries.ITEM.getKey(inv.getItem(i).getItem()).getPath())) kit.add(i);
		}
		int slot = (!kit.isEmpty() && RNG.nextDouble() < 0.6) ? kit.get(RNG.nextInt(kit.size())) : ok.get(RNG.nextInt(ok.size()));
		ItemStack st = inv.getItem(slot);
		long ref = Ceny.price(st);
		int cnt = 1 + RNG.nextInt(st.getCount());
		long price = Math.max(1, Math.round(ref * cnt * (0.8 + RNG.nextDouble() * 0.7)));
		String path = BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
		st.shrink(cnt);
		inv.setChanged();
		if (!Rynek.botList(nick, path, cnt, price)) {
			BotManager.addItem(nick, path, cnt); // nie wystawiono: przedmioty wracają
		}
	}

	private static void buy(MinecraftServer server, String buyer) {
		long money = Pieniadze.getById("bot:" + buyer);
		if (money <= 0) return;
		Rynek.Offer best = null;
		double bestScore = 0;
		for (Rynek.Offer o : Rynek.snapshot()) {
			if (o.seller == null || ("bot:" + buyer).equals(o.seller) || o.price > money) continue;
			net.minecraft.world.item.Item it = Rynek.itemOf(o.item);
			if (it == null) continue;
			long ref = Ceny.price(new ItemStack(it));
			if (ref < 0) ref = 50;
			double worth = (double) ref * o.count * 1.15;
			if (o.price > worth) continue;
			double score = worth / (double) o.price;
			if (score > bestScore) {
				bestScore = score;
				best = o;
			}
		}
		if (best != null) {
			Rynek.botBuy(server, best, buyer);
		}
	}
}
