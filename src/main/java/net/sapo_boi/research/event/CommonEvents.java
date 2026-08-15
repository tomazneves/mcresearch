package net.sapo_boi.research.event;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.sapo_boi.research.ResearchMod;
import net.sapo_boi.research.network.ResearchNetworking;
import net.sapo_boi.research.technology.TechnologyManager;

import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.sapo_boi.research.technology.RecipeFilter;
import net.sapo_boi.research.technology.ResearchSavedData;
import net.sapo_boi.research.technology.TechnologyManager;

@Mod.EventBusSubscriber(modid = ResearchMod.MODID)
public class CommonEvents {
    /** Hooks the technology JSON loader into the normal data pack reload cycle. */
    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(new TechnologyManager());
    }

    /**
     * Fired on player login and after /reload - keeps the client's copy of the
     * tree and unlock state up to date.
     */
    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            ResearchNetworking.syncToPlayer(event.getPlayer());
        } else {
            applyRecipeFilter(event.getPlayerList().getServer());
            ResearchNetworking.syncToAll(event.getPlayerList().getServer());
        }
    }

    // 1. Fires when the server is fully booted and recipes are loaded
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        applyRecipeFilter(event.getServer());
    }

    // Helper method to keep things clean
    private static void applyRecipeFilter(MinecraftServer server) {
        ResearchSavedData data = ResearchSavedData.get(server);
        RecipeFilter.updateGlobalRecipes(server, TechnologyManager.getAllTechnologies(), data);

        // Note: You may want to broadcast a recipe sync packet to all players here
        // so their client-side recipe books update immediately after the filter applies!
        server.getPlayerList().getPlayers().forEach(player ->
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "recipe take " + player.getScoreboardName() + " *")
        );
    }
}
