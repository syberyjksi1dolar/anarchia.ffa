package com.example.mymod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * Złoty Bilet: pojawia się w skrzyni 67 67 67 dwa razy dziennie (losowo 01:00-05:00 oraz 16:00-24:00),
 * po użyciu otwiera GUI 27 slotów z nagrodami do wyboru.
 */
public final class ZlotyBilet {
	private ZlotyBilet() {}

	private static final BlockPos CHEST = new BlockPos(67, 67, 67);
	private static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Random RNG = new Random();

	private static final class Data {
		String date = "";
		int t1, t2;           // minuta doby
		boolean done1, done2;
	}

	private static Data data = new Data();
	private static final Set<UUID> PENDING = new HashSet<>();


	private static final int[] SLOTS = {1, 3, 5, 7, 10, 12, 14, 16, 19, 21, 23, 25};

	private static List<ItemStack> reward(int i) {
		switch (i) {
			case 0: return List.of(new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 64));
			case 1: {
				var pieces = new net.minecraft.world.item.Item[] {ModItems.ZBROJA_DOLARA_HELM, ModItems.ZBROJA_DOLARA_KLATA,
						ModItems.ZBROJA_DOLARA_SPODNIE, ModItems.ZBROJA_DOLARA_BUTY};
				return List.of(new ItemStack(pieces[RNG.nextInt(pieces.length)]));
			}
			case 2: return List.of(new ItemStack(ModItems.ANARCHICZNY_KILOF));
			case 3: return List.of(new ItemStack(ModItems.LIZAK));
			case 4: return List.of(new ItemStack(ModItems.KREW_WAMPIRA, 16));
			case 5: return List.of(new ItemStack(ModItems.MIM_HELM), new ItemStack(ModItems.MIM_KLATA),
					new ItemStack(ModItems.MIM_SPODNIE), new ItemStack(ModItems.MIM_BUTY));
			case 6: return List.of(new ItemStack(ModItems.PIRAT_HELM), new ItemStack(ModItems.PIRAT_KLATA),
					new ItemStack(ModItems.PIRAT_SPODNIE), new ItemStack(ModItems.PIRAT_BUTY));
			case 7: return List.of(new ItemStack(ModItems.KLUCZ_DOLAROWA, 16));
			case 8: return List.of(new ItemStack(ModItems.KLUCZ_ANARCHICZNA, 32));
			case 9: return List.of(new ItemStack(ModItems.RANGA_GVIP));
			case 10: return List.of(new ItemStack(ModItems.PET_DRAKULA));
			default: return List.of(new ItemStack(ModItems.KSIEGA_ULASKAWIENIE, 4));
		}
	}

	private static final String[] LABELS = {"64 zaklęte złote jabłka", "1 przedmiot z dolarowego zestawu", "Anarchiczny kilof",
			"Lizak", "16 krwi wampira", "Kostium Mima", "Kostium Pirata", "16 dolarowych kluczy",
			"32 anarchiczne klucze", "Ranga GVIP", "Pet Dracula", "4 księgi ułaskawienia"};

	public static boolean openRewards(ServerPlayer p) {
		SimpleContainer box = new SimpleContainer(27);
		for (int i = 0; i < SLOTS.length; i++) {
			ItemStack ic = reward(i).get(0).copy();
			ic.set(DataComponents.CUSTOM_NAME, Component.literal(LABELS[i]).withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
			ic.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("Kliknij, aby wybrać tę nagrodę.").withStyle(ChatFormatting.GRAY))));
			box.setItem(SLOTS[i], ic);
		}
		PENDING.add(p.getUUID());
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new RangiMenu(id, inv, box, ZlotyBilet::click),
				Component.literal("Złoty Bilet").withStyle(ChatFormatting.GOLD)));
		return true;
	}

	private static void click(ServerPlayer p, int index) {
		int pick = -1;
		for (int i = 0; i < SLOTS.length; i++) if (SLOTS[i] == index) pick = i;
		if (pick < 0 || !PENDING.remove(p.getUUID())) return;
		for (ItemStack s : reward(pick)) {
			ItemStack c = s.copy();
			if (!p.getInventory().add(c)) p.drop(c, false);
		}
		p.sendSystemMessage(Component.literal("Odebrano nagrodę: " + LABELS[pick]).withStyle(ChatFormatting.GOLD));
		p.closeContainer();
	}

	// ---------- harmonogram ----------
	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("anarchia_ffa_bilet.json");
	}

	private static void load() {
		try {
			if (Files.exists(file())) {
				Data d = GSON.fromJson(Files.readString(file(), StandardCharsets.UTF_8), Data.class);
				if (d != null) data = d;
			}
		} catch (Exception e) {
			MyMod.LOGGER.warn("Bilet: nie wczytano: {}", e.toString());
		}
	}

	private static void save() {
		try {
			Files.writeString(file(), GSON.toJson(data), StandardCharsets.UTF_8);
		} catch (IOException e) {
			MyMod.LOGGER.warn("Bilet: nie zapisano: {}", e.toString());
		}
	}

	public static void initialize() {
		load();
		ServerTickEvents.END_SERVER_TICK.register(ZlotyBilet::tick);
	}

	private static void tick(MinecraftServer server) {
		if (server.getTickCount() % 200 != 0) return;
		ZonedDateTime now = ZonedDateTime.now(ZONE);
		String today = LocalDate.now(ZONE).toString();
		if (!today.equals(data.date)) {
			data = new Data();
			data.date = today;
			data.t1 = 60 + RNG.nextInt(240);    // 01:00-04:59
			data.t2 = 960 + RNG.nextInt(480);   // 16:00-23:59
			save();
		}
		int minute = now.getHour() * 60 + now.getMinute();
		if (!data.done1 && minute >= data.t1 && minute < 300 && place(server)) {
			data.done1 = true;
			save();
		}
		if (!data.done2 && minute >= data.t2 && place(server)) {
			data.done2 = true;
			save();
		}
	}

	/** Wkłada jeden bilet do skrzyni; false, jeśli skrzyni nie ma lub jest pełna. */
	private static boolean place(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level == null) return false;
		level.getChunkAt(CHEST);
		if (!(level.getBlockEntity(CHEST) instanceof Container c)) {
			MyMod.LOGGER.warn("Złoty Bilet: w {} nie ma skrzyni.", CHEST);
			return false;
		}
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (c.getItem(i).isEmpty()) {
				c.setItem(i, new ItemStack(ModItems.ZLOTY_BILET));
				c.setChanged();
				return true;
			}
		}
		return false;
	}
}
