package net.sapo_boi.research.technology;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.*;

/**
 * Loads technology definitions from {@code data/<namespace>/technology/*.json}.
 * <p>
 * This works exactly like recipes or loot tables: it reloads with the rest of the
 * data packs (including on {@code /reload}), and any data pack - including ones
 * shipped inside another mod's jar - can add or, with a higher pack priority,
 * override a technology. This is what makes the tree "configurable" without
 * touching any Java code.
 * <p>
 * Expected JSON shape:
 * <pre>{@code
 * {
 *   "name": "Automation",
 *   "blocked_items": [
 *     "minecraft:hopper",
 *     "examplemod:advanced_drill"
 *   ]
 * }
 * }</pre>
 */
public class TechnologyManager extends SimpleJsonResourceReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FOLDER = "technology";

    private static Map<ResourceLocation, Technology> TECHNOLOGIES = Map.of();

    public TechnologyManager() {
        super(new GsonBuilder().create(), FOLDER);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resourceMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Technology> built = new HashMap<>();

        resourceMap.forEach((id, json) -> {
            try {
                JsonObject obj = json.getAsJsonObject();
                String name = GsonHelper.getAsString(obj, "name");

                List<ResourceLocation> blockedItems = new ArrayList<>();
                if (obj.has("blocked_items")) {
                    GsonHelper.getAsJsonArray(obj, "blocked_items").forEach(element ->
                        blockedItems.add(ResourceLocation.parse(element.getAsString())));
                }

                built.put(id, new Technology(id, name, blockedItems));
            } catch (Exception e) {
                LOGGER.error("Failed to parse technology {}", id, e);
            }
        });

        TECHNOLOGIES = Map.copyOf(built);
        LOGGER.info("Loaded {} technologies", TECHNOLOGIES.size());
    }

    public static Map<ResourceLocation, Technology> getAll() {
        return TECHNOLOGIES;
    }

    public static Technology get(ResourceLocation id) {
        return TECHNOLOGIES.get(id);
    }

    public static Collection<Technology> getAllTechnologies() {
        return TECHNOLOGIES.values(); // Adjust 'TECHNOLOGIES' to whatever you named your map field
    }
}
