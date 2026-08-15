package net.sapo_boi.research.recipe;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "research", bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RecipeFilterReloadListener {
    private RecipeFilterReloadListener() {}

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) resourceManager -> {
            RecipeFilterService.reapplyAllFilters();
        });
    }
}