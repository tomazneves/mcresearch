package net.sapo_boi.research.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.sapo_boi.research.technology.ResearchController;

import javax.annotation.Nullable;
import java.util.function.Supplier;

/**
 * Sent by the client (from {@code TechnologyTreeScreen}, after the player confirms) to request
 * setting a new current technology, or to clear it (when {@link #id} is null). The server is
 * always the authority: it re-validates prerequisites/unlock state before applying anything.
 */
public class ServerboundSetCurrentTechnologyPacket {
    @Nullable
    private final ResourceLocation id;

    public ServerboundSetCurrentTechnologyPacket(@Nullable ResourceLocation id) {
        this.id = id;
    }

    public static void encode(ServerboundSetCurrentTechnologyPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.id != null);
        if (msg.id != null) {
            buf.writeResourceLocation(msg.id);
        }
    }

    public static ServerboundSetCurrentTechnologyPacket decode(FriendlyByteBuf buf) {
        ResourceLocation id = buf.readBoolean() ? buf.readResourceLocation() : null;
        return new ServerboundSetCurrentTechnologyPacket(id);
    }

    public static void handle(ServerboundSetCurrentTechnologyPacket msg, Supplier<NetworkEvent.Context> ctxSupplier) {
        NetworkEvent.Context ctx = ctxSupplier.get();
        ctx.enqueueWork(() -> {
            ServerPlayer player = ctx.getSender();
            if (player == null || player.getServer() == null) {
                return;
            }
            if (msg.id == null) {
                ResearchController.clearCurrent(player.getServer(), player);
            } else {
                ResearchController.trySetCurrent(player.getServer(), player, msg.id);
            }
        });
        ctx.setPacketHandled(true);
    }
}