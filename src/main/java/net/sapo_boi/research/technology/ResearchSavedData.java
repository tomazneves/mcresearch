package net.sapo_boi.research.technology;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.sapo_boi.research.recipe.RecipeFilterService;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Tracks which technologies have been researched. This is global to the world/server
 * (like a shared Factorio "force"), not per-player - matching how the chat command and
 * toasts broadcast to everyone.
 */
public class ResearchSavedData extends SavedData {
    private static final String ID = "research_progress";

    private final Set<ResourceLocation> unlocked = new HashSet<>();

    /** The technology currently being researched (server-authoritative), or null if none is selected. */
    private ResourceLocation currentTechnology = null;
    private int currentTechnologyCost = 0;

    /**
     * Global counter tracking how much research is still needed to finish {@link #currentTechnology}.
     * Starts at the technology's {@code cost} whenever it's selected, and is decremented by
     * every Lab that completes a processing cycle. Reaching 0 finishes the research.
     */
    private int remainingWork = 0;

    public static ResearchSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(ResearchSavedData::load, ResearchSavedData::new, ID);
    }

    public static ResearchSavedData get() {
        MinecraftServer server_backup = ServerLifecycleHooks.getCurrentServer();
        return server_backup.overworld().getDataStorage()
                .computeIfAbsent(ResearchSavedData::load, ResearchSavedData::new, ID);
    }

    public boolean isUnlocked(ResourceLocation techId) {
        return unlocked.contains(techId);
    }

    /**
     * @return true if the technology was newly unlocked, false if it was already researched
     */
    public boolean unlock(ResourceLocation techId) {
        boolean added = unlocked.add(techId);
        if (added) {
            setDirty();
            Technology tech = TechnologyManager.get(techId);

            tech.blockedItems().forEach(location ->
                    RecipeFilterService.restoreRecipesFor(ForgeRegistries.ITEMS.getValue(location))
            );
        }
        return added;
    }


    public boolean lock(ResourceLocation techId) {
        boolean removed = unlocked.remove(techId);
        if (removed) {
            setDirty();
            Technology tech = TechnologyManager.get(techId);

            tech.blockedItems().forEach(location ->
                    RecipeFilterService.removeRecipesFor(ForgeRegistries.ITEMS.getValue(location))
            );
        }
        return removed;
    }


    public Set<ResourceLocation> getUnlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public ResourceLocation getCurrentTechnology() {
        return currentTechnology;
    }

    public int getRemainingWork() {
        return remainingWork;
    }

    /** Selects a new technology to research and (re)starts its progress counter at {@code cost}. */
    public void setCurrentTechnology(ResourceLocation id, int cost) {
        this.currentTechnology = id;
        this.remainingWork = Math.max(0, cost);
        this.currentTechnologyCost = this.remainingWork;
        setDirty();
    }

    /** Clears the current technology without affecting unlock state. */
    public void clearCurrentTechnology() {
        this.currentTechnology = null;
        this.remainingWork = 0;
        this.currentTechnologyCost = this.remainingWork;
        setDirty();
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Decrements the global research counter, e.g. once per completed Lab processing cycle.
     *
     * @return true if this decrement brought the counter to 0 (i.e. the research is complete)
     */
    public boolean decrementRemainingWork(int amount) {
        if (currentTechnology == null) {
            return false;
        }
        remainingWork = Math.max(0, remainingWork - amount);
        setDirty();
        return remainingWork <= 0;
    }


    public boolean setRemainingWork(int amount) {
        if (currentTechnology == null) {
            return false;
        }
        remainingWork = Math.max(0, amount);
        setDirty();
        return remainingWork <= 0;
    }

    public static ResearchSavedData load(CompoundTag tag) {
        ResearchSavedData data = new ResearchSavedData();
        ListTag list = tag.getList("unlocked", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            data.unlocked.add(ResourceLocation.parse(list.getString(i)));
        }

        if (tag.contains("currentTechnology")) {
            data.currentTechnology = ResourceLocation.parse(tag.getString("currentTechnology"));
            data.remainingWork = tag.getInt("remainingWork");
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        unlocked.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        tag.put("unlocked", list);

        if (currentTechnology != null) {
            tag.putString("currentTechnology", currentTechnology.toString());
            tag.putInt("remainingWork", remainingWork);
        }

        return tag;
    }

    public int getCurrentTechnologyCost() {
        return currentTechnologyCost;
    }
}