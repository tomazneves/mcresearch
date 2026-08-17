package net.sapo_boi.research.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.sapo_boi.research.client.ClientResearchData;
import net.sapo_boi.research.client.ClientResearchHandler;
import net.sapo_boi.research.client.TechnologyToast;

import java.util.function.Supplier;

public class TechUnlockedToastPacket {
    private final ResourceLocation id;
    private final String name;
    private final ResourceLocation icon;

    public TechUnlockedToastPacket(ResourceLocation id, String name, ResourceLocation icon) {
        this.id = id;
        this.name = name;
        this.icon = icon;
    }

    public static void encode(TechUnlockedToastPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.id);
        buf.writeUtf(msg.name);
        buf.writeResourceLocation(msg.icon);
    }

    public static TechUnlockedToastPacket decode(FriendlyByteBuf buf) {
        return new TechUnlockedToastPacket(buf.readResourceLocation(), buf.readUtf(), buf.readResourceLocation());
    }

    public static void handle(TechUnlockedToastPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientResearchData.markUnlocked(msg.id);
            ClientResearchHandler.showToast(msg.name, msg.icon, "Technology researched!", TechnologyToast.BACKGROUND_COLOR);
        }));
        ctx.setPacketHandled(true);
    }
}
