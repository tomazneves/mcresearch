package net.sapo_boi.research.item;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.sapo_boi.research.ResearchMod;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ResearchMod.MODID);

    public static final RegistryObject<CreativeModeTab> RESEARCH_TAB = CREATIVE_MODE_TABS.register("research_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModItems.PACK1.get()))
                    .title(Component.translatable( "creativetab.research_tab"))
                    .displayItems((pParameters, pOutput) -> {

                        // Items go here...
                        pOutput.accept(ModItems.PACK1.get());
                        pOutput.accept(ModItems.PACK2.get());
                        pOutput.accept(ModItems.PACK3.get());
                        pOutput.accept(ModItems.PACK4.get());
                        pOutput.accept(ModItems.PACK5.get());
                        pOutput.accept(ModItems.PACK6.get());
                        pOutput.accept(ModItems.PACK7.get());

                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
