package net.sapo_boi.research.technology;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.server.ServerLifecycleHooks;

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
        }
        return added;
    }

    public Set<ResourceLocation> getUnlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public static ResearchSavedData load(CompoundTag tag) {
        ResearchSavedData data = new ResearchSavedData();
        ListTag list = tag.getList("unlocked", Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            data.unlocked.add(ResourceLocation.parse(list.getString(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        unlocked.forEach(id -> list.add(StringTag.valueOf(id.toString())));
        tag.put("unlocked", list);
        return tag;
    }
}
