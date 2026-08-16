package net.sapo_boi.research.client;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.sapo_boi.research.technology.Technology;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Client-side mirror of {@code TechnologyManager} + {@code ResearchSavedData}, kept in sync
 * via {@link net.sapo_boi.research.network.SyncTechTreePacket} and
 * {@link net.sapo_boi.research.network.TechUnlockedToastPacket}. Backs the progress screen.
 */
@OnlyIn(Dist.CLIENT)
public class ClientResearchData {
    private static Map<ResourceLocation, Technology> TECHNOLOGIES = new HashMap<>();
    private static final Set<ResourceLocation> UNLOCKED = new HashSet<>();
    private static Technology CURRENT = null;

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

    public static void setCurrent(Technology tech) {
        CURRENT = tech;
    }

    public static Collection<Technology> getTechnologies() {
        return TECHNOLOGIES.values();
    }

    public static boolean isUnlocked(ResourceLocation id) {
        return UNLOCKED.contains(id);
    }
}
