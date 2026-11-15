package com.example.mymod;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Serce: 10% szans na wypadnięcie po zabiciu gracza (trafia do zabójcy). Prawy klik = +1 serce na stałe
 * (max 30 serc łącznie, bazowo 10). Po śmierci tracisz 1 dodane serce, ale nigdy nie spadasz poniżej 10.
 */
public class SerceItem extends Item {
	private static final Identifier BONUS_ID = Identifier.fromNamespaceAndPath(MyMod.MOD_ID, "serca_bonus");
	public static final int BASE_HEARTS = 10;
	public static final int MAX_HEARTS = 30;
	public static final float DROP_CHANCE = 0.10F;

	public SerceItem(Properties properties) {
		super(properties);
	}

	/** Ile serc ponad bazowe 10 ma gracz (0-20). */
	private static int bonusHearts(AttributeInstance hp) {
		AttributeModifier m = hp == null ? null : hp.getModifier(BONUS_ID);
		return m == null ? 0 : (int) Math.round(m.amount() / 2.0);
	}

	private static void setBonus(AttributeInstance hp, int hearts) {
		if (hp == null) {
			return;
		}
		hp.removeModifier(BONUS_ID);
		if (hearts > 0) {
			hp.addPermanentModifier(new AttributeModifier(BONUS_ID, hearts * 2.0, AttributeModifier.Operation.ADD_VALUE));
		}
	}

	public static void initialize() {
		// 10% szans na serce po zabiciu gracza.
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof ServerPlayer victim && source.getEntity() instanceof ServerPlayer killer && killer != victim
					&& victim.level() instanceof ServerLevel level && level.getRandom().nextFloat() < DROP_CHANCE) {
				ItemStack heart = new ItemStack(ModItems.SERCE_ZYCIA);
				if (!killer.getInventory().add(heart)) {
					level.addFreshEntity(new ItemEntity(level, killer.getX(), killer.getY(), killer.getZ(), heart));
				}
				killer.sendSystemMessage(Component.literal("Zdobyłeś Serce!"), true);
			}
		});

		// Po śmierci: tracimy 1 dodane serce (nigdy poniżej 10), reszta zostaje.
		ServerPlayerEvents.COPY_FROM.register((oldPlayer, newPlayer, alive) -> {
			int bonus = bonusHearts(oldPlayer.getAttribute(Attributes.MAX_HEALTH));
			if (!alive) {
				bonus = Math.max(0, bonus - 1);
			}
			setBonus(newPlayer.getAttribute(Attributes.MAX_HEALTH), bonus);
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
				newPlayer.setHealth(newPlayer.getMaxHealth()));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		var hp = sp.getAttribute(Attributes.MAX_HEALTH);
		int bonus = bonusHearts(hp);
		if (BASE_HEARTS + bonus >= MAX_HEARTS) {
			sp.sendSystemMessage(Component.literal("Masz już maksymalną liczbę serc (" + MAX_HEARTS + ")!"), true);
			return InteractionResult.FAIL;
		}
		setBonus(hp, bonus + 1);
		ItemStack stack = player.getItemInHand(hand);
		stack.shrink(1);
		sp.setHealth(Math.min(sp.getMaxHealth(), sp.getHealth() + 2.0F));
		sp.sendSystemMessage(Component.literal("Masz teraz " + (BASE_HEARTS + bonus + 1) + " serc!"), true);
		return InteractionResult.SUCCESS;
	}
}
