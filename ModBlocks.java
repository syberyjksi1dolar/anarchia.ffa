package com.example.mymod;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {
	/**
	 * Niebieski koralowiec, który nigdy nie obumiera (wygląda jak tube_coral_block,
	 * ale nie jest zwykłym koralowcem, więc poza wodą nie robi się szary).
	 * Wytrzymały na wybuchy. Kto i co może niszczyć, pilnuje ChydroKlatkaManager.
	 */
	public static final Block CHYDRO_KORAL = register("chydro_koral", Block::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.COLOR_BLUE)
					.sound(SoundType.CORAL_BLOCK)
					.strength(1.0F, 3600000.0F));

	/** Pajęczyna postawiona przez właściciela klatki: pełny blok, który po czasie wraca do zwykłej pajęczyny. */
	public static final Block CHYDRO_PAJECZYNA = register("chydro_pajeczyna", Block::new,
			BlockBehaviour.Properties.of()
					.mapColor(MapColor.WOOL)
					.sound(SoundType.WOOL)
					.strength(1.0F, 3600000.0F));

	public static <T extends Block> T register(String name, Function<BlockBehaviour.Properties, T> factory,
			BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(MyMod.MOD_ID, name));
		T block = factory.apply(properties.setId(key));
		Registry.register(BuiltInRegistries.BLOCK, key, block);
		return block;
	}

	public static void initialize() {
		// Wywołanie ładuje klasę i rejestruje bloki.
	}
}
