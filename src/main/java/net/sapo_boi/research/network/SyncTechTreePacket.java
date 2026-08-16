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
        System.out.println("Encoding...\n");
        buf.writeVarInt(msg.technologies.size());
        for (Technology tech : msg.technologies) {
            tech.encode(buf);
        }

        buf.writeVarInt(msg.unlocked.size());
        for (ResourceLocation id : msg.unlocked) {
            buf.writeResourceLocation(id);
        }
    }

    public static SyncTechTreePacket decode(FriendlyByteBuf buf) {
        System.out.println("Decoding...\n");
        int techCount = buf.readVarInt();
        List<Technology> technologies = new ArrayList<>(techCount);
        System.out.println(techCount);

        for (int i = 0; i < techCount; i++) {

            // Construct with parsed fields instead of hardcoded defaults
            technologies.add(Technology.decode(buf));
        }

        int unlockedCount = buf.readVarInt();
        Set<ResourceLocation> unlocked = new HashSet<>(unlockedCount);
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
