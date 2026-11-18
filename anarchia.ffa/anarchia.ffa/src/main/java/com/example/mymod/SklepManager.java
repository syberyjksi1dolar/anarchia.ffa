package com.example.mymod;

import java.util.List;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * /sklep: menu z trzema ikonami: serce (sklep za serca), diament (sklep z rzeczami do sprzedania)
 * i jabłko (rynek). Zawartość każdego działu dojdzie później, na razie kliknięcie wyświetla informację.
 */
public final class SklepManager {
	private static final int SLOT_SERCE = 11;
	private static final int SLOT_SKLEP = 13;
	private static final int SLOT_RYNEK = 15;
	private static final int SLOT_SPRZEDAZ = 11;
	private static final int SLOT_KUPNO = 14;

	/** Pozycja w menu sprzedaży: slot, ikona, klucz ceny i nazwa (null = domyślna nazwa przedmiotu). */
	private record Entry(int slot, Item icon, String key, String name) {
	}

	private static final int SLOT_SALDO = 2;

	/** Układ jak na zrzucie od użytkownika (6 rzędów po 9 slotów). */
	private static final List<Entry> SPRZEDAZ = List.of(
			new Entry(10, Items.DARK_OAK_LOG, "drewno", "Drewno"),
			new Entry(11, Items.QUARTZ, "quartz", null),
			new Entry(12, Items.RAW_COPPER, "raw_copper", null),
			new Entry(14, Items.COPPER_INGOT, "copper_ingot", null),
			new Entry(15, Items.COAL, "coal", null),
			new Entry(16, Items.CHARCOAL, "charcoal", null),
			new Entry(20, Items.SLIME_BALL, "slime_ball", null),
			new Entry(21, Items.DRAGON_BREATH, "dragon_breath", null),
			new Entry(22, Items.CRAFTING_TABLE, "crafting_table", null),
			new Entry(23, Items.WIND_CHARGE, "wind_charge", null),
			new Entry(24, Items.PUFFERFISH, "pufferfish", null),
			new Entry(28, Items.SNOWBALL, "snowball", null),
			new Entry(29, Items.SHIELD, "shield", null),
			new Entry(30, Items.WHITE_BANNER, "ominous_banner", "Baner złowieszczy"),
			new Entry(31, Items.ARROW, "arrow", null),
			new Entry(32, Items.IRON_INGOT, "iron_ingot", null),
			new Entry(33, Items.RAW_IRON, "raw_iron", null),
			new Entry(34, Items.EMERALD, "emerald", null),
			new Entry(38, Items.POINTED_DRIPSTONE, "pointed_dripstone", null),
			new Entry(39, Items.COBWEB, "cobweb", null),
			new Entry(40, Items.OMINOUS_BOTTLE, "ominous_bottle", null),
			new Entry(41, Items.DIAMOND, "diamond", null),
			new Entry(42, Items.LAPIS_LAZULI, "lapis_lazuli", null),
			new Entry(46, Items.APPLE, "apple", null),
			new Entry(47, Items.COBBLESTONE, "cobblestone", null),
			new Entry(48, Items.WATER_BUCKET, "water_bucket", null),
			new Entry(50, Items.REDSTONE, "redstone", null),
			new Entry(51, Items.FIRE_CHARGE, "fire_charge", null),
			new Entry(52, Items.TNT, "tnt", null));

	private SklepManager() {
	}

	private static ItemStack sellIcon(Entry e) {
		ItemStack s = new ItemStack(e.icon());
		if (e.name() != null) {
			s.set(DataComponents.CUSTOM_NAME, Component.literal(e.name()).withStyle(st -> st.withItalic(false)));
		}
		String cena = e.key().equals("ominous_bottle")
				? "Cena: " + Pieniadze.format(Ceny.priceOfKey(e.key())) + " za poziom (każdy wyższy poziom + tyle samo)"
				: "Cena: " + Pieniadze.format(Ceny.priceOfKey(e.key())) + " za sztukę";
		s.set(DataComponents.LORE, new ItemLore(List.of(
				Component.literal(cena).withStyle(ChatFormatting.GOLD).withStyle(st -> st.withItalic(false)),
				Component.literal("PPM: sprzedaj 1. Shift + PPM: sprzedaj wszystkie.").withStyle(ChatFormatting.GRAY).withStyle(st -> st.withItalic(false)))));
		return s;
	}

	private static void refreshHeader(SimpleContainer box, ServerPlayer p) {
		ItemStack saldo = new ItemStack(Items.GOLD_INGOT);
		saldo.set(DataComponents.CUSTOM_NAME, Component.literal("Twoje pieniądze: " + Pieniadze.format(p))
				.withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD).withStyle(st -> st.withItalic(false)));
		box.setItem(SLOT_SALDO, saldo);
	}

	private static void openSell(ServerPlayer p) {
		SimpleContainer box = new SimpleContainer(54);
		for (Entry e : SPRZEDAZ) {
			box.setItem(e.slot(), sellIcon(e));
		}
		refreshHeader(box, p);
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new SklepMenu(id, inv, box, (sp, idx, shift) -> clickSell(sp, box, idx, shift)),
				Component.literal("Sprzedaż")));
	}

	private static void clickSell(ServerPlayer sp, SimpleContainer box, int idx, boolean shift) {
		Entry entry = null;
		for (Entry e : SPRZEDAZ) {
			if (e.slot() == idx) {
				entry = e;
			}
		}
		if (entry == null) {
			return;
		}
		// Najpierw tylko liczymy, co sprzedać. Przedmioty znikną dopiero po zapisaniu pieniędzy.
		int remaining = shift ? Integer.MAX_VALUE : 1;
		int sold = 0;
		long total = 0;
		Inventory inv = sp.getInventory();
		int[] take = new int[36];
		for (int i = 0; i < 36 && remaining > 0; i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty() || !entry.key().equals(Ceny.keyOf(s))) {
				continue;
			}
			int n = Math.min(remaining, s.getCount());
			take[i] = n;
			total += Ceny.price(s) * n;
			remaining -= n;
			sold += n;
		}
		String nazwa = entry.name() != null ? entry.name() : new ItemStack(entry.icon()).getHoverName().getString();
		if (sold == 0) {
			sp.sendSystemMessage(Component.literal("Nie masz w ekwipunku: " + nazwa + "."), true);
			return;
		}
		if (!Pieniadze.add(sp, total)) {
			sp.sendSystemMessage(Component.literal("Transakcja nie powiodła się, przedmioty zostały u ciebie."), true);
			return;
		}
		for (int i = 0; i < 36; i++) {
			if (take[i] > 0) {
				inv.getItem(i).shrink(take[i]);
			}
		}
		refreshHeader(box, sp);
		sp.sendSystemMessage(Component.literal("Sprzedano " + sold + "x " + nazwa + " za " + Pieniadze.format(total)
				+ ". Masz " + Pieniadze.format(sp)), true);
	}

	/** Po kliknięciu diamentu: sprzedaż (niebieski kamień) i kupno (netherytowy miecz). */
	private static void openSklep(ServerPlayer p) {
		SimpleContainer box = new SimpleContainer(27);
		box.setItem(SLOT_SPRZEDAZ, icon(Items.DIAMOND, "Sprzedaż", ChatFormatting.AQUA, "Itemy do sprzedaży. Kliknij, aby otworzyć."));
		box.setItem(SLOT_KUPNO, icon(Items.NETHERITE_SWORD, "Kupno", ChatFormatting.RED, "Itemy do kupienia. Kliknij, aby otworzyć."));
		p.openMenu(new SimpleMenuProvider((id, inv, pl) -> new RangiMenu(id, inv, box, SklepManager::clickSklep),
				Component.literal("Sklep")));
	}

	private static void clickSklep(ServerPlayer p, int index) {
		String dzial;
		if (index == SLOT_SPRZEDAZ) {
			openSell(p);
			return;
		} else if (index == SLOT_KUPNO) {
			dzial = "Kupno";
		} else {
			return;
		}
		p.closeContainer();
		p.sendSystemMessage(Component.literal(dzial + " jest jeszcze w przygotowaniu."));
	}

	private static void click(ServerPlayer p, int index) {
		if (index == SLOT_SKLEP) {
			openSklep(p);
			return;
		}
		String dzial;
		if (index == SLOT_RYNEK) {
			p.closeContainer();
			Rynek.showOffers(p);
			return;
		} else if (index == SLOT_SERCE) {
			dzial = "Sklep za serca";
		} else {
			return;
		}
		p.closeContainer();
		p.sendSystemMessage(Component.literal(dzial + " jest jeszcze w przygotowaniu."));
	}

	public static void initialize() {
		Pieniadze.initialize();
		Ceny.initialize();
		Rynek.initialize();
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
				dispatcher.register(Commands.literal("sklep").executes(ctx -> {
					ServerPlayer p = ctx.getSource().getPlayer();
					if (p == null) {
						ctx.getSource().sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
						return 0;
					}
					open(p);
					return 1;
				})));
	}
}
