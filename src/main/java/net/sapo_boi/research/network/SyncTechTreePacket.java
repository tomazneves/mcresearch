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
            // Existing fields
            buf.writeResourceLocation(tech.id());
            buf.writeUtf(tech.name());

            buf.writeVarInt(tech.blockedItems().size());
            for (ResourceLocation item : tech.blockedItems()) {
                buf.writeResourceLocation(item);
            }

            // NEW: icon
            buf.writeResourceLocation(tech.icon());

            // NEW: prerequisites
            buf.writeVarInt(tech.prerequisites().size());
            for (ResourceLocation prereq : tech.prerequisites()) {
                buf.writeResourceLocation(prereq);
            }

            // NEW: ingredients
            buf.writeVarInt(tech.ingredients().size());
            for (ResourceLocation ingredient : tech.ingredients()) {
                buf.writeResourceLocation(ingredient);
            }

            // NEW: time
            buf.writeVarInt(tech.time());
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
            // Existing fields
            ResourceLocation id = buf.readResourceLocation();
            System.out.println(id);

            String name = buf.readUtf();
            System.out.println(name);

            int blockedCount = buf.readVarInt();
            System.out.println(blockedCount);
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

            // Construct with parsed fields instead of hardcoded defaults
            technologies.add(new Technology(id, name, blockedItems, icon, prerequisites, ingredients, time));
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
