package com.example.mymod;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Lista TAB: obok graczy widać boty z ich statystykami (poziom, zabójstwa, śmierci, pieniądze, sojusz).
 * Nick na czerwono oznacza, że bota nie ma teraz na świecie (czeka na odrodzenie). Wpisy są wysyłane
 * pakietami tylko do graczy, więc nie wpływają na prawdziwą listę graczy serwera.
 * Pakiet i wpisy są budowane refleksją, żeby kod nie zależał od drobnych zmian w sygnaturach Minecrafta.
 */
final class BotTab {
	private BotTab() {}

	private static boolean failed;

	static void initialize() {
		ServerPlayConnectionEvents.JOIN.register((h, snd, srv) -> send(h.getPlayer()));
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (srv.getTickCount() % 100 != 53) return;
			for (ServerPlayer p : srv.getPlayerList().getPlayers()) send(p);
		});
	}

	private static UUID idOf(String nick) {
		return UUID.nameUUIDFromBytes(("anarchia_bot:" + nick).getBytes(StandardCharsets.UTF_8));
	}

	private static Component label(BotManager.TabRow r) {
		MutableComponent c = Component.literal("[" + r.level() + "] ").withStyle(ChatFormatting.GRAY);
		c.append(Component.literal(r.nick()).withStyle(r.online() ? ChatFormatting.WHITE : ChatFormatting.RED));
		if (r.clan() != null) c.append(Component.literal(" [" + r.clan() + "]").withStyle(ChatFormatting.AQUA));
		c.append(Component.literal(" K:" + r.kills()).withStyle(ChatFormatting.GREEN));
		c.append(Component.literal(" D:" + r.deaths()).withStyle(ChatFormatting.DARK_RED));
		c.append(Component.literal(" " + Pieniadze.formatShort(r.money())).withStyle(ChatFormatting.GOLD));
		return c;
	}

	private static ClientboundPlayerInfoUpdatePacket.Entry entry(BotManager.TabRow r) throws Exception {
		Class<?> c = ClientboundPlayerInfoUpdatePacket.Entry.class;
		RecordComponent[] rc = c.getRecordComponents();
		Class<?>[] types = new Class<?>[rc.length];
		Object[] args = new Object[rc.length];
		UUID id = idOf(r.nick());
		int boolIdx = 0, intIdx = 0;
		for (int i = 0; i < rc.length; i++) { // dopasowanie po typie (nazwy pól mogą się różnić między wersjami)
			Class<?> t = rc[i].getType();
			types[i] = t;
			Object v = null;
			if (t == UUID.class) v = id;
			else if (t == GameProfile.class) v = new GameProfile(id, r.nick());
			else if (t == GameType.class) v = GameType.SURVIVAL;
			else if (t == Component.class) v = label(r);
			else if (t == boolean.class) v = boolIdx++ == 0 ? Boolean.TRUE : Boolean.FALSE; // listed, potem showHat
			else if (t == int.class) v = intIdx++ == 0 ? Integer.valueOf(0) : Integer.valueOf(r.online() ? -1 : -2); // latency, potem listOrder
			args[i] = v;
		}
		Constructor<?> k = c.getDeclaredConstructor(types);
		k.setAccessible(true);
		return (ClientboundPlayerInfoUpdatePacket.Entry) k.newInstance(args);
	}

	private static Constructor<ClientboundPlayerInfoUpdatePacket> packetCtor() throws Exception {
		for (Constructor<?> k : ClientboundPlayerInfoUpdatePacket.class.getDeclaredConstructors()) {
			Class<?>[] pt = k.getParameterTypes();
			if (pt.length == 2 && pt[0] == EnumSet.class && List.class.isAssignableFrom(pt[1])) {
				k.setAccessible(true);
				@SuppressWarnings("unchecked")
				Constructor<ClientboundPlayerInfoUpdatePacket> ck = (Constructor<ClientboundPlayerInfoUpdatePacket>) k;
				// konstruktor (EnumSet, Collection<ServerPlayer>) też pasuje do List - pomijamy go po typie elementu w razie błędu przy wywołaniu
				return ck;
			}
		}
		throw new NoSuchMethodException("brak konstruktora (EnumSet, List) w ClientboundPlayerInfoUpdatePacket");
	}

	private static int logged;

	private static void send(ServerPlayer p) {
		if (failed) return;
		try {
			List<ClientboundPlayerInfoUpdatePacket.Entry> entries = new ArrayList<>();
			for (BotManager.TabRow r : BotManager.tabRows()) entries.add(entry(r));
			if (entries.isEmpty()) return;
			EnumSet<ClientboundPlayerInfoUpdatePacket.Action> acts = EnumSet.noneOf(ClientboundPlayerInfoUpdatePacket.Action.class);
			for (String n : new String[] {"ADD_PLAYER", "UPDATE_LISTED", "UPDATE_LATENCY", "UPDATE_GAME_MODE", "UPDATE_DISPLAY_NAME", "UPDATE_LIST_ORDER"}) {
				try {
					acts.add(ClientboundPlayerInfoUpdatePacket.Action.valueOf(n));
				} catch (IllegalArgumentException ignored) {
					// ta wersja gry nie ma takiej akcji
				}
			}
			p.connection.send(packetCtor().newInstance(acts, entries));
			if (logged++ < 3) MyMod.LOGGER.info("Lista TAB: wysłano {} botów do {} (akcje: {})", entries.size(), p.getName().getString(), acts);
		} catch (NoSuchMethodException e) {
			failed = true; // tej wersji gry nie da się obsłużyć - nie ponawiamy
			MyMod.LOGGER.warn("Lista TAB z botami wyłączona (brak konstruktora pakietu): {}", e.toString());
		} catch (Throwable e) {
			if (logged++ < 6) MyMod.LOGGER.warn("Lista TAB z botami: błąd wysyłania, spróbuję ponownie za 5 s", e);
		}
	}
}
