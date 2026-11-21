package com.example.mymod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Spawn: ochrona terenu (cała wysokość), niewidzialna ściana (bez bloków) oraz NPC rynku i sklepu.
 * Narożniki spawnu: 12 100 12 oraz -11 100 -11.
 */
public final class SpawnManager {
	private SpawnManager() {}

	// obszar spawnu (włącznie z blokami narożnymi)
	private static final int MIN_X = -11, MAX_X = 12, MIN_Z = -11, MAX_Z = 12;

	private static final double[] NPC_RYNEK = {-24, 100, 3};
	private static final double[] NPC_SKLEP = {-24, 100, -4};
	private static final String NAME_RYNEK = "Rynek";
	private static final String NAME_SKLEP = "Sklep";

	private static final Map<UUID, double[]> LAST_INSIDE = new HashMap<>();

	public static boolean inSpawn(double x, double z) {
		return x >= MIN_X && x < MAX_X + 1 && z >= MIN_Z && z < MAX_Z + 1;
	}

	private static boolean protectedPos(BlockPos p) {
		return p.getX() >= MIN_X && p.getX() <= MAX_X && p.getZ() >= MIN_Z && p.getZ() <= MAX_Z;
	}

	private static boolean isAdmin(net.minecraft.world.entity.player.Player pl) {
		return pl instanceof ServerPlayer sp && RangiManager.isAdminPlayer(sp);
	}

	public static void initialize() {
		// niszczenie
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) ->
				isAdmin(player) || !protectedPos(pos));
		AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) ->
				(!isAdmin(player) && protectedPos(pos)) ? InteractionResult.FAIL : InteractionResult.PASS);
		// stawianie bloków i wiader
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (isAdmin(player)) return InteractionResult.PASS;
			ItemStack st = player.getItemInHand(hand);
			if (st.getItem() instanceof BlockItem || st.getItem() instanceof BucketItem) {
				BlockPos target = hit.getBlockPos().relative(hit.getDirection());
				if (protectedPos(target) || protectedPos(hit.getBlockPos())) return InteractionResult.FAIL;
			}
			return InteractionResult.PASS;
		});
		// NPC rynku i sklepu
		UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (!(entity instanceof Mannequin m) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			String n = m.getName().getString();
			if (NAME_RYNEK.equals(n) && near(m, NPC_RYNEK)) {
				if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) Rynek.showOffers(sp);
				return InteractionResult.SUCCESS;
			}
			if (NAME_SKLEP.equals(n) && near(m, NPC_SKLEP)) {
				if (hand == net.minecraft.world.InteractionHand.MAIN_HAND) SklepManager.openMain(sp);
				return InteractionResult.SUCCESS;
			}
			return InteractionResult.PASS;
		});
		ServerTickEvents.END_SERVER_TICK.register(SpawnManager::tick);
	}

	private static boolean near(Mannequin m, double[] c) {
		return Math.abs(m.getX() - c[0]) < 3 && Math.abs(m.getY() - c[1]) < 6 && Math.abs(m.getZ() - c[2]) < 3;
	}

	private static void tick(MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level == null) return;
		// niewidzialna ściana: kto jest w spawnie, nie może z niego wyjść (admin może)
		for (ServerPlayer p : level.players()) {
			if (RangiManager.isAdminPlayer(p)) {
				LAST_INSIDE.remove(p.getUUID());
				continue;
			}
			double x = p.getX(), z = p.getZ();
			if (inSpawn(x, z)) {
				LAST_INSIDE.put(p.getUUID(), new double[] {x, p.getY(), z});
			} else {
				double[] last = LAST_INSIDE.get(p.getUUID());
				if (last != null) {
					double cx = Math.max(MIN_X + 0.3, Math.min(MAX_X + 1 - 0.3, x));
					double cz = Math.max(MIN_Z + 0.3, Math.min(MAX_Z + 1 - 0.3, z));
					p.connection.teleport(cx, last[1], cz, p.getYRot(), p.getXRot());
				}
			}
		}
		if (server.getTickCount() % 100 == 0) {
			removeNpc(level, NAME_RYNEK, NPC_RYNEK);
			removeNpc(level, NAME_SKLEP, NPC_SKLEP);
		}
	}

	/** Figury Rynek i Sklep zostały wycofane: usuwamy je ze świata (rynek i sklep otwiera /sklep). */
	private static void removeNpc(ServerLevel level, String name, double[] c) {
		BlockPos bp = BlockPos.containing(c[0], c[1], c[2]);
		if (!level.hasChunkAt(bp)) return;
		AABB box = new AABB(c[0] - 6, c[1] - 8, c[2] - 6, c[0] + 6, c[1] + 8, c[2] + 6);
		for (Mannequin m : level.getEntitiesOfClass(Mannequin.class, box, e -> name.equals(e.getName().getString()))) {
			m.discard();
		}
	}

	@SuppressWarnings("unused")
	private static void ensureNpc(MinecraftServer server, ServerLevel level, String name, double[] c) {
		BlockPos bp = BlockPos.containing(c[0], c[1], c[2]);
		if (!level.hasChunkAt(bp)) return;
		AABB box = new AABB(c[0] - 3, c[1] - 6, c[2] - 3, c[0] + 3, c[1] + 6, c[2] + 3);
		for (Mannequin m : level.getEntitiesOfClass(Mannequin.class, box, e -> name.equals(e.getName().getString()))) {
			return; // NPC już istnieje – nie tworzymy kopii
		}
		String base = "summon minecraft:mannequin " + c[0] + " " + c[1] + " " + c[2]
				+ " {CustomName:\"" + name + "\",CustomNameVisible:1b,Invulnerable:1b,hide_description:1b,";
		CommandSourceStack src = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
		try {
			server.getCommands().getDispatcher().execute(base + "profile:{texture:\"minecraft:entity/player/wide/steve\"}}", src);
		} catch (Exception e1) {
			try {
				server.getCommands().getDispatcher().execute(base.substring(0, base.length() - 1) + "}", src);
			} catch (Exception e2) {
				MyMod.LOGGER.warn("Nie udało się stworzyć NPC {}: {}", name, e2.toString());
			}
		}
	}
}
