package net.sapo_boi.research.technology;

import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A single node in the technology tree.
 * <p>
 * More fields (prerequisites, science pack costs, icons, etc.) will be added later -
 * this is intentionally the minimal shape needed to unlock/track a technology and to
 * know which items/blocks it gates.
 *
 * @param id           unique textual identifier, taken from the technology's JSON file
 *                     location (namespace:path), e.g. {@code research:automation}
 * @param name          human-readable display name shown in chat, toasts and the tree screen
 * @param blockedItems registry ids (namespace:path) of items/blocks - from this mod or any
 *                     other mod - that should be uncraftable until this technology is researched
 *
 * @param icon          registry id (namespace:path) of the icon representing the technology
 * @param prerequisites unique id of other technologies
 * @param ingredients   unique ids of items needed to unlock it
 * @param time          time needed for each item to be processed
 */
public record Technology(
        ResourceLocation id,
        String name,
        List<ResourceLocation> blockedItems,
        ResourceLocation icon,
        List<ResourceLocation> prerequisites,
        List<ResourceLocation> ingredients,
        int time) {
}
