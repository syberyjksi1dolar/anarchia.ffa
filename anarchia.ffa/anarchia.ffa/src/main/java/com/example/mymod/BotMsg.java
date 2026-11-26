package com.example.mymod;

import java.util.Collection;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.MessageArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /msg, /tell i /w działają także do botów (bot odpisuje prywatnie). Węzły o tych samych nazwach argumentów co w grze
 * ("targets", "message") scalają się z oryginalnymi komendami, więc podpowiedzi i składnia zostają takie same.
 * Nicki botów w podpowiedziach bierze gra z listy TAB (patrz BotTab).
 */
final class BotMsg {
	private BotMsg() {}

	static void initialize() {
		CommandRegistrationCallback.EVENT.register((d, reg, env) -> {
			for (String lit : new String[] {"msg", "tell", "w"}) {
				d.register(Commands.literal(lit).then(Commands.argument("targets", EntityArgument.players())
						.then(Commands.argument("message", MessageArgument.message()).executes(BotMsg::run))));
			}
		});
	}

	private static int run(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
		CommandSourceStack src = ctx.getSource();
		String input = ctx.getInput();
		String rawTarget = null, rawMsg = null;
		for (ParsedCommandNode<CommandSourceStack> pn : ctx.getNodes()) {
			String name = pn.getNode().getName();
			if (name.equals("targets")) rawTarget = pn.getRange().get(input);
			if (name.equals("message")) rawMsg = pn.getRange().get(input);
		}
		if (rawMsg == null) rawMsg = "";
		ServerPlayer sp = src.getPlayer();
		if (sp != null && rawTarget != null) {
			String bot = BotManager.findNick(rawTarget);
			if (bot != null && src.getServer().getPlayerList().getPlayerByName(rawTarget) == null) {
				BotChat.whisper(src.getServer(), sp, bot, rawMsg);
				return 1;
			}
		}
		Component text = Component.literal(rawMsg);
		Collection<ServerPlayer> targets = EntityArgument.getPlayers(ctx, "targets");
		for (ServerPlayer t : targets) {
			src.sendSuccess(() -> Component.translatable("commands.message.display.outgoing", t.getDisplayName(), text)
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), false);
			t.sendSystemMessage(Component.translatable("commands.message.display.incoming", src.getDisplayName(), text)
					.withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
		}
		return targets.size();
	}
}
