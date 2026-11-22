package com.example.mymod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

/** Boty piszą na czacie jak gracze: zagajają, odpowiadają na wiadomości i reagują na wydarzenia. */
public final class BotChat {
	private BotChat() {}

	private static final Random RNG = new Random();

	private record Msg(long at, String nick, String text) {}

	private static final List<Msg> QUEUE = new ArrayList<>();

	private static final String[] RANDOM = {"siema", "elo", "co tam?", "kto kupi diamenty?", "szukam żelaza", "buduje dom xd", "ktoś chce się zaprzyjaźnić?",
			"idę kopać", "ale tu cicho", "ktoś na rynku?", "gg", "ale farma mi rośnie", "potrzebuje drewna", "uważajcie na creepery", "xd",
			"mam coś na sprzedaż, sprawdźcie rynek", "ktoś wie gdzie diamenty?", "rtp i lecę", "heh", "dobra, wracam do roboty"};
	private static final String[] HELLO = {"siema", "elo", "hej", "cześć", "witam", "no hej"};
	private static final String[] SHOP = {"sprawdź rynek", "zajrzyj na rynek, coś tam wystawiłem", "na rynku coś znajdziesz", "mam kilka rzeczy na rynku"};
	private static final String[] UNSURE = {"nie wiem xd", "hmm", "no nie wiem", "a kto wie", "możliwe", "lol", "xd", "serio?"};
	private static final String[] FUN = {"xd", "lol", "no", "haha", "gg", "heh", "nice"};

	public static void say(MinecraftServer server, String nick, String text) {
		server.getPlayerList().broadcastSystemMessage(Component.literal("<" + nick + "> " + text), false);
	}

	private static String pick(String[] a) {
		return a[RNG.nextInt(a.length)];
	}

	/** Wydarzenia: "death" (zginął bot), "ally" (zawarto sojusz), "rtp" (bot rusza w wyprawę). */
	public static void event(MinecraftServer server, String kind, String nick) {
		List<String> alive = BotManager.aliveNicks();
		alive.remove(nick);
		if (alive.isEmpty()) return;
		String who = alive.get(RNG.nextInt(alive.size()));
		long at = server.getTickCount() + 20 + RNG.nextInt(60);
		switch (kind) {
			case "death" -> { if (RNG.nextInt(3) == 0) QUEUE.add(new Msg(at, who, RNG.nextBoolean() ? "F" : "rip " + nick)); }
			case "ally" -> { if (RNG.nextInt(2) == 0) QUEUE.add(new Msg(at, who, "gg")); }
			case "rtp" -> { if (RNG.nextInt(3) == 0) QUEUE.add(new Msg(server.getTickCount() + 10, nick, "rtp, lecę w świat")); }
			default -> { }
		}
	}

	public static void initialize() {
		ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
			MinecraftServer server = sender.level().getServer();
			if (server == null) return;
			String text = message.decoratedContent().getString().toLowerCase();
			List<String> alive = BotManager.aliveNicks();
			if (alive.isEmpty()) return;
			String who = null;
			for (String n : alive) if (text.contains(n.toLowerCase())) who = n; // ktoś woła bota po nicku
			boolean called = who != null;
			if (!called && RNG.nextInt(100) >= 25) return; // bez wołania odpowiadają rzadko
			if (who == null) who = alive.get(RNG.nextInt(alive.size()));
			String reply;
			if (text.contains("siema") || text.contains("hej") || text.contains("elo") || text.contains("cześć") || text.contains("czesc")) reply = pick(HELLO);
			else if (text.contains("kup") || text.contains("sprzed") || text.contains("rynek") || text.contains("sklep")) reply = pick(SHOP);
			else if (text.contains("?")) reply = pick(UNSURE);
			else reply = pick(FUN);
			QUEUE.add(new Msg(server.getTickCount() + 30 + RNG.nextInt(60), who, reply));
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			long t = server.getTickCount();
			if (t % 400 == 0 && RNG.nextInt(100) < 50) { // co ~20 s ktoś coś napisze
				List<String> alive = BotManager.aliveNicks();
				if (!alive.isEmpty()) QUEUE.add(new Msg(t + 5, alive.get(RNG.nextInt(alive.size())), pick(RANDOM)));
			}
			for (int i = QUEUE.size() - 1; i >= 0; i--) {
				Msg m = QUEUE.get(i);
				if (m.at() <= t) {
					QUEUE.remove(i);
					say(server, m.nick(), m.text());
				}
			}
		});
	}
}
