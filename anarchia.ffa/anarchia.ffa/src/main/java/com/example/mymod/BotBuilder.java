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
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Bot buduje jak zwykły gracz: wolno (kilka bloków co ok. 2 s), tylko z własnego ekwipunku.
 * 1) Najpierw własna działka: mur dookoła domu bota (od 15x15 do 21x21, wysokość 8-10, z bramą), z bruku/kamienia/ziemi.
 * 2) Potem w środku działki (a gdy zabraknie miejsca, tuż za murem): dom (5-11 x 5-9, wysokość 3-5), farma (3x3, 5x5, 9x9),
 *    wieża 3x3, duża wieża 5x5, baza 11x11 z murem i słupami, zagroda 9x9, piramida, schron 5x5, plac 9x9 i latarnie.
 * Przed budową bot wyrównuje teren pod budowlą: usuwa drzewa i inne naturalne przeszkody oraz podsypuje ziemię.
 */
final class BotBuilder {
	private BotBuilder() {}

	private static final Random RNG = new Random();
	private static final int WALL = 0, COBBLE = 1, TORCH = 2, WATER = 3, CROP = 4, CORNER = 5, COBWALL = 6, BLK = 7, TABLE = 8, FURNACE = 9;

	private record P(BlockPos pos, int type) {}

	private static final class Job {
		List<P> plan;
		int idx;
		int kind; // 0 zwykła budowla, 1 mur własnej działki, 2 mur sojuszu
		int wp, wh, wy;
		BlockPos ws; // gdy to warsztat: jego pozycja
	}

	private static final Map<String, Job> JOBS = new HashMap<>();

	/** Bloki, z których bot stawia mur działki (w tej kolejności). */
	private static final Item[] BLOCKS = {Items.COBBLESTONE, Items.STONE, Items.COBBLED_DEEPSLATE, Items.ANDESITE, Items.DIORITE, Items.GRANITE, Items.DIRT};

	static boolean busy(String nick) {
		return JOBS.containsKey(nick);
	}

	/** Miejsce, przy którym bot ma teraz stać i pracować (następny blok budowli) albo null. */
	static double[] focus(String nick) {
		Job j = JOBS.get(nick);
		if (j == null || j.idx >= j.plan.size()) return null;
		BlockPos p = j.plan.get(j.idx).pos();
		return new double[] {p.getX() + 0.5, p.getZ() + 0.5};
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

	private static int countBlocks(SimpleContainer c) {
		int n = 0;
		for (Item it : BLOCKS) n += count(c, it);
		return n;
	}

	private static BlockState takeBlock(SimpleContainer c) {
		for (Item it : BLOCKS) {
			if (take(c, it)) return ((BlockItem) it).getBlock().defaultBlockState();
		}
		return null;
	}

	/** Naturalne bloki, które bot może usunąć lub zastąpić przy wyrównywaniu terenu (nie rusza cudzych budowli). */
	private static boolean natural(BlockState st) {
		return st.isAir() || st.canBeReplaced() || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES) || st.is(BlockTags.DIRT)
				|| st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD) || st.is(Blocks.GRAVEL);
	}

	/** Wysokość (poziom stóp) nad gruntem w kolumnie, z pominięciem drzew i roślin. */
	static int ground(ServerLevel level, int x, int z) {
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		while (y > 5) {
			BlockState st = level.getBlockState(new BlockPos(x, y - 1, z));
			if (st.isAir() || st.canBeReplaced() || st.is(BlockTags.LOGS) || st.is(BlockTags.LEAVES)) y--;
			else break;
		}
		return y;
	}

	private static boolean spotOk(ServerLevel level, int x0, int z0, int size, int baseY, int clear) {
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				int x = x0 + dx, z = z0 + dz;
				int g = ground(level, x, z);
				if (Math.abs(g - baseY) > 3) return false;
				if (!level.getFluidState(new BlockPos(x, g - 1, z)).isEmpty() || !level.getFluidState(new BlockPos(x, g, z)).isEmpty()) return false;
				for (int h = 0; h < clear; h++) {
					if (!natural(level.getBlockState(new BlockPos(x, baseY + h, z)))) return false;
				}
				if (!natural(level.getBlockState(new BlockPos(x, baseY - 1, z)))) return false;
			}
		}
		return true;
	}

	/** Szuka wolnego miejsca na budowlę size x size: najpierw w środku działki z murem, potem tuż za nią. */
	private static BlockPos findSpot(ServerLevel level, String nick, int size, int clear, double[] home, int[] wi, boolean outside) {
		int cx = (int) Math.floor(home[0]), cz = (int) Math.floor(home[1]);
		int half = wi[0] / 2, baseY = wi[3];
		int lo = -half + 2, hi = half - 2 - size + 1;
		if (hi >= lo) {
			for (int t = 0; t < 30; t++) {
				int x0 = cx + lo + RNG.nextInt(hi - lo + 1), z0 = cz + lo + RNG.nextInt(hi - lo + 1);
				if (spotOk(level, x0, z0, size, baseY, clear)) return new BlockPos(x0, baseY, z0);
			}
		}
		if (!outside) return null;
		for (int t = 0; t < 30; t++) { // za murem
			int dx = RNG.nextInt(91) - 45, dz = RNG.nextInt(91) - 45;
			if (Math.abs(dx) <= half + 2 && Math.abs(dz) <= half + 2) continue;
			int x0 = cx + dx, z0 = cz + dz;
			if (Math.abs(x0) < 45 && Math.abs(z0) < 55) continue; // okolice spawnu, rynku i stawu
			int by = ground(level, x0 + size / 2, z0 + size / 2);
			if (spotOk(level, x0, z0, size, by, clear)) return new BlockPos(x0, by, z0);
		}
		return null;
	}

	/** Wyrównanie terenu: usuwa drzewa i inne naturalne przeszkody, podsypuje ziemię pod budowlą. */
	private static void prepare(ServerLevel level, BlockPos o, int size, int clear) {
		for (int dx = 0; dx < size; dx++) {
			for (int dz = 0; dz < size; dz++) {
				int x = o.getX() + dx, z = o.getZ() + dz;
				for (int h = 0; h < clear; h++) {
					BlockPos p = new BlockPos(x, o.getY() + h, z);
					if (!level.getBlockState(p).isAir()) level.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
				}
				for (int y = o.getY() - 1; y >= o.getY() - 8; y--) {
					BlockPos p = new BlockPos(x, y, z);
					BlockState st = level.getBlockState(p);
					boolean solid = !st.isAir() && !st.canBeReplaced() && !st.is(BlockTags.LOGS) && !st.is(BlockTags.LEAVES) && st.getFluidState().isEmpty();
					if (solid) break;
					level.setBlock(p, y == o.getY() - 1 ? Blocks.GRASS_BLOCK.defaultBlockState() : Blocks.DIRT.defaultBlockState(), 2);
				}
			}
		}
	}

	/** Wywoływane co 2 s dla bota; zwraca true, gdy bot jest zajęty budową. */
	static boolean step(ServerLevel level, String nick, Mannequin m) {
		Job j = JOBS.get(nick);
		SimpleContainer inv = BotManager.inv(nick);
		if (j == null) {
			boolean guard = BotManager.isGuard(nick); // strażnik pomaga tylko przy murze sojuszu
			double[] home = BotManager.home(nick);
			if (home == null) return false;
			int[] wi = BotManager.wallInfo(nick); // {rozmiar, wysokość, gotowy, poziom gruntu}
			int[] cw = BotManager.clanWallInfo(nick); // mur sojuszu albo null
			double[] cb = BotManager.clanBaseHome(nick);
			Job n = new Job();
			if (!guard && wi[2] == 0) { // najpierw mur dookoła własnej działki
				double ddx = home[0] - m.getX(), ddz = home[1] - m.getZ();
				if (ddx * ddx + ddz * ddz > 144) { // najpierw wraca na swoją działkę
					BotManager.goTo(nick, home[0], home[1]);
					return false;
				}
				List<P> plan = wallPlan(level, nick, inv, home, wi, n);
				if (plan == null) return false; // brak materiału na mur: bot najpierw zdobywa bloki
				n.plan = plan;
				n.kind = 1;
				JOBS.put(nick, n);
				return true;
			}
			if (cw != null && cb != null && cw[2] == 0) { // wszyscy z sojuszu budują wspólny mur wokół bazy (min. 15x15x5)
				double ddx = cb[0] - m.getX(), ddz = cb[1] - m.getZ();
				if (ddx * ddx + ddz * ddz > 30 * 30) { // idzie do bazy sojuszu
					BotManager.goTo(nick, cb[0], cb[1]);
					return false;
				}
				List<P> plan = clanWallPlan(level, nick, inv, cb, cw, n);
				if (plan != null) {
					n.plan = plan;
					n.kind = 2;
					JOBS.put(nick, n);
					return true;
				}
			}
			if (guard) return false;
			boolean inClan = cw != null && cb != null && cw[2] == 1 && RNG.nextInt(100) < 50; // połowa budowli powstaje w bazie sojuszu
			double[] site = home;
			int[] swi = wi;
			if (inClan) {
				site = cb;
				swi = new int[] {cw[0], cw[1], cw[2], cw[3]};
			}
			double sdx = site[0] - m.getX(), sdz = site[1] - m.getZ();
			if (sdx * sdx + sdz * sdz > (inClan ? 30 * 30 : 144)) { // najpierw wraca na swoją działkę (albo do bazy sojuszu)
				BotManager.goTo(nick, site[0], site[1]);
				return false;
			}
			int[] wsi = BotManager.workshop(nick);
			if (wsi[3] == 0 && countWall(inv) >= 4 && count(inv, Items.COBBLESTONE) >= 10) { // warsztat: piec i stół do craftingu
				BlockPos wo = findSpot(level, nick, 3, 3, home, wi, true);
				if (wo != null) {
					prepare(level, wo, 3, 3);
					n.plan = workshopPlan(wo);
					n.ws = wo;
					JOBS.put(nick, n);
					return true;
				}
			}
			List<Integer> kinds = new ArrayList<>();
			int wall = countWall(inv), cob = count(inv, Items.COBBLESTONE), seeds = count(inv, Items.WHEAT_SEEDS);
			if (wall >= 45) { kinds.add(0); kinds.add(0); kinds.add(0); }
			if (seeds >= 8 && count(inv, Items.WATER_BUCKET) >= 1) { kinds.add(1); kinds.add(1); }
			if (cob >= 80) kinds.add(2);
			if (cob >= 84) kinds.add(3);
			if (cob >= 70) { kinds.add(4); kinds.add(4); }
			if (cob >= 140) { kinds.add(5); kinds.add(5); }
			if (cob >= 160) kinds.add(6);
			if (cob >= 10) { kinds.add(7); kinds.add(7); kinds.add(7); }
			if (cob >= 70) kinds.add(8);
			if (cob >= 100) kinds.add(9);
			if (cob >= 200 && wall >= 100) { kinds.add(10); kinds.add(10); }
			if (cob >= 25) { kinds.add(11); kinds.add(11); }
			if (wall >= 45) { kinds.add(12); kinds.add(12); }
			if (kinds.isEmpty()) return false;
			int kind = kinds.get(RNG.nextInt(kinds.size()));
			int farmN = seeds >= 80 ? 9 : seeds >= 24 ? 5 : 3;
			int size = switch (kind) {
				case 0 -> 11;
				case 1 -> farmN;
				case 2 -> 3;
				case 3 -> 7;
				case 4 -> 9;
				case 5 -> 11;
				case 6 -> 5;
				case 7 -> 1;
				case 8 -> 5;
				case 10 -> 9;
				case 11 -> 3;
				case 12 -> 5;
				default -> 9;
			};
			int clear = switch (kind) {
				case 0, 5 -> 8;
				case 1 -> 3;
				case 2, 6 -> 11;
				case 3, 8 -> 5;
				case 7, 12 -> 6;
				case 10 -> 10;
				case 11 -> 12;
				default -> 3;
			};
			BlockPos o = findSpot(level, nick, size, clear, site, swi, !inClan);
			if (o == null && kind == 0) { // dom: próbuje mniejszy
				size = 9;
				o = findSpot(level, nick, size, clear, site, swi, !inClan);
				if (o == null) {
					size = 7;
					o = findSpot(level, nick, size, clear, site, swi, !inClan);
				}
			}
			if (o == null) return false;
			prepare(level, o, size, clear);
			List<P> plan = switch (kind) {
				case 0 -> housePlan(o, wall, cob, size);
				case 1 -> farmPlan(o, farmN);
				case 2 -> towerPlan(o);
				case 3 -> pyramidPlan(o);
				case 4 -> yardPlan(o);
				case 5 -> basePlan(o);
				case 6 -> bigTowerPlan(o);
				case 7 -> lampPlan(o);
				case 8 -> bunkerPlan(o);
				case 10 -> twoStoryPlan(o);
				case 11 -> obeliskPlan(o);
				case 12 -> pavilionPlan(o);
				default -> plazaPlan(o);
			};
			if (plan == null || plan.isEmpty()) return false;
			n.plan = plan;
			JOBS.put(nick, n);
			return true;
		}
		if (j.idx >= j.plan.size()) {
			JOBS.remove(nick);
			if (j.kind == 1) BotManager.setWall(nick, j.wp, j.wh, j.wy, true); // mur gotowy
			if (j.kind == 2) BotManager.setClanWall(nick, j.wp, j.wh, j.wy, true); // mur sojuszu gotowy
			if (j.ws != null) BotManager.setWorkshop(nick, j.ws.getX(), j.ws.getY(), j.ws.getZ(), true);
			return false;
		}
		// jak prawdziwy gracz: raz stawia kilka bloków pod rząd, raz rozgląda się i macha ręką
		if (RNG.nextInt(7) == 0) {
			float yaw = RNG.nextFloat() * 360f - 180f;
			m.setYRot(yaw);
			m.setYHeadRot(yaw);
			m.setYBodyRot(yaw);
			m.setXRot(RNG.nextFloat() * 40f - 20f);
			m.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
			return true;
		}
		int burst = j.kind != 0 ? 3 + RNG.nextInt(2) : 1 + RNG.nextInt(3); // mury powstają o ok. 80% szybciej
		P last = null;
		int placed = 0, scanned = 0;
		while (placed < burst && j.idx < j.plan.size() && scanned++ < 600) {
			P p = j.plan.get(j.idx++);
			BlockState cur = level.getBlockState(p.pos());
			if (p.type() != COBBLE && p.type() != CROP && p.type() != WATER && p.type() != TORCH && !cur.isAir() && !cur.canBeReplaced() && !cur.is(BlockTags.LOGS) && !cur.is(BlockTags.LEAVES)) {
				continue; // ten blok już stoi (np. po wznowieniu budowy albo położył go sojusznik) - nie liczy się do tempa
			}
			BlockState st = null;
			switch (p.type()) {
				case CORNER -> st = takeWall(inv, true);
				case WALL -> st = takeWall(inv, false);
				case COBBLE, COBWALL -> st = take(inv, Items.COBBLESTONE) ? Blocks.COBBLESTONE.defaultBlockState() : null;
				case BLK -> st = takeBlock(inv);
				case TABLE -> st = takeMany(inv, Items.OAK_PLANKS, 4) ? Blocks.CRAFTING_TABLE.defaultBlockState() : null;
				case FURNACE -> st = takeMany(inv, Items.COBBLESTONE, 8) ? Blocks.FURNACE.defaultBlockState() : null;
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
			if (st == null) { // zabrakło materiału: kończy tę turę budowy (mur zostanie dokończony później)
				JOBS.remove(nick);
				return false;
			}
			level.setBlock(p.pos(), st, 3);
			last = p;
			placed++;
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

	/** Zabiera n sztuk przedmiotu albo nic, gdy ich brakuje. */
	private static boolean takeMany(SimpleContainer c, Item it, int n) {
		if (count(c, it) < n) return false;
		for (int i = 0; i < n; i++) take(c, it);
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

	/** Mur wokół działki: kolumny po obwodzie (idzie dookoła), od gruntu do wysokości 8-10, z bramą 1x2 po południowej stronie. */
	private static List<P> wallPlan(ServerLevel level, String nick, SimpleContainer inv, double[] home, int[] wi, Job job) {
		int cx = (int) Math.floor(home[0]), cz = (int) Math.floor(home[1]);
		int avail = countBlocks(inv);
		int pSize = wi[0], h = wi[1], baseY = wi[3];
		List<P> plan;
		if (pSize > 0) { // wznowienie przerwanego muru
			plan = wallCells(level, cx, cz, pSize, h, baseY);
		} else {
			baseY = ground(level, cx, cz);
			plan = null;
			int[] sizes = {15, 17, 19, 21};
			int sz = sizes[RNG.nextInt(4)], hh = 8 + RNG.nextInt(3);
			for (int tries = 0; tries < 8; tries++) {
				List<P> cand = wallCells(level, cx, cz, sz, hh, baseY);
				if (cand.size() <= avail) {
					plan = cand;
					pSize = sz;
					h = hh;
					break;
				}
				if (sz > 15) sz -= 2;
				else if (hh > 8) hh--;
				else break;
			}
			if (plan == null) return null;
			BotManager.setWall(nick, pSize, h, baseY, false);
		}
		job.wp = pSize;
		job.wh = h;
		job.wy = baseY;
		return plan;
	}

	/** Mur sojuszu: duży pierścień (min. 21x21, wysokość 5-7) wokół bazy; każdy członek zaczyna w innym miejscu pierścienia, istniejące bloki pomija. */
	private static List<P> clanWallPlan(ServerLevel level, String nick, SimpleContainer inv, double[] cb, int[] cw, Job job) {
		if (countBlocks(inv) < 30) return null; // najpierw zbiera bloki
		int cx = (int) Math.floor(cb[0]), cz = (int) Math.floor(cb[1]);
		int pSize = cw[0], h = cw[1], baseY = cw[3];
		if (pSize == 0) {
			pSize = Math.max(21, cw[4] + 6) + 2 * RNG.nextInt(2);
			h = 5 + RNG.nextInt(3);
			baseY = cw[5] == 1 ? cw[6] : ground(level, cx, cz);
			BotManager.setClanWall(nick, pSize, h, baseY, false);
		}
		List<P> plan = wallCells(level, cx, cz, pSize, h, baseY);
		if (plan.isEmpty()) return null;
		java.util.Collections.rotate(plan, -RNG.nextInt(plan.size()));
		job.wp = pSize;
		job.wh = h;
		job.wy = baseY;
		return plan;
	}

	/** Warsztat: piec, stół do craftingu i pochodnia przy kamiennym słupku. */
	private static List<P> workshopPlan(BlockPos o) {
		List<P> l = new ArrayList<>();
		l.add(new P(o.offset(0, 0, 0), FURNACE));
		l.add(new P(o.offset(1, 0, 0), TABLE));
		l.add(new P(o.offset(2, 0, 0), COBWALL));
		l.add(new P(o.offset(2, 1, 0), TORCH));
		return l;
	}

	private static List<P> wallCells(ServerLevel level, int cx, int cz, int pSize, int h, int baseY) {
		int half = pSize / 2;
		List<int[]> ring = new ArrayList<>();
		for (int x = -half; x <= half; x++) ring.add(new int[] {x, -half});
		for (int z = -half + 1; z <= half; z++) ring.add(new int[] {half, z});
		for (int x = half - 1; x >= -half; x--) ring.add(new int[] {x, half});
		for (int z = half - 1; z > -half; z--) ring.add(new int[] {-half, z});
		List<P> l = new ArrayList<>();
		for (int[] c : ring) {
			int x = cx + c[0], z = cz + c[1];
			int g = ground(level, x, z);
			int y0 = Math.min(g, baseY);
			boolean gate = c[0] == 0 && c[1] == -half;
			for (int y = y0; y < baseY + h; y++) {
				if (gate && y < baseY + 2) continue;
				l.add(new P(new BlockPos(x, y, z), BLK));
			}
		}
		return l;
	}

	/** Dom o losowych wymiarach (5-11 x 5-9, wysokość 3-5); ściany z desek albo bruku, rogi z kłód. Gdy brak materiału, bot zmniejsza dom. */
	private static List<P> housePlan(BlockPos o, int wall, int cob, int cap) {
		List<Integer> opts = new ArrayList<>();
		for (int v : new int[] {5, 7, 9, 11}) if (v <= cap) opts.add(v);
		if (opts.isEmpty()) return null;
		int w = opts.get(RNG.nextInt(opts.size())), d = opts.get(RNG.nextInt(opts.size())), h = 3 + RNG.nextInt(3);
		if (d > 9) d = 9;
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
	private static List<P> towerPlan(BlockPos o) {
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
	private static List<P> pyramidPlan(BlockPos o) {
		List<P> l = new ArrayList<>();
		for (int y = 0; y < 4; y++) {
			int size = 7 - 2 * y;
			for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) l.add(new P(o.offset(x + y, y, z + y), COBWALL));
		}
		return l;
	}

	/** Zagroda: ogrodzenie z bruku 9x9, wysokość 2, z bramą. */
	private static List<P> yardPlan(BlockPos o) {
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
	private static List<P> basePlan(BlockPos o) {
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
	private static List<P> bigTowerPlan(BlockPos o) {
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
	private static List<P> lampPlan(BlockPos o) {
		List<P> l = new ArrayList<>();
		for (int y = 0; y < 4; y++) l.add(new P(o.offset(0, y, 0), COBWALL));
		l.add(new P(o.offset(0, 4, 0), TORCH));
		return l;
	}

	/** Schron: 5x5 z bruku, wysokość 3, dach, drzwi 1x2 i pochodnia w środku. */
	private static List<P> bunkerPlan(BlockPos o) {
		List<P> l = new ArrayList<>();
		for (int y = 0; y < 3; y++) {
			for (int x = 0; x < 5; x++) {
				for (int z = 0; z < 5; z++) {
					if (!(x == 0 || x == 4 || z == 0 || z == 4)) continue;
					if (x == 2 && z == 0 && y < 2) continue;
					l.add(new P(o.offset(x, y, z), COBWALL));
				}
			}
		}
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) l.add(new P(o.offset(x, 3, z), COBWALL));
		l.add(new P(o.offset(2, 0, 3), TORCH));
		return l;
	}

	/** Dom dwupiętrowy 7x7 lub 9x9: ściany z bruku (7 bloków), rogi z kłód, podłoga i strop z desek, okna na obu piętrach, wejście od północy. */
	private static List<P> twoStoryPlan(BlockPos o) {
		int w = RNG.nextBoolean() ? 7 : 9;
		List<P> l = new ArrayList<>();
		for (int x = 0; x < w; x++) for (int z = 0; z < w; z++) l.add(new P(o.offset(x, 3, z), WALL)); // strop między piętrami
		for (int y = 0; y < 7; y++) {
			for (int x = 0; x < w; x++) {
				for (int z = 0; z < w; z++) {
					if (!(x == 0 || x == w - 1 || z == 0 || z == w - 1)) continue;
					if (x == w / 2 && z == 0 && y < 2) continue; // wejście 1x2
					boolean win = (y == 1 || y == 5) && ((z == w / 2 && (x == 0 || x == w - 1)) || (x == w / 2 && z == w - 1));
					if (win) continue;
					boolean corner = (x == 0 || x == w - 1) && (z == 0 || z == w - 1);
					l.add(new P(o.offset(x, y, z), corner ? CORNER : COBWALL));
				}
			}
		}
		for (int x = 0; x < w; x++) for (int z = 0; z < w; z++) l.add(new P(o.offset(x, 7, z), WALL)); // dach
		l.add(new P(o.offset(1, 0, w - 2), TORCH));
		l.add(new P(o.offset(1, 4, w - 2), TORCH));
		return l;
	}

	/** Obelisk: podstawa 3x3 i smukła kolumna do 11 bloków z pochodnią na szczycie. */
	private static List<P> obeliskPlan(BlockPos o) {
		List<P> l = new ArrayList<>();
		for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) l.add(new P(o.offset(x, 0, z), COBWALL));
		for (int y = 1; y <= 10; y++) l.add(new P(o.offset(1, y, 1), COBWALL));
		l.add(new P(o.offset(1, 11, 1), TORCH));
		return l;
	}

	/** Altana: 4 słupy z kłód 5x5 o wysokości 4 i dach z desek. */
	private static List<P> pavilionPlan(BlockPos o) {
		List<P> l = new ArrayList<>();
		for (int y = 0; y < 4; y++) for (int x = 0; x <= 4; x += 4) for (int z = 0; z <= 4; z += 4) l.add(new P(o.offset(x, y, z), CORNER));
		for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) l.add(new P(o.offset(x, 4, z), WALL));
		l.add(new P(o.offset(2, 0, 2), TORCH));
		return l;
	}

	/** Plac: bruk 9x9 na poziomie gruntu z pochodniami w rogach. */
	private static List<P> plazaPlan(BlockPos o) {
		List<P> l = new ArrayList<>();
		for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) l.add(new P(o.offset(x, -1, z), COBBLE));
		for (int x = 0; x <= 8; x += 8) for (int z = 0; z <= 8; z += 8) l.add(new P(o.offset(x, 0, z), TORCH));
		return l;
	}

	private static List<P> farmPlan(BlockPos o, int n) {
		List<P> l = new ArrayList<>();
		int c = n / 2;
		l.add(new P(o.offset(c, -1, c), WATER));
		for (int dx = 0; dx < n; dx++) for (int dz = 0; dz < n; dz++) if (!(dx == c && dz == c)) l.add(new P(o.offset(dx, 0, dz), CROP));
		return l;
	}
}
