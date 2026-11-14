package com.example.mymod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Skrzynia (jeden blok dla wszystkich rodzajów): prawy klik otwiera GUI z nagrodami i przyciskami. */
public class CrateBlock extends Block {
	private final String crateId;

	public CrateBlock(Properties properties, String crateId) {
		super(properties);
		this.crateId = crateId;
	}

	public String crateId() {
		return crateId;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level.isClientSide() || !(player instanceof ServerPlayer sp)) {
			return InteractionResult.SUCCESS;
		}
		Crates.Crate crate = Crates.get(crateId);
		if (crate == null) {
			return InteractionResult.FAIL;
		}
		var container = SkrzynieManager.preview(crate, level.registryAccess());
		sp.openMenu(new SimpleMenuProvider(
				(id, inv, p) -> new SkrzyniaMenu(id, inv, container, crate, pos),
				Component.literal(crate.name())));
		return InteractionResult.SUCCESS;
	}
}
