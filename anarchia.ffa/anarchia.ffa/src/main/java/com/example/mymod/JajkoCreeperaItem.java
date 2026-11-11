package com.example.mymod;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Jajko Zmutowanego Creepera: przywołuje creepera, który nie niszczy bloków, a zadaje 50% HP. */
public class JajkoCreeperaItem extends Item {
	public static final String TAG = "mymod_zmutowany";

	public JajkoCreeperaItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		if (!(level instanceof ServerLevel serverLevel)) {
			return InteractionResult.SUCCESS;
		}
		BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
		var creeper = EntityType.CREEPER.create(serverLevel, EntitySpawnReason.SPAWN_ITEM_USE);
		if (creeper != null) {
			creeper.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
			creeper.setYRot(serverLevel.getRandom().nextFloat() * 360.0F);
			creeper.addTag(TAG);
			creeper.setCustomName(Component.literal("Zmutowany Creeper"));
			serverLevel.addFreshEntity(creeper);
		}
		Player player = context.getPlayer();
		if (player != null && !player.getAbilities().instabuild) {
			context.getItemInHand().shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
