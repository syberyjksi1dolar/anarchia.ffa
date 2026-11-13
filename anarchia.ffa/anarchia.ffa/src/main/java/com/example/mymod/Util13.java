package com.example.mymod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;

/** Wspólne pomocnicze metody nowych eventówek. */
public final class Util13 {
	private Util13() {
	}

	/** Pasek tekstowy, np. ▮▮▮▯▯▯. */
	public static String bar(double fraction, int segments) {
		int filled = (int) Math.round(Math.max(0, Math.min(1, fraction)) * segments);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < segments; i++) {
			sb.append(i < filled ? '▮' : '▯');
		}
		return sb.toString();
	}

	/** Czy ten byt może być celem (nie stojak peta, żywy, nie spectator). */
	public static boolean targetable(Entity e) {
		if (e.getType() == net.minecraft.world.entity.EntityType.IRON_GOLEM && e instanceof net.minecraft.world.entity.Mob m
				&& m.isNoAi() && m.isInvisible()) {
			return false; // niewidzialny wabik działka
		}
		return e instanceof LivingEntity le && le.isAlive() && !le.isSpectator() && !(e instanceof ArmorStand);
	}

	/** Wyrzut w podanym kierunku na w przybliżeniu podaną liczbę bloków (przybliżenie fizyki gry). */
	public static void launch(LivingEntity e, Vec3 dir, double blocks, double up) {
		Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
		double v = blocks / 5.5;
		e.setDeltaMovement(d.x * v, up, d.z * v);
		e.hurtMarked = true;
		e.resetFallDistance();
	}

	public static void moveTo(LivingEntity e, Vec3 p) {
		if (e instanceof ServerPlayer sp) {
			sp.connection.teleport(p.x, p.y, p.z, sp.getYRot(), sp.getXRot());
		} else {
			e.setPos(p.x, p.y, p.z);
		}
	}
}
