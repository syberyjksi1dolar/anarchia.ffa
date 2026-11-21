package com.example.mymod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Komendy administracyjne: /II [nick] oraz /stwórz. */
public final class AdminCommands {
	private AdminCommands() {}

	private static ItemStack pane() {
		ItemStack s = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		return s;
	}

	// ---------- /II ----------
	/** Widok kontenera o stałym rozmiarze; sloty poza źródłem to zablokowane szyby. */
	private static final class View implements Container {
		private final Container src;
		private final int size;
		private final int real;

		View(Container src, int size) {
			this.src = src;
			this.size = size;
			this.real = Math.min(src.getContainerSize(), size);
		}

		boolean dead(int i) {
			return i >= real;
		}

		@Override public int getContainerSize() { return size; }
		@Override public boolean isEmpty() { return src.isEmpty(); }
		@Override public ItemStack getItem(int i) { return dead(i) ? pane() : src.getItem(i); }
		@Override public ItemStack removeItem(int i, int n) { return dead(i) ? ItemStack.EMPTY : src.removeItem(i, n); }
		@Override public ItemStack removeItemNoUpdate(int i) { return dead(i) ? ItemStack.EMPTY : src.removeItemNoUpdate(i); }
		@Override public void setItem(int i, ItemStack s) { if (!dead(i)) src.setItem(i, s); }
		@Override public void setChanged() { src.setChanged(); }
		@Override public boolean stillValid(Player p) { return true; }
		@Override public void clearContent() { /* nigdy nie czyścimy cudzego ekwipunku */ }
	}

	private static final class InvMenu extends ChestMenu {
		private final View view;
		private final ServerPlayer target;
		private final int rows;

		InvMenu(MenuType<?> type, int id, Inventory inv, View view, int rows, ServerPlayer target) {
			super(type, id, inv, view, rows);
			this.view = view;
			this.target = target;
			this.rows = rows;
		}

		@Override
		public void clicked(int slotId, int button, ContainerInput input, Player player) {
			if (slotId >= 0 && slotId < rows * 9 && view.dead(slotId)) {
				return;
			}
			super.clicked(slotId, button, input, player);
			if (target != null) {
				target.inventoryMenu.broadcastChanges();
			}
		}

		@Override
		public void removed(Player player) {
			super.removed(player);
			if (target != null && player.level().getServer() != null) {
				player.level().getServer().getPlayerList().saveAll();
			}
		}
	}

	private static int openInv(CommandSourceStack s, String nick) {
		ServerPlayer admin = s.getPlayer();
		if (admin == null) {
			s.sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
			return 0;
		}
		if (!RangiManager.isAdminPlayer(admin)) {
			s.sendFailure(Component.literal("Tylko administrator może użyć /II."));
			return 0;
		}
		ServerPlayer tp = s.getServer().getPlayerList().getPlayerByName(nick);
		Container src;
		int rows;
		MenuType<?> type;
		ServerPlayer target = null;
		if (tp != null) {
			src = tp.getInventory();
			rows = 5;
			type = MenuType.GENERIC_9x5;
			target = tp;
		} else {
			SimpleContainer bi = BotManager.invOf(nick);
			if (bi == null) {
				s.sendFailure(Component.literal("Nie znaleziono gracza online ani bota o nicku " + nick + "."));
				return 0;
			}
			src = bi;
			rows = 4;
			type = MenuType.GENERIC_9x4;
		}
		final View view = new View(src, rows * 9);
		final int r = rows;
		final MenuType<?> mt = type;
		final ServerPlayer tgt = target;
		admin.openMenu(new SimpleMenuProvider((id, inv, pl) -> new InvMenu(mt, id, inv, view, r, tgt),
				Component.literal("Ekwipunek: " + nick)));
		return 1;
	}

	// ---------- /stwórz ----------
	private static final int BTN = 26;

	private static final class CreateMenu extends ChestMenu {
		private final SimpleContainer box;
		private final String nick;
		private final String skin;
		private final int level;
		private boolean created;

		CreateMenu(int id, Inventory inv, SimpleContainer box, String nick, String skin, int level) {
			super(MenuType.GENERIC_9x3, id, inv, box, 3);
			this.box = box;
			this.nick = nick;
			this.skin = skin;
			this.level = level;
		}

		@Override
		public void clicked(int slotId, int button, ContainerInput input, Player player) {
			if (slotId == BTN) {
				if (player instanceof ServerPlayer sp && !created) {
					List<ItemStack> items = new ArrayList<>();
					for (int i = 0; i < BTN; i++) {
						if (!box.getItem(i).isEmpty()) {
							items.add(box.getItem(i).copy());
						}
					}
					String err = BotManager.createBot(nick, skin, level, items);
					if (err != null) {
						sp.sendSystemMessage(Component.literal(err).withStyle(ChatFormatting.RED));
					} else {
						created = true;
						for (int i = 0; i < BTN; i++) {
							box.setItem(i, ItemStack.EMPTY); // przedmioty przeszły do bota
						}
						sp.sendSystemMessage(Component.literal("Bot " + nick + " (poziom " + level + ") został stworzony.").withStyle(ChatFormatting.GREEN));
						sp.closeContainer();
					}
				}
				return;
			}
			super.clicked(slotId, button, input, player);
		}

		@Override
		public void removed(Player player) {
			if (!created) {
				for (int i = 0; i < BTN; i++) {
					ItemStack s = box.getItem(i);
					if (!s.isEmpty()) {
						box.setItem(i, ItemStack.EMPTY);
						if (!player.getInventory().add(s)) {
							player.drop(s, false);
						}
					}
				}
			}
			super.removed(player);
		}
	}

	private static int stworz(CommandSourceStack s, String nick, String skin, int level) {
		ServerPlayer admin = s.getPlayer();
		if (admin == null) {
			s.sendFailure(Component.literal("Tej komendy może użyć tylko gracz."));
			return 0;
		}
		if (!RangiManager.isAdminPlayer(admin)) {
			s.sendFailure(Component.literal("Tylko administrator może użyć /stwórz."));
			return 0;
		}
		if (!nick.matches("[A-Za-z0-9_]{3,16}")) {
			s.sendFailure(Component.literal("Nick bota: 3-16 znaków (litery, cyfry, _)."));
			return 0;
		}
		MinecraftServer server = s.getServer();
		if (BotManager.invOf(nick) != null || server.getPlayerList().getPlayerByName(nick) != null) {
			s.sendFailure(Component.literal("Nick " + nick + " jest już zajęty."));
			return 0;
		}
		final String skinName = (skin == null || skin.isBlank()) ? nick : skin;
		s.sendSuccess(() -> Component.literal("Sprawdzam skin gracza " + skinName + "..."), false);
		CompletableFuture.supplyAsync(() -> skinExists(skinName)).thenAccept(res -> server.execute(() -> {
			if (Boolean.FALSE.equals(res)) {
				admin.sendSystemMessage(Component.literal("Nie znaleziono skina gracza \"" + skinName
						+ "\" (nie istnieje takie konto Minecraft). Podaj inny nick skina: /stwórz " + nick + " <nick_skina> " + level)
						.withStyle(ChatFormatting.RED));
				return;
			}
			if (res == null) {
				admin.sendSystemMessage(Component.literal("Nie udało się sprawdzić skina (brak połączenia z Mojang). Bot spróbuje go pobrać.")
						.withStyle(ChatFormatting.YELLOW));
			}
			SimpleContainer box = new SimpleContainer(27);
			ItemStack btn = new ItemStack(Items.EMERALD);
			btn.set(DataComponents.CUSTOM_NAME, Component.literal("Stwórz bota " + nick).withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD));
			box.setItem(BTN, btn);
			admin.openMenu(new SimpleMenuProvider((id, inv, pl) -> new CreateMenu(id, inv, box, nick, skinName, level),
					Component.literal("Nowy bot: " + nick + " (poz. " + level + ")")));
		}));
		return 1;
	}

	/** true = konto istnieje, false = nie istnieje, null = nie udało się sprawdzić. */
	private static Boolean skinExists(String name) {
		try {
			HttpClient c = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
			HttpRequest rq = HttpRequest.newBuilder(URI.create("https://api.mojang.com/users/profiles/minecraft/" + name))
					.timeout(Duration.ofSeconds(8)).GET().build();
			int code = c.send(rq, HttpResponse.BodyHandlers.discarding()).statusCode();
			if (code == 200) return true;
			if (code == 204 || code == 404) return false;
			return null;
		} catch (Exception e) {
			return null;
		}
	}

	public static void initialize() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
			for (String lit : new String[] {"II", "ii"}) {
				d.register(Commands.literal(lit).then(Commands.argument("nick", StringArgumentType.word())
						.executes(ctx -> openInv(ctx.getSource(), StringArgumentType.getString(ctx, "nick")))));
			}
			for (String lit : new String[] {"stwórz", "stworz"}) {
				d.register(Commands.literal(lit).then(Commands.argument("nick", StringArgumentType.word())
						.executes(ctx -> stworz(ctx.getSource(), StringArgumentType.getString(ctx, "nick"), null, 5))
						.then(Commands.argument("skin", StringArgumentType.word())
								.executes(ctx -> stworz(ctx.getSource(), StringArgumentType.getString(ctx, "nick"),
										StringArgumentType.getString(ctx, "skin"), 5))
								.then(Commands.argument("poziom", IntegerArgumentType.integer(1, 10))
										.executes(ctx -> stworz(ctx.getSource(), StringArgumentType.getString(ctx, "nick"),
												StringArgumentType.getString(ctx, "skin"), IntegerArgumentType.getInteger(ctx, "poziom")))))));
			}
		});
	}
}
