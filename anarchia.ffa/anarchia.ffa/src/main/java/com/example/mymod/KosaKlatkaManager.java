package com.example.mymod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Klatka z Kosy: 6 x 6 x 7 (wysokość), bez dołu (zostaje ziemia), na 15 sekund.
 *  - w środku wszyscy mogą stawiać bloki i niszczyć tylko te, które sami postawili,
 *  - ściany i sufit są niezniszczalne i odporne na wybuchy (od razu się odbudowują),
 *  - po 15 s postawione bloki znikają, a ściany wracają do poprzednich bloków,
 *  - właściciel dostaje Pośpiech V i Siłę I, zamknięty byt dostaje Ślepotę i Nudności.
 */
public final class KosaKlatkaManager {
	public static final int DURATION_TICKS = 15 * 20;
	private static final int WIDTH = 6;
	private static final int HEIGHT = 7;
	private static final double REACH = 6.0;
	private static final double MAX_DISTANCE = 3.5;

	private static final class Cage {
		final ServerLevel level;
		final long expiresAt;
		final int minX, maxX, minY, maxY, minZ, maxZ;
		final Map<BlockPos, BlockState> wallOriginals = new HashMap<>();
		final Map<BlockPos, BlockState> interior = new HashMap<>();

		Cage(ServerLevel level, long expiresAt, int minX, int minY, int minZ) {
			this.level = level;
			this.expiresAt = expiresAt;
			this.minX = minX;
			this.maxX = minX + WIDTH - 1;
			this.minY = minY;
			this.maxY = minY + HEIGHT - 1;
			this.minZ = minZ;
			this.maxZ = minZ + WIDTH - 1;
		}

		boolean contains(BlockPos p) {
			return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY
					&& p.getZ() >= minZ && p.getZ() <= maxZ;
		}

		boolean isWall(BlockPos p) {
			return p.getX() == minX || p.getX() == maxX || p.getZ() == minZ || p.getZ() == maxZ || p.getY() == maxY;
		}
	}

	private static final List<Cage> CAGES = new ArrayList<>();

	private KosaKlatkaManager() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Cage c : CAGES) {
				restore(c);
			}
			CAGES.clear();
		});
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> canBreak(level, pos));
	}

	private static Cage find(Level level, BlockPos pos) {
		for (Cage c : CAGES) {
			if (c.level == level && c.contains(pos)) {
				return c;
			}
		}
		return null;
	}

	/** Niszczyć wolno tylko bloki postawione w środku klatki. Ściany, sufit i teren nie. */
	private static boolean canBreak(Level level, BlockPos pos) {
		Cage cage = find(level, pos);
		if (cage == null) {
			return true;
		}
		if (cage.isWall(pos)) {
			return false;
		}
		BlockState original = cage.interior.get(pos);
		return original != null && level.getBlockState(pos) != original;
	}

	public static boolean tryCage(ServerLevel level, ServerPlayer player) {
		Vec3 look = player.getLookAngle();
		LivingEntity target = null;
		double best = Double.MAX_VALUE;
		for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(REACH),
				x -> x != player && x.isAlive() && !x.isSpectator())) {
			Vec3 to = e.position().add(0.0, e.getBbHeight() / 2.0, 0.0).subtract(player.getEyePosition());
			double dist = to.length();
			if (dist > MAX_DISTANCE + 0.5 || to.normalize().dot(look) < 0.8) {
				continue;
			}
			if (dist < best) {
				best = dist;
				target = e;
			}
		}
		if (target == null) {
			return false;
		}

		Vec3 mid = player.position().add(target.position()).scale(0.5);
		int minX = (int) Math.floor(mid.x) - WIDTH / 2;
		int minZ = (int) Math.floor(mid.z) - WIDTH / 2;
		int minY = (int) Math.floor(Math.min(player.getY(), target.getY()));
		Cage cage = new Cage(level, level.getGameTime() + DURATION_TICKS, minX, minY, minZ);
		if (!cage.contains(player.blockPosition()) || !cage.contains(target.blockPosition())
				|| cage.isWall(player.blockPosition()) || cage.isWall(target.blockPosition())) {
			return false; // nie mieszczą się oboje w środku
		}

		BlockState glass = Blocks.GLASS.defaultBlockState();
		for (int x = cage.minX; x <= cage.maxX; x++) {
			for (int z = cage.minZ; z <= cage.maxZ; z++) {
				for (int y = cage.minY; y <= cage.maxY; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.isInWorldBounds(pos)) {
						continue;
					}
					if (cage.isWall(pos)) {
						BlockState old = level.getBlockState(pos);
						if (old.is(Blocks.GLASS) || level.getBlockEntity(pos) != null || old.getDestroySpeed(level, pos) < 0) {
							continue;
						}
						cage.wallOriginals.put(pos, old);
						level.setBlock(pos, glass, Block.UPDATE_CLIENTS);
					} else {
						cage.interior.put(pos, level.getBlockState(pos));
					}
				}
			}
		}
		CAGES.add(cage);

		EffectUtil.give(player, "haste", DURATION_TICKS, 4);    // Pośpiech V
		EffectUtil.give(player, "strength", DURATION_TICKS, 0); // Siła I
		EffectUtil.give(target, "blindness", DURATION_TICKS, 0);
		EffectUtil.give(target, "nausea", DURATION_TICKS, 0);
		return true;
	}

	private static void tick() {
		Iterator<Cage> it = CAGES.iterator();
		while (it.hasNext()) {
			Cage cage = it.next();
			if (cage.level.getGameTime() >= cage.expiresAt) {
				restore(cage);
				it.remove();
				continue;
			}
			// Wybuchy nie rozwalają klatki: zniszczone szkło od razu wraca.
			for (BlockPos wall : cage.wallOriginals.keySet()) {
				if (!cage.level.getBlockState(wall).is(Blocks.GLASS)) {
					cage.level.setBlock(wall, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
	}

	private static void restore(Cage cage) {
		// Postawione bloki znikają (wracają do tego, co było w środku na początku).
		for (Map.Entry<BlockPos, BlockState> e : cage.interior.entrySet()) {
			if (cage.level.getBlockState(e.getKey()) != e.getValue()) {
				cage.level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
			}
		}
		for (Map.Entry<BlockPos, BlockState> e : cage.wallOriginals.entrySet()) {
			cage.level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_CLIENTS);
		}
	}

	@SuppressWarnings("unused")
	private static UUID unused() {
		return null;
	}
}
