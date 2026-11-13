package com.example.mymod;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.level.Level;

/** Wędka 1.8: zwykła wędka, a zwinięcie linki prawym kliknięciem przyciąga gracza do haczyka (blok lub byt). */
public class Wedka18Item extends FishingRodItem {
	public Wedka18Item(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!level.isClientSide() && player instanceof ServerPlayer sp && sp.fishing != null) {
			WedkiHandler.pull(sp);
		}
		return super.use(level, player, hand);
	}
}
