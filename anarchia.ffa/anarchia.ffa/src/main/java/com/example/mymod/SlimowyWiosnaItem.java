package com.example.mymod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Slimowy Wiosna: kucając z przedmiotem w ręce ładujesz moc (5 s). Prawy klik wyrzuca cię w stronę patrzenia:
 * 50% = 4 bloki, 100% = 6 bloków (pomiędzy liniowo). Przy pełnym naładowaniu trafione byty dostają 5 serc obrażeń.
 */
public class SlimowyWiosnaItem extends Item {
	public static final int FULL_TICKS = 100;
	public static final double MIN_BLOCKS = 2.0;
	public static final double FULL_BLOCKS = 6.0;
	public static final float FULL_DAMAGE = 10.0F;

	private static final class Dash {
		int ticksLeft = 12;
		final Set<UUID> hit = new HashSet<>();
	}

	private static final Map<UUID, Integer> CHARGE = new HashMap<>();
	private static final Map<UUID, Dash> DASHES = new HashMap<>();

	public SlimowyWiosnaItem(Properties properties) {
		super(properties);
	}

	private static boolean holds(Player p) {
		return p.getMainHandItem().is(ModItems.SLIMOWY_WIOSNA) || p.getOffhandItem().is(ModItems.SLIMOWY_WIOSNA);
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				UUID id = p.getUUID();
				Dash dash = DASHES.get(id);
				if (dash != null) {
					if (--dash.ticksLeft < 0) {
						DASHES.remove(id);
					} else if (p.level() instanceof ServerLevel level) {
						for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(1.2),
								x -> x != p && Util13.targetable(x) && !dash.hit.contains(x.getUUID()))) {
							dash.hit.add(e.getUUID());
							e.hurtServer(level, level.damageSources().playerAttack(p), FULL_DAMAGE);
						}
					}
				}
				if (holds(p) && p.isShiftKeyDown() && !DASHES.containsKey(id)) {
					int c = Math.min(FULL_TICKS, CHARGE.getOrDefault(id, 0) + 1);
					CHARGE.put(id, c);
					double f = c / (double) FULL_TICKS;
					p.sendSystemMessage(Component.literal("Moc: " + Util13.bar(f, 10) + " " + (int) (f * 100) + "%"), true);
				} else {
					CHARGE.remove(id);
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			CHARGE.clear();
			DASHES.clear();
		});
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer)) {
			return InteractionResult.SUCCESS;
		}
		UUID id = player.getUUID();
		int ticks = CHARGE.getOrDefault(id, 0);
		if (ticks <= 0) {
			return InteractionResult.PASS; // bez ładowania (kucania) nic się nie dzieje
		}
		double frac = ticks / (double) FULL_TICKS;
		// 50% = 4 bloki, 100% = 6 bloków; poniżej 50% proporcjonalnie od 2 bloków.
		double blocks = MIN_BLOCKS + (FULL_BLOCKS - MIN_BLOCKS) * frac;
		if (frac >= 0.5) {
			blocks = 4.0 + (FULL_BLOCKS - 4.0) * ((frac - 0.5) / 0.5);
		}
		Vec3 look = player.getLookAngle();
		double up = look.y < 0.15 ? 0.35 : 0.0;
		double v = blocks / 5.5;
		player.setDeltaMovement(look.x * v, look.y * v + up, look.z * v);
		player.hurtMarked = true;
		player.resetFallDistance();
		CHARGE.remove(id);
		if (frac >= 0.999) {
			DASHES.put(id, new Dash());
		}
		return InteractionResult.SUCCESS;
	}
}
