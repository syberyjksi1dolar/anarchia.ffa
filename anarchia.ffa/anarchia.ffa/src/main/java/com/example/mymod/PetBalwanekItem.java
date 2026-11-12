package com.example.mymod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Pet Bałwanek: prawy klik przywołuje / chowa peta (mała głowa bałwanka na prawym ramieniu).
 * Daje Pośpiech II, 2 dodatkowe serca i leczy 0,5 HP (0,25 serca) za każde uderzenie.
 * Pet jest niewidzialnym stojakiem z głową bałwanka, przesuwanym co tick do ramienia gracza.
 */
public class PetBalwanekItem extends Item {
	private static final Identifier HP_ID = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "pet_balwanek_hp");
	/** Obrót twarzy względem gracza; zmień na 180.0F, jeśli twarz patrzy do tyłu. */
	private static final float FACE_YAW_OFFSET = 0.0F;

	private static final Map<UUID, Entity> PETS = new HashMap<>();

	public PetBalwanekItem(Properties properties) {
		super(properties);
	}

	private static boolean isPet(Entity e) {
		return PETS.containsValue(e);
	}

	private static void dismiss(UUID id) {
		Entity stand = PETS.remove(id);
		if (stand != null) {
			stand.discard();
		}
	}

	public static void initialize() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				isPet(entity) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hitResult) ->
				isPet(entity) ? InteractionResult.FAIL : InteractionResult.PASS);

		// Leczenie 0,5 HP za każde uderzenie.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
			if (!blocked && source.getDirectEntity() instanceof Player p && PETS.containsKey(p.getUUID()) && p.isAlive()) {
				p.heal(0.5F);
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Map.Entry<UUID, Entity>> it = PETS.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, Entity> e = it.next();
				ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
				Entity stand = e.getValue();
				if (p == null || !p.isAlive() || stand.isRemoved()) {
					stand.discard();
					if (p != null) {
						var hp = p.getAttribute(Attributes.MAX_HEALTH);
						if (hp != null) {
							hp.removeModifier(HP_ID);
						}
					}
					it.remove();
					continue;
				}
				double yaw = Math.toRadians(p.getYRot());
				double rx = -Math.cos(yaw) * 0.42;
				double rz = -Math.sin(yaw) * 0.42;
				stand.setPos(p.getX() + rx, p.getY() + (p.isShiftKeyDown() ? 0.75 : 0.9), p.getZ() + rz);
				stand.setYRot(p.getYRot() + FACE_YAW_OFFSET);
				if (server.getTickCount() % 10 == 0) {
					p.addEffect(new net.minecraft.world.effect.MobEffectInstance(EffectUtil.get("haste"), 40, 1, true, false, true));
				}
				var hp = p.getAttribute(Attributes.MAX_HEALTH);
				if (hp != null && hp.getModifier(HP_ID) == null) {
					hp.addTransientModifier(new AttributeModifier(HP_ID, 4.0, AttributeModifier.Operation.ADD_VALUE));
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Entity stand : PETS.values()) {
				stand.discard();
			}
			PETS.clear();
		});
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		ItemStack stack = player.getItemInHand(hand);
		if (player.getCooldowns().isOnCooldown(stack)) {
			return InteractionResult.FAIL;
		}
		player.getCooldowns().addCooldown(stack, 20);
		UUID id = player.getUUID();
		if (PETS.containsKey(id)) {
			dismiss(id);
			var hp = sp.getAttribute(Attributes.MAX_HEALTH);
			if (hp != null) {
				hp.removeModifier(HP_ID);
			}
			sp.sendSystemMessage(Component.literal("Pet Bałwanek schowany"), true);
			return InteractionResult.SUCCESS;
		}
		var stand = EntityType.ARMOR_STAND.create(serverLevel, EntitySpawnReason.TRIGGERED);
		if (stand == null) {
			return InteractionResult.FAIL;
		}
		var scale = stand.getAttribute(Attributes.SCALE);
		if (scale != null) {
			scale.setBaseValue(0.45);
		}
		stand.setInvisible(true);
		stand.setNoGravity(true);
		stand.setInvulnerable(true);
		stand.setItemSlot(EquipmentSlot.HEAD, new ItemStack(ModItems.PET_BALWANEK));
		stand.setPos(player.getX(), player.getY() + 0.9, player.getZ());
		serverLevel.addFreshEntity(stand);
		PETS.put(id, stand);
		sp.sendSystemMessage(Component.literal("Pet Bałwanek przywołany!"), true);
		return InteractionResult.SUCCESS;
	}
}
