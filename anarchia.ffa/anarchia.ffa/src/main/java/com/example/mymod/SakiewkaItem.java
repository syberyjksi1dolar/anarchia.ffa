package com.example.mymod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
	static List<ItemStack> readContents(ItemContainerContents contents) {
		List<ItemStack> out = new ArrayList<>();
		try {
			NonNullList<ItemStack> list = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
			ItemContainerContents.class.getMethod("copyInto", NonNullList.class).invoke(contents, list);
			for (ItemStack stack : list) {
				if (!stack.isEmpty()) {
					out.add(stack.copy());
				}
			}
			return out;
		} catch (ReflectiveOperationException | RuntimeException e) {
			out.clear();
		}
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

	/** Zabicie zapamiętane do chwili, aż wypadną przedmioty. */
	private record Kill(ServerLevel level, UUID killer, Vec3 pos, long time) {
	}

	private static final List<Kill> KILLS = new ArrayList<>();

	/** Przechwytuje przedmioty wypadające z zabitego bytu do sakiewki zabójcy. */
	public static void initialize() {
		// Zapamiętujemy, kto i gdzie zabił; przedmioty łapiemy pod koniec ticka, kiedy już wypadną.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (source.getEntity() instanceof ServerPlayer killer && entity != killer
					&& entity.level() instanceof ServerLevel level) {
				KILLS.add(new Kill(level, killer.getUUID(), entity.position(), level.getGameTime()));
			}
			return true;
		});
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Kill> it = KILLS.iterator();
			while (it.hasNext()) {
				Kill kill = it.next();
				ServerLevel level = kill.level();
				ServerPlayer killer = server.getPlayerList().getPlayer(kill.killer());
				long age = level.getGameTime() - kill.time();
				if (killer != null) {
					collect(level, killer, kill.pos());
				}
				if (age > 5 || killer == null) {
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> KILLS.clear());
	}

	private static void collect(ServerLevel level, ServerPlayer killer, Vec3 pos) {
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
		AABB box = new AABB(pos.x - 4, pos.y - 3, pos.z - 4, pos.x + 4, pos.y + 4, pos.z + 4);
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.tickCount <= 4);
		if (drops.isEmpty()) {
			return;
		}
		SimpleContainer container = load(pouch);
		for (ItemEntity drop : drops) {
			ItemStack rest = container.addItem(drop.getItem().copy());
			if (rest.isEmpty()) {
				drop.discard();
			} else {
				drop.setItem(rest);
			}
		}
		save(pouch, container);
	}
}
