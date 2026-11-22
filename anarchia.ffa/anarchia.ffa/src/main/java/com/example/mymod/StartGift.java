package com.example.mymod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Każdy gracz dostaje raz losową eventówkę przy wejściu na serwer (jak boty przy pierwszym pojawieniu się). */
public final class StartGift {
	private StartGift() {}

	private static final Set<String> GOT = new HashSet<>();

	private static Path file() {
		return WorldData.file("anarchia_ffa_start2.txt");
	}

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			GOT.clear();
			try {
				if (Files.exists(file())) GOT.addAll(Files.readAllLines(file(), StandardCharsets.UTF_8));
			} catch (IOException e) {
				MyMod.LOGGER.warn("Nie wczytano {}: {}", file(), e.toString());
			}
		});
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayer p = handler.getPlayer();
			String id = p.getUUID().toString();
			if (GOT.contains(id)) return;
			Item it = Rynek.randomEventItem();
			if (it == null) return;
			GOT.add(id);
			try {
				Files.writeString(file(), id + "\n", StandardCharsets.UTF_8,
						java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			} catch (IOException e) {
				MyMod.LOGGER.warn("Nie zapisano {}: {}", file(), e.toString());
			}
			ItemStack st = new ItemStack(it);
			if (!p.getInventory().add(st)) p.drop(st, false);
			net.minecraft.world.SimpleContainer kit = new net.minecraft.world.SimpleContainer(54);
			BotManager.fillKit(kit, 4); // zestaw startowy: żelazny ekwipunek, jedzenie, materiały
			for (int i = 0; i < kit.getContainerSize(); i++) {
				ItemStack k = kit.getItem(i);
				if (!k.isEmpty() && !p.getInventory().add(k)) p.drop(k, false);
			}
			p.sendSystemMessage(Component.literal("Dostałeś zestaw startowy i losową eventówkę: ").withStyle(ChatFormatting.GOLD)
					.append(st.getHoverName()));
		});
	}
}
