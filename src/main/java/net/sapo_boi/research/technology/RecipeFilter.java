package net.sapo_boi.research.technology;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.util.*;

public class RecipeFilter {
    // Cache to hold recipes while they are locked
    private static final Map<ResourceLocation, Recipe<?>> HIDDEN_RECIPES = new HashMap<>();

    public static void updateGlobalRecipes(MinecraftServer server, Collection<Technology> allTechnologies, ResearchSavedData data) {
        RecipeManager manager = server.getRecipeManager();

        // 1. Compile all currently blocked items globally
        Set<ResourceLocation> blockedItems = new HashSet<>();
        for (Technology tech : allTechnologies) {
            // Adjust the accessor methods below if your records/methods are named differently
            if (!data.isUnlocked(tech.id())) {
                blockedItems.addAll(tech.blockedItems());
            }
        }

        try {
            // 2. Access both underlying recipe maps
            // "byName" is used for commands/advancements; "recipes" is used for the actual crafting grid
            Field byNameField = ObfuscationReflectionHelper.findField(RecipeManager.class, "byName");
            Field recipesField = ObfuscationReflectionHelper.findField(RecipeManager.class, "recipes");
            byNameField.setAccessible(true);
            recipesField.setAccessible(true);

            @SuppressWarnings("unchecked")
            Map<ResourceLocation, Recipe<?>> byName = (Map<ResourceLocation, Recipe<?>>) byNameField.get(manager);

            @SuppressWarnings("unchecked")
            Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> recipes =
                    (Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>>) recipesField.get(manager);

            // 3. Vanilla maps are Immutable. We must replace them with mutable copies if they aren't already.
            if (byName.getClass().getName().contains("Immutable")) {
                byName = new HashMap<>(byName);
                byNameField.set(manager, byName);
            }

            if (recipes.getClass().getName().contains("Immutable")) {
                Map<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> mutableRecipes = new HashMap<>();
                for (Map.Entry<RecipeType<?>, Map<ResourceLocation, Recipe<?>>> entry : recipes.entrySet()) {
                    mutableRecipes.put(entry.getKey(), new HashMap<>(entry.getValue()));
                }
                recipes = mutableRecipes;
                recipesField.set(manager, recipes);
            }

            // 4. Restore everything first to reset the state back to vanilla
            for (Map.Entry<ResourceLocation, Recipe<?>> entry : HIDDEN_RECIPES.entrySet()) {
                byName.put(entry.getKey(), entry.getValue());
                recipes.computeIfAbsent(entry.getValue().getType(), t -> new HashMap<>())
                        .put(entry.getKey(), entry.getValue());
            }
            HIDDEN_RECIPES.clear();

            // 5. Iterate and strip out recipes that output blocked items
            Iterator<Map.Entry<ResourceLocation, Recipe<?>>> it = byName.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<ResourceLocation, Recipe<?>> entry = it.next();
                Recipe<?> recipe = entry.getValue();

                String resultId = BuiltInRegistries.ITEM.getKey(
                        recipe.getResultItem(server.registryAccess()).getItem()
                ).toString();

                if (blockedItems.contains(resultId)) {
                    // Cache it for later
                    HIDDEN_RECIPES.put(entry.getKey(), recipe);

                    // Remove from the command/advancement map
                    it.remove();

                    // Remove from the actual crafting lookup map
                    Map<ResourceLocation, Recipe<?>> typeMap = recipes.get(recipe.getType());
                    if (typeMap != null) {
                        typeMap.remove(entry.getKey());
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}