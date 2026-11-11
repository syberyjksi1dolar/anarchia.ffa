package com.example.mymod;

import java.util.ArrayList;
import java.util.List;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.Level;

/**
 * Sakiewka (54 miejsca jak duża skrzynia):
 *  - po zabiciu bytu jego przedmioty trafiają do sakiewki (wystarczy mieć ją gdziekolwiek w ekwipunku),
 *  - prawy klik otwiera sakiewkę, shift + prawy klik wysypuje wszystko na ziemię.
 */
public class SakiewkaItem extends Item {
	private static final int SLOTS = 54;

	public SakiewkaItem(Properties properties) {
		super(properties);
	}

	/** Skrzynia sakiewki: każda zmiana od razu zapisuje się w przedmiocie. */
	private static final class PouchContainer extends SimpleContainer {
		private final ItemStack pouch;
		private boolean loading = true;

		PouchContainer(ItemStack pouch) {
			super(SLOTS);
			this.pouch = pouch;
		}

		@Override
		public void setChanged() {
			super.setChanged();
			if (!loading) {
				save(pouch, this);
			}
		}
	}

	/** Odczyt zawartości przez refleksję, bo nazwa metody różni się między wersjami gry. */
	private static List<ItemStack> readContents(ItemContainerContents contents) {
		List<ItemStack> out = new ArrayList<>();
		for (String name : new String[] { "nonEmptyItems", "nonEmptyItemsCopy", "nonEmptyItemCopyStream",
				"allItemsCopyStream", "allItems", "stream" }) {
			try {
				Object result = ItemContainerContents.class.getMethod(name).invoke(contents);
				Iterable<?> iterable = null;
				if (result instanceof Iterable<?> it) {
					iterable = it;
				} else if (result instanceof java.util.stream.Stream<?> st) {
					iterable = st.toList();
				}
				if (iterable != null) {
					for (Object o : iterable) {
						if (o instanceof ItemStack stack && !stack.isEmpty()) {
							out.add(stack.copy());
						}
					}
					return out;
				}
			} catch (ReflectiveOperationException | RuntimeException e) {
				// próbujemy następną nazwę
			}
		}
		try {
			int slots = (int) ItemContainerContents.class.getMethod("getSlots").invoke(contents);
			for (int i = 0; i < slots; i++) {
				Object o = ItemContainerContents.class.getMethod("getStackInSlot", int.class).invoke(contents, i);
				if (o instanceof ItemStack stack && !stack.isEmpty()) {
					out.add(stack.copy());
				}
			}
		} catch (ReflectiveOperationException | RuntimeException e) {
			// brak zawartości do odczytu
		}
		return out;
	}

	private static SimpleContainer load(ItemStack pouch) {
		PouchContainer container = new PouchContainer(pouch);
		ItemContainerContents contents = pouch.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
		List<ItemStack> items = new ArrayList<>();
		items.addAll(readContents(contents));
		for (int i = 0; i < items.size() && i < SLOTS; i++) {
			container.setItem(i, items.get(i));
		}
		container.loading = false;
		return container;
	}

	private static void save(ItemStack pouch, SimpleContainer container) {
		List<ItemStack> items = new ArrayList<>();
		for (int i = 0; i < container.getContainerSize(); i++) {
			ItemStack s = container.getItem(i);
			if (!s.isEmpty()) {
				items.add(s.copy());
			}
		}
		pouch.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack pouch = player.getItemInHand(hand);
		if (level.isClientSide()) {
			return InteractionResult.SUCCESS;
		}
		SimpleContainer container = load(pouch);
		if (player.isShiftKeyDown()) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack s = container.getItem(i);
				if (!s.isEmpty()) {
					player.drop(s.copy(), false);
					container.setItem(i, ItemStack.EMPTY);
				}
			}
			save(pouch, container);
			return InteractionResult.SUCCESS;
		}
		player.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> ChestMenu.sixRows(id, inv, container),
				Component.translatable("item.mymod.sakiewka")));
		return InteractionResult.SUCCESS;
	}

	/** Przechwytuje przedmioty wypadające z zabitego bytu do sakiewki zabójcy. */
	public static void initialize() {
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof ServerPlayer killer) || entity == killer) {
				return;
			}
			ItemStack pouch = ItemStack.EMPTY;
			for (int i = 0; i < killer.getInventory().getContainerSize(); i++) {
				ItemStack s = killer.getInventory().getItem(i);
				if (s.is(ModItems.SAKIEWKA)) {
					pouch = s;
					break;
				}
			}
			if (pouch.isEmpty()) {
				return;
			}
			SimpleContainer container = load(pouch);
			List<ItemEntity> drops = entity.level().getEntitiesOfClass(ItemEntity.class,
					entity.getBoundingBox().inflate(3.0), e -> e.tickCount < 2);
			for (ItemEntity drop : drops) {
				ItemStack rest = container.addItem(drop.getItem().copy());
				if (rest.isEmpty()) {
					drop.discard();
				} else {
					drop.setItem(rest);
				}
			}
			save(pouch, container);
		});
	}
}
