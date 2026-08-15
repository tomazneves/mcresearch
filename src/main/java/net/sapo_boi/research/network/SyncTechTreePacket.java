package net.sapo_boi.research.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.sapo_boi.research.client.ClientResearchData;
import net.sapo_boi.research.technology.Technology;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

public class SyncTechTreePacket {
    private final List<Technology> technologies;
    private final Set<ResourceLocation> unlocked;

    public SyncTechTreePacket(List<Technology> technologies, Set<ResourceLocation> unlocked) {
        this.technologies = technologies;
        this.unlocked = unlocked;
    }

    public static void encode(SyncTechTreePacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.technologies.size());
        for (Technology tech : msg.technologies) {
            buf.writeResourceLocation(tech.id());
            buf.writeUtf(tech.name());
            buf.writeVarInt(tech.blockedItems().size());
            for (ResourceLocation item : tech.blockedItems()) {
                buf.writeResourceLocation(item);
            }
        }

        buf.writeVarInt(msg.unlocked.size());
        for (ResourceLocation id : msg.unlocked) {
            buf.writeResourceLocation(id);
        }
    }

    public static SyncTechTreePacket decode(FriendlyByteBuf buf) {
        int techCount = buf.readVarInt();
        List<Technology> technologies = new ArrayList<>(techCount);
        for (int i = 0; i < techCount; i++) {
            ResourceLocation id = buf.readResourceLocation();
            String name = buf.readUtf();
            int blockedCount = buf.readVarInt();
            List<ResourceLocation> blocked = new ArrayList<>(blockedCount);
            for (int j = 0; j < blockedCount; j++) {
                blocked.add(buf.readResourceLocation());
            }
            technologies.add(new Technology(id, name, blocked));
        }

        int unlockedCount = buf.readVarInt();
        Set<ResourceLocation> unlocked = new HashSet<>();
        for (int i = 0; i < unlockedCount; i++) {
            unlocked.add(buf.readResourceLocation());
        }

        return new SyncTechTreePacket(technologies, unlocked);
    }

    public static void handle(SyncTechTreePacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientResearchData.update(msg.technologies, msg.unlocked))
        );
        ctx.setPacketHandled(true);
    }
}
