package pl.anarchia.rangi;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class RankItems {
    public static final Item VIP = register("vip", Rank.VIP);
    public static final Item SVIP = register("svip", Rank.SVIP);
    public static final Item MVIP = register("mvip", Rank.MVIP);
    public static final Item GVIP = register("gvip", Rank.GVIP);
    public static final Item DVIP = register("dvip", Rank.DVIP);
    public static final Item TWORCA = register("tworca", Rank.TWORCA);

    private RankItems() {}

    private static Item register(String id, Rank rank) {
        ResourceKey<Item> key = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(AnarchiaRangi.MOD_ID, id)
        );
        Item item = new RankItem(rank, new Item.Properties().stacksTo(1).setId(key));
        return Registry.register(BuiltInRegistries.ITEM, key, item);
    }

    public static void initialize() {
        // Loading this class registers all rank items.
    }
}
