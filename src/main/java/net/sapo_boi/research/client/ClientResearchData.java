package net.sapo_boi.research.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.sapo_boi.research.technology.Technology;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Client-side mirror of {@code TechnologyManager} + {@code ResearchSavedData}, kept in sync
 * via {@link net.sapo_boi.research.network.SyncTechTreePacket},
 * {@link net.sapo_boi.research.network.TechUnlockedToastPacket} and
 * {@link net.sapo_boi.research.network.CurrentResearchPacket}. Backs the tech tree screen.
 */
@OnlyIn(Dist.CLIENT)
public class ClientResearchData {
    private static Map<ResourceLocation, Technology> TECHNOLOGIES = new HashMap<>();
    private static final Set<ResourceLocation> UNLOCKED = new HashSet<>();

    @Nullable
    private static ResourceLocation currentId = null;
    private static int progressRemaining = 0;
    private static int progressCost = 0;

    public static void update(List<Technology> technologies, Set<ResourceLocation> unlocked) {
        TECHNOLOGIES = technologies.stream().collect(Collectors.toMap(Technology::id, t -> t));
        UNLOCKED.clear();
        UNLOCKED.addAll(unlocked);
    }

    public static void markUnlocked(ResourceLocation id) {
        UNLOCKED.add(id);
    }

    public static void markLocked(ResourceLocation id) {
        UNLOCKED.remove(id);
    }

    /** Applied whenever the server sends a {@code CurrentResearchPacket}. */
    public static void setCurrent(@Nullable ResourceLocation id, int remaining, int cost) {
        currentId = id;
        progressRemaining = remaining;
        progressCost = cost;
    }

    public static void setCurrent(Technology technology) {
        currentId = technology.id();
        progressRemaining = technology.cost();
        progressCost = technology.cost();
    }

    public static Collection<Technology> getTechnologies() {
        return TECHNOLOGIES.values();
    }

    public static Technology get(ResourceLocation id) {
        return TECHNOLOGIES.get(id);
    }

    public static boolean isUnlocked(ResourceLocation id) {
        return UNLOCKED.contains(id);
    }

    public static Set<ResourceLocation> getUnlocked() {
        return Collections.unmodifiableSet(UNLOCKED);
    }

    @Nullable
    public static ResourceLocation getCurrentId() {
        return currentId;
    }

    public static int getProgressRemaining() {
        return progressRemaining;
    }

    public static int getProgressCost() {
        return progressCost;
    }

    public static double getCurrentProgress() {
        double cost = progressCost;
        double remaining = progressRemaining;
        return 1.0 - remaining / cost;
    }

    public static int getProgressCompleted() {
        return Math.max(0, progressCost - progressRemaining);
    }
}