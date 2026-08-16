package net.sapo_boi.research.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.PacketDistributor;
import net.sapo_boi.research.technology.ResearchSavedData;
import net.sapo_boi.research.technology.Technology;
import net.sapo_boi.research.technology.TechnologyManager;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;

import static net.sapo_boi.research.ResearchMod.CHANNEL;

/**
 * Call {@link #register()} once from the mod constructor.
 */
public class ResearchNetworking {

    private static int packetId = 0;

    private static int nextId() {
        return packetId++;
    }

    public static void register() {
        CHANNEL.registerMessage(nextId(), TechUnlockedToastPacket.class,
                TechUnlockedToastPacket::encode, TechUnlockedToastPacket::decode, TechUnlockedToastPacket::handle);

        CHANNEL.registerMessage(nextId(), TechLockedToastPacket.class,
                TechLockedToastPacket::encode, TechLockedToastPacket::decode, TechLockedToastPacket::handle);

        CHANNEL.registerMessage(nextId(), SyncTechTreePacket.class,
                SyncTechTreePacket::encode, SyncTechTreePacket::decode, SyncTechTreePacket::handle);

        CHANNEL.registerMessage(nextId(), CurrentResearchPacket.class,
                CurrentResearchPacket::encode, CurrentResearchPacket::decode, CurrentResearchPacket::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(nextId(), ServerboundSetCurrentTechnologyPacket.class,
                ServerboundSetCurrentTechnologyPacket::encode, ServerboundSetCurrentTechnologyPacket::decode,
                ServerboundSetCurrentTechnologyPacket::handle, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** Shows the "researched" toast on every online player's client and marks it unlocked client-side. */
    public static void broadcastTechUnlocked(MinecraftServer server, Technology tech) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new TechUnlockedToastPacket(tech.id(), tech.name(), tech.icon()));
    }

    /** Shows the "lost" toast on every online player's client and marks it unlocked client-side. */
    public static void broadcastTechLocked(MinecraftServer server, Technology tech) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new TechLockedToastPacket(tech.id(), tech.name(), tech.icon()));
    }

    /** Sends the full tree + unlock state to a single player (used on login). */
    public static void syncToPlayer(ServerPlayer player) {
        MinecraftServer server = Objects.requireNonNull(player.getServer());
        ResearchSavedData data = ResearchSavedData.get(server);
        SyncTechTreePacket packet = new SyncTechTreePacket(
                new ArrayList<>(TechnologyManager.getAll().values()), data.getUnlocked());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        sendCurrentResearch(server, player);
    }

    /** Sends the full tree + unlock state to everyone (used after a /reload). */
    public static void syncToAll(MinecraftServer server) {
        ResearchSavedData data = ResearchSavedData.get(server);
        SyncTechTreePacket packet = new SyncTechTreePacket(
                new ArrayList<>(TechnologyManager.getAll().values()), data.getUnlocked());
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
        broadcastCurrentResearch(server);
    }

    /** Sends the current technology + progress to a single player (e.g. on login). */
    public static void sendCurrentResearch(MinecraftServer server, ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), buildCurrentResearchPacket(server));
    }

    /** Broadcasts the current technology + progress to everyone (selection, clearing, Lab progress). */
    public static void broadcastCurrentResearch(MinecraftServer server) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), buildCurrentResearchPacket(server));
    }

    private static CurrentResearchPacket buildCurrentResearchPacket(MinecraftServer server) {
        ResearchSavedData data = ResearchSavedData.get(server);
        ResourceLocation id = data.getCurrentTechnology();
        int cost = 0;
        if (id != null) {
            Technology tech = TechnologyManager.get(id);
            if (tech != null) {
                cost = tech.cost();
            }
        }
        return new CurrentResearchPacket(id, data.getRemainingWork(), cost);
    }
}