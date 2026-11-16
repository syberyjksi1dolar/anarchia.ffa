package pl.anarchia.rangi;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.ServerScoreboard;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

public final class RankManager {
    private RankManager() {}

    public static void activate(ServerPlayer player, Rank rank) {
        ServerScoreboard scoreboard = player.getServer().getScoreboard();

        // A player can belong to only one scoreboard team at a time.
        // Remove a previous Anarchia Rangi team, but do not delete the team itself.
        PlayerTeam current = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (current != null && current.getName().startsWith("ar_")) {
            scoreboard.removePlayerFromTeam(player.getScoreboardName(), current);
        }

        PlayerTeam team = scoreboard.getPlayerTeam(rank.teamId());
        if (team == null) {
            team = scoreboard.addPlayerTeam(rank.teamId());
            team.setDisplayName(Component.literal(rank.displayName()));
            team.setColor(rank.color());
            team.setPlayerPrefix(Component.literal("[" + rank.displayName() + "] ")
                    .withStyle(style -> style.withColor(TextColor.fromRgb(rank.rgb()))));
        }

        scoreboard.addPlayerToTeam(player.getScoreboardName(), team);
        player.sendSystemMessage(Component.literal("Aktywowano rangę ")
                .append(Component.literal(rank.displayName())
                        .withStyle(style -> style.withColor(TextColor.fromRgb(rank.rgb()))))
                .append(Component.literal("!")));
    }
}
