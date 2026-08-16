package net.sapo_boi.research.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.sapo_boi.research.client.ClientResearchData;
import net.sapo_boi.research.client.ClientResearchHandler;
import net.sapo_boi.research.technology.Technology;

import java.util.function.Supplier;

public class TechnologyPacket {
    private final Technology technology;

    public TechnologyPacket(Technology technology) {
        this.technology = technology;
    }

    public static void encode(TechnologyPacket msg, FriendlyByteBuf buf) {
        msg.technology.encode(buf);
    }

    public static TechnologyPacket decode(FriendlyByteBuf buf) {
        return new TechnologyPacket(Technology.decode(buf));
    }

    public static void handle(TechnologyPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            ClientResearchData.setCurrent(msg.technology);
        }));
        ctx.setPacketHandled(true);
    }
}
