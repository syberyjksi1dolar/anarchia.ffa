package com.example.mymod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Dane zapisywane w folderze świata (anarchia_ffa/ obok level.dat), dzięki czemu nowy świat zaczyna od zera:
 * pieniądze, rynek, statystyki, boty z sojuszami i ekwipunkami, bilety i DK.
 * Pliki z dawnego miejsca (folder config) trafiają raz do pierwszego świata, który wystartuje, i znikają z config.
 */
public final class WorldData {
	private WorldData() {}

	private static MinecraftServer server;

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
	}

	public static Path file(String name) {
		Path old = FabricLoader.getInstance().getConfigDir().resolve(name);
		if (server == null) return old;
		try {
			Path dir = server.getWorldPath(LevelResource.ROOT).resolve("anarchia_ffa");
			Files.createDirectories(dir);
			Path p = dir.resolve(name);
			if (!Files.exists(p) && Files.exists(old)) {
				Files.move(old, p); // dotychczasowe dane przejmuje ten świat
			}
			return p;
		} catch (IOException e) {
			MyMod.LOGGER.warn("WorldData {}: {}", name, e.toString());
			return old;
		}
	}
}
