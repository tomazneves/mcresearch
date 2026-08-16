package net.sapo_boi.research.technology;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.sapo_boi.research.network.ResearchNetworking;

/**
 * Central, server-authoritative logic for the "current technology" workflow described in the
 * design doc:
 * <ul>
 *   <li>selecting a technology as current (only if all of its prerequisites are researched)</li>
 *   <li>clearing the current technology</li>
 *   <li>advancing the shared research progress counter, driven by Lab block entities</li>
 * </ul>
 * All state lives in {@link ResearchSavedData} so it survives restarts and is shared server-wide,
 * matching the rest of the mod's Factorio-like "one research at a time, for the whole server" design.
 */
public final class ResearchController {

    private ResearchController() {
    }

    public enum Result {
        SUCCESS,
        UNKNOWN_TECHNOLOGY,
        ALREADY_RESEARCHED,
        MISSING_PREREQUISITES
    }

    /** Attempts to set {@code id} as the current technology. Always syncs clients on success. */
    public static Result trySetCurrent(MinecraftServer server, ResourceLocation id) {
        Technology tech = TechnologyManager.get(id);
        if (tech == null) {
            return Result.UNKNOWN_TECHNOLOGY;
        }

        ResearchSavedData data = ResearchSavedData.get(server);
        if (data.isUnlocked(id)) {
            return Result.ALREADY_RESEARCHED;
        }
        if (!TechnologyManager.prerequisitesMet(data, tech)) {
            return Result.MISSING_PREREQUISITES;
        }

        data.setCurrentTechnology(id, tech.cost());
        ResearchNetworking.broadcastCurrentResearch(server);
        return Result.SUCCESS;
    }

    /** Same as {@link #trySetCurrent(MinecraftServer, ResourceLocation)}, but also messages the requesting player. */
    public static Result trySetCurrent(MinecraftServer server, ServerPlayer requester, ResourceLocation id) {
        Result result = trySetCurrent(server, id);
        if (requester != null) {
            messageResult(requester, result, TechnologyManager.get(id));
        }
        return result;
    }

    public static void clearCurrent(MinecraftServer server) {
        ResearchSavedData data = ResearchSavedData.get(server);
        data.clearCurrentTechnology();
        ResearchNetworking.broadcastCurrentResearch(server);
    }

    public static void clearCurrent(MinecraftServer server, ServerPlayer requester) {
        clearCurrent(server);
        if (requester != null) {
            requester.displayClientMessage(Component.literal("Cleared the current research."), true);
        }
    }

    /**
     * Advances the shared research counter by {@code amount}. Intended to be called once per Lab
     * that completes a processing cycle. If this brings the counter to 0, the technology is
     * unlocked, the "current" slot is cleared, and everyone is notified.
     */
    public static void advanceCurrentResearch(MinecraftServer server, int amount) {
        ResearchSavedData data = ResearchSavedData.get(server);
        ResourceLocation currentId = data.getCurrentTechnology();
        if (currentId == null) {
            return;
        }

        boolean finished = data.decrementRemainingWork(amount);

        if (finished) {
            Technology tech = TechnologyManager.get(currentId);
            data.clearCurrentTechnology();
            if (tech != null && data.unlock(currentId)) {
                ResearchNetworking.broadcastTechUnlocked(server, tech);
            }
        }

        ResearchNetworking.broadcastCurrentResearch(server);
    }

    /**
     * TODO
     */
    public static void setCurrentResearchProgress(MinecraftServer server, int amount) {
        ResearchSavedData data = ResearchSavedData.get(server);
        ResourceLocation currentId = data.getCurrentTechnology();
        if (currentId == null) {
            return;
        }

        boolean finished = data.setRemainingWork(amount);

        if (finished) {
            Technology tech = TechnologyManager.get(currentId);
            data.clearCurrentTechnology();
            if (tech != null && data.unlock(currentId)) {
                ResearchNetworking.broadcastTechUnlocked(server, tech);
            }
        }

        ResearchNetworking.broadcastCurrentResearch(server);
    }

    private static void messageResult(ServerPlayer player, Result result, Technology tech) {
        switch (result) {
            case SUCCESS -> player.displayClientMessage(
                    Component.literal("Now researching: " + (tech != null ? tech.name() : "")), true);
            case UNKNOWN_TECHNOLOGY -> player.displayClientMessage(
                    Component.literal("Unknown technology."), true);
            case ALREADY_RESEARCHED -> player.displayClientMessage(
                    Component.literal("That technology is already researched."), true);
            case MISSING_PREREQUISITES -> player.displayClientMessage(
                    Component.literal("Not all prerequisites are researched yet."), true);
        }
    }
}