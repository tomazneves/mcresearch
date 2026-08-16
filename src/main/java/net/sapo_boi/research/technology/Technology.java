package net.sapo_boi.research.technology;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.sapo_boi.research.network.SyncTechTreePacket;

import java.util.ArrayList;
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
 * @param cost          ammount of items needed
 */
public record Technology(
        ResourceLocation id,
        String name,
        List<ResourceLocation> blockedItems,
        ResourceLocation icon,
        List<ResourceLocation> prerequisites,
        List<ResourceLocation> ingredients,
        int time,
        int cost) {

    public static Technology decode(FriendlyByteBuf buf) {
        // Existing fields
        ResourceLocation id = buf.readResourceLocation();

        String name = buf.readUtf();

        int blockedCount = buf.readVarInt();
        List<ResourceLocation> blockedItems = new ArrayList<>(blockedCount);
        for (int j = 0; j < blockedCount; j++) {
            blockedItems.add(buf.readResourceLocation());
        }

        // NEW: icon
        ResourceLocation icon = buf.readResourceLocation();

        // NEW: prerequisites
        int prereqCount = buf.readVarInt();
        List<ResourceLocation> prerequisites = new ArrayList<>(prereqCount);
        for (int j = 0; j < prereqCount; j++) {
            prerequisites.add(buf.readResourceLocation());
        }

        // NEW: ingredients
        int ingredientCount = buf.readVarInt();
        List<ResourceLocation> ingredients = new ArrayList<>(ingredientCount);
        for (int j = 0; j < ingredientCount; j++) {
            ingredients.add(buf.readResourceLocation());
        }

        // NEW: time
        int time = buf.readVarInt();

        // NEW: cost
        int cost = buf.readVarInt();

        // Construct with parsed fields instead of hardcoded defaults
        return new Technology(id, name, blockedItems, icon, prerequisites, ingredients, time, cost);
    }

    public void encode(FriendlyByteBuf buf) {
        // NEW: id
        buf.writeResourceLocation(this.id());

        // NEW: name
        buf.writeUtf(this.name());

        // NEW: blockedItems
        buf.writeVarInt(this.blockedItems().size());
        for (ResourceLocation item : this.blockedItems()) {
            buf.writeResourceLocation(item);
        }

        // NEW: icon
        buf.writeResourceLocation(this.icon());

        // NEW: prerequisites
        buf.writeVarInt(this.prerequisites().size());
        for (ResourceLocation prereq : this.prerequisites()) {
            buf.writeResourceLocation(prereq);
        }

        // NEW: ingredients
        buf.writeVarInt(this.ingredients().size());
        for (ResourceLocation ingredient : this.ingredients()) {
            buf.writeResourceLocation(ingredient);
        }

        // NEW: time
        buf.writeVarInt(this.time());

        // NEW: cost
        buf.writeVarInt(this.cost());
    }
}
