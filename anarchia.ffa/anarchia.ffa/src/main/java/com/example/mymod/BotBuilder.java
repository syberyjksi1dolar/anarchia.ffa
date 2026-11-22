package com.example.mymod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bot buduje jak zwykły gracz: wolno (jeden blok co ok. 2 s), tylko z własnego ekwipunku.
 * Dom 7x7: podłoga z bruku, ściany z desek z kłodami w rogach, wejście, okna (otwory), dach i pochodnia w środku.
 * Farma: woda pośrodku i pszenica dookoła; rozmiar zależy od liczby nasion (9x9, 5x5 albo 3x3).
 */
final class BotBuilder {
	private BotBuilder() {}

	private static final Random RNG = new Random();
	private static final int WALL = 0, COBBLE = 1, TORCH = 2, WATER = 3, CROP = 4, CORNER = 5, COBWALL = 6;

	private record P(BlockPos pos, int type) {}

	private static final class Job {
		List<P> plan;
		int idx;
	}

	private static final Map<String, Job> JOBS = new HashMap<>();

	static boolean busy(String nick) {
		return JOBS.containsKey(nick);
	}

	private static boolean isLog(ItemStack s) {
		return s.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().is(BlockTags.LOGS);
	}

	private static boolean isPlank(ItemStack s) {
		return s.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().is(BlockTags.PLANKS);
	}

	private static int countWall(SimpleContainer c) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (!s.isEmpty() && (isLog(s) || isPlank(s))) n += s.getCount();
		}
		return n;
	}

	private static int count(SimpleContainer c, Item it) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(it)) n += c.getItem(i).getCount();
		return n;
	}

	/** Wywoływane co 2 s dla bota; zwraca true, gdy bot jest zajęty budową. */
	static boolean step(ServerLevel level, String nick, Mannequin m) {
		Job j = JOBS.get(nick);
		SimpleContainer inv = BotManager.inv(nick);
		int bx = (int) Math.floor(m.getX()), bz = (int) Math.floor(m.getZ());
		if (j == null) {
			if (BotManager.isGuard(nick)) return false; // strażnik nie buduje
			double[] slot = BotManager.plotSlot(nick); // działka bota (posesja wokół jego pierwszego spawnu)
			if (slot == null) return false;
			List<Integer> kinds = new ArrayList<>();
			int wall = countWall(inv), cob = count(inv, Items.COBBLESTONE), seeds = count(inv, Items.WHEAT_SEEDS);
			if (wall >= 45) { kinds.add(0); kinds.add(0); kinds.add(0); } // domy najczęściej
			if (seeds >= 8 && count(inv, Items.WATER_BUCKET) >= 1) { kinds.add(1); kinds.add(1); }
			if (cob >= 80) kinds.add(2);
			if (cob >= 84) kinds.add(3);
			if (cob >= 70) { kinds.add(4); kinds.add(4); }
			if (cob >= 140) { kinds.add(5); kinds.add(5); }
			if (cob >= 160) kinds.add(6);
			if (cob >= 10) { kinds.add(7); kinds.add(7); }
			if (kinds.isEmpty()) return false;
			double ddx = slot[0] - m.getX(), ddz = slot[1] - m.getZ();
			if (ddx * ddx + ddz * ddz > 100) { // najpierw idzie na swoją działkę
				BotManager.goTo(nick, slot[0], slot[1]);
				return false;
			}
			int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) slot[0], (int) slot[1]);
			BlockPos o = new BlockPos((int) slot[0], gy, (int) slot[1]);
			int kind = kinds.get(RNG.nextInt(kinds.size()));
			List<P> plan = switch (kind) {
				case 0 -> housePlan(level, o, inv, wall, cob);
				case 1 -> farmPlan(level, o, seeds);
				case 2 -> towerPlan(level, o);
				case 3 -> pyramidPlan(level, o);
				case 4 -> yardPlan(level, o);
				case 5 -> basePlan(level, o);
				case 6 -> bigTowerPlan(level, o);
				default -> lampPlan(level, o);
			};
			if (plan == null) { // teren na tej działce nie nadaje się: następna działka
				BotManager.plotAdvance(nick);
				return false;
			}
			Job n = new Job();
			n.plan = plan;
			JOBS.put(nick, n);
			return true;
		}
		if (j.idx >= j.plan.size()) {
			JOBS.remove(nick);
			BotManager.plotAdvance(nick); // następna budowla na kolejnej działce
			return false;
		}
		// jak prawdziwy gracz: raz stawia kilka bloków pod rząd, raz rozgląda się i macha ręką
		if (RNG.nextInt(5) == 0) {
			float yaw = RNG.nextFloat() * 360f - 180f;
			m.setYRot(yaw);
			m.setYHeadRot(yaw);
			m.setYBodyRot(yaw);
			m.setXRot(RNG.nextFloat() * 40f - 20f);
			m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			return true;
		}
		int burst = 1 + RNG.nextInt(3);
		P last = null;
		for (int b = 0; b < burst && j.idx < j.plan.size(); b++) {
			P p = j.plan.get(j.idx++);
			BlockState st = null;
			switch (p.type()) {
				case CORNER -> st = takeWall(inv, true);
				case WALL -> st = takeWall(inv, false);
				case COBBLE, COBWALL -> st = take(inv, Items.COBBLESTONE) ? Blocks.COBBLESTONE.defaultBlockState() : null;
				case TORCH -> st = take(inv, Items.TORCH) ? Blocks.TORCH.defaultBlockState() : Blocks.AIR.defaultBlockState();
				case WATER -> {
					if (take(inv, Items.WATER_BUCKET)) {
						inv.addItem(new ItemStack(Items.BUCKET));
						st = Blocks.WATER.defaultBlockState();
					}
				}
				case CROP -> {
					if (take(inv, Items.WHEAT_SEEDS)) {
						level.setBlock(p.pos().below(), Blocks.FARMLAND.defaultBlockState(), 3);
						st = Blocks.WHEAT.defaultBlockState();
					}
				}
				default -> { }
			}
			if (st == null) { // zabrakło materiału: kończy budowę
				JOBS.remove(nick);
				BotManager.plotAdvance(nick);
				return false;
			}
			level.setBlock(p.pos(), st, 3);
			last = p;
		}
		if (last != null) { // patrzy na postawiony blok i macha ręką
			double dx = last.pos().getX() + 0.5 - m.getX(), dz = last.pos().getZ() + 0.5 - m.getZ();
			double dy = last.pos().getY() + 0.5 - (m.getY() + 1.6);
			float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.max(0.5, Math.sqrt(dx * dx + dz * dz))));
			m.setYRot(yaw);
			m.setYHeadRot(yaw);
			m.setYBodyRot(yaw);
			m.setXRot(pitch);
			m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
		}
		return true;
	}

	private static boolean take(SimpleContainer c, Item it) {
		for (int i = 0; i < c.getContainerSize(); i++) {
			ItemStack s = c.getItem(i);
			if (s.is(it)) {
				s.shrink(1);
				if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
				c.setChanged();
				return true;
			}
		}
		return false;
	}

	/** Kłoda w rogach, deski w ścianach; gdy brak preferowanego, bierze drugi rodzaj. */
	private static BlockState takeWall(SimpleContainer c, boolean preferLog) {
		for (int pass = 0; pass < 2; pass++) {
			boolean wantLog = (pass == 0) == preferLog;
			for (int i = 0; i < c.getContainerSize(); i++) {
				ItemStack s = c.getItem(i);
				if (!s.isEmpty() && (wantLog ? isLog(s) : isPlank(s))) {
					BlockState st = ((BlockItem) s.getItem()).getBlock().defaultBlockState();
					s.shrink(1);
					if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
					c.setChanged();
					return st;
				}
			}
		}
		return null;
	}

	private static boolean solidGround(BlockState gs) {
		return gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.DIRT) || gs.is(Blocks.SAND) || gs.is(Blocks.STONE);
	}

	private static boolean flat(ServerLevel level, BlockPos o, int w, int clearance) {
		for (int dx = 0; dx < w; dx++) {
			for (int dz = 0; dz < w; dz++) {
				if (!solidGround(level.getBlockState(o.offset(dx, -1, dz)))) return false;
				for (int h = 0; h < clearance; h++) {
					if (!level.getBlockState(o.offset(dx, h, dz)).isAir()) return false;
				}
			}
		}
		return true;
	}

	/** Dom o losowych wymiarach (5/7/9/11 x 5/7/9, wysokość 3-5); ściany z desek albo bruku, rogi z kłód. Gdy brak materiału, bot zmniejsza dom. */
	private static List<P> housePlan(ServerLevel level, BlockPos o, SimpleContainer inv, int wall, int cob) {
		int[] ws = {5, 7, 9, 11}, ds = {5, 7, 9};
		int w = ws[RNG.nextInt(4)], d = ds[RNG.nextInt(3)], h = 3 + RNG.nextInt(3);
		boolean cobWalls = RNG.nextBoolean();
		boolean ok = false;
		for (int tries = 0; tries < 12 && !ok; tries++) {
			int per = 2 * (w + d) - 4;
			int wallsCost = per * h - 3;
			if (cobWalls) {
				ok = cob >= wallsCost + w * d && wall >= w * d + 4 * h;
				if (!ok) cobWalls = false;
			} else {
				ok = wall >= wallsCost + w * d;
				if (!ok) {
					w = Math.max(5, w - 2);
					d = Math.max(5, d - 2);
					h = Math.max(3, h - 1);
				}
			}
		}
		if (!ok) return null;
		int per = 2 * (w + d) - 4;
		if (!flat(level, o, Math.max(w, d), h + 2)) return null;
		List<P> l = new ArrayList<>();
		if (cob >= w * d + (cobWalls ? per * h : 0)) {
			for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) l.add(new P(o.offset(x, -1, z), COBBLE));
		}
		int doorX = w / 2;
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < d; z++) {
					boolean edge = x == 0 || x == w - 1 || z == 0 || z == d - 1;
					if (!edge) continue;
					if (x == doorX && z == 0 && y < 2) continue; // wejście 1x2
					boolean sideMid = (z == d / 2 && (x == 0 || x == w - 1)) || (z == d - 1 && (x == doorX || (w >= 9 && (x == 2 || x == w - 3))));
					if (y == 1 && sideMid) continue; // okna
					boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == d - 1);
					l.add(new P(o.offset(x, y, z), corner ? CORNER : (cobWalls ? COBWALL : WALL)));
				}
			}
		}
		for (int x = 0; x < w; x++) for (int z = 0; z < d; z++) l.add(new P(o.offset(x, h, z), WALL));
		l.add(new P(o.offset(1, 0, d - 2), TORCH));
		if (w >= 7) l.add(new P(o.offset(w - 2, 0, d - 2), TORCH));
		return l;
	}

	/** Wieża obserwacyjna z bruku 3x3, 9 bloków wysokości, z wejściem i pochodnią na szczycie. */
	private static List<P> towerPlan(ServerLevel level, BlockPos o) {
		if (!flat(level, o, 3, 11)) return null;
		List<P> l = new ArrayList<>();
		for (int y = 0; y < 9; y++) {
			for (int x = 0; x < 3; x++) {
				for (int z = 0; z < 3; z++) {
					if (x == 1 && z == 1) continue;
					if (x == 1 && z == 0 && y < 2) continue;
					l.add(new P(o.offset(x, y, z), COBWALL));
				}
			}
		}
		l.add(new P(o.offset(1, 9, 1), TORCH));
		return l;
	}

	/** Piramida schodkowa z bruku (7x7, 5x5, 3x3, 1x1) jako ozdoba. */
	private static List<P> pyramidPlan(ServerLevel level, BlockPos o) {
		if (!flat(level, o, 7, 5)) return null;
		List<P> l = new ArrayList<>();
		for (int y = 0; y < 4; y++) {
			int size = 7 - 2 * y;
			for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) l.add(new P(o.offset(x + y, y, z + y), COBWALL));
		}
		return l;
	}

	/** Zagroda: ogrodzenie z bruku 9x9, wysokość 2, z bramą. */
	private static List<P> yardPlan(ServerLevel level, BlockPos o) {
		if (!flat(level, o, 9, 3)) return null;
		List<P> l = new ArrayList<>();
		for (int y = 0; y < 2; y++) {
			for (int x = 0; x < 9; x++) {
				for (int z = 0; z < 9; z++) {
					if (!(x == 0 || x == 8 || z == 0 || z == 8)) continue;
					if (x == 4 && z == 0) continue; // brama
					l.add(new P(o.offset(x, y, z), COBWALL));
				}
			}
		}
		l.add(new P(o.offset(3, 2, 0), TORCH));
		l.add(new P(o.offset(5, 2, 0), TORCH));
		return l;
	}

	/** Baza: mur 11x11 wysokości 3 z bramą, cztery narożne słupy do 6 bloków i pochodnie na szczytach. */
	private static List<P> basePlan(ServerLevel level, BlockPos o) {
		if (!flat(level, o, 11, 8)) return null;
		List<P> l = new ArrayList<>();
		for (int y = 0; y < 6; y++) {
			for (int x = 0; x < 11; x++) {
				for (int z = 0; z < 11; z++) {
					boolean edge = x == 0 || x == 10 || z == 0 || z == 10;
					if (!edge) continue;
					boolean corner = (x == 0 || x == 10) && (z == 0 || z == 10);
					if (y >= 3 && !corner) continue;
					if (x == 5 && z == 0 && y < 2) continue; // brama 1x2
					l.add(new P(o.offset(x, y, z), COBWALL));
				}
			}
		}
		for (int x = 0; x <= 10; x += 10) for (int z = 0; z <= 10; z += 10) l.add(new P(o.offset(x, 6, z), TORCH));
		return l;
	}

	/** Duża wieża strażnicza: 5x5, 8 bloków wysokości, z wejściem, platformą na szczycie i pochodniami. */
	private static List<P> bigTowerPlan(ServerLevel level, BlockPos o) {
		if (!flat(level, o, 5, 11)) return null;
		List<P> l = new ArrayList<>();
		for (int y = 0; y < 8; y++) {
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					if (!(x == 0 || x == 4 || z == 0 || z == 4)) continue;
					if (x == 2 && z == 0 && y < 2) continue;
					l.add(new P(o.offset(x, y, z), COBWALL));
				}
			}
		}
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) l.add(new P(o.offset(x, 8, z), COBWALL));
		for (int x = 0; x <= 4; x += 4) for (int z = 0; z <= 4; z += 4) l.add(new P(o.offset(x, 9, z), TORCH));
		return l;
	}

	/** Latarnia: słupek z bruku 4 bloki i pochodnia na górze. */
	private static List<P> lampPlan(ServerLevel level, BlockPos o) {
		if (!flat(level, o, 1, 6)) return null;
		List<P> l = new ArrayList<>();
		for (int y = 0; y < 4; y++) l.add(new P(o.offset(0, y, 0), COBWALL));
		l.add(new P(o.offset(0, 4, 0), TORCH));
		return l;
	}

	private static List<P> farmPlan(ServerLevel level, BlockPos o, int seeds) {
		int n = seeds >= 80 ? 9 : seeds >= 24 ? 5 : 3;
		for (int dx = 0; dx < n; dx++) {
			for (int dz = 0; dz < n; dz++) {
				BlockState gs = level.getBlockState(o.offset(dx, -1, dz));
				if (!(gs.is(Blocks.GRASS_BLOCK) || gs.is(Blocks.DIRT))) return null;
				if (!level.getBlockState(o.offset(dx, 0, dz)).isAir()) return null;
			}
		}
		List<P> l = new ArrayList<>();
		int c = n / 2;
		l.add(new P(o.offset(c, -1, c), WATER));
		for (int dx = 0; dx < n; dx++) for (int dz = 0; dz < n; dz++) if (!(dx == c && dz == c)) l.add(new P(o.offset(dx, 0, dz), CROP));
		return l;
	}
}
