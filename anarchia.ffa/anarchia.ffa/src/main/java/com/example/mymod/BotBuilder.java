package com.example.mymod;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bot buduje jak zwykły gracz: wolno (jeden blok co ok. 2 s), tylko z własnego ekwipunku.
 * Dom: 5x5, ściany 3 bloki + dach, wszystko z kłód (min. 72) z drzwiami (dziura). Farma 3x3: woda w środku,
 * ziemia zamieniona w grządki i posadzone nasiona pszenicy (8 nasion + wiadro wody).
 */
final class BotBuilder {
	private BotBuilder() {}

	private static final Random RNG = new Random();

	private static final class Job {
		boolean farm;
		BlockPos[] pos;
		int idx;
		int pause;
	}

	private static final Map<String, Job> JOBS = new HashMap<>();

	static boolean busy(String nick) {
		return JOBS.containsKey(nick);
	}

	private static int count(SimpleContainer c, boolean logs) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (s.isEmpty()) continue;
			if (logs ? isLog(s) : s.is(Items.WHEAT_SEEDS)) n += s.getCount();
		}
		return n;
	}

	private static boolean isLog(ItemStack s) {
		return s.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().is(BlockTags.LOGS);
	}

	/** Wywoływane co 2 s dla bota; zwraca true, gdy bot jest zajęty budową. */
	static boolean step(ServerLevel level, String nick, Mannequin m) {
		Job j = JOBS.get(nick);
		SimpleContainer inv = BotManager.inv(nick);
		int bx = (int) Math.floor(m.getX()), bz = (int) Math.floor(m.getZ());
		if (j == null) {
			if (Math.abs(bx) < 40 && Math.abs(bz) < 50) return false; // nie przy spawnie, rynku i stawie
			if (RNG.nextInt(8) != 0) return false;
			int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, bx, bz);
			boolean farm = count(inv, false) >= 8 && has(inv, Items.WATER_BUCKET);
			boolean house = count(inv, true) >= 72;
			if (!farm && !house) return false;
			if (house && farm && RNG.nextBoolean()) farm = false;
			if (!farm && !house) return false;
			Job n = new Job();
			n.farm = farm;
			BlockPos o = new BlockPos(bx + 3, gy, bz + 3);
			n.pos = farm ? farmPlan(level, o) : housePlan(level, o);
			if (n.pos == null) return false; // teren nie nadaje się, spróbuje gdzie indziej
			JOBS.put(nick, n);
			return true;
		}
		if (j.idx >= j.pos.length) {
			JOBS.remove(nick);
			return false;
		}
		BlockPos p = j.pos[j.idx++];
		if (j.farm) {
			// pozycje: [0]=woda, 1..8 = grządki (grządka + nasiono)
			if (j.idx == 1) {
				if (!take(inv, Items.WATER_BUCKET, 1)) { JOBS.remove(nick); return false; }
				level.setBlock(p, Blocks.WATER.defaultBlockState(), 3);
				inv.addItem(new ItemStack(Items.BUCKET));
			} else {
				if (!take(inv, Items.WHEAT_SEEDS, 1)) { JOBS.remove(nick); return false; }
				level.setBlock(p.below(), Blocks.FARMLAND.defaultBlockState(), 3);
				level.setBlock(p, Blocks.WHEAT.defaultBlockState(), 3);
			}
		} else {
			BlockState st = takeLog(inv);
			if (st == null) { JOBS.remove(nick); return false; }
			level.setBlock(p, st, 3);
		}
		return true;
	}

	private static boolean has(SimpleContainer c, net.minecraft.world.item.Item it) {
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(it)) return true;
		return false;
	}

	private static boolean take(SimpleContainer c, net.minecraft.world.item.Item it, int n) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (s.is(it)) {
				s.shrink(n);
				if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
				c.setChanged();
				return true;
			}
		}
		return false;
	}

	private static BlockState takeLog(SimpleContainer c) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && isLog(s)) {
				BlockState st = ((BlockItem) s.getItem()).getBlock().defaultBlockState();
				s.shrink(1);
				if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
				c.setChanged();
				return st;
			}
		}
		return null;
	}

	private static boolean flat(ServerLevel level, BlockPos o, int w) {
		for (int dx = 0; dx < w; dx++) {
			for (int dz = 0; dz < w; dz++) {
				BlockPos g = o.offset(dx, -1, dz);
				BlockPos a = o.offset(dx, 0, dz);
				BlockState gs = level.getBlockState(g);
				if (!(gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.DIRT) || gs.is(Blocks.SAND) || gs.is(Blocks.STONE))) return false;
				if (!level.getBlockState(a).isAir()) return false;
				if (!level.getBlockState(a.above()).isAir() || !level.getBlockState(a.above(2)).isAir()) return false;
			}
		}
		return true;
	}

	private static BlockPos[] housePlan(ServerLevel level, BlockPos o) {
		if (!flat(level, o, 5)) return null;
		java.util.List<BlockPos> l = new java.util.ArrayList<>();
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					boolean edge = x == 0 || x == 4 || z == 0 || z == 4;
					if (!edge) continue;
					if (x == 2 && z == 0 && y < 2) continue; // wejście
					l.add(o.offset(x, y, z));
				}
			}
		}
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) l.add(o.offset(x, 3, z));
		return l.toArray(new BlockPos[0]);
	}

	private static BlockPos[] farmPlan(ServerLevel level, BlockPos o) {
		for (int dx = 0; dx < 3; dx++) {
			for (int dz = 0; dz < 3; dz++) {
				BlockState gs = level.getBlockState(o.offset(dx, -1, dz));
				if (!(gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.DIRT))) return null;
				if (!level.getBlockState(o.offset(dx, 0, dz)).isAir()) return null;
			}
		}
		BlockPos[] a = new BlockPos[9];
		// woda zastępuje środkowy blok ziemi (poziom -1), reszta to grządki z nasionami
		a[0] = o.offset(1, -1, 1);
		int k = 1;
		for (int dx = 0; dx < 3; dx++) for (int dz = 0; dz < 3; dz++) if (!(dx == 1 && dz == 1)) a[k++] = o.offset(dx, 0, dz);
		return a;
	}
}
