package net.sapo_boi.research.network;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.sapo_boi.research.technology.ResearchSavedData;
import net.sapo_boi.research.technology.Technology;
import net.sapo_boi.research.technology.TechnologyManager;

import java.util.ArrayList;
import java.util.Objects;

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
        //MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        ResearchSavedData data = ResearchSavedData.get(Objects.requireNonNull(player.getServer()));
        SyncTechTreePacket packet = new SyncTechTreePacket(
            new ArrayList<>(TechnologyManager.getAll().values()), data.getUnlocked());
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    /** Sends the full tree + unlock state to everyone (used after a /reload). */
    public static void syncToAll(MinecraftServer server) {
        ResearchSavedData data = ResearchSavedData.get(server);
        SyncTechTreePacket packet = new SyncTechTreePacket(
            new ArrayList<>(TechnologyManager.getAll().values()), data.getUnlocked());
        CHANNEL.send(PacketDistributor.ALL.noArg(), packet);
    }
}
