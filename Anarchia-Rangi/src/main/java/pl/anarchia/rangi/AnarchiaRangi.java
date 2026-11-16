package pl.anarchia.rangi;

import net.fabricmc.api.ModInitializer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Registry;

public final class AnarchiaRangi implements ModInitializer {
    public static final String MOD_ID = "anarchia_rangi";

    public static final ResourceKey<CreativeModeTab> KSIEGI_TAB_KEY =
            ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(),
                    Identifier.fromNamespaceAndPath(MOD_ID, "ksiegi"));

    @Override
    public void onInitialize() {
        RankItems.initialize();

        CreativeModeTab tab = CreativeModeTab.builder()
                .title(Component.literal("Księgi"))
                .icon(() -> new ItemStack(RankItems.VIP))
                .displayItems((parameters, output) -> {
                    output.accept(RankItems.VIP);
                    output.accept(RankItems.SVIP);
                    output.accept(RankItems.MVIP);
                    output.accept(RankItems.GVIP);
                    output.accept(RankItems.DVIP);
                    output.accept(RankItems.TWORCA);
                })
                .build();

        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KSIEGI_TAB_KEY, tab);
    }
}
