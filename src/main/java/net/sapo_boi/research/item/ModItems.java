package net.sapo_boi.research.item;

import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.sapo_boi.research.ResearchMod;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ResearchMod.MODID);

    public static final RegistryObject<Item> PACK1 = ITEMS.register("science_pack_1",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PACK2 = ITEMS.register("science_pack_2",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PACK3 = ITEMS.register("science_pack_3",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PACK4 = ITEMS.register("science_pack_4",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PACK5 = ITEMS.register("science_pack_5",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PACK6 = ITEMS.register("science_pack_6",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> PACK7 = ITEMS.register("science_pack_7",
            () -> new Item(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
