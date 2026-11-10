package com.example.mymod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/**
 * Chydro klatka: klatka (6 x 9, 6 bloków w dół od trafienia (trafienie na Y -2 → podłoga na Y -8)) na 15 sekund.
 * Ściany i sufit są ze zwykłego szkła, podłoga i wszystkie bloki-przeszkody w środku z niebieskiego koralowca.
 *
 * Zasady wewnątrz klatki:
 *  - właściciel (ten, kto użył chydro) może niszczyć bloki w środku i na podłodze, ale nie ściany ani sufit,
 *  - właściciel może stawiać tylko zwykłe pajęczyny (działają normalnie),
 *  - nikt inny nie może niczego niszczyć ani stawiać,
 *  - po 15 sekundach wszystkie podmienione bloki wracają do poprzednich.
 */
public final class ChydroKlatkaManager {
	public static final int DURATION_TICKS = 15 * 20; // 15 sekund

	/** Ile bloków w dół od miejsca trafienia sięga klatka. */
	private static final int DEPTH_BELOW = 6;

	private static final int WIDTH = 16; // oś X
	private static final int LENGTH = 16; // oś Z
	private static final int HEIGHT = 16; // oś Y

	private static final List<Cage> CAGES = new ArrayList<>();

	/** Wystrzelona kula ognista, która po trafieniu zamieni się w klatkę. */
	private record Shot(ServerLevel level, LargeFireball fireball, UUID owner, Vec3 start) {
	}

	private static final List<Shot> SHOTS = new ArrayList<>();

	/** Zaczyna śledzić kulę. Gdy zniknie (trafi w coś), w tym miejscu powstanie klatka. */
	public static void trackShot(ServerLevel level, LargeFireball fireball, UUID owner) {
		SHOTS.add(new Shot(level, fireball, owner, fireball.position()));
	}

	private static final class Cage {
		final ServerLevel level;
		final UUID owner;
		final long expiresAt;
		final int minX, maxX, minY, maxY, minZ, maxZ;
		final Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
		final List<BlockPos> walls = new ArrayList<>(); // szkło do odbudowy po wybuchach

		Cage(ServerLevel level, UUID owner, long expiresAt, int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
			this.level = level;
			this.owner = owner;
			this.expiresAt = expiresAt;
			this.minX = minX;
			this.maxX = maxX;
			this.minY = minY;
			this.maxY = maxY;
			this.minZ = minZ;
			this.maxZ = maxZ;
		}

		boolean contains(BlockPos p) {
			return p.getX() >= minX && p.getX() <= maxX
					&& p.getY() >= minY && p.getY() <= maxY
					&& p.getZ() >= minZ && p.getZ() <= maxZ;
		}

		/** Ściany boczne i sufit. Podłoga (dolna warstwa) NIE jest tu liczona. */
		boolean isWallOrCeiling(BlockPos p) {
			return p.getX() == minX || p.getX() == maxX
					|| p.getZ() == minZ || p.getZ() == maxZ
					|| p.getY() == maxY;
		}
	}

	private ChydroKlatkaManager() {
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> tick());
		// Przy wyłączaniu serwera przywracamy świat od razu, żeby nic nie zostało.
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> restoreAll());

		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> canBreak(level, player, pos));
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> onUseBlock(player, level, hand, hit));
	}

	public static void trap(ServerLevel level, BlockPos center, UUID owner) {
		// Zakresy względem pozycji celu. Przy parzystym wymiarze nie da się wycentrować idealnie,
		// więc klatka jest o jeden blok dalej w stronę +X.
		int relMinX = -(WIDTH / 2);
		int relMinZ = -(LENGTH / 2);
		int relMinY = -1; // podłoga tuż pod celem

		// Dół sięga 6 bloków w dół od miejsca trafienia, a klatka ma 16 bloków wysokości.
		int bottomY = center.getY() - DEPTH_BELOW;
		int topY = bottomY + HEIGHT - 1;

		Cage cage = new Cage(level, owner, level.getGameTime() + DURATION_TICKS,
				center.getX() + relMinX, center.getX() + relMinX + WIDTH - 1,
				bottomY, topY,
				center.getZ() + relMinZ, center.getZ() + relMinZ + LENGTH - 1);

		BlockState coral = ModBlocks.CHYDRO_KORAL.defaultBlockState();
		BlockState glass = Blocks.GLASS.defaultBlockState();

		for (int x = cage.minX; x <= cage.maxX; x++) {
			for (int z = cage.minZ; z <= cage.maxZ; z++) {
				for (int y = cage.minY; y <= cage.maxY; y++) {
					BlockPos pos = new BlockPos(x, y, z);
					if (!level.isInWorldBounds(pos)) {
						continue;
					}

					boolean floor = y == cage.minY;
					boolean wallOrCeiling = cage.isWallOrCeiling(pos);
					BlockState old = level.getBlockState(pos);

					BlockState replacement;
					if (wallOrCeiling) {
						// Ściany i sufit: zwykłe szkło.
						replacement = glass;
					} else if (floor) {
						// Podłoga: niebieski koralowiec.
						replacement = coral;
					} else if (!old.isAir()) {
						// Wnętrze: tylko to, co jest przeszkodą, zamienia się w niebieski koralowiec.
						replacement = coral;
					} else {
						continue;
					}

					if (wallOrCeiling && old.is(Blocks.GLASS)) {
						cage.walls.add(pos);
					}

					// Nie ruszamy: istniejącej klatki, bloków z zawartością (skrzynie itp.),
					// bloków nie do zniszczenia (bedrock itd.), ani bloków, które już są właściwe.
					if (old.is(ModBlocks.CHYDRO_KORAL)
							|| old.is(ModBlocks.CHYDRO_PAJECZYNA)
							|| old.is(replacement.getBlock())
							|| level.getBlockEntity(pos) != null
							|| old.getDestroySpeed(level, pos) < 0) {
						continue;
					}

					cage.originals.put(pos, old);
					if (wallOrCeiling) {
						cage.walls.add(pos);
					}
					// Flaga UPDATE_CLIENTS: bez aktualizacji sąsiadów, żeby pochodnie, drabiny
					// i inne nie wypadały podczas podmiany.
					level.setBlock(pos, replacement, Block.UPDATE_CLIENTS);
				}
			}
		}

		CAGES.add(cage);
	}

	private static Cage findCage(Level level, BlockPos pos) {
		for (Cage cage : CAGES) {
			if (cage.level == level && cage.contains(pos)) {
				return cage;
			}
		}
		return null;
	}

	/** Niszczenie bloków: tylko właściciel i tylko nie ściany/sufit. */
	private static boolean canBreak(Level level, Player player, BlockPos pos) {
		Cage cage = findCage(level, pos);
		if (cage == null) {
			return true;
		}
		return cage.owner.equals(player.getUUID()) && !cage.isWallOrCeiling(pos);
	}

	/** Stawianie bloków wewnątrz klatki: tylko właściciel i tylko pajęczyny. */
	private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (level.isClientSide()) {
			return InteractionResult.PASS;
		}

		ItemStack stack = player.getItemInHand(hand);
		if (!(stack.getItem() instanceof BlockItem)) {
			return InteractionResult.PASS;
		}

		BlockPlaceContext context = new BlockPlaceContext(player, hand, stack, hit);
		BlockPos placePos = context.getClickedPos();
		Cage cage = findCage(level, placePos);
		if (cage == null) {
			return InteractionResult.PASS;
		}

		boolean isOwner = cage.owner.equals(player.getUUID());
		if (isOwner && stack.is(Items.COBWEB) && !cage.isWallOrCeiling(placePos)) {
			// Zwykła pajęczyna, stawiana normalnie (działa jak w grze).
			return InteractionResult.PASS;
		}

		// Każdy inny blok: odmowa, a blok zostaje w ekwipunku (wymuszamy odświeżenie ekwipunku u gracza).
		if (player instanceof ServerPlayer serverPlayer) {
			serverPlayer.containerMenu.sendAllDataToRemote();
		}
		return InteractionResult.FAIL;
	}

	private static void tick() {
		Iterator<Shot> shots = SHOTS.iterator();
		while (shots.hasNext()) {
			Shot shot = shots.next();
			if (shot.fireball().isRemoved()) {
				// Kula trafiła: klatka powstaje w miejscu trafienia.
				trap(shot.level(), BlockPos.containing(shot.fireball().position()), shot.owner());
				shots.remove();
			} else if (shot.fireball().position().distanceTo(shot.start()) > ChydroKlatkaItem.RANGE) {
				// Za daleko i nic nie trafiła: kula znika, klatki nie ma.
				shot.fireball().discard();
				shots.remove();
			}
		}

		Iterator<Cage> it = CAGES.iterator();
		while (it.hasNext()) {
			Cage cage = it.next();
			// Wybuchy (creeper, TNT itd.) nie rozwalają szkła: zepsute od razu odbudowujemy.
			for (BlockPos wall : cage.walls) {
				if (!cage.level.getBlockState(wall).is(Blocks.GLASS)) {
					cage.level.setBlock(wall, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
			if (cage.level.getGameTime() >= cage.expiresAt) {
				restore(cage);
				it.remove();
			}
		}
	}

	private static void restoreAll() {
		SHOTS.clear();
		for (Cage cage : CAGES) {
			restore(cage);
		}
		CAGES.clear();
	}

	private static void restore(Cage cage) {
		for (Map.Entry<BlockPos, BlockState> entry : cage.originals.entrySet()) {
			BlockPos pos = entry.getKey();
			// Przywracamy tylko tam, gdzie nadal jest koralowiec albo szkło klatki,
			// żeby nie nadpisać czegoś, co ktoś w międzyczasie postawił.
			BlockState now = cage.level.getBlockState(pos);
			if (now.is(ModBlocks.CHYDRO_KORAL) || now.is(Blocks.GLASS)) {
				cage.level.setBlock(pos, entry.getValue(), Block.UPDATE_CLIENTS);
			}
		}
	}
}
