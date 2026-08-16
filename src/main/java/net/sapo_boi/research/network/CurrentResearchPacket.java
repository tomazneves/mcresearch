package net.sapo_boi.research.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.sapo_boi.research.client.ClientResearchData;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Sent whenever the server-wide "current technology" or its progress changes (selection,
 * clearing, or a Lab completing a processing cycle), and once on player join.
 */
public class CurrentResearchPacket {
    @Nullable
    private final ResourceLocation id;
    private final int remaining;
    private final int cost;

    public CurrentResearchPacket(@Nullable ResourceLocation id, int remaining, int cost) {
        this.id = id;
        this.remaining = remaining;
        this.cost = cost;
    }

    public static void encode(CurrentResearchPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.id != null);
        if (msg.id != null) {
            buf.writeResourceLocation(msg.id);
        }
        buf.writeVarInt(msg.remaining);
        buf.writeVarInt(msg.cost);
    }

    public static CurrentResearchPacket decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readBoolean() ? buf.readResourceLocation() : null;
        int remaining = buf.readVarInt();
        int cost = buf.readVarInt();
        return new CurrentResearchPacket(id, remaining, cost);
    }

    public static void handle(CurrentResearchPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientResearchData.setCurrent(msg.id, msg.remaining, msg.cost)));
        ctx.setPacketHandled(true);
    }
}