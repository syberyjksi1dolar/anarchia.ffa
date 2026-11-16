package pl.anarchia.rangi;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

public final class RankItem extends Item {
    private final Rank rank;

    public RankItem(Rank rank, Properties properties) {
        super(properties);
        this.rank = rank;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            RankManager.activate(serverPlayer, rank);
        }
        return InteractionResult.SUCCESS;
    }
}
