package com.example.mymod;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Otwieranie skrzyń: klucz zużywa się w chwili kliknięcia przycisku, nagroda jest losowana od razu (równe szanse),
 * a animacja trwa 5 s: przez pierwsze 2 s zmiana co 0,3 s, potem co 1 s, ostatni pokazany przedmiot to nagroda.
 * Jeden gracz może mieć tylko jedno otwieranie naraz, więc nic się nie powiela.
 */
public final class SkrzynieManager {
	public static final int ANIM_TICKS = 100;
	public static final float KEY_DROP_CHANCE = 0.10F;
	private static final int[] EVENT_TICKS = { 0, 6, 12, 18, 24, 30, 36, 40, 60, 80 };
	private static final int CENTER = 22;

	private static final class Opening {
		final UUID player;
		final ServerLevel level;
		final BlockPos pos;
		final SkrzyniaMenu menu;
		final Crates.Crate crate;
		final int reward;
		final int[] shown;
		final long start;
		boolean given = false;

		Opening(UUID player, ServerLevel level, BlockPos pos, SkrzyniaMenu menu, Crates.Crate crate, int reward, int[] shown, long start) {
			this.player = player;
			this.level = level;
			this.pos = pos;
			this.menu = menu;
			this.crate = crate;
			this.reward = reward;
			this.shown = shown;
			this.start = start;
		}
	}

	private static final List<Opening> OPENINGS = new ArrayList<>();
	private static final Set<UUID> BUSY = new HashSet<>();

	private SkrzynieManager() {
	}

	private static ItemStack named(net.minecraft.world.item.Item item, String name) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return s;
	}

	/** Podgląd: nagrody w slotach od 0 (bez ozdobnych szyb) i trzy przyciski w ostatnim rzędzie. */
	public static SimpleContainer preview(Crates.Crate crate, RegistryAccess ra) {
		SimpleContainer c = new SimpleContainer(54);
		for (int i = 0; i < crate.entries().size() && i < SkrzyniaMenu.REWARD_SLOTS; i++) {
			c.setItem(i, crate.entries().get(i).icon().apply(ra));
		}
		c.setItem(SkrzyniaMenu.BTN_ALL, named(Items.BLUE_DYE, "Otwórz wszystkie klucze"));
		c.setItem(SkrzyniaMenu.BTN_NOW, named(Items.ORANGE_DYE, "Otwórz od razu"));
		c.setItem(SkrzyniaMenu.BTN_ANIM, named(Items.GREEN_DYE, "Otwórz z animacją (5 s)"));
		c.setItem(SkrzyniaMenu.BTN_CLOSE, named(Items.BARRIER, "Zamknij"));
		return c;
	}

	private static boolean takeKey(ServerPlayer p, net.minecraft.world.item.Item key) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			ItemStack s = p.getInventory().getItem(i);
			if (s.is(key)) {
				if (!p.getAbilities().instabuild) {
					s.shrink(1);
				}
				return true;
			}
		}
		return false;
	}

	private static void give(MinecraftServer server, Opening o, ServerPlayer sp) {
		if (o.given) {
			return;
		}
		o.given = true;
		RegistryAccess ra = server.registryAccess();
		for (ItemStack s : o.crate.entries().get(o.reward).give().apply(ra)) {
			if (sp == null || !sp.getInventory().add(s)) {
				if (sp != null) {
					sp.drop(s, false);
				} else {
					Block.popResource(o.level, o.pos.above(), s);
				}
			}
		}
	}

	/** Otwiera skrzynię tyle razy, ile gracz ma kluczy (od razu, bez animacji). */
	public static void requestAll(ServerPlayer sp, SkrzyniaMenu menu) {
		if (!(sp.level() instanceof ServerLevel level) || BUSY.contains(sp.getUUID()) || menu.busy) {
			return;
		}
		Crates.Crate crate = menu.crate;
		if (crate.entries().isEmpty()) {
			sp.sendSystemMessage(Component.literal("Ta skrzynia nie ma jeszcze nagród"), true);
			return;
		}
		int opened = 0;
		RegistryAccess ra = level.registryAccess();
		while (takeKey(sp, crate.key())) {
			int reward = level.getRandom().nextInt(crate.entries().size());
			for (ItemStack s : crate.entries().get(reward).give().apply(ra)) {
				if (!sp.getInventory().add(s)) {
					sp.drop(s, false);
				}
			}
			opened++;
			if (sp.getAbilities().instabuild && opened >= 64) {
				break; // w kreatywnym klucze się nie zużywają - ogranicznik
			}
		}
		if (opened == 0) {
			sp.sendSystemMessage(Component.literal("Potrzebujesz klucza do tej skrzyni"), true);
			return;
		}
		sp.sendSystemMessage(Component.literal("Otwarto skrzyń: " + opened), true);
		sp.closeContainer();
	}

	public static void request(ServerPlayer sp, SkrzyniaMenu menu, boolean animated) {
		if (!(sp.level() instanceof ServerLevel level) || BUSY.contains(sp.getUUID()) || menu.busy) {
			return;
		}
		Crates.Crate crate = menu.crate;
		if (crate.entries().isEmpty()) {
			sp.sendSystemMessage(Component.literal("Ta skrzynia nie ma jeszcze nagród"), true);
			return;
		}
		if (!takeKey(sp, crate.key())) {
			sp.sendSystemMessage(Component.literal("Potrzebujesz klucza do tej skrzyni"), true);
			return;
		}
		int reward = level.getRandom().nextInt(crate.entries().size());
		menu.busy = true;
		BUSY.add(sp.getUUID());
		int n = crate.entries().size();
		int[] shown = new int[EVENT_TICKS.length];
		int prev = -1;
		for (int i = 0; i < shown.length; i++) {
			int pick = level.getRandom().nextInt(n);
			int tries = 0;
			while (n > 1 && (pick == prev || pick == reward) && tries++ < 20) {
				pick = level.getRandom().nextInt(n);
			}
			shown[i] = pick;
			prev = pick;
		}
		shown[shown.length - 1] = reward; // ostatni pokazany przedmiot to nagroda
		Opening o = new Opening(sp.getUUID(), level, menu.pos, menu, crate, reward, shown, level.getGameTime());
		if (!animated) {
			give(level.getServer(), o, sp);
			BUSY.remove(sp.getUUID());
			sp.sendSystemMessage(Component.literal("Wylosowano: ")
					.append(crate.entries().get(reward).icon().apply(level.registryAccess()).getHoverName()), true);
			sp.closeContainer();
			return;
		}
		for (int i = 0; i < 54; i++) {
			menu.box.setItem(i, ItemStack.EMPTY);
		}
		OPENINGS.add(o);
	}

	public static void initialize() {
		// Skrzynie są nie do zniszczenia, także w trybie kreatywnym.
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, be) -> !(state.getBlock() instanceof CrateBlock));

		// Klucze wypadają z mobów zabitych przez gracza: 10% szans na losowy klucz.
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof net.minecraft.world.entity.Mob && source.getEntity() instanceof ServerPlayer
					&& entity.level() instanceof ServerLevel level && level.getRandom().nextFloat() < KEY_DROP_CHANCE) {
				List<Crates.Crate> all = new ArrayList<>();
				for (Crates.Crate c : Crates.all()) {
					all.add(c);
				}
				Crates.Crate pick = all.get(level.getRandom().nextInt(all.size()));
				Block.popResource(level, entity.blockPosition(), new ItemStack(pick.key()));
			}
			return true;
		});

		ServerTickEvents.END_SERVER_TICK.register(server -> {
			Iterator<Opening> it = OPENINGS.iterator();
			while (it.hasNext()) {
				Opening o = it.next();
				ServerPlayer sp = server.getPlayerList().getPlayer(o.player);
				long t = o.level.getGameTime() - o.start;
				RegistryAccess ra = server.registryAccess();
				for (int i = 0; i < EVENT_TICKS.length; i++) {
					if (t == EVENT_TICKS[i]) {
						o.menu.box.setItem(CENTER, o.crate.entries().get(o.shown[i]).icon().apply(ra));
						if (sp != null) {
							sp.level().playSound(null, sp.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.6F, 0.8F + i * 0.05F);
						}
					}
				}
				if (t >= ANIM_TICKS && !o.given) {
					give(server, o, sp);
					if (sp != null) {
						sp.level().playSound(null, sp.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);
						sp.sendSystemMessage(Component.literal("Wylosowano: ")
								.append(o.crate.entries().get(o.reward).icon().apply(ra).getHoverName()), true);
					}
				}
				if (t >= ANIM_TICKS + 30) {
					if (sp != null && sp.containerMenu == o.menu) {
						sp.closeContainer();
					}
					BUSY.remove(o.player);
					it.remove();
				}
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			for (Opening o : OPENINGS) {
				give(server, o, server.getPlayerList().getPlayer(o.player)); // nagroda nie przepada przy zamknięciu serwera
			}
			OPENINGS.clear();
			BUSY.clear();
		});
	}
}
