package com.example.mymod;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * Shulek Boxek (27 miejsc): prawy klik otwiera ekwipunek. Prawy klik na bycie atakuje go po kolei
 * wszystkimi broniami ze środka (obrażenia i zaklęcia się sumują). Cooldown ataku 5 minut;
 * otwieranie ekwipunku go nie uruchamia.
 */
public class ShulekBoxekItem extends Item {
	private static final int SLOTS = 27;
	public static final long COOLDOWN_TICKS = 300L * 20L;

	private static final Map<UUID, Long> READY_AT = new HashMap<>();

	public ShulekBoxekItem(Properties properties) {
		super(properties);
	}

	private static final class BoxContainer extends SimpleContainer {
		private final ItemStack box;
		private final Player owner;
		private boolean loading = true;

		BoxContainer(ItemStack box, Player owner) {
			super(SLOTS);
			this.box = box;
			this.owner = owner;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (loading) {
				return;
			}
			// Shulek Boxek nie może leżeć sam w sobie.
			for (int i = 0; i < getContainerSize(); i++) {
				ItemStack s = getItem(i);
				if (s.is(ModItems.SHULEK_BOXEK)) {
					loading = true;
					setItem(i, ItemStack.EMPTY);
					loading = false;
					if (!owner.getInventory().add(s)) {
						owner.drop(s, false);
					}
				}
			}
			save(box, this);
		}
	}

	private static BoxContainer load(ItemStack box, Player owner) {
		BoxContainer c = new BoxContainer(box, owner);
		List<ItemStack> items = SakiewkaItem.readContents(box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY));
		for (int i = 0; i < items.size() && i < SLOTS; i++) {
			c.setItem(i, items.get(i));
		}
		c.loading = false;
		return c;
	}

	private static void save(ItemStack box, SimpleContainer c) {
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < c.getContainerSize(); i++) {
			if (!c.getItem(i).isEmpty()) {
				items.add(c.getItem(i).copy());
			}
		}
		box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}

	/** Siła ataku bez czekania na naładowanie: ustawiamy licznik przez refleksję. */
	private static void fullStrength(Player p) {
		for (Class<?> c = p.getClass(); c != null; c = c.getSuperclass()) {
			try {
				Field f = c.getDeclaredField("attackStrengthTicker");
				f.setAccessible(true);
				f.setInt(p, 1000);
				return;
			} catch (ReflectiveOperationException | RuntimeException e) {
				// szukamy wyżej w hierarchii klas
			}
		}
	}

	public static void initialize() {
		UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
			ItemStack held = player.getItemInHand(hand);
			if (!held.is(ModItems.SHULEK_BOXEK) || !(player instanceof ServerPlayer sp)
					|| !(entity instanceof LivingEntity target) || !Util13.targetable(target)) {
				return InteractionResult.PASS;
			}
			long now = sp.level().getGameTime();
			Long ready = READY_AT.get(sp.getUUID());
			if (ready != null && now < ready) {
				sp.sendSystemMessage(Component.literal("Shulek Boxek: cooldown jeszcze " + ((ready - now) / 20 + 1) + " s"), true);
				return InteractionResult.FAIL;
			}
			BoxContainer box = load(held, sp);
			List<Integer> weapons = new ArrayList<>();
			for (int i = 0; i < box.getContainerSize(); i++) {
				if (!box.getItem(i).isEmpty()) {
					weapons.add(i);
				}
			}
			if (weapons.isEmpty()) {
				sp.sendSystemMessage(Component.literal("Shulek Boxek jest pusty"), true);
				return InteractionResult.FAIL;
			}
			ItemStack originalMain = sp.getMainHandItem();
			for (int slot : weapons) {
				ItemStack weapon = box.getItem(slot).copy();
				sp.setItemInHand(InteractionHand.MAIN_HAND, weapon);
				fullStrength(sp);
				target.invulnerableTime = 0;
				sp.attack(target);
				box.setItem(slot, sp.getMainHandItem().isEmpty() ? ItemStack.EMPTY : sp.getMainHandItem());
			}
			sp.setItemInHand(InteractionHand.MAIN_HAND, originalMain);
			READY_AT.put(sp.getUUID(), now + COOLDOWN_TICKS);
			return InteractionResult.SUCCESS;
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> READY_AT.clear());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		ItemStack box = player.getItemInHand(hand);
		BoxContainer container = load(box, player);
		player.openMenu(new SimpleMenuProvider((id, inv, p) -> ChestMenu.threeRows(id, inv, container),
				Component.translatable("item.mymod.shulek_boxek")));
		return InteractionResult.SUCCESS;
	}
}
