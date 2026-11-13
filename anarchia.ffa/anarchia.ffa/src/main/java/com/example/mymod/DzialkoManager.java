package com.example.mymod;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;

/**
 * Zarządza postawionymi działkami: co sekundę każde strzela strzałą (1 HP) w najbliższego byta w zasięgu.
 * Pozycje i właściciele zapisują się w pliku mymod_dzialka.txt w folderze świata.
 */
public final class DzialkoManager {
	public static final double RANGE = 15.0;
	public static final int SHOT_INTERVAL = 20;
	public static final double DECOY_HEALTH = 30.0;
	private static final String FILE = "mymod_dzialka.txt";

	private static final class Cannon {
		final String dimension;
		final BlockPos pos;
		final UUID owner;
		long nextShot;
		UUID decoy;

		Cannon(String dimension, BlockPos pos, UUID owner) {
			this.dimension = dimension;
			this.pos = pos;
			this.owner = owner;
		}
	}

	private static final List<Cannon> CANNONS = new ArrayList<>();
	private static boolean dirty = false;

	private DzialkoManager() {
	}

	public static void add(ServerLevel level, BlockPos pos, UUID owner) {
		CANNONS.add(new Cannon(level.dimension().toString(), pos.immutable(), owner));
		dirty = true;
	}

	private static Path file(MinecraftServer server) {
		return server.getWorldPath(LevelResource.ROOT).resolve(FILE);
	}

	private static void save(MinecraftServer server) {
		List<String> lines = new ArrayList<>();
		for (Cannon c : CANNONS) {
			lines.add(c.pos.getX() + ";" + c.pos.getY() + ";" + c.pos.getZ() + ";" + c.owner + ";" + c.dimension + ";" + (c.decoy == null ? "-" : c.decoy));
		}
		try {
			Files.write(file(server), lines, StandardCharsets.UTF_8);
			dirty = false;
		} catch (IOException e) {
			// zapis się nie udał; spróbujemy przy następnej okazji
		}
	}

	private static void load(MinecraftServer server) {
		CANNONS.clear();
		Path f = file(server);
		if (!Files.exists(f)) {
			return;
		}
		try {
			for (String line : Files.readAllLines(f, StandardCharsets.UTF_8)) {
				String[] p = line.split(";", 6);
				if (p.length >= 5) {
					Cannon c = new Cannon(p[4], new BlockPos(Integer.parseInt(p[0]), Integer.parseInt(p[1]),
							Integer.parseInt(p[2])), UUID.fromString(p[3]));
					if (p.length == 6 && !p[5].equals("-")) {
						c.decoy = UUID.fromString(p[5]);
					}
					CANNONS.add(c);
				}
			}
		} catch (IOException | RuntimeException e) {
			// uszkodzony plik: pomijamy
		}
	}

	private static ServerLevel levelOf(MinecraftServer server, String dimension) {
		for (ServerLevel l : server.getAllLevels()) {
			if (l.dimension().toString().equals(dimension)) {
				return l;
			}
		}
		return null;
	}

	public static void initialize() {
		ServerLifecycleEvents.SERVER_STARTED.register(DzialkoManager::load);
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			save(server);
			CANNONS.clear();
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (dirty && server.getTickCount() % 200 == 0) {
				save(server);
			}
			Iterator<Cannon> it = CANNONS.iterator();
			while (it.hasNext()) {
				Cannon c = it.next();
				ServerLevel level = levelOf(server, c.dimension);
				if (level == null || !level.hasChunkAt(c.pos)) {
					continue; // niezaładowany fragment świata: działko śpi
				}
				Entity existing = c.decoy == null ? null : level.getEntity(c.decoy);
				if (!level.getBlockState(c.pos).is(ModBlocks.DZIALKO)) {
					if (existing != null) {
						existing.discard(); // działko wykopane: znika też wabik
					}
					it.remove(); // wykopane lub zniszczone
					dirty = true;
					continue;
				}
				// Wabik: niewidzialny strażnik z 30 HP, który przyjmuje ataki mobów za działko.
				if (existing != null && c.decoy != null && !existing.isAlive()) {
					level.destroyBlock(c.pos, true); // działko zniszczone przez moby
					existing.discard();
					it.remove();
					dirty = true;
					continue;
				}
				if (existing == null) {
					var golem = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.TRIGGERED);
					if (golem != null) {
						var hp = golem.getAttribute(Attributes.MAX_HEALTH);
						if (hp != null) {
							hp.setBaseValue(DECOY_HEALTH);
						}
						golem.setHealth((float) DECOY_HEALTH);
						golem.setNoAi(true);
						golem.setInvisible(true);
						golem.setSilent(true);
						golem.setPos(c.pos.getX() + 0.5, c.pos.getY() + 1.0, c.pos.getZ() + 0.5);
						level.addFreshEntity(golem);
						c.decoy = golem.getUUID();
						existing = golem;
						dirty = true;
					}
				}
				long now = level.getGameTime();
				if (now < c.nextShot) {
					continue;
				}
				Vec3 muzzle = Vec3.atCenterOf(c.pos).add(0, 0.8, 0);
				LivingEntity best = null;
				double bestDist = Double.MAX_VALUE;
				for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class,
						new net.minecraft.world.phys.AABB(c.pos).inflate(RANGE),
						x -> !x.getUUID().equals(c.owner) && Util13.targetable(x))) {
					double d = e.getBoundingBox().getCenter().distanceToSqr(muzzle);
					if (d < bestDist) {
						bestDist = d;
						best = e;
					}
				}
				if (best == null) {
					continue;
				}
				var arrow = EntityType.ARROW.create(level, EntitySpawnReason.TRIGGERED);
				if (arrow == null) {
					continue;
				}
				Vec3 dir = best.getBoundingBox().getCenter().subtract(muzzle);
				if (best instanceof net.minecraft.world.entity.Mob mob && existing instanceof LivingEntity decoyEntity) {
					mob.setTarget(decoyEntity); // zaatakowany mob atakuje działko
				}
				arrow.setPos(muzzle.x, muzzle.y, muzzle.z);
				arrow.setBaseDamage(0.6); // przy prędkości 1,6 daje dokładnie 1 HP
				arrow.shoot(dir.x, dir.y + dir.horizontalDistance() * 0.04, dir.z, 1.6F, 0.5F);
				level.addFreshEntity(arrow);
				level.playSound(null, c.pos, SoundEvents.ARROW_SHOOT, SoundSource.BLOCKS, 1.0F, 1.0F);
				c.nextShot = now + SHOT_INTERVAL;
			}
		});
	}
}
