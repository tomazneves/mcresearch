package net.sapo_boi.research.client;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientResearchHandler {
    public static void showToast(String technologyName, ResourceLocation technologyIcon, String message, int color) {
        Minecraft.getInstance().getToasts().addToast(new TechnologyToast(technologyName, technologyIcon, message, color));
    }
}
