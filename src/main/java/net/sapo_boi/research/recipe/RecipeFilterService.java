package net.sapo_boi.research.recipe;

import net.minecraft.network.protocol.game.ClientboundUpdateRecipesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Dynamically removes/restores all recipes whose output item is in the filtered set.
 * State is kept in memory; if you need persistence, store FILTERED_ITEMS in a config
 * and re-apply on server start.
 */
public final class RecipeFilterService {
    private static final Logger LOGGER = LogManager.getLogger();

    private static final Set<Item> FILTERED_ITEMS = new HashSet<>();
    private static final Map<ResourceLocation, Recipe<?>> REMOVED_RECIPES = new HashMap<>();

    private RecipeFilterService() {}

    /**
     * Remove all recipes that output the given item.
     */
    public static void removeRecipesFor(Item item) {
        if (item == null) return;
        if (FILTERED_ITEMS.add(item)) {
            LOGGER.info("Filtering recipes that output: {}", item);
            applyFilters();
        }
    }

    /**
     * Restore all previously removed recipes that output the given item.
     */
    public static void restoreRecipesFor(Item item) {
        if (item == null) return;
        if (FILTERED_ITEMS.remove(item)) {
            LOGGER.info("Restoring recipes that output: {}", item);
            restoreFilteredItem(item);
        }
    }

    /**
     * Restore every removed recipe.
     */
    public static void restoreAll() {
        MinecraftServer server = getServer();
        if (server == null) {
            FILTERED_ITEMS.clear();
            REMOVED_RECIPES.clear();
            LOGGER.warn("No server running; cleared in-memory filter state without restoring recipes.");
            return;
        }

        RecipeManager recipeManager = server.getRecipeManager();
        Map<ResourceLocation, Recipe<?>> combined = new HashMap<>();

        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            combined.put(recipe.getId(), recipe);
        }
        for (Recipe<?> recipe : REMOVED_RECIPES.values()) {
            combined.put(recipe.getId(), recipe);
        }

        recipeManager.replaceRecipes(new ArrayList<>(combined.values()));
        FILTERED_ITEMS.clear();
        REMOVED_RECIPES.clear();

        syncRecipesToClients(server);
        LOGGER.info("All filtered recipes restored.");
    }

    /**
     * Called after a datapack reload. Re-applies every active filter to the freshly loaded
     * recipe manager state.
     */
    public static void reapplyAllFilters() {
        MinecraftServer server = getServer();
        if (server == null || FILTERED_ITEMS.isEmpty()) {
            return;
        }

        // The recipe manager has just been repopulated from datapacks, so old removed
        // recipe tracking is stale.
        REMOVED_RECIPES.clear();
        applyFilters(server);
        LOGGER.info("Re-applied active recipe filters after reload: {}", FILTERED_ITEMS);
    }

    private static void applyFilters() {
        MinecraftServer server = getServer();
        if (server == null) {
            LOGGER.warn("Cannot filter recipes: no server running.");
            return;
        }
        applyFilters(server);
    }

    private static void applyFilters(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        ArrayList<Recipe<?>> filteredRecipes = new ArrayList<>(recipeManager.getRecipes());

        Iterator<Recipe<?>> iterator = filteredRecipes.iterator();
        while (iterator.hasNext()) {
            Recipe<?> recipe = iterator.next();
            // FIX: pass registry access
            Item outputItem = recipe.getResultItem(server.registryAccess()).getItem();

            if (FILTERED_ITEMS.contains(outputItem)) {
                REMOVED_RECIPES.put(recipe.getId(), recipe);
                iterator.remove();
            }
        }

        recipeManager.replaceRecipes(filteredRecipes);
        syncRecipesToClients(server);
    }

    private static void restoreFilteredItem(Item item) {
        MinecraftServer server = getServer();
        if (server == null) {
            LOGGER.warn("Cannot restore recipes for {}: no server running.", item);
            return;
        }

        RecipeManager recipeManager = server.getRecipeManager();
        Map<ResourceLocation, Recipe<?>> combined = new HashMap<>();

        for (Recipe<?> recipe : recipeManager.getRecipes()) {
            combined.put(recipe.getId(), recipe);
        }

        Iterator<Map.Entry<ResourceLocation, Recipe<?>>> removedIterator = REMOVED_RECIPES.entrySet().iterator();
        while (removedIterator.hasNext()) {
            Map.Entry<ResourceLocation, Recipe<?>> entry = removedIterator.next();
            Recipe<?> recipe = entry.getValue();

            // FIX: pass registry access
            if (recipe.getResultItem(server.registryAccess()).getItem() == item) {
                combined.put(recipe.getId(), recipe);
                removedIterator.remove();
            }
        }

        recipeManager.replaceRecipes(new ArrayList<>(combined.values()));
        syncRecipesToClients(server);
    }

    private static void syncRecipesToClients(MinecraftServer server) {
        RecipeManager recipeManager = server.getRecipeManager();
        server.getPlayerList().broadcastAll(
                new ClientboundUpdateRecipesPacket(new ArrayList<>(recipeManager.getRecipes()))
        );
    }

    private static MinecraftServer getServer() {
        return ServerLifecycleHooks.getCurrentServer();
    }
}