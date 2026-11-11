package com.example.mymod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Usuwa Cudowną Latarnię, gdy skończą się jej efekty. */
public final class LatarniaManager {
	private record Lamp(ServerLevel level, BlockPos pos, long expiresAt) {
	}

	private static final List<Lamp> LAMPS = new ArrayList<>();

	private LatarniaManager() {
	}

	public static void register(ServerLevel level, BlockPos pos) {
		LAMPS.add(new Lamp(level, pos.immutable(), level.getGameTime() + CudownaLatarniaBlock.LIFETIME_TICKS));
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Lamp> it = LAMPS.iterator();
			while (it.hasNext()) {
				Lamp lamp = it.next();
				if (lamp.level().getGameTime() >= lamp.expiresAt()) {
					if (lamp.level().hasChunkAt(lamp.pos())
							&& lamp.level().getBlockState(lamp.pos()).is(ModBlocks.CUDOWNA_LATARNIA)) {
						lamp.level().removeBlock(lamp.pos(), false);
					}
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Lamp lamp : LAMPS) {
				if (lamp.level().getBlockState(lamp.pos()).is(ModBlocks.CUDOWNA_LATARNIA)) {
					lamp.level().removeBlock(lamp.pos(), false);
				}
			}
			LAMPS.clear();
		});
	}
}
